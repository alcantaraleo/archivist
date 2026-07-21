# Tasks: Embedding-Based Retrieval Strategy

**Input**: Design documents from `specs/006-embedding-retrieval/`

**Prerequisites**: plan.md ✅ | spec.md ✅ (approved 2026-07-20) | research.md ✅ | data-model.md ✅ | contracts/ ✅ | quickstart.md ✅

**Stack**: Java 21 · Spring Boot 4.1.0 · Spring AI 2.0.0 (transformers / OpenAI embeddings in infrastructure only) · Gradle (Kotlin DSL) · specs 003–005 (`KnowledgeCorpus`, retrieval SPI, `CorpusFingerprint`)

**Tests**: Required by spec (AC-1–AC-22). Domain/application unchanged — no new tests expected there. Infrastructure: plain JUnit unit tests + `@SpringBootTest(classes = RetrievalTestConfiguration.class)` integration tests per `quickstart.md`. CI embedding path uses `stub` + `memory` only (no ONNX download, no network — AC-14). Transport MCP contract tests remain unchanged (AC-1, AC-21).

**User story mapping** (spec scenarios → deliverable increments):

| Story | Deliverable | Acceptance Criteria |
| ----- | ----------- | ------------------- |
| US1 | Nested `embedding` `@ConfigurationProperties` | Config contract; AC-4/AC-16 prep |
| US2 | Private `TextEmbedder` + `VectorStore` SPIs + chunk records | AC-15 surface |
| US3 | `stub` embedder + `memory` store (CI path first) | AC-14 core adapters |
| US4 | `EntryChunker` (title/tags/body; size-limit policy) | Chunking policy; AC-12/AC-22 prep |
| US5 | Embedder/store registries + `EmbeddingIndexCache` | Fingerprint + TTL + embedder identity |
| US6 | `EmbeddingRetrievalStrategy` + unit tests | Filters, dedupe, availability |
| US7 | Spring beans + `embedding` strategy registration | AC-4, AC-16 |
| US8 | Embedding integration tests on fixture corpus | AC-5–AC-10, AC-12–AC-14, AC-22 |
| US9 | AC-11 ranking fixtures + differentiation test | AC-11 |
| US10 | `local` (ONNX) + `openai-compatible` adapters | Spec adapters; not CI-gated |
| US11 | ADR, Compose template, operator docs, regression | AC-1–AC-3, AC-17–AC-21 |

---

## Phase 1: Setup (Spring AI Embedding Dependencies)

**Purpose**: Add embedding-related Spring AI libraries to the version catalogue and infrastructure module only.

- [x] T001 Add Spring AI transformers (and OpenAI embedding client if needed) library aliases to `gradle/libs.versions.toml` — coordinates via Spring AI BOM `2.0.0` per `research.md` Decision 2–3; do not bump BOM unless required
- [x] T002 Update `infrastructure/build.gradle.kts` — `implementation` dependencies for transformers / OpenAI embedding client; verify `:domain:dependencies` and `:application:dependencies` compile classpaths contain no new Spring AI embedding artifacts
- [x] T003 Run `./gradlew :infrastructure:compileJava` — succeeds with new deps on infrastructure classpath only (depends on T001–T002)

**Checkpoint**: Embedding libs scoped to infrastructure. No production embedding strategy code yet.

---

## Phase 2: Foundational (Contract & Prerequisite Gate)

**Purpose**: Confirm 006 contract fixtures and specs 004–005 retrieval baseline before embedding implementation.

⚠️ **CRITICAL**: Do not change lexical/BM25 expected outcomes without spec workflow. AC-3 requires default `lexical` behaviour unchanged. Domain/`port.in`/MCP must stay untouched (AC-21).

- [x] T004 Verify contract fixtures in `specs/006-embedding-retrieval/contracts/` — `knowledge-gateway-embedding-semantics.md`, `embedding-configuration.md`, `text-embedder-spi.md`, `vector-store-spi.md`, `retrieval-strategy-spi.md`, and `fixture-embedding-ranking-scenario.json` align with approved `spec.md` §3–5; fix doc drift only (no implementation)
- [x] T005 Verify specs 004–005 prerequisite — `./gradlew :infrastructure:test --tests "*LexicalRetrievalIntegrationTest*" --tests "*Bm25RetrievalIntegrationTest*" --tests "*RetrievalStrategyRegistryTest*"` pass; `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/support/RetrievalTestConfiguration.java` exists; `CorpusFingerprint` available for reuse

**Checkpoint**: Lexical + BM25 integration green. 006 contracts ready for test assertions.

---

## Phase 3: User Story 1 — Embedding Configuration (Priority: P1) 🎯 MVP

**Goal**: Bind `archivist.retrieval.embedding.*` properties with documented defaults without activating embedding yet.

**Independent Test**: Unit test passes for default values; `./gradlew :infrastructure:compileJava` with extended properties classes.

### Implementation

- [x] T006 [US1] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/EmbeddingProperties.java` — fields per `contracts/embedding-configuration.md` / `data-model.md` (`store`=`memory`, `embedder`=`local`, `dimensions`=384, `topK`=50, `minScore`=null, `indexTtl`=`PT15M`, `chunkSizeChars`=1200, `chunkOverlapChars`=150, nested `local` + `openai`)
- [x] T007 [US1] Extend `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/RetrievalProperties.java` — nested `embedding` (`EmbeddingProperties`); ensure `ArchivistRetrievalAutoConfiguration` / `@EnableConfigurationProperties` binds nested type if required

### Tests

- [x] T008 [US1] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/embedding/EmbeddingPropertiesTest.java` — JUnit 5; assert defaults match `specs/006-embedding-retrieval/contracts/embedding-configuration.md`; reject invalid dimensions / chunk overlap where validated (depends on T006–T007)

**Checkpoint**: Configuration contract implementable. MVP for property wiring review — not feature-complete.

---

## Phase 4: User Story 2 — Private SPIs and Chunk Records (Priority: P2)

**Goal**: Infrastructure-only embedder/store SPIs and chunk value types; strategy will depend only on these (AC-15).

**Independent Test**: `./gradlew :infrastructure:compileJava` with SPI package; no Spring AI types on SPI method signatures.

### Implementation

- [x] T009 [P] [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/TextEmbedder.java` — `name()`, `dimensions()`, `embed(String)`, `embedBatch(List<String>)` per `contracts/text-embedder-spi.md`
- [x] T010 [P] [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/VectorStore.java` — `name()`, `replaceAll`, `search`, `clear` per `contracts/vector-store-spi.md`
- [x] T011 [P] [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/EmbeddedChunk.java` — package-private record (`sourceId`, `chunkIndex`, `text`, `vector`, `knowledgeType`, `knowledgeZone`)
- [x] T012 [P] [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/ScoredChunk.java` — package-private record (`chunk`, `score`)

**Checkpoint**: SPI surface compiles; no domain/application changes.

---

## Phase 5: User Story 3 — Stub Embedder + In-Memory Store (Priority: P3)

**Goal**: First adapters that make CI offline embedding path possible (`stub` + `memory`).

**Independent Test**: `./gradlew :infrastructure:test --tests "*StubTextEmbedder*" --tests "*InMemoryVectorStore*"` passes.

### Implementation

- [x] T013 [P] [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/StubTextEmbedder.java` — `name()`=`stub`; deterministic vectors from normalised text hash into configured dimensions per `research.md` Decision 4; same text → same vector
- [x] T014 [P] [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/InMemoryVectorStore.java` — `name()`=`memory`; L2-normalise on upsert/query; brute-force cosine top-K; `replaceAll` atomic replace per `research.md` Decision 5

### Tests

- [x] T015 [P] [US3] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/embedding/StubTextEmbedderTest.java` — determinism; dimension length; blank/empty behaviour documented
- [x] T016 [P] [US3] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/embedding/InMemoryVectorStoreTest.java` — search orders by cosine; `topK` cap; `replaceAll` clears prior set; `clear` empties (depends on T011–T012, T014)
- [x] T017 [US3] Optional test-only `FixtureEmbeddingTable` (same package or test sources) implementing `TextEmbedder` with injectable phrase→vector map for AC-11 — may land in US9 if deferred; stub must remain usable without it for US8

**Checkpoint**: Offline embed + search path unit-tested without ONNX/network.

---

## Phase 6: User Story 4 — Entry Chunker (Priority: P4)

**Goal**: Split corpus entries into embeddable chunks with title/tags prefix and ADR-0003 body omission.

**Independent Test**: `./gradlew :infrastructure:test --tests "*EntryChunker*"` passes.

### Implementation

- [x] T018 [US4] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/EntryChunker.java` — paragraph then hard-wrap at `chunkSizeChars` / `chunkOverlapChars`; prefix `Title:` / `Tags:`; size-limited → title/tags only; empty → single prefix chunk per `research.md` Decision 6 (depends on T006)

### Tests

- [x] T019 [US4] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/embedding/EntryChunkerTest.java` — multi-chunk long body; overlap; oversize omits body; short entry single chunk (depends on T018)
- [x] T020 [P] [US4] Add long-entry fixture if needed under `infrastructure/src/test/resources/fixture-corpus/` for AC-22 (or reuse existing long note); document `sourceId` in test

**Checkpoint**: Chunking policy testable independent of vector search.

---

## Phase 7: User Story 5 — Registries and Index Cache (Priority: P5)

**Goal**: Resolve embedder/store by config id (fail-fast); rebuild index on fingerprint, TTL, or embedder identity change.

**Independent Test**: `./gradlew :infrastructure:test --tests "*TextEmbedderRegistry*" --tests "*VectorStoreRegistry*" --tests "*EmbeddingIndexCache*"` passes.

### Implementation

- [x] T021 [P] [US5] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/TextEmbedderRegistry.java` — index by `name()`; duplicate → startup failure; unknown id → failure listing registered names (mirror `RetrievalStrategyRegistry`)
- [x] T022 [P] [US5] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/VectorStoreRegistry.java` — same fail-fast pattern for stores
- [x] T023 [US5] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/EmbeddingIndexCache.java` — rebuild when `CorpusFingerprint` changes **or** TTL expires **or** embedder identity (id + model + dimensions) changes; chunk → embed → `VectorStore.replaceAll`; propagate `KnowledgeCorpusException` (depends on T014, T018, T006; reuse `CorpusFingerprint` from 005)

### Tests

- [x] T024 [P] [US5] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/embedding/TextEmbedderRegistryTest.java` — unknown id lists registered names; duplicate registration fails (depends on T021)
- [x] T025 [P] [US5] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/embedding/VectorStoreRegistryTest.java` — same for stores (depends on T022)
- [x] T026 [US5] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/embedding/EmbeddingIndexCacheTest.java` — fingerprint / TTL / embedder-identity rebuild; stable corpus reuses cache (depends on T023)

**Checkpoint**: Cache + adapter registries testable without full strategy.

---

## Phase 8: User Story 6 — Embedding Retrieval Strategy (Priority: P6)

**Goal**: `EmbeddingRetrievalStrategy` implements `RetrievalStrategy`; `name()` returns `"embedding"`.

**Independent Test**: `./gradlew :infrastructure:test --tests "*EmbeddingRetrievalStrategyTest"` passes with mocked cache/corpus.

### Implementation

- [x] T027 [US6] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/EmbeddingRetrievalStrategy.java` — blank query → empty list; ensure index; embed query; search `topK`; type/zone filters; optional `minScore`; best-score `sourceId` dedupe; sort desc; limit `maxResults`; load `Evidence` via `KnowledgeCorpus.loadBySourceId` (depends on T023, T009–T010)

### Tests

- [x] T028 [US6] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/embedding/EmbeddingRetrievalStrategyTest.java` — JUnit 5; Mockito corpus/cache; type filter; size-limited title match vs body-only exclusion; multi-chunk → one evidence (AC-22 unit); dedupe; maxResults (depends on T027)

**Checkpoint**: Embedding algorithm unit-tested in isolation (AC-15: strategy depends on SPIs only).

---

## Phase 9: User Story 7 — Spring Registration (Priority: P7)

**Goal**: Register `embedding` strategy + stub/memory (and later local/openai) beans; registry accepts `active-strategy=embedding`.

**Independent Test**: `./gradlew :infrastructure:test --tests "*RetrievalStrategyRegistry*" --tests "*TextEmbedderRegistry*" --tests "*VectorStoreRegistry*"` includes embedding registration and unknown-adapter fail-fast (AC-4, AC-16).

### Implementation

- [x] T029 [US7] Update `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/RetrievalConfiguration.java` — beans for `StubTextEmbedder`, `InMemoryVectorStore`, registries, `EmbeddingIndexCache`, `EmbeddingRetrievalStrategy`; select active embedder/store from `EmbeddingProperties`; fail fast on unknown ids when strategy/`embedding` wiring validates (depends on T027, T021–T023)
- [x] T030 [US7] Wire production defaults — when `active-strategy=embedding` and embedder=`local`, ensure `LocalTextEmbedder` bean path is registered once US10 lands; until then tests force `embedder=stub` so default CI does not require ONNX

### Tests

- [x] T031 [US7] Extend `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/RetrievalStrategyRegistryTest.java` — `embedding` among registered names; `getActive()` resolves `embedding` when configured; unknown store/embedder fails with listed ids (AC-4, AC-16)

**Checkpoint**: AC-4 / AC-16 satisfied at registry level with stub+memory.

---

## Phase 10: User Story 8 — Integration Tests (Priority: P8)

**Goal**: End-to-end embedding retrieval against fixture corpus with `stub` + `memory`; performance smoke AC-14.

**Independent Test**: `./gradlew :infrastructure:test --tests "*EmbeddingRetrievalIntegrationTest*"` passes.

### Implementation

- [x] T032 [US8] Extend `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/support/RetrievalTestConfiguration.java` — support `@TestPropertySource` / properties for `active-strategy=embedding`, `embedding.embedder=stub`, `embedding.store=memory` without breaking lexical/BM25 integration tests

### Tests

- [x] T033 [US8] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/embedding/EmbeddingRetrievalIntegrationTest.java` — `@SpringBootTest(classes = RetrievalTestConfiguration.class)`; retrieveContext match + provenance (AC-5); findDecisions/findConcepts/findPeople type filters (AC-6–AC-8); dedupe (AC-9); maxResults (AC-10); oversize title match and body exclusion (AC-12–AC-13); multi-chunk → one `Evidence` (AC-22); `@Timeout(5)` on index+query (AC-14); fixture corpus path (depends on T032, T029)

**Checkpoint**: Embedding retrieval contract green on fixture corpus except AC-11.

---

## Phase 11: User Story 9 — Ranking Differentiation (Priority: P9)

**Goal**: Prove embedding ordering differs from lexical and/or BM25 for controlled paraphrase query (AC-11).

**Independent Test**: `./gradlew :infrastructure:test --tests "*EmbeddingRankingDifferentiationTest*"` passes.

### Fixture

- [x] T034 [P] [US9] Create `infrastructure/src/test/resources/fixture-corpus/wiki/concepts/ranking-embedding-semantic-target.md` — stable `sourceId` `concept:ranking-embedding-semantic-target` per `contracts/fixture-embedding-ranking-scenario.json`
- [x] T035 [P] [US9] Create `infrastructure/src/test/resources/fixture-corpus/wiki/concepts/ranking-embedding-term-bait.md` — stable `sourceId` `concept:ranking-embedding-term-bait`; term-collision bait for lexical/BM25

### Tests

- [x] T036 [US9] Ensure stub / `FixtureEmbeddingTable` maps query + semantic-target chunks so embedding top hit is semantic-target (depends on T013/T017; craft vectors per JSON `stubVectors` notes)
- [x] T037 [US9] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/embedding/EmbeddingRankingDifferentiationTest.java` — load `specs/006-embedding-retrieval/contracts/fixture-embedding-ranking-scenario.json`; same `Query` with `embedding`+`stub` vs `lexical` and/or `bm25`; assert top `sourceId` differs (AC-11); tune fixtures/vectors until green (depends on T034–T036, T033)

**Checkpoint**: AC-11 satisfied.

---

## Phase 12: User Story 10 — Local and OpenAI-Compatible Embedders (Priority: P10)

**Goal**: Ship production embedder adapters behind SPI; CI continues to use `stub`.

**Independent Test**: Unit tests for adapters (mocked HTTP / mocked Transformers model where possible); no CI requirement to download ONNX.

### Implementation

- [x] T038 [P] [US10] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/LocalTextEmbedder.java` — `name()`=`local`; wrap Spring AI `TransformersEmbeddingModel` (MiniLM, 384-d) using `EmbeddingProperties.local` resources/cache dir per `research.md` Decision 2
- [x] T039 [P] [US10] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/embedding/OpenAiCompatibleTextEmbedder.java` — `name()`=`openai-compatible`; OpenAI embeddings HTTP shape via RestClient or Spring AI OpenAI client + custom base URL; never log API key (`research.md` Decision 3)
- [x] T040 [US10] Register `local` and `openai-compatible` beans in `RetrievalConfiguration.java`; production default embedder id remains `local` when `active-strategy=embedding`; tests keep forcing `stub` (depends on T038–T039, T029)

### Tests

- [x] T041 [P] [US10] Add focused unit coverage — mock/stub HTTP for `OpenAiCompatibleTextEmbedder` request shape; `LocalTextEmbedder` construction validated against properties (optional `@EnabledIf` / no network); do not gate `./gradlew build` on ONNX download

**Checkpoint**: Spec adapters present; CI path still offline.

---

## Phase 13: Polish & Cross-Cutting Concerns

**Purpose**: ADR, Compose template, operator docs, layer isolation, full regression, GitHub issues.

- [x] T042 [P] Create `docs/adr/ADR-0005-pluggable-embedding-retrieval.md` — private SPIs, first adapters (`local`, `openai-compatible`, `memory`, `stub`), chunking + `sourceId` dedupe, why not domain ports / Spring AI VectorStore as strategy surface (AC-20)
- [x] T043 [P] Create `deploy/docker-compose.embedding.example.yml` — runnable/example OpenAI-compatible embeddings service; **commented** pgvector and chromadb placeholders labelled future/non-shipping for 006 (AC-17, AC-18)
- [x] T044 [P] Update root `README.md` — Phase 2 roadmap note for embedding strategy + pointers to ADR-0005, `specs/006-embedding-retrieval/quickstart.md`, Compose template (AC-19)
- [x] T045 [P] Refresh operator docs — ensure `specs/006-embedding-retrieval/quickstart.md` and `specs/006-embedding-retrieval/contracts/README.md` state defaults (`embedding` → `local` + `memory`), chunking/dedupe, Compose scope vs what ships (AC-19)
- [x] T046 [P] Verify transport isolation — `./gradlew :transport:test`; confirm no `io.archivist.infrastructure` imports under `transport/src/main/java` (AC-2, AC-21)
- [x] T047 [P] Confirm `domain` / `application` / MCP tool surface unchanged — no new `port.in` methods; `ArchivistMcpTools` still injects `port.in` only (AC-21)
- [x] T048 Run lexical regression — `./gradlew :infrastructure:test --tests "*LexicalRetrievalIntegrationTest*"` with default `active-strategy=lexical` (AC-3)
- [x] T049 Run full build — `./gradlew build` from repo root (AC-1); include embedding tests under stub config
- [x] T050 Execute all checks in `specs/006-embedding-retrieval/quickstart.md` in order
- [x] T051 Update `specs/006-embedding-retrieval/spec.md` **Issue** field with epic number after `/speckit-taskstoissues`
- [x] T052 Run `/speckit-taskstoissues` — create epic + phase/story sub-issues; write `specs/006-embedding-retrieval/github-issues.md` with PR **Issues resolved** block

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — blocks all user stories
- **Phase 3 (US1)**: Depends on Phase 2
- **Phase 4 (US2)**: Depends on Phase 2 — can parallel with US1
- **Phase 5 (US3)**: Depends on US2 (SPI types)
- **Phase 6 (US4)**: Depends on US1 (chunk properties)
- **Phase 7 (US5)**: Depends on US3 + US4 (+ `CorpusFingerprint` from 005)
- **Phase 8 (US6)**: Depends on US5
- **Phase 9 (US7)**: Depends on US6
- **Phase 10 (US8)**: Depends on US7
- **Phase 11 (US9)**: Depends on US8 (integration context); fixtures can be drafted earlier
- **Phase 12 (US10)**: Depends on US7 wiring; can start after US3 SPI exists; should not block US8/US9 CI path
- **Phase 13 (Polish)**: Depends on Phases 1–12 (ADR/Compose can draft earlier but close after feature green)

### User Story Dependencies

- **US1**: Independent after Phase 2
- **US2**: Independent after Phase 2; parallel with US1
- **US3**: After US2
- **US4**: After US1; parallel with US3 once US1 done
- **US5**: After US3 + US4
- **US6**: After US5
- **US7**: After US6
- **US8**: After US7
- **US9**: After US8
- **US10**: After US2; ideally after US7 bean wiring; parallel with US8/US9 if staffed (keep tests on stub)
- **US11 (Polish)**: After all stories

### Within Each Phase

- US2: T009–T012 all parallel
- US3: T013–T014 parallel → T015–T016 parallel
- US5: T021–T022 parallel → T023 → T024–T026
- US9: T034–T035 parallel → T036 → T037
- US10: T038–T039 parallel → T040 → T041
- Polish: T042–T047 parallel → T048–T050 sequential → T051–T052

---

## Parallel Opportunities

### Phase 1

```text
T001 libs.versions.toml → T002 infrastructure/build.gradle.kts → T003 compile check
```

### Phase 4 — SPI files in parallel

```text
T009 TextEmbedder.java [P]
T010 VectorStore.java [P]
T011 EmbeddedChunk.java [P]
T012 ScoredChunk.java [P]
```

### Phase 5 — stub vs memory in parallel

```text
T013 StubTextEmbedder.java [P]
T014 InMemoryVectorStore.java [P]
```

### Phase 7 — registries in parallel

```text
T021 TextEmbedderRegistry.java [P]
T022 VectorStoreRegistry.java [P]
# then T023 EmbeddingIndexCache.java
```

### Phase 11 — ranking fixtures in parallel

```text
T034 ranking-embedding-semantic-target.md [P]
T035 ranking-embedding-term-bait.md [P]
```

### Phase 12 — production embedders in parallel

```text
T038 LocalTextEmbedder.java [P]
T039 OpenAiCompatibleTextEmbedder.java [P]
```

### Phase 13 — docs/isolation in parallel

```text
T042 ADR-0005 [P]
T043 docker-compose template [P]
T044 README [P]
T045 quickstart/contracts docs [P]
T046 transport isolation [P]
T047 MCP/port.in unchanged [P]
# Then T048–T050 sequential verification
```

---

## Implementation Strategy

### MVP (US1 — configuration only)

1. Phase 1 → Phase 2 → Phase 3 (US1)
2. **STOP and VALIDATE**: `EmbeddingPropertiesTest` green; new Spring AI embedding deps not on domain classpath

### CI-first vertical slice (recommended)

1. Phases 1–2 → deps + gates  
2. US1–US3 → config + SPIs + stub/memory  
3. US4–US7 → chunker, cache, strategy, Spring beans  
4. US8 → fixture integration (AC-5–14, AC-22)  
5. US9 → AC-11 ranking proof  
6. US10 → local + openai-compatible (operator path)  
7. US11 → ADR, Compose, docs, `./gradlew build`, `/speckit-taskstoissues`

### Incremental Delivery

| Increment | Delivers |
| --------- | -------- |
| US1 | Embedding configuration binding |
| US2 | Private SPIs + chunk records |
| US3 | Offline stub + memory store |
| US4 | Chunking policy |
| US5 | Registries + index cache |
| US6 | Search strategy unit-tested |
| US7 | Opt-in `active-strategy=embedding` |
| US8 | Fixture integration AC-5–14, AC-22 |
| US9 | AC-11 ranking proof |
| US10 | Production embedder adapters |
| US11 | Docs/ADR/Compose + full build + issues |

### Full Feature Path

1. Phases 1–2 → dependencies and gates  
2. Phases 3–5 → config, SPIs, stub/memory  
3. Phases 6–9 → chunker, cache, strategy, Spring  
4. Phase 10 → integration tests  
5. Phase 11 → AC-11 fixtures  
6. Phase 12 → local + openai-compatible  
7. Phase 13 → ADR/Compose/docs + `./gradlew build` + `/speckit-taskstoissues`

---

## Notes

- `[P]` = different files, no intra-phase blocking dependencies
- `[USn]` maps tasks to user stories and spec AC groups
- Embedding / vector / Spring AI model types MUST NOT appear in `domain`, `application`, or `transport` main sources
- Default `archivist.retrieval.active-strategy` remains `lexical` until operator sets `embedding`
- CI embedding tests MUST set `embedder=stub` — never require ONNX download or network
- `EmbeddingRetrievalStrategy` depends only on `TextEmbedder` + `VectorStore` SPIs (AC-15)
- Tune AC-11 fixture markdown / stub vectors in US9 until `EmbeddingRankingDifferentiationTest` passes — expected iteration
- Compose vector DB services are placeholders only — no pgvector/Chroma adapters in this feature
- Confirm `git branch --show-current` is `006-embedding-retrieval` before every commit
- Commit after each phase checkpoint (user-requested commits only)
