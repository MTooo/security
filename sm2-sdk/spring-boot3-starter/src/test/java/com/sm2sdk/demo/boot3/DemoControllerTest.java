package com.sm2sdk.demo.boot3;

import com.sm2sdk.client.Sm2HttpClient;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * <h3>SM2 全链路加密集成测试</h3>
 *
 * <p>启动内嵌 Web 服务器（固定端口 19876），通过 {@link Sm2HttpClient}
 * 发起加密请求，验证：
 *
 * <pre>
 *  客户端                              服务端
 *  ① SM2 握手
 *  ② body/params → JSON → SM4 加密 →  HTTP
 *                                       ③ 解密（RequestBodyAdvice / Filter）
 *                                       ④ Controller 处理
 *                                       ⑤ 加密返回值（ResponseBodyAdvice）
 *  ⑥ SM4 解密 ← HTTP ←
 * </pre>
 *
 * <p>覆盖 POST / GET / PUT / DELETE 全方法。
 */
@SpringBootTest(
        classes = TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT
)
class DemoControllerTest {

    private static final int TEST_PORT = 19876;

    @Autowired
    private Sm2HttpClient client;

    /** 生成密钥对 & 注入配置（在 ApplicationContext 启动前执行） */
    @DynamicPropertySource
    static void registerDynamicProperties(DynamicPropertyRegistry registry) {
        String[] keys = generateSm2KeyPairHex();
        registry.add("sm2.sdk.sm2-private-key", () -> keys[0]);
        registry.add("sm2.sdk.sm2-public-key", () -> keys[1]);
        registry.add("sm2.sdk.server-url",
                () -> "http://localhost:" + TEST_PORT);
        registry.add("server.port", () -> String.valueOf(TEST_PORT));
    }

    // ═══════════════ POST ═══════════════

    @Nested
    @DisplayName("POST 请求 — JSON body 加解密")
    class PostTests {
        @Test
        @DisplayName("echo — 回显 body + serverTime")
        void testEcho() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "张三");
            body.put("msg", "hello sm2");

            @SuppressWarnings("unchecked")
            Map<String, Object> result = client.post("/api/echo")
                    .body(body).execute(Map.class);

            assertNotNull(result);
            assertEquals("张三", result.get("name"));
            assertNotNull(result.get("serverTime"));
        }

        @Test
        @DisplayName("createOrder — 创建订单")
        void testCreateOrder() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("product", "笔记本");
            body.put("quantity", 2);

            @SuppressWarnings("unchecked")
            Map<String, Object> result = client.post("/api/order")
                    .body(body).execute(Map.class);

            assertNotNull(result);
            assertEquals("ok", result.get("status"));
            assertNotNull(result.get("orderId"));
        }
    }

    // ═══════════════ GET ═══════════════

    @Nested
    @DisplayName("GET 请求 — 参数经 X-Sm2-Query 加密")
    class GetTests {
        @Test
        @DisplayName("queryUser — 带查询参数")
        void testQueryUser() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = client.get("/api/user/query")
                    .param("name", "李四").param("page", "2")
                    .execute(Map.class);

            assertNotNull(result);
            assertEquals("李四", result.get("name"));
            assertEquals("2", result.get("page"));
        }

        @Test
        @DisplayName("getUser — 路径参数，无查询参数")
        void testGetUser() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = client.get("/api/user/123")
                    .execute(Map.class);

            assertNotNull(result);
            assertEquals("123", result.get("id"));
        }

        @Test
        @DisplayName("ping — 无参数，返回纯字符串")
        void testPing() {
            String result = client.get("/api/ping").execute(String.class);
            assertEquals("pong", result);
        }
    }

    // ═══════════════ PUT ═══════════════

    @Nested
    @DisplayName("PUT 请求 — JSON body 加解密")
    class PutTests {
        @Test
        @DisplayName("updateUser — 更新用户信息")
        void testUpdateUser() {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("name", "张三-new");

            @SuppressWarnings("unchecked")
            Map<String, Object> result = client.put("/api/user/456")
                    .body(body).execute(Map.class);

            assertNotNull(result);
            assertEquals("张三-new", result.get("name"));
            assertNotNull(result.get("updatedAt"));
        }
    }

    // ═══════════════ DELETE ═══════════════

    @Nested
    @DisplayName("DELETE 请求 — 参数经 X-Sm2-Query 加密")
    class DeleteTests {
        @Test
        @DisplayName("clearCache — 清除缓存区域")
        void testClearCache() {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = client.delete("/api/cache")
                    .param("region", "user-data")
                    .execute(Map.class);

            assertNotNull(result);
            assertEquals("cleared", result.get("status"));
            assertEquals("user-data", result.get("region"));
        }
    }

    // ═══════════════ 会话复用 ═══════════════

    @Test
    @DisplayName("连续两次请求复用同一 SM2 会话")
    void testSessionReuse() {
        assertEquals("pong", client.get("/api/ping").execute(String.class));
        assertEquals("pong", client.get("/api/ping").execute(String.class));
    }

    // ═══════════════ SM2 密钥生成 ═══════════════

    private static String[] generateSm2KeyPairHex() {
        X9ECParameters x9 = GMNamedCurves.getByName("SM2P256V1");
        ECDomainParameters domain = new ECDomainParameters(
                x9.getCurve(), x9.getG(), x9.getN(), x9.getH());
        ECKeyPairGenerator gen = new ECKeyPairGenerator();
        gen.init(new ECKeyGenerationParameters(domain, new SecureRandom()));
        AsymmetricCipherKeyPair kp = gen.generateKeyPair();
        BigInteger d = ((ECPrivateKeyParameters) kp.getPrivate()).getD();
        byte[] pubKey = ((ECPublicKeyParameters) kp.getPublic()).getQ().getEncoded(false);
        return new String[]{bytesToHex(bigIntTo32Bytes(d)), bytesToHex(pubKey)};
    }

    private static byte[] bigIntTo32Bytes(BigInteger d) {
        byte[] raw = d.toByteArray(), out = new byte[32];
        int src = Math.max(0, raw.length - 32), dst = Math.max(0, 32 - raw.length);
        int len = Math.min(32, raw.length - src);
        System.arraycopy(raw, src, out, dst, len);
        return out;
    }

    private static String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte v : b) sb.append(String.format("%02x", v));
        return sb.toString();
    }
}
