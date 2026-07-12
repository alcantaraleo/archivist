# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Java 21+

**Primary Dependencies**: Spring Boot 3.x, Spring AI, Spring AI MCP Server

**Build**: Gradle (Kotlin DSL) — never Maven

**Storage**: Determined per retrieval strategy (implementation detail; hidden behind `domain.port.out`)

**Testing**: JUnit 5 + Mockito; domain/application tests plain JUnit (no Spring context); infrastructure tests may use `@SpringBootTest`

**Target Platform**: JVM / Linux server

**Project Type**: MCP server (domain-driven retrieval service)

**Performance Goals**: [NEEDS CLARIFICATION per capability — define per spec]

**Constraints**: Domain layer MUST be testable without running server; retrieval strategies MUST be swappable without contract changes

**Scale/Scope**: Personal knowledge system (Second Brain); single-user; optimise for retrieval quality over throughput

## Constitution Check

_GATE: Must pass before Phase 0 research. Re-check after Phase 1 design._

| Gate                    | Principle                            | Check                                                                                                                    |
| ----------------------- | ------------------------------------ | ------------------------------------------------------------------------------------------------------------------------ |
| Domain independence     | I. Clean Architecture                | Does any `domain.*` or `application.*` class import `org.springframework.*` or `io.modelcontextprotocol.*`? → MUST be NO |
| Contract language       | II. Domain-Driven Public Contract    | Do all public capability names use domain terms (not `vectorSearch`, `bm25`, `readFile`)? → MUST be YES                  |
| No reasoning in output  | III. Retrieval, Never Reasoning      | Do all capabilities return `List<Evidence>` (not strings, summaries, or primitives)? → MUST be YES                       |
| Provenance completeness | III. Retrieval, Never Reasoning      | Does every `Evidence` include a `Provenance` with `sourceId`, `type`, `zone`, `sources`? → MUST be YES                   |
| Spec approved           | IV. Specification-Driven Development | Is there an approved specification before implementation begins? → MUST be YES                                           |
| Strategy hidden         | V. Replaceable Infrastructure        | Are all retrieval implementations behind `domain.port.out` interfaces? → MUST be YES                                     |
| No Obsidian coupling    | V. Replaceable Infrastructure        | Does any code reference Obsidian folder paths, wikilink syntax, or YAML frontmatter? → MUST be NO                        |
| Build tool              | Technology Constraints               | Is Gradle (Kotlin DSL) used exclusively? No Maven? → MUST be YES                                                         |
| Injection style         | Technology Constraints               | Is constructor injection used everywhere? No `@Autowired` on fields? → MUST be YES                                       |

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# [REMOVE IF UNUSED] Option 1: Single project (DEFAULT)
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [REMOVE IF UNUSED] Option 2: Web application (when "frontend" + "backend" detected)
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [REMOVE IF UNUSED] Option 3: Mobile + API (when "iOS/Android" detected)
api/
└── [same as backend above]

ios/ or android/
└── [platform-specific structure: feature modules, UI flows, platform tests]
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation                  | Why Needed         | Simpler Alternative Rejected Because |
| -------------------------- | ------------------ | ------------------------------------ |
| [e.g., 4th project]        | [current need]     | [why 3 projects insufficient]        |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient]  |
