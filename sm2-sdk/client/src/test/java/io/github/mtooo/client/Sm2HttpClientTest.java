package io.github.mtooo.client;

import io.github.mtooo.core.model.Sm2SdkConfig;
import io.github.mtooo.core.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link Sm2HttpClient} 的单元测试。
 *
 * <p>验证 get/post/put/delete 方法返回正确的 {@link Sm2Request} 实例，
 * 并正确配置了 HTTP 方法、路径、服务器 URL 和会话 ID。
 */
class Sm2HttpClientTest {

    private SessionManager sessionManager;

    private Sm2HttpClient client;
    private static final String SERVER_URL = "https://api.example.com";
    private static final String PEER_ID = "test-peer";

    @BeforeEach
    void setUp() {
        sessionManager = mock(SessionManager.class);

        Sm2SdkConfig config = Sm2SdkConfig.builder()
                .serverUrl(SERVER_URL)
                .sm2PrivateKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef")
                .sm2PublicKey("fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210")
                .build();

        client = new Sm2HttpClient(config, sessionManager, PEER_ID);
    }

    @Test
    void testGetReturnsSm2Request() {
        // When
        Sm2Request request = client.get("/api/data");

        // Then
        assertNotNull(request);
        assertEquals("GET", getFieldValue(request, "httpMethod"));
        assertEquals("/api/data", getFieldValue(request, "path"));
    }

    @Test
    void testPostReturnsSm2Request() {
        // When
        Sm2Request request = client.post("/api/create");

        // Then
        assertNotNull(request);
        assertEquals("POST", getFieldValue(request, "httpMethod"));
        assertEquals("/api/create", getFieldValue(request, "path"));
    }

    @Test
    void testPutReturnsSm2Request() {
        // When
        Sm2Request request = client.put("/api/update");

        // Then
        assertNotNull(request);
        assertEquals("PUT", getFieldValue(request, "httpMethod"));
        assertEquals("/api/update", getFieldValue(request, "path"));
    }

    @Test
    void testDeleteReturnsSm2Request() {
        // When
        Sm2Request request = client.delete("/api/delete/1");

        // Then
        assertNotNull(request);
        assertEquals("DELETE", getFieldValue(request, "httpMethod"));
        assertEquals("/api/delete/1", getFieldValue(request, "path"));
    }

    @Test
    void testMultipleRequestsShareSameConfig() {
        // When
        Sm2Request getReq = client.get("/api/data");
        Sm2Request postReq = client.post("/api/create");

        // Then - 使用相同的配置
        Sm2ClientConfig getConfig = getFieldValue(getReq, "config");
        Sm2ClientConfig postConfig = getFieldValue(postReq, "config");
        assertSame(getConfig, postConfig);
        assertEquals(SERVER_URL, getConfig.getServerUrl());
        assertEquals(PEER_ID, getConfig.getPeerId());
    }

    @Test
    void testGetConfig() {
        // When
        Sm2ClientConfig config = client.getConfig();

        // Then
        assertNotNull(config);
        assertEquals(SERVER_URL, config.getServerUrl());
        assertEquals(PEER_ID, config.getPeerId());
    }

    @Test
    void testConstructorWithSm2ClientConfig() {
        // Given
        Sm2SdkConfig sdkConfig = Sm2SdkConfig.builder()
                .serverUrl(SERVER_URL)
                .sm2PrivateKey("abc")
                .build();
        Sm2ClientConfig clientConfig = new Sm2ClientConfig(sdkConfig, "direct-peer");

        // When
        Sm2HttpClient client2 = new Sm2HttpClient(clientConfig, sessionManager);

        // Then
        assertEquals("direct-peer", client2.getConfig().getPeerId());
        assertEquals(SERVER_URL, client2.getConfig().getServerUrl());
    }

    @Test
    void testPreExistingSessionId() {
        // Given
        client.setCurrentSessionId("pre-existing-session");

        // When
        Sm2Request request = client.get("/api/data");

        // Then - 请求继承了客户端的当前会话 ID
        String sessionId = getFieldValue(request, "currentSessionId");
        assertEquals("pre-existing-session", sessionId);

        // 验证客户端也保持
        assertEquals("pre-existing-session", client.getCurrentSessionId());
    }

    // ==================== 辅助方法 ====================

    /**
     * 通过反射获取对象的私有字段值。
     */
    @SuppressWarnings("unchecked")
    private static <T> T getFieldValue(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return (T) field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("反射获取字段失败: " + fieldName, e);
        }
    }
}
