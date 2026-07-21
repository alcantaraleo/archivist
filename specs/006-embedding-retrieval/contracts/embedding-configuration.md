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
| `embedder` | `local` | Must match registered `TextEmbedder.name()` — see embedder modes below |
| `dimensions` | `384` | **Must equal** the active model’s vector size (see table); mismatch fails fast |

#### Embedder modes

| `embedder` value | Transport | Typical `dimensions` |
| ---------------- | --------- | --------------------- |
| `local` | In-process ONNX (Spring AI Transformers) | `384` (default MiniLM) |
| `openai-compatible` | HTTP `POST {base-url}/v1/embeddings` | Depends on target — **not** the global default alone |
| `stub` | Deterministic hash (tests only) | Any positive int in tests (e.g. `64`) |

#### `openai-compatible`: OpenAI API vs local proxy (same config id)

Only **`embedding.openai.base-url`**, **`model`**, **`api-key`**, and **`embedding.dimensions`** change.

| Target | `openai.base-url` | Typical `openai.model` | Set `dimensions` to |
| ------ | ----------------- | ---------------------- | --------------------- |
| OpenAI | `https://api.openai.com` | `text-embedding-3-small` (default property) | `1536` |
| Local OpenAI-shaped server | e.g. `http://localhost:8080` | Id required by that server | That model’s output size (often `384`) |

Operator env vars (Spring relaxed binding): `ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_BASE_URL`, `ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_API_KEY`, `ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_MODEL`, `ARCHIVIST_RETRIEVAL_EMBEDDING_DIMENSIONS`. **`OPENAI_API_KEY` is not read** unless copied to `ARCHIVIST_RETRIEVAL_EMBEDDING_OPENAI_API_KEY`.

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

### OpenAI-compatible HTTP (`embedding.openai.*`)

Used when **`embedder=openai-compatible`** (OpenAI’s cloud API **or** any local server with the same `/v1/embeddings` contract).

| Property | Default |
| -------- | ------- |
| `base-url` | **required** when embedder=`openai-compatible` — API root **without** `/v1/embeddings` (adapter appends path) |
| `api-key` | optional — usually required for OpenAI; often empty for local proxies |
| `model` | `text-embedding-3-small` — must be accepted by the server at `base-url` |

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
