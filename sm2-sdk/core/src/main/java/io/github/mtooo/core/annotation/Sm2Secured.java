package io.github.mtooo.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 Controller 或方法需要 SM2 加解密处理。
 *
 * <p>可标注在类或方法上：
 * <ul>
 *   <li>类级别 — 该 Controller 的所有方法都走 SM2 加解密管线</li>
 *   <li>方法级别 — 仅该接口走 SM2 加解密管线（优先级高于类级别）</li>
 * </ul>
 *
 * <p>未标注此注解的端点，SDK 不会进行解密/加密处理，请求和响应原样通过。
 *
 * <h3>示例</h3>
 * <pre>{@code
 * @Sm2Secured
 * @RestController
 * public class SecureController {
 *     // 所有方法都会走 SM2 加解密
 *     @PostMapping("/api/echo")
 *     public Map echo(@RequestBody Map body) { return body; }
 * }
 *
 * @RestController
 * public class MixedController {
 *     @Sm2Secured
 *     @PostMapping("/api/secret")  // 仅此方法走 SM2
 *     public String secret(@RequestBody String body) { ... }
 *
 *     @GetMapping("/api/public")   // 明文接口
 *     public String health() { return "OK"; }
 * }
 * }</pre>
 *
 * @since 1.0.0
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Sm2Secured {
}
