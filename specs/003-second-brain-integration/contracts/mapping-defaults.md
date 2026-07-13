# Default Zone Path-Prefix Mapping

**Feature**: 003-second-brain-integration
**Date**: 2026-07-12

Infrastructure defaults for `archivist.second-brain.mapping.zone-prefixes`. Path prefixes are relative to corpus root, case-sensitive, and use forward slashes.

| Path prefix      | `KnowledgeZone` |
| ---------------- | --------------- |
| `raw/`           | `SOURCE`        |
| `wiki/`          | `SYNTHESIZED`   |
| `dev/`           | `TECHNICAL`     |
| `identity/`      | `IDENTITY`      |
| `runtime/`       | `COMPILED`      |
| `observability/` | `SIGNAL`        |

**Resolution**: Longest matching prefix wins. Metadata `zone` field overrides path inference when present and valid.

**Not mapped by default**: `content/` (Published zone) — excluded per spec §7 until explicitly added.

Override via `application.properties` or environment-specific config without code changes.
