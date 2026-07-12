# SPEC: MCP Transport Adapter

**Status:** Approved
**Feature Branch**: `002-mcp-transport-adapter`
**Created:** 2026-07-12
**Author:** Leonardo Alcantara
**Issue:** #48

---

## Clarifications

### Session 2026-07-12

- Q: Should this feature include build-time contract regression tests to prevent unintended MCP contract changes? → A: Add build-time contract regression tests comparing registered MCP contracts to checked-in expected fixtures.
- Q: Should MCP tool handlers and contract tests share one serialisation path? → A: Yes — `@McpTool` methods return JSON from `EvidenceJsonMapper` only; contract tests assert the same mapper output against fixtures.

---

## 1. Motivation

Phase 1 of the Archivist roadmap requires an MCP transport adapter so external LLM agents can invoke Archivist's domain capabilities over the Model Context Protocol. The project scaffold (001) delivers a runnable Spring Boot application with zero registered MCP tools. Domain capability interfaces (`port.in`) and domain model types already exist, but agents currently have no protocol surface to call them.

This specification defines the **transport adapter layer only**: registering the full set of domain-aligned MCP tools, validating inputs at the protocol boundary, delegating to application-layer entry points, and serialising `Evidence` results. It deliberately excludes retrieval logic, Second Brain connectivity, and real use case behaviour — those follow in separate specifications (lexical retrieval, Second Brain integration).

Without this adapter, agents cannot discover or invoke Archivist capabilities. With it, the stable MCP contract documented in `specs/001-project-scaffolding/contracts/mcp-server.md` becomes callable, returning well-formed (initially empty) responses that consuming agents can integrate against while retrieval implementations are built in parallel.

---

## 2. Responsibilities

### What this specification delivers

- Registration of all eight domain capability MCP tools, 1:1 with `domain.port.in` interfaces:
  - `retrieveContext`
  - `findDecisions`
  - `findProjects`
  - `findPeople`
  - `findConcepts`
  - `findRelatedKnowledge`
  - `findReadings`
  - `findDebriefs`
- MCP tool definitions matching the input schemas and descriptions in `specs/001-project-scaffolding/contracts/mcp-server.md`
- Transport-layer delegation from each MCP tool handler to the corresponding application use case (via constructor-injected `port.in` interface)
- Serialisation of `List<Evidence>` to the JSON response shape defined in the MCP server contract (content + provenance with `KnowledgeType` and `KnowledgeZone` as string enum values, ISO-8601 timestamps) via a single transport mapper (`EvidenceJsonMapper`) used by both MCP tool handlers and contract tests — no parallel Jackson serialisation path
- Transport-boundary validation: null, empty, or whitespace-only required parameters return an MCP tool error (not an empty evidence list)
- Minimal **stub use case implementations** in the `application` module — one per capability — that return an empty list without performing retrieval. These exist solely to satisfy dependency injection and allow end-to-end adapter invocation; they carry no business logic
- Transport-layer tests verifying tool registration, parameter validation, delegation wiring, and response serialisation shape
- **Build-time MCP contract regression tests** that compare the registered tool catalogue (names, descriptions, input schemas, server identity) and serialised `Evidence` response shape against checked-in expected fixtures derived from `specs/001-project-scaffolding/contracts/mcp-server.md`; unintended drift fails `./gradlew build`
- Server identity metadata (`name: archivist`, `version: 0.1.0`) consistent with the documented MCP contract

### What this specification does not do

- Does not implement retrieval strategies (lexical, BM25, hybrid, graph)
- Does not connect to Second Brain or read knowledge files
- Does not implement `KnowledgeGateway` or any `port.out` adapter
- Does not add real orchestration logic to use cases beyond returning an empty list
- Does not summarise, interpret, or reason about retrieved content
- Does not expose retrieval primitives (`grep`, `readFile`, `vectorSearch`, etc.) as MCP tools
- Does not introduce new domain capabilities beyond the eight already defined in `port.in`
- Does not change existing `port.in` interface signatures

---

## 3. Public Contract

This specification does not introduce a new domain capability. It exposes the existing eight `port.in` capabilities over MCP.

### MCP server identity

```
name:    archivist
version: 0.1.0
transport: STDIO (initial)
```

### Tool catalogue

Each tool maps 1:1 to a `domain.port.in` interface method. Parameter names and types MUST match the domain contract.

| MCP tool name          | Delegates to                                | Required parameter |
| ---------------------- | ------------------------------------------- | ------------------ |
| `retrieveContext`      | `RetrieveContext.retrieveContext`           | `query`            |
| `findDecisions`        | `FindDecisions.findDecisions`               | `topic`            |
| `findProjects`         | `FindProjects.findProjects`                 | `criteria`         |
| `findPeople`           | `FindPeople.findPeople`                     | `name`             |
| `findConcepts`         | `FindConcepts.findConcepts`                 | `topic`            |
| `findRelatedKnowledge` | `FindRelatedKnowledge.findRelatedKnowledge` | `query`            |
| `findReadings`         | `FindReadings.findReadings`                 | `topic`            |
| `findDebriefs`         | `FindDebriefs.findDebriefs`                 | `topic`            |

Full JSON Schema definitions for each tool's `inputSchema` are recorded in `specs/001-project-scaffolding/contracts/mcp-server.md` and MUST be reproduced verbatim in tool registration.

### Response shape

All tools return a JSON array of `Evidence` objects:

```json
{
  "content": "string",
  "provenance": {
    "sourceId": "string",
    "title": "string",
    "type": "CONCEPT | ENTITY | PERSON | PROJECT | DECISION | DEBRIEF | SYNTHESIS | READING",
    "zone": "SOURCE | SYNTHESIZED | TECHNICAL | IDENTITY | COMPILED | SIGNAL",
    "tags": ["string"],
    "sources": ["string"],
    "created": "ISO-8601 timestamp",
    "updated": "ISO-8601 timestamp"
  }
}
```

With stub use cases, successful invocations return `[]` until retrieval implementations replace the stubs.

### Error conditions

| Condition                                          | Behaviour                                                           |
| -------------------------------------------------- | ------------------------------------------------------------------- |
| Required parameter null, empty, or whitespace-only | MCP tool error with descriptive message (parameter name identified) |
| No matching knowledge found                        | Empty array `[]` — not an error (future retrieval behaviour)        |
| Stub use case invoked successfully                 | Empty array `[]`                                                    |
| Unexpected failure from delegated call             | MCP tool error with the underlying exception message                |

Transport-layer validation MUST occur before delegation. Stub use cases MUST NOT re-validate parameters already rejected at the transport boundary.

**Serialisation rule**: Every MCP tool handler MUST serialise its response exclusively through `EvidenceJsonMapper`. Handlers delegate to `port.in`, receive `List<Evidence>`, then return the mapper's JSON string. Spring AI/Jackson MUST NOT serialise `List<Evidence>` directly on the tool return path — this keeps production output and fixture-based contract tests on one code path.

### Contract stability enforcement

The MCP public contract is a stability boundary (Constitution Principle II). Contract regression tests MUST run as part of the standard build (`./gradlew build`) and fail when registered tools or serialised response structure diverge from checked-in expected fixtures without an intentional fixture update.

| Enforced surface                                        | Source of truth             | Failure mode |
| ------------------------------------------------------- | --------------------------- | ------------ |
| Tool names (exactly eight)                              | Checked-in contract fixture | Build fails  |
| Tool descriptions                                       | Checked-in contract fixture | Build fails  |
| Tool `inputSchema` (properties, types, required fields) | Checked-in contract fixture | Build fails  |
| Server identity (`name`, `version`)                     | Checked-in contract fixture | Build fails  |
| Serialised `Evidence` / `Provenance` JSON shape         | Checked-in contract fixture | Build fails  |

Intentional contract changes require updating the checked-in fixture **and** the authoritative contract document (`specs/001-project-scaffolding/contracts/mcp-server.md` or a successor spec approved through the specification workflow). Fixture-only updates without spec approval are not permitted.

---

## 4. Acceptance Criteria

1. `./gradlew build` completes without errors after implementation
2. The MCP server registers exactly eight tools with names matching the tool catalogue in §3
3. Each registered tool's `description` and `inputSchema` match `specs/001-project-scaffolding/contracts/mcp-server.md`
4. Invoking any tool with a valid non-blank parameter delegates to the corresponding `port.in` implementation and returns HTTP-equivalent MCP success with a JSON array body
5. Invoking any tool with a null, empty, or whitespace-only required parameter returns an MCP tool error without calling the use case
6. Stub use case implementations exist in `application.usecase` for all eight capabilities; each returns an empty list and contains no retrieval or gateway dependencies
7. The `transport` module does not import any class from `infrastructure`
8. No MCP tool exposes retrieval technology names (`grep`, `readFile`, `vectorSearch`, `bm25Search`, `graphSearch`)
9. Transport-layer tests verify: (a) all eight tools are registered, (b) invalid input produces tool errors for each tool (parameterized across all eight tool names), (c) valid input returns `[]` from stub delegation, (d) serialised response structure matches the Evidence contract when stub implementations are replaced with test doubles returning sample evidence (via the same `EvidenceJsonMapper` path used in production), (e) delegated call failures surface as MCP tool errors
10. Build-time MCP contract regression tests compare registered tools and serialised response shape against checked-in expected fixtures; `./gradlew build` fails on any unintended contract drift (tool names, descriptions, input schemas, server identity, Evidence/Provenance JSON structure)
11. Checked-in contract fixtures live under `specs/002-mcp-transport-adapter/contracts/` and are derived from `specs/001-project-scaffolding/contracts/mcp-server.md`
12. `./gradlew :transport:bootRun` (with required env vars set) starts successfully; an MCP client can connect and list all eight tools
13. Application logging continues on **stderr** only; **stdout** remains reserved for the MCP STDIO protocol
14. Constructor injection is used throughout new transport and application stub classes; no field injection
15. No star imports in any new source file

---

## 5. Domain Model Impact

This specification introduces no new domain types. It uses existing types for serialisation:

- `Evidence` — response payload
- `Provenance` — nested in each evidence element
- `KnowledgeType` — serialised as enum name string
- `KnowledgeZone` — serialised as enum name string

No changes to `domain.port.in` interfaces or domain model records.

---

## 6. Architectural Impact

### port.in (public capability)

No changes. Existing interfaces in `io.archivist.domain.port.in` are the delegation target.

### port.out (retrieval gateway)

No changes. Stub use cases do not depend on `KnowledgeGateway`.

### application (use case)

Eight stub implementations added under `io.archivist.application.usecase`:

| Class                         | Implements             | Behaviour (stub)   |
| ----------------------------- | ---------------------- | ------------------ |
| `RetrieveContextUseCase`      | `RetrieveContext`      | Returns empty list |
| `FindDecisionsUseCase`        | `FindDecisions`        | Returns empty list |
| `FindProjectsUseCase`         | `FindProjects`         | Returns empty list |
| `FindPeopleUseCase`           | `FindPeople`           | Returns empty list |
| `FindConceptsUseCase`         | `FindConcepts`         | Returns empty list |
| `FindRelatedKnowledgeUseCase` | `FindRelatedKnowledge` | Returns empty list |
| `FindReadingsUseCase`         | `FindReadings`         | Returns empty list |
| `FindDebriefsUseCase`         | `FindDebriefs`         | Returns empty list |

Stub use cases carry no Spring annotations. Spring `@Bean` registration for stubs occurs in `transport.config.StubUseCaseConfiguration` for this spec. A future retrieval spec replaces these beans in infrastructure configuration without changing MCP tool handlers.

### infrastructure (retrieval strategy)

No changes in this specification.

### transport (MCP)

New classes under `io.archivist.transport.mcp`:

- MCP tool handler(s) — one handler per capability or a consolidated registrar, delegating to injected `port.in` implementations and serialising responses exclusively via `EvidenceJsonMapper`
- `EvidenceJsonMapper` — sole serialisation path: converts `List<Evidence>` to JSON matching §3 response shape; used by tool handlers and contract tests
- Input validation utility — shared null/blank check for required string parameters
- Spring `@Configuration` class wiring MCP tool beans and stub use case beans
- Contract regression test suite under `transport/src/test` — loads registered MCP tool metadata and compares against fixtures in `specs/002-mcp-transport-adapter/contracts/`

Checked-in contract fixtures (authoritative for build-time comparison):

```
specs/002-mcp-transport-adapter/contracts/
├── mcp-tools-expected.json      # tool names, descriptions, inputSchema per tool
├── mcp-server-identity-expected.json
└── evidence-response-schema-expected.json
```

Dependency direction preserved:

```
MCP tool handler → port.in (stub use case) → (no port.out)
```

Transport imports: `domain.model`, `application.usecase`, `spring.*`, MCP SDK types.
Transport MUST NOT import `infrastructure.*`.

### Configuration

No new environment variables. Existing `ARCHIVIST_SECOND_BRAIN_PATH` requirement from scaffold remains unchanged (startup validation only; not used by stub adapter).

---

## 7. Out of Scope

- Lexical retrieval strategy implementation
- Second Brain file reading and adapter implementation
- `KnowledgeGateway` implementation
- Real use case orchestration (query construction, gateway invocation, ranking, deduplication)
- MCP Streamable HTTP transport (STDIO only for this spec)
- MCP resources or prompts beyond tool registration
- ArchUnit dependency enforcement tests
- Performance benchmarks for retrieval (no retrieval exists yet)
- Changing the MCP tool contract without updating checked-in fixtures and the authoritative contract document through the specification workflow
- CI/CD pipeline changes (contract tests run via existing `./gradlew build`; no separate CI configuration in this spec)

---

## 8. Open Questions

- [x] Should stub use cases live in `application` or should transport wire no-op lambdas? → **`application` module.** Stub use cases implement `port.in` interfaces, keeping transport free of business-facing contracts and preserving the transport → application → domain dependency chain.
- [x] Where are Spring `@Bean` definitions for stub use cases registered? → **`transport` configuration** for this spec. Infrastructure wiring of real use cases replaces these beans in a future spec without changing transport handlers.
- [x] Should invalid-parameter errors use `IllegalArgumentException` semantics from the domain contract or transport-native validation? → **Transport-native validation before delegation.** Matches the MCP server contract error table; avoids coupling MCP error shape to domain exception types.
- [x] Should build-time contract regression tests guard against unintended MCP contract changes? → **Yes.** Build-time tests compare registered MCP contracts to checked-in expected fixtures; `./gradlew build` fails on drift unless fixtures and the authoritative contract document are intentionally updated together.
- [x] Should MCP tool handlers and contract tests share one serialisation path? → **Yes.** `@McpTool` methods return JSON from `EvidenceJsonMapper` only; contract tests assert the same mapper output against fixtures.

---

## 9. Approval

| Reviewer           | Decision | Date       |
| ------------------ | -------- | ---------- |
| Leonardo Alcantara | Approved | 2026-07-12 |
