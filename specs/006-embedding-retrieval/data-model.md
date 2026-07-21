# Data Model: Embedding-Based Retrieval Strategy

**Feature**: 006-embedding-retrieval  
**Date**: 2026-07-20

> Adds infrastructure-only embedding retrieval, chunking, and pluggable adapters. **No** changes to `domain.model` types, `port.in`, or `KnowledgeGateway` / `KnowledgeCorpus` signatures.

---

## Domain Layer (unchanged)

| Type | Change |
| ---- | ------ |
| `Evidence`, `Provenance`, `Query`, `KnowledgeType`, `KnowledgeZone`, `ContentAvailability` | Unchanged |
| `KnowledgeGateway` | Unchanged signature; embedding affects ranking when `active-strategy=embedding` |
| `KnowledgeCorpus` | Unchanged — uses `catalog()`, `loadAll()`, `loadBySourceId()` |
| Eight `port.in` interfaces | Unchanged |

See [spec 004 data-model](../004-lexical-retrieval/data-model.md) for `Query` factories and gateway semantics.

---

## Application Layer (unchanged)

Use cases continue to translate capability parameters → `Query` → `KnowledgeGateway.retrieve`. No embedding types or configuration in application source.

---

## Infrastructure Layer: Configuration

### Extended: `RetrievalProperties`

Add nested `embedding` (`archivist.retrieval.embedding.*`):

| Field | Type | Default | Validation |
| ----- | ---- | ------- | ---------- |
| `store` | `String` | `memory` | non-blank; must match registered store |
| `embedder` | `String` | `local` | non-blank; must match registered embedder |
| `dimensions` | `int` | `384` | `> 0` |
| `topK` | `int` | `50` | `>= 1` |
| `minScore` | `Float` (nullable) | `null` (disabled) | if set, finite |
| `indexTtl` | `Duration` | `PT15M` | not negative |
| `chunkSizeChars` | `int` | `1200` | `>= 100` |
| `chunkOverlapChars` | `int` | `150` | `>= 0`, `< chunkSizeChars` |
| `local` | nested | see below | |
| `openai` | nested | see below | |

**Local nested**:

| Field | Default |
| ----- | ------- |
| `modelResource` | Spring AI MiniLM ONNX default (overridable) |
| `tokenizerResource` | Spring AI default tokenizer |
| `cacheDirectory` | `${java.io.tmpdir}/archivist-onnx-model` |

**OpenAI nested**:

| Field | Default |
| ----- | ------- |
| `baseUrl` | required when embedder=`openai-compatible` |
| `apiKey` | optional string / env binding |
| `model` | `text-embedding-3-small` |

Existing fields unchanged: `activeStrategy` (default `lexical`), `maxResults` (default `20`), `bm25` nest.

---

## Infrastructure Layer: Core Types

### `EmbeddedChunk` (package-private record)

| Field | Type | Description |
| ----- | ---- | ----------- |
| `sourceId` | `String` | Knowledge entry id |
| `chunkIndex` | `int` | 0-based index within entry |
| `text` | `String` | Prefixed chunk text that was embedded |
| `vector` | `float[]` | Embedding (length = dimensions) |
| `knowledgeType` | `KnowledgeType` | Filter metadata |
| `knowledgeZone` | `KnowledgeZone` | Filter metadata |

### `ScoredChunk` (package-private record)

| Field | Type | Description |
| ----- | ---- | ----------- |
| `chunk` | `EmbeddedChunk` | Hit |
| `score` | `float` | Cosine similarity |

---

## Infrastructure Layer: SPIs

### `TextEmbedder`

| Method | Contract |
| ------ | -------- |
| `String name()` | Config id |
| `int dimensions()` | Vector length |
| `float[] embed(String text)` | Non-null vector; empty/blank text → implementation may return zero vector or throw — strategy never embeds blank queries (gateway empty-list first) |
| `List<float[]> embedBatch(List<String> texts)` | Same length as input; default may loop |

### `VectorStore`

| Method | Contract |
| ------ | -------- |
| `String name()` | Config id |
| `void replaceAll(List<EmbeddedChunk> chunks)` | Atomic replace of searchable set |
| `List<ScoredChunk> search(float[] queryVector, int topK)` | Best-first; size ≤ topK |
| `void clear()` | Empty store |

---

## Infrastructure Layer: Components

### `EntryChunker`

| Input | Output |
| ----- | ------ |
| `Evidence` (or catalog entry + loaded evidence) | `List` of chunk texts with `chunkIndex` |

Rules: [research.md](research.md) Decision 6 (paragraph → char wrap; title/tags prefix; ADR-0003 body omission).

### `EmbeddingIndexCache`

| State | Description |
| ----- | ----------- |
| `fingerprintAtBuild` | Last corpus fingerprint |
| `embedderIdentity` | Embedder name + model identity + dimensions |
| `builtAt` | Last rebuild instant |
| `store` | Active `VectorStore` populated with chunks |

Rebuild when fingerprint changes **or** TTL expires **or** embedder identity changes.

### `TextEmbedderRegistry` / `VectorStoreRegistry`

Mirror `RetrievalStrategyRegistry`: index by `name()`, duplicate → startup failure, unknown active id → startup failure with registered names listed.

### `EmbeddingRetrievalStrategy`

```text
name() → "embedding"

retrieve(Query):
  blank text → empty list
  ensure index (cache)
  queryVector = embedder.embed(query.text())
  candidates = store.search(queryVector, topK)
  filter by Query.types() / zones()
  apply minScore if set
  dedupe by sourceId (best score)
  sort by score desc
  limit maxResults
  map sourceId → Evidence via corpus
```

### Adapters (first release)

| Id | Class | Role |
| -- | ----- | ---- |
| `local` | `LocalTextEmbedder` | Spring AI Transformers ONNX |
| `openai-compatible` | `OpenAiCompatibleTextEmbedder` | HTTP OpenAI embeddings shape |
| `stub` | `StubTextEmbedder` | Deterministic CI / tests |
| `memory` | `InMemoryVectorStore` | Brute-force cosine |

---

## Registry / wiring

| Bean | Change |
| ---- | ------ |
| `embeddingRetrievalStrategy` | **New** — `RetrievalStrategy` name `embedding` |
| `lexicalRetrievalStrategy` / `bm25RetrievalStrategy` | Unchanged |
| Embedder / store beans | Collected into registries; active pair selected from `EmbeddingProperties` |
| `RetrievalStrategyRegistry` | Collects additional `embedding` strategy |

---

## Flow

```text
port.in use case
  → KnowledgeGateway.retrieve(Query)
      → RetrievalStrategyRegistry.getActive()
          → EmbeddingRetrievalStrategy
              → EmbeddingIndexCache (CorpusFingerprint + TTL + embedder identity)
                  → EntryChunker + TextEmbedder + VectorStore.replaceAll
              → TextEmbedder.embed(query)
              → VectorStore.search
              → filter / dedupe / load Evidence
```

---

## Out of model (this feature)

- Domain vector types
- Persistent vector DB schemas (pgvector/Chroma) — future adapter specs
- MCP tool parameters for embedder/store
