# Research: Project Scaffolding

**Feature**: 001-project-scaffolding
**Date**: 2026-07-11
**Status**: Complete — all open questions resolved

---

## Decision 1: Spring Boot Version

**Decision**: Spring Boot **4.1.0**

**Rationale**: Spring Boot 3.5 reached end-of-life on June 30, 2026 (final OSS patch: 3.5.16, released June 25 2026). Starting a new project on an EOL line would require an immediate migration within months. Spring Boot 4.1.0 (released June 10, 2026) is the current stable release, supported through approximately July 2027. It requires Java 17 minimum and is compatible through Java 26 — fully compatible with the project's Java 21 target.

**Alternatives considered**:
- Spring Boot 3.5.16: Rejected — EOL as of June 30 2026; new projects should not start on an EOL line
- Spring Boot 4.0.x: Rejected — 4.1.0 is available and is the recommended current release

---

## Decision 2: Spring AI Version

**Decision**: Spring AI **2.0.0**

**Rationale**: Spring AI 2.0.0 GA was released June 12, 2026 and is designed specifically for Spring Boot 4.x. It ships the MCP Java SDK 2.0.0, which is compliant with the MCP specification 2025-11-25 (the current spec version, addressing the elicitation payload issue present in Spring AI 1.x). Spring AI 1.x targets Spring Boot 3.x — using it with Spring Boot 4.x is not supported.

Key Spring AI 2.0 MCP improvements:
- MCP Java SDK 2.0.0 (spec 2025-11-25 compliant)
- `@McpTool`, `@McpResource`, `@McpPrompt` annotations for declarative tool registration
- Streamable HTTP as the default transport (SSE deprecated)
- WebMVC and WebFlux transport implementations now part of Spring AI (not the MCP SDK)

**Alternatives considered**:
- Spring AI 1.1.x: Rejected — targets Spring Boot 3.x; not compatible with Spring Boot 4.x; also has known MCP spec compliance issues with modern clients (Cursor, Claude Code)

---

## Decision 3: MCP Transport Mechanism

**Decision**: **STDIO** transport for initial scaffold; architecture prepared to add Streamable HTTP

**Rationale**: Archivist is a personal knowledge retrieval service consumed locally by AI agents (Claude Desktop, Cursor). STDIO is the natural transport for local process-based MCP integrations and is the deployment model used by virtually all local MCP servers. No network exposure is required for the initial use case.

The `transport` module will use `spring-ai-starter-mcp-server` (STDIO variant) with `spring.ai.mcp.server.stdio=true`. The architecture must not couple to this choice — switching to Streamable HTTP (for remote deployment) requires only a dependency swap and property change, with zero domain or application layer changes.

**Alternatives considered**:
- Streamable HTTP: Valid for network-accessible deployment. Can be added in a follow-up without architectural changes
- SSE: Deprecated since Spring AI 2.0.0 — not used

---

## Decision 4: Version Management Strategy

**Decision**: **Gradle Version Catalogue** (`gradle/libs.versions.toml`)

**Rationale**: Centralising version declarations in a version catalogue prevents version drift across modules, provides IDE autocompletion for dependency references, and is the Gradle-recommended approach for multi-module projects. All four modules reference shared dependency versions through the catalogue rather than duplicating version strings.

**Alternatives considered**:
- Versions in root `build.gradle.kts` via `extra` properties: Rejected — less discoverable, no IDE autocompletion, informal convention
- Each module manages its own versions: Rejected — leads to version drift and makes upgrades error-prone

---

## Decision 5: Jackson Version

**Decision**: **Jackson 3** (transitively via Spring Boot 4.1.0 BOM)

**Rationale**: Spring Boot 4.x ships with Jackson 3, which aligns with Spring Framework 7. The MCP Java SDK 2.0.0 requires `mcp-json-jackson3` for the Jackson binding implementation. This is handled automatically through the Spring Boot BOM — no explicit Jackson version declaration is needed.

**Alternatives considered**:
- Jackson 2: Incompatible with Spring Boot 4.x and MCP Java SDK 2.0.0

---

## Decision 6: Gradle Wrapper Version

**Decision**: Latest stable Gradle at implementation time (7.x or 8.x — verify via `gradle wrapper --gradle-version latest`)

**Rationale**: The Gradle wrapper ensures reproducible builds regardless of the developer's local Gradle installation. The wrapper version should be the latest stable at project creation time, compatible with Kotlin DSL and the chosen Spring Boot version.

---

## Summary Table

| Question | Decision | Version / Detail |
| -------- | -------- | ---------------- |
| Spring Boot version | Spring Boot 4.1.0 | Released June 10, 2026 |
| Spring AI version | Spring AI 2.0.0 | Released June 12, 2026 |
| MCP transport | STDIO (initial) | Streamable HTTP addable without arch changes |
| Version management | Gradle Version Catalogue | `gradle/libs.versions.toml` |
| Jackson | Jackson 3 (via BOM) | Transitive through Spring Boot 4.x BOM |
| Gradle wrapper | Latest stable | Determined at implementation time |
