# Quickstart Validation Guide: Lexical Retrieval Strategy and Retrieval Foundation

**Feature**: 004-lexical-retrieval
**Date**: 2026-07-13

Verification guide after implementation. All automated checks run from repository root.

---

## Prerequisites

- Java 21 on `PATH`
- Spec 003 `KnowledgeCorpus` implemented (fixture corpus present)
- For manual end-to-end smoke against a real vault:

```bash
export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/your/second-brain
```

Automated retrieval tests use the checked-in fixture corpus — no personal vault required for CI.

---

## Verification Steps

### 1. Full build

```bash
./gradlew build
```

**Expected**: `BUILD SUCCESSFUL`. Includes:

- `:domain:test` — `Query` factories
- `:application:test` — eight use case delegation tests
- `:infrastructure:test` — lexical unit + integration tests
- `:transport:test` — MCP contract regression (mocked `port.in`, unchanged fixtures)

Maps to **AC-1**.

### 2. Domain remains Spring-free

```bash
./gradlew :domain:test
./gradlew :domain:dependencies --configuration compileClasspath
```

**Expected**: Tests pass; no `org.springframework` on compile classpath.

### 3. Application remains Spring-free

```bash
./gradlew :application:test
./gradlew :application:dependencies --configuration compileClasspath
```

**Expected**: Tests pass; compile classpath = `domain` only; Mockito for tests.

### 4. Infrastructure retrieval tests

```bash
./gradlew :infrastructure:test
```

**Expected**: All tests pass:

| Test class                        | Verifies                                                                                                     |
| --------------------------------- | ------------------------------------------------------------------------------------------------------------ |
| `LexicalScorerTest`               | Tokenisation, weighted scoring, case insensitivity                                                           |
| `LexicalRetrievalStrategyTest`    | Type/zone filters, availability policy (mocked corpus)                                                       |
| `LexicalRetrievalIntegrationTest` | End-to-end against fixture corpus + [fixture-lexical-expected.json](contracts/fixture-lexical-expected.json) |

Maps to **AC-4–AC-11**, **AC-13**, **AC-16**.

### 5. Transport layer isolation

```bash
./gradlew :transport:test --tests "*TransportLayerIsolationTest*"
```

**Expected**: No `import io.archivist.infrastructure` in `transport/src/main/java`.

Maps to **AC-2**.

### 6. MCP contract unchanged

```bash
./gradlew :transport:test --tests "*McpContract*"
```

**Expected**: Tool catalogue and `Evidence` JSON shape match checked-in fixtures from spec 002. Tool handlers inject `port.in` mocks — retrieval implementation not involved.

Maps to **AC-1**, **AC-15** (transport handlers untouched).

### 7. Stub configuration removed

```bash
test ! -f transport/src/main/java/io/archivist/transport/config/StubUseCaseConfiguration.java
grep -r "StubUseCaseConfiguration" transport/src/main/java || true
```

**Expected**: File deleted; no references in transport main sources.

Maps to **AC-3**.

### 8. Strategy configuration fail-fast

```bash
ARCHIVIST_SECOND_BRAIN_PATH="$(pwd)/infrastructure/src/test/resources/fixture-corpus" \
  ARCHIVIST_RETRIEVAL_ACTIVE_STRATEGY=nonexistent \
  ./gradlew :transport:bootRun --args='' 2>&1 | head -20
```

**Expected**: Application fails at startup with message referencing unknown strategy `nonexistent`.

Maps to **AC-14**.

(Property binding name may use relaxed binding — verify against `RetrievalProperties` field name in implementation.)

### 9. Manual smoke — real retrieval via MCP (optional)

```bash
export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/your/second-brain
./gradlew :transport:bootRun
```

Invoke `retrieveContext` with a term known to exist in your vault via MCP client.

**Expected**: Non-empty JSON evidence array with `content` and `provenance` fields.

---

## Acceptance Criteria Traceability

| AC    | Verification                                                                                                     |
| ----- | ---------------------------------------------------------------------------------------------------------------- |
| AC-1  | Step 1, 6                                                                                                        |
| AC-2  | Step 5                                                                                                           |
| AC-3  | Step 7                                                                                                           |
| AC-4  | Step 4 — integration test `retrieveContext("sample")`                                                            |
| AC-5  | Step 4 — `findDecisions("fixture")` type filter                                                                  |
| AC-6  | Step 4 — `findConcepts("sample")` multi-type                                                                     |
| AC-7  | Step 4 — title ranks above body-only                                                                             |
| AC-8  | Step 4 — dedupe by sourceId                                                                                      |
| AC-9  | Step 4 — maxResults cap                                                                                          |
| AC-10 | Step 4 — oversize entry title match                                                                              |
| AC-11 | Step 4 — oversize entry body-only exclusion                                                                      |
| AC-12 | Step 3 — eight use case tests                                                                                    |
| AC-13 | Step 4 — `findPeople("sample")`                                                                                  |
| AC-14 | Step 8                                                                                                           |
| AC-15 | Architectural — see [retrieval-strategy-spi.md](contracts/retrieval-strategy-spi.md) extension checklist; Step 6 |
| AC-16 | Step 4 — integration test timing guard                                                                           |

---

## Troubleshooting

| Symptom                                    | Check                                                                      |
| ------------------------------------------ | -------------------------------------------------------------------------- |
| MCP tools return `[]` but infra tests pass | `ArchivistApplication` still importing stubs; auto-config not on classpath |
| Startup: no `KnowledgeGateway` bean        | `implementation(project(":infrastructure"))` missing from transport        |
| Transport isolation test fails             | Accidental `import io.archivist.infrastructure` in transport source        |
| All retrieval tests empty                  | Fixture path misconfigured in `RetrievalTestConfiguration`                 |
| Unknown strategy not failing               | `RetrievalStrategyRegistry` validation not wired at startup                |

---

## Next Steps

After all steps pass:

1. Mark spec §10 approved
2. Run `/speckit-tasks` if not already done
3. Run `/speckit-implement` or implement manually following `tasks.md`
