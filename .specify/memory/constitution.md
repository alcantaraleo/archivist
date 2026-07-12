<!--
SYNC IMPACT REPORT
Version change: (none) → 1.0.0  (initial ratification)
Added sections: Core Principles (I–V), Technology Constraints, Development Workflow, Governance
Removed sections: N/A (first version)
Templates requiring updates:
  ✅ .specify/templates/plan-template.md — Constitution Check gates filled; Technical Context pre-filled
  ✅ .specify/templates/spec-template.md — replaced user-story format with Archivist domain-capability
      format (Motivation, Responsibilities, Public Contract, Acceptance Criteria, Domain Model Impact,
      Architectural Impact, Out of Scope, Open Questions, Approval)
  ✅ .specify/memory/constitution.md — this file
Follow-up TODOs: none
-->

# Archivist Constitution

## Core Principles

### I. Clean Architecture (NON-NEGOTIABLE)

The domain layer MUST be executable and testable without Spring Boot, Spring AI, or the MCP SDK.
Dependencies MUST always point inward: transport → application → domain. Outward dependencies are never permitted.
Frameworks (Spring Boot, Spring AI, MCP SDK, vector stores, embedding providers) MUST be treated as plugins — they implement interfaces defined by the domain, never the reverse.
Any class in `domain.*` or `application.*` that imports `org.springframework.*` or `io.modelcontextprotocol.*` is an architectural violation and MUST be rejected in review.
Domain and application layers MUST carry no Spring annotations (`@Component`, `@Service`, `@Bean`, `@Autowired`, etc.).

### II. Domain-Driven Public Contract (NON-NEGOTIABLE)

Public capabilities MUST express domain concepts, never retrieval technology.
MUST: `retrieveContext`, `findDecisions`, `findProjects`, `findPeople`, `findConcepts`, `findRelatedKnowledge`, `findReadings`, `findDebriefs`
MUST NOT: `vectorSearch`, `bm25Search`, `graphSearch`, `readFile`, `grep`, `getEmbedding`
The public MCP contract (port.in interfaces) MUST remain stable as retrieval strategies evolve.
Adding capabilities is permitted. Removing or renaming requires a deprecation period and specification approval.

### III. Retrieval, Never Reasoning (NON-NEGOTIABLE)

Archivist MUST only retrieve evidence. It MUST never reason, summarise, interpret, or generate responses.
All public capabilities MUST return `List<Evidence>`. Returning strings, summaries, or primitive types is a contract violation.
Every `Evidence` instance MUST include a `Provenance` with: `sourceId`, `title`, `KnowledgeType`, `KnowledgeZone`, `tags`, `sources` (chain to raw material), `created`, `updated`.
Reasoning, synthesis, and interpretation are the sole responsibility of the consuming agent.

### IV. Specification-Driven Development

Every significant capability MUST begin with a specification before any implementation.
Workflow: Issue → Specification (`docs/specs/` or `specs/`) → Review → Approved → Implement → Verify → Merge.
Implementation MUST NOT begin before specification approval.
Code MUST NOT become the source of architectural truth. Specifications are the primary design artifact.
Specifications MUST define: motivation, responsibilities, public contract (capability signature + return shape), acceptance criteria, architectural impact.

### V. Replaceable Infrastructure & Evidence Provenance

Retrieval strategies MUST be private implementation details behind `domain.port.out` interfaces.
Concrete retrieval classes (lexical, BM25, hybrid, graph) MUST live in `infrastructure.retrieval` and MUST NOT be referenced from `application` or `domain` layers.
Switching retrieval strategies MUST require zero changes to `domain.port.in` interfaces.
Evidence provenance MUST never be omitted. Every retrieved `Evidence` MUST be traceable to its origin in Second Brain.
Archivist MUST NOT couple to Obsidian internals: folder paths, wikilink syntax, YAML frontmatter, or file naming conventions.

## Technology Constraints

**Language**: Java 21+
**Framework**: Spring Boot 3.x (infrastructure and transport layers only)
**AI / Retrieval integration**: Spring AI (infrastructure layer only)
**Build**: Gradle with Kotlin DSL exclusively — Maven is never permitted
**MCP transport**: Spring AI MCP Server (transport layer only)
**Testing**: JUnit 5, Mockito; domain and application layer tests MUST NOT use Spring test runner
**Injection**: Constructor injection everywhere; field injection (`@Autowired` on fields) is never permitted
**Imports**: No star imports; always qualified imports
**Value objects**: Use Java records for immutable domain entities (`Evidence`, `Provenance`, `Query`, etc.)

**Layer import rules** (violations are build failures):

- `domain.*` → imports nothing outside `domain.*`
- `application.*` → imports `domain.*` only
- `infrastructure.*` → imports `domain.*`, `application.*`, `spring.*`, external libs; never `transport.*`
- `transport.*` → imports `domain.model.*`, `application.*`, `spring.*`, `mcp.*`; never `infrastructure.*` directly

## Development Workflow

**Lifecycle labels**: `spec: draft` → `spec: review` → `spec: approved` → implementation → `under-review` → `pending-release` → `released`

**PR titles**: MUST follow Conventional Commits — `type(scope): description`
Scopes that signal domain work: `capability`, `retrieval`, `domain`, `transport`, `infra`

**Spec location**: `docs/specs/SPEC-NNNN-capability-name.md` for domain capability specs; `specs/NNN-feature/` for spec kit feature specs

**Tests**: Domain tests MUST be plain JUnit (no Spring context). Infrastructure tests MAY use `@SpringBootTest`. All tests MUST describe behavior in names, not implementation.

**Release**: Automated via Release Please from conventional commits. `feat` and `fix` prefixes trigger version bumps.

**Issue structure**: Every spec produces exactly one GitHub epic issue. Below the epic, create one sub-issue per phase/user story (as defined in `tasks.md`). Tasks (`T001`, `T002`, …) are `- [ ] TNNN: description` checkboxes inside their phase/story sub-issue body — they MUST NOT become standalone issues. When running `/speckit-taskstoissues`, always follow this hierarchy:

```
Epic issue (one per spec)
  └── Sub-issue: Phase 1 — Setup            (tasks as checkboxes)
  └── Sub-issue: Phase 2 — Foundational     (tasks as checkboxes)
  └── Sub-issue: US1 — <story title> (P1)   (tasks as checkboxes)
  └── Sub-issue: US2 — <story title> (P2)   (tasks as checkboxes)
  └── ...
  └── Sub-issue: Polish & Verification       (tasks as checkboxes)
```

**Issue manifest**: `/speckit-taskstoissues` MUST write `specs/<feature>/github-issues.md` with the epic number, every sub-issue number, and a ready-to-paste **PR closing keywords** block.

**PR closing rule**: The implementation PR MUST include `Closes #NNN` for the epic and **every** sub-issue listed in `github-issues.md`. GitHub does not cascade-close sub-issues when the epic closes — explicit keywords are required.

## Governance

This constitution supersedes all other practices. When conflicts arise, this document is authoritative.
The extended reference for AI-assisted development is `AGENTS.md` at the repository root — read it completely before proposing or implementing any change.

**Amendment procedure**:

- Amendments MUST be documented with rationale
- Amendments MUST be approved before implementation
- AGENTS.md MUST be updated if principles change
- Constitution version MUST be incremented: MAJOR for principle removal/redefinition, MINOR for new principle/section, PATCH for clarifications

**Compliance**: All PRs MUST be verified against the Constitution Check in the implementation plan before merge. Complexity violations MUST be justified in the plan's Complexity Tracking table.

If a request conflicts with an Architectural Invariant (Principles I, II, or III), stop and surface the conflict — do not implement the violation.

**Version**: 1.1.0 | **Ratified**: 2026-07-12 | **Last Amended**: 2026-07-12
