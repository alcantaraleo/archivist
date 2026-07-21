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

## 5. Optional: local ONNX smoke (not CI-gated)

```properties
archivist.retrieval.active-strategy=embedding
archivist.retrieval.embedding.embedder=local
archivist.retrieval.embedding.store=memory
ARCHIVIST_SECOND_BRAIN_PATH=/path/to/vault
```

Start MCP as documented in root README. First embed may download ONNX weights into the configured cache directory.

**Expected**: `retrieveContext` returns provenance-bearing evidence for a natural-language query.

---

## 6. Optional: OpenAI-compatible via Compose template

```bash
# Review and adapt:
# deploy/docker-compose.embedding.example.yml
docker compose -f deploy/docker-compose.embedding.example.yml up -d
```

Point Archivist at the published base URL:

```properties
archivist.retrieval.active-strategy=embedding
archivist.retrieval.embedding.embedder=openai-compatible
archivist.retrieval.embedding.openai.base-url=http://localhost:<port>
archivist.retrieval.embedding.openai.api-key=<if-needed>
archivist.retrieval.embedding.openai.model=<model-id>
```

Vector DB services in the Compose file remain **placeholders** until a future store adapter ships — do not expect Archivist to connect to them in 006.

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
