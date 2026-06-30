package com.sm2sdk.core.session.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionStore;

import java.util.concurrent.TimeUnit;

/**
 * 基于 Caffeine 缓存的会话存储实现。
 *
 * <p>使用 Caffeine {@link Cache} 存储会话，每个缓存条目的过期时间基于
 * 会话自身的 {@link Session#getMaxLifetime()}。当会话过期后，Caffeine
 * 会自动驱逐该条目。
 *
 * <p>该实现是线程安全的，因为 Caffeine 的所有缓存操作都是线程安全的。
 */
public class CaffeineSessionStore implements SessionStore {

    /** 毫秒与纳秒的转换系数。 */
    private static final long NANOS_PER_MS = 1_000_000L;

    private final Cache<String, Session> cache;

    /**
     * 构造一个 CaffeineSessionStore，使用 Caffeine 默认配置。
     *
     * <p>缓存使用可让每个条目按自身 {@link Session#getMaxLifetime()} 过期的
     * {@link Expiry} 策略。
     */
    public CaffeineSessionStore() {
        this.cache = Caffeine.newBuilder()
                .expireAfter(new Expiry<String, Session>() {
                    @Override
                    public long expireAfterCreate(
                            final String key, final Session session, final long currentTime) {
                        return session.getMaxLifetime() * NANOS_PER_MS;
                    }

                    @Override
                    public long expireAfterUpdate(
                            final String key, final Session session,
                            final long currentTime, final long currentDuration) {
                        // 更新时重置过期计时器，基于会话当前的 maxLifetime
                        return session.getMaxLifetime() * NANOS_PER_MS;
                    }

                    @Override
                    public long expireAfterRead(
                            final String key, final Session session,
                            final long currentTime, final long currentDuration) {
                        // 读取不改变过期时间
                        return currentDuration;
                    }
                })
                .build();
    }

    /**
     * 使用自定义 Caffeine 缓存构造 CaffeineSessionStore。
     *
     * <p>适用于需要自定义缓存配置（如测试中使用可控时钟）的场景。
     *
     * @param cache 自定义的 Caffeine 缓存实例（必须非空）
     */
    public CaffeineSessionStore(final Cache<String, Session> cache) {
        this.cache = cache;
    }

    @Override
    public Session get(final String sessionId) {
        return cache.getIfPresent(sessionId);
    }

    @Override
    public void put(final Session session) {
        cache.put(session.getSessionId(), session);
    }

    @Override
    public void remove(final String sessionId) {
        cache.invalidate(sessionId);
    }

    @Override
    public boolean exists(final String sessionId) {
        return cache.getIfPresent(sessionId) != null;
    }

    @Override
    public void renew(final String sessionId) {
        final Session session = cache.getIfPresent(sessionId);
        if (session != null) {
            // 更新会话状态：刷新最后访问时间并重置请求计数
            session.renew();
            // 重新放入缓存以刷新 expireAfterUpdate 计时器
            cache.put(sessionId, session);
        }
    }
}
