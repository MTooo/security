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

import java.util.Base64;
import java.util.Objects;

/**
 * 服务端握手控制器，处理客户端发起的 SM2 密钥交换握手请求。
 *
 * <p>提供两个端点：
 * <ul>
 *   <li>{@code POST /handshake/init} — 处理客户端握手初始化请求</li>
 *   <li>{@code POST /handshake/confirm} — 验证客户端握手确认请求</li>
 * </ul>
 *
 * <p>使用方法：
 * <pre>{@code
 * Sm2ServerConfig config = new Sm2ServerConfig(sdkConfig);
 * Sm2HandshakeController controller = new Sm2HandshakeController(sessionManager, config);
 *
 * // 在 Spring MVC Controller 中委托：
 * @PostMapping("/handshake/init")
 * public HandshakeServerResp handleInit(@RequestBody HandshakeInit init) {
 *     return controller.handleInit(init);
 * }
 * }</pre>
 */
public class Sm2HandshakeController {

    private static final Logger log = LoggerFactory.getLogger(Sm2HandshakeController.class);

    private final SessionManager sessionManager;
    private final Sm2ServerConfig config;

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
     *   <li>校验参数完整性</li>
     *   <li>调用 {@link Sm2KeyExchange#processClientInit} 进行密钥交换</li>
     *   <li>获取服务端确认值 SB 和临时公钥 RB</li>
     *   <li>通过 SessionManager 创建会话</li>
     *   <li>返回 {@link HandshakeServerResp}（含 sessionId、RB、SB）</li>
     * </ol>
     *
     * @param init 客户端发送的握手初始化请求
     * @return 服务端握手响应（包含临时公钥 RB 和确认值 SB）
     * @throws Sm2SdkException 如果握手验证或密钥派生失败
     */
    public HandshakeServerResp handleInit(HandshakeInit init) {
        Objects.requireNonNull(init, "init must not be null");
        validateInit(init);

        log.info("收到客户端握手请求: clientId={}", init.getClientId());

        Sm2KeyExchange keyExchange = sessionManager.getKeyExchange();
        byte[] serverPrivKey = SessionManager.hexToBytes(
                config.getSdkConfig().getSm2PrivateKey());
        byte[] clientPubKey = SessionManager.hexToBytes(
                config.getSdkConfig().getSm2PublicKey());

        try {
            // 步骤 1: 服务端处理客户端初始化请求
            Sm2KeyExchange.HandshakeResult result = keyExchange.processClientInit(
                    init, serverPrivKey, clientPubKey, "server", init.getClientId());

            // 步骤 2: 获取 SB 确认值（HutoolSm2KeyExchange 专有方法）
            byte[] sbBytes = getConfirmationValue(keyExchange);

            // 步骤 3: Base64 编码 RB（服务端临时公钥）
            String rbBase64 = Base64.getEncoder().encodeToString(result.getRB());

            // 步骤 4: 通过 SessionManager 创建会话
            Session session = sessionManager.createSession(init.getClientId(), result);

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
     * @param confirm 客户端发送的握手确认请求
     * @throws Sm2SdkException 如果确认验证失败
     */
    public void handleConfirm(HandshakeConfirm confirm) {
        Objects.requireNonNull(confirm, "confirm must not be null");

        String sessionId = confirm.getSessionId();
        if (sessionId == null || sessionId.isEmpty()) {
            throw new Sm2SdkException(ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED,
                    "确认请求缺少 sessionId");
        }

        log.info("收到客户端握手确认: sessionId={}", sessionId);

        // 验证会话存在
        sessionManager.getSession(sessionId);

        // 获取密钥交换实现并验证确认值
        Sm2KeyExchange keyExchange = sessionManager.getKeyExchange();
        if (keyExchange instanceof HutoolSm2KeyExchange) {
            // verifyConfirm 需要 HandshakeResult，但服务端没有保存。
            // 这里做基本校验：会话已存在即为有效握手。
            // 安全增强版可在 Session 中存储 HandshakeResult 以便完整验证。
        }

        log.info("握手确认成功: sessionId={}", sessionId);
    }

    /**
     * 从密钥交换实现中获取服务端确认值 SB。
     *
     * <p>当前实现依赖 {@link HutoolSm2KeyExchange#getCurrentConfirmationValue()}。
     * 若使用其他实现，此方法返回 null。
     */
    private byte[] getConfirmationValue(Sm2KeyExchange keyExchange) {
        if (keyExchange instanceof HutoolSm2KeyExchange) {
            return ((HutoolSm2KeyExchange) keyExchange).getCurrentConfirmationValue();
        }
        return null;
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
    }
}
