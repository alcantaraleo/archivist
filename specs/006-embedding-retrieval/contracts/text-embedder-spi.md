# TextEmbedder SPI (Infrastructure-Internal)

**Feature**: 006-embedding-retrieval  
**Package**: `io.archivist.infrastructure.retrieval.embedding`  
**Scope**: NOT part of public domain contract. MCP / transport / application MUST NOT reference this SPI.

---

## Interface (conceptual)

```text
TextEmbedder
  String name()
  int dimensions()
  float[] embed(String text)
  List<float[]> embedBatch(List<String> texts)
```

---

## First adapters

| `name()` | Class | Production default? |
| -------- | ----- | ------------------- |
| `local` | `LocalTextEmbedder` | **Yes** when strategy=`embedding` |
| `openai-compatible` | `OpenAiCompatibleTextEmbedder` | Opt-in |
| `stub` | `StubTextEmbedder` | Tests / CI only |

---

## Extension checklist (future embedder)

1. Implement `TextEmbedder` in infrastructure
2. Register bean; registry indexes by `name()`
3. Document `archivist.retrieval.embedding.*` properties
4. Optional: add Compose service snippet under `deploy/`
5. No MCP / `port.in` / application changes

---

## Anti-patterns

| Anti-pattern | Why rejected |
| ------------ | ------------ |
| Expose embedder choice on MCP tools | Leaks retrieval technology |
| Put Spring AI `EmbeddingModel` in domain | Clean Architecture violation |
| Download ONNX in default CI path | Violates offline AC-14 |
