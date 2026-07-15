# Alignment Checklist: Lexical Retrieval Strategy and Retrieval Foundation

**Purpose**: Lightweight author sanity check that spec, plan, tasks, and contracts agree before `/speckit-implement`
**Created**: 2026-07-14
**Feature**: [spec.md](../spec.md) · [plan.md](../plan.md) · [tasks.md](../tasks.md) · [contracts/](../contracts/)

**Depth**: Lightweight (~18 items) · **Focus**: Cross-artifact alignment only · **Actor**: Author (pre-implement)

---

## Spec ↔ Contracts

- [x] CHK001 Are all eight `port.in` → type-filter mappings identical across [Spec §3](../spec.md), [query-model.md](../contracts/query-model.md), and tasks T019–T026? [Consistency]
- [x] CHK002 Do [knowledge-gateway-port.md](../contracts/knowledge-gateway-port.md) blank/null/empty-result semantics match [Spec §3](../spec.md) gateway table without contradiction? [Consistency]
- [x] CHK003 Does [fixture-lexical-expected.json](../contracts/fixture-lexical-expected.json) declare scenarios for every integration AC (AC-4–AC-11, AC-13, AC-16) that T047 claims to assert? [Traceability, Completeness]
- [x] CHK004 Is the Phase 2 strategy-extension path documented once and consistently in [retrieval-strategy-spi.md](../contracts/retrieval-strategy-spi.md), [plan.md](../plan.md) “Adding a new strategy”, and AC-15? [Consistency]

## Spec ↔ Plan ↔ Tasks (coverage)

- [x] CHK005 Is every numbered AC (AC-1–AC-16) owned by at least one task or tasks.md user-story mapping row? [Traceability, Completeness]
- [x] CHK006 Do plan Phases A–H map 1:1 onto tasks Phases 3–10 / US1–US8 without orphaned plan work or tasks with no plan home? [Consistency] - Note: Tasks Phase 1 pulls Gradle classpath forward from plan Phase F; Phase 2 verifies contracts already produced in plan Phase 1 output — intentional, not orphans.
- [x] CHK007 Are plan source-tree deliverables (`Query`, SPI, lexical, gateway, eight use cases, auto-config, stub removal, isolation test, integration test) each named by a concrete task ID? [Completeness]
- [x] CHK008 Is lexical scoring (title×3 / tags×2 / body×1, tokenisation, `sourceId` tie-break) stated the same way in [plan.md](../plan.md), tasks T010, and research/data-model? [Consistency]

## Layer & composition boundaries

- [x] CHK009 Is `RetrievalStrategy` consistently required to stay out of `domain.port.out` / `application` / `transport` across Spec §6, Constitution V notes in plan, and [retrieval-strategy-spi.md](../contracts/retrieval-strategy-spi.md)? [Consistency] - **Fixed**: Spec §6 said “package-private”; aligned to module-internal SPI (public in `infrastructure.retrieval`, not a domain port) to match plan/contracts/data-model. Registry visibility tightened in data-model.
- [x] CHK010 Is transport composition-root behaviour identical across Spec §6, plan Phase F, and tasks T001 + T040–T042 (classpath yes; Java imports no; stubs removed)? [Consistency]
- [x] CHK011 Is AC-2 enforcement assigned to a concrete task (`TransportLayerIsolationTest` / T042) and reflected in plan Phase F? [Traceability]
- [x] CHK012 Is `defaultMaxResults` injection (primitive from infra config, Spring kept out of application) consistent between plan Phase D preference and tasks T019 / T036? [Consistency]

## Scope & workflow hygiene

- [x] CHK013 Are out-of-scope items (BM25/embeddings/hybrid, graph `findRelatedKnowledge`, indexing/caching, MCP schema changes) excluded from tasks.md (no task invents them)? [Consistency, Boundary]
- [x] CHK014 Is the spec 003 `KnowledgeCorpus` dependency explicit in Spec §8 assumptions, plan Summary, and foundational task T004? [Dependency]
- [x] CHK015 Is [Spec §9](../spec.md) “Open Questions” still accurate given [github-issues.md](../github-issues.md) already records epic #74? [Consistency, Gap] - **Fixed**: §9 now “None remaining” with resolved epic #74 / #75–#84.
- [x] CHK016 Does plan “Next Step” still claim `/speckit-tasks` is pending even though `tasks.md` and github issues already exist? [Consistency, Stale] - **Skipped (informative only)** — not treated as an action item; left as historical next-step prose.
- [x] CHK017 Is T056 (`/speckit-taskstoissues`) still required, or should it be marked done given github-issues.md already lists #74–#84? [Consistency, Stale] - **Fixed**: T056 marked done; Full Feature Path step 7 no longer implies re-running taskstoissues.
- [x] CHK018 Does the tasks.md US2 AC mapping listing AC-16 match where AC-16 is actually proven (integration timing in US7 / T047), or is that row overstating US2 coverage? [Consistency, Ambiguity] - **Fixed**: US2 mapping is now “AC-4–AC-11 (partial; unit-level)”; AC-16 remains US7 / T047 only.

---

## Validation Summary

| Result                  | Count                              |
| ----------------------- | ---------------------------------- |
| Pass as-written         | 13                                 |
| Fixed then pass         | 4 (CHK009, CHK015, CHK017, CHK018) |
| Skipped (informational) | 1 (CHK016)                         |

**Gate**: Ready for `/speckit-implement`.
