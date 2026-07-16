package io.github.mtooo.client;

import io.github.mtooo.core.model.Sm2SdkConfig;
import io.github.mtooo.core.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * SM2 安全 HTTP 客户端门面，提供对端加密通信的入口。
 *
 * <p>使用示例：
 * <pre>{@code
 * Sm2SdkConfig config = Sm2SdkConfig.builder()
 *     .serverUrl("https://api.example.com")
 *     .sm2PrivateKey("...")
 *     .build();
 * SessionManager sessionManager = new SessionManager(keyExchange, sm4Crypto, sessionStore, config);
 * Sm2HttpClient client = new Sm2HttpClient(config, sessionManager, "peer1");
 *
 * // GET 请求
 * String data = client.get("/api/data").param("id", "123").execute(String.class);
 *
 * // POST 请求
 * MyResponse resp = client.post("/api/create").body(myObj).execute(MyResponse.class);
 * }</pre>
 *
 * <p>每个方法返回一个 {@link Sm2Request} 实例，支持链式调用设置参数、请求头和请求体，
 * 最终通过 {@link Sm2Request#execute(Class)} 执行并获取结果。
 */
public class Sm2HttpClient {

    private static final Logger log = LoggerFactory.getLogger(Sm2HttpClient.class);

    /** 客户端配置 */
    private final Sm2ClientConfig config;

    /** 会话管理器 */
    private final SessionManager sessionManager;

    /** 当前会话 ID（所有请求共享同一会话） */
    private volatile String currentSessionId;

    /**
     * 创建 Sm2HttpClient 实例。
     *
     * @param config         SDK 全局配置
     * @param sessionManager 会话管理器
     * @param peerId         对端标识
     */
    public Sm2HttpClient(Sm2SdkConfig config, SessionManager sessionManager, String peerId) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        Objects.requireNonNull(peerId, "peerId must not be null");
        this.config = new Sm2ClientConfig(config, peerId);
        this.sessionManager = sessionManager;
    }

    /**
     * 创建 Sm2HttpClient 实例（使用预构建的客户端配置）。
     *
     * @param config         客户端配置
     * @param sessionManager 会话管理器
     */
    public Sm2HttpClient(Sm2ClientConfig config, SessionManager sessionManager) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.config = config;
        this.sessionManager = sessionManager;
    }

    /**
     * 创建 GET 请求。
     *
     * @param path 请求路径（如 "/api/data"）
     * @return Sm2Request 实例
     */
    public Sm2Request get(String path) {
        return createRequest("GET", path);
    }

    /**
     * 创建 POST 请求。
     *
     * @param path 请求路径
     * @return Sm2Request 实例
     */
    public Sm2Request post(String path) {
        return createRequest("POST", path);
    }

    /**
     * 创建 PUT 请求。
     *
     * @param path 请求路径
     * @return Sm2Request 实例
     */
    public Sm2Request put(String path) {
        return createRequest("PUT", path);
    }

    /**
     * 创建 DELETE 请求。
     *
     * @param path 请求路径
     * @return Sm2Request 实例
     */
    public Sm2Request delete(String path) {
        return createRequest("DELETE", path);
    }

    /**
     * 创建 Sm2Request 实例，预配置当前会话 ID。
     *
     * @param method HTTP 方法
     * @param path   请求路径
     * @return Sm2Request 实例
     */
    private Sm2Request createRequest(String method, String path) {
        Sm2Request request = new Sm2Request(
                method, path, config, sessionManager,
                currentSessionId);
        return request;
    }

    /**
     * 获取客户端配置。
     *
     * @return 客户端配置
     */
    public Sm2ClientConfig getConfig() {
        return config;
    }

    /**
     * 获取当前会话 ID。
     *
     * @return 当前会话 ID（可能为 null）
     */
    public String getCurrentSessionId() {
        return currentSessionId;
    }

    /**
     * 设置当前会话 ID。
     *
     * @param sessionId 会话 ID
     */
    public void setCurrentSessionId(String sessionId) {
        this.currentSessionId = sessionId;
    }
}
