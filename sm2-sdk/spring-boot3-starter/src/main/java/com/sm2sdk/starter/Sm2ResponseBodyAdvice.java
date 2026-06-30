package com.sm2sdk.starter;

import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionManager;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.util.Objects;

/**
 * SM2 响应体加密处理器。
 *
 * <p>在 Controller 方法返回后、响应写入客户端前，自动使用会话密钥
 * 加密响应体。与 {@link Sm2ServerInterceptor} 配合使用：
 *
 * <ol>
 *   <li>拦截器解密请求体 → 请求属性</li>
 *   <li>Controller 读取明文 → 处理 → 返回明文响应</li>
 *   <li>本处理器加密明文响应 → 返回给客户端</li>
 * </ol>
 *
 * <p>跳过握手端点路径的响应加密。
 */
@ControllerAdvice
public class Sm2ResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(Sm2ResponseBodyAdvice.class);

    private final SessionManager sessionManager;
    private final Sm2ServerConfig config;

    /**
     * 创建响应体加密处理器。
     *
     * @param sessionManager 会话管理器
     * @param config         服务端配置
     */
    public Sm2ResponseBodyAdvice(SessionManager sessionManager, Sm2ServerConfig config) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {

        // 跳过握手端点
        String path = getRequestPath(request);
        if (path != null && (path.endsWith(config.getHandshakeInitPath())
                || path.endsWith(config.getHandshakeConfirmPath()))) {
            return body;
        }

        // 从请求属性获取会话
        Session session = getSessionFromRequest(request);
        if (session == null) {
            log.debug("请求中未找到会话属性，跳过响应加密");
            return body;
        }

        // 加密响应体
        try {
            String plainResponse;
            if (body instanceof String) {
                plainResponse = (String) body;
            } else {
                return body;
            }

            if (plainResponse.isEmpty()) {
                return body;
            }

            String encrypted = sessionManager.encryptBody(session.getSessionId(), plainResponse);
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            log.debug("响应加密成功: sessionId={}", session.getSessionId());
            return encrypted;

        } catch (Sm2SdkException e) {
            log.error("响应加密失败: sessionId={}, error={}",
                    session.getSessionId(), e.getMessage());
            throw e;
        }
    }

    /**
     * 从 ServerHttpRequest 中提取请求路径。
     */
    private String getRequestPath(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest httpRequest =
                    ((ServletServerHttpRequest) request).getServletRequest();
            return httpRequest.getRequestURI();
        }
        return request.getURI().getPath();
    }

    /**
     * 从 ServerHttpRequest 中提取会话对象。
     */
    private Session getSessionFromRequest(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest httpRequest =
                    ((ServletServerHttpRequest) request).getServletRequest();
            return (Session) httpRequest.getAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE);
        }
        return null;
    }
}
