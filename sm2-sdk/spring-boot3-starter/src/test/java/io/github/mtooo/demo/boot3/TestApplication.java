package io.github.mtooo.demo.boot3;

import io.github.mtooo.core.annotation.Sm2Secured;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * SM2 全链路测试的 Spring Boot 应用入口。
 * 显式注册 {@link SecuredApi} Controller bean 避免组件扫描的嵌套类发现难题。
 */
@SpringBootApplication
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    // ================================================================
    //  @Sm2Secured 测试 Controller（@ComponentScan 自动发现）
    // ================================================================

    @RestController
    @RequestMapping("/api")
    @Sm2Secured
    public static class SecuredApi {

        @PostMapping("/echo")
        public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
            Map<String, Object> result = new HashMap<>(body);
            result.put("serverTime", System.currentTimeMillis());
            return result;
        }

        @PostMapping("/order")
        public Map<String, Object> createOrder(@RequestBody Map<String, Object> body) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "ok");
            result.put("orderId", "ORD-" + System.currentTimeMillis());
            result.put("product", body.getOrDefault("product", "unknown"));
            result.put("quantity", body.getOrDefault("quantity", 1));
            return result;
        }

        @GetMapping("/user/query")
        public Map<String, Object> queryUser(
                @RequestParam("name") String name,
                @RequestParam(value = "page", defaultValue = "1") String page) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("name", name);
            result.put("page", page);
            return result;
        }

        @GetMapping("/user/{id}")
        public Map<String, Object> getUser(@PathVariable("id") String id) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", id);
            result.put("name", "王五");
            return result;
        }

        @PutMapping("/user/{id}")
        public Map<String, Object> updateUser(
                @PathVariable("id") String id,
                @RequestBody Map<String, Object> body) {
            Map<String, Object> result = new HashMap<>(body);
            result.put("id", id);
            result.put("updatedAt", System.currentTimeMillis());
            return result;
        }

        @DeleteMapping("/cache")
        public Map<String, Object> clearCache(
                @RequestParam(value = "region", defaultValue = "all") String region) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("status", "cleared");
            result.put("region", region);
            return result;
        }

        @GetMapping("/ping")
        public String ping() {
            return "pong";
        }
    }
}
