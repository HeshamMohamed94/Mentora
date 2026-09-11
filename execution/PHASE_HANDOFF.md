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

---

## INTERIM NOTE (2026-09-07) — Mentora Design-to-Code Source-of-Truth Pipeline

**This is NOT a Phase 2 completion entry and not a numbered Phase 2 product task** — Phase 2 (Website) remains `IN_PROGRESS` through Task 11 (Instructor Web), Task 12 (Admin Web) has explicitly **not** started, and this note does not supersede or complete Phase 2's own eventual handoff section (which will be written when Phase 2 itself is done). It is recorded here only because it is a durable, cross-cutting artifact (not scoped to one task) that future phases — especially any future Android/iOS client work — need to know exists.

Built between Task 11 and Task 12, per explicit user instruction: a structured, machine-readable `design-to-code/` directory normalizing the locked Design System v1.3.2, the locked Mentora Showcase, and the locked Product/UX specs into a platform-neutral source (tokens, component recipes, navigation/shell rules, the governed course-artwork system, 24 screen specs, 6 reusable layout patterns) — intended to reduce future reliance on developers visually approximating screenshots, and to make a future Android/iOS effort start from data rather than re-deriving everything from `design-system/*.md` prose a second time. No locked document (`design-system/`, `product/`, `ux/`, `architecture/`) was modified — every value in `design-to-code/` traces back to one of them via `design-to-code/SOURCE_MANIFEST.json`'s precedence rule. See `execution/DECISIONS_LOG.md` D50 for the full account, `design-to-code/README.md` for the directory map, and `design-to-code/validation/*.md` for extraction/coverage/mapping detail. Task 12 (Admin Web) still has not started as of this note.

---

## PHASE 2 — Website

**Status:** COMPLETE (started 2026-09-06, completed 2026-09-11) — pending the user's explicit approval of the Phase 2 report before Phase 3 begins.

### 1. What was implemented

The full Next.js 15 App Router website for Milestones M3–M4, M6–M16 (web slices) of `architecture/IMPLEMENTATION_ROADMAP.md`, consuming the Phase 1 backend over real HTTP with zero mocking anywhere in the app. Every screen in `product/SCREEN_INVENTORY.md`'s Phase 2 scope is implemented and live against real seeded data, in both `en` (LTR) and `ar` (RTL), Light and Dark mode: public discovery (Landing, Explore, Course Details, Learning Paths), auth (Login/Register), the full Student surface (Dashboard, My Learning, Course Player, Quiz, Quiz Results, Certificates List/Detail, AI Tutor chat, Profile, Settings), the full Instructor surface (Dashboard, Course Editor, Lesson Editor, Quiz Editor), and the full Admin surface (Dashboard, Manage Courses/Users/Instructors/Categories).

Tasks 1–17 all complete: foundation/tokens/i18n/auth-plumbing, auth screens, public discovery, demo checkout, Student Dashboard/My Learning, Course Player/Quiz/Quiz Results, Certificates, Learning Paths follow/unfollow (already complete at task 3), AI Tutor chat UI, Profile/Settings, Instructor Web, Admin Web, localization completion pass, Playwright E2E suite, `web/README.md`, this quality-gate pass, and this write-up. Plus four dedicated, explicitly user-requested visual-fidelity correction passes (D45/D46/D48/D49/D52/D53) and one cross-cutting source-of-truth pipeline (D50, `design-to-code/`) sitting between Task 11 and Task 12, all recorded above/in `DECISIONS_LOG.md`.

### 2. Files/modules created

`web/` — a full Next.js 15 App Router project: `src/app/[locale]/...` (thin route wrappers), `src/components/screens/` (one component per `SCREEN_INVENTORY.md` entry, shared across public/Student/Instructor/Admin route trees per the "same screens, not duplicate screens" IA rule), `src/components/ui/` (design-system component kit — Button, TextField, Select, Toggle, Tabs, FileUpload, DataTable, AppDialog, Avatar, and more), `src/components/navigation/` (AppShell/InstructorShell/AdminShell, parameterized nav), `src/lib/api/` (one typed module per backend resource, TanStack Query hooks), `src/lib/auth/`, `src/lib/design-to-code.generated.ts` (generated, D50), `messages/{en,ar}.json` (491/491 key parity), `e2e/` (10 Playwright spec files + `helpers.ts` + `playwright.config.ts`, D64), `web/README.md` + `web/.env`-equivalent config. `design-to-code/screens/admin-*.json` — 5 new screen specs added during Task 12 (screen count 24→29). Two repo-root scripts, `start-mentora.ps1`/`stop-mentora.ps1`, for one-command local dev-stack management.

**Git:** 33 commits on `main` from Phase 2 kickoff (`a35c80e`) through this write-up's parent commit, touching 220 files (23,770 insertions) under `web/`, `design-to-code/`, and `execution/`. Full list: `git log --oneline a35c80e^..HEAD`. Working tree is clean.

### 3. API/contracts produced

No new backend routes — Phase 2 is a pure consumer of the Phase 1 `architecture/API_CONTRACT.md`/`execution/INTEGRATION_CONTRACT.md` surface. Two small, approved backend changes were made *in service of* the web client, both additive/corrective, never a new contract: `instructorName` denormalized onto `CourseSummary`/`CourseResponse` (D37, avoids an N+1 client-side join) and a real backend defect fix in `CourseService.get()` — an already-enrolled student was losing Course Player access the moment their course was unpublished, contradicting `ux/INSTRUCTOR_ADMIN_UX.md`'s explicit "enrolled students keep access" rule; fixed by injecting `EnrollmentRepository` and allowing the draft-course fetch when the caller is enrolled (D64). Both are documented in `INTEGRATION_CONTRACT.md`/`DECISIONS_LOG.md` and covered by both integration and E2E tests.

### 4. Database changes

No schema changes. Seed data content was upgraded in place, still via the existing `SeedData.kt`/`seedDemoData` pipeline (no new collection, no new field beyond what D37/D64 already cover): every published seed course's first lesson now has a real, playable ffmpeg-encoded MP4 (D59/D60, one topic-relevant video per course, a genuinely Arabic video for the Arabic-content course) instead of synthetic placeholder bytes, and the 4 real published courses now have real topic-specific thumbnail images (D61) instead of the generic gradient motif colliding across categories. Per-locale course title/description fields were added and populated (D57) to close a language-leak found on the Dashboard (D56).

### 5. Tests/verification performed

**Automated, backend:** 77 tests / 17 suites / 0 failures / 0 errors, re-verified from a clean, non-cached state as this phase's final gate (`gradlew.bat clean test --rerun-tasks`), not a cached/incremental or self-reported result.

**Automated, frontend:** `typecheck` clean; `lint` clean (one pre-existing, expected `<img>` warning on `course-thumbnail.tsx`); `lint:logical-properties` clean (zero physical-direction CSS anywhere under `src/` — this app is RTL-first by construction); `validate:design-to-code` clean (29 screens/6 patterns/11 shared files); a stopped-dev-server production `build` clean (47 routes, both locales, 0 errors).

**Automated, end-to-end:** `web/e2e/`'s 10 Playwright specs (one per `architecture/TESTING_STRATEGY.md § 6` priority flow), run against the real local backend + MongoDB, never mocked. **Chromium: 19/19 passing. Firefox: 19/19 passing.** WebKit: 1/19 — a real, investigated, non-product `Secure`/`SameSite` cookie-policy incompatibility with local plain-HTTP `localhost` (see Known limitations). A genuine, locked-spec-violating backend defect was found and fixed while building this suite (§3 above), covered by both a new integration-test assertion and a dedicated E2E test that proves the fix end-to-end through a real browser.

**Manual/live, every task:** every one of Tasks 1–15 was independently verified live in a real browser against the real seeded backend, in both `en`/LTR and `ar`/RTL, before being marked DONE — not just gate-script output. Four dedicated visual-fidelity passes (D45/D46/D48/D49/D52/D53) additionally scored implementation against the locked `design-review-locked/Mentora Showcase.dc.html` screen-by-screen and corrected the highest-priority gaps, with every remaining gap explicitly disclosed rather than silently left (see Known limitations).

### 6. Known limitations

None of these block Phase 2's quality gate — each is a deliberate, disclosed, non-blocking gap or a real environment constraint investigated to its root cause, not an undetected defect (full detail at the cross-referenced decision):

1. **WebKit E2E: 1/19 passing** (D64) — a real `Secure`/`SameSite` cookie-policy difference between this app's correct production cookie config (`secure=true`/`SameSite=Lax`) and WebKit's stricter handling of that config over plain-HTTP `localhost`; Chromium/Firefox's well-known "localhost is trustworthy" relaxation doesn't apply the same way in WebKit. Not fixed — would require local HTTPS or a real CI TLS-terminating proxy, neither in scope; not a defect in the app's security-correct cookie config, which should not be weakened to accommodate this.
2. ~~Seed course lesson-2 videos are still fake placeholder bytes~~ — **RESOLVED** by the PRE-PHASE-3 seed data expansion (D67, 2026-09-11): every lesson in every published seed course now uploads a real, playable demo video; no seeded lesson opens into "This video can't be played" anymore. Kept here struck through rather than deleted so the original Phase 2 gap and its resolution are both traceable.
3. **Mobile drawer breakpoint behavior verified by code review only, not live** (D40) — the browser-automation environment's window resize doesn't change the rendered viewport, so the Sidebar's off-canvas behavior on narrow viewports is unverified live.
4. **`DataTable` breakpoint spec conflict** (D62) — `design-system/COMPONENTS.md` (collapses below `desktop`) vs. `design-tokens.json` (collapses below `tablet`) disagreed; resolved in favor of the more detailed component-specific prose, disclosed non-blocking.
5. **Course Editor IA conflict left unresolved** (D48) — showcase shows a persistent rail, locked spec implies two tabs; still built as tabs, deliberately not forced toward the showcase past the locked spec's own authority.
6. **Instructor Dashboard / Course Editor capped visual-fidelity scores** (Instructor Dashboard ≈78%, Course Editor ≈66–72%, D48/D49) — both capped by the same disclosed showcase-vs-locked-spec conflicts (4th stat card/table columns, Media tab/persistent rail) intentionally left unresolved rather than chased past the locked spec.
7. **Admin Course Management: two deliberate showcase deviations** (D62) — a 5-item nav (incl. Instructors) over the showcase's 4-item version; a read-only status Badge + one-way Unpublish over the showcase's bidirectional Toggle, since no admin-capable publish endpoint exists.
8. **No password-change endpoint; `avatarMediaId` is a dead field** (D44) — both real spec-vs-backend gaps in Profile/Settings, scoped out rather than built against a nonexistent contract.
9. **AI Tutor has no docked-panel variant** — only the full-screen `/app/ai-tutor` chat screen exists; carried forward unchanged since Task 9.
10. **Purchase Success renders with `shell:"none"`** (D52) — a deliberate, disclosed layout choice, not a bug.
11. **True-concurrency double-completion edge case in demo checkout** (Phase 1, D14) — a documented, non-blocking theoretical race under true concurrent duplicate requests; carried forward unchanged, out of Phase 2's scope to revisit.
12. **Landing page's `CourseCard`/`LearningPathCard` usage hardcodes the Guest `basePath`** (D39) — low priority since Landing is conceptually Guest-only; unchanged since Task 3/4.
13. **Icon set is a hand-drawn inline-SVG placeholder** (D40) — for the real self-hosted Material Symbols Rounded font; swap later behind the same `Icon` component API, no call-site changes needed.

### 7. Decisions made

32 implementation-time decisions recorded in `execution/DECISIONS_LOG.md` (D35–D66), spanning: the token/design-to-code pipelines (D35, D50); every UI-vs-backend gap-fill (D36, D38, D42, D44); four dedicated visual-fidelity correction passes against the locked showcase (D45/D46/D48/D49/D52/D53); the Student Dashboard acceptance-criteria and localized-metadata slice (D54–D61); Admin Web and its showcase-mockup discovery (D62); the localization/RTL completion audit (D63); the Playwright E2E suite, including a genuine backend defect found and fixed while building it (D64); `web/README.md` (D65); and this phase's own quality-gate verification (D66). None reopens or contradicts a locked architecture/product/UX/design-system decision — every one is either a real implementation-time gap the locked docs correctly left for implementation to resolve, or a disclosed, non-blocking limitation.

### 8. What Phase 3 (and later phases) depend on

- `design-to-code/` (D50, extended to 29 screens by D62) is the platform-neutral source Phase 3+ (KMP shared core, then Android/Phase 4 and iOS/Phase 5) should read from first, rather than re-deriving screen/component specs from `design-system/*.md` prose a second time.
- `execution/INTEGRATION_CONTRACT.md` remains the authoritative as-built API reference, now proven correct against a real, full client (this website) rather than only against Phase 1's own tests.
- The full local dev stack (`start-mentora.ps1`/`stop-mentora.ps1`, `web/README.md`, `backend/README.md`) is ready for any future phase's own local development needs.
- The `web/e2e/` Playwright suite and its `helpers.ts` (real registration/login/enrollment/course-authoring flows against a live backend) is a reusable reference for how to drive this backend for real from a test — useful pattern precedent for any future mobile-side integration testing, even though KMP/Android/iOS will need their own tooling.

### 9. What later phases must NOT redo

- Do not rebuild any Phase 2 web screen, component, or shell — the full Student/Instructor/Admin/public surface is complete, live-verified, and E2E-tested. Phase 3+ consumes the backend Phase 2 already proved out; it does not touch `web/`.
- Do not re-attempt to "fix" the WebKit cookie issue by weakening the backend's `Secure`/`SameSite` cookie configuration — that would be a real security regression for a real HTTPS deployment. If WebKit coverage is ever wanted, the fix is local HTTPS or a CI TLS-terminating proxy, not a cookie-policy change.
- Do not re-litigate the Course Editor IA conflict (persistent rail vs. tabs) or the Instructor Dashboard/Course Editor showcase-vs-locked-spec gaps — both were investigated twice (D48, D49) and deliberately left as-is in favor of the locked spec's own authority over the showcase.
- Do not treat the mobile-drawer live-verification gap as a new discovery — already disclosed, known, and low-priority; re-investigating it from scratch would be duplicate work. (The lesson-2 placeholder-video gap this bullet used to also name was resolved by the PRE-PHASE-3 seed data expansion, D67 — see `CURRENT_STATUS.md`.)

### 10. Git commit/state reference

`main` branch, working tree clean. Phase 2 spans commits `a35c80e`..`7748204` (33 commits; `git log --oneline a35c80e^..HEAD` for the full list with messages, includes the one pre-kickoff approved backend commit `4d33fcc`). The `design-system/`, `product/`, `ux/`, `architecture/`, and `design-review-locked/` directories were never modified during Phase 2 (verified via `git log --name-only a35c80e^..HEAD` over the full commit range) — every locked doc is exactly as it was at Phase 2's start, consulted and adapted-to, never edited. No `mobile/`/`infra/` directory exists — Phase 3+ has not been started.
