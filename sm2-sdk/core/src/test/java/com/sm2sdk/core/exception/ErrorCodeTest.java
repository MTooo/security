package com.sm2sdk.core.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void shouldHaveAll33ErrorCodes() {
        assertEquals(33, ErrorCode.values().length);
    }

    @Test
    void shouldHaveClientCodes() {
        assertEquals("11000", ErrorCode.CLIENT_INIT_FAILED.getCode());
        assertEquals("21202", ErrorCode.SM4_DECRYPT_TAG_FAILED.getCode());
        assertEquals("11301", ErrorCode.SESSION_EXPIRED.getCode());
        assertEquals("31401", ErrorCode.CLIENT_PRIVATE_KEY_MISSING.getCode());
    }

    @Test
    void shouldHaveServerCodes() {
        assertEquals("22301", ErrorCode.SESSION_NOT_FOUND.getCode());
        assertEquals("22101", ErrorCode.CLIENT_CERT_VERIFY_FAILED.getCode());
    }

    @Test
    void shouldHaveGeneralCodes() {
        assertEquals("39000", ErrorCode.UNKNOWN_ERROR.getCode());
        assertEquals("29001", ErrorCode.NONCE_REPLAY.getCode());
        assertEquals("19003", ErrorCode.CIRCUIT_BREAKER_OPEN.getCode());
    }

    @Test
    void shouldMapHttpStatus() {
        assertEquals(400, ErrorCode.SM4_DECRYPT_TAG_FAILED.getHttpStatus());
        assertEquals(401, ErrorCode.SESSION_EXPIRED.getHttpStatus());
        assertEquals(403, ErrorCode.NONCE_REPLAY.getHttpStatus());
        assertEquals(408, ErrorCode.HANDSHAKE_TIMEOUT.getHttpStatus());
        assertEquals(429, ErrorCode.SESSION_LIMIT_EXCEEDED.getHttpStatus());
        assertEquals(500, ErrorCode.UNKNOWN_ERROR.getHttpStatus());
    }

    @Test
    void shouldExtractSeverity() {
        assertEquals(1, ErrorCode.CLIENT_INIT_FAILED.getSeverity());
        assertEquals(2, ErrorCode.SM4_DECRYPT_TAG_FAILED.getSeverity());
        assertEquals(3, ErrorCode.CLIENT_PRIVATE_KEY_MISSING.getSeverity());
    }

    @Test
    void shouldHaveUniqueCodes() {
        ErrorCode[] values = ErrorCode.values();
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                assertNotEquals(values[i].getCode(), values[j].getCode(),
                        "Duplicate code between " + values[i] + " and " + values[j]);
            }
        }
    }

    @Test
    void shouldProduceReadableMessage() {
        assertNotNull(ErrorCode.UNKNOWN_ERROR.getMessage());
        assertFalse(ErrorCode.UNKNOWN_ERROR.getMessage().isEmpty());
        assertTrue(ErrorCode.UNKNOWN_ERROR.getMessage().contains("Unknown"));
    }

    @Test
    void sm2SdkExceptionShouldCarryErrorCode() {
        Sm2SdkException ex = new Sm2SdkException(ErrorCode.UNKNOWN_ERROR);
        assertEquals(ErrorCode.UNKNOWN_ERROR, ex.getErrorCode());
        assertEquals(500, ex.getHttpStatus());
    }

    @Test
    void sm2SdkExceptionShouldCarryDetail() {
        Sm2SdkException ex = new Sm2SdkException(ErrorCode.UNKNOWN_ERROR, "Something went wrong");
        assertEquals("Something went wrong", ex.getMessage());
        assertEquals(ErrorCode.UNKNOWN_ERROR, ex.getErrorCode());
    }

    @Test
    void sm2SdkExceptionShouldCarryCause() {
        Throwable cause = new RuntimeException("root cause");
        Sm2SdkException ex = new Sm2SdkException(ErrorCode.UNKNOWN_ERROR, "detail", cause);
        assertSame(cause, ex.getCause());
        assertEquals("detail", ex.getMessage());
    }

    @Test
    void sm2SdkExceptionShouldCarrySessionId() {
        Sm2SdkException ex = new Sm2SdkException(ErrorCode.UNKNOWN_ERROR, "detail", "session-123");
        assertEquals("session-123", ex.getSessionId());
        assertEquals("detail", ex.getMessage());
    }

    @Test
    void sm2SdkExceptionShouldBeRuntimeException() {
        assertInstanceOf(RuntimeException.class, new Sm2SdkException(ErrorCode.UNKNOWN_ERROR));
    }

    @Test
    void allCodesShouldBeExactlyFiveDigits() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertEquals(5, ec.getCode().length(),
                    "Code " + ec.getCode() + " must be exactly 5 digits");
        }
    }
}
