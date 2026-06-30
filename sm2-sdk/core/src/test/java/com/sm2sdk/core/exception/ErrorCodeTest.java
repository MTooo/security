package com.sm2sdk.core.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {

    @Test
    void shouldHaveAll35ErrorCodes() {
        assertEquals(35, ErrorCode.values().length);
    }

    // ========== Client Error Codes ==========

    @Test
    void clientInitFailed() {
        assertEquals("11000", ErrorCode.CLIENT_INIT_FAILED.getCode());
        assertEquals(500, ErrorCode.CLIENT_INIT_FAILED.getHttpStatus());
        assertEquals("客户端初始化失败", ErrorCode.CLIENT_INIT_FAILED.getMessage());
    }

    @Test
    void tempPubkeyGenFailed() {
        assertEquals("11101", ErrorCode.TEMP_PUBKEY_GEN_FAILED.getCode());
        assertEquals(400, ErrorCode.TEMP_PUBKEY_GEN_FAILED.getHttpStatus());
        assertEquals("临时公钥生成失败", ErrorCode.TEMP_PUBKEY_GEN_FAILED.getMessage());
    }

    @Test
    void localSignGenFailed() {
        assertEquals("11102", ErrorCode.LOCAL_SIGN_GEN_FAILED.getCode());
        assertEquals(400, ErrorCode.LOCAL_SIGN_GEN_FAILED.getHttpStatus());
        assertEquals("本地签名生成失败", ErrorCode.LOCAL_SIGN_GEN_FAILED.getMessage());
    }

    @Test
    void serverCertVerifyFailed() {
        assertEquals("21103", ErrorCode.SERVER_CERT_VERIFY_FAILED.getCode());
        assertEquals(400, ErrorCode.SERVER_CERT_VERIFY_FAILED.getHttpStatus());
        assertEquals("服务端证书验签失败", ErrorCode.SERVER_CERT_VERIFY_FAILED.getMessage());
    }

    @Test
    void serverTempPubkeyNotOnCurve() {
        assertEquals("11104", ErrorCode.SERVER_TEMP_PUBKEY_NOT_ON_CURVE.getCode());
        assertEquals(403, ErrorCode.SERVER_TEMP_PUBKEY_NOT_ON_CURVE.getHttpStatus());
        assertEquals("服务端临时公钥不在曲线上", ErrorCode.SERVER_TEMP_PUBKEY_NOT_ON_CURVE.getMessage());
    }

    @Test
    void sharedSecretCalcFailed() {
        assertEquals("21105", ErrorCode.SHARED_SECRET_CALC_FAILED.getCode());
        assertEquals(400, ErrorCode.SHARED_SECRET_CALC_FAILED.getHttpStatus());
        assertEquals("共享密钥计算失败(x1=0)", ErrorCode.SHARED_SECRET_CALC_FAILED.getMessage());
    }

    @Test
    void keyConfirmFailedSb() {
        assertEquals("21106", ErrorCode.KEY_CONFIRM_FAILED_SB.getCode());
        assertEquals(400, ErrorCode.KEY_CONFIRM_FAILED_SB.getHttpStatus());
        assertEquals("密钥确认失败(SB验证不通过)", ErrorCode.KEY_CONFIRM_FAILED_SB.getMessage());
    }

    @Test
    void timestampDeviationExceeded() {
        assertEquals("21107", ErrorCode.TIMESTAMP_DEVIATION_EXCEEDED.getCode());
        assertEquals(400, ErrorCode.TIMESTAMP_DEVIATION_EXCEEDED.getHttpStatus());
        assertEquals("时间戳偏差超限(>300s)", ErrorCode.TIMESTAMP_DEVIATION_EXCEEDED.getMessage());
    }

    @Test
    void handshakeTimeout() {
        assertEquals("11108", ErrorCode.HANDSHAKE_TIMEOUT.getCode());
        assertEquals(408, ErrorCode.HANDSHAKE_TIMEOUT.getHttpStatus());
        assertEquals("握手超时", ErrorCode.HANDSHAKE_TIMEOUT.getMessage());
    }

    @Test
    void sm4EncryptFailed() {
        assertEquals("11201", ErrorCode.SM4_ENCRYPT_FAILED.getCode());
        assertEquals(500, ErrorCode.SM4_ENCRYPT_FAILED.getHttpStatus());
        assertEquals("SM4加密失败", ErrorCode.SM4_ENCRYPT_FAILED.getMessage());
    }

    @Test
    void sm4DecryptTagFailed() {
        assertEquals("21202", ErrorCode.SM4_DECRYPT_TAG_FAILED.getCode());
        assertEquals(400, ErrorCode.SM4_DECRYPT_TAG_FAILED.getHttpStatus());
        assertEquals("SM4解密失败 - TAG校验失败", ErrorCode.SM4_DECRYPT_TAG_FAILED.getMessage());
    }

    @Test
    void decryptSessionKeyMissing() {
        assertEquals("11203", ErrorCode.DECRYPT_SESSION_KEY_MISSING.getCode());
        assertEquals(500, ErrorCode.DECRYPT_SESSION_KEY_MISSING.getHttpStatus());
        assertEquals("解密失败 - 会话密钥不存在", ErrorCode.DECRYPT_SESSION_KEY_MISSING.getMessage());
    }

    @Test
    void ivReuse() {
        assertEquals("11204", ErrorCode.IV_REUSE.getCode());
        assertEquals(500, ErrorCode.IV_REUSE.getHttpStatus());
        assertEquals("IV重复使用", ErrorCode.IV_REUSE.getMessage());
    }

    @Test
    void sessionExpired() {
        assertEquals("11301", ErrorCode.SESSION_EXPIRED.getCode());
        assertEquals(401, ErrorCode.SESSION_EXPIRED.getHttpStatus());
        assertEquals("会话已过期", ErrorCode.SESSION_EXPIRED.getMessage());
    }

    @Test
    void sessionRequestLimitExceeded() {
        assertEquals("11302", ErrorCode.SESSION_REQUEST_LIMIT_EXCEEDED.getCode());
        assertEquals(401, ErrorCode.SESSION_REQUEST_LIMIT_EXCEEDED.getHttpStatus());
        assertEquals("会话请求次数超限", ErrorCode.SESSION_REQUEST_LIMIT_EXCEEDED.getMessage());
    }

    @Test
    void sessionStateInvalid() {
        assertEquals("21303", ErrorCode.SESSION_STATE_INVALID.getCode());
        assertEquals(500, ErrorCode.SESSION_STATE_INVALID.getHttpStatus());
        assertEquals("会话状态异常", ErrorCode.SESSION_STATE_INVALID.getMessage());
    }

    @Test
    void clientPrivateKeyNotConfigured() {
        assertEquals("31401", ErrorCode.CLIENT_PRIVATE_KEY_NOT_CONFIGURED.getCode());
        assertEquals(500, ErrorCode.CLIENT_PRIVATE_KEY_NOT_CONFIGURED.getHttpStatus());
        assertEquals("客户端私钥未配置", ErrorCode.CLIENT_PRIVATE_KEY_NOT_CONFIGURED.getMessage());
    }

    @Test
    void serverPubkeyNotConfigured() {
        assertEquals("31402", ErrorCode.SERVER_PUBKEY_NOT_CONFIGURED.getCode());
        assertEquals(500, ErrorCode.SERVER_PUBKEY_NOT_CONFIGURED.getHttpStatus());
        assertEquals("服务端公钥未配置", ErrorCode.SERVER_PUBKEY_NOT_CONFIGURED.getMessage());
    }

    @Test
    void keyFileReadFailed() {
        assertEquals("21403", ErrorCode.KEY_FILE_READ_FAILED.getCode());
        assertEquals(500, ErrorCode.KEY_FILE_READ_FAILED.getHttpStatus());
        assertEquals("密钥文件读取失败", ErrorCode.KEY_FILE_READ_FAILED.getMessage());
    }

    @Test
    void networkConnectionFailed() {
        assertEquals("11501", ErrorCode.NETWORK_CONNECTION_FAILED.getCode());
        assertEquals(500, ErrorCode.NETWORK_CONNECTION_FAILED.getHttpStatus());
        assertEquals("网络连接失败", ErrorCode.NETWORK_CONNECTION_FAILED.getMessage());
    }

    @Test
    void httpRequestFailed() {
        assertEquals("11502", ErrorCode.HTTP_REQUEST_FAILED.getCode());
        assertEquals(502, ErrorCode.HTTP_REQUEST_FAILED.getHttpStatus());
        assertEquals("HTTP请求失败(>=400)", ErrorCode.HTTP_REQUEST_FAILED.getMessage());
    }

    // ========== Server Error Codes ==========

    @Test
    void clientCertVerifyFailed() {
        assertEquals("22101", ErrorCode.CLIENT_CERT_VERIFY_FAILED.getCode());
        assertEquals(400, ErrorCode.CLIENT_CERT_VERIFY_FAILED.getHttpStatus());
        assertEquals("客户端证书验签失败", ErrorCode.CLIENT_CERT_VERIFY_FAILED.getMessage());
    }

    @Test
    void clientTempPubkeyInvalid() {
        assertEquals("22102", ErrorCode.CLIENT_TEMP_PUBKEY_INVALID.getCode());
        assertEquals(403, ErrorCode.CLIENT_TEMP_PUBKEY_INVALID.getHttpStatus());
        assertEquals("客户端临时公钥非法", ErrorCode.CLIENT_TEMP_PUBKEY_INVALID.getMessage());
    }

    @Test
    void keyConfirmFailedSa() {
        assertEquals("22103", ErrorCode.KEY_CONFIRM_FAILED_SA.getCode());
        assertEquals(400, ErrorCode.KEY_CONFIRM_FAILED_SA.getHttpStatus());
        assertEquals("密钥确认失败(SA验证失败)", ErrorCode.KEY_CONFIRM_FAILED_SA.getMessage());
    }

    @Test
    void duplicateHandshakeRequest() {
        assertEquals("12104", ErrorCode.DUPLICATE_HANDSHAKE_REQUEST.getCode());
        assertEquals(200, ErrorCode.DUPLICATE_HANDSHAKE_REQUEST.getHttpStatus());
        assertEquals("重复握手请求", ErrorCode.DUPLICATE_HANDSHAKE_REQUEST.getMessage());
    }

    @Test
    void sessionNotFoundOrExpired() {
        assertEquals("22301", ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED.getCode());
        assertEquals(401, ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED.getHttpStatus());
        assertEquals("会话不存在或已过期", ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED.getMessage());
    }

    @Test
    void sessionTampered() {
        assertEquals("22302", ErrorCode.SESSION_TAMPERED.getCode());
        assertEquals(500, ErrorCode.SESSION_TAMPERED.getHttpStatus());
        assertEquals("会话被篡改(Redis数据异常)", ErrorCode.SESSION_TAMPERED.getMessage());
    }

    @Test
    void sessionCountExceeded() {
        assertEquals("12303", ErrorCode.SESSION_COUNT_EXCEEDED.getCode());
        assertEquals(429, ErrorCode.SESSION_COUNT_EXCEEDED.getHttpStatus());
        assertEquals("会话数超过上限", ErrorCode.SESSION_COUNT_EXCEEDED.getMessage());
    }

    @Test
    void requestRateLimitExceeded() {
        assertEquals("22501", ErrorCode.REQUEST_RATE_LIMIT_EXCEEDED.getCode());
        assertEquals(429, ErrorCode.REQUEST_RATE_LIMIT_EXCEEDED.getHttpStatus());
        assertEquals("请求频率超限", ErrorCode.REQUEST_RATE_LIMIT_EXCEEDED.getMessage());
    }

    // ========== General Error Codes ==========

    @Test
    void unknownError() {
        assertEquals("39000", ErrorCode.UNKNOWN_ERROR.getCode());
        assertEquals(500, ErrorCode.UNKNOWN_ERROR.getHttpStatus());
        assertEquals("未知异常", ErrorCode.UNKNOWN_ERROR.getMessage());
    }

    @Test
    void nonceReplay() {
        assertEquals("29001", ErrorCode.NONCE_REPLAY.getCode());
        assertEquals(403, ErrorCode.NONCE_REPLAY.getHttpStatus());
        assertEquals("Nonce重复(防重放拦截)", ErrorCode.NONCE_REPLAY.getMessage());
    }

    @Test
    void signatureVerifyFailed() {
        assertEquals("29002", ErrorCode.SIGNATURE_VERIFY_FAILED.getCode());
        assertEquals(403, ErrorCode.SIGNATURE_VERIFY_FAILED.getHttpStatus());
        assertEquals("签名校验失败", ErrorCode.SIGNATURE_VERIFY_FAILED.getMessage());
    }

    @Test
    void circuitBreakerTripped() {
        assertEquals("19003", ErrorCode.CIRCUIT_BREAKER_TRIPPED.getCode());
        assertEquals(503, ErrorCode.CIRCUIT_BREAKER_TRIPPED.getHttpStatus());
        assertEquals("连续握手失败触发熔断", ErrorCode.CIRCUIT_BREAKER_TRIPPED.getMessage());
    }

    @Test
    void memoryCleanupFailed() {
        assertEquals("19004", ErrorCode.MEMORY_CLEANUP_FAILED.getCode());
        assertEquals(500, ErrorCode.MEMORY_CLEANUP_FAILED.getHttpStatus());
        assertEquals("内存清理失败", ErrorCode.MEMORY_CLEANUP_FAILED.getMessage());
    }

    @Test
    void threadPoolFull() {
        assertEquals("29005", ErrorCode.THREAD_POOL_FULL.getCode());
        assertEquals(500, ErrorCode.THREAD_POOL_FULL.getHttpStatus());
        assertEquals("线程池满", ErrorCode.THREAD_POOL_FULL.getMessage());
    }

    // ========== Structural Tests ==========

    @Test
    void shouldMapHttpStatus() {
        assertEquals(400, ErrorCode.TEMP_PUBKEY_GEN_FAILED.getHttpStatus());
        assertEquals(401, ErrorCode.SESSION_EXPIRED.getHttpStatus());
        assertEquals(403, ErrorCode.NONCE_REPLAY.getHttpStatus());
        assertEquals(408, ErrorCode.HANDSHAKE_TIMEOUT.getHttpStatus());
        assertEquals(429, ErrorCode.SESSION_COUNT_EXCEEDED.getHttpStatus());
        assertEquals(500, ErrorCode.UNKNOWN_ERROR.getHttpStatus());
        assertEquals(502, ErrorCode.HTTP_REQUEST_FAILED.getHttpStatus());
        assertEquals(503, ErrorCode.CIRCUIT_BREAKER_TRIPPED.getHttpStatus());
    }

    @Test
    void shouldExtractSeverity() {
        assertEquals(1, ErrorCode.CLIENT_INIT_FAILED.getSeverity());
        assertEquals(1, ErrorCode.TEMP_PUBKEY_GEN_FAILED.getSeverity());
        assertEquals(1, ErrorCode.HANDSHAKE_TIMEOUT.getSeverity());
        assertEquals(2, ErrorCode.SM4_DECRYPT_TAG_FAILED.getSeverity());
        assertEquals(2, ErrorCode.SERVER_CERT_VERIFY_FAILED.getSeverity());
        assertEquals(3, ErrorCode.CLIENT_PRIVATE_KEY_NOT_CONFIGURED.getSeverity());
        assertEquals(3, ErrorCode.UNKNOWN_ERROR.getSeverity());
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
    void allCodesShouldBeExactlyFiveDigits() {
        for (ErrorCode ec : ErrorCode.values()) {
            assertEquals(5, ec.getCode().length(),
                    "Code " + ec.getCode() + " must be exactly 5 digits");
        }
    }
}
