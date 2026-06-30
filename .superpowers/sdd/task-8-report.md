# Task 8: HutoolSm4Crypto - 实施报告

## 文件

- **实现**: `sm2-sdk/core/src/main/java/com/sm2sdk/core/crypto/impl/HutoolSm4Crypto.java`
- **测试**: `sm2-sdk/core/src/test/java/com/sm2sdk/core/crypto/impl/HutoolSm4CryptoTest.java`

## 实现细节

### HutoolSm4Crypto
- 实现 `Sm4Crypto` 接口，使用 Hutool SM4 GCM 模式
- 使用 `new SM4("GCM", "NoPadding")` 字符串构造函数（因为 Hutool 5.8.32 的 `Mode` 枚举不包含 GCM）
- 通过 `sm4.getCipher()` 获取底层 `javax.crypto.Cipher`，手动初始化以支持 AAD
- 使用 `GCMParameterSpec(128, iv)` 设置 GCM 参数（128 位 = 16 字节 TAG）
- 使用 `cipher.updateAAD(aad)` 支持附加认证数据
- **encrypt**: iv 为 null 时生成 12 字节随机 IV，返回格式 `IV(12) || ciphertext || TAG(16)`
- **decrypt**: 从密文前 12 字节解析 IV（或使用传入的 iv），末 16 字节为 TAG，中间为 ciphertext
- 解密 TAG 校验失败时抛出 `Sm2SdkException(SM4_DECRYPT_TAG_FAILED, detail)`
- 每次调用创建独立 Cipher 实例，线程安全

### HutoolSm4CryptoTest
9 个测试用例，覆盖：
1. `testRoundtrip` — 基本加解密轮转（指定 IV）
2. `testRoundtripWithNullIv` — null IV 的加解密轮转
3. `testDecryptWithWrongKeyFails` — 错误密钥解密失败
4. `testDecryptWithWrongAadFails` — 错误 AAD 解密失败
5. `testEncryptionProducesDifferentResults` — 两次加密结果不同（随机 IV）
6. `testEmptyPlaintext` — 空明文加解密
7. `testLargePlaintext` — 10KB 大明文加解密
8. `testOutputFormat` — 验证输出格式 `IV(12) || ciphertext || TAG(16)`
9. `testNullAad` — null AAD 加解密

## 测试结果

```
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
```

所有 9 个测试通过，不影响其他 55 个已有测试。
