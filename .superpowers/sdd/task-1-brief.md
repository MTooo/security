### Task 1: Create Parent POM with Dependency Management

**Files:** Create: `sm2-sdk/pom.xml`

**Produces:** Parent POM defining all dependency versions, module list, and plugin management.

- [ ] **Step 1: Write parent POM**

See the parent POM at the end of this section for full XML content. Key elements:
- GroupId: `com.sm2sdk`, ArtifactId: `sm2-sdk-parent`
- 4 modules: core, client, server, starter
- Java 1.8 source/target
- Dependency management for: Hutool 5.8.32, Jackson 2.15.4, Caffeine 2.9.3, SLF4J 2.0.9, javax.servlet-api 4.0.1, jakarta.servlet-api 6.0.0, Spring Boot 3.2.0, Spring 6.1.1, Lettuce 6.3.2, JUnit 5.10.1
- Plugin management for: maven-compiler 3.12.1, maven-shade 3.5.3, maven-surefire 3.2.5, maven-source 3.3.0, maven-javadoc 3.6.3

- [ ] **Step 2: Verify POM is valid**

Run: `cd D:/workspace/security/sm2-sdk && mvn validate`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**
```bash
git add sm2-sdk/pom.xml && git commit -m "feat: create parent POM with dependency management"
```

