# SPEC: Lexical Retrieval Strategy and Retrieval Foundation

**Status:** Approved
**Feature Branch**: `004-lexical-retrieval`
**Created:** 2026-07-13
**Author:** Leonardo Alcantara
**Issue:** [#74](https://github.com/alcantaraleo/archivist/issues/74)

---

## 1. Motivation

Phase 1 of the Archivist roadmap has one remaining deliverable: **lexical retrieval** — the ability to search the Second Brain corpus and return ranked `Evidence` for domain capabilities. Specs 001–002 established the domain contract and MCP transport adapter; spec 003 delivered read-only corpus access via `KnowledgeCorpus`. Today, all eight `port.in` capabilities are wired to stub use cases that return empty lists. Agents can invoke MCP tools but receive no knowledge.

This specification closes Phase 1 by:

1. Implementing the **first retrieval strategy** — lexical (keyword/substring) search over corpus entries
2. Establishing the **retrieval strategy foundation** so Phase 2 strategies (BM25, embeddings, hybrid, re-ranking) can be added without changing the public contract, MCP tools, or transport layer
3. Implementing **`KnowledgeGateway`** as the domain retrieval port and wiring **real application use cases** that translate capability intent into gateway queries
4. Keeping the **MCP transport adapter oblivious** to which retrieval strategy is active — it continues to delegate only to `port.in` interfaces

Consuming agents need evidence ranked by relevance to their query, filtered by the semantic intent of each capability (decisions vs people vs readings, etc.), with full provenance. Lexical search is the correct Phase 1 baseline: simple, deterministic, testable, and sufficient to validate end-to-end retrieval before more sophisticated strategies arrive in Phase 2.

---

## 2. Responsibilities

### What this specification delivers

- **Enriched domain `Query`** — expresses retrieval text plus optional capability-driven filters (`KnowledgeType`, `KnowledgeZone`) and a result limit; constructed by application use cases, consumed by `KnowledgeGateway`
- **`KnowledgeGateway` implementation** in infrastructure that orchestrates retrieval by delegating to a selected internal strategy — transport and MCP never reference strategy types
- **Retrieval strategy foundation** in `infrastructure.retrieval`:
  - Internal strategy contract (not a public `port.out` interface — strategies are private implementation details per Constitution V)
  - Strategy registration and selection mechanism (Phase 1: only `lexical` registered and active; selection via configuration property with a single valid value)
  - Clear extension point for Phase 2 strategies without changing `KnowledgeGateway` signature or `port.in` contracts
- **Lexical retrieval strategy** that:
  - Loads searchable corpus via `KnowledgeCorpus`
  - Matches query terms against entry **title**, **tags**, and **body content** (case-insensitive)
  - Applies `KnowledgeType` and `KnowledgeZone` filters from `Query`
  - Ranks matches by relevance (title match weighted highest, then tags, then body; more matching terms rank higher)
  - Deduplicates results by `sourceId`
  - Enforces configurable maximum result count (default 20)
  - Applies retrieval policy for `ContentAvailability` (see §3)
- **Real use case implementations** for all eight `port.in` capabilities — each injects `KnowledgeGateway`, builds a capability-appropriate `Query`, and returns gateway results unchanged
- **Spring wiring** in infrastructure (auto-configuration or `@Configuration`) registering gateway, lexical strategy, and use case beans; **removal of stub use case configuration from transport**
- **Composition root update**: transport module gains a runtime classpath dependency on infrastructure so the running application assembles retrieval beans; transport source code continues to depend only on `port.in` interfaces — no imports of infrastructure retrieval types
- **Infrastructure integration tests** using the spec 003 fixture corpus validating lexical ranking, type/zone filtering, deduplication, size-limited entry policy, and empty-query behaviour
- **Application unit tests** per use case verifying correct `Query` construction and gateway delegation (mocked `KnowledgeGateway`)
- **Regression**: existing MCP contract tests (002) continue to pass without schema or tool registration changes

### What this specification does not do

- Does not add, remove, or rename any `port.in` capability or MCP tool
- Does not expose retrieval technology in the public contract (`grep`, `bm25Search`, `vectorSearch`, etc.)
- Does not implement BM25, embedding, hybrid, or graph retrieval strategies (Phase 2–3)
- Does not implement knowledge graph traversal for `findRelatedKnowledge` — Phase 1 uses the same lexical matching as other capabilities with unrestricted type/zone filters (graph semantics deferred)
- Does not summarise, interpret, rank with ML models, or reason about retrieved content
- Does not change `KnowledgeCorpus` interface or Second Brain adapter behaviour (spec 003)
- Does not introduce corpus indexing, caching, or filesystem watchers ([ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md))
- Does not write or modify Second Brain files
- Does not require Obsidian or any editor to be running

---

## 3. Public Contract

### port.in (unchanged)

All eight capability signatures remain identical to specs 001–002. No MCP tool schema changes.

| Capability             | Parameter  | Phase 1 retrieval intent                                            |
| ---------------------- | ---------- | ------------------------------------------------------------------- |
| `retrieveContext`      | `query`    | Lexical match across all types and zones                            |
| `findDecisions`        | `topic`    | Lexical match restricted to `DECISION`                              |
| `findProjects`         | `criteria` | Lexical match restricted to `PROJECT`                               |
| `findPeople`           | `name`     | Lexical match restricted to `PERSON`                                |
| `findConcepts`         | `topic`    | Lexical match restricted to `CONCEPT`, `SYNTHESIS`                  |
| `findRelatedKnowledge` | `query`    | Lexical match across all types and zones (graph traversal deferred) |
| `findReadings`         | `topic`    | Lexical match restricted to `READING`                               |
| `findDebriefs`         | `topic`    | Lexical match restricted to `DEBRIEF`                               |

### port.out — `KnowledgeGateway` (behaviour defined)

Existing interface unchanged:

```java
public interface KnowledgeGateway {
    List<Evidence> retrieve(Query query);
}
```

**Semantics for `retrieve(Query)`:**

| Condition                                         | Behaviour                                                                                                                                   |
| ------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- |
| `query` is null                                   | Throws `NullPointerException`                                                                                                               |
| `query.text()` is null, empty, or whitespace-only | Returns empty list (use cases MUST NOT call gateway with blank text — MCP transport already rejects blank inputs; gateway defends in depth) |
| No matching entries                               | Returns empty list                                                                                                                          |
| Matching entries found                            | Returns deduplicated list ordered by descending relevance; size ≤ `query.maxResults()`                                                      |
| Every returned `Evidence`                         | Includes non-null `Provenance` with all required fields                                                                                     |

**Retrieval policy for `ContentAvailability`** (per [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md)):

- Entries with `AVAILABLE` content: match against title, tags, and body
- Entries with `UNAVAILABLE_ENTRY_TOO_LARGE`: match against title and tags only (body is empty); include in results when title/tags match
- Infrastructure (spec 003) flags availability; lexical strategy applies this policy — it does not re-load or skip corpus entries

### Domain model — enriched `Query`

```java
public record Query(
    String text,
    Set<KnowledgeType> types,
    Set<KnowledgeZone> zones,
    int maxResults
) {
    // Compact constructors / factories for common capability patterns
    // e.g. unrestricted context query, single-type filter, multi-type filter
}
```

| Field        | Semantics                                                                              |
| ------------ | -------------------------------------------------------------------------------------- |
| `text`       | User-provided search text (already validated non-blank by use case caller)             |
| `types`      | Empty set = no type filter (all types eligible); non-empty = entry type must be in set |
| `zones`      | Empty set = no zone filter (all zones eligible); non-empty = entry zone must be in set |
| `maxResults` | Upper bound on returned evidence count; default 20 when constructed by use cases       |

`Query` remains a domain value object with no framework dependencies.

### Error conditions (gateway and use cases)

| Condition                                     | Behaviour                                                                                  |
| --------------------------------------------- | ------------------------------------------------------------------------------------------ |
| Blank capability parameter                    | Use case not invoked (MCP layer rejects) or returns empty list if called directly in tests |
| Unknown active strategy name in configuration | Application fails at startup with descriptive error                                        |
| `KnowledgeCorpus` failure during retrieval    | Propagates `KnowledgeCorpusException`; not swallowed                                       |

---

## 4. Acceptance Criteria

> Numbered, verifiable conditions. Each must be independently testable.

### Gateway and wiring

1. **AC-1**: `./gradlew build` succeeds with real use case beans replacing stubs; MCP contract regression tests pass unchanged
2. **AC-2**: Transport module Java sources import no types from `io.archivist.infrastructure.*` (ArchUnit or source scan in plan)
3. **AC-3**: `ArchivistApplication` no longer imports or registers `StubUseCaseConfiguration`; use case beans originate from infrastructure configuration
4. **AC-4**: Given a configured Second Brain fixture corpus and `retrieveContext("sample")`, returns at least one `Evidence` whose title or content contains "sample" (case-insensitive)

### Lexical matching and ranking

5. **AC-5**: Given `findDecisions("fixture")` against the fixture corpus, returns only entries with `KnowledgeType.DECISION` that match the term
6. **AC-6**: Given `findConcepts("sample")`, returns entries with type `CONCEPT` or `SYNTHESIS` only
7. **AC-7**: Given a query matching multiple entries, results are ordered with title matches ranked above body-only matches for the same term
8. **AC-8**: Given duplicate `sourceId` candidates, returned list contains at most one `Evidence` per `sourceId`
9. **AC-9**: Given a query exceeding the configured max results, returned list size equals `maxResults` (default 20)

### Content availability policy

10. **AC-10**: Given a size-limited fixture entry whose title matches the query but body does not, entry is included with empty content and `UNAVAILABLE_ENTRY_TOO_LARGE` provenance flag
11. **AC-11**: Given a size-limited entry whose title and tags do not match, entry is excluded even if a hypothetical body would match

### Capability coverage

12. **AC-12**: Each of the eight `port.in` use cases has an application-layer unit test verifying it constructs the expected `Query` filters and delegates to `KnowledgeGateway`
13. **AC-13**: Given `findPeople("sample")` against fixture corpus, returns only `PERSON` type entries matching the name

### Strategy foundation

14. **AC-14**: Configuration property `archivist.retrieval.active-strategy` defaults to `lexical`; startup fails fast if set to an unknown value
15. **AC-15**: Adding a second strategy implementation in a future spec requires no changes to `domain.port.in` interfaces, MCP tool registration, or transport handler code (verified by architectural review in plan — extension point documented in `research.md`)

### Performance smoke test

16. **AC-16**: Lexical retrieval against the fixture corpus (5 entries) completes in under 2 seconds on CI hardware (guards against accidental full-corpus regression loops in tests only; not a production SLA)

---

## 5. Domain Model Impact

### Extends

- **`Query`** — adds `types`, `zones`, `maxResults` fields with factory methods for capability-specific construction; existing single-argument usage replaced by factories in use cases

### Unchanged

- `Evidence`, `Provenance`, `KnowledgeType`, `KnowledgeZone`, `ContentAvailability`
- All eight `port.in` interfaces
- `KnowledgeCorpus` (spec 003)
- `KnowledgeGateway` interface signature (behaviour now specified)

### Does not introduce

- New public capabilities
- New `port.out` interfaces beyond behaviour definition for existing `KnowledgeGateway`
- Public retrieval strategy interfaces in domain layer

---

## 6. Architectural Impact

### port.in (public capability)

No signature changes. Stub use cases replaced by real implementations:

```java
public class FindDecisionsUseCase implements FindDecisions {
    private final KnowledgeGateway gateway;

    public FindDecisionsUseCase(KnowledgeGateway gateway) { ... }

    @Override
    public List<Evidence> findDecisions(String topic) {
        return gateway.retrieve(Query.forTypeFilter(topic, KnowledgeType.DECISION));
    }
}
```

(Exact factory names decided in plan; semantics per §3.)

### port.out (retrieval gateway)

**Implemented:**

```java
// infrastructure — implements domain.port.out.KnowledgeGateway
// Delegates to internal RetrievalStrategy selected by configuration
```

**Consumed:**

```java
// infrastructure.retrieval.lexical — uses KnowledgeCorpus (spec 003)
```

### application (use case)

All eight use case classes gain constructor-injected `KnowledgeGateway` and capability-specific `Query` construction. No Spring annotations in application module.

### infrastructure (retrieval)

**New components (conceptual):**

| Component                      | Responsibility                                                                                                                                                                                        |
| ------------------------------ | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `RetrievalStrategy` (internal) | Module-internal SPI in `infrastructure.retrieval` (public within that module for Spring/`List` injection; not a `domain.port.out` interface): `String name()`, `List<Evidence> retrieve(Query query)` |
| `LexicalRetrievalStrategy`     | Keyword matching, filtering, ranking, deduplication via `KnowledgeCorpus`                                                                                                                             |
| `RetrievalStrategyRegistry`    | Maps strategy name → implementation; validates active strategy at startup                                                                                                                             |
| `KnowledgeGatewayImpl`         | Implements `KnowledgeGateway`; delegates to active strategy                                                                                                                                           |
| `RetrievalConfiguration`       | Spring beans for gateway, strategies, registry, use cases                                                                                                                                             |
| `RetrievalProperties`          | `archivist.retrieval.active-strategy`, `archivist.retrieval.max-results` (default 20)                                                                                                                 |

Dependency direction:

```
infrastructure.retrieval → domain.port.out.KnowledgeGateway (implements)
infrastructure.retrieval → domain.port.out.KnowledgeCorpus (uses)
infrastructure.retrieval → domain.model.*
application.usecase → domain.port.out.KnowledgeGateway (injected)
transport.mcp → application.usecase / domain.port.in (unchanged)
```

### transport (MCP)

- Remove `StubUseCaseConfiguration`
- Add Gradle `implementation(project(":infrastructure"))` for composition root classpath
- Enable infrastructure auto-configuration (or broaden component scan) without transport Java files importing infrastructure types
- MCP tool handlers unchanged — inject `port.in` interfaces only

### Configuration

| Property / variable                   | Purpose                              | Default   |
| ------------------------------------- | ------------------------------------ | --------- |
| `archivist.retrieval.active-strategy` | Strategy name for `KnowledgeGateway` | `lexical` |
| `archivist.retrieval.max-results`     | Default max evidence per query       | `20`      |
| `ARCHIVIST_SECOND_BRAIN_PATH`         | Unchanged (spec 003)                 | required  |

---

## 7. Out of Scope

- BM25, embedding, hybrid, and re-ranking strategies (Phase 2)
- Knowledge graph traversal and wikilink-based `findRelatedKnowledge` (Phase 3)
- Query planning, query expansion, or synonym handling
- Persistent lexical index or inverted index (full scan per retrieval for Phase 1; acceptable per ADR-0002)
- Changing MCP tool names, descriptions, or JSON schemas
- ArchUnit enforcement beyond what plan tasks define
- Production Second Brain vault as CI dependency (fixture corpus only)
- REST or CLI transport adapters

---

## 8. Assumptions

- Spec 003 `KnowledgeCorpus` is implemented and available on the infrastructure classpath
- Lexical matching tokenises on whitespace and punctuation boundaries (simple split); no stemming or locale-specific normalisation in Phase 1
- `findRelatedKnowledge` agents accept lexical fallback until graph retrieval ships; tool description remains unchanged
- Default result limit of 20 balances agent context window size against recall for personal knowledge bases
- Phase 1 strategy selection is configuration-only (no runtime strategy switching API)
- Use cases remain thin — no retrieval logic in application layer beyond `Query` construction
- Transport tests that relied on stub empty responses are updated to use test doubles or fixture-backed integration context as decided in plan

---

## 9. Open Questions

None remaining.

| Resolved                       | Resolution                                                                                                                  |
| ------------------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| GitHub epic / phase sub-issues | Epic [#74](https://github.com/alcantaraleo/archivist/issues/74); sub-issues #75–#84 in [github-issues.md](github-issues.md) |

---

## 10. Approval

| Reviewer           | Decision | Date       |
| ------------------ | -------- | ---------- |
| Leonardo Alcantara | Approved | 2026-07-13 |
