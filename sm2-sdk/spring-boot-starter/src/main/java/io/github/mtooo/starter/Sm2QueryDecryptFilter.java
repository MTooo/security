package io.github.mtooo.starter;

import cn.hutool.json.JSONUtil;
import io.github.mtooo.core.session.Session;
import io.github.mtooo.core.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 解密 GET/DELETE 请求的加密查询参数。
 *
 * <p>当请求带有 {@code X-Sm2-Query} header 时，用会话密钥解密其中的参数 JSON，
 * 并将解密后的参数注入到 request 的 parameter map 中，使 {@code @RequestParam} 可以透明读取。
 *
 * <p>此 Filter 运行在 Spring Interceptor 之前，负责请求包装，Interceptor 负责会话验证。
 */
public class Sm2QueryDecryptFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(Sm2QueryDecryptFilter.class);
    private static final String HEADER_SM2_QUERY = "X-Sm2-Query";

    private final SessionManager sessionManager;

    public Sm2QueryDecryptFilter(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws java.io.IOException, javax.servlet.ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String encryptedQuery = request.getHeader(HEADER_SM2_QUERY);
        if (encryptedQuery == null || encryptedQuery.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }

        String sessionId = request.getHeader("X-Session-Id");
        if (sessionId == null || sessionId.isEmpty()) {
            log.debug("X-Sm2-Query 存在但无 X-Session-Id，跳过参数解密");
            chain.doFilter(request, response);
            return;
        }

        try {
            Session session = sessionManager.getSession(sessionId);
            if (session == null) {
                log.debug("会话不存在，跳过参数解密");
                chain.doFilter(request, response);
                return;
            }

            String plainJson = sessionManager.decryptBody(sessionId, encryptedQuery);
            @SuppressWarnings("unchecked")
            Map<String, Object> paramsMap = JSONUtil.parseObj(plainJson);
            Map<String, String> params = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : paramsMap.entrySet()) {
                params.put(e.getKey(), e.getValue() != null ? e.getValue().toString() : "");
            }

            Sm2DecryptedParamsRequestWrapper wrappedRequest =
                    new Sm2DecryptedParamsRequestWrapper(request, params);
            log.debug("GET/DELETE 参数解密成功: sessionId={}, params={}", sessionId, params.keySet());
            chain.doFilter(wrappedRequest, response);

        } catch (Exception e) {
            log.error("解密 GET 查询参数失败: {}", e.getMessage());
            chain.doFilter(request, response);
        }
    }
}
