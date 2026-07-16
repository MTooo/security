package io.github.mtooo.demo.boot2;

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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 演示三种使用方式：
 * <ol>
 *   <li>被动响应（服务端）：标记 {@link Sm2Secured} 的端点自动加解密</li>
 *   <li>主动调用（客户端）：注入 {@link Sm2HttpClient} 发加密请求</li>
 *   <li>测试工具：手动构造密文，方便用 Apifox/curl 测试</li>
 * </ol>
 */
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

    /** 回显：POST + JSON Body，最基础的加密接口 */
    @Sm2Secured
    @PostMapping("/api/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        body.put("serverTime", System.currentTimeMillis());
        return body;
    }

    /** GET 带查询参数：模拟用户查询 */
    @Sm2Secured
    @GetMapping("/api/user/query")
    public Map<String, Object> queryUser(@RequestParam("name") String name,
                                         @RequestParam(value = "page", defaultValue = "1") int page) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("page", page);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("id", "U001");
        record.put("name", name);
        record.put("age", 28);
        result.put("records", Collections.singletonList(record));
        return result;
    }

    /** GET 路径参数：模拟根据 ID 查详情 */
    @Sm2Secured
    @GetMapping("/api/user/{id}")
    public Map<String, Object> getUser(@PathVariable("id") String id) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", id);
        user.put("name", "用户-" + id);
        user.put("email", id + "@example.com");
        return user;
    }

    /** POST 提交：模拟创建订单 */
    @Sm2Secured
    @PostMapping("/api/order/create")
    public Map<String, Object> createOrder(@RequestBody Map<String, Object> order) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("orderId", UUID.randomUUID().toString().substring(0, 8));
        result.put("status", "created");
        result.put("amount", order.getOrDefault("amount", 0));
        result.put("product", order.getOrDefault("product", "unknown"));
        return result;
    }

    /** PUT 更新：模拟更新用户信息 */
    @Sm2Secured
    @PutMapping("/api/user/update")
    public Map<String, Object> updateUser(@RequestBody Map<String, Object> user) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", user.getOrDefault("id", "?"));
        result.put("updated", true);
        result.put("fields", user.keySet());
        return result;
    }

    /** DELETE 删除：模拟清理缓存 */
    @Sm2Secured
    @DeleteMapping("/api/cache/clear")
    public Map<String, Object> clearCache(@RequestParam(value = "scope", defaultValue = "all") String scope) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cleared", true);
        result.put("scope", scope);
        return result;
    }

    /** GET 无参：模拟对方系统状态查询 */
    @Sm2Secured
    @GetMapping("/api/peer/info")
    public Map<String, Object> peerInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("peerId", "demo-peer");
        info.put("status", "online");
        info.put("uptime", System.currentTimeMillis());
        return info;
    }

    // ============================================================
    //  主动调用（客户端角色）— 明文入口，内部用 Sm2HttpClient 调对方
    // ============================================================

    /** 模拟客户端：GET 查询用户 */
    @GetMapping("/api/client/query-user")
    public Object clientQueryUser(@RequestParam(defaultValue = "张三") String name) {
        if (sm2Client == null) return err("请配置 sm2.sdk.server-url");
        return sm2Client.get("/api/user/query").param("name", name).execute(Object.class);
    }

    /** 模拟客户端：GET 查详情 */
    @GetMapping("/api/client/get-user")
    public Object clientGetUser(@RequestParam(defaultValue = "U001") String id) {
        if (sm2Client == null) return err("请配置 sm2.sdk.server-url");
        return sm2Client.get("/api/user/" + id).execute(Object.class);
    }

    /** 模拟客户端：POST 创建订单 */
    @PostMapping("/api/client/create-order")
    public Object clientCreateOrder(@RequestBody Map<String, Object> order) {
        if (sm2Client == null) return err("请配置 sm2.sdk.server-url");
        return sm2Client.post("/api/order/create").body(order).execute(Object.class);
    }

    /** 模拟客户端：PUT 更新用户 */
    @PutMapping("/api/client/update-user")
    public Object clientUpdateUser(@RequestBody Map<String, Object> user) {
        if (sm2Client == null) return err("请配置 sm2.sdk.server-url");
        return sm2Client.put("/api/user/update").body(user).execute(Object.class);
    }

    /** 模拟客户端：DELETE 清理缓存 */
    @DeleteMapping("/api/client/clear-cache")
    public Object clientClearCache(@RequestParam(defaultValue = "all") String scope) {
        if (sm2Client == null) return err("请配置 sm2.sdk.server-url");
        return sm2Client.delete("/api/cache/clear").param("scope", scope).execute(Object.class);
    }

    /** 模拟客户端：GET 查 peer 状态 */
    @GetMapping("/api/call-peer")
    public Object callPeer() {
        if (sm2Client == null) return err("请配置 sm2.sdk.server-url");
        return sm2Client.get("/api/peer/info").execute(Object.class);
    }

    // ============================================================
    //  测试工具
    // ============================================================

    /** 自我握手获取测试用 sessionId */
    @GetMapping("/api/session")
    public Map<String, String> createSession() {
        byte[] privateKey = SessionManager.hexToBytes(config.getSm2PrivateKey());
        byte[] publicKey = SessionManager.hexToBytes(config.getSm2PublicKey());
        HandshakeInit init = keyExchange.buildInitRequest("demo", privateKey, publicKey, "demo");
        Session session = sessionManager.handleIncomingHandshake(init);
        return Collections.singletonMap("sessionId", session.getSessionId());
    }

    /**
     * 手动加密工具：sessionId + 明文 → 密文。
     * Apifox 用法：Body JSON → {"plaintext": "{\"name\":\"张三\"}"}
     */
    @PostMapping("/api/encrypt")
    public Map<String, String> encrypt(@RequestBody Map<String, String> body,
                                       @RequestHeader("X-Session-Id") String sessionId) {
        String plaintext = body.get("plaintext");
        if (plaintext == null || plaintext.isEmpty()) {
            return Collections.singletonMap("error", "缺少 plaintext 字段");
        }
        return Collections.singletonMap("ciphertext",
                sessionManager.encryptBody(sessionId, plaintext));
    }

    private static Map<String, Object> err(String msg) {
        return Collections.singletonMap("error", (Object) msg);
    }
}
