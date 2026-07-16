package io.github.mtooo.core.crypto;

import io.github.mtooo.core.exception.Sm2SdkException;
import io.github.mtooo.core.model.HandshakeConfirm;
import io.github.mtooo.core.model.HandshakeInit;
import io.github.mtooo.core.model.HandshakeServerResp;

/**
 * SM2 密钥交换接口，实现 GB/T 32918.4-2016 定义的密钥交换协议。
 *
 * <p>本接口定义了 SM2 临时-静态密钥交换中的客户端和服务端两种角色，
 * 产生共享秘密及派生的密钥材料（SM4 密钥、IV、HMAC 密钥），用于后续的安全通信。
 *
 * <p>协议流程（客户端发起）：
 * <ol>
 *   <li>客户端调用 {@link #buildInitRequest} 生成 {@link HandshakeInit}</li>
 *   <li>服务端调用 {@link #processClientInit} 进行验证并计算服务端结果</li>
 *   <li>服务端响应 {@link HandshakeServerResp}</li>
 *   <li>客户端调用 {@link #processServerResponse} 派生共享密钥材料</li>
 *   <li>客户端调用 {@link #buildConfirm} 生成 {@link HandshakeConfirm}</li>
 *   <li>服务端调用 {@link #verifyConfirm} 验证客户端身份</li>
 * </ol>
 */
public interface Sm2KeyExchange {

    /**
     * 构建客户端初始握手请求。
     *
     * @param clientId          客户端标识
     * @param clientPrivateKey  客户端静态私钥（原始字节）
     * @param serverPublicKey   服务端静态公钥（原始字节）
     * @param clientIdentity    客户端身份字符串（用于 ZA 计算）
     * @return 完整填充的 {@link HandshakeInit}
     * @throws Sm2SdkException 如果密钥生成或签名失败
     */
    HandshakeInit buildInitRequest(String clientId, byte[] clientPrivateKey,
                                   byte[] serverPublicKey, String clientIdentity)
            throws Sm2SdkException;

    /**
     * 在客户端处理服务端的响应以派生共享密钥材料。
     *
     * @param sent          发送给服务端的原始 {@link HandshakeInit}
     * @param resp          服务端的 {@link HandshakeServerResp}
     * @param clientPrivKey 客户端静态私钥（原始字节）
     * @param serverPubKey  服务端静态公钥（原始字节）
     * @param clientId      客户端标识
     * @param serverId      服务端标识
     * @return 派生的 {@link HandshakeResult}
     * @throws Sm2SdkException 如果验证或密钥派生失败
     */
    HandshakeResult processServerResponse(HandshakeInit sent, HandshakeServerResp resp,
                                          byte[] clientPrivKey, byte[] serverPubKey,
                                          String clientId, String serverId)
            throws Sm2SdkException;

    /**
     * 构建密钥派生成功后的客户端确认消息。
     *
     * @param result 来自 {@link #processServerResponse} 的 {@link HandshakeResult}
     * @return 包含 SA 值的 {@link HandshakeConfirm}
     * @throws Sm2SdkException 如果确认值计算失败
     */
    HandshakeConfirm buildConfirm(HandshakeResult result)
            throws Sm2SdkException;

    /**
     * 在服务端处理客户端的初始握手请求。
     *
     * @param init          客户端的 {@link HandshakeInit}
     * @param serverPrivKey 服务端静态私钥（原始字节）
     * @param clientPubKey  客户端静态公钥（原始字节）
     * @param serverId      服务端标识
     * @param clientId      客户端标识
     * @return 派生的 {@link HandshakeResult}
     * @throws Sm2SdkException 如果验证或密钥派生失败
     */
    HandshakeResult processClientInit(HandshakeInit init, byte[] serverPrivKey,
                                      byte[] clientPubKey, String serverId,
                                      String clientId)
            throws Sm2SdkException;

    /**
     * 在服务端验证客户端的确认消息。
     *
     * @param result  来自 {@link #processClientInit} 的 {@link HandshakeResult}
     * @param confirm 客户端的 {@link HandshakeConfirm}
     * @return 确认有效时返回 {@code true}
     * @throws Sm2SdkException 如果验证计算失败
     */
    boolean verifyConfirm(HandshakeResult result, HandshakeConfirm confirm)
            throws Sm2SdkException;

    /**
     * 保存 SM2 密钥交换握手的结果。
     *
     * <p>包含会话标识符、派生的对称密钥材料（SM4 密钥和 IV）、
     * 共享秘密、身份摘要（ZA、ZB）以及握手过程中交换的临时公钥（RA、RB）。
     */
    class HandshakeResult {

        private String sessionId;
        private byte[] sm4Key;
        private byte[] sm4Iv;
        private byte[] sharedKey;
        private String ZA;
        private String ZB;
        private byte[] RA;
        private byte[] RB;

        /** 无参构造器。 */
        public HandshakeResult() {
        }

        /**
         * 构造完整填充的握手结果。
         *
         * @param sessionId 会话标识符
         * @param sm4Key    派生的 SM4 加密密钥（16 字节）
         * @param sm4Iv     派生的 SM4 初始向量（12 字节）
         * @param sharedKey 派生的共享秘密字节
         * @param ZA        客户端身份摘要（Base64 编码）
         * @param ZB        服务端身份摘要（Base64 编码）
         * @param RA        客户端临时公钥（原始字节）
         * @param RB        服务端临时公钥（原始字节）
         */
        public HandshakeResult(String sessionId, byte[] sm4Key, byte[] sm4Iv,
                               byte[] sharedKey, String ZA, String ZB,
                               byte[] RA, byte[] RB) {
            this.sessionId = sessionId;
            this.sm4Key = sm4Key;
            this.sm4Iv = sm4Iv;
            this.sharedKey = sharedKey;
            this.ZA = ZA;
            this.ZB = ZB;
            this.RA = RA;
            this.RB = RB;
        }

        // ========== Getters / Setters ==========

        public String getSessionId() {
            return sessionId;
        }

        public void setSessionId(String sessionId) {
            this.sessionId = sessionId;
        }

        public byte[] getSm4Key() {
            return sm4Key;
        }

        public void setSm4Key(byte[] sm4Key) {
            this.sm4Key = sm4Key;
        }

        public byte[] getSm4Iv() {
            return sm4Iv;
        }

        public void setSm4Iv(byte[] sm4Iv) {
            this.sm4Iv = sm4Iv;
        }

        public byte[] getSharedKey() {
            return sharedKey;
        }

        public void setSharedKey(byte[] sharedKey) {
            this.sharedKey = sharedKey;
        }

        public String getZA() {
            return ZA;
        }

        public void setZA(String ZA) {
            this.ZA = ZA;
        }

        public String getZB() {
            return ZB;
        }

        public void setZB(String ZB) {
            this.ZB = ZB;
        }

        public byte[] getRA() {
            return RA;
        }

        public void setRA(byte[] RA) {
            this.RA = RA;
        }

        public byte[] getRB() {
            return RB;
        }

        public void setRB(byte[] RB) {
            this.RB = RB;
        }

        @Override
        public String toString() {
            return "HandshakeResult{" +
                    "sessionId='" + sessionId + '\'' +
                    '}';
        }
    }
}
