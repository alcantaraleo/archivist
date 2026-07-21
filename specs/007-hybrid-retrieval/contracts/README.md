# Contracts: 007-hybrid-retrieval

**Feature**: 007-hybrid-retrieval  
**Status**: Approved with spec (2026-07-20)

---

## Files

| Contract | Purpose |
| -------- | ------- |
| [hybrid-configuration.md](hybrid-configuration.md) | Properties, env vars, effective cap |
| [knowledge-gateway-hybrid-semantics.md](knowledge-gateway-hybrid-semantics.md) | Gateway behaviour when hybrid active |
| [hybrid-consensus-algorithm.md](hybrid-consensus-algorithm.md) | Normative vote + tie-break |
| [retrieval-strategy-spi.md](retrieval-strategy-spi.md) | Registry + checklist |
| [fixture-hybrid-consensus-scenario.json](fixture-hybrid-consensus-scenario.json) | AC-12 integration scenario |

---

## Authority

Architectural policy: [ADR-0006](../../../docs/adr/ADR-0006-hybrid-consensus-retrieval.md).

Implementation tests SHOULD assert behaviour against these contracts and ADR, not duplicate prose in test names only.

---

## README / roadmap

When implementation completes (AC-15), check Phase 2 hybrid item in root `README.md` and link ADR-0006 + [quickstart.md](../quickstart.md).
