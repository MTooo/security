package com.sm2sdk.core.crypto;

import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;

/**
 * SM4 对称加解密接口。
 *
 * <p>提供使用 SM4 在 GCM 模式（或产生认证标签的认证加密模式）下的加密和解密操作。
 * 密文格式为 {@code IV || ciphertext || TAG}。
 *
 * <p>实现必须保证线程安全。
 */
public interface Sm4Crypto {

    /**
     * 使用 SM4 加密给定的明文。
     *
     * @param key       SM4 加密密钥（必须为 16 字节）
     * @param iv        初始向量（GCM 模式下必须为 12 字节）
     * @param aad       附加认证数据（可为空）
     * @param plaintext 待加密的明文
     * @return 格式为 {@code IV || ciphertext || TAG} 的密文
     * @throws Sm2SdkException 如果加密失败（包装 {@link ErrorCode#SM4_ENCRYPT_FAILED}）
     */
    byte[] encrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext)
            throws Sm2SdkException;

    /**
     * 使用 SM4 解密给定的密文。
     *
     * <p>密文必须符合 {@link #encrypt} 生成的格式，即 {@code IV || ciphertext || TAG}。
     *
     * @param key               SM4 加密密钥（必须为 16 字节）
     * @param iv                加密时使用的初始向量
     * @param aad               附加认证数据（可为空）
     * @param ciphertextWithTag 包含认证标签的完整密文
     * @return 解密后的明文
     * @throws Sm2SdkException 如果认证标签不匹配
     *                         （包装 {@link ErrorCode#SM4_DECRYPT_TAG_FAILED}）
     */
    byte[] decrypt(byte[] key, byte[] iv, byte[] aad, byte[] ciphertextWithTag)
            throws Sm2SdkException;
}
