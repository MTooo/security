### Task 8: Implement HutoolSm4Crypto

Files:
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/crypto/impl/HutoolSm4Crypto.java
- Test: sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/crypto/impl/HutoolSm4CryptoTest.java

encrypt(): generate 12B random IV, SM4 GCM mode encrypt, return IV(12)||ciphertext||TAG(16)
decrypt(): parse IV(12), TAG(16), ciphertext from input, SM4 GCM decrypt with TAG verify, on failure throw Sm2SdkException(SM4_DECRYPT_TAG_FAILED)

Test: roundtrip, wrong key failure, wrong AAD failure, random IV per encryption, empty body, large body.

- [ ] Step 1: Write HutoolSm4CryptoTest
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement HutoolSm4Crypto
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit


## Phase 4: Core Module - Session & Nonce

