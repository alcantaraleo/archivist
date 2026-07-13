# Tasks: Second Brain Integration (Initial)

**Input**: Design documents from `specs/003-second-brain-integration/`

**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | data-model.md ✅ | contracts/ ✅ | quickstart.md ✅

**Stack**: Java 21 · Spring Boot 4.1.0 · SnakeYAML (BOM-managed) · Gradle (Kotlin DSL) · `libs.versions.toml`

**Tests**: Explicitly required by spec (AC-1, AC-4–AC-10, AC-14–AC-15). Infrastructure unit tests use plain JUnit 5 (no Spring). Integration tests use `@SpringBootTest(classes = SecondBrainCorpusTestConfiguration.class)` — minimal Spring context; fixture comparison via `specs/003-second-brain-integration/contracts/fixture-catalog-expected.json`.

**User story mapping** (plan phases → deliverable increments):

| Story | Deliverable                                                                   | Acceptance Criteria                          |
| ----- | ----------------------------------------------------------------------------- | -------------------------------------------- |
| US1   | Domain model and `KnowledgeCorpus` port (`ContentAvailability`, `Provenance`) | AC-2, AC-6, AC-15                            |
| US2   | Infrastructure parsing core (parser, resolver, mapper) + unit tests           | AC-11 (partial), AC-4–AC-6                   |
| US3   | Corpus walk, parallel load, size branching, `SecondBrainKnowledgeCorpus`      | AC-4–AC-6, AC-8, AC-10                       |
| US4   | Spring `@ConfigurationProperties` and bean wiring                             | AC-3                                         |
| US5   | Fixture corpus + integration tests                                            | AC-4–AC-10, AC-14 (incl. AC-7 sources chain) |
| US6   | Polish & end-to-end verification                                              | AC-1, AC-12, AC-13                           |

---

## Phase 1: Setup (Gradle Dependencies)

**Purpose**: Add infrastructure-only dependencies for YAML parsing, validation, and Spring tests. No production source changes yet.

- [x] T001 Add `snakeyaml` library alias to `gradle/libs.versions.toml` — coordinate `org.yaml:snakeyaml`, version via Spring Boot BOM (`4.1.0`)
- [x] T002 Update `infrastructure/build.gradle.kts` — add `implementation(libs.spring.boot.starter.validation)`, `implementation(libs.snakeyaml)`, `testImplementation(libs.spring.boot.starter.test)`

**Checkpoint**: `./gradlew :infrastructure:dependencies --configuration compileClasspath` lists `snakeyaml` and `spring-boot-starter-validation`; domain and application compile classpaths unchanged.

---

## Phase 2: Foundational (Contract Fixtures)

**Purpose**: Confirm machine-readable contract fixtures are in place before implementation. Fixtures were generated during `/speckit-plan`; this phase verifies they match the approved spec.

⚠️ **CRITICAL**: Integration tests (US5) depend on these fixtures. Do not modify expected outcomes without updating `spec.md` through the specification workflow.

- [x] T003 Verify contract fixtures in `specs/003-second-brain-integration/contracts/` — confirm `knowledge-corpus-port.md` matches spec §3 signatures; `mapping-defaults.md` zone-prefix table complete; `fixture-catalog-expected.json` has `catalogSize: 5`, five entries, `excludedSourceIds` includes `edge-cases/unknown-type`, and `loadBySourceIdCases` hit/miss ids; fix any drift before proceeding

**Checkpoint**: All contract artifacts present and consistent with approved spec §3 and plan Phase E.

---

## Phase 3: User Story 1 — Domain Model and KnowledgeCorpus Port (Priority: P1) 🎯 MVP

**Goal**: Introduce `ContentAvailability`, extend `Provenance`, and add `KnowledgeCorpus` / `KnowledgeCorpusException` in the domain layer. Domain remains Spring-free.

**Independent Test**: `./gradlew :domain:compileJava :domain:test` succeeds; `./gradlew :domain:dependencies --configuration compileClasspath` shows no `org.springframework` entries.

### Implementation

- [x] T004 [P] [US1] Create `domain/src/main/java/io/archivist/domain/model/ContentAvailability.java` — enum with `AVAILABLE` and `UNAVAILABLE_ENTRY_TOO_LARGE` only
- [x] T005 [US1] Extend `domain/src/main/java/io/archivist/domain/model/Provenance.java` — add non-null `ContentAvailability contentAvailability` as final record component (depends on T004)
- [x] T006 [US1] Preserve MCP contract unchanged (Option B per [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md)): add transport serialisation boundary in `transport/src/main/java/io/archivist/transport/mcp/` (e.g. `EvidenceResponse` / `ProvenanceResponse` records omitting `contentAvailability`); update `EvidenceJsonMapper.java` to map domain `Evidence` → response DTO before JSON; update `Provenance` construction sites in transport tests with `ContentAvailability.AVAILABLE`; verify `./gradlew :transport:test` still matches `specs/002-mcp-transport-adapter/contracts/evidence-response-schema-expected.json` unchanged (depends on T005)
- [x] T007 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/out/KnowledgeCorpus.java` — signatures and Javadoc exactly per spec §3 (`catalog`, `loadBySourceId`, `loadAll`; size-limited inclusion semantics documented)
- [x] T008 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/out/KnowledgeCorpusException.java` — unchecked exception for unrecoverable corpus access failures after startup; no Spring or IO type imports
- [x] T009 [US1] Verify domain module: `./gradlew :domain:compileJava :domain:test` — all tests pass; `:domain:dependencies --configuration compileClasspath` has zero Spring entries (depends on T004–T008)

**Checkpoint**: Domain compiles and tests pass. `KnowledgeCorpus` interface exists with approved signatures. Transport tests compile with updated `Provenance` constructors (still return `[]` via stubs).

---

## Phase 4: User Story 2 — Infrastructure Parsing Core (Priority: P2)

**Goal**: Private parsing and mapping components in `infrastructure.secondbrain` with plain JUnit unit tests. No Spring annotations in these classes.

**Independent Test**: `./gradlew :infrastructure:test --tests "io.archivist.infrastructure.secondbrain.MetadataEnvelopeParserTest" --tests "io.archivist.infrastructure.secondbrain.SourceIdNormalizerTest" --tests "io.archivist.infrastructure.secondbrain.ZoneTypeResolverTest" --tests "io.archivist.infrastructure.secondbrain.CorpusEntryMapperTest"` passes without `@SpringBootTest`.

### Implementation

- [x] T010 [P] [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/MetadataEnvelopeParser.java` — split leading `---` YAML envelope from Markdown body; parse map via SnakeYAML; support parse from partial prefix buffer for size-limited entries
- [x] T011 [P] [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/SourceIdNormalizer.java` — relative path → stable `sourceId` (normalised path without extension; optional metadata `id` override)
- [x] T012 [P] [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/ZoneTypeResolver.java` — longest-prefix zone rules + metadata fields → `KnowledgeZone` / `KnowledgeType`; unrecoverable mapping returns empty (caller omits with WARN)
- [x] T013 [US2] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/CorpusEntryMapper.java` — map parsed metadata + optional body → `Provenance` / `Evidence`; set `contentAvailability` (`AVAILABLE` or `UNAVAILABLE_ENTRY_TOO_LARGE`); title/timestamp fallback rules per plan (depends on T010–T012, T004–T005)

### Tests

- [x] T014 [P] [US2] Create `infrastructure/src/test/java/io/archivist/infrastructure/secondbrain/MetadataEnvelopeParserTest.java` — JUnit 5; envelope split, body extraction, prefix-buffer parse; zero Spring imports
- [x] T015 [P] [US2] Create `infrastructure/src/test/java/io/archivist/infrastructure/secondbrain/SourceIdNormalizerTest.java` — JUnit 5; stable sourceId from path and metadata override
- [x] T016 [P] [US2] Create `infrastructure/src/test/java/io/archivist/infrastructure/secondbrain/ZoneTypeResolverTest.java` — JUnit 5; path-prefix zone mapping per `contracts/mapping-defaults.md`; unknown type → empty
- [x] T017 [US2] Create `infrastructure/src/test/java/io/archivist/infrastructure/secondbrain/CorpusEntryMapperTest.java` — JUnit 5; AVAILABLE path populates body + flag; UNAVAILABLE_ENTRY_TOO_LARGE path has empty content + flag set; depends on T013

**Checkpoint**: All four unit test classes pass independently. Parser and mapper handle oversize flag path without Spring context.

---

## Phase 5: User Story 3 — Corpus Walk, Parallel Load, and Adapter (Priority: P3)

**Goal**: Discover Markdown entries, load with bounded virtual-thread parallelism, branch on size limit, and implement `KnowledgeCorpus` orchestration.

**Independent Test**: `SecondBrainKnowledgeCorpus` can be instantiated manually in a plain unit test with fixture path (or defer full assertion to US5); `./gradlew :infrastructure:compileJava` succeeds.

### Implementation

- [x] T018 [P] [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/CorpusWalker.java` — `Files.walk` discovery of `.md` files; skip hidden path segments (`.obsidian`, `.git`, etc.) and configured ignore globs
- [x] T019 [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/CorpusEntryLoader.java` — virtual threads + semaphore; `Files.size()` first; full read when ≤ `max-entry-bytes` → `AVAILABLE`; leading `metadata-read-bytes` only when oversized → `UNAVAILABLE_ENTRY_TOO_LARGE`; duplicate sourceId: first wins with WARN (depends on T010, T013, T018)
- [x] T020 [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/SecondBrainKnowledgeCorpus.java` — implements `KnowledgeCorpus`; orchestrates walk → parallel load → map; `catalog()` metadata-only for normal entries, includes flagged entries; `loadBySourceId` null → NPE, blank → empty; unrecoverable parse → omit with WARN (depends on T007, T018, T019)

**Checkpoint**: `:infrastructure:compileJava` succeeds. Adapter implements all three port methods with spec §3 semantics (integration proof in US5).

---

## Phase 6: User Story 4 — Spring Configuration (Priority: P4)

**Goal**: Register `KnowledgeCorpus` as a Spring bean with `@ConfigurationProperties` for corpus and mapping settings.

**Independent Test**: `./gradlew :infrastructure:compileJava` succeeds; bean creatable via `SecondBrainCorpusTestConfiguration` (US5).

### Implementation

- [x] T021 [P] [US4] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/SecondBrainCorpusProperties.java` — `@ConfigurationProperties("archivist.second-brain")` with `path`, `max-entry-bytes` (default 1048576), `metadata-read-bytes` (default 65536), `load-concurrency` (default 32), `ignore-globs`
- [x] T022 [P] [US4] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/SecondBrainMappingProperties.java` — `@ConfigurationProperties("archivist.second-brain.mapping")` with `zone-prefixes` defaults per `contracts/mapping-defaults.md` and optional `zone-default-types`
- [x] T023 [US4] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/SecondBrainConfiguration.java` — `@Configuration` registering `KnowledgeCorpus` bean wired to `SecondBrainKnowledgeCorpus` with properties; constructor injection only; no `transport.*` imports (depends on T020–T022)

**Checkpoint**: Spring configuration compiles. Corpus bean registerable in minimal test context.

---

## Phase 7: User Story 5 — Fixture Corpus and Integration Tests (Priority: P5)

**Goal**: Checked-in fixture Markdown exercises zone/type mapping, provenance chain, unknown-type omission, and oversize flagging. Integration test asserts against contract JSON.

**Independent Test**: `./gradlew :infrastructure:test --tests "io.archivist.infrastructure.secondbrain.SecondBrainKnowledgeCorpusIntegrationTest"` passes in under 2 seconds (AC-14).

### Fixture corpus

- [x] T024 [US5] Create fixture Markdown under `infrastructure/src/test/resources/fixture-corpus/` — `raw/readings/sample-reading.md`, `wiki/concepts/sample-concept.md`, `dev/decisions/sample-decision.md`, `wiki/people/sample-person.md`, `wiki/concepts/sample-with-sources.md`, `edge-cases/unknown-type.md` (invalid type for omission test); frontmatter and body text matching `contracts/fixture-catalog-expected.json`
- [x] T025 [US5] Add oversize entry scenario for AC-10 — either `infrastructure/src/test/resources/fixture-corpus/edge-cases/oversize-entry.md` exceeding default `max-entry-bytes` or `@TempDir` generated file in integration test setup

### Tests

- [x] T026 [US5] Create `infrastructure/src/test/java/io/archivist/infrastructure/secondbrain/support/SecondBrainCorpusTestConfiguration.java` — `@TestConfiguration` / minimal `@SpringBootTest` config; corpus path → fixture root; must NOT load full `ArchivistApplication` or transport beans
- [x] T027 [US5] Create `infrastructure/src/test/java/io/archivist/infrastructure/secondbrain/SecondBrainKnowledgeCorpusIntegrationTest.java` — `@SpringBootTest(classes = SecondBrainCorpusTestConfiguration.class)`; assert `catalog()` size and per-entry `KnowledgeType`/`KnowledgeZone` against `contracts/fixture-catalog-expected.json`; assert `loadAll()` body text; assert `sources` chain on `sample-with-sources`; assert `unknown-type` absent; assert oversize entry present with `UNAVAILABLE_ENTRY_TOO_LARGE` and empty content; assert `loadBySourceId` hit/miss cases (depends on T023–T026)

**Checkpoint**: Integration test passes. Contract drift fails the build. Fixture load completes in < 2 s on CI hardware.

---

## Phase 8: Polish & Verification

**Purpose**: Dependency rules, architectural boundaries, and full quickstart validation.

- [x] T028 [P] Verify domain and application have no infrastructure imports: `./gradlew :domain:dependencies --configuration compileClasspath` and `./gradlew :application:dependencies --configuration compileClasspath` — no `:infrastructure`, SnakeYAML, or Markdown parsing libraries (AC-11)
- [x] T029 [P] Verify transport does not depend on infrastructure: `./gradlew :transport:dependencies --configuration compileClasspath` — no `:infrastructure` entries; MCP stub behaviour unchanged (AC-12, AC-13)
- [x] T030 [P] Verify no star imports in new source: `grep -r "^import .*\*;" --include="*.java" infrastructure/src/main/java/io/archivist/infrastructure/secondbrain` — must produce no output
- [x] T031 Run infrastructure tests: `./gradlew :infrastructure:test` — all unit and integration tests pass
- [x] T032 Run full build: `./gradlew build` from repo root — `BUILD SUCCESSFUL` required (AC-1)
- [x] T033 Run full quickstart verification: execute all checks in `specs/003-second-brain-integration/quickstart.md` in order; confirm domain/application Spring-free, transport independent, fixture integration passes (AC-14, AC-15)
- [x] T034 Run `/speckit-taskstoissues` before opening the implementation PR — creates epic + sub-issues and writes `specs/003-second-brain-integration/github-issues.md` per constitution workflow

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — fixtures verified before domain work
- **Phase 3 (US1 — Domain)**: Depends on Phase 2 — port and model types block all infrastructure
- **Phase 4 (US2 — Parsing)**: Depends on Phase 3 — mapper uses `ContentAvailability` and `Provenance`
- **Phase 5 (US3 — Orchestration)**: Depends on Phase 4 — loader invokes parser/mapper/resolver
- **Phase 6 (US4 — Spring)**: Depends on Phase 5 — configuration wires `SecondBrainKnowledgeCorpus`
- **Phase 7 (US5 — Integration)**: Depends on Phase 6 — tests autowire Spring-managed corpus bean
- **Phase 8 (Polish)**: Depends on Phases 1–7 complete

### User Story Dependencies

- **US1 (Domain)**: Independent after Phase 2
- **US2 (Parsing)**: Depends on US1 (`ContentAvailability`, extended `Provenance`)
- **US3 (Orchestration)**: Depends on US2 (parser, mapper, resolver, normalizer)
- **US4 (Spring config)**: Depends on US3 (`SecondBrainKnowledgeCorpus`)
- **US5 (Integration tests)**: Depends on US4 (bean wiring + fixture path properties)
- **US6 (Polish)**: Depends on all stories

### Within Each Phase

- T004, T007, T008 (US1) — parallel where marked; T005 after T004; T006 after T005; T009 after all US1 tasks
- T010–T012 (US2 impl) — parallel; T013 after T010–T012; T014–T16 tests parallel; T017 after T013
- T018 parallel; T019 after T018 + US2; T020 after T019
- T021, T022 parallel; T023 after T020–T022
- T024, T025 sequential fixture work; T026 before T027; T027 after T023–T026

---

## Parallel Opportunities

### Phase 3 — domain types and port in parallel

```text
Task: ContentAvailability.java     [T004]
Task: KnowledgeCorpus.java           [T007]
Task: KnowledgeCorpusException.java  [T008]
# Then: Provenance.java [T005] → compile-site updates [T006] → verify [T009]
```

### Phase 4 — parser components and unit tests in parallel

```text
Task: MetadataEnvelopeParser.java       [T010]
Task: SourceIdNormalizer.java           [T011]
Task: ZoneTypeResolver.java             [T012]
# Then: CorpusEntryMapper.java [T013]
Task: MetadataEnvelopeParserTest.java   [T014]
Task: SourceIdNormalizerTest.java       [T015]
Task: ZoneTypeResolverTest.java         [T016]
# Then: CorpusEntryMapperTest.java [T017]
```

### Phase 6 — properties classes in parallel

```text
Task: SecondBrainCorpusProperties.java   [T021]
Task: SecondBrainMappingProperties.java  [T022]
# Then: SecondBrainConfiguration.java [T023]
```

### Phase 8 — verification checks in parallel

```text
Task: domain/application dependency check [T028]
Task: transport dependency check          [T029]
Task: star-import grep                    [T030]
# Then: test runs and quickstart [T031–T033]
```

---

## Implementation Strategy

### MVP (US1 — domain port and model)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (fixture verification)
3. Complete Phase 3: US1 (domain model + `KnowledgeCorpus` port)
4. **STOP and VALIDATE**: `./gradlew :domain:test` passes; port interface matches spec §3

### Incremental Delivery

| Increment | Delivers                                                  |
| --------- | --------------------------------------------------------- |
| US1       | Stable domain contract for future retrieval/gateway specs |
| US2       | Testable parsing pipeline (no filesystem yet)             |
| US3       | End-to-end corpus read against any directory path         |
| US4       | Spring-managed bean ready for future gateway wiring       |
| US5       | Fixture contract gate + AC-4–AC-10 sign-off               |
| US6       | Full build + quickstart + GitHub issue manifest           |

### Full Feature Path

1. Phases 1–3 → domain MVP validated
2. Phases 4–5 → parsing + orchestration (manual smoke with temp dir optional)
3. Phase 6 → Spring bean
4. Phase 7 → integration tests green
5. Phase 8 → `./gradlew build` + quickstart + `/speckit-taskstoissues`

---

## Notes

- `[P]` = different files, no intra-phase dependencies — safe to parallelize
- `[USn]` maps each task to its user story for traceability to spec acceptance criteria
- MCP JSON omits `contentAvailability` in spec 003 (transport response DTO per ADR-0003); gateway/retrieval wiring spec decides whether to expose the flag or drop flagged entries before MCP
- Infrastructure flags oversize entries; it does not filter them from `catalog()` / `loadAll()` ([ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md))
- No cache or watch-based reload in this feature ([ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md))
- Commit after each phase checkpoint
- Do not wire `KnowledgeCorpus` into application use cases or transport in this spec
