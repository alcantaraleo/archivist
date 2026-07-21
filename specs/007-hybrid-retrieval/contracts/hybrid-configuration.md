# Hybrid Configuration Contract

**Feature**: 007-hybrid-retrieval  
**Prefix**: `archivist.retrieval`

---

## Strategy selection (unchanged keys)

| Property | Default | Notes |
| -------- | ------- | ----- |
| `active-strategy` | `lexical` | Set to `hybrid` to enable multi-leg consensus |
| `max-results` | `20` | Passed into `Query.maxResults()` by application use cases |

Env: `ARCHIVIST_RETRIEVAL_MAX_RESULTS`.

---

## Hybrid nest (`archivist.retrieval.hybrid.*`)

| Property | Default | Notes |
| -------- | ------- | ----- |
| `max-results` | `20` | `@Min(1)` — hybrid output ceiling |

Env: `ARCHIVIST_RETRIEVAL_HYBRID_MAX_RESULTS`.

**Effective return cap** when `active-strategy=hybrid`:

```text
min(query.maxResults(), hybrid.max-results)
```

---

## Dependencies when hybrid is active

| Leg | Requires |
| --- | -------- |
| lexical | Valid corpus (always) |
| bm25 | Valid corpus |
| embedding | Valid `archivist.retrieval.embedding.*` per [006 embedding-configuration.md](../../006-embedding-retrieval/contracts/embedding-configuration.md) |

CI tests: `embedder=stub`, `store=memory`.

---

## Fail-fast

| Condition | Behaviour |
| --------- | --------- |
| Unknown `active-strategy` | Startup failure; list strategies |
| `hybrid` + invalid embedding config | Startup failure per 006 |

---

## Opacity

No configuration keys expose per-leg results to MCP. Debug logging of votes is **off** in default config; if added later, must be opt-in and stderr-only (not MCP).
