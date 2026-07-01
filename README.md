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
- 握手端点 `POST /handshake/init`、`POST /handshake/confirm`
- 请求拦截器（自动解密 Body）
- 响应加密处理器（自动加密 Response）

> **重要**：只有标记了 `@Sm2Secured` 注解的 Controller 或方法才会走 SM2 加解密管线，其余端点原样放行。

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

#### 配置方式（YAML）

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

import com.sm2sdk.core.access.Sm2AccessController;
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
import com.sm2sdk.core.access.Sm2AccessController;
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

Demo 项目演示了三种角色在一个应用中并存：

```
SDK 自动注册（无需写代码）
├─ POST /handshake/init       SM2 握手初始化
└─ POST /handshake/confirm    SM2 握手确认

Demo 业务端点
├─ GET  /api/session          测试工具：自动完成自我握手，返回 sessionId
├─ POST /api/encrypt          测试工具：明文 → 密文（手动测试用）
├─ POST /api/echo             @Sm2Secured  被动解密 + 自动加密
├─ GET  /api/peer/info        @Sm2Secured  模拟对方提供的加密接口
└─ GET  /api/call-peer        无注解  明文入口，内部用 Sm2HttpClient 调 /api/peer/info
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
curl http://localhost:8080/api/call-peer
# → {"peerId": "demo-peer", "status": "online", "uptime": 1719792000000}
```

调用链：`callPeer()` → `Sm2HttpClient` 自动握手 → 加密请求 → `/api/peer/info`（@Sm2Secured，自动解密）→ 加密响应 → `Sm2HttpClient` 解密 → 返回明文。

### 运行 Demo 测试

```bash
cd sm2-sdk-demo-boot2 && mvn test   # 8 tests
cd sm2-sdk-demo-boot3 && mvn test   # 8 tests
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

## 七、配置参考

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
| `sm2.sdk.client-access.enabled` | `false` | 启用客户端访问控制 |
| `sm2.sdk.client-access.default-policy` | `allow` | 默认策略：`allow` / `deny` |
| `sm2.sdk.client-access.rules[].client-id` | — | 客户端标识（空 = catch-all） |
| `sm2.sdk.client-access.rules[].paths` | — | 允许的路径模式列表（Ant 风格） |

---

## 八、异常码速查

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
│       ├── annotation/Sm2Secured.java    # @Sm2Secured 注解
│       ├── crypto/                       # SM2/SM3/SM4 加解密
│       ├── model/                        # 数据模型
│       ├── session/                      # 会话管理
│       └── util/Sm2KeyGen.java           # 密钥生成工具
├── client/                           # HTTP 客户端（Sm2HttpClient）
├── spring-boot-starter/              # Boot 2.7 自动配置 + shade（javax.servlet）
└── spring-boot3-starter/             # Boot 3.x 自动配置 + shade（jakarta.servlet）

tools/                                # 密钥生成脚本
├── keygen.bat
└── keygen.sh

sm2-sdk-demo-boot2/                   # Boot 2.7 演示项目（JDK 8）
├── pom.xml                           # 引入 sm2-sdk-spring-boot-starter
├── application.yml                   # SDK 配置
└── src/main/java/.../DemoController.java   # 5 个演示端点

sm2-sdk-demo-boot3/                   # Boot 3.x 演示项目（JDK 17）
├── pom.xml                           # 引入 sm2-sdk-spring-boot3-starter
├── application.yml                   # SDK 配置
└── src/main/java/.../DemoController.java   # 5 个演示端点
```

## 兼容性

| JDK | Spring Boot | 使用 |
|-----|-------------|------|
| 8 / 11 | 2.7 | `sm2-sdk-spring-boot-starter` |
| 17 / 21 | 3.x | `sm2-sdk-spring-boot3-starter` |
| 8+ | 无 | `sm2-sdk-core` |

## License

Apache License 2.0
