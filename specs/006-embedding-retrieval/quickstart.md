# Quickstart Validation Guide: Embedding-Based Retrieval Strategy

**Feature**: 006-embedding-retrieval  
**Date**: 2026-07-20

Runnable checks after implementation. Run from repository root unless noted.

---

## Prerequisites

- Java 21
- Specs 003–005 merged — fixture corpus at `infrastructure/src/test/resources/fixture-corpus/`
- Spec **Approved** (2026-07-20)
- Optional manual vault: `export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/second-brain`

CI uses fixture corpus + **`stub`** embedder only (no ONNX download, no network).

---

## 1. Full build and regression

```bash
./gradlew build
```

**Expected**: `BUILD SUCCESSFUL`.

| Maps to | Check |
| ------- | ----- |
| AC-1 | Full build; spec 002 MCP contract tests pass unchanged |
| AC-2 | No transport imports of infrastructure |
| AC-3 | Default `lexical` — existing lexical/BM25 tests unchanged |

---

## 2. Embedding infrastructure tests (stub)

```bash
./gradlew :infrastructure:test --tests "*Embedding*" --tests "*EntryChunker*" --tests "*InMemoryVectorStore*" --tests "*StubTextEmbedder*"
```

**Expected**: Pass, including:

| Test area | AC |
| --------- | -- |
| Basic retrieve + provenance | AC-5 |
| Type filters | AC-6–AC-8 |
| Dedupe + max results | AC-9–AC-10 |
| Ranking vs lexical/BM25 | AC-11 — [fixture-embedding-ranking-scenario.json](contracts/fixture-embedding-ranking-scenario.json) |
| Size-limited policy | AC-12–AC-13 |
| Offline / stub | AC-14 |
| SPI isolation | AC-15 |
| Unknown adapter fail-fast | AC-16 |
| Chunk → single Evidence | AC-22 |

---

## 3. Strategy + adapter registration

```bash
./gradlew :infrastructure:test --tests "*RetrievalStrategyRegistry*" --tests "*TextEmbedderRegistry*" --tests "*VectorStoreRegistry*"
```

**Expected**: `embedding` registered; unknown store/embedder fails fast (AC-4, AC-16).

---

## 4. Documentation and ADR (AC-19, AC-20)

Manual checklist:

- [ ] `docs/adr/ADR-0005-pluggable-embedding-retrieval.md` exists and records SPI + chunking/dedupe
- [ ] `deploy/docker-compose.embedding.example.yml` has embeddings example + commented pgvector/Chroma placeholders
- [ ] README Phase 2 mentions embedding strategy and points to docs
- [ ] This quickstart + [contracts/README.md](contracts/README.md) describe defaults (`local` + `memory`), chunking, and Compose scope

---

## 5. Optional: in-process local ONNX (`embedder=local`)

Not the same as a **local OpenAI-compatible HTTP server** — use `embedder=local` only when the ONNX model runs inside the Archivist JVM.

```properties
archivist.retrieval.active-strategy=embedding
archivist.retrieval.embedding.embedder=local
archivist.retrieval.embedding.store=memory
archivist.retrieval.embedding.dimensions=384
ARCHIVIST_SECOND_BRAIN_PATH=/path/to/vault
```

Start MCP as documented in root README. First embed may download ONNX weights into the configured cache directory.

**Expected**: `retrieveContext` returns provenance-bearing evidence for a natural-language query.

---

## 6. Optional: OpenAI-compatible HTTP embedder (`embedder=openai-compatible`)

**One adapter** for both **OpenAI’s API** and **any local server** that implements `POST {base-url}/v1/embeddings` with OpenAI’s JSON shape. Set `embedder=openai-compatible` and point `archivist.retrieval.embedding.openai.base-url` at either `https://api.openai.com` or `http://localhost:<port>`.

| Target | `openai.base-url` | Typical `openai.model` | Set `embedding.dimensions` to |
| ------ | ------------------- | ---------------------- | ------------------------------ |
| OpenAI | `https://api.openai.com` | `text-embedding-3-small` | `1536` |
| Local proxy (e.g. TEI) | `http://localhost:8080` | Model id the server expects | Output size of that model (often `384` for MiniLM) |

Shared properties:

```properties
archivist.retrieval.active-strategy=embedding
archivist.retrieval.embedding.embedder=openai-compatible
archivist.retrieval.embedding.store=memory
archivist.retrieval.embedding.openai.api-key=<if-needed>
```

For MCP/`bootRun`, use env vars (see root README **Embedding retrieval**): `ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_*` and `ARCHIVIST_RETRIEVAL_EMBEDDING_DIMENSIONS`. Spring does **not** bind `OPENAI_API_KEY` unless you copy it to `ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_API_KEY`.

### 6a. Local proxy via Compose (example)

```bash
# Review and adapt — on Apple Silicon you may need platform: linux/amd64 for the image
# deploy/docker-compose.embedding.example.yml
docker compose -f deploy/docker-compose.embedding.example.yml up -d
```

Then aim Archivist at the published URL (example service maps host `8080`):

```properties
archivist.retrieval.embedding.openai.base-url=http://localhost:8080
archivist.retrieval.embedding.openai.model=sentence-transformers/all-MiniLM-L6-v2
archivist.retrieval.embedding.dimensions=384
```

Vector DB services in the Compose file remain **placeholders** until a future store adapter ships — do not expect Archivist to connect to them in 006.

### 6b. OpenAI API (no Compose)

```properties
archivist.retrieval.embedding.openai.base-url=https://api.openai.com
archivist.retrieval.embedding.openai.model=text-embedding-3-small
archivist.retrieval.embedding.dimensions=1536
archivist.retrieval.embedding.openai.api-key=<from-env>
```

Automated smoke (requires `OPENAI_API_KEY`; maps key inside the test only):

```bash
./gradlew :infrastructure:test --tests 'io.archivist.infrastructure.retrieval.embedding.OpenAiEmbeddingLiveSmokeTest'
```

---

## 7. Isolation sanity

```bash
./gradlew :transport:test
rg -n "io\\.archivist\\.infrastructure" transport/src/main/java && exit 1 || true
```

**Expected**: transport tests pass; no infrastructure imports in transport main sources (AC-2, AC-21).

---

## Mapping to acceptance criteria

| AC | Where validated |
| -- | --------------- |
| AC-1–AC-4 | §§1, 3 |
| AC-5–AC-16, AC-22 | §2 |
| AC-17–AC-20 | §4 (+ optional §§5–6) |
| AC-21 | §7 |
