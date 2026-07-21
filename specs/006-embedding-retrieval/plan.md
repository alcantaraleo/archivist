# Implementation Plan: Embedding-Based Retrieval Strategy

**Branch**: `006-embedding-retrieval` | **Date**: 2026-07-20 | **Spec**: [spec.md](spec.md)  
**Status**: Plan complete (Phase 0–1) — `tasks.md` generated; ready for `/speckit-implement`  
**Input**: Feature specification from `specs/006-embedding-retrieval/spec.md`

---

## Summary

Add an opt-in **`embedding`** `RetrievalStrategy` in `infrastructure.retrieval` that ranks evidence by **vector similarity** over **chunked** corpus text. Embeddings come from a **pluggable `TextEmbedder`** (default **`local`** via Spring AI ONNX Transformers; opt-in **`openai-compatible`**; CI **`stub`**). Vectors live in a **pluggable `VectorStore`** (first: **`memory`** brute-force cosine).

**Public contract unchanged**: eight `port.in` capabilities, MCP tools, `KnowledgeGateway` signature, application use cases. Default **`lexical`** preserves pre-006 behaviour (AC-3).

**Docs + ADR (required)**: operator documentation for defaults, chunking/dedupe, and Compose examples; **`ADR-0005`** for SPI + chunking architecture. Compose template at `deploy/docker-compose.embedding.example.yml` with embeddings example + commented vector DB placeholders.

---

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**:

| Dependency | Version | Scope |
| ---------- | ------- | ----- |
| Spring Boot | 4.1.0 | infrastructure, transport (unchanged) |
| Spring AI | 2.0.0 | transport MCP (unchanged); **transformers / OpenAI embedding clients in infrastructure** |
| Apache Lucene | 9.12.1 | unchanged (BM25 only — not required for memory vector store v1) |

**Build**: Gradle Kotlin DSL; add Spring AI transformers (and OpenAI embedding client if used) to `infrastructure` via existing BOM

**Storage**: Second Brain via `KnowledgeCorpus`; vectors in JVM heap (`memory` store); future stores via SPI only

**Testing**:

| Layer | Focus |
| ----- | ----- |
| `domain` / `application` | No new tests expected — unchanged sources |
| `infrastructure` unit | Chunker, dedupe, registries, stub embedder, in-memory search |
| `infrastructure` integration | Fixture corpus + stub; AC-5–22; ranking JSON scenario |
| `transport` | MCP contract regression unchanged |

**Target Platform**: JVM; long-lived STDIO MCP process (index cache amortises rebuilds)

**Performance Goals**: Stub embedder + fixture corpus index build + query **< 5 s** (CI smoke)

**Constraints**:

- Embedding / vector / Spring AI model types MUST NOT appear on `domain` or `application` compile classpaths
- `transport` Java MUST NOT import `io.archivist.infrastructure.*`
- Strategy name `"embedding"` and adapter ids are configuration-only — never on `port.in`
- CI embedding tests MUST use `stub` (no ONNX download / no network)
- No production pgvector/Chroma adapters in this feature

**Scale/Scope**: Personal Second Brain; modest entry count; single active strategy at runtime

---

## Constitution Check

_GATE: Evaluated before Phase 0. Re-checked after Phase 1 design._

| Gate | Principle | Check | Pre-design | Post-design |
| ---- | --------- | ----- | ---------- | ----------- |
| Domain independence | I | No Spring/embedding libs in domain/application | ✅ PASS | ✅ PASS — infrastructure-only |
| Contract language | II | No `vectorSearch` / `embed` on `port.in` | ✅ PASS | ✅ PASS — config keys only |
| No reasoning | III | `List<Evidence>` only | ✅ PASS | ✅ PASS |
| Provenance | III | Full provenance on hits | ✅ PASS | ✅ PASS — load from corpus |
| Spec approved | IV | Spec approved before **implementation** | ✅ PASS | ✅ PASS — Approved 2026-07-20 |
| Strategy hidden | V | Behind `KnowledgeGateway` | ✅ PASS | ✅ PASS — private strategy + SPIs |
| No Obsidian coupling | V | Domain fields only | ✅ PASS | ✅ PASS |
| MCP is adapter | I / V | Transport → `port.in` only | ✅ PASS | ✅ PASS |
| Stable contract | II / VIII | No `port.in` changes | ✅ PASS | ✅ PASS |
| Build tool | Technology | Gradle Kotlin DSL | ✅ PASS | ✅ PASS |
| Injection | Technology | Constructor injection | ✅ PASS | ✅ PASS |

**Gate result**: All gates pass. Spec approved — ready for `/speckit-tasks` and implementation.

**Post-design note**: Spring AI transformers dependency is justified and scoped to infrastructure — Constitution technology constraints already name Spring AI for infra. Private SPIs prevent vendor lock-in at the strategy boundary.

---

## Project Structure

### Documentation (this feature)

```text
specs/006-embedding-retrieval/
├── spec.md
├── plan.md                              # This file
├── research.md                          # SPI, local/ONNX, chunking, stub CI
├── data-model.md                        # Components + EmbeddedChunk
├── quickstart.md                        # Verification guide
├── contracts/
│   ├── README.md
│   ├── knowledge-gateway-embedding-semantics.md
│   ├── embedding-configuration.md
│   ├── text-embedder-spi.md
│   ├── vector-store-spi.md
│   ├── retrieval-strategy-spi.md
│   └── fixture-embedding-ranking-scenario.json
├── checklists/
│   └── requirements.md
└── tasks.md                             # /speckit-tasks ✅
```

### Source Code (repository root — planned changes)

```text
gradle/libs.versions.toml                 # spring-ai transformers / openai embedding libs if needed

infrastructure/build.gradle.kts           # embedding-related implementation deps

infrastructure/src/main/java/io/archivist/infrastructure/retrieval/
├── RetrievalProperties.java              # EXTEND — nested EmbeddingProperties
├── RetrievalConfiguration.java           # EXTEND — embedding beans + registries
├── EmbeddingRetrievalStrategy.java       # NEW
└── embedding/                            # NEW package
    ├── TextEmbedder.java
    ├── VectorStore.java
    ├── EmbeddedChunk.java
    ├── ScoredChunk.java
    ├── EntryChunker.java
    ├── EmbeddingIndexCache.java
    ├── EmbeddingProperties.java
    ├── TextEmbedderRegistry.java
    ├── VectorStoreRegistry.java
    ├── LocalTextEmbedder.java
    ├── OpenAiCompatibleTextEmbedder.java
    ├── StubTextEmbedder.java
    └── InMemoryVectorStore.java

infrastructure/src/test/java/.../retrieval/
├── embedding/                            # NEW tests
│   ├── EntryChunkerTest.java
│   ├── InMemoryVectorStoreTest.java
│   ├── StubTextEmbedderTest.java
│   ├── TextEmbedderRegistryTest.java
│   ├── VectorStoreRegistryTest.java
│   ├── EmbeddingRetrievalStrategyTest.java
│   ├── EmbeddingRetrievalIntegrationTest.java
│   └── EmbeddingRankingDifferentiationTest.java
└── RetrievalStrategyRegistryTest.java    # EXTEND — embedding registration

infrastructure/src/test/resources/fixture-corpus/...
└── ranking-embedding-*.md                # NEW — AC-11 fixtures

deploy/docker-compose.embedding.example.yml   # NEW
docs/adr/ADR-0005-pluggable-embedding-retrieval.md   # NEW
README.md                                 # UPDATE — Phase 2 roadmap + doc pointers
```

**Unchanged modules**: `domain`, `application`, `transport` sources (no MCP/tool edits).

**Structure decision**: Same Gradle multi-module layout; all embedding code under `infrastructure.retrieval` (+ `embedding` subpackage). Ops template under `deploy/`.

---

## Phase 0 Output

**Artifact**: [research.md](research.md)

Resolved:

- Private `TextEmbedder` + `VectorStore` SPIs
- `local` = Spring AI Transformers ONNX (MiniLM, 384-d)
- `openai-compatible` = OpenAI embeddings HTTP shape
- `stub` for CI; `memory` = brute-force cosine
- Chunk 1200/150 chars; best-score `sourceId` dedupe
- ADR-0005 + Compose path + doc set

---

## Phase 1 Output

| Artifact | Path |
| -------- | ---- |
| Data model | [data-model.md](data-model.md) |
| Contracts | [contracts/](contracts/) |
| Quickstart | [quickstart.md](quickstart.md) |

**Post-design constitution re-check**: ✅ All gates pass (spec Approved 2026-07-20).

---

## Implementation Notes (for `/speckit-tasks`)

1. Add ADR-0005 draft early (can refine during implement) — blocks polish acceptance (AC-20)
2. Implement SPIs + `memory` + `stub` **before** `local` / `openai-compatible` so CI path is green first
3. Reuse `CorpusFingerprint` from 005 for cache invalidation; extend key with embedder identity
4. Extend `RetrievalProperties` with nested `embedding` (mirror `bm25` nesting)
5. Register `embedding` strategy without changing `KnowledgeGatewayImpl` interface
6. Craft AC-11 fixture with injectable stub vectors (not live MiniLM)
7. Add Compose template + README/quickstart docs before marking docs ACs done
8. Verify `./gradlew build` with default `lexical` for AC-3 regression
9. Spec is **Approved** (2026-07-20) — implementation may proceed after `/speckit-tasks`

---

## Complexity Tracking

> No unjustified constitution violations.

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --------- | ---------- | ------------------------------------ |
| _None_ | — | — |

---

## References

- [spec.md](spec.md) — AC-1–AC-22
- [research.md](research.md)
- [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md)
- [ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md)
- [004 plan](../004-lexical-retrieval/plan.md) — strategy SPI
- [005 plan](../005-bm25-retrieval/plan.md) — fingerprint / cache pattern
