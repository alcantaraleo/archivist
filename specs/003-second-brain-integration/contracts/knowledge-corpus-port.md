# KnowledgeCorpus Port Contract

**Feature**: 003-second-brain-integration
**Date**: 2026-07-12

Internal retrieval gateway access port — not exposed over MCP.

---

## Interface

```java
package io.archivist.domain.port.out;

public interface KnowledgeCorpus {

    List<Provenance> catalog();

    Optional<Evidence> loadBySourceId(String sourceId);

    List<Evidence> loadAll();
}
```

---

## Operation Semantics

### `catalog()`

Returns provenance metadata for every successfully mapped entry in the corpus. Does not include entry body text. Failed or skipped entries are omitted (not thrown). Order unspecified. Duplicate sourceIds: first wins.

### `loadBySourceId(String sourceId)`

Returns full `Evidence` when sourceId matches. Returns `Optional.empty()` for unknown id, blank id, or unparseable entry. Throws `NullPointerException` for null id.

### `loadAll()`

Returns full `Evidence` for all successfully mapped entries. Failed entries omitted. Order unspecified.

---

## Provenance Invariants

All returned `Provenance` values MUST have non-null: `sourceId`, `title`, `type`, `zone`, `tags`, `sources`, `created`, `updated`. Lists may be empty.

---

## Failure Modes

| Situation                            | Result                                                                          |
| ------------------------------------ | ------------------------------------------------------------------------------- |
| Single entry parse/mapping failure   | Entry skipped; operation continues                                              |
| Corpus root unreadable after startup | `KnowledgeCorpusException`                                                      |
| Entry exceeds `max-entry-bytes`      | Entry included; `UNAVAILABLE_ENTRY_TOO_LARGE`; empty body; metadata from prefix |

---

## Consumers (future)

- Lexical retrieval strategy (planned spec)
- `KnowledgeGateway` implementation (planned spec)

Not consumed by transport or stub use cases in spec 003.
