package io.github.mtooo.client;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import cn.hutool.http.Method;
import cn.hutool.json.JSONUtil;
import io.github.mtooo.core.crypto.MemoryCleanUtil;
import io.github.mtooo.core.crypto.Sm2KeyExchange;
import io.github.mtooo.core.exception.ErrorCode;
import io.github.mtooo.core.exception.Sm2SdkException;
import io.github.mtooo.core.model.HandshakeConfirm;
import io.github.mtooo.core.model.HandshakeInit;
import io.github.mtooo.core.model.HandshakeServerResp;
import io.github.mtooo.core.session.Session;
import io.github.mtooo.core.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * SM2 安全请求构建器，提供链式 API 用于构建和执行加密的 HTTP 请求。
 *
 * <p>使用示例：
 * <pre>{@code
 * Sm2HttpClient client = new Sm2HttpClient(config, sessionManager);
 *
 * // GET 请求
 * String result = client.get("/api/data")
 *     .param("id", "123")
 *     .execute(String.class);
 *
 * // POST 请求（自动注入 _idempotencyKey）
 * MyResponse resp = client.post("/api/create")
 *     .body(myRequest)
 *     .execute(MyResponse.class);
 * }</pre>
 *
 * <p>execute() 内部自动处理：
 * <ul>
 *   <li>获取或创建 SM2 会话（含三步握手）</li>
 *   <li>会话密钥续期检查</li>
 *   <li>请求体 SM4 加密</li>
 *   <li>响应体 SM4 解密</li>
 *   <li>401 会话过期自动重握手</li>
 *   <li>400 错误码 21202 自动废弃会话并重握手</li>
 * </ul>
 */
public class Sm2Request {

    private static final Logger log = LoggerFactory.getLogger(Sm2Request.class);

    /** HTTP 方法 */
    private final String httpMethod;

    /** 请求路径 */
    private final String path;

    /** 客户端配置 */
    private final Sm2ClientConfig config;

    /** 会话管理器 */
    private final SessionManager sessionManager;

    /** 查询参数 */
    private final Map<String, Object> params;

    /** 自定义 HTTP 头 */
    private final Map<String, Object> customHeaders;

    /** 请求体对象（POST/PUT） */
    private Object requestBody;

    /** 幂等键（一次 execute() 生命周期内不变，跨重试复用） */
    private String idempotencyKey;

    /** 当前会话 ID（自动维护） */
    private String currentSessionId;

    /** 重试次数上限（用于递归重试保护） */
    private static final int MAX_RETRY_COUNT = 1;

    /**
     * 创建 Sm2Request 实例（包级私有，由 {@link Sm2HttpClient} 创建）。
     *
     * @param httpMethod     HTTP 方法
     * @param path           请求路径
     * @param config         客户端配置
     * @param sessionManager 会话管理器
     * @param currentSessionId 当前会话 ID（可为 null）
     */
    Sm2Request(String httpMethod, String path, Sm2ClientConfig config,
               SessionManager sessionManager,
               String currentSessionId) {
        this.httpMethod = Objects.requireNonNull(httpMethod, "httpMethod must not be null");
        this.path = Objects.requireNonNull(path, "path must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.currentSessionId = currentSessionId;
        this.params = new LinkedHashMap<>();
        this.customHeaders = new LinkedHashMap<>();
    }

    // ==================== 链式方法 ====================

    /**
     * 添加查询参数（适用于 GET 和 DELETE 请求）。
     *
     * @param key   参数名
     * @param value 参数值，支持任意类型（Number、Boolean、String 等）
     * @return 当前请求实例（链式调用）
     */
    public Sm2Request param(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        this.params.put(key, value);
        return this;
    }

    /**
     * 添加自定义 HTTP 头。
     *
     * @param key   头名称
     * @param value 头值，支持任意类型，最终以 {@code toString()} 写入 HTTP 头
     * @return 当前请求实例（链式调用）
     */
    public Sm2Request header(String key, Object value) {
        Objects.requireNonNull(key, "key must not be null");
        this.customHeaders.put(key, value);
        return this;
    }

    /**
     * 设置请求体对象（适用于 POST 和 PUT 请求），
     * 执行时会被序列化为 JSON 字符串。
     *
     * @param body 请求体对象
     * @return 当前请求实例（链式调用）
     */
    public Sm2Request body(Object body) {
        this.requestBody = body;
        return this;
    }

    /**
     * 执行请求并返回反序列化后的响应结果。
     *
     * <p>内部流程：
     * <ol>
     *   <li>获取或创建会话（必要时自动执行三步握手）</li>
     *   <li>检查会话是否需要续期</li>
     *   <li>构建明文 JSON（GET/DELETE 使用参数，POST/PUT 使用请求体）</li>
     *   <li>使用会话 SM4 密钥加密明文 JSON</li>
     *   <li>发送加密的 HTTP 请求</li>
     *   <li>处理响应（正常返回、会话过期重试、解密失败重试）</li>
     *   <li>解密响应体并反序列化为指定类型</li>
     * </ol>
     *
     * @param responseType 响应类型
     * @param <T>          响应泛型
     * @return 反序列化后的响应对象
     * @throws Sm2SdkException 如果请求失败或加解密失败
     */
    public <T> T execute(Class<T> responseType) {
        Objects.requireNonNull(responseType, "responseType must not be null");
        return executeWithRetry(responseType, 0);
    }

    /**
     * 带重试计数的内部执行方法。
     *
     * @param responseType 响应类型
     * @param retryCount   当前重试次数
     * @param <T>          响应泛型
     * @return 反序列化后的响应对象
     */
    private <T> T executeWithRetry(Class<T> responseType, int retryCount) {
        // 步骤 1: 获取或创建会话
        Session session = getOrCreateSession();
        String sessionId = session.getSessionId();
        session.touch();

        // 步骤 2: 检查是否需要续期
        session = renewIfNeeded(session);

        // 步骤 3: 构建明文 JSON
        String plainJson = buildPlainJson();

        // 步骤 4: 使用会话密钥加密
        String ciphertext = null;
        if (!plainJson.isEmpty()) {
            try {
                ciphertext = sessionManager.encryptBody(sessionId, plainJson);
            } catch (Exception e) {
                throw new Sm2SdkException(ErrorCode.SM4_ENCRYPT_FAILED,
                        "请求体加密失败: " + e.getMessage(), e);
            }
        }

        // 步骤 5: 构建 HTTP 请求并发送
        String url = config.getServerUrl() + path;
        Map<String, String> headers = buildHeaders(sessionId);
        boolean isGetOrDelete = "GET".equalsIgnoreCase(httpMethod)
                || "DELETE".equalsIgnoreCase(httpMethod);
        // GET/DELETE: 加密参数通过 X-Sm2-Query header 传递，不放在 body 里
        if (isGetOrDelete && ciphertext != null) {
            headers.put("X-Sm2-Query", ciphertext);
            ciphertext = null;
        }
        final String bodyToSend = ciphertext;
        HttpResponse httpResponse;
        try {
            httpResponse = sendHttpRequest(httpMethod, url, headers, bodyToSend);
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED,
                    "HTTP 请求失败: " + e.getMessage(), e);
        }

        // 步骤 6: 检查响应状态并处理
        int status = httpResponse.getStatus();
        String responseBodyStr = httpResponse.body();

        // 200: 正常响应，解密并反序列化
        if (status == 200) {
            return handleSuccessResponse(sessionId, responseBodyStr, responseType);
        }

        // 401 + X-Session-Expired: 会话过期，重握手后重试
        if (status == 401 && "true".equalsIgnoreCase(httpResponse.header("X-Session-Expired"))) {
            if (retryCount < MAX_RETRY_COUNT) {
                log.warn("会话已过期（HTTP 401），执行重握手后重试");
                currentSessionId = null;
                return executeWithRetry(responseType, retryCount + 1);
            }
            throw new Sm2SdkException(ErrorCode.SESSION_EXPIRED,
                    "会话已过期，重试后仍然失败");
        }

        // 400 + 错误码 21202: SM4 解密失败，废弃会话后重试
        if (status == 400 && responseBodyStr != null && responseBodyStr.contains("21202")) {
            log.warn("服务端返回 SM4 解密失败（错误码 21202），废弃当前会话后重试");
            try {
                sessionManager.destroySession(sessionId);
            } catch (Exception ignored) {
                // 清除会话失败不影响重试
            }
            currentSessionId = null;
            if (retryCount < MAX_RETRY_COUNT) {
                return executeWithRetry(responseType, retryCount + 1);
            }
            throw new Sm2SdkException(ErrorCode.SM4_DECRYPT_TAG_FAILED,
                    "SM4 解密失败，重试后仍然失败");
        }

        // 其他错误状态
        throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED,
                "HTTP 请求失败: 状态码=" + status + ", 响应体=" + responseBodyStr);
    }

    // ==================== 内部方法 ====================

    /**
     * 获取当前会话，如果不存在则自动执行三步握手创建会话。
     *
     * @return 有效会话
     */
    private Session getOrCreateSession() {
        if (currentSessionId != null) {
            try {
                return sessionManager.getSession(currentSessionId);
            } catch (Sm2SdkException e) {
                // 会话已过期或不存在，清除后执行握手
                log.debug("当前会话无效（{}），执行握手", currentSessionId);
                currentSessionId = null;
            }
        }
        return doHandshake();
    }

    /**
     * 检查会话是否需要续期。如果剩余生命周期低于阈值则执行续期。
     *
     * @param session 当前会话
     * @return 续期后或原会话
     */
    private Session renewIfNeeded(Session session) {
        try {
            return sessionManager.renewSession(session.getSessionId());
        } catch (Sm2SdkException e) {
            // 续期失败不影响主流程，继续使用原会话
            log.warn("会话续期失败，继续使用原会话: {}", e.getMessage());
            return session;
        }
    }

    /**
     * 执行三步 SM2 握手。
     *
     * <p>流程：
     * <ol>
     *   <li>使用密钥交换构建 HandshakeInit 并发送到服务端</li>
     *   <li>接收 HandshakeServerResp，交由 SessionManager 处理并创建会话</li>
     *   <li>构建 HandshakeConfirm 发送到服务端完成握手</li>
     * </ol>
     *
     * @return 握手完成后创建的会话
     */
    // 包级可见性：允许单元测试通过 spy 重写此方法
    Session doHandshake() {
        Sm2KeyExchange keyExchange = sessionManager.getKeyExchange();
        String peerId = config.getPeerId();
        String serverUrl = config.getServerUrl();

        byte[] clientPrivKey = SessionManager.hexToBytes(config.getSm2PrivateKey());
        byte[] serverPubKey = SessionManager.hexToBytes(config.getPeerPublicKey());

        try {
            // 步骤 1: 构建握手初始化请求
            HandshakeInit init = keyExchange.buildInitRequest(
                    peerId, clientPrivKey, serverPubKey, peerId);
            String initJson = JSONUtil.toJsonStr(init);

            // 发送 HandshakeInit 到服务端
            String initRespBody = sendHandshakeRequest(
                    serverUrl + "/handshake/init", initJson);
            HandshakeServerResp serverResp = JSONUtil.toBean(
                    initRespBody, HandshakeServerResp.class);

            // 步骤 2: 处理服务端响应，派生共享密钥
            Sm2KeyExchange.HandshakeResult result = keyExchange.processServerResponse(
                    init, serverResp, clientPrivKey, serverPubKey,
                    peerId, config.getServerId());

            // 步骤 3: 构建确认消息并发送
            HandshakeConfirm confirm = keyExchange.buildConfirm(result);
            String confirmJson = JSONUtil.toJsonStr(confirm);
            sendHandshakeRequest(serverUrl + "/handshake/confirm", confirmJson);

            // 步骤 4: 创建会话
            Session session = sessionManager.createSession(peerId, result);
            this.currentSessionId = session.getSessionId();
            log.info("SM2 握手成功，会话 ID: {}", session.getSessionId());
            return session;

        } catch (Sm2SdkException e) {
            throw e;
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.HANDSHAKE_TIMEOUT,
                    "握手失败: " + e.getMessage(), e);
        } finally {
            MemoryCleanUtil.cleanKeys(clientPrivKey, serverPubKey);
        }
    }

    /**
     * 发送握手 HTTP 请求（未加密的明文 JSON）。
     *
     * @param url  请求 URL
     * @param json 明文 JSON 请求体
     * @return 响应体字符串
     */
    private String sendHandshakeRequest(String url, String json) {
        HttpResponse response = HttpUtil.createRequest(Method.POST, url)
                .header("Content-Type", "application/json")
                .body(json)
                .timeout(10000)
                .execute();
        if (response.getStatus() != 200) {
            String errorDetail = extractErrorDetail(response.body());
            throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED,
                    "握手请求失败: HTTP " + response.getStatus()
                            + ", url=" + url + ", " + errorDetail);
        }
        return response.body();
    }

    /**
     * 从错误响应体中提取可读的错误信息。
     * 如果响应是 SDK 返回的 JSON 格式，则解析错误码和消息；否则截取纯文本。
     */
    private String extractErrorDetail(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return "无响应体";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> errorMap = JSONUtil.toBean(responseBody, Map.class);
            if (errorMap.containsKey("code") && errorMap.containsKey("message")) {
                return String.format("errorCode=%s, message=%s, detail=%s",
                        errorMap.get("code"),
                        errorMap.get("message"),
                        errorMap.getOrDefault("detail", ""));
            }
        } catch (Exception ignored) {
            // 不是 JSON，使用原始文本
        }
        // 截断过长的 HTML 等非 JSON 响应
        if (responseBody.length() > 500) {
            return responseBody.substring(0, 500) + "...";
        }
        return responseBody;
    }

    /**
     * 发送加密的 HTTP 请求。
     *
     * @param method  HTTP 方法
     * @param url     URL
     * @param headers 请求头
     * @param body    加密后的请求体（Base64 密文）
     * @return HTTP 响应
     */
    private HttpResponse sendHttpRequest(String method, String url,
                                          Map<String, String> headers, String body) {
        HttpRequest request = HttpUtil.createRequest(Method.valueOf(method), url);
        // 设置请求头
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            request.header(entry.getKey(), entry.getValue());
        }
        // 设置请求体
        if (body != null && !body.isEmpty()) {
            request.body(body);
        }
        // 执行请求
        return request.timeout(30000).execute();
    }

    /**
     * 构建 HTTP 请求头。
     *
     * <p>包含：X-Session-Id、X-Timestamp、X-Nonce、Content-Type
     * 以及用户自定义头。
     *
     * @param sessionId 会话 ID
     * @return 请求头 Map
     */
    private Map<String, String> buildHeaders(String sessionId) {
        Map<String, String> headers = new HashMap<>();
        for (Map.Entry<String, Object> entry : customHeaders.entrySet()) {
            Object value = entry.getValue();
            headers.put(entry.getKey(), value != null ? value.toString() : "");
        }
        headers.put("X-Session-Id", sessionId);
        headers.put("X-Timestamp", String.valueOf(System.currentTimeMillis()));
        headers.put("X-Nonce", UUID.randomUUID().toString());
        headers.put("Content-Type", "text/plain");
        return headers;
    }

    /**
     * 根据 HTTP 方法构建明文 JSON。
     *
     * <ul>
     *   <li>GET/DELETE: params → JSON</li>
     *   <li>POST/PUT: body → JSON（POST 自动注入 _idempotencyKey）</li>
     * </ul>
     *
     * @return 明文 JSON 字符串
     */
    private String buildPlainJson() {
        try {
            switch (httpMethod.toUpperCase()) {
                case "GET":
                case "DELETE":
                    return params.isEmpty() ? "" : JSONUtil.toJsonStr(params);
                case "POST":
                case "PUT":
                    Object bodyObj = prepareBodyWithIdempotencyKey();
                    return bodyObj != null
                            ? JSONUtil.toJsonStr(bodyObj)
                            : "";
                default:
                    return "";
            }
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.CLIENT_INIT_FAILED,
                    "JSON 序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 准备请求体，POST 请求自动注入 _idempotencyKey。
     *
     * @return 请求体对象（可能被修改以注入幂等键）
     */
    private Object prepareBodyWithIdempotencyKey() {
        if (requestBody == null) {
            // POST 但无 body，创建一个仅含 _idempotencyKey 的 Map
            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("_idempotencyKey", getIdempotencyKey());
            return bodyMap;
        }

        // 如果 body 是 Map 类型，注入 _idempotencyKey
        if (requestBody instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> bodyMap = new LinkedHashMap<>((Map<String, Object>) requestBody);
            if (!bodyMap.containsKey("_idempotencyKey")) {
                bodyMap.put("_idempotencyKey", getIdempotencyKey());
            }
            return bodyMap;
        }

        // body 是 POJO 对象，通过 JSON 转换为 Map 后再注入
        try {
            Map<String, Object> bodyMap = JSONUtil.parseObj(
                    JSONUtil.toJsonStr(requestBody));
            if (!bodyMap.containsKey("_idempotencyKey")) {
                bodyMap.put("_idempotencyKey", getIdempotencyKey());
            }
            return bodyMap;
        } catch (Exception e) {
            // 非 Map 类型且不能转 Map，直接原样返回（_idempotencyKey 由调用方决定）
            return requestBody;
        }
    }

    /**
     * 获取或生成幂等键，一次 {@link #execute(Class)} 生命周期内只生成一次。
     */
    private String getIdempotencyKey() {
        if (idempotencyKey == null) {
            idempotencyKey = UUID.randomUUID().toString();
        }
        return idempotencyKey;
    }

    /**
     * 手动设置幂等键（可选，覆盖自动生成）。
     *
     * @param idempotencyKey 幂等键
     * @return 当前请求实例（链式调用）
     */
    public Sm2Request idempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
        return this;
    }

    /**
     * 处理成功响应：解密并反序列化。
     *
     * @param sessionId    会话 ID
     * @param encryptedBody 加密的响应体（Base64）
     * @param responseType 响应类型
     * @param <T>          响应泛型
     * @return 反序列化后的响应对象
     */
    private <T> T handleSuccessResponse(String sessionId, String encryptedBody,
                                         Class<T> responseType) {
        if (encryptedBody == null || encryptedBody.isEmpty()) {
            // 空响应体
            if (responseType == Void.class || responseType == void.class) {
                return null;
            }
            throw new Sm2SdkException(ErrorCode.HTTP_REQUEST_FAILED,
                    "响应体为空");
        }

        // 解密
        String plainResponse;
        try {
            plainResponse = sessionManager.decryptBody(sessionId, encryptedBody);
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.SM4_DECRYPT_TAG_FAILED,
                    "响应体解密失败: " + e.getMessage(), e);
        }

        // 反序列化
        if (responseType == String.class) {
            @SuppressWarnings("unchecked")
            T result = (T) plainResponse;
            return result;
        }
        try {
            return JSONUtil.toBean(plainResponse, responseType);
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.CLIENT_INIT_FAILED,
                    "响应 JSON 反序列化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 设置当前会话 ID（由 Sm2HttpClient 在握手后设置）。
     *
     * @param sessionId 会话 ID
     */
    void setCurrentSessionId(String sessionId) {
        this.currentSessionId = sessionId;
    }

    /**
     * 获取当前会话 ID。
     *
     * @return 会话 ID（可能为 null）
     */
    String getCurrentSessionId() {
        return currentSessionId;
    }
}
