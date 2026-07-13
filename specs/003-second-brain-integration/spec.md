# SPEC: Second Brain Integration (Initial)

**Status:** Approved
**Feature Branch**: `003-second-brain-integration`
**Created:** 2026-07-12
**Author:** Leonardo Alcantara
**Issue:** #64

---

## Clarifications

### Session 2026-07-12

- Q: When an entry exceeds the configured size limit, should infrastructure silently skip it or surface it in the corpus? → A: **Never silently skip size-limited entries.** Infrastructure MUST include the entry with full available provenance metadata and set domain `ContentAvailability` to `UNAVAILABLE_ENTRY_TOO_LARGE`. Body content is not loaded (`Evidence.content` empty). Infrastructure flags only; retrieval and gateway layers decide how to treat flagged entries (exclude, rank down, or pass through). Parse/mapping failures where metadata cannot be resolved remain omit-with-WARN.
- Q: Should unrecoverable parse/mapping failures (corrupt metadata, unknown type) also be flagged in the corpus like size-limited entries? → A: **No — omit with WARN.** Only size limits use `ContentAvailability`. Corrupt or unrecoverable entries cannot yield valid domain provenance; the knowledge is already lost before Archivist reads it. No shadow object for parse failures.

---

## 1. Motivation

Phase 1 of the Archivist roadmap requires initial Second Brain integration so the retrieval layer can access the personal knowledge corpus. The scaffold (001) validates `ARCHIVIST_SECOND_BRAIN_PATH` at startup but does not read knowledge. The MCP transport adapter (002) exposes all eight domain capabilities but stub use cases return empty lists because no infrastructure connects to Second Brain.

Second Brain is currently implemented as a collection of Markdown files organised across epistemic zones (`SOURCE`, `SYNTHESIZED`, `TECHNICAL`, etc.) and semantic types (`CONCEPT`, `DECISION`, `PERSON`, etc.) as defined in `docs/second-brain-domain.md`. Consuming agents invoke capabilities through MCP expecting `Evidence` with full `Provenance`. Before lexical or hybrid retrieval strategies can rank and filter results, Archivist must reliably **discover, classify, and load** knowledge entries from the configured Second Brain root and expose them as domain objects behind a `port.out` interface.

This specification defines the **infrastructure access layer only**: reading the corpus, mapping storage metadata to domain `KnowledgeType` and `KnowledgeZone`, and populating `Evidence` with traceable provenance. It deliberately excludes search ranking, MCP contract changes, and replacement of stub use cases — those follow when lexical retrieval and gateway wiring land in subsequent specifications.

Without this layer, retrieval strategies have no corpus to search. With it, future retrieval implementations can depend on a stable, domain-aligned access contract independent of Markdown, YAML frontmatter, or folder layout.

---

## 2. Responsibilities

### What this specification delivers

- A new domain **`port.out`** interface representing read-only access to the Second Brain corpus (catalog and load operations) — expressed in domain terms, not file-system primitives
- Infrastructure implementation under `infrastructure.secondbrain` that reads from the configured Second Brain root (`ARCHIVIST_SECOND_BRAIN_PATH`)
- Discovery of knowledge entries within the corpus (all readable entries under the root, excluding configured ignore patterns such as hidden directories and non-knowledge artefacts)
- Extraction of entry **body content** and **provenance metadata** sufficient to construct domain `Evidence` instances
- Mapping from storage-level signals to domain `KnowledgeType` and `KnowledgeZone` via infrastructure configuration — the domain model never references frontmatter field names, folder paths, or Obsidian conventions
- Stable **`sourceId`** assignment for each entry (logical identifier, not necessarily a raw filesystem path exposed to consumers)
- Population of all `Provenance` fields where data is available: `sourceId`, `title`, `type`, `zone`, `tags`, `sources`, `created`, `updated`, **`contentAvailability`**
- **Size-limited entries included, not skipped**: when an entry exceeds `max-entry-bytes`, infrastructure loads available metadata (from the leading portion of the file), sets `contentAvailability` to `UNAVAILABLE_ENTRY_TOO_LARGE`, and returns empty body content — the entry remains discoverable in `catalog()` and `loadAll()`. Infrastructure flags; it does not decide retrieval trust. See [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md).
- Graceful handling of entries that cannot be mapped or parsed at all: skip with structured logging; do not fail the entire corpus load for a single bad file
- Infrastructure integration tests using a **fixture corpus** (checked into the repository) that exercises zone/type mapping, provenance chain (`sources`), and edge cases (missing metadata, unknown type, empty body)
- Spring `@Configuration` wiring registering the `port.out` implementation as a bean in the `infrastructure` module only

### What this specification does not do

- Does not implement lexical, BM25, hybrid, or graph retrieval strategies
- Does not implement or extend `KnowledgeGateway.retrieve(Query)` — search and ranking belong to the lexical retrieval specification
- Does not replace application stub use cases or change MCP tool behaviour (MCP continues to return empty lists until gateway wiring spec)
- Does not add, remove, or rename any `port.in` capability interface
- Does not expose filesystem primitives (`readFile`, `grep`, `listDirectory`) as public MCP tools or domain capabilities
- Does not summarise, interpret, rank, deduplicate, or reason about retrieved content
- Does not write or modify Second Brain files
- Does not index embeddings or build vector stores
- Does not traverse wikilinks for graph expansion (`findRelatedKnowledge` graph semantics remain future work)
- Does not require Obsidian or any specific editor to be running

---

## 3. Public Contract

This specification does not introduce a new `port.in` domain capability. It introduces a **retrieval gateway access port** consumed by future retrieval strategies and gateway implementations.

### New port.out interface: `KnowledgeCorpus`

Read-only access to the full Second Brain corpus as domain objects.

```java
public interface KnowledgeCorpus {

    /**
     * Returns provenance metadata for every discoverable entry in the corpus,
     * including entries whose body was not loaded due to size limits.
     * Must not throw when individual entries fail to parse; unrecoverable entries are omitted.
     * Order is unspecified.
     */
    List<Provenance> catalog();

    /**
     * Loads a single entry by stable sourceId. Returns empty when not found.
     */
    Optional<Evidence> loadBySourceId(String sourceId);

    /**
     * Loads all discoverable entries with provenance; body present only when
     * contentAvailability is AVAILABLE. Size-limited entries included with empty content.
     * Entries that fail parsing are omitted. Order is unspecified.
     */
    List<Evidence> loadAll();
}
```

### Semantics

| Operation            | Returns                                                                                  | Empty case                                                                |
| -------------------- | ---------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| `catalog()`          | `List<Provenance>` — metadata only, no body content                                      | Empty list when corpus root is valid but contains no discoverable entries |
| `loadBySourceId(id)` | `Optional<Evidence>` — provenance always; body when `contentAvailability` is `AVAILABLE` | `Optional.empty()` when id unknown or entry failed parsing                |
| `loadAll()`          | `List<Evidence>` — all mapped entries; empty `content` when size-limited                 | Empty list when no discoverable entries                                   |

### Parameter validation

| Condition                                              | Behaviour                                                                                                                            |
| ------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| `loadBySourceId(null)`                                 | Throws `NullPointerException`                                                                                                        |
| `loadBySourceId("")` or whitespace-only                | Returns `Optional.empty()`                                                                                                           |
| Corpus root path missing or not a directory at runtime | Fail fast at application startup (existing `ArchivistProperties` validation); `KnowledgeCorpus` bean is not created in invalid state |

### Provenance invariants (all loaded evidence)

Every `Evidence` returned by `loadBySourceId` and `loadAll`, and every `Provenance` in `catalog()`, MUST satisfy:

- `sourceId` — non-null, non-blank, unique within the corpus snapshot
- `title` — non-null, non-blank (fallback to derived title from entry identity when storage provides no explicit title)
- `type` — valid `KnowledgeType` enum constant
- `zone` — valid `KnowledgeZone` enum constant
- `tags` — non-null list (empty list when no tags)
- `sources` — non-null list (empty list when no provenance chain known)
- `created` — non-null `Instant` (filesystem or metadata-derived; both missing → epoch or file mtime per implementation policy documented in plan)
- `updated` — non-null `Instant`
- `contentAvailability` — non-null `ContentAvailability` (`AVAILABLE` or `UNAVAILABLE_ENTRY_TOO_LARGE`)

When `contentAvailability` is `UNAVAILABLE_ENTRY_TOO_LARGE`, `Evidence.content` MUST be empty. Consumers (retrieval strategies, gateway) decide whether to exclude, rank down, or surface flagged entries — infrastructure does not filter them out.

### Error conditions

| Condition                                                   | Behaviour                                                                                                                       |
| ----------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| Entry exceeds `max-entry-bytes`                             | Entry **included**; metadata from leading bytes; `contentAvailability = UNAVAILABLE_ENTRY_TOO_LARGE`; `content` empty; INFO log |
| Single entry parse/mapping failure (metadata unrecoverable) | Entry **omitted**; WARN log — valid domain provenance cannot be constructed                                                     |
| Duplicate sourceId after mapping                            | First wins; subsequent duplicates omitted with WARN log                                                                         |
| Corpus root becomes unreadable after startup                | Operations throw `KnowledgeCorpusException` (unchecked, infrastructure-specific)                                                |

---

## 4. Acceptance Criteria

1. `./gradlew build` completes without errors including new infrastructure tests
2. `KnowledgeCorpus` interface exists in `domain.port.out` with signatures exactly as defined in §3
3. A concrete `KnowledgeCorpus` implementation exists in `infrastructure.secondbrain` and is registered as a Spring bean without importing `transport.*`
4. Given the checked-in fixture corpus, `catalog()` returns one `Provenance` per fixture entry with correct `KnowledgeType` and `KnowledgeZone` for each scenario file
5. Given the fixture corpus, `loadAll()` returns `Evidence` instances whose `content` matches expected body text (excluding storage metadata envelope) for each scenario file
6. Every `Provenance` in fixture results includes non-null `sourceId`, `title`, `type`, `zone`, `tags`, `sources`, `created`, and `updated`
7. Fixture entry with provenance chain metadata populates `sources` with stable identifiers pointing to source-zone entries
8. Fixture entry with missing or unknown type signal maps to a documented default or is omitted per §2 skip policy — behaviour verified by test
9. `loadBySourceId` returns matching `Evidence` for a known fixture id and `Optional.empty()` for unknown id
10. Fixture entry exceeding `max-entry-bytes` appears in `catalog()` and `loadAll()` with `contentAvailability = UNAVAILABLE_ENTRY_TOO_LARGE`, empty `content`, and non-null provenance metadata extracted from the leading portion of the file
11. `domain` and `application` modules contain zero imports from `infrastructure.secondbrain` or any Markdown/YAML parsing library
12. No `port.in` interface signature changes
13. MCP tools continue to delegate to stub use cases unchanged (no transport or application wiring to `KnowledgeCorpus` in this spec)
14. Corpus load against fixture completes in under 2 seconds on CI hardware (smoke performance guard, not a retrieval SLA)
15. `./gradlew :domain:test` and `./gradlew :application:test` remain Spring-free and pass

---

## 5. Domain Model Impact

### Uses (existing)

- `Evidence`
- `Provenance`
- `KnowledgeType`
- `KnowledgeZone`

### Introduces

- `KnowledgeCorpus` — `port.out` interface (§3)
- `KnowledgeCorpusException` — optional unchecked exception type in `domain.port.out` or `domain.model` for unrecoverable corpus access failures after startup (implementation may use a single runtime exception; if added, it MUST NOT depend on Spring or IO types)
- `ContentAvailability` — domain enum: `AVAILABLE`, `UNAVAILABLE_ENTRY_TOO_LARGE` only (parse failures do not produce flagged entries)

### Extends (additive)

- `Provenance` — new field `contentAvailability` (`ContentAvailability`, non-null)

### Does not introduce

- Changes to `Query` or `Evidence` field shapes (beyond empty `content` when flagged)
- New `port.in` capabilities
- Changes to `KnowledgeGateway` (remains as-is for lexical retrieval spec)

---

## 6. Architectural Impact

### port.in (public capability)

No changes.

### port.out (retrieval gateway)

**New:**

```java
// domain.port.out.KnowledgeCorpus — see §3
```

**Unchanged:**

```java
// domain.port.out.KnowledgeGateway — retrieve(Query) deferred to lexical retrieval spec
public interface KnowledgeGateway {
    List<Evidence> retrieve(Query query);
}
```

### application (use case)

No changes in this spec. Stub use cases remain; they do not inject `KnowledgeCorpus`.

### infrastructure (Second Brain adapter)

**New components (conceptual — exact class names decided in plan):**

- `KnowledgeCorpus` implementation coordinating discovery, metadata extraction, and domain mapping
- Entry reader/parser (Markdown body + metadata envelope) — private to `infrastructure.secondbrain`
- Zone/type mapping configuration (e.g. Spring `@ConfigurationProperties` under `archivist.second-brain.mapping`) translating storage signals to `KnowledgeType` / `KnowledgeZone`
- Ignore rules for non-knowledge paths (`.obsidian`, `.git`, templates, etc.)

Dependency direction:

```
infrastructure.secondbrain → domain.port.out.KnowledgeCorpus
infrastructure.secondbrain → domain.model.*
```

### transport (MCP)

No changes. `StubUseCaseConfiguration` remains the source of `port.in` beans.

### Configuration

| Variable / property                | Purpose                                                             |
| ---------------------------------- | ------------------------------------------------------------------- |
| `ARCHIVIST_SECOND_BRAIN_PATH`      | Existing — corpus root (required at startup)                        |
| `archivist.second-brain.mapping.*` | New optional mapping rules (defaults documented in plan/quickstart) |

---

## 7. Out of Scope

- Lexical retrieval strategy and `KnowledgeGateway` implementation
- Wiring real use cases or replacing stub beans
- MCP contract or tool registration changes
- BM25, embeddings, hybrid retrieval, re-ranking
- Knowledge graph traversal and wikilink resolution
- Obsidian-specific APIs (plugin, URI scheme, Base files)
- Production Second Brain vault as CI dependency (fixtures only in tests)
- ArchUnit enforcement tests
- Caching, incremental indexing, or watch-based reload (deferred — [ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md))
- Content zone `PUBLISHED` / `content` folder unless explicitly added to mapping defaults in plan

---

## 8. Assumptions

- Second Brain remains Markdown files with an optional metadata envelope at the top of each file (current Obsidian YAML frontmatter convention). Parsing is confined to `infrastructure`; the domain never names "frontmatter" or "YAML".
- Zone may be inferred from configurable path-prefix rules and/or metadata fields; type may be inferred from metadata fields and/or path conventions — all rules live in infrastructure configuration with sensible defaults for the current vault layout.
- `sourceId` is a stable logical identifier (e.g. normalised relative path without file extension or a frontmatter `id` when present); it MUST remain stable across reloads for the same entry.
- Title defaults to metadata `title` when present, otherwise first heading or filename-derived title.
- Timestamps prefer metadata `created`/`updated` when parseable; otherwise filesystem last-modified for `updated` and creation time or mtime for `created`.
- Initial corpus load is full scan on each operation (no index). Acceptable for Phase 1; indexing and caching deferred per [ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md).

---

## 9. Open Questions

- [x] Should `KnowledgeCorpus` expose a `reload()` or is per-call full scan sufficient for Phase 1? → **Per-call full scan for Phase 1.** No cache, no watch. Indexing/caching deferred — see [ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md).
- [x] Default behaviour for entries with unknown `KnowledgeType`? → **Omit with WARN log** unless mapping config supplies explicit default type for a zone prefix.
- [x] Should `catalog()` exclude `COMPILED` and `SIGNAL` zones by default? → **Include all mapped zones.** Retrieval strategies filter later.
- [x] Should size-limited entries be silently skipped? → **No.** Include with `ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE`, empty body, metadata from leading bytes. Infrastructure flags; retrieval decides trust. See [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md).
- [x] Should corrupt/unrecoverable parse failures be flagged like size limits? → **No.** Omit with WARN. Valid provenance cannot be constructed; knowledge is unrecoverable at source. See [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md).

---

## 10. Approval

| Reviewer           | Decision | Date       |
| ------------------ | -------- | ---------- |
| Leonardo Alcantara | Approved | 2026-07-12 |
