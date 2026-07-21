# Hybrid Consensus Algorithm (Infrastructure-Internal)

**Feature**: 007-hybrid-retrieval  
**Authority**: [ADR-0006](../../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md)

---

## Inputs

- `L` — ordered `sourceId`s from lexical leg (size ≤ `query.maxResults()`)
- `B` — ordered `sourceId`s from BM25 leg
- `E` — ordered `sourceId`s from embedding leg
- `effectiveMax = min(query.maxResults(), hybrid.maxResults)`

Rank index: first occurrence position in a list, **0 = best**. Absent id → rank **−1** (not in list).

---

## Consensus score

For each distinct `sourceId` appearing in **L ∪ B ∪ E**:

```text
score(id) = |{ legs containing id }|   // integer 1, 2, or 3
```

---

## Sort key (descending priority)

1. **Higher `score(id)`** first
2. If tied: **lower** embedding rank (ignore if −1; ids only in lexical/BM25 skip embedding tie level only when embedding rank equal/absent — see tie walk below)
3. If tied: **lower** BM25 rank
4. If tied: **lower** lexical rank
5. If still tied: **`sourceId` ascending** (deterministic tests)

**Tie walk (normative):** Compare embedding rank where both have embedding rank ≥ 0; if one lacks embedding rank, treat as worse than any ranked embedding hit for tie purposes at that level. Same pattern for BM25, then lexical.

---

## Output

Ordered `sourceId` list, take first **`effectiveMax`**, map to **`Evidence`** using leg-priority assembly (embedding → BM25 → lexical).

---

## Properties

- **Not** RRF, not LLM rerank
- **Opaque** — scores and ranks are not exported on `Evidence`
- **Deferred**: QueryGovernor may replace full fan-out

---

## Example

| id | in L | in B | in E | score |
| -- | ---- | ---- | ---- | ----- |
| A | ✓ | ✓ | ✓ | 3 |
| B | ✓ | ✓ | — | 2 |
| C | — | — | ✓ | 1 |

Order: A, B, C (subject to tie ranks within same score).
