# Implementation Plan: BM25 Retrieval Strategy

**Branch**: `005-bm25-retrieval` | **Date**: 2026-07-20 | **Spec**: [spec.md](spec.md)  
**Status**: Plan complete (Phase 0–1) — ready for `/speckit-tasks`  
**Input**: Feature specification from `specs/005-bm25-retrieval/spec.md`

---

## Summary

Add an opt-in **`bm25`** `RetrievalStrategy` in `infrastructure.retrieval` using **Apache Lucene 9.12.1** (in-memory `ByteBuffersDirectory`, standard `BM25Similarity`). Ranking honours **3 : 2 : 1** field boosts aligned with `LexicalScorer`, **identical tokenisation** to spec 004, and the same `Query` filters, dedupe, and content-availability rules.

**Public contract unchanged**: eight `port.in` capabilities, MCP tools, `KnowledgeGateway` signature, application use cases. Default **`lexical`** preserves pre-005 behaviour (AC-3).

**Index lifecycle** ([ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md)): per-process cache invalidated when **corpus fingerprint** (from `catalog()`) changes **or** **TTL** (`PT15M` default) expires.

---

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**:

| Dependency | Version | Scope |
| ---------- | ------- | ----- |
| Spring Boot | 4.1.0 | infrastructure, transport (unchanged) |
| Spring AI | 2.0.0 | transport MCP (unchanged) |
| Apache Lucene | **9.12.1** | **infrastructure only** (new) |

**Build**: Gradle Kotlin DSL; add Lucene to `gradle/libs.versions.toml`

**Storage**: Second Brain via spec 003 `KnowledgeCorpus`; BM25 index in JVM heap (ephemeral)

**Testing**:

| Layer | Focus |
| ----- | ----- |
| `domain` / `application` | No new tests expected — unchanged sources |
| `infrastructure` unit | Tokenisation parity, fingerprint, BM25 query building |
| `infrastructure` integration | Fixture corpus, AC-5–14, ranking JSON scenario |
| `transport` | MCP contract regression unchanged |

**Target Platform**: JVM; long-lived STDIO MCP process (index cache amortises rebuilds)

**Performance Goals**: Index build + first query on fixture corpus **< 5 s** (AC-14 smoke)

**Constraints**:

- Lucene MUST NOT appear on `domain` or `application` compile classpaths
- `transport` Java MUST NOT import `io.archivist.infrastructure.*`
- Tokenisation MUST match `LexicalScorer` (`[\\s\\p{Punct}]+` split, lowercase ROOT)
- No filesystem watchers; no persistent on-disk index in this spec
- BM25 strategy name `"bm25"` is configuration-only — never on `port.in`

**Scale/Scope**: Personal Second Brain; modest entry count; single active strategy at runtime

---

## Constitution Check

_GATE: Evaluated before Phase 0. Re-checked after Phase 1 design._

| Gate | Principle | Check | Pre-design | Post-design |
| ---- | --------- | ----- | ---------- | ----------- |
| Domain independence | I | No Spring/Lucene in domain/application | ✅ PASS | ✅ PASS — changes infrastructure-only |
| Contract language | II | No `bm25Search` on `port.in` | ✅ PASS | ✅ PASS — `bm25` is config key only |
| No reasoning | III | `List<Evidence>` only | ✅ PASS | ✅ PASS |
| Provenance | III | Full provenance on hits | ✅ PASS | ✅ PASS — map from corpus |
| Spec approved | IV | Spec approved 2026-07-20 | ✅ PASS | ✅ PASS |
| Strategy hidden | V | Behind `KnowledgeGateway` | ✅ PASS | ✅ PASS — `Bm25RetrievalStrategy` private |
| No Obsidian coupling | V | Domain fields only in index | ✅ PASS | ✅ PASS |
| MCP is adapter | I / V | Transport → `port.in` only | ✅ PASS | ✅ PASS |
| Stable contract | II / VIII | No `port.in` changes | ✅ PASS | ✅ PASS |
| Build tool | Technology | Gradle Kotlin DSL | ✅ PASS | ✅ PASS |
| Injection | Technology | Constructor injection | ✅ PASS | ✅ PASS |

**Gate result**: All gates pass. No Complexity Tracking entries required.

**Post-design note**: New Lucene dependency is justified and scoped to infrastructure — not a domain leak. Constitution V explicitly allows BM25 as private infrastructure.

---

## Project Structure

### Documentation (this feature)

```text
specs/005-bm25-retrieval/
├── spec.md
├── plan.md                              # This file
├── research.md                          # Lucene, fingerprint, TTL, AC-11 scenario
├── data-model.md                        # Infrastructure components + Lucene schema
├── quickstart.md                        # Verification guide
├── contracts/
│   ├── README.md
│   ├── knowledge-gateway-bm25-semantics.md
│   ├── bm25-configuration.md
│   ├── corpus-fingerprint-contract.md
│   ├── retrieval-strategy-spi.md
│   └── fixture-bm25-ranking-scenario.json
├── checklists/
│   └── requirements.md
└── tasks.md                             # Phase 2 — /speckit-tasks (not yet)
```

### Source Code (repository root — planned changes)

```text
gradle/libs.versions.toml                  # lucene version + library aliases

infrastructure/build.gradle.kts            # lucene-core, lucene-analysis-common, lucene-queryparser

infrastructure/src/main/java/io/archivist/infrastructure/retrieval/
├── LexicalScorer.java                     # MAY delegate tokenize to shared helper
├── RetrievalTokenization.java             # NEW — shared token rules
├── RetrievalProperties.java             # EXTEND — bm25 nested props
├── RetrievalConfiguration.java            # EXTEND — bm25RetrievalStrategy @Bean
├── CorpusFingerprint.java                 # NEW
├── Bm25IndexCache.java                    # NEW
├── Bm25IndexBuilder.java                  # NEW
├── ArchivistCorpusAnalyzer.java           # NEW — Lucene analyzer matching lexical split
└── Bm25RetrievalStrategy.java             # NEW

infrastructure/src/test/java/.../retrieval/
├── CorpusFingerprintTest.java             # NEW
├── Bm25RetrievalStrategyTest.java         # NEW
├── Bm25RetrievalIntegrationTest.java      # NEW
├── Bm25RankingDifferentiationTest.java    # NEW — AC-11
└── Bm25IndexCacheInvalidationTest.java    # NEW

infrastructure/src/test/resources/fixture-corpus/wiki/concepts/
├── ranking-bm25-sparse.md                 # NEW — AC-11 fixture
└── ranking-bm25-dense.md                  # NEW — AC-11 fixture
```

**Unchanged modules**: `domain`, `application`, `transport` source (except no edits expected for 005).

**Structure decision**: Single Gradle multi-module layout unchanged from specs 001–004; BM25 stays entirely under `infrastructure.retrieval`.

---

## Phase 0 Output

**Artifact**: [research.md](research.md)

Resolved items from spec open questions:

- Lucene **9.12.1**, infrastructure-only
- In-memory index + fingerprint + **PT15M** TTL
- k1 **1.2**, b **0.75**; field boosts **3 / 2 / 1**
- Tokenisation parity with `LexicalScorer`
- AC-11 ranking fixture scenario documented

---

## Phase 1 Output

| Artifact | Path |
| -------- | ---- |
| Data model | [data-model.md](data-model.md) |
| Contracts | [contracts/](contracts/) |
| Quickstart | [quickstart.md](quickstart.md) |

**Post-design constitution re-check**: ✅ All gates pass (see table above).

---

## Implementation Notes (for `/speckit-tasks`)

1. Add Lucene to version catalogue before strategy code
2. Extract or share tokenisation before BM25 analyzer — run existing `LexicalScorerTest` after refactor
3. Implement fingerprint contract before cache integration tests
4. Create ranking fixture markdown **before** AC-11 test — tune bodies until lexical top ≠ BM25 top
5. Register `bm25` bean without changing `KnowledgeGatewayImpl` or registry interface
6. Verify `./gradlew build` with default properties (lexical) for AC-3 regression

---

## Complexity Tracking

> No unjustified constitution violations.

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --------- | ---------- | ------------------------------------ |
| _None_ | — | — |

---

## References

- [spec.md](spec.md) — acceptance criteria AC-1–AC-15
- [ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md)
- [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md)
- [004 plan](../004-lexical-retrieval/plan.md) — strategy SPI and MCP obliviousness
