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

