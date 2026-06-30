package com.sm2sdk.core.session.impl;

import com.sm2sdk.core.model.Sm2SdkConfig;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.impl.RedisSessionStore.RedisOperations;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link RedisSessionStore} 的单元测试。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>基本存取（put / get）</li>
 *   <li>删除（remove）</li>
 *   <li>存在性检查（exists）</li>
 *   <li>会话续期（renew）</li>
 *   <li>TTL 同步</li>
 *   <li>Key 格式验证</li>
 *   <li>SM4 密钥加密保护</li>
 *   <li>Redis 不可用降级</li>
 *   <li>序列化反序列化完整性</li>
 * </ul>
 */
@DisplayName("RedisSessionStore")
class RedisSessionStoreTest {

    private static final String KEY_PREFIX = "test";
    private static final long SESSION_TIMEOUT_MS = 300_000L;
    private static final long MAX_LIFETIME_MS = 3_600_000L;
    private static final int MAX_REQUESTS = 1000;

    private Sm2SdkConfig config;
    private RedisOperations redisOps;
    private RedisSessionStore store;

    @BeforeEach
    void setUp() {
        config = new Sm2SdkConfig()
                .withRedisKeyPrefix(KEY_PREFIX)
                .withSessionTimeoutMs(SESSION_TIMEOUT_MS)
                .withMaxSessionLifetimeMs(MAX_LIFETIME_MS)
                .withMaxSessionRequests(MAX_REQUESTS);
        redisOps = mock(RedisOperations.class);
        store = new RedisSessionStore(config, redisOps);
    }

    // ========== 辅助方法 ==========

    /**
     * 创建测试用会话。
     */
    private static Session createSession(String id) {
        // 使用非全零的密钥材料以区分会话
        byte[] sm4Key = new byte[16];
        byte[] sm4Iv = new byte[12];
        for (int i = 0; i < sm4Key.length; i++) {
            sm4Key[i] = (byte) (id.charAt(i % id.length()) + i);
        }
        for (int i = 0; i < sm4Iv.length; i++) {
            sm4Iv[i] = (byte) (id.charAt(i % id.length()) + i + 1);
        }
        return new Session(
                id,
                "client-" + id,
                "peer-" + id,
                sm4Key, sm4Iv,
                System.currentTimeMillis(),
                MAX_LIFETIME_MS,
                MAX_REQUESTS
        );
    }

    /**
     * 创建使用 HashMap 模拟 Redis 存储的 Mock。
     * put/get/del 操作实际读写该 HashMap。
     */
    private static Map<String, String> createRedisMockMap(RedisOperations redisOps) {
        Map<String, String> map = new HashMap<>();
        doAnswer(invocation -> {
            map.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(redisOps).set(anyString(), anyString(), anyLong());
        when(redisOps.get(anyString()))
                .thenAnswer(invocation -> map.get(invocation.getArgument(0)));
        doAnswer(invocation -> {
            map.remove(invocation.getArgument(0));
            return null;
        }).when(redisOps).del(anyString());
        return map;
    }

    // ==================== put / get ====================

    @Test
    @DisplayName("put 之后 get 应返回字段值相同的会话")
    void putThenGetReturnsSameSession() {
        Map<String, String> redisMap = createRedisMockMap(redisOps);
        Session session = createSession("session-put-get");
        String sessionId = session.getSessionId();

        store.put(session);

        // 验证 Redis key 格式和 TTL
        String expectedKey = KEY_PREFIX + ":session:" + sessionId;
        verify(redisOps).set(eq(expectedKey), anyString(), eq(SESSION_TIMEOUT_MS));

        // 验证能从 Redis 中取出
        Session retrieved = store.get(sessionId);
        assertNotNull(retrieved, "put 后 get 不应返回 null");
        assertEquals(session.getSessionId(), retrieved.getSessionId());
        assertEquals(session.getClientId(), retrieved.getClientId());
        assertEquals(session.getPeerId(), retrieved.getPeerId());
        assertEquals(session.getCreateTime(), retrieved.getCreateTime());
        assertEquals(session.getMaxLifetime(), retrieved.getMaxLifetime());
        assertEquals(session.getMaxRequests(), retrieved.getMaxRequests());
    }

    @Test
    @DisplayName("get 不存在的会话应返回 null")
    void getNonExistentReturnsNull() {
        when(redisOps.get(anyString())).thenReturn(null);

        assertNull(store.get("non-existent"));
    }

    // ==================== remove ====================

    @Test
    @DisplayName("remove 后 get 应返回 null")
    void removeThenGetReturnsNull() {
        Map<String, String> redisMap = createRedisMockMap(redisOps);
        Session session = createSession("session-remove");
        store.put(session);

        // 确认存在
        assertNotNull(store.get(session.getSessionId()));

        // 执行删除
        store.remove(session.getSessionId());

        // 验证 Redis del 调用
        String expectedKey = KEY_PREFIX + ":session:" + session.getSessionId();
        verify(redisOps).del(expectedKey);

        // 验证已删除
        assertNull(store.get(session.getSessionId()));
    }

    @Test
    @DisplayName("remove 不存在的会话不应抛出异常")
    void removeNonExistentDoesNotThrow() {
        assertDoesNotThrow(() -> store.remove("non-existent"));
    }

    // ==================== exists ====================

    @Test
    @DisplayName("存在的会话返回 true，不存在的返回 false")
    void existsReturnsCorrectly() {
        Map<String, String> redisMap = createRedisMockMap(redisOps);
        Session session = createSession("session-exists");
        store.put(session);

        assertTrue(store.exists(session.getSessionId()),
                "已 put 的会话应返回 true");
        assertFalse(store.exists("non-existent"),
                "不存在的会话应返回 false");

        store.remove(session.getSessionId());
        assertFalse(store.exists(session.getSessionId()),
                "remove 后的会话应返回 false");
    }

    // ==================== renew ====================

    @Test
    @DisplayName("renew 应更新 lastAccessTime 并重置 requestCount")
    void renewUpdatesAccessTimeAndResetsRequestCount() {
        Map<String, String> redisMap = createRedisMockMap(redisOps);
        Session session = createSession("session-renew");
        session.touch();
        session.touch();
        assertTrue(session.getRequestCount() >= 2, "touch 后 requestCount 应递增");

        store.put(session);

        // 记录续期前状态
        long lastAccessBefore = session.getLastAccessTime();

        // 执行续期
        store.renew(session.getSessionId());

        // 验证续期后 key 被重新 set（刷新 TTL）
        String expectedKey = KEY_PREFIX + ":session:" + session.getSessionId();
        verify(redisOps, times(2)).set(eq(expectedKey), anyString(), eq(SESSION_TIMEOUT_MS));

        // 从 Redis 取回验证
        Session retrieved = store.get(session.getSessionId());
        assertNotNull(retrieved);
        assertEquals(0, retrieved.getRequestCount(), "renew 后 requestCount 应重置为 0");
        assertTrue(retrieved.getLastAccessTime() >= lastAccessBefore,
                "renew 后 lastAccessTime 应更新");
    }

    @Test
    @DisplayName("renew 不存在的会话不应抛出异常")
    void renewNonExistentDoesNotThrow() {
        when(redisOps.get(anyString())).thenReturn(null);
        assertDoesNotThrow(() -> store.renew("non-existent"));
    }

    // ==================== TTL ====================

    @Test
    @DisplayName("put 时应将 TTL 设置为 sessionTimeoutMs")
    void ttlIsSetOnPut() {
        Session session = createSession("session-ttl");
        store.put(session);

        String expectedKey = KEY_PREFIX + ":session:" + session.getSessionId();
        verify(redisOps).set(eq(expectedKey), anyString(), eq(SESSION_TIMEOUT_MS));
    }

    @Test
    @DisplayName("renew 后应重新设置 TTL")
    void ttlIsResetOnRenew() {
        Map<String, String> redisMap = createRedisMockMap(redisOps);
        Session session = createSession("session-ttl-renew");
        store.put(session);

        // 模拟续期
        store.renew(session.getSessionId());

        // 验证 TTL 被重置（共两次 set：put + renew）
        String expectedKey = KEY_PREFIX + ":session:" + session.getSessionId();
        verify(redisOps, times(2)).set(eq(expectedKey), anyString(), eq(SESSION_TIMEOUT_MS));
    }

    // ==================== Key 格式 ====================

    @Test
    @DisplayName("Redis key 格式应为 {prefix}:session:{sessionId}")
    void keyFormatIsCorrect() {
        Session session = createSession("session-keyfmt");

        // 捕获实际调用的 key
        final String[] capturedKey = new String[1];
        doAnswer(invocation -> {
            capturedKey[0] = invocation.getArgument(0);
            return null;
        }).when(redisOps).set(anyString(), anyString(), anyLong());

        store.put(session);

        String expectedKey = KEY_PREFIX + ":session:" + session.getSessionId();
        assertEquals(expectedKey, capturedKey[0],
                "Redis key 格式应为 {prefix}:session:{sessionId}");
    }

    @Test
    @DisplayName("自定义 redisKeyPrefix 应正确反映在 key 中")
    void customPrefixReflectedInKey() {
        String customPrefix = "myapp";
        Sm2SdkConfig customConfig = new Sm2SdkConfig()
                .withRedisKeyPrefix(customPrefix)
                .withSessionTimeoutMs(SESSION_TIMEOUT_MS);
        RedisSessionStore customStore = new RedisSessionStore(customConfig, redisOps);

        Session session = createSession("session-custom");

        final String[] capturedKey = new String[1];
        doAnswer(invocation -> {
            capturedKey[0] = invocation.getArgument(0);
            return null;
        }).when(redisOps).set(anyString(), anyString(), anyLong());

        customStore.put(session);

        String expectedKey = customPrefix + ":session:" + session.getSessionId();
        assertEquals(expectedKey, capturedKey[0],
                "自定义 prefix 应反映在 key 中");
    }

    // ==================== SM4 密钥加密 ====================

    @Test
    @DisplayName("未配置 localSecretKey 时，sm4Key 以 Base64 编码存储在 JSON 中")
    void sm4KeyBase64EncodedWithoutLocalKey() {
        Map<String, String> redisMap = createRedisMockMap(redisOps);
        Session session = createSession("session-base64");

        store.put(session);

        // 获取存入 Redis 的 JSON
        String storedJson = redisMap.get(KEY_PREFIX + ":session:" + session.getSessionId());
        assertNotNull(storedJson, "Redis 中应有存储的 JSON");

        // 验证 JSON 中包含 sm4Key 字段
        assertTrue(storedJson.contains("sm4Key"), "JSON 中应包含 sm4Key 字段");

        // 验证通过 Base64 解码后能得到原始密钥
        byte[] rawKey = session.getSm4KeyCopy();
        String expectedBase64 = Base64.getEncoder().encodeToString(rawKey);
        assertTrue(storedJson.contains("\"" + expectedBase64 + "\""),
                "JSON 中的 sm4Key 应为原始密钥的 Base64 编码（无加密时）");
        Session.clearKeyCopy(rawKey);
    }

    @Test
    @DisplayName("配置 localSecretKey 后，sm4Key 在 JSON 中为加密密文而非明文 Base64")
    void sm4KeyEncryptedWithLocalKey() {
        // 准备一个 Base64 编码的 32 字节密钥
        byte[] rawKey = new byte[32];
        for (int i = 0; i < rawKey.length; i++) {
            rawKey[i] = (byte) i;
        }
        String secretKeyB64 = Base64.getEncoder().encodeToString(rawKey);

        Sm2SdkConfig secureConfig = new Sm2SdkConfig()
                .withRedisKeyPrefix(KEY_PREFIX)
                .withSessionTimeoutMs(SESSION_TIMEOUT_MS)
                .withLocalSecretKey(secretKeyB64);

        RedisSessionStore secureStore = new RedisSessionStore(secureConfig, redisOps);
        Map<String, String> redisMap = createRedisMockMap(redisOps);

        Session session = createSession("session-encrypted");
        byte[] originalKey = session.getSm4KeyCopy();
        String originalKeyB64 = Base64.getEncoder().encodeToString(originalKey);

        secureStore.put(session);

        // 获取存入 Redis 的 JSON
        String storedJson = redisMap.get(KEY_PREFIX + ":session:" + session.getSessionId());
        assertNotNull(storedJson);

        // 验证 JSON 中的 sm4Key 不是原始密钥的 Base64（说明被加密了）
        assertFalse(storedJson.contains("\"" + originalKeyB64 + "\""),
                "加密模式下 sm4Key 不应包含原始密钥的 Base64");

        // 验证能正确解密取回
        Session retrieved = secureStore.get(session.getSessionId());
        assertNotNull(retrieved);
        byte[] retrievedKey = retrieved.getSm4KeyCopy();
        assertArrayEquals(originalKey, retrievedKey, "解密后的 sm4Key 应与原始密钥一致");
        Session.clearKeyCopy(originalKey);
        Session.clearKeyCopy(retrievedKey);
    }

    // ==================== Redis 不可用降级 ====================

    @Test
    @DisplayName("Redis 不可用时 get 降级到本地缓存返回结果")
    void getDegradesToLocalCacheWhenRedisUnavailable() {
        // Mock 所有 Redis 操作抛出异常，确保 put/get 均降级到本地缓存
        when(redisOps.get(anyString())).thenThrow(new RuntimeException("Redis 连接失败"));
        doThrow(new RuntimeException("Redis 连接失败")).when(redisOps).set(anyString(), anyString(), anyLong());

        Session session = createSession("session-degrade");
        // put 降级到本地缓存
        store.put(session);

        // get 也从本地缓存取
        Session retrieved = store.get(session.getSessionId());
        assertNotNull(retrieved, "Redis 不可用时 get 应从本地缓存返回");
        assertEquals(session.getSessionId(), retrieved.getSessionId());
    }

    @Test
    @DisplayName("Redis 不可用时 exists 降级到本地缓存")
    void existsDegradesToLocalCacheWhenRedisUnavailable() {
        // Mock 所有 Redis 操作抛出异常
        when(redisOps.get(anyString())).thenThrow(new RuntimeException("Redis 连接失败"));
        doThrow(new RuntimeException("Redis 连接失败")).when(redisOps).set(anyString(), anyString(), anyLong());

        Session session = createSession("session-exists-degrade");
        store.put(session);

        assertTrue(store.exists(session.getSessionId()),
                "Redis 不可用时 exists 应从本地缓存判断");
        assertFalse(store.exists("non-existent"),
                "Redis 不可用时也不存在的会话应返回 false");
    }

    @Test
    @DisplayName("Redis 不可用时 remove 降级到本地缓存")
    void removeDegradesToLocalCacheWhenRedisUnavailable() {
        doThrow(new RuntimeException("Redis 连接失败")).when(redisOps).del(anyString());

        Session session = createSession("session-remove-degrade");
        store.put(session);

        // remove 不应抛出异常（降级处理）
        assertDoesNotThrow(() -> store.remove(session.getSessionId()));
    }

    @Test
    @DisplayName("Redis 不可用时 renew 降级到本地缓存")
    void renewDegradesToLocalCacheWhenRedisUnavailable() {
        when(redisOps.get(anyString())).thenThrow(new RuntimeException("Redis 连接失败"));

        Session session = createSession("session-renew-degrade");
        store.put(session);

        // renew 不应抛出异常（降级处理）
        assertDoesNotThrow(() -> store.renew(session.getSessionId()));
    }

    // ==================== 序列化/反序列化完整性 ====================

    @Test
    @DisplayName("会话经过序列化与反序列化后所有字段应保持一致")
    void serializationRoundTripPreservesAllFields() {
        Map<String, String> redisMap = createRedisMockMap(redisOps);
        Session session = createSession("session-roundtrip");

        // 模拟一些访问和续期操作
        session.touch();
        session.touch();
        session.touch();
        session.renew(); // 重置 requestCount
        session.touch(); // 再增加一次

        store.put(session);

        Session retrieved = store.get(session.getSessionId());
        assertNotNull(retrieved);

        // 验证基础字段
        assertEquals(session.getSessionId(), retrieved.getSessionId());
        assertEquals(session.getClientId(), retrieved.getClientId());
        assertEquals(session.getPeerId(), retrieved.getPeerId());
        assertEquals(session.getCreateTime(), retrieved.getCreateTime());
        assertEquals(session.getMaxLifetime(), retrieved.getMaxLifetime());
        assertEquals(session.getMaxRequests(), retrieved.getMaxRequests());

        // 验证 SM4 密钥材料
        byte[] originalKey = session.getSm4KeyCopy();
        byte[] retrievedKey = retrieved.getSm4KeyCopy();
        assertArrayEquals(originalKey, retrievedKey, "sm4Key 应保持一致");
        Session.clearKeyCopy(originalKey);
        Session.clearKeyCopy(retrievedKey);

        byte[] originalIv = session.getSm4IvCopy();
        byte[] retrievedIv = retrieved.getSm4IvCopy();
        assertArrayEquals(originalIv, retrievedIv, "sm4Iv 应保持一致");
        Session.clearKeyCopy(originalIv);
        Session.clearKeyCopy(retrievedIv);

        // 验证 volatile 字段（通过反射恢复）
        assertEquals(session.getRequestCount(), retrieved.getRequestCount(),
                "requestCount 应保持一致");
        assertEquals(session.getLastAccessTime(), retrieved.getLastAccessTime(),
                "lastAccessTime 应保持一致");
        assertEquals(session.getRekeyVersion(), retrieved.getRekeyVersion(),
                "rekeyVersion 应保持一致");
        assertEquals(session.isDestroyed(), retrieved.isDestroyed(),
                "destroyed 应保持一致");
    }

    @Test
    @DisplayName("序列化 JSON 应包含所有必要字段")
    void serializedJsonContainsAllFields() {
        Map<String, String> redisMap = createRedisMockMap(redisOps);
        Session session = createSession("session-json-fields");
        store.put(session);

        String storedJson = redisMap.get(KEY_PREFIX + ":session:" + session.getSessionId());
        assertNotNull(storedJson);

        // 验证 JSON 包含所有字段
        assertTrue(storedJson.contains("\"sessionId\""), "JSON 应包含 sessionId");
        assertTrue(storedJson.contains("\"clientId\""), "JSON 应包含 clientId");
        assertTrue(storedJson.contains("\"peerId\""), "JSON 应包含 peerId");
        assertTrue(storedJson.contains("\"sm4Key\""), "JSON 应包含 sm4Key");
        assertTrue(storedJson.contains("\"sm4Iv\""), "JSON 应包含 sm4Iv");
        assertTrue(storedJson.contains("\"createTime\""), "JSON 应包含 createTime");
        assertTrue(storedJson.contains("\"lastAccessTime\""), "JSON 应包含 lastAccessTime");
        assertTrue(storedJson.contains("\"maxLifetime\""), "JSON 应包含 maxLifetime");
        assertTrue(storedJson.contains("\"maxRequests\""), "JSON 应包含 maxRequests");
        assertTrue(storedJson.contains("\"requestCount\""), "JSON 应包含 requestCount");
        assertTrue(storedJson.contains("\"rekeyVersion\""), "JSON 应包含 rekeyVersion");
        assertTrue(storedJson.contains("\"destroyed\""), "JSON 应包含 destroyed");
    }

    // ==================== 边界情况 ====================

    @Test
    @DisplayName("null 前缀应使用默认值 'sm2'")
    void nullPrefixUsesDefault() {
        Sm2SdkConfig defaultConfig = new Sm2SdkConfig()
                .withRedisKeyPrefix(null)
                .withSessionTimeoutMs(SESSION_TIMEOUT_MS);
        RedisSessionStore defaultStore = new RedisSessionStore(defaultConfig, redisOps);

        Session session = createSession("session-default");

        final String[] capturedKey = new String[1];
        doAnswer(invocation -> {
            capturedKey[0] = invocation.getArgument(0);
            return null;
        }).when(redisOps).set(anyString(), anyString(), anyLong());

        defaultStore.put(session);

        String expectedKey = "sm2:session:" + session.getSessionId();
        assertEquals(expectedKey, capturedKey[0],
                "null prefix 时应使用默认值 sm2");
    }

    @Test
    @DisplayName("连续的 put/get 操作互不干扰")
    void multipleSessionsDoNotInterfere() {
        Map<String, String> redisMap = createRedisMockMap(redisOps);

        Session sessionA = createSession("session-a");
        Session sessionB = createSession("session-b");

        store.put(sessionA);
        store.put(sessionB);

        Session retrievedA = store.get("session-a");
        Session retrievedB = store.get("session-b");

        assertNotNull(retrievedA);
        assertNotNull(retrievedB);
        assertEquals("session-a", retrievedA.getSessionId());
        assertEquals("session-b", retrievedB.getSessionId());

        // 删除 A 不影响 B
        store.remove("session-a");
        assertNull(store.get("session-a"));
        assertNotNull(store.get("session-b"));
    }
}
