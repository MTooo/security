package com.sm2sdk.core.access;

/**
 * 客户端 ID 访问控制策略接口。
 *
 * <p>服务端通过此接口决定特定客户端能否访问特定路径。
 * 默认实现 {@code ConfigBasedAccessController} 读取 YAML 配置中的规则，
 * 用户可通过声明自定义 {@code @Bean} 完全替换授权逻辑。
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * // YAML 配置方式
 * sm2.sdk.client-access.enabled=true
 * sm2.sdk.client-access.default-policy=deny
 * sm2.sdk.client-access.rules[0].client-id=app-a
 * sm2.sdk.client-access.rules[0].paths[0]=/api/user/**\n *
 * // 编程方式
 * @Bean
 * public Sm2AccessController myAccessController() {
 *     return (clientId, path) -> myAuthService.authorize(clientId, path);
 * }
 * }</pre>
 *
 * @since 1.1
 */
@FunctionalInterface
public interface Sm2AccessController {

    /**
     * 判断客户端是否有权访问指定路径。
     *
     * @param clientId 客户端标识（从握手会话获取，非 null）
     * @param path     请求路径（如 "/api/user/query"）
     * @return true 允许访问，false 拒绝访问
     */
    boolean isAllowed(String clientId, String path);
}
