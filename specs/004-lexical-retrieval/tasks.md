# Tasks: Lexical Retrieval Strategy and Retrieval Foundation

**Input**: Design documents from `specs/004-lexical-retrieval/`

**Prerequisites**: plan.md ✅ | spec.md ✅ (approved 2026-07-13) | research.md ✅ | data-model.md ✅ | contracts/ ✅ | quickstart.md ✅

**Stack**: Java 21 · Spring Boot 4.1.0 · Gradle (Kotlin DSL) · spec 003 `KnowledgeCorpus`

**Tests**: Explicitly required by spec (AC-1, AC-4–AC-16, AC-12 use case tests). Domain/application tests use plain JUnit 5 + Mockito (no Spring). Infrastructure unit tests use plain JUnit 5. Integration tests use `@SpringBootTest(classes = RetrievalTestConfiguration.class)` — minimal Spring context; assertions against `specs/004-lexical-retrieval/contracts/fixture-lexical-expected.json`. Transport MCP contract tests remain mocked `port.in` — no real retrieval in transport layer.

**User story mapping** (plan phases → deliverable increments):

| Story | Deliverable                                                       | Acceptance Criteria                 |
| ----- | ----------------------------------------------------------------- | ----------------------------------- |
| US1   | Enriched domain `Query` with factories                            | Gateway filter semantics            |
| US2   | `RetrievalStrategy` SPI + lexical scorer/strategy + unit tests    | AC-4–AC-11 (partial; unit-level)    |
| US3   | `KnowledgeGatewayImpl` + `RetrievalStrategyRegistry` + properties | AC-14, AC-15 (foundation)           |
| US4   | Real application use cases + delegation unit tests                | AC-12, capability → `Query` mapping |
| US5   | Spring wiring + Boot auto-configuration                           | AC-3, AC-14                         |
| US6   | Transport composition root + layer isolation                      | AC-2, AC-3                          |
| US7   | Fixture oversize entry + lexical integration tests                | AC-4–AC-11, AC-13, AC-16            |
| US8   | Polish & end-to-end verification                                  | AC-1, AC-15                         |

---

## Phase 1: Setup (Gradle Composition Root)

**Purpose**: Add infrastructure to transport classpath for Boot auto-configuration. No production Java changes beyond Gradle yet.

- [x] T001 Update `transport/build.gradle.kts` — add `implementation(project(":infrastructure"))` for composition-root classpath (transport main sources must still not import infrastructure types)
- [x] T002 Add retrieval property defaults to `transport/src/main/resources/application.properties` — `archivist.retrieval.active-strategy=lexical`, `archivist.retrieval.max-results=20`

**Checkpoint**: `./gradlew :transport:dependencies --configuration compileClasspath` lists `:infrastructure`; `:transport:compileJava` still succeeds (auto-config not yet consumed).

---

## Phase 2: Foundational (Contract Fixtures)

**Purpose**: Confirm machine-readable contract fixtures match approved spec before implementation.

⚠️ **CRITICAL**: Integration tests (US7) depend on these fixtures. Do not modify expected outcomes without updating `spec.md` through the specification workflow.

- [x] T003 Verify contract fixtures in `specs/004-lexical-retrieval/contracts/` — confirm `knowledge-gateway-port.md` matches spec §3 gateway semantics; `query-model.md` capability → factory table complete; `retrieval-strategy-spi.md` extension checklist present; `fixture-lexical-expected.json` scenarios cover AC-4–AC-11 and AC-13; fix any drift before proceeding
- [x] T004 Verify spec 003 prerequisite — `KnowledgeCorpus` interface and fixture corpus exist at `infrastructure/src/test/resources/fixture-corpus/` with `contracts/fixture-catalog-expected.json`; `./gradlew :infrastructure:test --tests "*SecondBrainKnowledgeCorpusIntegrationTest*"` passes on current branch

**Checkpoint**: All contract artifacts present. Spec 003 corpus integration green.

---

## Phase 3: User Story 1 — Domain Query Enrichment (Priority: P1) 🎯 MVP

**Goal**: Extend `Query` with type/zone filters, `maxResults`, and static factories per `contracts/query-model.md`. Domain remains Spring-free.

**Independent Test**: `./gradlew :domain:compileJava :domain:test` succeeds; `./gradlew :domain:dependencies --configuration compileClasspath` shows no `org.springframework` entries.

### Implementation

- [x] T005 [US1] Extend `domain/src/main/java/io/archivist/domain/model/Query.java` — record fields `text`, `Set<KnowledgeType> types`, `Set<KnowledgeZone> zones`, `int maxResults`; compact constructor validates non-null sets (`Set.copyOf`), `maxResults > 0`; factories `unrestricted`, `withType`, `withTypes` per `contracts/query-model.md`
- [x] T006 [US1] Update any existing `Query` construction sites in codebase (grep `new Query(` / `Query(`) to use new factories — likely none outside new code; ensure `:domain:compileJava` clean

### Tests

- [x] T007 [US1] Create `domain/src/test/java/io/archivist/domain/model/QueryTest.java` — JUnit 5; factory semantics (empty type/zone set = no filter); rejects non-positive `maxResults`; rejects null fields; zero Spring imports
- [x] T008 [US1] Verify domain module: `./gradlew :domain:compileJava :domain:test` — all tests pass; no Spring on compile classpath (depends on T005–T007)

**Checkpoint**: `Query` factories match `contracts/query-model.md`. Domain MVP ready for retrieval implementation.

---

## Phase 4: User Story 2 — Retrieval SPI and Lexical Strategy (Priority: P2)

**Goal**: Internal `RetrievalStrategy` SPI, `LexicalScorer`, and `LexicalRetrievalStrategy` using `KnowledgeCorpus`. Plain JUnit unit tests with mocked corpus.

**Independent Test**: `./gradlew :infrastructure:test --tests "io.archivist.infrastructure.retrieval.LexicalScorerTest" --tests "io.archivist.infrastructure.retrieval.LexicalRetrievalStrategyTest"` passes without full Boot context.

### Implementation

- [x] T009 [P] [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/RetrievalStrategy.java` — SPI: `String name()`, `List<Evidence> retrieve(Query query)`; Javadoc references `contracts/retrieval-strategy-spi.md`
- [x] T010 [P] [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/LexicalScorer.java` — package-private; tokenise query (`toLowerCase`, split `[\s\p{Punct}]+`); score title×3, tag×2, body×1 per matching term; tie-break `sourceId` ascending
- [x] T011 [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/LexicalRetrievalStrategy.java` — implements `RetrievalStrategy`; `name()` returns `"lexical"`; uses `KnowledgeCorpus.loadAll()`; applies type/zone filters; `UNAVAILABLE_ENTRY_TOO_LARGE` matches title/tags only; score, dedupe by `sourceId`, limit `maxResults` (depends on T009–T010)

### Tests

- [x] T012 [P] [US2] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/LexicalScorerTest.java` — JUnit 5; case insensitivity; title outranks body; multi-term scoring; zero Spring imports
- [x] T013 [US2] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/LexicalRetrievalStrategyTest.java` — JUnit 5; Mockito mock `KnowledgeCorpus`; type filter excludes wrong types; availability policy (title match includes oversize, body-only token excludes); deduplication; maxResults cap (depends on T011)

**Checkpoint**: Lexical strategy unit-tested in isolation. No Spring annotations on scorer/strategy classes.

---

## Phase 5: User Story 3 — Gateway and Strategy Registry (Priority: P3)

**Goal**: `KnowledgeGatewayImpl` delegates to active strategy via `RetrievalStrategyRegistry`. Startup validation for unknown/duplicate strategy names. `RetrievalProperties` for configuration.

**Independent Test**: `./gradlew :infrastructure:test --tests "io.archivist.infrastructure.retrieval.RetrievalStrategyRegistryTest"` passes; `KnowledgeGatewayImpl` unit-testable with mock registry.

### Implementation

- [x] T014 [P] [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/RetrievalProperties.java` — `@ConfigurationProperties("archivist.retrieval")` with `activeStrategy` (default `lexical`), `maxResults` (default `20`); validation annotations as needed
- [x] T015 [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/RetrievalStrategyRegistry.java` — index strategies by `name()`; reject duplicate names; `getActive()` resolves configured name; unknown name → `IllegalStateException` with registered names listed (depends on T009)
- [x] T016 [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/KnowledgeGatewayImpl.java` — implements `domain.port.out.KnowledgeGateway`; null query → NPE; blank text → empty list; else `registry.getActive().retrieve(query)` (depends on T015)

### Tests

- [x] T017 [US3] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/RetrievalStrategyRegistryTest.java` — JUnit 5; unknown active strategy fails construction; duplicate `name()` fails; resolves `lexical` when registered
- [x] T018 [US3] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/KnowledgeGatewayImplTest.java` — JUnit 5; mock registry/strategy; blank text returns empty; delegates to active strategy (depends on T016)

**Checkpoint**: Gateway + registry form the stable boundary between application and private strategies (AC-15 foundation).

---

## Phase 6: User Story 4 — Application Use Cases (Priority: P4)

**Goal**: Replace stub use case bodies with `KnowledgeGateway` delegation and capability-specific `Query` factories. Eight Mockito unit tests verify `Query` construction.

**Independent Test**: `./gradlew :application:test` — all eight `*UseCaseTest` classes pass; `:application:dependencies --configuration compileClasspath` = domain only.

### Implementation

- [x] T019 [P] [US4] Update `application/src/main/java/io/archivist/application/usecase/RetrieveContextUseCase.java` — constructor inject `KnowledgeGateway` + `int defaultMaxResults`; `retrieveContext` → `gateway.retrieve(Query.unrestricted(query, defaultMaxResults))`
- [x] T020 [P] [US4] Update `application/src/main/java/io/archivist/application/usecase/FindDecisionsUseCase.java` — `Query.withType(topic, KnowledgeType.DECISION, defaultMaxResults)`
- [x] T021 [P] [US4] Update `application/src/main/java/io/archivist/application/usecase/FindProjectsUseCase.java` — `Query.withType(criteria, KnowledgeType.PROJECT, defaultMaxResults)`
- [x] T022 [P] [US4] Update `application/src/main/java/io/archivist/application/usecase/FindPeopleUseCase.java` — `Query.withType(name, KnowledgeType.PERSON, defaultMaxResults)`
- [x] T023 [P] [US4] Update `application/src/main/java/io/archivist/application/usecase/FindConceptsUseCase.java` — `Query.withTypes(topic, Set.of(CONCEPT, SYNTHESIS), defaultMaxResults)`
- [x] T024 [P] [US4] Update `application/src/main/java/io/archivist/application/usecase/FindRelatedKnowledgeUseCase.java` — `Query.unrestricted(query, defaultMaxResults)` (lexical fallback)
- [x] T025 [P] [US4] Update `application/src/main/java/io/archivist/application/usecase/FindReadingsUseCase.java` — `Query.withType(topic, KnowledgeType.READING, defaultMaxResults)`
- [x] T026 [P] [US4] Update `application/src/main/java/io/archivist/application/usecase/FindDebriefsUseCase.java` — `Query.withType(topic, KnowledgeType.DEBRIEF, defaultMaxResults)`

### Tests

- [x] T027 [P] [US4] Create `application/src/test/java/io/archivist/application/usecase/RetrieveContextUseCaseTest.java` — Mockito mock `KnowledgeGateway`; verify `Query.unrestricted` with expected text and maxResults
- [x] T028 [P] [US4] Create `application/src/test/java/io/archivist/application/usecase/FindDecisionsUseCaseTest.java` — verify `Query.withType(..., DECISION, ...)`
- [x] T029 [P] [US4] Create `application/src/test/java/io/archivist/application/usecase/FindProjectsUseCaseTest.java` — verify `PROJECT` filter
- [x] T030 [P] [US4] Create `application/src/test/java/io/archivist/application/usecase/FindPeopleUseCaseTest.java` — verify `PERSON` filter
- [x] T031 [P] [US4] Create `application/src/test/java/io/archivist/application/usecase/FindConceptsUseCaseTest.java` — verify `CONCEPT` + `SYNTHESIS` filter
- [x] T032 [P] [US4] Create `application/src/test/java/io/archivist/application/usecase/FindRelatedKnowledgeUseCaseTest.java` — verify unrestricted `Query`
- [x] T033 [P] [US4] Create `application/src/test/java/io/archivist/application/usecase/FindReadingsUseCaseTest.java` — verify `READING` filter
- [x] T034 [P] [US4] Create `application/src/test/java/io/archivist/application/usecase/FindDebriefsUseCaseTest.java` — verify `DEBRIEF` filter
- [x] T035 [US4] Verify application module: `./gradlew :application:compileJava :application:test` — all tests pass; compile classpath has no `:infrastructure` (depends on T019–T034)

**Checkpoint**: AC-12 satisfied. Use cases are thin translators — no retrieval logic in application layer.

---

## Phase 7: User Story 5 — Spring Wiring and Auto-Configuration (Priority: P5)

**Goal**: Register retrieval beans and eight use case beans in infrastructure. Boot auto-configuration loads corpus + retrieval without transport importing infrastructure types.

**Independent Test**: Minimal `RetrievalTestConfiguration` (US7) can autowire `KnowledgeGateway` and all `port.in` beans.

### Implementation

- [x] T036 [US5] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/RetrievalConfiguration.java` — `@Configuration` registering: `LexicalRetrievalStrategy` bean, `RetrievalStrategyRegistry`, `KnowledgeGateway` → `KnowledgeGatewayImpl`, and eight use case beans wiring `defaultMaxResults` from `RetrievalProperties`; constructor injection only (depends on T011, T016, T019–T026)
- [x] T037 [US5] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/ArchivistRetrievalAutoConfiguration.java` — `@AutoConfiguration` + `@EnableConfigurationProperties(RetrievalProperties.class)` + `@Import({RetrievalConfiguration.class, SecondBrainConfiguration.class})`
- [x] T038 [US5] Create `infrastructure/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — register `io.archivist.infrastructure.retrieval.ArchivistRetrievalAutoConfiguration`
- [x] T039 [US5] Verify `./gradlew :infrastructure:compileJava` succeeds with Spring configuration (depends on T036–T038)

**Checkpoint**: Auto-configuration entry point registered. Beans creatable in test context (proven in US7).

---

## Phase 8: User Story 6 — Transport Composition Root (Priority: P6)

**Goal**: Remove stub use case configuration from transport. Enforce AC-2 — no infrastructure imports in transport main sources. MCP handlers unchanged.

**Independent Test**: `StubUseCaseConfiguration` deleted; `TransportLayerIsolationTest` passes; `McpAdapterTestConfiguration` unchanged and `./gradlew :transport:test` green.

### Implementation

- [x] T040 [US6] Update `transport/src/main/java/io/archivist/transport/ArchivistApplication.java` — remove `@Import(StubUseCaseConfiguration.class)`; keep `TransportJacksonConfiguration` only; rely on infrastructure auto-configuration for use case beans
- [x] T041 [US6] Delete `transport/src/main/java/io/archivist/transport/config/StubUseCaseConfiguration.java`
- [x] T042 [US6] Create `transport/src/test/java/io/archivist/transport/TransportLayerIsolationTest.java` — scan or grep `transport/src/main/java`; fail if any file contains `import io.archivist.infrastructure` (AC-2)
- [x] T043 [US6] Confirm `transport/src/test/java/io/archivist/transport/support/McpAdapterTestConfiguration.java` unchanged — still uses Mockito `port.in` mocks, not infrastructure retrieval beans
- [x] T044 [US6] Run `./gradlew :transport:test` — MCP contract regression tests pass unchanged (AC-1 partial; depends on T040–T043)

**Checkpoint**: AC-2, AC-3 satisfied. Transport oblivious to active retrieval strategy.

---

## Phase 9: User Story 7 — Fixture Corpus and Integration Tests (Priority: P7)

**Goal**: Oversize fixture entry for availability policy. End-to-end lexical retrieval against fixture corpus per `fixture-lexical-expected.json`.

**Independent Test**: `./gradlew :infrastructure:test --tests "io.archivist.infrastructure.retrieval.LexicalRetrievalIntegrationTest"` passes in under 2 seconds (AC-16).

### Fixture

- [x] T045 [US7] Add oversize entry for AC-10/AC-11 — create `infrastructure/src/test/resources/fixture-corpus/edge-cases/oversize-entry.md` with title containing `oversize-fixture-title` and body containing `unique-body-only-token-xyz` only (exceeds default `max-entry-bytes`), OR generate via `@TempDir` in integration test setup per plan Phase G

### Tests

- [x] T046 [US7] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/support/RetrievalTestConfiguration.java` — minimal `@SpringBootTest` config; `archivist.second-brain.path` → fixture corpus; loads retrieval + corpus beans only; must NOT load `ArchivistApplication` or transport MCP beans
- [x] T047 [US7] Create `infrastructure/src/test/java/io/archivist/infrastructure/retrieval/LexicalRetrievalIntegrationTest.java` — `@SpringBootTest(classes = RetrievalTestConfiguration.class)`; assert scenarios from `contracts/fixture-lexical-expected.json`: retrieveContext sample (AC-4), findDecisions fixture type filter (AC-5), findConcepts multi-type (AC-6), title ranking (AC-7), dedupe (AC-8), maxResults cap (AC-9), oversize title match (AC-10), oversize body exclusion (AC-11), findPeople (AC-13); timing guard < 2s (AC-16); uses `KnowledgeGateway` directly (depends on T046, T045)

**Checkpoint**: Lexical retrieval contract gate green. AC-4–AC-11, AC-13, AC-16 satisfied at infrastructure layer.

---

## Phase 10: Polish & Verification

**Purpose**: Architectural boundaries, full build, quickstart, GitHub issue manifest.

- [x] T048 [P] Verify domain and application have no infrastructure imports: `./gradlew :domain:dependencies --configuration compileClasspath` and `./gradlew :application:dependencies --configuration compileClasspath` — no `:infrastructure` entries
- [x] T049 [P] Verify transport main sources have no infrastructure imports: `./gradlew :transport:test --tests "*TransportLayerIsolationTest*"` (AC-2)
- [x] T050 [P] Verify `ArchivistMcpTools.java` unchanged in contract surface — no new imports from `infrastructure.*`; tool handlers inject `port.in` only (AC-15 review)
- [x] T051 [P] Verify no star imports in new retrieval sources: `grep -r "^import .*\*;" --include="*.java" infrastructure/src/main/java/io/archivist/infrastructure/retrieval` — must produce no output
- [x] T052 Run infrastructure tests: `./gradlew :infrastructure:test` — all unit and integration tests pass
- [x] T053 Run full build: `./gradlew build` from repo root — `BUILD SUCCESSFUL` required (AC-1)
- [x] T054 Run full quickstart verification: execute all checks in `specs/004-lexical-retrieval/quickstart.md` in order (AC-1–AC-16 traceability)
- [x] T055 Update `README.md` Phase 1 roadmap — mark "Lexical retrieval strategy" complete (checkbox `[x]`)
- [x] T056 Run `/speckit-taskstoissues` before opening the implementation PR — **done**: epic #74 + sub-issues #75–#84 in `specs/004-lexical-retrieval/github-issues.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — fixtures and spec 003 prerequisite verified
- **Phase 3 (US1 — Query)**: Depends on Phase 2 — enriched `Query` blocks retrieval
- **Phase 4 (US2 — Lexical)**: Depends on US1 — strategy consumes `Query`
- **Phase 5 (US3 — Gateway)**: Depends on US2 — registry collects `RetrievalStrategy` implementations
- **Phase 6 (US4 — Use cases)**: Depends on US1 — use cases build `Query`; can start after US1 if gateway mocked in tests; full wiring after US3/US5
- **Phase 7 (US5 — Spring)**: Depends on US2, US3, US4 — wires strategy, gateway, and use cases
- **Phase 8 (US6 — Transport)**: Depends on US5 — auto-config must exist before removing stubs
- **Phase 9 (US7 — Integration)**: Depends on US5 — test context needs retrieval beans
- **Phase 10 (Polish)**: Depends on Phases 1–9 complete

### User Story Dependencies

- **US1 (Query)**: Independent after Phase 2
- **US2 (Lexical)**: Depends on US1
- **US3 (Gateway)**: Depends on US2
- **US4 (Use cases)**: Depends on US1; tests can use mock gateway before US3
- **US5 (Spring)**: Depends on US2, US3, US4
- **US6 (Transport)**: Depends on US5
- **US7 (Integration)**: Depends on US5 (+ fixture from spec 003)
- **US8 (Polish)**: Depends on all stories

### Within Each Phase

- US1: T005 → T006 → T007 → T008
- US2: T009, T010 parallel → T011 → T012 parallel with T011 after T010 → T013
- US3: T014 parallel → T015 → T016 → T017, T018
- US4: T019–T026 parallel → T027–T034 parallel → T035
- US5: T036 → T037 → T038 → T039
- US6: T040 → T041 → T042, T043 parallel → T044
- US7: T045 → T046 → T047

---

## Parallel Opportunities

### Phase 4 — SPI and scorer in parallel

```text
Task: RetrievalStrategy.java    [T009]
Task: LexicalScorer.java        [T010]
# Then: LexicalRetrievalStrategy.java [T011]
```

### Phase 6 — use case updates in parallel

```text
Task: RetrieveContextUseCase.java       [T019]
Task: FindDecisionsUseCase.java         [T020]
Task: FindProjectsUseCase.java          [T021]
… (all eight use cases [T019–T026])
# Then: all *UseCaseTest.java [T027–T034] in parallel
```

### Phase 10 — verification checks in parallel

```text
Task: domain/application dependency check [T048]
Task: transport isolation test            [T049]
Task: MCP tools import review             [T050]
Task: star-import grep                    [T051]
# Then: test runs and quickstart [T052–T056]
```

---

## Implementation Strategy

### MVP (US1 — enriched Query)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: US1
4. **STOP and VALIDATE**: `./gradlew :domain:test` passes; factories match `contracts/query-model.md`

### Incremental Delivery

| Increment | Delivers                                                       |
| --------- | -------------------------------------------------------------- |
| US1       | Domain query model for capability-specific retrieval intent    |
| US2       | Testable lexical algorithm (mocked corpus)                     |
| US3       | Strategy registry + gateway — Phase 2 extension point          |
| US4       | Application layer wired to gateway contract                    |
| US5       | Runnable Spring assembly without transport stub                |
| US6       | Production Boot entry uses real retrieval; MCP still oblivious |
| US7       | Fixture contract gate + lexical AC sign-off                    |
| US8       | Full build + quickstart + GitHub issue manifest                |

### Full Feature Path

1. Phases 1–3 → Query MVP
2. Phases 4–5 → lexical + gateway (unit-tested)
3. Phase 6 → use cases + delegation tests
4. Phase 7 → Spring auto-config
5. Phase 8 → remove transport stubs
6. Phase 9 → integration tests green
7. Phase 10 → `./gradlew build` + quickstart (GitHub issues already filed — see `github-issues.md`)

---

## Notes

- `[P]` = different files, no intra-phase dependencies — safe to parallelize
- `[USn]` maps each task to its user story for traceability to spec acceptance criteria
- `RetrievalStrategy` is infrastructure-private — never import in `domain`, `application`, or `transport` main sources ([contracts/retrieval-strategy-spi.md](contracts/retrieval-strategy-spi.md))
- Transport MCP tests intentionally mock `port.in` — retrieval quality proven in `:infrastructure:test` only
- `McpAdapterTestConfiguration` must remain unchanged — AC-1 and AC-15 depend on it
- No persistent index in Phase 1 ([ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md))
- Lexical applies `ContentAvailability` policy at retrieval layer ([ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md))
- Commit after each phase checkpoint
- Confirm `git branch --show-current` is `004-lexical-retrieval` before every commit
