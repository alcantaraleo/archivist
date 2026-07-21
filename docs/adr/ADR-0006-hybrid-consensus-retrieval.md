# ADR-0006: Hybrid Retrieval by Multi-Leg Consensus

**Date:** 2026-07-20  
**Status:** Accepted  
**Author:** Leonardo Alcantara

---

## Context

README **Phase 2 — Retrieval evolution** closes with **hybrid retrieval and re-ranking**. Specs 004–006 deliver three standalone **`RetrievalStrategy`** implementations — **`lexical`**, **`bm25`**, and **`embedding`** — selected by `archivist.retrieval.active-strategy`. Each leg ranks differently (term overlap, BM25, semantic similarity).

There is **no golden query set** or offline eval harness today to choose the best single strategy or a tuned fusion recipe per query. Operators should still opt into stronger recall without exposing retrieval technology on MCP or changing `port.in`.

ADR-0005 deferred hybrid as a registry-level composition. This ADR records the **v1 hybrid policy** until a future **QueryGovernor** (Phase 3) can plan which legs to run per query.

---

## Decision

1. **New strategy** `HybridRetrievalStrategy` with stable name **`hybrid`**, registered like other strategies. **`KnowledgeGateway`** and MCP stay unchanged; callers cannot see which legs ran or which leg surfaced a hit.

2. **Worst-case fan-out**: when `active-strategy=hybrid`, run **`lexical`**, **`bm25`**, and **`embedding`** for the **same** domain **`Query`** (filters + text + `maxResults` unchanged). Legs may run concurrently in infrastructure; ordering of leg execution is not part of the public contract.

3. **Consensus score**: for each `sourceId`, **score = number of legs whose result list contains that id** (0–3). Only ids with score ≥ 1 are candidates. Sort candidates **descending by score**.

4. **Tie-break** (same consensus score): prefer the id with the **better (lower) rank index** on **embedding**; if still tied, **BM25**; if still tied, **lexical**. Rank index is 0-based position in that leg’s returned list. No leg attribution on `Evidence` or MCP payloads.

5. **Result cap (option B)**: returned list size ≤ **`min(query.maxResults(), archivist.retrieval.hybrid.max-results)`**. Global default **`archivist.retrieval.max-results`** remains **20** and continues to populate `Query.maxResults()` in application use cases. Hybrid nest **`hybrid.max-results`** defaults to **20** so operators can lower/raise the hybrid ceiling via properties or env (e.g. `ARCHIVIST_RETRIEVAL_HYBRID_MAX_RESULTS`) without changing behaviour of other strategies.

6. **Evidence shape**: one **`Evidence` per `sourceId`**, taken from the **embedding → BM25 → lexical** leg that returned that id (no corpus reload when a leg already returned it). ADR-0003 content policy unchanged. Consensus is a **private ranking policy**, not a new public type.

7. **Startup**: `hybrid` active requires all three legs to be constructible — embedding leg needs valid embedder/store config per spec 006. **Fail fast** at startup if any leg cannot run (same spirit as unknown strategy ids).

8. **Explicit non-goals for v1**:
   - LLM or generative “rerank” (reasoning boundary)
   - Per-result strategy labels or MCP debug fields (unless a future optional ops flag is specced separately)
   - Query-dependent leg selection (deferred to QueryGovernor)
   - Proof of optimality without a golden set — policy is **intentionally simple**

9. **Default deployment**: **`active-strategy=lexical`** until operator sets **`hybrid`**. Hybrid trades **CPU/latency** for **simplicity and cross-leg recall**.

---

## Options Considered

### Option A: Reciprocal rank fusion (RRF) or weighted score merge

**Rejected for v1** — requires score normalisation or tuning weights; no eval data to calibrate. May revisit after golden-set work or under QueryGovernor.

### Option B: Run one leg based on heuristics without eval

**Rejected** — no reliable heuristics today; would bake guesses into infrastructure.

### Option C: Expose leg choice or fusion weights on MCP

**Rejected** — violates Constitution II (retrieval technology on public contract).

### Option D: LLM reranker on fused pool

**Rejected** — reranking that generates or interprets text is reasoning; out of scope for Archivist.

---

## Consequences

- Every hybrid query pays **three full retrievals** (including embedding index/query embed cost).
- A hit strong on **one leg only** (e.g. paraphrase-only embedding win in AC-11-style fixtures) may rank **below** a term-collision doc that two legs agree on — accepted until eval or QueryGovernor.
- **`HybridRetrievalStrategy`** must delegate to existing leg strategies **without** re-entering `RetrievalStrategyRegistry.getActive()` (avoid recursion when hybrid is active).
- Implementation belongs in spec **`007-hybrid-retrieval`**; this ADR is the architectural source of truth for fusion policy.
- README Phase 2 hybrid checkbox should close when 007 is implemented and verified.

---

## References

- [spec 007](../../specs/007-hybrid-retrieval/spec.md)
- [spec 004 retrieval SPI](../../specs/004-lexical-retrieval/contracts/retrieval-strategy-spi.md) — hybrid composition note
- [ADR-0005](./ADR-0005-pluggable-embedding-retrieval.md)
- [ADR-0003](./ADR-0003-flag-size-limited-corpus-entries.md)
