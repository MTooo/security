package com.sm2sdk.demo.boot2;

import com.sm2sdk.client.Sm2HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 演示两种使用方式：
 * <ol>
 *   <li>被动响应：接收加密请求 → SDK 自动解密 → 处理后自动加密返回</li>
 *   <li>主动调用：注入 {@link Sm2HttpClient} 发加密请求</li>
 * </ol>
 */
@RestController
public class DemoController {

    @Autowired(required = false)
    private Sm2HttpClient sm2Client;

    /**
     * 被动响应：客户端发加密请求到这个端点 → SDK 自动解密 → 返回自动加密。
     */
    @PostMapping("/api/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        body.put("serverTime", System.currentTimeMillis());
        return body;
    }

    /**
     * 主动调用：通过 Sm2HttpClient 向对方系统发加密 GET 请求。
     */
    @GetMapping("/api/call-peer")
    public Object callPeer() {
        if (sm2Client == null) {
            return Map.of("error", "Sm2HttpClient 未启用，请配置 sm2.sdk.server-url");
        }
        return sm2Client.get("/api/peer/info").execute(Object.class);
    }
}
