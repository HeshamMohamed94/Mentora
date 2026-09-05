# ADR-012: MVP Scope Is Local-Only (Portfolio/Demo System)

**Status:** Locked
**Date:** 2026-09-04

## Decision

**The Mentora MVP is a local portfolio/demo system, not a system deployed to production during the current implementation phase.** Every client (Web, Android, iOS) and the Ktor backend run on the developer's own machine (plus emulator/simulator, for mobile) against a **local MongoDB Community Edition** instance and **local filesystem media storage**. No cloud hosting service — Railway, Fly.io, Vercel, MongoDB Atlas, Cloudflare R2, S3, a CDN, the Play Store, TestFlight, or the App Store — is part of the MVP implementation. This ADR is cross-cutting: it amends the hosting-related consequences of [ADR-004](./ADR-004-database-engine.md) and [ADR-008](./ADR-008-media-storage.md), and supersedes the staging/production sections previously described in [`DEPLOYMENT.md`](../DEPLOYMENT.md).

## Context

Every architecture document up to this point ([ADR-004](./ADR-004-database-engine.md), [ADR-008](./ADR-008-media-storage.md), [`DEPLOYMENT.md`](../DEPLOYMENT.md), [`TECH_STACK.md § 14`](../TECH_STACK.md)) assumed a staging/demo deployment to managed cloud services (MongoDB Atlas, Railway/Fly.io, Vercel, Cloudflare R2), reachable by evaluators over the public internet. That assumption is now explicitly withdrawn: the MVP's purpose is a **local, runnable, portfolio-quality demonstration** — an implementer or reviewer starts the backend and clients locally and walks through the 29 MVP screens and the portfolio-priority flow end to end on one machine (plus a mobile emulator/simulator). Cloud hosting, managed database tiers, object storage billing, and app-store distribution introduce cost, account setup, and operational surface that this phase does not need and must not assume.

## Options Considered

### Option A — Local-only MVP, cloud hosting as documented future evolution only (chosen)

Backend, database, and media storage all run on `localhost`/the local filesystem. Clients connect via environment-configurable base URLs (see [`DEPLOYMENT.md § 4a — Local Networking Strategy`](../DEPLOYMENT.md)) rather than a fixed public domain. Every previously-described cloud service (Atlas, Railway/Fly.io, Vercel, R2, Play Console, TestFlight/App Store) remains **documented** as an optional, clearly-labeled future evolution path — the architectural seams that would make that migration straightforward (a `MediaStorage` interface, an environment-variable-driven config loader, a repository-per-collection data-access boundary) are preserved, but nothing in the current roadmap requires standing the cloud version up.

**Why this fits:** removes real cost (Atlas/Railway/Vercel/R2 billing, a $99/year Apple Developer Program enrollment) and real operational complexity (account provisioning, secrets management across four platforms, store review processes) from a phase whose actual goal is a working local demonstration, not a publicly reachable product. It keeps every downstream architecture decision (modular monolith, MongoDB, KMP, REST) unchanged — this ADR only removes the *hosting* layer from MVP scope, not any application-layer decision.

### Option B — Keep the staging/cloud deployment as originally planned

**Why not:** directly contradicts the now-locked product decision that this phase is local-portfolio-only. Would introduce hosting accounts, billing, and store-distribution dependencies (Apple Developer Program, Play Console review, R2/Atlas provisioning) that are explicitly out of scope and were previously flagged in [`ADR_INDEX.md`](../ADR_INDEX.md) as requiring separate approval — approval that has now been resolved in the direction of *not* pursuing them for the MVP.

### Option C — Hybrid: local backend/database, but cloud media storage (R2) for video demo quality

**Why not:** reintroduces exactly the account-setup/billing dependency this decision removes, for a benefit (CDN-quality video delivery) that a local demo does not need — a locally-served MP4 over `localhost`/LAN is more than sufficient for a portfolio walkthrough. Rejected as unnecessary complexity for the stated goal.

## Consequences

- [ADR-004](./ADR-004-database-engine.md)'s decision becomes **local MongoDB Community Edition** for the MVP; MongoDB Atlas is retained only as a documented, optional future migration path, not an MVP dependency.
- [ADR-008](./ADR-008-media-storage.md)'s decision becomes **local filesystem storage** behind a storage-abstraction interface, served by Ktor; S3-compatible object storage (Cloudflare R2 or AWS S3) is retained only as a documented, optional future migration path.
- [`DEPLOYMENT.md`](../DEPLOYMENT.md) is rewritten around two environments only — **Local Development** and **Local Demo** — with all previously-described cloud hosting moved into a clearly labeled **"Optional Future Production Evolution"** section.
- [`IMPLEMENTATION_ROADMAP.md`](../IMPLEMENTATION_ROADMAP.md)'s former deployment milestone (M16) is replaced with a **Local Demo Readiness** milestone: local environment configuration, local backend startup, MongoDB Community setup, local media serving, and a full local end-to-end demo verification pass across Web, Android, and iOS — no cloud deployment step remains in the current roadmap.
- Android and iOS builds have **no** store-release requirement in MVP scope: no Play Console publishing, no TestFlight distribution, no Apple Developer Program enrollment. Both apps only need to build, run, and demonstrate the approved flows locally (emulator/simulator or a debug build on a physical device on the same network).
- CI (GitHub Actions) may remain lightweight — build, lint, unit/integration tests — but no CD pipeline for production deployment is part of MVP scope.
- Client base URLs (Web, Android emulator, iOS simulator) must be environment-configurable, never a single hardcoded `localhost` assumption that breaks the Android emulator's networking model — see [`DEPLOYMENT.md § 4a`](../DEPLOYMENT.md).
- No other locked architecture decision changes: the modular-monolith backend style ([ADR-003](./ADR-003-backend-architecture-style.md)), MongoDB modeling strategy ([ADR-005](./ADR-005-mongodb-modeling-strategy.md)), authentication strategy ([ADR-006](./ADR-006-authentication-strategy.md)), API style ([ADR-007](./ADR-007-api-style.md)), AI provider abstraction ([ADR-009](./ADR-009-ai-provider-abstraction.md)), repository strategy ([ADR-010](./ADR-010-repository-strategy.md)), and token pipeline ([ADR-011](./ADR-011-design-token-pipeline.md)) are all unaffected — this ADR is scoped strictly to hosting/deployment.

## Migration Path

Every seam needed to later stand up the previously-planned cloud deployment is preserved rather than deleted: the `MediaStorage` interface (§ [`MEDIA_ARCHITECTURE.md`](../MEDIA_ARCHITECTURE.md)) can gain an S3-compatible implementation without touching domain code; the config loader already reads `MONGODB_URI` and storage settings from environment variables, so pointing at Atlas or R2 is a configuration change, not a rewrite; and [`DEPLOYMENT.md`](../DEPLOYMENT.md)'s "Optional Future Production Evolution" section names the concrete services (Railway/Fly.io, Atlas, Vercel, Cloudflare R2, Play Console, TestFlight/Apple Developer Program) this decision defers, so a future decision to productionize the demo has a documented starting point rather than a blank page.
