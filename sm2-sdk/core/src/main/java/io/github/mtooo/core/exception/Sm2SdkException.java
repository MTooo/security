package io.github.mtooo.core.exception;

/**
 * Base exception for all SM2 SDK errors.
 *
 * <p>Carries an {@link ErrorCode} with its associated HTTP status,
 * an optional detail message, and an optional session identifier
 * for correlating failures in a multi-party exchange.
 */
public class Sm2SdkException extends RuntimeException {

    private final ErrorCode errorCode;
    private final int httpStatus;
    private final String sessionId;

    private static final long serialVersionUID = 1L;

    /**
     * Creates an exception from an {@link ErrorCode} alone.
     * The exception message is set to the enum's default message.
     */
    public Sm2SdkException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
        this.sessionId = null;
    }

    /**
     * Creates an exception from an {@link ErrorCode} with a custom detail message.
     */
    public Sm2SdkException(ErrorCode errorCode, String detail) {
        super(detail);
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
        this.sessionId = null;
    }

    /**
     * Creates an exception from an {@link ErrorCode} with a detail message
     * and a root cause.
     */
    public Sm2SdkException(ErrorCode errorCode, String detail, Throwable cause) {
        super(detail, cause);
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
        this.sessionId = null;
    }

    /**
     * Creates an exception from an {@link ErrorCode} with a detail message
     * and a session identifier for correlation.
     */
    public Sm2SdkException(ErrorCode errorCode, String detail, String sessionId) {
        super(detail);
        this.errorCode = errorCode;
        this.httpStatus = errorCode.getHttpStatus();
        this.sessionId = sessionId;
    }

    /**
     * Returns the {@link ErrorCode} associated with this exception.
     */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     * Returns the HTTP status code associated with this exception.
     */
    public int getHttpStatus() {
        return httpStatus;
    }

    /**
     * Returns the session identifier, or {@code null} if none was set.
     */
    public String getSessionId() {
        return sessionId;
    }
}
