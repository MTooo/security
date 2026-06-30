package com.sm2sdk.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Sm2KeyPrefix} 的单元测试。
 *
 * <p>验证所有 Redis Key 构建方法的格式正确性、空值和边界情况。
 */
@DisplayName("Sm2KeyPrefix Redis Key 前缀工具测试")
class Sm2KeyPrefixTest {

    private static final String PREFIX = "sm2";

    // ========== buildSessionKey ==========

    @Test
    @DisplayName("buildSessionKey 应生成正确的格式: {prefix}:session:{sessionId}")
    void buildSessionKey_shouldMatchExpectedFormat() {
        String sessionId = "sess-001";
        String key = Sm2KeyPrefix.buildSessionKey(PREFIX, sessionId);
        assertEquals("sm2:session:sess-001", key);
    }

    @Test
    @DisplayName("buildSessionKey 含空 sessionId 应正常拼接")
    void buildSessionKey_withEmptySessionId() {
        String key = Sm2KeyPrefix.buildSessionKey(PREFIX, "");
        assertEquals("sm2:session:", key);
    }

    @Test
    @DisplayName("buildSessionKey 含 null sessionId 应拼接 \"null\" 字符串")
    void buildSessionKey_withNullSessionId() {
        String key = Sm2KeyPrefix.buildSessionKey(PREFIX, null);
        assertEquals("sm2:session:null", key);
    }

    @Test
    @DisplayName("buildSessionKey 空前缀应正常处理")
    void buildSessionKey_withEmptyPrefix() {
        String key = Sm2KeyPrefix.buildSessionKey("", "sess-001");
        assertEquals(":session:sess-001", key);
    }

    @Test
    @DisplayName("buildSessionKey 含特殊字符的 sessionId 不应转义")
    void buildSessionKey_withSpecialChars() {
        String key = Sm2KeyPrefix.buildSessionKey(PREFIX, "123:456");
        assertEquals("sm2:session:123:456", key);
    }

    // ========== buildNonceKey ==========

    @Test
    @DisplayName("buildNonceKey 应生成正确的格式: {prefix}:nonce:{nonce}")
    void buildNonceKey_shouldMatchExpectedFormat() {
        String nonce = "a1b2c3d4e5f6";
        String key = Sm2KeyPrefix.buildNonceKey(PREFIX, nonce);
        assertEquals("sm2:nonce:a1b2c3d4e5f6", key);
    }

    @Test
    @DisplayName("buildNonceKey 空 nonce 应正常拼接")
    void buildNonceKey_withEmptyNonce() {
        String key = Sm2KeyPrefix.buildNonceKey(PREFIX, "");
        assertEquals("sm2:nonce:", key);
    }

    @Test
    @DisplayName("buildNonceKey 含 null nonce 应拼接 \"null\" 字符串")
    void buildNonceKey_withNullNonce() {
        String key = Sm2KeyPrefix.buildNonceKey(PREFIX, null);
        assertEquals("sm2:nonce:null", key);
    }

    // ========== buildBloomKey ==========

    @Test
    @DisplayName("buildBloomKey 应生成正确的格式: {prefix}:nonce:bf:{minuteTick}")
    void buildBloomKey_shouldMatchExpectedFormat() {
        String minuteTick = "202606301520";
        String key = Sm2KeyPrefix.buildBloomKey(PREFIX, minuteTick);
        assertEquals("sm2:nonce:bf:202606301520", key);
    }

    @Test
    @DisplayName("buildBloomKey 空 minuteTick 应正常拼接")
    void buildBloomKey_withEmptyMinuteTick() {
        String key = Sm2KeyPrefix.buildBloomKey(PREFIX, "");
        assertEquals("sm2:nonce:bf:", key);
    }

    @Test
    @DisplayName("buildBloomKey 不同分钟刻度应生成不同 Key")
    void buildBloomKey_differentTicks() {
        String key1 = Sm2KeyPrefix.buildBloomKey(PREFIX, "202606301520");
        String key2 = Sm2KeyPrefix.buildBloomKey(PREFIX, "202606301521");
        assertNotEquals(key1, key2);
    }

    // ========== buildHandshakeLockKey ==========

    @Test
    @DisplayName("buildHandshakeLockKey 应生成正确的格式: {prefix}:handshake:lock:{clientId}")
    void buildHandshakeLockKey_shouldMatchExpectedFormat() {
        String clientId = "client-abc";
        String key = Sm2KeyPrefix.buildHandshakeLockKey(PREFIX, clientId);
        assertEquals("sm2:handshake:lock:client-abc", key);
    }

    @Test
    @DisplayName("buildHandshakeLockKey 空 clientId 应正常拼接")
    void buildHandshakeLockKey_withEmptyClientId() {
        String key = Sm2KeyPrefix.buildHandshakeLockKey(PREFIX, "");
        assertEquals("sm2:handshake:lock:", key);
    }

    // ========== 不同前缀测试 ==========

    @Test
    @DisplayName("使用不同前缀应生成不同 Key")
    void differentPrefixes_shouldProduceDifferentKeys() {
        String key1 = Sm2KeyPrefix.buildSessionKey("app1", "sess-001");
        String key2 = Sm2KeyPrefix.buildSessionKey("app2", "sess-001");
        assertNotEquals(key1, key2);
    }

    @Test
    @DisplayName("使用自定义业务前缀应正确反映在 Key 中")
    void customPrefix_shouldBeReflectedInKey() {
        String key = Sm2KeyPrefix.buildSessionKey("mybiz", "sess-001");
        assertTrue(key.startsWith("mybiz:"));
    }

    // ========== 结构一致性验证 ==========

    @Test
    @DisplayName("所有 Key 生成方法均使用冒号分隔的层级结构")
    void allKeys_shouldUseColonDelimitedStructure() {
        String sessionKey = Sm2KeyPrefix.buildSessionKey(PREFIX, "id");
        String nonceKey = Sm2KeyPrefix.buildNonceKey(PREFIX, "id");
        String bloomKey = Sm2KeyPrefix.buildBloomKey(PREFIX, "id");
        String lockKey = Sm2KeyPrefix.buildHandshakeLockKey(PREFIX, "id");

        // session 和 nonce 为 2 个冒号分隔 3 段: {prefix}:{type}:{id}
        assertEquals(2, sessionKey.split(":", -1).length - 1,
                "sessionKey 应包含 2 个冒号（3 段）：" + sessionKey);
        assertEquals(2, nonceKey.split(":", -1).length - 1,
                "nonceKey 应包含 2 个冒号（3 段）：" + nonceKey);
        // bloom 和 lock 为 3 个冒号分隔 4 段: {prefix}:{type}:{subtype}:{id}
        assertEquals(3, bloomKey.split(":", -1).length - 1,
                "bloomKey 应包含 3 个冒号（4 段）：" + bloomKey);
        assertEquals(3, lockKey.split(":", -1).length - 1,
                "lockKey 应包含 3 个冒号（4 段）：" + lockKey);
    }
}
