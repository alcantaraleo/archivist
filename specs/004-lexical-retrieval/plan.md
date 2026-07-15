# Implementation Plan: Lexical Retrieval Strategy and Retrieval Foundation

**Branch**: `004-lexical-retrieval` | **Date**: 2026-07-13 | **Spec**: [spec.md](spec.md)
**Status:** Approved (synced with spec approval 2026-07-13)
**Input**: Feature specification from `specs/004-lexical-retrieval/spec.md`

**User directive**:

- Retrieval algorithms must be swappable without MCP or transport changes — the MCP layer remains unaware of how retrieval is performed.

---

## Summary

Close Phase 1 by implementing **lexical retrieval** and a **private retrieval strategy foundation** in `infrastructure.retrieval`. Application use cases replace stubs: each builds a capability-specific domain `Query` and delegates to `KnowledgeGateway`. A single `KnowledgeGatewayImpl` delegates to the **active** `RetrievalStrategy` selected at startup via `archivist.retrieval.active-strategy` (default `lexical`).

**MCP obliviousness** is enforced structurally:

```
MCP tool handler → port.in use case → KnowledgeGateway (port.out) → [private] RetrievalStrategy
```

Transport injects only `port.in` interfaces. Strategy types never appear in `transport.*` or `application.*` source. Infrastructure registers all beans via Spring Boot **auto-configuration** (`AutoConfiguration.imports`); transport adds a Gradle classpath dependency only — no `import io.archivist.infrastructure.*` in transport Java files.

Phase 2 strategies (BM25, embeddings, hybrid) add a new `RetrievalStrategy` implementation + registry entry + config value — no MCP, `port.in`, or transport handler changes.

**Technology stack** (unchanged from prior specs):

| Dependency  | Version    | Role                                              |
| ----------- | ---------- | ------------------------------------------------- |
| Spring Boot | 4.1.0      | Auto-configuration, `@ConfigurationProperties`    |
| Java        | 21         | Records, `Set.copyOf`, streams                    |
| Gradle      | Kotlin DSL | Build                                             |
| JUnit 5     | BOM        | Domain/application plain tests; infra integration |

**Depends on**: spec 003 `KnowledgeCorpus` (merged or on same branch).

---

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Spring Boot 4.1.0 (`spring-boot-starter`, `spring-boot-starter-validation`); spec 003 `KnowledgeCorpus` adapter on classpath

**Build**: Gradle (Kotlin DSL); version catalogue at `gradle/libs.versions.toml`

**Storage**: Corpus accessed via `KnowledgeCorpus.loadAll()` per retrieval call — full scan acceptable per [ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md)

**Testing**:

| Layer                        | Mechanism                                                                      | Spring context                                     |
| ---------------------------- | ------------------------------------------------------------------------------ | -------------------------------------------------- |
| `domain`                     | Plain JUnit 5 — `Query` factories, validation                                  | None                                               |
| `application`                | Mockito — mock `KnowledgeGateway`; assert `Query` construction                 | None                                               |
| `infrastructure` unit        | Plain JUnit — tokenisation, scoring, filter logic                              | None                                               |
| `infrastructure` integration | `@SpringBootTest(classes = RetrievalTestConfiguration.class)` + fixture corpus | Minimal — retrieval + corpus beans                 |
| `transport`                  | Mockito `port.in` mocks — MCP contract regression unchanged                    | `McpAdapterTestConfiguration` (no infra retrieval) |

**New dependencies**: None beyond existing modules

**Target Platform**: JVM; local Second Brain directory via `ARCHIVIST_SECOND_BRAIN_PATH`

**Project Type**: Domain-driven MCP retrieval service — infrastructure retrieval + application wiring

**Performance Goals**:

- Fixture corpus lexical retrieval < 2 s (spec AC-16)
- Full `loadAll()` per query acceptable for Phase 1 personal corpus size

**Constraints**:

- `port.in` signatures and MCP tool schemas unchanged (AC-1)
- Transport Java sources MUST NOT import `io.archivist.infrastructure.*` (AC-2)
- `RetrievalStrategy` is infrastructure-private — NOT a `domain.port.out` interface
- Domain/application MUST NOT import retrieval implementation packages
- Constructor injection; no star imports
- Lexical tokenisation: whitespace + punctuation split; case-insensitive; no stemming (Phase 1)
- Default `maxResults`: 20 (configurable via `archivist.retrieval.max-results`)

**Scale/Scope**: Personal Second Brain; hundreds of files; single active strategy at runtime (config-only switch)

---

## Constitution Check

_GATE: Evaluated before design. Re-check after Phase 1 design._

| Gate                 | Principle                         | Check                                            | Pre-design | Post-design                                                           |
| -------------------- | --------------------------------- | ------------------------------------------------ | ---------- | --------------------------------------------------------------------- |
| Domain independence  | I. Clean Architecture             | No Spring in `domain.*` / `application.*`        | ✅ PASS    | ✅ PASS — `Query` extend only; use cases plain Java                   |
| Contract language    | II. Domain-Driven Public Contract | No `bm25Search`/`grep` on `port.in`              | ✅ PASS    | ✅ PASS — capabilities unchanged                                      |
| No reasoning         | III. Retrieval, Never Reasoning   | Returns `List<Evidence>`                         | ✅ PASS    | ✅ PASS — ranked evidence only                                        |
| Provenance           | III                               | Full provenance on every `Evidence`              | ✅ PASS    | ✅ PASS — corpus provenance preserved                                 |
| Spec approved        | IV                                | Approved spec before implementation              | ✅ PASS    | ✅ PASS — §10 2026-07-13                                              |
| Strategy hidden      | V                                 | Retrieval behind `KnowledgeGateway` (`port.out`) | ✅ PASS    | ✅ PASS — strategies private; gateway is sole application-facing port |
| No Obsidian coupling | V                                 | No domain refs to storage format                 | ✅ PASS    | ✅ PASS — lexical uses domain fields only                             |
| MCP is adapter       | I / V                             | Transport depends on `port.in` only              | ✅ PASS    | ✅ PASS — auto-config; no transport→infra imports                     |
| Stable contract      | II / VIII                         | `port.in` unchanged across strategy swaps        | ✅ PASS    | ✅ PASS — extension point in registry only                            |
| Build tool           | Technology                        | Gradle Kotlin DSL                                | ✅ PASS    | ✅ PASS                                                               |
| Injection            | Technology                        | Constructor injection                            | ✅ PASS    | ✅ PASS                                                               |

**Gate result**: All architectural gates pass. Spec approved 2026-07-13 — ready for `/speckit-tasks` and `/speckit-implement`.

**Constitution note**: `RetrievalStrategy` deliberately lives in `infrastructure.retrieval`, not `domain.port.out`. Constitution V requires strategies to be _private_; the stable outward-facing retrieval port remains `KnowledgeGateway`. Application layer depends only on `KnowledgeGateway`, preserving replaceability without leaking algorithm names into the domain.

---

## Project Structure

### Documentation (this feature)

```text
specs/004-lexical-retrieval/
├── spec.md
├── plan.md                         # This file
├── research.md                     # Strategy SPI, auto-config, scoring, test split
├── data-model.md                   # Query enrichment, infrastructure class model
├── quickstart.md                   # End-to-end verification guide
├── contracts/
│   ├── README.md
│   ├── knowledge-gateway-port.md   # KnowledgeGateway.retrieve semantics
│   ├── query-model.md              # Query factories and filter rules
│   ├── retrieval-strategy-spi.md   # Internal SPI + extension checklist
│   └── fixture-lexical-expected.json
├── checklists/
│   ├── requirements.md
│   └── alignment.md                # Pre-implement cross-artifact alignment
├── github-issues.md                # Epic #74 + phase sub-issues
└── tasks.md
```

### Source Code (repository root — changes for this feature)

```text
domain/src/main/java/io/archivist/domain/
├── model/
│   └── Query.java                          # EXTEND — types, zones, maxResults, factories
└── port/out/
    └── KnowledgeGateway.java               # unchanged signature; behaviour now implemented

application/src/main/java/io/archivist/application/usecase/
├── RetrieveContextUseCase.java             # MODIFY — inject KnowledgeGateway
├── FindDecisionsUseCase.java               # MODIFY
├── FindProjectsUseCase.java                # MODIFY
├── FindPeopleUseCase.java                  # MODIFY
├── FindConceptsUseCase.java                # MODIFY
├── FindRelatedKnowledgeUseCase.java        # MODIFY
├── FindReadingsUseCase.java                # MODIFY
└── FindDebriefsUseCase.java                # MODIFY

application/src/test/java/io/archivist/application/usecase/
└── *UseCaseTest.java                       # NEW — eight tests (AC-12)

infrastructure/src/main/java/io/archivist/infrastructure/retrieval/
├── RetrievalStrategy.java                  # NEW — module-internal SPI (public in infra module)
├── RetrievalStrategyRegistry.java          # NEW — name → strategy; startup validation
├── KnowledgeGatewayImpl.java               # NEW — implements KnowledgeGateway
├── LexicalRetrievalStrategy.java           # NEW — first strategy
├── LexicalScorer.java                      # NEW — package-private scoring/tokenisation
├── RetrievalProperties.java                # NEW — active-strategy, max-results
├── RetrievalConfiguration.java             # NEW — beans: strategies, registry, gateway, use cases
└── ArchivistRetrievalAutoConfiguration.java # NEW — @AutoConfiguration entry point

infrastructure/src/main/resources/META-INF/spring/
└── org.springframework.boot.autoconfigure.AutoConfiguration.imports  # NEW

infrastructure/src/test/java/io/archivist/infrastructure/retrieval/
├── LexicalScorerTest.java                  # NEW — unit: scoring, tokenisation
├── LexicalRetrievalStrategyTest.java       # NEW — unit: filters, availability policy
├── LexicalRetrievalIntegrationTest.java    # NEW — fixture corpus end-to-end
└── support/RetrievalTestConfiguration.java # NEW — minimal Spring context

infrastructure/src/test/resources/fixture-corpus/
└── edge-cases/oversize-entry.md            # NEW or generated — AC-10, AC-11

transport/
├── build.gradle.kts                        # MODIFY — implementation(project(":infrastructure"))
├── src/main/java/.../ArchivistApplication.java   # MODIFY — remove StubUseCaseConfiguration
└── DELETE StubUseCaseConfiguration.java

transport/src/test/.../McpAdapterTestConfiguration.java  # UNCHANGED — mock port.in, no infra retrieval
```

**Unchanged by design**: `ArchivistMcpTools.java` (injects `port.in` only), MCP contract fixtures, `KnowledgeCorpus` interface.

---

## Retrieval Architecture

### Layer diagram (strategy swap invisible to MCP)

```text
┌─────────────────────────────────────────────────────────────┐
│  Transport (MCP)                                            │
│  ArchivistMcpTools → RetrieveContext / FindDecisions / …    │
│  Knows: port.in only                                        │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  Application                                                │
│  RetrieveContextUseCase → gateway.retrieve(Query.unrestricted(...)) │
│  FindDecisionsUseCase   → gateway.retrieve(Query.withType(..., DECISION)) │
│  Knows: port.out.KnowledgeGateway, domain.Query               │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────┐
│  Infrastructure — retrieval (PRIVATE)                       │
│  KnowledgeGatewayImpl                                       │
│       │ delegates to                                        │
│  RetrievalStrategyRegistry.getActive()                        │
│       │                                                     │
│       ├── LexicalRetrievalStrategy  ← active (Phase 1)      │
│       ├── Bm25RetrievalStrategy       ← Phase 2 (future)    │
│       └── HybridRetrievalStrategy     ← Phase 2 (future)    │
│                                                             │
│  LexicalRetrievalStrategy → KnowledgeCorpus.loadAll()       │
└─────────────────────────────────────────────────────────────┘
```

### Adding a new strategy (Phase 2+ checklist)

Documented in [contracts/retrieval-strategy-spi.md](contracts/retrieval-strategy-spi.md). Summary:

1. Implement `RetrievalStrategy` in `infrastructure.retrieval.<name>`
2. Register as `@Bean` in `RetrievalConfiguration`; add to `RetrievalStrategyRegistry`
3. Document supported `name()` string (e.g. `"bm25"`)
4. Set `archivist.retrieval.active-strategy=<name>` to activate
5. Add infrastructure tests — **no** changes to `transport.mcp`, `domain.port.in`, or MCP fixtures

---

## Implementation Phases

### Phase A: Domain `Query` enrichment

1. Extend `Query` record: `text`, `Set<KnowledgeType> types`, `Set<KnowledgeZone> zones`, `int maxResults`
2. Add compact factories per [contracts/query-model.md](contracts/query-model.md):
   - `unrestricted(text, maxResults)` — `retrieveContext`, `findRelatedKnowledge`
   - `withType(text, type, maxResults)` — single-type capabilities
   - `withTypes(text, types, maxResults)` — `findConcepts`
3. Compact constructor validates: non-null sets (empty = no filter), `maxResults > 0`
4. Domain unit tests for factories and validation
5. `./gradlew :domain:compileJava :domain:test`

### Phase B: Retrieval strategy SPI and lexical implementation

1. `RetrievalStrategy` interface — `String name()`, `List<Evidence> retrieve(Query query)`
2. `LexicalScorer` — tokenise query; score title×3 + tag×2 + body×1; case-insensitive contains
3. `LexicalRetrievalStrategy`:
   - `KnowledgeCorpus.loadAll()`
   - Apply type/zone filters from `Query`
   - Apply `ContentAvailability` policy (title/tags only when `UNAVAILABLE_ENTRY_TOO_LARGE`)
   - Score, sort descending, dedupe by `sourceId`, limit to `maxResults`
4. Plain JUnit unit tests for scorer and strategy (mock `KnowledgeCorpus`)
5. `./gradlew :infrastructure:compileJava`

### Phase C: Gateway and registry

1. `RetrievalStrategyRegistry` — constructor takes `List<RetrievalStrategy>` + active name; fail fast on unknown name or duplicate `name()`
2. `KnowledgeGatewayImpl` — `retrieve(Query)`: null → NPE; blank text → empty list; else delegate to registry active strategy
3. `RetrievalProperties` — `activeStrategy` default `lexical`, `maxResults` default `20`
4. Unit test: registry rejects unknown strategy at construction (simulates startup validation)

### Phase D: Application use cases

1. Update all eight use cases — constructor-inject `KnowledgeGateway` + `RetrievalProperties` (for default maxResults) OR pass `maxResults` from a domain constant/factory default
2. Each use case maps capability → `Query` factory (see spec §3 table)
3. Eight `*UseCaseTest` classes — Mockito verify `gateway.retrieve` called with expected `Query`
4. `./gradlew :application:test`

**Preferred maxResults wiring**: use cases read default from a constructor-injected `int defaultMaxResults` (primitive) supplied by `RetrievalConfiguration` bean factory — keeps application Spring-free.

### Phase E: Spring wiring and auto-configuration

1. `RetrievalConfiguration` — beans:
   - `LexicalRetrievalStrategy`
   - `RetrievalStrategyRegistry` (registers all strategy beans)
   - `KnowledgeGateway` → `KnowledgeGatewayImpl`
   - Eight use case beans (`RetrieveContext`, `FindDecisions`, …)
2. `ArchivistRetrievalAutoConfiguration` — `@AutoConfiguration` + `@Import({RetrievalConfiguration.class, SecondBrainConfiguration.class})`
3. Register in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
4. `application.properties` defaults:
   ```properties
   archivist.retrieval.active-strategy=lexical
   archivist.retrieval.max-results=20
   ```

### Phase F: Composition root (transport)

1. `transport/build.gradle.kts` — add `implementation(project(":infrastructure"))`
2. `ArchivistApplication` — remove `@Import(StubUseCaseConfiguration.class)`; keep `TransportJacksonConfiguration` only
3. Delete `StubUseCaseConfiguration.java`
4. Add `TransportLayerIsolationTest` (or Gradle task) — assert no `import io.archivist.infrastructure` in `transport/src/main/java` (AC-2)
5. **Do not change** `McpAdapterTestConfiguration` — transport tests continue mocking `port.in`; contract fixtures unchanged

### Phase G: Fixture corpus and integration tests

1. Add `edge-cases/oversize-entry.md` to fixture corpus (or `@TempDir` generated in test) — title matches test query, body would match but is not loaded
2. `RetrievalTestConfiguration` — wires `SecondBrainKnowledgeCorpus` (fixture path) + retrieval beans
3. `LexicalRetrievalIntegrationTest` — assert against [fixture-lexical-expected.json](contracts/fixture-lexical-expected.json):
   - AC-4, AC-5, AC-6, AC-7, AC-8, AC-9, AC-10, AC-11, AC-13, AC-16
4. `./gradlew :infrastructure:test`

### Phase H: Verification

Run [quickstart.md](quickstart.md). `./gradlew build` — all modules green; MCP contract tests pass with mocks (AC-1).

---

## Lexical Scoring Summary

| Signal                             | Weight | Notes                                      |
| ---------------------------------- | ------ | ------------------------------------------ |
| Query term in `provenance.title()` | 3      | Per matching term                          |
| Query term in `provenance.tags()`  | 2      | Per tag containing term                    |
| Query term in `evidence.content()` | 1      | Skipped when `UNAVAILABLE_ENTRY_TOO_LARGE` |
| Type filter (`Query.types()`)      | gate   | Empty set = all types                      |
| Zone filter (`Query.zones()`)      | gate   | Empty set = all zones                      |
| Tie-break                          | —      | Stable order by `sourceId` ascending       |

Tokenisation: `text.toLowerCase().split("[\\s\\p{Punct}]+")` — discard empty tokens.

---

## Complexity Tracking

No constitution violations.

| Violation | Why Needed | Simpler Alternative Rejected Because |
| --------- | ---------- | ------------------------------------ |
| —         | —          | —                                    |

---

## Phase 0 Output

See [research.md](research.md) — strategy SPI placement, auto-configuration composition root, transport test isolation, lexical scoring.

## Phase 1 Output

- [data-model.md](data-model.md) — `Query` enrichment, infrastructure retrieval class model
- [contracts/](contracts/) — gateway semantics, query factories, strategy SPI extension guide, lexical fixture expectations
- [quickstart.md](quickstart.md) — verification guide

## Related ADRs

- [ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md) — full scan per retrieval; no index in Phase 1
- [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md) — lexical applies availability policy at retrieval layer

## Next Step

Run **`/speckit-tasks`** to generate `tasks.md` reflecting Phase A–H and AC-1–AC-16, then **`/speckit-taskstoissues`** or **`/speckit-implement`** after spec approval.
