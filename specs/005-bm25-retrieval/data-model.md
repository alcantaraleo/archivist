# Data Model: BM25 Retrieval Strategy

**Feature**: 005-bm25-retrieval  
**Date**: 2026-07-20

> Adds infrastructure-only BM25 indexing and caching. **No** changes to `domain.model` types, `port.in`, or `KnowledgeGateway` / `KnowledgeCorpus` signatures.

---

## Domain Layer (unchanged)

| Type | Change |
| ---- | ------ |
| `Evidence`, `Provenance`, `Query`, `KnowledgeType`, `KnowledgeZone`, `ContentAvailability` | Unchanged |
| `KnowledgeGateway` | Unchanged signature; BM25 affects ranking when `active-strategy=bm25` |
| `KnowledgeCorpus` | Unchanged — BM25 uses `catalog()`, `loadAll()`, `loadBySourceId()` |
| Eight `port.in` interfaces | Unchanged |

See [spec 004 data-model](../004-lexical-retrieval/data-model.md) for `Query` factories and gateway semantics.

---

## Application Layer (unchanged)

Use cases continue to translate capability parameters → `Query` → `KnowledgeGateway.retrieve`. No BM25 types or configuration in application source.

---

## Infrastructure Layer: New and Extended Components

### Extended: `RetrievalProperties`

Add BM25 nested settings (bound from `archivist.retrieval.bm25.*`):

| Field | Type | Default | Validation |
| ----- | ---- | ------- | ---------- |
| `fieldBoostTitle` | `float` | `3.0f` | `> 0` |
| `fieldBoostTags` | `float` | `2.0f` | `> 0` |
| `fieldBoostBody` | `float` | `1.0f` | `> 0` |
| `indexTtl` | `Duration` | `PT15M` | not negative |
| `k1` | `float` | `1.2f` | `> 0` |
| `b` | `float` | `0.75f` | `0 <= b <= 1` |

Existing fields unchanged: `activeStrategy` (default `lexical`), `maxResults` (default `20`).

---

### New: `RetrievalTokenization` (package-private)

| Method | Contract |
| ------ | -------- |
| `List<String> tokenize(String text)` | Same as `LexicalScorer#tokenize` — lowercase ROOT, split `[\\s\\p{Punct}]+`, drop empty |

Shared by lexical scorer (may delegate) and BM25 query builder + analyzer.

---

### New: `CorpusFingerprint` (package-private)

| Method | Contract |
| ------ | -------- |
| `String compute(KnowledgeCorpus corpus)` | Returns stable string per [research.md](research.md) Decision 6 |

Inputs: `corpus.catalog()` only.

---

### New: `Bm25IndexCache` (package-private)

| State | Description |
| ----- | ----------- |
| `fingerprintAtBuild` | Last successful fingerprint |
| `builtAt` | `Instant` of last rebuild |
| `directory` | Lucene `Directory` (in-memory) |
| `searcherSupplier` | Provides current `IndexSearcher` |

| Method | Behaviour |
| ------ | --------- |
| `IndexSearcher acquireSearcher(KnowledgeCorpus corpus, Bm25Properties props, IndexBuilder builder)` | If invalid (fingerprint or TTL), rebuild; else reuse |
| `void invalidate()` | Test hook / optional manual invalidation |

Invalidation rule: rebuild when fingerprint ≠ cached **or** now > `builtAt + indexTtl`.

---

### New: `Bm25IndexBuilder` (package-private)

| Responsibility |
| -------------- |
| `loadAll()` from corpus |
| For each `Evidence`, add Lucene document per [research.md](research.md) Decision 4 |
| Apply field boosts and `BM25Similarity` from properties |
| Store `sourceId` for hit resolution |

---

### New: `Bm25RetrievalStrategy`

```java
final class Bm25RetrievalStrategy implements RetrievalStrategy {
    static final String STRATEGY_NAME = "bm25";

    @Override
    public String name() { return STRATEGY_NAME; }

    @Override
    public List<Evidence> retrieve(Query query);
}
```

| Step | Behaviour |
| ---- | --------- |
| Null query | `NullPointerException` |
| Blank / whitespace `query.text()` | Empty list |
| Tokenise | `RetrievalTokenization.tokenize` → empty tokens → empty list |
| Search | Acquire searcher from cache; build boolean query (all tokens MUST match) with type/zone filters |
| Rank | Lucene score descending |
| Map hits | Load `Evidence` by `sourceId` (map from last rebuild or `loadBySourceId`) |
| Finish | Dedupe by `sourceId`, cap at `query.maxResults()` |

---

### Modified: `RetrievalConfiguration`

| Bean | Change |
| ---- | ------ |
| `bm25RetrievalStrategy` | **New** — `RetrievalStrategy` with name `bm25` |
| `lexicalRetrievalStrategy` | Unchanged |
| `retrievalStrategyRegistry` | Collects both strategies |

Startup with `active-strategy=bm25` requires both beans registered (AC-4).

---

### Modified: `RetrievalStrategyRegistry` / SPI

No interface change. Implementation table:

| `name()` | Class | Status |
| -------- | ----- | ------ |
| `lexical` | `LexicalRetrievalStrategy` | Existing |
| `bm25` | `Bm25RetrievalStrategy` | **New in 005** |

See [contracts/retrieval-strategy-spi.md](contracts/retrieval-strategy-spi.md).

---

## Lucene Document Schema (infrastructure-internal)

| Field | Lucene type | Stored | Indexed | Notes |
| ----- | ----------- | ------ | ------- | ----- |
| `sourceId` | StringField | yes | yes | Document id key |
| `title` | TextField | no | yes | boost title |
| `tags` | TextField | no | yes | joined tags |
| `body` | TextField | no | yes | empty when size-limited |
| `knowledgeType` | StringField | no | yes | enum name |
| `knowledgeZone` | StringField | no | yes | enum name |

**Not exposed** outside `infrastructure.retrieval` package.

---

## Relationships

```text
KnowledgeGatewayImpl
    └── RetrievalStrategyRegistry.getActive()
            ├── LexicalRetrievalStrategy → KnowledgeCorpus.loadAll() [scan]
            └── Bm25RetrievalStrategy
                    ├── CorpusFingerprint ← KnowledgeCorpus.catalog()
                    ├── Bm25IndexCache
                    │       └── Bm25IndexBuilder ← KnowledgeCorpus.loadAll()
                    └── Lucene IndexSearcher
```

---

## Validation Rules (inherited + BM25-specific)

| Rule | Source |
| ---- | ------ |
| Every returned `Evidence` has complete `Provenance` | Spec §3 |
| Size-limited: no body in index | ADR-0003 |
| Duplicate `sourceId` at most once in results | Spec AC-9 |
| Unknown `active-strategy` | Startup failure |
| Index build failure | Propagate `KnowledgeCorpusException` |
