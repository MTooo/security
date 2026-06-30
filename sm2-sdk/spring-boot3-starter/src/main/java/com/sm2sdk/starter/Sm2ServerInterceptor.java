package com.sm2sdk.starter;

import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.nonce.NonceValidator;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SM2 服务端请求拦截器，负责解密请求体、验证会话和 Nonce。
 *
 * <p>拦截除握手端点外的所有业务请求，在请求到达 Controller 前：
 * <ol>
 *   <li>提取并验证请求头：X-Session-Id、X-Timestamp、X-Nonce</li>
 *   <li>从 SessionManager 获取会话并检查有效性</li>
 *   <li>使用会话密钥解密请求体</li>
 *   <li>将解密后的明文存入请求属性 {@value #PLAIN_BODY_ATTRIBUTE}</li>
 *   <li>可选：执行 Nonce 重放校验</li>
 * </ol>
 *
 * <p>Controller 通过请求属性获取解密后的明文，处理后返回明文响应——无需关心加解密细节。
 *
 * <p>配合 {@link Sm2ResponseBodyAdvice} 使用可实现完整的请求解密 → 业务处理 → 响应加密闭环。
 */
public class Sm2ServerInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Sm2ServerInterceptor.class);

    /** 解密后的请求体存储在请求属性中的键名 */
    public static final String PLAIN_BODY_ATTRIBUTE = "sm2.plainBody";

    /** 当前会话存储在请求属性中的键名 */
    public static final String SESSION_ATTRIBUTE = "sm2.session";

    private final SessionManager sessionManager;
    private final Sm2ServerConfig config;
    private final NonceValidator nonceValidator;

    /**
     * 创建服务端拦截器（不含 Nonce 校验）。
     *
     * @param sessionManager 会话管理器
     * @param config         服务端配置
     */
    public Sm2ServerInterceptor(SessionManager sessionManager, Sm2ServerConfig config) {
        this(sessionManager, config, null);
    }

    /**
     * 创建服务端拦截器（含可选的 Nonce 校验）。
     *
     * @param sessionManager 会话管理器
     * @param config         服务端配置
     * @param nonceValidator Nonce 重放验证器（可为 null 禁用）
     */
    public Sm2ServerInterceptor(SessionManager sessionManager, Sm2ServerConfig config,
                                NonceValidator nonceValidator) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.nonceValidator = nonceValidator;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        String path = request.getRequestURI();

        // 跳过握手端点
        if (path.endsWith(config.getHandshakeInitPath())
                || path.endsWith(config.getHandshakeConfirmPath())) {
            return true;
        }

        // 步骤 1: 提取请求头
        String sessionId = request.getHeader("X-Session-Id");
        String nonce = request.getHeader("X-Nonce");

        if (sessionId == null || sessionId.isEmpty()) {
            throw new Sm2SdkException(ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED,
                    "缺少请求头 X-Session-Id");
        }

        // 步骤 2: 获取并验证会话
        Session session;
        try {
            session = sessionManager.getSession(sessionId);
        } catch (Sm2SdkException e) {
            response.setStatus(401);
            response.setHeader("X-Session-Expired", "true");
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":\""
                    + e.getErrorCode().getCode()
                    + "\",\"message\":\"会话已过期\"}");
            return false;
        }

        // 步骤 3: Nonce 重放校验
        if (config.isNonceValidationEnabled() && nonceValidator != null
                && nonce != null && !nonce.isEmpty()) {
            try {
                if (nonceValidator.isDuplicate(nonce)) {
                    throw new Sm2SdkException(ErrorCode.NONCE_REPLAY,
                            "Nonce 重放检测: " + nonce);
                }
                // 校验通过，记录 nonce
                nonceValidator.markUsed(nonce);
            } catch (Sm2SdkException e) {
                throw e;
            }
        }

        // 步骤 4: 读取并解密请求体
        String encryptedBody = readRequestBody(request);
        if (encryptedBody != null && !encryptedBody.isEmpty()) {
            try {
                String plainBody = sessionManager.decryptBody(sessionId, encryptedBody);
                request.setAttribute(PLAIN_BODY_ATTRIBUTE, plainBody);
            } catch (Sm2SdkException e) {
                // SM4 解密失败 — 通知客户端需重新握手
                response.setStatus(400);
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"code\":\"21202\","
                        + "\"message\":\"SM4解密失败，TAG校验不通过\"}");
                return false;
            }
        }

        // 步骤 5: 刷新会话并将会话对象存入请求属性
        session.touch();
        request.setAttribute(SESSION_ATTRIBUTE, session);

        log.debug("请求解密成功: sessionId={}, path={}", sessionId, path);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理请求属性中的敏感数据
        request.removeAttribute(PLAIN_BODY_ATTRIBUTE);
        request.removeAttribute(SESSION_ATTRIBUTE);
    }

    /**
     * 从 HttpServletRequest 中读取完整的请求体字符串。
     *
     * @param request HTTP 请求
     * @return 请求体字符串，若为空则返回 null
     */
    private String readRequestBody(HttpServletRequest request) {
        try {
            int contentLength = request.getContentLength();
            if (contentLength <= 0) {
                return null;
            }

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8));
            String body = reader.lines().collect(Collectors.joining("\n"));
            return body.isEmpty() ? null : body;
        } catch (Exception e) {
            log.warn("读取请求体失败: {}", e.getMessage());
            return null;
        }
    }
}
