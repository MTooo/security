package io.github.mtooo.starter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 包装 HttpServletRequest，将解密后的 GET/DELETE 参数注入到请求的 parameter map 中。
 * 这样 {@code @RequestParam} 可以透明地读取到解密后的参数值。
 */
class Sm2DecryptedParamsRequestWrapper extends HttpServletRequestWrapper {

    private final Map<String, String[]> decryptedParams;

    Sm2DecryptedParamsRequestWrapper(HttpServletRequest request, Map<String, String> params) {
        super(request);
        this.decryptedParams = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            this.decryptedParams.put(entry.getKey(), new String[]{entry.getValue()});
        }
    }

    @Override
    public String getParameter(String name) {
        String[] values = decryptedParams.get(name);
        if (values != null && values.length > 0) {
            return values[0];
        }
        // 回退到原始 URL 参数（兼容未加密场景）
        return super.getParameter(name);
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        Map<String, String[]> merged = new LinkedHashMap<>(super.getParameterMap());
        merged.putAll(decryptedParams);
        return Collections.unmodifiableMap(merged);
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(getParameterMap().keySet());
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = decryptedParams.get(name);
        if (values != null) {
            return values;
        }
        return super.getParameterValues(name);
    }
}
