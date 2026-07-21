# Retrieval Strategy SPI — Embedding Addition

**Feature**: 006-embedding-retrieval  
**Extends**: [004 retrieval-strategy-spi](../../004-lexical-retrieval/contracts/retrieval-strategy-spi.md), [005 SPI table](../../005-bm25-retrieval/contracts/retrieval-strategy-spi.md)

---

## Registered strategies (after 006)

| `name()` | Class | Status |
| -------- | ----- | ------ |
| `lexical` | `LexicalRetrievalStrategy` | Existing (default active) |
| `bm25` | `Bm25RetrievalStrategy` | Existing (opt-in) |
| `embedding` | `EmbeddingRetrievalStrategy` | **New in 006** |

---

## Embedding strategy checklist

- [ ] `EmbeddingRetrievalStrategy` implements `RetrievalStrategy`; `name()` returns `"embedding"`
- [ ] Depends on `TextEmbedder` + `VectorStore` SPIs only (plus corpus/chunker/cache)
- [ ] Honours `Query` filters, blank-text empty list, dedupe, `maxResults`, ADR-0003
- [ ] Registered via `RetrievalConfiguration` bean
- [ ] Default `active-strategy` remains `lexical`

---

## Isolation

| Check | Expected |
| ----- | -------- |
| `domain.port.in` | Unchanged |
| MCP tools / fixtures (002) | Unchanged |
| `transport` imports | No `infrastructure.*` |
| `application` use cases | Unchanged |
