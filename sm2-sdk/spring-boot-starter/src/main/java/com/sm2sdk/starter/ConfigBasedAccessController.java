package com.sm2sdk.starter;

import com.sm2sdk.core.access.Sm2AccessController;
import com.sm2sdk.core.model.Sm2SdkConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import java.util.List;
import java.util.Objects;

/**
 * 基于 {@link Sm2SdkConfig.ClientAccessConfig} 的默认访问控制器。
 *
 * <p>使用 Spring 的 {@link AntPathMatcher} 进行路径模式匹配。
 * 规则处理顺序：
 * <ol>
 *   <li>精确匹配 clientId 的规则</li>
 *   <li>clientId 为空（catch-all）的规则</li>
 *   <li>以上都无匹配 → {@code defaultPolicy}</li>
 * </ol>
 */
public class ConfigBasedAccessController implements Sm2AccessController {

    private static final Logger log = LoggerFactory.getLogger(ConfigBasedAccessController.class);

    private final Sm2SdkConfig sdkConfig;
    private final PathMatcher pathMatcher;

    public ConfigBasedAccessController(Sm2SdkConfig sdkConfig) {
        this(sdkConfig, new AntPathMatcher());
    }

    /** 包级可见，允许测试注入自定义 PathMatcher。 */
    ConfigBasedAccessController(Sm2SdkConfig sdkConfig, PathMatcher pathMatcher) {
        this.sdkConfig = Objects.requireNonNull(sdkConfig, "sdkConfig");
        this.pathMatcher = Objects.requireNonNull(pathMatcher, "pathMatcher");
    }

    @Override
    public boolean isAllowed(String clientId, String path) {
        Sm2SdkConfig.ClientAccessConfig accessConfig = sdkConfig.getClientAccessConfig();

        // 未配置或未启用 → 全部放行（向后兼容）
        if (accessConfig == null || !accessConfig.isEnabled()) {
            return true;
        }

        List<Sm2SdkConfig.ClientAccessRule> rules = accessConfig.resolveRules();
        if (rules.isEmpty()) {
            return "allow".equalsIgnoreCase(accessConfig.getDefaultPolicy());
        }

        // 1. 精确匹配 clientId
        Sm2SdkConfig.ClientAccessRule matchedRule = null;
        Sm2SdkConfig.ClientAccessRule catchAllRule = null;

        for (Sm2SdkConfig.ClientAccessRule rule : rules) {
            String ruleClientId = rule.getClientId();
            if (ruleClientId != null && ruleClientId.equals(clientId)) {
                matchedRule = rule;
                break;
            }
            if (ruleClientId == null || ruleClientId.isEmpty()) {
                catchAllRule = rule;
            }
        }

        // 2. Fallback 到 catch-all
        if (matchedRule == null) {
            matchedRule = catchAllRule;
        }

        // 3. 无任何匹配 → default policy
        if (matchedRule == null) {
            boolean allowed = "allow".equalsIgnoreCase(accessConfig.getDefaultPolicy());
            log.debug("No matching rule for clientId={} path={}; defaultPolicy={} → {}",
                    clientId, path, accessConfig.getDefaultPolicy(), allowed ? "allow" : "deny");
            return allowed;
        }

        // 4. 检查路径是否匹配
        for (String pattern : matchedRule.getPaths()) {
            if (pathMatcher.match(pattern, path)) {
                log.debug("Access allowed: clientId={} path={} matched pattern={}",
                        clientId, path, pattern);
                return true;
            }
        }

        log.warn("Access denied: clientId={} path={} not in allowed patterns",
                clientId, path);
        return false;
    }
}
