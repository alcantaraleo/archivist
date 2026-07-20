# ADR-0004: In-Process BM25 Index Cache with Fingerprint and TTL Invalidation

**Date:** 2026-07-20  
**Status:** Accepted  
**Author:** Leonardo Alcantara

---

## Context

Spec `005-bm25-retrieval` (draft) adds a **BM25** retrieval strategy behind the existing private `RetrievalStrategy` SPI. BM25 requires building a search index over corpus text — materially more expensive than lexical substring scoring over entries already loaded via `KnowledgeCorpus`.

Archivist’s MCP transport runs as a **long-lived STDIO process**: `SpringApplication.run()` with `spring.ai.mcp.server.stdio=true` starts once per MCP client connection and serves many tool invocations on the same JVM ([spec 001](../../specs/001-project-scaffolding/spec.md) — process remains running until the client closes stdin or terminates the process). Each MCP tool call maps to one `KnowledgeGateway.retrieve()`; rebuilding a full in-memory index on **every** retrieve wastes work when the corpus is unchanged between agent turns.

[ADR-0002](./ADR-0002-defer-corpus-indexing-and-caching.md) deferred **persistent** indexes, filesystem watchers, and `KnowledgeCorpus`-level caching for Phase 1 (`003`) and scan-based lexical retrieval (`004`). Phase 2 BM25 still needs a **process-local** index cache and an explicit **staleness policy**: operators edit Second Brain files while the MCP server may stay up.

Requirements for any solution:

- Invalidation logic stays **inside infrastructure retrieval** — no new MCP tools, no `port.in` / `KnowledgeGateway` signature changes (Constitution II, V).
- `KnowledgeCorpus` public semantics remain stable; fingerprinting may use existing operations (`catalog()`, metadata) or infrastructure-only filesystem signals without leaking paths into the domain contract.
- Behaviour must be **testable** with the checked-in fixture corpus.

---

## Decision

1. **Hold the BM25 Lucene index in memory for the lifetime of the JVM** (per-process cache on the strategy bean or dedicated holder), not rebuild from scratch on every `retrieve()` when the cache is still valid.
2. **Invalidate and rebuild the index when either condition holds:**
   - **(Option 2 — corpus fingerprint)** The corpus **fingerprint** computed at retrieve time differs from the fingerprint recorded at the last successful index build.
   - **(Option 3 — TTL)** The index age exceeds a configurable **TTL** (`archivist.retrieval.bm25.index-ttl`, plan defines default).
3. If fingerprint matches **and** TTL has not expired, **reuse** the cached index and run the query only.
4. **Do not implement filesystem watchers (Option 4)** in spec `005` or as part of the initial BM25 delivery. Plan a **future specification** (corpus index / hot reload) for watcher-based invalidation when [ADR-0002](./ADR-0002-defer-corpus-indexing-and-caching.md) revisit triggers apply — especially operational need for near-real-time consistency during active vault editing on a long-running MCP session.
5. Document for operators: if the client reconnects or the JVM restarts, the index is rebuilt on next BM25 use; with fingerprint + TTL, edits are visible on the **next** retrieve after invalidation, without requiring a new public capability.

Fingerprint algorithm details (exact fields, use of `catalog()` vs filesystem mtime) belong in spec `005` plan/research and MUST respect ignore globs and spec 003 mapping rules.

---

## Options Considered

### Option 1: Document-only freshness (restart MCP to see edits)

**Description:** Cache index for JVM lifetime with **no** automatic invalidation; README states vault changes require MCP reconnect or process restart.

**Pros:** Simplest code; no fingerprint or TTL logic.

**Cons:** Silent staleness during long sessions; poor fit for active editing while Archivist stays connected.

**Outcome:** **Rejected** as sole strategy; acceptable only as operator fallback text alongside Options 2 and 3.

---

### Option 2: Corpus fingerprint before search (chosen)

**Description:** Before searching, compute a cheap fingerprint of corpus state (e.g. entry count plus aggregate of catalog metadata such as `sourceId` and `updated` timestamps; infrastructure may supplement with corpus-root filesystem signals if plan proves necessary). Rebuild index when fingerprint changes.

**Pros:** Amortizes index build across tool calls when vault is stable; correctness when catalog-visible corpus changes; no new public API; aligns with long-lived MCP process.

**Cons:** Fingerprint must reflect what `KnowledgeCorpus` actually serves; mis-designed fingerprint can miss updates or over-invalidate; design and tests required in spec 005.

**Outcome:** **Chosen** — primary invalidation signal.

---

### Option 3: Time-to-live (TTL) on cached index (chosen)

**Description:** Rebuild index when cache age exceeds configurable TTL, even if fingerprint unchanged.

**Pros:** Bounds maximum staleness if fingerprint is incomplete; simple operational knob; complements Option 2.

**Cons:** May rebuild when corpus unchanged; TTL alone is arbitrary without fingerprint.

**Outcome:** **Chosen** — combined with Option 2 (rebuild if **either** fingerprint changes **or** TTL expired).

---

### Option 4: Filesystem watcher (`WatchService`) (deferred)

**Description:** Register watches on corpus root (respecting ignore globs); invalidate cache on create/modify/delete events; rebuild on next retrieve.

**Pros:** Near-real-time invalidation after saves; less reliance on polling fingerprint on every call.

**Cons:** Platform and editor edge cases (atomic renames, sync tools, Obsidian flush behaviour); meaningful test matrix; scope overlap with ADR-0002 “indexing spec”; complexity inappropriate for first BM25 slice.

**Outcome:** **Deferred** to a **future specification** after BM25 lands; preferred evolution path when hot reload without full rebuild becomes a measured need.

---

### Option 5: Persistent on-disk index + generation metadata

**Description:** Lucene index on disk; generation file or fingerprint stored alongside; reuse across JVM restarts.

**Pros:** Faster cold start after client reconnect; shared substrate for future hybrid retrieval.

**Cons:** Largest scope; rebuild/corruption strategy; explicitly deferred by ADR-0002 for early phases.

**Outcome:** **Deferred** — future indexing specification (ADR-0002 Option D trajectory).

---

### Option 6: Operator control file or signal (no MCP)

**Description:** e.g. touch `.archivist-reindex` under corpus root or handle `SIGHUP` to force invalidation.

**Pros:** Explicit human trigger without leaking retrieval tech to MCP.

**Cons:** Extra convention; redundant if fingerprint + TTL suffice; polling/control file adds ops burden.

**Outcome:** **Not chosen** for spec 005; may revisit if fingerprint proves insufficient in practice.

---

### Option 7: Rebuild index on every `retrieve()` (no cache)

**Description:** Full index build per `KnowledgeGateway.retrieve()` while BM25 is active.

**Pros:** Always fresh; simplest invalidation story; similar cost profile to lexical `loadAll()` every call.

**Cons:** Wastes work on long-lived MCP sessions with many tool calls; ignores JVM reuse that this transport model provides.

**Outcome:** **Rejected** for BM25 default; may remain available as **`rebuild-every-retrieve`** debug/CI configuration if plan documents it.

---

## Rationale

**Match runtime model:** STDIO MCP keeps one JVM hot; per-process index caching is the correct amortization target.

**Correctness without watchers yet:** Fingerprint catches catalog-relevant changes; TTL caps worst-case staleness if fingerprint blind spots exist. Together they satisfy personal Second Brain editing patterns without Option 4’s cost now.

**Incremental architecture:** ADR-0002 intentionally deferred cross-cutting index infrastructure. Option 2 + 3 stay inside `Bm25RetrievalStrategy` (or a private helper) and do not require `KnowledgeCorpus` API changes. Option 4 remains the planned upgrade when revisit triggers (ADR-0002 §Decision point 4) or BM25 operational feedback demands near-real-time invalidation.

**Invariant preservation:** No `reindex` MCP tool; no domain exposure of Lucene or index lifecycle.

---

## Consequences

- Spec **`005-bm25-retrieval`** MUST specify per-process BM25 index caching with **fingerprint and TTL** invalidation; MUST NOT depend on filesystem watchers for initial acceptance criteria.
- Implementation MUST add configuration for TTL (and document fingerprint inputs in research/plan).
- Integration tests SHOULD cover: stable corpus → second retrieve reuses cache (observable via timing or test hook if needed); corpus change under fixture → subsequent retrieve sees new content without JVM restart.
- When a **filesystem watcher / indexing spec** is approved, it SHOULD:
  - Replace or supplement fingerprint polling for invalidation (Option 4)
  - Remain transparent to `port.in` and MCP
  - Clarify interaction with in-memory BM25 cache vs shared persistent index (ADR-0002)
- Lexical strategy (`004`) remains scan-based unless a separate spec changes it; BM25 cache invalidation does not oblige lexical to cache.
- Clarifications in spec 005 that mandated “rebuild on every retrieve” SHOULD be updated to reference this ADR.

---

## References

- [specs/005-bm25-retrieval/spec.md](../../specs/005-bm25-retrieval/spec.md) — BM25 retrieval (draft)
- [specs/004-lexical-retrieval/spec.md](../../specs/004-lexical-retrieval/spec.md) — lexical baseline, strategy SPI
- [specs/001-project-scaffolding/spec.md](../../specs/001-project-scaffolding/spec.md) — long-running STDIO MCP process
- [ADR-0002: Defer Corpus Indexing and Caching](./ADR-0002-defer-corpus-indexing-and-caching.md)
- [AGENTS.md](../../AGENTS.md) — Invariants II, IV, V, VIII
- [specs/004-lexical-retrieval/contracts/retrieval-strategy-spi.md](../../specs/004-lexical-retrieval/contracts/retrieval-strategy-spi.md)
