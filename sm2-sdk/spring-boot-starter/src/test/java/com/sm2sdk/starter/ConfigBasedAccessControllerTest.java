package com.sm2sdk.starter;

import com.sm2sdk.core.model.Sm2SdkConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ConfigBasedAccessController} 的单元测试。
 */
class ConfigBasedAccessControllerTest {

    private Sm2SdkConfig sdkConfig;

    @BeforeEach
    void setUp() {
        sdkConfig = new Sm2SdkConfig();
    }

    @Test
    @DisplayName("无 clientAccessConfig → 所有客户端可访问")
    void testNoConfigAllowsAll() {
        ConfigBasedAccessController ctrl = new ConfigBasedAccessController(sdkConfig);
        assertTrue(ctrl.isAllowed("any-client", "/any/path"));
    }

    @Test
    @DisplayName("enabled=false → 所有客户端可访问")
    void testDisabledAllowsAll() {
        Sm2SdkConfig.ClientAccessConfig config = new Sm2SdkConfig.ClientAccessConfig();
        config.setEnabled(false);
        config.setDefaultPolicy("deny");
        sdkConfig.setClientAccessConfig(config);

        ConfigBasedAccessController ctrl = new ConfigBasedAccessController(sdkConfig);
        assertTrue(ctrl.isAllowed("app-a", "/api/data"));
    }

    @Test
    @DisplayName("enabled=true + 无规则 + defaultPolicy=allow → 允许")
    void testEmptyRulesAllowByDefault() {
        Sm2SdkConfig.ClientAccessConfig config = new Sm2SdkConfig.ClientAccessConfig();
        config.setEnabled(true);
        config.setDefaultPolicy("allow");
        sdkConfig.setClientAccessConfig(config);

        ConfigBasedAccessController ctrl = new ConfigBasedAccessController(sdkConfig);
        assertTrue(ctrl.isAllowed("app-a", "/api/data"));
    }

    @Test
    @DisplayName("enabled=true + 无规则 + defaultPolicy=deny → 拒绝")
    void testEmptyRulesDenyByDefault() {
        Sm2SdkConfig.ClientAccessConfig config = new Sm2SdkConfig.ClientAccessConfig();
        config.setEnabled(true);
        config.setDefaultPolicy("deny");
        sdkConfig.setClientAccessConfig(config);

        ConfigBasedAccessController ctrl = new ConfigBasedAccessController(sdkConfig);
        assertFalse(ctrl.isAllowed("app-a", "/api/data"));
    }

    @Test
    @DisplayName("clientId 精确匹配 + 路径匹配 → 允许")
    void testExactMatchAllowed() {
        Sm2SdkConfig.ClientAccessConfig config = new Sm2SdkConfig.ClientAccessConfig();
        config.setEnabled(true);
        config.setDefaultPolicy("deny");
        config.addRule(new Sm2SdkConfig.ClientAccessRule("app-a",
                Arrays.asList("/api/user/**", "/api/order/**")));
        sdkConfig.setClientAccessConfig(config);

        ConfigBasedAccessController ctrl = new ConfigBasedAccessController(sdkConfig);
        assertTrue(ctrl.isAllowed("app-a", "/api/user/123"));
        assertTrue(ctrl.isAllowed("app-a", "/api/order/create"));
    }

    @Test
    @DisplayName("clientId 精确匹配 + 路径不匹配 → 拒绝")
    void testExactMatchDenied() {
        Sm2SdkConfig.ClientAccessConfig config = new Sm2SdkConfig.ClientAccessConfig();
        config.setEnabled(true);
        config.setDefaultPolicy("deny");
        config.addRule(new Sm2SdkConfig.ClientAccessRule("app-a",
                Arrays.asList("/api/user/**")));
        sdkConfig.setClientAccessConfig(config);

        ConfigBasedAccessController ctrl = new ConfigBasedAccessController(sdkConfig);
        assertFalse(ctrl.isAllowed("app-a", "/admin/secret"));
    }

    @Test
    @DisplayName("clientId 不匹配 → fallback 到 catch-all 规则")
    void testCatchAllRuleFallback() {
        Sm2SdkConfig.ClientAccessConfig config = new Sm2SdkConfig.ClientAccessConfig();
        config.setEnabled(true);
        config.setDefaultPolicy("deny");
        config.addRule(new Sm2SdkConfig.ClientAccessRule("app-a",
                Arrays.asList("/api/private/**")));
        config.addRule(new Sm2SdkConfig.ClientAccessRule("",
                Arrays.asList("/api/public/**")));
        sdkConfig.setClientAccessConfig(config);

        ConfigBasedAccessController ctrl = new ConfigBasedAccessController(sdkConfig);

        assertTrue(ctrl.isAllowed("app-a", "/api/private/data"));
        assertFalse(ctrl.isAllowed("app-a", "/api/public/info"));
        assertTrue(ctrl.isAllowed("app-b", "/api/public/info"));
        assertFalse(ctrl.isAllowed("app-b", "/api/private/data"));
    }

    @Test
    @DisplayName("clientId 不匹配且无 catch-all → 应用 defaultPolicy")
    void testNoMatchFallsToDefault() {
        Sm2SdkConfig.ClientAccessConfig config = new Sm2SdkConfig.ClientAccessConfig();
        config.setEnabled(true);
        config.setDefaultPolicy("allow");
        config.addRule(new Sm2SdkConfig.ClientAccessRule("app-a",
                Arrays.asList("/api/a/**")));
        sdkConfig.setClientAccessConfig(config);

        ConfigBasedAccessController ctrl = new ConfigBasedAccessController(sdkConfig);
        assertTrue(ctrl.isAllowed("unknown-client", "/any/path"));
    }

    @Test
    @DisplayName("多个 clientId 规则 → 选择正确的一条")
    void testMultipleRules() {
        Sm2SdkConfig.ClientAccessConfig config = new Sm2SdkConfig.ClientAccessConfig();
        config.setEnabled(true);
        config.setDefaultPolicy("deny");
        config.addRule(new Sm2SdkConfig.ClientAccessRule("app-a",
                Arrays.asList("/api/a/**")));
        config.addRule(new Sm2SdkConfig.ClientAccessRule("app-b",
                Arrays.asList("/api/b/**")));
        config.addRule(new Sm2SdkConfig.ClientAccessRule("app-c",
                Arrays.asList("/**")));
        sdkConfig.setClientAccessConfig(config);

        ConfigBasedAccessController ctrl = new ConfigBasedAccessController(sdkConfig);
        assertTrue(ctrl.isAllowed("app-a", "/api/a/data"));
        assertFalse(ctrl.isAllowed("app-a", "/api/b/data"));
        assertTrue(ctrl.isAllowed("app-b", "/api/b/data"));
        assertTrue(ctrl.isAllowed("app-c", "/anything"));
    }

    @Test
    @DisplayName("旧 paths 字段 → resolveRules 包装为 catch-all")
    void testLegacyPaths() {
        @SuppressWarnings("deprecation")
        Sm2SdkConfig.ClientAccessConfig c = new Sm2SdkConfig.ClientAccessConfig(
                Arrays.asList("/api/legacy/**", "/api/old/**"));
        c.setEnabled(true);
        c.setDefaultPolicy("deny");
        sdkConfig.setClientAccessConfig(c);

        ConfigBasedAccessController ctrl = new ConfigBasedAccessController(sdkConfig);
        assertTrue(ctrl.isAllowed("any-client", "/api/legacy/stuff"));
        assertFalse(ctrl.isAllowed("any-client", "/api/new/stuff"));
    }
}
