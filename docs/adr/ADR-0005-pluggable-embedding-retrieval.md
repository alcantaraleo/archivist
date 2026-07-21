# ADR-0005: Pluggable Embedding Retrieval (Embedder + Vector Store SPIs)

**Date:** 2026-07-20  
**Status:** Accepted  
**Author:** Leonardo Alcantara

---

## Context

Spec `006-embedding-retrieval` adds an opt-in **`embedding`** `RetrievalStrategy` so agents can retrieve Second Brain evidence by semantic similarity when wording differs from the corpus. Specs 004–005 already hide lexical and BM25 behind a private strategy registry; embedding must follow the same pattern.

Operators need interchangeable **embedding models** (process-local ONNX vs OpenAI-compatible HTTP) and interchangeable **vector backends** (in-memory first; pgvector/Chroma later) **without** changing `port.in`, MCP tools, or application use cases (Constitution II, V, VIII).

Long notes need **chunked** embedding for recall, but the public contract remains entry-level `List<Evidence>` with provenance — not a chunk API.

---

## Decision

1. **Private infrastructure SPIs** under `io.archivist.infrastructure.retrieval.embedding`:
   - `TextEmbedder` — `name()`, `dimensions()`, `embed` / `embedBatch`
   - `VectorStore` — `name()`, `replaceAll`, `search` (cosine), `clear`
2. **`EmbeddingRetrievalStrategy`** depends only on those SPIs (+ `KnowledgeCorpus`, chunker, properties) — never on Spring AI `EmbeddingModel`, HTTP clients, or DB drivers directly.
3. **First adapters**:
   - Embedders: `local` (Spring AI Transformers ONNX MiniLM, default when strategy=`embedding`), `openai-compatible` (HTTP `/v1/embeddings`), `stub` (deterministic CI)
   - Store: `memory` (brute-force cosine; default)
4. **Chunking**: title/tags prefix + body (or title/tags only when size-limited per ADR-0003); paragraph then hard-wrap (default 1200/150 chars). After search + filters, **dedupe by `sourceId` keeping best cosine score**.
5. **Index cache**: reuse `CorpusFingerprint` + TTL (ADR-0004 spirit); also rebuild when embedder identity (id / model / dimensions) changes.
6. **Configuration-only** selection via `archivist.retrieval.active-strategy` and `archivist.retrieval.embedding.*`. Unknown embedder/store ids fail fast at startup listing registered names.
7. **Compose template** (`deploy/docker-compose.embedding.example.yml`) documents an OpenAI-compatible embeddings example and **commented** future vector DB placeholders — not shipping adapters in 006.
8. **Do not** introduce domain `port.out` for vectors or expose `vectorSearch` / `embed` on MCP.

---

## Options Considered

### Option A: Spring AI `EmbeddingModel` + `VectorStore` as strategy dependencies

**Rejected** — couples the strategy to Spring AI vector-store APIs; harder to add non–Spring AI backends cleanly.

### Option B: Domain ports for embed/store

**Rejected** — Constitution V keeps retrieval technology in infrastructure; no new domain vector types.

### Option C: Whole-entry embeddings only

**Rejected** — spec chose chunks for long-note recall; entry-level Evidence + `sourceId` dedupe preserves the public contract.

---

## Consequences

- CI must use `embedder=stub` (no ONNX download / no network).
- Future `pgvector` / `chromadb` adapters implement `VectorStore` + config id only.
- Hybrid fusion is specified in [ADR-0006](./ADR-0006-hybrid-consensus-retrieval.md) and [spec 007](../../specs/007-hybrid-retrieval/spec.md).

---

## References

- [spec 006](../../specs/006-embedding-retrieval/spec.md)
- [research.md](../../specs/006-embedding-retrieval/research.md)
- [ADR-0003](./ADR-0003-flag-size-limited-corpus-entries.md)
- [ADR-0004](./ADR-0004-bm25-index-cache-invalidation.md)
