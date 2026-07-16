package io.github.mtooo.core.session.impl;

import cn.hutool.json.JSONUtil;
import io.github.mtooo.core.model.Sm2SdkConfig;
import io.github.mtooo.core.session.Session;
import io.github.mtooo.core.session.SessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 基于 Redis 的分布式会话存储实现。
 *
 * <p>将会话序列化为 JSON 后存储到 Redis，并设置与 {@link Sm2SdkConfig#getSessionTimeoutMs()}
 * 同步的 TTL（过期时间）。{@link Session Session} 的 SM4 密钥在序列化前使用本地加密密钥
 * （由 {@link Sm2SdkConfig#getLocalSecretKey()} 配置）进行 SM4-GCM 加密保护，
 * 确保即使 Redis 数据泄露，SM4 密钥材料也不被直接暴露。
 *
 * <p>Key 格式：<code>{prefix}:session:{sessionId}</code>，其中 prefix 通过
 * {@link Sm2SdkConfig#getRedisKeyPrefix()} 配置，默认 "sm2"。
 *
 * <p>Redis 操作通过 {@link RedisOperations} 接口抽象，不直接依赖具体的 Redis 客户端
 * （Lettuce / Jedis / Redisson / Spring Data Redis 均可适配）。当 Redis 操作抛出异常时，
 * 自动降级到本地 {@link CaffeineSessionStore} 并记录 WARN 日志。
 *
 * <p>线程安全：本类的所有公开方法都是线程安全的。序列化/反序列化操作在方法内部完成，
 * 不会暴露未完成的 Session 实例给外部线程。
 */
public class RedisSessionStore implements SessionStore {

    private static final Logger log = LoggerFactory.getLogger(RedisSessionStore.class);

    /** Redis key 模板：{prefix}:session:{sessionId} */
    private static final String KEY_TEMPLATE = "%s:session:%s";

    /** GCM 认证标签长度（位） */
    private static final int GCM_TAG_LENGTH = 128;

    /** GCM IV 长度（字节） */
    private static final int GCM_IV_LENGTH = 12;

    /** GCM 认证标签长度（字节） */
    private static final int GCM_TAG_LEN_BYTES = 16;

    private final String keyPrefix;
    private final RedisOperations redisOps;
    private final byte[] encryptionKey;
    private final long sessionTimeoutMs;
    private final SessionStore localFallback;
    private final SecureRandom secureRandom;

    /**
     * Redis 操作接口，抽象对 Redis 的 set / get / del 操作。
     *
     * <p>实现方可根据实际使用的 Redis 客户端（Lettuce / Jedis / Redisson / Spring Data Redis）
     * 提供适配实现。当这些操作抛出异常时，{@link RedisSessionStore} 会自动降级到本地缓存。
     */
    public interface RedisOperations {

        /**
         * 设置 key 的值并指定过期时间。
         *
         * @param key   Redis key
         * @param value 序列化后的会话 JSON
         * @param ttlMs 过期时间（毫秒）
         */
        void set(String key, String value, long ttlMs);

        /**
         * 获取 key 的值。
         *
         * @param key Redis key
         * @return 序列化后的会话 JSON，不存在则返回 {@code null}
         */
        String get(String key);

        /**
         * 删除 key。
         *
         * @param key Redis key
         */
        void del(String key);
    }

    /**
     * 构造 RedisSessionStore。
     *
     * @param config   SDK 配置，从中读取 redisKeyPrefix、sessionTimeoutMs 和 localSecretKey
     * @param redisOps Redis 操作接口实现
     */
    public RedisSessionStore(Sm2SdkConfig config, RedisOperations redisOps) {
        this.keyPrefix = config.getRedisKeyPrefix() != null
                ? config.getRedisKeyPrefix() : Sm2SdkConfig.DEFAULT_REDIS_KEY_PREFIX;
        this.redisOps = redisOps;
        this.sessionTimeoutMs = config.getSessionTimeoutMs();
        this.localFallback = new CaffeineSessionStore();
        this.secureRandom = new SecureRandom();

        // 解析本地加密密钥
        String localSecretKeyStr = config.getLocalSecretKey();
        if (localSecretKeyStr != null && !localSecretKeyStr.isEmpty()) {
            byte[] decoded = Base64.getDecoder().decode(localSecretKeyStr);
            this.encryptionKey = deriveSm4Key(decoded);
            log.info("本地加密密钥已配置，SM4 密钥将使用 SM4-GCM 加密后存储");
        } else {
            this.encryptionKey = null;
            log.warn("localSecretKey 未配置，sm4Key 将以 Base64 编码（非加密）形式存储在 Redis 中。"
                    + " 建议配置 localSecretKey 以加密保护 SM4 密钥材料。");
        }
    }

    @Override
    public Session get(String sessionId) {
        try {
            String json = redisOps.get(buildKey(sessionId));
            if (json == null) {
                return null;
            }
            return deserialize(json);
        } catch (Exception e) {
            log.warn("Redis 不可用，降级到本地缓存获取会话: {}", sessionId, e);
            return localFallback.get(sessionId);
        }
    }

    @Override
    public void put(Session session) {
        try {
            String json = serialize(session);
            redisOps.set(buildKey(session.getSessionId()), json, sessionTimeoutMs);
        } catch (Exception e) {
            log.warn("Redis 不可用，降级到本地缓存存储会话: {}", session.getSessionId(), e);
            localFallback.put(session);
        }
    }

    @Override
    public void remove(String sessionId) {
        try {
            redisOps.del(buildKey(sessionId));
        } catch (Exception e) {
            log.warn("Redis 不可用，降级到本地缓存移除会话: {}", sessionId, e);
            localFallback.remove(sessionId);
        }
    }

    @Override
    public boolean exists(String sessionId) {
        try {
            String json = redisOps.get(buildKey(sessionId));
            return json != null;
        } catch (Exception e) {
            log.warn("Redis 不可用，降级到本地缓存检查会话存在性: {}", sessionId, e);
            return localFallback.exists(sessionId);
        }
    }

    @Override
    public void renew(String sessionId) {
        try {
            String json = redisOps.get(buildKey(sessionId));
            if (json == null) {
                return;
            }
            Session session = deserialize(json);
            session.renew();
            // 重新序列化并存储，同时刷新 TTL
            String updatedJson = serialize(session);
            redisOps.set(buildKey(sessionId), updatedJson, sessionTimeoutMs);
        } catch (Exception e) {
            log.warn("Redis 不可用，降级到本地缓存续期会话: {}", sessionId, e);
            localFallback.renew(sessionId);
        }
    }

    // ========== 内部方法 ==========

    /**
     * 构建 Redis key。
     *
     * @param sessionId 会话 ID
     * @return 完整的 Redis key
     */
    private String buildKey(String sessionId) {
        return String.format(KEY_TEMPLATE, keyPrefix, sessionId);
    }

    /**
     * 将会话序列化为 JSON 字符串。
     *
     * @param session 会话对象
     * @return JSON 字符串
     */
    private String serialize(Session session) {
        try {
            SessionData data = new SessionData();
            data.sessionId = session.getSessionId();
            data.clientId = session.getClientId();
            data.peerId = session.getPeerId();
            data.sm4Key = encryptSm4Key(session);
            byte[] ivCopy = session.getSm4IvCopy();
            data.sm4Iv = Base64.getEncoder().encodeToString(ivCopy);
            Session.clearKeyCopy(ivCopy);
            data.createTime = session.getCreateTime();
            data.lastAccessTime = session.getLastAccessTime();
            data.maxLifetime = session.getMaxLifetime();
            data.maxRequests = session.getMaxRequests();
            data.requestCount = session.getRequestCount();
            data.rekeyVersion = session.getRekeyVersion();
            data.destroyed = session.isDestroyed();
            return JSONUtil.toJsonStr(data);
        } catch (Exception e) {
            throw new RuntimeException("序列化会话失败: " + session.getSessionId(), e);
        }
    }

    /**
     * 从 JSON 字符串反序列化会话。
     *
     * @param json JSON 字符串
     * @return 会话对象
     */
    private Session deserialize(String json) {
        try {
            SessionData data = JSONUtil.toBean(json, SessionData.class);

            byte[] sm4Key;
            if (encryptionKey != null) {
                sm4Key = decryptSm4Key(data.sm4Key);
            } else {
                sm4Key = Base64.getDecoder().decode(data.sm4Key);
            }
            byte[] sm4Iv = Base64.getDecoder().decode(data.sm4Iv);

            Session session = new Session(
                    data.sessionId, data.clientId, data.peerId,
                    sm4Key, sm4Iv,
                    data.createTime, data.maxLifetime, data.maxRequests
            );

            // 使用反射恢复无法通过构造函数设置的字段
            setField(session, "lastAccessTime", data.lastAccessTime);
            setField(session, "requestCount", data.requestCount);
            setField(session, "rekeyVersion", data.rekeyVersion);
            setField(session, "destroyed", data.destroyed);

            return session;
        } catch (Exception e) {
            throw new RuntimeException("反序列化会话失败", e);
        }
    }

    /**
     * 使用反射设置 Session 的私有字段。
     *
     * @param session   会话对象
     * @param fieldName 字段名
     * @param value     字段值
     */
    private static void setField(Session session, String fieldName, Object value) {
        try {
            Field field = Session.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(session, value);
        } catch (Exception e) {
            throw new RuntimeException("反射设置字段失败: " + fieldName, e);
        }
    }

    /**
     * 加密 SM4 密钥。
     *
     * <p>使用 SM4-GCM 加密 SM4 密钥。若未配置本地加密密钥，则只进行 Base64 编码。
     *
     * @param session 会话对象
     * @return Base64 编码的加密数据
     */
    private String encryptSm4Key(Session session) {
        byte[] keyBytes = session.getSm4KeyCopy();
        try {
            if (encryptionKey == null) {
                // 无本地密钥时，仅做 Base64 编码
                return Base64.getEncoder().encodeToString(keyBytes);
            }

            // SM4-GCM 加密
            GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            AEADParameters params = new AEADParameters(
                    new KeyParameter(encryptionKey), GCM_TAG_LENGTH, iv, null);
            cipher.init(true, params);
            byte[] output = new byte[cipher.getOutputSize(keyBytes.length)];
            int len = cipher.processBytes(keyBytes, 0, keyBytes.length, output, 0);
            cipher.doFinal(output, len);

            // 格式：IV（12字节） + 密文 + TAG（16字节）
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + output.length);
            buffer.put(iv);
            buffer.put(output);
            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            log.error("sm4Key SM4-GCM 加密失败，使用 Base64 编码降级", e);
            return Base64.getEncoder().encodeToString(keyBytes);
        } finally {
            Session.clearKeyCopy(keyBytes);
        }
    }

    /**
     * 解密 SM4 密钥。
     *
     * <p>使用 SM4-GCM 解密。若未配置本地加密密钥，则只进行 Base64 解码。
     *
     * @param encrypted Base64 编码的加密数据
     * @return 解密后的 SM4 密钥字节数组
     */
    private byte[] decryptSm4Key(String encrypted) {
        try {
            byte[] decoded = Base64.getDecoder().decode(encrypted);

            if (encryptionKey == null) {
                // 无本地密钥时，直接返回 Base64 解码结果
                return decoded;
            }

            // 格式：IV（12字节） + 密文 + TAG（16字节）
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);

            GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
            AEADParameters params = new AEADParameters(
                    new KeyParameter(encryptionKey), GCM_TAG_LENGTH, iv, null);
            cipher.init(false, params);
            byte[] output = new byte[cipher.getOutputSize(ciphertext.length)];
            int len = cipher.processBytes(ciphertext, 0, ciphertext.length, output, 0);
            cipher.doFinal(output, len);
            return output;
        } catch (Exception e) {
            throw new RuntimeException("sm4Key SM4-GCM 解密失败", e);
        }
    }

    /**
     * 从输入密钥材料派生 SM4 密钥。
     *
     * <p>使用 SHA-256 对输入取哈希后取前 16 字节作为 SM4 密钥。
     *
     * @param input 输入密钥材料
     * @return 16 字节的 SM4 密钥
     */
    private static byte[] deriveSm4Key(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(input);
            byte[] key = new byte[16];
            System.arraycopy(hash, 0, key, 0, 16);
            return key;
        } catch (Exception e) {
            throw new RuntimeException("无法派生 SM4 密钥", e);
        }
    }

    /**
     * 会话 JSON 序列化数据类。
     *
     * <p>用于 Session 与 JSON 之间的相互转换。sm4Key 字段存储经过加密或 Base64 编码后的字符串，
     * sm4Iv 字段存储 Base64 编码后的字符串。
     */
    static class SessionData {

        String sessionId;
        String clientId;
        String peerId;
        String sm4Key;
        String sm4Iv;
        long createTime;
        long lastAccessTime;
        long maxLifetime;
        int maxRequests;
        int requestCount;
        int rekeyVersion;
        boolean destroyed;

        /**
         * 无参构造器（反序列化需要）。
         */
        SessionData() {
        }

        // ===== 以下 getter/setter 供 Hutool JSON 序列化/反序列化使用 =====

        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public String getPeerId() { return peerId; }
        public void setPeerId(String peerId) { this.peerId = peerId; }

        public String getSm4Key() { return sm4Key; }
        public void setSm4Key(String sm4Key) { this.sm4Key = sm4Key; }

        public String getSm4Iv() { return sm4Iv; }
        public void setSm4Iv(String sm4Iv) { this.sm4Iv = sm4Iv; }

        public long getCreateTime() { return createTime; }
        public void setCreateTime(long createTime) { this.createTime = createTime; }

        public long getLastAccessTime() { return lastAccessTime; }
        public void setLastAccessTime(long lastAccessTime) { this.lastAccessTime = lastAccessTime; }

        public long getMaxLifetime() { return maxLifetime; }
        public void setMaxLifetime(long maxLifetime) { this.maxLifetime = maxLifetime; }

        public int getMaxRequests() { return maxRequests; }
        public void setMaxRequests(int maxRequests) { this.maxRequests = maxRequests; }

        public int getRequestCount() { return requestCount; }
        public void setRequestCount(int requestCount) { this.requestCount = requestCount; }

        public int getRekeyVersion() { return rekeyVersion; }
        public void setRekeyVersion(int rekeyVersion) { this.rekeyVersion = rekeyVersion; }

        public boolean isDestroyed() { return destroyed; }
        public void setDestroyed(boolean destroyed) { this.destroyed = destroyed; }
    }
}
