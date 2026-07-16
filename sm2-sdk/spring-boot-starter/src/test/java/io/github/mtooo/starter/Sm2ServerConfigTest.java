package io.github.mtooo.starter;

import io.github.mtooo.core.model.Sm2SdkConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link Sm2ServerConfig} 的单元测试。
 */
class Sm2ServerConfigTest {

    @Test
    void testDefaultValues() {
        Sm2SdkConfig sdkConfig = new Sm2SdkConfig();
        Sm2ServerConfig config = new Sm2ServerConfig(sdkConfig);

        assertEquals(Sm2ServerConfig.DEFAULT_HANDSHAKE_INIT_PATH,
                config.getHandshakeInitPath());
        assertEquals(Sm2ServerConfig.DEFAULT_HANDSHAKE_CONFIRM_PATH,
                config.getHandshakeConfirmPath());
        assertTrue(config.isNonceValidationEnabled());
        assertSame(sdkConfig, config.getSdkConfig());
    }

    @Test
    void testCustomPaths() {
        Sm2SdkConfig sdkConfig = new Sm2SdkConfig();
        Sm2ServerConfig config = new Sm2ServerConfig(sdkConfig,
                "/custom/init", "/custom/confirm", false, null);

        assertEquals("/custom/init", config.getHandshakeInitPath());
        assertEquals("/custom/confirm", config.getHandshakeConfirmPath());
        assertFalse(config.isNonceValidationEnabled());
    }

    @Test
    void testNullPathsFallbackToDefaults() {
        Sm2SdkConfig sdkConfig = new Sm2SdkConfig();
        Sm2ServerConfig config = new Sm2ServerConfig(sdkConfig, null, null, true, null);

        assertEquals(Sm2ServerConfig.DEFAULT_HANDSHAKE_INIT_PATH,
                config.getHandshakeInitPath());
        assertEquals(Sm2ServerConfig.DEFAULT_HANDSHAKE_CONFIRM_PATH,
                config.getHandshakeConfirmPath());
    }

    @Test
    void testConstructorThrowsOnNullSdkConfig() {
        assertThrows(NullPointerException.class,
                () -> new Sm2ServerConfig(null));
    }

    @Test
    void testToString() {
        Sm2SdkConfig sdkConfig = new Sm2SdkConfig();
        Sm2ServerConfig config = new Sm2ServerConfig(sdkConfig);

        String str = config.toString();
        assertTrue(str.contains("Sm2ServerConfig"));
        assertTrue(str.contains("handshakeInitPath"));
    }
}
