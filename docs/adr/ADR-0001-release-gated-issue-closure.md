# ADR-0001: Release-Gated Issue Closure

**Date:** 2026-07-12  
**Status:** Accepted  
**Author:** Leonardo Alcantara

---

## Context

Archivist uses Release Please to batch version bumps and publish GitHub releases after work merges to `main`. Implementation PRs previously used `Closes #NNN`, which closed issues at merge time.

Merged code is not necessarily shipped. A feature can sit on `main` until the next Release Please release PR merges and a GitHub release is published. Closing issues at merge time overstates what is available to consumers.

The project already documents lifecycle labels in the constitution (`under-review` → `pending-release` → `released`), but automation and PR templates still closed issues on merge.

---

## Decision

1. Implementation PRs reference issues with `Refs #NNN` (or `References #NNN`), not `Closes #NNN`.
2. Epic and sub-issues follow the same lifecycle rules. Each issue is listed explicitly in the PR body; there is no cascade closure from epic to sub-issues.
3. When an implementation PR opens, referenced issues receive `under-review`.
4. When an implementation PR merges to `main`, referenced issues move from `under-review` to `pending-release`.
5. When Release Please publishes a GitHub release (`release.published`), all open issues labeled `pending-release` are closed, `pending-release` is removed, and `released` is added.

---

## Options Considered

### Option A: Close issues when the implementation PR merges (`Closes #NNN`)

**Pros:**

- Uses native GitHub semantics
- No extra automation required

**Cons:**

- Conflates merge with ship
- Misleading while Release Please batches releases
- Fights the documented label lifecycle

### Option B: Release-gated closure with `Refs` and workflow automation (chosen)

**Pros:**

- Accurate issue state: merged vs shipped
- Labels provide a readable board state
- Epic and sub-issues share one simple rule

**Cons:**

- Requires workflow maintenance
- PR authors must list every issue explicitly

### Option C: Close only the epic and cascade to sub-issues

**Pros:**

- Shorter PR body

**Cons:**

- Different rules for parent and child issues
- Extra automation to find and close sub-issues
- Easy to miss sub-issues if the epic reference is wrong

---

## Rationale

Release Please makes merge and release distinct events. The issue tracker should reflect that distinction.

Using `Refs` avoids fighting GitHub's auto-close behavior. Treating epic and sub-issues identically keeps the workflow simple: every issue in `github-issues.md` is referenced in the PR and transitions through the same labels.

---

## Consequences

- `AGENTS.md`, PR templates, and `/speckit-taskstoissues` must use `Refs` language, not `Closes`.
- `.github/workflows/issue-lifecycle.yml` owns label transitions and release-time closure.
- Release Please PRs are excluded from implementation PR lifecycle handling.
- Omitting a sub-issue from the PR body means that issue will not transition; `github-issues.md` remains the checklist.

---

## References

- [AGENTS.md](../../AGENTS.md) §13
- [.specify/memory/constitution.md](../../.specify/memory/constitution.md)
- [.github/workflows/issue-lifecycle.yml](../../.github/workflows/issue-lifecycle.yml)
