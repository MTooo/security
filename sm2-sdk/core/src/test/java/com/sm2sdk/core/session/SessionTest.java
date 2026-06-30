package com.sm2sdk.core.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for {@link Session}.
 *
 * <p>Covers creation, defensive copying, key destruction, idempotent destroy,
 * touch semantics, expiry detection (idle, max requests, max lifetime),
 * rekey operations, and IllegalStateException guards.
 */
class SessionTest {

    private static final String SESSION_ID = "sess-test-001";
    private static final String CLIENT_ID = "client-01";
    private static final String PEER_ID = "server-01";
    private static final long CREATE_TIME = 1000L;
    private static final long MAX_LIFETIME = 30000L;
    private static final int MAX_REQUESTS = 100;

    private byte[] sm4Key;
    private byte[] sm4Iv;
    private Session session;

    @BeforeEach
    void setUp() {
        sm4Key = new byte[16];
        sm4Iv = new byte[12];
        for (int i = 0; i < 16; i++) {
            sm4Key[i] = (byte) (i + 1);
        }
        for (int i = 0; i < 12; i++) {
            sm4Iv[i] = (byte) (i + 10);
        }
        session = new Session(SESSION_ID, CLIENT_ID, PEER_ID, sm4Key, sm4Iv,
                CREATE_TIME, MAX_LIFETIME, MAX_REQUESTS);
    }

    // ========== Creation ==========

    @Test
    void shouldCreateSessionWithGivenFields() {
        assertEquals(SESSION_ID, session.getSessionId());
        assertEquals(CLIENT_ID, session.getClientId());
        assertEquals(PEER_ID, session.getPeerId());
        assertEquals(CREATE_TIME, session.getCreateTime());
        assertEquals(CREATE_TIME, session.getLastAccessTime());
        assertEquals(MAX_LIFETIME, session.getMaxLifetime());
        assertEquals(MAX_REQUESTS, session.getMaxRequests());
        assertEquals(0, session.getRequestCount());
        assertEquals(0, session.getRekeyVersion());
        assertFalse(session.isDestroyed());
    }

    @Test
    void shouldInitializeRequestCountToZero() {
        assertEquals(0, session.getRequestCount());
    }

    @Test
    void shouldInitializeRekeyVersionToZero() {
        assertEquals(0, session.getRekeyVersion());
    }

    // ========== Defensive Copy ==========

    @Test
    void getSm4KeyCopyShouldReturnCopyNotReference() {
        byte[] keyCopy = session.getSm4KeyCopy();
        assertNotNull(keyCopy);
        assertEquals(16, keyCopy.length);

        // Modify the copy — original must not be affected
        keyCopy[0] = (byte) 0xFF;
        byte[] secondCopy = session.getSm4KeyCopy();
        assertNotEquals(keyCopy[0], secondCopy[0],
                "Modifying returned copy must not affect internal key");
    }

    @Test
    void getSm4IvCopyShouldReturnCopyNotReference() {
        byte[] ivCopy = session.getSm4IvCopy();
        assertNotNull(ivCopy);
        assertEquals(12, ivCopy.length);

        ivCopy[0] = (byte) 0xFF;
        byte[] secondCopy = session.getSm4IvCopy();
        assertNotEquals(ivCopy[0], secondCopy[0],
                "Modifying returned copy must not affect internal IV");
    }

    @Test
    void getSm4KeyCopyShouldNotBeOriginalArray() {
        byte[] keyCopy = session.getSm4KeyCopy();
        assertNotSame(sm4Key, keyCopy,
                "Returned array must be a different object from the constructor argument");
    }

    @Test
    void getSm4IvCopyShouldNotBeOriginalArray() {
        byte[] ivCopy = session.getSm4IvCopy();
        assertNotSame(sm4Iv, ivCopy,
                "Returned array must be a different object from the constructor argument");
    }

    @Test
    void constructorShouldDefensivelyCopyKey() {
        // Modify original array after construction — session must not be affected
        sm4Key[0] = (byte) 0xAA;
        byte[] sessionKey = session.getSm4KeyCopy();
        assertNotEquals((byte) 0xAA, sessionKey[0],
                "Constructor must defensively copy key material");
    }

    @Test
    void constructorShouldDefensivelyCopyIv() {
        sm4Iv[0] = (byte) 0xBB;
        byte[] sessionIv = session.getSm4IvCopy();
        assertNotEquals((byte) 0xBB, sessionIv[0],
                "Constructor must defensively copy IV material");
    }

    // ========== clearKeyCopy ==========

    @Test
    void clearKeyCopyShouldZeroTheArray() {
        byte[] key = new byte[]{1, 2, 3, 4, 5};
        Session.clearKeyCopy(key);
        for (byte b : key) {
            assertEquals((byte) 0, b, "All bytes must be zero after clearKeyCopy");
        }
    }

    @Test
    void clearKeyCopyShouldHandleNullGracefully() {
        // Should not throw NPE — gracefully handle null
        assertDoesNotThrow(() -> Session.clearKeyCopy(null));
    }

    @Test
    void clearKeyCopyShouldHandleEmptyArray() {
        byte[] empty = new byte[0];
        assertDoesNotThrow(() -> Session.clearKeyCopy(empty));
        assertEquals(0, empty.length);
    }

    // ========== Destroy ==========

    @Test
    void destroyShouldBlockKeyAccess() {
        // After destroy, all key-access methods must throw IllegalStateException
        session.destroy();
        assertThrows(IllegalStateException.class, () -> session.getSm4KeyCopy(),
                "getSm4KeyCopy after destroy must throw IllegalStateException");
        assertThrows(IllegalStateException.class, () -> session.getSm4IvCopy(),
                "getSm4IvCopy after destroy must throw IllegalStateException");
    }

    @Test
    void destroyShouldSetDestroyedFlag() {
        assertFalse(session.isDestroyed());
        session.destroy();
        assertTrue(session.isDestroyed());
    }

    @Test
    void doubleDestroyShouldBeIdempotent() {
        session.destroy();
        // Second destroy must not throw
        assertDoesNotThrow(() -> session.destroy(),
                "Double destroy must be idempotent and not throw");
        assertTrue(session.isDestroyed());
    }

    @Test
    void tripleDestroyShouldBeIdempotent() {
        session.destroy();
        session.destroy();
        assertDoesNotThrow(() -> session.destroy(),
                "Triple destroy must be idempotent and not throw");
        assertTrue(session.isDestroyed());
    }

    // ========== Touch ==========

    @Test
    void touchShouldIncrementRequestCount() {
        assertEquals(0, session.getRequestCount());
        session.touch();
        assertEquals(1, session.getRequestCount());
    }

    @Test
    void touchShouldUpdateLastAccessTime() {
        long originalAccess = session.getLastAccessTime();
        // Simulate a small delay
        sleepQuietly(5);
        session.touch();
        assertTrue(session.getLastAccessTime() > originalAccess,
                "touch must update lastAccessTime");
    }

    @Test
    void touchShouldIncrementCountSequentially() {
        int increments = 5;
        for (int i = 0; i < increments; i++) {
            session.touch();
        }
        assertEquals(increments, session.getRequestCount());
    }

    // ========== Touch on Destroyed Session ==========

    @Test
    void touchOnDestroyedSessionShouldThrow() {
        session.destroy();
        assertThrows(IllegalStateException.class, () -> session.touch(),
                "touch on destroyed session must throw IllegalStateException");
    }

    // ========== Expiry Detection ==========

    @Test
    void isExpiredShouldReturnFalseForActiveSession() {
        long now = System.currentTimeMillis();
        Session fresh = new Session("fresh", CLIENT_ID, PEER_ID,
                sm4Key, sm4Iv, now, 300_000L, MAX_REQUESTS);
        assertFalse(fresh.isExpired(30_000L, 300_000L, MAX_REQUESTS),
                "Fresh session should not be expired");
    }

    @Test
    void isExpiredShouldDetectIdleTimeout() {
        long now = System.currentTimeMillis();
        // createTime=now, but lastAccessTime starts at createTime
        Session idleSession = new Session("idle", CLIENT_ID, PEER_ID,
                sm4Key, sm4Iv, now - 100L, 300_000L, MAX_REQUESTS);
        // idle timeout of 50ms — session was created 100ms ago, so idle
        assertTrue(idleSession.isExpired(50L, 300_000L, MAX_REQUESTS),
                "Session idle longer than timeoutMs should be expired");
    }

    @Test
    void isExpiredShouldDetectMaxLifetimeExceeded() {
        long now = System.currentTimeMillis();
        // createTime is far in the past relative to maxLifetime
        Session oldSession = new Session("old", CLIENT_ID, PEER_ID,
                sm4Key, sm4Iv, now - 5000L, 100L, MAX_REQUESTS);
        assertTrue(oldSession.isExpired(30_000L, 100L, MAX_REQUESTS),
                "Session past maxLifetime should be expired");
    }

    @Test
    void isExpiredShouldDetectMaxRequestsExceeded() {
        long now = System.currentTimeMillis();
        Session busySession = new Session("busy", CLIENT_ID, PEER_ID,
                sm4Key, sm4Iv, now, 300_000L, 5);
        for (int i = 0; i < 5; i++) {
            busySession.touch();
        }
        assertTrue(busySession.isExpired(30_000L, 300_000L, 5),
                "Session at max request limit should be expired");
    }

    @Test
    void isExpiredShouldReturnFalseWhenWithinAllLimits() {
        long now = System.currentTimeMillis();
        Session freshSession = new Session("ok", CLIENT_ID, PEER_ID,
                sm4Key, sm4Iv, now, 300_000L, MAX_REQUESTS);
        freshSession.touch();
        assertFalse(freshSession.isExpired(30_000L, 300_000L, MAX_REQUESTS),
                "Session within all limits should not be expired");
    }

    @Test
    void isExpiredShouldCheckDestroyedSession() {
        session.destroy();
        assertTrue(session.isExpired(30_000L, MAX_LIFETIME, MAX_REQUESTS),
                "Destroyed session should be considered expired");
    }

    // ========== remainingLifetimeMs ==========

    @Test
    void remainingLifetimeMsShouldReturnPositiveForFreshSession() {
        long now = System.currentTimeMillis();
        Session fresh = new Session("fresh", CLIENT_ID, PEER_ID,
                sm4Key, sm4Iv, now, 300_000L, MAX_REQUESTS);
        long remaining = fresh.remainingLifetimeMs(300_000L);
        assertTrue(remaining > 0,
                "Remaining lifetime should be positive for fresh session");
    }

    @Test
    void remainingLifetimeMsShouldReturnZeroForExpiredSession() {
        long now = System.currentTimeMillis();
        Session expired = new Session("expired", CLIENT_ID, PEER_ID,
                sm4Key, sm4Iv, now - 5000L, 100L, MAX_REQUESTS);
        long remaining = expired.remainingLifetimeMs(100L);
        assertTrue(remaining <= 0,
                "Remaining lifetime should be <= 0 for expired session");
    }

    // ========== Rekey ==========

    @Test
    void rekeyShouldReplaceKeys() {
        byte[] oldKeyCopy = session.getSm4KeyCopy();
        byte[] oldIvCopy = session.getSm4IvCopy();

        byte[] newKey = new byte[16];
        byte[] newIv = new byte[12];
        Arrays.fill(newKey, (byte) 0x77);
        Arrays.fill(newIv, (byte) 0x88);

        session.rekey(newKey, newIv);

        byte[] updatedKey = session.getSm4KeyCopy();
        byte[] updatedIv = session.getSm4IvCopy();

        // Verify new keys are set
        for (byte b : updatedKey) {
            assertEquals((byte) 0x77, b, "Key should be updated to new value");
        }
        for (byte b : updatedIv) {
            assertEquals((byte) 0x88, b, "IV should be updated to new value");
        }

        // Verify old keys are NOT the same as current
        assertFalse(Arrays.equals(oldKeyCopy, updatedKey),
                "Key should differ from original after rekey");
        assertFalse(Arrays.equals(oldIvCopy, updatedIv),
                "IV should differ from original after rekey");
    }

    @Test
    void rekeyShouldZeroOldKeys() {
        byte[] newKey = new byte[16];
        byte[] newIv = new byte[12];
        Arrays.fill(newKey, (byte) 0x55);
        Arrays.fill(newIv, (byte) 0x66);

        session.rekey(newKey, newIv);

        // The old key bytes (1..16, 10..21) should no longer be in the internal arrays
        byte[] currentKey = session.getSm4KeyCopy();
        byte[] currentIv = session.getSm4IvCopy();

        boolean foundOldKeyByte = false;
        for (int i = 0; i < currentKey.length && i < 16; i++) {
            if (currentKey[i] == (byte) (i + 1)) {
                foundOldKeyByte = true;
                break;
            }
        }
        assertFalse(foundOldKeyByte, "Old key bytes should be zeroed after rekey");

        boolean foundOldIvByte = false;
        for (int i = 0; i < currentIv.length && i < 12; i++) {
            if (currentIv[i] == (byte) (i + 10)) {
                foundOldIvByte = true;
                break;
            }
        }
        assertFalse(foundOldIvByte, "Old IV bytes should be zeroed after rekey");
    }

    @Test
    void rekeyShouldResetRequestCount() {
        session.touch();
        session.touch();
        assertEquals(2, session.getRequestCount());

        byte[] newKey = new byte[16];
        byte[] newIv = new byte[12];
        session.rekey(newKey, newIv);

        assertEquals(0, session.getRequestCount(),
                "Request count should be reset after rekey");
    }

    @Test
    void rekeyShouldIncrementRekeyVersion() {
        assertEquals(0, session.getRekeyVersion());

        byte[] newKey = new byte[16];
        byte[] newIv = new byte[12];
        session.rekey(newKey, newIv);

        assertEquals(1, session.getRekeyVersion(),
                "Rekey version should increment after rekey");
    }

    @Test
    void rekeyShouldDefensivelyCopyNewKeys() {
        byte[] newKey = new byte[16];
        byte[] newIv = new byte[12];
        Arrays.fill(newKey, (byte) 0xAA);
        Arrays.fill(newIv, (byte) 0xBB);

        session.rekey(newKey, newIv);

        // Modify the original arrays — session must not be affected
        newKey[0] = (byte) 0xCC;
        newIv[0] = (byte) 0xDD;

        byte[] sessionKey = session.getSm4KeyCopy();
        byte[] sessionIv = session.getSm4IvCopy();
        assertEquals((byte) 0xAA, sessionKey[0],
                "rekey must defensively copy key material");
        assertEquals((byte) 0xBB, sessionIv[0],
                "rekey must defensively copy IV material");
    }

    // ========== Destroyed Guard ==========

    @Test
    void getSm4KeyCopyOnDestroyedSessionShouldThrow() {
        session.destroy();
        assertThrows(IllegalStateException.class, () -> session.getSm4KeyCopy(),
                "getSm4KeyCopy on destroyed session must throw IllegalStateException");
    }

    @Test
    void getSm4IvCopyOnDestroyedSessionShouldThrow() {
        session.destroy();
        assertThrows(IllegalStateException.class, () -> session.getSm4IvCopy(),
                "getSm4IvCopy on destroyed session must throw IllegalStateException");
    }

    @Test
    void rekeyOnDestroyedSessionShouldThrow() {
        session.destroy();
        byte[] newKey = new byte[16];
        byte[] newIv = new byte[12];
        assertThrows(IllegalStateException.class, () -> session.rekey(newKey, newIv),
                "rekey on destroyed session must throw IllegalStateException");
    }

    @Test
    void touchOnDestroyedSessionShouldThrow2() {
        session.destroy();
        assertThrows(IllegalStateException.class, () -> session.touch(),
                "touch on destroyed session must throw IllegalStateException");
    }

    // ========== Null/Edge Case Constructor ==========

    @Test
    void constructorShouldThrowOnNullSessionId() {
        assertThrows(NullPointerException.class,
                () -> new Session(null, CLIENT_ID, PEER_ID, sm4Key, sm4Iv,
                        CREATE_TIME, MAX_LIFETIME, MAX_REQUESTS));
    }

    @Test
    void constructorShouldThrowOnNullKey() {
        assertThrows(NullPointerException.class,
                () -> new Session(SESSION_ID, CLIENT_ID, PEER_ID, null, sm4Iv,
                        CREATE_TIME, MAX_LIFETIME, MAX_REQUESTS));
    }

    @Test
    void constructorShouldThrowOnNullIv() {
        assertThrows(NullPointerException.class,
                () -> new Session(SESSION_ID, CLIENT_ID, PEER_ID, sm4Key, null,
                        CREATE_TIME, MAX_LIFETIME, MAX_REQUESTS));
    }

    // ========== Helpers ==========

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
