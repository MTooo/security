package io.github.mtooo.starter;

import io.github.mtooo.core.model.Sm2SdkConfig;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * SM2 SDK Spring Boot 配置属性，映射 {@code application.yml} 中的 {@code sm2.sdk} 前缀。
 *
 * <p>使用示例：
 * <pre>{@code
 * sm2:
 *   sdk:
 *     sm2-private-key: "0123456789abcdef..."
 *     sm2-public-key: "fedcba9876543210..."
 *     server-url: "https://api.example.com"
 *     session-timeout-ms: 600000
 * }</pre>
 *
 * <p>通过 {@link #toSdkConfig()} 转换为 {@link Sm2SdkConfig}。
 */
@ConfigurationProperties(prefix = "sm2.sdk")
public class Sm2SdkProperties {

    /** 本地 SM2 私钥（十六进制字符串） */
    private String sm2PrivateKey;

    /** 本地 SM2 公钥（十六进制字符串） */
    private String sm2PublicKey;

    /** 服务端 URL */
    private String serverUrl;

    /** 客户端标识，在握手时发送给服务端，用于服务端访问控制 */
    private String clientId;

    /** 服务端标识，握手 ZB 计算时使用，默认 "default" */
    private String serverId;

    /** 握手超时时间（毫秒），默认 10000 */
    private long handshakeTimeoutMs = Sm2SdkConfig.DEFAULT_HANDSHAKE_TIMEOUT_MS;

    /** 会话空闲超时时间（毫秒），默认 300000（5 分钟） */
    private long sessionTimeoutMs = Sm2SdkConfig.DEFAULT_SESSION_TIMEOUT_MS;

    /** 会话最大生命周期（毫秒），默认 3600000（1 小时） */
    private long maxSessionLifetimeMs = Sm2SdkConfig.DEFAULT_MAX_SESSION_LIFETIME_MS;

    /** 每个会话的最大请求数，默认 1000 */
    private int maxSessionRequests = Sm2SdkConfig.DEFAULT_MAX_SESSION_REQUESTS;

    /** 最大并发会话数，默认 10000 */
    private int maxSessions = Sm2SdkConfig.DEFAULT_MAX_SESSIONS;

    /** 会话清理间隔（毫秒），默认 60000（1 分钟） */
    private long sessionCleanupIntervalMs = Sm2SdkConfig.DEFAULT_SESSION_CLEANUP_INTERVAL_MS;

    /** Redis 键名前缀，默认 "sm2" */
    private String redisKeyPrefix = Sm2SdkConfig.DEFAULT_REDIS_KEY_PREFIX;

    /** 本地加密密钥（Base64 编码的 SM4 密钥材料），用于 SM4-GCM 加密保护 Redis 中的 SM4 密钥 */
    private String localSecretKey;

    /** 是否启用 Redis 会话存储 */
    private boolean redisSessionStore;

    /** 是否启用服务端角色（注册握手端点、拦截器等）。默认 true */
    private boolean serverRole = true;

    /** 握手速率限制（每秒最大请求数）。默认 50，兼顾安全与可用性 */
    private int handshakeRateLimitPerSecond = 50;

    /** 握手时间戳有效窗口（毫秒）。默认 30000（30 秒） */
    private long timestampWindowMs = 30000L;

    /** 最大请求体大小（字节）。默认 1048576（1 MB） */
    private int maxRequestBodySize = 1_048_576;

    /** 是否在错误响应中包含异常详情（仅调试用，生产环境应设为 false）。默认 false */
    private boolean includeErrorDetail;

    /** 对端配置列表 */
    private List<PeerProperties> peers = new ArrayList<>();

    /** 客户端访问控制配置 */
    @NestedConfigurationProperty
    private ClientAccessProperties clientAccess;

    // ========== Getters / Setters ==========

    public String getSm2PrivateKey() {
        return sm2PrivateKey;
    }

    public void setSm2PrivateKey(String sm2PrivateKey) {
        this.sm2PrivateKey = sm2PrivateKey;
    }

    public String getSm2PublicKey() {
        return sm2PublicKey;
    }

    public void setSm2PublicKey(String sm2PublicKey) {
        this.sm2PublicKey = sm2PublicKey;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public long getHandshakeTimeoutMs() {
        return handshakeTimeoutMs;
    }

    public void setHandshakeTimeoutMs(long handshakeTimeoutMs) {
        this.handshakeTimeoutMs = handshakeTimeoutMs;
    }

    public long getSessionTimeoutMs() {
        return sessionTimeoutMs;
    }

    public void setSessionTimeoutMs(long sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
    }

    public long getMaxSessionLifetimeMs() {
        return maxSessionLifetimeMs;
    }

    public void setMaxSessionLifetimeMs(long maxSessionLifetimeMs) {
        this.maxSessionLifetimeMs = maxSessionLifetimeMs;
    }

    public int getMaxSessionRequests() {
        return maxSessionRequests;
    }

    public void setMaxSessionRequests(int maxSessionRequests) {
        this.maxSessionRequests = maxSessionRequests;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public void setMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
    }

    public long getSessionCleanupIntervalMs() {
        return sessionCleanupIntervalMs;
    }

    public void setSessionCleanupIntervalMs(long sessionCleanupIntervalMs) {
        this.sessionCleanupIntervalMs = sessionCleanupIntervalMs;
    }

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    public String getLocalSecretKey() {
        return localSecretKey;
    }

    public void setLocalSecretKey(String localSecretKey) {
        this.localSecretKey = localSecretKey;
    }

    public boolean isRedisSessionStore() { return redisSessionStore; }
    public void setRedisSessionStore(boolean redisSessionStore) { this.redisSessionStore = redisSessionStore; }

    public boolean isServerRole() { return serverRole; }
    public void setServerRole(boolean serverRole) { this.serverRole = serverRole; }

    public int getHandshakeRateLimitPerSecond() { return handshakeRateLimitPerSecond; }
    public void setHandshakeRateLimitPerSecond(int handshakeRateLimitPerSecond) {
        this.handshakeRateLimitPerSecond = handshakeRateLimitPerSecond;
    }

    public long getTimestampWindowMs() { return timestampWindowMs; }
    public void setTimestampWindowMs(long timestampWindowMs) {
        this.timestampWindowMs = timestampWindowMs;
    }

    public int getMaxRequestBodySize() { return maxRequestBodySize; }
    public void setMaxRequestBodySize(int maxRequestBodySize) {
        this.maxRequestBodySize = maxRequestBodySize;
    }

    public boolean isIncludeErrorDetail() { return includeErrorDetail; }
    public void setIncludeErrorDetail(boolean includeErrorDetail) {
        this.includeErrorDetail = includeErrorDetail;
    }

    public List<PeerProperties> getPeers() {
        return peers;
    }

    public void setPeers(List<PeerProperties> peers) {
        this.peers = peers != null ? peers : new ArrayList<>();
    }

    public ClientAccessProperties getClientAccess() {
        return clientAccess;
    }

    public void setClientAccess(ClientAccessProperties clientAccess) {
        this.clientAccess = clientAccess;
    }

    // ========== 转换方法 ==========

    /**
     * 将 Spring Boot 配置属性转换为 {@link Sm2SdkConfig}。
     *
     * @return Sm2SdkConfig 实例
     */
    public Sm2SdkConfig toSdkConfig() {
        Sm2SdkConfig config = new Sm2SdkConfig();
        config.setSm2PrivateKey(sm2PrivateKey);
        config.setSm2PublicKey(sm2PublicKey);
        config.setServerUrl(serverUrl);
        // client-id 未配置时自动取 server-id 的值（大部分场景两者相同）
        config.setClientId(clientId != null && !clientId.isEmpty()
                ? clientId
                : (serverId != null ? serverId : "default"));
        config.setServerId(serverId != null ? serverId : "default");
        config.setHandshakeTimeoutMs(handshakeTimeoutMs);
        config.setSessionTimeoutMs(sessionTimeoutMs);
        config.setMaxSessionLifetimeMs(maxSessionLifetimeMs);
        config.setMaxSessionRequests(maxSessionRequests);
        config.setMaxSessions(maxSessions);
        config.setSessionCleanupIntervalMs(sessionCleanupIntervalMs);
        config.setRedisKeyPrefix(redisKeyPrefix);
        config.setLocalSecretKey(localSecretKey);
        config.setRedisSessionStore(redisSessionStore);

        // 转换对端配置列表
        if (peers != null) {
            List<Sm2SdkConfig.PeerConfig> peerConfigs = new ArrayList<>();
            for (PeerProperties p : peers) {
                peerConfigs.add(new Sm2SdkConfig.PeerConfig(
                        p.getPublicKey(), p.getServerUrl(), p.getServerId()));
            }
            config.setPeerConfigs(peerConfigs);
        }

        // 转换客户端访问控制配置
        if (clientAccess != null) {
            Sm2SdkConfig.ClientAccessConfig accessConfig = new Sm2SdkConfig.ClientAccessConfig();
            accessConfig.setEnabled(clientAccess.isEnabled());
            accessConfig.setDefaultPolicy(clientAccess.getDefaultPolicy());

            if (clientAccess.getRules() != null) {
                for (ClientAccessRuleProperties rp : clientAccess.getRules()) {
                    accessConfig.addRule(new Sm2SdkConfig.ClientAccessRule(
                            rp.getClientId(), rp.getPaths()));
                }
            }

            // 向后兼容：旧 paths 在没有 rules 时自动包装为 catch-all 规则
            if (clientAccess.getPaths() != null && !clientAccess.getPaths().isEmpty()
                    && (clientAccess.getRules() == null || clientAccess.getRules().isEmpty())) {
                accessConfig.addRule(new Sm2SdkConfig.ClientAccessRule(
                        "", new ArrayList<>(clientAccess.getPaths())));
            }

            config.setClientAccessConfig(accessConfig);
        }

        return config;
    }

    // ========== 内部类 ==========

    /**
     * 对端配置属性。
     */
    public static class PeerProperties {

        /** 对端 SM2 公钥（十六进制字符串） */
        private String publicKey;

        /** 对端服务端 URL */
        private String serverUrl;

        /** 对端服务端标识，握手 ZB 计算时使用 */
        private String serverId;

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getServerUrl() {
            return serverUrl;
        }

        public void setServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
        }

        public String getServerId() { return serverId; }
        public void setServerId(String serverId) { this.serverId = serverId; }
    }

    /**
     * 客户端访问规则属性，映射 YAML 中 {@code rules} 列表的单个条目。
     */
    public static class ClientAccessRuleProperties {

        /** 客户端标识。空或 null 表示匹配所有客户端的 catch-all 规则。 */
        private String clientId;

        /** 该客户端允许访问的路径模式（Ant 风格）。 */
        private List<String> paths = new ArrayList<>();

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }

        public List<String> getPaths() { return paths; }
        public void setPaths(List<String> paths) {
            this.paths = paths != null ? paths : new ArrayList<>();
        }
    }

    /**
     * 客户端访问控制属性。
     */
    public static class ClientAccessProperties {

        /** 是否启用客户端访问控制。默认 false 保持向后兼容。 */
        private boolean enabled;

        /**
         * 无匹配规则时的默认策略。"allow"（默认）或 "deny"。
         */
        private String defaultPolicy = "allow";

        /** 每客户端访问规则。 */
        private List<ClientAccessRuleProperties> rules = new ArrayList<>();

        /**
         * @deprecated 改用 {@link #rules} 配置每客户端的路径。
         */
        @Deprecated
        private List<String> paths = new ArrayList<>();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getDefaultPolicy() { return defaultPolicy; }
        public void setDefaultPolicy(String defaultPolicy) {
            this.defaultPolicy = defaultPolicy;
        }

        public List<ClientAccessRuleProperties> getRules() { return rules; }
        public void setRules(List<ClientAccessRuleProperties> rules) {
            this.rules = rules != null ? rules : new ArrayList<>();
        }

        /** @deprecated 改用 {@link #getRules()}。 */
        @Deprecated
        public List<String> getPaths() { return paths; }
        /** @deprecated 改用 {@link #setRules(List)}。 */
        @Deprecated
        public void setPaths(List<String> paths) {
            this.paths = paths != null ? paths : new ArrayList<>();
        }
    }
}
