# Quickstart Validation Guide: Hybrid Consensus Retrieval Strategy

**Feature**: 007-hybrid-retrieval  
**Date**: 2026-07-20

Runnable checks **after implementation**. Run from repository root.

---

## Prerequisites

- Java 21
- Specs 003–006 merged — fixture corpus at `infrastructure/src/test/resources/fixture-corpus/`
- Spec **Approved** (2026-07-20)
- [ADR-0006](../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md) accepted

CI: **`embedder=stub`**, **`store=memory`** for hybrid tests (no ONNX, no network).

---

## 1. Full build and regression

```bash
./gradlew build
```

**Expected**: `BUILD SUCCESSFUL` (AC-1, AC-2, AC-3, AC-16).

---

## 2. Hybrid unit tests

```bash
./gradlew :infrastructure:test --tests "*HybridConsensus*" --tests "*HybridRetrievalStrategy*"
```

**Expected**: Consensus ordering AC-9; tie-break AC-10; cap AC-8.

---

## 3. Hybrid integration tests

```bash
./gradlew :infrastructure:test --tests "*HybridRetrievalIntegration*"
```

**Expected**: AC-5–AC-7, AC-12 (JSON fixture).

---

## 4. Enable hybrid locally (optional)

```yaml
archivist:
  retrieval:
    active-strategy: hybrid
    max-results: 20
    hybrid:
      max-results: 20
    embedding:
      embedder: local   # or stub for offline experiments
      store: memory
```

Env examples:

```bash
export ARCHIVIST_RETRIEVAL_ACTIVE_STRATEGY=hybrid
export ARCHIVIST_RETRIEVAL_HYBRID_MAX_RESULTS=20
export ARCHIVIST_SECOND_BRAIN_PATH=/path/to/corpus
```

**Cost**: three retrievals per query — expect higher latency than single-strategy modes.

---

## 5. Registry smoke

With `active-strategy=hybrid`, application context starts and `RetrievalStrategyRegistry` resolves hybrid (AC-4).

---

## References

- [hybrid-configuration.md](contracts/hybrid-configuration.md)
- [spec.md](spec.md) acceptance criteria
