package com.sm2sdk.starter;

import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.model.Sm2SdkConfig;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link Sm2ResponseBodyAdvice} 的单元测试。
 */
class Sm2ResponseBodyAdviceTest {

    private SessionManager sessionManager;
    private Sm2ServerConfig serverConfig;
    private Sm2ResponseBodyAdvice advice;

    private ServerHttpRequest serverRequest;
    private ServerHttpResponse serverResponse;
    private ServletServerHttpRequest servletRequest;
    private HttpServletRequest httpServletRequest;
    private HttpHeaders responseHeaders;

    private MethodParameter returnType;
    private Class<? extends HttpMessageConverter<?>> converterType;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Sm2SdkConfig sdkConfig = new Sm2SdkConfig();
        sdkConfig.setSessionTimeoutMs(300000L);
        serverConfig = new Sm2ServerConfig(sdkConfig);

        sessionManager = mock(SessionManager.class);
        advice = new Sm2ResponseBodyAdvice(sessionManager, serverConfig);

        // Mock ServerHttpRequest/Response
        serverRequest = mock(ServerHttpRequest.class);
        serverResponse = mock(ServerHttpResponse.class);
        responseHeaders = new HttpHeaders();
        when(serverResponse.getHeaders()).thenReturn(responseHeaders);

        servletRequest = mock(ServletServerHttpRequest.class);
        httpServletRequest = mock(HttpServletRequest.class);
        when(servletRequest.getServletRequest()).thenReturn(httpServletRequest);

        returnType = mock(MethodParameter.class);
        converterType = (Class) HttpMessageConverter.class;
    }

    // ==================== supports() ====================

    @Test
    void testSupportsAlwaysReturnsTrue() {
        assertTrue(advice.supports(returnType, converterType));
    }

    // ==================== 握手端点跳过 ====================

    @Test
    void testSkipsHandshakeInitPath() {
        when(httpServletRequest.getRequestURI()).thenReturn("/api/sm2/handshake/init");

        Object result = advice.beforeBodyWrite("hello", returnType, MediaType.TEXT_PLAIN,
                converterType, servletRequest, serverResponse);

        assertEquals("hello", result);
        verify(sessionManager, never()).encryptBody(anyString(), anyString());
    }

    @Test
    void testSkipsHandshakeConfirmPath() {
        when(httpServletRequest.getRequestURI()).thenReturn("/api/sm2/handshake/confirm");

        Object result = advice.beforeBodyWrite("hello", returnType, MediaType.TEXT_PLAIN,
                converterType, servletRequest, serverResponse);

        assertEquals("hello", result);
    }

    // ==================== 无会话时跳过 ====================

    @Test
    void testSkipsWhenNoSessionAttribute() {
        when(httpServletRequest.getRequestURI()).thenReturn("/api/data");
        when(httpServletRequest.getAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE))
                .thenReturn(null);

        Object result = advice.beforeBodyWrite("hello", returnType, MediaType.TEXT_PLAIN,
                converterType, servletRequest, serverResponse);

        assertEquals("hello", result);
        verify(sessionManager, never()).encryptBody(anyString(), anyString());
    }

    // ==================== 非 String 响应体跳过 ====================

    @Test
    void testSkipsNonStringBody() {
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session("test-session", "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/data");
        when(httpServletRequest.getAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE))
                .thenReturn(session);

        // 传入非 String 对象
        Integer nonStringBody = 42;
        Object result = advice.beforeBodyWrite(nonStringBody, returnType, MediaType.TEXT_PLAIN,
                converterType, servletRequest, serverResponse);

        assertEquals(42, result);
        verify(sessionManager, never()).encryptBody(anyString(), anyString());
    }

    @Test
    void testSkipsEmptyStringBody() {
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session("test-session", "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/data");
        when(httpServletRequest.getAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE))
                .thenReturn(session);

        Object result = advice.beforeBodyWrite("", returnType, MediaType.TEXT_PLAIN,
                converterType, servletRequest, serverResponse);

        assertEquals("", result);
        verify(sessionManager, never()).encryptBody(anyString(), anyString());
    }

    // ==================== 正常加密 ====================

    @Test
    void testEncryptsResponseSuccessfully() {
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session("test-session", "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/data");
        when(httpServletRequest.getAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE))
                .thenReturn(session);
        when(sessionManager.encryptBody(eq("test-session"), eq("{\"result\":\"ok\"}")))
                .thenReturn("encrypted-base64");

        Object result = advice.beforeBodyWrite("{\"result\":\"ok\"}", returnType,
                MediaType.APPLICATION_JSON, converterType, servletRequest, serverResponse);

        assertEquals("encrypted-base64", result);
        assertEquals(MediaType.TEXT_PLAIN, responseHeaders.getContentType());
        verify(sessionManager).encryptBody("test-session", "{\"result\":\"ok\"}");
    }

    // ==================== 加密失败 ====================

    @Test
    void testPropagatesEncryptionException() {
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session("test-session", "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/data");
        when(httpServletRequest.getAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE))
                .thenReturn(session);
        when(sessionManager.encryptBody(anyString(), anyString()))
                .thenThrow(new Sm2SdkException(ErrorCode.SM4_ENCRYPT_FAILED, "加密失败"));

        assertThrows(Sm2SdkException.class, () ->
                advice.beforeBodyWrite("plaintext", returnType, MediaType.TEXT_PLAIN,
                        converterType, servletRequest, serverResponse));
    }

    // ==================== 非 ServletServerHttpRequest ====================

    @Test
    void testUsesUriPathWhenNotServletRequest() {
        when(serverRequest.getURI()).thenReturn(java.net.URI.create("/api/data"));

        // 非 ServletServerHttpRequest 无法获取 session 属性 → 跳过
        Object result = advice.beforeBodyWrite("hello", returnType, MediaType.TEXT_PLAIN,
                converterType, serverRequest, serverResponse);

        assertEquals("hello", result);
    }

    // ==================== 构造函数校验 ====================

    @Test
    void testConstructorRejectsNullSessionManager() {
        assertThrows(NullPointerException.class, () ->
                new Sm2ResponseBodyAdvice(null, serverConfig));
    }

    @Test
    void testConstructorRejectsNullConfig() {
        assertThrows(NullPointerException.class, () ->
                new Sm2ResponseBodyAdvice(sessionManager, null));
    }
}
