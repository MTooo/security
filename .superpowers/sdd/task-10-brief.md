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

