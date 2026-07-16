package io.github.mtooo.core.model;

/**
 * Client-to-server handshake confirmation message.
 *
 * <p>Sent by the client after verifying the server's {@link HandshakeServerResp}.
 * Contains the session identifier and the SA confirmation value proving key
 * possession.
 *
 * <p>The confirmation value is stored as a Base64-encoded string matching the
 * Section 2.2 wire format.
 */
public class HandshakeConfirm {

    private String sessionId;
    private String confirmation;

    /** No-arg constructor for JSON deserialisation. */
    public HandshakeConfirm() {
    }

    /**
     * Constructs a fully populated handshake confirmation.
     *
     * @param sessionId    the session identifier
     * @param confirmation the SA confirmation value, Base64-encoded
     */
    public HandshakeConfirm(String sessionId, String confirmation) {
        this.sessionId = sessionId;
        this.confirmation = confirmation;
    }

    // ========== Getters / Setters ==========

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(String confirmation) {
        this.confirmation = confirmation;
    }

    @Override
    public String toString() {
        return "HandshakeConfirm{" +
                "sessionId='" + sessionId + '\'' +
                '}';
    }
}
