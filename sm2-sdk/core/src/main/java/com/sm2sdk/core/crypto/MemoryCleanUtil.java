package com.sm2sdk.core.crypto;

import java.util.Arrays;

/**
 * 内存清理工具，用于安全地清除敏感密钥材料。
 *
 * <p>在密钥材料使用完毕后，调用 {@link #cleanKey(byte[])} 将数组内容置零，
 * 以降低密钥材料残留在内存中的风险。
 *
 * <p>注意：此工具适用于由调用方分配的 {@code byte[]} 类型密钥材料。
 * 对于不可变的 {@link java.math.BigInteger} 等类型，应在使用后将其
 * 转换为字节数组并清理该数组。
 */
public final class MemoryCleanUtil {

    private MemoryCleanUtil() {
        // 工具类，不可实例化
    }

    /**
     * 清除密钥字节数组，将其内容全部置零。
     *
     * <p>清零后主动调用 {@link System#gc()} 提示 JVM 执行垃圾回收，
     * 以尽量降低敏感数据在内存中残留的时间。此行为是最佳努力（best-effort），
     * 不保证立即回收。
     *
     * @param key 要清除的密钥字节数组；若为 {@code null} 则不执行任何操作
     */
    public static void cleanKey(byte[] key) {
        if (key != null) {
            Arrays.fill(key, (byte) 0);
            // best-effort 提示 JVM 回收，降低敏感数据残留时间
            System.gc();
        }
    }

    /**
     * 批量清除多个密钥字节数组，将其内容全部置零。
     *
     * <p>遍历所有传入的数组并逐个调用 {@link #cleanKey(byte[])}。
     *
     * @param keys 要清除的密钥字节数组列表；若为 {@code null} 或空则不执行任何操作
     */
    public static void cleanKeys(byte[]... keys) {
        if (keys != null) {
            for (byte[] key : keys) {
                cleanKey(key);
            }
        }
    }
}
