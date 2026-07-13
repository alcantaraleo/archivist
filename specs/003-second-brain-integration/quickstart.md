# Quickstart Validation Guide: Second Brain Integration (Initial)

**Feature**: 003-second-brain-integration
**Date**: 2026-07-12

Verification guide after implementation. All automated checks run from repository root.

---

## Prerequisites

- Java 21 on `PATH`
- For manual smoke against a real vault: `ARCHIVIST_SECOND_BRAIN_PATH` pointing to an existing directory

```bash
export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/your/second-brain
```

Automated tests use the checked-in fixture corpus — no personal vault required for CI.

---

## Verification Steps

### 1. Full build

```bash
./gradlew build
```

**Expected**: `BUILD SUCCESSFUL`. Includes `:infrastructure:test` fixture contract tests and unchanged `:transport:test` (MCP stubs still return `[]`).

### 2. Infrastructure tests only

```bash
./gradlew :infrastructure:test
```

**Expected**: All tests pass:

| Test class                                  | Verifies                                                                                     |
| ------------------------------------------- | -------------------------------------------------------------------------------------------- |
| `MetadataEnvelopeParserTest`                | YAML envelope split; body extraction                                                         |
| `ZoneTypeResolverTest`                      | Path prefix + metadata → zone/type                                                           |
| `SourceIdNormalizerTest`                    | Stable sourceId rules                                                                        |
| `SecondBrainKnowledgeCorpusIntegrationTest` | Full corpus against [fixture-catalog-expected.json](contracts/fixture-catalog-expected.json) |

Integration test uses `@SpringBootTest(classes = SecondBrainCorpusTestConfiguration.class)` — minimal context, not full `ArchivistApplication`.

### 3. Domain remains Spring-free

```bash
./gradlew :domain:test
./gradlew :domain:dependencies --configuration compileClasspath
```

**Expected**: Tests pass; no `org.springframework` on compile classpath.

### 4. Application unchanged (stubs)

```bash
./gradlew :application:test
```

**Expected**: Pass; use cases still return empty lists; no `KnowledgeCorpus` injection.

### 5. Transport still independent of infrastructure

```bash
./gradlew :transport:dependencies --configuration compileClasspath
```

**Expected**: No `:infrastructure` project dependency.

### 6. Performance smoke (fixture corpus)

```bash
./gradlew :infrastructure:test --tests "*SecondBrainKnowledgeCorpusIntegrationTest*"
```

**Expected**: Completes in under 2 seconds on typical CI hardware (spec AC-13).

### 7. Manual corpus load (optional — real vault)

Requires a small Spring Boot test main or temporary `@SpringBootTest` that autowires `KnowledgeCorpus` from `:infrastructure` with your vault path. Not required for spec acceptance — fixture tests are authoritative.

If added during implementation as a dev-only smoke:

```bash
ARCHIVIST_SECOND_BRAIN_PATH=/path/to/vault ./gradlew :infrastructure:test --tests "*ManualCorpusSmoke*"
```

**Expected**: `catalog().size()` > 0; no `KnowledgeCorpusException`.

---

## Negative Tests

### Oversize entry flagged (not skipped)

Fixture or generated file exceeding `max-entry-bytes`. **Expected**: Present in `catalog()` and `loadAll()` with `contentAvailability = UNAVAILABLE_ENTRY_TOO_LARGE`, empty `content`, provenance from leading bytes.

### Unknown type omitted

Fixture `edge-cases/unknown-type.md` with invalid `type` value. **Expected**: Not in `catalog()`; present in `excludedSourceIds` in [fixture-catalog-expected.json](contracts/fixture-catalog-expected.json).

### Contract drift fails build

Change expected `type` in fixture JSON without updating mapper. **Expected**: `SecondBrainKnowledgeCorpusIntegrationTest` fails.

---

## What Does Not Change

- MCP tools still return `[]` (stub use cases)
- `./gradlew :transport:bootRun` still starts MCP server; tools unchanged
- No new MCP tools or env vars beyond optional `archivist.second-brain.max-entry-bytes` / mapping properties

---

## Related Documents

- [spec.md](../spec.md) — acceptance criteria
- [research.md](../research.md) — parallel load and memory guardrails
- [data-model.md](../data-model.md) — class layout
- [ADR-0002](../../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md) — caching deferred
