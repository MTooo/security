package com.sm2sdk.core.session;

import com.sm2sdk.core.crypto.KeyDerivation;
import com.sm2sdk.core.crypto.Sm2KeyExchange;
import com.sm2sdk.core.crypto.Sm4Crypto;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.model.HandshakeConfirm;
import com.sm2sdk.core.model.HandshakeInit;
import com.sm2sdk.core.model.HandshakeServerResp;
import com.sm2sdk.core.model.Sm2SdkConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@link SessionManager} 的单元测试。
 *
 * <p>覆盖握手创建会话、加密解密往返、过期检测、续期、解密失败抛异常、会话销毁等场景。
 */
class SessionManagerTest {

    @Mock
    private Sm2KeyExchange keyExchange;

    @Mock
    private Sm4Crypto sm4Crypto;

    @Mock
    private SessionStore sessionStore;

    @Mock
    private Sm2SdkConfig config;

    private SessionManager sessionManager;

    private static final String SESSION_ID = "sess-test-001";
    private static final String PEER_ID = "peer-01";
    private static final String CLIENT_ID = "client-01";

    private byte[] sm4Key;
    private byte[] sm4Iv;
    private byte[] sharedKey;
    private byte[] ra;
    private byte[] rb;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // 16 字节 SM4 密钥
        sm4Key = new byte[16];
        for (int i = 0; i < 16; i++) {
            sm4Key[i] = (byte) (i + 1);
        }

        // 12 字节 SM4 IV
        sm4Iv = new byte[12];
        for (int i = 0; i < 12; i++) {
            sm4Iv[i] = (byte) (i + 10);
        }

        // 32 字节共享密钥
        sharedKey = new byte[32];
        for (int i = 0; i < 32; i++) {
            sharedKey[i] = (byte) (i + 100);
        }

        // 临时公钥（非压缩格式，65 字节）
        ra = new byte[65];
        ra[0] = 0x04;
        rb = new byte[65];
        rb[0] = 0x04;

        // 默认配置
        when(config.getMaxSessionLifetimeMs()).thenReturn(3_600_000L);
        when(config.getSessionTimeoutMs()).thenReturn(300_000L);
        when(config.getMaxSessionRequests()).thenReturn(1000);

        sessionManager = new SessionManager(() -> keyExchange, sm4Crypto, sessionStore, config);
    }

    // ========== 握手创建会话 ==========

    @Test
    void initiateHandshakeShouldCreateSessionAndStore() {
        // Given
        HandshakeInit mockInit = new HandshakeInit("v1", CLIENT_ID,
                "RA_b64", 1000L, "sig", "ZA");
        HandshakeServerResp mockResp = new HandshakeServerResp(SESSION_ID, "RB_b64", "SB_b64");
        Sm2KeyExchange.HandshakeResult mockResult = new Sm2KeyExchange.HandshakeResult(
                SESSION_ID, sm4Key, sm4Iv, sharedKey, "ZA", "ZB", ra, rb);
        HandshakeConfirm mockConfirm = new HandshakeConfirm(SESSION_ID, "SA_b64");

        when(config.getSm2PrivateKey()).thenReturn("0123456789ABCDEF");
        when(config.getSm2PublicKey()).thenReturn("FEDCBA9876543210");
        when(config.getPeerConfigs()).thenReturn(
                Collections.singletonList(new Sm2SdkConfig.PeerConfig("PUB_KEY", "http://peer.url")));
        when(keyExchange.buildInitRequest(anyString(), any(), any(), anyString())).thenReturn(mockInit);
        when(keyExchange.processServerResponse(any(), eq(mockResp), any(), any(), anyString(), anyString()))
                .thenReturn(mockResult);
        when(keyExchange.buildConfirm(any())).thenReturn(mockConfirm);

        // When
        Session session = sessionManager.initiateHandshake(PEER_ID, mockResp);

        // Then
        assertNotNull(session);
        assertEquals(SESSION_ID, session.getSessionId());
        assertArrayEquals(sm4Key, session.getSm4KeyCopy());
        assertArrayEquals(sm4Iv, session.getSm4IvCopy());
        assertFalse(session.isExpired(300_000L, 3_600_000L, 1000));
        verify(sessionStore).put(any(Session.class));
    }

    @Test
    void handleIncomingHandshakeShouldCreateServerSession() {
        // Given
        HandshakeInit mockInit = new HandshakeInit("v1", CLIENT_ID,
                "RA_b64", 1000L, "sig", "ZA");
        Sm2KeyExchange.HandshakeResult mockResult = new Sm2KeyExchange.HandshakeResult(
                SESSION_ID, sm4Key, sm4Iv, sharedKey, "ZA", "ZB", ra, rb);

        when(config.getSm2PrivateKey()).thenReturn("0123456789ABCDEF");
        when(config.getSm2PublicKey()).thenReturn("FEDCBA9876543210");
        when(keyExchange.processClientInit(any(), any(), any(), anyString(), anyString()))
                .thenReturn(mockResult);

        // When
        Session session = sessionManager.handleIncomingHandshake(mockInit);

        // Then
        assertNotNull(session);
        assertEquals(SESSION_ID, session.getSessionId());
        assertArrayEquals(sm4Key, session.getSm4KeyCopy());
        assertArrayEquals(sm4Iv, session.getSm4IvCopy());
        verify(sessionStore).put(any(Session.class));
    }

    // ========== 获取会话 ==========

    @Test
    void getSessionShouldReturnSessionFromStore() {
        // Given
        Session session = createTestSession(SESSION_ID);
        when(sessionStore.get(SESSION_ID)).thenReturn(session);

        // When
        Session result = sessionManager.getSession(SESSION_ID);

        // Then
        assertNotNull(result);
        assertEquals(SESSION_ID, result.getSessionId());
    }

    @Test
    void getSessionShouldThrowWhenSessionNotFound() {
        // Given
        when(sessionStore.get("non-existent")).thenReturn(null);

        // When & Then
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> sessionManager.getSession("non-existent"));
        assertEquals(ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED, ex.getErrorCode());
    }

    @Test
    void getSessionShouldThrowWhenSessionExpired() {
        // Given
        // 使用过去的时间戳确保会话已空闲超时（lastAccessTime = createTime 较早）
        long pastTime = System.currentTimeMillis() - 60_000L; // 60 秒前创建
        Session session = new Session(SESSION_ID, CLIENT_ID, PEER_ID,
                sm4Key, sm4Iv, pastTime, 3_600_000L, 1000);
        when(sessionStore.get(SESSION_ID)).thenReturn(session);

        // 设置极小的空闲超时（1ms），任何空闲都会触发过期
        when(config.getSessionTimeoutMs()).thenReturn(1L);
        when(config.getMaxSessionLifetimeMs()).thenReturn(3_600_000L);
        when(config.getMaxSessionRequests()).thenReturn(1000);

        // When & Then
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> sessionManager.getSession(SESSION_ID));
        assertEquals(ErrorCode.SESSION_EXPIRED, ex.getErrorCode());
        verify(sessionStore).remove(SESSION_ID);
    }

    // ========== 加密解密往返 ==========

    @Test
    void encryptDecryptRoundtripShouldSucceed() throws Exception {
        // Given
        String json = "{\"msg\":\"hello\"}";
        byte[] plainBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] encryptedBytes = "mock-ciphertext-with-tag".getBytes(StandardCharsets.UTF_8);

        Session session = createTestSession(SESSION_ID);
        when(sessionStore.get(SESSION_ID)).thenReturn(session);
        when(sm4Crypto.encrypt(any(), any(), isNull(), eq(plainBytes))).thenReturn(encryptedBytes);
        when(sm4Crypto.decrypt(any(), any(), isNull(), eq(encryptedBytes))).thenReturn(plainBytes);

        // When
        String encryptedBase64 = sessionManager.encryptBody(SESSION_ID, json);

        // Then
        assertNotNull(encryptedBase64);
        assertEquals(Base64.getEncoder().encodeToString(encryptedBytes), encryptedBase64);

        // When - decrypt
        String decrypted = sessionManager.decryptBody(SESSION_ID, encryptedBase64);

        // Then
        assertEquals(json, decrypted);
    }

    @Test
    void encryptBodyShouldUseSessionKeys() {
        // Given
        String json = "{\"data\":\"test\"}";
        byte[] plainBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = "cipher".getBytes(StandardCharsets.UTF_8);

        Session session = createTestSession(SESSION_ID);
        when(sessionStore.get(SESSION_ID)).thenReturn(session);
        when(sm4Crypto.encrypt(any(), any(), isNull(), eq(plainBytes))).thenReturn(ciphertext);

        // When
        String result = sessionManager.encryptBody(SESSION_ID, json);

        // Then
        assertNotNull(result);
        verify(sm4Crypto).encrypt(any(), any(), isNull(), eq(plainBytes));
    }

    @Test
    void decryptBodyShouldReturnPlainJson() {
        // Given
        String json = "{\"result\":\"ok\"}";
        byte[] plainBytes = json.getBytes(StandardCharsets.UTF_8);
        byte[] ciphertext = "encrypted".getBytes(StandardCharsets.UTF_8);
        String encryptedBase64 = Base64.getEncoder().encodeToString(ciphertext);

        Session session = createTestSession(SESSION_ID);
        when(sessionStore.get(SESSION_ID)).thenReturn(session);
        when(sm4Crypto.decrypt(any(), any(), isNull(), eq(ciphertext))).thenReturn(plainBytes);

        // When
        String decrypted = sessionManager.decryptBody(SESSION_ID, encryptedBase64);

        // Then
        assertEquals(json, decrypted);
    }

    @Test
    void decryptBodyShouldThrowOnInvalidCiphertext() {
        // Given
        String invalidBase64 = "!!!not-valid-base64@@@";

        Session session = createTestSession(SESSION_ID);
        when(sessionStore.get(SESSION_ID)).thenReturn(session);

        // When & Then
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> sessionManager.decryptBody(SESSION_ID, invalidBase64));
        assertEquals(ErrorCode.SM4_DECRYPT_TAG_FAILED, ex.getErrorCode());
    }

    @Test
    void decryptBodyShouldPropagateSm2SdkException() {
        // Given
        String encryptedBase64 = Base64.getEncoder().encodeToString("bad-data".getBytes(StandardCharsets.UTF_8));

        Session session = createTestSession(SESSION_ID);
        when(sessionStore.get(SESSION_ID)).thenReturn(session);
        when(sm4Crypto.decrypt(any(), any(), isNull(), any()))
                .thenThrow(new Sm2SdkException(ErrorCode.SM4_DECRYPT_TAG_FAILED, "TAG mismatch"));

        // When & Then
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> sessionManager.decryptBody(SESSION_ID, encryptedBase64));
        assertEquals(ErrorCode.SM4_DECRYPT_TAG_FAILED, ex.getErrorCode());
    }

    // ========== 续期 ==========

    @Test
    void renewSessionShouldRekeyWhenBelowThreshold() throws Exception {
        // Given
        // 将会话的创建时间设置到足够久远之前，使 remainingLifetimeMs < DEFAULT_RENEW_THRESHOLD_MS (60s)
        // lastAccessTime 初始值为 createTime，因此需要将 timeoutMs 设大以避免空闲过期
        long createTime = System.currentTimeMillis() - 3_570_000L; // 59.5 min ago, ~30s left
        Session session = new Session(SESSION_ID, CLIENT_ID, PEER_ID,
                sm4Key, sm4Iv, createTime, 3_600_000L, 1000);

        when(sessionStore.get(SESSION_ID)).thenReturn(session);
        when(config.getSessionTimeoutMs()).thenReturn(3_600_000L);

        // 通过反射将共享密钥注入 sharedKeyCache
        injectSharedKey(SESSION_ID, sharedKey);

        // 通过反射或直接验证续期后的密钥与原始密钥不同
        byte[] originalKey = session.getSm4KeyCopy();
        byte[] originalIv = session.getSm4IvCopy();

        // When
        Session renewed = sessionManager.renewSession(SESSION_ID);

        // Then
        assertNotNull(renewed);
        assertEquals(1, renewed.getRekeyVersion());
        // 密钥应该已经改变
        byte[] newKey = renewed.getSm4KeyCopy();
        byte[] newIv = renewed.getSm4IvCopy();
        assertFalse(java.util.Arrays.equals(originalKey, newKey),
                "续期后的密钥应与原始密钥不同");
        assertFalse(java.util.Arrays.equals(originalIv, newIv),
                "续期后的 IV 应与原始 IV 不同");
        verify(sessionStore).renew(SESSION_ID);

        // 清理
        Session.clearKeyCopy(originalKey);
        Session.clearKeyCopy(originalIv);
        Session.clearKeyCopy(newKey);
        Session.clearKeyCopy(newIv);
    }

    @Test
    void renewSessionShouldNotRekeyWhenAboveThreshold() {
        // Given
        // 刚创建的会话，剩余生命周期充足
        Session session = createTestSession(SESSION_ID);
        when(sessionStore.get(SESSION_ID)).thenReturn(session);

        // When
        Session result = sessionManager.renewSession(SESSION_ID);

        // Then
        assertEquals(SESSION_ID, result.getSessionId());
        assertEquals(0, result.getRekeyVersion(), "不应执行续期");
        verify(sessionStore, never()).renew(SESSION_ID);
    }

    @Test
    void renewSessionShouldThrowWhenSharedKeyMissing() {
        // Given
        // 使用 initiateHandshake 创建的会话会在 sharedKeyCache 中存储共享密钥，
        // 但直接构造 Session 并调用 renewSession 会导致 sharedKey 缺失。
        // 这里我们通过 getSession 返回一个 Session，但 sharedKeyCache 中没有对应条目。
        Session session = createTestSession(SESSION_ID);
        when(sessionStore.get(SESSION_ID)).thenReturn(session);
        // 设置足够的阈值触发续期尝试
        long remaining = session.remainingLifetimeMs(3_600_000L);
        // 如果 remaining 很大，则不会触发续期，不会走到 sharedKey 检查。
        // 为了测试，让 session 看起来需要续期
        Session oldSession = new Session(SESSION_ID, CLIENT_ID, PEER_ID,
                sm4Key, sm4Iv,
                System.currentTimeMillis() - 3_590_000L, // 约 10 秒剩余
                3_600_000L, 1000);
        when(sessionStore.get(SESSION_ID)).thenReturn(oldSession);
        // 空闲超时设置足够大，避免 idle 过期
        when(config.getSessionTimeoutMs()).thenReturn(3_600_000L);

        // When & Then
        Sm2SdkException ex = assertThrows(Sm2SdkException.class,
                () -> sessionManager.renewSession(SESSION_ID));
        assertEquals(ErrorCode.SESSION_STATE_INVALID, ex.getErrorCode());
    }

    // ========== 会话销毁 ==========

    @Test
    void destroySessionShouldRemoveFromStoreAndZeroKeys() {
        // Given
        Session session = createTestSession(SESSION_ID);
        when(sessionStore.get(SESSION_ID)).thenReturn(session);

        // When
        sessionManager.destroySession(SESSION_ID);

        // Then
        verify(sessionStore).remove(SESSION_ID);
        assertTrue(session.isDestroyed());
        assertThrows(IllegalStateException.class, () -> session.getSm4KeyCopy());
    }

    @Test
    void destroySessionShouldHandleNonExistentSession() {
        // Given
        when(sessionStore.get("ghost")).thenReturn(null);

        // When & Then - 不存在的会话不应抛出异常
        assertDoesNotThrow(() -> sessionManager.destroySession("ghost"));
        verify(sessionStore, never()).remove(any());
    }

    // ========== 辅助方法 ==========

    private Session createTestSession(String sessionId) {
        return new Session(sessionId, CLIENT_ID, PEER_ID,
                sm4Key, sm4Iv,
                System.currentTimeMillis(),
                3_600_000L, 1000);
    }

    /**
     * 通过反射将共享密钥注入 SessionManager 的 sharedKeyCache。
     * 模拟 initiateHandshake 完成后缓存的共享密钥。
     */
    @SuppressWarnings("unchecked")
    private void injectSharedKey(String sessionId, byte[] key) throws Exception {
        Field cacheField = SessionManager.class.getDeclaredField("sharedKeyCache");
        cacheField.setAccessible(true);
        ConcurrentHashMap<String, byte[]> cache =
                (ConcurrentHashMap<String, byte[]>) cacheField.get(sessionManager);
        cache.put(sessionId, key.clone());
    }
}
