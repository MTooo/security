package com.sm2sdk.core.crypto.impl;

import com.sm2sdk.core.crypto.Sm4Crypto;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import org.bouncycastle.crypto.engines.SM4Engine;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import java.security.SecureRandom;

/**
 * 基于 BouncyCastle 轻量级 API 的 SM4-GCM 加解密实现。
 *
 * <p>使用 BC 的 {@link SM4Engine} + {@link GCMBlockCipher}，
 * 完全不依赖 JCE（{@code javax.crypto.Cipher}），因此：
 * <ul>
 *   <li>无需注册 BC 为 JCE Provider</li>
 *   <li>不受 JCE Provider JAR 签名验证影响</li>
 *   <li>shade 打包后可直接使用</li>
 * </ul>
 *
 * <p>密文格式为 {@code IV(12) || ciphertext || TAG(16)}。
 */
public class HutoolSm4Crypto implements Sm4Crypto {

    private static final int GCM_IV_LEN = 12;
    private static final int GCM_TAG_LEN = 16;
    private static final int GCM_TAG_LEN_BITS = GCM_TAG_LEN * 8;

    private final SecureRandom secureRandom;

    public HutoolSm4Crypto() {
        this.secureRandom = new SecureRandom();
    }

    @Override
    public byte[] encrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext) throws Sm2SdkException {
        byte[] actualIv = iv;
        if (actualIv == null) {
            actualIv = new byte[GCM_IV_LEN];
            secureRandom.nextBytes(actualIv);
        }

        try {
            GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
            AEADParameters params = new AEADParameters(
                    new KeyParameter(key), GCM_TAG_LEN_BITS, actualIv, aad);
            cipher.init(true, params);

            byte[] output = new byte[cipher.getOutputSize(plaintext.length)];
            int len = cipher.processBytes(plaintext, 0, plaintext.length, output, 0);
            cipher.doFinal(output, len);

            byte[] result = new byte[GCM_IV_LEN + output.length];
            System.arraycopy(actualIv, 0, result, 0, GCM_IV_LEN);
            System.arraycopy(output, 0, result, GCM_IV_LEN, output.length);
            return result;

        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.SM4_ENCRYPT_FAILED,
                    "SM4加密失败: " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] decrypt(byte[] key, byte[] iv, byte[] aad, byte[] ciphertextWithTag) throws Sm2SdkException {
        if (ciphertextWithTag == null || ciphertextWithTag.length < GCM_IV_LEN + GCM_TAG_LEN) {
            throw new Sm2SdkException(ErrorCode.SM4_DECRYPT_TAG_FAILED,
                    "SM4解密失败 - 密文长度不足，最少需要 " + (GCM_IV_LEN + GCM_TAG_LEN) + " 字节");
        }

        try {
            byte[] actualIv;
            byte[] ciphertextAndTag;

            if (iv != null) {
                actualIv = iv;
                ciphertextAndTag = new byte[ciphertextWithTag.length - GCM_IV_LEN];
                System.arraycopy(ciphertextWithTag, GCM_IV_LEN, ciphertextAndTag, 0, ciphertextAndTag.length);
            } else {
                actualIv = new byte[GCM_IV_LEN];
                System.arraycopy(ciphertextWithTag, 0, actualIv, 0, GCM_IV_LEN);
                ciphertextAndTag = new byte[ciphertextWithTag.length - GCM_IV_LEN];
                System.arraycopy(ciphertextWithTag, GCM_IV_LEN, ciphertextAndTag, 0, ciphertextAndTag.length);
            }

            GCMBlockCipher cipher = new GCMBlockCipher(new SM4Engine());
            AEADParameters params = new AEADParameters(
                    new KeyParameter(key), GCM_TAG_LEN_BITS, actualIv, aad);
            cipher.init(false, params);

            byte[] output = new byte[cipher.getOutputSize(ciphertextAndTag.length)];
            int len = cipher.processBytes(ciphertextAndTag, 0, ciphertextAndTag.length, output, 0);
            cipher.doFinal(output, len);
            return output;

        } catch (Sm2SdkException e) {
            throw e;
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.SM4_DECRYPT_TAG_FAILED,
                    "SM4解密失败 - TAG校验失败: " + e.getMessage(), e);
        }
    }
}
