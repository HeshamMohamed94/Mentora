# ADR-003: Backend Architecture Style — Modular Monolith

**Status:** Locked
**Date:** 2026-09-04

## Decision

The backend is **one deployable Ktor application**, internally organized into clearly bounded packages/modules (`auth`, `users`, `courses`, `categories`, `enrollment`, `progress`, `quiz`, `learningPaths`, `certificates`, `aiTutor`, `instructor`, `admin`, `media`) — a **modular monolith**, not a microservices architecture.

## Context

The brief is explicit: "Do not create unnecessary microservices. Prefer a modular monolith for MVP unless there is a strong reason otherwise." Mentora's MVP has a small, well-understood domain (29 screens, one team), a single relatively low-traffic deployment target (portfolio/demo scale, not production SaaS scale), and every feature area shares the same core entities (`User`, `Course`, `Enrollment`) — the textbook case where microservices add coordination and operational cost without a corresponding scaling or team-boundary need.

## Options Considered

### Option A — Modular monolith (chosen)

One Ktor process, one deployment, one database connection pool, internally organized by bounded-context packages with disciplined internal boundaries (each module exposes its public API via interfaces/DTOs; modules don't reach into each other's internal repository/collection types directly — see [`BACKEND_ARCHITECTURE.md § 2`](../BACKEND_ARCHITECTURE.md)).

### Option B — Microservices (one service per domain area)

**Why not:** would mean 13 separately deployed services for a system with one small team and no independent-scaling requirement (nothing in Mentora's traffic profile suggests, e.g., `quiz` needs to scale independently of `courses`). Each service would need its own health checks, its own CI/CD pipeline, inter-service network calls (with their own retry/timeout/circuit-breaker concerns) for what are currently simple in-process function calls, and its own deployment cost on a portfolio budget. The AI Tutor's need to read enrollment + lesson content to build context (see [`AI_TUTOR_ARCHITECTURE.md`](../AI_TUTOR_ARCHITECTURE.md)) would become a network call with its own failure modes instead of a direct service-layer call — pure overhead at this scale. Microservices would also fragment the single MongoDB transaction Mentora relies on for checkout→enrollment and lesson-complete→progress→certificate-eligibility (see [`DATABASE_MODEL.md`](../DATABASE_MODEL.md)) into a distributed-transaction/saga problem that doesn't need to exist yet.

### Option C — Single-file/unstructured monolith (no internal module boundaries)

**Why not:** would satisfy "one deployment" but sacrifice the maintainability and testability a reviewer (and a future Codex implementer working milestone-by-milestone) needs — routes, services, and Mongo collections for `quiz` and `courses` would tangle together with no enforced boundary, making it hard to reason about ownership or safely evolve one area without touching another.

## Consequences

- One Gradle module (`backend/`) builds one JAR/container image, deployed as one unit (see [`DEPLOYMENT.md`](../DEPLOYMENT.md)).
- Internal module boundaries are enforced by convention and package structure now; if the team or scale ever genuinely require it, a specific module (most plausibly `aiTutor`, being the most compute/cost-sensitive) is the natural first candidate to extract into its own service — because it already exposes a clean interface boundary from day one, per [`BACKEND_ARCHITECTURE.md`](../BACKEND_ARCHITECTURE.md).
- All modules share one MongoDB connection and can use multi-document transactions across collections they own, which several critical flows (checkout, lesson completion) depend on.

## Migration Path

Extracting a module into its own deployable service later is a matter of: (1) the module already communicates with others only through interfaces/DTOs, never shared mutable state, so its "seams" already exist; (2) replace in-process calls to that module's interface with an HTTP/gRPC client implementing the same interface; (3) stand up the extracted service with its own deployment. This is why the internal module boundaries in [`BACKEND_ARCHITECTURE.md`](../BACKEND_ARCHITECTURE.md) are treated as a real architectural discipline now, not a cosmetic package layout — they're what makes this migration path realistic later without a rewrite.
