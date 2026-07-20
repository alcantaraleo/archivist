# BM25 Configuration Contract

**Feature**: 005-bm25-retrieval  
**Prefix**: `archivist.retrieval`

---

## Core retrieval (unchanged from 004)

| Property | Default | Notes |
| -------- | ------- | ----- |
| `active-strategy` | `lexical` | Set to `bm25` to activate BM25 ranking |
| `max-results` | `20` | Default cap for use cases |

## BM25 block (`archivist.retrieval.bm25.*`)

| Property | Default | Purpose |
| -------- | ------- | ------- |
| `field-boost-title` | `3.0` | Lucene boost — title field |
| `field-boost-tags` | `2.0` | Lucene boost — concatenated tags |
| `field-boost-body` | `1.0` | Lucene boost — body field |
| `index-ttl` | `PT15M` | Max cache age before rebuild |
| `k1` | `1.2` | BM25 k1 (Lucene `BM25Similarity`) |
| `b` | `0.75` | BM25 b |

## Environment

| Variable | Required when |
| -------- | ------------- |
| `ARCHIVIST_SECOND_BRAIN_PATH` | Any real corpus load (same as spec 003) |

## Startup validation

| Condition | Behaviour |
| --------- | --------- |
| `active-strategy=bm25` | Beans named `lexical` and `bm25` both registered |
| Unknown strategy name | Fail fast with list of registered `name()` values |
| Duplicate strategy `name()` | Fail fast |

## Example (local smoke)

```properties
archivist.retrieval.active-strategy=bm25
archivist.retrieval.bm25.index-ttl=PT15M
```

```bash
export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/fixture-or-vault
./gradlew :transport:bootRun
```
