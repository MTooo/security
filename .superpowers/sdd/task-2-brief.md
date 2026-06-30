### Task 2: Create sm2-sdk-core Module POM

**Files:** Create: `sm2-sdk/sm2-sdk-core/pom.xml`

**Produces:** Core module POM with compile-scope Hutool+Jackson (for shading), Caffeine, and provided SLF4J. Zero Spring/Servlet deps.

- [ ] **Step 1: Write core POM**

Module inherits from parent. Dependencies: hutool-all (compile), jackson-databind (compile), caffeine (compile), slf4j-api (provided), junit-jupiter + mockito (test).

- [ ] **Step 2: Verify**

Run: `cd D:/workspace/security/sm2-sdk && mvn validate -pl sm2-sdk-core`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**
```bash
git add sm2-sdk/sm2-sdk-core/pom.xml && git commit -m "feat: create sm2-sdk-core module POM"
```

