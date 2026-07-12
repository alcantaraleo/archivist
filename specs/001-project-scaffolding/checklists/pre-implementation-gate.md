# Pre-Implementation Gate Checklist: Project Scaffolding

**Feature**: `specs/001-project-scaffolding`
**Scope**: `spec.md` · `plan.md` · `tasks.md`
**Purpose**: Formal gate — all items must be resolved before the spec is marked **Approved** and implementation begins
**Audience**: Author (self-review) + peer reviewer
**Created**: 2026-07-12

> Use this checklist as a blocking gate.
> Items marked **CRITICAL** are hard blockers — the spec cannot be approved with these open.
> Items marked **HIGH** are significant risks — resolve or explicitly accept before approval.
> Items marked **MEDIUM / LOW** are quality improvements — address if time permits or record a rationale for deferral.

---

## Legend

| Symbol | Meaning                             |
| ------ | ----------------------------------- |
| `[ ]`  | Not yet reviewed                    |
| `[x]`  | Confirmed — no issue found          |
| `[!]`  | Issue found — action required       |
| `[~]`  | Accepted gap — rationale documented |

---

## Category 1 — Requirement Completeness

_Are all elements needed to verify the spec fully present?_

- [ ] **CHK001** `[HIGH]` spec §5 states "introduces no new domain types" but tasks T009–T022 create 13 fully-specified Java source files (enums, records, interfaces) with explicit field lists. A reviewer reading §5 could conclude no Java types are created, which is false. Does §5 accurately describe what is being delivered? → `spec.md §5 · tasks.md T009–T022`

- [ ] **CHK002** `[HIGH]` AC-5 ("application module declares compile dependency on domain only") is not assigned to any user story in the tasks.md mapping table. No row in the table claims to satisfy AC-5. Is AC-5 verified by an existing task or is it an unowned acceptance criterion? → `spec.md §4 AC-5 · tasks.md user-story table`

- [ ] **CHK003** `[MEDIUM]` The env-var table in spec §6 lists only `ARCHIVIST_SECOND_BRAIN_PATH`. Is this explicitly documented as the exhaustive list for this spec's scope, or could a reviewer expect additional variables? The word "Additional variables are added here as each capability spec introduces new infrastructure dependencies" implies this is complete — but it is not stated as such. → `spec.md §6`

- [ ] **CHK004** `[MEDIUM]` spec §4 AC-9 says "zero tests is acceptable" yet plan.md Technical Context declares JUnit 5 as a primary dependency. Why is JUnit 5 declared in the technology stack for a spec that explicitly permits no tests? Is this inconsistency intentional (tests added in a follow-up)? → `spec.md §4 AC-9 · plan.md Technical Context`

- [ ] **CHK005** `[MEDIUM]` No acceptance criterion explicitly verifies that the domain layer is independently testable without a Spring context (i.e., plain `./gradlew :domain:test` with zero Spring dependencies). This is Architectural Invariant 1. Is deferring this verification acceptable for the scaffold? → `spec.md §4 (gap) · AGENTS.md §3.1`

- [ ] **CHK006** `[LOW]` AC-13 requires "documented list of required environment variables exists in README.md **or equivalent**". Is "equivalent" defined? Can `quickstart.md` serve as the equivalent, or is `README.md` at the repo root the only accepted location? → `spec.md §4 AC-13`

---

## Category 2 — Requirement Clarity

_Are requirements specific and unambiguous enough to be verified?_

- [ ] **CHK007** `[HIGH]` AC-14 requires "a descriptive error message identifying the missing variable". This is not measurable as written. Does it require the environment variable name (`ARCHIVIST_SECOND_BRAIN_PATH`) to appear verbatim in the error output? Or does the property binding path (`archivist.second-brain.path`) satisfy the criterion? Spring's `BindValidationException` uses the property path, not the env-var name. → `spec.md §4 AC-14`

- [ ] **CHK008** `[HIGH]` AC-8 "logs `Started ArchivistApplication`" — is this log string a contractual requirement (must match exactly) or illustrative? In STDIO mode, Spring Boot redirects standard output to the MCP client. Is the startup log visible on stderr, in a separate log file, or absorbed by the STDIO channel? How does the verifier observe it? → `spec.md §4 AC-8 · quickstart.md`

- [ ] **CHK009** `[HIGH]` AC-3 requires "at least one compilable Java source file" per module. Does a `package-info.java` (which contains only a package declaration) satisfy this criterion? Phases 4 and 5 deliver only `package-info.java` files. If package-info.java does not count as a "compilable Java source file" for AC-3 purposes, those modules fail AC-3. → `spec.md §4 AC-3 · tasks.md T023, T024, T025`

- [ ] **CHK010** `[MEDIUM]` AC-14 says "absent or empty" triggers startup failure. Does "empty" include whitespace-only strings? `@NotBlank` (used in T027) rejects whitespace-only; `@NotEmpty` does not. Is the whitespace-only case explicitly in scope or explicitly excluded? → `spec.md §4 AC-14 · tasks.md T027`

- [ ] **CHK011** `[MEDIUM]` The constraint "no default — absence must trigger startup failure" (tasks.md T029) is not stated explicitly in spec §6 configuration table. The table says "Yes — absence causes startup failure (AC-14)" but does not require the absence of a default value in `application.properties`. Is the no-default constraint derivable from the spec alone, or does a reviewer need tasks.md to understand it? → `spec.md §6 · tasks.md T029`

---

## Category 3 — Requirement Consistency

_Do requirements align without contradicting each other?_

- [~] **CHK012** `[CRITICAL → ACCEPTED]` spec §5 says "this specification introduces no new domain types" but the deliverable includes 13 Java source files defining `KnowledgeType`, `KnowledgeZone`, `Query`, `Provenance`, `Evidence`, 8 port.in interfaces, and `KnowledgeGateway`. **Rationale for acceptance**: this is the bootstrap specification — by definition it establishes the initial domain skeleton. After this spec merges, §5's invariant ("introduces no new domain types") will apply correctly to all subsequent specs. The wording in §5 is acknowledged as misleading for this one-time bootstrap case but is accepted as-is. → `spec.md §5 · tasks.md T009–T022`

- [ ] **CHK013** `[HIGH]` tasks.md "Implementation Strategy — Full Scaffold" step 3 says "Phase 6 (US4) after **US3** complete". But the Phase Dependencies section says "Phase 6 depends on **Phase 2**" (not US3). These two sections contradict each other. If US4 does not depend on US3, the implementation strategy is misleading. → `tasks.md §Implementation Strategy · tasks.md §Phase Dependencies`

- [ ] **CHK014** `[HIGH]` tasks.md T006 (application `build.gradle.kts`) is in Phase 2 and tagged `[P]` but is NOT tagged `[US2]`. Tasks T007 (infrastructure) and T008 (transport) are also in Phase 2 but not tagged with user story IDs. However, AC-5 (application), AC-6 (infrastructure), AC-7 (transport) are ownership-verified by the build files. The traceability chain from these ACs to their verifying tasks is broken. → `tasks.md T006, T007, T008 · spec.md §4 AC-5, AC-6, AC-7`

- [ ] **CHK015** `[MEDIUM]` spec §6 module structure diagram shows only `build.gradle.kts` files — no Java source files. The architectural decision to place `ArchivistProperties` in `transport.config` is described in the §6 Configuration Pattern section but is not visible in the module tree. Could a reviewer approve the module structure from §6 without understanding the source layout? → `spec.md §6`

- [ ] **CHK016** `[MEDIUM]` spec §5 establishes 6 package locations to reserve. tasks.md creates source in `domain/model`, `domain/port/in`, `domain/port/out`, `application/usecase`, `infrastructure/retrieval`, and `infrastructure/secondbrain`. The root `io.archivist.infrastructure` package is never explicitly created as a standalone package — only subpackages are staked. Is the root infrastructure package location assumed to be created by its subpackages, or is there a gap? → `spec.md §5 · tasks.md T024, T025`

---

## Category 4 — Acceptance Criteria Quality

_Are success criteria measurable, independent, and sufficient?_

- [ ] **CHK017** `[HIGH]` AC-9 (`./gradlew test` executes without failures) is vacuously satisfied when there are zero tests. Gradle's `test` task exits with `BUILD SUCCESSFUL` even with no test classes. Does this criterion provide any gate value, or should it be replaced with a more meaningful structural check? → `spec.md §4 AC-9`

- [ ] **CHK018** `[HIGH]` AC-12 grep pattern (as implemented in tasks.md T034) checks for `/home/`, `/Users/`, `localhost`, `127.0.0.1`. It does not catch other forms of hardcoded configuration such as Windows paths, `0.0.0.0`, numeric IP addresses, or database ports. Is the partial coverage of AC-12 acceptable, or does the spec need a more precise definition of "hardcoded"? → `spec.md §4 AC-12 · tasks.md T034`

- [ ] **CHK019** `[MEDIUM]` AC-8 verification requires observing the startup log in STDIO mode while `./gradlew :transport:bootRun` blocks the terminal. `quickstart.md` describes this step, but the spec AC itself does not reference the quickstart procedure. Is the AC independently verifiable from the spec, or does it require reading quickstart.md? → `spec.md §4 AC-8 · quickstart.md`

- [ ] **CHK020** `[MEDIUM]` AC-10 and AC-11 define "no star imports" and "no field injection" as observable properties, but the exact verification commands are only in tasks.md T032 and T033, not in the spec. Should the operational definition (the grep commands) be included in the ACs to make them self-contained? → `spec.md §4 AC-10, AC-11 · tasks.md T032, T033`

---

## Category 5 — Edge Cases & Boundary Conditions

_Are boundaries explicitly addressed or explicitly excluded?_

- [ ] **CHK021** `[HIGH]` `ARCHIVIST_SECOND_BRAIN_PATH` set to a non-empty string pointing to a non-existent directory: `@NotBlank` validates string length, not path existence. Does the scaffold validate path existence? If not, is this explicitly out of scope? The spec §7 does not mention this case. → `spec.md §4 AC-14 · spec.md §7`

- [ ] **CHK022** `[MEDIUM]` STDIO server shutdown: STDIO servers terminate when the parent process (e.g., Cursor, Claude Desktop) disconnects. Is graceful shutdown in scope or explicitly excluded? The spec §7 Out of Scope does not mention it. → `spec.md §7`

- [ ] **CHK023** `[LOW]` tasks.md T001: `gradle wrapper --gradle-version latest` uses a floating version reference. The Gradle version installed at generation time becomes the pinned version in `gradle-wrapper.properties`. Is a specific Gradle version required to be pinned in the spec, or is "latest at time of generation" acceptable? → `tasks.md T001`

- [ ] **CHK024** `[LOW]` Is Gradle wrapper checksum verification (`distributionSha256Sum` in `gradle-wrapper.properties`) required by the spec? This prevents supply-chain attacks on the Gradle distribution. It is not mentioned as a requirement or out-of-scope item. → `tasks.md T001 (gap)`

---

## Category 6 — Dependencies & Assumptions

_Are technology choices confirmed and assumptions documented?_

- [ ] **CHK025** `[HIGH]` spec §8 resolves Spring Boot 4.1.0 and Spring AI 2.0.0 by reference to `research.md`. For a self-contained approvable artifact, should the spec embed the version numbers directly? A reviewer who does not read research.md will not know the pinned versions. → `spec.md §8 Open Questions`

- [ ] **CHK026** `[HIGH]` Spring AI 2.0.0 + Spring Boot 4.1.0 compatibility is stated as a resolved decision in plan.md but the supporting evidence is in research.md. Is the compatibility of this combination documented as **verified** (test result, release notes) or only as an assumption? → `plan.md Technical Context · research.md`

- [ ] **CHK027** `[MEDIUM]` plan.md Technical Context says "infrastructure tests may use `@SpringBootTest`" but Phase 5 (infrastructure) delivers only `package-info.java` files with zero test-relevant source. Does this statement unintentionally imply infrastructure tests will be written in this spec? Could it mislead the implementer? → `plan.md Technical Context · tasks.md Phase 5`

---

## Category 7 — Cross-Artifact Traceability

_Can every AC be traced to a task, and every task traced back to a requirement?_

- [x] **CHK028** `[CRITICAL → RESOLVED]` AC-5 now has an owning task — T006 has been tagged `[US2]` in `tasks.md`. → `spec.md §4 AC-5 · tasks.md T006`

- [x] **CHK029** `[HIGH → RESOLVED]` T007 has been tagged `[US3]` in `tasks.md`, establishing ownership of AC-6 verification. → `spec.md §4 AC-6 · tasks.md T007`

- [x] **CHK030** `[HIGH → RESOLVED]` T008 has been tagged `[US4]` in `tasks.md`, establishing ownership of AC-7 verification. → `spec.md §4 AC-7 · tasks.md T008`

- [ ] **CHK031** `[MEDIUM]` The `transport` module dependency rule in AC-7 says "does not depend on `infrastructure` directly". T008 states this in the implementation comment, but there is no dedicated verification task confirming it (comparable to T005's checkpoint for `domain`). Should there be a `./gradlew :transport:dependencies --configuration compileClasspath` checkpoint task explicitly for transport? → `spec.md §4 AC-7 · tasks.md T008`

- [ ] **CHK032** `[MEDIUM]` The architectural decision to place `ArchivistProperties` in `transport` (not `infrastructure`) is significant and was revised during spec development. AGENTS.md §12 says significant architectural decisions should be recorded as ADRs in `docs/adr/`. Is this decision captured in an ADR? → `AGENTS.md §12 · spec.md §6 Configuration Pattern`

---

## Category 8 — Constitutional Alignment

_Does the spec comply with AGENTS.md and constitution.md invariants?_

- [ ] **CHK033** `[HIGH]` Architectural Invariant 1 (domain executable without Spring Boot): spec AC-4 verifies domain has no Spring compile dependency at build time, but no AC verifies the domain is _runnable_ (i.e., tests execute) without Spring. Is build-time isolation sufficient for this spec, or should a runtime isolation test be required? → `AGENTS.md §3.1 · spec.md §4 AC-4`

- [ ] **CHK034** `[MEDIUM]` Architectural Invariant 8 (stable public contract): no port.in interfaces are changed by this spec. However, tasks T014–T021 establish the initial signatures for all 8 port.in interfaces. These initial signatures become the stable contract. Is the signature for each port.in interface in tasks.md consistent with the canonical signatures in AGENTS.md §8? → `AGENTS.md §8 · tasks.md T014–T021`

- [ ] **CHK035** `[LOW]` Specification Workflow (AGENTS.md §13): "Implementation must not begin before specification approval." spec §9 Approval table is empty. Is the spec being submitted for review, or has the review process already occurred informally? The approval gate must be explicitly closed before any branch work begins. → `AGENTS.md §13 · spec.md §9`

---

## Summary

| Category                        | Total  | CRITICAL | HIGH   | MEDIUM | LOW   |
| ------------------------------- | ------ | -------- | ------ | ------ | ----- |
| 1 — Completeness                | 6      | 0        | 2      | 3      | 1     |
| 2 — Clarity                     | 5      | 0        | 3      | 2      | 0     |
| 3 — Consistency                 | 5      | 1        | 2      | 2      | 0     |
| 4 — Acceptance Criteria Quality | 4      | 0        | 2      | 2      | 0     |
| 5 — Edge Cases                  | 4      | 0        | 1      | 1      | 2     |
| 6 — Assumptions                 | 3      | 0        | 2      | 1      | 0     |
| 7 — Traceability                | 5      | 1        | 2      | 2      | 0     |
| 8 — Constitutional Alignment    | 3      | 0        | 1      | 1      | 1     |
| **Total**                       | **35** | **2**    | **15** | **14** | **4** |

### Hard blockers before approval

| ID     | Status         | Summary                                                                                                       |
| ------ | -------------- | ------------------------------------------------------------------------------------------------------------- |
| CHK012 | `[~]` Accepted | Bootstrap spec by definition establishes the initial domain skeleton; §5 wording accepted as-is for this spec |
| CHK028 | `[x]` Resolved | T006 tagged `[US2]`, T007 tagged `[US3]`, T008 tagged `[US4]` in `tasks.md`                                   |

### Risk areas requiring resolution or explicit acceptance

| Risk Area                     | Items                                          |
| ----------------------------- | ---------------------------------------------- |
| Clean Architecture boundaries | CHK002, CHK014, CHK033                         |
| Configuration contract        | CHK007, CHK010, CHK011, CHK021, CHK025, CHK026 |
| Domain model specification    | CHK001, CHK034                                 |
| AC measurability              | CHK008, CHK009, CHK017, CHK018                 |
