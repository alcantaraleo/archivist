# Query Model Contract

**Feature**: 004-lexical-retrieval
**Date**: 2026-07-13

Domain value object expressing retrieval intent. Constructed by application use cases; consumed by `KnowledgeGateway` and retrieval strategies.

---

## Record shape

```java
public record Query(
    String text,
    Set<KnowledgeType> types,
    Set<KnowledgeZone> zones,
    int maxResults
) { ... }
```

---

## Field semantics

| Field   | Empty set meaning  | Non-empty meaning                        |
| ------- | ------------------ | ---------------------------------------- |
| `types` | All types eligible | Entry `provenance.type()` must be in set |
| `zones` | All zones eligible | Entry `provenance.zone()` must be in set |

`maxResults` MUST be positive.

---

## Factory methods

| Factory        | Signature                                                 | Purpose             |
| -------------- | --------------------------------------------------------- | ------------------- |
| `unrestricted` | `(String text, int maxResults)`                           | No type/zone filter |
| `withType`     | `(String text, KnowledgeType type, int maxResults)`       | Single-type filter  |
| `withTypes`    | `(String text, Set<KnowledgeType> types, int maxResults)` | Multi-type filter   |

Phase 1 does not use zone filters in use cases — `zones` remains empty set. Zone filtering is available for future capabilities or strategies.

---

## Capability → Query mapping

| `port.in` method       | Parameter  | Factory        | Type filter            |
| ---------------------- | ---------- | -------------- | ---------------------- |
| `retrieveContext`      | `query`    | `unrestricted` | —                      |
| `findDecisions`        | `topic`    | `withType`     | `DECISION`             |
| `findProjects`         | `criteria` | `withType`     | `PROJECT`              |
| `findPeople`           | `name`     | `withType`     | `PERSON`               |
| `findConcepts`         | `topic`    | `withTypes`    | `CONCEPT`, `SYNTHESIS` |
| `findRelatedKnowledge` | `query`    | `unrestricted` | — (lexical fallback)   |
| `findReadings`         | `topic`    | `withType`     | `READING`              |
| `findDebriefs`         | `topic`    | `withType`     | `DEBRIEF`              |

`maxResults` for all factories: supplied from `archivist.retrieval.max-results` (default 20) via use case constructor injection.

---

## Validation boundaries

| Layer                       | Responsibility                                                           |
| --------------------------- | ------------------------------------------------------------------------ |
| MCP transport               | Rejects null/blank capability parameters before use case                 |
| Use case                    | Assumes non-blank parameter when called from MCP; passes text to `Query` |
| Gateway                     | Returns empty list for blank `query.text()` (defence in depth)           |
| `Query` compact constructor | Rejects null fields, non-positive `maxResults`                           |

---

## Invariants

- `Query` carries no framework annotations
- `Query` does not reference retrieval technology names
- Changing active retrieval strategy MUST NOT require `Query` changes
