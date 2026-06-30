# Task 14: HandshakeRetryHandler (Circuit Breaker) — Report

## Files Created

- `D:/workspace/security/sm2-sdk/client/src/main/java/com/sm2sdk/client/HandshakeRetryHandler.java`
- `D:/workspace/security/sm2-sdk/client/src/test/java/com/sm2sdk/client/HandshakeRetryHandlerTest.java`

## Implementation Summary

### HandshakeRetryHandler

Implements a circuit breaker state machine for SM2 SDK handshake retry logic.

**State Machine:** CLOSED → OPEN → HALF_OPEN → CLOSED

| State | Behavior |
|---|---|
| **CLOSED** | Normal operation. Tracks consecutive failures via `AtomicInteger`. When failures reach `FAILURE_THRESHOLD (5)`, transitions to OPEN. |
| **OPEN** | All `executeWithRetry` calls immediately throw `Sm2SdkException(CIRCUIT_BREAKER_TRIPPED, 503)`. After `COOLDOWN_MS (30s)`, the next request atomically transitions to HALF_OPEN via CAS. |
| **HALF_OPEN** | Allows 1 full probe (with internal retries). Probe success → CLOSED (reset counter). Probe failure → OPEN (restart cooldown). |

**Key Methods:**
- `<T> T executeWithRetry(Callable<T> callable)` — Executes with exponential backoff (1s/2s/4s), retries up to `handshakeRetry` times. Each attempt checks circuit state. `Sm2SdkException(CIRCUIT_BREAKER_TRIPPED)` passes through without retry.
- `boolean isCircuitOpen()` — Returns `true` iff state is OPEN.
- `int getConsecutiveFailures()` — Returns current consecutive failure count.

**Thread Safety:** Uses `AtomicReference` for state transitions, `AtomicInteger` for failure counter, `AtomicLong` for timestamp — all lock-free and thread-safe.

### HandshakeRetryHandlerTest

14 tests covering the full state machine lifecycle:

| # | Test | Scenario |
|---|---|---|
| 1 | `executeWithRetryShouldReturnResultOnFirstAttempt` | Normal execution, immediate success |
| 2 | `executeWithRetryShouldRetryOnFailureThenSucceed` | Fails twice, succeeds on 3rd attempt |
| 3 | `executeWithRetryShouldThrowWhenRetriesExhausted` | Always fails, exception after all retries |
| 4 | `consecutiveFailuresShouldTripCircuitBreaker` | 5 consecutive failure calls → OPEN |
| 5 | `circuitBreakerShouldRejectRequestsWhenOpen` | OPEN state rejects all requests |
| 6 | `circuitBreakerShouldNotIncreaseFailuresWhenRejecting` | Rejected requests don't increment counter |
| 7 | `circuitBreakerShouldTransitionToHalfOpenAfterCooldown` | Cooldown elapsed → HALF_OPEN → probe succeeds → CLOSED |
| 8 | `halfOpenSuccessShouldResetToClosed` | HALF_OPEN success → CLOSED, normal operation resumes |
| 9 | `halfOpenFailureShouldReopenCircuit` | HALF_OPEN failure → OPEN, requests rejected again |
| 10 | `halfOpenResetAllowsNewFailureCounting` | After recovery, fresh failure counting starts |
| 11 | `executeWithRetryShouldPassThroughCircuitBreakerException` | CIRCUIT_BREAKER_TRIPPED passes through unmodified |
| 12 | `executeWithRetryShouldWrapNonSm2Exception` | Non-Sm2SdkException wrapped as HTTP_REQUEST_FAILED |
| 13 | `noRetryWhenHandshakeRetryIsZero` | handshakeRetry=0 → no retries, immediate failure |
| 14 | `isCircuitOpenShouldReturnCorrectState` | State reporting before/after circuit trip |

## Test Results

```
Tests run: 14, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

All tests pass. Test duration is ~6 minutes due to real Thread.sleep delays (1s/2s/4s backoff) in retry logic.
