# Mentora — Current Implementation Status

**Last updated:** 2026-09-06 (Phase 1 approved by the user; Phase 2 — Website — kicked off, task 1 (foundation scaffold) in progress)

---

## EXACT RESUME POINT

**Read this section first when resuming.**

- **Phase:** PHASE 2 — Website — `IN_PROGRESS`. Phase 1 is `COMPLETE` and was explicitly approved by the user on 2026-09-06.
- **Current task:** Tasks 1-4 (foundation, auth screens, public discovery, demo checkout/purchase success) are DONE and verified end-to-end (real browser + real backend, en+ar). Task 5 (Student Dashboard + My Learning) is next. Note: there is still no dedicated authenticated top-nav/shell component (`/app` pages render bare `<main>`) — see D39; whichever task builds it should also give the Landing page's cards a `basePath` the way task 4 did for Explore/Learning Paths.
- **What exists in `web/` right now (tasks 1–2):**
  - Next.js 15 App Router + TypeScript, `[locale]` routing (`en`/`ar`, `localePrefix: "always"`) via `next-intl`, `src/middleware.ts` combining locale detection with the `/app`, `/instructor`, `/admin` auth gate (redirects to `/login?redirect=<intent>` when the `mentora_refresh_token` cookie is absent).
  - `tools/token-pipeline/generate.js` (plain Node, see D35) generates `web/styles/tokens.css` (semantic + component-layer CSS custom properties, light/dark via `[data-theme]` + `prefers-color-scheme`), `web/styles/tailwind-theme.css` (Tailwind v4 `@theme inline` color mapping + custom `tablet`/`desktop`/`large-desktop` breakpoints), and `web/src/lib/design-tokens.generated.ts`. Re-run `npm run generate-tokens` (from `web/`) after any `design-tokens.json` change.
  - `web/src/app/components.css` — hand-authored Button (primary/secondary/tonal/text)/TextField CSS classes consuming only generated tokens; `web/src/components/ui/` has the React wrappers.
  - `web/src/lib/api/client.ts` — typed fetch client: CSRF header on writes, response-envelope unwrapping, 401→refresh→retry, `mentora:force-logout` event on unrecoverable 401. `web/src/lib/auth/` — `login`/`register`/`logout`/`getCurrentUser` + `useCurrentUser` TanStack Query hook. See D36: login/register's embedded `user` is a narrower shape than `/users/me`'s.
  - Real pages: Landing (`(public)/page.tsx`, placeholder hero only), Login, Register (both fully functional — react-hook-form + zod, wired to the live backend), and a minimal `/app` Dashboard shell (proves the session loop; not the real Dashboard screen from `SCREEN_INVENTORY.md § 8`, that's task 5).
  - Verified live: register → cookies set → `/app` accessible → `/users/me` succeeds → logout → cookies cleared → `/app` redirects to `/login?redirect=%2Fen%2Fapp`. Verified in an actual Chrome tab too (screenshots), both `en` (LTR) and `ar` (RTL, mirrored layout, IBM Plex Sans Arabic font) — see chat history for screenshots.
  - Gates green: `npm run typecheck`, `npm run lint`, `npm run lint:logical-properties`, `npm run build` (both locales prerender).
  - Backend is running locally for this work (`backend/.env` created with a generated `JWT_SIGNING_SECRET`; MongoDB replica set already existed from Phase 1). A test account exists in the dev DB: `phase2tester@example.com` / `MentoraDemo1`.
- **Not yet built (do not assume these exist):** Sidebar (authenticated shell chrome), Explore/Course Details/Learning Paths real content, Course Player, Quiz, Certificates, AI Tutor, Instructor Web, Admin Web, Settings/language-selector UI (routing supports `ar` already; no in-app switcher control yet), Playwright E2E, `web/README.md`. Icon system (Material Symbols) is not wired up yet — `TextField`'s error state is currently border+text only, missing the third "icon" signal `ACCESSIBILITY.md § 7` requires (tracked in a comment in `text-field.tsx`, not yet a DECISIONS_LOG entry since it's an open gap, not a resolved one).
- **Next immediate action:** Start Phase 2 task 3 (Explore/Course Details/Learning Paths) — needs `lib/api/courses.ts`/`categories.ts`/`learningpaths.ts` TanStack Query hook modules following the `execution/INTEGRATION_CONTRACT.md` shapes, plus the CourseCard/SearchField/CategoryChip components from `design-system/COMPONENTS.md`. `execution/PHASE_HANDOFF.md`'s Phase 1 entry remains the authoritative Phase 1 account — do not re-read backend code, do not modify `backend/`.

### What is COMPLETE (committed, reviewed, gates green) — tasks 1–23, all of Phase 1

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

Task 22 (Phase 1 quality gate verification): Claude-led, no delegation. A from-scratch clean build
(`gradlew.bat clean` then `test build --rerun-tasks`) confirmed 65 tests / 15 suites / 0 failures / 0
errors — the definitive number for the Phase 1 report, not a cached result. Full audit: RBAC/auth coverage
(every route file checked for `authenticate()`/CSRF coverage — found and fixed the one gap, D33), a
whole-backend grep sweep for payment vocabulary (zero matches), hardcoded secrets (zero), stray debug
logging (zero), TODO/FIXME markers (zero), module/index/route wiring completeness (all 13 modules
correctly registered), and a `git log --name-only` confirmation that no commit this phase ever touched
`architecture/`, `product/`, `ux/`, or `design-system/`. Compiled the full known-limitations list (7 items,
all pre-existing/deliberate, none blocking — see `PHASE_HANDOFF.md`'s Phase 1 entry § 6).

Task 23 (`PHASE_HANDOFF.md` final write-up): Claude-authored, the fixed structure the file's header
specifies (status, what was implemented, files/modules, API/contracts, database changes, tests/verification,
known limitations, decisions, next-phase dependencies, what not to redo, git reference) — see that file
directly for the full account; not duplicated here.

**Phase 1 is COMPLETE.** All 23 tasks done, gates green, working tree clean. Awaiting the user's explicit
approval before any Phase 2 work begins.

### What is PARTIAL / uncommitted

Nothing. All of Phase 1 is committed and clean.

### Exact next steps on resume

1. Wait for the user's explicit approval of the Phase 1 report delivered this session.
2. Do not start Phase 2 (or any later phase) under any circumstances before that approval — this is an
   explicit, standing instruction, not a default that erodes over a long gap between sessions.
3. Once approved: Phase 2 — Website (see `execution/MASTER_IMPLEMENTATION_PLAN.md`'s Phase → Milestone map).

---

Allowed phase states: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETE`.

| Phase | Status | Notes |
|---|---|---|
| PHASE 1 — Backend Foundation & API | **COMPLETE** | Started 2026-09-05, completed 2026-09-06, approved by the user 2026-09-06. See `PHASE_HANDOFF.md` for the full write-up. |
| PHASE 2 — Website | **IN_PROGRESS** | Started 2026-09-06. See task breakdown below. |
| PHASE 3 — KMP Shared Mobile Core | NOT_STARTED | Blocked on Phase 1. |
| PHASE 4 — Android | NOT_STARTED | Blocked on Phase 3. |
| PHASE 5 — iOS | NOT_STARTED | Blocked on Phase 3. |
| PHASE 6 — AI Tutor Integration | NOT_STARTED | Blocked on Phases 2, 4, 5 (client shells) + Phase 1 (aitutor scaffold). |
| PHASE 7 — Full Integration | NOT_STARTED | Blocked on all prior phases. |
| PHASE 8 — QA, Polish & Portfolio Demo | NOT_STARTED | Blocked on Phase 7. |

---

## PHASE 1 — Task Breakdown (COMPLETE — all 23 tasks done)

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
| 22 | Phase 1 quality gate verification | DONE — Claude-led, no delegation: from-scratch clean build (65 tests, 15 suites, 0 failures), full RBAC/CSRF/secrets/payment-vocabulary/module-wiring audit (found and fixed the D33 CSRF gap), locked-docs-untouched confirmation, known-limitations compilation. |
| 23 | `PHASE_HANDOFF.md` write-up | DONE — Claude-authored final Phase 1 entry in the file's required fixed structure; status marked COMPLETE. |

## PHASE 2 — Task Breakdown

Web app lives in `web/` at repo root (sibling to `backend/`), per `REPOSITORY_STRUCTURE.md`. Consumes `execution/INTEGRATION_CONTRACT.md` as the authoritative API shape — never re-derives it from `architecture/API_CONTRACT.md` alone where the two differ.

| # | Task | Status |
|---|---|---|
| 1 | Foundation: Next.js scaffold, Tailwind v4 + token pipeline, i18n routing, API proxy, auth/CSRF/TanStack Query plumbing, base Navbar + core component kit (Button, TextField) | DONE |
| 2 | Auth screens (Login, Register) + middleware auth gate | DONE |
| 3 | Public discovery: Landing, Explore, Course Details, Learning Paths (+ Learning Path Details) | DONE — real content against live `courses`/`categories`/`learningpaths` endpoints; shared screens under `components/screens/` rendered from both `(public)/...` and `app/...` route trees per the "same screens, not duplicate screens" IA rule; Landing server-fetches featured courses/paths/categories in parallel (`revalidate = 300`); enrollment-aware CTA on Course Details (Login to Enroll / Enroll / Continue Learning); course level labels centralized in `lib/i18n/course-labels.ts` and translated everywhere (D38's `explicitNulls=false` gotcha caught and fixed here — see D38). Verified via real browser against live seeded backend in both en (LTR) and ar (RTL). Clean `lint`, `lint:logical-properties`, and `build` (both locales, 0 errors, only pre-existing `<img>` warnings). |
| 4 | Demo Checkout + Purchase Success | DONE — `CheckoutScreen`/`PurchaseSuccessScreen` at `/app/checkout/:id` and `/app/checkout/:id/success` against the live `enrollment` endpoints; exact `Checkout/OrderSummary` + `SuccessState` component specs (design-system/COMPONENTS.md), zero payment vocabulary/fields (product/DEMO_PAYMENT_FLOW.md § 1 verified by inspection), idempotent re-checkout on an already-enrolled course confirmed live. Found and fixed a real pre-existing bug while browser-testing the entry path — see D39 (`CourseCard`/`LearningPathCard` `basePath` prop). Verified via real browser in en+ar end to end (Explore → Course Details → Enroll → Checkout → Complete Demo Purchase → Purchase Success → enrollment persisted). Clean lint/RTL-check/build. |
| 5 | Student Dashboard + My Learning | NOT_STARTED — next up |
| 6 | Course Player + Quiz + Quiz Results | NOT_STARTED |
| 7 | Certificates List + Certificate Detail | NOT_STARTED |
| 8 | Learning Paths (student-aware) follow/unfollow wiring | NOT_STARTED |
| 9 | AI Tutor chat UI (streaming) | NOT_STARTED |
| 10 | Profile + Settings (incl. language selector) | NOT_STARTED |
| 11 | Instructor Web: Dashboard, Course Editor (Overview/Curriculum), Lesson Editor, Quiz Editor | NOT_STARTED |
| 12 | Admin Web: Dashboard, Manage Courses/Users/Instructors/Categories | NOT_STARTED |
| 13 | Localization completion pass (M14 web slice) — full en/ar coverage + RTL QA sweep | NOT_STARTED |
| 14 | Playwright E2E suite (M15 web portion) | NOT_STARTED |
| 15 | `web/README.md` local run instructions (M16 web portion) | NOT_STARTED |
| 16 | Phase 2 quality gate verification | NOT_STARTED |
| 17 | `PHASE_HANDOFF.md` Phase 2 write-up | NOT_STARTED |

## Immediate Next Action

Task 5: Student Dashboard + My Learning, replacing the `dashboard-shell.tsx` smoke-test placeholder at `/app`. Needs enrolled-courses list with progress (backend has no dedicated "my learning" endpoint yet — check whether `GET /enrollments` plus `GET /courses/:id` per item is sufficient, or whether another minimal backend addition should be proposed the same way D37 was). This is also the natural place to build the first real authenticated top-nav/shell (see D39's noted gap) rather than continuing with bare `<main>` pages.
