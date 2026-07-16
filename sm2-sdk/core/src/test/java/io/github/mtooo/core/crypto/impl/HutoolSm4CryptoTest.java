package io.github.mtooo.core.crypto.impl;

import io.github.mtooo.core.crypto.Sm4Crypto;
import io.github.mtooo.core.exception.ErrorCode;
import io.github.mtooo.core.exception.Sm2SdkException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HutoolSm4Crypto} 的单元测试。
 *
 * <p>覆盖加解密轮转、参数校验、随机 IV、空明文和大明文等场景。
 */
class HutoolSm4CryptoTest {

    private Sm4Crypto crypto;
    private SecureRandom secureRandom;

    @BeforeEach
    void setUp() {
        crypto = new HutoolSm4Crypto();
        secureRandom = new SecureRandom();
    }

    @Test
    void testRoundtrip() {
        byte[] key = generateKey();
        byte[] iv = generateIv();
        byte[] aad = "test-aad".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "Hello, SM4-GCM!".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = crypto.encrypt(key, iv, aad, plaintext);
        byte[] decrypted = crypto.decrypt(key, iv, aad, ciphertext);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void testRoundtripWithNullIv() {
        byte[] key = generateKey();
        byte[] aad = "test-aad".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "Hello, SM4-GCM!".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = crypto.encrypt(key, null, aad, plaintext);
        byte[] decrypted = crypto.decrypt(key, null, aad, ciphertext);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void testDecryptWithWrongKeyFails() {
        byte[] key1 = generateKey();
        byte[] key2 = generateKey();
        byte[] iv = generateIv();
        byte[] aad = "test-aad".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "Secret message".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = crypto.encrypt(key1, iv, aad, plaintext);

        Sm2SdkException exception = assertThrows(Sm2SdkException.class,
                () -> crypto.decrypt(key2, iv, aad, ciphertext));
        assertEquals(ErrorCode.SM4_DECRYPT_TAG_FAILED, exception.getErrorCode());
    }

    @Test
    void testDecryptWithWrongAadFails() {
        byte[] key = generateKey();
        byte[] iv = generateIv();
        byte[] aad1 = "correct-aad".getBytes(StandardCharsets.UTF_8);
        byte[] aad2 = "wrong-aad".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "Secret message".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = crypto.encrypt(key, iv, aad1, plaintext);

        Sm2SdkException exception = assertThrows(Sm2SdkException.class,
                () -> crypto.decrypt(key, iv, aad2, ciphertext));
        assertEquals(ErrorCode.SM4_DECRYPT_TAG_FAILED, exception.getErrorCode());
    }

    @Test
    void testEncryptionProducesDifferentResults() {
        byte[] key = generateKey();
        byte[] aad = "test-aad".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = "Same text".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext1 = crypto.encrypt(key, null, aad, plaintext);
        byte[] ciphertext2 = crypto.encrypt(key, null, aad, plaintext);

        // 两次加密结果必须不同（随机 IV）
        assertFalse(Arrays.equals(ciphertext1, ciphertext2));
    }

    @Test
    void testEmptyPlaintext() {
        byte[] key = generateKey();
        byte[] iv = generateIv();
        byte[] aad = "test-aad".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = new byte[0];

        byte[] ciphertext = crypto.encrypt(key, iv, aad, plaintext);
        byte[] decrypted = crypto.decrypt(key, iv, aad, ciphertext);

        assertArrayEquals(plaintext, decrypted);

        // 空明文加密结果长度应为 IV(12) + ciphertext(0) + TAG(16) = 28
        assertEquals(28, ciphertext.length);
    }

    @Test
    void testLargePlaintext() {
        byte[] key = generateKey();
        byte[] iv = generateIv();
        byte[] aad = "test-aad".getBytes(StandardCharsets.UTF_8);
        byte[] plaintext = new byte[10 * 1024]; // 10KB
        secureRandom.nextBytes(plaintext);

        byte[] ciphertext = crypto.encrypt(key, iv, aad, plaintext);
        byte[] decrypted = crypto.decrypt(key, iv, aad, ciphertext);

        assertArrayEquals(plaintext, decrypted);
    }

    @Test
    void testOutputFormat() {
        // 验证输出格式: IV(12) || CIPHERTEXT || TAG(16)
        byte[] key = generateKey();
        byte[] iv = generateIv();
        byte[] plaintext = "Test".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = crypto.encrypt(key, iv, null, plaintext);

        // 前 12 字节应为 IV
        byte[] ivFromOutput = new byte[12];
        System.arraycopy(ciphertext, 0, ivFromOutput, 0, 12);
        assertArrayEquals(iv, ivFromOutput);

        // 总长度 = IV(12) + plaintext长度(4) + TAG(16) = 32
        assertEquals(12 + plaintext.length + 16, ciphertext.length);
    }

    @Test
    void testNullAad() {
        byte[] key = generateKey();
        byte[] iv = generateIv();
        byte[] plaintext = "Test with null AAD".getBytes(StandardCharsets.UTF_8);

        byte[] ciphertext = crypto.encrypt(key, iv, null, plaintext);
        byte[] decrypted = crypto.decrypt(key, iv, null, ciphertext);

        assertArrayEquals(plaintext, decrypted);
    }

    private byte[] generateKey() {
        byte[] key = new byte[16];
        secureRandom.nextBytes(key);
        return key;
    }

    private byte[] generateIv() {
        byte[] iv = new byte[12];
        secureRandom.nextBytes(iv);
        return iv;
    }
}
