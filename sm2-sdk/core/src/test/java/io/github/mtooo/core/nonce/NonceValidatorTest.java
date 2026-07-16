package io.github.mtooo.core.nonce;

import cn.hutool.bloomfilter.BloomFilterUtil;
import io.github.mtooo.core.exception.ErrorCode;
import io.github.mtooo.core.exception.Sm2SdkException;
import io.github.mtooo.core.nonce.NonceValidator.RedisOperations;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * NonceValidator 单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>新 nonce 不被判定为重复</li>
 *   <li>相同 nonce 被检测为重复（Bloom Filter 模式）</li>
 *   <li>相同 nonce 被检测为重复（Bloom Filter + Redis 模式）</li>
 *   <li>Bloom Filter 定期清理</li>
 *   <li>并发 nonce 校验</li>
 * </ul>
 *
 * <p>测试中使用较小的 Bloom Filter 参数以加快测试速度。
 */
class NonceValidatorTest {

    /** 测试用 Bloom Filter 参数：每哈希函数位数 */
    private static final int TEST_BLOOM_M = 100;

    /** 测试用 Bloom Filter 参数：期望元素数量 */
    private static final int TEST_BLOOM_N = 10;

    /** 测试用 Bloom Filter 参数：哈希函数数量 */
    private static final int TEST_BLOOM_K = 3;

    // ==================== 新 nonce 校验 ====================

    /**
     * 新 nonce 不应被判定为重复。
     *
     * <p>验证：未使用过的 nonce 调用 {@link NonceValidator#isDuplicate(String)} 返回 false。
     */
    @Test
    void testNewNonceIsNotDuplicate() {
        NonceValidator validator = new NonceValidator(null, TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K);

        // 全新 nonce，不应为重复
        assertFalse(validator.isDuplicate("new-nonce-001"));
        assertFalse(validator.isDuplicate("new-nonce-002"));
        assertFalse(validator.isDuplicate("new-nonce-003"));
    }

    // ==================== 相同 nonce 重复检测（Bloom Filter 模式） ====================

    /**
     * 相同 nonce 在使用 markUsed 后应被 Bloom Filter 检测为重复。
     *
     * <p>验证：markUsed 将 nonce 加入 Bloom Filter 后，isDuplicate 返回 true。
     */
    @Test
    void testDuplicateDetectedByBloomFilter() {
        NonceValidator validator = new NonceValidator(null, TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K);

        String nonce = "test-nonce-bloom";

        // 首次使用，不应为重复
        assertFalse(validator.isDuplicate(nonce));

        // 标记为已使用
        validator.markUsed(nonce);

        // 再次校验，应被 Bloom Filter 检测为重复
        assertTrue(validator.isDuplicate(nonce));
    }

    /**
     * 多个不同 nonce 互不干扰。
     *
     * <p>验证：Bloom Filter 能正确区分不同的 nonce。
     */
    @Test
    void testDifferentNoncesDoNotInterfere() {
        NonceValidator validator = new NonceValidator(null, TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K);

        String nonceA = "nonce-a";
        String nonceB = "nonce-b";

        validator.markUsed(nonceA);

        // nonceA 应被检测为重复
        assertTrue(validator.isDuplicate(nonceA));

        // nonceB 不应被检测为重复
        assertFalse(validator.isDuplicate(nonceB));
    }

    // ==================== 相同 nonce 重复检测（Bloom Filter + Redis 模式） ====================

    /**
     * Redis 模式下，相同 nonce 被 Redis SETNX 确认为重复时抛出 {@link Sm2SdkException}。
     *
     * <p>验证流程：
     * <ol>
     *   <li>markUsed 将 nonce 加入 Bloom Filter</li>
     *   <li>首次 isDuplicate：Bloom 报告"可能存在"，Redis SETNX 返回 true（新 key）→ 返回 false</li>
     *   <li>第二次 isDuplicate：Bloom 报告"可能存在"，Redis SETNX 返回 false（key 已存在）→ 抛出 NONCE_REPLAY</li>
     * </ol>
     */
    @Test
    void testDuplicateThrowsExceptionWithRedis() {
        RedisOperations redisOps = mock(RedisOperations.class);
        // 第一次调用 SETNX 返回 true（新 key），第二次返回 false（key 已存在）
        when(redisOps.setIfAbsent(anyString(), anyString(), anyInt()))
                .thenReturn(true)
                .thenReturn(false);

        NonceValidator validator = new NonceValidator(redisOps, TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K);

        String nonce = "test-nonce-redis";

        // 标记为已使用（加入 Bloom Filter）
        validator.markUsed(nonce);

        // 首次校验：SETNX 返回 true → 非重复
        assertFalse(validator.isDuplicate(nonce));

        // 再次校验：SETNX 返回 false → 确认为重复
        Sm2SdkException exception = assertThrows(Sm2SdkException.class, () -> validator.isDuplicate(nonce));
        assertEquals(ErrorCode.NONCE_REPLAY, exception.getErrorCode());
        assertEquals(403, exception.getHttpStatus());
    }

    /**
     * Redis 模式下，Bloom Filter 误判时（SETNX 返回 true）应放行。
     *
     * <p>验证：当 Bloom 报告"可能存在"但 Redis SETNX 返回 true（新 key）时，
     * isDuplicate 应返回 false（非重复），因为 Redis 的精确校验覆盖了 Bloom 的误判。
     */
    @Test
    void testBloomFalsePositiveWithRedis() {
        // 模拟 Redis SETNX 始终返回 true（所有 nonce 在 Redis 中均不存在）
        RedisOperations redisOps = mock(RedisOperations.class);
        when(redisOps.setIfAbsent(anyString(), anyString(), anyInt())).thenReturn(true);

        NonceValidator validator = new NonceValidator(redisOps, TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K);

        String nonce = "false-positive-nonce";
        validator.markUsed(nonce);

        // Bloom 报告可能存在，但 Redis 确认是新 key → 非重复
        assertFalse(validator.isDuplicate(nonce));
    }

    /**
     * 无 Redis 时，Bloom 判定可能存在应返回 true（安全兜底）。
     */
    @Test
    void testDuplicateReturnsTrueWhenNoRedis() {
        NonceValidator validator = new NonceValidator(null, TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K);

        String nonce = "no-redis-nonce";
        validator.markUsed(nonce);

        // 无 Redis，Bloom 判定可能重复 → 返回 true
        assertTrue(validator.isDuplicate(nonce));
    }

    // ==================== Bloom Filter 清理 ====================

    /**
     * 清理超过 5 分钟的 Bloom Filter，最近 5 分钟的过滤器应保留。
     *
     * <p>验证：cleanupExpiredFilters 移除旧分钟槽的过滤器，保留当前分钟槽的过滤器。
     */
    @Test
    void testCleanupExpiredFilters() {
        NonceValidator validator = new NonceValidator(null, TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K);

        // 标记当前 nonce，创建当前分钟槽的过滤器
        validator.markUsed("current-nonce");
        long currentSlot = validator.getCurrentMinuteSlot();

        // 验证当前过滤器已存在
        assertEquals(1, validator.getFilters().size());
        assertTrue(validator.getFilters().containsKey(currentSlot));

        // 直接添加一个旧的分钟槽过滤器（10 分钟前）
        validator.getFilters().put(currentSlot - 10, BloomFilterUtil.createBitSet(TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K));

        // 验证共有 2 个过滤器
        assertEquals(2, validator.getFilters().size());

        // 执行清理
        validator.cleanupExpiredFilters();

        // 旧过滤器应被移除，当前过滤器保留
        assertEquals(1, validator.getFilters().size());
        assertTrue(validator.getFilters().containsKey(currentSlot));
        assertFalse(validator.getFilters().containsKey(currentSlot - 10));
    }

    /**
     * 清理不应移除最近 5 分钟内的过滤器。
     *
     * <p>验证：在当前分钟和前 4 分钟内的过滤器应被保留。
     */
    @Test
    void testActiveFiltersNotCleaned() {
        NonceValidator validator = new NonceValidator(null, TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K);

        long currentSlot = validator.getCurrentMinuteSlot();

        // 直接添加当前分钟和前 4 分钟的过滤器
        for (long slot = currentSlot - 4; slot <= currentSlot; slot++) {
            validator.getFilters().put(slot, BloomFilterUtil.createBitSet(TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K));
        }

        assertEquals(5, validator.getFilters().size());

        // 执行清理
        validator.cleanupExpiredFilters();

        // 所有活跃过滤器应保留
        assertEquals(5, validator.getFilters().size());
    }

    /**
     * 过滤器映射为空时，清理操作无异常。
     */
    @Test
    void testCleanupOnEmptyFilters() {
        NonceValidator validator = new NonceValidator(null, TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K);

        assertDoesNotThrow(validator::cleanupExpiredFilters);
        assertTrue(validator.getFilters().isEmpty());
    }

    // ==================== 并发 nonce 校验 ====================

    /**
     * 并发 nonce 校验：多个线程同时校验同一 nonce，仅一个线程通过。
     *
     * <p>验证：AtomicBoolean 确保只放行第一个到达 Redis SETNX 的线程，
     * 其余线程均被拒绝（抛出 Sm2SdkException 或 isDuplicate 返回 true）。
     */
    @Test
    void testConcurrentNonceValidation() throws InterruptedException {
        // 使用 AtomicBoolean 模拟 SETNX：仅第一次调用返回 true
        AtomicBoolean firstAttempt = new AtomicBoolean(true);
        RedisOperations redisOps = (key, value, ttl) -> firstAttempt.getAndSet(false);

        NonceValidator validator = new NonceValidator(redisOps, TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K);

        // 预标记 nonce，确保 Bloom Filter 报告"可能存在"→ 触发 Redis 校验
        String concurrentNonce = "concurrent-nonce";
        validator.markUsed(concurrentNonce);

        int threadCount = 20;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger passCount = new AtomicInteger(0);
        AtomicInteger rejectCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            Thread t = new Thread(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // 等待所有线程就绪后同时启动

                    if (!validator.isDuplicate(concurrentNonce)) {
                        passCount.incrementAndGet();
                        validator.markUsed(concurrentNonce);
                    } else {
                        rejectCount.incrementAndGet();
                    }
                } catch (Sm2SdkException e) {
                    rejectCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }

        // 等待所有线程就绪，然后同时启动
        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        // 只有一个线程应通过校验
        assertEquals(1, passCount.get(), "应恰好有一个线程通过 nonce 校验");
        assertEquals(threadCount - 1, rejectCount.get(), "其余线程应被拒绝");
    }

    /**
     * 并发场景中，不同 nonce 互不干扰。
     *
     * <p>验证：多个线程分别校验不同的 nonce，所有线程都应通过。
     */
    @Test
    void testConcurrentDifferentNonces() throws InterruptedException {
        NonceValidator validator = new NonceValidator(null, TEST_BLOOM_M, TEST_BLOOM_N, TEST_BLOOM_K);

        int threadCount = 10;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger passCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String nonce = "unique-nonce-" + i;
            Thread t = new Thread(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();

                    assertFalse(validator.isDuplicate(nonce));
                    validator.markUsed(nonce);
                    passCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
            t.setDaemon(true);
            t.start();
        }

        readyLatch.await();
        startLatch.countDown();
        doneLatch.await();

        assertEquals(threadCount, passCount.get(), "所有线程都应通过各自 nonce 的校验");
    }
}
