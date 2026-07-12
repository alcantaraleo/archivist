# Implementation Plan: MCP Transport Adapter

**Branch**: `002-mcp-transport-adapter` | **Date**: 2026-07-12 | **Spec**: [spec.md](spec.md)
**Status:** Approved
**Approved by:** Leonardo Alcantara
**Approved:** 2026-07-12

**Input**: Feature specification from `specs/002-mcp-transport-adapter/spec.md`

**User directive**: Contract regression tests must use Spring-native testing mechanisms with the minimal Spring context necessary. Prefer Spring Boot transitive test utilities already on the classpath (e.g. JSONAssert via `spring-boot-starter-test`); do not add new HTTP testing libraries.

---

## Summary

Implement the MCP transport adapter layer for Archivist: register all eight domain capability tools via Spring AI `@McpTool`, validate inputs at the transport boundary, delegate to stub use cases in the application module, and serialise responses exclusively via `EvidenceJsonMapper`. Add build-time contract regression tests that compare registered tool metadata and evidence JSON shape against checked-in fixtures, failing `./gradlew build` on unintended drift.

No retrieval logic, Second Brain connectivity, or infrastructure changes.

**Technology stack** (unchanged from scaffold — see [research.md](research.md)):

| Dependency  | Version    | Role                                      |
| ----------- | ---------- | ----------------------------------------- |
| Spring Boot | 4.1.0      | Transport runtime, test support           |
| Spring AI   | 2.0.0      | `@McpTool` registration, MCP STDIO server |
| Java        | 21         | All modules                               |
| Gradle      | Kotlin DSL | Build                                     |

---

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.0, Spring AI 2.0.0 (`spring-ai-starter-mcp-server`), `@McpTool` / `@McpToolParam` for declarative tool registration

**Build**: Gradle (Kotlin DSL); version catalogue at `gradle/libs.versions.toml`

**Storage**: Not applicable — stub use cases return empty lists

**Testing**:

| Layer                           | Mechanism                                                      | Spring context                                                              |
| ------------------------------- | -------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `application`                   | No tests required (trivial stubs)                              | None                                                                        |
| `transport` unit                | Plain JUnit 5 — `McpInputValidator`, `EvidenceJsonMapper`      | None                                                                        |
| `transport` adapter integration | `@SpringBootTest(classes = McpAdapterTestConfiguration.class)` | Minimal — MCP tools + stub beans + Spring AI tool callback auto-config only |
| `transport` contract regression | Same minimal context; autowire `List<ToolCallback>`            | Minimal                                                                     |
| Smoke                           | Manual via MCP client against `:transport:bootRun`             | Full app (quickstart only)                                                  |

**New test dependency**: `spring-boot-starter-test` on `:transport` test scope only — no JSONAssert, approvaltests, or other libraries

**JSON comparison**: Spring Boot `ObjectMapper.readTree()` + `JsonNode.equals()` against fixtures in [contracts/](contracts/)

**Target Platform**: JVM / local STDIO MCP server

**Project Type**: MCP server transport adapter (domain-driven retrieval service)

**Performance Goals**: Not applicable — no retrieval; adapter returns empty lists

**Constraints**:

- Transport MUST NOT import `infrastructure.*`
- Application stub use cases MUST NOT import Spring or MCP SDK
- All eight MCP tool names MUST match domain capability names exactly
- Contract fixtures are authoritative for build-time comparison (AC-10, AC-11)
- Invalid parameters rejected at transport boundary before delegation
- MCP tool responses serialised exclusively via `EvidenceJsonMapper` (production and tests share one path)
- stdout reserved for MCP STDIO; logs on stderr (unchanged from scaffold)
- Constructor injection everywhere; no star imports

**Scale/Scope**: Single-user personal knowledge system; eight tools; stub responses only

---

## Constitution Check

_GATE: Evaluated before implementation. Re-check after implementation before merge._

| Gate                    | Principle                            | Check                                            | Pre-design | Post-design                                              |
| ----------------------- | ------------------------------------ | ------------------------------------------------ | ---------- | -------------------------------------------------------- |
| Domain independence     | I. Clean Architecture                | No Spring/MCP in `domain.*` or `application.*`   | ✅ PASS    | ✅ PASS — stubs are plain Java; Spring only in transport |
| Contract language       | II. Domain-Driven Public Contract    | MCP tool names are domain terms                  | ✅ PASS    | ✅ PASS — eight tools match `port.in` names              |
| No reasoning in output  | III. Retrieval, Never Reasoning      | Tools return `List<Evidence>`                    | ✅ PASS    | ✅ PASS — stubs return empty evidence lists              |
| Provenance completeness | III. Retrieval, Never Reasoning      | Serialised shape includes full provenance fields | ✅ PASS    | ✅ PASS — fixture enforces all fields                    |
| Spec approved           | IV. Specification-Driven Development | Approved spec before implementation              | ✅ PASS    | ✅ PASS — spec §9 approved 2026-07-12                    |
| Strategy hidden         | V. Replaceable Infrastructure        | No retrieval in transport                        | ✅ PASS    | ✅ PASS — no `port.out` usage                            |
| No Obsidian coupling    | V. Replaceable Infrastructure        | No storage format coupling                       | ✅ PASS    | ✅ PASS — adapter only                                   |
| Build tool              | Technology Constraints               | Gradle Kotlin DSL                                | ✅ PASS    | ✅ PASS                                                  |
| Injection style         | Technology Constraints               | Constructor injection                            | ✅ PASS    | ✅ PASS                                                  |
| MCP is adapter          | I / V                                | Domain invocable without MCP server              | ✅ PASS    | ✅ PASS — stub use cases testable via plain Java         |
| Stable contract         | II                                   | Contract regression tests enforce stability      | ✅ PASS    | ✅ PASS — fixtures + `McpContractRegressionTest`         |

**Gate result**: All gates pass. Spec and plan approved 2026-07-12.

---

## Project Structure

### Documentation (this feature)

```text
specs/002-mcp-transport-adapter/
├── spec.md
├── plan.md                    # This file
├── research.md                # Technology and testing decisions
├── data-model.md              # Transport classes, stub use cases, serialisation
├── quickstart.md              # Verification guide
├── contracts/
│   ├── README.md
│   ├── mcp-tools-expected.json
│   ├── mcp-server-identity-expected.json
│   └── evidence-response-schema-expected.json
├── checklists/
│   └── requirements.md
└── tasks.md                   # Generated by /speckit-tasks
```

### Source Code (repository root — changes for this feature)

```text
application/src/main/java/io/archivist/application/usecase/
├── RetrieveContextUseCase.java
├── FindDecisionsUseCase.java
├── FindProjectsUseCase.java
├── FindPeopleUseCase.java
├── FindConceptsUseCase.java
├── FindRelatedKnowledgeUseCase.java
├── FindReadingsUseCase.java
└── FindDebriefsUseCase.java

transport/src/main/java/io/archivist/transport/
├── config/
│   └── StubUseCaseConfiguration.java      # NEW — @Bean stub use cases
└── mcp/
    ├── ArchivistMcpTools.java             # NEW — @Component @McpTool registrar
    ├── EvidenceJsonMapper.java            # NEW — JSON serialisation
    └── McpInputValidator.java             # NEW — transport boundary validation

transport/src/test/java/io/archivist/transport/
├── support/
│   └── McpAdapterTestConfiguration.java  # Minimal Spring test context
└── mcp/
    ├── McpContractRegressionTest.java    # Build-time contract gate
    ├── ArchivistMcpToolsTest.java        # Delegation + error behaviour
    ├── EvidenceJsonMapperTest.java       # Serialisation shape (plain JUnit)
    └── McpInputValidatorTest.java        # Validation (plain JUnit)

transport/build.gradle.kts                  # + spring-boot-starter-test (testImplementation)
```

**Unchanged**: `domain/`, `infrastructure/`, `ArchivistApplication.java`, `ArchivistProperties.java`, `application.properties` (server identity already configured).

---

## Implementation Phases

### Phase A: Application stub use cases

Create eight stub classes in `application.usecase`. Each implements one `port.in` interface and returns `List.of()`. Verify `:application` compiles with no new dependencies.

### Phase B: Transport adapter core

1. `McpInputValidator` — static blank check
2. `EvidenceJsonMapper` — `ObjectMapper`-based serialisation of `List<Evidence>`
3. `StubUseCaseConfiguration` — `@Bean` methods wiring stub use cases to `port.in` types
4. `ArchivistMcpTools` — `@Component` with eight `@McpTool` methods:
   - `@McpToolParam(description = "...", required = true)` matching contract parameter names
   - Validate → delegate → serialise via `EvidenceJsonMapper` → return JSON string

Ensure `@EnableConfigurationProperties` or component scan picks up `StubUseCaseConfiguration` from `ArchivistApplication` (add `@Import` if scan does not cover `config` subpackage).

### Phase C: Tests

1. **Unit** (no Spring): `McpInputValidatorTest`, `EvidenceJsonMapperTest` with manually constructed `ObjectMapper`
2. **Minimal integration**: `McpAdapterTestConfiguration` — explicit `@Import` of stub config, `ArchivistMcpTools`, `EvidenceJsonMapper`, Spring AI MCP auto-configuration; test properties disable STDIO if supported
3. **`McpContractRegressionTest`**:
   - Load fixtures from `specs/002-mcp-transport-adapter/contracts/`
   - Autowire `List<ToolCallback>`; assert count == 8
   - For each tool: compare `getToolDefinition()` name/description/inputSchema to fixture via `JsonNode.equals()`
   - Assert server identity from test properties matches `mcp-server-identity-expected.json`
   - Serialize canonical sample evidence via `EvidenceJsonMapper`; compare to `evidence-response-schema-expected.json`
4. **`ArchivistMcpToolsTest`**: `@MockitoBean` one `port.in` at a time; verify call on valid input and `[]` JSON response; parameterized invalid-input cases for all eight tools (assert `IllegalArgumentException` with parameter name, mock never called); mock returning sample evidence → response matches fixture via same `EvidenceJsonMapper` path; mock throwing → MCP tool error surfaces

Add to `transport/build.gradle.kts`:

```kotlin
dependencies {
    testImplementation(libs.spring.boot.starter.test)  // add alias to libs.versions.toml
}
```

### Phase D: Verification

Run [quickstart.md](quickstart.md) steps. Confirm `./gradlew build` passes and contract drift negative test fails as expected.

---

## Complexity Tracking

> No constitution violations. No entries required.

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --------- | ---------- | ------------------------------------ |
| —         | —          | —                                    |

---

## Phase 0 Output

See [research.md](research.md) — all technical decisions resolved.

## Phase 1 Output

- [data-model.md](data-model.md) — transport artefacts and module layout
- [contracts/](contracts/) — JSON fixtures for build-time regression
- [quickstart.md](quickstart.md) — verification guide

## Next Step

Artifacts complete (`tasks.md` generated). Proceed to **`/speckit-implement`** (or `/speckit-taskstoissues` for GitHub tracking first).
