# Research: MCP Transport Adapter

**Feature**: 002-mcp-transport-adapter
**Date**: 2026-07-12
**Status**: Complete — all technical decisions resolved

---

## Decision 1: MCP Tool Registration Mechanism

**Decision**: **`@McpTool` / `@McpToolParam` annotations** on a single `@Component` class (`ArchivistMcpTools`) in `transport.mcp`

**Rationale**: Spring AI 2.0.0 provides declarative MCP tool registration via `@McpTool`. Auto-configuration scans annotated beans, generates JSON input schemas from `@McpToolParam` metadata, and registers `ToolCallback` beans with the MCP server. This aligns with the scaffold's `spring-ai-starter-mcp-server` dependency and avoids manual `McpServer.SyncSpecification` wiring.

Each tool method:

1. Validates the required string parameter via `McpInputValidator` (transport boundary)
2. Delegates to the injected `port.in` interface
3. Serialises the returned `List<Evidence>` via `EvidenceJsonMapper` and returns the JSON string (sole serialisation path — no direct Jackson serialisation of domain types on the `@McpTool` return path)

**Alternatives considered**:

- Manual `ToolCallbackProvider` with hand-built `DefaultToolDefinition` per tool: Rejected — duplicates schema generation Spring AI already provides; increases drift risk vs `@McpToolParam` metadata
- One `@Component` per tool: Rejected — eight nearly identical wiring classes; consolidated registrar is simpler

---

## Decision 2: Stub Use Case Placement and Wiring

**Decision**: **Stub use cases in `application.usecase`**; **`@Bean` registration in `transport.config.StubUseCaseConfiguration`**

**Rationale**: Preserves Clean Architecture — application classes implement `port.in` with zero Spring annotations; transport owns Spring wiring for this spec (no infrastructure module work). Future retrieval specs replace stub `@Bean` definitions in infrastructure configuration without changing MCP tool handlers.

**Alternatives considered**:

- Stub beans defined inline as lambdas in transport: Rejected — obscures the `port.in` contract; harder to swap for real use cases
- `@Component` on stub use cases in application: Rejected — violates constitution (no Spring annotations in application layer)

---

## Decision 3: Evidence Serialisation

**Decision**: **`EvidenceJsonMapper`** — dedicated transport class using Spring Boot's auto-configured **`ObjectMapper`** (Jackson 3 via BOM). **Sole serialisation path** for all MCP tool responses and contract tests.

**Rationale**: MCP tools delegate to `port.in`, receive `List<Evidence>`, then return JSON from `EvidenceJsonMapper`. A single mapper ensures production MCP output and fixture-based contract tests cannot diverge. Domain records remain unchanged.

Contract test compares mapper output for a canonical sample `Evidence` against `evidence-response-schema-expected.json` using `JsonNode.equals()`. `ArchivistMcpToolsTest` invokes the same mapper path when test doubles return sample evidence (AC-9d).

**Alternatives considered**:

- Return raw `String` JSON from tool methods: Rejected — pushes serialisation into each handler; duplicates logic across eight tools
- Transport DTOs mirroring `Evidence`: Rejected — unnecessary duplication; domain records are already immutable value objects suitable for serialisation

---

## Decision 4: Invalid Input Error Handling

**Decision**: **Transport-native validation** via `McpInputValidator.requireNonBlank(value, paramName)` throwing `IllegalArgumentException` before delegation

**Rationale**: Matches clarified spec decision and the MCP server contract error table. Spring AI converts uncaught exceptions from `@McpTool` methods into MCP tool error results. Stub use cases are not invoked for invalid input. Tests assert the thrown `IllegalArgumentException` message includes the parameter name — Spring AI maps this to the MCP tool error visible to clients.

**Alternatives considered**:

- Delegate validation to stub use cases: Rejected — couples application layer to MCP error semantics; violates spec clarification

---

## Decision 5: Contract Regression Test Strategy

**Decision**: **`@SpringBootTest` with a dedicated minimal test configuration** (`McpAdapterTestConfiguration`) — not full `ArchivistApplication` context

**Rationale**: User requirement: Spring-native testing, minimal context, no new libraries.

Test configuration `@Import`s only:

- `StubUseCaseConfiguration`
- `ArchivistMcpTools`
- `EvidenceJsonMapper`
- Spring AI MCP server auto-configuration (tool callback registration slice)

Test properties supply a valid temp directory for `archivist.second-brain.path` to satisfy `@Validated` on `ArchivistProperties` when that bean is included; contract tests that exclude properties validation use `@ContextConfiguration` with explicit classes only (see Decision 6).

Contract assertions:

1. Autowire `List<ToolCallback>` (or `ToolCallbackProvider`) — Spring-native bean provided by Spring AI auto-config
2. For each callback, call `getToolDefinition()` → extract `name`, `description`, `inputSchema`
3. Load expected fixture from `specs/002-mcp-transport-adapter/contracts/mcp-tools-expected.json`
4. Compare using **`ObjectMapper.readTree()` + `JsonNode.equals()`** — Jackson is already on the classpath via Spring Boot; no JSONAssert, approvaltests, or snapshot libraries

Server identity verified from `application.properties` test overlay matching `mcp-server-identity-expected.json`.

**Alternatives considered**:

- Full `@SpringBootTest(classes = ArchivistApplication.class)`: Rejected — loads entire application surface including STDIO transport lifecycle; slower and unnecessary for contract verification
- JSONAssert / approval tests: Rejected — user directive to avoid new libraries; Jackson `JsonNode` equality is sufficient
- Parsing `mcp-server.md` at test runtime: Rejected — markdown is not a stable machine-readable contract; JSON fixtures are the build-time source of truth per spec AC-11

---

## Decision 6: Test Context Slicing (Two Tiers)

**Decision**: **Two test tiers** with explicitly scoped Spring contexts

| Tier                    | Scope                                                    | Spring mechanism                                               | Purpose                                                |
| ----------------------- | -------------------------------------------------------- | -------------------------------------------------------------- | ------------------------------------------------------ |
| **Unit**                | `EvidenceJsonMapper`, `McpInputValidator`                | Plain JUnit 5 — no Spring context                              | Serialisation shape, validation logic                  |
| **Adapter integration** | MCP tool registration + delegation + contract regression | `@SpringBootTest(classes = McpAdapterTestConfiguration.class)` | Tool catalogue, schema contract, stub delegation       |
| **Smoke**               | Full application boot                                    | Manual `quickstart.md` step only — not automated in this spec  | STDIO server lists eight tools via external MCP client |

`McpAdapterTestConfiguration` is a `@TestConfiguration` that:

- Does **not** `@SpringBootApplication` scan the full `io.archivist.transport` package
- Does **not** load `ArchivistApplication`, `ArchivistProperties`, or any `:infrastructure` beans
- `@Import`s only: `StubUseCaseConfiguration`, `ArchivistMcpTools`, `EvidenceJsonMapper`, and Spring AI MCP tool-callback auto-configuration
- Sets test properties: `archivist.second-brain.path` to a temp directory (if properties bean is ever pulled in), and `spring.ai.mcp.server.stdio=false` to avoid STDIO binding during tests (verify property name at implementation; fallback: `@EnableAutoConfiguration(exclude = …)` for STDIO auto-config)

Behavior tests (`ArchivistMcpToolsTest`) use the same minimal configuration with `@MockitoBean` replacements for individual `port.in` beans — parameterized across all eight tools for invalid-input cases; sample-evidence and delegation-failure cases use `@MockitoBean` on one capability at a time.

**Alternatives considered**:

- `@WebMvcTest`-style slice for MCP: Not available — no dedicated Spring Boot MCP test slice; custom `@TestConfiguration` is the Spring-native equivalent
- `@SpringBootTest` + `@MockBean` on entire infrastructure: Rejected — infrastructure module not on classpath for transport tests anyway

---

## Decision 7: Test Dependencies

**Decision**: Add **`spring-boot-starter-test`** to `transport/build.gradle.kts` **test scope only** — no other new test libraries

**Rationale**: Brings JUnit 5, AssertJ, Mockito, Spring Test, and JSON support transitively. Sufficient for minimal-context integration tests and Mockito-based delegation tests. Domain and application modules remain Spring-free in tests.

**Alternatives considered**:

- AssertJ JSON module (separate artifact): Rejected — use Jackson `JsonNode` instead
- Testcontainers / MCP client integration: Rejected — out of scope; contract tests inspect `ToolCallback` definitions directly

---

## Decision 8: Contract Fixture Location and Format

**Decision**: JSON fixtures under **`specs/002-mcp-transport-adapter/contracts/`**

| File                                     | Contents                                                          |
| ---------------------------------------- | ----------------------------------------------------------------- |
| `mcp-tools-expected.json`                | Array of `{ name, description, inputSchema }` for all eight tools |
| `mcp-server-identity-expected.json`      | `{ name, version }`                                               |
| `evidence-response-schema-expected.json` | Canonical serialised sample `Evidence` array (one element)        |

Tests load fixtures via `Path.of("specs/002-mcp-transport-adapter/contracts/...")` relative to repo root. Gradle test task sets working directory to repo root (standard Gradle behaviour) or uses `getClass().getResource()` if classpath resource copy is preferred at implementation time.

Fixtures derived from `specs/001-project-scaffolding/contracts/mcp-server.md` — authoritative human-readable contract remains in 001; machine-readable fixtures in 002 are the build-time enforcement source.

---

## Summary Table

| Question                  | Decision                                                                        |
| ------------------------- | ------------------------------------------------------------------------------- |
| Tool registration         | `@McpTool` on `ArchivistMcpTools` `@Component`                                  |
| Stub use cases            | `application.usecase` classes; `@Bean` in `transport.config`                    |
| Serialisation             | `EvidenceJsonMapper` — sole path for MCP responses and contract tests           |
| Input validation          | `McpInputValidator` at transport boundary                                       |
| Contract regression tests | Minimal `@SpringBootTest` + `ToolCallback.getToolDefinition()` vs JSON fixtures |
| JSON comparison           | Jackson `JsonNode.equals()` — no new libraries                                  |
| Test dependencies         | `spring-boot-starter-test` only (transport module)                              |
| Test context              | `McpAdapterTestConfiguration` — explicit `@Import`, no full app scan            |
