package io.github.mtooo.core.util;

/**
 * Redis Key 前缀构建工具类。
 *
 * <p>提供统一的 Redis Key 格式定义，所有 Key 均采用 {@code {prefix}:{type}:{identifier}} 的层级结构，
 * 便于按业务类型进行管理和排查。
 *
 * <p>使用方式：
 * <pre>{@code
 * String key = Sm2KeyPrefix.buildSessionKey("sm2", "session-001");
 * // 结果: "sm2:session:session-001"
 * }</pre>
 */
public final class Sm2KeyPrefix {

    private Sm2KeyPrefix() {
        // 工具类，不可实例化
    }

    /**
     * 构建会话 Key：{prefix}:session:{sessionId}
     *
     * @param prefix   业务前缀（如 "sm2"）
     * @param sessionId 会话标识
     * @return 格式化的 Redis Key
     */
    public static String buildSessionKey(String prefix, String sessionId) {
        return prefix + ":session:" + sessionId;
    }

    /**
     * 构建 Nonce Key：{prefix}:nonce:{nonce}
     *
     * @param prefix 业务前缀（如 "sm2"）
     * @param nonce  Nonce 值（通常为十六进制字符串）
     * @return 格式化的 Redis Key
     */
    public static String buildNonceKey(String prefix, String nonce) {
        return prefix + ":nonce:" + nonce;
    }

    /**
     * 构建布隆过滤器 Key：{prefix}:nonce:bf:{minuteTick}
     *
     * <p>按分钟刻度分片，用于 nonce 重放校验的布隆过滤器。
     *
     * @param prefix     业务前缀（如 "sm2"）
     * @param minuteTick 分钟时间戳（如 "202606301520"）
     * @return 格式化的 Redis Key
     */
    public static String buildBloomKey(String prefix, String minuteTick) {
        return prefix + ":nonce:bf:" + minuteTick;
    }

    /**
     * 构建握手分布式锁 Key：{prefix}:handshake:lock:{clientId}
     *
     * @param prefix   业务前缀（如 "sm2"）
     * @param clientId 客户端标识
     * @return 格式化的 Redis Key
     */
    public static String buildHandshakeLockKey(String prefix, String clientId) {
        return prefix + ":handshake:lock:" + clientId;
    }
}
