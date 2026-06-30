# Task 2: Create sm2-sdk-core Module POM

**Status:** DONE

**Commit:** `10b7bcf` feat: create sm2-sdk-core module POM

## Files Changed

- `sm2-sdk/core/pom.xml` -- Replaced stub (bare-bones) with full POM declaring compile-scope dependencies (hutool-all, jackson-databind, caffeine), provided-scope (slf4j-api), and test-scope (junit-jupiter, mockito-core). Plugin references align with parent pluginManagement.
- `sm2-sdk/core/src/test/java/com/sm2sdk/core/package-info.java` -- Empty placeholder ensuring test source directory exists for compilation verification.

## Test Summary

`mvn validate -pl core` -- BUILD SUCCESS  
`mvn compile -pl core` -- BUILD SUCCESS (all dependencies resolved, no sources to compile as expected)

## Concerns

- CRLF line-ending warnings on Windows during commit (harmless; `.gitattributes` can be introduced later for the project).
- `maven-shade-plugin` is available in parent `pluginManagement` but is not yet activated in the core module; shading of hutool/jackson will be addressed in a future task.
- No `.gitignore` yet for the `target/` directories (standard Maven convention, not blocking).
