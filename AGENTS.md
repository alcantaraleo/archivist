# AGENTS.md — Archivist Project Constitution

This document is the authoritative guide for AI-assisted development on Archivist.

Read this document completely before proposing or implementing any change.
If a request conflicts with anything written here, stop and raise the conflict explicitly rather than proceeding.

---

## Table of Contents

1. [Project Philosophy](#1-project-philosophy)
2. [Architectural North Star](#2-architectural-north-star)
3. [Architectural Invariants](#3-architectural-invariants)
4. [Clean Architecture Expectations](#4-clean-architecture-expectations)
5. [Package Organisation](#5-package-organisation)
6. [Coding Standards](#6-coding-standards)
7. [Domain Model](#7-domain-model)
8. [Public Capabilities](#8-public-capabilities)
9. [Second Brain Domain Context](#9-second-brain-domain-context)
10. [Testing Expectations](#10-testing-expectations)
11. [Dependency Rules](#11-dependency-rules)
12. [Documentation Standards](#12-documentation-standards)
13. [Specification Workflow](#13-specification-workflow)
14. [AI Decision Policy](#14-ai-decision-policy)
15. [How to Propose New Capabilities](#15-how-to-propose-new-capabilities)
16. [Good vs Bad Architectural Decisions](#16-good-vs-bad-architectural-decisions)
17. [What Archivist Deliberately Does Not Do](#17-what-archivist-deliberately-does-not-do)

---

## 1. Project Philosophy

Archivist exists to provide stable, domain-driven access to a personal knowledge system called **Second Brain**.

It is a retrieval layer — nothing more.

The distinction between _retrieval_ and _reasoning_ is the foundational design principle of this project. Archivist retrieves evidence. External agents reason about it. This boundary is absolute and must never be blurred.

The public contract is the only thing consumers depend on. The retrieval implementation is entirely private. It must be free to evolve — from simple lexical search today to hybrid retrieval, knowledge graphs, and learned query planning in the future — without any change to the public interface.

This project values:

- **Simplicity** over cleverness
- **Explicitness** over magic
- **Maintainability** over brevity
- **Replaceability** of infrastructure
- **Long-term evolution** over short-term convenience

Architecture should evolve incrementally. Do not introduce complexity before it is needed.

---

## 2. Architectural North Star

> Archivist exists to provide stable, domain-driven access to knowledge while allowing retrieval strategies to evolve independently.

Every architectural decision should reinforce this separation:

- **The domain retrieves** — domain capabilities define _what_ can be retrieved
- **The consuming agent reasons** — interpretation, synthesis, and answers belong to the consumer
- **The transport adapts** — MCP, REST, CLI are interchangeable adapters
- **The infrastructure serves the domain** — infrastructure implements interfaces defined by the domain

When multiple implementations are possible, choose the one that best preserves this architecture.

---

## 3. Architectural Invariants

These rules are non-negotiable. Every implementation must preserve all of them.

### 3.1 Clean Architecture

The project follows Clean Architecture strictly.

The domain must be executable and testable without Spring Boot, Spring AI, or the MCP SDK. If a domain class requires Spring to run, the architecture has been violated.

### 3.2 Domain before implementation

Public capabilities represent domain concepts, never retrieval technologies.

```
// CORRECT — domain concept
retrieveContext(query: String): List<Evidence>
findDecisions(topic: String): List<Evidence>

// WRONG — retrieval technology leaked into the contract
vectorSearch(embedding: float[]): List<Document>
bm25Search(query: String, k: int): List<String>
```

### 3.3 Retrieval, never reasoning

Archivist retrieves evidence. It never:

- answers questions
- summarises content
- interprets or draws conclusions
- infers meaning from retrieved knowledge
- generates any free-form text response

If a method returns reasoning rather than evidence, it violates this invariant.

### 3.4 Retrieval strategies are replaceable

The retrieval implementation is private and may change at any time. Retrieval strategies implement interfaces defined by the domain (`port.out`). No consumer — including application-layer use cases — should depend on a concrete retrieval implementation.

### 3.5 MCP is an adapter

MCP is one transport mechanism. The domain must be reusable from:

- MCP (current transport)
- REST
- CLI
- tests (directly, without a running server)

If the domain can only be exercised through MCP, the architecture has been violated.

### 3.6 Frameworks are plugins

Spring Boot, Spring AI, MCP SDK, vector stores, embedding providers, and databases are implementation details. They must:

- implement interfaces defined by the domain
- never be imported in domain or application layers
- be substitutable without domain changes

### 3.7 Evidence must remain traceable

Every piece of retrieved evidence must preserve provenance — the origin of the knowledge: its source, location within the Second Brain, and, when available, its temporal context.

Evidence without provenance is not acceptable output.

### 3.8 Stable public contract

The domain capability interface (`port.in`) must remain stable as retrieval evolves. Adding new retrieval strategies, changing ranking algorithms, or switching storage backends must not require changes to the public capability interface.

---

## 4. Clean Architecture Expectations

Archivist uses the following Clean Architecture layers:

### Domain Layer (`domain`)

The innermost layer. No external dependencies whatsoever.

Contains:

- **Entities** — core domain objects (`Evidence`, `Source`, `Provenance`, `Query`, `Capability`)
- **Input ports** — interfaces representing public domain capabilities (`port.in`)
- **Output ports** — interfaces representing retrieval gateway contracts (`port.out`)
- **Domain services** — pure business logic that operates only on domain entities

Rules:

- Zero imports from `org.springframework`, `io.modelcontextprotocol`, or any infrastructure package
- No annotations from Spring (`@Component`, `@Service`, `@Repository`) in this layer
- Testable with plain JUnit, no Spring test runner

### Application Layer (`application`)

The use case layer. Implements input ports defined by the domain.

Contains:

- **Use case interactors** — concrete implementations of `port.in` interfaces
- **Orchestration logic** — coordinates domain entities and output ports

Rules:

- May import domain layer only
- Must not import Spring Boot, MCP SDK, or infrastructure
- Depends on domain output ports (`port.out`), never on concrete infrastructure classes
- Each use case class implements exactly one input port method, or one cohesive input port

### Infrastructure Layer (`infrastructure`)

Implements domain output ports using real technologies.

Contains:

- **Retrieval strategy implementations** — lexical search, BM25, embeddings, etc.
- **Second Brain adapters** — reads from the actual Second Brain
- **Gateway implementations** — concrete adapters for all `port.out` interfaces

Rules:

- May import Spring Boot, Spring AI, MCP SDK, external libraries
- Must implement `port.out` interfaces defined in the domain layer
- Must not be depended on by domain or application layers (dependency inversion)
- Retrieval strategy selection logic lives here, not in the domain

### Transport Layer (`transport`)

Exposes domain capabilities over external protocols.

Contains:

- **MCP adapter** — Spring AI MCP server that invokes application use cases
- **REST adapter** (future) — REST controllers
- **CLI adapter** (future) — command-line interface

Rules:

- May import Spring Boot, MCP SDK, and application layer
- Must not contain business logic
- Must not import domain directly, except to reference domain model types for responses
- Translates external protocol messages into domain capability invocations

---

## 5. Package Organisation

```
io.archivist
├── domain
│   ├── model
│   │   ├── Evidence.java          # A retrieved piece of knowledge
│   │   ├── Source.java            # Origin of a piece of knowledge
│   │   ├── Provenance.java        # Full traceability metadata
│   │   └── Query.java             # A domain query (not a search string)
│   ├── port
│   │   ├── in
│   │   │   ├── RetrieveContext.java
│   │   │   ├── FindDecisions.java
│   │   │   ├── FindProjects.java
│   │   │   ├── FindPeople.java
│   │   │   └── FindRelatedKnowledge.java
│   │   └── out
│   │       └── KnowledgeGateway.java   # Single or multiple retrieval port(s)
│   └── service                         # Pure domain logic (if needed)
├── application
│   └── usecase
│       ├── RetrieveContextUseCase.java
│       ├── FindDecisionsUseCase.java
│       └── ...
├── infrastructure
│   ├── retrieval
│   │   ├── LexicalRetrievalStrategy.java
│   │   ├── BM25RetrievalStrategy.java  # future
│   │   └── HybridRetrievalStrategy.java # future
│   └── secondbrain
│       └── SecondBrainKnowledgeGateway.java
└── transport
    ├── mcp
    │   └── ArchivistMcpServer.java
    └── rest                            # future
```

---

## 6. Coding Standards

### Java

- Java 21 or newer
- Use records for immutable domain value objects (`Evidence`, `Provenance`, etc.)
- Use sealed interfaces or sealed classes for closed-set domain hierarchies
- Prefer constructor injection everywhere; avoid field injection (`@Autowired` on fields)
- No star imports; always use qualified imports
- No `var` where the type is not obvious from the right-hand side
- `final` fields by default in domain and application layers

### Gradle

- Use Kotlin DSL (`build.gradle.kts`) exclusively; never use Groovy
- Never use Maven

### Spring Boot

- Spring annotations belong exclusively in infrastructure and transport layers
- Domain and application layers must not use `@Component`, `@Service`, `@Bean`, or any Spring annotation
- Configuration classes belong in the infrastructure or transport packages

### General

- Favour explicit code over clever code
- Do not introduce abstractions before they are needed
- A class should have one clear responsibility
- Method names should reflect domain intent, not implementation mechanism

---

## 7. Domain Model

The following are the primary domain concepts. They must reflect knowledge domain terminology, not retrieval technology terminology.

For the complete domain context — including knowledge zones, types, provenance model, and the knowledge graph — read `docs/second-brain-domain.md`. That document is the authoritative reference.

### Core Types

| Type            | Description                                                                                       |
| --------------- | ------------------------------------------------------------------------------------------------- |
| `Evidence`      | A single retrieved piece of knowledge with content and full provenance                            |
| `Provenance`    | Full traceability metadata: source identifier, title, type, zone, tags, sources chain, timestamps |
| `Query`         | A domain-level query expressing retrieval intent (not a raw search string)                        |
| `KnowledgeType` | The semantic type of a knowledge entry (see below)                                                |
| `KnowledgeZone` | The epistemic zone a knowledge entry belongs to (see below)                                       |

These types live exclusively in `domain.model`. They must not carry Spring or persistence annotations.

### KnowledgeType

Archivist recognises the following knowledge types. These map to the types used in Second Brain's knowledge layer, expressed as domain constants rather than implementation strings.

| Constant    | Meaning                                                          |
| ----------- | ---------------------------------------------------------------- |
| `CONCEPT`   | A synthesised understanding of a technical or professional topic |
| `ENTITY`    | A named thing: organisation, technology, tool, framework         |
| `PERSON`    | A named individual with professional context                     |
| `PROJECT`   | An active or past project or initiative                          |
| `DECISION`  | An architecture or product decision (ADR)                        |
| `DEBRIEF`   | An incident retrospective or learning review                     |
| `SYNTHESIS` | A cross-cutting insight connecting multiple concepts             |
| `READING`   | Source material: article, transcript, book note                  |

### KnowledgeZone

Archivist recognises the following epistemic zones. These are stable conceptual labels, not folder names or storage paths.

| Constant      | Epistemic Role                                                   |
| ------------- | ---------------------------------------------------------------- |
| `SOURCE`      | Immutable source material — highest fidelity to original         |
| `SYNTHESIZED` | LLM-maintained concepts and entities — highest semantic density  |
| `TECHNICAL`   | Architecture decisions, debriefs, technical project docs         |
| `IDENTITY`    | Personal principles, professional thesis, editorial constitution |
| `COMPILED`    | Pre-assembled operational context snapshot                       |
| `SIGNAL`      | Weekly signal extraction artifacts                               |

### Provenance

Every `Evidence` instance must include a `Provenance` with:

```
sourceId    — stable identifier for the knowledge entry (not a file path)
title       — human-readable title of the entry
type        — KnowledgeType
zone        — KnowledgeZone
tags        — list of semantic tags
sources     — list of source identifiers the entry was grounded in (the chain to raw material)
created     — creation timestamp
updated     — last modification timestamp
```

The `sources` field is the provenance chain to immutable source material. It must never be omitted if the entry has known sources.

---

## 8. Public Capabilities

The following are the stable public domain capabilities. These form the contract.

Every capability returns `List<Evidence>`. Evidence is neutral — it is content plus provenance. The consuming agent decides what to do with it.

| Capability             | Signature                             | Primary Types          | Description                                                    |
| ---------------------- | ------------------------------------- | ---------------------- | -------------------------------------------------------------- |
| `retrieveContext`      | `(query: String) → List<Evidence>`    | All                    | General contextual retrieval across the full knowledge base    |
| `findDecisions`        | `(topic: String) → List<Evidence>`    | `DECISION`             | Architecture and product decisions (ADRs)                      |
| `findProjects`         | `(criteria: String) → List<Evidence>` | `PROJECT`              | Active or past projects and initiatives                        |
| `findPeople`           | `(name: String) → List<Evidence>`     | `PERSON`               | Known individuals with professional context                    |
| `findConcepts`         | `(topic: String) → List<Evidence>`    | `CONCEPT`, `SYNTHESIS` | Technical and professional concepts and cross-cutting insights |
| `findRelatedKnowledge` | `(query: String) → List<Evidence>`    | All                    | Graph-traversal retrieval of semantically connected knowledge  |
| `findReadings`         | `(topic: String) → List<Evidence>`    | `READING`              | Source materials: articles, transcripts, book notes            |
| `findDebriefs`         | `(topic: String) → List<Evidence>`    | `DEBRIEF`              | Incident retrospectives and learning reviews                   |

### Contract Rules

- Signatures may evolve to richer parameter objects over time, but only through specification approval
- The concept names (capability names) are stable
- All return types are `List<Evidence>` — never strings, summaries, or primitive types
- Adding new capabilities is permitted
- Removing or renaming existing ones requires a deprecation period and specification approval

### Naming Convention

Capability names must follow domain terminology, not Second Brain implementation terminology:

| Use             | Do not use                            |
| --------------- | ------------------------------------- |
| `findDecisions` | `searchADRs`, `getADRs`               |
| `findPeople`    | `searchEntities` (for people queries) |
| `findDebriefs`  | `searchIncidents`, `getRawDebriefs`   |
| `findReadings`  | `searchClippings`, `getRawNotes`      |

---

## 9. Second Brain Domain Context

Archivist retrieves from a personal knowledge system called Second Brain. The full domain context is in `docs/second-brain-domain.md`. Key invariants for development:

### What Archivist may model

- Knowledge has a `KnowledgeType` (`CONCEPT`, `PERSON`, `DECISION`, `PROJECT`, `DEBRIEF`, `SYNTHESIS`, `READING`, `ENTITY`)
- Knowledge has a `KnowledgeZone` (`SOURCE`, `SYNTHESIZED`, `TECHNICAL`, `IDENTITY`, `COMPILED`, `SIGNAL`)
- Knowledge entries are connected via a semantic graph (concepts reference other concepts, projects contain decisions, etc.)
- Every entry has a provenance chain leading back to immutable source material

### What Archivist must never couple to

| Implementation Detail                  | Why                                                 |
| -------------------------------------- | --------------------------------------------------- |
| Obsidian as the storage engine         | Second Brain may be migrated                        |
| Folder paths (`raw/`, `Wiki/`, `dev/`) | Folders are an organisational implementation choice |
| Wikilink syntax (`[[...]]`)            | Obsidian-specific format                            |
| YAML frontmatter                       | Storage metadata convention                         |
| Markdown as the file format            | Files may be stored differently                     |
| Obsidian Base files                    | These are Obsidian-specific query views             |
| File naming conventions                | Implementation detail                               |

The domain model must remain valid if Second Brain's entire storage backend is replaced.

### Terminology

When writing code, specifications, or documentation, use domain terminology consistently:

| Use                          | Avoid                                  |
| ---------------------------- | -------------------------------------- |
| `KnowledgeZone`              | vault zone, Obsidian folder            |
| `KnowledgeType`              | note type, page type, frontmatter type |
| `Evidence`                   | document, note, file, page             |
| `Provenance`                 | metadata, frontmatter                  |
| `sourceId`                   | file path, vault path, note path       |
| `sources` (provenance chain) | backlinks, wikilinks                   |

---

## 10. Testing Expectations

### Domain Layer Tests

- Pure unit tests; zero Spring dependencies
- Must validate that domain rules and entity invariants hold
- Run without application context

### Application Layer Tests

- Unit tests using Mockito to mock output ports
- Validate use case orchestration logic
- Zero Spring dependencies

### Infrastructure Layer Tests

- Integration tests that validate concrete adapters against real or embedded systems
- May use Spring Boot test context (`@SpringBootTest`)
- Cover retrieval strategy correctness (recall, deduplication, provenance)

### Transport Layer Tests

- Integration or end-to-end tests via the transport protocol
- MCP adapter tests verify that capabilities are correctly wired and invocable

### General Rules

- The domain must be fully testable without a running server
- Do not skip tests to meet a deadline; untested infrastructure is unshippable
- Test names must describe behaviour, not implementation: `shouldReturnEmptyListWhenNoDecisionsFound`, not `testFindDecisions`

---

## 11. Dependency Rules

The following import rules are absolute. Violations must be treated as build failures.

| Layer            | May import                                         | Must never import                                      |
| ---------------- | -------------------------------------------------- | ------------------------------------------------------ |
| `domain`         | Nothing outside `domain`                           | `spring.*`, `mcp.*`, `infrastructure.*`, `transport.*` |
| `application`    | `domain` only                                      | `spring.*`, `mcp.*`, `infrastructure.*`, `transport.*` |
| `infrastructure` | `domain`, `application`, `spring.*`, external libs | `transport.*`                                          |
| `transport`      | `domain.model`, `application`, `spring.*`, `mcp.*` | `infrastructure.*` directly                            |

These rules should be enforced with ArchUnit tests as the project matures.

---

## 12. Documentation Standards

### Specifications

Every significant capability must have a specification in `docs/specs/` before implementation. Use `docs/specs/SPEC_TEMPLATE.md` as the format.

A specification must define:

- Motivation
- Responsibilities (what Archivist does; what it does not do)
- Public contract (capability signature and return shape)
- Acceptance criteria (numbered, verifiable conditions)
- Architectural impact (how this touches the layers)

### Code Comments

Comments should explain non-obvious intent, trade-offs, or constraints — never what the code already says. The following are prohibited:

```java
// Bad: narrates obvious code
// Call the gateway to retrieve evidence
List<Evidence> results = gateway.retrieve(query);

// Good: explains a non-obvious constraint
// Limit results to 20 to avoid overwhelming the consuming agent's context window
```

### ADRs (Architecture Decision Records)

Significant architectural decisions should be recorded in `docs/adr/` using the format: `ADR-NNNN-short-title.md`. Each ADR records the decision, its context, the options considered, and the rationale.

---

## 13. Specification Workflow

Archivist follows Specification-Driven Development.

```
Issue → Specification → Review → Approve → Implement → Verify → Merge
```

1. **Issue** — describe the capability, motivation, and constraints in a GitHub issue
2. **Specification** — write `docs/specs/SPEC-NNNN-capability-name.md` following the template
3. **Review** — specification is reviewed for architectural alignment
4. **Approve** — specification is explicitly approved before any implementation begins
5. **Implement** — implementation follows the approved specification strictly
6. **Verify** — each acceptance criterion is checked and confirmed
7. **Merge** — code is merged only when all criteria are met and review passes

**Implementation must not begin before specification approval.**

If the implementation diverges from the specification, update the specification first and have it re-approved.

### GitHub Issue Structure

Every spec produces exactly one epic issue. Phases and user stories from `tasks.md` become sub-issues of the epic. Individual tasks (`T001`, `T002`, …) are checklist items inside their phase/story sub-issue — they must never become standalone issues.

```
Epic issue              ← one per spec; implementation PR closes this
  ├── Phase 1 sub-issue      ← tasks T001–T00N as checkboxes; PR closes this
  ├── Phase 2 sub-issue      ← tasks as checkboxes; PR closes this
  ├── US1 sub-issue (P1)     ← tasks as checkboxes; PR closes this
  ├── US2 sub-issue (P2)     ← tasks as checkboxes; PR closes this
  └── Polish sub-issue       ← tasks as checkboxes; PR closes this
```

This structure keeps the issue tracker readable. Individual checkboxes can be promoted to GitHub sub-issues from the UI if a single task needs independent tracking.

**Creating issues** — run `/speckit-taskstoissues` after `tasks.md` is approved. The skill creates the epic, one sub-issue per phase/user story (via `gh issue create --parent`), and writes `specs/<feature>/github-issues.md` listing every issue number.

**Closing issues** — the single implementation PR for a spec MUST close the epic **and every sub-issue** explicitly. Do not rely on cascade from the epic; GitHub does not auto-close sub-issues when a parent closes.

1. Read `specs/<feature>/github-issues.md`
2. Copy the **PR closing keywords** block into the PR body under **Issues closed**
3. One `Closes #NNN` line per issue (epic first, then all sub-issues)

Example:

```
Closes #38
Closes #39
Closes #40
```

Use `.github/pull_request_template.md` — the **Issues closed** section is mandatory for every implementation PR.

---

## 14. AI Decision Policy

When multiple implementations are possible, apply this priority order:

1. **Preserve Architectural Invariants** — non-negotiable; stop if they would be violated
2. **Preserve Clean Architecture** — domain-first, inward dependencies
3. **Prefer domain-driven design** over infrastructure-driven design
4. **Prefer explicit code** over clever or concise code
5. **Prefer simple solutions** before introducing abstractions
6. **Avoid unnecessary dependencies** — each new dependency has a maintenance cost
7. **Never violate architectural boundaries** to make implementation easier or shorter

**If a request conflicts with an Architectural Invariant, stop and explain the conflict instead of implementing it.**

Do not resolve the conflict silently. Surface it.

---

## 15. How to Propose New Capabilities

To propose a new public capability:

1. Check that the capability is expressed in domain terms, not retrieval technology terms
2. Verify that the capability represents _retrieval of evidence_, not _reasoning or synthesis_
3. Open a GitHub issue with:
   - The capability name and proposed signature
   - The motivation (what agent need does it serve?)
   - Domain model types it returns
4. Write a specification in `docs/specs/` using the template
5. Wait for specification approval before writing any code

**A capability that leaks retrieval technology into its name or signature will not be approved.**

---

## 16. Good vs Bad Architectural Decisions

### Capability Design

```
// GOOD — domain concept, stable, implementation-agnostic
interface FindDecisions {
    List<Evidence> findDecisions(String topic);
}

// BAD — retrieval technology in the contract
interface FindDecisions {
    List<String> bm25Search(String query, int topK);
}
```

### Layer Dependencies

```java
// GOOD — application layer depends on output port (interface)
public class RetrieveContextUseCase implements RetrieveContext {
    private final KnowledgeGateway gateway; // port.out interface
}

// BAD — application layer imports concrete infrastructure
public class RetrieveContextUseCase implements RetrieveContext {
    private final ObsidianVaultReader vaultReader; // concrete infrastructure
}
```

### Domain Purity

```java
// GOOD — domain entity with no framework dependency
public record Evidence(String content, Provenance provenance) {}

// BAD — domain entity carries Spring/persistence annotations
@Entity
@Document
public class Evidence {
    @Id private String id;
    @Column private String content;
}
```

### Reasoning Boundary

```java
// GOOD — returns evidence, lets the agent reason
List<Evidence> findDecisions(String topic); // returns raw evidence

// BAD — reasons on behalf of the agent
String summariseDecisions(String topic); // generates a summary — not Archivist's job
```

### Retrieval Strategy Encapsulation

```java
// GOOD — strategy is hidden behind an output port
public interface KnowledgeGateway {
    List<Evidence> retrieve(Query query);
}

// BAD — strategy selection is exposed to the application layer
public class RetrieveContextUseCase {
    public List<Evidence> execute(String q) {
        if (featureFlags.isHybridEnabled()) {
            return hybridSearcher.search(q); // infrastructure detail in application layer
        }
        return lexicalSearcher.search(q);
    }
}
```

---

## 17. What Archivist Deliberately Does Not Do

Understanding what Archivist will never do is as important as understanding what it does.

| Temptation                                                     | Why it is out of scope                                                     |
| -------------------------------------------------------------- | -------------------------------------------------------------------------- |
| Summarise retrieved content                                    | Summarisation is reasoning — belongs to the consuming agent                |
| Answer questions                                               | Answering is reasoning — belongs to the consuming agent                    |
| Interpret or infer meaning                                     | Interpretation is reasoning — belongs to the consuming agent               |
| Expose `grep` or `readFile`                                    | Violates Invariant 2 — leaks retrieval technology into the contract        |
| Expose `vectorSearch` or `bm25Search`                          | Violates Invariant 2 — leaks retrieval technology into the contract        |
| Couple to a specific Second Brain format                       | Archivist must remain independent of Second Brain's storage implementation |
| Depend on Spring Boot in domain tests                          | Violates Invariant 1 — domain must be framework-independent                |
| Change the public contract when switching retrieval strategies | Violates Invariant 8 — the public contract must be stable                  |
| Use `@Autowired` field injection in any class                  | Violates coding standards — use constructor injection                      |
| Skip the specification step to ship faster                     | Violates the development workflow — specs precede code                     |

---

_This document is the project constitution. It takes precedence over any verbal instruction, convenience, or implementation shortcut. When in doubt, return to the Architectural North Star._
