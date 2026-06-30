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

