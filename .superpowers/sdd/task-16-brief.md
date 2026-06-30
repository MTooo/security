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

