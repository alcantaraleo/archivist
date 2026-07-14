# Retrieval Strategy SPI (Infrastructure-Internal)

**Feature**: 004-lexical-retrieval
**Date**: 2026-07-13

**Scope**: `io.archivist.infrastructure.retrieval` only. This SPI is NOT part of the public domain contract. MCP, transport, and application layers MUST NOT reference it.

---

## Interface

```java
package io.archivist.infrastructure.retrieval;

public interface RetrievalStrategy {

    /**
     * Stable configuration key — must match archivist.retrieval.active-strategy.
     */
    String name();

    /**
     * Execute retrieval for the given domain query.
     * Returns ranked, deduplicated evidence (may be empty).
     */
    List<Evidence> retrieve(Query query);
}
```

---

## Registry contract

`RetrievalStrategyRegistry`:

1. Collects all `RetrievalStrategy` beans at construction
2. Indexes by `name()` — **duplicate names → startup failure**
3. Resolves active strategy from `archivist.retrieval.active-strategy`
4. **Unknown active name → startup failure** with descriptive message listing registered names

`KnowledgeGatewayImpl` calls `registry.getActive().retrieve(query)` — no strategy name parameter on public API.

---

## Phase 1 implementations

| `name()`  | Class                      | Status                   |
| --------- | -------------------------- | ------------------------ |
| `lexical` | `LexicalRetrievalStrategy` | Implemented in this spec |

---

## Extension checklist: adding a new strategy (Phase 2+)

Use this checklist when implementing BM25, embeddings, hybrid, or graph retrieval. **None of these steps touch MCP or transport handlers.**

### 1. Implement SPI

```text
infrastructure/src/main/java/io/archivist/infrastructure/retrieval/bm25/
└── Bm25RetrievalStrategy.java    implements RetrievalStrategy
```

- `name()` returns unique string (e.g. `"bm25"`)
- `retrieve(Query)` honours `Query.types()`, `Query.zones()`, `Query.maxResults()`
- Returns `List<Evidence>` with full provenance — no reasoning/summarisation
- Apply `ContentAvailability` policy per [ADR-0003](../../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md)

### 2. Register bean

In `RetrievalConfiguration`:

```java
@Bean
RetrievalStrategy bm25RetrievalStrategy(KnowledgeCorpus corpus, /* deps */) {
    return new Bm25RetrievalStrategy(corpus, ...);
}
```

Registry auto-collects via `List<RetrievalStrategy>` injection.

### 3. Add tests

| Test type   | Location                                                         |
| ----------- | ---------------------------------------------------------------- |
| Unit        | `infrastructure/.../Bm25RetrievalStrategyTest`                   |
| Integration | `infrastructure/.../Bm25RetrievalIntegrationTest` + fixture JSON |

### 4. Activate via configuration

```properties
archivist.retrieval.active-strategy=bm25
```

### 5. Verify isolation

| Check                            | Expected                                  |
| -------------------------------- | ----------------------------------------- |
| `domain.port.in` interfaces      | Unchanged                                 |
| `ArchivistMcpTools.java`         | Unchanged                                 |
| MCP contract fixtures (spec 002) | Unchanged                                 |
| `transport` Java imports         | No `io.archivist.infrastructure`          |
| `application` use cases          | Unchanged (still call `KnowledgeGateway`) |

---

## Hybrid strategies (Phase 2 note)

Hybrid retrieval may compose multiple `RetrievalStrategy` implementations internally (e.g. merge lexical + BM25 scores) behind a single `name()` (e.g. `"hybrid"`). Composition stays inside one strategy class — registry still exposes one active strategy to `KnowledgeGateway`.

---

## Anti-patterns (review reject)

| Anti-pattern                                       | Why rejected                                    |
| -------------------------------------------------- | ----------------------------------------------- |
| Expose `RetrievalStrategy` on MCP as a tool        | Leaks retrieval technology (Invariant 2)        |
| Add `strategy` parameter to `port.in` capabilities | Couples public contract to algorithms           |
| Import strategy types in `ArchivistMcpTools`       | Breaks MCP obliviousness                        |
| Select strategy in application use cases           | Retrieval selection is infrastructure concern   |
| Put SPI in `domain.port.out`                       | Violates private strategy rule (Constitution V) |
