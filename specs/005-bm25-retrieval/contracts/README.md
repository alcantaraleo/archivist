# BM25 Retrieval Contract Fixtures

**Feature**: 005-bm25-retrieval  
**Date**: 2026-07-20  
**Authoritative human contract**: [spec.md](../spec.md) §3–5, [data-model.md](../data-model.md)

---

## Purpose

These artefacts define **machine-verifiable expectations** for BM25 retrieval and **ranking differentiation** vs lexical (AC-11). Spec 004 contracts remain authoritative for unchanged gateway and query semantics.

MCP contract fixtures (spec 002) and transport handlers stay **unchanged**.

---

## Fixtures

| File | Enforces |
| ---- | -------- |
| [knowledge-gateway-bm25-semantics.md](knowledge-gateway-bm25-semantics.md) | Gateway behaviour when BM25 active |
| [bm25-configuration.md](bm25-configuration.md) | Configuration keys and defaults |
| [retrieval-strategy-spi.md](retrieval-strategy-spi.md) | Updated SPI implementation table |
| [fixture-bm25-ranking-scenario.json](fixture-bm25-ranking-scenario.json) | AC-11 lexical vs BM25 order |
| [corpus-fingerprint-contract.md](corpus-fingerprint-contract.md) | Fingerprint inputs for cache invalidation |

**Inherited (unchanged)** from spec 004:

- [../004-lexical-retrieval/contracts/query-model.md](../004-lexical-retrieval/contracts/query-model.md)
- [../004-lexical-retrieval/contracts/knowledge-gateway-port.md](../004-lexical-retrieval/contracts/knowledge-gateway-port.md) — baseline; BM25 doc extends ranking only

---

## Change process

1. Update approved [spec.md](../spec.md)
2. Update ranking fixture markdown + JSON together
3. Re-run [quickstart.md](../quickstart.md)

Adding strategies beyond BM25 still follows spec 004 SPI checklist — no MCP updates.
