package io.github.mtooo.starter;

import io.github.mtooo.core.access.Sm2AccessController;
import io.github.mtooo.core.annotation.Sm2Secured;
import io.github.mtooo.core.exception.ErrorCode;
import io.github.mtooo.core.exception.Sm2SdkException;
import io.github.mtooo.core.nonce.NonceValidator;
import io.github.mtooo.core.session.Session;
import io.github.mtooo.core.session.SessionManager;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Objects;

/**
 * SM2 服务端请求拦截器，负责验证会话、Nonce 和 @Sm2Secured 注解。
 *
 * <p>body 解密由 {@link Sm2RequestBodyAdvice} 处理，
 * 响应加密由 {@link Sm2ResponseBodyAdvice} 处理。
 */
public class Sm2ServerInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(Sm2ServerInterceptor.class);

    /** 当前会话存储在请求属性中的键名 */
    public static final String SESSION_ATTRIBUTE = "sm2.session";

    private final SessionManager sessionManager;
    private final Sm2ServerConfig config;
    private final NonceValidator nonceValidator;
    private final Sm2AccessController accessController;

    public Sm2ServerInterceptor(SessionManager sessionManager, Sm2ServerConfig config) {
        this(sessionManager, config, null, null);
    }

    public Sm2ServerInterceptor(SessionManager sessionManager, Sm2ServerConfig config,
                                NonceValidator nonceValidator) {
        this(sessionManager, config, nonceValidator, null);
    }

    public Sm2ServerInterceptor(SessionManager sessionManager, Sm2ServerConfig config,
                                NonceValidator nonceValidator,
                                Sm2AccessController accessController) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.nonceValidator = nonceValidator;
        this.accessController = accessController;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {

        String path = request.getRequestURI();

        if (path.endsWith(config.getHandshakeInitPath())
                || path.endsWith(config.getHandshakeConfirmPath())) {
            return true;
        }

        if (!isSm2Secured(handler)) {
            return true;
        }

        String sessionId = request.getHeader("X-Session-Id");
        String nonce = request.getHeader("X-Nonce");

        if (sessionId == null || sessionId.isEmpty()) {
            throw new Sm2SdkException(ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED,
                    "缺少请求头 X-Session-Id");
        }

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

        if (config.isNonceValidationEnabled() && nonceValidator != null
                && nonce != null && !nonce.isEmpty()) {
            try {
                if (nonceValidator.isDuplicate(nonce)) {
                    throw new Sm2SdkException(ErrorCode.NONCE_REPLAY,
                            "Nonce 重放检测: " + nonce);
                }
                nonceValidator.markUsed(nonce);
            } catch (Sm2SdkException e) {
                throw e;
            }
        }

        session.touch();
        request.setAttribute(SESSION_ATTRIBUTE, session);

        // 客户端访问控制检查
        if (accessController != null) {
            String clientId = session.getClientId();
            if (!accessController.isAllowed(clientId, path)) {
                log.warn("客户端访问被拒绝: clientId={}, path={}, sessionId={}",
                        clientId, path, sessionId);
                throw new Sm2SdkException(ErrorCode.CLIENT_ACCESS_DENIED,
                        "客户端 [" + clientId + "] 无权访问路径: " + path);
            }
        }

        log.debug("会话验证成功: sessionId={}, path={}", sessionId, path);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        request.removeAttribute(SESSION_ATTRIBUTE);
    }

    private boolean isSm2Secured(Object handler) {
        if (handler instanceof HandlerMethod) {
            HandlerMethod hm = (HandlerMethod) handler;
            if (hm.hasMethodAnnotation(Sm2Secured.class)) {
                return true;
            }
            // 向上遍历父类以兼容 CGLIB 代理
            for (Class<?> clazz = hm.getBeanType();
                 clazz != null && clazz != Object.class;
                 clazz = clazz.getSuperclass()) {
                if (clazz.isAnnotationPresent(Sm2Secured.class)) {
                    return true;
                }
            }
        }
        return false;
    }
}
