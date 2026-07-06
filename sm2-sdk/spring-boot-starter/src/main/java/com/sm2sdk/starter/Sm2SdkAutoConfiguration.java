package com.sm2sdk.starter;

import com.sm2sdk.client.Sm2HttpClient;
import com.sm2sdk.core.access.Sm2AccessController;
import com.sm2sdk.core.crypto.Sm2KeyExchange;
import com.sm2sdk.core.crypto.Sm4Crypto;
import com.sm2sdk.core.crypto.impl.HutoolSm2KeyExchange;
import com.sm2sdk.core.crypto.impl.HutoolSm4Crypto;
import com.sm2sdk.core.model.Sm2SdkConfig;
import com.sm2sdk.core.nonce.NonceValidator;
import com.sm2sdk.core.session.SessionManager;
import com.sm2sdk.core.session.SessionStore;
import com.sm2sdk.core.session.impl.CaffeineSessionStore;
import com.sm2sdk.core.session.impl.RedisSessionStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@AutoConfiguration
@EnableConfigurationProperties(Sm2SdkProperties.class)
@ConditionalOnProperty(prefix = "sm2.sdk", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class Sm2SdkAutoConfiguration {

    // ==================== 核心组件 ====================

    @Bean
    @ConditionalOnMissingBean
    public Sm2SdkConfig sm2SdkConfig(Sm2SdkProperties properties) {
        return properties.toSdkConfig();
    }

    @Bean
    @ConditionalOnMissingBean
    public Sm2ServerConfig sm2ServerConfig(Sm2SdkConfig sdkConfig, Sm2SdkProperties properties) {
        return new Sm2ServerConfig(
                sdkConfig,
                Sm2ServerConfig.DEFAULT_HANDSHAKE_INIT_PATH,
                Sm2ServerConfig.DEFAULT_HANDSHAKE_CONFIRM_PATH,
                Sm2ServerConfig.DEFAULT_NONCE_VALIDATION_ENABLED,
                sdkConfig.getServerId() != null ? sdkConfig.getServerId() : Sm2ServerConfig.DEFAULT_SERVER_ID,
                properties.getHandshakeRateLimitPerSecond(),
                properties.getTimestampWindowMs(),
                properties.getMaxRequestBodySize(),
                properties.isIncludeErrorDetail());
    }

    @Bean
    @Scope("prototype")
    @ConditionalOnMissingBean
    public Sm2KeyExchange sm2KeyExchange() {
        return new HutoolSm2KeyExchange();
    }

    @Bean
    @ConditionalOnMissingBean
    public Sm4Crypto sm4Crypto() {
        return new HutoolSm4Crypto();
    }

    // ==================== 会话存储（默认 Caffeine） ====================

    @Bean
    @ConditionalOnMissingBean(SessionStore.class)
    public SessionStore sessionStore() {
        return new CaffeineSessionStore();
    }

    // ==================== Nonce 校验（默认无 Redis） ====================

    @Bean
    @ConditionalOnMissingBean(NonceValidator.class)
    public NonceValidator nonceValidatorDefault() {
        return new NonceValidator();
    }

    // ==================== 会话管理器 ====================

    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(Sm4Crypto sm4Crypto,
                                         SessionStore sessionStore, Sm2SdkConfig config,
                                         ObjectProvider<NonceValidator> nonceValidatorProvider,
                                         ObjectProvider<Sm2KeyExchange> keyExchangeProvider) {
        NonceValidator nonceValidator = nonceValidatorProvider.getIfAvailable();
        // 每次 getKeyExchange() 通过 Supplier 获取独立实例，避免握手状态互相覆盖
        java.util.function.Supplier<Sm2KeyExchange> keyExchangeSupplier = () -> {
            Sm2KeyExchange provided = keyExchangeProvider.getIfAvailable();
            return provided != null ? provided : new HutoolSm2KeyExchange();
        };
        return new SessionManager(keyExchangeSupplier, sm4Crypto, sessionStore, config,
                nonceValidator);
    }

    // ==================== 服务端组件 ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sm2.sdk", name = "server-role", havingValue = "true",
            matchIfMissing = true)
    public Sm2HandshakeController sm2HandshakeController(SessionManager sessionManager,
                                                         Sm2ServerConfig serverConfig) {
        return new Sm2HandshakeController(sessionManager, serverConfig);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sm2.sdk", name = "server-role", havingValue = "true",
            matchIfMissing = true)
    public Sm2ServerInterceptor sm2ServerInterceptor(SessionManager sessionManager,
                                                     Sm2ServerConfig serverConfig,
                                                     ObjectProvider<NonceValidator> nonceValidatorProvider,
                                                     ObjectProvider<Sm2AccessController> accessControllerProvider) {
        NonceValidator nonceValidator = nonceValidatorProvider.getIfAvailable();
        Sm2AccessController accessController = accessControllerProvider.getIfAvailable();
        return new Sm2ServerInterceptor(sessionManager, serverConfig,
                nonceValidator, accessController);
    }

    // ==================== 访问控制 ====================

    @Bean
    @ConditionalOnMissingBean(Sm2AccessController.class)
    @ConditionalOnProperty(prefix = "sm2.sdk", name = "server-role", havingValue = "true",
            matchIfMissing = true)
    public Sm2AccessController sm2AccessController(Sm2SdkConfig sdkConfig) {
        return new ConfigBasedAccessController(sdkConfig);
    }

    // ==================== Filter ====================

    @Bean
    @ConditionalOnProperty(prefix = "sm2.sdk", name = "server-role", havingValue = "true",
            matchIfMissing = true)
    public Sm2QueryDecryptFilter sm2QueryDecryptFilter(SessionManager sessionManager) {
        return new Sm2QueryDecryptFilter(sessionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sm2.sdk", name = "server-role", havingValue = "true",
            matchIfMissing = true)
    public Sm2RequestBodyAdvice sm2RequestBodyAdvice(SessionManager sessionManager) {
        return new Sm2RequestBodyAdvice(sessionManager);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sm2.sdk", name = "server-role", havingValue = "true",
            matchIfMissing = true)
    public Sm2ResponseBodyAdvice sm2ResponseBodyAdvice(SessionManager sessionManager,
                                                       Sm2ServerConfig serverConfig) {
        return new Sm2ResponseBodyAdvice(sessionManager, serverConfig);
    }

    // ==================== 异常处理 ====================

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sm2.sdk", name = "server-role", havingValue = "true",
            matchIfMissing = true)
    public Sm2SdkExceptionHandler sm2SdkExceptionHandler(Sm2ServerConfig serverConfig) {
        return new Sm2SdkExceptionHandler(serverConfig);
    }

    // ==================== 客户端组件 ====================

    @Bean
    @ConditionalOnMissingBean
    @Conditional(HasClientConfigCondition.class)
    public Sm2HttpClient sm2HttpClient(Sm2SdkConfig config,
                                       SessionManager sessionManager) {
        return new Sm2HttpClient(config, sessionManager,
                config.getClientId() != null && !config.getClientId().isEmpty()
                        ? config.getClientId()
                        : config.getServerUrl() != null ? "default" : "peer");
    }

    @Bean
    @ConditionalOnMissingBean
    public Sm2EncryptedBodyConverter sm2EncryptedBodyConverter() {
        return new Sm2EncryptedBodyConverter();
    }

    // ==================== Web MVC 配置 ====================

    @Bean
    @ConditionalOnProperty(prefix = "sm2.sdk", name = "server-role", havingValue = "true",
            matchIfMissing = true)
    public WebMvcConfigurer sm2WebMvcConfigurer(Sm2ServerInterceptor interceptor,
                                                 Sm2EncryptedBodyConverter encryptedBodyConverter) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns("/handshake/**");
            }

            @Override
            public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
                converters.add(0, encryptedBodyConverter);
            }
        };
    }

    // ==================== 条件判断 ====================

    /**
     * 满足以下任一条件时创建 {@link Sm2HttpClient}：
     * <ul>
     *   <li>配置了 {@code sm2.sdk.server-url}</li>
     *   <li>配置了 {@code sm2.sdk.peers} 列表</li>
     * </ul>
     */
    static class HasClientConfigCondition extends AnyNestedCondition {

        HasClientConfigCondition() {
            super(ConfigurationPhase.PARSE_CONFIGURATION);
        }

        @ConditionalOnProperty(prefix = "sm2.sdk", name = "server-url")
        static class HasServerUrl {}

        @ConditionalOnProperty(prefix = "sm2.sdk", name = "peers")
        static class HasPeers {}
    }

    // ==================== Redis 扩展（仅在 Spring Data Redis 可用时加载） ====================

    @AutoConfiguration
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnProperty(prefix = "sm2.sdk", name = "enabled", havingValue = "true",
            matchIfMissing = true)
    static class RedisConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "sm2.sdk", name = "redis-session-store",
                havingValue = "true")
        public SessionStore redisSessionStore(Sm2SdkConfig config,
                                              StringRedisTemplate redisTemplate) {
            RedisSessionStore.RedisOperations adapter = new RedisSessionStore.RedisOperations() {
                @Override
                public void set(String key, String value, long ttlMs) {
                    redisTemplate.opsForValue().set(key, value,
                            java.time.Duration.ofMillis(ttlMs));
                }
                @Override
                public String get(String key) {
                    return redisTemplate.opsForValue().get(key);
                }
                @Override
                public void del(String key) {
                    redisTemplate.delete(key);
                }
            };
            return new RedisSessionStore(config, adapter);
        }

        @Bean
        @ConditionalOnMissingBean(NonceValidator.class)
        public NonceValidator nonceValidator(
                ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
            StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
            if (redisTemplate != null) {
                NonceValidator.RedisOperations adapter = (key, value, expireSeconds) -> {
                    Boolean result = redisTemplate.opsForValue()
                            .setIfAbsent(key, value,
                                    java.time.Duration.ofSeconds(expireSeconds));
                    return Boolean.TRUE.equals(result);
                };
                return new NonceValidator(adapter);
            }
            return new NonceValidator();
        }
    }
}
