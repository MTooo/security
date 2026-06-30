### Task 3: Create Client, Server, and Starter Module POMs

**Files:** Create: `sm2-sdk/sm2-sdk-client/pom.xml`, `sm2-sdk/sm2-sdk-server/pom.xml`, `sm2-sdk/sm2-sdk-spring-boot-starter/pom.xml`

**Produces:** All module POMs with correct dependency chains:
- client → core, SLF4J provided
- server → core, javax.servlet-api + jakarta.servlet-api provided, spring-web provided
- starter → core + client + server, spring-boot-autoconfigure provided, spring-data-redis optional + provided

**Key: starter POM includes maven-shade-plugin with Hutool and Jackson relocation.**

- [ ] **Step 1: Write all three POMs**
- [ ] **Step 2: Verify multi-module build**

Run: `cd D:/workspace/security/sm2-sdk && mvn validate`
Expected: BUILD SUCCESS for all 5 modules (parent + 4 children)

- [ ] **Step 3: Commit**
```bash
git add sm2-sdk/sm2-sdk-client/pom.xml sm2-sdk/sm2-sdk-server/pom.xml sm2-sdk/sm2-sdk-spring-boot-starter/pom.xml
git commit -m "feat: create client, server, and starter module POMs with shade plugin"
```


---

## Phase 2: Core Module - Foundation Types

