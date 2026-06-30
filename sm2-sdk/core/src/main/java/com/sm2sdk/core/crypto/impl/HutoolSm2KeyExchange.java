package com.sm2sdk.core.crypto.impl;

import cn.hutool.crypto.SmUtil;
import cn.hutool.crypto.asymmetric.SM2;
import com.sm2sdk.core.crypto.KeyDerivation;
import com.sm2sdk.core.crypto.MemoryCleanUtil;
import com.sm2sdk.core.crypto.Sm2KeyExchange;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import com.sm2sdk.core.model.HandshakeConfirm;
import com.sm2sdk.core.model.HandshakeInit;
import com.sm2sdk.core.model.HandshakeServerResp;
import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.*;
import org.bouncycastle.math.ec.ECCurve;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

/**
 * 基于 Hutool SM3 和 BouncyCastle 的 SM2 密钥交换实现，
 * 遵循 GB/T 32918.3-2016 第6章（原 GM/T 0003.3-2012）定义的密钥交换协议。
 *
 * <p>协议流程（客户端发起）：
 * <ol>
 *   <li>客户端调用 {@link #buildInitRequest} 生成临时密钥对并构建握手请求</li>
 *   <li>服务端调用 {@link #processClientInit} 验证请求并生成响应</li>
 *   <li>客户端调用 {@link #processServerResponse} 验证响应并派生共享密钥</li>
 *   <li>客户端调用 {@link #buildConfirm} 构建确认消息</li>
 *   <li>服务端调用 {@link #verifyConfirm} 验证客户端确认</li>
 * </ol>
 *
 * <p>重要：临时私钥在使用后通过 {@link MemoryCleanUtil#cleanKey(byte[])} 清除。
 *
 * <p>注意：本实现不是线程安全的。每个密钥交换流程应使用独立的实例。
 */
public class HutoolSm2KeyExchange implements Sm2KeyExchange {

    /** 协议版本号。 */
    private static final String PROTOCOL_VERSION = "1.0";

    /** KDF 输出长度（比特），60 字节 = SM4密钥(16) + SM4 IV(12) + 预留(32)。 */
    private static final int KEY_BITS = 480;

    /** 时间戳最大允许偏差（毫秒），300 秒。 */
    private static final long MAX_TIMESTAMP_DEVIATION_MS = 300_000L;

    // ==================== SM2 曲线参数 ====================

    private static final X9ECParameters X9 = GMNamedCurves.getByName("SM2P256V1");
    private static final ECDomainParameters DOMAIN = new ECDomainParameters(
            X9.getCurve(), X9.getG(), X9.getN(), X9.getH());
    private static final ECCurve CURVE = X9.getCurve();
    private static final ECPoint G_POINT = X9.getG();

    /** Z 值计算中使用的曲线参数字节（32 字节，大端序）。 */
    private static final byte[] A_BYTES = bigIntegerTo32Bytes(CURVE.getA().toBigInteger());
    private static final byte[] B_BYTES = bigIntegerTo32Bytes(CURVE.getB().toBigInteger());
    private static final byte[] GX_BYTES = bigIntegerTo32Bytes(G_POINT.getAffineXCoord().toBigInteger());
    private static final byte[] GY_BYTES = bigIntegerTo32Bytes(G_POINT.getAffineYCoord().toBigInteger());

    /** 用于 x̄ 计算的 w = 2^127。 */
    private static final BigInteger W = BigInteger.ONE.shiftLeft(127);
    private static final BigInteger W_MINUS_ONE = W.subtract(BigInteger.ONE);

    /** 安全随机数生成器。 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    // ==================== 实例状态（非线程安全） ====================

    /** 当前握手的客户端临时私钥（原始字节），由 buildInitRequest 生成，在 processServerResponse 中使用后清除。 */
    private byte[] ephemeralPrivKeyBytes;

    /** 共享密钥点 x1 坐标（32 字节），在共享密钥计算后存储，用于后续确认。 */
    private byte[] currentX1Bytes;

    /** 共享密钥点 y1 坐标（32 字节），在共享密钥计算后存储，用于后续确认。 */
    private byte[] currentY1Bytes;

    /** 服务端确认值 SB，由 processClientInit 计算，调用者通过 {@link #getCurrentConfirmationValue()} 获取。 */
    private byte[] currentConfirmationValue;

    // ==================== 内部工具方法 ====================

    /**
     * 将 BigInteger 转换为精确 32 字节的大端序字节数组。
     * SM2 使用 256 位域，所有域元素均编码为 32 字节。
     */
    private static byte[] bigIntegerTo32Bytes(BigInteger n) {
        byte[] raw = n.toByteArray();
        if (raw.length == 32) {
            return raw;
        }
        byte[] result = new byte[32];
        if (raw.length > 32) {
            // 移除 BigInteger 可能添加的前导符号字节
            System.arraycopy(raw, raw.length - 32, result, 0, 32);
        } else {
            // 左侧补零至 32 字节
            System.arraycopy(raw, 0, result, 32 - raw.length, raw.length);
        }
        return result;
    }

    /**
     * 将 long 值编码为 8 字节大端序字节数组。
     */
    private static byte[] longToBytes(long v) {
        byte[] bytes = new byte[8];
        bytes[0] = (byte) (v >>> 56);
        bytes[1] = (byte) (v >>> 48);
        bytes[2] = (byte) (v >>> 40);
        bytes[3] = (byte) (v >>> 32);
        bytes[4] = (byte) (v >>> 24);
        bytes[5] = (byte) (v >>> 16);
        bytes[6] = (byte) (v >>> 8);
        bytes[7] = (byte) v;
        return bytes;
    }

    /**
     * 拼接多个字节数组。
     */
    private static byte[] concat(byte[]... arrays) {
        int totalLen = 0;
        for (byte[] arr : arrays) {
            totalLen += arr.length;
        }
        byte[] result = new byte[totalLen];
        int offset = 0;
        for (byte[] arr : arrays) {
            System.arraycopy(arr, 0, result, offset, arr.length);
            offset += arr.length;
        }
        return result;
    }

    /**
     * 生成 SM2 密钥对。
     */
    private static AsymmetricCipherKeyPair generateKeyPair() {
        ECKeyPairGenerator gen = new ECKeyPairGenerator();
        gen.init(new ECKeyGenerationParameters(DOMAIN, SECURE_RANDOM));
        return gen.generateKeyPair();
    }

    /**
     * 计算 SM2 身份摘要 Z 值。
     *
     * <p>Z = SM3(ENTLA || IDA || a || b || xG || yG || xA || yA)
     *
     * @param idaBytes 身份标识字节
     * @param xAPub    公钥 x 坐标（32 字节）
     * @param yAPub    公钥 y 坐标（32 字节）
     * @return SM3 哈希值（32 字节）
     */
    private static byte[] computeZ(byte[] idaBytes, byte[] xAPub, byte[] yAPub) {
        int entla = idaBytes.length * 8; // IDA 的比特长度
        byte[] entlaBytes = new byte[]{(byte) (entla >>> 8), (byte) entla};
        byte[] input = concat(entlaBytes, idaBytes, A_BYTES, B_BYTES,
                GX_BYTES, GY_BYTES, xAPub, yAPub);
        return SmUtil.sm3().digest(input);
    }

    /**
     * 计算确认哈希值。
     *
     * <p>用于 SB 和 SA 的计算：
     * <ul>
     *   <li>SB = SM3(0x02 || y1 || SM3(x1 || ZA || ZB || RA || RB))</li>
     *   <li>SA = SM3(0x03 || y1 || SM3(x1 || ZA || ZB || RA || RB))</li>
     * </ul>
     *
     * @param prefix 前缀字节（0x02 用于 SB，0x03 用于 SA）
     * @param y1Bytes y1 坐标（32 字节）
     * @param innerHashInput 内部哈希输入（x1 || ZA || ZB || RA || RB）
     * @return 确认哈希值（32 字节）
     */
    private static byte[] computeConfirmation(byte prefix, byte[] y1Bytes, byte[] innerHashInput) {
        // 计算内部 SM3 哈希: SM3(x1 || ZA || ZB || RA || RB)
        byte[] innerHash = SmUtil.sm3().digest(innerHashInput);
        // 计算外部 SM3 哈希: SM3(prefix || y1 || innerHash)
        byte[] outerInput = concat(new byte[]{prefix}, y1Bytes, innerHash);
        return SmUtil.sm3().digest(outerInput);
    }

    /**
     * 解码 ECPoint 并验证其在 SM2 曲线上。
     *
     * @param pointBytes 点编码字节（非压缩格式 04||x||y）
     * @return 解码并验证通过的 ECPoint
     * @throws Sm2SdkException 如果点不在曲线上或为无穷远点
     */
    private static ECPoint decodeAndVerifyPoint(byte[] pointBytes) {
        try {
            ECPoint point = CURVE.decodePoint(pointBytes);
            // 检查是否为无穷远点且在曲线上
            if (point == null || point.isInfinity()) {
                throw new Sm2SdkException(ErrorCode.SERVER_TEMP_PUBKEY_NOT_ON_CURVE,
                        "临时公钥为无穷远点");
            }
            // ECPoint.isValid() 会验证点在曲线上
            if (!point.isValid()) {
                throw new Sm2SdkException(ErrorCode.SERVER_TEMP_PUBKEY_NOT_ON_CURVE,
                        "临时公钥不在 SM2 曲线上");
            }
            return point;
        } catch (Sm2SdkException e) {
            throw e;
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.SERVER_TEMP_PUBKEY_NOT_ON_CURVE,
                    "临时公钥解码失败或不在曲线上: " + e.getMessage(), e);
        }
    }

    /**
     * 计算 x̄ = 2^127 + (x & (2^127 - 1))。
     *
     * <p>用于密钥交换中的临时公钥变换。
     */
    private static BigInteger computeXBar(BigInteger x) {
        return W.add(x.and(W_MINUS_ONE));
    }

    /**
     * 使用客户端静态私钥对消息进行 SM2 签名。
     *
     * <p>使用 Hutool SM2 的 {@code sign(data, id)} 方法，
     * 支持 GB/T 32918 标准的自定义身份标识（ID），内部使用 BouncyCastle SM2Signer。
     *
     * @param privateKey 客户端静态私钥原始字节
     * @param data       待签名数据
     * @param ida        SM2 签名使用的身份标识字节
     * @return DER 编码的 SM2 签名
     */
    private static byte[] sign(byte[] privateKey, byte[] data, byte[] ida) {
        try {
            // 使用 Hutool SM2 签名，传入私钥和自定义身份标识
            SM2 sm2 = SmUtil.sm2(privateKey, null);
            return sm2.sign(data, ida);
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.LOCAL_SIGN_GEN_FAILED,
                    "SM2 签名失败: " + e.getMessage(), e);
        }
    }

    /**
     * 使用客户端静态公钥验证 SM2 签名。
     *
     * <p>使用 Hutool SM2 的 {@code verify(data, signature, id)} 方法，
     * 支持 GB/T 32918 标准的自定义身份标识（ID），内部使用 BouncyCastle SM2Signer。
     *
     * @param publicKey 客户端静态公钥原始字节（非压缩格式 04||x||y）
     * @param data      已签名数据
     * @param signature DER 编码的 SM2 签名
     * @param ida       SM2 签名使用的身份标识字节
     * @return 签名有效返回 true
     */
    private static boolean verify(byte[] publicKey, byte[] data, byte[] signature, byte[] ida) {
        // 使用 Hutool SM2 验签，传入公钥和自定义身份标识
        SM2 sm2 = SmUtil.sm2(null, publicKey);
        return sm2.verify(data, signature, ida);
    }

    /**
     * 计算共享密钥点并执行 KDF 派生。
     *
     * <p>共享点计算公式（GB/T 32918.3-2016 第6章）：
     * <ul>
     *   <li>t = ourStaticPriv + x̄_our * ourEphemeralPriv (mod n)</li>
     *   <li>U = t * (theirStaticPub + x̄_their * theirEphemeralPub)</li>
     *   <li>x̄ = 2^127 + (x_R & (2^127 - 1))</li>
     * </ul>
     *
     * <p>客户端（发起方）和服务端（响应方）使用相同的公式计算，结果一致：
     * <ul>
     *   <li>客户端: tA = dA + x̄A * rA, U = tA * (PB + x̄B * RB)</li>
     *   <li>服务端: tB = dB + x̄B * rB, V = tB * (PA + x̄A * RA)</li>
     *   <li>U = V = (dA + x̄A * rA)(dB + x̄B * rB) * G</li>
     * </ul>
     *
     * @param ourStaticPriv       本方静态私钥 (d)
     * @param ourEphemeralPriv    本方临时私钥 (r)
     * @param ourEphemeralPub     本方临时公钥点 (R)，用于计算 x̄_our
     * @param theirStaticPub      对方静态公钥点 (P)
     * @param theirEphemeralPub   对方临时公钥点 (R 对方)，用于计算 x̄_their
     * @param zaBytes             客户端身份摘要 ZA
     * @param zbBytes             服务端身份摘要 ZB
     * @return 包含派生密钥材料的 HandshakeResult（不含 RA/RB）
     */
    private HandshakeResult computeSharedKey(
            BigInteger ourStaticPriv,
            BigInteger ourEphemeralPriv,
            ECPoint ourEphemeralPub,
            ECPoint theirStaticPub,
            ECPoint theirEphemeralPub,
            byte[] zaBytes,
            byte[] zbBytes) {

        // 计算 x̄_our = 2^127 + (x_our & (2^127 - 1))
        BigInteger xOur = ourEphemeralPub.getAffineXCoord().toBigInteger();
        BigInteger xBarOur = computeXBar(xOur);

        // 计算 t = ourStaticPriv + x̄_our * ourEphemeralPriv (mod n)
        BigInteger n = DOMAIN.getN();
        BigInteger t = ourStaticPriv.add(xBarOur.multiply(ourEphemeralPriv)).mod(n);

        // 计算 x̄_their = 2^127 + (x_their & (2^127 - 1))
        BigInteger xTheir = theirEphemeralPub.getAffineXCoord().toBigInteger();
        BigInteger xBarTheir = computeXBar(xTheir);

        // 计算 U = t * (theirStaticPub + x̄_their * theirEphemeralPub)
        ECPoint uTemp = theirStaticPub.add(theirEphemeralPub.multiply(xBarTheir)).normalize();
        ECPoint u = uTemp.multiply(t).normalize();

        BigInteger x1 = u.getAffineXCoord().toBigInteger();
        if (x1.equals(BigInteger.ZERO)) {
            throw new Sm2SdkException(ErrorCode.SHARED_SECRET_CALC_FAILED,
                    "共享密钥计算失败（x1 = 0）");
        }
        BigInteger y1 = u.getAffineYCoord().toBigInteger();

        byte[] x1Bytes = bigIntegerTo32Bytes(x1);
        byte[] y1Bytes = bigIntegerTo32Bytes(y1);

        // 存储共享点坐标用于后续确认计算
        this.currentX1Bytes = x1Bytes;
        this.currentY1Bytes = y1Bytes;

        // KDF 派生密钥材料: KDF(x1 || ZA || ZB, 480)
        byte[] kdfInput = concat(x1Bytes, zaBytes, zbBytes);
        byte[] derived = KeyDerivation.kdf(kdfInput, KEY_BITS);
        byte[] sm4Key = KeyDerivation.extractSm4Key(derived);
        byte[] sm4Iv = KeyDerivation.extractSm4Iv(derived);

        // 构建结果（不包含 RA/RB，由调用方设置）
        HandshakeResult result = new HandshakeResult();
        result.setSm4Key(sm4Key);
        result.setSm4Iv(sm4Iv);
        result.setSharedKey(derived);
        result.setZA(Base64.getEncoder().encodeToString(zaBytes));
        result.setZB(Base64.getEncoder().encodeToString(zbBytes));

        return result;
    }

    // ==================== 接口方法实现 ====================

    @Override
    public HandshakeInit buildInitRequest(String clientId, byte[] clientPrivateKey,
                                          byte[] serverPublicKey, String clientIdentity)
            throws Sm2SdkException {
        try {
            // 1. 生成临时密钥对 (rA, RA = [rA]G)
            AsymmetricCipherKeyPair ephemPair = generateKeyPair();
            ECPrivateKeyParameters ephemPriv = (ECPrivateKeyParameters) ephemPair.getPrivate();
            ECPublicKeyParameters ephemPub = (ECPublicKeyParameters) ephemPair.getPublic();
            ECPoint raPoint = ephemPub.getQ();

            // 保存临时私钥用于后续 processServerResponse
            this.ephemeralPrivKeyBytes = bigIntegerTo32Bytes(ephemPriv.getD());

            // 编码 RA（非压缩格式 04||x||y，65 字节）
            byte[] raBytes = raPoint.getEncoded(false);

            // 2. 计算客户端静态公钥点 qA = [dA]G
            BigInteger dA = new BigInteger(1, clientPrivateKey);
            ECPoint qA = new FixedPointCombMultiplier().multiply(G_POINT, dA).normalize();
            byte[] xA = bigIntegerTo32Bytes(qA.getAffineXCoord().toBigInteger());
            byte[] yA = bigIntegerTo32Bytes(qA.getAffineYCoord().toBigInteger());

            // 3. 计算 ZA = SM3(ENTLA || IDA || a || b || xG || yG || xA || yA)
            byte[] idaBytes = clientIdentity.getBytes(StandardCharsets.UTF_8);
            byte[] zaBytes = computeZ(idaBytes, xA, yA);

            // 4. 构建签名消息: RA || clientId || ZA || timestamp
            long timestamp = System.currentTimeMillis();
            byte[] clientIdBytes = clientId.getBytes(StandardCharsets.UTF_8);
            byte[] timestampBytes = longToBytes(timestamp);
            byte[] signMessage = concat(raBytes, clientIdBytes, zaBytes, timestampBytes);

            // 使用客户端静态私钥签名
            byte[] signature = sign(clientPrivateKey, signMessage, idaBytes);

            // 5. 构建 HandshakeInit
            HandshakeInit init = new HandshakeInit();
            init.setProtocolVersion(PROTOCOL_VERSION);
            init.setClientId(clientId);
            init.setEphemeralPublicKey(Base64.getEncoder().encodeToString(raBytes));
            init.setTimestamp(timestamp);
            init.setSignature(Base64.getEncoder().encodeToString(signature));
            init.setZA(Base64.getEncoder().encodeToString(zaBytes));

            return init;

        } catch (Sm2SdkException e) {
            throw e;
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.CLIENT_INIT_FAILED,
                    "构建握手初始请求失败: " + e.getMessage(), e);
        }
    }

    @Override
    public HandshakeResult processServerResponse(HandshakeInit sent, HandshakeServerResp resp,
                                                  byte[] clientPrivKey, byte[] serverPubKey,
                                                  String clientId, String serverId)
            throws Sm2SdkException {
        // 获取保存的临时私钥 rA
        byte[] raPrivBytes = this.ephemeralPrivKeyBytes;
        if (raPrivBytes == null) {
            throw new Sm2SdkException(ErrorCode.CLIENT_INIT_FAILED,
                    "未找到临时私钥，请先调用 buildInitRequest");
        }
        BigInteger rA = new BigInteger(1, raPrivBytes);

        try {
            // 1. 解码 RB 并验证在曲线上
            byte[] rbEncoded = Base64.getDecoder().decode(resp.getEphemeralPublicKey());
            ECPoint rbPoint = decodeAndVerifyPoint(rbEncoded);

            // 2. 解码 ZA（来自 init 请求）
            byte[] zaBytes = Base64.getDecoder().decode(sent.getZA());

            // 3. 计算 ZB（服务端身份摘要）
            byte[] serverIdBytes = serverId.getBytes(StandardCharsets.UTF_8);
            ECPoint qB = CURVE.decodePoint(serverPubKey);
            byte[] xB = bigIntegerTo32Bytes(qB.getAffineXCoord().toBigInteger());
            byte[] yB = bigIntegerTo32Bytes(qB.getAffineYCoord().toBigInteger());
            byte[] zbBytes = computeZ(serverIdBytes, xB, yB);

            // 4. 计算共享密钥
            byte[] raEncoded = Base64.getDecoder().decode(sent.getEphemeralPublicKey());
            ECPoint raPoint = CURVE.decodePoint(raEncoded); // 本方临时公钥点（客户端 RA）
            ECPoint pbPoint = CURVE.decodePoint(serverPubKey); // 对方静态公钥点（服务端 PB）
            BigInteger dA = new BigInteger(1, clientPrivKey);
            HandshakeResult result = computeSharedKey(
                    dA,             // 本方静态私钥 (dA)
                    rA,             // 本方临时私钥 (rA)
                    raPoint,        // 本方临时公钥点 (RA)，用于计算 x̄A
                    pbPoint,        // 对方静态公钥点 (PB)
                    rbPoint,        // 对方临时公钥点 (RB)
                    zaBytes,        // ZA
                    zbBytes         // ZB
            );
            result.setSessionId(resp.getSessionId());
            result.setRA(raEncoded);
            result.setRB(rbEncoded);

            // 5. 验证 SB
            byte[] sbReceived = Base64.getDecoder().decode(resp.getConfirmation());
            byte[] innerHashInput = concat(this.currentX1Bytes, zaBytes, zbBytes,
                    raEncoded, rbEncoded);
            byte[] sbExpected = computeConfirmation((byte) 0x02, this.currentY1Bytes, innerHashInput);

            if (!Arrays.equals(sbReceived, sbExpected)) {
                throw new Sm2SdkException(ErrorCode.KEY_CONFIRM_FAILED_SB,
                        "密钥确认失败（SB 验证不通过）");
            }

            return result;

        } catch (Sm2SdkException e) {
            throw e;
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.SHARED_SECRET_CALC_FAILED,
                    "处理服务端响应失败: " + e.getMessage(), e);
        } finally {
            // 清除临时私钥
            MemoryCleanUtil.cleanKey(this.ephemeralPrivKeyBytes);
            this.ephemeralPrivKeyBytes = null;
        }
    }

    @Override
    public HandshakeConfirm buildConfirm(HandshakeResult result) throws Sm2SdkException {
        try {
            // 从 result 中获取各参数
            byte[] raBytes = result.getRA();
            byte[] rbBytes = result.getRB();
            byte[] zaBytes = Base64.getDecoder().decode(result.getZA());
            byte[] zbBytes = Base64.getDecoder().decode(result.getZB());

            // 使用存储的共享点坐标
            if (this.currentX1Bytes == null || this.currentY1Bytes == null) {
                throw new Sm2SdkException(ErrorCode.CLIENT_INIT_FAILED,
                        "未找到共享密钥点坐标，请先调用 processServerResponse");
            }

            // 计算 SA = SM3(0x03 || y1 || SM3(x1 || ZA || ZB || RA || RB))
            byte[] innerHashInput = concat(this.currentX1Bytes, zaBytes, zbBytes,
                    raBytes, rbBytes);
            byte[] sa = computeConfirmation((byte) 0x03, this.currentY1Bytes, innerHashInput);

            HandshakeConfirm confirm = new HandshakeConfirm();
            confirm.setSessionId(result.getSessionId());
            confirm.setConfirmation(Base64.getEncoder().encodeToString(sa));

            return confirm;

        } catch (Sm2SdkException e) {
            throw e;
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.CLIENT_INIT_FAILED,
                    "构建确认消息失败: " + e.getMessage(), e);
        }
    }

    @Override
    public HandshakeResult processClientInit(HandshakeInit init, byte[] serverPrivKey,
                                              byte[] clientPubKey, String serverId,
                                              String clientId) throws Sm2SdkException {
        try {
            // 1. 验证时间戳偏差 |now - ts| <= 300s
            long now = System.currentTimeMillis();
            long ts = init.getTimestamp();
            long deviation = Math.abs(now - ts);
            if (deviation > MAX_TIMESTAMP_DEVIATION_MS) {
                throw new Sm2SdkException(ErrorCode.TIMESTAMP_DEVIATION_EXCEEDED,
                        "时间戳偏差: " + deviation + "ms，超过限制 " + MAX_TIMESTAMP_DEVIATION_MS + "ms");
            }

            // 2. 解码 RA 并验证在曲线上
            byte[] raEncoded = Base64.getDecoder().decode(init.getEphemeralPublicKey());
            ECPoint raPoint = decodeAndVerifyPoint(raEncoded);

            // 3. 验证客户端签名
            byte[] zaBytes = Base64.getDecoder().decode(init.getZA());
            byte[] shortClientIdBytes = init.getClientId().getBytes(StandardCharsets.UTF_8);
            byte[] timestampBytes = longToBytes(ts);
            byte[] signMessage = concat(raEncoded, shortClientIdBytes, zaBytes, timestampBytes);

            byte[] signature = Base64.getDecoder().decode(init.getSignature());
            byte[] idaBytes = clientId.getBytes(StandardCharsets.UTF_8);

            boolean valid = verify(clientPubKey, signMessage, signature, idaBytes);
            if (!valid) {
                throw new Sm2SdkException(ErrorCode.SIGNATURE_VERIFY_FAILED,
                        "客户端签名验证失败");
            }

            // 4. 生成服务端临时密钥对 (rB, RB)
            AsymmetricCipherKeyPair ephemPair = generateKeyPair();
            ECPrivateKeyParameters ephemPriv = (ECPrivateKeyParameters) ephemPair.getPrivate();
            ECPublicKeyParameters ephemPub = (ECPublicKeyParameters) ephemPair.getPublic();
            ECPoint rbPoint = ephemPub.getQ();

            byte[] rbPrivBytes = bigIntegerTo32Bytes(ephemPriv.getD());
            BigInteger rB = new BigInteger(1, rbPrivBytes);
            byte[] rbEncoded = rbPoint.getEncoded(false);

            try {
                // 5. 计算 ZB（服务端身份摘要）
                byte[] serverIdBytes = serverId.getBytes(StandardCharsets.UTF_8);
                BigInteger dB = new BigInteger(1, serverPrivKey);
                ECPoint qB = new FixedPointCombMultiplier().multiply(G_POINT, dB).normalize();
                byte[] xB = bigIntegerTo32Bytes(qB.getAffineXCoord().toBigInteger());
                byte[] yB = bigIntegerTo32Bytes(qB.getAffineYCoord().toBigInteger());
                byte[] zbBytes = computeZ(serverIdBytes, xB, yB);

                // 6. 计算共享密钥
                ECPoint paPoint = CURVE.decodePoint(clientPubKey); // 对方静态公钥点（客户端 PA）
                HandshakeResult result = computeSharedKey(
                        dB,             // 本方静态私钥 (dB)
                        rB,             // 本方临时私钥 (rB)
                        rbPoint,        // 本方临时公钥点 (RB)，用于计算 x̄B
                        paPoint,        // 对方静态公钥点 (PA)
                        raPoint,        // 对方临时公钥点 (RA)
                        zaBytes,        // ZA
                        zbBytes         // ZB
                );
                result.setRA(raEncoded);
                result.setRB(rbEncoded);

                // 7. 生成 sessionId
                String sessionId = UUID.randomUUID().toString().replace("-", "");
                result.setSessionId(sessionId);

                // 8. 计算 SB = SM3(0x02 || y1 || SM3(x1 || ZA || ZB || RA || RB))
                byte[] innerHashInput = concat(this.currentX1Bytes, zaBytes, zbBytes,
                        raEncoded, rbEncoded);
                this.currentConfirmationValue = computeConfirmation(
                        (byte) 0x02, this.currentY1Bytes, innerHashInput);

                return result;

            } finally {
                MemoryCleanUtil.cleanKey(rbPrivBytes);
            }

        } catch (Sm2SdkException e) {
            throw e;
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.CLIENT_CERT_VERIFY_FAILED,
                    "处理客户端初始请求失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean verifyConfirm(HandshakeResult result, HandshakeConfirm confirm)
            throws Sm2SdkException {
        try {
            byte[] raBytes = result.getRA();
            byte[] rbBytes = result.getRB();
            byte[] zaBytes = Base64.getDecoder().decode(result.getZA());
            byte[] zbBytes = Base64.getDecoder().decode(result.getZB());

            // 使用存储的共享点坐标（在 processClientInit 中已计算）
            if (this.currentX1Bytes == null || this.currentY1Bytes == null) {
                throw new Sm2SdkException(ErrorCode.CLIENT_CERT_VERIFY_FAILED,
                        "未找到共享密钥点坐标，请先调用 processClientInit");
            }

            // 计算期望的 SA
            byte[] innerHashInput = concat(this.currentX1Bytes, zaBytes, zbBytes,
                    raBytes, rbBytes);
            byte[] saExpected = computeConfirmation((byte) 0x03, this.currentY1Bytes, innerHashInput);
            byte[] saReceived = Base64.getDecoder().decode(confirm.getConfirmation());

            return Arrays.equals(saExpected, saReceived);

        } catch (Sm2SdkException e) {
            throw e;
        } catch (Exception e) {
            throw new Sm2SdkException(ErrorCode.KEY_CONFIRM_FAILED_SA,
                    "验证确认消息失败: " + e.getMessage(), e);
        }
    }

    // ==================== 额外公开方法 ====================

    /**
     * 获取当前握手的服务端确认值（SB），由 {@link #processClientInit} 计算。
     *
     * <p>调用者使用此值构造 {@link HandshakeServerResp} 的确认字段。
     *
     * @return SB 字节数组；若尚未调用 processClientInit 则返回 {@code null}
     */
    public byte[] getCurrentConfirmationValue() {
        return this.currentConfirmationValue;
    }
}
