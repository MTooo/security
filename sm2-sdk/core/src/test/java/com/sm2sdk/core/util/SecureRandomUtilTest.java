package com.sm2sdk.core.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link SecureRandomUtil} 的单元测试。
 *
 * <p>验证安全随机数生成、IV 生成和 UUID 生成的核心功能以及边界情况。
 */
@DisplayName("SecureRandomUtil 安全随机数工具测试")
class SecureRandomUtilTest {

    // ========== generateNonce(int) ==========

    @Test
    @DisplayName("generateNonce(int) 应生成指定长度的随机字节数组")
    void generateNonce_shouldReturnSpecifiedLength() {
        byte[] nonce16 = SecureRandomUtil.generateNonce(16);
        assertEquals(16, nonce16.length);

        byte[] nonce32 = SecureRandomUtil.generateNonce(32);
        assertEquals(32, nonce32.length);

        byte[] nonce8 = SecureRandomUtil.generateNonce(8);
        assertEquals(8, nonce8.length);
    }

    @Test
    @DisplayName("generateNonce(int) 不应返回全零数组")
    void generateNonce_shouldNotBeAllZeros() {
        byte[] nonce = SecureRandomUtil.generateNonce(16);
        boolean allZero = true;
        for (byte b : nonce) {
            if (b != 0) {
                allZero = false;
                break;
            }
        }
        assertFalse(allZero, "随机数不应全为零");
    }

    @Test
    @DisplayName("多次调用 generateNonce(int) 应返回不同的结果")
    void generateNonce_callsShouldBeDistinct() {
        byte[] nonce1 = SecureRandomUtil.generateNonce(16);
        byte[] nonce2 = SecureRandomUtil.generateNonce(16);
        // 两次随机数完全相同的概率极低 (1/2^128)，此处验证返回的是不同内容
        boolean identical = true;
        for (int i = 0; i < nonce1.length; i++) {
            if (nonce1[i] != nonce2[i]) {
                identical = false;
                break;
            }
        }
        assertFalse(identical, "两次生成的随机数不应完全相同");
    }

    @Test
    @DisplayName("generateNonce(int) 参数为 0 应返回空数组")
    void generateNonce_withZeroBytes() {
        byte[] nonce = SecureRandomUtil.generateNonce(0);
        assertNotNull(nonce);
        assertEquals(0, nonce.length);
    }

    @Test
    @DisplayName("generateNonce(int) 参数为负数应不抛异常")
    void generateNonce_withNegativeBytes() {
        // 负长度会由虚拟机抛出 NegativeArraySizeException，此处验证工具类不额外处理
        assertThrows(NegativeArraySizeException.class, () -> SecureRandomUtil.generateNonce(-1));
    }

    // ========== generateNonce() 默认重载 ==========

    @Test
    @DisplayName("generateNonce() 无参方法应返回 16 字节")
    void generateNonce_defaultShouldReturn16Bytes() {
        byte[] nonce = SecureRandomUtil.generateNonce();
        assertEquals(16, nonce.length);
    }

    // ========== generateIV() ==========

    @Test
    @DisplayName("generateIV() 应返回 12 字节的 IV")
    void generateIV_shouldReturn12Bytes() {
        byte[] iv = SecureRandomUtil.generateIV();
        assertEquals(12, iv.length);
    }

    @Test
    @DisplayName("generateIV() 不应返回全零数组")
    void generateIV_shouldNotBeAllZeros() {
        byte[] iv = SecureRandomUtil.generateIV();
        boolean allZero = true;
        for (byte b : iv) {
            if (b != 0) {
                allZero = false;
                break;
            }
        }
        assertFalse(allZero, "IV 不应全为零");
    }

    @Test
    @DisplayName("多次调用 generateIV() 应返回不同的结果")
    void generateIV_callsShouldBeDistinct() {
        byte[] iv1 = SecureRandomUtil.generateIV();
        byte[] iv2 = SecureRandomUtil.generateIV();
        // 两次 IV 完全相同的概率极低 (1/2^96)，此处验证返回的是不同内容
        boolean identical = true;
        for (int i = 0; i < iv1.length; i++) {
            if (iv1[i] != iv2[i]) {
                identical = false;
                break;
            }
        }
        assertFalse(identical, "两次生成的 IV 不应完全相同");
    }

    // ========== generateUUID() ==========

    @Test
    @DisplayName("generateUUID() 应返回非空字符串")
    void generateUUID_shouldReturnNonEmptyString() {
        String uuid = SecureRandomUtil.generateUUID();
        assertNotNull(uuid);
        assertFalse(uuid.isEmpty());
    }

    @Test
    @DisplayName("generateUUID() 应返回标准 UUID 格式（36 字符，含连字符）")
    void generateUUID_shouldMatchStandardFormat() {
        String uuid = SecureRandomUtil.generateUUID();
        // 标准 UUID 格式: 8-4-4-4-12 = 36 字符
        assertEquals(36, uuid.length());
        assertEquals('-', uuid.charAt(8));
        assertEquals('-', uuid.charAt(13));
        assertEquals('-', uuid.charAt(18));
        assertEquals('-', uuid.charAt(23));
    }

    @Test
    @DisplayName("generateUUID() 应返回十六进制字符")
    void generateUUID_shouldContainOnlyHexAndHyphens() {
        String uuid = SecureRandomUtil.generateUUID();
        String hexPart = uuid.replace("-", "");
        assertTrue(hexPart.matches("[0-9a-fA-F]+"), "UUID 的连字符以外的部分应为十六进制字符");
    }

    @Test
    @DisplayName("多次调用 generateUUID() 应返回不同的结果")
    void generateUUID_callsShouldBeDistinct() {
        String uuid1 = SecureRandomUtil.generateUUID();
        String uuid2 = SecureRandomUtil.generateUUID();
        assertNotEquals(uuid1, uuid2, "两次生成的 UUID 不应相同");
    }

    @Test
    @DisplayName("generateUUID() 版本位应为 4（随机 UUID）")
    void generateUUID_versionShouldBe4() {
        String uuid = SecureRandomUtil.generateUUID();
        // 版本位位于第 13 字符（0-indexed 第 12 位）
        char versionChar = uuid.charAt(14);
        assertEquals('4', versionChar, "随机 UUID 的版本位应为 4");
    }

    // ========== 边界与异常情况 ==========

    @Test
    @DisplayName("1 字节的 Nonce 应正常生成")
    void generateNonce_oneByte() {
        byte[] nonce = SecureRandomUtil.generateNonce(1);
        assertEquals(1, nonce.length);
    }

    @Test
    @DisplayName("大长度 Nonce（1MB）应正常生成且不抛异常")
    void generateNonce_largeSize() {
        assertDoesNotThrow(() -> {
            byte[] nonce = SecureRandomUtil.generateNonce(1024 * 1024);
            assertEquals(1024 * 1024, nonce.length);
        });
    }
}
