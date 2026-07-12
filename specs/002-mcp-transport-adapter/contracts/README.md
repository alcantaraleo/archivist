# MCP Server Contract Fixtures

**Feature**: 002-mcp-transport-adapter
**Date**: 2026-07-12
**Authoritative human contract**: [specs/001-project-scaffolding/contracts/mcp-server.md](../../001-project-scaffolding/contracts/mcp-server.md)

---

## Purpose

These JSON fixtures are the **machine-readable source of truth** for build-time MCP contract regression tests. They enforce the stable public MCP contract defined in spec 001 and clarified in [spec.md](../spec.md) §3.

Unintended drift between registered MCP tools / serialised response shape and these fixtures **fails `./gradlew build`**.

---

## Fixtures

| File                                                                             | Enforces                                                         |
| -------------------------------------------------------------------------------- | ---------------------------------------------------------------- |
| [mcp-tools-expected.json](mcp-tools-expected.json)                               | Tool names, descriptions, input schemas (all eight tools)        |
| [mcp-server-identity-expected.json](mcp-server-identity-expected.json)           | Server `name` and `version`                                      |
| [evidence-response-schema-expected.json](evidence-response-schema-expected.json) | Serialised `List<Evidence>` JSON shape from `EvidenceJsonMapper` |

---

## Intentional Contract Changes

To change the MCP public contract:

1. Update the specification through the specification workflow
2. Update [specs/001-project-scaffolding/contracts/mcp-server.md](../../001-project-scaffolding/contracts/mcp-server.md) (or approved successor)
3. Update all fixtures in this directory together
4. Update `EvidenceJsonMapper` / `@McpTool` definitions if needed

Fixture-only changes without spec approval are not permitted.

---

## Comparison Mechanism

Contract regression tests (see [research.md](../research.md) Decision 5) load these files and compare against:

- `ToolCallback.getToolDefinition()` for tool metadata
- `EvidenceJsonMapper` output for response shape
- Test property overlay for server identity

Comparison uses Spring Boot's `ObjectMapper` and Jackson `JsonNode.equals()` — no additional JSON assertion libraries.
