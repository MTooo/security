package com.sm2sdk.starter;

import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 全局异常处理器，将所有异常统一转换为结构化的 JSON 错误响应。
 *
 * <p>响应格式：
 * <pre>{@code
 * {
 *   "code": "29002",
 *   "message": "签名校验失败",
 *   "detail": "客户端签名验证失败"
 * }
 * }</pre>
 */
@RestControllerAdvice
public class Sm2SdkExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(Sm2SdkExceptionHandler.class);

    /** SDK 业务异常 */
    @ExceptionHandler(Sm2SdkException.class)
    public ResponseEntity<Map<String, String>> handleSm2SdkException(Sm2SdkException ex) {
        int httpStatus = ex.getHttpStatus();
        String code = ex.getErrorCode().getCode();
        String errorMsg = ex.getErrorCode().getMessage();
        String detail = ex.getMessage();

        log.error("SDK 异常: code={}, http={}, detail={}", code, httpStatus, detail);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", errorMsg);
        body.put("detail", detail);

        return ResponseEntity.status(httpStatus).body(body);
    }

    /** 请求体解析失败（如 JSON 格式错误、@RequestBody 缺失） */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex) {
        log.error("请求体解析失败: {}", ex.getMessage());

        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", ErrorCode.CLIENT_INIT_FAILED.getCode());
        body.put("message", "请求体解析失败");
        body.put("detail", ex.getMessage());

        return ResponseEntity.status(400).body(body);
    }

    /** 兜底：其他未处理的异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception ex) {
        log.error("未处理异常", ex);

        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", ErrorCode.UNKNOWN_ERROR.getCode());
        body.put("message", ErrorCode.UNKNOWN_ERROR.getMessage());
        body.put("detail", ex.getMessage() != null ? ex.getMessage() : "未知错误");

        return ResponseEntity.status(500).body(body);
    }
}
