# SPEC: Embedding-Based Retrieval Strategy

**Status:** Approved
**Feature Branch**: `006-embedding-retrieval`
**Created:** 2026-07-20
**Author:** Leonardo Alcantara
**Issue:** TBD — feat(retrieval): Embedding-Based Retrieval Strategy

---

## 1. Motivation

Phase 2 of the Archivist roadmap calls for **retrieval evolution** beyond lexical and BM25 matching. Specs 004 and 005 delivered **lexical** and **BM25** strategies behind a private `RetrievalStrategy` registry so algorithms can change without touching MCP tools or `port.in` interfaces.

Consuming agents still miss relevant Second Brain entries when wording differs from the corpus (synonyms, paraphrase, conceptual match). Term-based ranking does not capture that similarity. Agents benefit when the same eight domain capabilities return **semantically closer evidence** without learning new tools or parameters.

This specification adds an **`embedding` retrieval strategy** as an opt-in configuration value. Embeddings are produced by a **pluggable embedder** (local model or OpenAI-compatible HTTP endpoint) and stored/searched via a **pluggable vector store** (first implementation: local in-memory; later adapters such as pgvector or ChromaDB without public-contract change). The **public MCP contract and all capability signatures stay unchanged**. Transport continues to invoke `port.in` only; strategy and adapter selection remain infrastructure-internal per Constitution V.

---

## Clarifications

### Session 2026-07-20 (pre-specify intent)

- Q: Vector persistence — single hard-wired store? → A: **No.** Private **vector store SPI** in infrastructure; first adapter **`memory`** (process-local). Future adapters (`pgvector`, `chromadb`, …) MUST plug in via configuration without changing `port.in`, MCP, or application use cases.
- Q: Embedding model — single vendor? → A: **No.** Private **embedder SPI** in infrastructure; first adapters **`local`** and **`openai-compatible`** (HTTP API compatible with OpenAI embeddings shape). Selected by configuration.
- Q: Operator examples for optional companion services? → A: **Yes.** Ship a **docker-compose template** with example services operators can adapt. Compose is not required for the default Archivist path, lexical/BM25, or in-memory embedding CI.

### Session 2026-07-20 (specify clarifications)

- Q: Default embedder when `active-strategy=embedding`? → A: **`local`** — offline-capable personal/STDIO default once a model is configured; `openai-compatible` remains a first-class opt-in adapter.
- Q: Embedding unit — whole entry vs chunks? → A: **Chunks** — multiple vectors per knowledge entry; retrieval still **deduplicates to at most one `Evidence` per `sourceId`** (best chunk score wins unless plan documents a better aggregation).
- Q: Compose template scope for this spec? → A: **OpenAI-compatible embeddings example plus commented/placeholder vector DB services** (pgvector and/or Chroma) for future stores — placeholders MUST NOT imply that those store adapters ship in 006.

---

## 2. Responsibilities

### What this specification delivers

- An **`embedding` `RetrievalStrategy`** registered at startup with stable configuration name **`embedding`**
- **Embedding-based ranking** over searchable corpus text: **chunk** corpus entries, embed chunks, embed the query, rank by vector similarity, honouring the same **`Query`** filters (`KnowledgeType`, `KnowledgeZone`, `maxResults`) and gateway blank-text semantics as specs 004–005
- A private **embedder SPI** so the model/endpoint is interchangeable; first adapters:
  - **`local`** — on-box / process-local embedding model (**default** when `active-strategy=embedding`)
  - **`openai-compatible`** — HTTP API with OpenAI-compatible embeddings request/response shape (base URL, model, credentials via config)
- A private **vector store SPI** so the persistence/search backend is interchangeable; first adapter:
  - **`memory`** — process-local in-memory store suitable for STDIO MCP and CI (**default** store)
- **Configuration-only** selection of active strategy, embedder, and vector store (and their settings); no MCP/tool parameters for provider or store choice
- **Index / corpus sync policy** for the in-memory store aligned with fingerprint + TTL intent from [ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md), plus rebuild when embedder identity (model id / dimensions) changes; persistent store adapters may define sync details in follow-on work
- **Content availability** policy per [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md): size-limited entries contribute title/tags only; body remains empty in evidence when unavailable
- **Deduplication** by `sourceId` after chunk-level scoring (at most one `Evidence` per entry), provenance on every returned `Evidence`, configurable global max results
- A **docker-compose template** in-repo with an OpenAI-compatible embeddings example **and** commented/placeholder vector DB services (pgvector and/or Chroma) for future adapters
- **Operator documentation** that clearly explains pluggable embedders, pluggable stores, chunking + dedupe behaviour, defaults (`embedding` → `local` + `memory`), and Compose examples; **`/speckit-plan` MUST** enumerate the doc set (README / quickstart / contracts) and **require an ADR** for architectural decisions (at minimum: pluggable embedder + vector-store SPIs, and chunking with `sourceId` dedupe — one ADR or split if research prefers)
- **Infrastructure tests** on the fixture corpus plus at least one **ranking fixture** where embedding order is expected to differ from lexical and/or BM25 for the same query (paraphrase / synonym scenario), exercisable **without network**
- **Regression**: MCP contract tests (002) unchanged; default active strategy remains **`lexical`**

### What this specification does not do

- Does not add, remove, or rename any `port.in` capability or MCP tool
- Does not expose retrieval technology in the public contract (`vectorSearch`, `embed`, `bm25Search`, etc.)
- Does not hard-wire a single embedding vendor or a single vector database into domain or application layers
- Does not implement production adapters for pgvector, ChromaDB, or other stores in this spec — only the **SPI + first `memory` store** and documentation of how future stores plug in
- Does not implement hybrid fusion or ML re-ranking (next Phase 2 item; may compose this strategy later)
- Does not implement knowledge graph traversal (Phase 3)
- Does not change `KnowledgeCorpus` interface semantics or Second Brain adapter behaviour (spec 003)
- Does not require Docker, Compose, pgvector, or ChromaDB for lexical, BM25, or in-memory embedding CI
- Does not require a contributor’s private vault or a live cloud API in CI
- Does not summarise, interpret, or answer on behalf of agents
- Does not write or modify Second Brain files

---

## 3. Public Contract

### port.in (unchanged)

All eight capability signatures and MCP tool schemas remain identical to specs 001–002. No transport handler changes.

| Capability             | Parameter  | Retrieval intent (unchanged filters; ranking when `embedding` active) |
| ---------------------- | ---------- | --------------------------------------------------------------------- |
| `retrieveContext`      | `query`    | Match across all types and zones                                      |
| `findDecisions`        | `topic`    | Restrict to `DECISION`                                                |
| `findProjects`         | `criteria` | Restrict to `PROJECT`                                                 |
| `findPeople`           | `name`     | Restrict to `PERSON`                                                  |
| `findConcepts`         | `topic`    | Restrict to `CONCEPT`, `SYNTHESIS`                                    |
| `findRelatedKnowledge` | `query`    | Unrestricted types/zones (graph traversal still deferred)             |
| `findReadings`         | `topic`    | Restrict to `READING`                                                 |
| `findDebriefs`         | `topic`    | Restrict to `DEBRIEF`                                                 |

### port.out — `KnowledgeGateway` (behaviour extension)

Interface signature unchanged:

```java
public interface KnowledgeGateway {
    List<Evidence> retrieve(Query query);
}
```

**Semantics** (same as spec 004 except ranking when `archivist.retrieval.active-strategy=embedding`):

| Condition                                         | Behaviour                                                                                  |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------ |
| `query` is null                                   | Throws `NullPointerException`                                                              |
| `query.text()` is null, empty, or whitespace-only | Returns empty list                                                                         |
| No matching entries                               | Returns empty list                                                                         |
| Matching entries found                            | Deduplicated list (one `Evidence` per `sourceId`) ordered by descending embedding similarity after chunk scoring; size ≤ `query.maxResults()` |
| Every returned `Evidence`                         | Includes non-null `Provenance` with all required fields                                    |

**Content availability** (same policy as lexical/BM25 — ADR-0003):

- `AVAILABLE`: contribute title, tags, and body into chunk text per embedding-unit policy below
- `UNAVAILABLE_ENTRY_TOO_LARGE`: contribute title and tags only; body remains empty in evidence

### Configuration

| Property / variable                   | Purpose                  | Default   |
| ------------------------------------- | ------------------------ | --------- |
| `archivist.retrieval.active-strategy` | Active strategy `name()` | `lexical` |
| `archivist.retrieval.max-results`     | Default max evidence     | `20`      |
| `ARCHIVIST_SECOND_BRAIN_PATH`         | Corpus root (spec 003)   | required  |

**Strategy + SPI selection** (property names illustrative — plan finalises):

| Property                                       | Purpose                                                          | Default when strategy=`embedding` |
| ---------------------------------------------- | ---------------------------------------------------------------- | --------------------------------- |
| `archivist.retrieval.embedding.store`          | Vector store adapter id (`memory`; later `pgvector`, `chromadb`) | `memory`                          |
| `archivist.retrieval.embedding.embedder`       | Embedder adapter id (`local`, `openai-compatible`, …)            | `local`                           |
| `archivist.retrieval.embedding.dimensions`     | Expected vector size; fail on mismatch                           | plan documents                    |
| `archivist.retrieval.embedding.top-k`          | Candidate pool before filter/dedupe                              | plan documents                    |
| `archivist.retrieval.embedding.min-score`      | Optional similarity floor (or disabled)                          | disabled unless plan sets default |
| `archivist.retrieval.embedding.index-ttl`      | In-memory cache TTL (ADR-0004-style)                             | plan documents                    |

**Embedder: `openai-compatible`** (illustrative):

| Property / env | Purpose |
| -------------- | ------- |
| Base URL       | OpenAI or compatible API base |
| Model name     | Embedding model identifier |
| API key / env  | Credential (never logged) |

**Embedder: `local`** (illustrative):

| Property | Purpose |
| -------- | ------- |
| Model path or model id | On-box model location / bundle reference |

**Store: future adapters** — each adds its own config namespace (e.g. JDBC URL for pgvector, Chroma host). This spec reserves the `store` discriminator and ships `memory`.

Unknown `store` or `embedder` id when `active-strategy=embedding` → **startup failure** listing registered adapter names (same fail-fast spirit as the strategy registry).

Misconfigured or unavailable embedder when `embedding` is active → **fail fast at startup**, not silent empty or random results.

### Error conditions

| Condition                              | Behaviour                                                      |
| -------------------------------------- | -------------------------------------------------------------- |
| Blank capability parameter             | MCP rejects; gateway returns empty list if invoked in tests    |
| Unknown `active-strategy`              | Startup failure listing registered strategy names              |
| Unknown embedder or store id           | Startup failure listing registered adapter ids                 |
| Embedder unavailable / misconfigured   | Fail fast at startup when `embedding` is active                |
| Index build or corpus load failure     | Propagates `KnowledgeCorpusException`; not swallowed           |

### Embedding unit

Corpus entries are split into **chunks** before embedding (chunk size / overlap / field composition finalised in plan). Multiple vectors may share one `sourceId`. After similarity search and type/zone filters, results **deduplicate to at most one `Evidence` per `sourceId`** (default aggregation: best chunk score wins unless plan documents otherwise). Returned `Evidence` content/provenance remain entry-level (not a chunk fragment API).

---

## 4. User Scenarios & Testing

### Primary scenarios

1. **Operator enables embeddings** — Sets `active-strategy=embedding` (defaults: embedder=`local`, store=`memory`), configures local model path/id, starts MCP with valid corpus, invokes `retrieveContext` with a paraphrase of fixture content; receives ordered evidence with provenance (no new tools).
2. **Agent uses capability filters** — Invokes `findDecisions("…")`; receives only `DECISION` entries, ranked by embedding similarity when that strategy is active.
3. **Default remains lexical** — With no configuration change after upgrade, behaviour matches pre-006 lexical results for the same fixture queries.
4. **Swap embedder via config** — Operator switches from default `local` to `openai-compatible` (or a future embedder id) without MCP tool or `port.in` changes; invalid id fails at startup.
5. **Compose examples** — Operator copies the docker-compose template, starts an example OpenAI-compatible embeddings endpoint (vector DB services remain commented placeholders), points Archivist at the published URL with `embedder=openai-compatible`, and retrieves evidence (optional path; not required for CI).
6. **Oversized entry discoverability** — Query matches title/tag semantics of a size-limited fixture entry; entry appears with correct `contentAvailability` and empty body.
7. **Chunked long entry** — A long fixture entry yields multiple indexed chunks; a query matching one chunk returns at most one `Evidence` for that `sourceId`.

### Edge cases

- Query with no near neighbours above threshold (if threshold enabled) → empty list
- Multiple chunks from the same `sourceId` in the candidate set → at most one evidence
- Model/version or dimensions change → in-memory index rebuild (stale vectors must not be reused silently)
- Corpus with zero indexable entries after filters → empty list
- Future store id configured before adapter exists → startup failure (fail fast)

---

## 5. Acceptance Criteria

> Numbered, verifiable conditions. Each must be independently testable.

### Wiring and invariants

1. **AC-1**: `./gradlew build` succeeds; MCP contract regression tests (002) pass without schema or tool registration changes
2. **AC-2**: Transport module Java sources import no types from `io.archivist.infrastructure.*`
3. **AC-3**: With `active-strategy=lexical` (default), fixture-based retrieval behaviour matches pre-006 baseline
4. **AC-4**: Startup with `active-strategy=embedding` succeeds when corpus, embedder, and store config are valid; registry includes `embedding` among registered strategy names

### Embedding retrieval

5. **AC-5**: Given fixture corpus and `active-strategy=embedding`, `retrieveContext` with text known to match a fixture entry (including a **paraphrase** case) returns at least one matching `Evidence` with complete provenance
6. **AC-6**: `findDecisions` results are only `DECISION` and pass the same filter semantics as 004/005
7. **AC-7**: `findConcepts` results are only `CONCEPT` or `SYNTHESIS`
8. **AC-8**: `findPeople` results are only `PERSON`
9. **AC-9**: At most one `Evidence` per `sourceId`
10. **AC-10**: Result list size ≤ configured `maxResults` (default 20)

### Ranking differentiation

11. **AC-11**: Dedicated fixture scenario where semantic similarity should prefer one entry over another: **`embedding` active ordering differs from `lexical` and/or `bm25`** for the same `Query` text and filters

### Content availability

12. **AC-12**: Size-limited fixture entry: title/tag match includes entry with `UNAVAILABLE_ENTRY_TOO_LARGE` and empty content
13. **AC-13**: Size-limited entry with no title/tag signal is excluded even if body text would match

### Pluggability and ops

14. **AC-14**: Embedding retrieval path is exercisable in CI **without network** (stub/fake embedder and in-memory store — exact mechanism in plan)
15. **AC-15**: `EmbeddingRetrievalStrategy` depends only on private embedder + vector-store SPIs, not on a concrete database or HTTP client type beyond config wiring
16. **AC-16**: Startup with unknown `archivist.retrieval.embedding.store` or `.embedder` fails fast with registered ids listed
17. **AC-17**: Repository includes a **docker-compose example/template** with (a) an OpenAI-compatible embeddings service example and (b) **commented/placeholder** vector DB services for pgvector and/or Chroma; short operator notes explain wiring; default Archivist build/tests do not require Compose to be up
18. **AC-18**: Documented extension points state that a future `pgvector` / `chromadb` (or similar) adapter is expected to land as a new vector-store implementation + config id **without** MCP or `port.in` changes
19. **AC-19**: Operator-facing docs clearly state defaults (`active-strategy=embedding` → embedder=`local`, store=`memory`), chunking + `sourceId` dedupe behaviour, and how to switch embedder/store; placeholders in Compose are labelled as future/non-shipping adapters
20. **AC-20**: At least one **ADR** under `docs/adr/` records the architectural decisions for pluggable embedder + vector-store SPIs and for chunking with entry-level dedupe (single or multiple ADRs as plan decides)

### Extension point

21. **AC-21**: No changes to `domain.port.in`, MCP tool registration, transport handlers, or application use cases beyond what 004 already established

### Chunking

22. **AC-22**: Given a fixture entry that produces multiple chunks, a query that matches only one chunk returns at most one `Evidence` for that entry’s `sourceId`

---

## 6. Success Criteria

1. Operators can switch to embedding ranking **using configuration only**, without editing agent MCP tool lists.
2. For at least one controlled paraphrase/synonym fixture scenario, agents receive evidence ordered by a **documented embedding similarity policy** that differs from term matching (AC-11).
3. **100%** of MCP tools and JSON schemas remain identical to spec 002 fixtures after implementation.
4. Default deployments behave as today (**lexical**) until `active-strategy` is explicitly set to `embedding`.
5. Every retrieved item remains **traceable** via full provenance.
6. Activating `embedding` without a usable embedder/store configuration fails **loudly at startup**, not with silent empty or random results.
7. Operators can change **embedder** and (later) **vector store** via configuration; this release proves store interchangeability by shipping a real `memory` adapter behind a stable SPI and documenting how pgvector/Chroma-class backends would plug in.
8. A Compose **template** exists so operators can try an embeddings endpoint example and see placeholder vector DB services without baking one vendor into the domain.
9. Documentation and ADR(s) make the pluggable design, defaults, and chunking/dedupe rules discoverable without reading infrastructure source.

---

## 7. Domain Model Impact

### Unchanged

- `Evidence`, `Provenance`, `KnowledgeType`, `KnowledgeZone`, `ContentAvailability`, `Query`
- All eight `port.in` interfaces
- `KnowledgeGateway` and `KnowledgeCorpus` interface signatures

### Does not introduce

- New public capabilities or MCP tools
- Public embedding/vector types in the domain layer
- New `port.out` interfaces beyond existing gateway behaviour

---

## 8. Architectural Impact

### port.in / application / transport

No signature or wiring changes from spec 004. Use cases continue to build `Query` and call `KnowledgeGateway`.

### infrastructure (retrieval)

New private components (names illustrative; plan finalises packages):

| Component | Responsibility |
| --------- | -------------- |
| `EmbeddingRetrievalStrategy` | Implements `RetrievalStrategy`; `name()` returns `embedding`; orchestrates embedder + store; applies Query filters, dedupe, provenance |
| Embedder SPI (private) | Text → vector(s); infrastructure-only |
| `LocalEmbeddingModel` | First local embedder adapter |
| `OpenAiCompatibleEmbeddingModel` | HTTP OpenAI-compatible embedder adapter |
| Vector store SPI (private) | Upsert / search / invalidate (exact methods in plan); backend-agnostic |
| `InMemoryVectorStore` | First `memory` store implementation |
| Adapter registries / config wiring | Resolve `store` + `embedder` ids; fail fast on unknown |
| `RetrievalConfiguration` | Registers strategy + default adapters |

**Extension checklist (future store / embedder)** — analogous to the RetrievalStrategy SPI from spec 004:

1. Implement private SPI in `infrastructure`
2. Register bean with stable config id
3. Document properties + optional Compose service snippet
4. No changes to `port.in`, MCP, or application use cases

**docker-compose template**

- Example file in-repo (path finalised in plan)
- Includes a runnable/example **OpenAI-compatible embeddings** service configuration
- Includes **commented/placeholder** services for future vector stores (pgvector and/or Chroma), clearly marked as non-shipping for 006
- Operator notes in `quickstart.md` (or equivalent): how to point Archivist config at Compose-published URLs
- Compose is **not** a runtime dependency of the `memory` + `local` (or other) embedder path

**Documentation and ADR (plan MUST schedule)**

`/speckit-plan` MUST include explicit work items for:

1. Operator docs (README roadmap note and/or feature `quickstart.md` + contracts README) covering: strategy activation, default embedder=`local` and store=`memory`, switching to `openai-compatible`, chunking behaviour, `sourceId` dedupe, Compose examples vs what ships
2. One or more ADRs under `docs/adr/` for the architectural choices above (SPI interchangeability and chunking/dedupe at minimum) — follow existing ADR numbering (next free `ADR-NNNN`)

### Dependency direction

Unchanged: application → `KnowledgeGateway`; infrastructure retrieval → `KnowledgeCorpus`; transport → `port.in` only. Embedding and vector libraries MUST be scoped to the **infrastructure** module only.

---

## 9. Out of Scope

- Full production adapters for pgvector, ChromaDB, or other stores (**SPI + docs + `memory` only** in this spec)
- Shipping or maintaining a production embedding model weights distribution policy beyond config references for `local` (details in plan)
- Hybrid fusion and learned re-ranking
- Knowledge graph traversal for `findRelatedKnowledge`
- Persistent cross-restart vector store as a product requirement of this spec
- Corpus filesystem watchers and incremental embedding updates
- Changing MCP tool names, descriptions, or JSON schemas
- Production personal vault or live cloud API as CI dependency
- Requiring Docker/Compose for lexical, BM25, or in-memory embedding CI
- Putting `store` / `embedder` selection on MCP tools
- REST or CLI transport

---

## 10. Assumptions

- Specs 003, 004, and 005 are implemented: real corpus, lexical, BM25, gateway, use cases
- Personal Second Brain size remains modest enough for a **per-process** in-memory vector store in the first release
- Interchangeability is an **infrastructure** concern: domain never sees vectors, collection names, or provider SDKs
- First vertical slice is **`embedding` strategy + `memory` store + default `local` embedder + opt-in `openai-compatible`**
- Chunking improves long-note recall; public results stay entry-level `Evidence` with `sourceId` dedupe
- docker-compose examples are **templates**, not the source of truth for architecture; vector DB stubs are placeholders only
- Later store adapters may need their own ADR for sync/invalidation vs ADR-0004 (in-memory-centric)
- `findRelatedKnowledge` tool description remains unchanged; embeddings do not imply graph semantics
- Ranking comparison tests (AC-11) use checked-in fixtures, not a contributor’s private vault
- Similarity metric defaults to **cosine** (or equivalent normalized similarity) unless plan documents a better default for the chosen stack
- Hybrid will later compose strategies behind a single registry name; this spec only ships a standalone `embedding` strategy

---

## 11. Open Questions

None remaining for specify-time product choices — resolved in [Clarifications](#clarifications).

| Topic | Resolution / deferral |
| ----- | --------------------- |
| Default embedder | **`local`** |
| Embedding unit | **Chunks** with `sourceId` dedupe |
| Compose template | Embeddings example + **placeholder** vector DB services |
| Similarity metric | Lean cosine — plan may confirm |
| Optional min-score | Disabled by default — plan documents |
| Chunk size / overlap / aggregation | Deferred to `/speckit-plan` research |
| SPI method shapes | Deferred to `/speckit-plan` |
| Local model choice / distribution | Deferred to `/speckit-plan` |
| Credential handling | env/config; never log secrets — plan documents |
| ADR split vs single document | Deferred to `/speckit-plan` (AC-20 requires at least one) |

Implementation details (artifact coordinates, defaults, exact packages, local model choice, chunk parameters) land in **`research.md` / `plan.md`** during `/speckit-plan`.

---

## 12. Approval

| Reviewer           | Decision | Date       |
| ------------------ | -------- | ---------- |
| Leonardo Alcantara | Approved | 2026-07-20 |
