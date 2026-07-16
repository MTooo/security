package io.github.mtooo.client;

import io.github.mtooo.core.exception.ErrorCode;
import io.github.mtooo.core.exception.Sm2SdkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HandshakeRetryHandler} 的单元测试。
 *
 * <p>覆盖熔断器状态机：CLOSED → OPEN → HALF_OPEN → CLOSED 的完整生命周期，
 * 包括指数退避重试、连续失败触发熔断、冷却后半开探测、半开成功恢复和半开失败重新熔断等场景。
 */
class HandshakeRetryHandlerTest {

    private HandshakeRetryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new HandshakeRetryHandler(3);
    }

    // ========== 正常执行 ==========

    @Test
    void executeWithRetryShouldReturnResultOnFirstAttempt() throws Exception {
        // When
        String result = handler.executeWithRetry(() -> "success");

        // Then
        assertEquals("success", result);
        assertEquals(0, handler.getConsecutiveFailures());
        assertFalse(handler.isCircuitOpen());
    }

    // ========== 失败重试 ==========

    @Test
    void executeWithRetryShouldRetryOnFailureThenSucceed() throws Exception {
        // Given - 前2次失败，第3次成功
        AtomicInteger counter = new AtomicInteger(0);

        // When
        String result = handler.executeWithRetry(() -> {
            int count = counter.incrementAndGet();
            if (count < 3) {
                throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED, "模拟失败#" + count);
            }
            return "success";
        });

        // Then
        assertEquals("success", result);
        assertEquals(3, counter.get());
        assertEquals(0, handler.getConsecutiveFailures());
    }

    // ========== 重试耗尽抛出异常 ==========

    @Test
    void executeWithRetryShouldThrowWhenRetriesExhausted() {
        // Given - 始终失败的 Callable
        Callable<String> callable = () -> {
            throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED, "始终失败");
        };

        // When & Then
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> handler.executeWithRetry(callable));
        assertEquals(ErrorCode.HTTP_REQUEST_FAILED, ex.getErrorCode());
        assertEquals(1, handler.getConsecutiveFailures());
        assertFalse(handler.isCircuitOpen());
    }

    // ========== 连续5次失败触发熔断 ==========

    @Test
    void consecutiveFailuresShouldTripCircuitBreaker() {
        // Given
        Callable<String> callable = () -> {
            throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED, "始终失败");
        };

        // When - 前4次调用每次重试耗尽后记录1次连续失败
        for (int i = 0; i < 4; i++) {
            assertThrows(Sm2SdkException.class, () -> handler.executeWithRetry(callable));
        }

        // Then - 4次失败，尚未触发熔断
        assertEquals(4, handler.getConsecutiveFailures());
        assertFalse(handler.isCircuitOpen());

        // When - 第5次调用触发熔断
        assertThrows(Sm2SdkException.class, () -> handler.executeWithRetry(callable));

        // Then - 熔断器打开
        assertEquals(5, handler.getConsecutiveFailures());
        assertTrue(handler.isCircuitOpen());
    }

    // ========== 熔断期间拒绝请求 ==========

    @Test
    void circuitBreakerShouldRejectRequestsWhenOpen() {
        // Given - 触发熔断
        triggerCircuitBreaker();

        // When & Then - 熔断状态下请求被直接拒绝
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> handler.executeWithRetry(() -> "不应执行到此"));
        assertEquals(ErrorCode.CIRCUIT_BREAKER_TRIPPED, ex.getErrorCode());
        assertTrue(handler.isCircuitOpen());
    }

    // ========== 熔断拒绝后不增加失败计数 ==========

    @Test
    void circuitBreakerShouldNotIncreaseFailuresWhenRejecting() {
        // Given - 触发熔断
        triggerCircuitBreaker();
        assertEquals(5, handler.getConsecutiveFailures());

        // When - 多次被拒绝
        for (int i = 0; i < 3; i++) {
            assertThrows(Sm2SdkException.class,
                    () -> handler.executeWithRetry(() -> "不应执行到此"));
        }

        // Then - 失败计数不变
        assertEquals(5, handler.getConsecutiveFailures());
    }

    // ========== 冷却后进入半开 ==========

    @Test
    void circuitBreakerShouldTransitionToHalfOpenAfterCooldown() throws Exception {
        // Given - 触发熔断后修改 openTimestamp 模拟冷却完成
        triggerCircuitBreaker();
        setOpenTimestamp(System.currentTimeMillis() - 31_000L);

        // When - 执行成功探测
        String result = handler.executeWithRetry(() -> "probe");

        // Then - 探测成功，熔断器关闭
        assertEquals("probe", result);
        assertEquals(0, handler.getConsecutiveFailures());
        assertFalse(handler.isCircuitOpen());
    }

    // ========== 半开成功恢复 ==========

    @Test
    void halfOpenSuccessShouldResetToClosed() throws Exception {
        // Given - 熔断后冷却完成
        triggerCircuitBreaker();
        setOpenTimestamp(System.currentTimeMillis() - 31_000L);

        // When - 半开状态下执行成功
        handler.executeWithRetry(() -> "ok");

        // Then - 转为 CLOSED 状态
        assertFalse(handler.isCircuitOpen());
        assertEquals(0, handler.getConsecutiveFailures());

        // 再执行一次应正常
        String result = handler.executeWithRetry(() -> "normal");
        assertEquals("normal", result);
    }

    // ========== 半开失败重新熔断 ==========

    @Test
    void halfOpenFailureShouldReopenCircuit() throws Exception {
        // Given - 熔断后冷却完成
        triggerCircuitBreaker();
        setOpenTimestamp(System.currentTimeMillis() - 31_000L);

        // When - 半开状态下执行失败
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> handler.executeWithRetry(() -> {
                    throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED, "半开探测失败");
                }));
        assertEquals(ErrorCode.HTTP_REQUEST_FAILED, ex.getErrorCode());

        // Then - 重新熔断
        assertTrue(handler.isCircuitOpen());
        assertEquals(6, handler.getConsecutiveFailures());

        // 请求应被拒绝
        Sm2SdkException rejected = assertThrows(Sm2SdkException.class,
                () -> handler.executeWithRetry(() -> "不应执行到此"));
        assertEquals(ErrorCode.CIRCUIT_BREAKER_TRIPPED, rejected.getErrorCode());
    }

    // ========== 半开成功后连续失败应重新计数 ==========

    @Test
    void halfOpenResetAllowsNewFailureCounting() throws Exception {
        // Given - 半开成功恢复
        triggerCircuitBreaker();
        setOpenTimestamp(System.currentTimeMillis() - 31_000L);
        handler.executeWithRetry(() -> "ok");
        assertEquals(0, handler.getConsecutiveFailures());
        assertFalse(handler.isCircuitOpen());

        // When - 再次连续失败
        Callable<String> failing = () -> {
            throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED, "失败");
        };
        for (int i = 0; i < 4; i++) {
            assertThrows(Sm2SdkException.class, () -> handler.executeWithRetry(failing));
        }

        // Then - 4次失败后尚未熔断
        assertEquals(4, handler.getConsecutiveFailures());
        assertFalse(handler.isCircuitOpen());

        // 第5次触发熔断
        assertThrows(Sm2SdkException.class, () -> handler.executeWithRetry(failing));
        assertTrue(handler.isCircuitOpen());
        assertEquals(5, handler.getConsecutiveFailures());
    }

    // ========== 熔断器异常直通（不包裹） ==========

    @Test
    void executeWithRetryShouldPassThroughCircuitBreakerException() {
        // Given - 触发熔断
        triggerCircuitBreaker();

        // When & Then - CIRCUIT_BREAKER_TRIPPED 异常不应被包裹
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> handler.executeWithRetry(() -> "不应执行到此"));
        assertEquals(ErrorCode.CIRCUIT_BREAKER_TRIPPED, ex.getErrorCode());
        assertNull(ex.getCause());
    }

    // ========== 非 Sm2SdkException 的异常处理 ==========

    @Test
    void executeWithRetryShouldWrapNonSm2Exception() {
        // Given - 抛出非 Sm2SdkException
        Callable<String> callable = () -> {
            throw new RuntimeException("网络异常");
        };

        // When & Then - 包裹为 HTTP_REQUEST_FAILED
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> handler.executeWithRetry(callable));
        assertEquals(ErrorCode.HTTP_REQUEST_FAILED, ex.getErrorCode());
        assertNotNull(ex.getCause());
        assertEquals("网络异常", ex.getCause().getMessage());
    }

    // ========== handshakeRetry=0 时无重试 ==========

    @Test
    void noRetryWhenHandshakeRetryIsZero() {
        // Given
        HandshakeRetryHandler noRetryHandler = new HandshakeRetryHandler(0);

        AtomicInteger counter = new AtomicInteger(0);

        // When & Then - 只执行1次，失败后不重试
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> noRetryHandler.executeWithRetry(() -> {
                    counter.incrementAndGet();
                    throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED, "失败");
                }));
        assertEquals(1, counter.get());
        assertEquals(ErrorCode.HTTP_REQUEST_FAILED, ex.getErrorCode());
        assertEquals(1, noRetryHandler.getConsecutiveFailures());
    }

    // ========== isCircuitOpen 检查 ==========

    @Test
    void isCircuitOpenShouldReturnCorrectState() {
        // Given - 新建的熔断器
        assertFalse(handler.isCircuitOpen());

        // When - 触发熔断
        triggerCircuitBreaker();

        // Then
        assertTrue(handler.isCircuitOpen());
    }

    // ========== 辅助方法 ==========

    /**
     * 触发熔断器打开。连续执行5次始终失败的 Callable。
     */
    private void triggerCircuitBreaker() {
        Callable<String> callable = () -> {
            throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED, "始终失败");
        };
        for (int i = 0; i < 5; i++) {
            try {
                handler.executeWithRetry(callable);
            } catch (Sm2SdkException ignored) {
                // 预期异常
            }
        }
    }

    /**
     * 通过反射设置熔断器内部的 openTimestamp。
     * 用于模拟冷却时间流逝。
     */
    private void setOpenTimestamp(long timestamp) throws Exception {
        Field openTimestampField = HandshakeRetryHandler.class.getDeclaredField("openTimestamp");
        openTimestampField.setAccessible(true);
        AtomicLong openTs = (AtomicLong) openTimestampField.get(handler);
        openTs.set(timestamp);
    }
}
