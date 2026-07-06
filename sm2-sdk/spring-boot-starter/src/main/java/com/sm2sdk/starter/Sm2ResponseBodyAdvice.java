package com.sm2sdk.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm2sdk.core.annotation.Sm2Secured;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionManager;
import javax.servlet.http.HttpServletRequest;
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

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * SM2 响应体加密处理器。
 *
 * <p>在 Controller 返回后、响应写入客户端前，将返回值序列化为 JSON 并用会话密钥加密。
 * 客户端收到的是 Base64 编码的密文，需用协商的 SM4 会话密钥解密。
 */
@ControllerAdvice
public class Sm2ResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    private static final Logger log = LoggerFactory.getLogger(Sm2ResponseBodyAdvice.class);

    private final SessionManager sessionManager;
    private final Sm2ServerConfig config;
    private final ObjectMapper objectMapper;

    public Sm2ResponseBodyAdvice(SessionManager sessionManager, Sm2ServerConfig config) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        // === 安全加固：仅处理 @Sm2Secured 方法，减少不必要的响应拦截 ===
        return isSm2Secured(returnType);
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {

        String path = getRequestPath(request);
        if (path != null && (path.endsWith(config.getHandshakeInitPath())
                || path.endsWith(config.getHandshakeConfirmPath()))) {
            return body;
        }

        if (!isSm2Secured(returnType)) {
            return body;
        }

        Session session = getSessionFromRequest(request);
        if (session == null) {
            log.debug("请求中未找到会话属性，跳过响应加密");
            return body;
        }

        try {
            String plainJson;
            if (body instanceof String) {
                plainJson = (String) body;
            } else {
                plainJson = objectMapper.writeValueAsString(body);
            }

            if (plainJson.isEmpty()) {
                return body;
            }

            String encrypted = sessionManager.encryptBody(session.getSessionId(), plainJson);
            log.debug("响应加密成功: sessionId={}", session.getSessionId());

            byte[] bytes = encrypted.getBytes(StandardCharsets.UTF_8);
            response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
            response.getHeaders().setContentLength(bytes.length);

            // 优先直接写入响应流（跳过 Spring converter，防止 Jackson 二次序列化）。
            // 若 getBody() 为 null（测试 mock 场景），回退到返回 byte[]。
            if (response.getBody() != null) {
                try {
                    response.getBody().write(bytes);
                    response.getBody().flush();
                } catch (IOException ioe) {
                    throw new Sm2SdkException(ErrorCode.SM4_ENCRYPT_FAILED,
                            "写入加密响应体失败: " + ioe.getMessage(), ioe);
                }
                return null;
            }
            return bytes;

        } catch (Sm2SdkException e) {
            log.error("响应加密失败: sessionId={}, error={}",
                    session.getSessionId(), e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("响应序列化或加密失败: error={}", e.getMessage());
            throw new Sm2SdkException(ErrorCode.SM4_ENCRYPT_FAILED,
                    "响应加密失败: " + e.getMessage(), e);
        }
    }

    private String getRequestPath(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest httpRequest =
                    ((ServletServerHttpRequest) request).getServletRequest();
            return httpRequest.getRequestURI();
        }
        return request.getURI().getPath();
    }

    private Session getSessionFromRequest(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest) {
            HttpServletRequest httpRequest =
                    ((ServletServerHttpRequest) request).getServletRequest();
            return (Session) httpRequest.getAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE);
        }
        return null;
    }

    private boolean isSm2Secured(MethodParameter returnType) {
        Method method = returnType.getMethod();
        if (method != null && method.isAnnotationPresent(Sm2Secured.class)) {
            return true;
        }
        // 检查类级别注解，向上遍历父类（兼容 CGLIB 代理等场景）
        for (Class<?> clazz = returnType.getContainingClass();
             clazz != null && clazz != Object.class;
             clazz = clazz.getSuperclass()) {
            if (clazz.isAnnotationPresent(Sm2Secured.class)) {
                return true;
            }
        }
        return false;
    }
}
