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
 *
 * <p>Exactly 35 codes as defined in the specification Section 7.2.
 */
public enum ErrorCode {

    // ========== Client Errors (B=1, 21 codes) ==========
    CLIENT_INIT_FAILED("11000", 500, "客户端初始化失败"),
    TEMP_PUBKEY_GEN_FAILED("11101", 400, "临时公钥生成失败"),
    LOCAL_SIGN_GEN_FAILED("11102", 400, "本地签名生成失败"),
    SERVER_CERT_VERIFY_FAILED("21103", 400, "服务端证书验签失败"),
    SERVER_TEMP_PUBKEY_NOT_ON_CURVE("11104", 403, "服务端临时公钥不在曲线上"),
    SHARED_SECRET_CALC_FAILED("21105", 400, "共享密钥计算失败(x1=0)"),
    KEY_CONFIRM_FAILED_SB("21106", 400, "密钥确认失败(SB验证不通过)"),
    TIMESTAMP_DEVIATION_EXCEEDED("21107", 400, "时间戳偏差超限(>300s)"),
    HANDSHAKE_TIMEOUT("11108", 408, "握手超时"),
    SM4_ENCRYPT_FAILED("11201", 500, "SM4加密失败"),
    SM4_DECRYPT_TAG_FAILED("21202", 400, "SM4解密失败 - TAG校验失败"),
    DECRYPT_SESSION_KEY_MISSING("11203", 500, "解密失败 - 会话密钥不存在"),
    IV_REUSE("11204", 500, "IV重复使用"),
    SESSION_EXPIRED("11301", 401, "会话已过期"),
    SESSION_REQUEST_LIMIT_EXCEEDED("11302", 401, "会话请求次数超限"),
    SESSION_STATE_INVALID("21303", 500, "会话状态异常"),
    CLIENT_PRIVATE_KEY_NOT_CONFIGURED("31401", 500, "客户端私钥未配置"),
    SERVER_PUBKEY_NOT_CONFIGURED("31402", 500, "服务端公钥未配置"),
    KEY_FILE_READ_FAILED("21403", 500, "密钥文件读取失败"),
    NETWORK_CONNECTION_FAILED("11501", 500, "网络连接失败"),
    HTTP_REQUEST_FAILED("11502", 502, "HTTP请求失败(>=400)"),

    // ========== Server Errors (B=2, 8 codes) ==========
    CLIENT_CERT_VERIFY_FAILED("22101", 400, "客户端证书验签失败"),
    CLIENT_TEMP_PUBKEY_INVALID("22102", 403, "客户端临时公钥非法"),
    KEY_CONFIRM_FAILED_SA("22103", 400, "密钥确认失败(SA验证失败)"),
    DUPLICATE_HANDSHAKE_REQUEST("12104", 200, "重复握手请求"),
    SESSION_NOT_FOUND_OR_EXPIRED("22301", 401, "会话不存在或已过期"),
    SESSION_TAMPERED("22302", 500, "会话被篡改(Redis数据异常)"),
    SESSION_COUNT_EXCEEDED("12303", 429, "会话数超过上限"),
    REQUEST_RATE_LIMIT_EXCEEDED("22501", 429, "请求频率超限"),
    CLIENT_ACCESS_DENIED("22502", 403, "客户端无权访问该路径"),

    // ========== General Errors (B=9, 6 codes) ==========
    UNKNOWN_ERROR("39000", 500, "未知异常"),
    NONCE_REPLAY("29001", 403, "Nonce重复(防重放拦截)"),
    SIGNATURE_VERIFY_FAILED("29002", 403, "签名校验失败"),
    CIRCUIT_BREAKER_TRIPPED("19003", 503, "连续握手失败触发熔断"),
    MEMORY_CLEANUP_FAILED("19004", 500, "内存清理失败"),
    THREAD_POOL_FULL("29005", 500, "线程池满");

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
