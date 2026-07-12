# Pre-Implementation Gate Checklist: MCP Transport Adapter

**Feature**: `specs/002-mcp-transport-adapter`
**Branch**: `002-mcp-transport-adapter`
**Scope**: Spec-kit deliverables produced to date · gate before `/speckit-implement`
**Purpose**: Confirm all specification-phase artifacts are complete, aligned, and approved before writing code
**Audience**: Author (self-review) + peer reviewer
**Created**: 2026-07-12
**Last updated**: 2026-07-12 (post `/speckit-analyze` remediation)

> Use this checklist as a blocking gate.
> Items marked **CRITICAL** are hard blockers — do not start implementation with these open.
> Items marked **HIGH** are significant risks — resolve or explicitly accept before implementation.
> Items marked **MEDIUM / LOW** are quality improvements — address if time permits or record a rationale for deferral.

---

## Legend

| Symbol | Meaning                             |
| ------ | ----------------------------------- |
| `[ ]`  | Not yet done                        |
| `[x]`  | Complete — verified                 |
| `[!]`  | Issue found — action required       |
| `[~]`  | Accepted gap — rationale documented |

---

## Category 1 — Spec-Kit Core Artifacts

_Are all required design documents present and approved?_

- [x] **DEL001** `[CRITICAL]` **spec.md** exists, status **Approved**, approval table signed (2026-07-12) → `spec.md §9`
- [x] **DEL002** `[CRITICAL]` **plan.md** exists, status **Approved**, Constitution Check all PASS → `plan.md §Constitution Check`
- [x] **DEL003** `[CRITICAL]` **tasks.md** exists with 28 dependency-ordered tasks (T001–T028) → `tasks.md`
- [x] **DEL004** `[HIGH]` **research.md** complete — all technical decisions resolved → `research.md`
- [x] **DEL005** `[HIGH]` **data-model.md** documents transport classes, stub use cases, serialisation rules → `data-model.md`
- [x] **DEL006** `[HIGH]` **quickstart.md** verification guide with AC mapping → `quickstart.md §Acceptance Criteria Mapping`

---

## Category 2 — Contract Fixtures

_Are machine-readable contract fixtures in place for build-time regression?_

- [x] **DEL007** `[CRITICAL]` **mcp-tools-expected.json** — eight tools with name, description, inputSchema → `contracts/mcp-tools-expected.json`
- [x] **DEL008** `[CRITICAL]` **mcp-server-identity-expected.json** — `name: archivist`, `version: 0.1.0` → `contracts/mcp-server-identity-expected.json`
- [x] **DEL009** `[CRITICAL]` **evidence-response-schema-expected.json** — canonical Evidence/Provenance JSON shape → `contracts/evidence-response-schema-expected.json`
- [x] **DEL010** `[HIGH]` **contracts/README.md** — fixture purpose, change procedure, comparison mechanism → `contracts/README.md`
- [x] **DEL011** `[HIGH]` Fixtures derived from authoritative contract → `specs/001-project-scaffolding/contracts/mcp-server.md` (verified in T003 scope)

---

## Category 3 — Quality & Analysis Gates

_Have specification quality checks and cross-artifact analysis been completed?_

- [x] **DEL012** `[HIGH]` **requirements.md** spec-quality checklist — all items pass → `checklists/requirements.md`
- [x] **DEL013** `[HIGH]` **`/speckit-analyze`** run — cross-artifact consistency report produced
- [x] **DEL014** `[CRITICAL]` Analyze finding **C1** resolved — constitution updated to Spring Boot **4.x** (v1.1.1) → `.specify/memory/constitution.md`
- [x] **DEL015** `[HIGH]` Analyze finding **I1** resolved — single serialisation path via `EvidenceJsonMapper` documented across spec/plan/tasks/research → `spec.md §3 · research.md D3`
- [x] **DEL016** `[HIGH]` Analyze finding **E1** resolved — T021 expanded for AC-9(d) sample-evidence fixture match → `tasks.md T021`
- [x] **DEL017** `[MEDIUM]` Remaining analyze items addressed — parameterized invalid-input (AC-9b), delegation failure (AC-9e), explicit test context (T019), transport-only stub wiring (spec §6), T028 for GitHub issues → `spec.md §4 AC-9 · tasks.md`

---

## Category 4 — Cross-Artifact Alignment

_Do spec, plan, tasks, and contracts tell a consistent story?_

- [x] **DEL018** `[CRITICAL]` Eight MCP tool names match `domain.port.in` capability names 1:1 → `spec.md §3 · data-model.md`
- [x] **DEL019** `[CRITICAL]` Transport MUST NOT import `infrastructure` — stated in spec AC-7, plan constraints, tasks T024 → `spec.md §4 AC-7`
- [x] **DEL020** `[HIGH]` Stub use cases in `application.usecase`; Spring `@Bean` wiring in `transport.config` only → `spec.md §6 · research.md D2`
- [x] **DEL021** `[HIGH]` `@McpTool` handlers return JSON from `EvidenceJsonMapper` only — no parallel Jackson path → `spec.md §3 Serialisation rule · plan.md Phase B`
- [x] **DEL022** `[HIGH]` All 15 acceptance criteria (AC-1–AC-15) mapped to tasks or quickstart steps → `tasks.md User story mapping · quickstart.md`
- [x] **DEL023** `[MEDIUM]` Open questions in spec §8 all resolved (including serialisation path clarification) → `spec.md §8`

---

## Category 5 — Constitutional & Dependency Readiness

_Is the project constitution aligned and upstream scaffold in place?_

- [x] **DEL024** `[CRITICAL]` Constitution Technology Constraints match pinned stack (Spring Boot 4.1.0, Spring AI 2.0.0) → `constitution.md · gradle/libs.versions.toml`
- [x] **DEL025** `[CRITICAL]` Scaffold (001) delivered — domain `port.in` interfaces and domain model types exist → `domain/src/main/java/io/archivist/domain/`
- [x] **DEL026** `[HIGH]` Authoritative MCP human contract exists in 001 → `specs/001-project-scaffolding/contracts/mcp-server.md`
- [x] **DEL027** `[HIGH]` Plan Constitution Check — all 11 gates PASS pre- and post-design → `plan.md §Constitution Check`

---

## Category 6 — GitHub & Workflow (Pending)

_Tracking artifacts required before implementation PR — not yet produced._

- [x] **DEL028** `[HIGH]` Run **`/speckit-taskstoissues`** — creates epic + phase/story sub-issues → `tasks.md T028` · epic #48
- [x] **DEL029** `[HIGH]` **github-issues.md** manifest written with PR closing keywords block → `specs/002-mcp-transport-adapter/github-issues.md`
- [x] **DEL030** `[MEDIUM]` GitHub epic issue linked in spec header (`Issue: #48`) → `spec.md` header

---

## Category 7 — Implementation (Not Started)

_Code deliverables — all pending; tracked here for visibility._

- [x] **DEL031** `[CRITICAL]` Phase 1 — test infrastructure (T001–T002): `spring-boot-starter-test` on `:transport`
- [x] **DEL032** `[CRITICAL]` Phase 2 — fixture verification (T003)
- [x] **DEL033** `[CRITICAL]` Phase 3 — eight application stub use cases (T004–T011)
- [x] **DEL034** `[CRITICAL]` Phase 4 — MCP transport adapter core (T012–T016)
- [x] **DEL035** `[HIGH]` Phase 5 — transport unit tests (T017–T018)
- [x] **DEL036** `[HIGH]` Phase 6 — contract regression + adapter integration tests (T019–T021)
- [x] **DEL037** `[HIGH]` Phase 7 — polish & full quickstart verification (T022–T027)

---

## Summary

**Spec-kit gate status: ✓ PASS (specification phase complete)**

**Implementation gate status: ✓ READY — start `/speckit-implement`**

| Category                                 | Total  | Complete | Pending | Open issues |
| ---------------------------------------- | ------ | -------- | ------- | ----------- |
| 1 — Spec-Kit Core Artifacts              | 6      | 6        | 0       | 0           |
| 2 — Contract Fixtures                    | 5      | 5        | 0       | 0           |
| 3 — Quality & Analysis Gates             | 6      | 6        | 0       | 0           |
| 4 — Cross-Artifact Alignment             | 6      | 6        | 0       | 0           |
| 5 — Constitutional & Dependency          | 4      | 4        | 0       | 0           |
| 6 — GitHub & Workflow                    | 3      | 3        | 0       | 0           |
| 7 — Implementation                       | 7      | 0        | 7       | 0           |
| **Specification phase (Categories 1–5)** | **27** | **27**   | **0**   | **0**       |
| **Full feature (all categories)**        | **37** | **30**   | **7**   | **0**       |

### Recommended next steps

1. **Start implementation**: `/speckit-implement` — begin at Phase 1 (T001)
2. **MVP checkpoint**: After Phases 1–4, validate eight tools via `:transport:bootRun` + MCP client

### Artifact inventory

```text
specs/002-mcp-transport-adapter/
├── spec.md                          ✅ Approved · Issue #48
├── plan.md                          ✅ Approved
├── tasks.md                         ✅ 28 tasks (T028 done)
├── research.md                      ✅ Complete
├── data-model.md                    ✅ Complete
├── quickstart.md                    ✅ Complete
├── github-issues.md                 ✅ Epic #48 + sub-issues #49–#55
├── contracts/
│   ├── README.md                    ✅
│   ├── mcp-tools-expected.json      ✅
│   ├── mcp-server-identity-expected.json ✅
│   └── evidence-response-schema-expected.json ✅
└── checklists/
    ├── requirements.md              ✅ All pass
    └── pre-implementation-gate.md   ✅ This file
```
