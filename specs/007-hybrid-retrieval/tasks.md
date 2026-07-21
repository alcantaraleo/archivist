# Tasks: Hybrid Consensus Retrieval Strategy

**Input**: Design documents from `specs/007-hybrid-retrieval/`

**Prerequisites**: plan.md ✅ | spec.md ✅ (approved 2026-07-20) | research.md ✅ | data-model.md ✅ | contracts/ ✅ | quickstart.md ✅ | [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md) ✅

**Stack**: Java 21 · Spring Boot 4.1.0 · Gradle (Kotlin DSL) · specs 003–006 (retrieval SPI, embedding stub CI path)

**Tests**: AC-1–AC-16. Domain/application unchanged. Infrastructure: unit ranker + strategy tests; `@SpringBootTest(classes = RetrievalTestConfiguration.class)` integration. Hybrid CI uses `stub` + `memory`.

**User story mapping**:

| Story | Deliverable | AC |
| ----- | ----------- | -- |
| US1 | `HybridProperties` + `RetrievalProperties` nest | AC-8, config contract |
| US2 | `HybridConsensusRanker` | AC-9, AC-10 |
| US3 | `HybridRetrievalStrategy` | AC-5–AC-7, AC-11 |
| US4 | Spring registration + fail-fast | AC-4, AC-13 |
| US5 | Fixture corpus + integration + JSON scenario | AC-12 |
| US6 | Docs + README roadmap | AC-14, AC-15 |

---

## Phase 1: Setup (Prerequisite Gate)

**Purpose**: Confirm 004–006 baseline before hybrid code.

- [x] T001 Verify specs 004–006 — `./gradlew :infrastructure:test --tests "*LexicalRetrievalIntegrationTest*" --tests "*Bm25RetrievalIntegrationTest*" --tests "*EmbeddingRetrievalIntegrationTest*" --tests "*RetrievalStrategyRegistryTest*"` pass
- [x] T002 Verify 007 contract fixtures align with [spec.md](spec.md) §3–5 and [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md); fix doc drift only

**Checkpoint**: Leg strategies green; contracts ready for tests.

---

## Phase 2: User Story 1 — Hybrid configuration (P1)

- [x] T003 [US1] Create `HybridProperties.java` — `maxResults` default `20`, `@Min(1)` per [hybrid-configuration.md](contracts/hybrid-configuration.md)
- [x] T004 [US1] Extend `RetrievalProperties.java` — nested `hybrid`; ensure auto-configuration binds nest
- [x] T005 [US1] Create `HybridPropertiesTest.java` — defaults; env binding smoke if pattern exists elsewhere

**Checkpoint**: Config bindable; no strategy yet.

---

## Phase 3: User Story 2 — Consensus ranker (P2)

- [x] T006 [US2] Create `HybridConsensusRanker.java` — pure consensus + tie-break per [hybrid-consensus-algorithm.md](contracts/hybrid-consensus-algorithm.md)
- [x] T007 [US2] Create `HybridConsensusRankerTest.java` — AC-9 ordering 3>2>1; AC-10 tie-break; deterministic `sourceId` fallback

**Checkpoint**: Ranker testable without Spring.

---

## Phase 4: User Story 3 — Hybrid strategy (P3)

- [x] T008 [US3] Create `HybridRetrievalStrategy.java` — inject three leg strategies + corpus; fan-out same `Query`; **no** `getActive()`; assemble `Evidence`
- [x] T009 [US3] Create `HybridRetrievalStrategyTest.java` — Mockito legs; blank query empty; cap `min(query.maxResults(), hybrid.maxResults)`

**Checkpoint**: Strategy logic unit-tested.

---

## Phase 5: User Story 4 — Spring registration (P4)

- [x] T010 [US4] Register `HybridRetrievalStrategy` bean in `RetrievalConfiguration` — wire lexical, BM25, embedding beans
- [x] T011 [US4] Extend `RetrievalStrategyRegistryTest` — `hybrid` resolves; unknown strategy unchanged

**Checkpoint**: AC-4 registry includes hybrid.

---

## Phase 6: User Story 5 — Integration + fixtures (P5)

- [x] T012 [P] [US5] Add fixture corpus markdown entries for [fixture-hybrid-consensus-scenario.json](contracts/fixture-hybrid-consensus-scenario.json) if not present
- [x] T013 [US5] Create `HybridRetrievalIntegrationTest.java` — stub embedder; AC-5–AC-7, AC-12 JSON-driven scenario

**Checkpoint**: Hybrid path green on fixture corpus.

---

## Phase 7: User Story 6 — Polish (P6)

- [x] T014 [P] [US6] Update root `README.md` — check Phase 2 hybrid roadmap item; link ADR-0006, quickstart (AC-15)
- [x] T015 [P] [US6] Run `./gradlew build` — AC-1–AC-3, AC-16; MCP contract tests unchanged

**Checkpoint**: Feature complete pending spec approval merge.

---

## Dependencies

```text
Phase 1 → Phase 2 → Phase 3 → Phase 4 → Phase 5 → Phase 6 → Phase 7
```

Phase 3 and 4 can overlap after US1 (T003–T005).

---

## Implementation note

Spec **Approved** 2026-07-20 — implementation may proceed on this branch per AGENTS.md §13.
