# SPEC: BM25 Retrieval Strategy

**Status:** Approved
**Feature Branch**: `005-bm25-retrieval`
**Created:** 2026-07-20
**Author:** Leonardo Alcantara
**Issue:** _TBD — epic to be opened before approval_

---

## 1. Motivation

Phase 2 of the Archivist roadmap calls for **retrieval evolution** beyond Phase 1 lexical matching. Spec 004 delivered a **lexical** strategy (substring token scoring over corpus entries), the `KnowledgeGateway`, real use cases, and a **private** `RetrievalStrategy` registry so algorithms can change without touching MCP tools or `port.in` interfaces.

Consuming agents still need **better relevance ranking** on multi-term queries and longer prose in a personal Second Brain. Lexical weights (title + tags + body) are a useful baseline but do not model term frequency and document length the way classical **BM25** does. Agents benefit when the same eight domain capabilities return **more appropriately ordered evidence** without learning new tools or parameters.

This specification adds a **`bm25` retrieval strategy** as an opt-in configuration value. The **public MCP contract and all capability signatures stay unchanged**. Transport continues to invoke `port.in` only; strategy selection remains infrastructure-internal per Constitution V.

---

## Clarifications

### Session 2026-07-20

- Q: In-process search/index library vs custom inverted index? → A: **Apache Lucene** (in-process, ephemeral in-memory directory) in `infrastructure` only; standard BM25 similarity — no public Lucene types leak from infrastructure module API.
- Q: Index lifecycle — rebuild every retrieve vs once per process vs refresh hook? → A: **Per-process in-memory index** with invalidation per [ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md): rebuild when **corpus fingerprint** changes **or** **TTL** expires; reuse index otherwise (long-lived STDIO MCP).
- Q: BM25 field weights vs Phase 1 lexical (title/tags/body)? → A: **Preserve 3 : 2 : 1 relative priority** — default field boosts title **3**, tags **2**, body **1** (same as `LexicalScorer` in spec 004); tags indexed as one searchable field; overridable via `archivist.retrieval.bm25.field-boost-*`.

---

## 2. Responsibilities

### What this specification delivers

- A second **`RetrievalStrategy` implementation** registered at startup with stable configuration name **`bm25`**
- **BM25-based ranking** over searchable corpus text, honouring the same **`Query`** filters (`KnowledgeType`, `KnowledgeZone`, `maxResults`) and gateway blank-text semantics as spec 004
- **Search index construction** from `KnowledgeCorpus` with **per-process cache** invalidated by fingerprint and TTL ([ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md))
- **Field-aware indexing** aligned with Phase 1 intent: searchable **title**, **tags**, and **body** where content is available; title/tag-only indexing for size-limited entries per [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md)
- **Deduplication** by `sourceId`, provenance on every returned `Evidence`, and configurable global max results (unchanged property)
- **Infrastructure integration tests** on the spec 003 fixture corpus plus **ranking fixtures** where BM25 ordering is expected to differ from lexical for the same query
- **Regression**: MCP contract tests (002) and transport layer isolation unchanged; default active strategy remains **`lexical`**

### What this specification does not do

- Does not add, remove, or rename any `port.in` capability or MCP tool
- Does not expose retrieval technology in the public contract (`bm25Search`, `grep`, etc.)
- Does not implement embedding, hybrid, graph, or ML re-ranking strategies (later Phase 2–3 items)
- Does not change `KnowledgeCorpus` interface semantics or Second Brain adapter behaviour (spec 003)
- Does not implement persistent on-disk indexes, filesystem watchers, or hot reload without process restart ([ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md) follow-on may extend this later)
- Does not summarise, interpret, or answer on behalf of agents
- Does not write or modify Second Brain files
- Does not require Obsidian or any editor to be running

---

## 3. Public Contract

### port.in (unchanged)

All eight capability signatures and MCP tool schemas remain identical to specs 001–002. No transport handler changes.

| Capability             | Parameter  | Retrieval intent (unchanged filters; ranking algorithm when `bm25` active) |
| ---------------------- | ---------- | -------------------------------------------------------------------------- |
| `retrieveContext`      | `query`    | Match across all types and zones                                           |
| `findDecisions`        | `topic`    | Restrict to `DECISION`                                                     |
| `findProjects`         | `criteria` | Restrict to `PROJECT`                                                      |
| `findPeople`           | `name`     | Restrict to `PERSON`                                                       |
| `findConcepts`         | `topic`    | Restrict to `CONCEPT`, `SYNTHESIS`                                         |
| `findRelatedKnowledge` | `query`    | Unrestricted types/zones (graph traversal still deferred to Phase 3)       |
| `findReadings`         | `topic`    | Restrict to `READING`                                                      |
| `findDebriefs`         | `topic`    | Restrict to `DEBRIEF`                                                      |

### port.out — `KnowledgeGateway` (behaviour extension)

Interface signature unchanged:

```java
public interface KnowledgeGateway {
    List<Evidence> retrieve(Query query);
}
```

**Semantics** (same as spec 004 except ranking when `archivist.retrieval.active-strategy=bm25`):

| Condition                                         | Behaviour                                                                                      |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| `query` is null                                   | Throws `NullPointerException`                                                                  |
| `query.text()` is null, empty, or whitespace-only | Returns empty list                                                                             |
| No matching entries                               | Returns empty list                                                                             |
| Matching entries found                            | Returns deduplicated list ordered by descending BM25 relevance; size ≤ `query.maxResults()`    |
| Every returned `Evidence`                         | Includes non-null `Provenance` with all required fields                                        |

**Content availability** (same policy as lexical — [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md)):

- `AVAILABLE`: index and match title, tags, and body
- `UNAVAILABLE_ENTRY_TOO_LARGE`: index and match title and tags only; include in results when those fields match; body remains empty in evidence

### Configuration

| Property / variable                   | Purpose                              | Default   |
| ------------------------------------- | ------------------------------------ | --------- |
| `archivist.retrieval.active-strategy` | Active strategy `name()`             | `lexical` |
| `archivist.retrieval.max-results`     | Default max evidence per query       | `20`      |
| `ARCHIVIST_SECOND_BRAIN_PATH`         | Corpus root (spec 003)               | required  |

When `active-strategy=bm25`, startup MUST register both `lexical` and `bm25` strategies; unknown or duplicate strategy names MUST fail fast (same registry rules as spec 004).

| Property | Purpose | Default |
| -------- | ------- | ------- |
| `archivist.retrieval.bm25.field-boost-title` | Lucene boost for title field | `3.0` |
| `archivist.retrieval.bm25.field-boost-tags` | Lucene boost for tags field (concatenated tags) | `2.0` |
| `archivist.retrieval.bm25.field-boost-body` | Lucene boost for body field | `1.0` |
| `archivist.retrieval.bm25.index-ttl` | Max age of cached index before rebuild | plan defines default (ADR-0004) |
| `archivist.retrieval.bm25.k1` | BM25 k1 parameter (Lucene similarity) | library default (plan documents value) |
| `archivist.retrieval.bm25.b` | BM25 b parameter (Lucene similarity) | library default (plan documents value) |

Query tokenisation for BM25 MUST use the **same rules as spec 004 lexical tokenisation** (lowercase; split on whitespace and Unicode punctuation) so operator expectations and AC-11 comparisons stay aligned.

Corpus **fingerprint** inputs and algorithm are defined in plan/research (ADR-0004 Option 2); MUST reflect catalog-visible corpus state.

### Error conditions

| Condition                                     | Behaviour                                                    |
| --------------------------------------------- | ------------------------------------------------------------ |
| Blank capability parameter                    | MCP rejects; gateway returns empty list if invoked in tests  |
| Unknown `active-strategy`                     | Startup failure with message listing registered strategy names |
| Index build or corpus load failure            | Propagates `KnowledgeCorpusException`; not swallowed         |

---

## 4. User Scenarios & Testing

### Primary scenarios

1. **Operator enables BM25** — Sets `archivist.retrieval.active-strategy=bm25`, starts MCP server with valid corpus path, invokes `retrieveContext` with a multi-word query; receives ordered evidence with provenance (no new tools).
2. **Agent uses capability filters** — Invokes `findDecisions("architecture")`; receives only `DECISION` entries relevant to the query, ranked by BM25 when that strategy is active.
3. **Default remains lexical** — With no configuration change after upgrade, behaviour matches pre-005 lexical results for the same fixture queries (regression).
4. **Oversized entry discoverability** — Query matches title of a size-limited fixture entry; entry appears with correct `contentAvailability` and empty body.

### Edge cases

- Query terms that appear only in body of an `UNAVAILABLE_ENTRY_TOO_LARGE` entry → excluded
- Query with no corpus matches → empty list, no error
- Duplicate `sourceId` in candidate set → at most one evidence in response
- Corpus with zero indexable entries after filters → empty list

---

## 5. Acceptance Criteria

> Numbered, verifiable conditions. Each must be independently testable.

### Wiring and invariants

1. **AC-1**: `./gradlew build` succeeds; MCP contract regression tests (002) pass without schema or tool registration changes
2. **AC-2**: Transport module Java sources import no types from `io.archivist.infrastructure.*`
3. **AC-3**: With `archivist.retrieval.active-strategy=lexical` (default), fixture-based retrieval behaviour matches pre-005 baseline (no regression)
4. **AC-4**: Startup with `active-strategy=bm25` succeeds when corpus path is valid; registry reports both `lexical` and `bm25` as registered names

### BM25 retrieval

5. **AC-5**: Given fixture corpus and `active-strategy=bm25`, `retrieveContext` with a term known to exist returns at least one matching `Evidence` with complete provenance
6. **AC-6**: Given `findDecisions` against fixture corpus, every result has `KnowledgeType.DECISION` and matches the query under BM25 rules
7. **AC-7**: Given `findConcepts`, results are only `CONCEPT` or `SYNTHESIS`
8. **AC-8**: Given `findPeople` against fixture corpus, results are only `PERSON` type
9. **AC-9**: Results contain at most one `Evidence` per `sourceId`
10. **AC-10**: Result list size ≤ configured `maxResults` (default 20)

### Ranking differentiation

11. **AC-11**: Given a dedicated fixture scenario (documented in plan/contracts) where term frequency should prefer one entry over another, **`bm25` active ordering differs from `lexical` active ordering** for the same `Query` text and filters

### Content availability

12. **AC-12**: Size-limited fixture entry: title match includes entry with `UNAVAILABLE_ENTRY_TOO_LARGE` and empty content
13. **AC-13**: Size-limited entry with no title/tag match is excluded even if body text would match

### Performance smoke

14. **AC-14**: Index build plus BM25 query against fixture corpus completes in under **5 seconds** on CI hardware (smoke guard; not a production SLA for full personal vaults)

### Extension point (unchanged from 004)

15. **AC-15**: No changes to `domain.port.in` interfaces, MCP tool registration, transport handlers, or application use case logic beyond what 004 already established

---

## 6. Success Criteria

1. Operators can switch to BM25 ranking **using configuration only**, without editing agent MCP tool lists.
2. For multi-term queries on the fixture corpus, agents receive evidence ordered by a **documented BM25 relevance policy** that improves on lexical ordering in at least one controlled scenario (AC-11).
3. **100%** of MCP tools and JSON schemas remain identical to spec 002 fixtures after implementation.
4. Default deployments behave as today (**lexical**) until `active-strategy` is explicitly set to `bm25`.
5. Every retrieved item remains **traceable** via full provenance (no anonymous hits).

---

## 7. Domain Model Impact

### Unchanged

- `Evidence`, `Provenance`, `KnowledgeType`, `KnowledgeZone`, `ContentAvailability`, `Query`
- All eight `port.in` interfaces
- `KnowledgeGateway` and `KnowledgeCorpus` interface signatures

### Does not introduce

- New public capabilities or MCP tools
- Public retrieval strategy types in the domain layer
- New `port.out` interfaces beyond existing gateway behaviour

---

## 8. Architectural Impact

### port.in / application / transport

No signature or wiring changes from spec 004. Use cases continue to build `Query` and call `KnowledgeGateway`.

### infrastructure (retrieval)

New private components (names illustrative; plan finalises packages):

| Component                   | Responsibility                                                                 |
| --------------------------- | ------------------------------------------------------------------------------ |
| `Bm25RetrievalStrategy`     | Implements `RetrievalStrategy`; `name()` returns `"bm25"`                      |
| Lucene index builder (private) | Loads corpus via `KnowledgeCorpus`; caches RAM index per JVM; invalidates per ADR-0004 |
| `RetrievalConfiguration`  | Registers BM25 strategy bean alongside lexical                                 |

**Dependency:** Apache Lucene JAR(s) scoped to **`infrastructure` module only** (Gradle `implementation`); domain and application MUST NOT depend on Lucene.

`KnowledgeGatewayImpl` and `RetrievalStrategyRegistry` unchanged in responsibility; registry collects one additional strategy bean.

### Dependency direction

Unchanged from 004: application → `KnowledgeGateway`; infrastructure retrieval → `KnowledgeCorpus`; transport → `port.in` only.

---

## 9. Out of Scope

- Embedding and vector retrieval
- Hybrid fusion and learned re-ranking
- Knowledge graph traversal for `findRelatedKnowledge`
- Persistent cross-restart index storage
- Corpus filesystem watchers and incremental index updates
- Changing MCP tool names, descriptions, or JSON schemas
- Production personal vault as CI dependency (fixture corpus only)
- REST or CLI transport

---

## 10. Assumptions

- Specs 003 and 004 are implemented: real `KnowledgeCorpus`, lexical strategy, gateway, and use cases
- Personal Second Brain size remains modest; **per-process BM25 index cache** with fingerprint + TTL invalidation ([ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md)); filesystem watchers deferred to a future indexing spec
- Tokenisation matches spec 004 `LexicalScorer` rules (see Configuration §3)
- Default field boosts **3 / 2 / 1** (title / tags / body) preserve Phase 1 lexical priority
- `findRelatedKnowledge` tool description remains unchanged; BM25 does not imply graph semantics
- Ranking comparison tests (AC-11) use checked-in fixture entries crafted in this spec kit, not a contributor’s private vault

---

## 11. Open Questions

None remaining — resolved in [Clarifications](#clarifications) (2026-07-20). Implementation details (exact Lucene artifact coordinates, analyzer choice, k1/b defaults) are documented in **`research.md`** during `/speckit-plan`.

| Topic | Resolution |
| ----- | ---------- |
| Index library | Apache Lucene, in-process RAM index, infrastructure-only |
| Index lifecycle | Per-process cache; invalidate on fingerprint change or TTL expiry ([ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md)); Option 4 watcher deferred |
| Field weights | Default boosts title 3, tags 2, body 1 (configurable) |

---

## 12. Approval

| Reviewer           | Decision | Date       |
| ------------------ | -------- | ---------- |
| Leonardo Alcantara | Approved | 2026-07-20 |
