package com.sm2sdk.server;

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

    // ========== 字段 ==========

    private final Sm2SdkConfig sdkConfig;
    private final String handshakeInitPath;
    private final String handshakeConfirmPath;
    private final boolean nonceValidationEnabled;

    /**
     * 从 SDK 全局配置构建服务端配置（使用默认值）。
     *
     * @param sdkConfig SDK 全局配置
     */
    public Sm2ServerConfig(Sm2SdkConfig sdkConfig) {
        this(sdkConfig, DEFAULT_HANDSHAKE_INIT_PATH, DEFAULT_HANDSHAKE_CONFIRM_PATH,
                DEFAULT_NONCE_VALIDATION_ENABLED);
    }

    /**
     * 完整构造服务端配置。
     *
     * @param sdkConfig               SDK 全局配置
     * @param handshakeInitPath       握手初始化端点路径
     * @param handshakeConfirmPath    握手确认端点路径
     * @param nonceValidationEnabled  是否启用 Nonce 重放校验
     */
    public Sm2ServerConfig(Sm2SdkConfig sdkConfig, String handshakeInitPath,
                           String handshakeConfirmPath, boolean nonceValidationEnabled) {
        this.sdkConfig = Objects.requireNonNull(sdkConfig, "sdkConfig must not be null");
        this.handshakeInitPath = handshakeInitPath != null ? handshakeInitPath
                : DEFAULT_HANDSHAKE_INIT_PATH;
        this.handshakeConfirmPath = handshakeConfirmPath != null ? handshakeConfirmPath
                : DEFAULT_HANDSHAKE_CONFIRM_PATH;
        this.nonceValidationEnabled = nonceValidationEnabled;
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

    @Override
    public String toString() {
        return "Sm2ServerConfig{" +
                "handshakeInitPath='" + handshakeInitPath + '\'' +
                ", handshakeConfirmPath='" + handshakeConfirmPath + '\'' +
                ", nonceValidationEnabled=" + nonceValidationEnabled +
                '}';
    }
}
