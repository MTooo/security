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
 * <p>在项目根目录执行：
 * <pre>{@code
 *   mvn exec:java -pl core -Dexec.mainClass="com.sm2sdk.core.util.Sm2KeyGen" -Dexec.args="2"
 * }</pre>
 *
 * <p>输出可直接填入 {@code application.yml} 的 {@code sm2.sdk.sm2-private-key}
 * 和 {@code sm2.sdk.sm2-public-key}。
 */
public class Sm2KeyGen {

    private static final X9ECParameters CURVE = GMNamedCurves.getByName("sm2p256v1");

    public static void main(String[] args) {
        int count = 1;
        if (args.length > 0) {
            count = Integer.parseInt(args[0]);
        }

        ECDomainParameters domain = new ECDomainParameters(
                CURVE.getCurve(), CURVE.getG(), CURVE.getN(), CURVE.getH());

        for (int i = 1; i <= count; i++) {
            if (count > 1) System.out.println("========== 密钥对 #" + i + " ==========");

            ECKeyPairGenerator gen = new ECKeyPairGenerator();
            gen.init(new ECKeyGenerationParameters(domain, new SecureRandom()));
            AsymmetricCipherKeyPair pair = gen.generateKeyPair();

            // 私钥: 32 字节 → 64 hex
            byte[] priv = bigIntToFixedBytes(
                    ((ECPrivateKeyParameters) pair.getPrivate()).getD(), 32);
            System.out.println("sm2-private-key: " + Hex.toHexString(priv));

            // 公钥: 04 || x || y，共 65 字节 → 130 hex
            ECPublicKeyParameters pub = (ECPublicKeyParameters) pair.getPublic();
            byte[] x = bigIntToFixedBytes(pub.getQ().getAffineXCoord().toBigInteger(), 32);
            byte[] y = bigIntToFixedBytes(pub.getQ().getAffineYCoord().toBigInteger(), 32);
            System.out.println("sm2-public-key:  " +
                    "04" + Hex.toHexString(x) + Hex.toHexString(y));
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
