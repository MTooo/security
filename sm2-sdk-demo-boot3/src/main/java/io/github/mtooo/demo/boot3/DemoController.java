package io.github.mtooo.demo.boot3;

import io.github.mtooo.client.Sm2HttpClient;
import io.github.mtooo.core.annotation.Sm2Secured;
import io.github.mtooo.core.crypto.Sm2KeyExchange;
import io.github.mtooo.core.model.HandshakeInit;
import io.github.mtooo.core.model.Sm2SdkConfig;
import io.github.mtooo.core.session.Session;
import io.github.mtooo.core.session.SessionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class DemoController {

    @Autowired(required = false)
    private Sm2HttpClient sm2Client;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private Sm2KeyExchange keyExchange;

    @Autowired
    private Sm2SdkConfig config;


    // ============================================================
    //  被动响应（服务端角色）— @Sm2Secured 自动解密请求、加密响应
    // ============================================================

    @Sm2Secured
    @PostMapping("/api/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        body.put("serverTime", System.currentTimeMillis());
        return body;
    }

    @Sm2Secured
    @GetMapping("/api/user/query")
    public Map<String, Object> queryUser(@RequestParam("name") String name,
                                         @RequestParam(value = "page", defaultValue = "1") int page) {
        return Map.of("name", name, "page", page,
                "records", List.of(Map.of("id", "U001", "name", name, "age", 28)));
    }

    @Sm2Secured
    @GetMapping("/api/user/{id}")
    public Map<String, Object> getUser(@PathVariable("id") String id) {
        return Map.of("id", id, "name", "用户-" + id, "email", id + "@example.com");
    }

    @Sm2Secured
    @PostMapping("/api/order/create")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> order) {
        return Map.of("orderId", UUID.randomUUID().toString().substring(0, 8),
                "status", "created",
                "amount", order.getOrDefault("amount", 0),
                "product", order.getOrDefault("product", "unknown"));
    }

    @Sm2Secured
    @PutMapping("/api/user/update")
    public Map<String, Object> updateUser(@RequestBody Map<String, Object> user) {
        return Map.of("id", user.getOrDefault("id", "?"), "updated", true,
                "fields", user.keySet());
    }

    @Sm2Secured
    @DeleteMapping("/api/cache/clear")
    public Map<String, Object> clearCache(@RequestParam(value = "scope", defaultValue = "all") String scope) {
        return Map.of("cleared", true, "scope", scope);
    }

    @Sm2Secured
    @GetMapping("/api/peer/info")
    public Map<String, Object> peerInfo() {
        return Map.of("peerId", "demo-peer", "status", "online",
                "uptime", System.currentTimeMillis());
    }

    // ============================================================
    //  主动调用（客户端角色）— 明文入口，内部用 Sm2HttpClient 调对方
    // ============================================================

    @GetMapping("/api/client/query-user")
    public Object clientQueryUser(@RequestParam(defaultValue = "张三") String name) {
        if (sm2Client == null) return Map.of("error", "请配置 sm2.sdk.server-url");
        return sm2Client.get("/api/user/query").param("name", name).execute(Object.class);
    }

    @GetMapping("/api/client/get-user")
    public Object clientGetUser(@RequestParam(defaultValue = "U001") String id) {
        if (sm2Client == null) return Map.of("error", "请配置 sm2.sdk.server-url");
        return sm2Client.get("/api/user/" + id).execute(Object.class);
    }

    @PostMapping("/api/client/create-order")
    public Object clientCreateOrder(@RequestBody Map<String, Object> order) {
        if (sm2Client == null) return Map.of("error", "请配置 sm2.sdk.server-url");
        return sm2Client.post("/api/order/create").body(order).execute(Object.class);
    }

    @PutMapping("/api/client/update-user")
    public Object clientUpdateUser(@RequestBody Map<String, Object> user) {
        if (sm2Client == null) return Map.of("error", "请配置 sm2.sdk.server-url");
        return sm2Client.put("/api/user/update").body(user).execute(Object.class);
    }

    @DeleteMapping("/api/client/clear-cache")
    public Object clientClearCache(@RequestParam(defaultValue = "all") String scope) {
        if (sm2Client == null) return Map.of("error", "请配置 sm2.sdk.server-url");
        return sm2Client.delete("/api/cache/clear").param("scope", scope).execute(Object.class);
    }

    @GetMapping("/api/call-peer")
    public Object callPeer() {
        if (sm2Client == null) return Map.of("error", "请配置 sm2.sdk.server-url");
        return sm2Client.get("/api/peer/info").execute(Object.class);
    }

    // ============================================================
    //  测试工具
    // ============================================================

    @GetMapping("/api/session")
    public Map<String, String> createSession() {
        byte[] privateKey = SessionManager.hexToBytes(config.getSm2PrivateKey());
        byte[] publicKey = SessionManager.hexToBytes(config.getSm2PublicKey());
        HandshakeInit init = keyExchange.buildInitRequest("demo", privateKey, publicKey, "demo");
        Session session = sessionManager.handleIncomingHandshake(init);
        return Map.of("sessionId", session.getSessionId());
    }

    @PostMapping("/api/encrypt")
    public Map<String, String> encrypt(@RequestBody Map<String, String> body,
                                       @RequestHeader("X-Session-Id") String sessionId) {
        String plaintext = body.get("plaintext");
        if (plaintext == null || plaintext.isEmpty()) {
            return Map.of("error", "缺少 plaintext 字段");
        }
        return Map.of("ciphertext", sessionManager.encryptBody(sessionId, plaintext));
    }
}
