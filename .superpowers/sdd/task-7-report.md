# Task 7: HutoolSm2KeyExchange - 实现报告

## 实现文件

- **主实现**: `D:/workspace/security/sm2-sdk/core/src/main/java/com/sm2sdk/core/crypto/impl/HutoolSm2KeyExchange.java`
- **测试**: `D:/workspace/security/sm2-sdk/core/src/test/java/com/sm2sdk/core/crypto/impl/HutoolSm2KeyExchangeTest.java`
- **工具类**: `D:/workspace/security/sm2-sdk/core/src/main/java/com/sm2sdk/core/crypto/MemoryCleanUtil.java`

## 实现说明

### HutoolSm2KeyExchange

实现 `Sm2KeyExchange` 接口，遵循 GB/T 32918.3-2016 第6章 SM2 密钥交换协议。

**核心技术选择:**
- 使用 BouncyCastle `SM2Signer` 直接进行签名和验签（避免 Hutool 密钥编码格式问题）
- 使用 BouncyCastle `ECKeyPairGenerator` 生成临时密钥对
- 使用 BouncyCastle `ECPoint` 进行椭圆曲线点运算
- 使用 Hutool `SmUtil.sm3()` 进行 SM3 哈希（以 `digest(byte[])` 方式调用，因 Hutool 5.8.32 的 SM3 不支持流式 `update`）
- 使用 `KeyDerivation.kdf()` 进行密钥派生

**共享点计算公式（修正后的正确公式）:**

按照 GM/T 0003.3-2012 / GB/T 32918.3-2016 Section 6.1:
- `t = ourStaticPriv + x̄_our * ourEphemeralPriv (mod n)`
- `U = t * (theirStaticPub + x̄_their * theirEphemeralPub)`
- `x̄ = 2^127 + (x_R & (2^127 - 1))`

关键点: 使用 `t = d + x̄ * r`（静态私钥参与运算）而非仅使用临时私钥 `r`，并且点运算公式为 `P_static + x̄ * R_ephemeral`（先静态后临时）。

**实例状态（非线程安全）：**
- `ephemeralPrivKeyBytes`: 客户端临时私钥，由 `buildInitRequest` 存储，在 `processServerResponse` 中使用后清除
- `currentX1Bytes/currentY1Bytes`: 共享点坐标，用于后续确认计算
- `currentConfirmationValue`: SB 确认值（服务端），通过 `getCurrentConfirmationValue()` 获取

## 测试覆盖

| 测试方法 | 场景 | 验证 |
|---------|------|------|
| `testFullHandshakeRoundtrip` | 完整握手轮转 | 双方 SM4 密钥和 IV 完全一致 |
| `testSignatureVerificationFailure` | 签名验证失败 | 抛出 `SIGNATURE_VERIFY_FAILED` |
| `testTimestampDeviationExceeded` | 时间戳超限 | 抛出 `TIMESTAMP_DEVIATION_EXCEEDED` |
| `testPointNotOnCurve` | 临时公钥不在曲线上 | 抛出 `SERVER_TEMP_PUBKEY_NOT_ON_CURVE` |

## 测试结果

全部 108 个测试通过（core 模块全量测试）：

```
Tests run: 108, Failures: 0, Errors: 0, Skipped: 0
```

其中 HutoolSm2KeyExchangeTest 4 个测试全部通过。

## 已知问题

- 实现不是线程安全的，每次密钥交换应使用独立的 `HutoolSm2KeyExchange` 实例
- 临时私钥通过 `MemoryCleanUtil.cleanKey()` 在 `finally` 块中清除
