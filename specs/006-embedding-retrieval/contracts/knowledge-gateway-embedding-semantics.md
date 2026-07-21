# KnowledgeGateway Semantics — Embedding Strategy Active

**Feature**: 006-embedding-retrieval  
**When**: `archivist.retrieval.active-strategy=embedding`

---

## Unchanged from specs 004–005

| Condition | Behaviour |
| --------- | --------- |
| `query == null` | `NullPointerException` |
| Blank `query.text()` | Empty list |
| Type / zone filters | Applied; non-matching excluded |
| Deduplication | At most one `Evidence` per `sourceId` |
| `maxResults` | Hard cap on returned list |
| Provenance | Required on every hit |
| Size-limited entries | ADR-0003 — title/tags only in index; empty body in evidence |

---

## Ranking when embedding is active

1. Corpus entries are **chunked** and embedded; query text is embedded with the active `TextEmbedder`.
2. Candidates come from `VectorStore.search` (cosine), then filtered.
3. Multiple chunk hits for one `sourceId` collapse to **one** evidence using **best chunk score**.
4. Order: descending similarity score.
5. Optional `min-score`: candidates below threshold excluded.

Blank query never reaches the embedder (empty list at strategy/gateway boundary).

---

## Content of returned Evidence

Entry-level content and provenance from `KnowledgeCorpus` (not raw chunk text as the public content contract for v1).
