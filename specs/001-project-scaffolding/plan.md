# Implementation Plan: Project Scaffolding

**Branch**: `001-project-scaffolding` | **Date**: 2026-07-11 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `specs/001-project-scaffolding/spec.md`

---

## Summary

Establish the Gradle multi-module project structure for Archivist: four modules (`domain`, `application`, `infrastructure`, `transport`) wired with the correct Clean Architecture dependency rules, a Gradle version catalogue for centralised dependency management, placeholder source files confirming each module's package hierarchy compiles, and a runnable Spring Boot STDIO MCP server entry point in the `transport` module. No domain capabilities are implemented; this is the structural foundation all future specs depend on.

**Technology stack** (see [research.md](research.md) for full rationale):

- Spring Boot 4.1.0 + Spring Framework 7.0.8
- Spring AI 2.0.0 (MCP Java SDK 2.0.0, MCP spec 2025-11-25)
- Java 21, Gradle (Kotlin DSL), `gradle/libs.versions.toml` version catalogue
- STDIO MCP transport (Streamable HTTP addable without architectural changes)

---

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.0, Spring AI 2.0.0, `spring-ai-starter-mcp-server` (STDIO)

**Build**: Gradle (Kotlin DSL) exclusively — version catalogue at `gradle/libs.versions.toml`

**Storage**: Not applicable to this scaffold

**Testing**: JUnit 5 (via Spring Boot BOM); domain and application tests plain JUnit — no Spring context; infrastructure tests may use `@SpringBootTest`

**Target Platform**: JVM / local process (STDIO MCP server)

**Project Type**: MCP server (domain-driven retrieval service)

**Performance Goals**: Not applicable to scaffold — defined per capability spec

**Constraints**:

- Domain layer MUST compile with zero Spring or MCP imports
- Application layer MUST compile with zero Spring or MCP imports
- No star imports anywhere
- No field injection anywhere
- All environment-specific values (file paths, URLs, credentials) MUST be supplied via environment variables — never hardcoded in source or property files
- A single `ArchivistProperties` `@ConfigurationProperties` class in `transport.config` is the sole entry point for all injected configuration; configuration binding is a Spring Boot wiring concern that belongs in `transport`, not `infrastructure`
- Infrastructure adapters receive plain values (`String`, `Path`) via constructor injection — they never import `ArchivistProperties` or read env vars directly
- Missing required environment variables MUST cause a clear startup failure (fail-fast via `@Validated`)

**Scale/Scope**: Personal knowledge system (Second Brain); single-user

---

## Constitution Check

_GATE: Evaluated before implementation begins. Re-check after implementation before merge._

| Gate                    | Principle                            | Check                                                                                                 | Status                                                                      |
| ----------------------- | ------------------------------------ | ----------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| Domain independence     | I. Clean Architecture                | No `domain.*` or `application.*` class imports `org.springframework.*` or `io.modelcontextprotocol.*` | ✅ PASS — enforced by module-level dependency declarations                  |
| Contract language       | II. Domain-Driven Public Contract    | All public capability names use domain terms                                                          | ✅ PASS — scaffold introduces no capabilities                               |
| No reasoning in output  | III. Retrieval, Never Reasoning      | All capabilities return `List<Evidence>`                                                              | ✅ PASS — scaffold introduces no capabilities                               |
| Provenance completeness | III. Retrieval, Never Reasoning      | Every `Evidence` includes full `Provenance`                                                           | ✅ PASS — scaffold introduces no capabilities; types noted in data-model.md |
| Spec approved           | IV. Specification-Driven Development | Approved specification exists before implementation                                                   | ⚠️ PENDING — spec requires approval before implementation starts            |
| Strategy hidden         | V. Replaceable Infrastructure        | All retrieval implementations behind `domain.port.out`                                                | ✅ PASS — scaffold introduces no retrieval                                  |
| No Obsidian coupling    | V. Replaceable Infrastructure        | No code references Obsidian paths, wikilinks, or frontmatter                                          | ✅ PASS — scaffold introduces no Second Brain coupling                      |
| Build tool              | Technology Constraints               | Gradle (Kotlin DSL) exclusively; no Maven                                                             | ✅ PASS — Maven files must not exist                                        |
| Injection style         | Technology Constraints               | Constructor injection everywhere; no `@Autowired` on fields                                           | ✅ PASS — enforced in placeholder sources                                   |

**Gate result**: All gates pass except "Spec approved" which is procedurally pending human review. No Complexity Tracking entries required.

---

## Project Structure

### Documentation (this feature)

```text
specs/001-project-scaffolding/
├── spec.md              # Feature specification
├── plan.md              # This file
├── research.md          # Version and technology decisions
├── data-model.md        # Module layout and domain type summaries
├── quickstart.md        # Verification guide
├── contracts/
│   └── mcp-server.md    # MCP tool contract (scaffold state + intended final contract)
├── checklists/
│   └── requirements.md  # Specification quality checklist
└── tasks.md             # Implementation tasks (generated by /speckit-tasks)
```

### Source Code (repository root)

```text
archivist/                                          ← root project
├── gradle/
│   └── libs.versions.toml                          ← version catalogue
├── settings.gradle.kts                             ← includes all four modules
├── build.gradle.kts                                ← shared Java toolchain, plugin management
│
├── domain/
│   ├── build.gradle.kts                            ← no Spring; java-library
│   └── src/
│       ├── main/java/io/archivist/domain/
│       │   ├── model/
│       │   │   ├── Evidence.java                   ← placeholder record
│       │   │   ├── Provenance.java                 ← placeholder record
│       │   │   ├── Query.java                      ← placeholder record
│       │   │   ├── KnowledgeType.java              ← enum
│       │   │   └── KnowledgeZone.java              ← enum
│       │   └── port/
│       │       ├── in/                             ← placeholder capability interfaces
│       │       └── out/
│       │           └── KnowledgeGateway.java       ← placeholder interface
│       └── test/java/io/archivist/domain/
│           └── (placeholder or empty)
│
├── application/
│   ├── build.gradle.kts                            ← depends on :domain; no Spring
│   └── src/
│       ├── main/java/io/archivist/application/
│       │   └── usecase/                            ← placeholder (empty package-info)
│       └── test/java/io/archivist/application/
│           └── (placeholder or empty)
│
├── infrastructure/
│   ├── build.gradle.kts                            ← depends on :domain, :application, spring-boot, spring-ai
│   └── src/
│       ├── main/java/io/archivist/infrastructure/
│       │   ├── retrieval/                          ← placeholder
│       │   └── secondbrain/                        ← placeholder
│       └── test/java/io/archivist/infrastructure/
│           └── (placeholder or empty)
│
└── transport/
    ├── build.gradle.kts                            ← depends on :application, spring-boot, spring-ai-mcp-server
    └── src/
        ├── main/
        │   ├── java/io/archivist/transport/
        │   │   ├── ArchivistApplication.java       ← @SpringBootApplication
        │   │   ├── config/
        │   │   │   └── ArchivistProperties.java    ← @ConfigurationProperties; env var binding + @Validated fail-fast
        │   │   └── mcp/                            ← placeholder
        │   └── resources/
        │       └── application.properties          ← spring.ai.mcp.server.stdio=true etc.
        └── test/java/io/archivist/transport/
            └── (placeholder or empty)
```

**Structure Decision**: Multi-module Gradle project with one module per Clean Architecture layer. Module-level dependency declarations in `build.gradle.kts` files enforce the import rules without requiring ArchUnit (which can be added in a follow-up).

---

## Complexity Tracking

> No Constitution Check violations were found. This section is intentionally empty.
