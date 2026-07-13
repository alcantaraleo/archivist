# ADR-0002: Defer Corpus Indexing and Caching to a Future Specification

**Date:** 2026-07-12  
**Status:** Accepted  
**Author:** Leonardo Alcantara

---

## Context

Spec `003-second-brain-integration` introduces `KnowledgeCorpus` — a read-only `port.out` interface for discovering and loading Second Brain entries as domain `Evidence` with full `Provenance`. The initial implementation must read a Markdown-based corpus from `ARCHIVIST_SECOND_BRAIN_PATH`.

Every retrieval operation ultimately depends on corpus access. Naive implementations can rescan the filesystem and reparse every file on each call to `catalog()`, `loadAll()`, or `loadBySourceId()`. Alternatives include in-memory caching, filesystem watchers with incremental updates, and persistent indexes (lexical, metadata, or embedding-backed).

Phase 1 targets a personal Second Brain of modest size. Lexical retrieval (also Phase 1) will add its own scan or index concerns. Introducing caching or a shared index in the Second Brain integration spec would expand scope, couple two independent deliverables, and optimize before measured need.

Archivist's invariants require retrieval strategies to remain replaceable private infrastructure. The public contract (`port.out` interfaces, `port.in` capabilities) must stay stable as access and retrieval implementations evolve.

---

## Decision

1. **Phase 1 (`003-second-brain-integration`) implements a full corpus scan on each `KnowledgeCorpus` operation.** No in-memory cache, no filesystem watcher, and no persistent index in that specification.
2. **Caching, incremental reload, and corpus indexing are explicitly out of scope for spec 003** and will be addressed in a **separate future specification** (working title: corpus indexing / knowledge index), to be proposed when performance or retrieval requirements justify it.
3. **`KnowledgeCorpus` public semantics remain stable.** Future indexing or caching MUST be transparent to consumers of `KnowledgeCorpus` — either as an internal optimization within the same `port.out` implementation or as a decorator behind the interface, without changing method signatures or `Evidence` / `Provenance` shapes.
4. **Revisit triggers** for the indexing spec (any one is sufficient):
   - Fixture or production corpus load exceeds the 2-second smoke threshold in spec 003 acceptance criteria on CI hardware
   - Lexical or hybrid retrieval specs require a shared inverted index or metadata catalog that full rescan cannot support efficiently
   - Operational need for hot reload without process restart (e.g. active vault edits during long-running MCP sessions)

Until a revisit trigger fires, the team accepts full-scan cost for Phase 1.

---

## Options Considered

### Option A: Full scan on every `KnowledgeCorpus` call (chosen for Phase 1)

**Pros:**

- Minimal implementation; no cache invalidation logic
- Always consistent with on-disk state
- Keeps spec 003 focused on domain mapping and provenance correctness
- Easy to test with checked-in fixtures

**Cons:**

- O(n) cost per operation over entry count
- Repeated parse work when lexical retrieval also scans
- Does not scale to very large vaults without later rework

### Option B: In-memory cache with TTL or manual `reload()`

**Pros:**

- Reduces repeated I/O and parsing for steady-state workloads
- Simpler than a persistent index

**Cons:**

- Cache invalidation semantics must be defined and tested
- Stale reads unless TTL is short or watcher added
- Expands spec 003 scope without proven need
- `reload()` on a public port adds lifecycle API surface prematurely

### Option C: Filesystem watcher + incremental in-memory index

**Pros:**

- Near real-time consistency after edits
- Amortizes parse cost across requests

**Cons:**

- Platform-specific watcher behaviour and edge cases
- Significant infrastructure complexity for Phase 1
- Long-running MCP STDIO process assumptions differ from one-shot CLI use

### Option D: Persistent corpus index (e.g. embedded store, search engine)

**Pros:**

- Best scalability; shared substrate for lexical, BM25, and metadata filters
- Survives process restarts

**Cons:**

- Largest scope; schema migration and rebuild strategy required
- Blurs boundary between "Second Brain access" and "retrieval strategy"
- Premature before lexical retrieval spec defines index requirements

---

## Rationale

**YAGNI and incremental architecture:** Phase 1 must prove correct domain mapping and provenance, not optimal throughput. A personal Second Brain at foundation scale fits full scan.

**Separation of concerns:** Spec 003 owns _access and classification_. Indexing and caching are cross-cutting performance concerns that may serve lexical retrieval, BM25, and hybrid strategies alike. A dedicated spec avoids baking the wrong index shape into the first adapter.

**Stable contract (Invariant 8):** Deferring optimization behind `KnowledgeCorpus` preserves the public boundary. Consumers — future `KnowledgeGateway` implementations and retrieval strategies — depend on domain operations, not on scan versus cache versus index.

**Replaceable infrastructure (Invariant 4):** The first `KnowledgeCorpus` implementation may be naive; a later implementation (or internal delegate) may add index-backed access without changing `port.in` or MCP tools.

Option A is chosen for Phase 1 only. Option D (or B/C as stepping stones) remains the expected path when revisit triggers fire, documented in the future indexing specification.

---

## Consequences

- Spec `003-second-brain-integration` MUST NOT add cache, watcher, or persistent index artifacts.
- Lexical retrieval spec SHOULD assume `KnowledgeCorpus` may be scan-based initially and MUST NOT require index APIs on `port.out` until the indexing spec lands.
- When the indexing spec is written, it SHOULD:
  - Keep `KnowledgeCorpus` signatures unchanged
  - Define invalidation, rebuild, and startup behaviour
  - Clarify interaction with lexical/BM25 indexes (shared vs separate)
- Performance regressions beyond the spec 003 smoke threshold SHOULD prompt indexing spec prioritisation rather than ad hoc caching in unrelated specs.
- Open question in spec 003 §9 (`reload()` vs per-call scan) is **closed**: per-call full scan for Phase 1; any `reload()` or cache API is deferred to the indexing spec.

---

## References

- [specs/003-second-brain-integration/spec.md](../../specs/003-second-brain-integration/spec.md) — §7 Out of Scope, §8 Assumptions, §9 Open Questions
- [AGENTS.md](../../AGENTS.md) §3.4 (Retrieval strategies are replaceable), §3.8 (Stable public contract)
- [docs/second-brain-domain.md](../second-brain-domain.md)
- README.md — Phase 1 Foundation, Phase 2 Retrieval Evolution
