# KnowledgeGateway Port Contract

**Feature**: 004-lexical-retrieval
**Date**: 2026-07-13

Public retrieval port — consumed by application use cases. Implemented by infrastructure; strategy selection is invisible to callers.

---

## Interface

```java
package io.archivist.domain.port.out;

public interface KnowledgeGateway {
    List<Evidence> retrieve(Query query);
}
```

---

## Operation Semantics

### `retrieve(Query query)`

| Input condition                                | Result                                                               |
| ---------------------------------------------- | -------------------------------------------------------------------- |
| `query == null`                                | `NullPointerException`                                               |
| `query.text()` null, empty, or whitespace-only | Empty list                                                           |
| No entries match filters + lexical criteria    | Empty list                                                           |
| Matches found                                  | Deduplicated list, descending relevance, size ≤ `query.maxResults()` |

Every returned `Evidence` MUST have non-null `provenance` with all required fields populated.

### Delegation rule

`KnowledgeGateway` implementation MUST delegate retrieval to exactly one **active** `RetrievalStrategy` (infrastructure-private). Callers MUST NOT select or name strategies.

### Error propagation

| Situation                                           | Result                                         |
| --------------------------------------------------- | ---------------------------------------------- |
| `KnowledgeCorpus` throws `KnowledgeCorpusException` | Propagate — do not return partial results      |
| Unknown active strategy at startup                  | Fail application startup (registry validation) |

---

## Consumers

| Consumer                         | Usage                          |
| -------------------------------- | ------------------------------ |
| Eight application use cases      | Build `Query`, call `retrieve` |
| Infrastructure integration tests | Direct gateway invocation      |

## Non-consumers (MUST NOT call gateway)

| Layer             | Reason                      |
| ----------------- | --------------------------- |
| `transport.mcp`   | Delegates to `port.in` only |
| `KnowledgeCorpus` | Corpus access, not search   |

---

## Stability guarantee

This interface signature MUST NOT change when adding BM25, embedding, hybrid, or graph strategies. Strategy swaps are configuration-only (`archivist.retrieval.active-strategy`).
