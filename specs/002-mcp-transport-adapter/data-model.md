# Data Model: MCP Transport Adapter

**Feature**: 002-mcp-transport-adapter
**Date**: 2026-07-12

> This spec introduces no new domain types. This document records the **transport-layer artefacts**, **stub use cases**, and **serialisation contract** for the MCP adapter.

---

## Domain Types Used (unchanged)

Existing types from `io.archivist.domain.model` — no modifications:

| Type            | Role in transport               |
| --------------- | ------------------------------- |
| `Evidence`      | MCP tool return payload element |
| `Provenance`    | Nested in serialised evidence   |
| `KnowledgeType` | Serialised as enum name string  |
| `KnowledgeZone` | Serialised as enum name string  |

---

## Application Layer: Stub Use Cases

Eight plain Java classes in `io.archivist.application.usecase`. Each implements one `port.in` interface. No Spring annotations. No `port.out` dependencies.

| Class                         | Implements             | Method                               | Stub behaviour |
| ----------------------------- | ---------------------- | ------------------------------------ | -------------- |
| `RetrieveContextUseCase`      | `RetrieveContext`      | `retrieveContext(String query)`      | `List.of()`    |
| `FindDecisionsUseCase`        | `FindDecisions`        | `findDecisions(String topic)`        | `List.of()`    |
| `FindProjectsUseCase`         | `FindProjects`         | `findProjects(String criteria)`      | `List.of()`    |
| `FindPeopleUseCase`           | `FindPeople`           | `findPeople(String name)`            | `List.of()`    |
| `FindConceptsUseCase`         | `FindConcepts`         | `findConcepts(String topic)`         | `List.of()`    |
| `FindRelatedKnowledgeUseCase` | `FindRelatedKnowledge` | `findRelatedKnowledge(String query)` | `List.of()`    |
| `FindReadingsUseCase`         | `FindReadings`         | `findReadings(String topic)`         | `List.of()`    |
| `FindDebriefsUseCase`         | `FindDebriefs`         | `findDebriefs(String topic)`         | `List.of()`    |

---

## Transport Layer: New Classes

### `ArchivistMcpTools` (`@Component`)

Single MCP tool registrar. Eight `@McpTool` methods — one per domain capability. Constructor-injected `port.in` dependencies.

| MCP tool               | Injected dependency    | Parameter  | Validation                          |
| ---------------------- | ---------------------- | ---------- | ----------------------------------- |
| `retrieveContext`      | `RetrieveContext`      | `query`    | `McpInputValidator.requireNonBlank` |
| `findDecisions`        | `FindDecisions`        | `topic`    | same                                |
| `findProjects`         | `FindProjects`         | `criteria` | same                                |
| `findPeople`           | `FindPeople`           | `name`     | same                                |
| `findConcepts`         | `FindConcepts`         | `topic`    | same                                |
| `findRelatedKnowledge` | `FindRelatedKnowledge` | `query`    | same                                |
| `findReadings`         | `FindReadings`         | `topic`    | same                                |
| `findDebriefs`         | `FindDebriefs`         | `topic`    | same                                |

Each method: validate → delegate to `port.in` → serialise via injected `EvidenceJsonMapper` → return JSON string.

### `EvidenceJsonMapper`

Transport utility and **sole serialisation path** for MCP tool responses. Converts `List<Evidence>` to JSON string. Used by `ArchivistMcpTools` handlers and all contract/behaviour tests. Uses injected `ObjectMapper`. Canonical sample used in contract regression test matches [evidence-response-schema-expected.json](contracts/evidence-response-schema-expected.json).

### `McpInputValidator`

Static utility (no Spring dependency):

```
requireNonBlank(String value, String paramName) → void
```

Throws `IllegalArgumentException` with message identifying the parameter when value is null, empty, or whitespace-only.

### `StubUseCaseConfiguration` (`@Configuration`)

Registers eight stub use case `@Bean` methods, each returning the `port.in` interface type. Lives in `io.archivist.transport.config`.

---

## MCP Tool → Domain Mapping

```
┌─────────────────────────────────────────────────────────────┐
│                     MCP Client (external)                    │
└──────────────────────────┬──────────────────────────────────┘
                           │ STDIO protocol
┌──────────────────────────▼──────────────────────────────────┐
│  transport.mcp.ArchivistMcpTools (@McpTool methods)          │
│    → McpInputValidator                                       │
│    → port.in delegate                                        │
│    → EvidenceJsonMapper (sole serialisation path)            │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  application.usecase.*UseCase (stub → List.of())             │
└─────────────────────────────────────────────────────────────┘
```

No `infrastructure` or `port.out` involvement in this feature.

---

## Serialisation Rules

| Field                 | JSON representation                  |
| --------------------- | ------------------------------------ |
| `Evidence.content`    | string                               |
| `Provenance.sourceId` | string                               |
| `Provenance.title`    | string                               |
| `Provenance.type`     | enum name (`CONCEPT`, `DECISION`, …) |
| `Provenance.zone`     | enum name (`SOURCE`, `TECHNICAL`, …) |
| `Provenance.tags`     | string array                         |
| `Provenance.sources`  | string array                         |
| `Provenance.created`  | ISO-8601 UTC (`Instant.toString()`)  |
| `Provenance.updated`  | ISO-8601 UTC                         |

Tool return value: JSON array (possibly empty `[]` from stubs).

---

## Test Artefacts

### Production source layout (new files)

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
│   └── StubUseCaseConfiguration.java
└── mcp/
    ├── ArchivistMcpTools.java
    ├── EvidenceJsonMapper.java
    └── McpInputValidator.java

transport/src/test/java/io/archivist/transport/
├── mcp/
│   ├── ArchivistMcpToolsTest.java
│   ├── EvidenceJsonMapperTest.java
│   ├── McpInputValidatorTest.java
│   └── McpContractRegressionTest.java
└── support/
    └── McpAdapterTestConfiguration.java
```

### Contract fixtures

```text
specs/002-mcp-transport-adapter/contracts/
├── README.md
├── mcp-tools-expected.json
├── mcp-server-identity-expected.json
└── evidence-response-schema-expected.json
```

---

## Module Dependency Impact

| Module           | Change                                                                      |
| ---------------- | --------------------------------------------------------------------------- |
| `domain`         | None                                                                        |
| `application`    | +8 stub use case classes                                                    |
| `infrastructure` | None                                                                        |
| `transport`      | +MCP adapter classes, +test suite, +`spring-boot-starter-test` (test scope) |

Transport compile classpath: unchanged (`:application` only — no `:infrastructure`).
