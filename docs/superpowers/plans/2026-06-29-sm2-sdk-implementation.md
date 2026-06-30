# SM2 Security Data Exchange SDK — Implementation Plan (Revised)

> **Revised:** 2026-06-30 to reflect the actual three-project architecture: a standalone core SDK plus two separate Spring Boot integration projects for Boot 2.x (javax.servlet) and Boot 3.x (jakarta.servlet).

**Goal:** Build a complete SM2 Security Data Exchange SDK — a pure Java 8 core SDK for encryption/signing, plus optional Spring Boot integration modules.

**Architecture:** Three independent Maven projects:
- `sm2-sdk/` — Core SDK (core + client), zero Spring, JDK 8 bytecode
- `sm2-sdk-spring-boot2/` — Spring Boot 2.7 integration (javax.servlet, JDK 8)
- `sm2-sdk-spring-boot3/` — Spring Boot 3.2 integration (jakarta.servlet, JDK 17)

**Tech Stack:**
- Core: Java 8, Maven 3.8+, Hutool 5.8.x, BouncyCastle jdk18on, Caffeine 2.9.x, Jackson 2.15.x, SLF4J
- Boot2: Spring Boot 2.7.18, Spring 5.3.31, javax.servlet-api 4.0.1
- Boot3: Spring Boot 3.2.0, Spring 6.1.1, jakarta.servlet-api 6.0.0

---

## Project Architecture

```
security/
├── sm2-sdk/                          # 🔥 Core SDK (pure crypto + HTTP client)
│   ├── pom.xml                       # Parent POM, Java 8 release
│   ├── core/                         # SM2/SM3/SM4 crypto, session, nonce
│   │   └── artifact: sm2-sdk-core
│   └── client/                       # Sm2HttpClient, Sm2Request, circuit breaker
│       └── artifact: sm2-sdk-client
│
├── sm2-sdk-spring-boot2/             # Spring Boot 2.7 integration
│   ├── pom.xml                       # Parent: spring-boot 2.7.18, release 8
│   ├── server/                       # HandlerInterceptor + ResponseBodyAdvice (javax)
│   │   └── artifact: sm2-sdk-server
│   └── starter/                      # Auto-configuration, shaded JAR
│       └── artifact: sm2-sdk-spring-boot-starter
│
└── sm2-sdk-spring-boot3/             # Spring Boot 3.2 integration
    ├── pom.xml                       # Parent: spring-boot 3.2.0, release 17
    ├── server/                       # HandlerInterceptor + ResponseBodyAdvice (jakarta)
    │   └── artifact: sm2-sdk-server3
    └── starter/                      # Auto-configuration, shaded JAR
        └── artifact: sm2-sdk-spring-boot3-starter
```

### Key Design Decisions (vs Original Plan)

| Original Plan | Actual Implementation | Rationale |
|---|---|---|
| Single 4-module Maven project | 3 independent projects | Clean separation; SDK users don't need Spring |
| Dual Servlet Filters (javax+jakarta) | Spring Interceptor + ResponseBodyAdvice | More idiomatic for Spring Boot; no Servlet API coupling |
| @Sm2Api annotation | Path-based config via Sm2ServerConfig | Simpler; skip paths configured in properties |
| ApiAccessProvider SPI | Not implemented (future) | Scope reduction for v1.0 |
| One JAR works everywhere | Separate boot2/boot3 artifacts | Jakarta namespace migration makes single-source impossible |

---

## Task List (Revised)

### Phase 1-5: Core SDK (Completed)
Tasks 1-14: Core module — crypto, session, nonce, utilities — ALL DONE

### Phase 6: Client Module (Completed)
- Task 14: HandshakeRetryHandler — DONE
- Task 15: Sm2Request — DONE
- Task 16: Sm2HttpClient + Sm2ClientConfig — DONE

### Phase 7: Server Module (Completed)
- Task 17: Sm2ServerConfig — DONE
- Task 18: Sm2HandshakeController — DONE
- Task 19: Sm2ServerInterceptor — DONE
- Task 20: Sm2ResponseBodyAdvice — DONE

### Phase 8: Starter Module (Completed)
- Task 21: Sm2SdkProperties — DONE
- Task 22: Sm2SdkAutoConfiguration — DONE
- Task 23: spring.factories / AutoConfiguration.imports — DONE

### Phase 9: Project Split (Completed)
- Task 24: Split into three independent projects (sm2-sdk / boot2 / boot3) — DONE
- Task 25: Separate boot2 (javax+JDK8) and boot3 (jakarta+JDK17) — DONE

### Phase 10: Testing & Quality Gates (In Progress)
- Task 26: Sm2ResponseBodyAdvice unit tests — TODO
- Task 27: Sm2SdkAutoConfiguration unit tests — TODO
- Task 28: Integration tests (end-to-end handshake + encrypt/decrypt) — TODO
- Task 29: Build verification (full mvn clean package, JDK bytecode check) — TODO

---

## Build Order

```bash
# 1. Build core SDK first
cd sm2-sdk && mvn clean install

# 2. Build Spring integrations (require installed SDK)
cd ../sm2-sdk-spring-boot2 && mvn clean package
cd ../sm2-sdk-spring-boot3 && mvn clean package
```

## Usage

| Scenario | Dependency |
|----------|-----------|
| Pure crypto/signing (any JDK 8+) | `com.sm2sdk:sm2-sdk-core` |
| Crypto + HTTP client | `com.sm2sdk:sm2-sdk-core` + `sm2-sdk-client` |
| Spring Boot 2.7 integration (JDK 8) | `com.sm2sdk:sm2-sdk-spring-boot-starter` |
| Spring Boot 3.x integration (JDK 17) | `com.sm2sdk:sm2-sdk-spring-boot3-starter` |

---

## Completion Checklist

- [x] Core SDK builds and tests pass (196 tests)
- [x] Client module builds and tests pass
- [x] Server module builds (boot2 + boot3)
- [x] Starter module builds with shaded dependencies (boot2 + boot3)
- [x] Three independent projects cleanly separated
- [ ] Missing unit tests: Sm2ResponseBodyAdvice, Sm2SdkAutoConfiguration
- [ ] Integration tests: full handshake + encrypt/decrypt flow
- [ ] `mvn clean package` passes for all three projects
- [ ] JDK 8 bytecode verified for core SDK
- [ ] Progress tracking updated
