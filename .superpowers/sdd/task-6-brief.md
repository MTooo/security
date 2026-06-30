### Task 6: Create Crypto Interfaces and KDF Implementation

Files:
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/crypto/Sm2KeyExchange.java
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/crypto/Sm4Crypto.java
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/crypto/KeyDerivation.java
- Test: sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/crypto/KeyDerivationTest.java

Consumes: ErrorCode, Sm2SdkException, HandshakeInit, HandshakeServerResp, HandshakeConfirm
Produces: Sm2KeyExchange interface, Sm4Crypto interface, KeyDerivation utility class

**Sm2KeyExchange interface** defines:
- buildInitRequest(clientId, clientPrivateKey, serverPublicKey, clientIdentity) -> HandshakeInit
- processServerResponse(sentRequest, serverResp, clientPrivateKey, serverPublicKey, clientIdentity, serverIdentity) -> HandshakeResult
- buildConfirm(result) -> HandshakeConfirm
- processClientInit(clientInit, serverPrivateKey, clientPublicKey, serverIdentity, clientIdentity) -> HandshakeResult
- verifyConfirm(result, confirm) -> boolean
- Inner class HandshakeResult: sessionId, sm4Key(16B), sm4Iv(12B), sharedKey, ZA, ZB, RA, RB

**Sm4Crypto interface** defines:
- encrypt(byte[] key, byte[] iv, byte[] aad, byte[] plaintext) -> byte[] (IV || ciphertext || TAG)
- decrypt(byte[] key, byte[] iv, byte[] aad, byte[] ciphertextWithTag) -> byte[] plaintext (throws Sm2SdkException on TAG failure)

**KeyDerivation** (GB/T 32918.4-2016 Chapter 5):
- kdf(byte[] z, int klenBits) -> byte[]: SM3-based hash iteration
  - ct = 0x00000001, for i = 1 to ceil(klen/256): Ha[i] = SM3(Z || ct), ct++
  - Output: Ha[1] || Ha[2] || ... first klen bits
- extractSm4Key(derived) -> bytes[0..15]
- extractSm4Iv(derived) -> bytes[16..27]
- extractHmacKey(derived) -> bytes[28..59] (optional)

KeyDerivationTest covers: deterministic output, different inputs produce different outputs, correct length (60 bytes for klen=480), component extraction.

- [ ] Step 1: Write KeyDerivationTest (TDD first)
- [ ] Step 2: Run test (FAIL - class not found)
- [ ] Step 3: Write all three interfaces/classes
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit

