# Data Model: Project Scaffolding

**Feature**: 001-project-scaffolding
**Date**: 2026-07-11

> This spec introduces no new domain types. This document records the **intended package homes** for future domain types and the **module dependency graph** as structural artefacts of the scaffold.

---

## Module Dependency Graph

```
transport
    └── depends on → application
                         └── depends on → domain
infrastructure
    ├── depends on → domain
    └── depends on → application
transport
    ├── depends on → application
    └── depends NOT depend on → infrastructure (dependency inversion via port.out)
```

Visualised:

```
              ┌────────────┐
              │   domain   │  (no external deps)
              └─────┬──────┘
                    │ ↑ imports allowed
          ┌─────────┴──────────┐
          │    application     │  (imports domain only)
          └────────┬───────────┘
                   │ ↑ imports allowed
     ┌─────────────┴──────────────────┐
     │                                │
┌────┴──────────┐         ┌───────────┴────────┐
│ infrastructure│         │    transport        │
│ (Spring Boot, │         │ (Spring Boot, MCP)  │
│  Spring AI)   │         │                     │
└───────────────┘         └────────────────────┘
     infrastructure does NOT import transport
     transport does NOT import infrastructure directly
```

---

## Module Source Layout

### `domain` module

```
domain/src/main/java/io/archivist/domain/
├── model/
│   ├── Evidence.java          # record — retrieved knowledge unit
│   ├── Provenance.java        # record — full traceability metadata
│   ├── Query.java             # record — domain query (not a raw search string)
│   ├── KnowledgeType.java     # enum — semantic type of a knowledge entry
│   └── KnowledgeZone.java     # enum — epistemic zone of a knowledge entry
├── port/
│   ├── in/
│   │   ├── RetrieveContext.java
│   │   ├── FindDecisions.java
│   │   ├── FindProjects.java
│   │   ├── FindPeople.java
│   │   ├── FindConcepts.java
│   │   ├── FindRelatedKnowledge.java
│   │   ├── FindReadings.java
│   │   └── FindDebriefs.java
│   └── out/
│       └── KnowledgeGateway.java
└── service/                   # (reserved for pure domain logic if needed)
```

> **Scaffold deliverable**: placeholder source files confirming the package compiles. Full type implementations follow in domain capability specs.

### `application` module

```
application/src/main/java/io/archivist/application/
└── usecase/
    └── (placeholder — use cases added per capability spec)
```

### `infrastructure` module

```
infrastructure/src/main/java/io/archivist/infrastructure/
├── retrieval/
│   └── (placeholder — retrieval strategies added per capability spec)
└── secondbrain/
    └── (placeholder — Second Brain adapter added per capability spec)
```

### `transport` module

```
transport/src/main/java/io/archivist/transport/
├── ArchivistApplication.java       # @SpringBootApplication entry point
├── config/
│   └── ArchivistProperties.java   # @ConfigurationProperties + @Validated — env var binding
└── mcp/
    └── (placeholder — MCP tool registrations added per capability spec)
```

---

## Domain Type Summaries (placeholder contract for future specs)

### `Evidence` (record)

| Field        | Type         | Description                  |
| ------------ | ------------ | ---------------------------- |
| `content`    | `String`     | The retrieved knowledge text |
| `provenance` | `Provenance` | Full traceability metadata   |

### `Provenance` (record)

| Field      | Type            | Description                               |
| ---------- | --------------- | ----------------------------------------- |
| `sourceId` | `String`        | Stable identifier for the knowledge entry |
| `title`    | `String`        | Human-readable title                      |
| `type`     | `KnowledgeType` | Semantic type of the entry                |
| `zone`     | `KnowledgeZone` | Epistemic zone                            |
| `tags`     | `List<String>`  | Semantic tags                             |
| `sources`  | `List<String>`  | Provenance chain to raw source material   |
| `created`  | `Instant`       | Creation timestamp                        |
| `updated`  | `Instant`       | Last modification timestamp               |

### `Query` (record)

| Field  | Type     | Description                   |
| ------ | -------- | ----------------------------- |
| `text` | `String` | The domain-level query string |

### `KnowledgeType` (enum)

| Constant    | Meaning                                                |
| ----------- | ------------------------------------------------------ |
| `CONCEPT`   | Synthesised technical or professional understanding    |
| `ENTITY`    | Named thing: organisation, technology, tool, framework |
| `PERSON`    | Named individual with professional context             |
| `PROJECT`   | Active or past project or initiative                   |
| `DECISION`  | Architecture or product decision (ADR)                 |
| `DEBRIEF`   | Incident retrospective or learning review              |
| `SYNTHESIS` | Cross-cutting insight connecting multiple concepts     |
| `READING`   | Source material: article, transcript, book note        |

### `KnowledgeZone` (enum)

| Constant      | Epistemic Role                                           |
| ------------- | -------------------------------------------------------- |
| `SOURCE`      | Immutable source material                                |
| `SYNTHESIZED` | LLM-maintained concepts and entities                     |
| `TECHNICAL`   | Architecture decisions, debriefs, technical project docs |
| `IDENTITY`    | Personal principles, professional thesis                 |
| `COMPILED`    | Pre-assembled operational context snapshot               |
| `SIGNAL`      | Weekly signal extraction artifacts                       |

### `KnowledgeGateway` (port.out interface)

```java
// Placeholder — full signature defined in capability specs
public interface KnowledgeGateway {
    List<Evidence> retrieve(Query query);
}
```

---

## Gradle Module Configuration

### `settings.gradle.kts`

```kotlin
rootProject.name = "archivist"
include("domain", "application", "infrastructure", "transport")
```

### Root `build.gradle.kts` responsibilities

- Apply `java` or `java-library` plugin to all subprojects
- Set Java 21 toolchain across all modules
- Import Spring Boot BOM via `platform(libs.spring.boot.bom)` in modules that need it
- Register Gradle wrapper

---

## Configuration Class

### `ArchivistProperties` (`@ConfigurationProperties`)

Lives in `io.archivist.transport.config`. Configuration binding is a Spring Boot wiring concern that belongs at the application entry point, alongside `ArchivistApplication` — not in the retrieval infrastructure layer.

Infrastructure adapters receive plain values (`String`, `java.nio.file.Path`) via constructor injection. They never import `ArchivistProperties` or call `System.getenv()` directly.

| Property path                 | Environment variable          | Type     | Required | Description                                                  |
| ----------------------------- | ----------------------------- | -------- | -------- | ------------------------------------------------------------ |
| `archivist.second-brain.path` | `ARCHIVIST_SECOND_BRAIN_PATH` | `String` | Yes      | Absolute path to an **existing** Second Brain root directory |

Additional properties are added to this class as each capability spec introduces new external dependencies. The class is annotated `@Validated` with `@NotBlank`/`@NotNull` on every required field. Validation error messages MUST include the property path, environment variable name, and failure reason (AC-14). Path values MUST be validated for existence via `@AssertTrue` (AC-15). Missing, empty, whitespace-only, or non-existent paths must prevent the service from starting.

---

### `gradle/libs.versions.toml` key entries

| Alias                  | Coordinate                                            | Version             |
| ---------------------- | ----------------------------------------------------- | ------------------- |
| `spring-boot`          | `org.springframework.boot:spring-boot-starter`        | `4.1.0`             |
| `spring-boot-bom`      | `org.springframework.boot:spring-boot-dependencies`   | `4.1.0`             |
| `spring-ai-bom`        | `org.springframework.ai:spring-ai-bom`                | `2.0.0`             |
| `spring-ai-mcp-server` | `org.springframework.ai:spring-ai-starter-mcp-server` | via BOM             |
| `junit-jupiter`        | `org.junit.jupiter:junit-jupiter`                     | via Spring Boot BOM |
| `mockito-core`         | `org.mockito:mockito-core`                            | via Spring Boot BOM |
