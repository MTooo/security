package com.sm2sdk.starter;

import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.model.Sm2SdkConfig;
import com.sm2sdk.core.nonce.NonceValidator;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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

    // ==================== 缺少请求头测试 ====================

    @Test
    void testPreHandleThrowsOnMissingSessionId() {
        when(request.getRequestURI()).thenReturn("/api/data");

        assertThrows(Sm2SdkException.class,
                () -> interceptor.preHandle(request, response, null));
    }

    // ==================== 会话过期测试 ====================

    @Test
    void testPreHandleReturnsFalseOnExpiredSession() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("X-Session-Id")).thenReturn("expired-session");
        when(sessionManager.getSession("expired-session"))
                .thenThrow(new Sm2SdkException(ErrorCode.SESSION_EXPIRED, "过期"));

        boolean result = interceptor.preHandle(request, response, null);

        assertFalse(result);
        verify(response).setStatus(401);
        verify(response).setHeader("X-Session-Expired", "true");
    }

    // ==================== 正常解密测试 ====================

    @Test
    void testPreHandleDecryptsBodySuccessfully() throws Exception {
        // Given
        String sessionId = "valid-session";
        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session(sessionId, "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);

        String encryptedBody = "base64-encrypted-body";
        String plainBody = "{\"name\":\"test\"}";

        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("X-Session-Id")).thenReturn(sessionId);
        when(request.getContentLength()).thenReturn(encryptedBody.length());
        when(request.getInputStream()).thenReturn(
                new javax.servlet.ServletInputStream() {
                    private final ByteArrayInputStream bis = new ByteArrayInputStream(
                            encryptedBody.getBytes(StandardCharsets.UTF_8));
                    @Override
                    public int read() { return bis.read(); }
                    @Override
                    public boolean isFinished() { return bis.available() == 0; }
                    @Override
                    public boolean isReady() { return true; }
                    @Override
                    public void setReadListener(
                            javax.servlet.ReadListener listener) {}
                });
        when(sessionManager.getSession(sessionId)).thenReturn(session);
        when(sessionManager.decryptBody(sessionId, encryptedBody))
                .thenReturn(plainBody);

        // When
        boolean result = interceptor.preHandle(request, response, null);

        // Then
        assertTrue(result);
        verify(request).setAttribute(Sm2ServerInterceptor.PLAIN_BODY_ATTRIBUTE,
                plainBody);
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
                true);

        Sm2ServerInterceptor interceptorWithNonce = new Sm2ServerInterceptor(
                sessionManager, configWithNonce, nonceValidator);

        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("X-Session-Id")).thenReturn(sessionId);
        when(request.getHeader("X-Nonce")).thenReturn("test-nonce");
        when(request.getContentLength()).thenReturn(-1); // 空请求体
        when(sessionManager.getSession(sessionId)).thenReturn(session);

        // When
        boolean result = interceptorWithNonce.preHandle(request, response, null);

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
        verify(request).removeAttribute(Sm2ServerInterceptor.PLAIN_BODY_ATTRIBUTE);
        verify(request).removeAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE);
    }
}
