package com.sm2sdk.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.Map;

public class Sm2QueryDecryptFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(Sm2QueryDecryptFilter.class);

    private final SessionManager sessionManager;
    private final ObjectMapper objectMapper;

    public Sm2QueryDecryptFilter(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) {
        try {
            HttpServletRequest request = (HttpServletRequest) req;
            HttpServletResponse response = (HttpServletResponse) res;

            String encryptedQuery = request.getHeader("X-Sm2-Query");
            if (encryptedQuery == null || encryptedQuery.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }

            String sessionId = request.getHeader("X-Session-Id");
            if (sessionId == null || sessionId.isEmpty()) {
                chain.doFilter(request, response);
                return;
            }

            Session session = sessionManager.getSession(sessionId);
            if (session == null) {
                chain.doFilter(request, response);
                return;
            }

            String plainJson = sessionManager.decryptBody(sessionId, encryptedQuery);
            Map<String, Object> paramsMap = objectMapper.readValue(plainJson, Map.class);
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
            try { chain.doFilter(req, res); } catch (Exception ignored) {}
        }
    }
}
