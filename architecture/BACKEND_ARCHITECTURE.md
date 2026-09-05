# Mentora — Backend Architecture

Kotlin + Ktor, modular monolith ([ADR-003](./adr/ADR-003-backend-architecture-style.md)). This file defines module boundaries, the internal layering every module follows, and the request lifecycle.

---

## 1. Modules (Bounded Contexts)

One package per module, under `com.mentora.backend.<module>` ([`REPOSITORY_STRUCTURE.md § 2`](./REPOSITORY_STRUCTURE.md)):

| Module | Owns | Primary MongoDB collections |
|---|---|---|
| `auth` | Registration, login, logout, token issuance/rotation/revocation | `users` (write path for credentials), `refreshTokens` |
| `users` | Profile read/update, role data | `users` |
| `courses` | Course CRUD, sections, lessons, publish/unpublish, curriculum reorder | `courses` |
| `categories` | Category CRUD | `categories` |
| `enrollment` | Demo checkout, enrollment creation/lookup | `demoPurchases`, `enrollments` |
| `progress` | Lesson/course progress, resume position, completion computation | `progress` |
| `quiz` | Quiz authoring, quiz-taking (correct-answer-stripped), grading, attempts | `quizzes`, `quizAttempts` |
| `learningpaths` | Learning path CRUD (admin-authored), follow/unfollow | `learningPaths`, `learningPathFollows` |
| `certificates` | Certificate issuance (triggered by `progress`), retrieval | `certificates` |
| `aitutor` | Conversation/message persistence, `AiProvider` interface + implementation, prompt construction, lesson-context resolution | `aiConversations`, `aiMessages` |
| `instructor` | Instructor-scoped read aggregations (dashboard stats: enrollment count, completion rate per owned course) | reads across `courses`, `enrollments`, `progress` — no collection of its own |
| `admin` | Admin-scoped read aggregations + moderation actions (unpublish) | reads across `courses`, `users`, `categories`; writes only `courses.status` |
| `media` | Presigned upload issuance, media metadata, signed playback URL issuance | `media` |
| `common` | Cross-cutting DTOs, error types, pagination helpers, Mongo codec registration | none (no collection ownership) |

**Rule:** a module never queries another module's collection directly. It calls the owning module's service interface. This is what makes [ADR-003](./adr/ADR-003-backend-architecture-style.md)'s "extract a module into its own service later" migration path real rather than aspirational — `instructor`'s dashboard stats, for example, depend on `courses`, `enrollment`, and `progress` through their service interfaces, not by reaching into `enrollments`/`progress` collections directly.

## 2. Internal Layering (every module)

```
routes/      Ktor route definitions — parse request, call service, map result/errors to HTTP response.
             No business logic here. No direct Mongo access here.
service/     Business logic, authorization checks (ownership, role), transaction boundaries,
             orchestration across this module's own repositories and other modules' service interfaces.
repository/  Mongo collection access only — query building via the typed driver's Filters/Updates builders,
             index-aware queries, mapping BSON documents to/from Kotlin domain data classes.
```

This mirrors the same separation the `shared` KMP module uses on the client side (use case → repository interface → networking), which is a deliberate consistency: an engineer moving between backend and client code recognizes the same layering.

## 3. Request Lifecycle

```
HTTP request
  → CallId plugin (assigns/propagates X-Request-Id)
  → CallLogging plugin (structured log line: method, path, status, duration, requestId)
  → CORS plugin (origin allowlist check)
  → Authentication plugin (verifies JWT from cookie or Authorization header; attaches Principal)
  → RequestValidation plugin (schema/shape validation of the request body)
  → RateLimit plugin (scoped per-route: strict on /auth/*, /ai-tutor/*; lenient elsewhere)
  → route handler (routes/ layer)
      → service layer: role/ownership authorization check
      → service layer: business logic, calling repository/other-module-service as needed
      → (transaction boundary opened here for multi-collection writes — see § 4)
  → StatusPages plugin (catches any thrown exception, maps to the fixed error envelope — see API_CONTRACT.md § 4)
  → response serialized (kotlinx.serialization)
```

Every plugin above is configured once, centrally, in `plugins/` ([`REPOSITORY_STRUCTURE.md § 2`](./REPOSITORY_STRUCTURE.md)) — no module reimplements auth-checking or error-mapping itself.

## 4. Transaction Boundaries

MongoDB multi-document transactions ([ADR-004](./adr/ADR-004-database-engine.md)) are opened in the **service layer**, never in a route handler or repository method in isolation, at exactly these points:

| Flow | Collections touched atomically |
|---|---|
| Demo checkout completion | `demoPurchases` (insert) + `enrollments` (insert, or no-op if already enrolled — idempotency check inside the same transaction) |
| Lesson completion | `progress` (update per-lesson + recalculated course percentage) + `certificates` (insert, only if this update crosses the course's completion threshold) |
| Quiz submission | `quizAttempts` (insert) + `progress` (update `quizPassed` flag) + `certificates` (insert, if this submission is what completes the course) |
| Course publish/unpublish | `courses` (status update) — single-document, transaction not required, listed for completeness |

Every other write in the system is a single-document operation and does not need a transaction — MongoDB's single-document writes are already atomic.

## 5. Dependency Injection — Koin

Koin modules are declared per feature module (`authModule`, `coursesModule`, ...) and combined in `Application.kt`. This mirrors the `shared` KMP module's own Koin usage ([`TECH_STACK.md § 4`](./TECH_STACK.md)) — one DI story across backend and mobile client, not two.

## 6. Configuration & Secrets

Typed configuration loaded from environment variables at startup (`config/` package), validated eagerly (the application fails fast at boot if a required secret/URL is missing, rather than failing on the first request that needs it). No secret is ever logged, including at DEBUG level — the logging plugin's field allowlist (see [`AUTH_SECURITY.md § 8`](./AUTH_SECURITY.md)) excludes anything from `config/`. Per-environment values (Mongo URI, JWT secret, object-storage credentials, AI provider key, CORS allowed origins) are documented per environment in [`DEPLOYMENT.md § 4`](./DEPLOYMENT.md).

## 7. Health & Correlation

- `GET /healthz` — checks MongoDB connectivity (a lightweight `ping` command), returns `200` with `{ status: "ok", mongo: "ok" }` or `503` with the failing dependency named. No auth required (used by the hosting platform's health checks).
- Every request/response log line and every error response's `meta` field carries the same `requestId` generated by the `CallId` plugin, so a user-reported issue ("I got an error at 3:04pm") can be traced to its exact log line without needing full request/response body logging.

## 8. Why Not More Modules / Why Not Fewer

The module list in § 1 maps close to 1:1 with the design system's own MVP feature grouping ([`../product/PRODUCT_SPEC.md § 7`](../product/PRODUCT_SPEC.md)) rather than being invented independently — `instructor` and `admin` are kept as thin aggregation modules (not full domains) because they don't own new data, only new *views* over existing data, which avoids the trap of duplicating `courses`/`enrollment`/`progress` logic under an "Instructor" or "Admin" label. `media` is its own module (not folded into `courses`) because upload/permission/signed-URL logic is a genuinely distinct concern reused by both `courses` (thumbnails) and `courses`' lessons (videos) — giving it its own module avoids two copies of presigned-URL logic.
