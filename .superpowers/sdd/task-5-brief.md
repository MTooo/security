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

