# VectorStore SPI (Infrastructure-Internal)

**Feature**: 006-embedding-retrieval  
**Package**: `io.archivist.infrastructure.retrieval.embedding`  
**Scope**: NOT part of public domain contract.

---

## Interface (conceptual)

```text
VectorStore
  String name()
  void replaceAll(List<EmbeddedChunk> chunks)
  List<ScoredChunk> search(float[] queryVector, int topK)
  void clear()
```

Search returns cosine similarity scores, best-first, size ≤ `topK`.

---

## First adapters

| `name()` | Class | Status in 006 |
| -------- | ----- | ------------- |
| `memory` | `InMemoryVectorStore` | **Ships** (default store) |
| `pgvector` | — | **Not implemented** — reserved id for future spec |
| `chromadb` | — | **Not implemented** — reserved id for future spec |

Configuring `pgvector` / `chromadb` before an adapter exists MUST fail fast at startup.

---

## Extension checklist (future store)

1. Implement `VectorStore` in infrastructure
2. Register bean with stable config id
3. Document connection properties under `archivist.retrieval.embedding.<store>.*`
4. Add/uncomment Compose service in `deploy/docker-compose.embedding.example.yml`
5. ADR for sync/invalidation if it diverges from ADR-0004 / ADR-0005 in-memory policy
6. No MCP / `port.in` / application changes

---

## Compose placeholders

Commented services for pgvector and Chroma in the example Compose file are **ops hints only** — they do not register adapters.
