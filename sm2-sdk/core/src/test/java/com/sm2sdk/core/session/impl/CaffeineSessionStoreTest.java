package com.sm2sdk.core.session.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import com.sm2sdk.core.session.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CaffeineSessionStore} 的单元测试。
 */
@DisplayName("CaffeineSessionStore")
class CaffeineSessionStoreTest {

    private static final long MAX_LIFETIME_MS = 60_000L;
    private static final int MAX_REQUESTS = 100;

    private CaffeineSessionStore store;

    @BeforeEach
    void setUp() {
        store = new CaffeineSessionStore();
    }

    // ========== 辅助方法 ==========

    private static Session createSession(final String id) {
        return new Session(
                id,
                "client-" + id,
                "peer-" + id,
                new byte[16],  // sm4Key
                new byte[12],  // sm4Iv
                System.currentTimeMillis(),
                MAX_LIFETIME_MS,
                MAX_REQUESTS
        );
    }

    // ========== put / get ==========

    @Test
    @DisplayName("put 之后 get 应返回相同的会话对象")
    void putThenGetReturnsSameSession() {
        Session session = createSession("session-1");
        store.put(session);

        Session retrieved = store.get("session-1");
        assertNotNull(retrieved);
        assertSame(session, retrieved, "应返回同一个会话实例");
        assertEquals("session-1", retrieved.getSessionId());
    }

    @Test
    @DisplayName("get 不存在的会话应返回 null")
    void getNonExistentReturnsNull() {
        assertNull(store.get("non-existent"));
    }

    // ========== remove ==========

    @Test
    @DisplayName("remove 之后 get 应返回 null")
    void removeThenGetReturnsNull() {
        Session session = createSession("session-2");
        store.put(session);

        store.remove("session-2");

        assertNull(store.get("session-2"));
    }

    @Test
    @DisplayName("remove 不存在的会话不应抛出异常")
    void removeNonExistentDoesNotThrow() {
        assertDoesNotThrow(() -> store.remove("non-existent"));
    }

    // ========== exists ==========

    @Test
    @DisplayName("存在的会话返回 true，不存在的返回 false")
    void existsReturnsCorrectly() {
        Session session = createSession("session-3");
        store.put(session);

        assertTrue(store.exists("session-3"));
        assertFalse(store.exists("non-existent"));

        store.remove("session-3");
        assertFalse(store.exists("session-3"));
    }

    // ========== renew ==========

    @Test
    @DisplayName("renew 应更新 lastAccessTime 并重置 requestCount")
    void renewUpdatesAccessTimeAndResetsRequestCount() {
        Session session = createSession("session-4");
        store.put(session);

        // 模拟访问以增加 requestCount
        long lastAccessBefore = session.getLastAccessTime();
        session.touch();
        session.touch();
        assertTrue(session.getRequestCount() >= 2, "touch 后 requestCount 应递增");

        // 执行续期
        store.renew("session-4");

        // lastAccessTime 应更新为当前时间（不低于之前的值）
        assertTrue(session.getLastAccessTime() >= lastAccessBefore,
                "renew 后 lastAccessTime 应被更新");
        assertEquals(0, session.getRequestCount(),
                "renew 后 requestCount 应重置为 0");

        // 通过 store.get 验证持久化状态
        Session fromStore = store.get("session-4");
        assertNotNull(fromStore);
        assertEquals(0, fromStore.getRequestCount());
    }

    @Test
    @DisplayName("renew 不存在的会话不应抛出异常")
    void renewNonExistentDoesNotThrow() {
        assertDoesNotThrow(() -> store.renew("non-existent"));
    }

    // ========== 过期 ==========

    @Test
    @DisplayName("会话过期后 get 应返回 null")
    void expiredSessionReturnsNull() {
        // 使用可控时钟的缓存构造 store
        AtomicLong tickerNanos = new AtomicLong(0L);
        Ticker ticker = tickerNanos::get;

        Cache<String, Session> cache = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfter(new Expiry<String, Session>() {
                    @Override
                    public long expireAfterCreate(
                            final String key, final Session s, final long currentTime) {
                        return s.getMaxLifetime() * 1_000_000L; // 毫秒转纳秒
                    }

                    @Override
                    public long expireAfterUpdate(
                            final String key, final Session s,
                            final long currentTime, final long currentDuration) {
                        return s.getMaxLifetime() * 1_000_000L;
                    }

                    @Override
                    public long expireAfterRead(
                            final String key, final Session s,
                            final long currentTime, final long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();

        CaffeineSessionStore testStore = new CaffeineSessionStore(cache);

        // 创建 maxLifetime = 100ms 的会话
        Session session = new Session(
                "expire-test",
                "client", "peer",
                new byte[16], new byte[12],
                0L,       // createTime (ticker 基准)
                100L,     // maxLifetime = 100ms
                10        // maxRequests
        );

        testStore.put(session);

        // 存入后立即读取，应存在
        assertNotNull(testStore.get("expire-test"));

        // 将时钟前进到超过 maxLifetime（200ms）
        tickerNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(200L));
        cache.cleanUp(); // 触发缓存维护，驱逐过期条目

        // 过期后应返回 null
        assertNull(testStore.get("expire-test"));
    }

    @Test
    @DisplayName("renew 后过期计时器应重置")
    void renewResetsExpiryTimer() {
        AtomicLong tickerNanos = new AtomicLong(0L);
        Ticker ticker = tickerNanos::get;

        Cache<String, Session> cache = Caffeine.newBuilder()
                .ticker(ticker)
                .expireAfter(new Expiry<String, Session>() {
                    @Override
                    public long expireAfterCreate(
                            final String key, final Session s, final long currentTime) {
                        return s.getMaxLifetime() * 1_000_000L;
                    }

                    @Override
                    public long expireAfterUpdate(
                            final String key, final Session s,
                            final long currentTime, final long currentDuration) {
                        return s.getMaxLifetime() * 1_000_000L;
                    }

                    @Override
                    public long expireAfterRead(
                            final String key, final Session s,
                            final long currentTime, final long currentDuration) {
                        return currentDuration;
                    }
                })
                .build();

        CaffeineSessionStore testStore = new CaffeineSessionStore(cache);

        Session session = new Session(
                "renew-expire",
                "client", "peer",
                new byte[16], new byte[12],
                0L,
                100L,     // maxLifetime = 100ms
                10
        );

        testStore.put(session);

        // 前进 80ms（未过期）
        tickerNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(80L));
        assertNotNull(testStore.get("renew-expire"));

        // renew 重置过期计时器
        testStore.renew("renew-expire");

        // 再前进 80ms（若未续期则已过期，续期后应仍然存活）
        tickerNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(80L));
        cache.cleanUp();
        assertNotNull(testStore.get("renew-expire"),
                "renew 后过期计时器应重置，80ms 后仍需可访问");

        // 再前进 80ms（总计 160ms > 100ms），此时应过期
        tickerNanos.addAndGet(TimeUnit.MILLISECONDS.toNanos(80L));
        cache.cleanUp();
        assertNull(testStore.get("renew-expire"),
                "超过重置后的 maxLifetime 后应返回 null");
    }
}
