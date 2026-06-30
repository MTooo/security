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

