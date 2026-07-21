# SPEC: Hybrid Consensus Retrieval Strategy

**Status:** Approved  
**Feature Branch**: `007-hybrid-retrieval`  
**Created:** 2026-07-20  
**Approved:** 2026-07-20  
**Author:** Leonardo Alcantara  
**Issue:** [#127](https://github.com/alcantaraleo/archivist/issues/127) — feat(retrieval): Hybrid Consensus Retrieval Strategy

**Architecture decision:** [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md)

---

## 1. Motivation

Phase 2 of the Archivist roadmap lists **hybrid retrieval and re-ranking** as the remaining item after lexical (004), BM25 (005), and embedding (006). Each leg ranks evidence differently; no **golden query set** exists yet to pick a single winner or tuned fusion per query.

Operators who opt in should get **broader recall** by running all three legs and merging results **without** new MCP tools, without `port.in` changes, and **without** callers knowing which strategies ran or which leg produced a hit.

This specification adds a **`hybrid` retrieval strategy** that implements **multi-leg consensus ranking** per ADR-0006: count leg agreement, tie-break embedding → BM25 → lexical, cap results via `Query.maxResults()` and optional hybrid max. **QueryGovernor**-style query planning remains a **Phase 3** follow-on.

---

## Clarifications

### Session 2026-07-20

- Q: Fusion algorithm without eval data? → A: **Consensus vote** — score = number of legs (1–3) that returned the `sourceId` in their ranked lists for the same `Query`.
- Q: Tie on consensus score? → A: **embedding rank → BM25 rank → lexical rank** (lower index wins).
- Q: Result cap? → A: **`min(query.maxResults(), archivist.retrieval.hybrid.max-results)`**; global `max-results` default **20** unchanged; hybrid max default **20**, overridable via properties/env.
- Q: Separate ML rerank stage? → A: **No for v1** — consensus ordering **is** the rerank; no cross-encoder or LLM judge.
- Q: Per-leg pool size? → A: Each leg receives the **same** `Query` (including `maxResults` from use cases); no hidden larger internal pool in v1.
- Q: How is `Evidence` assembled after consensus ranks `sourceId`s? → A: For each id, take the **`Evidence` from the highest-priority leg that returned it**: **embedding**, then **BM25**, then **lexical**. Do **not** reload from `KnowledgeCorpus` when a leg already returned that id. `loadBySourceId` is used only if no leg returned an instance (must not occur for ids in **L ∪ B ∪ E**).
- Q: AC-12 fixture — corpus and stub? → A: **Isolated three-file corpus** copied from classpath (see JSON `testCorpus.copyFromClasspath`). Markers **`HYBRID_UNIVERSAL_MARKER`**, **`HYBRID_TWO_LEG_MARKER`**, **`HYBRID_ONE_LEG_MARKER`** with **`StubTextEmbedder`** overrides documented in [fixture-hybrid-consensus-scenario.json](contracts/fixture-hybrid-consensus-scenario.json). Expected hybrid order: universal (score 3) → two-leg (2) → one-leg (1).

---

## 2. Responsibilities

### What this specification delivers

- A **`hybrid` `RetrievalStrategy`** registered at startup with stable configuration name **`hybrid`**
- **Parallel fan-out** (implementation may use concurrent leg execution) to **`lexical`**, **`bm25`**, and **`embedding`** delegate strategies with identical **`Query`**
- **Consensus fusion** and **tie-break** exactly as [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md)
- **Nested configuration** `archivist.retrieval.hybrid.max-results` (default `20`, `@Min(1)`)
- **Startup validation** when `active-strategy=hybrid`: all three legs must be available; embedding config valid per spec 006
- **Infrastructure tests**: unit tests for scoring/tie-break/cap; integration test on fixture corpus; at least one **consensus fixture** where a triple-agreement doc outranks single-leg-only hits when `hybrid` is active
- **Regression**: default **`lexical`** unchanged; MCP contract tests (002) unchanged; specs 004–006 leg behaviour unchanged when those strategies are active individually
- **Operator docs**: quickstart + contracts; README Phase 2 hybrid checkbox updated when implemented

### What this specification does not do

- Does not add, remove, or rename any `port.in` capability or MCP tool
- Does not expose leg names, vote counts, or ranks on `Evidence` / MCP responses
- Does not implement RRF, learned weights, or LLM reranking
- Does not implement **QueryGovernor** or query-dependent leg skipping (Phase 3)
- Does not change `KnowledgeCorpus`, domain model, or application use case signatures
- Does not require a golden eval set (document deferral in ADR)
- Does not summarise, interpret, or answer on behalf of agents

---

## 3. Public Contract

### port.in (unchanged)

All eight capability signatures and MCP tool schemas remain identical to specs 001–002.

| Capability             | Parameter  | Retrieval intent (unchanged filters; ranking when `hybrid` active) |
| ---------------------- | ---------- | ------------------------------------------------------------------- |
| `retrieveContext`      | `query`    | Match across all types and zones                                    |
| `findDecisions`        | `topic`    | Restrict to `DECISION`                                              |
| `findProjects`         | `criteria` | Restrict to `PROJECT`                                               |
| `findPeople`           | `name`     | Restrict to `PERSON`                                                |
| `findConcepts`         | `topic`    | Restrict to `CONCEPT`, `SYNTHESIS`                                  |
| `findRelatedKnowledge` | `query`    | Unrestricted types/zones                                            |
| `findReadings`         | `topic`    | Restrict to `READING`                                               |
| `findDebriefs`         | `topic`    | Restrict to `DEBRIEF`                                               |

### port.out — `KnowledgeGateway` (behaviour extension)

Interface signature unchanged:

```java
public interface KnowledgeGateway {
    List<Evidence> retrieve(Query query);
}
```

**Semantics** when `archivist.retrieval.active-strategy=hybrid`:

| Condition                                         | Behaviour                                                                                       |
| ------------------------------------------------- | ----------------------------------------------------------------------------------------------- |
| `query` is null                                   | Throws `NullPointerException`                                                                   |
| `query.text()` is null, empty, or whitespace-only | Returns empty list (before leg fan-out)                                                         |
| No matching entries after fusion                  | Returns empty list                                                                              |
| Matching entries found                            | Deduplicated list (one `Evidence` per `sourceId`) ordered by consensus score desc, tie-break per ADR-0006; size ≤ `min(query.maxResults(), hybrid.max-results)` |
| Every returned `Evidence`                         | Includes non-null `Provenance` with all required fields                                         |

**Content availability**: unchanged (ADR-0003). Evidence content comes from corpus entry load, not leg-specific fragments.

### Configuration

| Property / variable                   | Purpose                  | Default   |
| ------------------------------------- | ------------------------ | --------- |
| `archivist.retrieval.active-strategy` | Active strategy `name()` | `lexical` |
| `archivist.retrieval.max-results`     | Use case `Query` cap     | `20`      |
| `ARCHIVIST_RETRIEVAL_MAX_RESULTS`     | Env override for above   | —         |
| `ARCHIVIST_SECOND_BRAIN_PATH`         | Corpus root (spec 003)   | required  |

**Hybrid nest** (`archivist.retrieval.hybrid.*`):

| Property      | Default | Notes                                      |
| ------------- | ------- | ------------------------------------------ |
| `max-results` | `20`    | Hybrid-only ceiling; see effective cap above |

Env (Spring relaxed binding): `ARCHIVIST_RETRIEVAL_HYBRID_MAX_RESULTS`.

Embedding/BM25 nests unchanged; **`hybrid` active** implies embedding embedder/store must be valid (006 fail-fast rules apply to the embedding leg).

### Error conditions

| Condition                              | Behaviour                                                |
| -------------------------------------- | -------------------------------------------------------- |
| Blank capability parameter             | MCP rejects; gateway returns empty list if invoked in tests |
| Unknown `active-strategy`              | Startup failure listing registered strategy names        |
| `hybrid` active but embedding leg misconfigured | Startup failure per 006                          |
| Corpus load failure                    | Propagates `KnowledgeCorpusException`                    |

---

## 4. User Scenarios & Testing

### Primary scenarios

1. **Operator enables hybrid** — Sets `active-strategy=hybrid`, valid corpus + embedding config; invokes `retrieveContext`; receives merged ranking with no new tools.
2. **Triple agreement wins** — Fixture where one `sourceId` appears in all three leg top lists ranks above ids with score 2 or 1 under `hybrid`.
3. **Tie-break** — Fixture or unit case: same consensus score; embedding rank decides order.
4. **Cap** — With `max-results=20` and `hybrid.max-results=20`, list length ≤ 20; lowering `hybrid.max-results` to 5 returns at most 5 even if `Query.maxResults()` is 20.
5. **Default lexical** — Without config change, behaviour matches pre-007 lexical baseline.
6. **Filters** — `findDecisions` under `hybrid` returns only `DECISION` entries (filters applied by each leg consistently; fusion preserves leg-valid hits only).

### Edge cases

- Id appears in only one leg → score 1; ordered after score 2 and 3
- Id absent from all legs → not returned
- Empty leg result (no tokens / no hits) → other legs still contribute
- Hybrid must not call `registry.getActive()` internally when hybrid is active

---

## 5. Acceptance Criteria

### Wiring and invariants

1. **AC-1**: `./gradlew build` succeeds; MCP contract regression tests (002) pass unchanged
2. **AC-2**: Transport module Java sources import no types from `io.archivist.infrastructure.*`
3. **AC-3**: With `active-strategy=lexical` (default), fixture-based retrieval matches pre-007 baseline
4. **AC-4**: Startup with `active-strategy=hybrid` succeeds when corpus and embedding config are valid; registry lists `hybrid` among strategy names

### Hybrid retrieval

5. **AC-5**: Given fixture corpus and `active-strategy=hybrid`, `retrieveContext` returns at least one `Evidence` with complete provenance for a known matching query
6. **AC-6**: `findDecisions` under hybrid returns only `DECISION` entries
7. **AC-7**: At most one `Evidence` per `sourceId`
8. **AC-8**: Result list size ≤ `min(query.maxResults(), hybrid.max-results)`
9. **AC-9**: Unit test proves consensus ordering: score 3 before 2 before 1 for constructed leg lists

### Tie-break and opacity

10. **AC-10**: Unit test proves tie-break order embedding → BM25 → lexical
11. **AC-11**: Returned `Evidence` / MCP payloads do not include leg identifiers or vote counts (inspect serializers/adapters as needed)

### Consensus fixture

12. **AC-12**: Integration test driven by `contracts/fixture-hybrid-consensus-scenario.json` — isolated corpus under `infrastructure/src/test/resources/fixture-corpus/wiki/concepts/hybrid-consensus-*.md`; stub overrides per JSON; full hybrid order matches `expected.orderingByConsensusScore`

### Ops and docs

13. **AC-13**: Unknown `active-strategy` still fails fast (registry unchanged)
14. **AC-14**: Operator docs describe cost trade-off, consensus policy, caps, env vars; reference ADR-0006
15. **AC-15**: README roadmap Phase 2 hybrid item checked with pointer to ADR-0006 and quickstart

### Unchanged

16. **AC-16**: No changes to `domain.port.in`, MCP tool registration, transport handlers, or application use cases beyond existing `max-results` wiring

---

## 6. Success Criteria

1. Operators can enable hybrid **via configuration only** without MCP changes.
2. Consensus policy matches ADR-0006 without requiring a golden eval set.
3. MCP tools and schemas remain identical to spec 002.
4. Default deployments stay **lexical** until `active-strategy=hybrid`.
5. Documentation states QueryGovernor may supersede full fan-out later.

---

## 7. Domain Model Impact

### Unchanged

- `Evidence`, `Provenance`, `Query`, `KnowledgeType`, `KnowledgeZone`, `ContentAvailability`
- All eight `port.in` interfaces
- `KnowledgeGateway` and `KnowledgeCorpus` signatures

### Does not introduce

- New public capabilities or MCP tools
- Leg attribution fields on domain types

---

## 8. Architectural Impact

### port.in / application / transport

No signature or wiring changes. Use cases continue to build `Query` with `archivist.retrieval.max-results`.

### infrastructure (retrieval)

| Component | Responsibility |
| --------- | -------------- |
| `HybridRetrievalStrategy` | `name()` → `hybrid`; fan-out to three delegates; consensus + tie-break + cap |
| `HybridProperties` | `max-results` nest under `RetrievalProperties` |
| `RetrievalConfiguration` | Register hybrid bean; inject lexical, BM25, embedding strategies + properties |
| `HybridConsensusRanker` (or inline) | Pure scoring/tie-break logic (testable without corpus) |

Delegates reuse existing `LexicalRetrievalStrategy`, `Bm25RetrievalStrategy`, `EmbeddingRetrievalStrategy` — no duplicate ranking logic.

### Dependency direction

Hybrid lives in `infrastructure.retrieval` only. Delegates must not depend on hybrid.

---

## 9. References

- [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md)
- [spec 004](../004-lexical-retrieval/spec.md) · [spec 005](../005-bm25-retrieval/spec.md) · [spec 006](../006-embedding-retrieval/spec.md)
- [retrieval-strategy-spi (004)](../004-lexical-retrieval/contracts/retrieval-strategy-spi.md) — hybrid composition note
