# Research: Hybrid Consensus Retrieval Strategy

**Feature**: 007-hybrid-retrieval  
**Date**: 2026-07-20  
**Status**: Complete — decisions resolved for plan

**Inputs**: [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md), [spec.md](spec.md), specs 004–006 strategy patterns

---

## Decision 1: Composition inside one strategy (registry unchanged)

**Decision**: Add **`HybridRetrievalStrategy`** with `name()` **`hybrid`**. It injects the three existing leg strategies by type or by explicit beans — **not** via `RetrievalStrategyRegistry.getActive()`.

**Rationale**: Registry exposes exactly one active strategy to `KnowledgeGateway`. Calling `getActive()` from hybrid would recurse. 004 SPI already notes hybrid composes internally.

**Alternatives considered**:

- Multiple active strategies on gateway: Rejected — breaks registry model
- Application-layer orchestration: Rejected — retrieval selection is infrastructure

---

## Decision 2: Consensus vote (not RRF)

**Decision**: Score = count of legs whose **returned list** contains `sourceId`. Sort descending. No score normalisation.

**Rationale**: No golden set to tune RRF `k` or weights. Vote count is deterministic and matches operator intent (“if all three agree, top pick”).

**Alternatives considered**:

- RRF: Deferred — needs eval or documented constants without data
- Max of normalised leg scores: Rejected — leg scores incomparable (lexical int, BM25 float, cosine)

---

## Decision 3: Tie-break embedding → BM25 → lexical

**Decision**: For equal consensus score, compare **rank index** (0 = best) on embedding list, then BM25, then lexical. First differing rank wins; if still tied, stable order by `sourceId` (implementation detail for determinism in tests).

**Rationale**: Session 2026-07-20; semantic leg breaks ties when agreement equal.

---

## Decision 4: Effective max results

**Decision**:

```text
effectiveMax = min(query.maxResults(), hybridProperties.getMaxResults())
```

Defaults: both **20**. Application continues to pass global `archivist.retrieval.max-results` into `Query`.

**Rationale**: Option B — honour domain `Query` cap while allowing hybrid-specific ceiling via env without changing lexical/BM25/embedding when they are active alone.

---

## Decision 5: Same Query to each leg

**Decision**: Pass the **same** `Query` instance (text, types, zones, `maxResults`) to lexical, BM25, and embedding. No enlarged per-leg pool in v1.

**Rationale**: Simplest behaviour; documented limitation (single-leg deep hits below top-N per leg never enter fusion).

**Alternatives considered**:

- Per-leg `maxResults * multiplier`: Deferred — adds config and eval need

---

## Decision 6: Evidence assembly (normative)

**Decision**: After ordering `sourceId`s, for each id take **`Evidence` from the highest tie-break priority leg that returned it**: **embedding → BM25 → lexical**. No corpus reload when a leg already returned that id. `KnowledgeCorpus.loadBySourceId` only if no leg returned the id ( invariant violation for ranked ids).

**Rationale**: Single entry-level payload aligned with the leg that “wins” tie-break; avoids redundant corpus I/O.

---

## Decision 7: CI path for hybrid integration tests

**Decision**: Hybrid integration tests use **`embedder=stub`**, **`store=memory`** (006 CI path) plus fixture corpus — same as embedding integration tests.

**Rationale**: AC-1 build must not require ONNX or network for hybrid gate.

---

## Decision 8: QueryGovernor (future)

**Decision**: Document only — Phase 3 **QueryGovernor** may skip legs or change weights per query. v1 always runs all three when `hybrid` active.

**Rationale**: User roadmap intent; no types or ports in 007.

---

## References

- [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md)
- [spec 006 research](../006-embedding-retrieval/research.md)
