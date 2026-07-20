# Corpus Fingerprint Contract (BM25 Cache)

**Feature**: 005-bm25-retrieval  
**ADR**: [ADR-0004](../../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md)

---

## Inputs

Source: **`KnowledgeCorpus.catalog()`** — ordered processing only; catalog iteration order is unspecified.

For each `Provenance` entry **included in catalog**:

| Component | Value |
| --------- | ----- |
| `sourceId` | As returned |
| `updated` | `Instant.toEpochMilli()` |
| `contentAvailability` | `enum.name()` |

Entries omitted from catalog (parse failures) do not contribute — fingerprint reflects **discoverable** corpus state.

---

## Algorithm

1. Sort catalog entries by `sourceId` (lexicographic, `String.compareTo`)
2. Concatenate lines `sourceId + "|" + updatedMillis + "|" + contentAvailability + "\n"`
3. Let `count` = number of catalog entries
4. Fingerprint string = `count + ":" + hex(SHA-256(UTF-8 bytes of concatenation))`

---

## Invalidation

Rebuild BM25 index when **any** holds:

- Computed fingerprint ≠ fingerprint at last successful build
- `Instant.now()` is after `builtAt + archivist.retrieval.bm25.index-ttl`

Otherwise reuse in-memory index.

---

## Tests

`CorpusFingerprintTest` MUST assert:

- Same catalog → same fingerprint
- Changed `updated` on one entry → different fingerprint
- Changed entry count → different fingerprint

Integration test SHOULD assert second retrieve with stable corpus does not require full rebuild (via test double or timing hook).
