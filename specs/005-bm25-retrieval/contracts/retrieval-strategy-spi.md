# Retrieval Strategy SPI — BM25 Extension

**Feature**: 005-bm25-retrieval  
**Base SPI**: [004 retrieval-strategy-spi.md](../../004-lexical-retrieval/contracts/retrieval-strategy-spi.md)

---

## Registered implementations (after 005)

| `name()` | Class | Status |
| -------- | ----- | ------ |
| `lexical` | `LexicalRetrievalStrategy` | Default active strategy |
| `bm25` | `Bm25RetrievalStrategy` | Opt-in via `active-strategy=bm25` |

Registry rules unchanged: duplicate names or unknown active name → startup failure.

---

## BM25 implementation checklist (005)

- [ ] `Bm25RetrievalStrategy` implements `RetrievalStrategy`; `name()` returns `"bm25"`
- [ ] Lucene dependency scoped to `infrastructure` Gradle module only
- [ ] `@Bean` in `RetrievalConfiguration` alongside lexical bean
- [ ] Honour `Query` filters, blank text, dedupe, `maxResults`
- [ ] `ContentAvailability` body omission for size-limited entries
- [ ] `CorpusFingerprint` + TTL cache per [corpus-fingerprint-contract.md](corpus-fingerprint-contract.md)
- [ ] Unit + integration tests; AC-11 scenario in [fixture-bm25-ranking-scenario.json](fixture-bm25-ranking-scenario.json)
- [ ] No changes to `port.in`, MCP tools, transport Java imports

---

## Future strategies

Hybrid, embedding, and graph strategies still add a bean + unique `name()` — see 004 extension checklist. BM25 does not block parallel registration of future beans.
