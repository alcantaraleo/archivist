# Archivist

A domain-driven MCP server that provides stable, structured access to a personal knowledge system called Second Brain.

---

## Overview

Archivist is a retrieval layer.

It sits between external LLM agents and a personal knowledge system, exposing domain-specific capabilities that allow agents to retrieve knowledge without knowing how that knowledge is stored, indexed, or organised.

The public interface speaks in domain terms.
The retrieval implementation is entirely private.

---

## Motivation

LLM agents frequently need access to personal context — past decisions, active projects, known people, accumulated knowledge. The naive approach is to expose raw storage primitives: file reads, text searches, vector queries.

This creates tight coupling. When the storage or indexing mechanism changes, every consuming agent breaks. Retrieval technology leaks into the contract.

Archivist solves this by publishing a stable domain contract that hides retrieval implementation entirely. The contract defines _what_ is retrieved. The implementation decides _how_.

The retrieval engine is free to evolve — from lexical search to hybrid retrieval to knowledge graph traversal — without changing the public interface that consumers depend on.

---

## Purpose

Archivist exposes domain capabilities, not retrieval primitives.

**Public capabilities — domain concepts:**

```
retrieveContext(query)        → contextually relevant knowledge across all zones
findDecisions(topic)          → architecture and product decisions (ADRs)
findProjects(criteria)        → active or past projects and initiatives
findPeople(name)              → known individuals with professional context
findConcepts(topic)           → technical and professional concepts and insights
findRelatedKnowledge(query)   → semantically connected knowledge via graph traversal
findReadings(topic)           → source materials: articles, transcripts, book notes
findDebriefs(topic)           → incident retrospectives and learning reviews
```

**Capabilities that will never be exposed:**

```
grep()
readFile()
vectorSearch()
bm25Search()
graphSearch()
```

Consumers interact with knowledge concepts.
They never see retrieval technology.

---

## What Archivist Is Not

- Not a filesystem wrapper
- Not a Markdown search tool
- Not a RAG pipeline
- Not an AI agent
- Not a summariser
- Not a reasoning engine

---

## Separation of Responsibilities

**Archivist is responsible for:**

- query planning
- retrieval strategy selection
- evidence retrieval
- ranking
- deduplication
- context expansion
- provenance tracking

**Consuming agents are responsible for:**

- reasoning
- synthesis
- interpretation
- writing
- answering questions

Archivist retrieves evidence. Consuming agents reason about it.
This boundary is absolute.

---

## Architecture

```
┌───────────────────────────────────────────────────────────────────┐
│                        External Consumers                         │
│               (LLM Agents, Claude, GPT, CLI, Tests)              │
└──────────────────────────────┬────────────────────────────────────┘
                               │
               ┌───────────────▼───────────────┐
               │        Transport Layer         │
               │   MCP Server  │  REST  │  CLI  │
               └───────────────┬───────────────┘
                               │  domain capabilities only
               ┌───────────────▼───────────────┐
               │       Application Layer        │
               │    Use Cases / Interactors     │
               └──────┬────────────────┬────────┘
                      │                │
          ┌───────────▼──────┐  ┌──────▼──────────────────┐
          │   Domain Layer   │  │   Infrastructure Layer   │
          │                  │  │                          │
          │  · Entities      │  │  · Retrieval strategies  │
          │  · Ports In      │  │  · Second Brain adapters │
          │  · Ports Out     │  │  · Embedding providers   │
          │  · Domain rules  │  │  · Vector stores         │
          └──────────────────┘  └──────────────────────────┘
```

**Dependency rule:** all arrows point inward.

The domain knows nothing about Spring, MCP, Spring AI, or any storage technology. Infrastructure implements interfaces defined by the domain. The domain does not reference infrastructure.

---

## Architectural Invariants

These rules are non-negotiable. Every implementation must preserve them.

| #   | Invariant                                | Rule                                                                                      |
| --- | ---------------------------------------- | ----------------------------------------------------------------------------------------- |
| 1   | **Clean Architecture**                   | The domain must be executable and testable without Spring Boot, Spring AI, or the MCP SDK |
| 2   | **Domain before implementation**         | Public capabilities represent domain concepts, never retrieval technologies               |
| 3   | **Retrieval, never reasoning**           | Archivist returns evidence; consuming agents reason                                       |
| 4   | **Retrieval strategies are replaceable** | The retrieval implementation is private and may change without notice                     |
| 5   | **MCP is an adapter**                    | MCP is one transport; the domain must be reusable from CLI, REST, and tests               |
| 6   | **Frameworks are plugins**               | Spring Boot, Spring AI, MCP SDK, vector stores are implementation details                 |
| 7   | **Evidence must remain traceable**       | All retrieved content preserves provenance (source, location, timestamp)                  |
| 8   | **Stable public contract**               | The domain capability interface must remain stable as retrieval evolves                   |

---

## Technology Stack

| Concern                    | Technology                   |
| -------------------------- | ---------------------------- |
| Language                   | Java 21+                     |
| Framework                  | Spring Boot 4.1.0            |
| AI / Retrieval integration | Spring AI 2.0.0              |
| Build system               | Gradle (Kotlin DSL)          |
| MCP transport              | Spring AI MCP Server (STDIO) |
| Testing                    | JUnit 5, Mockito             |

---

## Package Organisation

```
io.archivist
├── domain
│   ├── model               # Evidence, Provenance, Query, KnowledgeType, KnowledgeZone
│   ├── port
│   │   ├── in              # Domain capability interfaces (input ports)
│   │   └── out             # Retrieval gateway interfaces (output ports)
│   └── service             # Domain services (pure logic, no framework dependencies)
├── application
│   └── usecase             # Use case interactors implementing input ports
├── infrastructure
│   ├── retrieval           # Retrieval strategy implementations (lexical, BM25, hybrid…)
│   └── secondbrain         # Second Brain adapters (decoupled from Obsidian internals)
└── transport
    ├── mcp                 # MCP server adapter
    └── rest                # REST adapter (future)
```

---

## Development Workflow

Archivist follows **Specification-Driven Development**.

Every significant capability begins with a specification before any implementation.

```
1. Propose    → Open a GitHub issue describing the capability and motivation
2. Specify    → Write a spec in docs/specs/ defining contract and acceptance criteria
3. Review     → Specification is reviewed and approved
4. Implement  → Implementation strictly follows the approved specification
5. Verify     → Implementation is validated against all acceptance criteria
6. Merge      → Code is merged only when all criteria are met
```

Code never becomes the source of architectural truth.
Specifications are the primary design artifact.

See `docs/specs/SPEC_TEMPLATE.md` for the specification format.

---

## Getting Started

### Prerequisites

- Java 21+
- An existing directory for your Second Brain knowledge root

### Build

```bash
./gradlew build
```

### Environment Variables

| Variable                      | Description                                                  | Example                 | Required |
| ----------------------------- | ------------------------------------------------------------ | ----------------------- | -------- |
| `ARCHIVIST_SECOND_BRAIN_PATH` | Absolute path to an **existing** Second Brain root directory | `/path/to/second-brain` | Yes      |

All environment-specific values are supplied via environment variables. They are bound in `transport/src/main/resources/application.properties` using `${ENV_VAR_NAME}` placeholder syntax — no literals in source or properties files.

### Run (STDIO MCP server)

```bash
export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/existing/directory
./gradlew :transport:bootRun
```

Startup confirmation is written to **stderr**; stdout is reserved for the MCP STDIO protocol.

---

## Roadmap

### Phase 1 — Foundation

- [x] Clean Architecture skeleton with domain model
- [x] Domain capability interfaces (input ports)
- [ ] Lexical retrieval strategy
- [x] MCP transport adapter
- [ ] Second Brain integration (initial)

### Phase 2 — Retrieval Evolution

- [ ] BM25 retrieval strategy
- [ ] Embedding-based retrieval
- [ ] Hybrid retrieval (BM25 + embeddings)
- [ ] Re-ranking

### Phase 3 — Advanced Retrieval

- [ ] Knowledge graph traversal
- [ ] Temporal retrieval
- [ ] Learned query planning
- [ ] GraphRAG

The public MCP contract must not change across these phases.

---

## License

TBD
