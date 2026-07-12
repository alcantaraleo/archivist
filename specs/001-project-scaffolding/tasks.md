# Tasks: Project Scaffolding

**Input**: Design documents from `specs/001-project-scaffolding/`

**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | data-model.md ✅ | contracts/ ✅ | quickstart.md ✅

**Stack**: Java 21 · Spring Boot 4.1.0 · Spring AI 2.0.0 · Gradle (Kotlin DSL) · `libs.versions.toml`

**Tests**: Not requested in this spec — placeholder sources only. Acceptance is verified via `./gradlew` commands and static analysis per `quickstart.md`.

**User story mapping** (acceptance criteria → deliverable phases):

| Story | Deliverable                                                  | Acceptance Criteria    |
| ----- | ------------------------------------------------------------ | ---------------------- |
| US1   | Gradle multi-module project compiles                         | AC 1, 2, 3             |
| US2   | Domain layer skeleton — all placeholder types and interfaces | AC 3, 4, 10, 11        |
| US3   | Infrastructure layer skeleton                                | AC 6                   |
| US4   | Transport layer boots as STDIO MCP server + config contract  | AC 7, 8, 9, 12, 13, 14 |

---

## Phase 1: Setup (Gradle Build Infrastructure)

**Purpose**: Initialize the Gradle project — wrapper, version catalogue, root settings, root build file. No Java source yet.

- [ ] T001 Initialize Gradle wrapper at repo root: `gradle wrapper --gradle-version latest` (creates `gradlew`, `gradlew.bat`, `gradle/wrapper/`)
- [ ] T002 [P] Create `gradle/libs.versions.toml` — declare `spring-boot = "4.1.0"`, `spring-ai = "2.0.0"`, `junit-jupiter` (via BOM), `mockito-core` (via BOM) version aliases
- [ ] T003 Create `settings.gradle.kts` — set `rootProject.name = "archivist"` and include all four modules: `domain`, `application`, `infrastructure`, `transport`
- [ ] T004 Create root `build.gradle.kts` — configure `java` toolchain to Java 21 for all subprojects via `subprojects {}` block; register Spring Boot and Spring AI BOMs in `dependencyManagement`; apply `java-library` plugin baseline

**Checkpoint**: `./gradlew projects` lists all four modules.

---

## Phase 2: Foundational (Module Dependency Graph)

**Purpose**: Declare the inter-module dependency rules. These `build.gradle.kts` files are the enforcement mechanism for Clean Architecture — the compiler will reject illegal imports.

⚠️ **CRITICAL**: No Java source can compile in any module until this phase is complete.

- [ ] T005 Create `domain/build.gradle.kts` — apply `java-library`; declare **zero** compile-time dependencies on Spring, Spring AI, MCP SDK, or any sibling module
- [ ] T006 [P] [US2] Create `application/build.gradle.kts` — apply `java-library`; declare single compile dependency: `api(project(":domain"))`; no Spring, MCP SDK
- [ ] T007 [P] [US3] Create `infrastructure/build.gradle.kts` — apply `java-library`; declare `api(project(":domain"))`, `api(project(":application"))`; import Spring Boot BOM; add `spring-boot-starter`, `spring-ai-starter` compile dependencies; **no** `:transport` dependency
- [ ] T008 [P] [US4] Create `transport/build.gradle.kts` — apply `org.springframework.boot` plugin (for `bootJar`/`bootRun`); declare `implementation(project(":application"))`; add `spring-boot-starter`, `spring-ai-starter-mcp-server` (STDIO); **no** `:infrastructure` compile dependency

**Checkpoint**: `./gradlew :domain:dependencies --configuration compileClasspath` shows no Spring entries.

---

## Phase 3: User Story 1 — Domain Layer Skeleton (Priority: P1) 🎯 MVP

**Goal**: The `domain` module contains all placeholder types and port interfaces, compiles cleanly, and has zero Spring or MCP imports.

**Independent Test**: `./gradlew :domain:build` succeeds; `./gradlew :domain:dependencies` contains no `org.springframework` entries.

### Implementation

- [ ] T009 [P] [US1] Create `domain/src/main/java/io/archivist/domain/model/KnowledgeType.java` — enum with constants: `CONCEPT`, `ENTITY`, `PERSON`, `PROJECT`, `DECISION`, `DEBRIEF`, `SYNTHESIS`, `READING`
- [ ] T010 [P] [US1] Create `domain/src/main/java/io/archivist/domain/model/KnowledgeZone.java` — enum with constants: `SOURCE`, `SYNTHESIZED`, `TECHNICAL`, `IDENTITY`, `COMPILED`, `SIGNAL`
- [ ] T011 [P] [US1] Create `domain/src/main/java/io/archivist/domain/model/Query.java` — record with single field: `String text`
- [ ] T012 [P] [US1] Create `domain/src/main/java/io/archivist/domain/model/Provenance.java` — record with fields: `String sourceId`, `String title`, `KnowledgeType type`, `KnowledgeZone zone`, `List<String> tags`, `List<String> sources`, `Instant created`, `Instant updated`
- [ ] T013 [US1] Create `domain/src/main/java/io/archivist/domain/model/Evidence.java` — record with fields: `String content`, `Provenance provenance` (depends on T012)
- [ ] T014 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/RetrieveContext.java` — interface: `List<Evidence> retrieveContext(String query)`
- [ ] T015 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindDecisions.java` — interface: `List<Evidence> findDecisions(String topic)`
- [ ] T016 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindProjects.java` — interface: `List<Evidence> findProjects(String criteria)`
- [ ] T017 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindPeople.java` — interface: `List<Evidence> findPeople(String name)`
- [ ] T018 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindConcepts.java` — interface: `List<Evidence> findConcepts(String topic)`
- [ ] T019 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindRelatedKnowledge.java` — interface: `List<Evidence> findRelatedKnowledge(String query)`
- [ ] T020 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindReadings.java` — interface: `List<Evidence> findReadings(String topic)`
- [ ] T021 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindDebriefs.java` — interface: `List<Evidence> findDebriefs(String topic)`
- [ ] T022 [US1] Create `domain/src/main/java/io/archivist/domain/port/out/KnowledgeGateway.java` — interface: `List<Evidence> retrieve(Query query)` (depends on T013)

**Checkpoint**: `./gradlew :domain:build` succeeds. Domain module has all model types, all 8 port.in interfaces, and the port.out gateway. Zero Spring imports.

---

## Phase 4: User Story 2 — Application Layer Skeleton (Priority: P2)

**Goal**: The `application` module establishes its package hierarchy, depends only on `:domain`, and compiles cleanly with no Spring or MCP imports.

**Independent Test**: `./gradlew :application:build` succeeds; `./gradlew :application:dependencies --configuration compileClasspath` shows only `:domain` as a compile dependency.

### Implementation

- [ ] T023 [US2] Create `application/src/main/java/io/archivist/application/usecase/package-info.java` — package declaration only; establishes the package path where use case interactors will live in future specs

**Checkpoint**: `./gradlew :application:build` succeeds. Application module compiles with `:domain` as its sole dependency.

---

## Phase 5: User Story 3 — Infrastructure Layer Skeleton (Priority: P3)

**Goal**: The `infrastructure` module compiles against `:domain` and `:application`. Package stubs are in place for retrieval strategies and the Second Brain adapter.

**Independent Test**: `./gradlew :infrastructure:build` succeeds; `./gradlew :infrastructure:dependencies --configuration compileClasspath` contains no `:transport` entries.

### Implementation

- [ ] T024 [P] [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/package-info.java` — package declaration only; reserves package for future retrieval strategy implementations
- [ ] T025 [P] [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/package-info.java` — package declaration only; reserves package for future Second Brain adapter

**Checkpoint**: `./gradlew :infrastructure:build` succeeds. Infrastructure module compiles cleanly with no transport dependency.

---

## Phase 6: User Story 4 — Transport Layer Boots as STDIO MCP Server (Priority: P4)

**Goal**: The `transport` module starts a Spring Boot application with the STDIO MCP server configured and the external configuration contract enforced. `ArchivistProperties` lives in `transport.config` — configuration binding is a Spring Boot wiring concern, not infrastructure logic. The service starts when all required environment variables are present and fails fast with a clear error when they are absent.

**Independent Test**: `ARCHIVIST_SECOND_BRAIN_PATH=/tmp ./gradlew :transport:bootRun` logs `Started ArchivistApplication` and remains running. Starting without `ARCHIVIST_SECOND_BRAIN_PATH` produces a startup `BindValidationException`, not a running service.

### Implementation

- [ ] T026 [US4] Create `transport/src/main/java/io/archivist/transport/ArchivistApplication.java` — `@SpringBootApplication`; `@ConfigurationPropertiesScan` or `@EnableConfigurationProperties(ArchivistProperties.class)`; standard `main(String[] args)` entry point; no field injection
- [ ] T027 [P] [US4] Create `transport/src/main/java/io/archivist/transport/config/ArchivistProperties.java` — `@ConfigurationProperties(prefix = "archivist")` and `@Validated`; nested `SecondBrain` record with `@NotBlank String path` bound to `archivist.second-brain.path` (env: `ARCHIVIST_SECOND_BRAIN_PATH`); lives in `transport` — infrastructure adapters will receive plain values, never this class directly
- [ ] T028 [P] [US4] Create `transport/src/main/java/io/archivist/transport/mcp/package-info.java` — package declaration only; reserves package for future `@McpTool`-annotated tool registrations
- [ ] T029 [US4] Create `transport/src/main/resources/application.properties` — set `spring.application.name=archivist`; `spring.ai.mcp.server.name=archivist`; `spring.ai.mcp.server.version=0.1.0`; `spring.ai.mcp.server.stdio=true`; `archivist.second-brain.path=${ARCHIVIST_SECOND_BRAIN_PATH}` (no default — absence must trigger startup failure)

**Checkpoint**: `ARCHIVIST_SECOND_BRAIN_PATH=/tmp ./gradlew :transport:bootRun` logs `Started ArchivistApplication`. Starting with the variable absent or empty produces a startup failure with a descriptive message.

---

## Phase 7: Polish & Verification

**Purpose**: Code quality enforcement, documentation, and full end-to-end quickstart validation.

- [ ] T031 [P] Create `README.md` at repo root — document all required environment variables in a table: variable name, description, example value, required/optional; include minimum `./gradlew build` and run instructions
- [ ] T032 [P] Verify no star imports across all Java source: `grep -r "^import .*\*;" --include="*.java" .` must produce no output
- [ ] T033 [P] Verify no field injection across all Java source: `grep -r "@Autowired" --include="*.java" .` must produce no output
- [ ] T034 [P] Verify no hardcoded paths or addresses in source or properties: `grep -rn "/home/\|/Users/\|localhost\|127\.0\.0\.1" --include="*.java" --include="*.properties" .` must produce no matches in Java files
- [ ] T035 Run full build: `./gradlew build` from repo root — `BUILD SUCCESSFUL` required
- [ ] T036 Run full quickstart verification: execute all checks in `specs/001-project-scaffolding/quickstart.md` in order; confirm each passes

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately
- **Phase 2 (Foundational)**: Depends on Phase 1 — `settings.gradle.kts` must exist before module `build.gradle.kts` files are useful
- **Phase 3 (US1 — Domain)**: Depends on Phase 2 — module build file must exist before source is added
- **Phase 4 (US2 — Application)**: Depends on Phase 2 + Phase 3 — `application` depends on `:domain`
- **Phase 5 (US3 — Infrastructure)**: Depends on Phase 2 + Phase 3 — `infrastructure` depends on `:domain` and `:application`
- **Phase 6 (US4 — Transport)**: Depends on Phase 2 — `transport` depends on `:application`; `ArchivistProperties` is created within this phase (same module)
- **Phase 7 (Polish)**: Depends on all prior phases complete

### User Story Dependencies

- **US1 (Domain)**: Can start after Phase 2 — independent of all other user stories
- **US2 (Application)**: Can start after Phase 2 + US1 complete
- **US3 (Infrastructure)**: Can start after Phase 2 + US1 complete — parallel to US2
- **US4 (Transport)**: Can start after Phase 2 complete — `ArchivistProperties` is created within US4 itself; no dependency on US3

### Within Each Phase

- All `[P]`-marked tasks within a phase can run in parallel (different files, no intra-phase dependency)
- T013 depends on T012 (Evidence depends on Provenance)
- T014–T021 depend on T013 (port.in interfaces return `List<Evidence>`; Evidence must compile first)
- T022 depends on T013 (KnowledgeGateway references Evidence)
- T026 depends on T027 (`ArchivistApplication` registers `ArchivistProperties`; properties class must exist first)

---

## Parallel Opportunities

### Phase 2 — all module build files in parallel

```
Task: "Create domain/build.gradle.kts"           [T005]
Task: "Create application/build.gradle.kts"      [T006]
Task: "Create infrastructure/build.gradle.kts"   [T007]
Task: "Create transport/build.gradle.kts"         [T008]
```

### Phase 3 — all enums, all port.in interfaces in parallel

```
# Enums (T009, T010) — parallel
# Model records (T011, T012) — parallel; then T013 after T012
# All 8 port.in interfaces (T014–T021) — fully parallel, but only after T013
#   (interfaces return List<Evidence>; Evidence.java must compile first)
# T022 after T013
```

### Phases 4, 5, 6 — all can run in parallel after Phase 2 + US1 complete

```
Task: "US2 — Application skeleton"               [T023]
Task: "US3 — Infrastructure package stubs"       [T024, T025]
Task: "US4 — Transport (ArchivistApplication,    [T026, T027, T028, T029]
       ArchivistProperties, application.properties)"
```

---

## Implementation Strategy

### MVP (US1 Only — verify Clean Architecture foundation)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: US1 (Domain layer)
4. **STOP and VALIDATE**: `./gradlew :domain:build` + dependency check
5. Domain layer is the architectural heart — validate it before building outward

### Full Scaffold (All Stories)

1. Phase 1 → Phase 2 → Phase 3 (US1) → validate
2. Phase 4 (US2) + Phase 5 (US3) in parallel → validate each independently
3. Phase 6 (US4) after US3 complete → validate STDIO boot
4. Phase 7: Polish + full quickstart verification

---

## Notes

- `[P]` = different files, no intra-phase dependencies — safe to parallelize
- `[USn]` maps each task to its user story for traceability to spec acceptance criteria
- Every source file must use qualified imports (no `import io.archivist.domain.model.*`)
- Every source file must use constructor injection (no `@Autowired` on fields)
- `application.properties` values must use `${ENV_VAR}` syntax — no literals for external config
- Commit after each phase checkpoint to maintain a clean git history
