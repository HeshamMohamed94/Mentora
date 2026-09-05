# ADR-007: API Style — REST over GraphQL

**Status:** Locked
**Date:** 2026-09-04

## Decision

Mentora's API is **REST**, JSON over HTTP, versioned at the URL path (`/api/v1/...`). No GraphQL layer.

## Context

Three clients (Web, Android, iOS) with genuinely different data-shaping needs per screen is the textbook argument *for* GraphQL (each client fetches exactly the fields/nesting it needs in one round trip, no over/under-fetching). The brief asks this to be evaluated honestly rather than defaulted away.

## Options Considered

### Option A — REST (chosen)

A well-known, small set of resources (courses, enrollments, progress, quizzes, certificates, learning paths, AI conversations, users) with a genuinely bounded, well-understood set of client "views" into them — 29 screens, not an open-ended reporting tool. REST's per-endpoint predictability (a route does one thing, has one documented response shape) is simpler to implement, test, cache (HTTP caching semantics apply directly), and reason about for a small team, and simpler for a future Codex implementer to build correctly milestone-by-milestone without needing to reason about resolver graphs, N+1 query mitigation (DataLoader-style batching), or schema evolution discipline.

### Option B — GraphQL

**Why not:** GraphQL's core benefit (flexible, client-driven field selection) matters most when there are many client shapes against a large, evolving schema, or when the same screen legitimately needs different graphs of nested data across a large surface. Mentora's screens are individually well-scoped (per [`../product/SCREEN_INVENTORY.md`](../../product/SCREEN_INVENTORY.md), each screen's "Main content" is already enumerated), so REST endpoints can be designed to match each screen's real needs directly (e.g. a `GET /courses/{id}` response shaped for Course Details, not a generic graph the client must traverse). GraphQL would add: a schema/resolver layer to build and keep in sync with the REST-equivalent domain logic anyway, N+1 query mitigation work (a `Course` resolver reaching into `Section`/`Lesson` naturally risks this), and a steeper learning curve for a reviewer skimming the codebase — all real cost for a benefit (flexible querying) this MVP's bounded screen set doesn't need.

### Option C — Hybrid (REST for most things, GraphQL for one complex area, e.g. Admin dashboards)

**Why not:** the brief explicitly says "hybrid only if genuinely justified." Admin's data needs (§ `../product/SCREEN_INVENTORY.md` screens 25–29) are simple lists/counts/tables — not complex enough nested-graph queries to justify running two API paradigms in one small backend. Rejected as unjustified complexity.

## API Conventions Locked Alongside This Decision

Documented fully in [`API_CONTRACT.md`](../API_CONTRACT.md); summarized here since they follow directly from choosing REST:

- **Versioning:** URL path (`/api/v1/...`) — simple, visible, and cache-friendly; a `v2` can be introduced additively without breaking existing mobile clients that haven't updated (a real concern for native apps, which can't be force-upgraded the way a web page can).
- **Pagination:** cursor-based for every list endpoint, not offset/limit — avoids MongoDB's skip/limit performance cliff on large collections and gives stable pagination under concurrent writes.
- **Errors:** a fixed `{ error: { code, message, details? } }` envelope — see [`API_CONTRACT.md § 4`](../API_CONTRACT.md) — where `code` is what every client localizes from, `message` is an English developer-facing fallback, never surfaced raw to end users for 5xx-class errors.
- **Search/filter/sort:** query parameters (`?q=`, `?category=`, `?level=`, `?sort=`), backed by a MongoDB text index for keyword search — no separate search engine at MVP scale.
- **Idempotency:** the demo-checkout completion endpoint is idempotent by construction (checks for an existing `Enrollment` before creating a second one for the same user+course), not via a client-supplied idempotency-key header — simpler, and sufficient given there's no legitimate reason to "re-purchase" an already-owned course.

## Consequences

- Every one of the 29 screens' data needs maps to one or a small number of REST endpoints, enumerated in [`API_CONTRACT.md § 6`](../API_CONTRACT.md)'s conceptual endpoint inventory.
- Mobile clients (which can't be force-upgraded) are protected from breaking changes by the `/v1` path — a genuinely necessary discipline for native app APIs, whichever style was chosen.

## Migration Path

If a specific screen's data-shaping needs ever become genuinely painful under REST (e.g. a future Admin analytics view that needs many different nested-graph shapes), a **GraphQL gateway in front of the existing REST/service layer** for that one area is the natural next step — not a full rewrite, since the underlying service-layer logic REST endpoints call is the same logic a GraphQL resolver would call.
