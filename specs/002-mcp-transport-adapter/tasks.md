# Tasks: MCP Transport Adapter

**Input**: Design documents from `specs/002-mcp-transport-adapter/`

**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | data-model.md ✅ | contracts/ ✅ | quickstart.md ✅

**Stack**: Java 21 · Spring Boot 4.1.0 · Spring AI 2.0.0 · Gradle (Kotlin DSL) · `libs.versions.toml`

**Tests**: Explicitly required by spec (AC-9, AC-10). Contract regression tests use `@SpringBootTest(classes = McpAdapterTestConfiguration.class)` — minimal Spring context; Jackson `JsonNode.equals()` for fixture comparison; `spring-boot-starter-test` only (no new JSON libraries).

**User story mapping** (acceptance criteria → deliverable phases):

| Story | Deliverable                                                                             | Acceptance Criteria                    |
| ----- | --------------------------------------------------------------------------------------- | -------------------------------------- |
| US1   | Application stub use cases (eight `port.in` implementations)                            | AC-6                                   |
| US2   | MCP transport adapter core (`@McpTool` registration, validation, serialisation, wiring) | AC-2, AC-3, AC-4, AC-5, AC-8           |
| US3   | Transport unit tests (plain JUnit — no Spring context)                                  | AC-9 (partial)                         |
| US4   | Contract regression + adapter integration tests (minimal Spring context)                | AC-9, AC-10, AC-11                     |
| US5   | Polish & end-to-end verification                                                        | AC-1, AC-7, AC-12, AC-13, AC-14, AC-15 |

---

## Phase 1: Setup (Test Infrastructure)

**Purpose**: Add Spring Boot test support to the transport module. No production source changes.

- [x] T001 Add `spring-boot-starter-test` library alias to `gradle/libs.versions.toml` — coordinate `org.springframework.boot:spring-boot-starter-test`, version via Spring Boot BOM (`4.1.0`)
- [x] T002 Add `testImplementation(libs.spring.boot.starter.test)` to `transport/build.gradle.kts`

**Checkpoint**: `./gradlew :transport:dependencies --configuration testCompileClasspath` lists `spring-boot-starter-test`; no new non-Spring test libraries added.

---

## Phase 2: Foundational (Contract Fixtures)

**Purpose**: Confirm machine-readable contract fixtures are in place before adapter implementation. Fixtures were generated during `/speckit-plan`; this phase verifies they match the authoritative contract.

⚠️ **CRITICAL**: Contract regression tests (US4) depend on these fixtures. Do not modify tool names, descriptions, or schemas without updating `specs/001-project-scaffolding/contracts/mcp-server.md` through the specification workflow.

- [x] T003 Verify contract fixtures in `specs/002-mcp-transport-adapter/contracts/` — confirm `mcp-tools-expected.json` (eight tools), `mcp-server-identity-expected.json` (`name: archivist`, `version: 0.1.0`), and `evidence-response-schema-expected.json` match `specs/001-project-scaffolding/contracts/mcp-server.md`; fix any drift before proceeding

**Checkpoint**: All three JSON fixtures present and consistent with the 001 MCP server contract.

---

## Phase 3: User Story 1 — Application Stub Use Cases (Priority: P1) 🎯 MVP

**Goal**: Eight plain Java stub use cases implement `domain.port.in` interfaces and return empty lists. No Spring annotations. No `port.out` dependencies.

**Independent Test**: `./gradlew :application:build` succeeds; `./gradlew :application:dependencies --configuration compileClasspath` shows only `:domain` — no Spring or MCP entries.

### Implementation

- [x] T004 [P] [US1] Create `application/src/main/java/io/archivist/application/usecase/RetrieveContextUseCase.java` — implements `RetrieveContext`; `retrieveContext(String query)` returns `List.of()`
- [x] T005 [P] [US1] Create `application/src/main/java/io/archivist/application/usecase/FindDecisionsUseCase.java` — implements `FindDecisions`; `findDecisions(String topic)` returns `List.of()`
- [x] T006 [P] [US1] Create `application/src/main/java/io/archivist/application/usecase/FindProjectsUseCase.java` — implements `FindProjects`; `findProjects(String criteria)` returns `List.of()`
- [x] T007 [P] [US1] Create `application/src/main/java/io/archivist/application/usecase/FindPeopleUseCase.java` — implements `FindPeople`; `findPeople(String name)` returns `List.of()`
- [x] T008 [P] [US1] Create `application/src/main/java/io/archivist/application/usecase/FindConceptsUseCase.java` — implements `FindConcepts`; `findConcepts(String topic)` returns `List.of()`
- [x] T009 [P] [US1] Create `application/src/main/java/io/archivist/application/usecase/FindRelatedKnowledgeUseCase.java` — implements `FindRelatedKnowledge`; `findRelatedKnowledge(String query)` returns `List.of()`
- [x] T010 [P] [US1] Create `application/src/main/java/io/archivist/application/usecase/FindReadingsUseCase.java` — implements `FindReadings`; `findReadings(String topic)` returns `List.of()`
- [x] T011 [P] [US1] Create `application/src/main/java/io/archivist/application/usecase/FindDebriefsUseCase.java` — implements `FindDebriefs`; `findDebriefs(String topic)` returns `List.of()`

**Checkpoint**: `./gradlew :application:build` succeeds. All eight stubs compile with constructor-free plain classes (no-args or implicit default constructor).

---

## Phase 4: User Story 2 — MCP Transport Adapter Core (Priority: P2)

**Goal**: Register all eight domain capabilities as MCP tools via `@McpTool`, validate inputs at the transport boundary, delegate to stub use cases, and return serialised JSON via `EvidenceJsonMapper`.

**Independent Test**: `./gradlew :transport:compileJava` succeeds; `./gradlew :transport:dependencies --configuration compileClasspath` shows no `:infrastructure` dependency. After wiring, `./gradlew :transport:bootRun` (with env vars set) starts and an MCP client lists eight tools (manual smoke — full automation in US4/US5).

### Implementation

- [x] T012 [P] [US2] Create `transport/src/main/java/io/archivist/transport/mcp/McpInputValidator.java` — static `requireNonBlank(String value, String paramName)` throwing `IllegalArgumentException` with parameter name when null, empty, or whitespace-only
- [x] T013 [P] [US2] Create `transport/src/main/java/io/archivist/transport/mcp/EvidenceJsonMapper.java` — constructor-injected `com.fasterxml.jackson.databind.ObjectMapper`; method to serialise `List<Evidence>` to JSON string matching contract shape (enum names, ISO-8601 instants)
- [x] T014 [US2] Create `transport/src/main/java/io/archivist/transport/config/StubUseCaseConfiguration.java` — `@Configuration` with eight `@Bean` methods returning `port.in` interface types wired to stub use case classes from US1; constructor injection only
- [x] T015 [US2] Create `transport/src/main/java/io/archivist/transport/mcp/ArchivistMcpTools.java` — `@Component` with eight `@McpTool` / `@McpToolParam` methods (names, descriptions, parameter names per `specs/002-mcp-transport-adapter/contracts/mcp-tools-expected.json`); validate → delegate to injected `port.in` → serialise via injected `EvidenceJsonMapper` → return JSON string (sole serialisation path; depends on T012, T013, T014)
- [x] T016 [US2] Update `transport/src/main/java/io/archivist/transport/ArchivistApplication.java` — ensure `StubUseCaseConfiguration` and `ArchivistMcpTools` are picked up (add `@Import(StubUseCaseConfiguration.class)` if component scan does not cover `config` subpackage); no field injection

**Checkpoint**: `./gradlew :transport:build` compiles. MCP server boots with eight registered tools (manual MCP client list — names match contract).

---

## Phase 5: User Story 3 — Transport Unit Tests (Priority: P3)

**Goal**: Plain JUnit 5 tests for transport utilities — no Spring test context.

**Independent Test**: `./gradlew :transport:test --tests "io.archivist.transport.mcp.McpInputValidatorTest" --tests "io.archivist.transport.mcp.EvidenceJsonMapperTest"` passes.

### Tests

- [x] T017 [P] [US3] Create `transport/src/test/java/io/archivist/transport/mcp/McpInputValidatorTest.java` — JUnit 5; assert `requireNonBlank` throws for null, empty, and whitespace-only values; assert no throw for valid non-blank input; zero Spring imports
- [x] T018 [P] [US3] Create `transport/src/test/java/io/archivist/transport/mcp/EvidenceJsonMapperTest.java` — JUnit 5 with manually constructed `ObjectMapper`; serialise canonical sample `Evidence` list; compare output to `specs/002-mcp-transport-adapter/contracts/evidence-response-schema-expected.json` via `JsonNode.equals()`; zero Spring test context

**Checkpoint**: Both unit test classes pass independently without `@SpringBootTest`.

---

## Phase 6: User Story 4 — Contract Regression & Adapter Integration Tests (Priority: P4)

**Goal**: Build-time contract gate comparing registered MCP tools and serialised evidence shape to checked-in fixtures. Adapter behaviour tests for validation, delegation, and stub empty responses — minimal Spring context only.

**Independent Test**: `./gradlew :transport:test --tests "io.archivist.transport.mcp.McpContractRegressionTest" --tests "io.archivist.transport.mcp.ArchivistMcpToolsTest"` passes. Temporarily changing a tool description without updating fixtures causes `McpContractRegressionTest` to fail.

### Tests

- [x] T019 [US4] Create `transport/src/test/java/io/archivist/transport/support/McpAdapterTestConfiguration.java` — `@TestConfiguration` with explicit `@Import` of `StubUseCaseConfiguration`, `ArchivistMcpTools`, `EvidenceJsonMapper`, and Spring AI MCP tool-callback auto-configuration only; must NOT load `ArchivistApplication`, `ArchivistProperties`, or `:infrastructure`; test properties: `spring.ai.mcp.server.stdio=false` (or exclude STDIO auto-config if property unsupported)
- [x] T020 [US4] Create `transport/src/test/java/io/archivist/transport/mcp/McpContractRegressionTest.java` — `@SpringBootTest(classes = McpAdapterTestConfiguration.class)`; autowire `List<org.springframework.ai.tool.ToolCallback>`; assert exactly eight tools; compare each `getToolDefinition()` name/description/inputSchema to `specs/002-mcp-transport-adapter/contracts/mcp-tools-expected.json` via `ObjectMapper.readTree()` + `JsonNode.equals()`; assert server identity matches `mcp-server-identity-expected.json`; assert `EvidenceJsonMapper` output matches `evidence-response-schema-expected.json` (depends on T019, T015)
- [x] T021 [US4] Create `transport/src/test/java/io/archivist/transport/mcp/ArchivistMcpToolsTest.java` — `@SpringBootTest(classes = McpAdapterTestConfiguration.class)`; use `@MockitoBean` to replace individual `port.in` beans; **parameterized** invalid-input tests across all eight tools (blank/null → `IllegalArgumentException` with parameter name, mock never called); valid input → delegates and returns `[]` JSON; mock returning sample evidence → serialised response matches `evidence-response-schema-expected.json` via production `EvidenceJsonMapper` path (AC-9d); mock throwing → tool error surfaces (depends on T019, T015)

**Checkpoint**: `./gradlew :transport:test` passes including contract regression. Unintended contract drift fails the build (AC-10).

---

## Phase 7: Polish & Verification

**Purpose**: Code quality, dependency rules, and full quickstart validation.

- [x] T022 [P] Verify no star imports in new source: `grep -r "^import .*\*;" --include="*.java" application/src/main/java/io/archivist/application/usecase transport/src` — must produce no output (AC-15)
- [x] T023 [P] Verify no field injection in new source: `grep -r "@Autowired" --include="*.java" application/src/main/java/io/archivist/application/usecase transport/src` — must produce no output (AC-14)
- [x] T024 [P] Verify transport does not depend on infrastructure: `./gradlew :transport:dependencies --configuration compileClasspath` — no `:infrastructure` entries (AC-7)
- [x] T025 Run transport tests: `./gradlew :transport:test` — all tests pass
- [x] T026 Run full build: `./gradlew build` from repo root — `BUILD SUCCESSFUL` required; contract regression tests included (AC-1, AC-10)
- [x] T027 Run full quickstart verification: execute all checks in `specs/002-mcp-transport-adapter/quickstart.md` in order; confirm MCP client lists eight tools and stub invocations return `[]` (AC-12, AC-13)
- [x] T028 Run `/speckit-taskstoissues` before opening the implementation PR — creates epic + sub-issues and writes `specs/002-mcp-transport-adapter/github-issues.md` per constitution workflow

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — fixtures verified before adapter work
- **Phase 3 (US1 — Stubs)**: Depends on Phase 2 — domain `port.in` interfaces already exist from scaffold
- **Phase 4 (US2 — Adapter)**: Depends on Phase 3 — `ArchivistMcpTools` delegates to stub use cases wired in `StubUseCaseConfiguration`
- **Phase 5 (US3 — Unit tests)**: Depends on Phase 4 — tests target `McpInputValidator` and `EvidenceJsonMapper` from US2
- **Phase 6 (US4 — Contract/integration tests)**: Depends on Phase 4 — tests require registered `@McpTool` beans
- **Phase 7 (Polish)**: Depends on Phases 1–6 complete

### User Story Dependencies

- **US1 (Stubs)**: Independent after Phase 2
- **US2 (Adapter core)**: Depends on US1
- **US3 (Unit tests)**: Depends on US2 (T012, T013)
- **US4 (Contract tests)**: Depends on US2 (T015, T016); parallel to US3 after US2 complete
- **US5 (Polish)**: Depends on all stories

### Within Each Phase

- T004–T011 (US1 stubs) — fully parallel
- T012, T013 (US2 utilities) — parallel; T014 after US1; T015 after T012+T014; T016 after T014
- T017, T018 (US3) — parallel after US2
- T019 before T020, T021; T020 and T021 parallel after T019

---

## Parallel Opportunities

### Phase 3 — all stub use cases in parallel

```text
Task: RetrieveContextUseCase.java      [T004]
Task: FindDecisionsUseCase.java        [T005]
Task: FindProjectsUseCase.java         [T006]
Task: FindPeopleUseCase.java           [T007]
Task: FindConceptsUseCase.java         [T008]
Task: FindRelatedKnowledgeUseCase.java [T009]
Task: FindReadingsUseCase.java         [T010]
Task: FindDebriefsUseCase.java         [T011]
```

### Phase 4 — validator and mapper in parallel

```text
Task: McpInputValidator.java    [T012]
Task: EvidenceJsonMapper.java   [T013]
# Then: StubUseCaseConfiguration [T014] → ArchivistMcpTools [T015] → ArchivistApplication [T016]
```

### Phases 5 + 6 — unit and integration tests in parallel (after US2)

```text
Task: McpInputValidatorTest.java       [T017]
Task: EvidenceJsonMapperTest.java      [T018]
Task: McpAdapterTestConfiguration.java [T019]
# Then in parallel:
Task: McpContractRegressionTest.java   [T020]
Task: ArchivistMcpToolsTest.java       [T021]
```

---

## Implementation Strategy

### MVP (US1 + US2 — callable adapter with empty responses)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (fixture verification)
3. Complete Phase 3: US1 (stub use cases)
4. Complete Phase 4: US2 (MCP adapter core)
5. **STOP and VALIDATE**: `./gradlew :transport:bootRun` + MCP client lists eight tools; valid invocation returns `[]`

### Full Feature (All Stories)

1. Phases 1–4 → MVP validated
2. Phase 5 (US3) + Phase 6 (US4) in parallel → `./gradlew :transport:test` passes
3. Phase 7: Polish + full quickstart verification

### Incremental Delivery

Each story adds verifiable value without breaking prior work:

| Increment | Delivers                                              |
| --------- | ----------------------------------------------------- |
| US1       | Application layer entry points (testable without MCP) |
| US2       | MCP tools callable by external agents                 |
| US3       | Unit test safety net for serialisation and validation |
| US4       | Build-time contract stability gate                    |
| US5       | Full acceptance criteria sign-off                     |

---

## Notes

- `[P]` = different files, no intra-phase dependencies — safe to parallelize
- `[USn]` maps each task to its user story for traceability to spec acceptance criteria
- Contract fixture changes require simultaneous update of `specs/001-project-scaffolding/contracts/mcp-server.md` and spec approval
- Use `@MockitoBean` (Spring Boot 4) in preference to legacy `@MockBean` where available — both are Spring-native
- Commit after each phase checkpoint
- Do not import `:infrastructure` in `transport` at any point
- stdout remains reserved for MCP STDIO; all logging on stderr (unchanged from scaffold)
