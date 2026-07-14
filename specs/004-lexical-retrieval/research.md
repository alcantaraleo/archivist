# Research: Lexical Retrieval Strategy and Retrieval Foundation

**Feature**: 004-lexical-retrieval
**Date**: 2026-07-13
**Status**: Complete — all technical decisions resolved

**User directive incorporated**:

- MCP and transport must remain unaware of which retrieval algorithm runs; new strategies must plug in without MCP changes.

---

## Decision 1: Strategy SPI Placement (Domain vs Infrastructure)

**Decision**: **`RetrievalStrategy` is an infrastructure-module interface** in `io.archivist.infrastructure.retrieval` — NOT a `domain.port.out` interface.

**Rationale**: Constitution V states retrieval strategies are _private implementation details_. The domain exposes one stable retrieval port: `KnowledgeGateway.retrieve(Query)`. Leaking `LexicalRetrievalStrategy`, `Bm25RetrievalStrategy`, etc. into `domain.port.out` would:

- Encourage application/transport to depend on algorithm names
- Violate Invariant 2 (domain before implementation) if capabilities ever referenced strategy types
- Complicate Phase 2 hybrid composition (multiple strategies behind one gateway)

Application layer depends only on `KnowledgeGateway`. Strategy selection is entirely inside infrastructure.

**Alternatives considered**:

- `domain.port.out.RetrievalStrategy`: Rejected — exposes algorithm concept in domain; violates "strategies are private"
- Anonymous strategy via class name only (no interface): Rejected — no registry extension point for Phase 2
- Spring `ApplicationContext` lookup by bean name in gateway: Rejected — hides contract; harder to test; framework leak in gateway logic

---

## Decision 2: Strategy Registration and Selection

**Decision**: **`RetrievalStrategyRegistry`** — constructed at startup with all `RetrievalStrategy` beans; validates `archivist.retrieval.active-strategy` against registered `name()` values; **fail fast** on unknown or duplicate names.

**Rationale**: Phase 1 has one strategy (`lexical`). Phase 2 adds beans + registry entries without changing gateway or use cases. Configuration-only switch matches spec assumption (no runtime API). Fail-fast prevents silent fallback to wrong algorithm in production.

**Phase 2 extension** (no MCP/transport changes):

```text
1. Create Bm25RetrievalStrategy implements RetrievalStrategy
2. @Bean in RetrievalConfiguration
3. Registry auto-collects List<RetrievalStrategy>
4. archivist.retrieval.active-strategy=bm25
```

**Alternatives considered**:

- `@ConditionalOnProperty` per strategy bean with single active bean: Rejected — does not scale to hybrid (multiple strategies active)
- Enum in domain for strategy names: Rejected — leaks implementation catalogue into domain
- Runtime strategy switching API: Rejected — out of scope; config-only for Phase 1

---

## Decision 3: MCP Obliviousness — Composition Root Pattern

**Decision**: **Spring Boot auto-configuration in infrastructure** + **Gradle classpath dependency from transport** — transport Java source imports zero infrastructure types.

| Mechanism                                                                          | Role                                                                             |
| ---------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- |
| `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | Loads `ArchivistRetrievalAutoConfiguration` when infrastructure JAR on classpath |
| `ArchivistRetrievalAutoConfiguration`                                              | `@Import` retrieval + Second Brain configuration                                 |
| `transport/build.gradle.kts`                                                       | `implementation(project(":infrastructure"))` — composition root only             |
| `ArchivistMcpTools`                                                                | Unchanged — injects `port.in` interfaces only                                    |
| `McpAdapterTestConfiguration`                                                      | Unchanged — Mockito mocks for `port.in`; no real retrieval in contract tests     |

**Rationale**: AGENTS.md layer rule forbids `transport.*` importing `infrastructure.*`. Auto-configuration satisfies composition root needs without import violations. MCP contract tests intentionally isolate transport from retrieval — they verify tool schemas and serialisation, not search quality.

**Alternatives considered**:

- `@SpringBootApplication(scanBasePackages = "io.archivist")` in transport: Rejected — broad scan couples transport bootstrap to infra package layout; still needs classpath dep
- `@Import(RetrievalConfiguration.class)` in `ArchivistApplication`: Rejected — requires transport source import of infrastructure class
- Separate `bootstrap` module: Rejected — YAGNI for Phase 1; auto-config is sufficient

---

## Decision 4: Use Case Bean Ownership

**Decision**: **Use case `@Bean` definitions live in `RetrievalConfiguration` (infrastructure)** — remove `StubUseCaseConfiguration` from transport.

**Rationale**: Use cases need `KnowledgeGateway` implementation, which only infrastructure provides. Transport must not construct use cases with stubs once retrieval exists. Infrastructure already owns `KnowledgeGateway` and `KnowledgeCorpus` beans; co-locating use case beans keeps wiring in one module. Application classes remain Spring-free POJOs.

**Alternatives considered**:

- Keep stub config in transport, real config in infrastructure with `@Primary`: Rejected — confusing; two sources of truth
- `@Component` on use cases in application: Rejected — violates "no Spring in application"
- Manual wiring in `ArchivistApplication`: Rejected — transport would import infrastructure types

---

## Decision 5: Enriched `Query` Model

**Decision**: Extend `Query` record with `types`, `zones`, `maxResults` and **static factories** for capability patterns.

| Factory                                    | Used by                                                                       |
| ------------------------------------------ | ----------------------------------------------------------------------------- |
| `Query.unrestricted(text, maxResults)`     | `retrieveContext`, `findRelatedKnowledge`                                     |
| `Query.withType(text, type, maxResults)`   | `findDecisions`, `findProjects`, `findPeople`, `findReadings`, `findDebriefs` |
| `Query.withTypes(text, types, maxResults)` | `findConcepts` (`CONCEPT`, `SYNTHESIS`)                                       |

Empty `types` / `zones` sets mean **no filter** (all eligible).

**Rationale**: Keeps retrieval logic in infrastructure; use cases remain one-liner translators from capability semantics to `Query`. Factories prevent ad-hoc `Set.of()` duplication and document intent.

**Alternatives considered**:

- Separate `QueryBuilder` class: Rejected — unnecessary abstraction for three patterns
- Capability-specific `Query` subclasses: Rejected — sealed hierarchy overkill
- Pass `KnowledgeType` as gateway method parameter: Rejected — changes `KnowledgeGateway` signature; couples gateway to filter mechanics

---

## Decision 6: Lexical Matching Algorithm (Phase 1)

**Decision**: **Substring match on normalised tokens** with weighted scoring — no inverted index, no BM25, no embeddings.

| Step           | Behaviour                                                               |
| -------------- | ----------------------------------------------------------------------- |
| Tokenise query | Lowercase; split on whitespace + Unicode punctuation; drop empty tokens |
| Filter         | Apply `Query.types()` / `Query.zones()` gates                           |
| Availability   | `UNAVAILABLE_ENTRY_TOO_LARGE`: match title + tags only; skip body       |
| Score          | Sum per term: title +3, tag +2, body +1                                 |
| Rank           | Descending score; tie-break `sourceId` ascending                        |
| Dedupe         | First occurrence per `sourceId`                                         |
| Limit          | `Query.maxResults()`                                                    |

**Rationale**: Deterministic, testable, sufficient for Phase 1 personal corpus. Full `loadAll()` scan aligns with ADR-0002 deferral of indexing. Weights favour metadata matches agents see in provenance.

**Alternatives considered**:

- `String.contains` on full query string (no tokenisation): Rejected — poor multi-word behaviour
- Regex word boundaries only: Rejected — marginal gain over simple split for Markdown prose
- Lucene in-process: Rejected — new dependency; BM25 is Phase 2

---

## Decision 7: `ContentAvailability` at Retrieval Layer

**Decision**: Lexical strategy **includes** `UNAVAILABLE_ENTRY_TOO_LARGE` entries when title or tags match; **excludes** when only body would match.

**Rationale**: Implements spec §3 and [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md). Infrastructure flagged the entry; retrieval applies trust policy. Agents still discover oversized entries via title/tag relevance.

**Alternatives considered**:

- Exclude all non-`AVAILABLE` entries: Rejected — loses discoverability ADR-0003 preserves
- Include all flagged entries regardless of match: Rejected — pollutes results

---

## Decision 8: Transport Test Strategy

**Decision**: **Keep `McpAdapterTestConfiguration` with Mockito `port.in` mocks** — do not boot real retrieval in transport tests.

**Rationale**: MCP contract tests (002) verify tool registration, input validation, and JSON serialisation shape. Retrieval correctness belongs in infrastructure integration tests. This guarantees AC-1 (contract fixtures unchanged) and AC-15 (transport handlers never touched when adding strategies).

**Alternatives considered**:

- Full-stack transport test with fixture corpus: Rejected — couples contract tests to retrieval; slower; violates separation
- Shared abstract test base across modules: Rejected — unnecessary coupling

---

## Decision 9: Transport Layer Isolation Enforcement

**Decision**: **`TransportLayerIsolationTest`** in transport module — scans `transport/src/main/java` sources and fails if any line contains `import io.archivist.infrastructure`.

**Rationale**: Machine-verifies AC-2; prevents accidental regression when developers add convenience imports during wiring changes.

**Alternatives considered**:

- ArchUnit dependency: Rejected for now — simple source scan sufficient; spec says "ArchUnit or source scan"
- Gradle forbidden-dependencies plugin: Rejected — Gradle dep on infrastructure is required and intentional

---

## Decision 10: `findRelatedKnowledge` Phase 1 Semantics

**Decision**: **Same as `retrieveContext`** — `Query.unrestricted(query, maxResults)`; lexical match across all types/zones.

**Rationale**: Graph traversal is Phase 3. Tool description unchanged per spec. Agents receive keyword-related evidence until graph spec ships.

**Alternatives considered**:

- Return empty list with documented limitation: Rejected — poor agent experience; lexical fallback is strictly better
- Boost entries with matching `provenance.sources()`: Deferred — optional enhancement; not required for AC

---

## Open Items Resolved

| Item                               | Resolution                                                                  |
| ---------------------------------- | --------------------------------------------------------------------------- |
| Where does strategy SPI live?      | `infrastructure.retrieval` only                                             |
| How does MCP stay oblivious?       | `port.in` → use case → `KnowledgeGateway` → private strategy                |
| How to add BM25 later?             | New `RetrievalStrategy` + registry entry + config                           |
| Transport tests vs real retrieval? | Transport mocks `port.in`; infra integration tests retrieval                |
| Default result limit source?       | `archivist.retrieval.max-results` injected as primitive into use case beans |
