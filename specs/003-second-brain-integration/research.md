# Research: Second Brain Integration (Initial)

**Feature**: 003-second-brain-integration
**Date**: 2026-07-12
**Status**: Complete — all technical decisions resolved

**User directives incorporated**:

- Markdown entries are small today but may grow; loading entire corpus into memory is a risk to design for now.
- Corpus may reach hundreds of files; parallel disk read/parse during scan is worth adopting within Phase 1.

---

## Decision 1: Corpus Access Model (ADR-0002 alignment)

**Decision**: **Full filesystem scan on each `KnowledgeCorpus` operation** — no cache, watcher, or persistent index in this feature.

**Rationale**: Accepted in [ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md). Spec 003 focuses on correct discovery, mapping, and provenance. Optimisations that change consistency semantics (cache, index) stay in a future indexing spec.

**Alternatives considered**:

- In-memory corpus snapshot reused across calls: Rejected for this spec — invalidation semantics and memory retention conflict with ADR-0002
- Persistent metadata index: Rejected — premature; lexical retrieval spec may define shared index requirements

---

## Decision 2: Parallel Entry Loading During Scan

**Decision**: **Two-phase scan with bounded parallel parse** — (1) sequential path discovery via `Files.walk`, (2) parallel per-entry read/parse using **Java 21 virtual threads** with a **bounded semaphore** (default concurrency: `min(32, availableProcessors * 4)`).

**Rationale**: Hundreds of small files spend significant time in I/O wait. Virtual threads keep implementation simple without a large fixed thread pool. A semaphore caps concurrent open/read operations so a 500-file vault does not open 500 files at once.

**Alternatives considered**:

- Fully sequential read/parse: Rejected — unnecessary latency at hundreds of files; user explicitly flagged parallelism
- `ForkJoinPool.commonPool()` for all files: Rejected — unbounded fan-out risk; harder to tune under CI
- Parallel directory walk: Rejected — walk is cheap relative to parse; complexity not justified

**Scope note**: Parallelism is an **implementation detail** inside `infrastructure.secondbrain`. `KnowledgeCorpus` method signatures and return types unchanged.

---

## Decision 3: Memory Guardrails for Growing Entries

**Decision**: **Per-entry byte limit with flagged inclusion, not silent skip**

| Mechanism                                    | Behaviour                                                                                                                                                    |
| -------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `archivist.second-brain.max-entry-bytes`     | Default `1048576` (1 MiB). When exceeded, entry **included** with `ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE`, empty body, metadata from leading bytes |
| `archivist.second-brain.metadata-read-bytes` | Default `65536`. Leading read cap when body not loaded (Decision 3b)                                                                                         |
| `catalog()`                                  | Metadata-only for normal entries; size-limited entries included with flag                                                                                    |
| `loadBySourceId()`                           | Returns flagged entry with empty content when size-limited                                                                                                   |
| `loadAll()`                                  | All mapped entries included; empty `content` when flagged                                                                                                    |

**Rationale**: Silent skip loses discoverability. Infrastructure **flags** via `ContentAvailability`; retrieval/gateway decides trust. Memory protected by not loading oversized bodies.

**Alternatives considered**:

- Skip oversize with WARN: Rejected — clarification 2026-07-12; lost information risk
- Streaming `KnowledgeCorpus` API (`Stream<Evidence>`): Rejected — changes approved `port.out` contract
- Hard cap on total corpus bytes per call: Deferred — ADR-0002 indexing spec

### Decision 3b: Metadata Read for Size-Limited Entries

**Decision**: When file size exceeds `max-entry-bytes`, read only the leading `metadata-read-bytes` (default `65536`) to extract the metadata envelope. Do not read the full file.

**Rationale**: Frontmatter typically at file start; bounded read preserves memory while retaining type, zone, title for flagged entries.

---

## Decision 4: Metadata Envelope Parsing (Frontmatter)

**Decision**: **Private `MetadataEnvelopeParser` in `infrastructure.secondbrain`** — split leading `---` / `---` YAML block from Markdown body using delimiter rules; parse YAML map with **SnakeYAML** (`org.yaml:snakeyaml`, version from Spring Boot BOM).

**Rationale**: Current Second Brain uses Obsidian-style YAML frontmatter. Parsing stays in infrastructure — domain and `KnowledgeCorpus` never reference YAML or frontmatter. SnakeYAML is already managed by Spring Boot BOM; add explicit `implementation` in `:infrastructure` for a stable compile classpath (may be transitive today).

**Alternatives considered**:

- Regex-only field extraction: Rejected — brittle for list fields (`tags`, `sources`) and dates
- Full Markdown AST (CommonMark): Rejected — unnecessary dependency; body is stored as plain text for retrieval
- Coupling domain `Provenance` to YAML keys: Rejected — violates constitution

---

## Decision 5: Zone and Type Mapping

**Decision**: **`SecondBrainMappingProperties`** (`@ConfigurationProperties(prefix = "archivist.second-brain.mapping")`) with **defaults matching current vault layout**:

| Path prefix (relative to corpus root) | Default `KnowledgeZone` |
| ------------------------------------- | ----------------------- |
| `raw/`                                | `SOURCE`                |
| `wiki/`                               | `SYNTHESIZED`           |
| `dev/`                                | `TECHNICAL`             |
| `identity/`                           | `IDENTITY`              |
| `runtime/`                            | `COMPILED`              |
| `observability/`                      | `SIGNAL`                |

**Type resolution order** (first match wins):

1. Metadata `type` field (case-insensitive enum name)
2. Optional zone-level default type from mapping config (if configured)
3. Otherwise **omit entry** with WARN (per spec §9)

**Zone resolution order**:

1. Metadata `zone` field if present and valid
2. Longest matching path-prefix rule
3. Otherwise omit with WARN

**Rationale**: Aligns with `docs/second-brain-domain.md` conceptual zones without hardcoding Obsidian in domain code. Prefix rules are configuration — swappable if storage layout changes.

**Alternatives considered**:

- Folder name equals enum name only: Rejected — current vault uses `raw`, `wiki`, `dev` not `SOURCE`, `SYNTHESIZED`
- Single global default type for unknown: Rejected — spec §9 says omit unless zone default configured

---

## Decision 6: Stable `sourceId` Assignment

**Decision**: **`sourceId` = normalised relative path without file extension**, POSIX-style `/`, lowercase, UTF-8, relative to corpus root. Optional metadata `id` overrides when present and non-blank.

**Examples**:

- `wiki/concepts/Modular Monolith.md` → `wiki/concepts/modular-monolith` (or preserve case per normalisation policy — **use path segments as-is except strip `.md`**, no forced lowercasing of title segments)

**Clarification**: Use **path-based id** preserving segment casing from filesystem except normalise extension removal only — avoids surprising id changes on case-sensitive filesystems. Fixture contract uses lowercase paths for determinism.

**Rationale**: Stable across reloads; logical identifier; not exposed as absolute filesystem path to MCP consumers.

---

## Decision 7: Title, Tags, Sources, Timestamps

**Decision**:

| Field     | Resolution                                                                                                             |
| --------- | ---------------------------------------------------------------------------------------------------------------------- |
| `title`   | Metadata `title` → first Markdown `# heading` line → filename stem                                                     |
| `tags`    | Metadata `tags` list or comma-separated string → `List<String>`; empty list if absent                                  |
| `sources` | Metadata `sources` list or single string → normalised sourceIds; empty list if absent                                  |
| `created` | Metadata `created` (ISO-8601) → `Instant`; else `Files.getLastModifiedTime` for both if metadata `updated` also absent |
| `updated` | Metadata `updated` → `Instant`; else file last-modified time                                                           |

**Rationale**: Matches spec §8 assumptions and provenance model in `docs/second-brain-domain.md`.

---

## Decision 8: Discovery Ignore Rules

**Decision**: Skip during walk:

- Hidden path segments (name starts with `.`) — covers `.obsidian`, `.git`
- Non-`.md` files
- Paths matching configurable glob ignores (default: `**/templates/**`, `**/.trash/**`)

**Rationale**: Keeps corpus limited to knowledge notes; avoids Obsidian internals without naming Obsidian in domain code.

---

## Decision 9: Spring Wiring and Configuration Split

**Decision**:

- **`SecondBrainCorpusProperties`** in `infrastructure` — binds `archivist.second-brain.path`, `max-entry-bytes`, ignore globs (same env var `ARCHIVIST_SECOND_BRAIN_PATH` as transport)
- **`SecondBrainMappingProperties`** in `infrastructure` — mapping rules
- **`SecondBrainConfiguration`** — `@Configuration` registering `KnowledgeCorpus` bean
- Transport **`ArchivistProperties`** unchanged — continues startup validation only; no `:infrastructure` import in transport

**Rationale**: Transport validates path before boot; infrastructure reads same property for corpus access. Clean Architecture preserved — transport does not depend on infrastructure.

**Alternatives considered**:

- Share properties class in domain: Rejected — domain has no Spring
- Move validation only to infrastructure: Rejected — breaks existing AC-14/15 from scaffold

---

## Decision 10: Testing Strategy

**Decision**:

| Layer                        | Mechanism                                                                                                                                  |
| ---------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ |
| `domain`                     | Plain JUnit — `KnowledgeCorpus` interface compiles; optional exception type test                                                           |
| `infrastructure` unit        | Plain JUnit — `MetadataEnvelopeParser`, mapping logic, sourceId normalisation (no Spring)                                                  |
| `infrastructure` integration | `@SpringBootTest(classes = SecondBrainCorpusTestConfiguration.class)` — minimal context; corpus path → `src/test/resources/fixture-corpus` |
| Contract regression          | Compare `catalog()` / `loadAll()` output to [contracts/fixture-catalog-expected.json](contracts/fixture-catalog-expected.json)             |

**New test dependencies on `:infrastructure`**: `spring-boot-starter-test` (test scope), `spring-boot-starter-validation` (implementation — for `@ConfigurationProperties` validation if needed)

**Rationale**: Mirrors 002 pattern — minimal Spring context for integration; pure unit tests for parsers; checked-in fixtures as contract gate.

---

## Decision 11: `KnowledgeCorpusException`

**Decision**: Add **`io.archivist.domain.port.out.KnowledgeCorpusException`** extending `RuntimeException` — thrown when corpus root unreadable after startup (e.g. permission denied mid-operation).

**Rationale**: Spec §3 error table; domain-level signal without IO types in API.

---

## Summary Table

| Topic           | Choice                                                                             |
| --------------- | ---------------------------------------------------------------------------------- |
| Scan model      | Full scan per call (ADR-0002)                                                      |
| Parallelism     | Virtual threads + bounded semaphore                                                |
| Memory          | Flagged inclusion via `ContentAvailability`; 1 MiB body cap; leading metadata read |
| YAML            | SnakeYAML in infrastructure only                                                   |
| Mapping         | Configurable path prefixes + metadata overrides                                    |
| sourceId        | Relative path without extension; metadata `id` override                            |
| Caching/index   | Out of scope (ADR-0002)                                                            |
| MCP / use cases | Unchanged (stubs)                                                                  |
