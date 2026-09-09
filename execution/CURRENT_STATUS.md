# Mentora — Current Implementation Status

**Last updated:** 2026-09-07 (Mentora Design-to-Code source-of-truth pipeline — DONE, D50; awaiting user approval before task 12, Admin Web)

---

## EXACT RESUME POINT

**Read this section first when resuming.**

- **Phase:** PHASE 2 — Website — `IN_PROGRESS`. Phase 1 is `COMPLETE` and was explicitly approved by the user on 2026-09-06.
- **Current task:** Tasks 1-11 are DONE and verified end-to-end (real browser + real backend, en+ar) — foundation, auth, public discovery, demo checkout/purchase success, dashboard/my learning + the authenticated Sidebar shell, Course Player/Quiz/Quiz Results, Certificates List/Detail, Learning Paths follow/unfollow (already complete since task 3), AI Tutor chat UI (streaming), Profile + Settings (incl. functional language selector), and Instructor Web (Dashboard, Course Editor Overview/Curriculum, Lesson Editor, Quiz Editor). Tasks 6-7-9-10-11 were delegated to Codex via the `codex-delegate` skill (D41/D42/D43/D44/D47) and independently reviewed/verified/landed by Claude. **Two dedicated UI-fidelity correction passes across Tasks 1-10** were done before Task 11, per explicit user request: D45 (2026-09-07) fixed `TextField`'s missing floating-label behavior, added the required `PasswordField` visibility toggle, and rebuilt Login/Register from unstyled scaffolding into a proper card surface with a minimal logo-only header; D46 (2026-09-07, a stricter follow-up pass) fixed every course thumbnail in the local demo rendering as a broken-image icon, restored `CourseCard`/`LearningPathCard`'s spec'd-but-missing primary-action element, and fixed a widespread "error retry button shows the error sentence" bug. **A third, strictest fidelity pass (2026-09-07, D48) is also DONE**, this time comparing directly against the locked `design-review-locked/Mentora Showcase.dc.html` (not only `design-system/*.md` prose) per explicit user instruction: implemented the governed 5-motif course-artwork gradient system for the first time (`CourseThumbnail`'s new `seed`/`categoryId`/`badge` props, used everywhere a course thumbnail renders), moved the category chip onto the artwork as a scrim overlay, gave `LearningPathCard` its missing icon/eyebrow/arrow, rebuilt `CourseProgressCard` as the showcase's horizontal row (Dashboard/My Learning), rebuilt the Landing hero as a two-column layout with a real stats row and course-artwork collage, added a 4th Dashboard stat + an AI Tutor nudge card, and gave the Instructor Dashboard course list a real table header row. **Awaiting explicit user approval of this pass before Task 12 starts** — this is a standing instruction, not a default that erodes over time. Icon set is a hand-drawn inline-SVG placeholder for the real self-hosted Material Symbols Rounded font (D40) — swap later behind the same `Icon` component API, no call-site changes needed. Landing page's own `CourseCard`/`LearningPathCard` usage still hardcodes the Guest `basePath` (unchanged from D39) — low priority since Landing is conceptually Guest-only. Two spec-vs-backend gaps disclosed and scoped out in task 10 (D44): no password-change endpoint exists, and `avatarMediaId` is a dead field nothing ever writes — Profile shows initials-only, no upload control. `DataTable` (needed for Task 12's Manage Courses/Users/Instructors/Categories tables) is still unbuilt — deliberately out of scope per D47. D48 deliberately left the Instructor Course Editor's showcase-vs-locked-spec IA conflict (persistent rail vs. two tabs) unresolved — see D48 for the full reasoning — and did not add a public language-toggle or guest AI-Tutor nav link (neither is real functionality yet). **A fourth, targeted pass (2026-09-07, D49) is also DONE**: a full visual audit (`D:\Work\MentoraVisualAudit\`) had scored 24 screens against the locked showcase and found only 7 with a genuine EXACT assembled reference (Landing, Explore, Dashboard, Course Player, Instructor Dashboard, Course Editor Overview/Curriculum); D49 corrected those 7 specifically rather than chasing the showcase everywhere. The highest-priority fix was Course Player, rebuilt from a single-column/sidebar-visible/tab-less layout (55% fidelity) into the locked two-column "focused learning shell" (top bar, video-left/curriculum-right split, Overview/Resources tabs, Previous/Mark Complete/Next row) — now ≈88%. Dashboard gained a real-data-driven "Up Next" right-rail module (72%→91%). Landing's hero heading copy and collage grid were corrected (78%→90%). Instructor Dashboard's table header was made uppercase (74%→78%) and Course Editor's panel was given a bounded max-width (60%→66% Overview, 68%→72% Curriculum) — both capped below 90% by the same disclosed showcase-vs-locked-spec conflicts D48 already identified (Instructor Dashboard's 4th stat card/table columns, Course Editor's Media tab/persistent rail), which D49 re-confirms as intentionally unresolved, not missed. Explore was re-verified but received no structural change (88%→89%). Full re-scoring, side-by-side images, and the disclosed-conflict list are in `D:\Work\MentoraVisualAuditExactPass\EXACT_PASS_REPORT.md`. One operational incident during this pass: running `npm run build` while `npm run dev` was still active corrupted the dev server's `.next` cache (`Cannot find module './vendor-chunks/@formatjs.js'`); fixed by killing the dev process, deleting `.next`, and restarting `npm run dev` clean — no application code was at fault. **A dedicated Design-to-Code source-of-truth phase (2026-09-07, D50) is also DONE**, sitting between Task 11 and Task 12 (NOT a numbered Phase 2 product task — Task 12 has still not started). Built `design-to-code/` (README, `SOURCE_MANIFEST.json` with a locked precedence rule — Product/UX > Design System > Showcase > current web implementation-as-evidence-only; `shared/` — tokens/typography/spacing/shape/elevation/components/navigation/artwork/responsive/localization/platform-contract, all normalized FROM the existing locked sources, never re-invented; `screens/` — one JSON spec per all 24 implemented MVP screens, each tagged `exact-showcase`/`approved-pattern`/`ux-only` honestly, 7/5/12 respectively; `patterns/` — 6 reusable layout patterns for screens with no exact mockup; `validation/` — EXTRACTION_REPORT.md, COVERAGE_REPORT.md, MAPPING_REPORT.md). Deliberately did NOT generate Admin (screens 25-29) specs even though the locked docs already define them, to avoid any appearance of starting Task 12. Added `tools/design-to-code/{validate.js,generate.js}` (plain Node, no new dependency, same D35 precedent as `tools/token-pipeline/`) — validation passes clean (24 screens/6 patterns/11 shared files, 0 errors/warnings) and generation writes `web/src/lib/design-to-code.generated.ts` plus two audit-trail JSON files under `design-to-code/generated/web/`. Migrated exactly two genuine hardcoded-duplication cases onto the new generated source with verified byte-identical output: the Student/Instructor Sidebar nav-item arrays (`app-shell.tsx`/`instructor-shell.tsx`) and the 5-motif course-artwork gradient array (`course-thumbnail.tsx`) — confirmed via live re-verification (Dashboard/Explore/Instructor Dashboard, EN+AR/dark) that rendering is unchanged. All four gates re-run clean (`typecheck`/`lint`/`lint:logical-properties`/`build`, same single pre-existing `<img>` warning). See `execution/DECISIONS_LOG.md` D50 and `design-to-code/validation/*.md` for the full account.
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
| 5 | Student Dashboard + My Learning | DONE — also built the first real authenticated `Sidebar` shell (design-system/COMPONENTS.md § Sidebar), replacing the bare chrome-less `<main>` every `/app/*` page had until now (see D39/D40). Dashboard (`/app`) has stats, Continue Learning, followed Learning Paths in progress, and recommendations; My Learning (`/app/my-learning`) has the full filterable enrollment grid. No dedicated aggregate backend endpoint exists for either — both compose `GET /enrollments`/`GET /learning-paths` with per-item detail fetches client-side (D40), an accepted N+1-at-small-scale tradeoff given the seed data's size. Verified live in en+ar (Sidebar collapse/expand, RTL mirroring, real stats/progress from the backend); mobile drawer verified by code review only — the browser automation environment's window resize did not change the rendered viewport, so the off-canvas breakpoint behavior itself is unverified live (see D40). Clean lint/RTL-check/build. |
| 6 | Course Player + Quiz + Quiz Results | DONE — delegated to Codex via the `codex-delegate` skill (D41), Claude reviewed and landed. `/app/learn/:id` (curriculum + real VideoPlayer/PlaybackControls against the media playback-url/stream endpoints + Mark Complete/auto-advance), `/app/learn/:id/quiz` (question-by-question QuestionCard/AnswerOption flow), `/app/learn/:id/quiz/results` (fresh-fetched score/breakdown + completion confirmation). Sidebar auto-collapses on both screens via a new `SidebarForceCollapseContext`, reverting on navigate-away without touching the user's saved preference. Verified live end-to-end as the seeded student: completed both lessons of a real course, took its real 3-question quiz, saw the graded breakdown, reached the certificate-issued completion confirmation, and confirmed My Learning/Dashboard stats updated correctly. Independently re-ran typecheck/lint/RTL-check/build (all matched Codex's own claims exactly) and read every diff against the brief — zero out-of-scope changes. |
| 7 | Certificates List + Certificate Detail | DONE — delegated to Codex via `codex-delegate` (D42), Claude reviewed and landed. `/app/certificates` (grid + `EmptyState`) and `/app/certificates/:id` (`:id` is the backend's public `MTR-XXXX-...` format, passed through verbatim). No real certificate-image asset exists in the backend, so both screens build a polished, fully token-driven placeholder/"document" presentation instead of pointing at a nonexistent image endpoint — see D42. "Share" is confirmed UI-only (zero network calls). Verified live: the certificate earned during task 6's live test appears correctly in both screens, en+ar, including the not-found path for an invalid id. Independently re-ran every gate; matched Codex's own claims exactly. |
| 8 | Learning Paths (student-aware) follow/unfollow wiring | DONE — already fully implemented as part of task 3's `learning-path-details-screen.tsx` (`useFollowLearningPath`/`useUnfollowLearningPath`, login-gated for guests, `path.isFollowing`-driven button state). Discovered already complete while planning task 6/7 — noted here so it isn't redone. |
| 9 | AI Tutor chat UI (streaming) | DONE — delegated to Codex via `codex-delegate` (D43), Claude reviewed and landed. `/app/ai-tutor` chat screen against the real `AiTutorRoutes`/`StubAiProvider` chunked `text/plain` streaming endpoint (architecturally distinct from every other endpoint's JSON envelope — a dedicated `streamAiMessage()` in `lib/api/ai-tutor.ts` reads the raw `ReadableStream` via `getReader()`/`TextDecoder`, bypassing the shared `apiFetch` abstraction on purpose). Thinking-dots indicator, token-by-token streaming render with blinking cursor, quick-action chips that send immediately, IME-safe Enter-to-send, partial-reply preservation on mid-stream failure with a separate retryable error bubble, accessible `role="log"` thread plus a completion-only `sr-only` live region. Sidebar stays visible (not force-collapsed, unlike Course Player/Quiz). Verified live as the seeded student: sent a typed message and a quick action, watched the stub reply stream and complete correctly, then confirmed `/ar/app/ai-tutor` mirrors correctly via logical properties (bubble sides swap, Arabic strings render). Independently re-ran every gate; matched Codex's own claims exactly. |
| 10 | Profile + Settings (incl. language selector) | DONE — delegated to Codex via `codex-delegate` (D44), Claude reviewed and landed. `/app/profile` (Avatar with initials fallback, name, email, real Courses Completed/Certificates stats reusing Dashboard's exact derivation, inline name editing via `PATCH /users/me`) and `/app/settings` (Theme Light/Dark/System via the pre-existing, previously-unused `useTheme()` hook; Language English/العربية switching the UI immediately in place via `next-intl` navigation AND persisting to the account, per `product/USER_FLOWS.md` flow 28). Two new reusable components: `Select` (accessible combobox, keyboard nav + typeahead) and `Avatar`. Two real spec-vs-backend gaps disclosed and scoped out (D44): no password-change endpoint exists in the backend; `avatarMediaId` is a dead field nothing ever writes, so no avatar-upload control was built. Theme modeled as a 3-state `Select`, not a 2-state Toggle, to match the already-built `useTheme` state model. Verified live as the seeded student: name edit round-tripped and propagated to the Sidebar top-bar via shared query-cache invalidation; Theme switch re-themed the whole app instantly; Language switch changed URL/direction/strings immediately with no reload and persisted across a fresh reload; RTL mirroring confirmed correct on both screens including the non-mirrored `expand_more`/`expand_less` Select indicator. Independently re-ran every gate; matched Codex's own claims exactly. |
| 11 | Instructor Web: Dashboard, Course Editor (Overview/Curriculum), Lesson Editor, Quiz Editor | DONE — delegated to Codex via `codex-delegate` (D47), Claude reviewed and landed. `/instructor` (Dashboard: stats + owned-course list + Create Course), `/instructor/courses/new` + `/instructor/courses/:id` (Course Editor, Overview/Curriculum as sibling tabs, category/level/content-language Selects, thumbnail FileUpload, Draft/Published Toggle always paired with a visible text label, a publish-readiness checklist driven by the backend's exact per-field validation codes), Lesson Editor, and Quiz Editor (a single atomic `PUT` replace of the whole question set, matching the backend's real replace-not-patch contract). New reusable components: `Toggle`, `FileUpload`, `Tabs`, `ReorderableList` (drag handle + a mandatory always-visible, never-mirrored Move Up/Down non-drag alternative), `AppDialog` + a shared `useUnsavedChanges` guard used by all four authoring screens. `AppShell`'s nav items are now parameterized (Student behavior unchanged by default) so the new `InstructorShell` reuses the same chrome with its own nav list and a client-side role redirect for non-instructors; Instructor Profile/Settings reuse the existing Student screen components (student-only stats disabled), per the product spec calling these "minimal equivalents," not new screens. One brief inaccuracy Codex caught and correctly overrode: every section/lesson mutation actually returns the full `CourseResponse`, not a narrower per-resource shape — see D47. `courses.ts`/`quiz.ts`/`media.ts` extended additively; new `instructor.ts`. Full en/ar key parity (349/349). No backend changes. Independently re-ran every gate (matched Codex's claims exactly) and verified live as the seeded `instructor1@mentora.dev` account in en+ar — Dashboard, both Course Editor tabs, Lesson Editor, and Quiz Editor all against real seeded course/quiz data, including RTL mirroring. |
| 12 | Admin Web: Dashboard, Manage Courses/Users/Instructors/Categories | NOT_STARTED |
| 13 | Localization completion pass (M14 web slice) — full en/ar coverage + RTL QA sweep | NOT_STARTED |
| 14 | Playwright E2E suite (M15 web portion) | NOT_STARTED |
| 15 | `web/README.md` local run instructions (M16 web portion) | NOT_STARTED |
| 16 | Phase 2 quality gate verification | NOT_STARTED |
| 17 | `PHASE_HANDOFF.md` Phase 2 write-up | NOT_STARTED |

Task 9 note: built as the standalone full-navigation `/app/ai-tutor` screen only — the
`ux/RESPONSIVE_BEHAVIOR.md` § 9 docked-panel-alongside-the-player variant for Course Player was
**not** built (Course Player's "Ask AI Tutor" link still full-navigates away, same forward-reference
behavior as before task 9 existed). Not a defect, just an unbuilt refinement — flag it if a future
task revisits Course Player.

**UI-fidelity correction pass (2026-09-07, D45) — done before Task 11, by explicit user request:**
a cross-cutting visual-fidelity pass across Tasks 1-10 (not a numbered task itself), closing real
gaps against the locked `design-system/COMPONENTS.md` spec found via a live-browser audit: `TextField`
now has the documented floating-label behavior (was a static always-visible label); a new
`PasswordField` component ships the spec-required visibility toggle (Login/Register previously had
none); Login and Register were rebuilt with a real card surface, a minimal logo-only header (was
the full `PublicNavbar`, contradicting `ux/SCREEN_UX_SPECS.md §§ 6-7`), and corrected title copy;
`color.text.link` (previously defined in tokens but consumed nowhere) now backs a new `.mtx-link`
class; `SearchField` uses the real icon system instead of a raw Unicode glyph and has its spec'd
clear button. Zero product behavior, routes, or backend contracts changed. Full detail, the
Select-label regression caught and fixed mid-pass, and the verification performed: see D45.

**Second, stricter UI-fidelity pass (2026-09-07, D46) — done before Task 11, by explicit user
follow-up request** ("still does NOT visually match... not merely like a functional app using the
same purple palette"): fixed a real, high-impact bug — every course thumbnail in the local demo
rendered as a broken-image icon, because `SeedData.kt` uploads placeholder bytes with an
`image/jpeg` content-type that Chrome cannot decode (same category as D41's lesson-video
placeholders, never fixed for course artwork until now). New shared `CourseThumbnail` component
(`web/src/components/ui/course-thumbnail.tsx`) renders a branded fallback (the existing
`myLearning` book icon on `brand.primaryContainer`) at all five places a course thumbnail is
rendered. Also restored `CourseCard`/`LearningPathCard`'s spec'd-but-missing "primary action"
content-hierarchy item (COMPONENTS.md item 7 for CourseCard), and fixed a widespread bug where 7
screens' `ErrorState` retry buttons displayed the full error sentence instead of "Try again" (both
props were accidentally passed the same translation key). Zero product behavior, routes, or
backend contracts changed. Full detail and verification performed: see D46.

**Login/Register design-derived auth-screen refinement (2026-09-07, D51) — done after the D50
post-pipeline visual audit, by explicit user follow-up request** ("rebuild them as DESIGN-DERIVED
screens... not exact-reference"): reworked strictly from `design-system/COMPONENTS.md` §
Inputs/§ Buttons and `ux/SCREEN_UX_SPECS.md` §§ 6-7, no showcase mockup exists for either screen.
Fixed real defects: field-level errors previously rendered the field's own label text instead of
a message; Register's password strength hint (already called for in `register.json`) had no
`PasswordField` support at all; Register's "email already registered" server error rendered as a
generic banner instead of inline under the Email field as this project's own spec already said;
`.mtx-auth-page` didn't vertically center on tall viewports. Both screens' `referenceType`
reclassified `"ux-only"` → `"approved-pattern"` in `design-to-code/screens/{login,register}.json`.
Zero product behavior, routes, backend, or `design-system`/`product`/`ux` document changes. Full
detail and verification performed: see D51.

**Final targeted visual correction pass (2026-09-07, D52) — done after manual side-by-side
review of the D50 audit's comparison images (not just automated scores), by explicit user
request.** Corrected 7 screens: Demo Checkout (regressed to ~75%, rebuilt to include thumbnail/
instructor/itemized row/total per its own already-existing spec), Purchase Success (icon
32→96px, vertical centering), Course Details (curriculum now card-contained sections, semantic
list preserved), AI Tutor (identity header + pill composer + circular send button), Instructor
Dashboard (byline + "+" icon + a `.mtx-instructor-card` styling regression fixed), Course Editor
Overview (Save/Publish moved to a top action bar, real `CourseThumbnail` preview wired into
FileUpload), Course Editor Curriculum (explicit section-card surface color — the same one-line
fix also applied to Quiz Editor, which shares the CSS class). Purchase Success's `shell: "none"`
gap (shared AppShell still renders chrome) deliberately left unresolved and disclosed, not
silently fixed — see D52. The already-locked Instructor Dashboard (3-vs-4 stat cards) and Course
Editor (2-tab vs. showcase's 3-tab/persistent-rail) conflicts from D47/D48/D49 were re-confirmed,
not re-litigated. New external audit: `D:\Work\MentoraFinalVisualAudit\` (zipped to
`MentoraFinalVisualAudit.zip`) — exact-showcase average ≈94.1%, 4 of 7 exact-showcase screens
≥95%, one screen (Course Editor Overview, 88%) below 90% due to the disclosed locked-spec
conflict. Zero product behavior, routes, backend, or `design-system`/`product`/`ux` document
changes. Full detail and verification performed: see D52.

**Primary Light Visual Alignment pass (2026-09-07, D53) — done by explicit user request to make
Light Mode the primary visual validation baseline.** Investigated first rather than assuming a
defect: byte-level comparison of `theme-light.json` against generated `tokens.css` (identical),
a repo-wide grep for hardcoded/generic-gray colors (zero matches), and live verification of all
24 screens in freshly-reset Light Mode — **no CSS/token defect was found**. Login/Register
already share the same page background as every other screen (`.mtx-auth-page` has no
background rule of its own). Likely explanation for the "feels dark" perception: this dev
machine's OS/browser reports `prefers-color-scheme: dark`, and the app correctly defers to
system preference when no explicit override exists (unchanged this pass, per instruction not to
remove system-preference support). Since no CSS fix was needed, the concrete work was extending
`design-to-code/shared/platform-contract.json` with an explicit "MENTORA VISUAL PARITY RULE" plus
Android (Compose ColorScheme/Typography/Shapes) and iOS (SwiftUI Color/Font/shape) mapping
tables — mapping only, no native code written. New external audit:
`D:\Work\MentoraLightVisualAudit\` (zipped to `MentoraLightVisualAudit.zip`) — Light-mode overall
consistency 97%, exact-showcase Light average ≈93.9% (materially unchanged from D52, since no
screen structure changed). Zero product behavior, routes, backend, or
`design-system`/`product`/`ux` document changes. Full detail and verification performed: see D53.

**Student Dashboard Acceptance Criteria — Search + Theme Toggle + Title (2026-09-09, D54) — done
per an explicit user acceptance-criteria ticket, not a Task 12 start.** Investigated first:
neither `design-to-code/screens/dashboard.json` nor the locked `Mentora Showcase.dc.html` Student
Dashboard mockup (light or dark) specs a distinct "Dashboard" title or a theme-toggle control —
the showcase's top row is only an eyebrow + "Welcome back, {name}" greeting (already the page's
h1) next to an inline search box and an avatar. This gap was disclosed to the user before
implementation (not silently resolved); the user made the product call: "Dashboard" becomes the
page h1 (matching the h1-as-screen-name pattern already used by Explore/Settings), the existing
greeting demotes to a secondary line beneath it, and the search bar + a new theme-toggle
`IconButton` sit in the same top-right control area, toggle directly after the search bar in
DOM/logical order (mirrors correctly in RTL). Implementation reused existing pieces throughout:
the approved `SearchField` component (previously only used on Explore), the locked `IconButton`
spec (`design-system/COMPONENTS.md § IconButton`) via the existing `.mtx-icon-button` class (which
was missing its specced `:active`/pressed state — added, benefiting every existing consumer, not
just this control), and the existing `useTheme()` hook/`mentora-theme` localStorage mechanism
(extended, non-breaking, with a `resolvedTheme` field so the toggle can show the icon for the
theme actually in effect, including live system-preference changes when no explicit override is
set — Settings' theme `Select` is unaffected). Two new hand-drawn `Icon` entries (`darkMode`
moon / `lightMode` sun) added following the existing inline-SVG icon pattern; both are
non-directional and were left out of `design-system/design-tokens.json`'s `icon.directional`
lists on purpose (that locked file was not touched) — the documented default for an undeclared
icon is already `neverMirror`, which is correct here. Dashboard's search submits on Enter to
`/app/explore?q=<value>` (the only place Mentora actually filters courses/categories by text);
`ExploreScreen` gained a one-line `useSearchParams()` read to seed its existing search state from
that `?q=`, wrapped in the `Suspense` boundary Next.js requires for it in both places `ExploreScreen`
is mounted — the only screen besides Dashboard this change touched, and only for this reason.
Zero changes to Dashboard stats, Continue Learning, Learning Paths, AI Tutor, Sidebar,
authentication, or any `design-system`/`product`/`ux` document. Full detail and verification
performed: see D54.

**Student Dashboard Full Acceptance Criteria Alignment (2026-09-10, D55) — done per a follow-up
acceptance-criteria ticket extending D54, not a Task 12 start.** Restructured the header: search
bar + theme toggle now sit in their own row above a standalone `Dashboard` h1 (previously the
title/greeting shared a row with search+toggle); search placeholder text changed to the ticket's
exact new string ("Search for courses, skills or anything...") and the field widened
(`tablet:max-w-[440px]`, up from 280px) so it renders unclipped. Replaced the static "Welcome
back, {name}" greeting with a dynamic, client-local-time greeting (`new Date().getHours()`, never
server time) across 4 buckets — morning/afternoon/evening/night — using the authenticated user's
first name (parsed client-side from the existing single `name` field; no backend change) plus a
new supporting subtitle line, both via new i18n keys in `en.json`/`ar.json` (old unused
`dashboard.greeting` key removed, confirmed no other references first). Investigated the 4th stat
card before touching it: the showcase's actual 4th metric is "Learning hours," and a full
backend+frontend sweep (Progress/Enrollment/Lesson/Media models, all DTOs) found **no real
watch-time data anywhere in the system** — fabricating an hours figure was explicitly disallowed
by the ticket, so the existing, real Avg. progress metric was kept in that slot instead and the
gap disclosed here and in `design-to-code/screens/dashboard.json`'s `conflicts[]`, rather than
inventing a number. `design-to-code/screens/dashboard.json` (not a locked source — explicitly
permitted to be kept current per this ticket's own instruction) updated to record the new header
composition, typography hierarchy, and both conflicts; `design-system/product/ux` documents
themselves were not touched. Verified live in Chrome as two seeded students (`student1`/
`student2@mentora.dev`): dynamic greeting with real first name and correct time bucket in both EN
and AR, theme toggle Light↔Dark with refresh persistence in both locales, search (case-insensitive
partial match via "koTLIN", no-result query, empty-Enter no-op) landing on Explore pre-filled, a
real Continue Learning card (thumbnail/progress/Resume) produced by enrolling and partially
completing a course through the actual demo-checkout flow, accessible name for the search field
confirmed via DOM inspection to come from its associated `<label>` (not the placeholder). Narrow-
viewport responsive behavior could **not** be live-verified this session — `resize_window`
returned success but the tab's `window.innerWidth` never changed from 2048px in this environment;
the new header row reuses the same shrinkable-flex-item pattern already relied on elsewhere in the
app, but this is disclosed as unverified rather than claimed. `typecheck`/`lint`/
`lint:logical-properties`/`validate:design-to-code` (24 screens, re-passed after the
`dashboard.json` edit)/production `build` (37 routes) all clean. Full detail and verification
performed: see D55.

## Immediate Next Action

**Per explicit user instruction (most recently reaffirmed 2026-09-10, D55): do not start Task 12
without a new go-ahead — this note is for whenever that go-ahead comes.**

Task 12: Admin Web — Dashboard, Manage Courses/Users/Instructors/Categories. Read the real
backend source before assuming any endpoint shape (same discipline as every prior task):
`backend/src/main/kotlin/com/mentora/backend/admin/` for the four admin aggregation/list
endpoints (`GET /api/v1/admin/{dashboard,courses,users,instructors}`, per Phase 1's task 17
summary) and the already-built `categories` CRUD (`courses` module) that Admin's Manage Categories
screen reuses rather than duplicating. This task needs a new `DataTable` component
(`design-system/COMPONENTS.md` — not yet built; screens 25-29 per `SCREEN_INVENTORY.md § C`,
explicitly out of scope for Task 11 per D47) — check its full spec (columns, sort, pagination, row
actions, the "reusable popover/menu convention" referenced in D40/D41/D47) before building. Reuse
Task 11's `Select`/`Toggle`/`Tabs`/`AppDialog`/`useUnsavedChanges` where they fit rather than
rebuilding equivalents; `AppShell`'s `navItems` prop (D47) is ready for a third, Admin-specific nav
list the same way `InstructorShell` used it.
