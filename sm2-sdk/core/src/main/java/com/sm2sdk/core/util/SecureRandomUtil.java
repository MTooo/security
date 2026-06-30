package com.sm2sdk.core.util;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.UUID;

/**
 * 安全随机数工具类。
 *
 * <p>提供安全随机数、初始化向量（IV）和 UUID 的生成方法。
 * 底层使用 {@link SecureRandom}，优先选择 {@code NativePRNG} 或 {@code SHA1PRNG} 算法。
 *
 * <p>所有生成的随机数均适用于密码学场景。
 */
public final class SecureRandomUtil {

    private static final SecureRandom SECURE_RANDOM = initSecureRandom();

    /** IV 的标准长度（12 字节，适用于 GCM 模式） */
    private static final int IV_LENGTH = 12;

    private SecureRandomUtil() {
        // 工具类，不可实例化
    }

    /**
     * 初始化 SecureRandom 实例，优先使用 NativePRNG，回退至 SHA1PRNG。
     *
     * @return SecureRandom 实例
     */
    private static SecureRandom initSecureRandom() {
        try {
            return SecureRandom.getInstance("NativePRNG");
        } catch (NoSuchAlgorithmException e) {
            try {
                return SecureRandom.getInstance("SHA1PRNG");
            } catch (NoSuchAlgorithmException ex) {
                // 兜底：使用无参构造（默认实现）
                return new SecureRandom();
            }
        }
    }

    /**
     * 生成指定长度的安全随机字节数组（Nonce）。
     *
     * @param bytes 随机数的字节长度
     * @return 包含安全随机数据的字节数组
     */
    public static byte[] generateNonce(int bytes) {
        byte[] nonce = new byte[bytes];
        SECURE_RANDOM.nextBytes(nonce);
        return nonce;
    }

    /**
     * 生成默认长度（16 字节）的安全随机 Nonce。
     *
     * @return 包含 16 字节安全随机数据的字节数组
     */
    public static byte[] generateNonce() {
        return generateNonce(16);
    }

    /**
     * 生成 12 字节的初始化向量（IV），适用于 AES-GCM 等对称加密模式。
     *
     * @return 12 字节的 IV 字节数组
     */
    public static byte[] generateIV() {
        byte[] iv = new byte[IV_LENGTH];
        SECURE_RANDOM.nextBytes(iv);
        return iv;
    }

    /**
     * 生成随机 UUID 字符串（标准 36 字符格式，含连字符）。
     *
     * <p>内部使用 {@link UUID#randomUUID()}，底层依赖 SecureRandom。
     *
     * @return 标准 UUID 字符串，例如 {@code "550e8400-e29b-41d4-a716-446655440000"}
     */
    public static String generateUUID() {
        return UUID.randomUUID().toString();
    }
}
