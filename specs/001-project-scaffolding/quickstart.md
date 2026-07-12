# Quickstart Validation Guide: Project Scaffolding

**Feature**: 001-project-scaffolding
**Date**: 2026-07-11

This guide describes how to verify that the scaffold is correctly in place. All checks can be run from the repository root.

---

## Prerequisites

- Java 21 installed and on `PATH`
- `ARCHIVIST_SECOND_BRAIN_PATH` set to an **existing directory** (e.g. `export ARCHIVIST_SECOND_BRAIN_PATH=/tmp`)

---

## Verification Steps

### 1. Full build

```bash
./gradlew build
```

**Expected outcome**: `BUILD SUCCESSFUL` with no compilation errors across all four modules. Validates compile integrity and Spring Boot 4.1.0 + Spring AI 2.0.0 compatibility (AC-1, AC-9). Test execution is not required at scaffold stage.

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

### 4. Domain tests run without Spring

```bash
./gradlew :domain:test
./gradlew :domain:dependencies --configuration testCompileClasspath
```

**Expected outcome**: `BUILD SUCCESSFUL`. Test compile classpath contains **no** `org.springframework` or `io.modelcontextprotocol` entries (AC-16).

### 5. Application module depends only on domain

```bash
./gradlew :application:dependencies --configuration compileClasspath
```

**Expected outcome**: The dependency tree for `:application` contains `:domain` and **no** `org.springframework` or `io.modelcontextprotocol` entries.

### 6. Transport boots (logs on stderr)

```bash
ARCHIVIST_SECOND_BRAIN_PATH=/tmp ./gradlew :transport:bootRun 2>boot.log
```

**Expected outcome**: Spring Boot starts; startup confirmation appears in `boot.log` (stderr), not on stdout. Process remains running (STDIO MCP server ready). Terminate with `Ctrl+C`. Stdout must remain reserved for the MCP protocol (AC-8).

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
grep -rnE '/home/|/Users/|/var/|localhost|127\.0\.0\.1|0\.0\.0\.0|jdbc:|mongodb://|redis://|postgres://|mysql://|:5432|:3306|:6379|:8080' --include="*.java" --include="*.properties" .
```

**Expected outcome**: No matches in Java source files. `application.properties` may contain `${ENV_VAR}` placeholder references but never literal paths, hosts, or connection strings.

### 10. Missing required env var fails fast

```bash
ARCHIVIST_SECOND_BRAIN_PATH="" ./gradlew :transport:bootRun 2>&1 | head -20
```

**Expected outcome**: Application fails at startup with a descriptive validation error on stderr that includes the property path (`archivist.second-brain.path`), the environment variable name (`ARCHIVIST_SECOND_BRAIN_PATH`), and the failure reason (AC-14).

### 11. Non-existent path fails fast

```bash
ARCHIVIST_SECOND_BRAIN_PATH=/nonexistent/path ./gradlew :transport:bootRun 2>&1 | head -20
```

**Expected outcome**: Application fails at startup with a descriptive error indicating the path does not exist or is not a directory (AC-15).

---

## Scaffold Acceptance Summary

| Check                       | Command                               | Pass Condition                                      |
| --------------------------- | ------------------------------------- | --------------------------------------------------- |
| Build succeeds              | `./gradlew build`                     | `BUILD SUCCESSFUL`; version compatibility validated |
| Four modules present        | `./gradlew projects`                  | domain, application, infrastructure, transport      |
| Domain is Spring-free       | `./gradlew :domain:dependencies`      | No spring.\* in compileClasspath                    |
| Domain tests without Spring | `./gradlew :domain:test`              | `BUILD SUCCESSFUL`; no spring.\* on test classpath  |
| Application is Spring-free  | `./gradlew :application:dependencies` | No spring.\* in compileClasspath                    |
| Application boots on stderr | `./gradlew :transport:bootRun`        | Startup confirmation on stderr; stdout clean        |
| No star imports             | `grep -r "import .*\*;"`              | No matches                                          |
| No field injection          | `grep -r "@Autowired"`                | No matches                                          |
| No hardcoded config         | `grep -rnE` paths/hosts/URLs          | No matches in .java files                           |
| Env var fail-fast           | start with empty required var         | Descriptive error with property + env var name      |
| Path existence              | start with non-existent path          | Descriptive error; service does not start           |

---

## Next Steps After Validation

Once all checks above pass, the scaffold is complete. Proceed to:

1. Mark spec `001-project-scaffolding` as **Implemented**
2. Open the first domain capability spec (e.g., `retrieveContext` or `findDecisions`)
3. Run `/speckit-specify` for the next capability
