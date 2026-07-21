# Research: Embedding-Based Retrieval Strategy

**Feature**: 006-embedding-retrieval  
**Date**: 2026-07-20  
**Status**: Complete — all technical decisions resolved for plan

**Inputs**: [spec.md](spec.md) clarifications (2026-07-20), [ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md), Spring AI 2.0 EmbeddingModel / Transformers ONNX docs, specs 004–005 SPI patterns

---

## Decision 1: Private SPIs (not Spring AI types on the strategy)

**Decision**: Introduce two **package-private infrastructure SPIs** under `io.archivist.infrastructure.retrieval.embedding`:

```text
TextEmbedder
  String name()                    // config id: local | openai-compatible | stub
  int dimensions()
  float[] embed(String text)
  List<float[]> embedBatch(List<String> texts)   // may loop embed(); batch when provider supports

VectorStore
  String name()                    // config id: memory | (future pgvector, chromadb, …)
  void replaceAll(List<EmbeddedChunk> chunks)    // full rebuild for memory
  List<ScoredChunk> search(float[] queryVector, int topK)
  void clear()
```

`EmbeddingRetrievalStrategy` depends **only** on these SPIs + `KnowledgeCorpus` + chunker + properties — never on Spring AI `EmbeddingModel`, HTTP clients, or DB drivers directly.

**Rationale**: Spec requires interchangeability without MCP/`port.in` changes. Thin SPIs keep future pgvector/Chroma adapters from forcing Spring AI vector-store types into the strategy. Mirrors `RetrievalStrategy` registry pattern.

**Alternatives considered**:

- Use Spring AI `EmbeddingModel` + `VectorStore` as the strategy’s direct dependencies: Rejected — couples strategy to Spring AI vector-store API; harder to add non–Spring AI backends cleanly
- Domain-layer ports for embed/store: Rejected — Constitution V keeps retrieval tech in infrastructure; no new `port.out` for vectors

---

## Decision 2: Local embedder = Spring AI Transformers (ONNX)

**Decision**: Adapter id **`local`** wraps Spring AI **`TransformersEmbeddingModel`** (ONNX / DJL), default model **all-MiniLM-L6-v2** (384 dimensions). Dependencies scoped to **`infrastructure` only** (`spring-ai-transformers` / starter equivalent pinned via Spring AI BOM 2.0.0).

Config (illustrative → final names in contracts):

| Property | Default |
| -------- | ------- |
| `archivist.retrieval.embedding.local.model-resource` | Spring AI default ONNX URI (or explicit classpath/file/https) |
| `archivist.retrieval.embedding.local.tokenizer-resource` | Spring AI default tokenizer URI |
| `archivist.retrieval.embedding.local.cache-directory` | `${java.io.tmpdir}/archivist-onnx-model` |

First real embed may download/cache the model — document for operators. **CI MUST NOT** require download: tests use `stub` embedder (Decision 4).

**Rationale**: Spec default embedder is `local`. Spring AI is already a project technology constraint for infrastructure; TransformersEmbeddingModel is the supported in-process path. Avoids inventing a custom ONNX runner.

**Alternatives considered**:

- Raw ONNX Runtime without Spring AI: Rejected — more code; Spring AI already wraps DJL/ONNX
- “Local” = call Ollama OpenAI-compatible on localhost: Rejected — that is `openai-compatible` with a local base URL, not a distinct process-local model adapter
- Bundle multi-hundred-MB weights in git: Rejected — use cache download or operator-provided path

---

## Decision 3: OpenAI-compatible embedder = HTTP OpenAI embeddings shape

**Decision**: Adapter id **`openai-compatible`** calls `POST {base-url}/v1/embeddings` (OpenAI embeddings JSON shape) via Spring `RestClient` or Spring AI OpenAI embedding client configured with custom base URL. Credentials from env/config; **never logged**.

| Property | Purpose | Default |
| -------- | ------- | ------- |
| `archivist.retrieval.embedding.openai.base-url` | API root | none (required when embedder selected) |
| `archivist.retrieval.embedding.openai.api-key` | Bearer token | none (may be empty for some local proxies) |
| `archivist.retrieval.embedding.openai.model` | Model name | `text-embedding-3-small` (overridable) |
| `archivist.retrieval.embedding.openai.dimensions` | Optional request dim if API supports | unset → use response / `embedding.dimensions` |

**Rationale**: Spec requires OpenAI-compatible endpoint support for Compose examples and remote providers. HTTP shape is the interchange contract, not “OpenAI-only”.

**Alternatives considered**:

- Provider-specific SDKs only: Rejected — breaks compatible proxies
- gRPC-only local servers: Rejected — out of scope

---

## Decision 4: Stub embedder for CI (deterministic, offline)

**Decision**: Register embedder id **`stub`** for tests only (or always registered but never default). Produces **deterministic** unit vectors from a stable hash of normalised text into `archivist.retrieval.embedding.dimensions` (default **384**). Same text → same vector; different texts → generally different directions so paraphrase fixtures can still be crafted by controlling stub vocabulary if needed.

Production default remains **`local`**. Integration tests set `archivist.retrieval.embedding.embedder=stub`.

**Rationale**: AC-14 — embedding path must run in CI without network. ONNX download is unsuitable for default CI.

**Alternatives considered**:

- Check in ONNX weights: Rejected — repo bloat
- Skip embedding tests in CI: Rejected — violates AC-14
- Mock only at Mockito level without stub bean: Rejected — weaker registry/config coverage

---

## Decision 5: In-memory vector store = brute-force cosine

**Decision**: Adapter id **`memory`** holds `List<EmbeddedChunk>` in the JVM. Search: cosine similarity (normalize vectors on upsert and query), return top-K. Full **`replaceAll`** on rebuild. No external process.

Reuse **`CorpusFingerprint`** + TTL (**default `PT15M`**) pattern from ADR-0004 / BM25 cache: rebuild when fingerprint changes, TTL expires, or **embedder identity** changes (`embedder` id + model id/resource + dimensions).

**Rationale**: Personal Second Brain scale; YAGNI vs HNSW for v1. SPI allows pgvector/Chroma later without strategy changes.

**Alternatives considered**:

- Lucene `KnnFloatVectorField`: Viable but adds index complexity; defer unless brute-force smoke fails AC timing
- External DB in 006: Rejected by spec (SPI + memory only)

---

## Decision 6: Chunking policy

**Decision**:

1. Build searchable text from title, tags (space-joined), and body when `AVAILABLE`; title+tags only when size-limited (ADR-0003).
2. Split into chunks:
   - Prefer paragraph boundaries (`\n\n`)
   - Then hard-wrap at **`chunk-size-chars` default 1200** with **`chunk-overlap-chars` default 150**
   - Empty body after size-limit → single chunk of title/tags prefix only
3. Each chunk text is prefixed with a stable header:
   ```text
   Title: {title}
   Tags: {tag1 tag2 …}

   {chunk body slice}
   ```
4. Each `EmbeddedChunk` carries: `sourceId`, `chunkIndex`, `text`, `vector`, plus filter metadata `knowledgeType`, `knowledgeZone`.
5. After search + type/zone filters: **dedupe by `sourceId`**, keep **best cosine score** per entry, then sort descending, apply `maxResults`.
6. Load `Evidence` via `KnowledgeCorpus.loadBySourceId` (entry-level content/provenance — not chunk text as the public content unless plan later opts to surface snippet; **default: full entry content as today**).

**Rationale**: Spec chose chunks for long-note recall; public contract stays `List<Evidence>` per entry.

**Alternatives considered**:

- Token-based chunking with tokenizer: Deferred — char-based is simpler and good enough for MiniLM context
- Return chunk text as Evidence.content: Rejected for v1 — breaks agent expectation of full note; can revisit

---

## Decision 7: Configuration defaults

| Property | Default |
| -------- | ------- |
| `archivist.retrieval.active-strategy` | `lexical` (unchanged) |
| `archivist.retrieval.embedding.store` | `memory` |
| `archivist.retrieval.embedding.embedder` | `local` |
| `archivist.retrieval.embedding.dimensions` | `384` |
| `archivist.retrieval.embedding.top-k` | `50` |
| `archivist.retrieval.embedding.min-score` | disabled (`null` / empty) |
| `archivist.retrieval.embedding.index-ttl` | `PT15M` |
| `archivist.retrieval.embedding.chunk-size-chars` | `1200` |
| `archivist.retrieval.embedding.chunk-overlap-chars` | `150` |

Unknown `store` / `embedder` → startup failure listing registered ids. Dimension mismatch between embedder and config → fail fast at startup or first build.

---

## Decision 8: Similarity metric

**Decision**: **Cosine similarity** on L2-normalised vectors. Higher is better. Optional `min-score` filters after scoring when set.

**Rationale**: Spec lean; works for MiniLM and typical OpenAI embedding spaces when normalised.

---

## Decision 9: ADR and documentation set

**Decision**:

1. Write **`docs/adr/ADR-0005-pluggable-embedding-retrieval.md`** covering:
   - Private embedder + vector-store SPIs
   - First adapters (`local`, `openai-compatible`, `memory`; `stub` for tests)
   - Chunking + `sourceId` dedupe
   - Why not domain ports / why not Spring AI VectorStore as the public infra surface
2. Operator docs (implementation tasks):
   - Update [README.md](../../README.md) Phase 2 roadmap (embedding item + pointer)
   - Feature [quickstart.md](quickstart.md) (validation)
   - [contracts/README.md](contracts/README.md) + configuration contract
   - Compose template README section / comments in YAML
3. Compose file: **`deploy/docker-compose.embedding.example.yml`**
   - Example OpenAI-compatible embeddings service (e.g. commented `tei` / ollama / openai-proxy sample — plan leaves exact image to implementer with a working default in quickstart)
   - **Commented** `pgvector` and `chromadb` service stubs labelled “future adapter — not wired in Archivist 006”

**Rationale**: Spec AC-19 / AC-20; single ADR keeps related decisions together (split only if ADR grows unwieldy during write-up).

---

## Decision 10: Ranking differentiation fixture (AC-11)

**Decision**: Add fixture entries where **paraphrase** of concept A matches embedding better than a lexical/BM25 term collision on concept B. JSON scenario lists expected `sourceId` order for `embedding`+`stub` (craft stub so paraphrase hash neighbourhood is controlled) **or** use a tiny fixed embedding table in the fixture test embedder.

Prefer a **test-only `FixtureEmbeddingTable`** implementing `TextEmbedder` (`name()` = `stub` with injectable map) for AC-11 determinism: map canonical phrases → hand-picked vectors so synonym query ranks target first while lexical/BM25 rank differently.

**Rationale**: Real MiniLM in CI is heavy and non-deterministic across versions; controlled vectors prove the ranking path.

---

## Decision 11: Package layout

```text
infrastructure/.../retrieval/
├── EmbeddingRetrievalStrategy.java
├── embedding/
│   ├── TextEmbedder.java
│   ├── VectorStore.java
│   ├── EmbeddedChunk.java
│   ├── ScoredChunk.java
│   ├── EntryChunker.java
│   ├── EmbeddingIndexCache.java
│   ├── EmbeddingProperties.java
│   ├── TextEmbedderRegistry.java
│   ├── VectorStoreRegistry.java
│   ├── LocalTextEmbedder.java
│   ├── OpenAiCompatibleTextEmbedder.java
│   ├── StubTextEmbedder.java
│   └── InMemoryVectorStore.java
```

Register strategy + adapters in `RetrievalConfiguration`. Extend `RetrievalProperties` with nested `embedding`.

---

## Decision 12: Performance smoke

**Decision**: With `stub` embedder + fixture corpus, index rebuild + query **< 5 s** on CI (same spirit as BM25 AC-14). Real `local` ONNX timing is operator-dependent — document, do not gate CI on first-download latency.

---

## Resolved open items from spec §11

| Topic | Resolution |
| ----- | ---------- |
| Default embedder | `local` |
| Embedding unit | Chunks + best-score `sourceId` dedupe |
| Compose scope | Embeddings example + placeholder vector DBs |
| Similarity | Cosine |
| min-score | Disabled by default |
| Chunk params | 1200 / 150 chars |
| SPI shapes | Decision 1 |
| Local model | Spring AI Transformers ONNX MiniLM |
| ADR | ADR-0005 (single) |

---

## References

- [Spring AI Transformers (ONNX) Embeddings](https://docs.spring.io/spring-ai/reference/api/embeddings/onnx.html)
- [ADR-0004](../../docs/adr/ADR-0004-bm25-index-cache-invalidation.md)
- [004 retrieval-strategy-spi](../004-lexical-retrieval/contracts/retrieval-strategy-spi.md)
