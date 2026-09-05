# ADR-010: Repository Strategy — Monorepo

**Status:** Locked
**Date:** 2026-09-04

## Decision

**One monorepo** at `D:\Work\Mentora`, containing the already-locked `design-system/`, `product/`, `ux/`, this `architecture/` directory, and the new `backend/`, `web/`, `mobile/` (with `shared/`, `androidApp/`, `iosApp/`), and `tools/` code directories. No separate per-app repositories.

## Context

Mentora's product/UX/design-system documentation already lives together on disk as siblings, and this architecture explicitly requires design tokens to flow atomically into three consuming codebases (Web, Android, iOS) — a change to `design-tokens.json` legitimately needs to land alongside its generated outputs in all three places in one reviewable unit.

## Options Considered

### Option A — Monorepo (chosen)

One repository, one issue tracker, one place a reviewer (technical or portfolio) looks to see the whole system. A design-token change, a shared API contract change, or a cross-cutting bug fix touching backend + one client is one PR, one CI run, one commit history — not a multi-repo choreography of "bump the shared package version, then update three consumers separately."

### Option B — Multi-repo (one repo per app: `mentora-backend`, `mentora-web`, `mentora-android`, `mentora-ios`, `mentora-design-system`)

Would be justified by independent release cadences, separate teams with separate access control needs, or a desire to open-source one component (e.g. the design system) independently of the product. **None of these apply to Mentora**: it's a single-team (or solo) portfolio project, all four codebases release in lockstep with the same product milestones, and there's no access-control reason to separate them.

**Why not chosen:** would add real coordination overhead disproportionate to the benefit — a shared API contract or design-token change becomes a multi-repo versioning dance (publish a package, bump the version in each consumer, open four PRs instead of one), CI has to be duplicated and cross-repo-triggered instead of running as one pipeline with path filters, and a reviewer wanting to understand "how does an API change flow to all three clients" has to open four repositories instead of one. This is meaningfully worse for a portfolio project specifically, where a reviewer's ability to see the whole system in one place is a real asset.

### Option C — Monorepo, but only for the code (backend/web/mobile), with `design-system/`/`product/`/`ux/` staying in their current location as-is

This is effectively what's being decided — `design-system/`, `product/`, `ux/` already exist at the repository root and this ADR keeps them there, adding the code directories as new top-level siblings rather than nesting them elsewhere. Not a real alternative, more a confirmation that no restructuring of the locked documentation directories is needed or performed.

## Consequences

- One Git repository, one root `README`, one CI system (GitHub Actions, [`DEPLOYMENT.md`](../DEPLOYMENT.md)) with path-based job filtering (a backend-only change doesn't need to run the Android build, etc.).
- The token-generation pipeline (`tools/token-pipeline`, [ADR-011](./ADR-011-design-token-pipeline.md)) can commit its generated output as a single atomic commit/PR touching `web/`, `mobile/androidApp/`, and `mobile/iosApp/` together — the entire reason a monorepo was preferred for this specific cross-cutting concern.
- Full directory tree is in [`REPOSITORY_STRUCTURE.md`](../REPOSITORY_STRUCTURE.md).

## Migration Path

If Mentora ever grows into genuinely independent teams per platform (unlikely for a portfolio project, plausible in a hypothetical "this becomes a real company" future), any one app directory can be extracted into its own repository later using standard history-preserving extraction (`git filter-repo`) — a monorepo-to-multi-repo split is a well-trodden, low-risk migration; the reverse (multi-repo-to-mono) is messier. Starting with a monorepo keeps the cheaper migration direction open.
