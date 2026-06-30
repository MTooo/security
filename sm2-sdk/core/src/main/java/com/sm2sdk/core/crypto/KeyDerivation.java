package com.sm2sdk.core.crypto;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.digest.SM3;

/**
 * 密钥派生函数（KDF），依据 GB/T 32918.4-2016 第 5 章定义。
 *
 * <p>该 KDF 基于 SM3 密码杂凑算法，从种子值 {@code Z} 产生任意长度的字节串。
 * 算法迭代过程：对于 {@code i = 1} 到 {@code ceil(klen / 256)}，
 * 计算 {@code Ha[i] = SM3(Z || ct)}，其中 {@code ct} 是大端序的 4 字节计数器，
 * 从 {@code 0x00000001} 开始，每次迭代递增。
 * 输出为 {@code Ha[1] || Ha[2] || ...} 并截取前 {@code klen} 比特。
 *
 * <p>提供工具方法从派生密钥材料中提取 SM4 密钥、SM4 IV 和可选的 HMAC 密钥。
 */
public final class KeyDerivation {

    /** SM3 哈希输出长度（字节数）。 */
    static final int SM3_HASH_LEN = 32;

    private KeyDerivation() {
        // 工具类，不可实例化
    }

    /**
     * 使用基于 SM3 的 KDF 从种子派生密钥材料。
     *
     * @param z        输入种子字节
     * @param klenBits 请求的输出长度（比特）
     * @return 派生密钥材料，长度为 {@code ceil(klenBits / 8)} 字节
     */
    public static byte[] kdf(byte[] z, int klenBits) {
        int klenBytes = (klenBits + 7) / 8;
        int hashBitLen = SM3_HASH_LEN * 8;
        int count = (klenBits + hashBitLen - 1) / hashBitLen;

        SM3 sm3 = SmUtil.sm3();
        byte[] result = new byte[klenBytes];
        int offset = 0;

        for (int i = 1; i <= count; i++) {
            byte[] ha = sm3.digest(concat(z, intToBytes(i)));
            int copyLen = Math.min(SM3_HASH_LEN, klenBytes - offset);
            System.arraycopy(ha, 0, result, offset, copyLen);
            offset += copyLen;
        }

        return result;
    }

    /**
     * 从派生密钥材料中提取 SM4 加密密钥。
     *
     * @param derived 通过 {@link #kdf} 获得的完整派生密钥材料
     * @return 字节 {@code [0..15]}（16 字节）
     */
    public static byte[] extractSm4Key(byte[] derived) {
        byte[] key = new byte[16];
        System.arraycopy(derived, 0, key, 0, 16);
        return key;
    }

    /**
     * 从派生密钥材料中提取 SM4 初始向量。
     *
     * @param derived 通过 {@link #kdf} 获得的完整派生密钥材料
     * @return 字节 {@code [16..27]}（12 字节）
     */
    public static byte[] extractSm4Iv(byte[] derived) {
        byte[] iv = new byte[12];
        System.arraycopy(derived, 16, iv, 0, 12);
        return iv;
    }

    /**
     * 拼接两个字节数组。
     *
     * @param a 第一个字节数组
     * @param b 第二个字节数组
     * @return 包含 {@code a} 后接 {@code b} 的新数组
     */
    static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    /**
     * 将整数转换为大端序的 4 字节数组。
     *
     * @param v 整数值
     * @return 大端序的 4 字节数组
     */
    static byte[] intToBytes(int v) {
        return new byte[]{
                (byte) (v >>> 24),
                (byte) (v >>> 16),
                (byte) (v >>> 8),
                (byte) v
        };
    }
}
