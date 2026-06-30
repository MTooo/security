### Task 7: Implement HutoolSm2KeyExchange

Files:
- Create: sm2-sdk/sm2-sdk-core/src/main/java/com/sm2sdk/core/crypto/impl/HutoolSm2KeyExchange.java
- Test: sm2-sdk/sm2-sdk-core/src/test/java/com/sm2sdk/core/crypto/impl/HutoolSm2KeyExchangeTest.java

Full SM2 key exchange following GB/T 32918.3-2016 Chapter 6. Implementation steps match spec Section 3.4 exactly:

buildInitRequest: generate (rA,RA), compute ZA = SM3(ENTLA||IDA||a||b||xG||yG||xA||yA), sign with dA over (RA||clientId||ZA||timestamp), return HandshakeInit with Base64(RA), Base64(signature), Base64(ZA)

processServerResponse: decode RB, verify on curve, compute xBarB=2^127+(xRB&(2^127-1)), shared point (x1,y1)=[rA]*(RB+[xBarB]*PB), KDF derive keys, verify SB=SM3(0x02||y1||SM3(x1||ZA||ZB||RA||RB)), return HandshakeResult

processClientInit: verify timestamp(|now-timestamp|<=300s), decode+verify RA and signature, generate (rB,RB), compute ZB, shared point (x1,y1)=[rB]*(RA+[xBarA]*PA), KDF derive, generate sessionId=UUID, compute SB, return HandshakeResult

buildConfirm: compute SA=SM3(0x03||y1||SM3(x1||ZA||ZB||RA||RB)), return Base64(SA)

verifyConfirm: compare computed SA with received SA

Critical: all SM2 ops use relocated Hutool (com.sm2sdk.third.hutool.crypto.SmUtil). Ephemeral private keys cleared after use with MemoryCleanUtil.

Test: full roundtrip (generate keys, handshake, both sides same sm4Key), signature verification failures, timestamp validation, point-on-curve checks.

- [ ] Step 1: Write HutoolSm2KeyExchangeTest
- [ ] Step 2: Run test (FAIL)
- [ ] Step 3: Implement HutoolSm2KeyExchange
- [ ] Step 4: Run tests (PASS)
- [ ] Step 5: Commit

