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

