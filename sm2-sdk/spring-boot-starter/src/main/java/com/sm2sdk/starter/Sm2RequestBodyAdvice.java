package com.sm2sdk.starter;

import com.sm2sdk.core.annotation.Sm2Secured;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;

import javax.servlet.http.HttpServletRequest;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;

/**
 * SM2 请求体解密处理器。
 *
 * <p>在 Controller 的 {@code @RequestBody} 反序列化之前，使用会话密钥解密请求体。
 * 与 {@link Sm2ServerInterceptor} 配合：
 *
 * <ol>
 *   <li>Interceptor 验证会话 → 存入 request 属性</li>
 *   <li>本处理器解密 body → 返回明文给 Spring 反序列化</li>
 *   <li>Controller 拿到明文对象</li>
 *   <li>{@link Sm2ResponseBodyAdvice} 加密响应</li>
 * </ol>
 */
@ControllerAdvice
public class Sm2RequestBodyAdvice implements RequestBodyAdvice {

    private static final Logger log = LoggerFactory.getLogger(Sm2RequestBodyAdvice.class);

    private final SessionManager sessionManager;

    public Sm2RequestBodyAdvice(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        Method method = methodParameter.getMethod();
        if (method != null && method.isAnnotationPresent(Sm2Secured.class)) {
            return true;
        }
        Class<?> containingClass = methodParameter.getContainingClass();
        return containingClass != null && containingClass.isAnnotationPresent(Sm2Secured.class);
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage,
                                           MethodParameter parameter, Type targetType,
                                           Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {

        // 读取加密的请求体
        byte[] encryptedBytes = readAllBytes(inputMessage.getBody());
        if (encryptedBytes.length == 0) {
            return inputMessage;
        }
        String encryptedBody = new String(encryptedBytes, StandardCharsets.UTF_8);

        // 获取会话
        Session session = getSession();
        if (session == null) {
            throw new Sm2SdkException(ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED, "未找到会话");
        }

        // 解密
        try {
            String plainBody = sessionManager.decryptBody(session.getSessionId(), encryptedBody);
            byte[] plainBytes = plainBody.getBytes(StandardCharsets.UTF_8);
            log.debug("请求体解密成功: sessionId={}", session.getSessionId());

            return new HttpInputMessage() {
                @Override
                public InputStream getBody() {
                    return new ByteArrayInputStream(plainBytes);
                }

                @Override
                public HttpHeaders getHeaders() {
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(
                            org.springframework.http.MediaType.APPLICATION_JSON);
                    return headers;
                }
            };
        } catch (Sm2SdkException e) {
            log.error("请求体解密失败: sessionId={}, error={}",
                    session.getSessionId(), e.getMessage());
            throw e;
        }
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage,
                                MethodParameter parameter, Type targetType,
                                Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage,
                                  MethodParameter parameter, Type targetType,
                                  Class<? extends HttpMessageConverter<?>> converterType) {
        return body;
    }

    private Session getSession() {
        try {
            HttpServletRequest request =
                    ((org.springframework.web.context.request.ServletRequestAttributes)
                            org.springframework.web.context.request.RequestContextHolder
                                    .currentRequestAttributes()).getRequest();
            return (Session) request.getAttribute(Sm2ServerInterceptor.SESSION_ATTRIBUTE);
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
