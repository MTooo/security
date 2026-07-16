package io.github.mtooo.starter;

import io.github.mtooo.core.annotation.Sm2Secured;
import io.github.mtooo.core.exception.ErrorCode;
import io.github.mtooo.core.exception.Sm2SdkException;
import io.github.mtooo.core.model.Sm2SdkConfig;
import io.github.mtooo.core.nonce.NonceValidator;
import io.github.mtooo.core.session.Session;
import io.github.mtooo.core.session.SessionManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.method.HandlerMethod;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link Sm2ServerInterceptor} 的单元测试。
 */
class Sm2ServerInterceptorTest {

    private SessionManager sessionManager;
    private Sm2ServerConfig serverConfig;
    private Sm2ServerInterceptor interceptor;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private StringWriter responseWriter;

    // ==================== 测试用 Controller ====================

    /** 无注解的普通 Controller */
    static class PlainController {
        @PostMapping("/api/plain")
        public Map<String, Object> echo(@RequestBody Map<String, Object> body) { return body; }
    }

    /** 类级别 @Sm2Secured 注解 */
    @Sm2Secured
    static class SecuredController {
        @PostMapping("/api/echo")
        public Map<String, Object> echo(@RequestBody Map<String, Object> body) { return body; }
    }

    /** 混合：仅部分方法标记 @Sm2Secured */
    static class MixedController {
        @Sm2Secured
        @PostMapping("/api/secured")
        public Map<String, Object> secured(@RequestBody Map<String, Object> body) { return body; }

        @PostMapping("/api/plain")
        public Map<String, Object> plain(@RequestBody Map<String, Object> body) { return body; }
    }

    @BeforeEach
    void setUp() throws Exception {
        Sm2SdkConfig sdkConfig = new Sm2SdkConfig();
        sdkConfig.setSessionTimeoutMs(300000L);
        serverConfig = new Sm2ServerConfig(sdkConfig);

        sessionManager = mock(SessionManager.class);
        interceptor = new Sm2ServerInterceptor(sessionManager, serverConfig);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        responseWriter = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(responseWriter));
    }

    // ==================== 握手端点跳过测试 ====================

    @Test
    void testPreHandleSkipsHandshakeInitPath() throws Exception {
        when(request.getRequestURI()).thenReturn("/handshake/init");

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
        verify(sessionManager, never()).getSession(anyString());
    }

    @Test
    void testPreHandleSkipsHandshakeConfirmPath() throws Exception {
        when(request.getRequestURI()).thenReturn("/handshake/confirm");

        boolean result = interceptor.preHandle(request, response, null);

        assertTrue(result);
    }

    // ==================== @Sm2Secured 注解检查 ====================

    @Test
    void testPreHandleSkipsNonAnnotatedHandler() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/plain");
        PlainController controller = new PlainController();
        Method method = PlainController.class.getMethod("echo", Map.class);
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        // 无 @Sm2Secured，直接放行，不检查 session
        assertTrue(result);
        verify(sessionManager, never()).getSession(anyString());
    }

    @Test
    void testPreHandleProcessesClassLevelAnnotation() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/echo");
        SecuredController controller = new SecuredController();
        Method method = SecuredController.class.getMethod("echo", Map.class);
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        // @Sm2Secured 在类上 → 应检查 session
        when(request.getHeader("X-Session-Id")).thenReturn("session-1");
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session("session-1", "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);
        when(sessionManager.getSession("session-1")).thenReturn(session);
        when(request.getContentLength()).thenReturn(-1);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
        verify(sessionManager).getSession("session-1");
    }

    @Test
    void testPreHandleProcessesMethodLevelAnnotation() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/secured");
        MixedController controller = new MixedController();
        Method method = MixedController.class.getMethod("secured", Map.class);
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        // @Sm2Secured 在方法上 → 应检查 session
        when(request.getHeader("X-Session-Id")).thenReturn("session-2");
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session("session-2", "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);
        when(sessionManager.getSession("session-2")).thenReturn(session);
        when(request.getContentLength()).thenReturn(-1);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
        verify(sessionManager).getSession("session-2");
    }

    @Test
    void testPreHandleSkipsNonAnnotatedMethodInMixedController() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/plain");
        MixedController controller = new MixedController();
        Method method = MixedController.class.getMethod("plain", Map.class);
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        // 类无注解，方法也无注解 → 放行
        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
        verify(sessionManager, never()).getSession(anyString());
    }

    @Test
    void testPreHandleSkipsNonHandlerMethod() throws Exception {
        // handler 不是 HandlerMethod（老式 Controller 等）
        when(request.getRequestURI()).thenReturn("/api/data");

        boolean result = interceptor.preHandle(request, response, "not-a-handler-method");

        assertTrue(result);
        verify(sessionManager, never()).getSession(anyString());
    }

    // ==================== 缺少请求头测试 ====================

    @Test
    void testPreHandleThrowsOnMissingSessionId() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/echo");
        SecuredController controller = new SecuredController();
        Method method = SecuredController.class.getMethod("echo", Map.class);
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        assertThrows(Sm2SdkException.class,
                () -> interceptor.preHandle(request, response, handlerMethod));
    }

    // ==================== 会话过期测试 ====================

    @Test
    void testPreHandleReturnsFalseOnExpiredSession() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/echo");
        SecuredController controller = new SecuredController();
        Method method = SecuredController.class.getMethod("echo", Map.class);
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        when(request.getHeader("X-Session-Id")).thenReturn("expired-session");
        when(sessionManager.getSession("expired-session"))
                .thenThrow(new Sm2SdkException(ErrorCode.SESSION_EXPIRED, "过期"));

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertFalse(result);
        verify(response).setStatus(401);
        verify(response).setHeader("X-Session-Expired", "true");
    }

    // ==================== 正常会话验证测试 ====================

    @Test
    void testPreHandleValidatesSessionSuccessfully() throws Exception {
        String sessionId = "valid-session";
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session(sessionId, "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);

        when(request.getRequestURI()).thenReturn("/api/echo");
        when(request.getHeader("X-Session-Id")).thenReturn(sessionId);
        when(sessionManager.getSession(sessionId)).thenReturn(session);

        SecuredController controller = new SecuredController();
        Method method = SecuredController.class.getMethod("echo", Map.class);
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        boolean result = interceptor.preHandle(request, response, handlerMethod);

        assertTrue(result);
        verify(request).setAttribute(eq(Sm2ServerInterceptor.SESSION_ATTRIBUTE),
                any(Session.class));
    }

    // ==================== Nonce 校验测试 ====================

    @Test
    void testPreHandleValidatesNonce() throws Exception {
        // Given
        String sessionId = "valid-session";
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session(sessionId, "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);

        NonceValidator nonceValidator = mock(NonceValidator.class);
        when(nonceValidator.isDuplicate("test-nonce")).thenReturn(false);

        Sm2ServerConfig configWithNonce = new Sm2ServerConfig(
                new Sm2SdkConfig(),
                Sm2ServerConfig.DEFAULT_HANDSHAKE_INIT_PATH,
                Sm2ServerConfig.DEFAULT_HANDSHAKE_CONFIRM_PATH,
                true, null);

        Sm2ServerInterceptor interceptorWithNonce = new Sm2ServerInterceptor(
                sessionManager, configWithNonce, nonceValidator);

        when(request.getRequestURI()).thenReturn("/api/echo");
        when(request.getHeader("X-Session-Id")).thenReturn(sessionId);
        when(request.getHeader("X-Nonce")).thenReturn("test-nonce");
        when(request.getContentLength()).thenReturn(-1); // 空请求体
        when(sessionManager.getSession(sessionId)).thenReturn(session);

        SecuredController controller = new SecuredController();
        Method method = SecuredController.class.getMethod("echo", Map.class);
        HandlerMethod handlerMethod = new HandlerMethod(controller, method);

        // When
        boolean result = interceptorWithNonce.preHandle(request, response, handlerMethod);

        // Then
        assertTrue(result);
        verify(nonceValidator).isDuplicate("test-nonce");
        verify(nonceValidator).markUsed("test-nonce");
    }

    // ==================== 清理测试 ====================

    @Test
    void testAfterCompletionClearsAttributes() throws Exception {
        // When
        interceptor.afterCompletion(request, response, null, null);

        // Then
        verify(request).removeAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE);
    }
}
