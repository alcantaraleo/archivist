# Embedding Configuration Contract

**Feature**: 006-embedding-retrieval  
**Prefix**: `archivist.retrieval`

---

## Strategy selection (unchanged key)

| Property | Default | Notes |
| -------- | ------- | ----- |
| `active-strategy` | `lexical` | Set to `embedding` to enable this feature |
| `max-results` | `20` | Global default max evidence |

---

## Embedding nest (`archivist.retrieval.embedding.*`)

| Property | Default | Notes |
| -------- | ------- | ----- |
| `store` | `memory` | Must match registered `VectorStore.name()` |
| `embedder` | `local` | Must match registered `TextEmbedder.name()` |
| `dimensions` | `384` | Must match active embedder output |
| `top-k` | `50` | Candidate pool before filter/dedupe |
| `min-score` | _(unset)_ | Disabled when unset |
| `index-ttl` | `PT15M` | In-memory rebuild TTL |
| `chunk-size-chars` | `1200` | Soft max chunk body size |
| `chunk-overlap-chars` | `150` | Overlap between hard wraps |

### Local embedder (`embedding.local.*`)

| Property | Default |
| -------- | ------- |
| `model-resource` | Spring AI MiniLM ONNX default |
| `tokenizer-resource` | Spring AI default tokenizer |
| `cache-directory` | `${java.io.tmpdir}/archivist-onnx-model` |

### OpenAI-compatible (`embedding.openai.*`)

| Property | Default |
| -------- | ------- |
| `base-url` | **required** when embedder=`openai-compatible` |
| `api-key` | optional |
| `model` | `text-embedding-3-small` |

---

## Fail-fast rules

| Condition | Behaviour |
| --------- | --------- |
| Unknown `active-strategy` | Startup failure; list registered strategies |
| Unknown `embedding.store` / `embedding.embedder` | Startup failure; list registered adapter ids |
| `embedding` active + misconfigured local/openai | Startup failure (or fail on first use only if research documents deferral — prefer startup) |
| Dimension mismatch | Startup or index-build failure with clear message |

---

## Test / CI

| Property | CI value |
| -------- | -------- |
| `active-strategy` | `embedding` (in embedding tests only) |
| `embedder` | `stub` |
| `store` | `memory` |

Default application config for releases remains `active-strategy=lexical`.
