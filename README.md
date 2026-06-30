# SM2 安全数据交换 SDK

基于国密 SM2/SM3/SM4 算法，提供加解密、签名验签、密钥交换、会话管理、HTTP 客户端及 Spring Boot 自动配置。

---

## 目录

- [一、获取 SDK](#一获取-sdk)
- [二、生成密钥](#二生成密钥)
- [三、接入方式](#三接入方式)
  - [Spring Boot 自动配置](#1-spring-boot-自动配置推荐)
  - [纯 Java API 无-Spring](#2-纯-java-api无-spring)
- [四、使用方式](#四使用方式)
  - [主动调用客户端](#主动调用作为客户端)
  - [被动响应服务端](#被动响应作为服务端)
- [五、构建与发布](#五构建与发布)
- [六、配置参考](#六配置参考)
- [七、异常码速查](#七异常码速查)
- [八、项目结构](#八项目结构)

---

## 一、获取 SDK

### Maven 依赖

```xml
<!-- Spring Boot 2.7 + JDK 8/11 -->
<dependency>
    <groupId>com.sm2sdk</groupId>
    <artifactId>sm2-sdk-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Spring Boot 3.x + JDK 17+ -->
<dependency>
    <groupId>com.sm2sdk</groupId>
    <artifactId>sm2-sdk-spring-boot3-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 仅加解密，不依赖 Spring -->
<dependency>
    <groupId>com.sm2sdk</groupId>
    <artifactId>sm2-sdk-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 直接下载 JAR

| JAR | 说明 | 内置依赖 |
|-----|------|---------|
| `sm2-sdk-core-1.0.0.jar` | 核心加解密 | 无，需自行提供 bcprov、hutool 等 |
| `sm2-sdk-client-1.0.0.jar` | HTTP 客户端 | 依赖 core |
| `sm2-sdk-spring-boot-starter-1.0.0.jar` | Boot 2.7 一体化包 | **内置全部依赖**（Hutool、Jackson、BouncyCastle） |
| `sm2-sdk-spring-boot3-starter-1.0.0.jar` | Boot 3.x 一体化包 | **内置全部依赖** |

---

## 二、生成密钥

双方各自生成一对 SM2 密钥。**私钥自己保留，公钥给对方。**

### 方式一：从 Starter JAR（推荐，拿到 JAR 就能用）

```bash
# Windows
java -cp sm2-sdk-spring-boot-starter-1.0.0.jar com.sm2sdk.core.util.Sm2KeyGen

# Linux / Mac
java -cp sm2-sdk-spring-boot-starter-1.0.0.jar com.sm2sdk.core.util.Sm2KeyGen

# 一次生成多对
java -cp sm2-sdk-spring-boot-starter-1.0.0.jar com.sm2sdk.core.util.Sm2KeyGen 3
```

### 方式二：从 Core JAR（需额外提供依赖）

```bash
# Windows
java -cp "sm2-sdk-core-1.0.0.jar;bcprov-jdk18on-1.84.jar;hutool-all-5.8.32.jar" com.sm2sdk.core.util.Sm2KeyGen

# Linux / Mac
java -cp "sm2-sdk-core-1.0.0.jar:bcprov-jdk18on-1.84.jar:hutool-all-5.8.32.jar" com.sm2sdk.core.util.Sm2KeyGen
```

### 方式三：开发期（Maven）

```bash
cd sm2-sdk

# 首次需先编译
mvn clean install -DskipTests

# 生成密钥
mvn exec:java -pl core -Dexec.mainClass="com.sm2sdk.core.util.Sm2KeyGen"

# Windows 脚本
tools\keygen.bat

# Linux / Mac 脚本
tools/keygen.sh
```

### 输出示例

```
sm2-private-key: 10f5854d3844797a57e53e7d27db088479566394ff5b694efbf17be3189dc90b
sm2-public-key:  04ecc36a8b58afceda42755c86f42cd5692eeb91aa27bd8082b9b8c7320ab0cc7cd9...
```

---

## 三、接入方式

### 1. Spring Boot 自动配置（推荐）

**引入 Starter 依赖 → 配置 yml → 启动。零代码。**

```yaml
# application.yml
sm2:
  sdk:
    sm2-private-key: "10f5854d...（64 位十六进制）"
    sm2-public-key:  "04ecc36a...（130 位十六进制）"
    server-url: "https://peer.example.com"   # 需要主动调用时才配
```

启动后 SDK 自动注册：
- 握手端点 `POST /sm2/handshake/init`、`POST /sm2/handshake/confirm`
- 请求拦截器（自动解密 Body）
- 响应加密处理器（自动加密 Response）

### 2. 纯 Java API（无 Spring）

```java
// 1. 创建配置
Sm2SdkConfig config = new Sm2SdkConfig()
    .withSm2PrivateKey("10f5854d...")
    .withSm2PublicKey("04ecc36a...");

// 2. 创建密钥交换和加解密实例
Sm2KeyExchange keyExchange = new HutoolSm2KeyExchange();
Sm4Crypto sm4Crypto = new HutoolSm4Crypto();
SessionStore store = new CaffeineSessionStore();

// 3. 创建会话管理器
SessionManager sessionManager = new SessionManager(
    keyExchange, sm4Crypto, store, config, null);

// 4. 发起握手
Session session = sessionManager.initiateHandshake("peer-id");

// 5. 加解密
String encrypted = sessionManager.encryptBody(session.getSessionId(), "{\"data\":\"hello\"}");
String decrypted = sessionManager.decryptBody(session.getSessionId(), encrypted);
```

---

## 四、使用方式

### 主动调用（作为客户端）

```java
@Autowired
private Sm2HttpClient sm2Client;

// GET 查询
UserInfo user = sm2Client.get("/api/user/query")
        .param("idCard", "110101199001011234")
        .execute(UserInfo.class);

// POST 提交
Result r = sm2Client.post("/api/payment/submit")
        .body(orderRequest)
        .execute(Result.class);

// PUT 更新
sm2Client.put("/api/user/update")
        .body(updateRequest)
        .execute(Void.class);

// DELETE 删除
sm2Client.delete("/api/cache/clear")
        .param("scope", "all")
        .execute(Void.class);
```

SDK 内部自动完成：握手 → 加密 → 发请求 → 解密响应 → 返回明文。会话过期自动重握手，对调用方完全透明。

### 被动响应（作为服务端）

**不需要写任何代码。** 引入 Starter 后所有请求自动解密、响应自动加密。握手端点路径（`/sm2/handshake/**`）自动跳过加解密。

---

## 五、构建与发布

### 开发构建（本地验证）

```bash
cd sm2-sdk

# 编译 + 测试
mvn clean test

# 打包（不混淆）
mvn clean package -DskipTests
```

### 发布构建（ProGuard 混淆）

```bash
mvn clean package -Prelease
```

混淆策略：
- **保持** 所有公开 API 类名和方法签名不变
- **保持** Spring Boot 自动配置类、注解、枚举
- **混淆** 内部实现细节
- **移除** 调试信息
- 目标兼容 JDK 8

### 发布产物

```
sm2-sdk/
├── core/target/sm2-sdk-core-1.0.0.jar
├── client/target/sm2-sdk-client-1.0.0.jar
├── spring-boot-starter/target/sm2-sdk-spring-boot-starter-1.0.0.jar    ← Boot 2.7 一体化
└── spring-boot3-starter/target/sm2-sdk-spring-boot3-starter-1.0.0.jar  ← Boot 3.x 一体化
```

Starter JAR 已通过 maven-shade-plugin 内置 Hutool、Jackson、BouncyCastle（包名 relocate 到 `com.sm2sdk.third.*`，不与业务方依赖冲突）。

### 给下游系统交付

交付两个 Starter JAR（boot2 + boot3），以及本 README。下游系统只需：
1. 把 JAR 放进项目（或配置私有 Maven 仓库）
2. 生成自己的密钥对
3. 配 yml、启动

---

## 六、配置参考

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `sm2.sdk.enabled` | `true` | SDK 开关 |
| `sm2.sdk.sm2-private-key` | — | 本方 SM2 私钥（64 位十六进制） |
| `sm2.sdk.sm2-public-key` | — | 本方 SM2 公钥（130 位十六进制） |
| `sm2.sdk.server-url` | — | 对方服务地址（主动调用时必配） |
| `sm2.sdk.session-timeout-ms` | `300000` | 会话空闲超时 (ms) |
| `sm2.sdk.max-session-lifetime-ms` | `3600000` | 会话最大生命周期 (ms) |
| `sm2.sdk.max-session-requests` | `1000` | 单会话最大请求数 |
| `sm2.sdk.handshake-timeout-ms` | `10000` | 握手超时 (ms) |
| `sm2.sdk.max-sessions` | `10000` | 最大并发会话数 |
| `sm2.sdk.redis-key-prefix` | `sm2` | Redis 键前缀 |
| `sm2.sdk.redis-session-store` | `false` | 启用 Redis 会话存储 |

---

## 七、异常码速查

| 错误码 | HTTP | 含义 | SDK 自动处理 |
|--------|------|------|-------------|
| `11301` | 401 | 会话已过期 | 自动重握手 |
| `11302` | 401 | 请求次数超限 | 自动重握手 |
| `22301` | 401 | 服务端会话不存在 | 自动重握手 |
| `21202` | 400 | TAG 校验失败 | 废弃会话 + 重握手 |
| `21103` | 400 | 签名验证失败 | 检查对方公钥 |
| `11108` | 408 | 握手超时 | 检查网络 |
| `19003` | 503 | 握手熔断 | 30s 后自动恢复 |
| `29001` | 403 | Nonce 重放 | 安全告警 |
| `31401` | 500 | 私钥未配置 | 检查配置 |

---

## 八、项目结构

```
sm2-sdk/                              # SDK 主项目
├── pom.xml                           # 父 POM（版本管理 + 混淆配置）
├── proguard.pro                      # ProGuard 混淆规则
├── core/                             # 核心加解密（零框架依赖）
├── client/                           # HTTP 客户端
├── spring-boot-starter/              # Boot 2.7 自动配置 + shade
└── spring-boot3-starter/             # Boot 3.x 自动配置 + shade

tools/                                # 密钥生成脚本
├── keygen.bat
└── keygen.sh

sm2-sdk-demo-boot2/                   # Boot 2.7 演示项目
sm2-sdk-demo-boot3/                   # Boot 3.x 演示项目
```

## 兼容性

| JDK | Spring Boot | 使用 |
|-----|-------------|------|
| 8 / 11 | 2.7 | `sm2-sdk-spring-boot-starter` |
| 17 / 21 | 3.x | `sm2-sdk-spring-boot3-starter` |
| 8+ | 无 | `sm2-sdk-core` |

## License

Apache License 2.0
