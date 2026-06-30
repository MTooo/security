# SM2 安全数据对接 SDK — 完整实现规格

> 基于 `数据对接方案.md` 设计思路，整合为自包含的 AI 可执行实现规格。
> **目标**：一个 JAR，兼容 JDK 8/11/17 + Spring Boot 2.x/3.x；高内聚、低耦合、少侵入。

---

## 一、总体原则与架构

### 1.1 核心原则

1. **一个 JAR，两种角色**：SDK 包含 Client（主动调用）和 Server（被动响应），通过配置开关 `client.enabled` / `server.enabled`
2. **运行时自适应 Servlet API**：同一个 JAR 编译时同时依赖 javax.servlet 和 jakarta.servlet（provided），`@ConditionalOnClass` 运行时自动选择正确的 Filter
3. **Hutool 包重定位**：maven-shade-plugin 将 `cn.hutool` → `com.sm2sdk.third.hutool`，不与业务方 Hutool 冲突
4. **核心零框架依赖**：sm2-sdk-core 不依赖 Spring/Servlet，可独立用于非 Spring 项目 / Android / CLI
5. **少侵入**：Client 多一个 Bean `Sm2HttpClient`；Server 多一个注解 `@Sm2Api` + Filter；不加注解的接口不受任何影响

### 1.2 模块划分

```
sm2-sdk/
├── sm2-sdk-core                 核心层：SM2协商 + SM4加解密 + 会话管理（零框架依赖）
├── sm2-sdk-client               客户端：Sm2HttpClient + Sm2Request 链式构建器
├── sm2-sdk-server               服务端：Filter(javax+jakarta) + @Sm2Api + 访问控制
└── sm2-sdk-spring-boot-starter 一键引入的 Starter

依赖方向（单向，高层依赖低层）：
  starter → client → core
  starter → server → core
  client 和 server 互不依赖
```

### 1.3 分层对应

| 层 | 模块 | 职责 |
|----|------|------|
| 业务应用层 | 业务方代码 | 注入 `Sm2HttpClient` 调用；加 `@Sm2Api` 暴露接口 |
| SDK 门面层 | client/server | `Sm2HttpClient.get()/post()`、`Sm2ServerFilter` |
| 会话管理层 | core/session | `SessionManager` 编排握手/加解密/缓存 |
| 密钥协商模块 | core/crypto | SM2 3次握手、KDF 派生 |
| 加解密模块 | core/crypto | SM4-GCM 加解密 |
| 网络通信层 | client/server | Hutool Httputil 发送、Servlet Filter 拦截 |

---

## 二、HTTP 协议规范（线格式 — SDK 必须实现的协议契约）

### 2.1 整体约定

| 项目 | 约定 |
|------|------|
| 传输协议 | HTTPS（必须） |
| 算法 | SM2 密钥协商（GB/T 32918.3）+ SM4-GCM |
| 编码 | 所有二进制字段 Base64 编码 |
| 字符编码 | UTF-8 |
| 时间戳 | Unix 毫秒时间戳 |

### 2.2 握手协议（3 次 HTTP 请求）

#### 第一步：客户端 → 服务端

```
POST /sm2/handshake/init
Content-Type: application/json

{
    "protocolVersion": "1.0",
    "clientId": "SYSTEM_A",
    "ephemeralPublicKey": "BASE64(RA)",          // 客户端临时公钥，64字节未压缩
    "timestamp": 1719676800000,
    "signature": "BASE64(SIGN(dA, RA || clientId || ZA || timestamp))"
}
```

#### 第二步：服务端 → 客户端

```
HTTP 200
Content-Type: application/json

{
    "sessionId": "uuid-string",
    "ephemeralPublicKey": "BASE64(RB)",          // 服务端临时公钥
    "confirmation": "BASE64(SB)"                 // 服务端确认参数
}
```

#### 第三步：客户端 → 服务端

```
POST /sm2/handshake/confirm
Content-Type: application/json

{
    "sessionId": "uuid-string",
    "confirmation": "BASE64(SA)"                 // 客户端确认参数
}

→ HTTP 200：握手成功；HTTP 4xx：握手失败
```

### 2.3 业务请求加密信封格式

**所有业务请求**（GET/POST/PUT/DELETE）共用同一套加密信封：

```
{HTTP方法} /api/xxx（路径与明文接口一致）
Content-Type: text/plain
X-Session-Id: {sessionId}
X-Timestamp: {unixMillis}
X-Nonce: {16字节随机数 Base64}

Body: BASE64( IV[12字节] || CIPHERTEXT || TAG[16字节] )
```

**各 HTTP 方法的 Body 明文格式**：

| HTTP 方法 | Body 明文格式 | 示例 |
|-----------|--------------|------|
| GET | Query 参数键值对 → JSON | `{"idCard":"xxx","page":1}` |
| GET（无参）| 空字符串 `""` | `""` |
| POST | 原始 JSON Body（对象/数组均可） | `{"orderId":"ORD001","amount":10000}` |
| POST（表单/文件）| 表单字段 + 文件 Base64 → JSON | `{"userName":"张三","attachmentData":"/9j/..."}` |
| POST（无参）| 空字符串 `""` | `""` |
| PUT | 原始 JSON Body | `{"phone":"139xxx"}` |
| DELETE | 删除条件 → JSON | `{"id":"REC001","reason":"误录入"}` |
| DELETE（无参）| 空字符串 `""` | `""` |

**统一原则**：Body 明文始终是合法 JSON 字符串或空串，加密后 `BASE64(IV || CIPHERTEXT || TAG)` 放入 Body，`Content-Type` 统一 `text/plain`。

### 2.4 业务响应格式

```
HTTP 200
Content-Type: text/plain
X-Session-Expired: false
X-Session-Renewed: {newSessionId}    // 可选，续期时返回

Body: BASE64( IV[12字节] || CIPHERTEXT || TAG[16字节] )
```

### 2.5 错误响应格式

```json
{
    "errorCode": "21103",
    "message": "服务端证书验签失败",
    "sessionId": null
}
```

### 2.6 密钥失效处理与自动恢复

这是 SDK 内部最关键的自动化流程：

#### 场景一：会话自然过期 → HTTP 401 + X-Session-Expired: true

```
客户端发出请求 → 服务端发现 session 不存在/过期
→ 返回 HTTP 401 + X-Session-Expired: true + {"errorCode":"22301","message":"会话不存在或已过期"}
→ 客户端 SDK 内部：① 自动重新握手（3次请求）→ 获得新 sessionId
→ ② 用新会话重试原请求（对业务方透明）
```

**注意**：401 响应 Body 是**明文 JSON**，不经过 SM4 加密。客户端通过 `X-Session-Expired: true` + `HTTP 401` 组合判断触发重握手。

#### 场景二：TAG 校验失败 → HTTP 400 + errorCode=21202

```
服务端解密时 TAG 校验不通过（密钥不一致/数据被篡改）
→ 返回 HTTP 400 + {"errorCode":"21202","message":"SM4解密失败 - TAG校验失败"}
→ 客户端 SDK 内部：① 清除本地会话缓存 → ② 重新握手 → ③ 用新会话重试原请求
```

#### 客户端重试策略汇总

| 服务端返回 | 含义 | SDK 行为 | 重试原请求 |
|-----------|------|---------|:---:|
| HTTP 401 + X-Session-Expired: true | 会话过期 | 重握手→重试 | ✅ |
| HTTP 400 + errorCode=21202 | TAG 失败 | 废弃会话→重握手→重试 | ✅ |
| HTTP 403 + Nonce 重复 | 重放攻击 | 新 Nonce 重试一次 | ✅(1次) |
| HTTP 400 + errorCode=21103 | 签名失败 | 检查密钥配置，**不重试** | ❌ |
| HTTP 200 + X-Session-Renewed | 会话续期 | 更新本地 sessionId | N/A |

### 2.7 POST 幂等性

POST（非幂等）请求自动在加密 Body 中注入 `_idempotencyKey` 字段（UUID），服务端缓存 `key → 响应结果`（TTL 5分钟）。相同幂等键返回首次处理结果 + `X-Idempotent-Replay: true` Header。

### 2.8 加密参数

| 参数 | 值 | 说明 |
|------|-----|------|
| SM4 密钥 | 16 字节 | KDF 派生 |
| SM4 模式 | GCM | |
| GCM IV | 12 字节 | 每次加密随机生成 |
| GCM TAG | 16 字节 | 自动校验 |
| AAD | `sessionId` 的 UTF-8 字节 | 加密解密必须一致 |

### 2.9 防重放

- 握手层：服务端校验 timestamp 偏差 ≤ 300 秒
- 业务层：Nonce 16 字节随机数，服务端记录，5 分钟内重复则拒绝（HTTP 403）

---

## 三、核心模块（sm2-sdk-core）详细规格

### 3.1 模块依赖

| 依赖 | 范围 | 说明 |
|------|------|------|
| Hutool 5.8.x | compile（shade 重定位） | SM2/SM3/SM4 + Httputil |
| Caffeine 2.9.x | compile | 本地会话缓存 |
| Jackson 2.15.x | compile（shade 重定位） | JSON |
| SLF4J | provided | 日志门面 |
| Servlet API (javax + jakarta) | provided | 仅 server 模块 |

### 3.2 核心接口定义

```java
// ========== 密钥协商接口（无状态，纯数学运算） ==========
public interface Sm2KeyExchange {

    // 握手第一步：生成客户端临时密钥对 + 签名
    HandshakeInit buildInitRequest(HandshakeContext ctx);
    // ctx 包含: clientId, clientPrivateKey, serverPublicKey, clientIdentity(Z)

    // 握手第二步：处理服务端响应，计算共享密钥
    HandshakeResult processServerResponse(
        HandshakeInit sentRequest,
        HandshakeServerResp serverResp);

    // 握手第三步：生成客户端确认参数
    HandshakeConfirm buildConfirm(HandshakeResult result);

    // 服务端校验客户端确认参数
    boolean verifyConfirm(HandshakeResult result, HandshakeConfirm confirm);

    // 服务端处理客户端发来的握手初始请求
    HandshakeResult processClientInit(
        HandshakeInit clientInit,
        String serverPrivateKey,
        String clientPublicKey);
}

// ========== 加解密接口（无状态） ==========
public interface Sm4Crypto {
    // 加密：返回 ciphertext + tag (GCM 模式下 tag 已拼接在末尾)
    byte[] encrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext);
    // 解密：传入 ciphertext+tag，解密并校验 tag，失败抛 Sm2SdkException(21202)
    byte[] decrypt(byte[] key, byte[] iv, byte[] aad, byte[] ciphertextWithTag);
}

// ========== 会话存储接口 ==========
public interface SessionStore {
    Session  get(String sessionId);
    void     put(Session session);
    void     remove(String sessionId);
    boolean  exists(String sessionId);
    // 续期：更新时间 + 重置请求计数
    void     renew(String sessionId);
}

// ========== 会话编排类 ==========
public class SessionManager {
    // 主动握手（作为客户端）
    public Session initiateHandshake(String peerId);
    // 被动握手（作为服务端，处理对方发来的请求）
    public Session handleIncomingHandshake(HandshakeInit init);
    // 获取会话（自动检查过期）
    public Session getSession(String sessionId);
    // 续期（含密钥更新）
    public Session renewSession(String sessionId);
    // 加密业务数据（自动从 Session 取 key）
    public String encryptBody(String sessionId, String plainJson);
    // 解密业务数据（自动从 Session 取 key）
    public String decryptBody(String sessionId, String encryptedBase64);
}
```

### 3.3 Session 对象结构

```java
public class Session {
    private String sessionId;          // UUID
    private String clientId;           // 客户端标识（握手时认证）
    private String peerId;             // 对方标识
    private byte[] sm4Key;             // SM4 会话密钥（16字节）—— 仅内存
    private long createTime;           // 创建时间（毫秒时间戳）
    private long lastAccessTime;       // 最后使用时间
    private long maxLifetime;          // 总生命周期上限（毫秒）
    private int maxRequests;           // 最大请求次数
    private int requestCount;          // 已使用请求次数
    private int rekeyVersion;          // Rekey 版本号（续期时递增）
    // sm4Key 使用后立即清零：用完即 Arrays.fill(key, (byte)0)
}
```

### 3.4 SM2 密钥协商算法实现要点（GB/T 32918.3-2016 第 6 章）

**HutoolSm2KeyExchange 必须实现以下数学步骤**：

```
第1步（客户端）:
  - 生成临时密钥对 (rA, RA)，RA = [rA]G
  - 计算 ZA = SM3(ENTLA || IDA || a || b || xG || yG || xA || yA)
  - 签名: SIGN(dA, RA || clientId || ZA || timestamp)
  - 发送: {protocolVersion, clientId, RA(Base64), timestamp, signature(Base64)}

第2步（服务端）:
  - 验证时间戳 |now - timestamp| ≤ 300s
  - 用对方公钥 PA 验签
  - 验证 RA 在 SM2 曲线上
  - 生成临时密钥对 (rB, RB)，RB = [rB]G
  - 计算 ZB = SM3(ENTLB || IDB || a || b || xG || yG || xB || yB)
  - 计算共享密钥点: (x1, y1) = [rB] * (RA + [x̄A] * PA)
    其中 x̄A = 2^w + (xRA & (2^w - 1))，w = 127
  - KDF 派生: K = KDF(x1 || ZA || ZB, klen)
    → SM4 密钥(16B) || SM4 IV(12B) || HMAC-SM3 密钥(32B 可选)
  - 生成 SB = SM3(0x02 || y1 || SM3(x1 || ZA || ZB || RA || RB))
  - 返回: {sessionId, RB(Base64), SB(Base64)}

第3步（客户端）:
  - 验证 RB 在曲线上
  - 计算共享密钥点: (x1, y1) = [rA] * (RB + [x̄B] * PB)
  - KDF 派生: K = KDF(x1 || ZA || ZB, klen)
  - 验证 SB: 核对自己计算的 SB' == SB
  - 生成 SA = SM3(0x03 || y1 || SM3(x1 || ZA || ZB || RA || RB))
  - 发送: {sessionId, SA(Base64)} → 服务端验证 SA 后握手完成
```

### 3.5 KDF 密钥派生规范（GB/T 32918.4-2016 第 5 章）

```
输入: Z = x1 || ZA || ZB, klen = 要派生的密钥比特长度
算法:
  1. ct = 0x00000001
  2. for i = 1 to ceil(klen/256):
       Ha[i] = SM3(Z || ct)
       ct++
  3. 若有余数，最后一个 Ha[i] 取最左 klen - 256*floor(klen/256) 位
  4. 输出: Ha[1] || Ha[2] || ... 的前 klen 位

派生输出映射（klen = 480 位 = 60 字节）:
  bytes[0..15]   = SM4 会话密钥 (16字节)
  bytes[16..27]  = SM4 初始 IV (12字节)
  bytes[28..59]  = HMAC-SM3 密钥 (32字节，可选)
```

### 3.6 SM4-GCM 加解密实现要点

```java
// 加密
public byte[] encrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext) {
    // 1. 生成 12 字节随机 IV（每次加密必须不同）
    // 2. 用 Hutool SM4 GCM 模式加密
    // 3. 输出格式: IV[12] || CIPHERTEXT || TAG[16]
    // 4. 返回 Base64 编码的结果
}

// 解密
public byte[] decrypt(byte[] key, byte[] iv, byte[] aad, byte[] ciphertextWithTag) {
    // 1. 从 ciphertextWithTag 分离: IV[12] || CIPHERTEXT || TAG[16]
    // 2. 用 Hutool SM4 GCM 解密 + TAG 校验
    // 3. TAG 校验失败 → throw new Sm2SdkException("21202", "TAG校验失败")
    // 4. 返回明文
}
```

### 3.7 会话生命周期管理

```
会话创建时间: T_create
最后使用时间: T_last
当前请求次数: count
最大请求次数: max_count (默认 1000)
会话有效期: timeout (默认 1800 秒 = 30 分钟)
总生命周期: maxLifetime (默认 7200 秒 = 2 小时)
续期阈值: renewThreshold (默认 300 秒 = 5 分钟)

过期判断（任一条件满足即过期）:
  1. now - T_last > timeout (空闲超时)
  2. count >= max_count (次数超限)
  3. now - T_create > maxLifetime (总生命周期超限，禁止续期)

续期条件:
  now - T_last < timeout (未过期)
  maxLifetime - (now - T_create) < renewThreshold (剩余寿命 < 阈值)
  → 自动续期：延长 T_last，重置 count，rekeyVersion++，通过 KDF(offset=rekeyVersion) 派生新密钥

总生命周期上限（无论续期多少次）:
  单个会话从创建到强制失效 ≤ maxLifetime，达到后强制重新握手
```

### 3.8 Nonce 防重放：Bloom Filter + Redis 两级架构

```
第一级 — 内存 Bloom Filter:
  - 每分钟一个 Filter，保留最近 5 分钟
  - 新 Nonce → Bloom Filter 检查：
    - 报告"不存在" → 直接放行
    - 报告"可能存在" → 进入第二级

第二级 — Redis 精确校验:
  - Redis SETNX sm2:nonce:{nonce} 1 EX 300
  - SETNX 返回 0 → Nonce 重复 → 拒绝请求 (HTTP 403)
  - SETNX 返回 1 → 新 Nonce，放行

内存估算（10,000 QPS）:
  - 5 分钟 × 60 万 Nonce，0.1% 误判率 → ~9MB/Filter × 5 = ~45MB JVM 堆
  - Redis 仅存储 Bloom 误判穿透的 ~3000 条，而非 300 万条
```

---

## 四、客户端模块（sm2-sdk-client）详细规格

### 4.1 对外 API

```java
public class Sm2HttpClient {
    public Sm2Request get(String path);
    public Sm2Request post(String path);
    public Sm2Request put(String path);
    public Sm2Request delete(String path);
}

public class Sm2Request {
    public Sm2Request param(String key, String value);    // GET/DELETE 查询参数
    public Sm2Request header(String key, String value);   // 自定义 Header
    public Sm2Request body(Object jsonBody);               // POST/PUT JSON Body
    public <T> T execute(Class<T> responseType);           // 执行并返回类型
}
```

### 4.2 execute() 内部执行链

```
execute(responseType):
  1. 从 SessionStore 获取当前 peerId 的会话
     ├─ 无会话 → 自动 SM2 握手（3次请求，见2.2）
     │   ├─ 握手成功 → 缓存 Session
     │   └─ 握手失败 → 重试（指数退避1s/2s/4s）→ 仍失败 → 检查熔断器
  2. 检查会话是否需续期（剩余寿命 < renewThreshold）
     ├─ 是 → 后台异步续期（不阻塞本次请求）
     └─ 否 → 继续
  3. 根据 HTTP 方法构建明文字符串
     ├─ GET/DELETE: params → JSON string（无参则 ""）
     ├─ POST/PUT: body → JSON string（无 body 则 ""）
     └─ POST 自动注入 _idempotencyKey(UUID) 到 JSON
  4. Sm4Crypto.encrypt(会话密钥, 随机IV, sessionId作为AAD, 明文)
     → Base64(IV || CIPHERTEXT || TAG)
  5. 组装 HTTP 请求
     ├─ 方法: 保持原方法(GET/POST/PUT/DELETE)
     ├─ URL: {serverUrl}/{path}
     ├─ Headers: X-Session-Id / X-Timestamp / X-Nonce
     ├─ Body: Base64 密文
     └─ Content-Type: text/plain
  6. Hutool Httputil 发送 → 获取响应
  7. 检查响应状态:
     ├─ HTTP 200 → 继续步骤8
     ├─ HTTP 401 + X-Session-Expired → 清除会话 → 重握手 → 跳回步骤1重试(最多1次)
     ├─ HTTP 400 + errorCode=21202 → 清除会话 → 重握手 → 跳回步骤1重试(最多1次)
     └─ 其他错误 → 抛异常给业务方
  8. Sm4Crypto.decrypt(响应 Body) → 明文 JSON
  9. JSON 反序列化为 responseType → 返回
```

### 4.3 熔断保护

```
状态机: CLOSED → OPEN → HALF_OPEN → CLOSED

- CLOSED: 正常状态
  - 连续握手失败 5 次 → OPEN
- OPEN: 熔断
  - 直接返回 Sm2SdkException(19003, "握手熔断，请稍后重试")
  - 30 秒后 → HALF_OPEN
- HALF_OPEN: 试探
  - 允许 1 次握手尝试
  - 成功 → CLOSED
  - 失败 → OPEN（重新计时 30 秒）
```

---

## 五、服务端模块（sm2-sdk-server）详细规格

### 5.1 @Sm2Api 注解

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sm2Api {
    String value() default "";
}
```

使用规则：
- 标记在方法上 → 仅该方法走 SM2 加解密
- 标记在类上 → 该 Controller 所有方法走 SM2
- 不加注解 → Filter 直接放行，完全不受影响

### 5.2 Filter 执行流程（Sm2ServerFilterLogic）

```
请求进入 Filter:
  1. 根据请求 URL + Method 查找 Spring HandlerMethod
     ├─ 找不着（非 Controller） → doFilter 放行
     └─ 找到了 → 继续步骤2
  2. 检查方法/类是否有 @Sm2Api
     ├─ 无注解 → doFilter 放行（明文直通）
     └─ 有注解 → 继续步骤3
  3. 提取 HTTP Headers:
     ├─ X-Session-Id (必填，否则 401)
     ├─ X-Timestamp (必填，否则 400)
     └─ X-Nonce (必填，否则 400)
  4. 校验 X-Timestamp: |now - timestamp| ≤ tolerance(300s)，否则 400
  5. Nonce 防重放检查（Bloom Filter + Redis，见3.8）
     ├─ 重复 → 403
     └─ 通过 → 继续步骤6
  6. 从 SessionStore 获取 Session:
     ├─ 不存在 → 401 + X-Session-Expired: true
     └─ 存在 → 继续步骤7
  7. 从 Session 取 clientId → ApiAccessProvider.check(clientId, path, method)
     ├─ 拒绝 → 403
     └─ 通过 → 继续步骤8
  8. 解密 Body:
     ├─ 提取 IV[12] + CIPHERTEXT + TAG[16]
     ├─ Sm4Crypto.decrypt(key, iv, sessionId(AAD), ciphertextWithTag)
     ├─ TAG 校验失败 → 400 + errorCode=21202 + X-Session-Expired: true
     └─ 成功 → 明文 JSON 字符串
  9. 包装 HttpServletRequest:
     ├─ 替换 Body 为明文
     └─ 保持 Content-Type: application/json（Controller 看到的是正常 JSON 请求）
  10. doFilter → Controller 处理（对加解密无感知）
  11. Controller 返回后：
      ├─ 获取响应体 → JSON 序列化
      ├─ Sm4Crypto.encrypt → Base64(IV || CIPHER || TAG)
      ├─ 写入响应 Content-Type: text/plain
      └─ 返回
```

### 5.3 双 Servlet API 适配实现

```java
// ========== 实际过滤逻辑（一份代码，不依赖任何 Servlet API） ==========
public class Sm2ServerFilterLogic {
    // 处理请求：返回解密后的明文 body（或 null 表示不放行）
    public FilterResult process(
        String method, String path,
        Map<String, String> headers,
        byte[] requestBody,
        String remoteAddr);
    // 加密响应
    public byte[] encryptResponse(String sessionId, Object responseBody);
}

// ========== javax.servlet 适配（Spring Boot 2.x） ==========
public class JavaxSm2ServerFilter implements javax.servlet.Filter {
    private Sm2ServerFilterLogic logic;
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) {
        // 适配 javax.servlet → 调用 logic.process() → 包装 request → chain.doFilter
    }
}

// ========== jakarta.servlet 适配（Spring Boot 3.x） ==========
public class JakartaSm2ServerFilter implements jakarta.servlet.Filter {
    private Sm2ServerFilterLogic logic;
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) {
        // 适配 jakarta.servlet → 调用 logic.process() → 包装 request → chain.doFilter
    }
}
```

**编译要点**：
- core/server 模块 pom 中 javax.servlet-api 和 jakarta.servlet-api 都是 `provided` 范围
- 两个 Filter 类编译时同时存在于 classpath
- 运行时只有一个 Servlet API 在 classpath 上，另一个 Filter 类永远不会被加载，不报错

### 5.4 接口访问控制 SPI

```java
public interface ApiAccessProvider {
    boolean check(String clientId, String path, String method);
    List<String> getAllowedPaths(String clientId);
    default void refresh() {}
}

// 默认实现：读取 yml 配置，支持 Ant 风格路径匹配
public class ConfigBasedApiAccessProvider implements ApiAccessProvider {
    // sm2-sdk.access.clients.{clientId}.paths = [/api/payment/**, ...]
    // 使用 AntPathMatcher 匹配
    // default-policy: deny → 未配置的客户端默认拒绝
    // default-policy: allow → 未配置的客户端默认放行
}
```

业务方实现 `ApiAccessProvider` 并注册 Spring Bean → SDK 自动检测覆盖默认实现。

### 5.5 RedisSessionStore（分布式会话存储）

```
使用 spring.redis.* 标准配置获取 RedisConnectionFactory

存储结构:
  Key:   {prefix}:session:{sessionId}
  Value: Session 对象 → JSON 序列化（sm4Key 用服务端本地密钥加密后存储）
  TTL:   与 session.timeout 一致

非功能性:
  - Redis 不可用 → 降级为本地 CaffeineSessionStore + 记录告警
  - Session 对象中 sm4Key 用服务端启动时生成的本地加密密钥保护
```

---

## 六、配置设计

### 6.1 Redis Key 前缀

只暴露一个前缀，SDK 内部自动拼接：

| 用途 | Key 格式 | 示例 |
|------|---------|------|
| 会话 | `{prefix}:session:{sessionId}` | `sm2:session:abc123` |
| Nonce | `{prefix}:nonce:{nonce}` | `sm2:nonce:xyz789` |
| Nonce Bloom | `{prefix}:nonce:bf:{minuteTick}` | `sm2:nonce:bf:437` |
| 握手锁 | `{prefix}:handshake:lock:{clientId}` | `sm2:handshake:lock:SYSTEM_A` |

### 6.2 完整 application.yml

```yaml
# Redis（复用 Spring Boot 标准配置，SDK 不自定义）
spring:
  redis:
    host: 127.0.0.1
    port: 6379
    password: ${REDIS_PASSWORD:}

# SDK 完整配置
sm2-sdk:
  # ──── Redis Key 前缀 ────
  redis-key-prefix: sm2

  # ──── 本方身份 ────
  my-identity: SYSTEM_A
  my-private-key: /secure/private.pem

  # ──── 对方系统配置（可配多个） ────
  peer:
    SYSTEM_B:
      public-key: /secure/b_public.pem
      server-url: https://api.system-b.com
    SYSTEM_C:
      public-key: /secure/c_public.pem
      server-url: https://api.system-c.com

  # ──── 角色开关 ────
  client:
    enabled: true                # 开启主动调用能力
  server:
    enabled: true                # 开启被动响应能力
    session-store: local         # local | redis（多实例部署用 redis）

  # ──── 会话管理 ────
  session:
    timeout: 1800                # 会话空闲超时（秒）
    max-lifetime: 7200           # 会话总生命周期上限（秒，超过禁止续期）
    max-requests: 1000           # 单会话最大请求次数
    renew-threshold: 300         # 提前续期阈值（秒）

  # ──── 握手 ────
  handshake:
    retry: 3                     # 握手失败重试次数
    timeout: 5000                # 握手超时（毫秒）
    protocol-version: "1.0"      # 协议版本

  # ──── 安全参数 ────
  timestamp:
    tolerance: 300               # 时间戳容差（秒）
  nonce:
    cache-seconds: 300           # Nonce 黑名单有效期（秒）

  # ──── 降级 ────
  fallback:
    enabled: false               # 是否允许降级使用上次会话密钥

  # ──── 路径兜底（仅用于无法加注解的第三方接口） ────
  path:
    include:                     # 路径白名单（空 = 仅靠 @Sm2Api）
    exclude:                     # 路径黑名单

  # ──── 接口访问控制 ────
  access:
    default-policy: deny         # deny=未配置拒绝, allow=未配置放行
    provider: config             # config | database
    clients:
      SYSTEM_B:
        paths:
          - /api/user/**
          - /api/order/query
```

---

## 七、异常码规范

### 7.1 编码规则：5位数字 `ABCDD`

```
A: 错误级别 (1=警告/2=严重/3=致命)
B: 模块层级 (1=客户端/2=服务端)
C: 操作类型 (1=握手/2=加解密/3=会话/4=配置/5=网络/9=通用)
DD: 具体错误序号 (00-99)
```

### 7.2 完整异常码表

#### 客户端异常

| 错误码 | 级别 | 说明 | 处理 |
|--------|------|------|------|
| 11000 | 警告 | 客户端初始化失败 | 检查配置 |
| 11101 | 警告 | 临时公钥生成失败 | 检查随机数源 |
| 11102 | 警告 | 本地签名生成失败 | 检查私钥 |
| 21103 | 严重 | 服务端证书验签失败 | 检查服务端公钥 |
| 11104 | 警告 | 服务端临时公钥不在曲线上 | 重试 |
| 21105 | 严重 | 共享密钥计算失败(x1=0) | 重试(最多3次) |
| 21106 | 严重 | 密钥确认失败(SB验证不通过) | 重试 |
| 21107 | 严重 | 时间戳偏差超限(>300s) | 同步时间 |
| 11108 | 警告 | 握手超时 | 检查网络/增加超时 |
| 11201 | 警告 | SM4加密失败 | 检查会话密钥 |
| 21202 | 严重 | SM4解密-TAG校验失败 | 数据可能被篡改 |
| 11203 | 警告 | 解密失败-会话密钥不存在 | 触发重握手 |
| 11204 | 警告 | IV重复使用 | 检查随机数生成器 |
| 11301 | 警告 | 会话已过期 | 自动重握手 |
| 11302 | 警告 | 会话请求次数超限 | 自动重握手 |
| 21303 | 严重 | 会话状态异常 | 清理会话重新初始化 |
| 31401 | 致命 | 客户端私钥未配置 | 检查密钥配置 |
| 31402 | 致命 | 服务端公钥未配置 | 检查密钥配置 |
| 21403 | 严重 | 密钥文件读取失败 | 检查文件权限 |
| 11501 | 警告 | 网络连接失败 | 检查地址和网络 |
| 11502 | 警告 | HTTP请求失败(≥400) | 检查服务端 |

#### 服务端异常

| 错误码 | 级别 | 说明 | 处理 |
|--------|------|------|------|
| 22101 | 严重 | 客户端证书验签失败 | 客户端私钥可能错误 |
| 22102 | 严重 | 客户端临时公钥非法 | 客户端随机数异常 |
| 22103 | 严重 | 密钥确认失败(SA验证失败) | 客户端计算错误 |
| 12104 | 警告 | 重复握手请求 | 返回已有会话ID |
| 22301 | 严重 | 会话不存在或已过期 | 返回401让客户端重握手 |
| 22302 | 严重 | 会话被篡改(Redis数据异常) | 清理缓存记录事件 |
| 12303 | 警告 | 会话数超过上限 | 检查是否泄漏 |
| 22501 | 严重 | 请求频率超限 | 429通知客户端降级 |

#### 通用异常

| 错误码 | 级别 | 说明 |
|--------|------|------|
| 39000 | 致命 | 未知异常 |
| 29001 | 严重 | Nonce重复(防重放拦截) |
| 29002 | 严重 | 签名校验失败 |
| 19003 | 警告 | 连续握手失败触发熔断 |
| 19004 | 警告 | 内存清理失败 |
| 29005 | 严重 | 线程池满 |

### 7.3 HTTP 状态码映射

| HTTP | SDK 错误码 | 说明 |
|------|-----------|------|
| 200 | - | 成功 |
| 400 | 21103,21106,21202 | 请求错误 |
| 401 | 11301,22301 | 会话过期需重握手 |
| 403 | 11104,22102,29001 | 非法/重放 |
| 408 | 11108 | 握手超时 |
| 429 | 12303 | 频率超限 |
| 500 | 19004,39000 | 内部错误 |

---

## 八、日志与监控

### 8.1 日志规范

**必须记录**（脱敏后）：
- 握手开始/成功/失败（含 sessionId，不含密钥）
- 会话创建/续期/销毁
- 加解密异常（含 sessionId、错误码）

**严禁记录**：
- SM2 私钥（静态/临时）
- 共享密钥 K
- SM4 会话密钥
- 业务明文数据

**正确示例**：`sessionId=abc123, 协商成功, 耗时=12ms`
**错误示例**：`sessionId=abc123, key=3F7A8B..., iv=9E2D...`

### 8.2 监控指标

| 指标 | 说明 |
|------|------|
| `sm2_handshake_total` | 握手总次数（按成功/失败） |
| `sm2_handshake_duration` | 握手耗时分布 |
| `sm2_session_active` | 当前活跃会话数 |
| `sm2_encrypt_requests` | 加密请求总数 |
| `sm2_decrypt_errors` | 解密失败次数 |
| `sm2_replay_blocks` | 防重放拦截次数 |

---

## 九、安全加固清单

| 类别 | 措施 |
|------|------|
| 密钥管理 | 长期密钥存文件/环境变量；临时密钥仅内存用完清零 |
| 内存安全 | 基础：`char[]` + `Arrays.fill(key, '\0')`；增强：`ByteBuffer.allocateDirect()` 堆外 |
| 防重放 | 握手层时间戳(300s) + 业务层Nonce(Bloom+Redis两级) |
| 通信安全 | HTTPS 叠加 SM4；证书 Pinning（内置2个备用指纹） |
| 编译安全 | 代码混淆 + 敏感字符串编译期加密 + Jar包 MD5/SHA256 校验 |

---

## 十、降级与逃生策略

| 场景 | 降级策略 | 恢复条件 |
|------|---------|---------|
| Redis 不可用 | 降级为本地 Caffeine 内存缓存 | Redis 恢复后切回 |
| 握手服务整体异常 | 返回 HandshakeFailedException，业务方自行降级 | 握手成功率恢复 |
| Nonce Redis 故障 | 仅依赖时间戳校验（降低安全级别，记录告警） | Redis 恢复 |
| 极端逃生 | 管理员通过配置开关临时关闭加密（最大10分钟，审计+告警） | 故障修复后立即关闭 |

---

## 十一、兼容性矩阵

| JDK | Spring Boot | Servlet API | 自动加载的 Filter |
|-----|-------------|-------------|-------------------|
| 8 | 2.7 | javax | `JavaxSm2ServerFilter` |
| 11 | 2.7 | javax | `JavaxSm2ServerFilter` |
| 17 | 2.7 | javax | `JavaxSm2ServerFilter` |
| 17 | 3.x | jakarta | `JakartaSm2ServerFilter` |
| 21 | 3.x | jakarta | `JakartaSm2ServerFilter` |

---

## 十二、项目文件结构

```
sm2-sdk/
├── pom.xml                                    ← 父 POM（版本管理 + shade 配置）
├── sm2-sdk-core/
│   └── src/main/java/com/sm2sdk/core/
│       ├── crypto/
│       │   ├── Sm2KeyExchange.java            ← 密钥协商接口
│       │   ├── Sm4Crypto.java                 ← 加解密接口
│       │   ├── KeyDerivation.java             ← KDF 实现
│       │   └── impl/
│       │       ├── HutoolSm2KeyExchange.java  ← Hutool SM2 实现
│       │       └── HutoolSm4Crypto.java       ← Hutool SM4 实现
│       ├── session/
│       │   ├── Session.java                   ← 会话对象
│       │   ├── SessionStore.java              ← 会话存储接口
│       │   ├── SessionManager.java            ← 编排类
│       │   └── impl/
│       │       ├── CaffeineSessionStore.java  ← Caffeine 本地实现
│       │       └── RedisSessionStore.java     ← Redis 分布式实现
│       ├── nonce/
│       │   └── NonceValidator.java            ← Bloom Filter + Redis 两级
│       ├── model/
│       │   ├── HandshakeContext.java
│       │   ├── HandshakeInit.java
│       │   ├── HandshakeServerResp.java
│       │   ├── HandshakeConfirm.java
│       │   └── Sm2SdkConfig.java
│       ├── exception/
│       │   ├── Sm2SdkException.java
│       │   └── ErrorCode.java                 ← 异常码枚举（完整7.2表）
│       └── util/
│           ├── SecureRandomUtil.java
│           ├── MemoryCleanUtil.java
│           └── Sm2KeyPrefix.java              ← Redis Key 前缀工具
├── sm2-sdk-client/
│   └── src/main/java/com/sm2sdk/client/
│       ├── Sm2HttpClient.java
│       ├── Sm2Request.java
│       ├── Sm2ClientConfig.java
│       └── HandshakeRetryHandler.java         ← 握手重试 + 熔断
├── sm2-sdk-server/
│   └── src/main/java/com/sm2sdk/server/
│       ├── Sm2Api.java                        ← @Sm2Api 注解
│       ├── Sm2ServerFilterLogic.java          ← 过滤逻辑（一份代码）
│       ├── JavaxSm2ServerFilter.java          ← javax.servlet.Filter
│       ├── JakartaSm2ServerFilter.java        ← jakarta.servlet.Filter
│       ├── ApiAccessProvider.java             ← 访问控制 SPI
│       ├── ConfigBasedApiAccessProvider.java  ← 默认 yml 实现
│       └── PathMatcher.java                   ← Ant 路径匹配
└── sm2-sdk-spring-boot-starter/
    └── src/main/java/com/sm2sdk/starter/
        ├── Sm2SdkProperties.java              ← @ConfigurationProperties
        ├── Sm2SdkAutoConfiguration.java       ← 自动配置（含双 Filter 选择）
        └── spring.factories                   ← Spring Boot 自动配置注册
```

---

## 十三、打包与发布

### 13.1 Maven Shade 重定位

```xml
<relocations>
    <relocation>
        <pattern>cn.hutool</pattern>
        <shadedPattern>com.sm2sdk.third.hutool</shadedPattern>
    </relocation>
    <relocation>
        <pattern>com.fasterxml.jackson</pattern>
        <shadedPattern>com.sm2sdk.third.jackson</shadedPattern>
    </relocation>
</relocations>
```

### 13.2 发布物

| 制品 | 说明 |
|------|------|
| `sm2-sdk-spring-boot-starter-1.0.0.jar` (shaded) | 内嵌 Hutool+Jackson，推荐 |
| `sm2-sdk-spring-boot-starter-1.0.0-slim.jar` | 不 shade，自行管理依赖版本 |
| `-sources.jar` / `-javadoc.jar` | 源码 + API 文档 |

---

## 十四、质量门禁

- 单元测试覆盖率 ≥ 80%（核心加解密 100%）
- JDK 8 / 11 / 17 兼容性测试
- 完整握手 + 加解密请求/响应 集成测试
- 会话过期自动重握手 + TAG 校验失败重试 场景测试
- OWASP Dependency Check 通过

