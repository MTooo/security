package com.sm2sdk.core.model;

/**
 * Server-to-client handshake response message.
 *
 * <p>Sent by the server after validating the client's {@link HandshakeInit}.
 * Contains the session identifier, the server's ephemeral public key, and the
 * SB confirmation value.
 *
 * <p>Binary values (ephemeral public key, confirmation) are stored as
 * Base64-encoded strings matching the Section 2.2 wire format.
 */
public class HandshakeServerResp {

    private String sessionId;
    private String ephemeralPublicKey;
    private String confirmation;

    /** No-arg constructor for JSON deserialisation. */
    public HandshakeServerResp() {
    }

    /**
     * Constructs a fully populated handshake server response.
     *
     * @param sessionId          the assigned session identifier
     * @param ephemeralPublicKey the server's ephemeral public key, Base64-encoded
     * @param confirmation       the SB confirmation value, Base64-encoded
     */
    public HandshakeServerResp(String sessionId, String ephemeralPublicKey,
                               String confirmation) {
        this.sessionId = sessionId;
        this.ephemeralPublicKey = ephemeralPublicKey;
        this.confirmation = confirmation;
    }

    // ========== Getters / Setters ==========

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getEphemeralPublicKey() {
        return ephemeralPublicKey;
    }

    public void setEphemeralPublicKey(String ephemeralPublicKey) {
        this.ephemeralPublicKey = ephemeralPublicKey;
    }

    public String getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(String confirmation) {
        this.confirmation = confirmation;
    }

    @Override
    public String toString() {
        return "HandshakeServerResp{" +
                "sessionId='" + sessionId + '\'' +
                '}';
    }
}
