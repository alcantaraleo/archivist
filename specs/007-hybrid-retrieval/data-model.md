# Data Model: Hybrid Consensus Retrieval Strategy

**Feature**: 007-hybrid-retrieval  
**Date**: 2026-07-20

> Adds infrastructure-only hybrid orchestration and configuration. **No** changes to `domain.model` types, `port.in`, or `KnowledgeGateway` / `KnowledgeCorpus` signatures.

---

## Domain Layer (unchanged)

| Type | Change |
| ---- | ------ |
| `Evidence`, `Provenance`, `Query`, … | Unchanged |
| `KnowledgeGateway` | Unchanged signature; hybrid affects ranking when `active-strategy=hybrid` |
| Eight `port.in` interfaces | Unchanged |

---

## Application Layer (unchanged)

Use cases continue to pass `defaultMaxResults` from `archivist.retrieval.max-results` into `Query` factories.

---

## Infrastructure Layer: Configuration

### Extended: `RetrievalProperties`

Add nested **`hybrid`** (`archivist.retrieval.hybrid.*`):

| Field | Type | Default | Validation |
| ----- | ---- | ------- | ---------- |
| `maxResults` | `int` | `20` | `@Min(1)` |

Existing fields unchanged: `activeStrategy` (default `lexical`), top-level `maxResults` (default `20`), `bm25`, `embedding`.

**Env**: `ARCHIVIST_RETRIEVAL_HYBRID_MAX_RESULTS` → `hybrid.max-results`.

---

## Infrastructure Layer: Core Types

### `HybridProperties` (Java bean)

Mirrors nest above; bound via `@NestedConfigurationProperty` on `RetrievalProperties`.

### `HybridConsensusRanker` (package-private, optional extract)

Pure functions:

| Input | Output |
| ----- | ------ |
| Three ordered lists of `sourceId` (or `Evidence`) | Ordered list of `sourceId` with consensus + tie-break |

No Spring dependencies — unit-test friendly.

### `HybridRetrievalStrategy`

| Dependency | Role |
| ---------- | ---- |
| `LexicalRetrievalStrategy` (or `RetrievalStrategy` lexical bean) | Leg 1 |
| `Bm25RetrievalStrategy` | Leg 2 |
| `EmbeddingRetrievalStrategy` | Leg 3 |
| `HybridProperties` | Cap |
| `KnowledgeCorpus` | `loadBySourceId` only when no leg returned the id (should not happen for fused ids) |

**Does not** depend on `RetrievalStrategyRegistry`.

---

## Internal ranking record (package-private, optional)

```text
LegMembership
  sourceId
  inLexical, inBm25, inEmbedding : boolean
  lexicalRank, bm25Rank, embeddingRank : int  // -1 if absent
  consensusScore : int  // 1..3
```

Used for sorting; **not** exposed on `Evidence`.

---

## Registry (after 007)

| `name()` | Class |
| -------- | ----- |
| `lexical` | `LexicalRetrievalStrategy` |
| `bm25` | `Bm25RetrievalStrategy` |
| `embedding` | `EmbeddingRetrievalStrategy` |
| `hybrid` | `HybridRetrievalStrategy` |

Default active: **`lexical`**.

---

## References

- [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md)
- [spec 006 data-model](../006-embedding-retrieval/data-model.md)
