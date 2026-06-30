package com.sm2sdk.core.session;

import java.util.Arrays;
import java.util.Objects;

/**
 * Represents an authenticated session between two peers using SM4 keys.
 *
 * <p>This class is <b>not</b> thread-safe for multi-writer scenarios without external
 * synchronization. Key-access methods ({@link #getSm4KeyCopy()}, {@link #getSm4IvCopy()},
 * {@link #rekey(byte[], byte[])}, {@link #touch()}, {@link #destroy()}) are
 * <b>synchronized</b> on {@code this} to provide safe serial access within a single
 * JVM. After {@link #destroy()} all key material is zeroed and every key-access method
 * throws {@link IllegalStateException}.
 *
 * <p>Callers <b>must</b> invoke {@link #clearKeyCopy(byte[])} on any array returned
 * by {@link #getSm4KeyCopy()} or {@link #getSm4IvCopy()} once they are done with it,
 * to minimise the window during which key material exists outside the session.
 */
public class Session {

    private final String sessionId;
    private final String clientId;
    private final String peerId;
    private final byte[] sm4Key;
    private final byte[] sm4Iv;
    private final long createTime;
    private volatile long lastAccessTime;
    private final long maxLifetime;
    private final int maxRequests;
    private volatile int requestCount;
    private volatile int rekeyVersion;
    private volatile boolean destroyed;

    /**
     * Constructs a new Session with the given parameters.
     *
     * <p>Makes defensive copies of all key material arrays. The caller should
     * {@link #clearKeyCopy(byte[])} the original arrays after construction.
     *
     * @param sessionId   unique session identifier (must not be null)
     * @param clientId    client identity (must not be null)
     * @param peerId      peer identity (must not be null)
     * @param sm4Key      SM4 encryption key (16 bytes, must not be null)
     * @param sm4Iv       SM4 initialisation vector (12 bytes, must not be null)
     * @param createTime  epoch millis when the session was created
     * @param maxLifetime maximum lifetime of the session in milliseconds
     * @param maxRequests maximum number of requests allowed over the session lifetime
     * @throws NullPointerException if any of sessionId, clientId, peerId, sm4Key, or sm4Iv is null
     */
    public Session(String sessionId, String clientId, String peerId,
                   byte[] sm4Key, byte[] sm4Iv,
                   long createTime, long maxLifetime, int maxRequests) {
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId must not be null");
        this.clientId = Objects.requireNonNull(clientId, "clientId must not be null");
        this.peerId = Objects.requireNonNull(peerId, "peerId must not be null");
        Objects.requireNonNull(sm4Key, "sm4Key must not be null");
        Objects.requireNonNull(sm4Iv, "sm4Iv must not be null");
        // Defensive copies
        this.sm4Key = Arrays.copyOf(sm4Key, sm4Key.length);
        this.sm4Iv = Arrays.copyOf(sm4Iv, sm4Iv.length);
        this.createTime = createTime;
        this.lastAccessTime = createTime;
        this.maxLifetime = maxLifetime;
        this.maxRequests = maxRequests;
        this.requestCount = 0;
        this.rekeyVersion = 0;
        this.destroyed = false;
    }

    // ========== Plain getters (non-key) ==========

    public String getSessionId() {
        return sessionId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getPeerId() {
        return peerId;
    }

    public long getCreateTime() {
        return createTime;
    }

    public long getLastAccessTime() {
        return lastAccessTime;
    }

    public long getMaxLifetime() {
        return maxLifetime;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public int getRequestCount() {
        return requestCount;
    }

    public int getRekeyVersion() {
        return rekeyVersion;
    }

    public boolean isDestroyed() {
        return destroyed;
    }

    // ========== Key access — defensive copies ==========

    /**
     * Returns a <b>defensive copy</b> of the SM4 key.
     *
     * <p>The caller must invoke {@link #clearKeyCopy(byte[])} on the returned array
     * when it is no longer needed.
     *
     * @return a copy of the internal SM4 key (never the internal reference)
     * @throws IllegalStateException if the session has been destroyed
     */
    public synchronized byte[] getSm4KeyCopy() {
        checkNotDestroyed();
        return Arrays.copyOf(sm4Key, sm4Key.length);
    }

    /**
     * Returns a <b>defensive copy</b> of the SM4 IV.
     *
     * <p>The caller must invoke {@link #clearKeyCopy(byte[])} on the returned array
     * when it is no longer needed.
     *
     * @return a copy of the internal SM4 IV (never the internal reference)
     * @throws IllegalStateException if the session has been destroyed
     */
    public synchronized byte[] getSm4IvCopy() {
        checkNotDestroyed();
        return Arrays.copyOf(sm4Iv, sm4Iv.length);
    }

    /**
     * Securely zeroes a key-material array so it can be garbage-collected safely.
     *
     * <p>This is a static utility intended for arrays returned by
     * {@link #getSm4KeyCopy()} and {@link #getSm4IvCopy()}.
     *
     * @param key the array to zero; if {@code null} the call is a no-op
     */
    public static void clearKeyCopy(byte[] key) {
        if (key == null) {
            return;
        }
        Arrays.fill(key, (byte) 0);
    }

    // ========== Lifecycle ==========

    /**
     * Records an access to this session.
     *
     * <p>Updates {@link #lastAccessTime} to the current time and increments
     * {@link #requestCount}.
     *
     * @throws IllegalStateException if the session has been destroyed
     */
    public synchronized void touch() {
        checkNotDestroyed();
        this.lastAccessTime = System.currentTimeMillis();
        this.requestCount++;
    }

    /**
     * Replaces the session keys with new material.
     *
     * <p>The previous key and IV are zeroed before the new values are copied in
     * (defensively). {@link #requestCount} is reset to zero and
     * {@link #rekeyVersion} is incremented.
     *
     * @param newKey the new SM4 key (16 bytes, must not be null)
     * @param newIv  the new SM4 IV (12 bytes, must not be null)
     * @throws IllegalStateException if the session has been destroyed
     * @throws NullPointerException  if newKey or newIv is null
     */
    public synchronized void rekey(byte[] newKey, byte[] newIv) {
        checkNotDestroyed();
        Objects.requireNonNull(newKey, "newKey must not be null");
        Objects.requireNonNull(newIv, "newIv must not be null");

        // Zero old keys
        Arrays.fill(sm4Key, (byte) 0);
        Arrays.fill(sm4Iv, (byte) 0);

        // Copy new keys defensively
        byte[] newKeyCopy = Arrays.copyOf(newKey, newKey.length);
        byte[] newIvCopy = Arrays.copyOf(newIv, newIv.length);
        System.arraycopy(newKeyCopy, 0, sm4Key, 0, Math.min(newKeyCopy.length, sm4Key.length));
        System.arraycopy(newIvCopy, 0, sm4Iv, 0, Math.min(newIvCopy.length, sm4Iv.length));

        // Clear the temporary copies
        clearKeyCopy(newKeyCopy);
        clearKeyCopy(newIvCopy);

        this.requestCount = 0;
        this.rekeyVersion++;
    }

    // ========== Expiry ==========

    /**
     * Checks whether this session has expired according to any of the three
     * constraints: idle timeout, maximum lifetime, or maximum request count.
     *
     * @param timeoutMs        idle timeout in milliseconds (time since last access)
     * @param maxLifetimeMs    maximum session lifetime in milliseconds (time since creation)
     * @param maxRequestsLimit maximum number of requests permitted
     * @return {@code true} if the session is destroyed or any constraint is exceeded
     */
    public boolean isExpired(long timeoutMs, long maxLifetimeMs, int maxRequestsLimit) {
        if (destroyed) {
            return true;
        }
        long now = System.currentTimeMillis();
        if (now - lastAccessTime > timeoutMs) {
            return true;
        }
        if (now - createTime > maxLifetimeMs) {
            return true;
        }
        return requestCount >= maxRequestsLimit;
    }

    /**
     * Returns the remaining lifetime in milliseconds before this session exceeds
     * the given {@code maxLifetimeMs}.
     *
     * @param maxLifetimeMs the maximum lifetime to compare against
     * @return remaining milliseconds (may be negative or zero if already expired)
     */
    public long remainingLifetimeMs(long maxLifetimeMs) {
        return (createTime + maxLifetimeMs) - System.currentTimeMillis();
    }

    // ========== Destruction ==========

    /**
     * Destroys this session by zeroing all key material and setting the destroyed flag.
     *
     * <p>This method is <b>idempotent</b> — calling it multiple times has no
     * additional effect and does not throw.
     */
    public synchronized void destroy() {
        if (destroyed) {
            return;
        }
        Arrays.fill(sm4Key, (byte) 0);
        Arrays.fill(sm4Iv, (byte) 0);
        this.destroyed = true;
    }

    // ========== Internal ==========

    private void checkNotDestroyed() {
        if (destroyed) {
            throw new IllegalStateException(
                    "Session [" + sessionId + "] has been destroyed");
        }
    }

    @Override
    public String toString() {
        return "Session{" +
                "sessionId='" + sessionId + '\'' +
                ", clientId='" + clientId + '\'' +
                ", peerId='" + peerId + '\'' +
                ", createTime=" + createTime +
                ", lastAccessTime=" + lastAccessTime +
                ", maxLifetime=" + maxLifetime +
                ", maxRequests=" + maxRequests +
                ", requestCount=" + requestCount +
                ", rekeyVersion=" + rekeyVersion +
                ", destroyed=" + destroyed +
                '}';
    }
}
