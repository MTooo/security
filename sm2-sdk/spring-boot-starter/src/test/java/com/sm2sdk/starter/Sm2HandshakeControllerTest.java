package com.sm2sdk.starter;

import com.sm2sdk.core.crypto.Sm2KeyExchange;
import com.sm2sdk.core.crypto.impl.HutoolSm2KeyExchange;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.model.HandshakeConfirm;
import com.sm2sdk.core.model.HandshakeInit;
import com.sm2sdk.core.model.HandshakeServerResp;
import com.sm2sdk.core.model.Sm2SdkConfig;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link Sm2HandshakeController} 的单元测试。
 */
class Sm2HandshakeControllerTest {

    private SessionManager sessionManager;
    private Sm2ServerConfig serverConfig;
    private Sm2HandshakeController controller;
    private Sm2KeyExchange keyExchange;

    @BeforeEach
    void setUp() {
        Sm2SdkConfig sdkConfig = new Sm2SdkConfig();
        sdkConfig.setSm2PrivateKey(
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        sdkConfig.setSm2PublicKey(
                "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210");

        serverConfig = new Sm2ServerConfig(sdkConfig);

        // 使用真实的 HutoolSm2KeyExchange 实例
        keyExchange = spy(new HutoolSm2KeyExchange());
        sessionManager = mock(SessionManager.class);
        when(sessionManager.getKeyExchange()).thenReturn(keyExchange);

        controller = new Sm2HandshakeController(sessionManager, serverConfig);
    }

    @Test
    void testHandleInitThrowsOnNullInit() {
        assertThrows(NullPointerException.class,
                () -> controller.handleInit(null));
    }

    @Test
    void testHandleInitThrowsOnMissingClientId() {
        HandshakeInit init = new HandshakeInit();
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> controller.handleInit(init));
        assertEquals(ErrorCode.HANDSHAKE_TIMEOUT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("clientId"));
    }

    @Test
    void testHandleInitThrowsOnMissingEphemeralPublicKey() {
        HandshakeInit init = new HandshakeInit();
        init.setClientId("test-client");
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> controller.handleInit(init));
        assertEquals(ErrorCode.HANDSHAKE_TIMEOUT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("临时公钥"));
    }

    @Test
    void testHandleInitThrowsOnInvalidTimestamp() {
        HandshakeInit init = new HandshakeInit();
        init.setClientId("test-client");
        init.setEphemeralPublicKey("AAAA");
        init.setTimestamp(0);
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> controller.handleInit(init));
        assertEquals(ErrorCode.HANDSHAKE_TIMEOUT, ex.getErrorCode());
        assertTrue(ex.getMessage().contains("时间戳"));
    }

    @Test
    void testHandleConfirmThrowsOnNullConfirm() {
        assertThrows(NullPointerException.class,
                () -> controller.handleConfirm(null));
    }

    @Test
    void testHandleConfirmThrowsOnMissingSessionId() {
        HandshakeConfirm confirm = new HandshakeConfirm();
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> controller.handleConfirm(confirm));
        assertEquals(ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED, ex.getErrorCode());
    }

    @Test
    void testHandleConfirmSuccess() {
        // Given
        String sessionId = "test-session-id";
        HandshakeConfirm confirm = new HandshakeConfirm();
        confirm.setSessionId(sessionId);
        confirm.setConfirmation("dGVzdA==");

        byte[] key = new byte[16];
        byte[] iv = new byte[12];
        Arrays.fill(key, (byte) 0x01);
        Arrays.fill(iv, (byte) 0x02);
        Session session = new Session(sessionId, "client1", "server",
                key, iv, System.currentTimeMillis(), 3600000L, 1000);

        when(sessionManager.getSession(sessionId)).thenReturn(session);

        // When / Then — should not throw
        assertDoesNotThrow(() -> controller.handleConfirm(confirm));
        verify(sessionManager).getSession(sessionId);
    }

    @Test
    void testHandleConfirmThrowsOnInvalidSession() {
        String sessionId = "invalid-session";
        HandshakeConfirm confirm = new HandshakeConfirm();
        confirm.setSessionId(sessionId);

        when(sessionManager.getSession(sessionId))
                .thenThrow(new Sm2SdkException(ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED,
                        "会话不存在"));

        assertThrows(Sm2SdkException.class,
                () -> controller.handleConfirm(confirm));
    }
}
