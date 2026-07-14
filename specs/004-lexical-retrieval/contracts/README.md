# Retrieval Contract Fixtures

**Feature**: 004-lexical-retrieval
**Date**: 2026-07-13
**Authoritative human contract**: [spec.md](../spec.md) §3, [data-model.md](../data-model.md)

---

## Purpose

These artefacts define the **machine-verifiable contract** for lexical retrieval and the **internal strategy SPI** extension point. Integration tests load the spec 003 fixture corpus and compare lexical outcomes to [fixture-lexical-expected.json](fixture-lexical-expected.json).

Unintended drift in scoring, filtering, or gateway semantics **fails `./gradlew :infrastructure:test`**.

MCP contract fixtures (spec 002) are **intentionally unchanged** — retrieval strategy swaps must not affect transport tests.

---

## Fixtures

| File                                                           | Enforces                                    |
| -------------------------------------------------------------- | ------------------------------------------- |
| [knowledge-gateway-port.md](knowledge-gateway-port.md)         | `KnowledgeGateway.retrieve` semantics       |
| [query-model.md](query-model.md)                               | `Query` factories and capability mapping    |
| [retrieval-strategy-spi.md](retrieval-strategy-spi.md)         | Internal SPI + Phase 2 extension checklist  |
| [fixture-lexical-expected.json](fixture-lexical-expected.json) | Expected lexical outcomes per test scenario |

---

## Intentional Contract Changes

To change retrieval behaviour:

1. Update the approved specification
2. Update fixture corpus entries if scenarios change
3. Update `fixture-lexical-expected.json` together with scorer/strategy changes
4. Re-run [quickstart.md](../quickstart.md)

To add a new strategy (Phase 2+):

1. Follow [retrieval-strategy-spi.md](retrieval-strategy-spi.md) — no MCP fixture updates required

---

## Comparison Mechanism

`LexicalRetrievalIntegrationTest` (see [quickstart.md](../quickstart.md)):

- Boots `RetrievalTestConfiguration` with fixture corpus path
- Invokes `KnowledgeGateway.retrieve` with documented `Query` instances
- Asserts result count, order, types, and `sourceId` sets against JSON scenarios
- Asserts oversize entry availability policy (AC-10, AC-11)
