# Second Brain — Domain Context for Archivist

This document describes the conceptual structure of Second Brain as a knowledge domain.

It is the authoritative reference for Archivist's domain model and public capabilities.

**This document describes stable knowledge concepts, not implementation details.**

The Second Brain is currently implemented as an Obsidian vault. That is an implementation detail. Archivist must not depend on Obsidian, its folder structure, its file formats, or its wikilink syntax. If Second Brain is migrated to a different storage system, this domain model should remain valid and Archivist's public contract should not need to change.

---

## Knowledge Zones

Second Brain organises knowledge into zones. Each zone represents a distinct epistemic role — a different relationship between the human, the AI, and the knowledge it contains.

Archivist maps these to a stable `KnowledgeZone` concept in the domain model. Zone names in this document are conceptual labels, not folder paths.

| Zone | Conceptual Label | Epistemic Role | Mutability |
|---|---|---|---|
| `raw` | **Source** | Immutable source material: clippings, transcripts, meeting summaries, daily notes | Immutable — created once, never modified |
| `wiki` | **Synthesized** | LLM-maintained knowledge layer: concepts, entities, people, projects, debriefs | Evolving — AI writes and updates |
| `dev` | **Technical** | Collaborative technical notes: ADRs, debriefs, project documentation | Collaborative — both human and AI contribute |
| `identity` | **Identity** | Human-maintained source of truth: principles, professional thesis, editorial constitution | Authoritative — human writes only |
| `runtime` | **Compiled** | AI-generated operational context snapshot, assembled from Identity | Derived — AI generates, read-only for agents |
| `observability` | **Signal** | Weekly signal extraction artifacts, written through the Identity lens | Derived — AI generates |
| `content` | **Published** | Downstream content seeds and published artifacts | Optional — created only when durable patterns emerge |

### Zone Semantics for Retrieval

These zone semantics inform retrieval strategy and relevance scoring:

- **Source** zone provides raw evidence — highest fidelity to original material
- **Synthesized** zone provides interpreted concepts — highest semantic density
- **Technical** zone provides architectural and operational decisions
- **Identity** zone provides personal values and professional positioning
- **Compiled** zone provides pre-assembled operational context

---

## Knowledge Types

Every piece of knowledge in Second Brain carries a type. Type drives retrieval targeting: a query for decisions should return `DECISION` entries; a query for people should return `PERSON` entries.

| Type | Description | Primary Zone |
|---|---|---|
| `CONCEPT` | A synthesised understanding of a technical or professional topic | Synthesized |
| `ENTITY` | A named thing: organisation, technology, tool, framework | Synthesized |
| `PERSON` | A named individual with professional context | Synthesized |
| `PROJECT` | An active or past project or initiative | Synthesized, Technical |
| `DECISION` | An architecture or product decision (ADR) | Technical, Synthesized |
| `DEBRIEF` | An incident retrospective or learning review | Technical, Synthesized |
| `SYNTHESIS` | A cross-cutting insight connecting multiple concepts | Synthesized |
| `READING` | A source material item: article, transcript, book note | Source |

Archivist's public capabilities should be aligned to these types. The type vocabulary is stable. The storage representation behind each type is not.

---

## Provenance Model

Provenance is the traceable chain from a retrieved piece of knowledge back to its origin. Every `Evidence` instance returned by Archivist must carry full provenance.

Archivist's provenance model captures the following, regardless of how it is stored:

| Field | Description | Example |
|---|---|---|
| `sourceId` | A stable identifier for the knowledge source | `wiki/concepts/modular-monolith` |
| `title` | The title of the knowledge entry | `Modular Monolith` |
| `type` | The knowledge type | `CONCEPT` |
| `zone` | The zone the knowledge was retrieved from | `SYNTHESIZED` |
| `tags` | Semantic tags associated with the entry | `["architecture", "modular-design"]` |
| `sources` | Links to the raw source material that informed this entry | `["raw/readings/sam-newman-talk"]` |
| `created` | When the entry was created | `2024-11-15` |
| `updated` | When the entry was last modified | `2025-03-02` |

The `sources` field represents the chain back to immutable source material. This is the semantic link between synthesized knowledge and the evidence that grounded it.

---

## The Knowledge Graph

Second Brain's internal links form a semantic knowledge graph. Concepts link to other concepts. Entities link to concepts and projects. Readings link to the concepts they informed.

This graph is a retrieval asset. Archivist can use graph traversal as one retrieval strategy to find related knowledge that a keyword query would miss. This is an implementation concern — the domain capability `findRelatedKnowledge` abstracts it.

The graph has these relationship patterns:

- `CONCEPT` → references `CONCEPT` (related concepts)
- `CONCEPT` → grounded-in `READING` (source evidence)
- `PERSON` → associated-with `PROJECT`, `CONCEPT`
- `PROJECT` → contains `DECISION`, `DEBRIEF`
- `SYNTHESIS` → connects `CONCEPT`+

From a domain perspective, the capability `findRelatedKnowledge` traverses these relationships regardless of how they are physically stored (wikilinks, graph database, edge table, etc.).

---

## Public Capabilities — Domain Alignment

The following table maps each public Archivist capability to the Second Brain domain concepts it retrieves:

| Capability | Primary Types | Primary Zones | Description |
|---|---|---|---|
| `retrieveContext(query)` | All | All | General contextual retrieval across the full knowledge base |
| `findDecisions(topic)` | `DECISION` | Technical, Synthesized | Architecture and product decisions (ADRs) |
| `findProjects(criteria)` | `PROJECT` | Synthesized, Technical | Active or past projects and initiatives |
| `findPeople(name)` | `PERSON` | Synthesized | Known individuals with professional context |
| `findConcepts(topic)` | `CONCEPT`, `SYNTHESIS` | Synthesized | Technical or professional concepts and cross-cutting insights |
| `findRelatedKnowledge(query)` | All | All | Graph-traversal retrieval of semantically connected knowledge |
| `findReadings(topic)` | `READING` | Source | Source materials: articles, transcripts, book notes |
| `findDebriefs(topic)` | `DEBRIEF` | Technical, Synthesized | Incident retrospectives and learning reviews |

---

## What Archivist Must Not Assume

The following are current Second Brain implementation details. Archivist's domain model and public contract must be independent of all of them.

| Detail | Why it must not be assumed |
|---|---|
| Obsidian as the storage engine | Second Brain may be migrated |
| Folder paths (`raw/`, `Wiki/`, `dev/`) | Folder structure is an organisational choice, not a domain concept |
| Wikilink syntax (`[[wikilink]]`) | Link format is Obsidian-specific |
| YAML frontmatter | Metadata format is a storage convention |
| Markdown as the file format | Files may be stored in another format |
| Obsidian Base files (`Entities.base`, etc.) | These are query views, not domain entities |
| Any specific file naming convention | Naming is an implementation detail |

The domain model should be designed so that a complete migration of Second Brain's storage backend requires no changes to Archivist's public API.

---

## Terminology Reference

When writing specifications, code, or documentation for Archivist, use the following terms consistently:

| Use | Avoid |
|---|---|
| `KnowledgeZone` | Obsidian folder, vault zone |
| `KnowledgeType` | note type, page type, frontmatter type |
| `Evidence` | document, note, file, page |
| `Provenance` | metadata, frontmatter, source info |
| `sourceId` | file path, vault path, note path |
| `sources` | backlinks, wikilinks, raw links |
| `findDecisions` | searchADRs, getADRs |
| `findPeople` | searchEntities (for people) |
| `DECISION` | adr (as a type name) |
| `READING` | clipping, raw note |
