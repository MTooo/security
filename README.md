# SM2 安全数据交换 SDK

基于国密 SM2/SM3/SM4 算法的安全数据交换 SDK，提供加解密、签名验签、密钥交换、会话管理、HTTP 客户端及 Spring Boot 自动配置。

**一个依赖，零代码侵入。**

---

## 快速开始（3 步接入）

### 第 1 步：生成密钥对

```bash
# 首次使用先编译核心模块
cd sm2-sdk && mvn clean install -DskipTests

# 生成 SM2 密钥对
mvn exec:java -pl core -Dexec.mainClass="com.sm2sdk.core.util.Sm2KeyGen"
```

输出示例：

```
sm2-private-key: 3f7a8b2c1d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f
sm2-public-key:  04a1b2c3d4e5f6...（130 位十六进制字符串）
```

> 双方各自生成一对。**私钥自己保留，公钥给对方。**

### 第 2 步：引入依赖

**Spring Boot 2.7（JDK 8/11）：**

```xml
<dependency>
    <groupId>com.sm2sdk</groupId>
    <artifactId>sm2-sdk-spring-boot-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Spring Boot 3.x（JDK 17+）：**

```xml
<dependency>
    <groupId>com.sm2sdk</groupId>
    <artifactId>sm2-sdk-spring-boot3-starter</artifactId>
    <version>1.0.0</version>
</dependency>
```

**纯加解密（不依赖 Spring）：**

```xml
<dependency>
    <groupId>com.sm2sdk</groupId>
    <artifactId>sm2-sdk-core</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 第 3 步：配置 application.yml

```yaml
sm2:
  sdk:
    # 本方 SM2 密钥对（第 1 步生成的）
    sm2-private-key: "3f7a8b2c...（64 位十六进制）"
    sm2-public-key:  "04a1b2c3...（130 位十六进制）"

    # 对方服务地址（需要主动调用时才配）
    server-url: "https://peer.example.com"

    # 对方公钥（多对端时使用 peers 配置）
    # 如果只有一个对端，可以通过 peer 配置
    # peers:
    #   - public-key: "04def567...（对方公钥）"
    #     server-url: "https://peer.example.com"
```

**启动应用，SDK 自动生效。** 握手端点、请求解密、响应加密全部自动注册。

---

## 使用方式

### 主动调用（作为客户端，发加密请求）

```java
@RestController
public class PaymentController {

    @Autowired
    private Sm2HttpClient sm2Client;

    public Result submitOrder(OrderRequest order) {
        // GET: 查询
        UserInfo user = sm2Client.get("/api/user/query")
                .param("idCard", "110101199001011234")
                .execute(UserInfo.class);

        // POST: 提交
        Result r = sm2Client.post("/api/payment/submit")
                .body(order)
                .execute(Result.class);

        // PUT: 更新
        sm2Client.put("/api/user/update")
                .body(updateRequest)
                .execute(Void.class);

        // DELETE: 删除
        sm2Client.delete("/api/cache/clear")
                .param("scope", "all")
                .execute(Void.class);

        return r;
    }
}
```

### 被动响应（作为服务端，接收加密请求）

**不需要写任何代码。** SDK 自动解密所有请求、加密所有响应。

不想走加解密通道的路径，在 `Sm2ServerConfig` 中配置排除：

```yaml
sm2:
  sdk:
    # 握手路径自动跳过，无需配置
    # 其他路径如需跳过，可自定义 handshake-init-path / handshake-confirm-path
```

---

## 配置参考

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `sm2.sdk.enabled` | `true` | 是否启用 SDK |
| `sm2.sdk.sm2-private-key` | — | 本方 SM2 私钥（十六进制，必填） |
| `sm2.sdk.sm2-public-key` | — | 本方 SM2 公钥（十六进制） |
| `sm2.sdk.server-url` | — | 对方服务地址（主动调用时配） |
| `sm2.sdk.session-timeout-ms` | `300000` | 会话空闲超时（毫秒） |
| `sm2.sdk.max-session-lifetime-ms` | `3600000` | 会话最大生命周期（毫秒） |
| `sm2.sdk.max-session-requests` | `1000` | 单会话最大请求数 |
| `sm2.sdk.handshake-timeout-ms` | `10000` | 握手超时（毫秒） |
| `sm2.sdk.max-sessions` | `10000` | 最大并发会话数 |
| `sm2.sdk.redis-key-prefix` | `sm2` | Redis 键前缀 |
| `sm2.sdk.redis-session-store` | `false` | 是否启用 Redis 会话存储 |

---

## 异常码速查

| 错误码 | HTTP | 含义 | 处理 |
|--------|------|------|------|
| `11301` | 401 | 会话已过期 | SDK 自动重握手 |
| `11302` | 401 | 请求次数超限 | SDK 自动重握手 |
| `22301` | 401 | 服务端会话不存在 | SDK 自动重握手 |
| `21202` | 400 | TAG 校验失败 | SDK 废弃会话，重新握手 |
| `21103` | 400 | 签名验证失败 | 检查对方公钥 |
| `11108` | 408 | 握手超时 | 检查网络 |
| `19003` | 503 | 握手熔断 | 等待 30s 自动恢复 |
| `29001` | 403 | Nonce 重放 | 安全告警 |
| `31401` | 500 | 私钥未配置 | 检查配置 |
| `39000` | 500 | 未知异常 | 查看日志 |

---

## 项目结构

```
sm2-sdk/
├── core/                          # 核心加解密（零框架依赖）
├── client/                        # HTTP 客户端
├── spring-boot-starter/           # Spring Boot 2.7 自动配置
├── spring-boot3-starter/          # Spring Boot 3.x 自动配置
└── tools/                         # 密钥生成工具

sm2-sdk-demo-boot2/                # Spring Boot 2.7 演示项目
sm2-sdk-demo-boot3/                # Spring Boot 3.x 演示项目
```

## 兼容性

| JDK | Spring Boot | 依赖 artifactId |
|-----|-------------|----------------|
| 8 / 11 | 2.7 | `sm2-sdk-spring-boot-starter` |
| 17 / 21 | 3.x | `sm2-sdk-spring-boot3-starter` |
| 任意 | 无 | `sm2-sdk-core` |

## License

Apache License 2.0
