package com.sm2sdk.core.exception;

/**
 * Standardized error codes for the SM2 SDK.
 *
 * <p>Each error code is a 5-digit number where:
 * <ul>
 *   <li>The first digit indicates severity (1=minor, 2=warning, 3=critical)</li>
 *   <li>The second digit indicates category (1=client, 2=server, 9=general)</li>
 *   <li>The remaining three digits are the specific error number</li>
 * </ul>
 */
public enum ErrorCode {

    // ========== Client Errors (11xxx) ==========
    CLIENT_INIT_FAILED("11000", 500, "Client initialization failed"),
    CLIENT_HANDSHAKE_FAILED("11100", 500, "Client handshake failed"),
    HANDSHAKE_TIMEOUT("11101", 408, "Handshake timed out"),
    CLIENT_SESSION_INVALID("11200", 401, "Client session invalid"),
    SESSION_EXPIRED("11301", 401, "Session has expired"),
    CLIENT_SESSION_MISMATCH("11302", 401, "Client session mismatch"),
    CLIENT_CONFIG_INVALID("11400", 500, "Client configuration is invalid"),
    CLIENT_ENCRYPT_FAILED("11500", 500, "Client encryption failed"),
    CLIENT_DECRYPT_FAILED("11501", 500, "Client decryption failed"),
    CLIENT_SIGN_FAILED("11502", 500, "Client signing failed"),
    SESSION_LIMIT_EXCEEDED("11503", 429, "Session limit exceeded"),

    // ========== Crypto Errors (21xxx) ==========
    SM4_ENCRYPT_FAILED("21201", 500, "SM4 encryption failed"),
    SM4_DECRYPT_TAG_FAILED("21202", 400, "SM4 decryption tag verification failed"),
    SM2_SIGN_FAILED("21203", 500, "SM2 signing failed"),
    SM2_VERIFY_FAILED("21204", 400, "SM2 signature verification failed"),
    KEY_EXCHANGE_FAILED("21205", 500, "Key exchange failed"),

    // ========== Server Errors (22xxx) ==========
    CLIENT_CERT_VERIFY_FAILED("22101", 401, "Client certificate verification failed"),
    SERVER_HANDSHAKE_ERROR("22200", 500, "Server handshake error"),
    SESSION_NOT_FOUND("22301", 404, "Session not found"),
    SERVER_CONFIG_ERROR("22400", 500, "Server configuration error"),
    SERVER_INTERNAL_ERROR("22501", 500, "Server internal error"),

    // ========== Security / Credential Errors (3xxxx) ==========
    CLIENT_PRIVATE_KEY_MISSING("31401", 500, "Client private key is missing"),
    CLIENT_CERT_MISSING("31402", 500, "Client certificate is missing"),
    SERVER_CERT_MISSING("32401", 500, "Server certificate is missing"),

    // ========== General Errors (19xxx, 29xxx, 39xxx) ==========
    TIMESTAMP_INVALID("19001", 400, "Timestamp is invalid"),
    NONCE_INVALID("19002", 400, "Nonce is invalid"),
    CIRCUIT_BREAKER_OPEN("19003", 503, "Circuit breaker is open"),
    NONCE_REPLAY("29001", 403, "Nonce replay detected"),
    INTEGRITY_CHECK_FAILED("29002", 400, "Integrity check failed"),
    DECRYPT_FAILED("29003", 500, "Decryption failed"),
    ENCRYPT_FAILED("29004", 500, "Encryption failed"),
    SIGNATURE_INVALID("29005", 400, "Signature is invalid"),
    UNKNOWN_ERROR("39000", 500, "Unknown error");

    private final String code;
    private final int httpStatus;
    private final String message;

    ErrorCode(String code, int httpStatus, String message) {
        this.code = code;
        this.httpStatus = httpStatus;
        this.message = message;
    }

    /**
     * Returns the 5-digit error code string.
     */
    public String getCode() {
        return code;
    }

    /**
     * Returns the recommended HTTP status code for this error.
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns a human-readable description of this error.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the severity level extracted from the first digit of the error code.
     *
     * @return 1 (minor), 2 (warning), or 3 (critical)
     */
    public int getSeverity() {
        return code.charAt(0) - '0';
    }
}
