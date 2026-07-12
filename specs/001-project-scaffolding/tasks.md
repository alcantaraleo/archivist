# Tasks: Project Scaffolding

**Input**: Design documents from `specs/001-project-scaffolding/`

**Prerequisites**: plan.md ✅ | spec.md ✅ | research.md ✅ | data-model.md ✅ | contracts/ ✅ | quickstart.md ✅

**Stack**: Java 21 · Spring Boot 4.1.0 · Spring AI 2.0.0 · Gradle (Kotlin DSL) · `libs.versions.toml`

**Tests**: Domain module only — one plain JUnit 5 smoke test (AC-16). No tests in other modules. Acceptance verified via `./gradlew build` and static analysis per `quickstart.md`.

**User story mapping** (acceptance criteria → deliverable phases):

| Story | Deliverable                                                  | Acceptance Criteria     |
| ----- | ------------------------------------------------------------ | ----------------------- |
| US1   | Gradle multi-module project compiles                         | AC 1, 2, 3, 16          |
| US2   | Domain layer skeleton — all placeholder types and interfaces | AC 3, 4, 10, 11, 16     |
| US3   | Infrastructure layer skeleton                                | AC 6                    |
| US4   | Transport layer boots as STDIO MCP server + config contract  | AC 7, 8, 12, 13, 14, 15 |

---

## Phase 1: Setup (Gradle Build Infrastructure)

**Purpose**: Initialize the Gradle project — wrapper, version catalogue, root settings, root build file. No Java source yet.

- [x] T001 Initialize Gradle wrapper at repo root: `gradle wrapper --gradle-version latest` — pins the Gradle version in `gradle-wrapper.properties` at generation time; wrapper task generates `distributionSha256Sum` checksum automatically
- [x] T002 [P] Create `gradle/libs.versions.toml` — declare `spring-boot = "4.1.0"`, `spring-ai = "2.0.0"`, `junit-jupiter` (via BOM), `mockito-core` (via BOM) version aliases
- [x] T003 Create `settings.gradle.kts` — set `rootProject.name = "archivist"` and include all four modules: `domain`, `application`, `infrastructure`, `transport`
- [x] T004 Create root `build.gradle.kts` — configure `java` toolchain to Java 21 for all subprojects via `subprojects {}` block; register Spring Boot and Spring AI BOMs in `dependencyManagement`; apply `java-library` plugin baseline

**Checkpoint**: `./gradlew projects` lists all four modules.

---

## Phase 2: Foundational (Module Dependency Graph)

**Purpose**: Declare the inter-module dependency rules. These `build.gradle.kts` files are the enforcement mechanism for Clean Architecture — the compiler will reject illegal imports.

⚠️ **CRITICAL**: No Java source can compile in any module until this phase is complete.

- [x] T005 Create `domain/build.gradle.kts` — apply `java-library`; declare **zero** compile-time dependencies on Spring, Spring AI, MCP SDK, or any sibling module
- [x] T006 [P] [US2] Create `application/build.gradle.kts` — apply `java-library`; declare single compile dependency: `api(project(":domain"))`; no Spring, MCP SDK
- [x] T007 [P] [US3] Create `infrastructure/build.gradle.kts` — apply `java-library`; declare `api(project(":domain"))`, `api(project(":application"))`; import Spring Boot BOM; add `spring-boot-starter`, `spring-ai-starter` compile dependencies; **no** `:transport` dependency
- [x] T008 [P] [US4] Create `transport/build.gradle.kts` — apply `org.springframework.boot` plugin (for `bootJar`/`bootRun`); declare `implementation(project(":application"))`; add `spring-boot-starter`, `spring-ai-starter-mcp-server` (STDIO); **no** `:infrastructure` compile dependency

**Checkpoint**: `./gradlew :domain:dependencies --configuration compileClasspath` shows no Spring entries. `./gradlew :transport:dependencies --configuration compileClasspath` shows no `:infrastructure` entries (AC-7).

---

## Phase 3: User Story 1 — Domain Layer Skeleton (Priority: P1) 🎯 MVP

**Goal**: The `domain` module contains all placeholder types and port interfaces, compiles cleanly, and has zero Spring or MCP imports.

**Independent Test**: `./gradlew :domain:build` succeeds; `./gradlew :domain:dependencies` contains no `org.springframework` entries.

### Implementation

- [x] T009 [P] [US1] Create `domain/src/main/java/io/archivist/domain/model/KnowledgeType.java` — enum with constants: `CONCEPT`, `ENTITY`, `PERSON`, `PROJECT`, `DECISION`, `DEBRIEF`, `SYNTHESIS`, `READING`
- [x] T010 [P] [US1] Create `domain/src/main/java/io/archivist/domain/model/KnowledgeZone.java` — enum with constants: `SOURCE`, `SYNTHESIZED`, `TECHNICAL`, `IDENTITY`, `COMPILED`, `SIGNAL`
- [x] T011 [P] [US1] Create `domain/src/main/java/io/archivist/domain/model/Query.java` — record with single field: `String text`
- [x] T012 [P] [US1] Create `domain/src/main/java/io/archivist/domain/model/Provenance.java` — record with fields: `String sourceId`, `String title`, `KnowledgeType type`, `KnowledgeZone zone`, `List<String> tags`, `List<String> sources`, `Instant created`, `Instant updated`
- [x] T013 [US1] Create `domain/src/main/java/io/archivist/domain/model/Evidence.java` — record with fields: `String content`, `Provenance provenance` (depends on T012)
- [x] T014 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/RetrieveContext.java` — interface: `List<Evidence> retrieveContext(String query)`
- [x] T015 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindDecisions.java` — interface: `List<Evidence> findDecisions(String topic)`
- [x] T016 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindProjects.java` — interface: `List<Evidence> findProjects(String criteria)`
- [x] T017 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindPeople.java` — interface: `List<Evidence> findPeople(String name)`
- [x] T018 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindConcepts.java` — interface: `List<Evidence> findConcepts(String topic)`
- [x] T019 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindRelatedKnowledge.java` — interface: `List<Evidence> findRelatedKnowledge(String query)`
- [x] T020 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindReadings.java` — interface: `List<Evidence> findReadings(String topic)`
- [x] T021 [P] [US1] Create `domain/src/main/java/io/archivist/domain/port/in/FindDebriefs.java` — interface: `List<Evidence> findDebriefs(String topic)`
- [x] T022 [US1] Create `domain/src/main/java/io/archivist/domain/port/out/KnowledgeGateway.java` — interface: `List<Evidence> retrieve(Query query)` (depends on T013)
- [x] T023 [US1] Create `domain/src/test/java/io/archivist/domain/DomainModuleIsolationTest.java` — JUnit 5 smoke test (e.g. assert `KnowledgeType` enum values); zero Spring or MCP imports; `./gradlew :domain:test` must succeed with no `org.springframework` on test compile classpath (AC-16)

**Checkpoint**: `./gradlew :domain:build` and `./gradlew :domain:test` succeed. Domain module has all model types, all 8 port.in interfaces, the port.out gateway, and a plain JUnit test. Zero Spring imports.

---

## Phase 4: User Story 2 — Application Layer Skeleton (Priority: P2)

**Goal**: The `application` module establishes its package hierarchy, depends only on `:domain`, and compiles cleanly with no Spring or MCP imports.

**Independent Test**: `./gradlew :application:build` succeeds; `./gradlew :application:dependencies --configuration compileClasspath` shows only `:domain` as a compile dependency.

### Implementation

- [x] T024 [US2] Create `application/src/main/java/io/archivist/application/usecase/package-info.java` — package declaration only; establishes the package path where use case interactors will live in future specs

**Checkpoint**: `./gradlew :application:build` succeeds. Application module compiles with `:domain` as its sole dependency.

---

## Phase 5: User Story 3 — Infrastructure Layer Skeleton (Priority: P3)

**Goal**: The `infrastructure` module compiles against `:domain` and `:application`. Package stubs are in place for retrieval strategies and the Second Brain adapter.

**Independent Test**: `./gradlew :infrastructure:build` succeeds; `./gradlew :infrastructure:dependencies --configuration compileClasspath` contains no `:transport` entries.

### Implementation

- [x] T025 [P] [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/retrieval/package-info.java` — package declaration only; reserves package for future retrieval strategy implementations
- [x] T026 [P] [US3] Create `infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/package-info.java` — package declaration only; reserves package for future Second Brain adapter

**Checkpoint**: `./gradlew :infrastructure:build` succeeds. Infrastructure module compiles cleanly with no transport dependency.

---

## Phase 6: User Story 4 — Transport Layer Boots as STDIO MCP Server (Priority: P4)

**Goal**: The `transport` module starts a Spring Boot application with the STDIO MCP server configured and the external configuration contract enforced. `ArchivistProperties` lives in `transport.config` — configuration binding is a Spring Boot wiring concern, not infrastructure logic. The service starts when all required environment variables are present and valid, fails fast with descriptive errors when they are absent, and routes all logging to stderr.

**Independent Test**: `ARCHIVIST_SECOND_BRAIN_PATH=/tmp ./gradlew :transport:bootRun` prints startup confirmation on stderr and remains running. Starting without `ARCHIVIST_SECOND_BRAIN_PATH` or with a non-existent path produces a startup validation error on stderr, not a running service.

### Implementation

- [x] T027 [US4] Create `transport/src/main/java/io/archivist/transport/ArchivistApplication.java` — `@SpringBootApplication`; `@ConfigurationPropertiesScan` or `@EnableConfigurationProperties(ArchivistProperties.class)`; standard `main(String[] args)` entry point; no field injection
- [x] T028 [P] [US4] Create `transport/src/main/java/io/archivist/transport/config/ArchivistProperties.java` — `@ConfigurationProperties(prefix = "archivist")` and `@Validated`; nested `SecondBrain` record with `@NotBlank(message = "...")` on `path` (include property path and env var name in message); `@AssertTrue` method validating `Files.isDirectory(Paths.get(path))` with descriptive message on failure (AC-14, AC-15); bound to `archivist.second-brain.path` (env: `ARCHIVIST_SECOND_BRAIN_PATH`)
- [x] T029 [P] [US4] Create `transport/src/main/java/io/archivist/transport/mcp/package-info.java` — package declaration only; reserves package for future `@McpTool`-annotated tool registrations
- [x] T030 [US4] Create `transport/src/main/resources/application.properties` — set `spring.application.name=archivist`; `spring.ai.mcp.server.name=archivist`; `spring.ai.mcp.server.version=0.1.0`; `spring.ai.mcp.server.stdio=true`; `archivist.second-brain.path=${ARCHIVIST_SECOND_BRAIN_PATH}` (no default — absence must trigger startup failure)
- [x] T031 [P] [US4] Create `transport/src/main/resources/logback-spring.xml` — ConsoleAppender with `<target>System.err</target>`; all application logging to stderr; stdout reserved for MCP STDIO protocol (AC-8)

**Checkpoint**: `ARCHIVIST_SECOND_BRAIN_PATH=/tmp ./gradlew :transport:bootRun` prints startup confirmation on stderr. Absent, empty, whitespace-only, or non-existent path values produce descriptive startup failures on stderr.

---

## Phase 7: Polish & Verification

**Purpose**: Code quality enforcement, documentation, and full end-to-end quickstart validation.

- [x] T032 [P] Create `README.md` at repo root — document all required environment variables in a table: variable name, description, example value, required/optional; include minimum `./gradlew build` and run instructions
- [x] T033 [P] Verify no star imports across all Java source: `grep -r "^import .*\*;" --include="*.java" .` must produce no output
- [x] T034 [P] Verify no field injection across all Java source: `grep -r "@Autowired" --include="*.java" .` must produce no output
- [x] T035 [P] Verify no hardcoded paths or addresses in source or properties: `grep -rnE '/home/|/Users/|/var/|localhost|127\.0\.0\.1|0\.0\.0\.0|jdbc:|mongodb://|redis://|postgres://|mysql://|:5432|:3306|:6379|:8080' --include="*.java" --include="*.properties" .` must produce no matches in Java files (Windows paths excluded)
- [x] T036 Run full build: `./gradlew build` from repo root — `BUILD SUCCESSFUL` required (validates compile integrity and dependency version compatibility — AC-1, AC-9)
- [x] T037 Run full quickstart verification: execute all checks in `specs/001-project-scaffolding/quickstart.md` in order; confirm each passes

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
- T027 depends on T028 (`ArchivistApplication` registers `ArchivistProperties`; properties class must exist first)

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
Task: "US2 — Application skeleton"               [T024]
Task: "US3 — Infrastructure package stubs"       [T025, T026]
Task: "US4 — Transport (ArchivistApplication,    [T027, T028, T029, T030, T031]
       ArchivistProperties, application.properties, logback-spring.xml)"
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
3. Phase 6 (US4) after Phase 2 + US1 complete → validate STDIO boot on stderr
4. Phase 7: Polish + full quickstart verification

---

## Notes

- `[P]` = different files, no intra-phase dependencies — safe to parallelize
- `[USn]` maps each task to its user story for traceability to spec acceptance criteria
- Every source file must use qualified imports (no `import io.archivist.domain.model.*`)
- Every source file must use constructor injection (no `@Autowired` on fields)
- `application.properties` values must use `${ENV_VAR}` syntax — no literals for external config
- Commit after each phase checkpoint to maintain a clean git history
