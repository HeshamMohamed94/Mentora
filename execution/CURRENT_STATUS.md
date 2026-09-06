# Mentora — Current Implementation Status

**Last updated:** 2026-09-06 (tasks 20 and 21 closed out and committed, in the same all-remaining-Phase-1-tasks session as 19/22/23 — see PHASE_HANDOFF.md for the full final write-up once task 23 lands)

---

## EXACT RESUME POINT

**Read this section first when resuming.**

- **Phase:** PHASE 1 — Backend Foundation & API — `IN_PROGRESS`
- **Current task:** Task 22 — Phase 1 quality gate verification — in progress (Claude-led, no delegation).
- **Next immediate action:** Continue/finish task 22's audit, then task 23 (`PHASE_HANDOFF.md` final write-up), then produce the "MENTORA IMPLEMENTATION — PHASE 1 REPORT" and STOP for the user's explicit approval before any Phase 2 work — per the user's explicit instruction this session to complete all remaining Phase 1 tasks without stopping in between.

### What is COMPLETE (committed, reviewed, gates green) — tasks 1–21

All of: execution continuity docs, backend Gradle scaffold, Ktor foundation (M1), Auth & RBAC (M2), Users module, Courses & Categories (M5 slice), Enrollment/Demo Checkout (M6 slice), Progress (M7 slice), Quiz (M8 slice), Certificates + completion-crossing wiring (M9 slice), Learning Paths (M10 slice), Media/local filesystem storage (M11 slice), Instructor aggregation endpoint (M11 tail), Admin aggregation endpoints (M12 slice), AI Tutor scaffold (M13 boundary only). Last commit: see `git log` on `main` — "Phase 1: AI Tutor scaffold (M13 boundary only)".

Task 15 (Media): Codex-authored, Claude-reviewed closely. Implements the `MediaStorage` abstraction + local-filesystem impl with canonical-path-escape protection, streaming upload with per-kind size caps (images 5 MB / video 500 MB, aborted-and-cleaned-up mid-stream on overflow, never buffering the full file), public thumbnail/avatar serving, and a gated lesson-video playback flow (a second, purpose-scoped 5-minute JWT, verified by hand — not a second Authentication provider — plus Ktor's `PartialContent` plugin for range-request seeking). Ownership/enrollment authorization reuses the already-exposed `CourseService.requireOwnership`/`EnrollmentService.requireEnrollment` (no new cross-module surface). One correctly-identified and fixed implementation-time conflict: `media.ownerRefId` had to become a `String` (not the originally-assumed `ObjectId`) because `courses` generates lesson ids as UUID strings, not Mongo ObjectIds — see `DECISIONS_LOG.md` D21/D25 for the full reasoning, D22–D24 for the size-limit/token/status-lifecycle decisions, D26 for a real Phase-2-relevant contract note (multipart form fields must precede the file part in the upload request). Verified independently: re-ran `./gradlew test --rerun` myself (not just trusting Codex's self-report) — fresh, non-cached run against live MongoDB, 29 tests/9 suites, 0 failures/errors. Reviewed the diff directly (not just the test outcome): storage-key generation is always server-generated, never client-filename-derived; content-type/kind cross-validation happens before any disk write; `courseId` additive field correctly resolves both the upload-ownership and playback-enrollment checks without a new reverse lookup; public `/file` route correctly 404s for `lessonVideo`-kind media.

Task 16 (Instructor aggregation endpoint): Codex-authored, Claude-reviewed. Implements `GET /api/v1/instructor/dashboard` per `architecture/BACKEND_ARCHITECTURE.md § 3`'s explicit design for this module (no collection of its own — reads directly across `courses`/`enrollments`/`progress`, the one documented exception to the usual reuse-another-module's-service-method boundary rule): owner-scoped `stats` (`totalCourses`, `publishedCount`, `totalEnrollments`) plus a per-course list (`enrollmentCount`, `completionRate` — defined as the average of `progress.completionPercent` across that course's progress documents, `0` when there are none). Codex's dispatch was cut off mid-run by an OpenAI usage-limit error, after writing the module and test but before self-verifying — Claude ran the gates independently instead of waiting for quota reset, found and fixed one test-fixture-only bug (JWT claim name mismatch, `sub` vs. the codebase's actual `userId` claim), and re-verified clean. See `DECISIONS_LOG.md` D27 for the full account. All gates green (31 tests, 10 suites).

Task 18 (AI Tutor scaffold, M13 boundary only): Codex-authored, Claude-reviewed. Implements `GET /api/v1/ai-tutor/conversation` and `POST /api/v1/ai-tutor/conversation/messages`, the `AiProvider` interface, and a `StubAiProvider` binding (Phase 1 streams fixed placeholder text, genuinely chunked over the wire — never a real LLM completion; Phase 6 swaps only the Koin binding). Real enrollment gate, real persistence (`aiConversations`/`aiMessages`, both newly-owned collections with their own indexes), real per-minute-and-daily rate limiting (both now `AppConfig`-driven, closing a gap where only a hardcoded per-minute cap existed). One implementation-time gap resolved before dispatch (not discovered mid-review this time): lesson ids have no reverse lookup to their owning course, so the request pairs `courseId` with `lessonContextId` — the same fix already established for `media`'s `lessonVideo` upload (D21), applied here proactively. See `DECISIONS_LOG.md` D30 for this and three other implementation-time specifics (system prompt scope, streaming wire format, rate-limit config). All gates green (41 tests, 12 suites) — independently re-verified, not just Codex's self-report.

**Also fixed during task 18's independent verification, as its own separate commit:** a pre-existing flaky test in `MediaIntegrationTest` (task 15), unrelated to `aitutor` — see `DECISIONS_LOG.md` D31.

Task 19 (backend test suite completeness review, M15 portion): a fork-based audit found two concrete,
bounded gaps against `architecture/TESTING_STRATEGY.md § 1` (not a rewrite — every module already had a
passing integration suite): (1) zero unit tests existed anywhere despite the strategy doc naming three
explicit examples — added `QuizServiceTest.kt`/`CourseServiceTest.kt`/`ProgressServiceTest.kt` (MockK,
no live Mongo); (2) `POST /courses/{id}/unpublish` had never been called by any test despite being named
in two milestones' roadmap acceptance criteria — added coverage (owner/Admin/wrong-role/enrolled-student-
keeps-access) plus three other never-exercised courses routes (section rename/delete, lesson delete, both
asserting order re-compaction). Zero production bugs found — every new test asserts already-correct
behavior. See `DECISIONS_LOG.md` D32. All gates green (64 tests, 15 suites), independently re-verified.

**Also found and fixed during task 22's audit, as its own separate commit:** `POST /media/uploads` was
missing the CSRF header check every other mutating route already has — a genuine, real security gap, not
a pre-existing documented limitation. See `DECISIONS_LOG.md` D33.

Task 20 (seed/demo data script, M16 portion): a backend-native Kotlin entry point (`SeedData.kt`) + Gradle
`seedDemoData` task — not `infra/docker/mongo-init` (D34: no Docker/infra exists in Phase 1 at all, per
D1). Reuses the real service layer for every entity it can (register/category/course/media/quiz), with
two narrow justified exceptions (Instructor/Admin role-flip, Learning Path insert — both because no other
write path exists for them, by design). Idempotent, verified by running it twice. Seeds 6 demo accounts
(`@mentora.dev` / `MentoraDemo1`), 4 categories, 6 courses (4 published/2 draft, real en/ar content), a
quiz, a Learning Path. Independently re-verified beyond the self-report: `mongosh` count spot-check, and a
real end-to-end check — started the actual server, logged in over HTTP as the seeded admin account.

Task 21 (local run instructions, M16 portion): `backend/README.md` + `backend/.env.example`, Claude-authored
directly (pure documentation, not delegated). Covers the one-time MongoDB replica-set conversion (D12,
previously undocumented anywhere outside the decisions log), env setup, build/test/run/seed commands, and
the exact demo credentials task 20 produced.

### What is PARTIAL / uncommitted

Nothing. Tasks 22-23 are in progress in this same session — see "Current task" above for exactly where to
pick up if interrupted.

### Exact next steps on resume

1. Task 22 — Phase 1 quality gate verification: Claude-led full audit (fresh build/test, RBAC/security
   spot-checks, module-boundary compliance, no real-payment-path re-confirmation, known-limitations
   compilation from the decisions log) — no delegation, per the Master Plan's Claude-led final-polish rule.
   Already substantially done inline during tasks 19-21's review passes (found/fixed D32's test gaps and
   D33's CSRF gap); what remains is compiling the final known-issues list and a last fresh full-suite run.
2. Task 23 — `PHASE_HANDOFF.md` final write-up (mark Phase 1 `COMPLETE` in the fixed structure the file's
   header already specifies) — Claude-authored.
3. After task 23: produce the "MENTORA IMPLEMENTATION — PHASE 1 REPORT" the user asked for, then STOP and
   wait for explicit approval. Do not start Phase 2 under any circumstances before that approval.

---

Allowed phase states: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETE`.

| Phase | Status | Notes |
|---|---|---|
| PHASE 1 — Backend Foundation & API | **IN_PROGRESS** | Started 2026-09-05. See below for task breakdown. |
| PHASE 2 — Website | NOT_STARTED | Blocked on Phase 1 COMPLETE + HANDOFF COMPLETE. |
| PHASE 3 — KMP Shared Mobile Core | NOT_STARTED | Blocked on Phase 1. |
| PHASE 4 — Android | NOT_STARTED | Blocked on Phase 3. |
| PHASE 5 — iOS | NOT_STARTED | Blocked on Phase 3. |
| PHASE 6 — AI Tutor Integration | NOT_STARTED | Blocked on Phases 2, 4, 5 (client shells) + Phase 1 (aitutor scaffold). |
| PHASE 7 — Full Integration | NOT_STARTED | Blocked on all prior phases. |
| PHASE 8 — QA, Polish & Portfolio Demo | NOT_STARTED | Blocked on Phase 7. |

---

## PHASE 1 — Task Breakdown (in progress)

| # | Task | Status |
|---|---|---|
| 1 | Read locked docs (architecture, product, ux, design-system) | DONE |
| 2 | Confirm git state (branch `main`, clean tree) | DONE |
| 3 | Create `execution/` continuity documents | DONE |
| 4 | Audit local toolchain (JDK, MongoDB, Gradle, Codex) | DONE |
| 5 | `backend/` Gradle project scaffold (M0 slice) | DONE — Claude-authored (Gradle Kotlin DSL, wrapper 8.11, package tree per REPOSITORY_STRUCTURE.md) |
| 6 | Ktor plugin stack, config, `/healthz` (M1) | DONE — common/config foundation Claude-authored; plugin wiring + Mongo connectivity Codex-authored (run 01), Claude-reviewed, fixed 2 build-breaking issues (jbcrypt version, koin/Ktor 3 incompatibility), verified `./gradlew build`/`test` green. Committed `18040a0`. |
| 7 | Auth & RBAC (M2) | DONE — Codex-authored (run 02), Claude-reviewed line-by-line (security-critical per roadmap): reuse-detection breach path, timing-safe login response (dummy BCrypt hash), atomic conditional revocation, SHA-256 refresh-token hashing, CSRF enforcement. Claude extracted a duplicated CSRF check into common/Csrf.kt. All gates green. |
| 8 | Users module | DONE — implemented alongside M2 (GET/PATCH /users/me), Claude-reviewed |
| 9 | Courses/Categories module (M5 slice) | DONE — Codex-authored (run 03), Claude-reviewed: verified published-only filter can't be bypassed via query param, draft visibility returns 404 not 403, ownership/role gating correct at route layer, category counters atomic ($inc), nested sections/lessons round-trip through BSON codec. All gates green. |
| 10 | Enrollment / demo checkout (M6 slice) | DONE — Codex-authored (run 04), Claude-reviewed (no-real-payment-path boundary per roadmap): verified zero payment vocabulary via independent grep, real MongoDB transaction with correct abort-on-exception, idempotent double-completion proven both sequentially and via duplicate-key fallback. One known minor limitation documented (D14, true-concurrency edge case, non-blocking). All gates green. |
| 11 | Progress module (M7 slice) | DONE — Codex-authored (run 05), Claude-reviewed: verified enrollment gate, idempotent lesson-completion map, correct integer percentage math, lazy find-or-create via atomic upsert, and that quizPassed/courseCompletedAt stay null as scoped (D15). All gates green. |
| 12 | Quiz module (M8 slice) | DONE — Codex-authored (run 06), Claude-reviewed (isCorrect-stripping per roadmap): confirmed `StudentQuizOption` is structurally a distinct type with no `isCorrect` property (not a serialization-omitted field), verified via raw-JSON substring test; grading math, editor validation, and the authorized `progress.setQuizPassed` addition all verified. Certificate/completion-crossing wiring deferred to next task (D16). All gates green. |
| 13 | Certificates module + course-completion wiring (M9 slice + D15/D16/D17 follow-through) | DONE — Codex-authored (run 07), Claude-reviewed closely: independently verified via grep that progress/quiz services have zero dependency on certificates (route-layer-only wiring, no circular dependency), confirmed the eligibility check, the reversible human-readable public ID, and order-independence (quiz-first and lessons-first both correctly trigger issuance) with genuine direct-collection-count proof of no double-issuance. All gates green. |
| 14 | Learning paths module (M10 slice) | DONE — Codex-authored (run 08c, resumed after a usage-limit pause and a machine-shutdown pause, D18/D19), Claude-reviewed: production code correct on first read (order-preserving course resolution, dangling-course omission from detail + progress denominator, idempotent follow/unfollow via unique index + upsert, guest-safe optional auth). Found and fixed 3 test-only bugs (ambiguous helper overload emptying register/login bodies, BSON date-type mismatch in a raw-seed fixture, a null-encoding assertion mismatched to the app's `explicitNulls = false` config) — see D20. All gates green (25 tests, 8 classes). |
| 15 | Media module — local filesystem storage (M11 slice) | DONE — Codex-authored, Claude-reviewed: verified the `MediaStorage` abstraction's path-escape defense, streaming size-cap-with-cleanup, server-generated (never client-derived) storage keys, public-thumbnail vs. gated-lesson-video route split, the two-JWT (session vs. purpose-scoped playback) design, and that ownership/enrollment checks reuse existing cross-module methods with zero new surface. One correctly-caught implementation conflict (lesson ids are UUID strings, not ObjectIds — D25). Independently re-ran the test suite myself (fresh, non-cached, live MongoDB) rather than trusting the self-report. All gates green (29 tests, 9 classes). |
| 16 | Instructor aggregation endpoints (M11 slice) | DONE — Codex-authored, Claude-reviewed: `GET /api/v1/instructor/dashboard` reads directly across `courses`/`enrollments`/`progress` per the module's documented no-own-collection design; verified owner-scoping (another Instructor's courses never leak in), stats math, and the completion-rate definition (average `progress.completionPercent` per course). Codex's dispatch was interrupted by an OpenAI usage-limit error before it could self-verify; Claude ran the gates independently, found and fixed one test-fixture-only bug (JWT claim name), re-verified green — see D27. All gates green (31 tests, 10 suites). |
| 17 | Admin aggregation endpoints (M12 slice) | DONE — Codex-authored, Claude-reviewed: `GET /api/v1/admin/{dashboard,courses,users,instructors}` reading directly across `courses`/`users`/`enrollments` per the module's no-own-collection design (same pattern as `instructor`, task 16). `users`/`instructors` are disjoint role-scoped lists (`role == student` / `role == instructor`, never "all accounts"); `q` search on both escapes untrusted input via `Pattern.quote` before building the Mongo regex filter. Reused the already-implemented `categories` CRUD and `POST /courses/{id}/unpublish` (already Admin-capable) rather than duplicating either. Codex's dispatch hit the same OpenAI usage-limit wall as task 16 twice before a third immediate retry succeeded cleanly — see D28/D29. Independently re-verified: fresh `gradlew.bat test --rerun-tasks` (35 tests, 11 suites, 0 failures) and `gradlew.bat build`, plus a full diff read. |
| 18 | AI Tutor scaffold + `AiProvider` interface + stub impl (M13 boundary only) | DONE — Codex-authored, Claude-reviewed: `GET /ai-tutor/conversation` + `POST /ai-tutor/conversation/messages`, real persistence/enrollment-gate/rate-limiting, bound to a stub `AiProvider` (placeholder text, genuinely chunked streaming, no real LLM call — Phase 6 swaps only the Koin binding). `courseId`+`lessonContextId` paired in the request (no lesson→course reverse lookup exists — same fix as media's D21). Added both a per-minute and a per-day rate cap, both `AppConfig`-driven (previously only a hardcoded per-minute cap existed). See D30. Independently re-verified (41 tests, 12 suites, 0 failures) rather than trusting the self-report; that re-verification also surfaced and fixed an unrelated pre-existing flaky test in `MediaIntegrationTest` (task 15) — see D31, landed as its own separate commit. |
| 19 | Backend test suite (M15 portion) | DONE — Codex-authored, Claude-reviewed: added the previously-missing unit-test layer (MockK, quiz scoring/publish-validation/completion-percentage) and closed 4 never-exercised `courses` routes (`unpublish` — the highest-priority gap, plus section rename/delete and lesson delete). Zero production bugs found. See D32. 64 tests, 15 suites, independently re-verified. |
| 20 | Seed data script | DONE — Codex-authored, Claude-reviewed: backend-native `SeedData.kt` + Gradle `seedDemoData` task (not `infra/docker/mongo-init` — see D34), idempotent, reuses the real service layer. Seeds 6 accounts/4 categories/6 courses (4 published/2 draft, en+ar)/1 quiz/1 Learning Path. Independently verified via `mongosh` + a real HTTP login as the seeded admin. |
| 21 | Local run instructions (M16 portion) | DONE — Claude-authored directly (documentation, not delegated): `backend/README.md` + `backend/.env.example`, covering the MongoDB replica-set one-time setup (D12), env config, build/test/run/seed commands, and the seed script's exact demo credentials. |
| 22 | Phase 1 quality gate verification | NOT_STARTED |
| 23 | `PHASE_HANDOFF.md` write-up | NOT_STARTED |

## Immediate Next Action

Task 22 — Phase 1 quality gate verification, then task 23 (see "Exact next steps on resume" above) — the user has instructed completing all remaining Phase 1 tasks in one session without stopping for approval between them, then a final Phase 1 report and a stop for explicit approval before Phase 2.
