package com.sm2sdk.starter;

import com.sm2sdk.client.Sm2HttpClient;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SM2 SDK Spring Boot 自动配置。
 *
 * <p>自动装配以下组件：
 * <ul>
 *   <li>{@link Sm2SdkConfig} — SDK 全局配置</li>
 *   <li>{@link Sm2ServerConfig} — 服务端配置</li>
 *   <li>{@link Sm2KeyExchange} — SM2 密钥交换实现</li>
 *   <li>{@link Sm4Crypto} — SM4 加解密实现</li>
 *   <li>{@link SessionStore} — 会话存储（Caffeine 或 Redis）</li>
 *   <li>{@link SessionManager} — 会话管理器</li>
 *   <li>{@link Sm2HandshakeController} — 握手控制器</li>
 *   <li>{@link Sm2ServerInterceptor} — 请求拦截器</li>
 *   <li>{@link Sm2ResponseBodyAdvice} — 响应加密处理器</li>
 *   <li>{@link Sm2HttpClient} — HTTP 客户端（客户端角色）</li>
 * </ul>
 *
 * <p>使用 {@code sm2.sdk.enabled=true} 启用（默认启用）。
 */
@AutoConfiguration
@EnableConfigurationProperties(Sm2SdkProperties.class)
@ConditionalOnProperty(prefix = "sm2.sdk", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class Sm2SdkAutoConfiguration {

    // ==================== 核心组件 ====================

    /**
     * 创建 SDK 全局配置。
     */
    @Bean
    @ConditionalOnMissingBean
    public Sm2SdkConfig sm2SdkConfig(Sm2SdkProperties properties) {
        return properties.toSdkConfig();
    }

    /**
     * 创建服务端配置。
     */
    @Bean
    @ConditionalOnMissingBean
    public Sm2ServerConfig sm2ServerConfig(Sm2SdkConfig sdkConfig) {
        return new Sm2ServerConfig(sdkConfig);
    }

    /**
     * 创建 SM2 密钥交换实现。
     */
    @Bean
    @ConditionalOnMissingBean
    public Sm2KeyExchange sm2KeyExchange() {
        return new HutoolSm2KeyExchange();
    }

    /**
     * 创建 SM4 加解密实现。
     */
    @Bean
    @ConditionalOnMissingBean
    public Sm4Crypto sm4Crypto() {
        return new HutoolSm4Crypto();
    }

    // ==================== 会话存储 ====================

    /**
     * 创建 Caffeine 本地会话存储（默认）。
     *
     * <p>当 Redis 会话存储未激活时使用此实现。
     */
    @Bean
    @ConditionalOnMissingBean(SessionStore.class)
    public SessionStore sessionStore() {
        return new CaffeineSessionStore();
    }

    /**
     * 创建 Redis 会话存储（当 StringRedisTemplate 可用且配置启用时）。
     *
     * <p>此 Bean 会覆盖默认的 CaffeineSessionStore。
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
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

    // ==================== Nonce 校验 ====================

    /**
     * 创建 Nonce 重放校验器（有 Redis 时使用 Redis 精确校验，否则仅使用 Bloom Filter）。
     * 当 StringRedisTemplate 可用时优先使用此实现。
     */
    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
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

    /**
     * 创建 Nonce 重放校验器（无 Redis 的默认实现，仅使用 Bloom Filter）。
     */
    @Bean
    @ConditionalOnMissingBean(NonceValidator.class)
    public NonceValidator nonceValidatorDefault() {
        return new NonceValidator();
    }

    // ==================== 会话管理器 ====================

    /**
     * 创建会话管理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public SessionManager sessionManager(Sm2KeyExchange keyExchange, Sm4Crypto sm4Crypto,
                                         SessionStore sessionStore, Sm2SdkConfig config,
                                         ObjectProvider<NonceValidator> nonceValidatorProvider) {
        NonceValidator nonceValidator = nonceValidatorProvider.getIfAvailable();
        return new SessionManager(keyExchange, sm4Crypto, sessionStore, config,
                nonceValidator);
    }

    // ==================== 服务端组件 ====================

    /**
     * 创建握手控制器。
     */
    @Bean
    @ConditionalOnMissingBean
    public Sm2HandshakeController sm2HandshakeController(SessionManager sessionManager,
                                                         Sm2ServerConfig serverConfig) {
        return new Sm2HandshakeController(sessionManager, serverConfig);
    }

    /**
     * 创建请求拦截器。
     */
    @Bean
    @ConditionalOnMissingBean
    public Sm2ServerInterceptor sm2ServerInterceptor(SessionManager sessionManager,
                                                     Sm2ServerConfig serverConfig,
                                                     ObjectProvider<NonceValidator> nonceValidatorProvider) {
        NonceValidator nonceValidator = nonceValidatorProvider.getIfAvailable();
        return new Sm2ServerInterceptor(sessionManager, serverConfig, nonceValidator);
    }

    /**
     * 创建响应加密处理器。
     */
    @Bean
    @ConditionalOnMissingBean
    public Sm2ResponseBodyAdvice sm2ResponseBodyAdvice(SessionManager sessionManager,
                                                       Sm2ServerConfig serverConfig) {
        return new Sm2ResponseBodyAdvice(sessionManager, serverConfig);
    }

    // ==================== 客户端组件 ====================

    /**
     * 创建 SM2 HTTP 客户端（当配置了 server-url 时）。
     */
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "sm2.sdk", name = "server-url")
    public Sm2HttpClient sm2HttpClient(Sm2SdkConfig config,
                                       SessionManager sessionManager) {
        return new Sm2HttpClient(config, sessionManager,
                config.getServerUrl() != null ? "default" : "peer");
    }

    // ==================== Web MVC 配置 ====================

    /**
     * 自动注册 {@link Sm2ServerInterceptor}，拦截所有请求并解密/加密。
     */
    @Bean
    public WebMvcConfigurer sm2WebMvcConfigurer(Sm2ServerInterceptor interceptor) {
        return new WebMvcConfigurer() {
            @Override
            public void addInterceptors(InterceptorRegistry registry) {
                registry.addInterceptor(interceptor)
                        .addPathPatterns("/**")
                        .excludePathPatterns("/sm2/handshake/**");
            }
        };
    }
}
