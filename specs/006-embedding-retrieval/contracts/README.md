# Embedding Retrieval Contract Fixtures

**Feature**: 006-embedding-retrieval  
**Date**: 2026-07-20  
**Authoritative human contract**: [spec.md](../spec.md) §3–5, [data-model.md](../data-model.md), [research.md](../research.md)

---

## Purpose

Machine-verifiable expectations for **embedding** retrieval, **pluggable adapters**, and **ranking differentiation** vs lexical/BM25 (AC-11). Specs 004–005 remain authoritative for unchanged gateway and query semantics.

MCP contract fixtures (spec 002) and transport handlers stay **unchanged**.

---

## Fixtures

| File | Enforces |
| ---- | -------- |
| [knowledge-gateway-embedding-semantics.md](knowledge-gateway-embedding-semantics.md) | Gateway behaviour when embedding active |
| [embedding-configuration.md](embedding-configuration.md) | Configuration keys and defaults |
| [text-embedder-spi.md](text-embedder-spi.md) | Embedder SPI + first adapters |
| [vector-store-spi.md](vector-store-spi.md) | Vector store SPI + memory + future extension |
| [retrieval-strategy-spi.md](retrieval-strategy-spi.md) | Updated strategy table including `embedding` |
| [fixture-embedding-ranking-scenario.json](fixture-embedding-ranking-scenario.json) | AC-11 ordering scenario |

**Inherited (unchanged)** from prior specs:

- [../004-lexical-retrieval/contracts/query-model.md](../004-lexical-retrieval/contracts/query-model.md)
- [../004-lexical-retrieval/contracts/knowledge-gateway-port.md](../004-lexical-retrieval/contracts/knowledge-gateway-port.md)
- [../005-bm25-retrieval/contracts/corpus-fingerprint-contract.md](../005-bm25-retrieval/contracts/corpus-fingerprint-contract.md) — reused for embedding index cache

---

## Operator documentation (implementation delivers)

| Artifact | Content |
| -------- | ------- |
| `docs/adr/ADR-0005-pluggable-embedding-retrieval.md` | Architectural decision: SPIs + chunking/dedupe |
| `deploy/docker-compose.embedding.example.yml` | Compose template (embeddings example + placeholder DBs) |
| `README.md` Phase 2 | Roadmap checkbox + pointer to quickstart/ADR |
| [../quickstart.md](../quickstart.md) | How to validate; **`local` vs `openai-compatible`** (OpenAI API vs local HTTP proxy) |

---

## Change process

1. Update approved [spec.md](../spec.md)
2. Update ranking fixture markdown/JSON together
3. Re-run [quickstart.md](../quickstart.md)
