# ADR-0003: Flag Size-Limited Corpus Entries; Omit Unrecoverable Parse Failures

**Date:** 2026-07-12  
**Status:** Accepted  
**Author:** Leonardo Alcantara

---

## Context

Spec `003-second-brain-integration` introduces `KnowledgeCorpus` to discover and load Second Brain entries as domain `Evidence` with full `Provenance`. To protect the JVM from memory pressure as individual Markdown files grow, the plan defines a per-entry byte limit (`max-entry-bytes`, default 1 MiB).

Two failure modes arise when reading the corpus:

1. **Size limit exceeded** — the file exists and is structurally valid, but loading the full body risks excessive memory use. Metadata (type, zone, title, tags) is typically available in a leading metadata envelope.
2. **Unrecoverable parse or mapping failure** — corrupt metadata, missing required mapping signals, or other errors that prevent construction of valid domain `Provenance`.

An initial plan treated oversize entries like operational noise: skip with a WARN log. That creates a **silent information gap**: Archivist is a retrieval gateway; an entry that exists on disk but never appears in `catalog()` or `loadAll()` is indistinguishable from absent knowledge. Consumers cannot decide whether to fetch, rank down, or surface the entry to a human operator.

At the same time, not every I/O failure deserves a domain object. If metadata cannot be parsed into valid `KnowledgeType`, `KnowledgeZone`, and `sourceId`, there is nothing meaningful to retrieve — the knowledge is already lost at the source.

The team needed a rule that distinguishes **protective withholding of body content** from **absence of retrievable knowledge**, and that keeps the decision about trust in the retrieval layer rather than in infrastructure.

---

## Decision

1. **Introduce domain enum `ContentAvailability`** with values:
   - `AVAILABLE` — entry body loaded into `Evidence.content`
   - `UNAVAILABLE_ENTRY_TOO_LARGE` — entry discoverable; body not loaded due to size limit

2. **Add `contentAvailability` to `Provenance`** (non-null). Every catalog and load operation sets this field explicitly.

3. **When an entry exceeds `max-entry-bytes`:**
   - **Include** the entry in `catalog()`, `loadAll()`, and `loadBySourceId()` results
   - Read only the leading `metadata-read-bytes` (default 64 KiB) to extract provenance metadata
   - Set `contentAvailability = UNAVAILABLE_ENTRY_TOO_LARGE` and `Evidence.content` to empty
   - Log at INFO (not silent skip)

4. **Infrastructure flags; it does not judge reliability.** Retrieval strategies and future `KnowledgeGateway` implementations decide whether to exclude, rank down, or pass through flagged entries. No filtering of size-limited entries in the Second Brain adapter.

5. **When parse or mapping is unrecoverable** (corrupt metadata envelope, unknown type with no zone default, duplicate sourceId after first wins, etc.):
   - **Omit** the entry from corpus results
   - WARN log with operator hint (path or provisional sourceId)
   - **Do not** add `ContentAvailability` values for parse failures — no shadow or partial domain object

6. **Distinction preserved:** size limiting is a **protective load policy** on otherwise valid knowledge; parse failure is **absence of valid domain evidence**.

---

## Options Considered

### Option A: Silently skip all problematic entries (size and parse)

**Pros:**

- Simplest adapter logic
- Lowest memory use

**Cons:**

- Size-limited entries vanish from the corpus — defeats Archivist's purpose as a retrieval gateway
- Operators and agents cannot detect or remediate large files
- Conflates "too big to load" with "does not exist"

### Option B: Flag size-limited entries; omit unrecoverable parse failures (chosen)

**Pros:**

- Size-limited knowledge remains discoverable with usable metadata
- Memory protected without information loss at the catalog level
- Clear separation: infrastructure flags, retrieval decides trust
- Parse omissions avoid fake provenance for truly broken files

**Cons:**

- Adds `ContentAvailability` to domain model (additive `Provenance` field)
- `loadAll()` still returns entries with empty content — consumers must check flag
- Leading-byte metadata read adds implementation complexity for oversize files

### Option C: Flag all failures including parse errors (`UNAVAILABLE_PARSE_ERROR`, etc.)

**Pros:**

- Uniform "never omit" policy
- Maximum visibility into broken files

**Cons:**

- Partial or invalid `Provenance` violates provenance invariants (type, zone, sourceId)
- Shadow objects suggest retrievable knowledge when there is none
- Pushes data-quality repair into retrieval for cases with no valid domain representation

### Option D: Skip size-limited entries but expose separate diagnostic API

**Pros:**

- Keeps `Provenance` shape unchanged

**Cons:**

- Second API leaks operational concern into contract
- Diagnostics are not domain retrieval — violates single corpus access port

---

## Rationale

Archivist retrieves evidence; it must not hide evidence that exists but is expensive to load. Size limits protect the process; they must not erase catalog presence. A flagged entry with title, type, zone, and `sourceId` still supports agent decisions ("this ADR exists but body was not loaded — request manual review or raise limit").

Unrecoverable parse failures are different: without valid metadata, Archivist cannot produce traceable `Evidence`. Emitting a placeholder would violate Invariant 7 (evidence must remain traceable) with fabricated or incomplete provenance. As clarified in spec 003: if the file is corrupt at source, the knowledge is already lost before Archivist reads it.

Infrastructure's job is accurate loading and honest signalling (`ContentAvailability`). Retrieval's job is relevance, ranking, and whether to surface flagged bodies to consuming agents — preserving Invariant 3 (retrieval, never reasoning) while allowing policy in the gateway layer.

This complements [ADR-0002](ADR-0002-defer-corpus-indexing-and-caching.md): byte limits address per-entry memory risk without deferring to a future index; indexing spec may later optimise repeated loads but must preserve flag semantics.

---

## Consequences

- `Provenance` gains mandatory `contentAvailability` in the domain model.
- **MCP transport boundary (spec 003):** `EvidenceJsonMapper` MUST NOT serialise `contentAvailability` to MCP clients yet. Transport maps domain `Evidence` / `Provenance` to an explicit response DTO that omits the field, preserving the spec 002 MCP contract fixture unchanged while stubs remain in place.
- **Gateway / retrieval wiring (deferred — settle in that spec):** When real corpus data flows to MCP, the retrieval layer MUST decide one of:
  - **Expose** — extend MCP JSON schema to include `contentAvailability` so consuming agents can act on flagged entries; or
  - **Filter** — exclude or drop `UNAVAILABLE_ENTRY_TOO_LARGE` entries before serialisation (retrieval policy; infrastructure still flags in `KnowledgeCorpus`).
    That choice belongs to retrieval/gateway policy, not infrastructure. Document the decision in the gateway wiring or lexical retrieval spec before changing MCP contract fixtures.
- Lexical retrieval spec MUST document handling of `UNAVAILABLE_ENTRY_TOO_LARGE` (e.g. index metadata only, exclude from body search, rank down).
- Fixture and integration tests MUST include an oversize scenario verifying inclusion with flag, not absence.
- Operators tune `max-entry-bytes` and `metadata-read-bytes` rather than losing catalog entries silently.
- Future `ContentAvailability` values require ADR + spec amendment — do not ad hoc extend the enum in infrastructure.

---

## References

- [specs/003-second-brain-integration/spec.md](../../specs/003-second-brain-integration/spec.md) — Clarifications §Session 2026-07-12, §3, §5
- [specs/003-second-brain-integration/research.md](../../specs/003-second-brain-integration/research.md) — Decision 3, 3b
- [ADR-0002: Defer Corpus Indexing and Caching](ADR-0002-defer-corpus-indexing-and-caching.md)
- [AGENTS.md](../../AGENTS.md) §3.7 (Evidence must remain traceable), §3.3 (Retrieval, never reasoning)
