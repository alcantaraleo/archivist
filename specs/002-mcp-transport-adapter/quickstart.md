# Quickstart Validation Guide: MCP Transport Adapter

**Feature**: 002-mcp-transport-adapter
**Date**: 2026-07-12

This guide describes how to verify the MCP transport adapter after implementation. All automated checks run from the repository root.

---

## Prerequisites

- Java 21 on `PATH`
- `ARCHIVIST_SECOND_BRAIN_PATH` set to an **existing directory**

```bash
export ARCHIVIST_SECOND_BRAIN_PATH=/tmp
```

---

## Verification Steps

### 1. Full build (includes contract regression tests)

```bash
./gradlew build
```

**Expected outcome**: `BUILD SUCCESSFUL`. Contract regression tests in `:transport:test` compare registered MCP tools and serialised evidence shape against fixtures in [contracts/](contracts/). Unintended contract drift fails the build (AC-1, AC-10).

### 2. Transport tests only

```bash
./gradlew :transport:test
```

**Expected outcome**: All tests pass, including:

| Test class                  | Verifies                                                                                                                      |
| --------------------------- | ----------------------------------------------------------------------------------------------------------------------------- |
| `McpContractRegressionTest` | Tool names, descriptions, input schemas, server identity, evidence JSON shape vs fixtures                                     |
| `ArchivistMcpToolsTest`     | Invalid input → error (all eight tools); valid input → `[]`; sample evidence → fixture match; delegation failure → tool error |
| `EvidenceJsonMapperTest`    | Serialisation matches [evidence-response-schema-expected.json](contracts/evidence-response-schema-expected.json)              |
| `McpInputValidatorTest`     | Null/blank/whitespace rejection                                                                                               |

Contract regression tests use `@SpringBootTest(classes = McpAdapterTestConfiguration.class)` — minimal Spring context, not full `ArchivistApplication` (see [research.md](research.md) Decision 6).

### 3. Application module remains Spring-free

```bash
./gradlew :application:dependencies --configuration compileClasspath
```

**Expected outcome**: Dependency tree contains `:domain` only; no `org.springframework` or `io.modelcontextprotocol` entries.

### 4. Transport does not depend on infrastructure

```bash
./gradlew :transport:dependencies --configuration compileClasspath
```

**Expected outcome**: No `:infrastructure` project dependency (AC-7).

### 5. MCP server boots and exposes eight tools

```bash
ARCHIVIST_SECOND_BRAIN_PATH=/tmp ./gradlew :transport:bootRun 2>boot.log
```

In a separate terminal, use an MCP client (Cursor, Claude Desktop, or `npx @modelcontextprotocol/inspector`) to connect via STDIO and list tools.

**Expected outcome**:

- Server starts; startup logs appear in `boot.log` (stderr), not stdout (AC-13)
- MCP client lists exactly eight tools matching [contracts/mcp-tools-expected.json](contracts/mcp-tools-expected.json)
- Invoking any tool with a valid parameter returns `[]` (stub behaviour)
- Invoking with empty/null parameter returns a tool error (AC-5, AC-12)

Terminate the server with `Ctrl+C`.

### 6. Contract drift detection (negative test)

To confirm regression tests work, temporarily change a tool description in `ArchivistMcpTools` without updating the fixture, then run:

```bash
./gradlew :transport:test --tests McpContractRegressionTest
```

**Expected outcome**: Test failure indicating contract mismatch. Revert the change before committing.

### 7. No star imports or field injection

```bash
grep -r "^import .*\*;" --include="*.java" application/src transport/src
grep -r "@Autowired" --include="*.java" application/src transport/src
```

**Expected outcome**: No matches in new source files (AC-14, AC-15).

---

## Acceptance Criteria Mapping

| AC                       | Verification step             |
| ------------------------ | ----------------------------- |
| AC-1                     | Step 1                        |
| AC-2, AC-3, AC-10, AC-11 | Steps 1–2, 6                  |
| AC-4, AC-5, AC-9         | Step 2                        |
| AC-6                     | Step 2 + source inspection    |
| AC-7                     | Step 4                        |
| AC-8                     | Step 2 (tool name assertions) |
| AC-12                    | Step 5                        |
| AC-13                    | Step 5                        |
| AC-14, AC-15             | Step 7                        |

---

## Intentional Contract Updates

If the MCP contract must change:

1. Get specification approval
2. Update [specs/001-project-scaffolding/contracts/mcp-server.md](../001-project-scaffolding/contracts/mcp-server.md)
3. Update all files in [contracts/](contracts/)
4. Re-run Step 1

See [contracts/README.md](contracts/README.md) for the full procedure.
