package io.github.mtooo.client;

import io.github.mtooo.core.exception.ErrorCode;
import io.github.mtooo.core.exception.Sm2SdkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 握手重试处理器，内置熔断器（Circuit Breaker）状态机。
 *
 * <p>熔断器状态机：CLOSED → OPEN → HALF_OPEN → CLOSED
 * <ul>
 *   <li><b>CLOSED</b>：正常状态，追踪连续失败次数。连续失败达到 {@link #FAILURE_THRESHOLD} 次后转为 OPEN。</li>
 *   <li><b>OPEN</b>：所有请求直接抛出 {@link Sm2SdkException}（{@link ErrorCode#CIRCUIT_BREAKER_TRIPPED}）。
 *       冷却 {@link #COOLDOWN_MS} 毫秒后转为 HALF_OPEN。</li>
 *   <li><b>HALF_OPEN</b>：允许 1 次探测请求。成功则转为 CLOSED（重置计数），失败则转为 OPEN（重新计时）。</li>
 * </ul>
 *
 * <p>此实现是线程安全的，使用 {@code AtomicReference} 和 {@code AtomicInteger} 等无锁原语保证并发安全。
 */
public class HandshakeRetryHandler {

    private static final Logger log = LoggerFactory.getLogger(HandshakeRetryHandler.class);

    /**
     * 熔断器内部状态枚举。
     */
    private enum CircuitState {
        /** 正常状态 */
        CLOSED,
        /** 熔断打开状态，请求被拒绝 */
        OPEN,
        /** 半开状态，允许探测 */
        HALF_OPEN
    }

    /** 连续失败阈值，达到此值触发熔断 */
    public static final int FAILURE_THRESHOLD = 5;

    /** 熔断冷却时间（毫秒） */
    public static final long COOLDOWN_MS = 30_000L;

    /** 指数退避延迟序列（毫秒） */
    private static final long[] BACKOFF_MS = {1000L, 2000L, 4000L};

    /** 最大重试次数（不含首次执行） */
    private final int handshakeRetry;

    /** 当前连续失败次数 */
    private final AtomicInteger consecutiveFailures;

    /** 熔断器当前状态 */
    private final AtomicReference<CircuitState> state;

    /** 熔断器打开时的时间戳（毫秒） */
    private final AtomicLong openTimestamp;

    /**
     * 创建一个握手重试处理器。
     *
     * @param handshakeRetry 最大重试次数（不含首次执行），不能为负数
     */
    public HandshakeRetryHandler(int handshakeRetry) {
        if (handshakeRetry < 0) {
            throw new IllegalArgumentException("handshakeRetry 不能为负数");
        }
        this.handshakeRetry = handshakeRetry;
        this.consecutiveFailures = new AtomicInteger(0);
        this.state = new AtomicReference<>(CircuitState.CLOSED);
        this.openTimestamp = new AtomicLong(0);
    }

    /**
     * 创建默认配置的握手重试处理器（最多重试3次）。
     */
    public HandshakeRetryHandler() {
        this(3);
    }

    /**
     * 执行握手操作，失败时进行指数退避重试。
     *
     * <p>每次执行前检查熔断器状态。若熔断器打开则直接抛出异常。
     * 调用失败后按 1s/2s/4s 指数退避重试，最多重试 {@code handshakeRetry} 次。
     * 所有重试耗尽后记录连续失败计数，并根据状态机规则决定是否触发熔断。
     *
     * @param callable 握手操作
     * @param <T>      返回类型
     * @return 执行结果
     * @throws Sm2SdkException 若所有重试均失败、熔断器打开或调用被中断
     */
    public <T> T executeWithRetry(Callable<T> callable) {
        // 执行前检查熔断器状态
        checkCircuitBreaker();

        int attempt = 0;
        Exception lastException = null;

        while (true) {
            attempt++;
            try {
                T result = callable.call();
                // 成功：重置计数，若处于 HALF_OPEN 则转为 CLOSED
                onSuccess();
                return result;
            } catch (Sm2SdkException e) {
                // 熔断器异常直通，不重试
                if (e.getErrorCode() == ErrorCode.CIRCUIT_BREAKER_TRIPPED) {
                    throw e;
                }
                lastException = e;
            } catch (Exception e) {
                lastException = e;
            }

            // 判断是否已达到最大重试次数
            if (attempt > handshakeRetry) {
                break;
            }

            // 指数退避等待
            long delay = getBackoffDelay(attempt - 1);
            log.warn("握手失败（第{}次），{}ms后重试", attempt, delay);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("休眠被中断", ie);
                lastException = new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED, "休眠被中断", ie);
                break;
            }
        }

        // 所有重试耗尽，记录连续失败
        onFailure();
        log.error("握手失败，已重试{}次", handshakeRetry);

        if (lastException instanceof Sm2SdkException) {
            throw (Sm2SdkException) lastException;
        }
        throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED,
                "握手请求失败: " + lastException.getMessage(), lastException);
    }

    /**
     * 检查熔断器状态。
     *
     * <p>若当前为 OPEN 状态且冷却时间已过，则原子地转为 HALF_OPEN。
     * 若为 OPEN 且冷却中，则抛出 {@link Sm2SdkException}（{@link ErrorCode#CIRCUIT_BREAKER_TRIPPED}）。
     */
    private void checkCircuitBreaker() {
        CircuitState current = state.get();
        if (current == CircuitState.OPEN) {
            long now = System.currentTimeMillis();
            if (now - openTimestamp.get() >= COOLDOWN_MS) {
                // 冷却时间已过，尝试转为 HALF_OPEN
                if (state.compareAndSet(CircuitState.OPEN, CircuitState.HALF_OPEN)) {
                    log.info("熔断器冷却结束，转为 HALF_OPEN 状态");
                    return;
                }
                // CAS 失败，其他线程已转换
                current = state.get();
                if (current != CircuitState.OPEN) {
                    return;
                }
            }
            log.warn("熔断器打开，请求被拒绝");
            throw new Sm2SdkException(ErrorCode.CIRCUIT_BREAKER_TRIPPED,
                    "熔断器已打开，请冷却后重试");
        }
        // CLOSED 或 HALF_OPEN 则放行
    }

    /**
     * 成功处理：重置连续失败计数。
     * 若当前处于 HALF_OPEN 状态则转为 CLOSED。
     */
    private void onSuccess() {
        CircuitState current = state.get();
        if (current == CircuitState.HALF_OPEN) {
            // 半开探测成功，转为 CLOSED
            if (state.compareAndSet(CircuitState.HALF_OPEN, CircuitState.CLOSED)) {
                log.info("熔断器半开探测成功，转为 CLOSED 状态");
            }
        }
        consecutiveFailures.set(0);
    }

    /**
     * 失败处理：递增连续失败计数。
     *
     * <ul>
     *   <li>若处于 HALF_OPEN 状态：转为 OPEN（重新计时）</li>
     *   <li>若处于 CLOSED 状态且连续失败达到阈值：转为 OPEN</li>
     * </ul>
     */
    private void onFailure() {
        int failures = consecutiveFailures.incrementAndGet();
        CircuitState current = state.get();
        if (current == CircuitState.HALF_OPEN) {
            state.set(CircuitState.OPEN);
            openTimestamp.set(System.currentTimeMillis());
            log.warn("熔断器半开探测失败，转为 OPEN 状态");
        } else if (current == CircuitState.CLOSED && failures >= FAILURE_THRESHOLD) {
            if (state.compareAndSet(CircuitState.CLOSED, CircuitState.OPEN)) {
                openTimestamp.set(System.currentTimeMillis());
                log.warn("连续{}次握手失败，熔断器打开", failures);
            }
        }
    }

    /**
     * 获取指数退避延迟时间。
     *
     * @param attemptIndex 从 0 开始的尝试序号
     * @return 延迟毫秒数
     */
    private long getBackoffDelay(int attemptIndex) {
        if (attemptIndex < BACKOFF_MS.length) {
            return BACKOFF_MS[attemptIndex];
        }
        return BACKOFF_MS[BACKOFF_MS.length - 1];
    }

    /**
     * 检查熔断器是否处于打开状态。
     *
     * @return true 表示熔断器打开，请求将被拒绝
     */
    public boolean isCircuitOpen() {
        return state.get() == CircuitState.OPEN;
    }

    /**
     * 获取当前连续失败次数。
     *
     * @return 连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }
}
