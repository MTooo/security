package com.sm2sdk.core.model;

/**
 * Client-to-server handshake initiation message.
 *
 * <p>Carries the client's protocol version, identity, ephemeral public key,
 * timestamp, signature, and ZA value — all as plain/String fields suitable for
 * JSON serialisation/deserialisation with Jackson.
 *
 * <p>Binary values (ephemeral public key, signature, ZA) are stored as
 * Base64-encoded strings matching the Section 2.2 wire format.
 */
public class HandshakeInit {

    private String protocolVersion;
    private String clientId;
    private String ephemeralPublicKey;
    private long timestamp;
    private String signature;
    private String ZA;

    /** No-arg constructor for JSON deserialisation. */
    public HandshakeInit() {
    }

    /**
     * Constructs a fully populated handshake initiation.
     *
     * @param protocolVersion    the protocol version string (e.g. "1.0")
     * @param clientId           the client identifier
     * @param ephemeralPublicKey the client's ephemeral public key, Base64-encoded
     * @param timestamp          the request timestamp in epoch millis
     * @param signature          the client's signature, Base64-encoded
     * @param ZA                 the client's ZA value, Base64-encoded
     */
    public HandshakeInit(String protocolVersion, String clientId,
                         String ephemeralPublicKey, long timestamp,
                         String signature, String ZA) {
        this.protocolVersion = protocolVersion;
        this.clientId = clientId;
        this.ephemeralPublicKey = ephemeralPublicKey;
        this.timestamp = timestamp;
        this.signature = signature;
        this.ZA = ZA;
    }

    // ========== Getters / Setters ==========

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getEphemeralPublicKey() {
        return ephemeralPublicKey;
    }

    public void setEphemeralPublicKey(String ephemeralPublicKey) {
        this.ephemeralPublicKey = ephemeralPublicKey;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSignature() {
        return signature;
    }

    public void setSignature(String signature) {
        this.signature = signature;
    }

    public String getZA() {
        return ZA;
    }

    public void setZA(String ZA) {
        this.ZA = ZA;
    }

    @Override
    public String toString() {
        return "HandshakeInit{" +
                "protocolVersion='" + protocolVersion + '\'' +
                ", clientId='" + clientId + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
