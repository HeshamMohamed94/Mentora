# ADR-004: Database Engine — MongoDB

**Status:** Locked (amended for local-demo scope, see [ADR-012](./ADR-012-local-demo-scope.md))
**Date:** 2026-09-04

## Decision

**MongoDB**, accessed via the official Kotlin Coroutine driver. **For the current MVP, this is MongoDB Community Edition running as a local instance on the developer's machine** — this is the active MVP database, not a placeholder for a cloud tier. MongoDB Atlas is **not** used in the MVP and is retained only as a documented, optional future migration path (§ Migration Path below), per [ADR-012](./ADR-012-local-demo-scope.md)'s local-only scope decision.

## Context

The brief named MongoDB as a direction to strongly evaluate, and explicitly required that it not be accepted "simply because suggested" — this ADR does that evaluation honestly, including where PostgreSQL would arguably be the *safer* default choice. The database-engine choice (document vs. relational) is independent of where that database instance runs; this ADR's Options Considered section evaluates the former, while [ADR-012](./ADR-012-local-demo-scope.md) settles the latter (local, for the current MVP).

Mentora's data has two distinct shapes:
1. **Naturally hierarchical, bounded, authored-as-a-whole content** — a `Course` with ordered `Section`s, each with ordered `Lesson`s; a `Quiz` with ordered `Question`s and their options. These are read and written as a unit (an Instructor edits a whole curriculum in one session; a Student reads a whole course's curriculum outline at once).
2. **Relational, unboundedly-growing, cross-referenced records** — `Enrollment`, `Progress`, `QuizAttempt`, AI Tutor `Message`s, `Certificate`s — records that reference `User` and `Course` by ID and accumulate over the life of the product.

## Options Considered

### Option A — MongoDB (chosen)

**Why it fits:** shape (1) above is MongoDB's strongest case — a course's curriculum is a document tree that's naturally modeled as embedded arrays (bounded: a course won't realistically have thousands of lessons, and it's always read/written together), with zero JOIN overhead for the single most-read piece of content in the product (Course Details' curriculum outline, Course Player's lesson list). Kotlin's official MongoDB coroutine driver integrates cleanly with Ktor's coroutine-first model. MongoDB Community Edition installs and runs locally with effectively zero setup (a single binary/service, or one `docker compose up` line) — exactly matched to a local-demo MVP's actual need, with MongoDB Atlas available later, unchanged in code, if the product ever moves toward a hosted deployment (§ Migration Path).

**Where MongoDB is the weaker fit, and how this architecture compensates:** shape (2) above is where a relational database's foreign-key constraints and JOINs would normally do integrity work MongoDB doesn't do automatically. This architecture compensates with three concrete disciplines, locked here rather than left to implementation judgment:
- **MongoDB multi-document ACID transactions** (available since MongoDB 4.0, fully supported by the Kotlin coroutine driver) are used at every point where two collections must change together consistently — most critically, demo-checkout completion (`DemoPurchase` + `Enrollment` created together) and lesson-completion (`Progress` update + course-completion check + `Certificate` issuance) — see [`DATABASE_MODEL.md`](../DATABASE_MODEL.md).
- **No document is allowed to grow unboundedly.** `QuizAttempt`, AI `Message`, and progress-event data are always their own collection, referenced by ID from the owning `User`/`Enrollment` — never appended into an array inside a document that's read on every request. This directly answers the brief's explicit document-growth-limit concern; see [`DATABASE_MODEL.md § 1`](../DATABASE_MODEL.md).
- **Application-level referential checks** (does this `courseId` exist before creating an `Enrollment`? is this `userId` the owner of this `Course` before allowing an edit?) are enforced in the service layer, not assumed away — this is standard practice for any document database and is made explicit per-collection in [`DATABASE_MODEL.md`](../DATABASE_MODEL.md).

### Option B — PostgreSQL

**The strongest alternative, genuinely.** Foreign keys would make "an `Enrollment` cannot reference a deleted `Course`" free instead of an application-level check; a normalized `sections`/`lessons` schema with JOINs would make certain admin queries (e.g. "list all lessons missing a video across every course") a single query instead of an aggregation pipeline; and Postgres's transactional guarantees are the database's default behavior, not an opt-in feature reached for at specific points.

**Why not chosen:** the course-authoring/curriculum-reading path — the single most product-central data shape in Mentora — is meaningfully more natural and performant as an embedded document tree than as a normalized `courses`/`sections`/`lessons` three-table JOIN reconstructed on every Course Details/Course Player page load. Given the brief's explicit direction to evaluate MongoDB seriously (not reflexively default to Postgres), and given that this architecture's compensating disciplines (transactions + bounded-embedding + app-level checks) close the realistic integrity gap for an MVP of this size, MongoDB is the better fit *for this specific product shape*, not a default preference for document databases in general.

### Option C — Both (Postgres for relational data, MongoDB for course content)

**Why not:** a polyglot-persistence split is a legitimate pattern at larger scale, but for an MVP-sized system it roughly doubles operational surface (two database connections, two backup/migration stories, two sets of driver code) for a integrity benefit that transactions + discipline already capture. This is exactly the kind of premature complexity the brief asks to avoid; noted as a plausible **future evolution**, not adopted now.

## Consequences

- Every collection design in [`DATABASE_MODEL.md`](../DATABASE_MODEL.md) states its embed-vs-reference decision explicitly, with the document-growth reasoning shown, not left implicit.
- Multi-document transactions are used at the specific named points above — engineers implementing those flows must use them, not "happy path, add transactions later" (a mid-project retrofit of transactions is real risk to avoid).
- Text search for course discovery uses MongoDB's built-in `$text` index (§ [`DATABASE_MODEL.md`](../DATABASE_MODEL.md)) rather than a separate search engine — sufficient at MVP catalog scale.

## Migration Path

If Mentora's catalog and user base ever grow to a scale where relational integrity gaps or complex cross-entity reporting (e.g. a real BI/analytics need) become a genuine pain point, the `progress`/`enrollment`/`certificates` collections are the most likely candidates to migrate to a relational store first (Option C above), since course-content itself will remain a good document fit indefinitely. This is a realistic, non-rewrite migration path because those collections are already accessed only through repository interfaces in both the backend and `shared` KMP module.

**OPTIONAL FUTURE EVOLUTION — NOT PART OF CURRENT MVP IMPLEMENTATION:** should Mentora ever move beyond local-demo scope ([ADR-012](./ADR-012-local-demo-scope.md)), swapping the local MongoDB Community instance for a managed **MongoDB Atlas** tier is a `MONGODB_URI` configuration change only — the driver, data model, and every repository implementation are identical against either target. No application code, collection design, or transaction usage described in this ADR or [`DATABASE_MODEL.md`](../DATABASE_MODEL.md) changes.
