package io.github.mtooo.starter;

import io.github.mtooo.client.Sm2HttpClient;
import io.github.mtooo.core.crypto.Sm2KeyExchange;
import io.github.mtooo.core.crypto.Sm4Crypto;
import io.github.mtooo.core.model.Sm2SdkConfig;
import io.github.mtooo.core.nonce.NonceValidator;
import io.github.mtooo.core.session.SessionManager;
import io.github.mtooo.core.session.SessionStore;
import io.github.mtooo.core.session.impl.CaffeineSessionStore;
import io.github.mtooo.starter.Sm2HandshakeController;
import io.github.mtooo.starter.Sm2ResponseBodyAdvice;
import io.github.mtooo.starter.Sm2ServerConfig;
import io.github.mtooo.starter.Sm2ServerInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@link Sm2SdkAutoConfiguration} 的自动配置测试。
 */
class Sm2SdkAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(Sm2SdkAutoConfiguration.class));

    // ==================== 默认启用 ====================

    @Test
    void testAutoConfigurationEnabledByDefault() {
        contextRunner
                .withPropertyValues("sm2.sdk.sm2-private-key=0123456789abcdef")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(Sm2SdkConfig.class);
                    assertThat(ctx).hasSingleBean(Sm2ServerConfig.class);
                    assertThat(ctx).hasSingleBean(Sm2KeyExchange.class);
                    assertThat(ctx).hasSingleBean(Sm4Crypto.class);
                    assertThat(ctx).hasSingleBean(SessionStore.class);
                    assertThat(ctx).hasSingleBean(SessionManager.class);
                    assertThat(ctx).hasSingleBean(NonceValidator.class);
                    assertThat(ctx).hasSingleBean(Sm2HandshakeController.class);
                    assertThat(ctx).hasSingleBean(Sm2ServerInterceptor.class);
                    assertThat(ctx).hasSingleBean(Sm2ResponseBodyAdvice.class);
                });
    }

    // ==================== 禁用 ====================

    @Test
    void testAutoConfigurationDisabled() {
        contextRunner
                .withPropertyValues("sm2.sdk.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(Sm2SdkConfig.class);
                    assertThat(ctx).doesNotHaveBean(SessionManager.class);
                });
    }

    // ==================== CaffeineSessionStore 为默认实现 ====================

    @Test
    void testDefaultSessionStoreIsCaffeine() {
        contextRunner
                .withPropertyValues("sm2.sdk.sm2-private-key=0123456789abcdef")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(SessionStore.class);
                    assertThat(ctx.getBean(SessionStore.class))
                            .isInstanceOf(CaffeineSessionStore.class);
                });
    }

    // ==================== Sm2HttpClient 条件创建 ====================

    @Test
    void testSm2HttpClientCreatedWhenServerUrlConfigured() {
        contextRunner
                .withPropertyValues(
                        "sm2.sdk.sm2-private-key=0123456789abcdef",
                        "sm2.sdk.server-url=https://api.example.com"
                )
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(Sm2HttpClient.class);
                });
    }

    @Test
    void testSm2HttpClientNotCreatedWithoutServerUrl() {
        contextRunner
                .withPropertyValues("sm2.sdk.sm2-private-key=0123456789abcdef")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(Sm2HttpClient.class);
                });
    }

    // ==================== Sm2SdkConfig 从 Properties 创建 ====================

    @Test
    void testSm2SdkConfigFromProperties() {
        contextRunner
                .withPropertyValues(
                        "sm2.sdk.sm2-private-key=abcdef1234567890",
                        "sm2.sdk.sm2-public-key=fedcba0987654321",
                        "sm2.sdk.session-timeout-ms=600000",
                        "sm2.sdk.max-session-requests=500"
                )
                .run(ctx -> {
                    Sm2SdkConfig config = ctx.getBean(Sm2SdkConfig.class);
                    assertThat(config.getSm2PrivateKey()).isEqualTo("abcdef1234567890");
                    assertThat(config.getSm2PublicKey()).isEqualTo("fedcba0987654321");
                    assertThat(config.getSessionTimeoutMs()).isEqualTo(600000L);
                    assertThat(config.getMaxSessionRequests()).isEqualTo(500);
                });
    }

    // ==================== Bean 可被覆盖 ====================

    @Test
    void testCustomSm2KeyExchangeOverridesDefault() {
        Sm2KeyExchange customExchange = mock(Sm2KeyExchange.class);
        contextRunner
                .withPropertyValues("sm2.sdk.sm2-private-key=0123456789abcdef")
                .withBean(Sm2KeyExchange.class, () -> customExchange)
                .run(ctx -> {
                    assertThat(ctx.getBean(Sm2KeyExchange.class))
                            .isSameAs(customExchange);
                });
    }

    @Test
    void testCustomSessionStoreOverridesDefault() {
        SessionStore customStore = mock(SessionStore.class);
        contextRunner
                .withPropertyValues("sm2.sdk.sm2-private-key=0123456789abcdef")
                .withBean(SessionStore.class, () -> customStore)
                .run(ctx -> {
                    assertThat(ctx.getBean(SessionStore.class))
                            .isSameAs(customStore);
                });
    }

    // ==================== Sm2SdkProperties 绑定 ====================

    @Test
    void testPropertiesBinding() {
        contextRunner
                .withPropertyValues(
                        "sm2.sdk.sm2-private-key=prop-test-key",
                        "sm2.sdk.server-url=https://prop.example.com",
                        "sm2.sdk.handshake-timeout-ms=15000",
                        "sm2.sdk.max-sessions=5000"
                )
                .run(ctx -> {
                    Sm2SdkProperties props = ctx.getBean(Sm2SdkProperties.class);
                    assertThat(props.getSm2PrivateKey()).isEqualTo("prop-test-key");
                    assertThat(props.getServerUrl()).isEqualTo("https://prop.example.com");
                    assertThat(props.getHandshakeTimeoutMs()).isEqualTo(15000L);
                    assertThat(props.getMaxSessions()).isEqualTo(5000);
                });
    }
}
