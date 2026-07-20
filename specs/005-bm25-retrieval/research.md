# Research: BM25 Retrieval Strategy

**Feature**: 005-bm25-retrieval  
**Date**: 2026-07-20  
**Status**: Complete — all technical decisions resolved

**Inputs**: [spec.md](spec.md) clarifications (2026-07-20), [ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md), spec 004 `LexicalScorer` tokenisation

---

## Decision 1: Apache Lucene Module Scope

**Decision**: Add **Apache Lucene 9.12.1** artifacts as `implementation` dependencies on **`infrastructure` only**:

| Artifact | Role |
| -------- | ---- |
| `org.apache.lucene:lucene-core` | Index, search, BM25 similarity |
| `org.apache.lucene:lucene-analysis-common` | Filters/tokenizers for custom analyzer |
| `org.apache.lucene:lucene-queryparser` | Optional helpers; primary query build may be manual |

Pin version in `gradle/libs.versions.toml` (`lucene = "9.12.1"`). **No** Lucene types in `domain`, `application`, or `transport` public APIs.

**Rationale**: Spec mandates in-process BM25 with standard Lucene BM25Similarity. Lucene is the de facto JVM choice; keeps implementation out of domain. Version 9.x is stable on Java 21; no Spring Boot BOM requirement for Lucene.

**Alternatives considered**:

- Custom inverted index + BM25 math: Rejected — error-prone; reinvents Lucene
- Elasticsearch client: Rejected — external process; scope explosion
- Spring AI vector store / embedding stack: Rejected — out of scope; not BM25

---

## Decision 2: In-Memory Index Directory

**Decision**: Use Lucene **`ByteBuffersDirectory`** (or `MMapDirectory` on temp path only in tests if needed) held by a private **`Bm25IndexCache`** component inside `infrastructure.retrieval`. Index documents keyed by **`sourceId`** (StringField, stored + indexed for lookup).

**Rationale**: ADR-0004 requires per-process in-memory cache; no persistent index in spec 005. `ByteBuffersDirectory` avoids disk IO in CI and matches modest personal corpus size.

**Alternatives considered**:

- Rebuild on every `retrieve()` (ADR Option 7): Rejected — wastes long-lived MCP sessions
- On-disk persistent index (ADR Option 5): Rejected — deferred by ADR-0002

---

## Decision 3: BM25 Similarity Parameters (k1, b)

**Decision**: Use Lucene defaults unless overridden via configuration:

| Parameter | Lucene default | Property |
| --------- | -------------- | -------- |
| k1 | **1.2** | `archivist.retrieval.bm25.k1` (optional override) |
| `b` | **0.75** | `archivist.retrieval.bm25.b` (optional override) |

Implement with `BM25Similarity(k1, b)` on `IndexWriterConfig` / `IndexSearcher` when properties differ from defaults; otherwise use `new BM25Similarity()` for library defaults.

**Rationale**: Spec asks plan to document values; Lucene defaults are industry-standard Okapi BM25. Exposing properties allows tuning without code change.

**Alternatives considered**:

- Hard-code only: Rejected — spec lists configurable k1/b
- Non-BM25 similarity: Rejected — violates spec intent

---

## Decision 4: Field Model and Boosts (3 : 2 : 1)

**Decision**: Three searchable **`TextField`** instances per index document:

| Lucene field name | Source | Default boost |
| ----------------- | ------ | ------------- |
| `title` | `provenance.title()` | `archivist.retrieval.bm25.field-boost-title` = **3.0** |
| `tags` | space-joined lowercased tags | **2.0** |
| `body` | `evidence.content()` when `AVAILABLE`; omitted/empty when size-limited | **1.0** |

Apply boosts via `Field.setBoost(float)` at index time (or per-field type boost in writer config). Matches Phase 1 lexical weights in `LexicalScorer`.

Additional **non-scored** fields for filtering:

| Field | Type | Purpose |
| ----- | ---- | ------- |
| `sourceId` | `StringField` (stored) | Map hit → `Evidence` |
| `knowledgeType` | `StringField` (indexed, not tokenized) | `Query.types()` filter |
| `knowledgeZone` | `StringField` (indexed, not tokenized) | `Query.zones()` filter |

**Rationale**: Spec clarification preserves title/tags/body priority; filters mirror `LexicalRetrievalStrategy.matchesFilters`.

**Alternatives considered**:

- Single concatenated field: Rejected — loses per-field boost semantics
- Index body for size-limited entries: Rejected — ADR-0003 policy

---

## Decision 5: Tokenisation Parity with Lexical (AC-11 baseline)

**Decision**: **Share tokenisation rules** with spec 004:

1. Extract package-private **`RetrievalTokenization`** (or delegate to package-visible `LexicalScorer#tokenize`) in `io.archivist.infrastructure.retrieval`.
2. **Query side**: tokenise `query.text()` with that helper; build a Lucene `BooleanQuery` with **MUST** clauses — each token must appear in at least one of `title`, `tags`, or `body` (same AND semantics as lexical “every token must match somewhere”).
3. **Index side**: custom **`Analyzer`** using the same split regex as `LexicalScorer`: `text.toLowerCase(Locale.ROOT)` then split on `[\\s\\p{Punct}]+`, drop empties.

**Rationale**: Spec §3 requires identical tokenisation for operator expectations and ranking comparison tests.

**Alternatives considered**:

- Lucene `StandardAnalyzer` (stemming/stopwords): Rejected — diverges from lexical
- OR-only multi-term query: Rejected — lexical requires all tokens to contribute to score

---

## Decision 6: Corpus Fingerprint (ADR-0004 Option 2)

**Decision**: Compute fingerprint from **`KnowledgeCorpus.catalog()`** only (no filesystem mtime in initial delivery):

```text
1. Load catalog list
2. Sort by sourceId (lexicographic)
3. For each Provenance p, append line: sourceId + "|" + updated.toEpochMilli() + "|" + contentAvailability.name()
4. SHA-256 hex digest of UTF-8 concatenation, prefixed with entry count: count + ":" + digest
```

Example shape: `42:a1b2c3...` (not a fixed length in tests — compare equality only).

**Rationale**: Reflects catalog-visible corpus state per ADR-0004; uses existing port; respects parse omissions (catalog is source of truth for discoverable entries). `contentAvailability` catches transitions when oversize policy triggers reload.

**Alternatives considered**:

- Filesystem aggregate mtime walk: Deferred — may supplement in future indexing spec if fingerprint blind spots found
- Fingerprint on every `loadAll()` payload hash: Rejected — expensive; catalog metadata sufficient for edit detection when `updated` changes on save

---

## Decision 7: Index TTL Default

**Decision**: Default **`archivist.retrieval.bm25.index-ttl`** = **`PT15M`** (15 minutes, ISO-8601 `Duration`).

Invalidate cached index when **either** fingerprint differs **or** `Instant.now().isAfter(builtAt.plus(ttl))`.

**Rationale**: Bounds staleness if fingerprint misses an edge case (ADR Option 3); 15 minutes is acceptable for personal vault editing without rebuilding every MCP tool call.

**Alternatives considered**:

- No TTL (fingerprint only): Rejected — ADR chose combined 2+3
- TTL 24h: Rejected — too stale for active editing sessions

---

## Decision 8: Index Cache Concurrency

**Decision**: **`synchronized`** rebuild + search on the cache holder (single JVM, single active BM25 strategy bean). Search uses point-in-time `IndexSearcher` from current reader; rebuild closes prior directory/reader.

**Rationale**: Personal corpus + single-threaded MCP STDIO usage makes fine-grained RW locks unnecessary. Document with `ponytail:` if contention appears — upgrade to `ReentrantReadWriteLock`.

**Alternatives considered**:

- Lock-free double buffering: Rejected — YAGNI for scale
- Rebuild on every retrieve in tests only: Optional test property — not default

---

## Decision 9: Configuration Properties Structure

**Decision**: Extend retrieval configuration:

| Property | Default |
| -------- | ------- |
| `archivist.retrieval.active-strategy` | `lexical` (unchanged) |
| `archivist.retrieval.max-results` | `20` (unchanged) |
| `archivist.retrieval.bm25.field-boost-title` | `3.0` |
| `archivist.retrieval.bm25.field-boost-tags` | `2.0` |
| `archivist.retrieval.bm25.field-boost-body` | `1.0` |
| `archivist.retrieval.bm25.index-ttl` | `PT15M` |
| `archivist.retrieval.bm25.k1` | `1.2` |
| `archivist.retrieval.bm25.b` | `0.75` |

Bind via `@ConfigurationProperties` — nested `Bm25Properties` under `RetrievalProperties` or `archivist.retrieval.bm25.*` prefix (implementation choice; both valid in Spring Boot 4).

**Rationale**: Spec §3 table; keeps BM25 tuning isolated from lexical defaults.

---

## Decision 10: AC-11 Ranking Fixture Scenario

**Decision**: Add two **CONCEPT** fixture entries under `infrastructure/src/test/resources/fixture-corpus/wiki/concepts/`:

| sourceId (stable) | Intent |
| ----------------- | ------ |
| `ranking-bm25-sparse` | Title mentions query term once; body minimal mention |
| `ranking-bm25-dense` | Title neutral; body repeats query term many times |

**Query**: `retrieveContext` with text **`retrieval ranking probe`** (tokens: `retrieval`, `ranking`, `probe` — craft bodies so both match all tokens).

**Expected ordering difference**:

- **Lexical**: substring scoring counts at most once per token per field → sparse title hit can beat dense body repetition.
- **BM25**: term frequency in body elevates `ranking-bm25-dense` above sparse when both satisfy AND token constraints.

Document exact `sourceId` order in [contracts/fixture-bm25-ranking-scenario.json](contracts/fixture-bm25-ranking-scenario.json). Integration test runs same `Query` twice with `active-strategy` `lexical` vs `bm25` and asserts **order differs** and **top hit sourceId differs**.

**Rationale**: AC-11 requires provable ordering delta on checked-in fixtures, not private vaults.

---

## Decision 11: Test and Performance Guard

**Decision**:

| Test | Scope |
| ---- | ----- |
| `Bm25RetrievalStrategyTest` | Unit — filters, blank query, mocked index/cache |
| `CorpusFingerprintTest` | Unit — deterministic digest from catalog fixtures |
| `Bm25RetrievalIntegrationTest` | Spring minimal context + fixture corpus — AC-5–AC-10, AC-12–AC-13 |
| `Bm25RankingDifferentiationTest` | AC-11 lexical vs bm25 order |
| `Bm25IndexCacheInvalidationTest` | Fingerprint change + TTL expiry (may use controllable clock or test hooks) |

**AC-14**: `@Timeout(5)` on integration test method covering index build + first query against full fixture corpus.

**Rationale**: Mirrors spec 004 test split; keeps transport mocked/isolated.

---

## Decision 12: Shared Filter / Dedupe Logic

**Decision**: Reuse the same **post-search pipeline** as lexical where possible:

- Type/zone: Lucene `Filter` or `BooleanQuery` MUST clauses on `knowledgeType` / `knowledgeZone` when `Query` sets non-empty
- **Dedupe + limit**: same `sourceId` walk as `LexicalRetrievalStrategy.dedupeAndLimit` after loading `Evidence` via `KnowledgeCorpus.loadBySourceId` or in-memory map built during index rebuild

**Rationale**: DRY inside infrastructure module; gateway semantics unchanged.

**Alternatives considered**:

- Duplicate filter logic only in BM25: Rejected — drift risk

---

## References

- [spec.md](spec.md)
- [ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md)
- [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md)
- [spec 004 research](../004-lexical-retrieval/research.md)
- Apache Lucene 9.12 — `BM25Similarity`, `ByteBuffersDirectory`
