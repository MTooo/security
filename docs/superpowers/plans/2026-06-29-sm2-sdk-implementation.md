# SM2 Security Data Exchange SDK — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete SM2 Security Data Exchange SDK as a Maven multi-module project — one JAR that works across JDK 8/11/17 and Spring Boot 2.x/3.x, with both active calling (Sm2HttpClient) and passive response (@Sm2Api annotation + Servlet Filter) capabilities.

**Architecture:** 4-module Maven project: sm2-sdk-core (zero framework deps) → sm2-sdk-client / sm2-sdk-server (Spring/Servlet at provided scope) → sm2-sdk-spring-boot-starter (auto-configuration). Hutool and Jackson shaded+relocated via maven-shade-plugin. Dual javax/jakarta Filters coexist with @ConditionalOnClass runtime selection.

**Tech Stack:** Java 8, Maven 3.8+, Hutool 5.8.x (SM2/SM3/SM4 + HttpUtil), Caffeine 2.9.x, Jackson 2.15.x, SLF4J (provided), javax.servlet-api 4.0.1 (provided), jakarta.servlet-api 6.0.0 (provided), Spring Boot 2.7+/3.x (provided), maven-shade-plugin 3.5.x

## Global Constraints

- Source/target compatibility: Java 8 (`maven.compiler.source=1.8`, `maven.compiler.target=1.8`)
- Single JAR works on JDK 8, 11, 17, 21 without recompilation
- Spring Boot 2.x (javax.servlet) and 3.x (jakarta.servlet) both supported from the same JAR
- Hutool relocated: `cn.hutool` → `com.sm2sdk.third.hutool`
- Jackson relocated: `com.fasterxml.jackson` → `com.sm2sdk.third.jackson`
- Core module: zero Spring/Servlet dependencies
- All cryptographic keys handled as `byte[]`, cleared with `Arrays.fill(key, (byte)0)` after use
- No private keys, shared secrets, or plaintext business data ever logged
- Error codes: 5-digit `ABCDD` format per spec Section 7
- HTTP protocol: all binary fields Base64-encoded, Content-Type: text/plain for encrypted bodies
- Session lifecycle: idle timeout (1800s), max requests (1000), total lifetime (7200s), auto-renewal + rekey
- Nonce anti-replay: Bloom Filter (in-memory per-minute, 5-min window) + Redis SETNX fallback
- Circuit breaker: CLOSED → OPEN (30s cooldown) → HALF_OPEN for handshake failures
- POST idempotency: auto-inject `_idempotencyKey` UUID, server caches response 5 min
- Redis config: reuse `spring.redis.*` standard keys; SDK only adds `redis-key-prefix`
- Package namespace: `com.sm2sdk.core`, `com.sm2sdk.client`, `com.sm2sdk.server`, `com.sm2sdk.starter`
- Module directory: `D:\workspace\security\sm2-sdk\`

## File Structure

```
sm2-sdk/
├── pom.xml                                    — Parent POM (version management + shade config)
├── sm2-sdk-core/
│   └── src/main/java/com/sm2sdk/core/
│       ├── crypto/
│       │   ├── Sm2KeyExchange.java            — Interface: SM2 key exchange operations
│       │   ├── Sm4Crypto.java                 — Interface: SM4-GCM encrypt/decrypt
│       │   ├── KeyDerivation.java             — KDF implementation (GB/T 32918.4 Ch5)
│       │   └── impl/
│       │       ├── HutoolSm2KeyExchange.java  — Hutool SM2 implementation
│       │       └── HutoolSm4Crypto.java       — Hutool SM4-GCM implementation
│       ├── session/
│       │   ├── Session.java                   — Session value object
│       │   ├── SessionStore.java              — Session storage interface
│       │   ├── SessionManager.java            — Session orchestration
│       │   └── impl/
│       │       ├── CaffeineSessionStore.java  — Caffeine local implementation
│       │       └── RedisSessionStore.java     — Redis distributed implementation
│       ├── nonce/
│       │   └── NonceValidator.java            — Bloom Filter + Redis two-level
│       ├── model/
│       │   ├── Sm2SdkConfig.java              — SDK configuration POJO
│       │   ├── HandshakeContext.java          — Handshake context value object
│       │   ├── HandshakeInit.java             — Handshake step 1 request DTO
│       │   ├── HandshakeServerResp.java       — Handshake step 2 response DTO
│       │   └── HandshakeConfirm.java          — Handshake step 3 request DTO
│       ├── exception/
│       │   ├── ErrorCode.java                 — Error code enum (complete table)
│       │   └── Sm2SdkException.java           — SDK exception class
│       └── util/
│           ├── SecureRandomUtil.java          — Secure random generation
│           ├── MemoryCleanUtil.java           — Memory zeroing utility
│           └── Sm2KeyPrefix.java              — Redis key prefix builder
├── sm2-sdk-client/
│   └── src/main/java/com/sm2sdk/client/
│       ├── Sm2HttpClient.java                 — HTTP client facade
│       ├── Sm2Request.java                    — Chained request builder
│       ├── Sm2ClientConfig.java               — Client configuration
│       └── HandshakeRetryHandler.java         — Retry + circuit breaker
├── sm2-sdk-server/
│   └── src/main/java/com/sm2sdk/server/
│       ├── Sm2Api.java                        — @Sm2Api annotation
│       ├── Sm2ServerFilterLogic.java          — Shared filter logic (no Servlet deps)
│       ├── JavaxSm2ServerFilter.java          — javax.servlet.Filter adapter
│       ├── JakartaSm2ServerFilter.java        — jakarta.servlet.Filter adapter
│       ├── ApiAccessProvider.java             — Access control SPI
│       ├── ConfigBasedApiAccessProvider.java  — YAML-based default implementation
│       └── PathMatcher.java                   — Ant-style path matching
└── sm2-sdk-spring-boot-starter/
    └── src/main/java/com/sm2sdk/starter/
        ├── Sm2SdkProperties.java              — @ConfigurationProperties
        ├── Sm2SdkAutoConfiguration.java       — Auto-configuration class
        └── resources/
            └── META-INF/spring.factories       — Spring Boot auto-config entry
```

---

---

## Phase 1: Project Scaffolding

### Task 1: Create Parent POM with Dependency Management

**Files:** Create: `sm2-sdk/pom.xml`

**Produces:** Parent POM defining all dependency versions, module list, and plugin management.

- [ ] **Step 1: Write parent POM**

See the parent POM at the end of this section for full XML content. Key elements:
- GroupId: `com.sm2sdk`, ArtifactId: `sm2-sdk-parent`
- 4 modules: core, client, server, starter
- Java 1.8 source/target
- Dependency management for: Hutool 5.8.32, Jackson 2.15.4, Caffeine 2.9.3, SLF4J 2.0.9, javax.servlet-api 4.0.1, jakarta.servlet-api 6.0.0, Spring Boot 3.2.0, Spring 6.1.1, Lettuce 6.3.2, JUnit 5.10.1
- Plugin management for: maven-compiler 3.12.1, maven-shade 3.5.3, maven-surefire 3.2.5, maven-source 3.3.0, maven-javadoc 3.6.3

- [ ] **Step 2: Verify POM is valid**

Run: `cd D:/workspace/security/sm2-sdk && mvn validate`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**
```bash
git add sm2-sdk/pom.xml && git commit -m "feat: create parent POM with dependency management"
```

### Task 2: Create sm2-sdk-core Module POM

**Files:** Create: `sm2-sdk/sm2-sdk-core/pom.xml`

**Produces:** Core module POM with compile-scope Hutool+Jackson (for shading), Caffeine, and provided SLF4J. Zero Spring/Servlet deps.

- [ ] **Step 1: Write core POM**

Module inherits from parent. Dependencies: hutool-all (compile), jackson-databind (compile), caffeine (compile), slf4j-api (provided), junit-jupiter + mockito (test).

- [ ] **Step 2: Verify**

Run: `cd D:/workspace/security/sm2-sdk && mvn validate -pl sm2-sdk-core`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**
```bash
git add sm2-sdk/sm2-sdk-core/pom.xml && git commit -m "feat: create sm2-sdk-core module POM"
```

### Task 3: Create Client, Server, and Starter Module POMs

**Files:** Create: `sm2-sdk/sm2-sdk-client/pom.xml`, `sm2-sdk/sm2-sdk-server/pom.xml`, `sm2-sdk/sm2-sdk-spring-boot-starter/pom.xml`

**Produces:** All module POMs with correct dependency chains:
- client → core, SLF4J provided
- server → core, javax.servlet-api + jakarta.servlet-api provided, spring-web provided
- starter → core + client + server, spring-boot-autoconfigure provided, spring-data-redis optional + provided

**Key: starter POM includes maven-shade-plugin with Hutool and Jackson relocation.**

- [ ] **Step 1: Write all three POMs**
- [ ] **Step 2: Verify multi-module build**

Run: `cd D:/workspace/security/sm2-sdk && mvn validate`
Expected: BUILD SUCCESS for all 5 modules (parent + 4 children)

- [ ] **Step 3: Commit**
```bash
git add sm2-sdk/sm2-sdk-client/pom.xml sm2-sdk/sm2-sdk-server/pom.xml sm2-sdk/sm2-sdk-spring-boot-starter/pom.xml
git commit -m "feat: create client, server, and starter module POMs with shade plugin"
```


---

## Phase 2: Core Module - Foundation Types

### Task 4: Create ErrorCode Enum and Sm2SdkException

**Files:**
- Create: `sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/exception/ErrorCode.java`
- Create: `sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/exception/Sm2SdkException.java`
- Test: `sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/exception/ErrorCodeTest.java`

**Produces:** Complete 5-digit error code enum (30+ codes from spec Section 7.2) and base SDK exception.

- [ ] **Step 1: Write ErrorCodeTest (TDD)**

```java
package com.sm2sdk.core.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ErrorCodeTest {
    @Test void shouldHaveClientCodes() {
        assertEquals("11000", ErrorCode.CLIENT_INIT_FAILED.getCode());
        assertEquals("21202", ErrorCode.SM4_DECRYPT_TAG_FAILED.getCode());
        assertEquals("11301", ErrorCode.SESSION_EXPIRED.getCode());
        assertEquals("31401", ErrorCode.CLIENT_PRIVATE_KEY_MISSING.getCode());
    }
    @Test void shouldHaveServerCodes() {
        assertEquals("22301", ErrorCode.SESSION_NOT_FOUND.getCode());
        assertEquals("22101", ErrorCode.CLIENT_CERT_VERIFY_FAILED.getCode());
    }
    @Test void shouldHaveGeneralCodes() {
        assertEquals("39000", ErrorCode.UNKNOWN_ERROR.getCode());
        assertEquals("29001", ErrorCode.NONCE_REPLAY.getCode());
        assertEquals("19003", ErrorCode.CIRCUIT_BREAKER_OPEN.getCode());
    }
    @Test void shouldMapHttpStatus() {
        assertEquals(400, ErrorCode.SM4_DECRYPT_TAG_FAILED.getHttpStatus());
        assertEquals(401, ErrorCode.SESSION_EXPIRED.getHttpStatus());
        assertEquals(403, ErrorCode.NONCE_REPLAY.getHttpStatus());
        assertEquals(408, ErrorCode.HANDSHAKE_TIMEOUT.getHttpStatus());
        assertEquals(429, ErrorCode.SESSION_LIMIT_EXCEEDED.getHttpStatus());
        assertEquals(500, ErrorCode.UNKNOWN_ERROR.getHttpStatus());
    }
    @Test void shouldExtractSeverity() {
        assertEquals(1, ErrorCode.CLIENT_INIT_FAILED.getSeverity());
        assertEquals(2, ErrorCode.SM4_DECRYPT_TAG_FAILED.getSeverity());
        assertEquals(3, ErrorCode.CLIENT_PRIVATE_KEY_MISSING.getSeverity());
    }
}
```

- [ ] **Step 2: Run test - FAIL (compilation error, class not found)**

Run: `cd D:/workspace/security/sm2-sdk && mvn test -pl sm2-sdk-core -Dtest=ErrorCodeTest`

- [ ] **Step 3: Write ErrorCode enum with all 33 codes**

All error codes per spec Section 7.2 with fields: code (String), httpStatus (int), message (String). Methods: getCode(), getHttpStatus(), getMessage(), getSeverity() (parsed from first digit).

- [ ] **Step 4: Write Sm2SdkException**

Constructor takes ErrorCode + optional detail/cause/sessionId. Fields: errorCode, httpStatus, sessionId.

- [ ] **Step 5: Run tests - PASS**

- [ ] **Step 6: Commit**
```bash
git add sm2-sdk/sm2-sdk-core/src/
git commit -m "feat: add ErrorCode enum and Sm2SdkException"
```

### Task 5: Create Model Classes

**Files:**
- Create: `sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/session/Session.java`
- Create: `sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/model/HandshakeInit.java`
- Create: `sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/model/HandshakeServerResp.java`
- Create: `sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/model/HandshakeConfirm.java`
- Create: `sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/model/Sm2SdkConfig.java`
- Test: `sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/session/SessionTest.java`

**Produces:** All value objects and DTOs.

- [ ] **Step 1: Write SessionTest**

Test cases: create session, verify fields, touch increments count, expiry detection (idle timeout / max requests / max lifetime), destroy zeros keys, double-destroy idempotent, getSm4KeyCopy returns copy not reference.

- [ ] **Step 2: Run test - FAIL**

- [ ] **Step 3: Write Session.java**

Key design: sm4Key (byte[16]) and sm4Iv (byte[12]) stored in memory only. getSm4KeyCopy() returns defensive copy - caller MUST call Session.clearKeyCopy(byte[]) after use. touch() records access time + increments count. rekey(newKey, newIv) replaces keys + resets count. isExpired(timeoutMs, maxLifetimeMs, maxRequestsLimit) checks triple constraint. destroy() does Arrays.fill zero + sets destroyed flag. All key-access methods synchronized.

- [ ] **Step 4: Write HandshakeInit, HandshakeServerResp, HandshakeConfirm POJOs**

Simple Java beans with fields matching spec Section 2.2 wire format. All binary fields are Base64-encoded String. Include no-arg constructor for JSON deserialization.

- [ ] **Step 5: Write Sm2SdkConfig**

Builder-pattern POJO with all configuration fields from spec Section 6. Fluent setters. Inner classes: PeerConfig (publicKey, serverUrl), ClientAccessConfig (paths list).

- [ ] **Step 6: Run tests - PASS**

- [ ] **Step 7: Commit**
```bash
git add sm2-sdk/sm2-sdk-core/src/
git commit -m "feat: add Session, Handshake DTOs, and Sm2SdkConfig model classes"
```

---
## Phase 3: Core Module - Cryptography

### Task 6: Create Crypto Interfaces and KDF Implementation

Files:
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/crypto/Sm2KeyExchange.java
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/crypto/Sm4Crypto.java
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/crypto/KeyDerivation.java
- Test: sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/crypto/KeyDerivationTest.java

Consumes: ErrorCode, Sm2SdkException, HandshakeInit, HandshakeServerResp, HandshakeConfirm
Produces: Sm2KeyExchange interface, Sm4Crypto interface, KeyDerivation utility class

**Sm2KeyExchange interface** defines:
- buildInitRequest(clientId, clientPrivateKey, serverPublicKey, clientIdentity) -> HandshakeInit
- processServerResponse(sentRequest, serverResp, clientPrivateKey, serverPublicKey, clientIdentity, serverIdentity) -> HandshakeResult
- buildConfirm(result) -> HandshakeConfirm
- processClientInit(clientInit, serverPrivateKey, clientPublicKey, serverIdentity, clientIdentity) -> HandshakeResult
- verifyConfirm(result, confirm) -> boolean
- Inner class HandshakeResult: sessionId, sm4Key(16B), sm4Iv(12B), sharedKey, ZA, ZB, RA, RB

**Sm4Crypto interface** defines:
- encrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext) -> byte[] (IV || ciphertext || TAG)
- decrypt(byte[] key, byte[] iv, byte[] aad, byte[] ciphertextWithTag) -> byte[] plaintext (throws Sm2SdkException on TAG failure)

**KeyDerivation** (GB/T 32918.4-2016 Chapter 5):
- kdf(byte[] z, int klenBits) -> byte[]: SM3-based hash iteration
  - ct = 0x00000001, for i = 1 to ceil(klen/256): Ha[i] = SM3(Z || ct), ct++
  - Output: Ha[1] || Ha[2] || ... first klen bits
- extractSm4Key(derived) -> bytes[0..15]
- extractSm4Iv(derived) -> bytes[16..27]
- extractHmacKey(derived) -> bytes[28..59] (optional)

KeyDerivationTest covers: deterministic output, different inputs produce different outputs, correct length (60 bytes for klen=480), component extraction.

- [ ] Step 1: Write KeyDerivationTest (TDD first)
- [ ] Step 2: Run test (FAIL - class not found)
- [ ] Step 3: Write all three interfaces/classes
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit

### Task 7: Implement HutoolSm2KeyExchange

Files:
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/crypto/impl/HutoolSm2KeyExchange.java
- Test: sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/crypto/impl/HutoolSm2KeyExchangeTest.java

Full SM2 key exchange following GB/T 32918.3-2016 Chapter 6. Implementation steps match spec Section 3.4 exactly:

buildInitRequest: generate (rA,RA), compute ZA = SM3(ENTLA||IDA||a||b||xG||yG||xA||yA), sign with dA over (RA||clientId||ZA||timestamp), return HandshakeInit with Base64(RA), Base64(signature), Base64(ZA)

processServerResponse: decode RB, verify on curve, compute xBarB=2^127+(xRB&(2^127-1)), shared point (x1,y1)=[rA]*(RB+[xBarB]*PB), KDF derive keys, verify SB=SM3(0x02||y1||SM3(x1||ZA||ZB||RA||RB)), return HandshakeResult

processClientInit: verify timestamp(|now-timestamp|<=300s), decode+verify RA and signature, generate (rB,RB), compute ZB, shared point (x1,y1)=[rB]*(RA+[xBarA]*PA), KDF derive, generate sessionId=UUID, compute SB, return HandshakeResult

buildConfirm: compute SA=SM3(0x03||y1||SM3(x1||ZA||ZB||RA||RB)), return Base64(SA)

verifyConfirm: compare computed SA with received SA

Critical: all SM2 ops use relocated Hutool (com.sm2sdk.third.hutool.crypto.SmUtil). Ephemeral private keys cleared after use with MemoryCleanUtil.

Test: full roundtrip (generate keys, handshake, both sides same sm4Key), signature verification failures, timestamp validation, point-on-curve checks.

- [ ] Step 1: Write HutoolSm2KeyExchangeTest
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement HutoolSm2KeyExchange
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit

### Task 8: Implement HutoolSm4Crypto

Files:
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/crypto/impl/HutoolSm4Crypto.java
- Test: sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/crypto/impl/HutoolSm4CryptoTest.java

encrypt(): generate 12B random IV, SM4 GCM mode encrypt, return IV(12)||ciphertext||TAG(16)
decrypt(): parse IV(12), TAG(16), ciphertext from input, SM4 GCM decrypt with TAG verify, on failure throw Sm2SdkException(SM4_DECRYPT_TAG_FAILED)

Test: roundtrip, wrong key failure, wrong AAD failure, random IV per encryption, empty body, large body.

- [ ] Step 1: Write HutoolSm4CryptoTest
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement HutoolSm4Crypto
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit


## Phase 4: Core Module - Session & Nonce

### Task 9: Create SessionStore Interface and CaffeineSessionStore

Files:
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/session/SessionStore.java
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/session/impl/CaffeineSessionStore.java
- Test: sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/session/impl/CaffeineSessionStoreTest.java

SessionStore interface: get(sessionId)->Session, put(Session), remove(sessionId), exists(sessionId)->boolean, renew(sessionId)

CaffeineSessionStore: Caffeine cache with expireAfterWrite based on session timeout. renew() updates lastAccessTime and resets requestCount on the Session object.

- [ ] Step 1: Write CaffeineSessionStoreTest
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement both
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit

### Task 10: Create RedisSessionStore

Files:
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/session/impl/RedisSessionStore.java
- Test: sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/session/impl/RedisSessionStoreTest.java

RedisSessionStore uses spring-data-redis (provided scope, optional). Key format: {redisKeyPrefix}:session:{sessionId}. Session serialized as JSON (sm4Key encrypted with local storage key before serialization). TTL synced with session timeout. Fallback: if Redis unavailable, log warning and degrade to local CaffeineSessionStore.

- [ ] Step 1: Write RedisSessionStoreTest (use embedded Redis or mock)
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement RedisSessionStore
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit

### Task 11: Create SessionManager

Files:
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/session/SessionManager.java
- Test: sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/session/SessionManagerTest.java

SessionManager orchestrates the full session lifecycle:
- initiateHandshake(peerId): client-side 3-step handshake -> Session
- handleIncomingHandshake(init): server-side handshake -> Session
- getSession(sessionId): retrieve from store, check expiry, return
- renewSession(sessionId): check conditions, rekey via KDF, update store
- encryptBody(sessionId, plainJson): get session, encrypt, return Base64
- decryptBody(sessionId, encryptedBase64): get session, decrypt, return JSON string
- destroySession(sessionId): remove from store, zero keys

Dependencies: Sm2KeyExchange, Sm4Crypto, SessionStore, Sm2SdkConfig, NonceValidator

- [ ] Step 1: Write SessionManagerTest
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement SessionManager
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit

### Task 12: Create NonceValidator (Bloom Filter + Redis)

Files:
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/nonce/NonceValidator.java
- Test: sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/nonce/NonceValidatorTest.java

Two-level architecture:
- Level 1: In-memory Bloom Filter (per-minute, keep last 5, ~9MB each). Uses relocated Hutool BloomFilter with SM3 hash.
- Level 2: Redis SETNX sm2:{prefix}:nonce:{nonce} EX 300 for exact check.

isDuplicate(nonce): check Bloom -> if "possibly exists", check Redis -> if SETNX returns 0, it IS duplicate -> throw NONCE_REPLAY. If Bloom says "definitely not", pass through.

cleanupExpiredFilters(): remove Bloom filters older than 5 minutes.

- [ ] Step 1: Write NonceValidatorTest
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement NonceValidator
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit

## Phase 5: Core Module - Utilities

### Task 13: Create Utility Classes

Files:
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/util/SecureRandomUtil.java
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/util/MemoryCleanUtil.java
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/util/Sm2KeyPrefix.java

SecureRandomUtil: generateNonce(16), generateIV(12), generateUUID(). Uses SecureRandom with SHA1PRNG or NativePRNG.

MemoryCleanUtil: cleanKey(byte[]), cleanKeys(byte[]...). Arrays.fill with zeros, plus System.gc() hint (best-effort).

Sm2KeyPrefix: buildSessionKey(prefix, sessionId), buildNonceKey(prefix, nonce), buildBloomKey(prefix, minuteTick), buildHandshakeLockKey(prefix, clientId). Single config point for all Redis key patterns.

- [ ] Step 1: Write all three utility classes
- [ ] Step 2: Write unit tests
- [ ] Step 3: Run tests (PASS)
- [ ] Step 4: Commit


## Phase 6: Client Module

### Task 14: Create HandshakeRetryHandler (Circuit Breaker)

Files:
- Create: sm2-sdk/sm2-sdk-client/src/main/java/com/sm2sdk/client/HandshakeRetryHandler.java
- Test: sm2-sdk/sm2-sdk-client/src/test/java/com/sm2sdk/client/HandshakeRetryHandlerTest.java

State machine: CLOSED -> OPEN -> HALF_OPEN -> CLOSED

CLOSED: normal operation. Track consecutive failures. On 5th consecutive failure -> transition to OPEN.
OPEN: all requests immediately fail with Sm2SdkException(CIRCUIT_BREAKER_OPEN). After cooldown (30s) -> HALF_OPEN.
HALF_OPEN: allow 1 probe request. Success -> CLOSED (reset counter). Failure -> OPEN (restart cooldown).

executeWithRetry(Callable): execute the handshake, on failure retry with exponential backoff (1s, 2s, 4s) up to handshakeRetry times. Each attempt checks circuit state.

- [ ] Step 1: Write HandshakeRetryHandlerTest
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement HandshakeRetryHandler
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit

### Task 15: Create Sm2Request (Chain Builder)

Files:
- Create: sm2-sdk/sm2-sdk-client/src/main/java/com/sm2sdk/client/Sm2Request.java
- Test: sm2-sdk/sm2-sdk-client/src/test/java/com/sm2sdk/client/Sm2RequestTest.java

Fluent API:
- param(key, value): add query parameter (for GET/DELETE)
- header(key, value): add custom HTTP header
- body(Object): set JSON body (for POST/PUT)
- execute(Class<T> responseType): execute the request and return deserialized response

Internal execute() flow (spec Section 4.2):
1. Get/create session via SessionManager
2. Check if renewal needed (remainingLifetime < renewThreshold)
3. Build plaintext JSON based on HTTP method (GET: params to JSON, POST/PUT: body to JSON, DELETE: params to JSON). POST auto-injects _idempotencyKey UUID.
4. encryptBody(sessionId, plainJson) -> Base64 ciphertext
5. Build HTTP request: method preserved, URL = serverUrl + path, Headers: X-Session-Id, X-Timestamp, X-Nonce, Content-Type: text/plain, Body: Base64 ciphertext
6. Send via Hutool HttpUtil (using relocated class)
7. Check response: 200 -> decrypt, 401+X-Session-Expired -> rehandshake+retry(once), 400+21202 -> discard session+rehandshake+retry(once)
8. Decrypt response body -> plaintext JSON
9. Deserialize to responseType -> return

- [ ] Step 1: Write Sm2RequestTest (mock SessionManager and HTTP)
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement Sm2Request
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit

### Task 16: Create Sm2HttpClient (Facade)

Files:
- Create: sm2-sdk/sm2-sdk-client/src/main/java/com/sm2sdk/client/Sm2HttpClient.java
- Create: sm2-sdk/sm2-sdk-client/src/main/java/com/sm2sdk/client/Sm2ClientConfig.java
- Test: sm2-sdk/sm2-sdk-client/src/test/java/com/sm2sdk/client/Sm2HttpClientTest.java

Sm2HttpClient is the main entry point for active-calling applications:
- get(path) -> Sm2Request
- post(path) -> Sm2Request
- put(path) -> Sm2Request
- delete(path) -> Sm2Request

Constructor takes Sm2SdkConfig + SessionManager.
Each method creates a Sm2Request pre-configured with the peer's serverUrl.

Sm2ClientConfig: thin wrapper extracting client-relevant settings from Sm2SdkConfig.

- [ ] Step 1: Write Sm2HttpClientTest
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement Sm2HttpClient and Sm2ClientConfig
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit


## Phase 7: Server Module

### Task 17: Create @Sm2Api Annotation

Files:
- Create: sm2-sdk/sm2-sdk-server/src/main/java/com/sm2sdk/server/Sm2Api.java

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Sm2Api {
    String value() default "";
}
```

Usage: mark on method -> only that method uses SM2. Mark on class -> all methods in Controller use SM2. No annotation -> Filter passes through.

- [ ] Step 1: Write annotation
- [ ] Step 2: Verify compilation

### Task 18: Create ApiAccessProvider SPI and Default Implementation

Files:
- Create: sm2-sdk/sm2-sdk-server/src/main/java/com/sm2sdk/server/ApiAccessProvider.java
- Create: sm2-sdk/sm2-sdk-server/src/main/java/com/sm2sdk/server/ConfigBasedApiAccessProvider.java
- Create: sm2-sdk/sm2-sdk-server/src/main/java/com/sm2sdk/server/PathMatcher.java

ApiAccessProvider interface: check(clientId, path, method)->boolean, getAllowedPaths(clientId)->List<String>, default refresh() {}

ConfigBasedApiAccessProvider: reads sm2-sdk.access.* from yml. Uses AntPathMatcher for path matching (/api/user/**, /api/order/query). Supports default-policy: deny|allow. Caches parsed rules.

PathMatcher: thin wrapper around AntPathMatcher (or manual Ant-style matching to avoid Spring dependency at compile time).

- [ ] Step 1: Write all three classes
- [ ] Step 2: Write unit tests for ConfigBasedApiAccessProvider
- [ ] Step 3: Run tests (PASS)
- [ ] Step 4: Commit

### Task 19: Create Sm2ServerFilterLogic (No Servlet Dependencies)

Files:
- Create: sm2-sdk/sm2-sdk-server/src/main/java/com/sm2sdk/server/Sm2ServerFilterLogic.java
- Test: sm2-sdk/sm2-sdk-server/src/test/java/com/sm2sdk/server/Sm2ServerFilterLogicTest.java

This is the core server-side logic, with ZERO Servlet API dependencies. It can be unit-tested without a Servlet container.

process(method, path, headers, requestBody, remoteAddr) -> FilterResult:
1. Look up HandlerMethod for path+method (delegated to Spring, injected)
2. Check @Sm2Api annotation on method/class -> if none, FilterResult.passthrough()
3. Extract X-Session-Id (required, else 401), X-Timestamp (required, else 400), X-Nonce (required, else 400)
4. Verify timestamp: |now - timestamp| <= tolerance
5. NonceValidator.check(nonce) -> reject on duplicate
6. SessionStore.get(sessionId) -> if null, 401 + X-Session-Expired: true
7. ApiAccessProvider.check(clientId from session, path, method) -> reject on deny
8. Decrypt Body: parse IV(12)+ciphertext+TAG(16), Sm4Crypto.decrypt(), on TAG failure -> 400 + 21202
9. Extract _idempotencyKey if POST, check idempotency cache (Redis/local, 5 min TTL)
10. Return FilterResult.decrypted(plainJson, sessionId, clientId)

encryptResponse(sessionId, responseBody) -> byte[]:
1. Serialize response body to JSON
2. Sm4Crypto.encrypt() -> Base64(IV || ciphertext || TAG)
3. Set X-Session-Expired: false, optional X-Session-Renewed if rekeyed
4. Return encrypted bytes

- [ ] Step 1: Write Sm2ServerFilterLogicTest
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement Sm2ServerFilterLogic
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit

### Task 20: Create Dual Servlet Filters (Javax + Jakarta)

Files:
- Create: sm2-sdk/sm2-sdk-server/src/main/java/com/sm2sdk/server/JavaxSm2ServerFilter.java
- Create: sm2-sdk/sm2-sdk-server/src/main/java/com/sm2sdk/server/JakartaSm2ServerFilter.java

Both adapters delegate to Sm2ServerFilterLogic. They only differ in which Servlet API they implement:

JavaxSm2ServerFilter implements javax.servlet.Filter:
- doFilter(ServletRequest, ServletResponse, FilterChain)
- Wraps HttpServletRequest to replace encrypted body with decrypted plaintext
- Wraps HttpServletResponse to capture response and encrypt it
- On error (401/400/403), writes plaintext error JSON response directly

JakartaSm2ServerFilter implements jakarta.servlet.Filter:
- Same logic, jakarta.servlet API types

Both compiled with provided scope. At runtime, only one Servlet API is on classpath, so only one Filter class can be loaded. The other class is never loaded (no ClassNotFoundException).

- [ ] Step 1: Write JavaxSm2ServerFilter
- [ ] Step 2: Write JakartaSm2ServerFilter
- [ ] Step 3: Write integration tests (using Spring MockMvc or embedded server)
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit


## Phase 8: Spring Boot Starter Module

### Task 21: Create Sm2SdkProperties

Files:
- Create: sm2-sdk/sm2-sdk-spring-boot-starter/src/main/java/com/sm2sdk/starter/Sm2SdkProperties.java

@ConfigurationProperties(prefix = "sm2-sdk") class mapping all yml config from spec Section 6.2.
Fields: myIdentity, myPrivateKey, redisKeyPrefix, peers (Map<String, PeerProperties>), client (ClientProperties), server (ServerProperties), session (SessionProperties), handshake (HandshakeProperties), timestamp (TimestampProperties), nonce (NonceProperties), fallback (FallbackProperties), access (AccessProperties), path (PathProperties).

Inner classes for each config group with defaults matching spec.
Sm2SdkConfig toConfig() method converts properties to core config POJO.

- [ ] Step 1: Write Sm2SdkProperties
- [ ] Step 2: Write unit test for property binding
- [ ] Step 3: Run tests (PASS)
- [ ] Step 4: Commit

### Task 22: Create Sm2SdkAutoConfiguration

Files:
- Create: sm2-sdk/sm2-sdk-spring-boot-starter/src/main/java/com/sm2sdk/starter/Sm2SdkAutoConfiguration.java

@Configuration class with conditional beans:

Always (when sm2-sdk.my-identity is set):
- Sm2SdkConfig bean (from properties)
- SessionManager bean (depends on Sm2KeyExchange, Sm4Crypto, SessionStore)
- HutoolSm2KeyExchange bean
- HutoolSm4Crypto bean
- CaffeineSessionStore bean (default, @ConditionalOnMissingBean)

When client.enabled=true:
- HandshakeRetryHandler bean
- Sm2HttpClient bean

When server.enabled=true:
- Sm2ServerFilterLogic bean
- ApiAccessProvider bean (ConfigBasedApiAccessProvider if no custom bean)
- @ConditionalOnClass(javax.servlet.Filter.class) -> JavaxSm2ServerFilter bean + FilterRegistrationBean
- @ConditionalOnClass(jakarta.servlet.Filter.class) -> JakartaSm2ServerFilter bean + FilterRegistrationBean
- When spring.redis.* is configured and server.session-store=redis -> RedisSessionStore bean (overrides Caffeine)

- [ ] Step 1: Write Sm2SdkAutoConfiguration
- [ ] Step 2: Write unit test for auto-configuration (using Spring Boot test slices)
- [ ] Step 3: Run tests (PASS)
- [ ] Step 4: Commit

### Task 23: Create spring.factories

Files:
- Create: sm2-sdk/sm2-sdk-spring-boot-starter/src/main/resources/META-INF/spring.factories
- Create: sm2-sdk/sm2-sdk-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

spring.factories (Spring Boot 2.x):
```
org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.sm2sdk.starter.Sm2SdkAutoConfiguration
```

AutoConfiguration.imports (Spring Boot 3.x):
```
com.sm2sdk.starter.Sm2SdkAutoConfiguration
```

Both files included so starter works with both Spring Boot 2.x and 3.x.

- [ ] Step 1: Create both files
- [ ] Step 2: Commit


## Phase 9: Build, Integration Tests & Quality Gates

### Task 24: Finalize Maven Shade Plugin Configuration

Files:
- Modify: sm2-sdk/sm2-sdk-spring-boot-starter/pom.xml

Ensure the shade plugin in the starter POM:
- Relocates cn.hutool -> com.sm2sdk.third.hutool
- Relocates com.fasterxml.jackson -> com.sm2sdk.third.jackson
- Excludes signature files (META-INF/*.SF, *.DSA, *.RSA)
- Creates dependency-reduced-pom.xml
- Produces both shaded JAR and slim JAR (classifier: slim, without shading)

- [ ] Step 1: Verify shade configuration is correct
- [ ] Step 2: Run `mvn package -pl sm2-sdk-spring-boot-starter`
- [ ] Step 3: Verify relocated packages in shaded JAR using `jar tf` and grep
- [ ] Step 4: Commit

### Task 25: Write Integration Tests

Files:
- Create: sm2-sdk/sm2-sdk-spring-boot-starter/src/test/java/com/sm2sdk/starter/Sm2SdkIntegrationTest.java

End-to-end integration test using Spring Boot test framework:

Test scenarios:
1. Full handshake + encrypted GET request/response
2. Full handshake + encrypted POST request/response (with idempotency)
3. Session expiry -> 401 -> auto re-handshake -> retry success
4. TAG verification failure -> 400 + errorCode 21202 -> re-handshake -> retry success
5. Nonce replay detection -> 403
6. Timestamp out of tolerance -> 400
7. Access control: allowed path passes, denied path gets 403
8. Circuit breaker: 5 consecutive handshake failures -> CIRCUIT_BREAKER_OPEN
9. Session renewal: near expiry -> auto rekey -> new sessionId
10. @Sm2Api on method only, class only, no annotation
11. Custom ApiAccessProvider Bean replaces default
12. Server mode with Redis session store fallback to local

- [ ] Step 1: Write integration test class
- [ ] Step 2: Run integration tests
- [ ] Step 3: Fix issues until all pass
- [ ] Step 4: Commit

### Task 26: Build Verification and Quality Gates

- [ ] Step 1: Full build all modules
Run: `cd D:/workspace/security/sm2-sdk && mvn clean package`
Expected: BUILD SUCCESS, all tests pass

- [ ] Step 2: Verify JDK compatibility
Run: `mvn clean package -Dmaven.compiler.source=1.8 -Dmaven.compiler.target=1.8`
Expected: BUILD SUCCESS (Java 8 bytecode)

- [ ] Step 3: Run test coverage
Run: `mvn clean verify -Pcoverage` (if JaCoCo plugin configured)
Expected: Coverage >= 80% overall, core crypto >= 95%

- [ ] Step 4: Verify shaded JAR contains all needed classes
Run: `jar tf sm2-sdk-spring-boot-starter/target/sm2-sdk-spring-boot-starter-1.0.0-SNAPSHOT.jar | grep "com/sm2sdk/third"`
Expected: Shows relocated hutool and jackson classes

- [ ] Step 5: Verify no framework deps in core JAR
Run: `jar tf sm2-sdk-core/target/sm2-sdk-core-1.0.0-SNAPSHOT.jar | grep -E "(javax|jakarta|spring)"`
Expected: NO output (no Servlet/Spring classes in core)

- [ ] Step 6: Run OWASP dependency check
Run: `mvn org.owasp:dependency-check-maven:check`
Expected: No critical vulnerabilities

- [ ] Step 7: Final commit and tag

```bash
git add -A
git commit -m "feat: complete SM2 Security Data Exchange SDK v1.0.0

- sm2-sdk-core: SM2 key exchange + SM4-GCM + session management + nonce anti-replay
- sm2-sdk-client: Sm2HttpClient + Sm2Request chain builder + circuit breaker
- sm2-sdk-server: Dual Servlet Filter (javax+jakarta) + @Sm2Api + access control
- sm2-sdk-spring-boot-starter: Auto-configuration with shaded Hutool+Jackson
- Compatible with JDK 8/11/17/21 and Spring Boot 2.x/3.x"
git tag v1.0.0-SNAPSHOT
```

---

## Task Dependency Graph

```
Phase 1: Project Scaffolding
  Task 1 (Parent POM)
    └─> Task 2 (Core POM)
    └─> Task 3 (Client/Server/Starter POMs)

Phase 2: Foundation Types
  Task 4 (ErrorCode + Sm2SdkException) [no deps]
  Task 5 (Model classes) [depends on Task 4 for Session]

Phase 3: Cryptography
  Task 6 (Crypto Interfaces + KDF) [depends on Task 5]
  Task 7 (HutoolSm2KeyExchange) [depends on Task 6]
  Task 8 (HutoolSm4Crypto) [depends on Task 6]

Phase 4: Session & Nonce
  Task 9 (SessionStore + CaffeineSessionStore) [depends on Task 5]
  Task 10 (RedisSessionStore) [depends on Task 9]
  Task 11 (SessionManager) [depends on Tasks 7,8,9]
  Task 12 (NonceValidator) [depends on Task 5]

Phase 5: Utilities
  Task 13 (SecureRandomUtil, MemoryCleanUtil, Sm2KeyPrefix) [depends on Task 5]

Phase 6: Client
  Task 14 (HandshakeRetryHandler) [depends on Task 4,13]
  Task 15 (Sm2Request) [depends on Task 11,14]
  Task 16 (Sm2HttpClient) [depends on Task 15]

Phase 7: Server
  Task 17 (@Sm2Api annotation) [no deps]
  Task 18 (ApiAccessProvider + PathMatcher) [no deps]
  Task 19 (Sm2ServerFilterLogic) [depends on Tasks 11,12,17,18]
  Task 20 (Javax + Jakarta Filters) [depends on Task 19]

Phase 8: Starter
  Task 21 (Sm2SdkProperties) [depends on Task 5]
  Task 22 (Sm2SdkAutoConfiguration) [depends on all above]
  Task 23 (spring.factories) [depends on Task 22]

Phase 9: Build & Integration
  Task 24 (Shade config finalize) [depends on all modules]
  Task 25 (Integration tests) [depends on Tasks 22,23]
  Task 26 (Build verification) [depends on Tasks 24,25]
```

## Execution Order

Tasks can be partially parallelized:
- After Task 5, Tasks 6, 9, 12, 13 can run in parallel
- After Task 6, Tasks 7 and 8 can run in parallel
- After Tasks 7+8+9, Task 11 can start
- After Task 9, Task 10 can start
- After Task 11, Tasks 14-16 (client) and Tasks 17-20 (server) can run in parallel
- After all module tasks, Tasks 21-23 (starter) can run

## Completion Checklist

- [ ] All 26 tasks completed
- [ ] 4 modules compile and test successfully
- [ ] `mvn clean package` passes for entire project
- [ ] Shaded JAR contains relocated Hutool and Jackson
- [ ] Core JAR has zero Servlet/Spring dependencies
- [ ] Unit test coverage >= 80%
- [ ] Integration tests cover: handshake, encrypt/decrypt, session expiry recovery, TAG failure recovery, nonce replay, access control, circuit breaker
- [ ] JDK 8 bytecode verified (via javap -v on class files)
- [ ] Both javax and jakarta Filter classes present in server JAR
- [ ] spring.factories AND AutoConfiguration.imports both present in starter JAR
- [ ] OWASP dependency check passes

## Notes for Implementers

1. **All Hutool references must use relocated package**: In core module code, import from `com.sm2sdk.third.hutool.*` not `cn.hutool.*`. This is critical since shade relocation happens at the starter level, but core module is compiled against original Hutool and then shaded in the final assembly.

2. **Wait, actually**: The core module compiles against original `cn.hutool` packages. The shade plugin in the starter module relocates them when building the final fat JAR. Source code in core/client/server modules should import from `cn.hutool.*` normally. The relocation only affects the final shaded JAR output.

3. **Servlet Filter compilation**: Both javax and jakarta servlet-api JARs are on the compile classpath of the server module (provided scope). The Java compiler will compile both Filter classes successfully. At runtime, only one servlet API is available, so the JVM only tries to load the Filter that matches.

4. **Key material handling**: Always use `Session.getSm4KeyCopy()` which returns a defensive copy. After using the key for encryption/decryption, call `Session.clearKeyCopy(copy)` to zero it. Never hold key references longer than needed.

5. **Sm2SdkConfig serialization**: Never serialize the full Sm2SdkConfig — it contains key paths. Only serialize the subset needed for Redis storage of Session objects.

6. **Redis key format**: Always use Sm2KeyPrefix utility to build keys. Never hardcode key strings. This ensures the single `redis-key-prefix` config controls all keys.

7. **Idempotency**: Only for POST requests. The `_idempotencyKey` is injected into the plaintext JSON body before encryption. Server extracts it after decryption.

