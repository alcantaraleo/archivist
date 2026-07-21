# KnowledgeGateway Semantics — Hybrid Strategy Active

**Feature**: 007-hybrid-retrieval  
**When**: `archivist.retrieval.active-strategy=hybrid`  
**Algorithm**: [hybrid-consensus-algorithm.md](hybrid-consensus-algorithm.md) · [ADR-0006](../../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md)

---

## Unchanged from specs 004–006

| Condition | Behaviour |
| --------- | --------- |
| `query == null` | `NullPointerException` |
| Blank `query.text()` | Empty list |
| Type / zone filters | Applied by each leg on the same `Query` |
| Provenance | Required on every hit |
| Size-limited entries | ADR-0003 |

---

## Ranking when hybrid is active

1. Run **lexical**, **bm25**, and **embedding** with the **same** `Query`.
2. Build consensus scores from leg **membership** in each leg’s returned list.
3. Sort by consensus desc; tie-break embedding rank → BM25 rank → lexical rank.
4. Truncate to **`min(query.maxResults(), hybrid.max-results)`**.
5. Return entry-level `Evidence` (no leg attribution). For each `sourceId`, use the **`Evidence` from embedding if present, else BM25, else lexical** — no corpus reload when a leg already returned that id.

Callers cannot observe which legs ran or which leg surfaced an id.

---

## Content of returned Evidence

Entry-level content and provenance from `KnowledgeCorpus` / leg results — not a fusion summary string.
