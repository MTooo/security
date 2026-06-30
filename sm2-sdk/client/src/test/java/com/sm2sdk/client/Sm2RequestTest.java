package com.sm2sdk.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link Sm2Request} 的单元测试。
 *
 * <p>覆盖四种 HTTP 方法、POST 幂等键注入、加密解密流程、401 会话过期重试
 * 以及 400 错误码 21202 重试等场景。
 */
class Sm2RequestTest {

    private SessionManager sessionManager;

    private Sm2ClientConfig config;
    private ObjectMapper objectMapper;

    /** 测试用的会话对象 */
    private static final String SESSION_ID = "test-session-id";
    private static final String PEER_ID = "peer1";
    private static final String SERVER_URL = "https://api.example.com";

    @BeforeEach
    void setUp() {
        sessionManager = mock(SessionManager.class);

        // 使用真实的 Sm2ClientConfig，通过 Sm2SdkConfig 构建
        com.sm2sdk.core.model.Sm2SdkConfig sdkConfig = new com.sm2sdk.core.model.Sm2SdkConfig();
        sdkConfig.setServerUrl(SERVER_URL);
        sdkConfig.setSm2PrivateKey("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        sdkConfig.setSm2PublicKey("fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210");
        config = new Sm2ClientConfig(sdkConfig, PEER_ID);
        objectMapper = new ObjectMapper();
    }

    /**
     * 创建测试用的有效会话。
     */
    private Session createTestSession() {
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        return new Session(SESSION_ID, PEER_ID, PEER_ID, key, iv,
                System.currentTimeMillis(), 3600000L, 1000);
    }

    // ==================== GET 请求测试 ====================

    @Test
    void testGetRequestWithParams() throws Exception {
        // Given
        Session session = createTestSession();
        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.renewSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.encryptBody(eq(SESSION_ID), anyString())).thenReturn("encrypted-body");
        when(sessionManager.decryptBody(eq(SESSION_ID), anyString()))
                .thenReturn("{\"message\":\"success\"}");

        Sm2Request request = new Sm2Request("GET", "/api/data", config, sessionManager,
                objectMapper, SESSION_ID);
        request.param("key1", "value1");
        request.param("key2", "value2");

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            HttpRequest httpRequest = mock(HttpRequest.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            setupHttpMock(httpUtilMock, httpRequest, httpResponse, 200, "encrypted-response");

            // When
            String result = request.execute(String.class);

            // Then
            assertEquals("{\"message\":\"success\"}", result);

            // 验证参数被序列化为 JSON 并加密
            ArgumentCaptor<String> plainJsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(sessionManager).encryptBody(eq(SESSION_ID), plainJsonCaptor.capture());
            String plainJson = plainJsonCaptor.getValue();
            assertTrue(plainJson.contains("key1"));
            assertTrue(plainJson.contains("value1"));
            assertTrue(plainJson.contains("key2"));
            assertTrue(plainJson.contains("value2"));

            // 验证请求头中包含 Content-Type: text/plain
            verify(httpRequest, atLeastOnce()).header(eq("Content-Type"), eq("text/plain"));
        }
    }

    @Test
    void testGetRequestWithNoParams() throws Exception {
        // Given
        Session session = createTestSession();
        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.renewSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.encryptBody(eq(SESSION_ID), anyString())).thenReturn("encrypted-body");
        when(sessionManager.decryptBody(eq(SESSION_ID), anyString()))
                .thenReturn("{\"message\":\"empty\"}");

        Sm2Request request = new Sm2Request("GET", "/api/empty", config, sessionManager,
                objectMapper, SESSION_ID);

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            HttpRequest httpRequest = mock(HttpRequest.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            setupHttpMock(httpUtilMock, httpRequest, httpResponse, 200, "encrypted-response");

            // When
            String result = request.execute(String.class);

            // Then
            assertEquals("{\"message\":\"empty\"}", result);
        }
    }

    // ==================== POST 请求测试 ====================

    @Test
    void testPostRequestWithStringBody() throws Exception {
        // Given
        Session session = createTestSession();
        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.renewSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.encryptBody(eq(SESSION_ID), anyString())).thenReturn("encrypted-body");
        when(sessionManager.decryptBody(eq(SESSION_ID), anyString()))
                .thenReturn("{\"id\":42}");

        Sm2Request request = new Sm2Request("POST", "/api/create", config, sessionManager,
                objectMapper, SESSION_ID);
        request.body("{\"name\":\"test\"}");

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            HttpRequest httpRequest = mock(HttpRequest.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            setupHttpMock(httpUtilMock, httpRequest, httpResponse, 200, "encrypted-response");

            // When
            String result = request.execute(String.class);

            // Then
            assertEquals("{\"id\":42}", result);
        }
    }

    @Test
    void testPostRequestWithMapBodyAndIdempotencyKey() throws Exception {
        // Given
        Session session = createTestSession();
        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.renewSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.encryptBody(eq(SESSION_ID), anyString())).thenReturn("encrypted-body");
        when(sessionManager.decryptBody(eq(SESSION_ID), anyString()))
                .thenReturn("{\"id\":42}");

        Sm2Request request = new Sm2Request("POST", "/api/create", config, sessionManager,
                objectMapper, SESSION_ID);
        request.body(Map.of("name", "test"));

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            HttpRequest httpRequest = mock(HttpRequest.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            setupHttpMock(httpUtilMock, httpRequest, httpResponse, 200, "encrypted-response");

            // When
            String result = request.execute(String.class);

            // Then
            assertEquals("{\"id\":42}", result);

            // 验证请求体包含 _idempotencyKey
            ArgumentCaptor<String> plainJsonCaptor = ArgumentCaptor.forClass(String.class);
            verify(sessionManager).encryptBody(eq(SESSION_ID), plainJsonCaptor.capture());
            String plainJson = plainJsonCaptor.getValue();
            assertTrue(plainJson.contains("_idempotencyKey"));
            assertTrue(plainJson.contains("name"));
            assertTrue(plainJson.contains("test"));
        }
    }

    @Test
    void testPostRequestWithNoBody() throws Exception {
        // Given
        Session session = createTestSession();
        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.renewSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.encryptBody(eq(SESSION_ID), anyString())).thenReturn("encrypted-body");
        when(sessionManager.decryptBody(eq(SESSION_ID), anyString()))
                .thenReturn("{\"result\":\"ok\"}");

        Sm2Request request = new Sm2Request("POST", "/api/no-body", config, sessionManager,
                objectMapper, SESSION_ID);

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            HttpRequest httpRequest = mock(HttpRequest.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            setupHttpMock(httpUtilMock, httpRequest, httpResponse, 200, "encrypted-response");

            // When
            String result = request.execute(String.class);

            // Then
            assertEquals("{\"result\":\"ok\"}", result);

            // 无 body 时，自动创建仅含 _idempotencyKey 的 JSON
            verify(sessionManager).encryptBody(eq(SESSION_ID), contains("_idempotencyKey"));
        }
    }

    // ==================== PUT 请求测试 ====================

    @Test
    void testPutRequestWithBody() throws Exception {
        // Given
        Session session = createTestSession();
        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.renewSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.encryptBody(eq(SESSION_ID), anyString())).thenReturn("encrypted-body");
        when(sessionManager.decryptBody(eq(SESSION_ID), anyString()))
                .thenReturn("{\"updated\":true}");

        Sm2Request request = new Sm2Request("PUT", "/api/update", config, sessionManager,
                objectMapper, SESSION_ID);
        request.body(Map.of("field", "value"));

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            HttpRequest httpRequest = mock(HttpRequest.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            setupHttpMock(httpUtilMock, httpRequest, httpResponse, 200, "encrypted-response");

            // When
            String result = request.execute(String.class);

            // Then
            assertEquals("{\"updated\":true}", result);
        }
    }

    // ==================== DELETE 请求测试 ====================

    @Test
    void testDeleteRequest() throws Exception {
        // Given
        Session session = createTestSession();
        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.renewSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.encryptBody(eq(SESSION_ID), anyString())).thenReturn("encrypted-body");
        when(sessionManager.decryptBody(eq(SESSION_ID), anyString()))
                .thenReturn("{\"deleted\":true}");

        Sm2Request request = new Sm2Request("DELETE", "/api/delete/1", config, sessionManager,
                objectMapper, SESSION_ID);

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            HttpRequest httpRequest = mock(HttpRequest.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            setupHttpMock(httpUtilMock, httpRequest, httpResponse, 200, "encrypted-response");

            // When
            String result = request.execute(String.class);

            // Then
            assertEquals("{\"deleted\":true}", result);
        }
    }

    // ==================== 加密流程测试 ====================

    @Test
    void testEncryptionAndDecryptionFlow() throws Exception {
        // Given
        Session session = createTestSession();
        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.renewSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.encryptBody(eq(SESSION_ID), anyString())).thenReturn("base64-encrypted-body");
        when(sessionManager.decryptBody(eq(SESSION_ID), anyString()))
                .thenReturn("{\"result\":\"decrypted\"}");

        Sm2Request request = new Sm2Request("GET", "/api/encrypt-test", config, sessionManager,
                objectMapper, SESSION_ID);

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            HttpRequest httpRequest = mock(HttpRequest.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            setupHttpMock(httpUtilMock, httpRequest, httpResponse, 200, "encrypted-response");

            // When
            String result = request.execute(String.class);

            // Then
            assertEquals("{\"result\":\"decrypted\"}", result);

            // 验证加密调用（encryptBody 被调用了）
            verify(sessionManager).encryptBody(eq(SESSION_ID), anyString());
            // 验证解密调用（decryptBody 被调用了）
            verify(sessionManager).decryptBody(eq(SESSION_ID), anyString());
        }
    }

    // ==================== 401 会话过期重试测试 ====================

    @Test
    void testRetryOn401SessionExpired() throws Exception {
        // Given
        Session session = createTestSession();
        Session newSession = createTestSession();

        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.renewSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.encryptBody(eq(SESSION_ID), anyString())).thenReturn("encrypted-body");

        Sm2Request request = spy(new Sm2Request("GET", "/api/data", config, sessionManager,
                objectMapper, SESSION_ID));
        // 重试时 doHandshake 被调用，返回新会话
        doReturn(newSession).when(request).doHandshake();

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            HttpRequest httpRequest = mock(HttpRequest.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            setupHttpMock(httpUtilMock, httpRequest, httpResponse, 200, "retry-encrypted");

            // 第一次返回 401 + X-Session-Expired，第二次返回 200
            when(httpResponse.getStatus()).thenReturn(401, 200);
            when(httpResponse.header("X-Session-Expired")).thenReturn("true", (String) null);
            when(httpResponse.body()).thenReturn("expired-body", "retry-encrypted");

            when(sessionManager.decryptBody(anyString(), eq("retry-encrypted")))
                    .thenReturn("{\"recovered\":true}");

            // When
            String result = request.execute(String.class);

            // Then
            assertEquals("{\"recovered\":true}", result);
            // 验证握手被执行（重试时调用）
            verify(request, times(1)).doHandshake();
        }
    }

    // ==================== 400 错误码 21202 重试测试 ====================

    @Test
    void testRetryOn400Error21202() throws Exception {
        // Given
        Session session = createTestSession();
        Session newSession = createTestSession();

        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.renewSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.encryptBody(eq(SESSION_ID), anyString())).thenReturn("encrypted-body");

        Sm2Request request = spy(new Sm2Request("GET", "/api/data", config, sessionManager,
                objectMapper, SESSION_ID));
        doReturn(newSession).when(request).doHandshake();

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            HttpRequest httpRequest = mock(HttpRequest.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            setupHttpMock(httpUtilMock, httpRequest, httpResponse, 200, "retry-encrypted");

            // 第一次返回 400（body 含 21202），第二次返回 200
            when(httpResponse.getStatus()).thenReturn(400, 200);
            when(httpResponse.body()).thenReturn(
                    "{\"code\":\"21202\",\"message\":\"TAG校验失败\"}",
                    "retry-encrypted");

            when(sessionManager.decryptBody(anyString(), eq("retry-encrypted")))
                    .thenReturn("{\"recovered\":true}");

            // When
            String result = request.execute(String.class);

            // Then
            assertEquals("{\"recovered\":true}", result);
            // 验证旧会话被销毁
            verify(sessionManager).destroySession(SESSION_ID);
            // 验证握手被执行
            verify(request, times(1)).doHandshake();
        }
    }

    // ==================== 普通 HTTP 错误测试 ====================

    @Test
    void testThrowsOnHttpError() {
        // Given
        Session session = createTestSession();
        when(sessionManager.getSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.renewSession(SESSION_ID)).thenReturn(session);
        when(sessionManager.encryptBody(eq(SESSION_ID), anyString())).thenReturn("encrypted-body");

        Sm2Request request = new Sm2Request("GET", "/api/error", config, sessionManager,
                objectMapper, SESSION_ID);

        try (MockedStatic<HttpUtil> httpUtilMock = mockStatic(HttpUtil.class)) {
            HttpRequest httpRequest = mock(HttpRequest.class);
            HttpResponse httpResponse = mock(HttpResponse.class);
            setupHttpMock(httpUtilMock, httpRequest, httpResponse, 500, "Internal Server Error");

            // When & Then
            Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                    () -> request.execute(String.class));
            assertEquals(ErrorCode.HTTP_REQUEST_FAILED, ex.getErrorCode());
        }
    }

    // ==================== 链式调用方法测试 ====================

    @Test
    void testChainMethods() {
        Sm2Request request = new Sm2Request("GET", "/test", config, sessionManager,
                objectMapper, null);

        // param returns this
        assertSame(request, request.param("k1", "v1"));
        assertSame(request, request.param("k2", "v2"));

        // header returns this
        assertSame(request, request.header("X-Custom", "value"));

        // body returns this
        Object bodyObj = new Object();
        assertSame(request, request.body(bodyObj));
    }

    // ==================== 辅助方法 ====================

    /**
     * 设置 Hutool HttpUtil Mock。
     */
    private void setupHttpMock(MockedStatic<HttpUtil> httpUtilMock,
                                HttpRequest httpRequest, HttpResponse httpResponse,
                                int statusCode, String body) {
        httpUtilMock.when(() -> HttpUtil.createRequest(any(), any())).thenReturn(httpRequest);
        when(httpRequest.header(anyString(), anyString())).thenReturn(httpRequest);
        when(httpRequest.body(anyString())).thenReturn(httpRequest);
        when(httpRequest.timeout(anyInt())).thenReturn(httpRequest);
        when(httpRequest.execute()).thenReturn(httpResponse);
        when(httpResponse.getStatus()).thenReturn(statusCode);
        when(httpResponse.body()).thenReturn(body);
    }
}
