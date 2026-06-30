package com.sm2sdk.core.nonce;

import cn.hutool.bloomfilter.BloomFilter;
import cn.hutool.bloomfilter.BloomFilterUtil;
import com.sm2sdk.core.exception.ErrorCode;
import com.sm2sdk.core.exception.Sm2SdkException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Nonce 重放防护校验器，采用两级防重放架构：
 *
 * <p><b>第一级：</b>内存 Bloom Filter（每分钟一个，保留最近5个），使用 cn.hutool.bloomfilter.BloomFilter。
 * Bloom Filter 提供快速的概率性判断，若 nonce "确定不存在"则直接放行，避免不必要的 Redis 调用。
 *
 * <p><b>第二级：</b>Redis SETNX 精确校验（可选，当 Redis 可用时）。
 * 当 Bloom 报告 nonce "可能存在"时，通过 Redis SETNX 命令进行原子性的精确校验。
 * Redis SETNX 成功（返回 true）说明是新 nonce；失败（返回 false）说明已被使用，确认为重放攻击。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 无 Redis 模式（仅 Bloom Filter 概率性校验）
 * NonceValidator validator = new NonceValidator();
 *
 * // 有 Redis 模式（Bloom Filter + Redis 精确校验）
 * NonceValidator validator = new NonceValidator((key, value, ttl) ->
 *     redisTemplate.opsForValue().setIfAbsent(key, value, Duration.ofSeconds(ttl)));
 *
 * if (validator.isDuplicate(nonce)) {
 *     throw new Sm2SdkException(ErrorCode.NONCE_REPLAY);
 * }
 * validator.markUsed(nonce);
 * }</pre>
 *
 * <p><b>线程安全：</b>Bloom Filter 的并发 add/contains 操作存在极低的位丢失可能（导致假阴性增加），
 * 但不会产生数据损坏。Redis 层级提供原子性精确校验，确保在有 Redis 的场景下防重放语义严格正确。
 */
public class NonceValidator {

    private static final Logger log = LoggerFactory.getLogger(NonceValidator.class);

    /** Bloom Filter 默认每哈希函数位数，约 15M 位，5 个哈希函数合计约 75M 位（~9MB） */
    static final int DEFAULT_BLOOM_M = 15_000_000;

    /** Bloom Filter 默认期望元素数量 */
    static final int DEFAULT_BLOOM_N = 1_000_000;

    /** Bloom Filter 默认哈希函数数量 */
    static final int DEFAULT_BLOOM_K = 5;

    /** 活跃 Bloom Filter 保留数量（最近 5 个分钟槽） */
    static final int ACTIVE_FILTER_WINDOW = 5;

    /** 一分钟的毫秒数 */
    private static final long MINUTE_MS = 60_000L;

    /** Redis key 前缀模板 */
    private static final String REDIS_KEY_PREFIX = "sm2:nonce:%s";

    /** Redis key 过期时间（秒），默认 5 分钟 */
    private static final int REDIS_KEY_EXPIRE_SECONDS = 300;

    /** 每分钟一个 Bloom Filter，key 为分钟时间戳（epochMs / 60000） */
    private final ConcurrentHashMap<Long, BloomFilter> filters = new ConcurrentHashMap<>();

    /** Redis 操作接口（可选，为 null 时仅使用 Bloom Filter） */
    private final RedisOperations redisOps;

    /** Bloom Filter 配置参数：每哈希函数位数 */
    private final int bloomM;

    /** Bloom Filter 配置参数：期望元素数量 */
    private final int bloomN;

    /** Bloom Filter 配置参数：哈希函数数量 */
    private final int bloomK;

    /**
     * Redis 操作函数式接口。
     *
     * <p>对应 Redis SETNX 命令语义：仅当 key 不存在时设置值并设置过期时间。
     * 调用方可根据实际使用的 Redis 客户端（Lettuce / Jedis / Redisson / Spring Data Redis）提供适配实现。
     */
    @FunctionalInterface
    public interface RedisOperations {

        /**
         * 执行 SETNX 操作，当 key 不存在时设置值并指定过期时间。
         *
         * @param key           Redis key
         * @param value         值
         * @param expireSeconds 过期时间（秒）
         * @return true 表示设置成功（key 原本不存在），false 表示 key 已存在
         */
        boolean setIfAbsent(String key, String value, int expireSeconds);
    }

    /**
     * 构造 NonceValidator，不使用 Redis，使用默认 Bloom Filter 参数。
     */
    public NonceValidator() {
        this(null, DEFAULT_BLOOM_M, DEFAULT_BLOOM_N, DEFAULT_BLOOM_K);
    }

    /**
     * 构造 NonceValidator，使用默认 Bloom Filter 参数。
     *
     * @param redisOps Redis 操作接口，可为 null（不使用 Redis 精确校验，仅依靠 Bloom Filter）
     */
    public NonceValidator(RedisOperations redisOps) {
        this(redisOps, DEFAULT_BLOOM_M, DEFAULT_BLOOM_N, DEFAULT_BLOOM_K);
    }

    /**
     * 构造 NonceValidator，可自定义 Bloom Filter 参数。
     *
     * @param redisOps Redis 操作接口，可为 null
     * @param bloomM   Bloom Filter 每哈希函数位数
     * @param bloomN   Bloom Filter 期望元素数量
     * @param bloomK   Bloom Filter 哈希函数数量
     */
    public NonceValidator(RedisOperations redisOps, int bloomM, int bloomN, int bloomK) {
        this.redisOps = redisOps;
        this.bloomM = bloomM;
        this.bloomN = bloomN;
        this.bloomK = bloomK;
    }

    /**
     * 检查 nonce 是否重复。
     *
     * <p>校验逻辑：
     * <ol>
     *   <li>遍历所有活跃（最近 5 分钟）的 Bloom Filter</li>
     *   <li>若所有 Bloom Filter 均返回 "确定不存在" → 返回 {@code false}（非重复，放行）</li>
     *   <li>若有任一 Bloom Filter 报告 "可能存在"：
     *     <ul>
     *       <li>Redis 可用 → 执行 SETNX 精确校验：
     *         <ul>
     *           <li>{@code true}（新 key）→ 返回 {@code false}（Bloom 误判，实际非重复）</li>
     *           <li>{@code false}（key 已存在）→ 抛出 {@link Sm2SdkException}({@link ErrorCode#NONCE_REPLAY})</li>
     *         </ul>
     *       </li>
     *       <li>Redis 不可用 → 返回 {@code true}（安全兜底，视为重复）</li>
     *     </ul>
     *   </li>
     * </ol>
     *
     * @param nonce 待校验的 nonce 字符串
     * @return true 表示重复（应拒绝请求），false 表示未重复（可继续处理）
     * @throws Sm2SdkException 当 Redis 确认重复时抛出 {@link ErrorCode#NONCE_REPLAY}
     */
    public boolean isDuplicate(String nonce) {
        // 步骤1：检查所有活跃 Bloom Filter
        boolean possiblyExists = checkBloomFilters(nonce);

        if (!possiblyExists) {
            // Bloom 确定不存在 → 非重复，直接放行（快速路径）
            return false;
        }

        // 步骤2：Bloom 报告可能存在，若 Redis 可用则使用 SETNX 精确校验
        if (redisOps != null) {
            String redisKey = String.format(REDIS_KEY_PREFIX, nonce);
            boolean isNew = redisOps.setIfAbsent(redisKey, "1", REDIS_KEY_EXPIRE_SECONDS);
            if (isNew) {
                // SETNX 成功，说明是新 nonce（Bloom 误判），放行
                return false;
            }
            // SETNX 失败，说明该 nonce 已被使用 → 确认为重放攻击
            log.warn("检测到 Nonce 重复攻击，已被 Redis 拦截: {}", nonce);
            throw new Sm2SdkException(ErrorCode.NONCE_REPLAY, "Nonce 重复，已被使用: " + nonce);
        }

        // 步骤3：Redis 不可用，Bloom 报告可能存在，安全起见视为重复
        log.warn("Redis 不可用，Bloom Filter 判定 Nonce 可能重复: {}", nonce);
        return true;
    }

    /**
     * 记录 nonce 已使用，将其添加到当前分钟对应的 Bloom Filter 中。
     *
     * <p>应在 {@link #isDuplicate(String)} 返回 {@code false} 后调用。
     * 若当前分钟的 Bloom Filter 尚不存在，则自动创建。
     *
     * @param nonce 已使用的 nonce，将被添加到 Bloom Filter
     */
    public void markUsed(String nonce) {
        long minuteSlot = getCurrentMinuteSlot();
        BloomFilter filter = filters.computeIfAbsent(minuteSlot, this::createBloomFilter);
        filter.add(nonce);
    }

    /**
     * 清理超过 5 分钟的 Bloom Filter，释放内存。
     *
     * <p>应由外部定时任务定期调用（如每分钟执行一次），以避免内存持续增长。
     * 仅保留最近 {@link #ACTIVE_FILTER_WINDOW} 个分钟槽的过滤器。
     */
    public void cleanupExpiredFilters() {
        long currentMinute = getCurrentMinuteSlot();
        long cutoff = currentMinute - ACTIVE_FILTER_WINDOW + 1;

        int before = filters.size();
        filters.keySet().removeIf(slot -> slot < cutoff);
        int after = filters.size();

        if (before != after) {
            log.debug("清理过期 Bloom Filter: 移除 {} 个, 剩余 {} 个", before - after, after);
        }
    }

    /**
     * 检查所有活跃（最近 5 分钟）Bloom Filter 中是否可能存在指定 nonce。
     *
     * <p>遍历当前分钟以及前 {@link #ACTIVE_FILTER_WINDOW} - 1 个分钟槽的过滤器，
     * 若有任一过滤器返回 "可能存在" 则返回 {@code true}。
     *
     * @param nonce nonce 值
     * @return true 表示可能存在（存在误判概率），false 表示一定不存在
     */
    private boolean checkBloomFilters(String nonce) {
        long currentMinute = getCurrentMinuteSlot();
        long startSlot = currentMinute - ACTIVE_FILTER_WINDOW + 1;

        for (long slot = startSlot; slot <= currentMinute; slot++) {
            BloomFilter filter = filters.get(slot);
            if (filter != null && filter.contains(nonce)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取当前分钟槽编号（Unix 时间戳毫秒数 / 60000）。
     *
     * @return 当前分钟槽编号
     */
    long getCurrentMinuteSlot() {
        return System.currentTimeMillis() / MINUTE_MS;
    }

    /**
     * 获取内部过滤器映射（包级私有，仅用于测试和监控）。
     *
     * @return 分钟槽到 Bloom Filter 的映射
     */
    ConcurrentHashMap<Long, BloomFilter> getFilters() {
        return filters;
    }

    /**
     * 创建分钟槽对应的 Bloom Filter 实例。
     *
     * @param minuteSlot 分钟槽编号（仅用于预留，暂未使用）
     * @return 新创建的 BitSetBloomFilter 实例
     */
    private BloomFilter createBloomFilter(long minuteSlot) {
        return BloomFilterUtil.createBitSet(bloomM, bloomN, bloomK);
    }
}
