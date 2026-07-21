# Retrieval Strategy SPI — Hybrid Addition

**Feature**: 007-hybrid-retrieval  
**Extends**: [004 retrieval-strategy-spi](../../004-lexical-retrieval/contracts/retrieval-strategy-spi.md), [005](../../005-bm25-retrieval/contracts/retrieval-strategy-spi.md), [006](../../006-embedding-retrieval/contracts/retrieval-strategy-spi.md)

---

## Registered strategies (after 007)

| `name()` | Class | Status |
| -------- | ----- | ------ |
| `lexical` | `LexicalRetrievalStrategy` | Existing (default active) |
| `bm25` | `Bm25RetrievalStrategy` | Existing |
| `embedding` | `EmbeddingRetrievalStrategy` | Existing |
| `hybrid` | `HybridRetrievalStrategy` | **New in 007** |

---

## Hybrid strategy checklist

- [x] `HybridRetrievalStrategy` implements `RetrievalStrategy`; `name()` returns `"hybrid"`
- [x] Delegates to lexical + BM25 + embedding beans — **never** `RetrievalStrategyRegistry.getActive()`
- [x] Consensus + tie-break per [hybrid-consensus-algorithm.md](hybrid-consensus-algorithm.md)
- [x] Cap: `min(query.maxResults(), hybrid.max-results)`
- [x] Registered via `RetrievalConfiguration` bean
- [x] Default `active-strategy` remains `lexical`

---

## Isolation

| Check | Expected |
| ----- | -------- |
| `domain.port.in` | Unchanged |
| MCP tools / fixtures (002) | Unchanged |
| `transport` imports | No `infrastructure.*` |
| `application` use cases | Unchanged |

---

## Future: QueryGovernor

Phase 3 planner may call subset of legs or alternate fusion. Governor stays infrastructure-private; registry may still expose single active strategy to gateway.
