package com.sm2sdk.starter;

import com.sm2sdk.core.annotation.Sm2Secured;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.model.Sm2SdkConfig;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
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

    private Class<? extends HttpMessageConverter<?>> converterType;
    private ByteArrayOutputStream responseBodyStream;

    // ==================== 测试用 Controller ====================

    /** 无注解的普通 Controller */
    static class PlainController {
        @PostMapping("/api/plain")
        public String echo(@RequestBody String body) { return body; }
    }

    /** 类级别 @Sm2Secured 注解 */
    @Sm2Secured
    static class SecuredController {
        @PostMapping("/api/echo")
        public String echo(@RequestBody String body) { return body; }
    }

    /** 混合：仅部分方法标记 @Sm2Secured */
    static class MixedController {
        @Sm2Secured
        @PostMapping("/api/secured")
        public String secured(@RequestBody String body) { return body; }

        @PostMapping("/api/plain")
        public String plain(@RequestBody String body) { return body; }
    }

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
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

        responseBodyStream = new ByteArrayOutputStream();
        when(serverResponse.getBody()).thenReturn(responseBodyStream);

        converterType = (Class) HttpMessageConverter.class;
    }

    /** 创建 MethodParameter（代表返回值类型）。 */
    private static MethodParameter methodReturnType(Class<?> clazz, String methodName, Class<?>... paramTypes) {
        try {
            Method method = clazz.getMethod(methodName, paramTypes);
            return new MethodParameter(method, -1);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    // ==================== supports() ====================

    @Test
    void testSupportsAlwaysReturnsTrue() {
        MethodParameter returnType = methodReturnType(PlainController.class, "echo", String.class);
        assertTrue(advice.supports(returnType, converterType));
    }

    // ==================== 握手端点跳过 ====================

    @Test
    void testSkipsHandshakeInitPath() {
        MethodParameter returnType = methodReturnType(PlainController.class, "echo", String.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/api/sm2/handshake/init");

        Object result = advice.beforeBodyWrite("hello", returnType, MediaType.TEXT_PLAIN,
                converterType, servletRequest, serverResponse);

        assertEquals("hello", result);
        verify(sessionManager, never()).encryptBody(anyString(), anyString());
    }

    @Test
    void testSkipsHandshakeConfirmPath() {
        MethodParameter returnType = methodReturnType(PlainController.class, "echo", String.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/api/sm2/handshake/confirm");

        Object result = advice.beforeBodyWrite("hello", returnType, MediaType.TEXT_PLAIN,
                converterType, servletRequest, serverResponse);

        assertEquals("hello", result);
    }

    // ==================== @Sm2Secured 注解检查 ====================

    @Test
    void testSkipsNonAnnotatedMethod() {
        // PlainController 无 @Sm2Secured
        MethodParameter returnType = methodReturnType(PlainController.class, "echo", String.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/api/plain");

        Object result = advice.beforeBodyWrite("hello", returnType, MediaType.TEXT_PLAIN,
                converterType, servletRequest, serverResponse);

        assertEquals("hello", result);
        verify(sessionManager, never()).encryptBody(anyString(), anyString());
    }

    @Test
    void testSkipsNonAnnotatedMethodInMixedController() {
        // MixedController.plain() 无 @Sm2Secured
        MethodParameter returnType = methodReturnType(MixedController.class, "plain", String.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/api/plain");

        Object result = advice.beforeBodyWrite("hello", returnType, MediaType.TEXT_PLAIN,
                converterType, servletRequest, serverResponse);

        assertEquals("hello", result);
        verify(sessionManager, never()).encryptBody(anyString(), anyString());
    }

    // ==================== 无会话时跳过 ====================

    @Test
    void testSkipsWhenNoSessionAttribute() {
        MethodParameter returnType = methodReturnType(SecuredController.class, "echo", String.class);
        when(httpServletRequest.getRequestURI()).thenReturn("/api/data");
        when(httpServletRequest.getAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE))
                .thenReturn(null);

        Object result = advice.beforeBodyWrite("hello", returnType, MediaType.TEXT_PLAIN,
                converterType, servletRequest, serverResponse);

        assertEquals("hello", result);
        verify(sessionManager, never()).encryptBody(anyString(), anyString());
    }

    // ==================== 非 String 响应体序列化后加密 ====================

    @Test
    @DisplayName("Should serialize non-String body via Jackson and encrypt")
    void testSerializesAndEncryptsNonStringBody() {
        MethodParameter returnType = methodReturnType(SecuredController.class, "echo", String.class);
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session("test-session", "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/data");
        when(httpServletRequest.getAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE))
                .thenReturn(session);
        when(sessionManager.encryptBody(eq("test-session"), eq("42")))
                .thenReturn("encrypted-int");

        // Integer → Jackson serializes to "42" → encrypts → writes to stream → returns null
        Object result = advice.beforeBodyWrite(42, returnType, MediaType.TEXT_PLAIN,
                converterType, servletRequest, serverResponse);

        assertNull(result);
        assertEquals("encrypted-int", responseBodyStream.toString(StandardCharsets.UTF_8));
        verify(sessionManager).encryptBody("test-session", "42");
    }

    @Test
    void testSkipsEmptyStringBody() {
        MethodParameter returnType = methodReturnType(SecuredController.class, "echo", String.class);
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
        MethodParameter returnType = methodReturnType(SecuredController.class, "echo", String.class);
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

        assertNull(result);
        assertEquals("encrypted-base64", responseBodyStream.toString(StandardCharsets.UTF_8));
        assertEquals(MediaType.TEXT_PLAIN, responseHeaders.getContentType());
        verify(sessionManager).encryptBody("test-session", "{\"result\":\"ok\"}");
    }

    @Test
    void testEncryptsResponseWithMethodLevelAnnotation() {
        // MixedController.secured() 方法级别 @Sm2Secured
        MethodParameter returnType = methodReturnType(MixedController.class, "secured", String.class);
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session("test-session", "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);

        when(httpServletRequest.getRequestURI()).thenReturn("/api/secured");
        when(httpServletRequest.getAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE))
                .thenReturn(session);
        when(sessionManager.encryptBody(eq("test-session"), eq("secret-data")))
                .thenReturn("encrypted");

        Object result = advice.beforeBodyWrite("secret-data", returnType,
                MediaType.TEXT_PLAIN, converterType, servletRequest, serverResponse);

        assertNull(result);
        assertEquals("encrypted", responseBodyStream.toString(StandardCharsets.UTF_8));
        verify(sessionManager).encryptBody("test-session", "secret-data");
    }

    // ==================== 加密失败 ====================

    @Test
    void testPropagatesEncryptionException() {
        MethodParameter returnType = methodReturnType(SecuredController.class, "echo", String.class);
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
        MethodParameter returnType = methodReturnType(PlainController.class, "echo", String.class);
        when(serverRequest.getURI()).thenReturn(java.net.URI.create("/api/data"));

        // 非 ServletServerHttpRequest + 非 @Sm2Secured 方法 → 跳过
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
