# Implementation Plan: Hybrid Consensus Retrieval Strategy

**Branch**: `007-hybrid-retrieval` | **Date**: 2026-07-20 | **Spec**: [spec.md](spec.md)  
**Status**: Plan complete (Phase 0–1) — spec **Approved** 2026-07-20; ready for implementation  
**Input**: Feature specification from `specs/007-hybrid-retrieval/spec.md`

---

## Summary

Add opt-in **`hybrid`** `RetrievalStrategy` that runs **lexical**, **BM25**, and **embedding** for the same `Query`, fuses by **consensus vote** (ADR-0006), tie-breaks **embedding → BM25 → lexical**, and caps with **`min(query.maxResults(), hybrid.max-results)`**.

**Public contract unchanged**: eight `port.in` capabilities, MCP tools, `KnowledgeGateway`, application use cases. Default **`lexical`**.

**ADR**: [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md) accepted alongside this spec.

---

## Technical Context

**Language/Version**: Java 21

**Primary Dependencies**: Unchanged from 006 (Spring Boot 4.1.0, Spring AI infra for embedding leg, Lucene for BM25)

**Build**: Gradle Kotlin DSL — **no new dependencies** expected

**Testing**:

| Layer | Focus |
| ----- | ----- |
| `domain` / `application` | No new tests |
| `infrastructure` unit | `HybridConsensusRanker` scoring, tie-break, cap |
| `infrastructure` integration | Fixture corpus + stub embedder; consensus JSON scenario |
| `transport` | MCP contract regression unchanged |

**Constraints**:

- Hybrid must not call `RetrievalStrategyRegistry.getActive()`
- No leg metadata on MCP/`Evidence`
- CI hybrid tests use `stub` + `memory` embedding path

---

## Constitution Check

| Gate | Check | Result |
| ---- | ----- | ------ |
| Domain independence | No new framework in domain/application | ✅ PASS |
| Contract language | No hybrid/fusion on `port.in` | ✅ PASS |
| No reasoning | `List<Evidence>` only | ✅ PASS |
| Strategy hidden | Single registry name `hybrid` | ✅ PASS |
| Stable contract | No `port.in` changes | ✅ PASS |
| Spec before implement | Spec approved before implementation | ✅ PASS — Approved 2026-07-20 |

---

## Project Structure

### Documentation (this feature)

```text
specs/007-hybrid-retrieval/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── tasks.md
├── contracts/
│   ├── README.md
│   ├── hybrid-configuration.md
│   ├── knowledge-gateway-hybrid-semantics.md
│   ├── hybrid-consensus-algorithm.md
│   ├── retrieval-strategy-spi.md
│   └── fixture-hybrid-consensus-scenario.json
└── github-issues.md          # after /speckit-taskstoissues
```

### Source (implementation phase)

```text
infrastructure/src/main/java/io/archivist/infrastructure/retrieval/
├── HybridRetrievalStrategy.java
├── HybridProperties.java
└── HybridConsensusRanker.java   # optional extract

infrastructure/src/test/java/.../
├── HybridConsensusRankerTest.java
├── HybridRetrievalStrategyTest.java
└── HybridRetrievalIntegrationTest.java
```

---

## Phase 0: ADR

- [x] [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md) accepted on branch

## Phase 1: Design artifacts

- [x] spec.md, research.md, data-model.md, contracts/, quickstart.md, plan.md

## Phase 2: Implementation (post-approval)

See [tasks.md](tasks.md) after `/speckit-tasks` generation or manual task list.

---

## Verification mapping

| AC | Verification |
| -- | -------------- |
| AC-1–AC-3 | `./gradlew build`; default lexical tests |
| AC-4 | Spring context / registry test with `active-strategy=hybrid` |
| AC-5–AC-8 | Integration + unit cap tests |
| AC-9–AC-10 | `HybridConsensusRankerTest` |
| AC-11 | MCP/transport regression + code review |
| AC-12 | JSON fixture integration test |
| AC-14–AC-15 | quickstart + README |

---

## References

- [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md)
- [spec 006 plan](../006-embedding-retrieval/plan.md)
