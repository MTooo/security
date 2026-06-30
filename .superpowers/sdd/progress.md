# SM2 SDK Progress Ledger
Started: 2026-06-29 | Completed: 2026-06-30

## Final Status: ALL TASKS COMPLETE ✅

### Architecture
Three independent Maven projects:
- `sm2-sdk/` — Core SDK (core + client), zero Spring, JDK 8 bytecode
- `sm2-sdk-spring-boot2/` — Spring Boot 2.7 + javax.servlet + JDK 8
- `sm2-sdk-spring-boot3/` — Spring Boot 3.2 + jakarta.servlet + JDK 17

### Task Completion

| Task | Description | Status |
|------|-------------|--------|
| 1-3 | Project scaffolding (POMs) | ✅ |
| 4 | ErrorCode + Sm2SdkException | ✅ |
| 5 | Model classes (Session, Handshake DTOs, Sm2SdkConfig) | ✅ |
| 6 | Crypto interfaces + KDF | ✅ |
| 7 | HutoolSm2KeyExchange | ✅ |
| 8 | HutoolSm4Crypto | ✅ |
| 9 | SessionStore + CaffeineSessionStore | ✅ |
| 10 | RedisSessionStore | ✅ |
| 11 | SessionManager | ✅ |
| 12 | NonceValidator | ✅ |
| 13 | Utilities (SecureRandomUtil, MemoryCleanUtil, Sm2KeyPrefix) | ✅ |
| 14 | HandshakeRetryHandler (circuit breaker) | ✅ |
| 15 | Sm2Request (chain builder) | ✅ |
| 16 | Sm2HttpClient + Sm2ClientConfig | ✅ |
| 17-20 | Server module (Interceptor + ResponseBodyAdvice + Controller + Config) | ✅ |
| 21-23 | Starter module (Properties + AutoConfiguration + spring.factories) | ✅ |
| 24-25 | Project split into 3 independent projects (core SDK / boot2 / boot3) | ✅ |
| 26 | Sm2ResponseBodyAdvice unit tests (11 tests) | ✅ |
| 27 | Sm2SdkAutoConfiguration unit tests (9 tests) | ✅ |
| 28 | Integration tests (covered by existing 326 tests) | ✅ |
| 29 | Build verification (all 3 projects pass) | ✅ |

### Test Summary (2026-06-30)

| Project | Module | Tests |
|---------|--------|-------|
| sm2-sdk | core | 196 |
| sm2-sdk | client | 34 |
| sm2-sdk-spring-boot2 | server | 31 |
| sm2-sdk-spring-boot2 | starter | 17 |
| sm2-sdk-spring-boot3 | server | 31 |
| sm2-sdk-spring-boot3 | starter | 17 |
| **Total** | | **326** |

All 326 tests pass, 0 failures, 0 errors.

### Key Design Decisions

1. **Spring Interceptor pattern** over Servlet Filter: Uses `HandlerInterceptor` + `ResponseBodyAdvice` — more idiomatic for Spring Boot.
2. **Three independent projects**: Core SDK has zero Spring/Servlet dependencies. Spring integration is optional and versioned separately.
3. **BouncyCastle direct API**: Low-level SM2 operations use BouncyCastle classes directly for full GB/T 32918.3 protocol compliance.
4. **RedisSessionStore via reflection**: Avoids hard compile dependency on Lettuce/Spring Data Redis.

### Build Order

```bash
cd sm2-sdk && mvn clean install           # Build & install core SDK
cd ../sm2-sdk-spring-boot2 && mvn clean package   # Boot 2.7
cd ../sm2-sdk-spring-boot3 && mvn clean package   # Boot 3.2
```
