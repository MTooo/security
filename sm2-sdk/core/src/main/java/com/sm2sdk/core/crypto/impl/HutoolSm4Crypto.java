package com.sm2sdk.core.crypto.impl;

import cn.hutool.crypto.symmetric.SM4;
import com.sm2sdk.core.crypto.Sm4Crypto;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

/**
 * 基于 Hutool {@link SM4} 的 GCM 模式加解密实现。
 *
 * <p>密文格式为 {@code IV(12) || ciphertext || TAG(16)}。
 * 每次加解密操作均创建独立的 Cipher 实例，故实现是线程安全的。
 *
 * <p>内部使用 Hutool 的 SM4 类创建 "SM4/GCM/NoPadding" 变换的 Cipher，
 * 并通过 JCE 的 {@link Cipher#updateAAD(byte[])} 支持附加认证数据（AAD）。
 */
public class HutoolSm4Crypto implements Sm4Crypto {

    /** GCM 模式 IV 长度（12 字节）。 */
    private static final int GCM_IV_LEN = 12;

    /** GCM 认证标签长度（16 字节 = 128 位）。 */
    private static final int GCM_TAG_LEN = 16;

    /** GCM 认证标签长度（位），用于 {@link GCMParameterSpec}。 */
    private static final int GCM_TAG_LEN_BITS = GCM_TAG_LEN * 8;

    /** 安全随机数生成器，用于生成随机 IV。 */
    private final SecureRandom secureRandom;

    /**
     * 构造一个新的 {@code HutoolSm4Crypto} 实例。
     */
    public HutoolSm4Crypto() {
        this.secureRandom = new SecureRandom();
    }

    @Override
    public byte[] encrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext) throws Sm2SdkException {
        // 如果 iv 为 null，生成 12 字节随机 IV
        byte[] actualIv = iv;
        if (actualIv == null) {
            actualIv = new byte[GCM_IV_LEN];
            secureRandom.nextBytes(actualIv);
        }

        try {
            // 使用 Hutool SM4 创建 "SM4/GCM/NoPadding" 的 Cipher 实例
            SM4 sm4 = new SM4("GCM", "NoPadding");
            Cipher cipher = sm4.getCipher();

            // 初始化 Cipher 为加密模式
            SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LEN_BITS, actualIv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

            // 设置附加认证数据（AAD）
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }

            // 执行加密，输出为 ciphertext || TAG
            byte[] ciphertextWithTag = cipher.doFinal(plaintext);

            // 拼接 IV || ciphertext || TAG
            byte[] result = new byte[GCM_IV_LEN + ciphertextWithTag.length];
            System.arraycopy(actualIv, 0, result, 0, GCM_IV_LEN);
            System.arraycopy(ciphertextWithTag, 0, result, GCM_IV_LEN, ciphertextWithTag.length);
            return result;

        } catch (Exception e) {
            throw new Sm2SdkException(
                    ErrorCode.SM4_ENCRYPT_FAILED,
                    "SM4加密失败: " + e.getMessage(),
                    e
            );
        }
    }

    @Override
    public byte[] decrypt(byte[] key, byte[] iv, byte[] aad, byte[] ciphertextWithTag) throws Sm2SdkException {
        // 校验输入长度下限：IV(12) + ciphertext(≥0) + TAG(16) = 28
        if (ciphertextWithTag == null || ciphertextWithTag.length < GCM_IV_LEN + GCM_TAG_LEN) {
            throw new Sm2SdkException(
                    ErrorCode.SM4_DECRYPT_TAG_FAILED,
                    "SM4解密失败 - 密文长度不足，最少需要 " + (GCM_IV_LEN + GCM_TAG_LEN) + " 字节"
            );
        }

        try {
            // 解析密文格式: IV(12) || ciphertext || TAG(16)
            byte[] actualIv;
            byte[] ciphertextAndTag;

            if (iv != null) {
                // 使用调用方提供的 IV
                actualIv = iv;
                // ciphertextWithTag = IV(12) || ciphertext || TAG(16)
                // 跳过前 12 字节 IV，取 ciphertext || TAG
                ciphertextAndTag = new byte[ciphertextWithTag.length - GCM_IV_LEN];
                System.arraycopy(ciphertextWithTag, GCM_IV_LEN, ciphertextAndTag, 0, ciphertextAndTag.length);
            } else {
                // 从前 12 字节解析 IV
                actualIv = new byte[GCM_IV_LEN];
                System.arraycopy(ciphertextWithTag, 0, actualIv, 0, GCM_IV_LEN);
                // ciphertextWithTag 余下部分为 ciphertext || TAG
                ciphertextAndTag = new byte[ciphertextWithTag.length - GCM_IV_LEN];
                System.arraycopy(ciphertextWithTag, GCM_IV_LEN, ciphertextAndTag, 0, ciphertextAndTag.length);
            }

            // 使用 Hutool SM4 创建 "SM4/GCM/NoPadding" 的 Cipher 实例
            SM4 sm4 = new SM4("GCM", "NoPadding");
            Cipher cipher = sm4.getCipher();

            // 初始化 Cipher 为解密模式
            SecretKeySpec keySpec = new SecretKeySpec(key, "SM4");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LEN_BITS, actualIv);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

            // 设置附加认证数据（AAD）
            if (aad != null && aad.length > 0) {
                cipher.updateAAD(aad);
            }

            // 执行解密（含 TAG 校验）
            return cipher.doFinal(ciphertextAndTag);

        } catch (Sm2SdkException e) {
            // 已包装的异常直接抛出（如最小长度校验失败）
            throw e;
        } catch (Exception e) {
            // TAG 校验失败或其他解密异常统一包装
            throw new Sm2SdkException(
                    ErrorCode.SM4_DECRYPT_TAG_FAILED,
                    "SM4解密失败 - TAG校验失败: " + e.getMessage(),
                    e
            );
        }
    }
}
