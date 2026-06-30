# SM2 安全数据对接 SDK — 实现规格 (Revised)

> **修订:** 2026-06-30，匹配实际三项目架构。
> **核心 SDK**：零 Spring 依赖的纯加解密/签名/会话管理库，JDK 8 字节码。
> **Spring 集成**：两个独立项目分别支持 Boot 2.x (javax+JDK8) 和 Boot 3.x (jakarta+JDK17)。

---

## 一、总体架构

### 1.1 项目结构

```
security/
├── sm2-sdk/                          # 🔥 核心 SDK
│   ├── core/       sm2-sdk-core      # SM2/SM3/SM4 加解密、密钥交换、会话管理
│   └── client/     sm2-sdk-client    # 加密 HTTP 客户端 (Sm2HttpClient + Sm2Request)
│
├── sm2-sdk-spring-boot2/             # Spring Boot 2.7 集成
│   ├── server/     sm2-sdk-server    # HandlerInterceptor + ResponseBodyAdvice (javax)
│   └── starter/    sm2-sdk-spring-boot-starter  # 自动配置 + shade
│
└── sm2-sdk-spring-boot3/             # Spring Boot 3.2 集成
    ├── server/     sm2-sdk-server3   # HandlerInterceptor + ResponseBodyAdvice (jakarta)
    └── starter/    sm2-sdk-spring-boot3-starter # 自动配置 + shade
```

### 1.2 核心原则

1. **核心 SDK 零框架依赖**：core + client 不依赖 Spring/Servlet，可用于任何 Java 8+ 项目
2. **Spring 集成分离**：boot2（javax.servlet）和 boot3（jakarta.servlet）是两个独立项目
3. **Spring Interceptor 模式**：服务端使用 `HandlerInterceptor` + `ResponseBodyAdvice`，比 Filter 更贴合 Spring Boot 生态
4. **Shade 重定位**：Starter 中将 Hutool/Jackson/BouncyCastle relocate 到 `com.sm2sdk.third.*`

### 1.3 模块依赖

```
core (zero Spring)
  ↑
client (zero Spring, depends on core)
  ↑
server (Spring Web MVC, provided)  ← 两个版本：boot2/javax 和 boot3/jakarta
  ↑
starter (Spring Boot, auto-config, shaded)
```

---

## 二、HTTP 协议规范

(协议规范保持不变 — 握手 3 次请求、业务请求加密信封格式、密钥失效恢复流程、POST 幂等性等，详见 `数据对接方案.md`)

---

## 三、核心模块规格

### 3.1 模块组成

| 类 | 职责 |
|----|------|
| `Sm2KeyExchange` | SM2 密钥协商接口 |
| `Sm4Crypto` | SM4-GCM 加解密接口 |
| `KeyDerivation` | KDF 密钥派生 (GB/T 32918.4 Ch5) |
| `HutoolSm2KeyExchange` | Hutool + BouncyCastle SM2 实现 |
| `HutoolSm4Crypto` | Hutool SM4-GCM 实现 |
| `Session` | 会话对象 (密钥仅内存) |
| `SessionStore` | 会话存储接口 |
| `SessionManager` | 会话编排 (握手/加密/解密/续期) |
| `CaffeineSessionStore` | Caffeine 本地缓存实现 |
| `RedisSessionStore` | Redis 分布式实现 (反射调用 Lettuce) |
| `NonceValidator` | Bloom Filter + Redis 两级防重放 |
| `Sm2SdkConfig` | SDK 配置 POJO |
| `ErrorCode` | 35 个 5 位错误码枚举 |
| `Sm2SdkException` | SDK 异常类 |

### 3.2 服务端模块规格

服务端使用 Spring MVC 原生机制，不使用 Servlet Filter：

| 类 | 职责 |
|----|------|
| `Sm2ServerConfig` | 服务端配置 (握手路径、nonce 开关) |
| `Sm2HandshakeController` | 握手端点 `/sm2/handshake/init` `/sm2/handshake/confirm` |
| `Sm2ServerInterceptor` | `HandlerInterceptor` — 解密请求体，验证会话和 Nonce |
| `Sm2ResponseBodyAdvice` | `ResponseBodyAdvice` — 加密响应体 |

**执行流程**：
```
请求 → Interceptor.preHandle()
  1. 跳过握手端点路径
  2. 提取 X-Session-Id / X-Timestamp / X-Nonce
  3. 获取 Session → 校验有效期
  4. Nonce 重放校验 (可选)
  5. 解密 Body → 存入 request.setAttribute()
  6. 放行到 Controller

Controller 返回 → ResponseBodyAdvice.beforeBodyWrite()
  1. 跳过握手端点路径
  2. 从 request 属性获取 Session
  3. 加密响应体 → 返回密文
```

### 3.3 Starter 模块规格

| 类 | 职责 |
|----|------|
| `Sm2SdkProperties` | `@ConfigurationProperties(prefix="sm2.sdk")` |
| `Sm2SdkAutoConfiguration` | `@AutoConfiguration` — 自动装配所有 Bean |

---

## 四、兼容性矩阵

| JDK | Spring Boot | Servlet API | 使用哪个项目 |
|-----|-------------|-------------|-------------|
| 8 | 2.7 | javax | `sm2-sdk-spring-boot2` |
| 11 | 2.7 | javax | `sm2-sdk-spring-boot2` |
| 17 | 2.7 | javax | `sm2-sdk-spring-boot2` |
| 17 | 3.x | jakarta | `sm2-sdk-spring-boot3` |
| 21 | 3.x | jakarta | `sm2-sdk-spring-boot3` |

核心 SDK (`sm2-sdk-core` + `sm2-sdk-client`) 兼容 JDK 8~21，不依赖任何 Servlet/Spring API。

---

## 五、安全规范

(保持不变 — 密钥管理、内存安全、防重放、通信安全、降级策略等详见 `数据对接方案.md`)
