package io.github.mtooo.core.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KeyDerivation} 的单元测试。
 *
 * <p>覆盖确定性行为、输出长度正确性、组件提取、迭代次数、
 * 空输入等边界情况以及已知测试向量。
 */
class KeyDerivationTest {

    @Test
    void testDeterministic() {
        byte[] z = "test-deterministic".getBytes(StandardCharsets.UTF_8);
        byte[] result1 = KeyDerivation.kdf(z, 256);
        byte[] result2 = KeyDerivation.kdf(z, 256);
        assertArrayEquals(result1, result2);
    }

    @Test
    void testDifferentInputsProduceDifferentOutputs() {
        byte[] z1 = "input-alpha".getBytes(StandardCharsets.UTF_8);
        byte[] z2 = "input-beta".getBytes(StandardCharsets.UTF_8);
        byte[] result1 = KeyDerivation.kdf(z1, 256);
        byte[] result2 = KeyDerivation.kdf(z2, 256);
        assertFalse(Arrays.equals(result1, result2));
    }

    @Test
    void testCorrectLengthFor480Bits() {
        byte[] z = "length-check-480".getBytes(StandardCharsets.UTF_8);
        byte[] result = KeyDerivation.kdf(z, 480);
        assertEquals(60, result.length);
    }

    @Test
    void testCorrectLengthFor256Bits() {
        byte[] z = "length-check-256".getBytes(StandardCharsets.UTF_8);
        byte[] result = KeyDerivation.kdf(z, 256);
        assertEquals(32, result.length);
    }

    @Test
    void testExtractSm4KeyReturns16Bytes() {
        byte[] z = "extract-sm4-key".getBytes(StandardCharsets.UTF_8);
        byte[] derived = KeyDerivation.kdf(z, 512);
        byte[] key = KeyDerivation.extractSm4Key(derived);
        assertEquals(16, key.length);
    }

    @Test
    void testExtractSm4IvReturns12Bytes() {
        byte[] z = "extract-sm4-iv".getBytes(StandardCharsets.UTF_8);
        byte[] derived = KeyDerivation.kdf(z, 512);
        byte[] iv = KeyDerivation.extractSm4Iv(derived);
        assertEquals(12, iv.length);
    }

    @Test
    void testExtractSm4KeyCopiesFirst16Bytes() {
        byte[] z = "extract-key-correct".getBytes(StandardCharsets.UTF_8);
        byte[] derived = KeyDerivation.kdf(z, 512);
        byte[] key = KeyDerivation.extractSm4Key(derived);
        for (int i = 0; i < 16; i++) {
            assertEquals(derived[i], key[i]);
        }
    }

    @Test
    void testExtractSm4IvCopiesBytes16to27() {
        byte[] z = "extract-iv-correct".getBytes(StandardCharsets.UTF_8);
        byte[] derived = KeyDerivation.kdf(z, 512);
        byte[] iv = KeyDerivation.extractSm4Iv(derived);
        for (int i = 0; i < 12; i++) {
            assertEquals(derived[16 + i], iv[i]);
        }
    }

    @Test
    void testKdfTwoIterationsFor512Bits() {
        // klen=512 bits => 64 bytes, exactly 2 SM3 hash iterations
        byte[] z = "two-iter".getBytes(StandardCharsets.UTF_8);
        byte[] result = KeyDerivation.kdf(z, 512);
        assertEquals(64, result.length);
    }

    @Test
    void testKdfPartialIterationFor300Bits() {
        // klen=300 bits => ceil(300/8) = 38 bytes, requires 2 iterations
        byte[] z = "partial-iter".getBytes(StandardCharsets.UTF_8);
        byte[] result = KeyDerivation.kdf(z, 300);
        assertEquals(38, result.length);
    }

    @Test
    void testKdfSingleBitKlen() {
        // klen=1 bit => 1 byte
        byte[] z = "single-bit".getBytes(StandardCharsets.UTF_8);
        byte[] result = KeyDerivation.kdf(z, 1);
        assertEquals(1, result.length);
    }

    @Test
    void testKdfWithEmptyInput() {
        byte[] z = new byte[0];
        byte[] result = KeyDerivation.kdf(z, 256);
        assertEquals(32, result.length);
    }

    @Test
    void testKdfWithLargeInput() {
        byte[] z = new byte[1024];
        Arrays.fill(z, (byte) 0xAB);
        byte[] result = KeyDerivation.kdf(z, 256);
        assertEquals(32, result.length);
    }

    @Test
    void testConcat() {
        byte[] a = new byte[]{0x01, 0x02};
        byte[] b = new byte[]{0x03, 0x04, 0x05};
        byte[] result = KeyDerivation.concat(a, b);
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04, 0x05}, result);
    }

    @Test
    void testIntToBytes() {
        assertArrayEquals(new byte[]{0x00, 0x00, 0x00, 0x01}, KeyDerivation.intToBytes(1));
        assertArrayEquals(new byte[]{0x00, 0x00, 0x00, (byte) 0xFF}, KeyDerivation.intToBytes(255));
        assertArrayEquals(new byte[]{(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF},
                KeyDerivation.intToBytes(-1));
    }
}
