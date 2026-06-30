# Task 13 Report: Utility Classes

## Summary

All three utility classes created/enhanced and tested. 32/32 tests pass.

## Files Created

### Source Files

1. **`D:/workspace/security/sm2-sdk/core/src/main/java/com/sm2sdk/core/util/SecureRandomUtil.java`**
   - `generateNonce(int bytes)` — 指定长度的安全随机字节数组
   - `generateNonce()` — 默认 16 字节安全随机字节数组
   - `generateIV()` — 12 字节初始化向量（适用于 GCM 模式）
   - `generateUUID()` — 标准 36 字符 UUID 字符串
   - 底层使用 `SecureRandom`，优先 `NativePRNG`，回退 `SHA1PRNG`，兜底 `new SecureRandom()`

2. **`D:/workspace/security/sm2-sdk/core/src/main/java/com/sm2sdk/core/util/Sm2KeyPrefix.java`**
   - `buildSessionKey(prefix, sessionId)` → `{prefix}:session:{sessionId}`
   - `buildNonceKey(prefix, nonce)` → `{prefix}:nonce:{nonce}`
   - `buildBloomKey(prefix, minuteTick)` → `{prefix}:nonce:bf:{minuteTick}`
   - `buildHandshakeLockKey(prefix, clientId)` → `{prefix}:handshake:lock:{clientId}`

### Enhanced File

3. **`D:/workspace/security/sm2-sdk/core/src/main/java/com/sm2sdk/core/crypto/MemoryCleanUtil.java`** (增强)
   - 原有 `cleanKey(byte[])` 增加 `System.gc()` 最佳努力提示
   - 新增 `cleanKeys(byte[]...)` 批量清零方法

### Test Files

4. **`D:/workspace/security/sm2-sdk/core/src/test/java/com/sm2sdk/core/util/SecureRandomUtilTest.java`** — 16 tests
   - 长度验证 (16/32/8/0/1/1MB)
   - 非全零验证
   - 多次调用结果不同验证
   - UUID 格式/版本位验证
   - 边界及异常情况

5. **`D:/workspace/security/sm2-sdk/core/src/test/java/com/sm2sdk/core/util/Sm2KeyPrefixTest.java`** — 16 tests
   - 各方法格式正确性验证
   - 空值/null/特殊字符处理
   - 不同前缀隔离验证
   - 冒号分隔层级结构一致性验证

## Test Results

```
Tests run: 32, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
