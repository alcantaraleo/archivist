# MCP Server Contract: Archivist

**Feature**: 001-project-scaffolding
**Date**: 2026-07-11
**Transport**: STDIO (initial); Streamable HTTP (future)
**MCP Spec Version**: 2025-11-25 (via Spring AI 2.0.0 / MCP Java SDK 2.0.0)

---

## Server Identity

```
name:    archivist
version: 0.1.0
```

---

## Scaffold State

At the end of the scaffolding feature, the MCP server **starts successfully** but **registers zero tools**. This is intentional — tools are registered by domain capability specifications, each of which follows the specification workflow independently.

The contract below describes the **intended final contract** once all public capabilities are implemented. It is recorded here as the stable interface target, not as something the scaffold delivers.

---

## Intended Tool Contract (future capabilities)

Each tool maps 1:1 to a `domain.port.in` capability interface. All tools return a JSON array of `Evidence` objects.

### `retrieveContext`

```json
{
  "name": "retrieveContext",
  "description": "General contextual retrieval across the full knowledge base",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": { "type": "string", "description": "The retrieval query" }
    },
    "required": ["query"]
  }
}
```

### `findDecisions`

```json
{
  "name": "findDecisions",
  "description": "Retrieve architecture and product decisions (ADRs)",
  "inputSchema": {
    "type": "object",
    "properties": {
      "topic": { "type": "string", "description": "The topic or context to search decisions for" }
    },
    "required": ["topic"]
  }
}
```

### `findProjects`

```json
{
  "name": "findProjects",
  "description": "Retrieve active or past projects and initiatives",
  "inputSchema": {
    "type": "object",
    "properties": {
      "criteria": { "type": "string", "description": "Search criteria for projects" }
    },
    "required": ["criteria"]
  }
}
```

### `findPeople`

```json
{
  "name": "findPeople",
  "description": "Retrieve known individuals with professional context",
  "inputSchema": {
    "type": "object",
    "properties": {
      "name": { "type": "string", "description": "Name or identifier of the person" }
    },
    "required": ["name"]
  }
}
```

### `findConcepts`

```json
{
  "name": "findConcepts",
  "description": "Retrieve technical and professional concepts and cross-cutting insights",
  "inputSchema": {
    "type": "object",
    "properties": {
      "topic": { "type": "string", "description": "The concept or topic to retrieve" }
    },
    "required": ["topic"]
  }
}
```

### `findRelatedKnowledge`

```json
{
  "name": "findRelatedKnowledge",
  "description": "Graph-traversal retrieval of semantically connected knowledge",
  "inputSchema": {
    "type": "object",
    "properties": {
      "query": { "type": "string", "description": "Starting point for knowledge graph traversal" }
    },
    "required": ["query"]
  }
}
```

### `findReadings`

```json
{
  "name": "findReadings",
  "description": "Retrieve source materials: articles, transcripts, book notes",
  "inputSchema": {
    "type": "object",
    "properties": {
      "topic": { "type": "string", "description": "Topic to search readings for" }
    },
    "required": ["topic"]
  }
}
```

### `findDebriefs`

```json
{
  "name": "findDebriefs",
  "description": "Retrieve incident retrospectives and learning reviews",
  "inputSchema": {
    "type": "object",
    "properties": {
      "topic": { "type": "string", "description": "Topic or incident context" }
    },
    "required": ["topic"]
  }
}
```

---

## Evidence Response Shape

All tools return a JSON array. Each element:

```json
{
  "content": "string — the retrieved knowledge text",
  "provenance": {
    "sourceId": "string — stable identifier",
    "title": "string — human-readable title",
    "type": "CONCEPT | ENTITY | PERSON | PROJECT | DECISION | DEBRIEF | SYNTHESIS | READING",
    "zone": "SOURCE | SYNTHESIZED | TECHNICAL | IDENTITY | COMPILED | SIGNAL",
    "tags": ["string"],
    "sources": ["string"],
    "created": "ISO-8601 timestamp",
    "updated": "ISO-8601 timestamp"
  }
}
```

---

## Error Conditions

| Condition | MCP Response |
| --------- | ------------ |
| Empty or null input parameter | Tool returns error result with `IllegalArgumentException` message |
| No matching knowledge found | Tool returns empty array `[]` — not an error |
| Second Brain unavailable | Tool returns error result with connectivity message |
