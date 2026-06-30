package com.sm2sdk.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Configuration POJO for the SM2 SDK.
 *
 * <p>Uses the builder pattern for construction. All fields have sensible defaults
 * where applicable. Fluent setters ({@code withXxx}) are provided for use in
 * configuration frameworks (Spring, etc.).
 *
 * <p>Contains inner classes for per-peer configuration ({@link PeerConfig}) and
 * client access control ({@link ClientAccessConfig}).
 */
public class Sm2SdkConfig {

    // ========== Defaults ==========

    /** Default handshake timeout: 10 seconds. */
    public static final long DEFAULT_HANDSHAKE_TIMEOUT_MS = 10_000L;

    /** Default session idle timeout: 5 minutes. */
    public static final long DEFAULT_SESSION_TIMEOUT_MS = 300_000L;

    /** Default maximum session lifetime: 1 hour. */
    public static final long DEFAULT_MAX_SESSION_LIFETIME_MS = 3_600_000L;

    /** Default maximum requests per session. */
    public static final int DEFAULT_MAX_SESSION_REQUESTS = 1000;

    /** Default maximum concurrent sessions. */
    public static final int DEFAULT_MAX_SESSIONS = 10000;

    /** Default session cleanup interval: 1 minute. */
    public static final long DEFAULT_SESSION_CLEANUP_INTERVAL_MS = 60_000L;

    // ========== Fields ==========

    private String sm2PrivateKey;
    private String sm2PublicKey;
    private String serverUrl;
    private long handshakeTimeoutMs = DEFAULT_HANDSHAKE_TIMEOUT_MS;
    private long sessionTimeoutMs = DEFAULT_SESSION_TIMEOUT_MS;
    private long maxSessionLifetimeMs = DEFAULT_MAX_SESSION_LIFETIME_MS;
    private int maxSessionRequests = DEFAULT_MAX_SESSION_REQUESTS;
    private int maxSessions = DEFAULT_MAX_SESSIONS;
    private long sessionCleanupIntervalMs = DEFAULT_SESSION_CLEANUP_INTERVAL_MS;
    private List<PeerConfig> peerConfigs = new ArrayList<>();
    private ClientAccessConfig clientAccessConfig;

    /** Creates a new {@link Sm2SdkConfig} with default values. */
    public Sm2SdkConfig() {
    }

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

    public List<PeerConfig> getPeerConfigs() {
        return peerConfigs;
    }

    public void setPeerConfigs(List<PeerConfig> peerConfigs) {
        this.peerConfigs = peerConfigs != null ? new ArrayList<>(peerConfigs) : new ArrayList<>();
    }

    public ClientAccessConfig getClientAccessConfig() {
        return clientAccessConfig;
    }

    public void setClientAccessConfig(ClientAccessConfig clientAccessConfig) {
        this.clientAccessConfig = clientAccessConfig;
    }

    // ========== Fluent Setters ==========

    public Sm2SdkConfig withSm2PrivateKey(String sm2PrivateKey) {
        this.sm2PrivateKey = sm2PrivateKey;
        return this;
    }

    public Sm2SdkConfig withSm2PublicKey(String sm2PublicKey) {
        this.sm2PublicKey = sm2PublicKey;
        return this;
    }

    public Sm2SdkConfig withServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
        return this;
    }

    public Sm2SdkConfig withHandshakeTimeoutMs(long handshakeTimeoutMs) {
        this.handshakeTimeoutMs = handshakeTimeoutMs;
        return this;
    }

    public Sm2SdkConfig withSessionTimeoutMs(long sessionTimeoutMs) {
        this.sessionTimeoutMs = sessionTimeoutMs;
        return this;
    }

    public Sm2SdkConfig withMaxSessionLifetimeMs(long maxSessionLifetimeMs) {
        this.maxSessionLifetimeMs = maxSessionLifetimeMs;
        return this;
    }

    public Sm2SdkConfig withMaxSessionRequests(int maxSessionRequests) {
        this.maxSessionRequests = maxSessionRequests;
        return this;
    }

    public Sm2SdkConfig withMaxSessions(int maxSessions) {
        this.maxSessions = maxSessions;
        return this;
    }

    public Sm2SdkConfig withSessionCleanupIntervalMs(long sessionCleanupIntervalMs) {
        this.sessionCleanupIntervalMs = sessionCleanupIntervalMs;
        return this;
    }

    public Sm2SdkConfig withPeerConfigs(List<PeerConfig> peerConfigs) {
        this.peerConfigs = peerConfigs != null ? new ArrayList<>(peerConfigs) : new ArrayList<>();
        return this;
    }

    public Sm2SdkConfig withClientAccessConfig(ClientAccessConfig clientAccessConfig) {
        this.clientAccessConfig = clientAccessConfig;
        return this;
    }

    // ========== Builder ==========

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link Sm2SdkConfig}.
     */
    public static final class Builder {
        private final Sm2SdkConfig config = new Sm2SdkConfig();

        private Builder() {
        }

        public Builder sm2PrivateKey(String sm2PrivateKey) {
            config.setSm2PrivateKey(sm2PrivateKey);
            return this;
        }

        public Builder sm2PublicKey(String sm2PublicKey) {
            config.setSm2PublicKey(sm2PublicKey);
            return this;
        }

        public Builder serverUrl(String serverUrl) {
            config.setServerUrl(serverUrl);
            return this;
        }

        public Builder handshakeTimeoutMs(long handshakeTimeoutMs) {
            config.setHandshakeTimeoutMs(handshakeTimeoutMs);
            return this;
        }

        public Builder sessionTimeoutMs(long sessionTimeoutMs) {
            config.setSessionTimeoutMs(sessionTimeoutMs);
            return this;
        }

        public Builder maxSessionLifetimeMs(long maxSessionLifetimeMs) {
            config.setMaxSessionLifetimeMs(maxSessionLifetimeMs);
            return this;
        }

        public Builder maxSessionRequests(int maxSessionRequests) {
            config.setMaxSessionRequests(maxSessionRequests);
            return this;
        }

        public Builder maxSessions(int maxSessions) {
            config.setMaxSessions(maxSessions);
            return this;
        }

        public Builder sessionCleanupIntervalMs(long sessionCleanupIntervalMs) {
            config.setSessionCleanupIntervalMs(sessionCleanupIntervalMs);
            return this;
        }

        public Builder peerConfigs(List<PeerConfig> peerConfigs) {
            config.setPeerConfigs(peerConfigs);
            return this;
        }

        public Builder addPeerConfig(PeerConfig peerConfig) {
            config.peerConfigs.add(peerConfig);
            return this;
        }

        public Builder clientAccessConfig(ClientAccessConfig clientAccessConfig) {
            config.setClientAccessConfig(clientAccessConfig);
            return this;
        }

        public Sm2SdkConfig build() {
            return config;
        }
    }

    // ========== Inner Classes ==========

    /**
     * Configuration for a single peer (server or client).
     */
    public static final class PeerConfig {

        private String publicKey;
        private String serverUrl;

        public PeerConfig() {
        }

        public PeerConfig(String publicKey, String serverUrl) {
            this.publicKey = publicKey;
            this.serverUrl = serverUrl;
        }

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

        // Fluent setters

        public PeerConfig withPublicKey(String publicKey) {
            this.publicKey = publicKey;
            return this;
        }

        public PeerConfig withServerUrl(String serverUrl) {
            this.serverUrl = serverUrl;
            return this;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PeerConfig that = (PeerConfig) o;
            return Objects.equals(publicKey, that.publicKey)
                    && Objects.equals(serverUrl, that.serverUrl);
        }

        @Override
        public int hashCode() {
            return Objects.hash(publicKey, serverUrl);
        }

        @Override
        public String toString() {
            return "PeerConfig{" +
                    "serverUrl='" + serverUrl + '\'' +
                    '}';
        }
    }

    /**
     * Access-control configuration for the client SDK.
     *
     * <p>Defines which request paths the client is permitted to reach.
     */
    public static final class ClientAccessConfig {

        private List<String> paths = new ArrayList<>();

        public ClientAccessConfig() {
        }

        public ClientAccessConfig(List<String> paths) {
            this.paths = paths != null ? new ArrayList<>(paths) : new ArrayList<>();
        }

        public List<String> getPaths() {
            return paths;
        }

        public void setPaths(List<String> paths) {
            this.paths = paths != null ? new ArrayList<>(paths) : new ArrayList<>();
        }

        // Fluent setters

        public ClientAccessConfig withPaths(List<String> paths) {
            this.paths = paths != null ? new ArrayList<>(paths) : new ArrayList<>();
            return this;
        }

        public ClientAccessConfig addPath(String path) {
            this.paths.add(path);
            return this;
        }

        /**
         * Returns an unmodifiable view of the permitted paths.
         */
        public List<String> getUnmodifiablePaths() {
            return Collections.unmodifiableList(paths);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ClientAccessConfig that = (ClientAccessConfig) o;
            return Objects.equals(paths, that.paths);
        }

        @Override
        public int hashCode() {
            return Objects.hash(paths);
        }

        @Override
        public String toString() {
            return "ClientAccessConfig{paths=" + paths + '}';
        }
    }

    @Override
    public String toString() {
        return "Sm2SdkConfig{" +
                "handshakeTimeoutMs=" + handshakeTimeoutMs +
                ", sessionTimeoutMs=" + sessionTimeoutMs +
                ", maxSessionLifetimeMs=" + maxSessionLifetimeMs +
                ", maxSessionRequests=" + maxSessionRequests +
                ", maxSessions=" + maxSessions +
                '}';
    }
}
