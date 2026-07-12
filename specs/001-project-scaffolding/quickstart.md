# Quickstart Validation Guide: Project Scaffolding

**Feature**: 001-project-scaffolding
**Date**: 2026-07-11

This guide describes how to verify that the scaffold is correctly in place. All checks can be run from the repository root without any configuration.

---

## Prerequisites

- Java 21 installed and on `PATH`
- No additional configuration required — all dependencies are resolved by Gradle

---

## Verification Steps

### 1. Full build

```bash
./gradlew build
```

**Expected outcome**: `BUILD SUCCESSFUL` with no compilation errors or test failures across all four modules.

### 2. Module presence

```bash
./gradlew projects
```

**Expected outcome**: Output lists exactly four subprojects:

```
Root project 'archivist'
+--- Project ':domain'
+--- Project ':application'
+--- Project ':infrastructure'
\--- Project ':transport'
```

### 3. Domain module has no Spring dependency

```bash
./gradlew :domain:dependencies --configuration compileClasspath
```

**Expected outcome**: The dependency tree for `:domain` contains **no** `org.springframework` or `io.modelcontextprotocol` entries.

### 4. Application module depends only on domain

```bash
./gradlew :application:dependencies --configuration compileClasspath
```

**Expected outcome**: The dependency tree for `:application` contains `:domain` and **no** `org.springframework` or `io.modelcontextprotocol` entries.

### 5. Transport boots

```bash
./gradlew :transport:bootRun
```

**Expected outcome**: Spring Boot starts, logs `Started ArchivistApplication`, and the process remains running (STDIO MCP server is ready). Terminate with `Ctrl+C`.

### 6. Test suite passes

```bash
./gradlew test
```

**Expected outcome**: `BUILD SUCCESSFUL`. Zero test failures. (Placeholder tests or zero tests are both acceptable at scaffold stage.)

### 7. No star imports

```bash
grep -r "^import .*\*;" --include="*.java" .
```

**Expected outcome**: No output — zero star imports in any source file.

### 8. No field injection

```bash
grep -r "@Autowired" --include="*.java" .
```

**Expected outcome**: No output — zero `@Autowired` annotations in any source file.

### 9. No hardcoded configuration values

```bash
grep -r "ARCHIVIST_SECOND_BRAIN_PATH\|/home/\|/Users/\|localhost\|127\.0\.0\.1" --include="*.java" --include="*.properties" --include="*.yml" .
```

**Expected outcome**: No matches in Java source files. `application.properties` may contain `${ENV_VAR}` placeholder references but never literal paths or addresses.

### 10. Missing required env var fails fast

```bash
# Unset any configured path and attempt to start
ARCHIVIST_SECOND_BRAIN_PATH="" ./gradlew :transport:bootRun
```

**Expected outcome**: Application fails at startup with a clear `BindValidationException` or equivalent Spring Boot validation error, not a NullPointerException at call time.

> Note: `ARCHIVIST_SECOND_BRAIN_PATH` is not yet required to start the scaffold (no retrieval adapter exists yet). This check validates the wiring is in place for when the first adapter is added.

---

## Scaffold Acceptance Summary

| Check                      | Command                               | Pass Condition                                 |
| -------------------------- | ------------------------------------- | ---------------------------------------------- |
| Build succeeds             | `./gradlew build`                     | `BUILD SUCCESSFUL`                             |
| Four modules present       | `./gradlew projects`                  | domain, application, infrastructure, transport |
| Domain is Spring-free      | `./gradlew :domain:dependencies`      | No spring.\* in compileClasspath               |
| Application is Spring-free | `./gradlew :application:dependencies` | No spring.\* in compileClasspath               |
| Application boots          | `./gradlew :transport:bootRun`        | `Started ArchivistApplication`                 |
| Tests pass                 | `./gradlew test`                      | `BUILD SUCCESSFUL`                             |
| No star imports            | `grep -r "import .*\*;"`              | No matches                                     |
| No field injection         | `grep -r "@Autowired"`                | No matches                                     |
| No hardcoded config        | `grep -r` paths/hosts in sources      | No matches in .java files                      |
| Env var fail-fast          | start with empty required var         | Clear startup validation error                 |

---

## Next Steps After Validation

Once all checks above pass, the scaffold is complete. Proceed to:

1. Mark spec `001-project-scaffolding` as **Implemented**
2. Open the first domain capability spec (e.g., `retrieveContext` or `findDecisions`)
3. Run `/speckit-specify` for the next capability
