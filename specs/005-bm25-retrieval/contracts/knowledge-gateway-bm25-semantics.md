# Knowledge Gateway — BM25 Active Semantics

**Feature**: 005-bm25-retrieval  
**Extends**: [004 knowledge-gateway-port.md](../../004-lexical-retrieval/contracts/knowledge-gateway-port.md)

When `archivist.retrieval.active-strategy=bm25`, `KnowledgeGateway.retrieve(Query)` behaviour matches spec 004 **except**:

| Aspect | Lexical (004) | BM25 (005) |
| ------ | ------------- | ---------- |
| Ranking | Weighted substring token hits | Lucene BM25 score with field boosts 3:2:1 |
| Corpus access | `loadAll()` per call | `catalog()` for fingerprint; `loadAll()` on index rebuild |
| Index | None | In-memory Lucene index with cache ([ADR-0004](../../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md)) |

**Unchanged** (both strategies):

| Condition | Result |
| --------- | ------ |
| `query == null` | `NullPointerException` |
| Blank `query.text()` | Empty list |
| No matches | Empty list |
| Filters | `Query.types()` / `Query.zones()` — empty set = no filter |
| Dedupe | At most one `Evidence` per `sourceId` |
| Limit | Size ≤ `query.maxResults()` |
| Provenance | Required on every hit |
| Size-limited entries | Title/tags indexed; body omitted from index and evidence content empty |

Tokenisation for matching MUST match [004 LexicalScorer](../../../infrastructure/src/main/java/io/archivist/infrastructure/retrieval/LexicalScorer.java) rules (see [research.md](../research.md) Decision 5).
