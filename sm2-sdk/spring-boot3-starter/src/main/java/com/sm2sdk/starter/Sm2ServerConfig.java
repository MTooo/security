package com.sm2sdk.starter;

import com.sm2sdk.core.model.Sm2SdkConfig;

import java.util.Objects;

/**
 * 服务端配置，封装 {@link Sm2SdkConfig} 并添加服务端专属字段。
 *
 * <p>包含握手端点路径、Nonce 校验开关、客户端访问控制等
 * 服务端处理安全请求所需的全部配置信息。
 */
public class Sm2ServerConfig {

    /** 默认握手初始化端点路径 */
    public static final String DEFAULT_HANDSHAKE_INIT_PATH = "/handshake/init";

    /** 默认握手确认端点路径 */
    public static final String DEFAULT_HANDSHAKE_CONFIRM_PATH = "/handshake/confirm";

    /** 是否启用 Nonce 重放校验，默认启用 */
    public static final boolean DEFAULT_NONCE_VALIDATION_ENABLED = true;

    /** 默认服务端标识（与客户端 AutoConfiguration 创建的 peerId 保持一致） */
    public static final String DEFAULT_SERVER_ID = "default";

    /** 默认握手速率限制（每秒最大请求数），兼顾安全与可用性 */
    public static final int DEFAULT_HANDSHAKE_RATE_LIMIT = 50;

    /** 默认握手时间戳有效窗口（毫秒），30 秒 */
    public static final long DEFAULT_TIMESTAMP_WINDOW_MS = 30_000L;

    /** 默认最大请求体大小（字节），1 MB */
    public static final int DEFAULT_MAX_REQUEST_BODY_SIZE = 1_048_576;

    /** 默认是否暴露异常详情到响应，生产环境应设为 false */
    public static final boolean DEFAULT_INCLUDE_ERROR_DETAIL = false;

    // ========== 字段 ==========

    private final Sm2SdkConfig sdkConfig;
    private final String handshakeInitPath;
    private final String handshakeConfirmPath;
    private final boolean nonceValidationEnabled;
    private final String serverId;
    private final int handshakeRateLimitPerSecond;
    private final long timestampWindowMs;
    private final int maxRequestBodySize;
    private final boolean includeErrorDetail;

    /**
     * 从 SDK 全局配置构建服务端配置（使用默认值）。
     *
     * @param sdkConfig SDK 全局配置
     */
    public Sm2ServerConfig(Sm2SdkConfig sdkConfig) {
        this(sdkConfig, DEFAULT_HANDSHAKE_INIT_PATH, DEFAULT_HANDSHAKE_CONFIRM_PATH,
                DEFAULT_NONCE_VALIDATION_ENABLED,
                sdkConfig.getServerId() != null ? sdkConfig.getServerId() : DEFAULT_SERVER_ID,
                DEFAULT_HANDSHAKE_RATE_LIMIT, DEFAULT_TIMESTAMP_WINDOW_MS,
                DEFAULT_MAX_REQUEST_BODY_SIZE, DEFAULT_INCLUDE_ERROR_DETAIL);
    }

    /**
     * 完整构造服务端配置。
     *
     * @param sdkConfig               SDK 全局配置
     * @param handshakeInitPath       握手初始化端点路径
     * @param handshakeConfirmPath    握手确认端点路径
     * @param nonceValidationEnabled  是否启用 Nonce 重放校验
     * @param serverId                服务端标识（SM2 握手 ZB 计算中作为服务端身份字符串，
     *                                须与客户端 peerId 匹配）
     */
    public Sm2ServerConfig(Sm2SdkConfig sdkConfig, String handshakeInitPath,
                           String handshakeConfirmPath, boolean nonceValidationEnabled,
                           String serverId) {
        this(sdkConfig, handshakeInitPath, handshakeConfirmPath, nonceValidationEnabled,
                serverId, DEFAULT_HANDSHAKE_RATE_LIMIT, DEFAULT_TIMESTAMP_WINDOW_MS,
                DEFAULT_MAX_REQUEST_BODY_SIZE, DEFAULT_INCLUDE_ERROR_DETAIL);
    }

    /**
     * 完整构造服务端配置（含安全参数）。
     */
    public Sm2ServerConfig(Sm2SdkConfig sdkConfig, String handshakeInitPath,
                           String handshakeConfirmPath, boolean nonceValidationEnabled,
                           String serverId, int handshakeRateLimitPerSecond,
                           long timestampWindowMs, int maxRequestBodySize,
                           boolean includeErrorDetail) {
        this.sdkConfig = Objects.requireNonNull(sdkConfig, "sdkConfig must not be null");
        this.handshakeInitPath = handshakeInitPath != null ? handshakeInitPath
                : DEFAULT_HANDSHAKE_INIT_PATH;
        this.handshakeConfirmPath = handshakeConfirmPath != null ? handshakeConfirmPath
                : DEFAULT_HANDSHAKE_CONFIRM_PATH;
        this.nonceValidationEnabled = nonceValidationEnabled;
        this.serverId = serverId != null ? serverId : DEFAULT_SERVER_ID;
        this.handshakeRateLimitPerSecond = handshakeRateLimitPerSecond > 0
                ? handshakeRateLimitPerSecond : DEFAULT_HANDSHAKE_RATE_LIMIT;
        this.timestampWindowMs = timestampWindowMs > 0
                ? timestampWindowMs : DEFAULT_TIMESTAMP_WINDOW_MS;
        this.maxRequestBodySize = maxRequestBodySize > 0
                ? maxRequestBodySize : DEFAULT_MAX_REQUEST_BODY_SIZE;
        this.includeErrorDetail = includeErrorDetail;
    }

    // ========== Getters ==========

    public Sm2SdkConfig getSdkConfig() {
        return sdkConfig;
    }

    public String getHandshakeInitPath() {
        return handshakeInitPath;
    }

    public String getHandshakeConfirmPath() {
        return handshakeConfirmPath;
    }

    public boolean isNonceValidationEnabled() {
        return nonceValidationEnabled;
    }

    /** 获取服务端标识，默认 {@value #DEFAULT_SERVER_ID}。 */
    public String getServerId() {
        return serverId;
    }

    /** 获取握手速率限制（每秒最大请求数）。 */
    public int getHandshakeRateLimitPerSecond() {
        return handshakeRateLimitPerSecond;
    }

    /** 获取握手时间戳有效窗口（毫秒）。 */
    public long getTimestampWindowMs() {
        return timestampWindowMs;
    }

    /** 获取最大请求体大小（字节）。 */
    public int getMaxRequestBodySize() {
        return maxRequestBodySize;
    }

    /** 是否在错误响应中包含异常详情。 */
    public boolean isIncludeErrorDetail() {
        return includeErrorDetail;
    }

    @Override
    public String toString() {
        return "Sm2ServerConfig{" +
                "handshakeInitPath='" + handshakeInitPath + '\'' +
                ", handshakeConfirmPath='" + handshakeConfirmPath + '\'' +
                ", nonceValidationEnabled=" + nonceValidationEnabled +
                ", serverId='" + serverId + '\'' +
                ", handshakeRateLimit=" + handshakeRateLimitPerSecond +
                ", timestampWindowMs=" + timestampWindowMs +
                ", maxRequestBodySize=" + maxRequestBodySize +
                '}';
    }
}
