# Data Model: Second Brain Integration (Initial)

**Feature**: 003-second-brain-integration
**Date**: 2026-07-12

> Introduces `KnowledgeCorpus` `port.out`, infrastructure Second Brain adapter, and fixture corpus. No changes to `port.in`, MCP tools, or stub use cases.

---

## Domain Layer: New and Unchanged Types

### New: `KnowledgeCorpus` (`domain.port.out`)

```java
public interface KnowledgeCorpus {
    List<Provenance> catalog();
    Optional<Evidence> loadBySourceId(String sourceId);
    List<Evidence> loadAll();
}
```

See [spec.md §3](spec.md) for full semantics and validation rules.

### New: `KnowledgeCorpusException` (`domain.port.out`)

Unchecked exception for unrecoverable corpus access failures after startup (unreadable root, IO failure aborting entire operation).

### New: `ContentAvailability` (`domain.model`)

```java
public enum ContentAvailability {
    AVAILABLE,
    UNAVAILABLE_ENTRY_TOO_LARGE
}
```

- `AVAILABLE` — body loaded into `Evidence.content`
- `UNAVAILABLE_ENTRY_TOO_LARGE` — entry discoverable; body not loaded; `Evidence.content` empty

Infrastructure sets the flag. Retrieval strategies and `KnowledgeGateway` implementations decide how to treat non-`AVAILABLE` entries.

### Extended: `Provenance`

Add field: `ContentAvailability contentAvailability` (non-null).

### Unchanged domain model

| Type               | Role                                                      |
| ------------------ | --------------------------------------------------------- |
| `Evidence`         | Loaded entry: body + provenance                           |
| `Provenance`       | Metadata returned by `catalog()` and nested in `Evidence` |
| `KnowledgeType`    | Entry semantic type                                       |
| `KnowledgeZone`    | Entry epistemic zone                                      |
| `KnowledgeGateway` | Unchanged — lexical retrieval spec                        |

---

## Infrastructure Layer: Class Model

### Public adapter

| Class                          | Responsibility                                                          |
| ------------------------------ | ----------------------------------------------------------------------- |
| `SecondBrainKnowledgeCorpus`   | Implements `KnowledgeCorpus`; orchestrates walk, parallel load, mapping |
| `SecondBrainConfiguration`     | Spring `@Configuration` — registers corpus bean                         |
| `SecondBrainCorpusProperties`  | `@ConfigurationProperties` — path, max-entry-bytes, ignore globs        |
| `SecondBrainMappingProperties` | `@ConfigurationProperties` — zone prefix rules, optional zone defaults  |

### Private implementation (package-private)

| Class                    | Responsibility                                                       |
| ------------------------ | -------------------------------------------------------------------- |
| `CorpusWalker`           | `Files.walk` discovery; applies ignore rules; yields candidate paths |
| `CorpusEntryLoader`      | Bounded parallel read + invoke parser                                |
| `MetadataEnvelopeParser` | Split YAML envelope / body; SnakeYAML → map                          |
| `CorpusEntryMapper`      | Map → `Provenance` / `Evidence`; sourceId, title, timestamps         |
| `ZoneTypeResolver`       | Path prefix + metadata → `KnowledgeZone`, `KnowledgeType`            |
| `SourceIdNormalizer`     | Relative path → stable sourceId                                      |

---

## Internal Parse Pipeline (single entry)

```
Path on disk
    → Files.size()
    → if size > max-entry-bytes: read leading metadata-read-bytes only → UNAVAILABLE_ENTRY_TOO_LARGE
    → else: read full file → AVAILABLE
    → MetadataEnvelopeParser → { metadata map, body or empty }
    → ZoneTypeResolver → KnowledgeZone, KnowledgeType (or omit if unrecoverable)
    → CorpusEntryMapper → Provenance / Evidence with contentAvailability
```

**`catalog()` path**: metadata parse only for normal entries; size-limited entries included with flag.

**`loadAll()` / `loadBySourceId()`**: full body when `AVAILABLE`; empty `content` when `UNAVAILABLE_ENTRY_TOO_LARGE`.

---

## Configuration Model

### `SecondBrainCorpusProperties` (`archivist.second-brain`)

| Property              | Env                           | Default                           | Description                                 |
| --------------------- | ----------------------------- | --------------------------------- | ------------------------------------------- |
| `path`                | `ARCHIVIST_SECOND_BRAIN_PATH` | (required)                        | Corpus root directory                       |
| `max-entry-bytes`     | —                             | `1048576`                         | Body load limit; exceed → flag, do not skip |
| `metadata-read-bytes` | —                             | `65536`                           | Leading read when body not loaded           |
| `load-concurrency`    | —                             | `32`                              | Max parallel entry loads                    |
| `ignore-globs`        | —                             | `**/templates/**`, `**/.trash/**` | Extra skip patterns                         |

### `SecondBrainMappingProperties` (`archivist.second-brain.mapping`)

| Property             | Default                                                            | Description                                                       |
| -------------------- | ------------------------------------------------------------------ | ----------------------------------------------------------------- |
| `zone-prefixes`      | See [contracts/mapping-defaults.md](contracts/mapping-defaults.md) | Map path prefix → `KnowledgeZone`                                 |
| `zone-default-types` | `{}`                                                               | Optional zone → default `KnowledgeType` when metadata type absent |

### Metadata field mapping (infrastructure-only)

| Storage key | Domain field        | Notes                       |
| ----------- | ------------------- | --------------------------- |
| `id`        | `sourceId` override | Optional                    |
| `title`     | `title`             |                             |
| `type`      | `KnowledgeType`     | Enum name, case-insensitive |
| `zone`      | `KnowledgeZone`     | Optional override           |
| `tags`      | `tags`              | List or CSV                 |
| `sources`   | `sources`           | List of sourceIds           |
| `created`   | `created`           | ISO-8601                    |
| `updated`   | `updated`           | ISO-8601                    |

Domain types never reference these key names in public API.

---

## Dependency Flow

```
┌─────────────────────────────────────────────────────────────┐
│  Future: lexical retrieval / KnowledgeGateway impl         │
└──────────────────────────┬──────────────────────────────────┘
                           │ injects
┌──────────────────────────▼──────────────────────────────────┐
│  domain.port.out.KnowledgeCorpus                           │
└──────────────────────────┬──────────────────────────────────┘
                           │ implements
┌──────────────────────────▼──────────────────────────────────┐
│  infrastructure.secondbrain.SecondBrainKnowledgeCorpus       │
│    → CorpusWalker → CorpusEntryLoader (parallel)             │
│    → MetadataEnvelopeParser → ZoneTypeResolver → Mapper      │
└──────────────────────────┬──────────────────────────────────┘
                           │ reads
┌──────────────────────────▼──────────────────────────────────┐
│  Second Brain root (ARCHIVIST_SECOND_BRAIN_PATH)             │
│  Test: infrastructure/src/test/resources/fixture-corpus/     │
└─────────────────────────────────────────────────────────────┘
```

**No transport or application dependency on `KnowledgeCorpus` in this feature.**

---

## Source Layout (this feature)

```text
domain/src/main/java/io/archivist/domain/port/out/
├── KnowledgeCorpus.java              # NEW
├── KnowledgeCorpusException.java     # NEW
└── KnowledgeGateway.java             # unchanged

infrastructure/src/main/java/io/archivist/infrastructure/secondbrain/
├── SecondBrainKnowledgeCorpus.java
├── SecondBrainConfiguration.java
├── SecondBrainCorpusProperties.java
├── SecondBrainMappingProperties.java
├── CorpusWalker.java
├── CorpusEntryLoader.java
├── MetadataEnvelopeParser.java
├── CorpusEntryMapper.java
├── ZoneTypeResolver.java
└── SourceIdNormalizer.java

infrastructure/src/test/java/io/archivist/infrastructure/secondbrain/
├── MetadataEnvelopeParserTest.java
├── ZoneTypeResolverTest.java
├── SourceIdNormalizerTest.java
├── SecondBrainKnowledgeCorpusIntegrationTest.java
└── support/
    └── SecondBrainCorpusTestConfiguration.java

infrastructure/src/test/resources/fixture-corpus/
├── raw/readings/sample-reading.md
├── wiki/concepts/sample-concept.md
├── dev/decisions/sample-decision.md
├── wiki/people/sample-person.md
├── wiki/concepts/sample-with-sources.md
└── edge-cases/unknown-type.md         # omitted from catalog
└── edge-cases/oversize-entry.md       # flagged UNAVAILABLE_ENTRY_TOO_LARGE (or test-generated)
```

---

## Gradle Changes

**`infrastructure/build.gradle.kts`**:

```kotlin
dependencies {
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.snakeyaml)  // explicit; version from Spring Boot BOM
    testImplementation(libs.spring.boot.starter.test)
}
```

**`gradle/libs.versions.toml`**: add `snakeyaml` library alias if not already present (coordinate `org.yaml:snakeyaml`, BOM-managed).

---

## Fixture Corpus Scenarios

| File                     | Exercises                                                       |
| ------------------------ | --------------------------------------------------------------- |
| `sample-reading.md`      | `SOURCE` + `READING`; minimal frontmatter                       |
| `sample-concept.md`      | `SYNTHESIZED` + `CONCEPT`; tags                                 |
| `sample-decision.md`     | `TECHNICAL` + `DECISION`                                        |
| `sample-person.md`       | `SYNTHESIZED` + `PERSON`                                        |
| `sample-with-sources.md` | `sources` provenance chain                                      |
| `unknown-type.md`        | Omitted from catalog; WARN                                      |
| `oversize-entry.md`      | Included with `UNAVAILABLE_ENTRY_TOO_LARGE`; empty body (AC-10) |

Expected outcomes: [contracts/fixture-catalog-expected.json](contracts/fixture-catalog-expected.json).

---

## Memory and Scale Characteristics

| Operation        | Disk reads                        | Peak memory (order)                        |
| ---------------- | --------------------------------- | ------------------------------------------ |
| `catalog()`      | All entries (metadata-only parse) | O(entries × metadata size)                 |
| `loadBySourceId` | One entry                         | O(one entry body)                          |
| `loadAll()`      | All entries (parallel)            | O(total body size) bounded by per-file max |

At ~500 files × ~10 KiB average ≈ 5 MiB for full `loadAll()` — acceptable Phase 1. Oversize entries contribute metadata only to memory. See [ADR-0002](../../docs/adr/ADR-0002-defer-corpus-indexing-and-caching.md) and [ADR-0003](../../docs/adr/ADR-0003-flag-size-limited-corpus-entries.md).
