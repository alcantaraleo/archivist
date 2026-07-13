# Implementation Plan: Second Brain Integration (Initial)

**Branch**: `003-second-brain-integration` | **Date**: 2026-07-12 | **Spec**: [spec.md](spec.md)
**Status:** Approved (synced post-clarification 2026-07-12)
**Input**: Feature specification from `specs/003-second-brain-integration/spec.md`

**User directives**:

- Markdown entries may grow over time — design for memory risk when loading corpus.
- Corpus may reach hundreds of files — use parallel disk read/parse during scan where worthwhile.

**Clarifications applied** (see spec § Clarifications, [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md)):

- Size-limited entries **included** with `ContentAvailability.UNAVAILABLE_ENTRY_TOO_LARGE`, not silently skipped.
- Unrecoverable parse/mapping failures **omitted** with WARN — no shadow domain object.

---

## Summary

Implement read-only Second Brain access behind `domain.port.out.KnowledgeCorpus`: discover Markdown entries under `ARCHIVIST_SECOND_BRAIN_PATH`, parse metadata envelopes in infrastructure, map to `KnowledgeType` / `KnowledgeZone`, return domain `Evidence` and `Provenance` with **`contentAvailability`**.

Use **bounded parallel loading** (Java 21 virtual threads + semaphore) for hundreds of files. Apply **per-entry body limit** (`max-entry-bytes`, default 1 MiB) with **flagged inclusion** for oversize entries — metadata from leading bytes (`metadata-read-bytes`, default 64 KiB), empty body, `UNAVAILABLE_ENTRY_TOO_LARGE`. Infrastructure flags only; retrieval/gateway decides trust later.

Unrecoverable parse failures omit with WARN. No cache or persistent index ([ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md)). MCP and stub use cases unchanged.

**Technology stack**:

| Dependency  | Version     | Role                                                     |
| ----------- | ----------- | -------------------------------------------------------- |
| Spring Boot | 4.1.0       | Infrastructure config, `@ConfigurationProperties`, tests |
| SnakeYAML   | BOM-managed | Metadata envelope parsing (infrastructure only)          |
| Java        | 21          | Virtual threads for parallel entry load                  |
| Gradle      | Kotlin DSL  | Build                                                    |

---

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.0 (`spring-boot-starter`, `spring-boot-starter-validation`), SnakeYAML (`org.yaml:snakeyaml`)

**Build**: Gradle (Kotlin DSL); version catalogue at `gradle/libs.versions.toml`

**Storage**: Markdown files on local filesystem at `ARCHIVIST_SECOND_BRAIN_PATH` — hidden behind `KnowledgeCorpus`

**Testing**:

| Layer                        | Mechanism                                                             | Spring context              |
| ---------------------------- | --------------------------------------------------------------------- | --------------------------- |
| `domain`                     | Plain JUnit 5                                                         | None                        |
| `application`                | Unchanged stubs                                                       | None                        |
| `infrastructure` unit        | Plain JUnit — parser, mapper, resolver, availability logic            | None                        |
| `infrastructure` integration | `@SpringBootTest(classes = SecondBrainCorpusTestConfiguration.class)` | Minimal — corpus beans only |
| `transport`                  | Unchanged MCP tests                                                   | Unchanged                   |

**New dependencies**: `snakeyaml` (implementation), `spring-boot-starter-validation` (implementation), `spring-boot-starter-test` (infrastructure test scope)

**Target Platform**: JVM; local Second Brain directory

**Project Type**: Domain-driven retrieval service — infrastructure adapter

**Performance Goals**:

- Fixture corpus integration test < 2 s (spec AC-14)
- Hundreds of modest files: parallel load should outperform sequential scan (not a formal SLA)

**Constraints**:

- Full scan per `KnowledgeCorpus` call — no cache ([ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md))
- `KnowledgeCorpus` signatures fixed per approved spec §3
- Domain/application MUST NOT import YAML/Markdown libraries or infrastructure packages
- Transport MUST NOT depend on `:infrastructure`
- **Size policy** ([ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md)): exceed `max-entry-bytes` → include entry, flag `UNAVAILABLE_ENTRY_TOO_LARGE`, empty body, metadata from prefix — never silent skip
- **Parse policy**: unrecoverable metadata/mapping → omit with WARN; no `ContentAvailability` for parse failures
- Unknown `KnowledgeType` → omit unless zone default configured
- `catalog()` includes size-limited entries with flag; metadata-only parse for normal entries
- Constructor injection; no star imports

**Scale/Scope**: Personal Second Brain; hundreds of files; individual files may grow; aggregate corpus modest until indexing spec

---

## Constitution Check

_GATE: Evaluated before implementation. Re-check after Phase 1 design._

| Gate                 | Principle                         | Check                                          | Pre-design | Post-design                                                    |
| -------------------- | --------------------------------- | ---------------------------------------------- | ---------- | -------------------------------------------------------------- |
| Domain independence  | I. Clean Architecture             | No Spring in `domain.*` / `application.*`      | ✅ PASS    | ✅ PASS — port, enum, exception in domain; `Provenance` extend |
| Contract language    | II. Domain-Driven Public Contract | No `readFile`/`grep` on port                   | ✅ PASS    | ✅ PASS — `KnowledgeCorpus` domain terms                       |
| No reasoning         | III. Retrieval, Never Reasoning   | Returns `Evidence`/`Provenance`, not summaries | ✅ PASS    | ✅ PASS — raw content + metadata; flag not a judgment          |
| Provenance           | III                               | Full provenance incl. `contentAvailability`    | ✅ PASS    | ✅ PASS — AC-6, AC-10, fixture contract                        |
| Spec approved        | IV                                | Approved spec before implementation            | ✅ PASS    | ✅ PASS — §10 2026-07-12                                       |
| Strategy hidden      | V                                 | Access behind `port.out`                       | ✅ PASS    | ✅ PASS — adapter in infrastructure                            |
| No Obsidian coupling | V                                 | No domain refs to frontmatter/paths            | ✅ PASS    | ✅ PASS — mapping in config; YAML private                      |
| Build tool           | Technology                        | Gradle Kotlin DSL                              | ✅ PASS    | ✅ PASS                                                        |
| Injection            | Technology                        | Constructor injection                          | ✅ PASS    | ✅ PASS                                                        |
| MCP is adapter       | I / V                             | Stubs unchanged; no transport→infra            | ✅ PASS    | ✅ PASS                                                        |
| Stable contract      | II / VIII                         | `port.in` unchanged                            | ✅ PASS    | ✅ PASS                                                        |

**Gate result**: All gates pass.

**Constitution note**: Infrastructure reads YAML and path prefixes privately. `ContentAvailability` is domain vocabulary — infrastructure sets it; retrieval interprets it. Parse omissions preserve traceability invariant (no fabricated provenance).

---

## Project Structure

### Documentation (this feature)

```text
specs/003-second-brain-integration/
├── spec.md
├── plan.md                    # This file
├── research.md                # Parallel load, memory, flagging, YAML, mapping
├── data-model.md              # Domain port, ContentAvailability, infrastructure
├── quickstart.md              # Verification guide
├── contracts/
│   ├── README.md
│   ├── knowledge-corpus-port.md
│   ├── mapping-defaults.md
│   └── fixture-catalog-expected.json
├── checklists/
│   └── requirements.md
└── tasks.md                   # Generated by /speckit-tasks
```

### Source Code (repository root — changes for this feature)

```text
domain/src/main/java/io/archivist/domain/
├── model/
│   ├── ContentAvailability.java      # NEW enum
│   ├── Provenance.java               # EXTEND — contentAvailability field
│   └── (Evidence, KnowledgeType, KnowledgeZone unchanged)
└── port/out/
    ├── KnowledgeCorpus.java          # NEW
    ├── KnowledgeCorpusException.java # NEW
    └── KnowledgeGateway.java         # unchanged

infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/
├── SecondBrainKnowledgeCorpus.java
├── SecondBrainConfiguration.java
├── SecondBrainCorpusProperties.java
├── SecondBrainMappingProperties.java
├── CorpusWalker.java
├── CorpusEntryLoader.java            # parallel load; size branch → prefix read
├── MetadataEnvelopeParser.java
├── CorpusEntryMapper.java            # sets contentAvailability on Provenance
├── ZoneTypeResolver.java
└── SourceIdNormalizer.java

infrastructure/src/test/java/io/archivist/infrastructure/secondbrain/
├── MetadataEnvelopeParserTest.java
├── ZoneTypeResolverTest.java
├── SourceIdNormalizerTest.java
├── CorpusEntryMapperTest.java        # AVAILABLE vs UNAVAILABLE_ENTRY_TOO_LARGE
├── SecondBrainKnowledgeCorpusIntegrationTest.java
└── support/SecondBrainCorpusTestConfiguration.java

infrastructure/src/test/resources/fixture-corpus/
├── raw/readings/sample-reading.md
├── wiki/concepts/sample-concept.md
├── dev/decisions/sample-decision.md
├── wiki/people/sample-person.md
├── wiki/concepts/sample-with-sources.md
├── edge-cases/unknown-type.md         # omitted from catalog
└── edge-cases/oversize-entry.md       # flagged, empty body (generated or stub in test)

infrastructure/build.gradle.kts
gradle/libs.versions.toml
```

**Unchanged**: `transport/`, `application/` stubs, MCP tools, `ArchivistProperties` startup validation.

**Deferred (ADR-0003)**: MCP exposure of `contentAvailability` — spec 003 adds a transport serialisation boundary (response DTO omitting the field) so the 002 MCP contract is unchanged. Gateway/retrieval wiring spec must decide whether to expose the flag in MCP JSON or filter `UNAVAILABLE_ENTRY_TOO_LARGE` entries at retrieval before serialisation.

---

## Implementation Phases

### Phase A: Domain model and port

1. Add `ContentAvailability` enum (`AVAILABLE`, `UNAVAILABLE_ENTRY_TOO_LARGE`)
2. Extend `Provenance` record with `contentAvailability` (non-null)
3. Update existing domain tests / compile sites that construct `Provenance`
4. Add `KnowledgeCorpus` interface (spec §3 Javadoc including size-limited inclusion)
5. Add `KnowledgeCorpusException`
6. `./gradlew :domain:compileJava :domain:test`

### Phase B: Infrastructure parsing core (plain JUnit)

1. `MetadataEnvelopeParser` — delimiter split + SnakeYAML; support parse from partial prefix buffer
2. `SourceIdNormalizer` — relative path → sourceId
3. `ZoneTypeResolver` — prefix rules + metadata; unrecoverable → empty (caller omits)
4. `CorpusEntryMapper` — map + optional body → `Provenance` / `Evidence`; set `contentAvailability`
5. Unit tests including oversize path (empty body, flag set) and AVAILABLE path

### Phase C: Corpus walk, size branching, parallel load

1. `CorpusWalker` — discover `.md`; ignore hidden segments and globs
2. `CorpusEntryLoader` — virtual threads + semaphore:
   - `Files.size()` first
   - If size ≤ `max-entry-bytes`: read full file → parse → `AVAILABLE`
   - If size > `max-entry-bytes`: read leading `metadata-read-bytes` only → parse metadata → `UNAVAILABLE_ENTRY_TOO_LARGE`, empty body
3. `catalog()` — metadata path; includes flagged entries
4. Wire `SecondBrainKnowledgeCorpus`

### Phase D: Spring configuration

1. `SecondBrainCorpusProperties` — path, `max-entry-bytes`, `metadata-read-bytes`, `load-concurrency`, ignore globs
2. `SecondBrainMappingProperties` — zone prefixes, zone default types
3. `SecondBrainConfiguration` — `KnowledgeCorpus` bean
4. Defaults per [contracts/mapping-defaults.md](contracts/mapping-defaults.md)

### Phase E: Fixture corpus and integration tests

1. Create fixture Markdown under `infrastructure/src/test/resources/fixture-corpus/`
2. Oversize scenario: fixture file or `@TempDir` generated file exceeding `max-entry-bytes`
3. `SecondBrainCorpusTestConfiguration` — minimal Spring context; path → fixture root
4. `SecondBrainKnowledgeCorpusIntegrationTest`:
   - Assert against [fixture-catalog-expected.json](contracts/fixture-catalog-expected.json)
   - AC-10: oversize entry in catalog + loadAll with flag and empty content
   - AC-8: `unknown-type.md` absent
   - AC-9: loadBySourceId hit/miss

### Phase F: Verification

Run [quickstart.md](quickstart.md). `./gradlew build` passes; `:transport:test` unchanged (stubs return `[]`).

---

## Entry Handling Summary

| Condition                   | In corpus? | `contentAvailability`         | `Evidence.content` | Log  |
| --------------------------- | ---------- | ----------------------------- | ------------------ | ---- |
| Normal load                 | Yes        | `AVAILABLE`                   | Body text          | —    |
| Exceeds `max-entry-bytes`   | Yes        | `UNAVAILABLE_ENTRY_TOO_LARGE` | Empty              | INFO |
| Unrecoverable parse/mapping | No         | —                             | —                  | WARN |
| Duplicate sourceId          | First only | Per first entry               | Per first          | WARN |

Infrastructure does not filter flagged entries from results. Retrieval/gateway handles trust ([ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md)).

---

## Complexity Tracking

No constitution violations.

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --------- | ---------- | ------------------------------------ |
| —         | —          | —                                    |

---

## Phase 0 Output

See [research.md](research.md) — Decisions 3, 3b (flag vs skip), parallel load, parse omit policy.

## Phase 1 Output

- [data-model.md](data-model.md) — `ContentAvailability`, extended `Provenance`, infrastructure layout
- [contracts/](contracts/) — port semantics, mapping defaults, fixture expected JSON
- [quickstart.md](quickstart.md) — verification guide

## Related ADRs

- [ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md) — no cache/index in this spec
- [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md) — flag size limits; omit unrecoverable parse

## Next Step

Run **`/speckit-tasks`** to generate `tasks.md` reflecting Phase A–F and AC-1–AC-15, then **`/speckit-taskstoissues`** or **`/speckit-implement`**.
