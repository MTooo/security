package io.github.mtooo.demo.boot2;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * SM2 SDK 演示 — Spring Boot 2.7 + JDK 8。
 * <p>
 * 启动后 SDK 自动注册握手端点、请求拦截器和响应加密处理器。
 * 无需额外代码。
 */
@SpringBootApplication
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
