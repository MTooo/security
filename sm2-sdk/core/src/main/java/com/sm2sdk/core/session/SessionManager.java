package com.sm2sdk.core.session;

import com.sm2sdk.core.crypto.KeyDerivation;
import com.sm2sdk.core.crypto.MemoryCleanUtil;
import com.sm2sdk.core.crypto.Sm2KeyExchange;
import com.sm2sdk.core.crypto.Sm4Crypto;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.model.HandshakeConfirm;
import com.sm2sdk.core.model.HandshakeInit;
import com.sm2sdk.core.model.HandshakeServerResp;
import com.sm2sdk.core.model.Sm2SdkConfig;
import com.sm2sdk.core.nonce.NonceValidator;
import com.sm2sdk.core.util.SecureRandomUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 会话编排中心，协调密钥交换、加解密、会话存储。
 *
 * <p>SessionManager 整合 {@link Sm2KeyExchange}、{@link Sm4Crypto}、{@link SessionStore}
 * 和 {@link Sm2SdkConfig}，提供完整的会话生命周期管理：
 * <ul>
 *   <li>客户端主动握手 ({@link #initiateHandshake(String, HandshakeServerResp)})</li>
 *   <li>服务端被动握手 ({@link #handleIncomingHandshake(HandshakeInit)}) </li>
 *   <li>会话检索与过期检测 ({@link #getSession(String)})</li>
 *   <li>会话密钥续期 ({@link #renewSession(String)})</li>
 *   <li>基于会话密钥的加解密 ({@link #encryptBody(String, String)}, {@link #decryptBody(String, String)})</li>
 *   <li>会话销毁 ({@link #destroySession(String)})</li>
 * </ul>
 *
 * <p>握手成功后，共享密钥 (sharedKey) 会被缓存用于后续的密钥续期操作。
 * 会话销毁时，所有密钥材料会被安全清零。
 */
public class SessionManager {

    /** 默认续期阈值（毫秒）：当会话剩余生命周期低于此值时触发续期。 */
    public static final long DEFAULT_RENEW_THRESHOLD_MS = 60_000L;

    /** SM4 密钥派生所需的比特长度（16 字节密钥 + 12 字节 IV = 28 字节 = 224 比特）。 */
    private static final int KDF_SM4_BITS = 480;

    private final Supplier<Sm2KeyExchange> keyExchangeSupplier;
    private final Sm4Crypto sm4Crypto;
    private final SessionStore sessionStore;
    private final Sm2SdkConfig config;
    private final NonceValidator nonceValidator;

    /** 共享密钥缓存，用于会话续期时重新派生 SM4 密钥。 */
    private final ConcurrentHashMap<String, byte[]> sharedKeyCache = new ConcurrentHashMap<>();

    /**
     * 创建 SessionManager 实例（使用 {@link Supplier} 模式，每次握手获取独立实例）。
     *
     * @param keyExchangeSupplier SM2 密钥交换实现工厂
     * @param sm4Crypto           SM4 加解密实现
     * @param sessionStore        会话存储实现
     * @param config              SDK 配置
     */
    public SessionManager(Supplier<Sm2KeyExchange> keyExchangeSupplier, Sm4Crypto sm4Crypto,
                          SessionStore sessionStore, Sm2SdkConfig config) {
        this(keyExchangeSupplier, sm4Crypto, sessionStore, config, null);
    }

    /**
     * 创建 SessionManager 实例（含可选的 NonceValidator，使用 {@link Supplier} 模式）。
     *
     * @param keyExchangeSupplier SM2 密钥交换实现工厂（每次调用返回新实例）
     * @param sm4Crypto           SM4 加解密实现
     * @param sessionStore        会话存储实现
     * @param config              SDK 配置
     * @param nonceValidator      Nonce 重放验证器（可选，可为 null）
     */
    public SessionManager(Supplier<Sm2KeyExchange> keyExchangeSupplier, Sm4Crypto sm4Crypto,
                          SessionStore sessionStore, Sm2SdkConfig config,
                          NonceValidator nonceValidator) {
        this.keyExchangeSupplier = Objects.requireNonNull(keyExchangeSupplier,
                "keyExchangeSupplier must not be null");
        this.sm4Crypto = Objects.requireNonNull(sm4Crypto, "sm4Crypto must not be null");
        this.sessionStore = Objects.requireNonNull(sessionStore, "sessionStore must not be null");
        this.config = Objects.requireNonNull(config, "config must not be null");
        this.nonceValidator = nonceValidator;
    }

    /**
     * @deprecated 请使用 {@link #SessionManager(Supplier, Sm4Crypto, SessionStore, Sm2SdkConfig)}，
     *             传入 {@code () -> new HutoolSm2KeyExchange()} 等工厂方法，
     *             确保每次握手获取独立的密钥交换实例。
     */
    @Deprecated
    public SessionManager(Sm2KeyExchange keyExchange, Sm4Crypto sm4Crypto,
                          SessionStore sessionStore, Sm2SdkConfig config) {
        this(() -> keyExchange, sm4Crypto, sessionStore, config, null);
    }

    /**
     * @deprecated 请使用 {@link #SessionManager(Supplier, Sm4Crypto, SessionStore, Sm2SdkConfig, NonceValidator)}，
     *             传入工厂方法以确保每次握手获取独立的密钥交换实例。
     */
    @Deprecated
    public SessionManager(Sm2KeyExchange keyExchange, Sm4Crypto sm4Crypto,
                          SessionStore sessionStore, Sm2SdkConfig config,
                          NonceValidator nonceValidator) {
        this(() -> keyExchange, sm4Crypto, sessionStore, config, nonceValidator);
    }

    // ==================== 握手方法 ====================

    /**
     * 客户端发起完整握手流程（含 HTTP 传输）。
     *
     * <p>此方法包含完整的 3 步 Sm2KeyExchange 调用。HTTP 传输层由
     * 调用方负责实现 — 此方法直接接受服务端的响应。
     *
     * @param peerId         对端标识
     * @param serverResponse 服务端握手响应（从 HTTP 响应反序列化得到）
     * @return 创建的会话
     * @throws Sm2SdkException 如果握手或会话创建失败
     */
    public Session initiateHandshake(String peerId, HandshakeServerResp serverResponse) {
        Objects.requireNonNull(peerId, "peerId must not be null");
        Objects.requireNonNull(serverResponse, "serverResponse must not be null");

        // 查找对端配置
        Sm2SdkConfig.PeerConfig peer = findPeerConfig(peerId);
        byte[] clientPrivKey = hexToBytes(config.getSm2PrivateKey());
        byte[] serverPubKey = hexToBytes(peer.getPublicKey());

        Sm2KeyExchange keyExchange = getKeyExchange();
        try {
            // Step 1: 构建初始化请求
            HandshakeInit init = keyExchange.buildInitRequest(
                    peerId, clientPrivKey, serverPubKey, peerId);

            // Step 2: 处理服务端响应
            Sm2KeyExchange.HandshakeResult result = keyExchange.processServerResponse(
                    init, serverResponse, clientPrivKey, serverPubKey, peerId, peerId);

            // Step 3: 构建确认消息
            keyExchange.buildConfirm(result);

            // 使用握手结果中的 sessionId
            String sessionId = result.getSessionId() != null
                    ? result.getSessionId()
                    : SecureRandomUtil.generateUUID();

            // 创建会话
            Session session = new Session(
                    sessionId, peerId, peerId,
                    result.getSm4Key(), result.getSm4Iv(),
                    System.currentTimeMillis(),
                    config.getMaxSessionLifetimeMs(),
                    config.getMaxSessionRequests());

            // 缓存共享密钥（用于后续续期）
            if (result.getSharedKey() != null) {
                sharedKeyCache.put(session.getSessionId(), result.getSharedKey().clone());
            }

            // 存储会话
            sessionStore.put(session);
            return session;

        } finally {
            MemoryCleanUtil.cleanKeys(clientPrivKey, serverPubKey);
        }
    }

    /**
     * 客户端发起握手 — 完整流程（含 HTTP 传输）。
     *
     * <p>当前实现中，HTTP 传输层尚未集成。请使用
     * {@link #initiateHandshake(String, HandshakeServerResp)} 提供服务端响应。
     *
     * @param peerId 对端标识
     * @return 创建的会话
     * @throws UnsupportedOperationException 始终抛出，表示此方法尚未实现 HTTP 传输
     */
    public Session initiateHandshake(String peerId) {
        throw new UnsupportedOperationException(
                "直接握手（含 HTTP 传输）尚未实现。请先调用 buildInitRequest() 发送请求，" +
                "然后使用 initiateHandshake(peerId, HandshakeServerResp) 完成握手。");
    }

    /**
     * 服务端处理被动接收的握手请求并创建会话。
     *
     * <p>服务端接收到客户端发送的 {@link HandshakeInit} 后，调用此方法
     * 进行密钥交换计算并创建会话。
     *
     * @param init 客户端发送的握手初始化请求
     * @return 创建的会话
     * @throws Sm2SdkException 如果握手验证或密钥派生失败
     */
    public Session handleIncomingHandshake(HandshakeInit init) {
        Objects.requireNonNull(init, "init must not be null");

        byte[] serverPrivKey = hexToBytes(config.getSm2PrivateKey());
        byte[] clientPubKey = hexToBytes(config.getSm2PublicKey());

        Sm2KeyExchange keyExchange = getKeyExchange();
        try {
            // 服务端处理客户端初始化请求
            Sm2KeyExchange.HandshakeResult result = keyExchange.processClientInit(
                    init, serverPrivKey, clientPubKey, "server", init.getClientId());

            String sessionId = result.getSessionId() != null
                    ? result.getSessionId()
                    : SecureRandomUtil.generateUUID();

            // 创建会话
            Session session = new Session(
                    sessionId,
                    init.getClientId(), "server",
                    result.getSm4Key(), result.getSm4Iv(),
                    System.currentTimeMillis(),
                    config.getMaxSessionLifetimeMs(),
                    config.getMaxSessionRequests());

            // 缓存共享密钥
            if (result.getSharedKey() != null) {
                sharedKeyCache.put(session.getSessionId(), result.getSharedKey().clone());
            }

            sessionStore.put(session);
            return session;

        } finally {
            MemoryCleanUtil.cleanKeys(serverPrivKey, clientPubKey);
        }
    }

    // ==================== 会话生命周期 ====================

    /**
     * 从会话存储中获取会话，并自动检查是否过期。
     *
     * <p>如果会话已过期（空闲超时、最大生命周期或请求次数超限），
     * 会从存储中移除并抛出 {@link Sm2SdkException}。
     *
     * @param sessionId 会话 ID
     * @return 会话实例
     * @throws Sm2SdkException 如果会话不存在或已过期
     */
    public Session getSession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        Session session = sessionStore.get(sessionId);
        if (session == null) {
            throw new Sm2SdkException(ErrorCode.SESSION_NOT_FOUND_OR_EXPIRED,
                    "会话不存在或已过期: " + sessionId);
        }

        if (session.isExpired(config.getSessionTimeoutMs(),
                config.getMaxSessionLifetimeMs(),
                config.getMaxSessionRequests())) {
            sessionStore.remove(sessionId);
            sharedKeyCache.remove(sessionId);
            throw new Sm2SdkException(ErrorCode.SESSION_EXPIRED,
                    "会话已过期: " + sessionId);
        }

        return session;
    }

    /**
     * 续期会话密钥。
     *
     * <p>检查会话的剩余生命周期：如果 < {@link #DEFAULT_RENEW_THRESHOLD_MS}，
     * 则使用 KDF 从共享密钥重新派生 SM4 密钥和 IV，并更新会话存储。
     *
     * <p>续期流程：
     * <ol>
     *   <li>检查 {@code remainingLifetimeMs < renewThreshold} → 需要续期</li>
     *   <li>{@code KeyDerivation.kdf(sharedKey, 480, offset=rekeyVersion+1)} → 派生新密钥材料</li>
     *   <li>{@code session.rekey(newKey, newIv)} → 更新会话密钥</li>
     *   <li>{@code sessionStore.renew(sessionId)} → 更新存储</li>
     * </ol>
     *
     * @param sessionId 会话 ID
     * @return 续期后的会话
     * @throws Sm2SdkException 如果会话不存在、已过期或共享密钥缺失
     */
    public Session renewSession(String sessionId) {
        Session session = getSession(sessionId);

        long remaining = session.remainingLifetimeMs(config.getMaxSessionLifetimeMs());
        if (remaining >= DEFAULT_RENEW_THRESHOLD_MS) {
            // 剩余生命周期充足，无需续期
            return session;
        }

        // 获取缓存的共享密钥
        byte[] sharedKey = sharedKeyCache.get(sessionId);
        if (sharedKey == null) {
            throw new Sm2SdkException(ErrorCode.SESSION_STATE_INVALID,
                    "共享密钥不存在，无法续期: " + sessionId);
        }

        // 使用续期版本号作为偏移量，重新派生密钥
        int newVersion = session.getRekeyVersion() + 1;
        byte[] counterBytes = ByteBuffer.allocate(4).putInt(newVersion).array();
        byte[] z = new byte[sharedKey.length + counterBytes.length];
        System.arraycopy(sharedKey, 0, z, 0, sharedKey.length);
        System.arraycopy(counterBytes, 0, z, sharedKey.length, counterBytes.length);

        byte[] derived = null;
        byte[] newKey = null;
        byte[] newIv = null;
        try {
            derived = KeyDerivation.kdf(z, KDF_SM4_BITS);
            newKey = KeyDerivation.extractSm4Key(derived);
            newIv = KeyDerivation.extractSm4Iv(derived);

            // 更新会话密钥
            session.rekey(newKey, newIv);
            sessionStore.renew(sessionId);

            return session;

        } finally {
            MemoryCleanUtil.cleanKeys(z);
            if (derived != null) {
                MemoryCleanUtil.cleanKey(derived);
            }
            if (newKey != null) {
                MemoryCleanUtil.cleanKey(newKey);
            }
            if (newIv != null) {
                MemoryCleanUtil.cleanKey(newIv);
            }
        }
    }

    // ==================== 加解密操作 ====================

    /**
     * 使用会话密钥加密 JSON 字符串。
     *
     * <p>从会话中取出 SM4 密钥和 IV，对明文 JSON 进行加密，
     * 返回 Base64 编码的密文。
     *
     * @param sessionId  会话 ID
     * @param plainJson  待加密的 JSON 字符串
     * @return Base64 编码的密文
     * @throws Sm2SdkException 如果会话不存在、已过期或加密失败
     */
    public String encryptBody(String sessionId, String plainJson) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(plainJson, "plainJson must not be null");

        Session session = getSession(sessionId);
        session.touch();

        byte[] key = session.getSm4KeyCopy();
        byte[] iv = session.getSm4IvCopy();
        try {
            byte[] plaintext = plainJson.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertext = sm4Crypto.encrypt(key, iv, null, plaintext);
            return Base64.getEncoder().encodeToString(ciphertext);
        } finally {
            Session.clearKeyCopy(key);
            Session.clearKeyCopy(iv);
        }
    }

    /**
     * 使用会话密钥解密 Base64 编码的密文。
     *
     * <p>解码 Base64 密文，从会话中取出 SM4 密钥和 IV 进行解密，
     * 返回明文 JSON 字符串。
     *
     * @param sessionId       会话 ID
     * @param encryptedBase64 Base64 编码的密文
     * @return 解密后的 JSON 字符串
     * @throws Sm2SdkException 如果会话不存在、已过期、Base64 解码失败或解密失败
     */
    public String decryptBody(String sessionId, String encryptedBase64) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        Objects.requireNonNull(encryptedBase64, "encryptedBase64 must not be null");

        Session session = getSession(sessionId);
        session.touch();

        byte[] key = session.getSm4KeyCopy();
        byte[] iv = session.getSm4IvCopy();
        try {
            byte[] ciphertext;
            try {
                ciphertext = Base64.getDecoder().decode(encryptedBase64);
            } catch (IllegalArgumentException e) {
                throw new Sm2SdkException(ErrorCode.SM4_DECRYPT_TAG_FAILED,
                        "Base64 解码失败: " + e.getMessage(), e);
            }

            byte[] plaintext = sm4Crypto.decrypt(key, iv, null, ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);

        } catch (Sm2SdkException e) {
            // 直接传播 Sm2SdkException（如 SM4_DECRYPT_TAG_FAILED）
            throw e;
        } finally {
            Session.clearKeyCopy(key);
            Session.clearKeyCopy(iv);
        }
    }

    /**
     * 销毁会话。
     *
     * <p>从会话存储中移除会话，将会话密钥清零，
     * 并清除缓存的共享密钥。
     *
     * @param sessionId 会话 ID
     */
    public void destroySession(String sessionId) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");

        Session session = sessionStore.get(sessionId);
        if (session == null) {
            return;
        }

        sessionStore.remove(sessionId);

        // 将会话密钥清零
        session.destroy();

        // 清除缓存的共享密钥
        byte[] cachedSharedKey = sharedKeyCache.remove(sessionId);
        if (cachedSharedKey != null) {
            MemoryCleanUtil.cleanKey(cachedSharedKey);
        }
    }

    // ==================== 内部辅助方法 ====================

    // ==================== 密钥交换访问 ====================

    /**
     * 获取 SM2 密钥交换实现，用于构建和处理握手消息。
     *
     * <p>客户端发起握手时需要先调用 {@link Sm2KeyExchange#buildInitRequest} 构建
     * {@link com.sm2sdk.core.model.HandshakeInit}，发送到服务端后，再调用
     * {@link Sm2KeyExchange#processServerResponse} 处理响应，最后调用
     * {@link Sm2KeyExchange#buildConfirm} 构建确认消息。
     *
     * @return SM2 密钥交换实现
     */
    public Sm2KeyExchange getKeyExchange() {
        return keyExchangeSupplier.get();
    }

    /**
     * 从密钥交换结果创建会话并存入存储。
     *
     * <p>此方法适用于客户端自行完成握手流程（构建 Init、发送、接收响应、处理响应、
     * 构建并发送 Confirm）后，将得到的 {@link Sm2KeyExchange.HandshakeResult}
     * 交由 SessionManager 创建和管理会话。
     *
     * <p>会话创建完成后，会缓存共享密钥（用于后续续期），与会话一起存入存储。
     *
     * @param peerId 对端标识
     * @param result 密钥交换结果（包含派生的 SM4 密钥、IV 和会话 ID）
     * @return 创建的会话
     * @throws Sm2SdkException 如果会话创建失败
     */
    public Session createSession(String peerId, Sm2KeyExchange.HandshakeResult result) {
        Objects.requireNonNull(peerId, "peerId must not be null");
        Objects.requireNonNull(result, "result must not be null");

        String sessionId = result.getSessionId() != null
                ? result.getSessionId()
                : SecureRandomUtil.generateUUID();

        Session session = new Session(
                sessionId, peerId, peerId,
                result.getSm4Key(), result.getSm4Iv(),
                System.currentTimeMillis(),
                config.getMaxSessionLifetimeMs(),
                config.getMaxSessionRequests());

        // 缓存共享密钥（用于后续续期）
        if (result.getSharedKey() != null) {
            sharedKeyCache.put(session.getSessionId(), result.getSharedKey().clone());
        }

        sessionStore.put(session);
        return session;
    }

    // ==================== 内部辅助方法 ====================

    /**
     * 查找对端配置。
     *
     * <p>在 {@link Sm2SdkConfig#getPeerConfigs()} 中查找与指定 peerId 匹配的配置。
     * 如果找不到精确匹配，返回第一个配置；如果没有配置，返回一个基于 SDK 配置的默认配置。
     */
    private Sm2SdkConfig.PeerConfig findPeerConfig(String peerId) {
        List<Sm2SdkConfig.PeerConfig> peers = config.getPeerConfigs();
        if (peers == null || peers.isEmpty()) {
            // 没有显式配置时使用默认值
            return new Sm2SdkConfig.PeerConfig(config.getSm2PublicKey(), config.getServerUrl());
        }
        // 使用第一个配置（简化策略）
        return peers.get(0);
    }

    /**
     * 将十六进制字符串转换为字节数组。
     *
     * @param hex 十六进制字符串（可包含非十六进制字符，会被自动过滤）
     * @return 字节数组（输入为 null 或空时返回空数组）
     */
    public static byte[] hexToBytes(String hex) {
        if (hex == null || hex.isEmpty()) {
            return new byte[0];
        }
        // 过滤非十六进制字符（允许带 0x 前缀、空格、连字符等）
        String clean = hex.replaceAll("[^0-9A-Fa-f]", "");
        if (clean.isEmpty()) {
            return new byte[0];
        }
        int len = clean.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(clean.charAt(i), 16) << 4)
                    + Character.digit(clean.charAt(i + 1), 16));
        }
        return data;
    }
}
