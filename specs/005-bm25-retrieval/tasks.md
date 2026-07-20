# Tasks: BM25 Retrieval Strategy

**Input**: Design documents from `specs/005-bm25-retrieval/`

**Prerequisites**: plan.md ✅ | spec.md ✅ (approved 2026-07-20) | research.md ✅ | data-model.md ✅ | contracts/ ✅ | quickstart.md ✅

**Stack**: Java 21 · Spring Boot 4.1.0 · Apache Lucene 9.12.1 · Gradle (Kotlin DSL) · spec 003 `KnowledgeCorpus` · spec 004 retrieval SPI

**Tests**: Required by spec (AC-1–AC-15). Domain/application unchanged — no new tests expected there. Infrastructure: plain JUnit unit tests + `@SpringBootTest(classes = RetrievalTestConfiguration.class)` integration tests per `quickstart.md`. Transport MCP contract tests remain mocked `port.in` (AC-1, AC-15).

**User story mapping** (spec scenarios → deliverable increments):

| Story | Deliverable | Acceptance Criteria |
| ----- | ----------- | ------------------- |
| US1 | Lucene on classpath + BM25 `@ConfigurationProperties` | Config contract; AC-4 prep |
| US2 | Shared tokenisation + Lucene analyzer | Token parity with `LexicalScorer` |
| US3 | Corpus fingerprint + unit tests | ADR-0004 invalidation signal |
| US4 | Index builder + in-memory cache | Index schema per data-model |
| US5 | `Bm25RetrievalStrategy` + unit tests | Filters, dedupe, availability |
| US6 | Spring `bm25` bean + registry | AC-4 startup with both strategies |
| US7 | BM25 integration tests on fixture corpus | AC-5–AC-10, AC-12–AC-13, AC-14 |
| US8 | AC-11 ranking fixtures + lexical vs BM25 test | AC-11 |
| US9 | Polish, regression, quickstart | AC-1–AC-3, AC-15 |

---

## Phase 1: Setup (Lucene Dependency)

**Purpose**: Add Lucene to the version catalogue and infrastructure module only.

- [ ] T001 Add Lucene version and library aliases to `gradle/libs.versions.toml` — `lucene = "9.12.1"`; `lucene-core`, `lucene-analysis-common`, `lucene-queryparser` (coordinates per `research.md` Decision 1)
- [ ] T002 Update `infrastructure/build.gradle.kts` — `implementation` dependencies on Lucene libraries; verify `:domain:dependencies` and `:application:dependencies` compile classpaths contain no Lucene artifacts
- [ ] T003 Run `./gradlew :infrastructure:compileJava` — succeeds with Lucene on infrastructure classpath only (depends on T001–T002)

**Checkpoint**: Lucene scoped to infrastructure. No production BM25 code yet.

---

## Phase 2: Foundational (Contract & Prerequisite Gate)

**Purpose**: Confirm 005 contract fixtures and spec 004 retrieval baseline before BM25 implementation.

⚠️ **CRITICAL**: Do not change lexical expected outcomes without spec workflow. AC-3 requires default `lexical` behaviour unchanged.

- [ ] T004 Verify contract fixtures in `specs/005-bm25-retrieval/contracts/` — `knowledge-gateway-bm25-semantics.md`, `bm25-configuration.md`, `corpus-fingerprint-contract.md`, `retrieval-strategy-spi.md`, and `fixture-bm25-ranking-scenario.json` align with approved `spec.md` §3–5; fix doc drift only (no implementation)
- [ ] T005 Verify spec 004 prerequisite — `./gradlew :infrastructure:test --tests "io.archivist.infrastructure.retrieval.LexicalRetrievalIntegrationTest"` and `RetrievalStrategyRegistryTest` pass; `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/support/RetrievalTestConfiguration.java` exists

**Checkpoint**: Lexical integration green. 005 contracts ready for test assertions.

---

## Phase 3: User Story 1 — BM25 Configuration (Priority: P1) 🎯 MVP

**Goal**: Bind `archivist.retrieval.bm25.*` properties with documented defaults without activating BM25 yet.

**Independent Test**: Unit test passes for default values; `./gradlew :infrastructure:compileJava` with extended properties class.

### Implementation

- [ ] T006 [US1] Extend `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/RetrievalProperties.java` — nested `Bm25Properties` (or `@ConfigurationProperties` prefix `archivist.retrieval.bm25`) with field boosts 3/2/1, `indexTtl` default `Duration.ofMinutes(15)`, `k1` 1.2f, `b` 0.75f; validation per `contracts/bm25-configuration.md`
- [ ] T007 [US1] Ensure `ArchivistRetrievalAutoConfiguration` enables nested BM25 properties binding — update `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/ArchivistRetrievalAutoConfiguration.java` if `@EnableConfigurationProperties` must list nested type

### Tests

- [ ] T008 [US1] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/Bm25PropertiesTest.java` — JUnit 5; assert defaults match `specs/005-bm25-retrieval/contracts/bm25-configuration.md`; rejects invalid boost/TTL where validated (depends on T006)

**Checkpoint**: Configuration contract implementable. MVP for `/speckit-implement` can stop here only for property wiring review — not feature-complete.

---

## Phase 4: User Story 2 — Shared Tokenisation (Priority: P2)

**Goal**: One tokenisation rule for lexical and BM25 query/index paths.

**Independent Test**: `./gradlew :infrastructure:test --tests "*LexicalScorerTest" --tests "*RetrievalTokenizationTest"` passes.

### Implementation

- [ ] T009 [P] [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/RetrievalTokenization.java` — package-private; `tokenize(String)` matching `LexicalScorer` regex and lowercase ROOT rules per `research.md` Decision 5
- [ ] T010 [US2] Refactor `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/LexicalScorer.java` — delegate `tokenize` to `RetrievalTokenization` (behaviour unchanged)
- [ ] T011 [P] [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/ArchivistCorpusAnalyzer.java` — Lucene `Analyzer` using same split/normalisation as `RetrievalTokenization` for index-time tokenisation

### Tests

- [ ] T012 [P] [US2] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/RetrievalTokenizationTest.java` — parity cases shared with lexical expectations (punctuation split, empty drops)
- [ ] T013 [US2] Re-run `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/LexicalScorerTest.java` — all pass after refactor (AC-3 regression guard; depends on T010)

**Checkpoint**: Tokenisation shared. Lexical tests still green.

---

## Phase 5: User Story 3 — Corpus Fingerprint (Priority: P3)

**Goal**: Stable fingerprint from `KnowledgeCorpus.catalog()` for cache invalidation.

**Independent Test**: `./gradlew :infrastructure:test --tests "*CorpusFingerprintTest"` passes.

### Implementation

- [ ] T014 [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/CorpusFingerprint.java` — implement algorithm in `specs/005-bm25-retrieval/contracts/corpus-fingerprint-contract.md`

### Tests

- [ ] T015 [US3] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/CorpusFingerprintTest.java` — JUnit 5; same catalog → same fingerprint; changed `updated` or entry count → different fingerprint; mock or minimal `KnowledgeCorpus` (depends on T014)

**Checkpoint**: Fingerprint contract testable independent of Lucene index.

---

## Phase 6: User Story 4 — Index Builder and Cache (Priority: P4)

**Goal**: Build in-memory Lucene index from `loadAll()`; cache with fingerprint + TTL invalidation.

**Independent Test**: Unit tests with mocked corpus or small in-memory fixture list; index builds without throwing.

### Implementation

- [ ] T016 [P] [US4] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/Bm25IndexBuilder.java` — `ByteBuffersDirectory`; document fields `sourceId`, `title`, `tags`, `body`, `knowledgeType`, `knowledgeZone` per `data-model.md`; field boosts from `Bm25Properties`; `BM25Similarity(k1,b)`; omit body for `UNAVAILABLE_ENTRY_TOO_LARGE`
- [ ] T017 [US4] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/Bm25IndexCache.java` — acquire/rebuild on fingerprint mismatch or TTL expiry; `synchronized` rebuild per `research.md` Decision 8; propagate `KnowledgeCorpusException` (depends on T014, T016)

### Tests

- [ ] T018 [US4] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/Bm25IndexBuilderTest.java` — JUnit 5; mocked corpus entries; assert oversize entry has empty body field in index; title/tags indexed
- [ ] T019 [US4] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/Bm25IndexCacheTest.java` — fingerprint change triggers rebuild; TTL expiry triggers rebuild; stable corpus reuses cache (test hook or spy; depends on T017)

**Checkpoint**: Index lifecycle testable without full gateway.

---

## Phase 7: User Story 5 — BM25 Retrieval Strategy (Priority: P5)

**Goal**: `Bm25RetrievalStrategy` implements `RetrievalStrategy`; `name()` returns `"bm25"`.

**Independent Test**: `./gradlew :infrastructure:test --tests "*Bm25RetrievalStrategyTest"` passes with mocked cache or corpus.

### Implementation

- [ ] T020 [US5] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/Bm25RetrievalStrategy.java` — blank query → empty list; boolean query all tokens MUST match; type/zone filters; map hits to `Evidence`; dedupe by `sourceId`; limit `maxResults`; same filter semantics as `LexicalRetrievalStrategy` (depends on T017, T009)

### Tests

- [ ] T021 [US5] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/Bm25RetrievalStrategyTest.java` — JUnit 5; Mockito corpus/cache; type filter; size-limited title match vs body-only exclusion; dedupe; maxResults (depends on T020)

**Checkpoint**: BM25 algorithm unit-tested in isolation.

---

## Phase 8: User Story 6 — Spring Registration (Priority: P6)

**Goal**: Register `bm25` strategy bean alongside `lexical`; registry accepts `active-strategy=bm25`.

**Independent Test**: `./gradlew :infrastructure:test --tests "*RetrievalStrategyRegistry*"` includes bm25 registration case.

### Implementation

- [ ] T022 [US6] Update `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/RetrievalConfiguration.java` — add `@Bean RetrievalStrategy bm25RetrievalStrategy(KnowledgeCorpus, Bm25Properties, …)` wiring `Bm25RetrievalStrategy` (depends on T020)

### Tests

- [ ] T023 [US6] Extend `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/RetrievalStrategyRegistryTest.java` — both `lexical` and `bm25` registered; `getActive()` resolves `bm25` when configured; unknown name still fails (AC-4)

**Checkpoint**: AC-4 satisfied at registry level.

---

## Phase 9: User Story 7 — Integration Tests (Priority: P7)

**Goal**: End-to-end BM25 against fixture corpus; performance smoke AC-14.

**Independent Test**: `./gradlew :infrastructure:test --tests "*Bm25RetrievalIntegrationTest*"` passes.

### Implementation

- [ ] T024 [US7] Extend `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/support/RetrievalTestConfiguration.java` — support `@TestPropertySource` or properties for `archivist.retrieval.active-strategy=bm25` without breaking lexical integration tests

### Tests

- [ ] T025 [US7] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/Bm25RetrievalIntegrationTest.java` — `@SpringBootTest(classes = RetrievalTestConfiguration.class)` with `active-strategy=bm25`; assert retrieveContext match + provenance (AC-5); findDecisions/findConcepts/findPeople type filters (AC-6–AC-8); dedupe (AC-9); maxResults (AC-10); oversize title match and body exclusion (AC-12–AC-13); `@Timeout(5)` on index+query method (AC-14); uses fixture corpus path (depends on T024, T022)

**Checkpoint**: BM25 retrieval contract green on fixture corpus except AC-11.

---

## Phase 10: User Story 8 — Ranking Differentiation (Priority: P8)

**Goal**: Prove BM25 ordering differs from lexical for controlled query (AC-11).

**Independent Test**: `./gradlew :infrastructure:test --tests "*Bm25RankingDifferentiationTest*"` passes.

### Fixture

- [ ] T026 [P] [US8] Create `infrastructure/src/test/resources/fixture-corpus/wiki/concepts/ranking-bm25-sparse.md` — stable `sourceId` `ranking-bm25-sparse`; frontmatter/type CONCEPT; title/tags strong for query `retrieval ranking probe`; body minimal term mentions per `contracts/fixture-bm25-ranking-scenario.json`
- [ ] T027 [P] [US8] Create `infrastructure/src/test/resources/fixture-corpus/wiki/concepts/ranking-bm25-dense.md` — stable `sourceId` `ranking-bm25-dense`; weaker title; body repeats probe tokens for BM25 TF delta

### Tests

- [ ] T028 [US8] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/Bm25RankingDifferentiationTest.java` — load JSON from `specs/005-bm25-retrieval/contracts/fixture-bm25-ranking-scenario.json`; same `Query` with `lexical` vs `bm25` active; assert top `sourceId` differs and full order differs (AC-11); tune fixture bodies in T026–T027 if test fails (depends on T026–T027, T025)

**Checkpoint**: AC-11 satisfied.

---

## Phase 11: Polish & Verification

**Purpose**: Full build, lexical regression, layer boundaries, quickstart, GitHub issues.

- [ ] T029 [P] Verify domain/application compile classpaths — `./gradlew :domain:dependencies --configuration compileClasspath` and `:application:dependencies` show no Lucene or new infrastructure leakage (AC-15)
- [ ] T030 [P] Verify transport isolation — `./gradlew :transport:test --tests "*TransportLayerIsolationTest*"` (AC-2)
- [ ] T031 [P] Confirm `transport/src/main/java/io/archivist/transport/mcp/ArchivistMcpTools.java` unchanged in tool surface — no new tools; still injects `port.in` only (AC-15)
- [ ] T032 Run lexical regression — `./gradlew :infrastructure:test --tests "*LexicalRetrievalIntegrationTest*"` with default `active-strategy=lexical` (AC-3)
- [ ] T033 Run full infrastructure suite — `./gradlew :infrastructure:test`
- [ ] T034 Run full build — `./gradlew build` from repo root (AC-1)
- [ ] T035 Execute all checks in `specs/005-bm25-retrieval/quickstart.md` in order
- [x] T036 Update `specs/005-bm25-retrieval/spec.md` **Issue** field with epic number after `/speckit-taskstoissues`
- [x] T037 Run `/speckit-taskstoissues` — create epic + phase sub-issues; write `specs/005-bm25-retrieval/github-issues.md` with PR **Issues resolved** block

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — blocks all user stories
- **Phase 3 (US1)**: Depends on Phase 2
- **Phase 4 (US2)**: Depends on Phase 1 (Lucene for analyzer in T011)
- **Phase 5 (US3)**: Depends on Phase 2 — can parallel with US2 after Phase 2
- **Phase 6 (US4)**: Depends on US2 (analyzer), US3 (fingerprint), US1 (properties)
- **Phase 7 (US5)**: Depends on US4
- **Phase 8 (US6)**: Depends on US5
- **Phase 9 (US7)**: Depends on US6
- **Phase 10 (US8)**: Depends on US7 (integration context); fixtures can be drafted earlier but test needs wired BM25
- **Phase 11 (Polish)**: Depends on Phases 1–10

### User Story Dependencies

- **US1**: Independent after Phase 2
- **US2**: After Phase 1; independent of US3
- **US3**: After Phase 2; independent of US2
- **US4**: After US1, US2, US3
- **US5**: After US4
- **US6**: After US5
- **US7**: After US6
- **US8**: After US7
- **US9 (Polish)**: After all stories

### Within Each Phase

- US4: T016 parallel with prep → T017 → T018, T019
- US8: T026, T027 parallel → T028 (iterate fixture copy until AC-11 green)

---

## Parallel Opportunities

### Phase 1

```text
T001 libs.versions.toml → T002 build.gradle.kts → T003 compile check
```

### Phase 4 — tokenisation vs analyzer file

```text
T009 RetrievalTokenization.java [P]
T011 ArchivistCorpusAnalyzer.java [P]  (after T009 for shared rules reference)
T010 LexicalScorer refactor (after T009)
```

### Phase 6 — builder vs tests sequencing

```text
T016 Bm25IndexBuilder.java [P]
T014 CorpusFingerprint already done in US3
T017 Bm25IndexCache.java (after T016)
```

### Phase 10 — fixture markdown in parallel

```text
T026 ranking-bm25-sparse.md [P]
T027 ranking-bm25-dense.md [P]
```

### Phase 11 — verification in parallel

```text
T029 classpath check [P]
T030 transport isolation [P]
T031 MCP tools review [P]
# Then T032–T035 sequential test runs
```

---

## Implementation Strategy

### MVP (US1 — configuration only)

1. Phase 1 → Phase 2 → Phase 3 (US1)
2. **STOP and VALIDATE**: `Bm25PropertiesTest` green; Lucene not on domain classpath

### Incremental Delivery

| Increment | Delivers |
| --------- | -------- |
| US1 | BM25 configuration binding |
| US2 | Tokenisation parity + lexical regression |
| US3 | Fingerprint for cache invalidation |
| US4 | Lucene index build + cache |
| US5 | Search strategy unit-tested |
| US6 | Opt-in `active-strategy=bm25` |
| US7 | Fixture integration AC-5–14 |
| US8 | AC-11 ranking proof |
| US9 | Full build + issues manifest |

### Full Feature Path

1. Phases 1–2 → dependencies and gates
2. Phases 3–5 → config, tokens, fingerprint
3. Phases 6–8 → index, strategy, Spring bean
4. Phase 9 → integration tests
5. Phase 10 → AC-11 fixtures
6. Phase 11 → `./gradlew build` + `/speckit-taskstoissues`

---

## Notes

- `[P]` = different files, no intra-phase blocking dependencies
- `[USn]` maps tasks to user stories and spec AC groups
- Lucene types MUST NOT appear in `domain`, `application`, or `transport` main sources
- Default `archivist.retrieval.active-strategy` remains `lexical` until operator sets `bm25`
- Tune AC-11 fixture markdown in US8 until `Bm25RankingDifferentiationTest` passes — expected iteration
- Confirm `git branch --show-current` is `005-bm25-retrieval` before every commit
- Commit after each phase checkpoint (user-requested commits only)
