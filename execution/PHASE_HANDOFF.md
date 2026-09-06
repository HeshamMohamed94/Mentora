# Mentora — Phase Handoff Log

Each completed phase gets one dated section below, appended (never overwritten), in the fixed structure required by the implementation supervision rules: Phase status, what was implemented, files/modules created, API/contracts produced, database changes, tests/verification performed, known limitations, decisions made, what the next phase depends on, what the next phase must NOT redo, and the git commit/state reference.

---

## PHASE 1 — Backend Foundation & API

**Status:** COMPLETE (started 2026-09-05, completed 2026-09-06) — pending the user's explicit approval of the Phase 1 report before Phase 2 begins.

### 1. What was implemented

The full backend for Milestones M0–M2, M5–M13 (backend slices), M15, and M16 (backend portion) of `architecture/IMPLEMENTATION_ROADMAP.md`, per `execution/MASTER_IMPLEMENTATION_PLAN.md`'s Phase 1 scope. A Ktor/Kotlin backend with 13 feature modules plus shared infrastructure, running against a local MongoDB replica set, with a real (not stubbed) implementation of every backend behavior except the one deliberately-scoped exception (the AI Tutor's LLM call, explicitly deferred to Phase 6 per the user's own instruction — see D4).

Tasks 1–23 all complete: continuity docs, toolchain audit, Gradle scaffold, Ktor foundation, Auth & RBAC, Users, Courses & Categories, Enrollment/Demo Checkout, Progress, Quiz, Certificates, Learning Paths, Media, Instructor aggregation, Admin aggregation, AI Tutor scaffold, backend test-suite completeness review, seed/demo data script, local run instructions, this quality-gate pass, and this write-up.

### 2. Files/modules created

13 feature modules under `backend/src/main/kotlin/com/mentora/backend/`: `auth`, `users`, `categories`, `courses`, `enrollment`, `progress`, `quiz`, `certificates`, `learningpaths`, `media`, `instructor`, `admin`, `aitutor` — each with its own `repository`/`service`/`routes` package (plus a module-level Koin file), consistent with `architecture/BACKEND_ARCHITECTURE.md`'s module-boundary design. Shared infrastructure: `common` (error taxonomy, pagination, CSRF, principal/role helpers, response envelope), `config` (typed, fail-fast, env-driven `AppConfig`), `plugins` (CORS, security/JWT auth, rate limiting, request validation, status pages, monitoring, database lifecycle, health check). One top-level `SeedData.kt` entry point. 15 test files under `backend/src/test/kotlin/com/mentora/backend/` (12 integration suites, 3 pure-unit-test suites). `backend/README.md` and `backend/.env.example` (new this phase — local run instructions did not exist before task 21).

**Git:** 19 commits on `main` since the initial project-setup commit (`c3a2ed5`), touching 115 files under `backend/` (8,155 insertions), plus this `execution/` continuity layer. Full list: `git log --oneline c3a2ed5..HEAD`. Working tree is clean.

### 3. API/contracts produced

The full `architecture/API_CONTRACT.md` surface for every Phase 1 module: auth (register/login/logout/refresh), users (profile read/update), categories (CRUD), courses (browse/CRUD/sections/lessons/publish/unpublish), enrollment (checkout preview/complete, list), progress (get/complete-lesson/position), quiz (student view/editor/submit/latest), certificates (list/get), learning paths (list/get/follow/unfollow), media (upload/file/playback-url/stream), instructor dashboard, admin (dashboard/courses/users/instructors), AI Tutor (conversation/messages). The as-built shape of every one of these — including every place it differs in a small, deliberate way from the original architecture docs — is recorded in `execution/INTEGRATION_CONTRACT.md`, which is the authoritative as-built reference for Phase 2+ client work. Every deviation there is cross-referenced to the `DECISIONS_LOG.md` entry that explains why.

### 4. Database changes

MongoDB collections now live and indexed: `users`, `refreshTokens`, `categories`, `courses`, `enrollments`, `demoPurchases`, `progress`, `quizzes`, `quizAttempts`, `certificates`, `learningPaths`, `learningPathFollows`, `media`, `aiConversations`, `aiMessages` — matching `architecture/DATABASE_MODEL.md` exactly, with the one intentional, tracked exception (`media.ownerRefId` typed `String` not `ObjectId`, D25). `instructor` and `admin` own no collections by design (read-aggregation modules, D-precedent in D16/D28). The local MongoDB instance was converted from standalone to a single-node replica set (`rs0`) to support the real multi-document transactions this design requires (D12) — this is a one-time environment setup step, now documented in `backend/README.md`, not a schema change.

### 5. Tests/verification performed

**Automated:** 65 tests across 15 suites (12 integration — Ktor `testApplication` + live local MongoDB; 3 pure unit — JUnit5+MockK, no live Mongo), 0 failures, 0 errors, verified with a from-scratch clean build (`gradlew.bat clean` then `gradlew.bat test build --rerun-tasks`) as the final gate for this report — not a cached/incremental result. Every module's suite was independently re-verified by Claude at commit time (never trusting Codex's self-report alone); this pass additionally found and fixed one pre-existing flaky test (D31) and one genuine security gap (D33, below) that had escaped every prior review.

**Manual/live:** started the actual backend process and exercised it over real HTTP — `/healthz` returns `{"status":"ok","mongo":"ok"}`; logged in as a seeded demo account and received a valid access token with the correct role.

**Security-specific:** demo-checkout idempotency (real MongoDB transaction + unique-index fallback, proven via duplicate requests), the lesson-completion→progress→certificate transaction (both orderings), the quiz `isCorrect`-stripping projection (raw-JSON substring check, not just a null-field check), Auth & RBAC (reuse-detection breach path, timing-safe login, CSRF enforcement on every mutating route — including the one gap found and closed this session), a full grep-based sweep confirming zero payment vocabulary anywhere in the backend, zero hardcoded secrets, zero stray debug logging, `.env` never committed.

### 6. Known limitations

None of these block Phase 1's quality gate — each is a deliberate, documented, non-blocking simplification appropriate to this phase's scope, not an undetected defect:

1. **Testcontainers not used** (D5) — this dev machine has no Docker; integration tests run against the local native MongoDB service instead, with a disposable per-test-class database. A deviation from `TESTING_STRATEGY.md`'s stated *tooling*, not from its *intent* (real Mongo behavior is still exercised, not mocked).
2. **Course media reference fields are format-only validated** (D10) — `publish()` checks `thumbnailMediaId`/lesson `videoMediaId` are present and well-formed, not that a real, `ready` media document exists for that id. Still open even after the `media` module shipped (task 15 never added this cross-check). Low risk at this scale; if ever wanted, it's an additive `media`-service read call from `courses`, not a redesign.
3. **`isEnrolled` on course responses was deferred and never implemented** (D11) — a client currently determines enrollment by cross-referencing `GET /enrollments` itself rather than reading a flag directly on `GET /courses`/`GET /courses/{id}`. Worth a decision at Phase 2 UI-integration time (add the field, or keep the client-side join).
4. **A known, minor, non-blocking demo-checkout concurrency edge case** (D14) — a true simultaneous duplicate checkout request can surface a transient MongoDB transaction error that isn't auto-retried. The idempotency guarantee itself (never two enrollments) holds either way; the additive fix (retry once on `TransientTransactionError`) is documented but not implemented.
5. **Rate-limit caller-identity granularity** — `AUTH_SECURITY.md § 7`'s illustrative table describes `/auth/login`/`/auth/register` as "per-IP and per-email"; the actual implementation uses Ktor's `RateLimit` plugin with no custom `requestKey`, i.e. its default keying, not an explicitly-verified per-IP-and-per-email scheme. Zero public exposure exists at this phase (local-only MVP, `ADR-012`), so this carries no current risk — flagged here so it gets an explicit look before any future phase changes that exposure.
6. **AI Tutor is a real, functional boundary bound to a stub provider** (D4/D30, by explicit user instruction, not a defect) — auth, rate-limiting, persistence, and the enrollment gate are all genuinely live; the response content itself is fixed placeholder text until Phase 6 implements the real Anthropic Claude API call. No route/schema/contract change is expected then, only the `AiProvider` Koin binding.
7. **Seed script media is synthetic byte content**, not real video files (D34) — correct and sufficient for a backend-only phase with no client yet to play one; Phase 2/7's "Local Demo" readiness (real-looking thumbnails and at least one real demo video, per `DEPLOYMENT.md § 2`) is explicitly a later concern, not part of this phase's M16 slice.

### 7. Decisions made

34 implementation-time decisions recorded in `execution/DECISIONS_LOG.md` (D1–D34), covering local-environment facts (native MongoDB, no Docker, the replica-set conversion), every implementation-time gap-fill between an architecture doc's prose and what the existing codebase could actually do (D9-D11, D21, D25, D28, D30, D34), every security-relevant finding and fix (D27's JWT-claim bug, D31's flaky test, D33's CSRF gap), and every Codex-dispatch process note (D3, D27, D29). None reopens or contradicts a locked architecture decision — every one is either a local-environment fact or an implementation-time detail the architecture correctly left unspecified at the ADR level.

### 8. What Phase 2 (and later phases) depend on

- `execution/INTEGRATION_CONTRACT.md` is the authoritative as-built API reference — read it before `architecture/API_CONTRACT.md` where the two differ (every difference is explained there and cross-referenced to a decision).
- The backend runs locally per `backend/README.md` — MongoDB replica-set conversion, `.env` setup, `seedDemoData` for realistic content, all four demo-account roles ready to authenticate against from Phase 2's Web client.
- `CourseService.requireOwnership`/`EnrollmentService.requireEnrollment` are the established cross-module read-reuse pattern (used by `media`, `quiz`, `aitutor`) — Phase 2 client work doesn't need this, but any *further backend* work should follow the same reuse pattern rather than a new direct collection query.
- The `AiProvider` interface (D4/ADR-009) is ready for Phase 6 to bind a real implementation with zero route/schema change.

### 9. What later phases must NOT redo

- Do not rebuild any of the 13 backend modules, their routes, or their persistence — they are complete, tested, and reviewed. Phase 2 consumes them as-is.
- Do not re-litigate the AI Tutor's Phase 1 stub — Phase 6 only swaps the Koin binding.
- Do not reintroduce a real-payment code path anywhere — explicitly, permanently out of scope (verified zero payment vocabulary this session, per `product/DEMO_PAYMENT_FLOW.md`).
- Do not build `infra/docker/mongo-init` reflexively because `REPOSITORY_STRUCTURE.md` mentions it — Phase 1's `seedDemoData` Gradle task is the working equivalent for as long as Docker isn't otherwise needed (D34); revisit only if/when Docker genuinely enters the stack (e.g. CI Testcontainers).

### 10. Git commit/state reference

`main` branch, working tree clean. Phase 1 spans commits `18040a0`..`bad0d06` (19 commits; `git log --oneline c3a2ed5..HEAD` for the full list with messages). The design-system/product/ux/architecture directories were never modified during Phase 1 (verified via `git log --name-only` over the full commit range) — every locked doc is exactly as it was at Phase 1's start, consulted and adapted-to, never edited. No `web/`/`mobile/`/`infra/` directory exists — Phase 2+ has not been started.
