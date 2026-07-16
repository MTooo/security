package io.github.mtooo.core.crypto.impl;

import io.github.mtooo.core.crypto.KeyDerivation;
import io.github.mtooo.core.crypto.MemoryCleanUtil;
import io.github.mtooo.core.crypto.Sm2KeyExchange;
import io.github.mtooo.core.exception.ErrorCode;
import io.github.mtooo.core.exception.Sm2SdkException;
import io.github.mtooo.core.model.HandshakeConfirm;
import io.github.mtooo.core.model.HandshakeInit;
import io.github.mtooo.core.model.HandshakeServerResp;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.math.ec.ECPoint;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link HutoolSm2KeyExchange} 的单元测试。
 *
 * <p>HandshakeResult 是 {@link io.github.mtooo.core.crypto.Sm2KeyExchange} 的内部类。
 *
 * <p>覆盖完整握手轮转、签名验证失败、时间戳超限、临时公钥不在曲线上等场景。
 */
class HutoolSm2KeyExchangeTest {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final X9ECParameters X9 = GMNamedCurves.getByName("SM2P256V1");
    private static final ECDomainParameters DOMAIN = new ECDomainParameters(
            X9.getCurve(), X9.getG(), X9.getN(), X9.getH());

    /** 客户端标识（短标识，用于签名消息）。 */
    private static final String SHORT_CLIENT_ID = "client01";

    /** 客户端身份字符串（用于 ZA 计算和 SM2 IDA）。 */
    private static final String CLIENT_IDENTITY = "client@example.com";

    /** 服务端身份字符串（用于 ZB 计算和 SM2 IDA）。 */
    private static final String SERVER_IDENTITY = "server@example.com";

    /** 客户端静态密钥对。 */
    private static byte[] clientPrivKey;
    private static byte[] clientPubKey;

    /** 服务端静态密钥对。 */
    private static byte[] serverPrivKey;
    private static byte[] serverPubKey;

    @BeforeAll
    static void setUp() {
        // 生成客户端静态密钥对
        AsymmetricCipherKeyPair clientKP = generateKeyPair();
        clientPrivKey = bigIntegerTo32Bytes(
                ((ECPrivateKeyParameters) clientKP.getPrivate()).getD());
        clientPubKey = ((ECPublicKeyParameters) clientKP.getPublic()).getQ().getEncoded(false);

        // 生成服务端静态密钥对
        AsymmetricCipherKeyPair serverKP = generateKeyPair();
        serverPrivKey = bigIntegerTo32Bytes(
                ((ECPrivateKeyParameters) serverKP.getPrivate()).getD());
        serverPubKey = ((ECPublicKeyParameters) serverKP.getPublic()).getQ().getEncoded(false);
    }

    // ==================== 测试用例 ====================

    /**
     * 测试完整握手轮转：客户端构建 init → 服务端处理 → 客户端处理响应 →
     * 客户端确认 → 服务端验证。验证双方派生的 SM4 密钥和 IV 一致。
     */
    @Test
    void testFullHandshakeRoundtrip() {
        // 客户端构建初始请求
        HutoolSm2KeyExchange clientExch = new HutoolSm2KeyExchange();
        HandshakeInit init = clientExch.buildInitRequest(SHORT_CLIENT_ID, clientPrivKey,
                serverPubKey, CLIENT_IDENTITY);

        // 服务端处理初始请求
        HutoolSm2KeyExchange serverExch = new HutoolSm2KeyExchange();
        Sm2KeyExchange.HandshakeResult serverResult = serverExch.processClientInit(init, serverPrivKey,
                clientPubKey, SERVER_IDENTITY, CLIENT_IDENTITY);

        // 检查服务器端密钥材料不为空
        assertNotNull(serverResult.getSm4Key(), "服务端 SM4 密钥不应为空");
        assertNotNull(serverResult.getSm4Iv(), "服务端 SM4 IV 不应为空");
        assertNotNull(serverResult.getSessionId(), "服务端 sessionId 不应为空");
        assertFalse(serverResult.getSessionId().isEmpty(), "sessionId 不应为空字符串");

        // 构建服务端响应（调用方构建 HandshakeServerResp）
        byte[] sb = serverExch.getCurrentConfirmationValue();
        assertNotNull(sb, "SB 确认值不应为空");
        assertEquals(32, sb.length, "SB 应为 32 字节");

        HandshakeServerResp serverResp = new HandshakeServerResp(
                serverResult.getSessionId(),
                Base64.getEncoder().encodeToString(serverResult.getRB()),
                Base64.getEncoder().encodeToString(sb)
        );

        // 客户端处理服务端响应
        Sm2KeyExchange.HandshakeResult clientResult = clientExch.processServerResponse(
                init, serverResp, clientPrivKey, serverPubKey,
                CLIENT_IDENTITY, SERVER_IDENTITY);

        // 客户端构建确认消息
        HandshakeConfirm confirm = clientExch.buildConfirm(clientResult);
        assertNotNull(confirm.getConfirmation(), "SA 确认值不应为空");

        // 服务端验证确认消息
        boolean verified = serverExch.verifyConfirm(serverResult, confirm);
        assertTrue(verified, "服务端应成功验证客户端确认");

        // 验证双方派生的密钥材料完全一致
        assertArrayEquals(serverResult.getSm4Key(), clientResult.getSm4Key(),
                "双方派生的 SM4 密钥应一致");
        assertArrayEquals(serverResult.getSm4Iv(), clientResult.getSm4Iv(),
                "双方派生的 SM4 IV 应一致");
        assertArrayEquals(serverResult.getSharedKey(), clientResult.getSharedKey(),
                "双方派生的共享密钥应一致");
    }

    /**
     * 测试签名验证失败场景。
     *
     * <p>使用错误的客户端公钥验证签名应抛出 {@link ErrorCode#SIGNATURE_VERIFY_FAILED} 异常。
     */
    @Test
    void testSignatureVerificationFailure() {
        // 使用正确的客户端密钥构建 init
        HutoolSm2KeyExchange clientExch = new HutoolSm2KeyExchange();
        HandshakeInit init = clientExch.buildInitRequest(SHORT_CLIENT_ID, clientPrivKey,
                serverPubKey, CLIENT_IDENTITY);

        // 生成另一对密钥用于错误的公钥
        AsymmetricCipherKeyPair wrongKP = generateKeyPair();
        byte[] wrongPubKey = ((ECPublicKeyParameters) wrongKP.getPublic()).getQ().getEncoded(false);

        // 服务端使用错误的客户端公钥验证签名
        HutoolSm2KeyExchange serverExch = new HutoolSm2KeyExchange();
        Sm2SdkException exception = assertThrows(Sm2SdkException.class,
                () -> serverExch.processClientInit(init, serverPrivKey,
                        wrongPubKey, SERVER_IDENTITY, CLIENT_IDENTITY),
                "应抛出签名验证失败异常");
        assertEquals(ErrorCode.SIGNATURE_VERIFY_FAILED, exception.getErrorCode(),
                "异常 ErrorCode 应为 SIGNATURE_VERIFY_FAILED");
    }

    /**
     * 测试时间戳偏差超限场景。
     *
     * <p>时间戳超过 300 秒偏差应抛出 {@link ErrorCode#TIMESTAMP_DEVIATION_EXCEEDED} 异常。
     */
    @Test
    void testTimestampDeviationExceeded() {
        // 构建 init 后篡改时间戳为 10 分钟前
        HutoolSm2KeyExchange clientExch = new HutoolSm2KeyExchange();
        HandshakeInit init = clientExch.buildInitRequest(SHORT_CLIENT_ID, clientPrivKey,
                serverPubKey, CLIENT_IDENTITY);
        // 将时间戳设为 301 秒之前（超过 300 秒限制）
        init.setTimestamp(init.getTimestamp() - 301_000L);

        // 服务端处理时应发现时间戳超限
        HutoolSm2KeyExchange serverExch = new HutoolSm2KeyExchange();
        Sm2SdkException exception = assertThrows(Sm2SdkException.class,
                () -> serverExch.processClientInit(init, serverPrivKey,
                        clientPubKey, SERVER_IDENTITY, CLIENT_IDENTITY),
                "应抛出时间戳偏差超限异常");
        assertEquals(ErrorCode.TIMESTAMP_DEVIATION_EXCEEDED, exception.getErrorCode(),
                "异常 ErrorCode 应为 TIMESTAMP_DEVIATION_EXCEEDED");
    }

    /**
     * 测试服务端临时公钥不在 SM2 曲线上场景。
     *
     * <p>客户端处理服务端响应时，若 RB 不在曲线上应抛出
     * {@link ErrorCode#SERVER_TEMP_PUBKEY_NOT_ON_CURVE} 异常。
     */
    @Test
    void testPointNotOnCurve() {
        // 客户端构建 init
        HutoolSm2KeyExchange clientExch = new HutoolSm2KeyExchange();
        HandshakeInit init = clientExch.buildInitRequest(SHORT_CLIENT_ID, clientPrivKey,
                serverPubKey, CLIENT_IDENTITY);

        // 构造一个不在 SM2 曲线上的 "RB" 点（随机字节）
        byte[] invalidPoint = new byte[65];
        invalidPoint[0] = 0x04; // 非压缩格式标识
        SECURE_RANDOM.nextBytes(invalidPoint);
        invalidPoint[0] = 0x04; // 确保格式标识正确

        // 构造服务端响应
        HandshakeServerResp invalidResp = new HandshakeServerResp(
                "session-test",
                Base64.getEncoder().encodeToString(invalidPoint),
                Base64.getEncoder().encodeToString(new byte[32])
        );

        // 客户端处理时应发现点不在曲线上
        Sm2SdkException exception = assertThrows(Sm2SdkException.class,
                () -> clientExch.processServerResponse(init, invalidResp,
                        clientPrivKey, serverPubKey,
                        CLIENT_IDENTITY, SERVER_IDENTITY),
                "应抛出临时公钥不在曲线上的异常");
        assertEquals(ErrorCode.SERVER_TEMP_PUBKEY_NOT_ON_CURVE, exception.getErrorCode(),
                "异常 ErrorCode 应为 SERVER_TEMP_PUBKEY_NOT_ON_CURVE");
    }

    // ==================== 工具方法 ====================

    /**
     * 生成 SM2 密钥对。
     */
    private static AsymmetricCipherKeyPair generateKeyPair() {
        ECKeyPairGenerator gen = new ECKeyPairGenerator();
        gen.init(new ECKeyGenerationParameters(DOMAIN, SECURE_RANDOM));
        return gen.generateKeyPair();
    }

    /**
     * 将 BigInteger 转换为精确 32 字节的大端序字节数组。
     */
    private static byte[] bigIntegerTo32Bytes(BigInteger n) {
        byte[] raw = n.toByteArray();
        if (raw.length == 32) {
            return raw;
        }
        byte[] result = new byte[32];
        if (raw.length > 32) {
            System.arraycopy(raw, raw.length - 32, result, 0, 32);
        } else {
            System.arraycopy(raw, 0, result, 32 - raw.length, raw.length);
        }
        return result;
    }
}
