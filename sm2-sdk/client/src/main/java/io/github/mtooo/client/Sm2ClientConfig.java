package io.github.mtooo.client;

import io.github.mtooo.core.model.Sm2SdkConfig;

import java.util.List;
import java.util.Objects;

/**
 * 客户端配置，从 {@link Sm2SdkConfig} 提取客户端相关的配置字段。
 *
 * <p>包含服务端 URL、对端标识、本地 SM2 私钥以及目标对端的公钥等
 * 客户端发起安全请求所需的全部配置信息。
 */
public class Sm2ClientConfig {

    private final String serverUrl;
    private final String peerId;
    private final String serverId;
    private final String sm2PrivateKey;
    private final String sm2PublicKey;
    private final Sm2SdkConfig.PeerConfig peerConfig;

    /**
     * 从 SDK 全局配置和对端标识构建客户端配置。
     *
     * @param config SDK 全局配置
     * @param peerId 对端标识
     */
    public Sm2ClientConfig(Sm2SdkConfig config, String peerId) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(peerId, "peerId must not be null");
        this.peerId = peerId;
        this.peerConfig = findPeerConfig(config, peerId);
        // server-url：优先顶层配置，回退到 PeerConfig
        String topServerUrl = config.getServerUrl();
        String peerServerUrl = (peerConfig != null) ? peerConfig.getServerUrl() : null;
        this.serverUrl = (topServerUrl != null && !topServerUrl.isEmpty())
                ? topServerUrl : peerServerUrl;
        // 优先使用 PeerConfig 中的 serverId，否则默认 "default"
        // 注意：不使用全局 serverId，因为那是"自己作为服务端"的标识
        String peerServerId = (peerConfig != null && peerConfig.getServerId() != null)
                ? peerConfig.getServerId() : null;
        this.serverId = peerServerId != null ? peerServerId : "default";
        this.sm2PrivateKey = config.getSm2PrivateKey();
        this.sm2PublicKey = config.getSm2PublicKey();
    }

    /**
     * 获取服务端 URL。
     *
     * @return 服务端 URL
     */
    public String getServerUrl() {
        return serverUrl;
    }

    /**
     * 获取对端标识（即客户端自身标识）。
     *
     * @return 对端标识
     */
    public String getPeerId() {
        return peerId;
    }

    /**
     * 获取服务端标识，用于 SM2 握手 ZB 计算。
     *
     * @return 服务端标识，默认 "default"
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * 获取本地 SM2 私钥（十六进制字符串）。
     *
     * @return SM2 私钥十六进制字符串
     */
    public String getSm2PrivateKey() {
        return sm2PrivateKey;
    }

    /**
     * 获取本地 SM2 公钥（十六进制字符串）。
     *
     * @return SM2 公钥十六进制字符串
     */
    public String getSm2PublicKey() {
        return sm2PublicKey;
    }

    /**
     * 获取对端的 SM2 公钥（十六进制字符串）。
     *
     * <p>如果找不到匹配的 PeerConfig，默认使用本地公钥。
     *
     * @return 对端公钥十六进制字符串
     */
    public String getPeerPublicKey() {
        return peerConfig != null ? peerConfig.getPublicKey() : sm2PublicKey;
    }

    /**
     * 在 {@link Sm2SdkConfig#getPeerConfigs()} 中查找与指定 peerId 匹配的配置。
     */
    private static Sm2SdkConfig.PeerConfig findPeerConfig(Sm2SdkConfig config, String peerId) {
        List<Sm2SdkConfig.PeerConfig> peers = config.getPeerConfigs();
        if (peers == null || peers.isEmpty()) {
            return null;
        }
        // 精确匹配 peerId（目前简化：返回第一个配置）
        return peers.get(0);
    }

    @Override
    public String toString() {
        return "Sm2ClientConfig{" +
                "serverUrl='" + serverUrl + '\'' +
                ", peerId='" + peerId + '\'' +
                '}';
    }
}
