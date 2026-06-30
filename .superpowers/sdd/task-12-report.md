# Task 12: NonceValidator 实现报告

## 完成内容

### 创建的文件

1. **`D:/workspace/security/sm2-sdk/core/src/main/java/com/sm2sdk/core/nonce/NonceValidator.java`**
   - 两级防重放架构实现
   - 第一级：内存 Bloom Filter（每分钟一个，保留最近5个），使用 Hutool `BitSetBloomFilter`
   - 第二级：Redis SETNX 精确校验（可选，通过 `RedisOperations` 函数式接口注入）
   - 所有 Javadoc 和注释均为中文

2. **`D:/workspace/security/sm2-sdk/core/src/test/java/com/sm2sdk/core/nonce/NonceValidatorTest.java`**
   - 11 个测试用例，覆盖全部功能点

### 核心接口

```java
public class NonceValidator {
    @FunctionalInterface
    public interface RedisOperations {
        boolean setIfAbsent(String key, String value, int expireSeconds);
    }

    boolean isDuplicate(String nonce);    // 检查 nonce 是否重复
    void markUsed(String nonce);          // 记录 nonce 已使用
    void cleanupExpiredFilters();         // 清理过期 Bloom Filter
}
```

### isDuplicate 逻辑

1. Bloom Filter 检查：若"确定不存在"→ 返回 false（放行）
2. 若 Bloom 报告"可能存在"且 Redis 可用 → Redis SETNX 精确校验
   - SETNX 返回 true → 新 nonce（Bloom 误判），返回 false
   - SETNX 返回 false → 重复，抛出 `Sm2SdkException(NONCE_REPLAY)`
3. Redis 不可用时，安全兜底：返回 true

## 测试结果

- **128 个测试全部通过**（11 个新增 + 117 个现有）
- 新增测试覆盖：
  - 新 nonce 校验（3 个）
  - Bloom Filter 重复检测（2 个）
  - Redis 模式重复检测（3 个）
  - Bloom Filter 清理（3 个）
  - 并发校验（2 个，含 Redis AtomicBoolean 模拟）

## 关键设计决策

- **Bloom Filter 实现**：使用 `cn.hutool.bloomfilter.BitSetBloomFilter`（通过 `BloomFilterUtil.createBitSet` 创建），默认参数 m=15_000_000, n=1_000_000, k=5，约 9MB
- **Redis 可选性**：通过 `RedisOperations` 函数式接口实现，非必须依赖，调用方提供适配实现
- **线程安全**：`ConcurrentHashMap` 管理分钟槽过滤器，Bloom Filter 并发操作接受极低概率的位丢失
- **清理机制**：`cleanupExpiredFilters()` 由外部定时任务调用，移除超过 5 分钟的过滤器
