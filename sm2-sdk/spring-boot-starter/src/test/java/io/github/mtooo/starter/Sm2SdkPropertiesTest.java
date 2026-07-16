package io.github.mtooo.starter;

import io.github.mtooo.core.model.Sm2SdkConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Sm2SdkProperties} 的单元测试。
 */
class Sm2SdkPropertiesTest {

    private Sm2SdkProperties properties;

    @BeforeEach
    void setUp() {
        properties = new Sm2SdkProperties();
    }

    @Test
    void testDefaultValues() {
        assertEquals(Sm2SdkConfig.DEFAULT_HANDSHAKE_TIMEOUT_MS,
                properties.getHandshakeTimeoutMs());
        assertEquals(Sm2SdkConfig.DEFAULT_SESSION_TIMEOUT_MS,
                properties.getSessionTimeoutMs());
        assertEquals(Sm2SdkConfig.DEFAULT_MAX_SESSION_LIFETIME_MS,
                properties.getMaxSessionLifetimeMs());
        assertEquals(Sm2SdkConfig.DEFAULT_MAX_SESSION_REQUESTS,
                properties.getMaxSessionRequests());
        assertEquals(Sm2SdkConfig.DEFAULT_MAX_SESSIONS,
                properties.getMaxSessions());
        assertEquals(Sm2SdkConfig.DEFAULT_SESSION_CLEANUP_INTERVAL_MS,
                properties.getSessionCleanupIntervalMs());
        assertEquals(Sm2SdkConfig.DEFAULT_REDIS_KEY_PREFIX,
                properties.getRedisKeyPrefix());
        assertNotNull(properties.getPeers());
        assertTrue(properties.getPeers().isEmpty());
    }

    @Test
    void testSetAndGet() {
        properties.setSm2PrivateKey("private-key");
        properties.setSm2PublicKey("public-key");
        properties.setServerUrl("https://example.com");
        properties.setHandshakeTimeoutMs(5000L);
        properties.setSessionTimeoutMs(600000L);
        properties.setRedisKeyPrefix("myapp");

        assertEquals("private-key", properties.getSm2PrivateKey());
        assertEquals("public-key", properties.getSm2PublicKey());
        assertEquals("https://example.com", properties.getServerUrl());
        assertEquals(5000L, properties.getHandshakeTimeoutMs());
        assertEquals(600000L, properties.getSessionTimeoutMs());
        assertEquals("myapp", properties.getRedisKeyPrefix());
    }

    @Test
    void testToSdkConfigBasicFields() {
        properties.setSm2PrivateKey("0123456789abcdef0123456789abcdef"
                + "0123456789abcdef0123456789abcdef");
        properties.setSm2PublicKey("fedcba9876543210fedcba9876543210"
                + "fedcba9876543210fedcba9876543210");
        properties.setServerUrl("https://api.example.com");
        properties.setHandshakeTimeoutMs(20000L);
        properties.setSessionTimeoutMs(600000L);
        properties.setMaxSessionLifetimeMs(7200000L);
        properties.setMaxSessionRequests(500);
        properties.setMaxSessions(5000);
        properties.setRedisKeyPrefix("custom-sm2");
        properties.setLocalSecretKey("base64-secret-key");

        Sm2SdkConfig config = properties.toSdkConfig();

        assertEquals(properties.getSm2PrivateKey(), config.getSm2PrivateKey());
        assertEquals(properties.getSm2PublicKey(), config.getSm2PublicKey());
        assertEquals(properties.getServerUrl(), config.getServerUrl());
        assertEquals(20000L, config.getHandshakeTimeoutMs());
        assertEquals(600000L, config.getSessionTimeoutMs());
        assertEquals(7200000L, config.getMaxSessionLifetimeMs());
        assertEquals(500, config.getMaxSessionRequests());
        assertEquals(5000, config.getMaxSessions());
        assertEquals("custom-sm2", config.getRedisKeyPrefix());
        assertEquals("base64-secret-key", config.getLocalSecretKey());
    }

    @Test
    void testToSdkConfigWithPeers() {
        List<Sm2SdkProperties.PeerProperties> peers = new ArrayList<>();

        Sm2SdkProperties.PeerProperties peer1 = new Sm2SdkProperties.PeerProperties();
        peer1.setPublicKey("peer1-public-key");
        peer1.setServerUrl("https://peer1.example.com");
        peers.add(peer1);

        Sm2SdkProperties.PeerProperties peer2 = new Sm2SdkProperties.PeerProperties();
        peer2.setPublicKey("peer2-public-key");
        peer2.setServerUrl("https://peer2.example.com");
        peers.add(peer2);

        properties.setPeers(peers);

        Sm2SdkConfig config = properties.toSdkConfig();

        assertEquals(2, config.getPeerConfigs().size());
        assertEquals("peer1-public-key",
                config.getPeerConfigs().get(0).getPublicKey());
        assertEquals("https://peer2.example.com",
                config.getPeerConfigs().get(1).getServerUrl());
    }

    @Test
    void testToSdkConfigWithClientAccess() {
        Sm2SdkProperties.ClientAccessProperties clientAccess =
                new Sm2SdkProperties.ClientAccessProperties();
        List<String> paths = new ArrayList<>();
        paths.add("/api/data");
        paths.add("/api/user");
        clientAccess.setPaths(paths);
        properties.setClientAccess(clientAccess);

        Sm2SdkConfig config = properties.toSdkConfig();

        assertNotNull(config.getClientAccessConfig());
        // 旧 paths 被转换为 catch-all 规则（clientId=""）
        assertEquals(1, config.getClientAccessConfig().getRules().size());
        assertEquals(2, config.getClientAccessConfig().getRules().get(0).getPaths().size());
    }

    @Test
    void testToSdkConfigNullPeers() {
        properties.setPeers(null);

        Sm2SdkConfig config = properties.toSdkConfig();

        assertNotNull(config.getPeerConfigs());
        assertTrue(config.getPeerConfigs().isEmpty());
    }

    @Test
    void testNullPeerProperties() {
        // setPeers with null should become empty
        properties.setPeers(null);
        assertNotNull(properties.getPeers());
        assertTrue(properties.getPeers().isEmpty());
    }

    @Test
    void testNullClientAccessPaths() {
        Sm2SdkProperties.ClientAccessProperties clientAccess =
                new Sm2SdkProperties.ClientAccessProperties();
        clientAccess.setPaths(null);
        properties.setClientAccess(clientAccess);

        Sm2SdkConfig config = properties.toSdkConfig();
        assertNotNull(config.getClientAccessConfig());
        assertNotNull(config.getClientAccessConfig().getPaths());
        assertTrue(config.getClientAccessConfig().getPaths().isEmpty());
    }
}
