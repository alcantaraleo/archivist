# Archivist

Domain-driven **MCP server** (STDIO) that exposes stable retrieval capabilities over a personal knowledge corpus called **Second Brain**. Archivist returns **evidence** with provenance; your agent does the reasoning.

Archivist is built to read corpora that follow the **Second Brain** domain model — especially the public reference vault [alcantaraleo/second-brain-public](https://github.com/alcantaraleo/second-brain-public). Clone that repository (or use your own compatible tree) and point `ARCHIVIST_SECOND_BRAIN_PATH` at its root.

---

## Quick start

**Prerequisites:** Java 21+, Gradle wrapper (included), a directory to use as the corpus root.

```bash
git clone https://github.com/alcantaraleo/archivist.git
cd archivist
./gradlew :transport:bootJar
export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/your/corpus   # must exist
java -jar transport/build/libs/transport.jar               # MCP on stdout; logs on stderr
```

Try without a personal vault (checked-in fixture):

```bash
export ARCHIVIST_SECOND_BRAIN_PATH="$(pwd)/infrastructure/src/test/resources/fixture-corpus"
./gradlew :transport:bootRun
```

Or use the public reference Second Brain (same layout Archivist targets):

```bash
git clone https://github.com/alcantaraleo/second-brain-public.git /path/to/second-brain-public
export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/second-brain-public
./gradlew :transport:bootRun
```

Wire your agent to the jar — see [Agent MCP configuration](#agent-mcp-configuration).

---

## Table of contents

- [Overview](#overview)
- [MCP tools and responses](#mcp-tools-and-responses)
- [Corpus requirements](#corpus-requirements)
- [Getting started](#getting-started)
- [Configuration](#configuration)
- [Architecture](#architecture)
- [Development](#development)
- [Roadmap](#roadmap)
- [Documentation](#documentation)
- [License](#license)

---

## Overview

Archivist is a **retrieval layer** between LLM agents and a knowledge corpus. The public contract speaks in **domain terms** (`findDecisions`, `findPeople`, …). Storage, indexing, and ranking strategies stay **private** and can evolve (lexical today; hybrid and graph on the roadmap) without breaking MCP clients.

It sits between external LLM agents and a personal knowledge system, exposing domain-specific capabilities so agents retrieve knowledge without knowing how it is stored, indexed, or organised.

```
┌──────────────────────────────────────────────────────────────────┐
│                     Consuming agents (reason)                     │
│          Cursor · Claude · GPT · custom agents · CLI             │
└───────────────────────────────┬──────────────────────────────────┘
                                │
                     MCP / future REST · CLI
                     domain capabilities only
                     (evidence + provenance — not answers)
                                │
┌───────────────────────────────▼──────────────────────────────────┐
│                            Archivist                              │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │ Transport (MCP today) → Application → Domain contract       │  │
│  └──────────────────────────────┬─────────────────────────────┘  │
│                                 │ private                         │
│  ┌──────────────────────────────▼─────────────────────────────┐  │
│  │ Infrastructure: retrieval strategies · Second Brain adapter │  │
│  └────────────────────────────────────────────────────────────┘  │
└───────────────────────────────┬──────────────────────────────────┘
                                │ read-only corpus access
┌───────────────────────────────▼──────────────────────────────────┐
│              Second Brain (your knowledge corpus)                 │
│     Markdown entries · zones · types · provenance chain          │
│     storage layout and indexing are implementation details        │
└──────────────────────────────────────────────────────────────────┘
```

### What Archivist is

- An MCP adapter over eight stable **capabilities**
- Read-only access to Markdown knowledge entries with metadata
- Evidence + **provenance** (source id, type, zone, tags, source chain, timestamps)

### What Archivist is not

- Not a filesystem wrapper, Markdown full-text search UI, or RAG answer engine
- Not an agent, summariser, or reasoning engine
- Not a hosted service — it runs locally against **your** corpus path

**Boundary:** Archivist retrieves evidence. Consuming agents synthesise, interpret, and answer.

---

## MCP tools and responses

**Server identity:** `archivist` (MCP server name). Version is set in `transport/src/main/resources/application.properties` (Release Please keeps it in sync with GitHub releases).

**Transport:** MCP over **STDIO**. Startup confirmation and errors go to **stderr**; **stdout** is reserved for the MCP protocol.

### Tools

| Tool | Parameter | Purpose |
| ---- | --------- | ------- |
| `retrieveContext` | `query` | Contextual retrieval across all zones |
| `findDecisions` | `topic` | Architecture and product decisions (ADRs) |
| `findProjects` | `criteria` | Projects and initiatives |
| `findPeople` | `name` | People with professional context |
| `findConcepts` | `topic` | Concepts and cross-cutting insights |
| `findRelatedKnowledge` | `query` | Related knowledge (see [current behaviour](#roadmap) below) |
| `findReadings` | `topic` | Source materials (articles, transcripts, notes) |
| `findDebriefs` | `topic` | Incident retrospectives and learning reviews |

Canonical tool names and JSON Schemas: [`specs/002-mcp-transport-adapter/contracts/mcp-tools-expected.json`](specs/002-mcp-transport-adapter/contracts/mcp-tools-expected.json).

### Response shape

Each tool returns a JSON array of **evidence** objects:

```json
[
  {
    "content": "Retrieved body text when available.",
    "provenance": {
      "sourceId": "stable-entry-id",
      "title": "Human-readable title",
      "type": "CONCEPT",
      "zone": "SYNTHESIZED",
      "tags": ["tag-one"],
      "sources": ["upstream-source-id"],
      "created": "2026-01-01T12:00:00Z",
      "updated": "2026-01-02T12:00:00Z"
    }
  }
]
```

Example fixture: [`specs/002-mcp-transport-adapter/contracts/evidence-response-schema-expected.json`](specs/002-mcp-transport-adapter/contracts/evidence-response-schema-expected.json). Entries may omit body `content` when over size limits; provenance still describes the entry (`contentAvailability` in the domain model).

**Retrieval today:** all tools use the **lexical** strategy with a default cap of **20** results per call. Type- and zone-aware capabilities filter to the relevant `KnowledgeType` where applicable.

---

## Corpus requirements

Archivist does **not** ship a knowledge base. You point `ARCHIVIST_SECOND_BRAIN_PATH` at an **existing directory** on disk.

**Reference corpus:** [github.com/alcantaraleo/second-brain-public](https://github.com/alcantaraleo/second-brain-public) — public Second Brain vault (LLM-Wiki-style layout) that this project is intended to retrieve from. Use it to try Archivist end-to-end or as a template for your own corpus.

### Format (current implementation)

- **Markdown** files under the corpus root (read-only scan)
- **YAML frontmatter** for metadata (`type`, optional `zone`, tags, dates, `sources`, etc.)
- Entries without a recognised `type` (and no configured alias) are skipped

The domain model is defined in [`docs/second-brain-domain.md`](docs/second-brain-domain.md). The [second-brain-public](https://github.com/alcantaraleo/second-brain-public) repository is the primary public example of that model on disk. Many users keep a corpus in an **Obsidian vault** with the same folder layout; Archivist’s **public contract** does not depend on Obsidian — only the infrastructure adapter maps files to domain types and zones.

### Default zone mapping (path prefixes)

Relative to the corpus root, longest matching prefix wins (case-insensitive):

| Prefix | Domain zone |
| ------ | ----------- |
| `raw/` | `SOURCE` |
| `wiki/` | `SYNTHESIZED` |
| `dev/` | `TECHNICAL` |
| `identity/` | `IDENTITY` |
| `runtime/` | `COMPILED` |
| `observability/` | `SIGNAL` |

Frontmatter `zone` overrides path inference when valid. Details and type aliases (e.g. `meeting-person` → `PERSON`): [`specs/003-second-brain-integration/contracts/mapping-defaults.md`](specs/003-second-brain-integration/contracts/mapping-defaults.md).

Override mappings via Spring configuration (`archivist.second-brain.mapping.*`) — see [Configuration](#configuration).

### Privacy

The server reads only the path you configure. Keep MCP config and env vars off public repos if they contain personal paths. Archivist does not upload your corpus.

---

## Getting started

### Prerequisites

- Java 21+
- An **existing** directory for the corpus root

### Build

```bash
./gradlew build
```

MCP fat jar (stable path — version bumps do not rename the file):

```text
transport/build/libs/transport.jar
```

Build only the jar:

```bash
./gradlew :transport:bootJar
```

### Run (STDIO MCP server)

```bash
export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/existing/directory
./gradlew :transport:bootRun
```

Or run the jar directly (after `bootJar`):

```bash
export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/existing/directory
java -jar transport/build/libs/transport.jar
```

If the variable is missing, blank, or not an existing directory, the process **fails at startup** with a message naming `archivist.second-brain.path` and `ARCHIVIST_SECOND_BRAIN_PATH`.

### Agent MCP configuration

Point harnesses at the stable jar path. Rebuild after pulling changes.

```json
{
  "mcpServers": {
    "archivist": {
      "command": "java",
      "args": ["-jar", "/absolute/path/to/archivist/transport/build/libs/transport.jar"],
      "env": {
        "ARCHIVIST_SECOND_BRAIN_PATH": "/absolute/path/to/corpus"
      }
    }
  }
}
```

Works with Cursor, Claude Desktop, and other MCP STDIO clients — adjust the config file location for your tool.

---

## Configuration

Environment-specific values use **environment variables** and Spring Boot external configuration. Defaults live in [`transport/src/main/resources/application.properties`](transport/src/main/resources/application.properties).

| Variable | Required | Description |
| -------- | -------- | ----------- |
| `ARCHIVIST_SECOND_BRAIN_PATH` | **Yes** | Absolute path to an existing corpus root directory |
| `ARCHIVIST_RETRIEVAL_ACTIVE_STRATEGY` | No | Active retrieval strategy: `lexical` (default), `bm25`, or `embedding` |
| `ARCHIVIST_RETRIEVAL_MAX_RESULTS` | No | Max evidence items per capability call (default: `20`) |

Full embedding and BM25 property lists: [`specs/006-embedding-retrieval/contracts/embedding-configuration.md`](specs/006-embedding-retrieval/contracts/embedding-configuration.md) and [`specs/005-bm25-retrieval/contracts/bm25-configuration.md`](specs/005-bm25-retrieval/contracts/bm25-configuration.md). Validation walkthrough: [`specs/006-embedding-retrieval/quickstart.md`](specs/006-embedding-retrieval/quickstart.md).

### Embedding retrieval (optional)

Enable semantic ranking with **`ARCHIVIST_RETRIEVAL_ACTIVE_STRATEGY=embedding`**. Vector index uses in-memory store only in the current release (`ARCHIVIST_RETRIEVAL_EMBEDDING_STORE=memory`).

| Embedder id | What it is |
| ------------- | ---------- |
| `local` | ONNX model in the Archivist JVM (default when strategy is `embedding`; first run may download weights). Use with `dimensions=384` unless you override the model. |
| `openai-compatible` | HTTP **`POST {base-url}/v1/embeddings`** (OpenAI JSON shape). **Same adapter** for [OpenAI](https://platform.openai.com/) and for a **local proxy** (Text Embeddings Inference, Ollama OpenAI shim, etc.) — only `base-url`, `model`, `api-key`, and **`dimensions`** change. |
| `stub` | Deterministic offline vectors for tests; not for production. |

**OpenAI-compatible: two operator paths (one embedder id)**

| Target | `ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_BASE_URL` | Typical `…_OPENAI_MODEL` | Typical `ARCHIVIST_RETRIEVAL_EMBEDDING_DIMENSIONS` |
| ------ | ------------------------------------------------- | -------------------------- | -------------------------------------------------- |
| OpenAI API | `https://api.openai.com` | `text-embedding-3-small` | `1536` |
| Local OpenAI-shaped server | `http://localhost:<port>` (e.g. Compose example on `8080`) | Server’s model id (e.g. `sentence-transformers/all-MiniLM-L6-v2`) | Must match that model (often `384`) |

Spring Boot does **not** read `OPENAI_API_KEY` automatically. For MCP/`bootRun`, set **`ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_API_KEY`** (e.g. `export ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_API_KEY="$OPENAI_API_KEY"`).

Example — **OpenAI** (after `export ARCHIVIST_SECOND_BRAIN_PATH=…`):

```bash
export ARCHIVIST_RETRIEVAL_ACTIVE_STRATEGY=embedding
export ARCHIVIST_RETRIEVAL_EMBEDDING_EMBEDDER=openai-compatible
export ARCHIVIST_RETRIEVAL_EMBEDDING_STORE=memory
export ARCHIVIST_RETRIEVAL_EMBEDDING_DIMENSIONS=1536
export ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_BASE_URL=https://api.openai.com
export ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_API_KEY="$OPENAI_API_KEY"
export ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_MODEL=text-embedding-3-small
./gradlew :transport:bootRun
```

Example — **local OpenAI-compatible endpoint** (TEI or similar; see [`deploy/docker-compose.embedding.example.yml`](deploy/docker-compose.embedding.example.yml)):

```bash
export ARCHIVIST_RETRIEVAL_ACTIVE_STRATEGY=embedding
export ARCHIVIST_RETRIEVAL_EMBEDDING_EMBEDDER=openai-compatible
export ARCHIVIST_RETRIEVAL_EMBEDDING_STORE=memory
export ARCHIVIST_RETRIEVAL_EMBEDDING_DIMENSIONS=384
export ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_BASE_URL=http://localhost:8080
export ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_MODEL=sentence-transformers/all-MiniLM-L6-v2
# api-key often unset for local proxies
./gradlew :transport:bootRun
```

Example — **in-process local ONNX** (no HTTP embedding server):

```bash
export ARCHIVIST_RETRIEVAL_ACTIVE_STRATEGY=embedding
export ARCHIVIST_RETRIEVAL_EMBEDDING_EMBEDDER=local
export ARCHIVIST_RETRIEVAL_EMBEDDING_STORE=memory
export ARCHIVIST_RETRIEVAL_EMBEDDING_DIMENSIONS=384
./gradlew :transport:bootRun
```

Mis-matched **`dimensions`** (property vs vectors returned by the model) fail at startup or index build with an explicit error.

Additional corpus tuning (optional, Spring property names):

| Property | Default | Purpose |
| -------- | ------- | ------- |
| `archivist.second-brain.max-entry-bytes` | `1048576` | Skip loading oversized bodies; provenance retained |
| `archivist.second-brain.ignore-globs` | `**/templates/**`, `**/.trash/**` | Paths excluded from discovery |

Zone/type mapping: `archivist.second-brain.mapping.zone-prefixes`, `archivist.second-brain.mapping.type-aliases`. See [`SecondBrainMappingProperties`](infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/SecondBrainMappingProperties.java) for defaults.

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

**Dependency rule:** all arrows point inward. The domain knows nothing about Spring, MCP, Spring AI, or any storage technology. Infrastructure implements interfaces defined by the domain. Full invariants and layer rules: [`AGENTS.md`](AGENTS.md).

### Package layout

```
io.archivist
├── domain/model, domain/port/in, domain/port/out
├── application/usecase
├── infrastructure/retrieval, infrastructure/secondbrain
└── transport/mcp
```

### Technology stack

| Concern | Technology |
| ------- | ---------- |
| Language | Java 21+ |
| Framework | Spring Boot 4.1.0 |
| AI / MCP | Spring AI 2.0.0 (MCP server, STDIO) |
| Build | Gradle (Kotlin DSL) |
| Tests | JUnit 5, Mockito |

---

## Development

### Verify locally

```bash
./gradlew build
```

Runs domain, application, infrastructure, and transport tests (including MCP contract regression with fixture contracts).

### Specification-driven workflow

Significant capabilities start with a spec before implementation:

1. Propose — GitHub issue  
2. Specify — [`docs/specs/SPEC_TEMPLATE.md`](docs/specs/SPEC_TEMPLATE.md) or `specs/<NNN-feature>/`  
3. Review and approve spec  
4. Implement on feature branch `NNN-feature-slug`  
5. Merge via PR  

Contributors: read [`AGENTS.md`](AGENTS.md) and use [`.github/pull_request_template.md`](.github/pull_request_template.md). PR titles follow [Conventional Commits](https://www.conventionalcommits.org/) (enforced in CI).

---

## Roadmap

### Phase 1 — Foundation (largely complete)

- [x] Clean Architecture skeleton and domain model  
- [x] Eight domain capability interfaces and MCP tools  
- [x] Lexical retrieval strategy  
- [x] Second Brain integration (Markdown corpus, mapping, provenance)  

### Phase 2 — Retrieval evolution

- [x] BM25 retrieval — [`specs/005-bm25-retrieval/`](specs/005-bm25-retrieval/); [`ADR-0004`](docs/adr/ADR-0004-bm25-index-cache-invalidation.md)  
- [x] Embedding-based retrieval — [`specs/006-embedding-retrieval/`](specs/006-embedding-retrieval/) (quickstart, contracts); [`ADR-0005`](docs/adr/ADR-0005-pluggable-embedding-retrieval.md); Compose example [`deploy/docker-compose.embedding.example.yml`](deploy/docker-compose.embedding.example.yml)  
- [ ] Hybrid retrieval and re-ranking  

### Phase 3 — Advanced retrieval

- [ ] Knowledge graph traversal (`findRelatedKnowledge` today uses lexical retrieval over the full corpus, not graph walks)  
- [ ] Temporal retrieval, learned query planning, GraphRAG  

The **MCP tool names and signatures** should remain stable across these phases; behaviour and ranking improve behind the same contract.

---

## Documentation

| Document | Contents |
| -------- | -------- |
| [`AGENTS.md`](AGENTS.md) | Project constitution, invariants, capabilities |
| [`docs/second-brain-domain.md`](docs/second-brain-domain.md) | Knowledge zones, types, provenance |
| [`docs/adr/`](docs/adr/) | Architecture decision records |
| [`specs/`](specs/) | Feature specifications and contracts |
| [`docs/specs/SPEC_TEMPLATE.md`](docs/specs/SPEC_TEMPLATE.md) | Spec template for new work |

---

## License

No `LICENSE` file is published yet. Until one is added, standard copyright applies; copying or distributing the code beyond GitHub’s display terms may require permission from the copyright holder. If you intend to adopt this project commercially or redistribute it, open an issue to clarify licensing intent.
