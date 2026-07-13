# Second Brain Integration Contract Fixtures

**Feature**: 003-second-brain-integration
**Date**: 2026-07-12
**Authoritative human contract**: [spec.md](../spec.md) §3, [data-model.md](../data-model.md)

---

## Purpose

These artefacts define the **machine-verifiable contract** for `KnowledgeCorpus` behaviour against the checked-in fixture corpus. Integration tests load the fixture vault from `infrastructure/src/test/resources/fixture-corpus/` and compare results to [fixture-catalog-expected.json](fixture-catalog-expected.json).

Unintended drift in mapping, provenance, or content extraction **fails `./gradlew :infrastructure:test`**.

---

## Fixtures

| File                                                           | Enforces                                                      |
| -------------------------------------------------------------- | ------------------------------------------------------------- |
| [knowledge-corpus-port.md](knowledge-corpus-port.md)           | `KnowledgeCorpus` method semantics (human-readable)           |
| [mapping-defaults.md](mapping-defaults.md)                     | Default zone path-prefix rules                                |
| [fixture-catalog-expected.json](fixture-catalog-expected.json) | Expected `catalog()` / `loadAll()` outcomes per fixture entry |

---

## Intentional Contract Changes

To change corpus access behaviour:

1. Update the approved specification
2. Update fixture Markdown under `infrastructure/src/test/resources/fixture-corpus/` if scenarios change
3. Update `fixture-catalog-expected.json` together with mapper/parser changes
4. Update [mapping-defaults.md](mapping-defaults.md) if default zone rules change

Fixture-only changes without spec approval are not permitted.

---

## Comparison Mechanism

`SecondBrainKnowledgeCorpusIntegrationTest` (see [quickstart.md](../quickstart.md)):

- Boots minimal Spring context with `archivist.second-brain.path` → fixture corpus
- Asserts `catalog()` size and each `Provenance` field against JSON expected entries
- Asserts `loadAll()` content bodies match expected trimmed body strings
- Asserts `loadBySourceId` hit/miss cases
- Asserts `unknown-type.md` absent from catalog

Comparison uses Jackson `JsonNode` or structured assertions on domain records — no new assertion libraries.
