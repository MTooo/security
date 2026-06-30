# Task 3 Report: Create Client, Server, and Starter POMs

## STATUS: COMPLETE

## Commit
```
f3cdac4 feat: create client, server, and starter module POMs with shade plugin
```

## Files Created/Modified
- `sm2-sdk/client/pom.xml` - Client module POM (depends on core, slf4j-api provided, JUnit+Mockito test)
- `sm2-sdk/server/pom.xml` - Server module POM (depends on core, javax.servlet-api + jakarta.servlet-api provided, spring-web + spring-context provided, slf4j-api provided, JUnit+Mockito test)
- `sm2-sdk/starter/pom.xml` - Starter module POM (depends on core + client + server, spring-boot-autoconfigure provided, spring-boot-configuration-processor provided+optional, spring-data-redis provided+optional, JUnit test; includes maven-shade-plugin with Hutool/Jackson relocations)

## Validation
`mvn validate -f sm2-sdk/pom.xml` -- BUILD SUCCESS
All 5 modules passed: parent, core, client, server, starter.

## Key Details
- All three modules inherit from `com.sm2sdk:sm2-sdk-parent:1.0.0-SNAPSHOT`
- All dependency versions come from parent `dependencyManagement` (via BOM imports for Spring Boot and Spring Framework)
- Starter POM includes `maven-shade-plugin` with:
  - Relocation: `cn.hutool` -> `com.sm2sdk.third.hutool`
  - Relocation: `com.fasterxml.jackson` -> `com.sm2sdk.third.jackson`
  - Filter exclusions: `META-INF/*.SF`, `META-INF/*.DSA`, `META-INF/*.RSA`
  - `createDependencyReducedPom: true`
  - Phase: `package`, Goal: `shade`

## Concerns
- No Java source files exist yet in client, server, or starter modules (expected at this stage)
- Mockito 5.8.0 deprecates `mockito-core` in favor of `mockito-subclass`/`mockito-inline` — but the current reference is correct for API compatibility
