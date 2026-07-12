# SPEC: [Capability Name]

**Status:** Draft | Under Review | Approved | Implemented | Rejected
**Feature Branch**: `[###-feature-name]`
**Created:** [DATE]
**Author:** [Author]
**Issue:** #N

---

## 1. Motivation

> Why does this capability need to exist?
> What agent need does it serve?
> What problem does it solve?

---

## 2. Responsibilities

### What Archivist does

- ...

### What Archivist does not do

- Does not summarise or interpret retrieved content
- Does not reason about the results
- ...

---

## 3. Public Contract

### Capability name

```
capabilityName(param: Type, ...): ReturnType
```

### Parameters

| Parameter | Type | Description |
|---|---|---|
| `param` | `String` | Description of the parameter |

### Return type

```java
List<Evidence>
```

Each `Evidence` must include:
- `content` — the retrieved knowledge
- `provenance` — source, location, and timestamp

### Error conditions

| Condition | Behaviour |
|---|---|
| No results found | Returns empty list |
| Query is null or empty | Throws `IllegalArgumentException` |

---

## 4. Acceptance Criteria

> Numbered, verifiable conditions. Each must be independently testable.

1. Given a query with matching knowledge, returns at least one `Evidence` with non-empty content
2. Every returned `Evidence` includes a `Provenance` with a non-null source identifier
3. Given a query with no matching knowledge, returns an empty list without throwing
4. Does not return duplicate evidence for the same source
5. Returns evidence ranked by relevance (most relevant first)
6. Response time is under [threshold] for a typical Second Brain size

---

## 5. Domain Model Impact

> Which domain types does this capability use or introduce?
> Are new entities or value objects required?

- Uses: `Evidence`, `Provenance`, `Query`
- Introduces: [any new types, or "none"]

---

## 6. Architectural Impact

> Which layers are affected?
> Which interfaces are added or changed?
> Are new infrastructure implementations required?

### port.in (public capability)

```java
// New interface or new method on existing interface
public interface CapabilityName {
    List<Evidence> capabilityName(String param);
}
```

### port.out (retrieval gateway)

> Does this capability require a new retrieval gateway method?

### application (use case)

> Which use case class implements this capability?

### infrastructure (retrieval strategy)

> Does this require a new retrieval strategy or changes to an existing one?

### transport (MCP)

> How is this capability exposed over MCP?

---

## 7. Out of Scope

> What is explicitly excluded from this specification?

- ...

---

## 8. Open Questions

> Unresolved decisions that need input before implementation begins.

- [ ] ...

---

## 9. Approval

| Reviewer | Decision | Date |
|---|---|---|
| | | |
