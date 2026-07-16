# SM2 安全数据交换 SDK

基于国密 SM2/SM3/SM4 算法，提供加解密、签名验签、密钥交换、会话管理、HTTP 客户端及 Spring Boot 自动配置。

---

## 目录

- [一、获取 SDK](#一获取-sdk)
- [二、生成密钥](#二生成密钥)
- [三、接入方式](#三接入方式)
  - [1. Spring Boot 自动配置（推荐）](#1-spring-boot-自动配置推荐)
  - [2. 纯 Java API（无 Spring）](#2-纯-java-api无-spring)
- [四、使用方式](#四使用方式)
  - [@Sm2Secured 注解](#sm2secured-注解)
  - [主动调用（客户端）](#主动调用作为客户端)
  - [被动响应（服务端）](#被动响应作为服务端)
  - [客户端访问控制](#客户端访问控制)
- [五、Demo 演示项目](#五demo-演示项目)
  - [启动 Demo](#启动-demo)
  - [端点说明](#端点说明)
  - [手动测试流程](#手动测试流程)
  - [Sm2HttpClient 自动调用](#sm2httpclient-自动调用)
- [六、构建与发布](#六构建与发布)
- [七、配置参考](#七配置参考)
- [八、异常码速查](#八异常码速查)
- [九、项目结构](#九项目结构)
- [十、技术实现与安全保障](#十技术实现与安全保障)
- [十一、最简接入指南](#十一最简接入指南)

---

## 一、获取 SDK

SDK 尚未发布到公共 Maven 仓库，可通过以下方式一或方式二获取。

### Maven 依赖（暂不支持请看下载jar）

```xml
<!-- Spring Boot 2.7 + JDK 8/11 -->
<dependency>
    <groupId>io.github.mtooo</groupId>
    <artifactId>sm2-sdk-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- Spring Boot 3.x + JDK 17+ -->
<dependency>
    <groupId>io.github.mtooo</groupId>
    <artifactId>sm2-sdk-spring-boot3-starter</artifactId>
    <version>1.0.0</version>
</dependency>

<!-- 仅加解密，不依赖 Spring -->
<dependency>
    <groupId>io.github.mtooo</groupId>
    <artifactId>sm2-sdk-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 直接下载 JAR

> **v1.0.0+**：Starter 已改为标准 thin JAR（~50KB）。所有第三方依赖（hutool、bcprov、caffeine）通过 Maven 传递依赖自动引入，无需手动管理。**必须使用 `mvn install` 方式引入**，不再支持 `system` scope 引入。

| JAR | 大小 | 说明 |
|-----|------|------|
| `sm2-sdk-core-1.0.0.jar` | ~60KB | 核心加解密 |
| `sm2-sdk-client-1.0.0.jar` | ~16KB | HTTP 客户端，依赖 core |
| `sm2-sdk-spring-boot-starter-1.0.0.jar` | ~50KB | Boot 2.7 自动配置，依赖 core+client |
| `sm2-sdk-spring-boot3-starter-1.0.0.jar` | ~50KB | Boot 3.x 自动配置，依赖 core+client |

> **传递依赖**：Maven 会自动引入 `bcprov-jdk18on`、`hutool-crypto`、`hutool-http`、`hutool-bloomFilter`、`hutool-json`、`caffeine`，无需手动声明。

### 安装到本地 Maven 仓库

```bash
# 在 sm2-sdk 项目根目录执行（一键安装全部模块到 ~/.m2）
cd sm2-sdk
mvn clean install -DskipTests
```

如需单独安装各个 JAR：

```bash
# 安装父 POM
mvn install:install-file \
  -Dfile=sm2-sdk-parent-1.0.0.pom \
  -DgroupId=io.github.mtooo \
  -DartifactId=sm2-sdk-parent \
  -Dversion=1.0.0 \
  -Dpackaging=pom

# 安装 core
mvn install:install-file \
  -Dfile=lib/sm2-sdk-core-1.0.0.jar \
  -DgroupId=io.github.mtooo \
  -DartifactId=sm2-sdk-core \
  -Dversion=1.0.0 \
  -Dpackaging=jar

# 安装 client
mvn install:install-file \
  -Dfile=lib/sm2-sdk-client-1.0.0.jar \
  -DgroupId=io.github.mtooo \
  -DartifactId=sm2-sdk-client \
  -Dversion=1.0.0 \
  -Dpackaging=jar

# 安装 Starter
mvn install:install-file \
  -Dfile=lib/sm2-sdk-spring-boot3-starter-1.0.0.jar \
  -DgroupId=io.github.mtooo \
  -DartifactId=sm2-sdk-spring-boot3-starter \
  -Dversion=1.0.0 \
  -Dpackaging=jar
```

安装后按上方 Maven 依赖正常引用，Maven 会自动拉取 bcprov、hutool、caffeine 等传递依赖。

---

## 二、生成密钥

双方各自生成一对 SM2 密钥。**私钥自己保留，公钥给对方。**

### 方式一：使用 Maven（推荐）

```bash
# 在任意目录执行，Maven 自动解析传递依赖
mvn dependency:exec -Dexec.classpathScope=compile \
  -Dexec.mainClass="io.github.mtooo.core.util.Sm2KeyGen" \
  -DincludeGroupIds="io.github.mtooo"

# 批量生成
mvn dependency:exec -Dexec.classpathScope=compile \
  -Dexec.mainClass="io.github.mtooo.core.util.Sm2KeyGen" \
  -Dexec.args="3"
```

### 方式二：从本地 Maven 仓库 classpath（需手动拼依赖）

```bash
# 在已 mvn install 的机器上，用 Maven 构建 classpath
CP=$(mvn -f path/to/your/pom.xml dependency:build-classpath -DincludeScope=compile -q -Dmdep.outputFile=/dev/stdout 2>/dev/null)
java -cp "$CP" io.github.mtooo.core.util.Sm2KeyGen
```

### 输出示例

```
sm2-private-key: 10f5854d3844797a57e53e7d27db088479566394ff5b694efbf17be3189dc90b
sm2-public-key:  04ecc36a8b58afceda42755c86f42cd5692eeb91aa27bd8082b9b8c7320ab0cc7cd9...
sm4-key:         3a7b2c1d4e5f6a8b9c0d1e2f3a4b5c6d  (hex)
sm4-key-base64:  OnssHU5faoucDR4vOktcbQ==          (base64, 可直接填入 local-secret-key)
```

---

## 三、接入方式

### 1. Spring Boot 自动配置（推荐）

**引入 Starter 依赖 → 配置 yml → 启动。零代码。**

```yaml
# application.yml
sm2:
  sdk:
    # ===== 密钥配置（必填） =====
    sm2-private-key: "3f5731fe..." 	# 自己生成的sm2的私钥
    sm2-public-key: "04182d88..." 	# 自己生成的sm2公钥，需要给到对方
    server-id: my-server        	# 我自己的身份标识
    redis-session-store: true		# 启用redis存储密钥，不设置则用的是内存
    redis-key-prefix: sm2-server	# 设置redis存储密钥的前缀键
    local-secret-key: "fnSs....."	# 设置存储sm4密钥，防止redis被脱库拿到明文的密钥
    # ===== 对端配置 =====
    peers:
      # 对端 A
      - public-key: "04aaabbb..." # 对方的公钥
        server-url: "https://service-a.com" # 对方的地址
        server-id: service-a          # 对端 A 声称的 server-id
      # 对端 B
      - public-key: "04cccddd..."
        server-url: "https://service-b.com"
        server-id: service-b
      # 本机闭环测试 不做闭环测试可不配置
      - public-key: "04182d88..."     # 自己的公钥
        server-url: "http://localhost:8080"
        server-id: my-server          # 与全局 server-id 一致

    # ===== 安全加固（可选，均有默认值 以下全是默认值 特殊情况可配置） =====
    server-role: true                      # 是否启用服务端端点、拦截器。纯客户端设为 false
    handshake-rate-limit-per-second: 5     # 每个对端每秒最大握手次数，超过返回 408，防止 CPU/内存耗尽 握手成功后5分钟内有效
    timestamp-window-ms: 30000             # 握手时间戳有效期(ms)，超时拒绝，防止重放攻击
    max-request-body-size: 1048576         # 请求体最大字节数，超过拒绝，防止大密文 OOM
    include-error-detail: false            # 生产必须 false，调试才开 true，防止泄露内部实现
```

启动后 SDK 自动注册：
- 握手端点 `POST /handshake/init`、`POST /handshake/confirm`
- 请求拦截器（自动解密 Body）
- 响应加密处理器（自动加密 Response）

> **重要**：只有标记了 `@Sm2Secured` 注解的 Controller 或方法才会走 SM2 加解密管线，其余端点原样放行。

### 2. 纯 Java API（无 Spring）

```java
// 1. 创建配置
Sm2SdkConfig config = new Sm2SdkConfig()
    .withSm2PrivateKey("10f5854d...")
    .withSm2PublicKey("04ecc36a...")
    .withServerId("my-server")
    .withClientId("my-client");

// 2. 创建会话存储和加解密实例
Sm4Crypto sm4Crypto = new HutoolSm4Crypto();
SessionStore store = new CaffeineSessionStore();

// 3. 创建会话管理器（使用 Supplier 确保每次握手独立实例）
SessionManager sessionManager = new SessionManager(
    () -> new HutoolSm2KeyExchange(), sm4Crypto, store, config);

// 4. 客户端发起握手
Sm2KeyExchange keyExchange = sessionManager.getKeyExchange();
byte[] clientPrivKey = SessionManager.hexToBytes(config.getSm2PrivateKey());
byte[] serverPubKey = SessionManager.hexToBytes(config.getSm2PublicKey());

// 步骤 1: 构建握手请求
HandshakeInit init = keyExchange.buildInitRequest(
    "my-client", clientPrivKey, serverPubKey, "my-client");

// 步骤 2: 发送 init 到服务端 /handshake/init，获得 HandshakeServerResp
// HandshakeServerResp resp = sendToServer(init);

// 步骤 3: 处理服务端响应，派生共享密钥
Sm2KeyExchange.HandshakeResult result = keyExchange.processServerResponse(
    init, resp, clientPrivKey, serverPubKey, "my-client", config.getServerId());

// 步骤 4: 发送 confirm 到服务端 /handshake/confirm
// HandshakeConfirm confirm = keyExchange.buildConfirm(result);
// sendToServer(confirm);

// 步骤 5: 创建会话
Session session = sessionManager.createSession("my-client", result);

// 5. 加解密
String encrypted = sessionManager.encryptBody(session.getSessionId(), "{\"data\":\"hello\"}");
String decrypted = sessionManager.decryptBody(session.getSessionId(), encrypted);
```

---

## 四、使用方式

### @Sm2Secured 注解

`@Sm2Secured` 标注在 Controller 类或方法上，标记该端点需要 SM2 加解密处理。未标记的端点完全不受影响。

```java
// 方式一：类级别 — 整个 Controller 都走 SM2
@Sm2Secured
@RestController
public class SecureController {
    @PostMapping("/api/secret")
    public Map secret(@RequestBody Map body) { ... }
}

// 方式二：方法级别 — 仅该接口走 SM2
@RestController
public class MixedController {
    @Sm2Secured
    @PostMapping("/api/secret")    // 加密
    public Map secret(@RequestBody Map body) { ... }

    @GetMapping("/api/public")     // 明文
    public String health() { return "OK"; }
}
```

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

在需要加解密的端点标记 `@Sm2Secured`：

```java
@RestController
public class MyController {

    @Sm2Secured
    @PostMapping("/api/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        body.put("serverTime", System.currentTimeMillis());
        return body;  // 请求自动解密，响应自动加密
    }

    @GetMapping("/api/health")  // 未标记，明文访问
    public String health() { return "OK"; }
}
```

SDK 自动完成：请求解密 → Controller 拿明文处理 → 响应加密。握手端点（`/handshake/**`）自动跳过。**未标记 `@Sm2Secured` 的端点不受影响。**

### 客户端访问控制

SDK 支持基于客户端身份（`clientId`）的接口访问控制——不同客户端只能访问授权给它的路径。

#### 服务端：配置访问规则

```yaml
sm2:
  sdk:
    client-access:
      enabled: true           # 启用访问控制，默认 false（向后兼容）
      default-policy: deny    # 无规则匹配时的默认策略：allow（默认）或 deny
      rules:
        - client-id: "app-a"
          paths:
            - /api/user/**
            - /api/order/**
        - client-id: "app-b"
          paths:
            - "/**"           # app-b 可访问一切
```

#### 客户端：声明自身标识

要想让服务端识别你的身份，客户端必须配置 `sm2.sdk.client-id`：

```yaml
sm2:
  sdk:
    client-id: "app-a"    # 告诉服务端"我是 app-a"
    server-url: "https://peer.example.com"
    sm2-private-key: "..."
    sm2-public-key: "..."
```

SDK 会在握手时自动将 `client-id` 发送给服务端，服务端根据 `client-access.rules` 决定是否放行。

**完整示例**：两个客户端 `app-a` 和 `app-b`，各自只能访问自己的路径。

客户端 `app-a` 的 `application.yml`：

```yaml
sm2:
  sdk:
    client-id: "app-a"
    server-url: "https://api.example.com"
```

客户端 `app-b` 的 `application.yml`：

```yaml
sm2:
  sdk:
    client-id: "app-b"
    server-url: "https://api.example.com"
```

服务端 `application.yml`：

```yaml
sm2:
  sdk:
    client-access:
      enabled: true
      default-policy: deny
      rules:
        - client-id: "app-a"
          paths:
            - /api/user/**
        - client-id: "app-b"
          paths:
            - /api/order/**
```

**匹配逻辑**：

```
请求 → Sm2ServerInterceptor
  ├─ 精确匹配 clientId 的规则 → 检查路径是否在 paths 中
  ├─ 无精确匹配 → fallback 到 clientId 为空（catch-all）的规则
  └─ 无任何匹配 → 应用 defaultPolicy
       ├─ allow → 放行
       └─ deny  → 返回 403，错误码 22502
```

**向后兼容**：不配置 `client-access` 时，所有客户端均可访问（等同于 `enabled: false`）。

#### 代码扩展方式

实现 `Sm2AccessController` 接口并注册为 Spring Bean，即可完全接管访问控制逻辑：

```java
package com.example.myapp;

import io.github.mtooo.core.access.Sm2AccessController;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyAccessControlConfig {

    /**
     * 注册自定义访问控制器。
     * @ConditionalOnMissingBean 确保该 Bean 优先于 SDK 默认实现。
     */
    @Bean
    public Sm2AccessController myAccessController() {
        // 方式一：Lambda（简单场景）
        return (clientId, path) -> {
            // 从数据库、Redis 或其他系统查询权限
            return myAuthService.authorize(clientId, path);
        };
    }

    // 方式二：实现类（复杂场景）
    @Bean
    public Sm2AccessController databaseAccessController() {
        return new DatabaseAccessController(jdbcTemplate);
    }
}
```

实现类示例：

```java
import io.github.mtooo.core.access.Sm2AccessController;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.AntPathMatcher;

public class DatabaseAccessController implements Sm2AccessController {

    private final JdbcTemplate jdbc;
    private final AntPathMatcher matcher = new AntPathMatcher();

    public DatabaseAccessController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isAllowed(String clientId, String path) {
        // 查询该客户端的所有授权路径
        List<String> allowedPaths = jdbc.queryForList(
            "SELECT path_pattern FROM client_access WHERE client_id = ?",
            String.class, clientId);

        // Ant 风格路径匹配
        for (String pattern : allowedPaths) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }
}
```

> 路径匹配使用 Spring `AntPathMatcher`，支持 `?`（单字符）、`*`（单级目录）、`**`（多级目录）。

### 身份标识与多对端配置

一个应用可能同时扮演多种角色：只做服务端、只做客户端、或两者兼有。SDK 将 **"我是谁"** 和 **"对方是谁"** 分开配置。

#### 核心概念

```
sm2.sdk.server-id          → "我作为服务端，握手时自称什么"
sm2.sdk.client-id          → "我作为客户端，握手时告诉对方我是谁"
sm2.sdk.peers[].server-id  → "我要调的那个对端，它在握手时自称什么"
```

两个 `server-id` 互不干扰：
- 全局 `server-id` 只用于**接收握手**（服务端角色）——计算 ZB 摘要
- `peers[].server-id` 只用于**发起握手**（客户端角色）——对方计算 ZB 摘要

**关键约束**：客户端配置的 `peers[].server-id` 必须与对端服务自己的 `server-id` 一致，否则 ZB 不匹配 → SB 验证失败 → 握手被拒。

#### 场景一：只做服务端

```yaml
sm2:
  sdk:
    sm2-private-key: "..."
    sm2-public-key: "..."
    server-id: my-server        # 我自己的服务端标识
    client-access:              # 可选：对不同 clientId 做访问控制
      enabled: true
      rules:
        - client-id: "app-a"
          paths: ["/api/user/**"]
```

#### 场景二：只做客户端

```yaml
sm2:
  sdk:
    sm2-private-key: "..."
    sm2-public-key: "..."
    client-id: app-a            # 告诉对端"我是 app-a"

    # 对端配置（server-id 必须和对方实际 server-id 一致）
    peers:
      - public-key: "04aaabbb..."          # 对端公钥
        server-url: "https://service-a.com" # 对端地址
        server-id: service-a               # 对端的 server-id
```

不配 `peers[].server-id` 时默认 `"default"`，与未配 `server-id` 的服务端对齐。

#### 场景三：既是服务端又是客户端

一个应用对外暴露接口（服务端），同时也会主动调用其他服务（客户端）：

```yaml
sm2:
  sdk:
    sm2-private-key: "3f5731fe..."
    sm2-public-key: "04182d88..."
    server-id: my-server        # 别人调我时，我的身份
    client-id: app-a            # 我调别人时，报上自己的身份
    client-access:              # 可选：对不同 clientId 做访问控制
      enabled: true
      rules:
        - client-id: "app-a"
          paths: ["/api/user/**"]
    peers:
      # 对端 A
      - public-key: "04aaabbb..."
        server-url: "https://service-a.com"
        server-id: service-a          # 对端 A 声称的 server-id
      # 对端 B
      - public-key: "04cccddd..."
        server-url: "https://service-b.com"
        server-id: service-b
      # 本机闭环测试
      - public-key: "04182d88..."     # 自己的公钥
        server-url: "http://localhost:8080"
        server-id: my-server          # 与全局 server-id 一致
```

#### 单机闭环测试（最简配置）

如果只是本地测试自己调自己，不配 `peers` 也能跑——SDK 会自动用全局公钥和 `server-url` 构建客户端配置，默认两边 `serverId` 都是 `"default"`，天然对齐：

```yaml
sm2:
  sdk:
    sm2-private-key: "..."
    sm2-public-key: "..."
    server-url: "http://localhost:8080"
    client-id: app-a
    # server-id 默认 "default"，可不配
```

#### 纯 Java API

```java
Sm2SdkConfig config = new Sm2SdkConfig()
    .withSm2PrivateKey("...")
    .withSm2PublicKey("...")
    .withServerId("my-server")      // 服务端标识
    .withClientId("app-a")          // 客户端标识
    .withServerUrl("https://service-a.com")
    // 对端配置
    .withPeerConfigs(List.of(
        new Sm2SdkConfig.PeerConfig("04aaab...", "https://service-a.com", "service-a"),
        new Sm2SdkConfig.PeerConfig("04cccd...", "https://service-b.com", "service-b")
    ));
```

> `PeerConfig` 的三参数构造器：`(publicKey, serverUrl, serverId)`。不传 `serverId` 时默认 `"default"`。

---

## 五、Demo 演示项目

项目中包含两个演示项目，展示如何在实际应用中集成 SDK：

| Demo 项目 | JDK | Spring Boot | Starter |
|-----------|-----|-------------|---------|
| `sm2-sdk-demo-boot2/` | 8 | 2.7 | `sm2-sdk-spring-boot-starter` |
| `sm2-sdk-demo-boot3/` | 17 | 3.2 | `sm2-sdk-spring-boot3-starter` |

### 启动 Demo

```bash
# Boot 2.7 版本
cd sm2-sdk-demo-boot2
mvn spring-boot:run

# Boot 3.x 版本
cd sm2-sdk-demo-boot3
mvn spring-boot:run
```

启动前请确保 `application.yml` 中已配置密钥对。

### 端点说明

Demo 项目演示了两种角色的完整交互：

```
SDK 自动注册（无需写代码）
├─ POST /handshake/init       SM2 握手初始化
└─ POST /handshake/confirm    SM2 握手确认

被动响应（服务端角色）— @Sm2Secured 自动加解密
├─ POST /api/echo             回显请求体
├─ GET  /api/user/query       查询用户（GET 参数自动解密）
├─ GET  /api/user/{id}        获取用户详情
├─ POST /api/order/create     创建订单
├─ PUT  /api/user/update      更新用户
├─ DELETE /api/cache/clear    清除缓存
└─ GET  /api/peer/info        对端信息

主动调用（客户端角色）— 明文入口，内部用 Sm2HttpClient
├─ GET    /api/client/query-user    GET 示例
├─ GET    /api/client/get-user      GET PathVariable 示例
├─ POST   /api/client/create-order  POST 示例
├─ PUT    /api/client/update-user   PUT 示例
├─ DELETE /api/client/clear-cache   DELETE 示例
└─ GET    /api/call-peer            通用调用示例

测试工具
├─ GET  /api/session           自我握手，返回 sessionId
└─ POST /api/encrypt           手动加密测试
```

### 手动测试流程

```bash
# 1. 获取测试会话（自动完成 SM2 自我握手）
curl http://localhost:8080/api/session
# → {"sessionId": "uuid-xxx"}

# 2. 用 sessionId 将明文加密为密文
curl -X POST http://localhost:8080/api/encrypt \
  -H "X-Session-Id: uuid-xxx" \
  -H "Content-Type: text/plain" \
  -d '{"name":"张三"}'
# → {"ciphertext": "base64密文..."}

# 3. 把密文发给 @Sm2Secured 端点，验证自动解密
curl -X POST http://localhost:8080/api/echo \
  -H "X-Session-Id: uuid-xxx" \
  -H "Content-Type: application/json" \
  -d '<上一步的 ciphertext>'
# → SDK 自动解密 → echo 处理 → 自动加密返回
```

### Sm2HttpClient 自动调用

如果配置了 `sm2.sdk.server-url`（指向自身 `http://localhost:8080`），可以测试完整的客户端 → 服务端闭环：

```bash
# 明文入口 → Sm2HttpClient 自动握手 → 加密 GET → /api/peer/info → 解密响应
curl "http://localhost:8080/api/client/query-user?name=张三"
# → 自动握手 → 加密 GET → /api/user/query → 解密响应 → 返回明文
```

调用链：明文入口 → `Sm2HttpClient` 自动握手 → 加密请求 → `@Sm2Secured` 端点（自动解密）→ 加密响应 → `Sm2HttpClient` 解密 → 返回明文。

### 运行 Demo 测试

```bash
cd sm2-sdk-demo-boot2 && mvn test    # Demo 集成测试
cd sm2-sdk-demo-boot3 && mvn test    # Demo 集成测试
```

---

## 六、构建与发布

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
├── pom.xml                                              ← 父 POM（版本管理）
├── core/target/sm2-sdk-core-1.0.0.jar                   ← 核心加解密（~60KB）
├── client/target/sm2-sdk-client-1.0.0.jar               ← HTTP 客户端（~16KB）
├── spring-boot-starter/target/sm2-sdk-spring-boot-starter-1.0.0.jar    ← Boot 2.7 自动配置（~50KB）
└── spring-boot3-starter/target/sm2-sdk-spring-boot3-starter-1.0.0.jar  ← Boot 3.x 自动配置（~50KB）
```

Starter 是标准 thin JAR，**不内置第三方依赖**。所有依赖（bcprov、hutool 模块、caffeine）通过 Maven 传递依赖自动引入。JSON 序列化使用 Hutool JSON（`hutool-json`），不再依赖 Jackson。

### 给下游系统交付

交付全部 JAR + 父 POM，下游系统通过 `mvn install` 或私有 Maven 仓库引用。下游系统只需：
1. 引入 Starter 依赖，Maven 自动拉取所有传递依赖
2. 生成自己的密钥对
3. 配 yml、启动

---

## 七、配置参考

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `sm2.sdk.enabled` | `true` | SDK 开关 |
| `sm2.sdk.sm2-private-key` | — | 本方 SM2 私钥（64 位十六进制） |
| `sm2.sdk.sm2-public-key` | — | 本方 SM2 公钥（130 位十六进制） |
| `sm2.sdk.server-url` | — | 对方服务地址（主动调用时必配） |
| `sm2.sdk.client-id` | `"default"` | 客户端标识，握手时发送给服务端，用于服务端访问控制 |
| `sm2.sdk.server-id` | `"default"` | 服务端标识，用于 SM2 握手 ZB 计算。客户端 `peers[].server-id` 须与此一致 |
| `sm2.sdk.peers` | — | 对端配置列表，多个对端时使用 |
| `sm2.sdk.peers[].public-key` | — | 对端 SM2 公钥（130 位十六进制） |
| `sm2.sdk.peers[].server-url` | — | 对端服务地址 |
| `sm2.sdk.peers[].server-id` | `"default"` | 对端服务端标识，须与对方 `server-id` 一致 |
| `sm2.sdk.session-timeout-ms` | `300000` | 会话空闲超时 (ms) |
| `sm2.sdk.max-session-lifetime-ms` | `3600000` | 会话最大生命周期 (ms) |
| `sm2.sdk.max-session-requests` | `1000` | 单会话最大请求数 |
| `sm2.sdk.handshake-timeout-ms` | `10000` | 握手超时 (ms) |
| `sm2.sdk.max-sessions` | `10000` | 最大并发会话数 |
| `sm2.sdk.session-cleanup-interval-ms` | `60000` | 会话清理间隔 (ms) |
| `sm2.sdk.local-secret-key` | — | SM4 密钥（Base64），加密 Redis 中的 SM4临时密钥 |
| `sm2.sdk.redis-key-prefix` | `sm2` | Redis 键前缀 |
| `sm2.sdk.redis-session-store` | `false` | 启用 Redis 会话存储 |
| `sm2.sdk.client-access.enabled` | `false` | 启用客户端访问控制 |
| `sm2.sdk.client-access.default-policy` | `allow` | 默认策略：`allow` / `deny` |
| `sm2.sdk.client-access.rules[].client-id` | — | 客户端标识（空 = catch-all） |
| `sm2.sdk.client-access.rules[].paths` | — | 允许的路径模式列表（Ant 风格） |
| `sm2.sdk.server-role` | `true` | **服务端角色开关**。设为 `false` 后不再注册握手端点、拦截器、Filter 等，SDK 仅保留 `Sm2HttpClient` 客户端能力。适用于纯客户端应用，减少攻击面 |
| `sm2.sdk.handshake-rate-limit-per-second` | `10` | **握手速率限制**（每秒最大请求数）。每次 SM2 握手涉及椭圆曲线运算（CPU 密集）+ 会话创建（I/O），高频调用可耗尽资源。公网服务建议 5~10，内网可放宽到 50~100 |
| `sm2.sdk.timestamp-window-ms` | `30000` | **握手时间戳有效窗口**（毫秒）。握手请求携带的时间戳与服务器当前时间的偏差超过此值则拒绝。防止攻击者截获握手请求后在有效期内重放。公网建议 15~30 秒，内网或时钟偏差大的环境可放宽到 60 秒 |
| `sm2.sdk.max-request-body-size` | `1048576` | **最大请求体大小**（字节，默认 1MB）。SDK 在解密请求体前检查大小，超过限制直接拒绝，防止攻击者发送超大密文耗尽内存。根据业务场景调整，文件上传接口需相应放宽 |
| `sm2.sdk.include-error-detail` | `false` | **错误详情开关**。生产环境必须为 `false`，错误响应仅返回 `code` + `message`（如 `{"code":"29002","message":"签名校验失败"}`）。设为 `true` 后额外返回 `detail` 字段包含异常堆栈信息，**仅调试环境开启**，否则会泄露服务端内部实现细节（加密算法、类名、方法调用链等） |

---

## 八、异常码速查

| 错误码 | HTTP | 含义 | SDK 自动处理 |
|--------|------|------|-------------|
| `11301` | 401 | 会话已过期 | 自动重握手 |
| `11302` | 401 | 请求次数超限 | 自动重握手 |
| `22301` | 401 | 服务端会话不存在 | 自动重握手 |
| `21202` | 400 | TAG 校验失败 | 废弃会话 + 重握手 |
| `21103` | 400 | 服务端证书验签失败 | 检查对方公钥配置是否正确 |
| `11108` | 408 | 握手超时 | 检查网络 |
| `19003` | 503 | 握手熔断 | 30s 后自动恢复 |
| `29001` | 403 | Nonce 重放 | 安全告警 |
| `22502` | 403 | 客户端无权访问该路径 | 检查 client-access 配置或 clientId |
| `31401` | 500 | 私钥未配置 | 检查配置 |

---

## 九、项目结构

```
sm2-sdk/                              # SDK 主项目
├── pom.xml                           # 父 POM（版本管理 + 混淆配置）
├── proguard.pro                      # ProGuard 混淆规则
├── core/                             # 核心加解密（零框架依赖）
│   └── src/main/java/com/sm2sdk/core/
│       ├── access/                       # 客户端访问控制接口
│       ├── annotation/Sm2Secured.java    # @Sm2Secured 注解
│       ├── crypto/                       # SM2/SM3/SM4 加解密
│       ├── exception/                    # 错误码定义
│       ├── model/                        # 数据模型
│       ├── nonce/                        # Nonce 防重放
│       ├── session/                      # 会话管理
│       └── util/Sm2KeyGen.java           # 密钥生成工具
├── client/                           # HTTP 客户端（Sm2HttpClient）
├── spring-boot-starter/              # Boot 2.7 自动配置（javax.servlet）
└── spring-boot3-starter/             # Boot 3.x 自动配置（jakarta.servlet）

tools/                                # 密钥生成脚本
├── keygen.bat
└── keygen.sh

sm2-sdk-demo-boot2/                   # Boot 2.7 演示项目（JDK 8）
├── pom.xml                           # 引入 sm2-sdk-spring-boot-starter
├── application.yml                   # SDK 配置
└── src/main/java/.../DemoController.java   # 演示端点

sm2-sdk-demo-boot3/                   # Boot 3.x 演示项目（JDK 17）
├── pom.xml                           # 引入 sm2-sdk-spring-boot3-starter
├── application.yml                   # SDK 配置
└── src/main/java/.../DemoController.java   # 演示端点
```

## 十、技术实现与安全保障

### 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        业务层                                    │
│  @Sm2Secured 注解  │  Sm2HttpClient  │  SessionManager API       │
├─────────────────────────────────────────────────────────────────┤
│                        安全会话层                                 │
│  三次握手协商  │  会话生命周期  │  密钥轮换  │  Nonce 防重放       │
├─────────────────────────────────────────────────────────────────┤
│                        密码算法层                                 │
│  SM2 非对称（密钥交换/签名验签）│ SM3 哈希  │ SM4 对称（数据加解密）│
└─────────────────────────────────────────────────────────────────┘
```

### 通信全流程

```
			客户端 (Client)                          服务端 (Server)
    │                                        │
    │  ① SM2 握手 — 密钥协商                   │
    │  ──── HandshakeInit ──────────────►     │  验证签名 + 生成临时密钥
    │  ◄──── HandshakeServerResp ────────     │
    │  验证 SB + 派生共享密钥                    │
    │  ──── HandshakeConfirm ───────────►     │  验证 SA
    │                                        │
    │  ✅ 会话建立，双方持有相同 SM4 会话密钥      │
    │                                        │
    │  ② 业务数据 — SM4 对称加密                 │
    │  ──── {加密密文} + X-Session-Id ───►     │  SM4 解密 → 处理 → SM4 加密响应
    │  ◄──── {加密响应} ─────────────────     │
    │                                        │
    │  ③ 会话维护                              │
    │  自动续期 / 过期重握手 / 次数限制           │
```

### 密钥分层与安全等级

SDK 采用**三层密钥架构**，每层有独立的生命周期和泄露影响范围：

```
层级          密钥               生命周期          泄露影响
───────────────────────────────────────────────────────────
L1 静态密钥   SM2 公私钥对        长期（手动更换）    需重新分发公钥，但历史会话安全
L2 临时密钥   SM2 临时密钥对      单次握手（秒级）    仅影响当次握手
L3 会话密钥   SM4 对称密钥        单次会话（默认5分钟） 仅泄露该会话内的数据
```

**核心原则：密钥隔离，逐层缩小爆炸半径——任何一层密钥泄露，不会导致全量数据暴露。**

| 泄露场景 | 影响范围 | 原因 |
|----------|---------|------|
| SM4 会话密钥泄露 | 仅该会话内的数据 | 每个会话独立生成 SM4 密钥 |
| SM2 临时私钥泄露 | 仅当次握手 | 临时密钥一次性使用后立即清除 |
| SM2 静态私钥泄露 | **不影响历史会话** | 会话密钥通过 ECDH 派生，具备前向安全性 |
| 历史密文被截获 | 无法解密 | 会话过期后 SM4 密钥已销毁 |

### 关键技术要点

#### 1. SM2 密钥交换 — 不传输密钥，如何协商出相同的密钥？

核心原理是 **ECDH（椭圆曲线 Diffie-Hellman）**：双方各自持有私钥和对方的公钥，通过椭圆曲线运算得出相同的秘密值，而这个值无法被中间人反推。

##### 直观类比

**基点 G（生成元）**：整条曲线全局固定、写死在国密标准 GM/T 0003.5，所有人用同一个 G；分为Gx 和 Gy

想象一个简单的"颜色混合"过程：

```
公开的黄色颜料 = 椭圆曲线基点 G
Alice 的红色 = 私钥 dA       →  公钥 = 红+黄 = 橙色（可公开）  私钥点乘G生成公钥
Bob 的蓝色 = 私钥 dB         →  公钥 = 蓝+黄 = 绿色（可公开）

Alice 计算: 红 + 绿(Bob的公钥) = 红+蓝+黄 = 棕色
Bob 计算:   蓝 + 橙(Alice的公钥) = 蓝+红+黄 = 棕色
                        ↑ 相同！但窃听者只有 橙 和 绿，无法分离出 棕色
```

数学上利用了椭圆曲线的性质：`dA × (dB × G) = dB × (dA × G)`，运算顺序可交换但不可逆。

##### 完整握手流程

**前置条件**：双方已交换静态公钥（配在 `peers[].public-key` 中）。

```
客户端 (Client)                              服务端 (Server)
持有: dC (静态私钥), PS (服务端静态公钥)       持有: dS (静态私钥), PC (客户端静态公钥)
```

**步骤 ①：客户端生成临时密钥并构建请求**

```
客户端:
  1. 生成临时密钥对 (rA, RA)，其中 RA = [rA]G
  2. 计算 ZA = SM3(客户端身份 + 客户端静态公钥坐标)     ← 身份摘要，绑定密钥与身份
  3. 构造签名消息: M = RA || clientId || ZA || timestamp
  4. 用客户端静态私钥 dC 对 M 签名 → signature
  5. 构建 HandshakeInit { clientId, RA, ZA, timestamp, signature }
  6. 发送到服务端 POST /handshake/init
```

**步骤 ②：服务端验证并生成响应**

```
服务端:
  1. 验证时间戳偏差 ≤ 300s
  2. 从 peers 配置中按 clientId 查找客户端静态公钥 PC
  3. 用 PC 验证签名 → 确认客户端身份
  4. 生成临时密钥对 (rB, RB)，其中 RB = [rB]G
  5. 计算 ZB = SM3(服务端身份 + 服务端静态公钥坐标)
  6. 计算共享密钥点:
         (x1, y1) = [dB + rB] × (PC + RA)
                  = [dB + rB] × ([dC]G + [rA]G)
                  = [dB + rB] × [dC + rA]G
                  = [(dB + rB)(dC + rA)]G
  7. 计算确认值 SB = SM3(0x02 || y1 || SM3(x1 || ZA || ZB || RA || RB))
  8. 构建响应 { sessionId, RB, SB }
  9. 发送回客户端
```

**步骤 ③：客户端计算相同的共享密钥并验证确认值**

```
客户端:
  1. 用服务端静态公钥 PS 验证 RB 参数
  2. 计算 ZB = SM3(服务端身份 + PS 坐标)
  3. 计算共享密钥点:
         (x1, y1) = [dC + rA] × (PS + RB)
                  = [dC + rA] × ([dS]G + [rB]G)
                  = [(dC + rA)(dS + rB)]G    ← 和服务端的完全一样！
  4. 计算期望的 SB' = SM3(0x02 || y1 || SM3(x1 || ZA || ZB || RA || RB))
  5. 对比 SB' == SB → 验证服务端确实持有 dS
  6. 会话密钥 SM4_Key = KDF(x1, y1, ZA, ZB)     ← 从共享点派生
  7. 计算确认值 SA = SM3(0x03 || y1 || SM3(x1 || ZA || ZB || RA || RB))
  8. 发送 HandshakeConfirm { sessionId, SA }
```

**步骤 ④：服务端验证客户端确认**

```
服务端:
  1. 计算期望的 SA' = SM3(0x03 || y1 || SM3(x1 || ZA || ZB || RA || RB))
  2. 对比 SA' == SA → 验证客户端确实持有 dC
  3. 双方会话激活，使用相同的 SM4_Key 进行后续通信
```

##### 为什么两端能算出相同的 x1？

关键在于 ECDH 的双线性性质：

```
服务端: (dS + rB) × (dC·G + rA·G) = (dS + rB)(dC + rA)·G
客户端: (dC + rA) × (dS·G + rB·G) = (dC + rA)(dS + rB)·G

相同的点坐标 (x1, y1)
```

##### 为什么窃听者无法得到密钥？

窃听者通过网络抓包只能拿到：

| 可截获 | 不可截获 |
|--------|---------|
| RA（客户端临时公钥） | rA（客户端临时私钥） |
| RB（服务端临时公钥） | rB（服务端临时私钥） |
| PC（客户端静态公钥） | dC（客户端静态私钥） |
| PS（服务端静态公钥） | dS（服务端静态私钥） |

要计算 `x1 = (dS + rB)(dC + rA)·G`，必须知道私钥之一。但 `dS`、`dB` 从未传输，`rA`、`rB` 用后即焚。攻击者需要从 `RA = [rA]G` 反推 `rA`，这等价于解决**椭圆曲线离散对数问题（ECDLP）**，以当前计算能力无法在合理时间内完成。

##### 双向身份认证

| 验证步骤 | 验证方 | 验证内容 | 防护攻击 |
|----------|--------|---------|---------|
| 签名验证 | 服务端 | 客户端用 dC 对握手消息签名 | 防止伪造客户端 |
| SB 确认 | 客户端 | 服务端用 dS+ZB 计算确认值 | 防止伪造服务端 |
| SA 确认 | 服务端 | 客户端用 dC+ZA 计算确认值 | 双向完成，确认密钥一致 |

#### 2. SM4 对称加密（数据层）

- 使用 SM2 握手协商出的共享密钥作为 SM4 密钥
- 每次请求独立 IV，防止密文分析
- TAG 校验防篡改（SM4 GCM 模式等效）

#### 3. 会话安全策略

| 策略 | 配置项 | 默认值 | 作用 |
|------|--------|--------|------|
| 空闲超时 | `session-timeout-ms` | 5 分钟 | 无活动自动销毁，缩小密钥暴露窗口 |
| 最大生命周期 | `max-session-lifetime-ms` | 1 小时 | 强制密钥轮换 |
| 请求次数限制 | `max-session-requests` | 1000 次 | 单会话使用上限，防止密钥过度使用 |
| 最大并发会话 | `max-sessions` | 10000 | 防止资源耗尽 |
| Nonce 防重放 | 内置 | 开启 | 每次请求唯一 Nonce，Redis 模式下全集群生效 |

#### 4. 透明无感接入

```
开发者的代码                              SDK 自动完成
────────────────────────────────────────────────────────
@Sm2Secured                            → 请求自动解密
@PostMapping("/api/echo")              → 响应自动加密
public Map echo(@RequestBody Map b) {  → 会话自动管理
    return b;                          → 过期自动重握手
}                                      → 异常自动处理
```

- **注解驱动**：`@Sm2Secured` 一个注解搞定加解密，业务代码只处理明文
- **零代码握手**：Sm2HttpClient 内部自动完成三次握手，对调用方透明
- **自动续期**：会话过期前自动续期，长连接不断
- **选择性加密**：未标注 `@Sm2Secured` 的端点完全不受影响，同一 Controller 内可混用

#### 5. 安全边界说明

| 防护项 | 机制 | 说明 |
|--------|------|------|
| 密钥传输安全 | SM2 密钥交换 | 私钥从不出现在网络中 |
| 数据机密性 | SM4 对称加密 | 所有业务数据密文传输 |
| 数据完整性 | SM3 + TAG 校验 | 防篡改、防伪造 |
| 防重放攻击 | Nonce + 时间戳 | 每次请求唯一标识 |
| 身份认证 | SM2 签名 + ZA/ZB | 双向身份确认 |
| 前向安全 | ECDH 临时密钥 | 历史会话不可追溯 |
| 访问控制 | clientId + 路径规则 | 细粒度权限管控 |
| 会话隔离 | 独立 SM4 密钥 | 会话间数据完全隔离 |
| 密钥清除 | MemoryCleanUtil | 密钥使用后立即从内存擦除 |

#### 6. 密码学参数

| 算法 | 用途 | 参数 |
|------|------|------|
| SM2 | 密钥交换 / 数字签名 | 256 位椭圆曲线（sm2p256v1） |
| SM3 | 哈希 / 身份摘要 / 确认值 | 256 位输出 |
| SM4 | 对称加解密 | 128 位密钥，CBC/CTR 模式 |

---

## 兼容性

| JDK | Spring Boot | 使用 |
|-----|-------------|------|
| 8 / 11 | 2.7 | `sm2-sdk-spring-boot-starter` |
| 17 / 21 | 3.x | `sm2-sdk-spring-boot3-starter` |
| 8+ | 无 | `sm2-sdk-core` |

## 十一、最简接入指南

以下以 **Spring Boot 3.x** 为例，展示从零开始的最简接入流程。Spring Boot 2.7 步骤相同，只需把 `spring-boot3-starter` 换成 `spring-boot-starter`。

### 准备工作

**前置条件**：JDK 17+、Maven 3.6+、一个 Spring Boot 3.x 项目。

---

### 服务端最简接入（4 步）

#### 第 1 步：安装 SDK 到本地仓库，配置 pom.xml

```bash
# 在 sm2-sdk 项目根目录执行
cd sm2-sdk
mvn clean install -DskipTests
```

然后在你的项目 `pom.xml` 添加：

```xml
<dependency>
    <groupId>io.github.mtooo</groupId>
    <artifactId>sm2-sdk-spring-boot3-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

> Maven 会自动引入 bcprov、hutool（crypto/http/bloomFilter/json）、caffeine 等传递依赖，无需手动声明。

#### 第 2 步：生成密钥

```bash
java -cp lib/sm2-sdk-spring-boot3-starter-1.0.0.jar io.github.mtooo.core.util.Sm2KeyGen
```

输出：

```
sm2-private-key: 10f5854d3844797a57e53e7d27db088479566394ff5b694efbf17be3189dc90b
sm2-public-key:  04ecc36a8b58afceda42755c86f42cd5692eeb91aa27bd8082b9b8c7320ab0cc7cd9...
```

**私钥自己保留，公钥发给客户端。**

#### 第 3 步：最简配置

```yaml
# application.yml
sm2:
  sdk:
    sm2-private-key: "10f5854d3844797a57e53e7d27db088479566394ff5b694efbf17be3189dc90b"
    sm2-public-key:  "04ecc36a8b58afceda42755c86f42cd5692eeb91aa27bd8082b9b8c7320ab0cc7cd9..."
    server-id: my-server

    # === 分布式部署（可选） ===
    redis-session-store: true           # 启用 Redis 存储会话密钥（多实例必开）
    redis-key-prefix: sm2-server        # Redis 键前缀，多个 SDK 实例建议区分
    local-secret-key: <sm4-key-base64>  # 对 Redis 中的密钥做 SM4 二次加密，防止脱库泄露

    # === 安全加固（可选，生产建议配置） ===
    handshake-rate-limit-per-second: 10   # 每秒最大握手数，超过返回 408
    timestamp-window-ms: 30000            # 握手时间戳有效期(ms)，防止重放
    max-request-body-size: 1048576        # 请求体上限(字节)，防止大密文 OOM
    include-error-detail: false           # 生产环境必须 false，不泄露内部信息
```

就这三行。其余全部使用默认值。

#### 第 4 步：写一个受保护的接口

```java
package com.example.demo;

import io.github.mtooo.core.annotation.Sm2Secured;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class MyController {

    @Sm2Secured
    @PostMapping("/api/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        body.put("serverTime", System.currentTimeMillis());
        return body;
    }

    @GetMapping("/api/health")  // 明文，不受影响
    public String health() {
        return "OK";
    }
}
```

**启动项目，服务端接入完成。** SDK 自动注册了 `/handshake/init`、`/handshake/confirm` 握手端点，`@Sm2Secured` 标注的接口自动加解密。

---

### 客户端最简接入（4 步）

假设你已经有一个 Spring Boot 应用需要调用上面的服务端。

#### 第 1 步：安装 SDK 到本地仓库，配置 pom.xml

同服务端第 1 步。

#### 第 2 步：生成密钥

同服务端第 2 步，客户端自己也生成一对密钥。**客户端的公钥发给服务端，配在服务端的 peers 中。**

#### 第 3 步：最简配置

```yaml
# application.yml
sm2:
  sdk:
    sm2-private-key: "821bfc8d..."     # 客户端自己的私钥
    sm2-public-key:  "04d8eeaf..."     # 客户端自己的公钥
    client-id: my-client               # 告诉服务端"我是谁"

    # === 分布式部署（可选） ===
    redis-session-store: true           # 启用 Redis 存储会话密钥（多实例必开）
    redis-key-prefix: sm2-client        # Redis 键前缀，与对端区分避免冲突
    local-secret-key: <sm4-key-base64>  # 对 Redis 中的密钥做 SM4 二次加密

    # === 纯客户端模式（推荐，减小攻击面） ===
    server-role: false                  # 不暴露 /handshake 端点，只主动调用
    handshake-rate-limit-per-second: 20   # 客户端主动握手频率一般更高，可适当放宽
    

    peers:
      - public-key: "04ecc36a..."      # 服务端的公钥（第 2 步服务端生成的）
        server-url: "http://localhost:8080"
        server-id: my-server           # 必须和服务端的 server-id 一致
```

#### 第 4 步：发起加密调用

```java
package com.example.demo;

import io.github.mtooo.client.Sm2HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
public class ClientController {

    @Autowired
    private Sm2HttpClient sm2Client;

    @GetMapping("/call-echo")
    public Map callEcho() {
        // 自动握手 → 加密请求 → 解密响应 → 返回明文
        return sm2Client.post("/api/echo")
                .body(Map.of("msg", "hello"))
                .execute(Map.class);
    }
}
```

**启动项目，客户端接入完成。** `sm2Client.post("/api/echo")` 内部自动完成握手和加解密，对调用方完全透明。

---

### 两端对配速查

| 配置项 | 服务端 | 客户端 |
|--------|--------|--------|
| `sm2-private-key` | 服务端自己的私钥 | 客户端自己的私钥 |
| `sm2-public-key` | 服务端自己的公钥（给客户端） | 客户端自己的公钥（给服务端） |
| `server-id` | 自己作为服务端的标识 | 可选（自己不做服务端就不配） |
| `client-id` | 可选 | 告诉服务端"我是谁" |
| `peers[].public-key` | 客户端的公钥 | 服务端的公钥 |
| `peers[].server-url` | 可选 | 服务端地址 |
| `peers[].server-id` | 客户端的 `server-id`（如果有） | 必须 = 服务端的 `server-id` |

### 完整可用的最简项目

一个完整的单模块客户端项目只需要 3 个文件：

```
my-client/
├── pom.xml                     ← 标准 Maven 依赖
├── application.yml             ← 上面第 3 步的配置
└── src/main/java/com/example/
    ├── Application.java        ← @SpringBootApplication
    └── ClientController.java   ← 上面第 4 步的代码
```

`pom.xml` 最小示例：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <groupId>com.example</groupId>
    <artifactId>my-client</artifactId>
    <version>1.0.0</version>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>io.github.mtooo</groupId>
            <artifactId>sm2-sdk-spring-boot3-starter</artifactId>
            <version>1.0.0</version>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 十二、攻击防范与安全加固

SDK 内置多层安全防护，防范常见攻击手段。以下按攻击类型说明防护机制和配置方式。

### 1. DoS / 资源耗尽

**攻击方式**：攻击者高频调用 `/handshake/init`，每次握手涉及 SM2 椭圆曲线运算（CPU 密集）、会话创建（内存/Redis 写入），并发请求可耗尽服务端资源。

**防护机制**：

| 防护层 | 机制 | 默认值 | 配置项 |
|--------|------|--------|--------|
| 握手速率限制 | 每个客户端每秒最大握手请求数，超出返回错误 | 5 次/秒 | `sm2.sdk.handshake-rate-limit-per-second` |
| 请求体大小限制 | BodyAdvice 解密前检查请求体大小 | 1 MB | `sm2.sdk.max-request-body-size` |
| 最大并发会话 | 全局会话数上限 | 10000 | `sm2.sdk.max-sessions` |
| 会话自动清理 | 过期会话定时回收 | 60 秒间隔 | `sm2.sdk.session-cleanup-interval-ms` |

```yaml
sm2:
  sdk:
    # 每秒最多允许 5 次握手（公网高安全场景），超过返回 408
    # 单次 SM2 握手从椭圆曲线计算到会话创建约 5~20ms，5 次/秒约消耗 100ms CPU/秒
    handshake-rate-limit-per-second: 5
    # 请求体超过 512KB 直接拒绝，防止解密大文件时 OOM
    max-request-body-size: 524288
    # 全局最多 5000 个活跃会话，防止会话表无限膨胀
    max-sessions: 5000
```

### 2. 重放攻击

**攻击方式**：截获合法握手请求，在有效时间窗口内重复发送，尝试建立多个会话或耗尽资源。

**防护机制**：

| 防护层 | 机制 | 默认值 | 配置项 |
|--------|------|--------|--------|
| 时间戳窗口 | 握手请求时间戳必须在有效窗口内（默认 30 秒），超时拒绝 | 30 秒 | `sm2.sdk.timestamp-window-ms` |
| Nonce 防重放 | 每次请求携带唯一 Nonce，已使用过的 Nonce 被拒绝 | 启用 | 内置（Redis 模式下全集群生效） |
| 时钟偏差检测 | 时间戳在未来超过窗口值 → 拒绝（防止时钟不同步被利用） | 内置 | — |

```yaml
sm2:
  sdk:
    # 握手请求时间戳必须在 15 秒内（高安全环境）
    # 更短的窗口意味着更小的重放窗口，但也要求客户端和服务器时钟同步良好
    # 如果客户端服务器时钟偏差经常超过 15 秒，考虑放宽到 30 秒
    timestamp-window-ms: 15000
```

### 3. 中间人攻击 / 身份伪造

**攻击方式**：伪造握手请求（缺少合法私钥），尝试与服务端建立加密会话。

**防护机制**：

| 防护层 | 机制 |
|--------|------|
| SM2 签名验证 | 客户端用静态私钥对握手消息签名，服务端用其公钥验签 → 伪造请求被拒绝 |
| SB 确认值 | 服务端用 `dB + rB` 计算确认值，客户端验证 → 确认服务端持有合法私钥 |
| SA 确认值 | 客户端计算 SA，服务端验证 → 双向身份确认，握手确认不再是空校验 |
| HandshakeResult 暂存 | 握手结果仅在内存保留 30 秒，验证后立即销毁 → 防止密钥材料泄露 |

> **v1.0.0+ 安全增强**：`/handshake/confirm` 已修复为空校验漏洞。服务端现在暂存 HandshakeResult，并调用 `Sm2KeyExchange.verifyConfirm()` 完整验证客户端 SA 值，验证通过后立即清除密钥材料。

### 4. 信息泄露

**攻击方式**：通过异常消息推断内部实现细节（如"SM4 解密 TAG 校验失败"泄露加密模式）。

**防护机制**：

| 防护层 | 机制 | 配置项 |
|--------|------|--------|
| 异常详情隐藏 | 生产模式下错误响应仅包含 `code` + `message`，不暴露 `detail` | `sm2.sdk.include-error-detail`（默认 false） |
| 通用兜底异常 | 未处理异常统一返回 `{"code": "99999", "message": "未知错误"}`，不暴露堆栈 | 内置 |

```yaml
sm2:
  sdk:
    include-error-detail: true    # ⚠️ 仅调试环境开启，生产必须为 false
```

生产模式错误响应：
```json
{"code": "29002", "message": "签名校验失败"}
```

调试模式错误响应（`include-error-detail: true`）：
```json
{"code": "29002", "message": "签名校验失败", "detail": "客户端签名验证失败: ..."}
```

### 5. JSON 反序列化安全

**攻击方式**：通过构造恶意超大 JSON 请求体导致内存耗尽（DoS）。

**防护机制**：

| 防护层 | 机制 | 默认值 |
|--------|------|--------|
| 请求体大小限制 | `Sm2EncryptedBodyConverter` 反序列化前检查 Body 字节数 | 最大 1MB |
| Content-Type 隔离 | 仅处理 `text/plain`（密文请求），不干扰正常 JSON 处理 | — |
| Hutool JSON | SDK 内部使用 Hutool JSON（`JSONUtil`）替代 Jackson，无 gadget chain 风险 | — |

### 6. 未授权端点暴露

**攻击方式**：第三方引入 SDK JAR 后，`/handshake/init` 和 `/handshake/confirm` 端点自动注册，成为攻击面。

**防护机制**：

| 防护层 | 机制 | 配置项 |
|--------|------|--------|
| 服务端角色开关 | 纯客户端应用可关闭服务端角色，不注册握手端点和拦截器 | `sm2.sdk.server-role`（默认 true） |
| @Sm2Secured 隔离 | 仅标注 `@Sm2Secured` 的端点走加解密管线，其余端点完全不受影响 | — |

```yaml
sm2:
  sdk:
    # ===== 安全加固配置（v1.0.0+） =====

    # 【服务端角色开关】默认 true
    # 设为 false 后 SDK 不注册握手端点、拦截器、Filter、异常处理器，
    # 仅保留 Sm2HttpClient 客户端能力，适用于只作为客户端调用别人的场景。
    # true  → 暴露 /handshake/init、/handshake/confirm，等待客户端来握手
    # false → 不暴露任何端点，只主动发起调用（更安全，攻击面更小）
    server-role: false

    # 【握手速率限制】默认 5，单位：次/秒
    # SM2 握手涉及椭圆曲线点乘运算（CPU 密集）和会话存储写入（Redis/内存 I/O），
    # 高频握手请求可在数秒内耗尽 CPU 和内存。此限制为全局限制，所有来源的握手
    # 请求共享此配额，超出返回 408 错误。
    # 公网暴露的服务建议 5，内网服务可放宽到 10，压测时可临时调高。
    handshake-rate-limit-per-second: 5

    # 【握手时间戳有效窗口】默认 30000，单位：毫秒
    # 客户端在 HandshakeInit 中携带请求发出的时间戳，服务端计算
    #"当前时间 - 请求时间戳"，超出窗口则拒绝。防止攻击者截获合法握手请求后
    # 在有效期内重放（如短时间内重复发送同一个 init 消耗服务端资源）。
    # 公网建议 15~30 秒（15000~30000），内网或时钟偏差较大的环境可放宽到 60 秒。
    timestamp-window-ms: 30000

    # 【最大请求体大小】默认 1048576，单位：字节（1 MB）
    # Sm2RequestBodyAdvice 在解密请求体之前先检查原始密文大小，
    # 超过限制直接拒绝（返回 400），防止攻击者发送超大密文导致 OOM。
    # 仅影响走 @Sm2Secured 加解密管线的请求，不拦截普通明文请求。
    # 有文件上传等大数据场景时按需调大。
    max-request-body-size: 1048576

    # 【错误详情开关】默认 false
    # false → 错误响应仅包含 code + message，不暴露任何服务端内部信息
    #         例：{"code":"29002","message":"签名校验失败"}
    # true  → 额外返回 detail 字段，包含异常类名、堆栈、调用链等调试信息
    #         例：{"code":"29002","message":"签名校验失败","detail":"..."}
    # ⚠️ 生产环境必须为 false，否则攻击者可通过异常消息推断：
    #    - 加密算法实现细节（如"SM4-GCM TAG 校验失败"确认了加密模式）
    #    - 框架和库版本（如 Spring 6.2.18、Hutool 5.8.46）
    #    - 代码调用链路（堆栈 trace 暴露类名和方法名）
    #    仅本地调试或内网测试环境临时开启。
    include-error-detail: false
```

纯客户端模式下的 SDK 行为：
- ❌ 不注册 `/handshake/init`、`/handshake/confirm`
- ❌ 不注册 Sm2ServerInterceptor、RequestBodyAdvice、ResponseBodyAdvice
- ❌ 不注册 QueryDecryptFilter、Sm2SdkExceptionHandler
- ✅ Sm2HttpClient 仍可正常使用（主动发起握手 + 加解密）

### 7. 配置安全建议

| 环境 | 建议配置 |
|------|---------|
| **生产-高安全** | `handshake-rate-limit-per-second: 5`, `timestamp-window-ms: 15000`, `include-error-detail: false`, `server-role: true`（服务端）/ `false`（客户端） |
| **生产-标准** | `handshake-rate-limit-per-second: 10`, `timestamp-window-ms: 30000`, `include-error-detail: false` |
| **调试/测试** | `handshake-rate-limit-per-second: 100`, `timestamp-window-ms: 60000`, `include-error-detail: true` |

### 8. 攻击面总结

SDK 引入后的完整攻击面及防护状态：

| 攻击向量 | 风险等级 | 防护状态 |
|----------|---------|---------|
| 握手端点 DoS（高频请求） | 🔴 高 | ✅ 速率限制 + 时间戳窗口 |
| 握手确认绕过（伪造握手） | 🔴 高 | ✅ SA 完整验证 |
| 重放攻击 | 🟠 中 | ✅ Nonce + 时间戳 |
| 敏感信息泄露（异常详情） | 🟠 中 | ✅ 生产模式隐藏 detail |
| 请求体过大（内存耗尽） | 🟠 中 | ✅ Body size 限制 |
| JSON 反序列化 DoS | 🟡 低 | ✅ 请求体大小限制 + Hutool JSON |
| 解密 Oracle（侧信道） | 🟡 低 | ⚠️ 加密失败统一错误码 |
| 未授权端点暴露（纯客户端） | 🟡 低 | ✅ server-role 开关 |
| 会话 ID 暴力猜测 | 🟢 极低 | UUID 随机生成 |

### 9. 新增安全配置项参考

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `sm2.sdk.server-role` | `true` | **服务端角色开关**。`false` 时不注册握手端点、拦截器、Filter、异常处理器，仅保留 `Sm2HttpClient` 客户端能力。纯客户端应用建议设为 `false` 以减小攻击面 |
| `sm2.sdk.handshake-rate-limit-per-second` | `10` | **握手速率限制**（每秒）。SM2 握手涉及椭圆曲线点乘（CPU 密集）+ 会话存储写入（I/O），高频请求可耗尽资源。公网服务建议 5~10，内网可放宽到 50~100 |
| `sm2.sdk.timestamp-window-ms` | `30000` | **握手时间戳有效窗口**（毫秒）。请求时间戳与服务器当前时间偏差超出窗口即拒绝，防止攻击者截获握手请求后重放。建议 15~30 秒，时钟偏差大的环境可放宽 |
| `sm2.sdk.max-request-body-size` | `1048576` | **最大请求体大小**（字节，默认 1MB）。解密前检查密文大小，超过拒绝。防止超大密文耗尽内存。文件上传等大数据场景按需调大 |
| `sm2.sdk.include-error-detail` | `false` | **错误详情开关**。生产必须 `false`（仅返回 code+message），`true` 额外返回堆栈等调试信息。开启会泄露加密算法、框架版本、代码调用链等敏感信息 |

---

## License

Apache License 2.0
