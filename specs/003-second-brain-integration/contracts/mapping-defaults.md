# Default Zone Path-Prefix Mapping

**Feature**: 003-second-brain-integration
**Date**: 2026-07-12

Infrastructure defaults for `archivist.second-brain.mapping.zone-prefixes`. Path prefixes are relative to corpus root, use forward slashes, and match **case-insensitively** (so Title Case vault folders like `Wiki/` map to the `wiki/` default).

| Path prefix      | `KnowledgeZone` |
| ---------------- | --------------- |
| `raw/`           | `SOURCE`        |
| `wiki/`          | `SYNTHESIZED`   |
| `dev/`           | `TECHNICAL`     |
| `identity/`      | `IDENTITY`      |
| `runtime/`       | `COMPILED`      |
| `observability/` | `SIGNAL`        |

**Resolution**: Longest matching prefix wins (after lower-casing both path and prefix). Metadata `zone` field overrides path inference when present and valid.

**Not mapped by default**: `content/` (Published zone) — excluded per spec §7 until explicitly added.

### Type aliases (`archivist.second-brain.mapping.type-aliases`)

Frontmatter `type` values that are not exact `KnowledgeType` enum names may be aliased:

| Alias            | `KnowledgeType` |
| ---------------- | --------------- |
| `meeting-person` | `PERSON`        |

Unknown types with no alias are still omitted from the corpus.

Override via `application.properties` or environment-specific config without code changes.
