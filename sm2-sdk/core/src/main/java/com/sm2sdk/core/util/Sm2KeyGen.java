package com.sm2sdk.core.util;

import org.bouncycastle.asn1.gm.GMNamedCurves;
import org.bouncycastle.asn1.x9.X9ECParameters;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.ECKeyPairGenerator;
import org.bouncycastle.crypto.params.ECDomainParameters;
import org.bouncycastle.crypto.params.ECKeyGenerationParameters;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ECPublicKeyParameters;
import org.bouncycastle.util.encoders.Hex;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * SM2 密钥对生成工具。
 *
 * <p>使用分发的 JAR 直接运行：
 * <pre>{@code
 *   # 方式一：使用核心 JAR（需要 bcprov 在 classpath 上）
 *   java -cp sm2-sdk-core-1.0.0.jar;bcprov-jdk18on-1.84.jar com.sm2sdk.core.util.Sm2KeyGen
 *
 *   # 方式二：使用 Spring Boot Starter JAR（已内置所有依赖）
 *   java -cp sm2-sdk-spring-boot-starter-1.0.0.jar com.sm2sdk.core.util.Sm2KeyGen 3
 * }</pre>
 *
 * <p>开发期间使用 Maven：
 * <pre>{@code
 *   mvn exec:java -pl core -Dexec.mainClass="com.sm2sdk.core.util.Sm2KeyGen" -Dexec.args="2"
 * }</pre>
 */
public class Sm2KeyGen {

    private static final X9ECParameters CURVE = GMNamedCurves.getByName("sm2p256v1");

    public static void main(String[] args) {
        int count = 1;
        if (args.length > 0) {
            try {
                count = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("用法: java ... Sm2KeyGen [数量]");
                System.exit(1);
            }
        }

        ECDomainParameters domain = new ECDomainParameters(
                CURVE.getCurve(), CURVE.getG(), CURVE.getN(), CURVE.getH());
        SecureRandom random = new SecureRandom();

        for (int i = 1; i <= count; i++) {
            ECKeyPairGenerator gen = new ECKeyPairGenerator();
            gen.init(new ECKeyGenerationParameters(domain, random));
            AsymmetricCipherKeyPair pair = gen.generateKeyPair();

            byte[] priv = bigIntToFixedBytes(
                    ((ECPrivateKeyParameters) pair.getPrivate()).getD(), 32);
            ECPublicKeyParameters pub = (ECPublicKeyParameters) pair.getPublic();
            byte[] x = bigIntToFixedBytes(
                    pub.getQ().getAffineXCoord().toBigInteger(), 32);
            byte[] y = bigIntToFixedBytes(
                    pub.getQ().getAffineYCoord().toBigInteger(), 32);

            if (count > 1) {
                System.out.println("===== 密钥对 #" + i + " =====");
            }
            System.out.println("sm2-private-key: " + Hex.toHexString(priv));
            System.out.println("sm2-public-key:  " + "04" + Hex.toHexString(x) + Hex.toHexString(y));
            System.out.println();
        }
    }

    private static byte[] bigIntToFixedBytes(BigInteger bi, int len) {
        byte[] data = bi.toByteArray();
        byte[] result = new byte[len];
        int srcPos = Math.max(0, data.length - len);
        int destPos = Math.max(0, len - data.length);
        int copyLen = Math.min(data.length, len);
        System.arraycopy(data, srcPos, result, destPos, copyLen);
        return result;
    }
}
