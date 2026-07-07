package com.sm2sdk.starter;

import com.sm2sdk.core.crypto.MemoryCleanUtil;
import com.sm2sdk.core.crypto.Sm2KeyExchange;
import com.sm2sdk.core.crypto.impl.HutoolSm2KeyExchange;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.model.HandshakeConfirm;
import com.sm2sdk.core.model.HandshakeInit;
import com.sm2sdk.core.model.HandshakeServerResp;
import com.sm2sdk.core.session.Session;
import com.sm2sdk.core.session.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 服务端握手控制器，处理客户端发起的 SM2 密钥交换握手请求。
 *
 * <p>自动注册两个端点：
 * <ul>
 *   <li>{@code POST /handshake/init} — 处理客户端握手初始化请求</li>
 *   <li>{@code POST /handshake/confirm} — 验证客户端握手确认请求</li>
 * </ul>
 *
 * <h3>安全防护</h3>
 * <ul>
 *   <li>对端白名单：仅 peers 中配置的 clientId 允许握手，不在白名单的直接拒绝（零 CPU 消耗）</li>
 *   <li>速率限制：按 clientId 独立限流（默认 50/秒/客户端），一客户端超限不影响其他</li>
 *   <li>时间戳校验：握手请求时间戳必须在有效窗口内（默认 30 秒），防止重放</li>
 *   <li>握手确认验证：服务端存储 HandshakeResult 并完整验证 SA 值</li>
 *   <li>密钥材料安全：HandshakeResult 在确认验证后立即清零并移除</li>
 * </ul>
 */
@RestController
public class Sm2HandshakeController {

    private static final Logger log = LoggerFactory.getLogger(Sm2HandshakeController.class);

    private final SessionManager sessionManager;
    private final Sm2ServerConfig config;

    /** 临时存储 HandshakeResult，用于 confirm 时的 SM2 验证。Key: sessionId, Value: result。 */
    private final ConcurrentHashMap<String, Sm2KeyExchange.HandshakeResult> pendingResults
            = new ConcurrentHashMap<>();

    /**
     * 按 clientId 独立限流。Key: clientId（即 peers 中配置的 server-id），
     * Value: 该客户端的速率窗口。一个客户端超限不影响其他客户端。
     */
    private final ConcurrentHashMap<String, ClientRateWindow> clientRateWindows
            = new ConcurrentHashMap<>();

    /**
     * 单客户端速率窗口。记录当前秒的时间戳和该秒内的请求计数。
     */
    private static class ClientRateWindow {
        volatile long second;
        final AtomicInteger count = new AtomicInteger(0);

        ClientRateWindow(long second) {
            this.second = second;
        }
    }

    /**
     * 创建握手控制器。
     *
     * @param sessionManager 会话管理器
     * @param config         服务端配置
     */
    public Sm2HandshakeController(SessionManager sessionManager, Sm2ServerConfig config) {
        this.sessionManager = Objects.requireNonNull(sessionManager, "sessionManager must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
    }

    /**
     * 处理客户端握手初始化请求。
     *
     * <p>服务端收到 {@link HandshakeInit} 后：
     * <ol>
     *   <li>速率限制检查</li>
     *   <li>校验参数完整性 + 时间戳窗口</li>
     *   <li>调用 {@link Sm2KeyExchange#processClientInit} 进行密钥交换</li>
     *   <li>获取服务端确认值 SB 和临时公钥 RB</li>
     *   <li>通过 SessionManager 创建会话</li>
     *   <li>暂存 HandshakeResult 供 confirm 验证</li>
     *   <li>返回 {@link HandshakeServerResp}（含 sessionId、RB、SB）</li>
     * </ol>
     *
     * @param init 客户端发送的握手初始化请求
     * @return 服务端握手响应（包含临时公钥 RB 和确认值 SB）
     * @throws Sm2SdkException 如果握手验证或密钥派生失败
     */
    @PostMapping("/handshake/init")
    public HandshakeServerResp handleInit(@RequestBody HandshakeInit init) {
        Objects.requireNonNull(init, "init must not be null");

        String clientId = init.getClientId();

        // === 安全防护 ①：白名单校验 + 按 clientId 独立限流 ===
        checkClientAccess(clientId);

        // === 安全防护 ②：参数完整性 + 时间戳窗口 ===
        validateInit(init);

        log.info("收到客户端握手请求: clientId={}", clientId);

        Sm2KeyExchange keyExchange = sessionManager.getKeyExchange();
        byte[] serverPrivKey = SessionManager.hexToBytes(
                config.getSdkConfig().getSm2PrivateKey());
        // 从 peers 配置查找客户端公钥，匹配规则：peers 中 serverId 与客户端 clientId 一致
        String clientPubKeyHex = findClientPublicKey(init.getClientId());
        byte[] clientPubKey = SessionManager.hexToBytes(clientPubKeyHex);

        try {
            // 步骤 1: 服务端处理客户端初始化请求
            Sm2KeyExchange.HandshakeResult result = keyExchange.processClientInit(
                    init, serverPrivKey, clientPubKey,
                    config.getServerId(), init.getClientId());

            // 步骤 2: 获取 SB 确认值（HutoolSm2KeyExchange 专有方法）
            byte[] sbBytes = getConfirmationValue(keyExchange);

            // 步骤 3: Base64 编码 RB（服务端临时公钥）
            String rbBase64 = Base64.getEncoder().encodeToString(result.getRB());

            // 步骤 4: 通过 SessionManager 创建会话
            Session session = sessionManager.createSession(init.getClientId(), result);

            // === 安全防护：暂存 HandshakeResult 供 confirm 验证 ===
            pendingResults.put(session.getSessionId(), result);

            // 步骤 5: 构建响应
            HandshakeServerResp response = new HandshakeServerResp();
            response.setSessionId(session.getSessionId());
            response.setEphemeralPublicKey(rbBase64);
            response.setConfirmation(sbBytes != null
                    ? Base64.getEncoder().encodeToString(sbBytes) : null);

            log.info("握手初始化成功: sessionId={}", session.getSessionId());
            return response;

        } catch (Sm2SdkException e) {
            throw e;
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.HANDSHAKE_TIMEOUT,
                    "服务端握手处理失败: " + e.getMessage(), e);
        } finally {
            MemoryCleanUtil.cleanKeys(serverPrivKey, clientPubKey);
        }
    }

    /**
     * 处理客户端握手确认请求。
     *
     * <p>客户端发送 {@link HandshakeConfirm} 后，服务端通过
     * {@link Sm2KeyExchange#verifyConfirm} 校验确认值 SA 是否正确。
     *
     * <p>验证通过后立即清理暂存的 HandshakeResult，防止密钥材料残留。
     *
     * @param confirm 客户端发送的握手确认请求
     * @throws Sm2SdkException 如果确认验证失败
     */
    @PostMapping("/handshake/confirm")
    public void handleConfirm(@RequestBody HandshakeConfirm confirm) {
        Objects.requireNonNull(confirm, "confirm must not be null");

        String sessionId = confirm.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            throw new Sm2SdkException(ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED,
                    "确认请求缺少 sessionId");
        }

        // 从会话中获取 clientId 用于限流
        Session session = sessionManager.getSession(sessionId);
        String clientId = session.getClientId();

        // === 安全防护：按 clientId 独立限流 ===
        checkClientAccess(clientId);

        log.info("收到客户端握手确认: sessionId={}, clientId={}", sessionId, clientId);

        // === 安全防护：获取暂存的 HandshakeResult 并完整验证 SA 值 ===
        Sm2KeyExchange.HandshakeResult result = pendingResults.remove(sessionId);
        if (result == null) {
            // HandshakeResult 已被清理或不存在（可能已超时、已验证或从未初始化）
            throw new Sm2SdkException(ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED,
                    "握手结果不存在或已过期，请重新发起握手: " + sessionId);
        }

        try {
            Sm2KeyExchange keyExchange = sessionManager.getKeyExchange();
            if (keyExchange instanceof HutoolSm2KeyExchange) {
                boolean verified = keyExchange.verifyConfirm(result, confirm);
                if (!verified) {
                    log.warn("握手确认验证失败: sessionId={}", sessionId);
                    throw new Sm2SdkException(ErrorCode.HANDSHAKE_TIMEOUT,
                            "握手确认验证失败: SA 值不匹配");
                }
            }
            log.info("握手确认成功: sessionId={}", sessionId);
        } finally {
            // === 安全防护：清理密钥材料 ===
            if (result.getSharedKey() != null) {
                MemoryCleanUtil.cleanKey(result.getSharedKey());
            }
            if (result.getSm4Key() != null) {
                MemoryCleanUtil.cleanKey(result.getSm4Key());
            }
            if (result.getSm4Iv() != null) {
                MemoryCleanUtil.cleanKey(result.getSm4Iv());
            }
        }
    }

    // ==================== 安全防护方法 ====================

    /**
     * 白名单校验 + 按 clientId 独立限流。
     *
     * <p>逻辑：
     * <ol>
     *   <li>如果配置了 peers 列表 → 检查 clientId 是否在白名单中，不在直接拒绝</li>
     *   <li>如果未配置 peers（向后兼容：自测/闭环场景）→ 允许所有，按 clientId 限流</li>
     *   <li>按 clientId 做秒级滑动窗口限流，默认 50 次/秒/客户端</li>
     * </ol>
     *
     * <p>白名单检查在限流之前执行，未知客户端不消耗任何配额。
     *
     * @param clientId 客户端标识（HandshakeInit 中的 clientId 或 Session 中的 clientId）
     * @throws Sm2SdkException 如果客户端不在白名单或速率超限
     */
    private void checkClientAccess(String clientId) {
        List<com.sm2sdk.core.model.Sm2SdkConfig.PeerConfig> peers =
                config.getSdkConfig().getPeerConfigs();

        // ① 白名单校验（peers 已配置时才生效）
        if (peers != null && !peers.isEmpty()) {
            boolean found = false;
            for (com.sm2sdk.core.model.Sm2SdkConfig.PeerConfig peer : peers) {
                if (clientId.equals(peer.getServerId())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                log.warn("未知客户端握手被拒绝: clientId={} (不在 peers 白名单中)", clientId);
                throw new Sm2SdkException(ErrorCode.CLIENT_ACCESS_DENIED,
                        "客户端 [" + clientId + "] 未授权，握手被拒绝");
            }
        }

        // ② 按 clientId 独立限流
        int maxPerSecond = config.getHandshakeRateLimitPerSecond();
        long now = System.currentTimeMillis();
        long currentSecond = now / 1000;

        ClientRateWindow window = clientRateWindows.computeIfAbsent(
                clientId, k -> new ClientRateWindow(currentSecond));

        if (currentSecond != window.second) {
            synchronized (window) {
                if (currentSecond != window.second) {
                    window.second = currentSecond;
                    window.count.set(0);
                }
            }
        }

        int count = window.count.incrementAndGet();
        if (count > maxPerSecond) {
            log.warn("握手速率超限: clientId={}, count={}/s, limit={}/s",
                    clientId, count, maxPerSecond);
            throw new Sm2SdkException(ErrorCode.HANDSHAKE_TIMEOUT,
                    "握手请求过于频繁，请稍后重试（限制: " + maxPerSecond + "/秒/客户端）");
        }
    }

    /**
     * 从密钥交换实现中获取服务端确认值 SB。
     */
    private byte[] getConfirmationValue(Sm2KeyExchange keyExchange) {
        if (keyExchange instanceof HutoolSm2KeyExchange) {
            return ((HutoolSm2KeyExchange) keyExchange).getCurrentConfirmationValue();
        }
        return null;
    }

    /**
     * 从 peers 配置查找客户端的 SM2 公钥。
     */
    private String findClientPublicKey(String clientId) {
        if (config.getSdkConfig().getPeerConfigs() != null) {
            for (com.sm2sdk.core.model.Sm2SdkConfig.PeerConfig peer
                    : config.getSdkConfig().getPeerConfigs()) {
                if (clientId.equals(peer.getServerId())) {
                    return peer.getPublicKey();
                }
            }
        }
        // 未匹配时回退到服务端自己的公钥（向后兼容：自测/闭环场景）
        return config.getSdkConfig().getSm2PublicKey();
    }

    /**
     * 校验握手初始化请求的完整性。
     */
    private void validateInit(HandshakeInit init) {
        if (init.getClientId() == null || init.getClientId().isEmpty()) {
            throw new Sm2SdkException(ErrorCode.HANDSHAKE_TIMEOUT,
                    "握手请求缺少 clientId");
        }
        if (init.getEphemeralPublicKey() == null || init.getEphemeralPublicKey().isEmpty()) {
            throw new Sm2SdkException(ErrorCode.HANDSHAKE_TIMEOUT,
                    "握手请求缺少临时公钥");
        }
        if (init.getTimestamp() <= 0) {
            throw new Sm2SdkException(ErrorCode.HANDSHAKE_TIMEOUT,
                    "握手请求时间戳无效");
        }

        // === 安全防护：时间戳窗口校验 ===
        long now = System.currentTimeMillis();
        long windowMs = config.getTimestampWindowMs();
        long ageMs = now - init.getTimestamp();

        if (ageMs < -windowMs) {
            // 时间戳在未来（时钟偏差过大或恶意请求）
            throw new Sm2SdkException(ErrorCode.HANDSHAKE_TIMEOUT,
                    "握手请求时间戳在未来，请检查时钟同步");
        }
        if (ageMs > windowMs) {
            // 时间戳过期（重放攻击或网络延迟过大）
            log.warn("握手请求时间戳过期: clientId={}, ageMs={}, windowMs={}",
                    init.getClientId(), ageMs, windowMs);
            throw new Sm2SdkException(ErrorCode.HANDSHAKE_TIMEOUT,
                    "握手请求已过期，请重新发起");
        }
    }
}
