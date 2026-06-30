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

