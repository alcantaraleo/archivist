# Quickstart Validation Guide: BM25 Retrieval Strategy

**Feature**: 005-bm25-retrieval  
**Date**: 2026-07-20

Runnable checks after implementation. Run from repository root unless noted.

---

## Prerequisites

- Java 21
- Specs 003 and 004 merged — fixture corpus at `infrastructure/src/test/resources/fixture-corpus/`
- Optional manual vault: `export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/second-brain`

CI uses fixture corpus only.

---

## 1. Full build and regression

```bash
./gradlew build
```

**Expected**: `BUILD SUCCESSFUL`.

| Maps to | Check |
| ------- | ----- |
| AC-1 | Full build; spec 002 MCP contract tests pass unchanged |
| AC-2 | No transport imports of infrastructure (existing isolation test) |
| AC-3 | Default `lexical` — fixture lexical integration unchanged |

---

## 2. BM25 infrastructure tests

```bash
./gradlew :infrastructure:test --tests "*Bm25*"
```

**Expected**: All BM25-related tests pass, including:

| Test area | AC |
| --------- | -- |
| Basic retrieve + provenance | AC-5 |
| Type filters (`findDecisions`, etc.) | AC-6–AC-8 |
| Dedupe + max results | AC-9–AC-10 |
| Ranking vs lexical | AC-11 — see [fixture-bm25-ranking-scenario.json](contracts/fixture-bm25-ranking-scenario.json) |
| Size-limited entry policy | AC-12–AC-13 |
| Index build + query timing | AC-14 (`@Timeout(5)`) |

Full infrastructure suite:

```bash
./gradlew :infrastructure:test
```

---

## 3. Active strategy registration

```bash
./gradlew :infrastructure:test --tests "*RetrievalStrategyRegistry*"
```

With test property or dedicated test context setting `active-strategy=bm25`:

**Expected**: Startup succeeds; registry lists `lexical` and `bm25` (AC-4).

---

## 4. Fingerprint invalidation (when implemented)

```bash
./gradlew :infrastructure:test --tests "*CorpusFingerprint*" --tests "*Bm25IndexCache*"
```

**Expected**:

- Stable catalog → fingerprint stable
- Simulated catalog change → rebuild on next retrieve
- TTL expiry test → rebuild without fingerprint change

See [corpus-fingerprint-contract.md](contracts/corpus-fingerprint-contract.md).

---

## 5. Domain and application unchanged

```bash
./gradlew :domain:test :application:test
```

**Expected**: Pass without new failures (AC-15).

```bash
./gradlew :domain:dependencies --configuration compileClasspath | rg -i lucene || true
./gradlew :application:dependencies --configuration compileClasspath | rg -i lucene || true
```

**Expected**: No Lucene on domain/application classpaths.

---

## 6. Transport MCP contract (unchanged)

```bash
./gradlew :transport:test
```

**Expected**: Tool schemas and registration identical to spec 002 fixtures (AC-1, AC-15).

---

## 7. Manual BM25 smoke (optional)

```bash
export ARCHIVIST_SECOND_BRAIN_PATH=infrastructure/src/test/resources/fixture-corpus
export SPRING_APPLICATION_JSON='{"archivist":{"retrieval":{"active-strategy":"bm25"}}}'
./gradlew :transport:bootRun
```

Invoke MCP `retrieveContext` with a multi-word query via your MCP client.

**Expected**: Ordered evidence with provenance; no new tools.

---

## 8. Configuration reference

See [contracts/bm25-configuration.md](contracts/bm25-configuration.md) and [research.md](research.md) for defaults (TTL `PT15M`, k1 `1.2`, b `0.75`, boosts 3/2/1).

---

## Failure triage

| Symptom | Likely cause |
| ------- | ------------ |
| AC-11 fails (same order) | Fixture bodies do not create TF delta; revise ranking markdown files |
| AC-14 timeout | Index rebuild every call — check cache invalidation logic |
| Startup fails on `bm25` | Missing bean or duplicate `name()` |
| Lexical regression | Shared tokenisation change — run `LexicalScorerTest` |
