### Task 4: Create ErrorCode Enum and Sm2SdkException

**Files:**
- Create: `sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/exception/ErrorCode.java`
- Create: `sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/exception/Sm2SdkException.java`
- Test: `sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/exception/ErrorCodeTest.java`

**Produces:** Complete 5-digit error code enum (30+ codes from spec Section 7.2) and base SDK exception.

- [ ] **Step 1: Write ErrorCodeTest (TDD)**

```java
package io.github.mtooo.core.exception;

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

