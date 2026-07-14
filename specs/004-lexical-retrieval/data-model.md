# Data Model: Lexical Retrieval Strategy and Retrieval Foundation

**Feature**: 004-lexical-retrieval
**Date**: 2026-07-13

> Extends `Query`, implements `KnowledgeGateway`, introduces infrastructure retrieval SPI. No changes to `port.in`, MCP tools, or `KnowledgeCorpus`.

---

## Domain Layer: Extended and Unchanged Types

### Extended: `Query` (`domain.model`)

```java
public record Query(
    String text,
    Set<KnowledgeType> types,
    Set<KnowledgeZone> zones,
    int maxResults
) {
    public Query {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(zones, "zones");
        if (maxResults <= 0) {
            throw new IllegalArgumentException("maxResults must be positive");
        }
        types = Set.copyOf(types);
        zones = Set.copyOf(zones);
    }

    public static Query unrestricted(String text, int maxResults) {
        return new Query(text, Set.of(), Set.of(), maxResults);
    }

    public static Query withType(String text, KnowledgeType type, int maxResults) {
        return new Query(text, Set.of(type), Set.of(), maxResults);
    }

    public static Query withTypes(String text, Set<KnowledgeType> types, int maxResults) {
        return new Query(text, types, Set.of(), maxResults);
    }
}
```

| Field        | Semantics                                                                       |
| ------------ | ------------------------------------------------------------------------------- |
| `text`       | Search text from capability parameter (non-blank when use case invokes gateway) |
| `types`      | Empty = no type filter; non-empty = entry `provenance.type()` must be member    |
| `zones`      | Empty = no zone filter; non-empty = entry `provenance.zone()` must be member    |
| `maxResults` | Maximum evidence count returned                                                 |

See [contracts/query-model.md](contracts/query-model.md) for capability → factory mapping.

### Unchanged: `KnowledgeGateway` (`domain.port.out`)

```java
public interface KnowledgeGateway {
    List<Evidence> retrieve(Query query);
}
```

Behaviour specified in [contracts/knowledge-gateway-port.md](contracts/knowledge-gateway-port.md).

### Unchanged domain model

| Type                       | Role                                                    |
| -------------------------- | ------------------------------------------------------- |
| `Evidence`                 | Retrieved entry: content + provenance                   |
| `Provenance`               | Metadata including `contentAvailability`                |
| `KnowledgeType`            | Entry semantic type — used in `Query` filters           |
| `KnowledgeZone`            | Entry epistemic zone — used in `Query` filters          |
| `ContentAvailability`      | `AVAILABLE`, `UNAVAILABLE_ENTRY_TOO_LARGE`              |
| `KnowledgeCorpus`          | Corpus access (spec 003) — consumed by lexical strategy |
| Eight `port.in` interfaces | Unchanged signatures                                    |

---

## Application Layer: Use Case Model

All eight use cases share the same structure:

```java
public final class FindDecisionsUseCase implements FindDecisions {

    private final KnowledgeGateway gateway;
    private final int defaultMaxResults;

    public FindDecisionsUseCase(KnowledgeGateway gateway, int defaultMaxResults) {
        this.gateway = Objects.requireNonNull(gateway);
        this.defaultMaxResults = defaultMaxResults;
    }

    @Override
    public List<Evidence> findDecisions(String topic) {
        return gateway.retrieve(
                Query.withType(topic, KnowledgeType.DECISION, defaultMaxResults));
    }
}
```

| Use case                      | `Query` factory | Type filter             |
| ----------------------------- | --------------- | ----------------------- |
| `RetrieveContextUseCase`      | `unrestricted`  | none                    |
| `FindDecisionsUseCase`        | `withType`      | `DECISION`              |
| `FindProjectsUseCase`         | `withType`      | `PROJECT`               |
| `FindPeopleUseCase`           | `withType`      | `PERSON`                |
| `FindConceptsUseCase`         | `withTypes`     | `CONCEPT`, `SYNTHESIS`  |
| `FindRelatedKnowledgeUseCase` | `unrestricted`  | none (lexical fallback) |
| `FindReadingsUseCase`         | `withType`      | `READING`               |
| `FindDebriefsUseCase`         | `withType`      | `DEBRIEF`               |

No Spring annotations. No retrieval logic beyond `Query` construction.

---

## Infrastructure Layer: Retrieval Class Model

### Public adapter (implements domain ports)

| Class                                 | Responsibility                                                         |
| ------------------------------------- | ---------------------------------------------------------------------- |
| `KnowledgeGatewayImpl`                | Implements `KnowledgeGateway`; delegates to active `RetrievalStrategy` |
| `ArchivistRetrievalAutoConfiguration` | Spring Boot auto-config entry; imports retrieval + corpus config       |

### Module-internal SPI (`infrastructure.retrieval`)

| Class / Interface           | Visibility                | Responsibility                                            |
| --------------------------- | ------------------------- | --------------------------------------------------------- |
| `RetrievalStrategy`         | public in infra module    | SPI: `name()`, `retrieve(Query)`                          |
| `RetrievalStrategyRegistry` | package-private or public | Maps name → strategy; resolves active; startup validation |
| `LexicalRetrievalStrategy`  | package-private           | First SPI implementation                                  |
| `LexicalScorer`             | package-private           | Tokenisation + weighted scoring                           |
| `RetrievalConfiguration`    | package-private           | `@Configuration` — all retrieval beans                    |
| `RetrievalProperties`       | package-private           | `@ConfigurationProperties` prefix `archivist.retrieval`   |

### Consumed port (spec 003)

| Port              | Used by                                            |
| ----------------- | -------------------------------------------------- |
| `KnowledgeCorpus` | `LexicalRetrievalStrategy` — `loadAll()` per query |

---

## Dependency Graph

```text
transport.mcp.ArchivistMcpTools
    → domain.port.in.*                    (injected)

application.usecase.*
    → domain.port.out.KnowledgeGateway    (injected)
    → domain.model.Query                  (constructed)

infrastructure.retrieval.KnowledgeGatewayImpl
    → domain.port.out.KnowledgeGateway    (implements)
    → RetrievalStrategyRegistry

infrastructure.retrieval.LexicalRetrievalStrategy
    → RetrievalStrategy                   (implements)
    → domain.port.out.KnowledgeCorpus
    → LexicalScorer

infrastructure.retrieval.RetrievalConfiguration
    → registers: strategies, registry, gateway, use case beans
```

**Forbidden edges** (enforced by module boundaries + AC-2):

```text
transport.*  → infrastructure.*   (Java imports — FORBIDDEN)
application.* → infrastructure.* (FORBIDDEN)
domain.*     → infrastructure.*   (FORBIDDEN)
```

Gradle: `transport` → `infrastructure` (classpath only) is **allowed** for composition root.

---

## Configuration Model

### `RetrievalProperties`

| Property                              | Type     | Default   | Description                                         |
| ------------------------------------- | -------- | --------- | --------------------------------------------------- |
| `archivist.retrieval.active-strategy` | `String` | `lexical` | Registered strategy `name()` to activate            |
| `archivist.retrieval.max-results`     | `int`    | `20`      | Default `maxResults` for use case `Query` factories |

### Unchanged (spec 003)

| Property                      | Description                       |
| ----------------------------- | --------------------------------- |
| `ARCHIVIST_SECOND_BRAIN_PATH` | Corpus root — required at startup |

---

## Spring Bean Graph (runtime)

```text
KnowledgeCorpus                    ← SecondBrainConfiguration (spec 003)
LexicalRetrievalStrategy           ← RetrievalConfiguration
RetrievalStrategyRegistry          ← collects [LexicalRetrievalStrategy, ...]
KnowledgeGateway                   ← KnowledgeGatewayImpl(registry)
RetrieveContext                    ← RetrieveContextUseCase(gateway, maxResults)
FindDecisions                      ← FindDecisionsUseCase(gateway, maxResults)
… (six more use cases)
ArchivistMcpTools                  ← injects port.in interfaces (transport)
```

Transport does not declare these beans — auto-configuration does.

---

## Test Fixtures

| Fixture           | Location                                                                           | Purpose                          |
| ----------------- | ---------------------------------------------------------------------------------- | -------------------------------- |
| Spec 003 corpus   | `infrastructure/src/test/resources/fixture-corpus/`                                | Base entries for lexical tests   |
| Oversize entry    | `fixture-corpus/edge-cases/oversize-entry.md`                                      | AC-10, AC-11 availability policy |
| Expected outcomes | [contracts/fixture-lexical-expected.json](contracts/fixture-lexical-expected.json) | Integration test assertions      |

---

## Phase 2 Extension (data model impact)

Adding BM25 or hybrid strategies:

| Layer                      | Changes                                               |
| -------------------------- | ----------------------------------------------------- |
| `domain`                   | **None**                                              |
| `application`              | **None**                                              |
| `infrastructure.retrieval` | New `*RetrievalStrategy` class + `@Bean` registration |
| `transport`                | **None**                                              |
| MCP fixtures               | **None**                                              |
