# Mentora — Implementation Roadmap

**This is a plan, not implementation.** No milestone below has been executed by this architecture phase. Each milestone states its goal, dependencies, deliverables, acceptance criteria, tests, affected repository area, and whether Claude or Codex is best suited to execute it — per the brief's explicit instruction to reserve Codex's limited weekly usage for implementation-heavy, repetitive-code work, while Claude handles supervision, planning, review, architecture, documentation, and targeted fixes.

**Execution rule for every milestone:** Codex implements against this document and the relevant architecture file(s) linked per milestone — it should not need to make an architectural decision Codex wasn't already given here. Claude reviews the resulting diff against this roadmap and the locked architecture before a milestone is considered done, and handles any milestone marked Claude-led directly.

---

## M0 — Repository Scaffolding & Token Pipeline

**Goal:** stand up the monorepo skeleton exactly as defined in [`REPOSITORY_STRUCTURE.md`](./REPOSITORY_STRUCTURE.md), with the Style Dictionary token pipeline producing real generated output from the locked `design-tokens.json`.

**Dependencies:** none — this is the first milestone.

**Deliverables:** empty-but-building `backend/`, `web/`, `mobile/shared`, `mobile/androidApp`, `mobile/iosApp` projects (each runs/builds with no real features); `tools/token-pipeline` generating `web/styles/tokens.css`, `androidApp/.../MentoraTokens.kt`, `iosApp/.../MentoraTokens.swift`, `MentoraColors.xcassets`; `infra/docker/docker-compose.yml` bringing up a local MongoDB (optional convenience — a native local MongoDB Community install is an equally valid alternative, per [`DEPLOYMENT.md § 1`](./DEPLOYMENT.md)) and creating the local media-storage root directory used by [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md); base GitHub Actions workflows (can be no-op/build-only initially, no deploy step — see [`DEPLOYMENT.md § 5`](./DEPLOYMENT.md)).

**Acceptance criteria:** all five app projects build/run against placeholder content; regenerating tokens from `design-tokens.json` produces byte-identical output to what's committed (CI check per [ADR-011](./adr/ADR-011-design-token-pipeline.md) passes); `docker-compose up` (or a native local MongoDB install) brings up a working local MongoDB reachable at `mongodb://localhost:27017`.

**Tests:** the token-pipeline CI check itself is the test for this milestone (no product logic exists yet to unit-test).

**Repository area:** all of it, structurally, none of it, functionally.

**Owner:** **Codex** (scaffolding is repetitive, well-specified, low-ambiguity — exactly where Codex's implementation throughput matters most). Claude reviews the resulting structure against [`REPOSITORY_STRUCTURE.md`](./REPOSITORY_STRUCTURE.md) before sign-off.

---

## M1 — Backend Foundation

**Goal:** a running Ktor application with the plugin stack from [`BACKEND_ARCHITECTURE.md § 3`](./BACKEND_ARCHITECTURE.md) wired (CallId, CallLogging, CORS, StatusPages, RequestValidation, RateLimit skeleton), MongoDB connectivity, config loading, and `/healthz`.

**Dependencies:** M0.

**Deliverables:** `Application.kt` + `plugins/`; `config/` typed loader; `/healthz` returning real Mongo connectivity status; structured JSON logging configured.

**Acceptance criteria:** `/healthz` returns `200` locally and against a deliberately-broken Mongo URI returns `503` naming the failure; a malformed request to any (placeholder) route returns the fixed error envelope from [`API_CONTRACT.md § 3`](./API_CONTRACT.md), not a raw stack trace.

**Tests:** integration test hitting `/healthz` with the real local MongoDB instance up and (separately) down.

**Repository area:** `backend/`.

**Owner:** **Codex**, reviewed by Claude for adherence to [`BACKEND_ARCHITECTURE.md`](./BACKEND_ARCHITECTURE.md)'s layering rules before later modules build on top of it.

---

## M2 — Auth & RBAC

**Goal:** register/login/logout, JWT + rotating refresh tokens, role gating — the full [`AUTH_SECURITY.md`](./AUTH_SECURITY.md) design, working end-to-end against a raw HTTP client (no UI yet).

**Dependencies:** M1.

**Deliverables:** `auth` module (routes/service/repository); `users` module (minimal profile read); `refreshTokens` collection + TTL index; the CSRF header check; rate limiting on `/auth/*`.

**Acceptance criteria:** register → login → authenticated request → refresh → logout all work via integration tests; a revoked/reused refresh token is rejected and its family revoked (§ [`AUTH_SECURITY.md § 4`](./AUTH_SECURITY.md)); a Student token cannot hit a stubbed Instructor-only route.

**Tests:** integration tests covering the full token lifecycle including the reuse-detection breach path — this is security-critical and must not be unit-tested-only.

**Repository area:** `backend/` (`auth`, `users` modules).

**Owner:** **Codex implements**, **Claude reviews directly** — this is exactly the security-critical, high-consequence-if-wrong code the brief's Claude-vs-Codex allocation calls out for closer supervision, not routine sign-off.

---

## M3 — Web App Shell

**Goal:** Next.js project with the route-group structure from [`WEB_ARCHITECTURE.md § 2`](./WEB_ARCHITECTURE.md), i18n routing (`next-intl`, `en`/`ar`), theming wired to generated tokens, and a working Login/Register against M2's backend.

**Dependencies:** M0 (tokens), M2 (auth backend).

**Deliverables:** `app/[locale]/...` route groups (empty placeholder pages except Login/Register); `middleware.ts` (locale detection + auth gate); Tailwind config on generated CSS vars; light/dark theme toggle; the same-origin API proxy rewrite.

**Acceptance criteria:** switching `/en` ↔ `/ar` flips `dir` and renders translated placeholder strings; Login/Register work end-to-end with real cookies set; an unauthenticated request to an `(app)` route redirects to Login.

**Tests:** Playwright smoke test for the language switch and the auth gate redirect (early instances of the § `TESTING_STRATEGY.md § 6` suite).

**Repository area:** `web/`.

**Owner:** **Codex**, Claude spot-reviews the i18n/RTL wiring specifically against [`LOCALIZATION_ARCHITECTURE.md`](./LOCALIZATION_ARCHITECTURE.md) since it's foundational to every later Web milestone.

---

## M4 — Mobile App Shells

**Goal:** `shared` KMP module with the networking client + auth/session orchestration from [`KMP_ARCHITECTURE.md`](./KMP_ARCHITECTURE.md); Android Compose shell with bottom nav + Login/Register; iOS SwiftUI shell with tab bar + Login/Register, SKIE wired.

**Dependencies:** M0 (tokens), M2 (auth backend).

**Deliverables:** `shared/domain`, `shared/data/network`, `shared/auth` (with Android/iOS `actual` secure-storage implementations); `androidApp` bottom-nav shell; `iosApp` tab-bar shell; both consuming `shared`'s auth use cases for a working Login/Register.

**Acceptance criteria:** both apps register/login/logout against the same backend M2 stood up; a token is stored in platform secure storage (verified by inspection, not just "it works"); iOS consumes `shared`'s suspend functions as `async`/`await` (SKIE working correctly).

**Tests:** `commonTest` for the auth use cases/session state machine; a smoke UI test per platform for the login screen.

**Repository area:** `mobile/`.

**Owner:** **Codex**, Claude reviews the `shared`/`androidApp`/`iosApp` boundary specifically (is any UI code leaking into `shared`? per [ADR-002](./adr/ADR-002-kmp-sharing-boundary.md)) before this becomes the foundation every later mobile milestone builds on.

---

## M5 — Course Domain & Discovery

**Goal:** `courses`/`categories` backend modules; Explore, Course Details on Web + both mobile apps, Guest and Student-aware.

**Dependencies:** M3, M4 (client shells), M2 (auth, for enrollment-awareness).

**Deliverables:** full `courses`/`categories` CRUD (Instructor-write paths stubbed for later — M9 — but read/browse/search/filter working now); Explore + Course Details screens across all three clients, with seed data from `infra/docker/mongo-init`.

**Acceptance criteria:** search/filter/browse work identically (same results, same behavior) across Web/Android/iOS against the same backend, per [`../product/PRODUCT_SPEC.md § 10`](../product/PRODUCT_SPEC.md)'s "same product on three platforms" requirement; Guest sees full course details without being able to access learning content.

**Tests:** backend integration tests for search/filter/text-index behavior; one Playwright E2E ("Explore → Course Details" from § `TESTING_STRATEGY.md § 6`).

**Repository area:** `backend/` (`courses`, `categories`), `web/`, `mobile/`.

**Owner:** **Codex.**

---

## M6 — Demo Checkout & Enrollment

**Goal:** the full simulated purchase flow, backend + all three clients, per [`../product/DEMO_PAYMENT_FLOW.md`](../product/DEMO_PAYMENT_FLOW.md).

**Dependencies:** M5.

**Deliverables:** `enrollment` module (idempotent checkout-completion endpoint, transactional per [`BACKEND_ARCHITECTURE.md § 4`](./BACKEND_ARCHITECTURE.md)); Demo Checkout + Purchase Success screens across all three clients.

**Acceptance criteria:** completing checkout twice for the same course never creates two enrollments (idempotency integration test); **no code path in this milestone's diff references a real payment gateway, card field, or financial credential** — this is explicitly checked, not assumed.

**Tests:** the idempotency integration test above; the "Demo Purchase → Enrollment" Playwright E2E flow.

**Repository area:** `backend/` (`enrollment`), `web/`, `mobile/`.

**Owner:** **Codex implements**, **Claude reviews the diff specifically for the "never a real payment code path" boundary** before sign-off — a narrow, high-importance review, not a full re-implementation.

---

## M7 — Course Player & Progress

**Goal:** `progress` backend module (server-authoritative completion/resume, per [`ARCHITECTURE.md § 4.1`](./ARCHITECTURE.md)); Course Player across all three clients (Web two-column, mobile with Curriculum `BottomSheet` per [`../ux/MOBILE_UX.md § 6-7`](../ux/MOBILE_UX.md)); video playback wired to [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md)'s signed-URL flow (video content itself can be seed/placeholder video files for this milestone — full Instructor upload comes in M11).

**Dependencies:** M5, M4.

**Deliverables:** lesson-complete + playback-position endpoints; progress recalculation logic; Course Player UI (video, lesson list/bottom sheet, progress bar, mark-complete/auto-advance) on all three clients; resume-from-last-position working across a simulated "different device" (same account, different client).

**Acceptance criteria:** completing a lesson on one client is reflected in progress on another client for the same account (the cross-platform-progress requirement, tested directly); resume lands on the exact last lesson/position.

**Tests:** backend integration tests for progress recalculation; "Start/Resume Course" and "Complete a Lesson" Playwright E2E flows; a cross-client manual verification note in the milestone's acceptance (automating a true cross-client E2E test is disproportionate effort for MVP — a documented manual check is acceptable here).

**Repository area:** `backend/` (`progress`), `web/`, `mobile/`.

**Owner:** **Codex.**

---

## M8 — Quiz Engine

**Goal:** `quiz` backend module (including the security-critical `isCorrect`-stripping projection, [`DATABASE_MODEL.md § 7`](./DATABASE_MODEL.md)); Quiz + Quiz Results screens across all three clients.

**Dependencies:** M7 (quiz is gated on all-lessons-complete).

**Deliverables:** quiz-taking + grading + attempt-history endpoints; Quiz/Quiz Results UI with icon+text+color correctness display (never color-only, per [`../design-system/ACCESSIBILITY.md § 8`](../design-system/ACCESSIBILITY.md)); retry flow.

**Acceptance criteria:** a network inspection of the student-facing quiz-fetch response contains no `isCorrect` field anywhere (explicitly checked, since this is a real security requirement, not a UI nicety); failing routes to Retry; passing triggers course completion if this was the last requirement.

**Tests:** the `isCorrect`-stripping integration test (§ [`TESTING_STRATEGY.md § 1`](./TESTING_STRATEGY.md)); "Complete a Quiz" Playwright E2E flow.

**Repository area:** `backend/` (`quiz`), `web/`, `mobile/`.

**Owner:** **Codex implements**, **Claude reviews the `isCorrect`-stripping projection specifically** — a narrow, security-focused review.

---

## M9 — Certificates

**Goal:** `certificates` backend module (denormalized-snapshot issuance, transactional with the completing progress update per [`BACKEND_ARCHITECTURE.md § 4`](./BACKEND_ARCHITECTURE.md)); Certificates List + Certificate Detail across all three clients; client-side certificate rendering + share/download affordance (UI-only, per [`../product/PRODUCT_SPEC.md § 13`](../product/PRODUCT_SPEC.md)).

**Dependencies:** M7, M8.

**Deliverables:** certificate issuance on course completion (with or without a quiz, per both completion paths in [`../product/USER_FLOWS.md § 17`](../product/USER_FLOWS.md)); `CertificateCard` detail rendering; a client-side image export for "Share"/"Download" (no server-side PDF pipeline).

**Acceptance criteria:** a course with no quiz completes and issues a certificate on last-lesson-complete alone; a course with a quiz only completes after a passing attempt; editing the source course's title after issuance does not change an already-issued certificate's displayed title (verifies the snapshot design).

**Tests:** the "Course Completion → Certificate" Playwright E2E flow, including both completion paths (with/without quiz).

**Repository area:** `backend/` (`certificates`), `web/`, `mobile/`.

**Owner:** **Codex.**

---

## M10 — Learning Paths

**Goal:** `learningPaths` module (Admin-seeded, per [`DATABASE_MODEL.md § 9`](./DATABASE_MODEL.md)); Learning Paths browse/detail/follow across all three clients; derived path-level progress.

**Dependencies:** M5 (courses must exist to be referenced by a path), M7 (progress must exist to derive path completion from).

**Deliverables:** follow/unfollow endpoints; Learning Paths + Learning Path Details screens; path-level progress bar computed on read.

**Acceptance criteria:** following a path surfaces it in My Learning; path progress accurately reflects member-course completion without a separately-stored, driftable value.

**Tests:** backend test for the derived-progress computation logic.

**Repository area:** `backend/` (`learningpaths`), `web/`, `mobile/`.

**Owner:** **Codex.**

---

## M11 — Instructor Web

**Goal:** the full Instructor authoring surface — Dashboard, Course Editor (Overview/Curriculum), Lesson Editor, Quiz Editor, media upload — Web only, per [`../product/USER_ROLES.md`](../product/USER_ROLES.md).

**Dependencies:** M5 (courses backend already supports read; this milestone completes the write/authoring path), [`MEDIA_ARCHITECTURE.md`](./MEDIA_ARCHITECTURE.md)'s upload flow (built here, first real usage).

**Deliverables:** `media` module (local-filesystem upload handling via the `MediaStorage` abstraction + signed playback-URL issuance, § [`MEDIA_ARCHITECTURE.md § 1, § 3, § 5`](./MEDIA_ARCHITECTURE.md)); course/section/lesson/quiz write endpoints with ownership checks; the five Instructor screens with `ReorderableList`, `FileUpload`, `Toggle` publish control, and the publish-readiness validation from [`../product/USER_FLOWS.md § 26`](../product/USER_FLOWS.md).

**Acceptance criteria:** an Instructor can create a course end-to-end (metadata → sections → lessons with real uploaded video → quiz) and publish it, at which point it becomes visible in Explore for a Student; Publish is blocked with a visible, specific reason until requirements are met; unpublishing preserves existing enrolled students' access.

**Tests:** the full "Instructor Course Authoring" Playwright E2E flow; a backend integration test for the publish-validation rule and for the local upload flow (writing to a CI-ephemeral storage root, per [`DEPLOYMENT.md § 5`](./DEPLOYMENT.md)).

**Repository area:** `backend/` (`media`, `courses` write paths, `quiz` write path), `web/`.

**Owner:** **Codex.**

---

## M12 — Admin Web

**Goal:** Admin Dashboard, Manage Courses/Users/Instructors/Categories.

**Dependencies:** M11 (courses/instructors exist to manage).

**Deliverables:** `admin` module (aggregation reads + unpublish moderation action); the four Admin list screens using `DataTable`, plus the plain-list Categories screen.

**Acceptance criteria:** Admin can search/filter/unpublish any course regardless of owning Instructor; Admin cannot edit course content directly (verified as a negative test — the edit endpoint rejects an Admin principal); category deletion is blocked when in use, with a clear reason shown.

**Tests:** the "Admin Course Management" Playwright E2E flow; a backend authorization test confirming Admin's read-only boundary on course content.

**Repository area:** `backend/` (`admin`), `web/`.

**Owner:** **Codex.**

---

## M13 — AI Tutor

**Goal:** the full flow from [`AI_TUTOR_ARCHITECTURE.md`](./AI_TUTOR_ARCHITECTURE.md) — `aitutor` backend module, AI Tutor screens on all three clients, using the Anthropic Claude API as approved per [ADR-009](./adr/ADR-009-ai-provider-abstraction.md).

**Dependencies:** M7 (lesson-context needs enrollment/lesson data). AI provider selection is resolved (Anthropic Claude API, approved 2026-09-04 — see [`ADR_INDEX.md`](./ADR_INDEX.md)) and no longer blocks starting this milestone.

**Deliverables:** conversation/message persistence; the concrete `AiProvider` implementation; lesson-context resolution + enrollment check; rate limiting; the five quick actions; AI Tutor chat UI on all three clients (global + lesson-context entry points).

**Acceptance criteria:** a lesson-context message correctly injects that lesson's content only after verifying enrollment (tested with an unenrolled principal → `403`); "Quiz me" never creates a `Quiz`/`QuizAttempt` record (explicitly checked); no AI provider key appears in any client bundle/binary (checked by inspecting build output, not just source).

**Tests:** the enrollment-gate integration test above; a `commonTest` for the quick-action prompt-construction logic; manual verification of streaming UX per client (automated streaming-response E2E testing is lower-value effort here — a documented manual check suffices for MVP).

**Repository area:** `backend/` (`aitutor`), `web/`, `mobile/`.

**Owner:** **Codex implements**, **Claude reviews the enrollment-gate and key-handling boundaries directly** — the two places a mistake here has real security/cost consequences, per the brief's explicit "AI endpoint protection" and "keys never reach clients" requirements.

---

## M14 — Localization Completion Pass

**Goal:** full English/Arabic string coverage across all 29 screens, all six surfaces, and an explicit RTL QA pass.

**Dependencies:** every prior UI milestone (M3–M13) — this is a horizontal pass across everything already built, not a new vertical feature.

**Deliverables:** every UI string in every client's resource file (§ [`LOCALIZATION_ARCHITECTURE.md § 2`](./LOCALIZATION_ARCHITECTURE.md)) has a real Arabic translation (not a placeholder); every screen manually verified under `ar`/RTL for layout correctness (per the checklist already codified in [`../design-system/DESIGN_RULES.md`](../design-system/DESIGN_RULES.md)'s "Before Any Screen Is Designed or Implemented" checklist, applied retroactively as an audit here).

**Acceptance criteria:** no hardcoded English string remains reachable in the `ar` locale (a CI lint pass + manual audit); the video scrubber and wordmark are confirmed still LTR/unmirrored under `ar`; locale-aware formatting (§ [`LOCALIZATION_ARCHITECTURE.md § 5`](./LOCALIZATION_ARCHITECTURE.md)) is verified on real screens (dates, prices, durations) in both locales.

**Tests:** the "Language switch English ↔ Arabic" Playwright E2E flow, extended to assert a sampling of screens beyond Settings itself; a lint/CI check for missing translation keys per platform (§ [`LOCALIZATION_ARCHITECTURE.md § 2`](./LOCALIZATION_ARCHITECTURE.md)).

**Repository area:** all clients.

**Owner:** **Codex implements the translation/fix work**, **Claude supervises the audit checklist and signs off** — this is a completeness/quality gate more than a feature build, well-suited to Claude's review role.

---

## M15 — Testing Hardening

**Goal:** fill out the full test suite per [`TESTING_STRATEGY.md`](./TESTING_STRATEGY.md) to its target coverage — every milestone above included its own critical-path tests, but this pass closes remaining gaps (edge cases, error-path coverage, the full Playwright suite running reliably in CI).

**Dependencies:** all feature milestones.

**Deliverables:** complete backend integration suite per module; `commonTest` coverage for all `shared` use cases; the full ten-flow Playwright suite green in CI, consistently (no flaky tests tolerated as "normal").

**Acceptance criteria:** CI is green on a clean run, twice in a row (flakiness check); a deliberately-introduced regression in a reviewed PR (a standard "does the test suite actually catch things" sanity check performed once, then reverted) is caught by the suite.

**Repository area:** all.

**Owner:** **Codex implements test code**, **Claude reviews test quality** — per the loaded `test-guard` review discipline, checking for real assertions vs. superficial coverage padding, not just a green checkmark.

---

## M16 — Local Demo Readiness

**Goal:** the full local stack from [`DEPLOYMENT.md`](./DEPLOYMENT.md) — local MongoDB, local backend, local media storage, all three clients — verified to build, run, and demo end-to-end on the developer's own machine, per [ADR-012](./adr/ADR-012-local-demo-scope.md)'s locked local-only MVP scope. **No cloud deployment, hosting account, or app-store distribution step is part of this milestone.**

**Dependencies:** all feature milestones (verifying a local demo of an incomplete product isn't useful).

**Deliverables:** documented, one-command (or clearly-stepped) local environment startup — MongoDB Community running locally (native install or `docker-compose up`), the backend started locally, the local media-storage root seeded with real-looking demo thumbnails/videos; `API_BASE_URL` configuration verified for all four local-networking targets from [`DEPLOYMENT.md § 4a`](./DEPLOYMENT.md) — Web browser, Android emulator, Android/iOS physical device on the same LAN, iOS simulator; all five GitHub Actions workflows (build/lint/test only, no deploy step, per [`DEPLOYMENT.md § 5`](./DEPLOYMENT.md)) running on every PR; a local run-verification pass per client:
- **Web local run verification** — `npm run dev` reaches the local backend and completes the portfolio-priority flow.
- **Android local run verification** — the app builds and runs against the local backend via the emulator's `10.0.2.2` address (or a LAN address on a physical device).
- **iOS local/simulator run verification** — the app builds and runs against the local backend via the simulator's `localhost` address (or a LAN address on a physical device).
- **Full local end-to-end demo verification** — the complete portfolio-priority flow (Discovery → Course Details → Demo Checkout → Purchase Success → Course Player → Progress → Quiz → AI Tutor → Certificate) walked through on each client against the same local backend, in both languages.

**Acceptance criteria:** a developer following [`DEPLOYMENT.md § 1-2`](./DEPLOYMENT.md) from a clean checkout can bring up the local backend + MongoDB + media storage and complete the full portfolio-priority flow on Web, Android (emulator), and iOS (simulator) without any cloud account, hosting service, or store distribution step.

**Repository area:** `infra/`, `.github/workflows/`, plus this document's own local-run verification notes — no cloud/platform-side account configuration.

**Owner:** **Codex implements the local-config/verification work** (docker-compose, seed scripts, per-client base-URL wiring), **Claude supervises/reviews directly** — this milestone is what a portfolio evaluator actually runs, so it gets the same close review the former deployment milestone would have.

---

## M17 — Final Polish & Demo Readiness

**Goal:** a Claude-led pass focused on the "portfolio priority" flows specifically — visual polish, accessibility audit (contrast/focus/touch-target/screen-reader spot checks against [`../design-system/ACCESSIBILITY.md`](../design-system/ACCESSIBILITY.md)), performance sanity check (page load, video start time, AI Tutor response latency), and a final cross-check of every item in [`ARCHITECTURE.md § 5`](./ARCHITECTURE.md)'s "what this phase did not do" against what was actually built (confirming, e.g., that no real payment code path was accidentally introduced somewhere over 16 milestones of implementation).

**Dependencies:** all prior milestones.

**Deliverables:** a punch-list of polish items, triaged and either fixed directly (Claude, for small targeted fixes) or delegated back to Codex (for larger fixes) per the same allocation principle used throughout.

**Acceptance criteria:** the nine-step portfolio-priority flow (§ [`../product/PRODUCT_SPEC.md § 3`](../product/PRODUCT_SPEC.md)) is demo-ready end-to-end, in both languages, on all three clients.

**Repository area:** all, targeted.

**Owner:** **Claude-led**, with Codex handling any larger fixes Claude's audit surfaces — this is explicitly the milestone where Claude's supervisory role is the primary mode of work, not a review layer over Codex's.

---

## Summary Table

| # | Milestone | Depends on | Primary owner |
|---|---|---|---|
| M0 | Repo scaffolding & token pipeline | — | Codex |
| M1 | Backend foundation | M0 | Codex |
| M2 | Auth & RBAC | M1 | Codex + Claude review |
| M3 | Web app shell | M0, M2 | Codex |
| M4 | Mobile app shells | M0, M2 | Codex |
| M5 | Course domain & discovery | M2, M3, M4 | Codex |
| M6 | Demo checkout & enrollment | M5 | Codex + Claude review |
| M7 | Course player & progress | M4, M5 | Codex |
| M8 | Quiz engine | M7 | Codex + Claude review |
| M9 | Certificates | M7, M8 | Codex |
| M10 | Learning paths | M5, M7 | Codex |
| M11 | Instructor Web | M5, media architecture | Codex |
| M12 | Admin Web | M11 | Codex |
| M13 | AI Tutor | M7 (provider decision resolved — Anthropic Claude API) | Codex + Claude review |
| M14 | Localization completion pass | M3–M13 | Codex + Claude supervision |
| M15 | Testing hardening | all feature milestones | Codex + Claude review |
| M16 | Local demo readiness | all feature milestones | Codex + Claude supervision |
| M17 | Final polish & demo readiness | all | **Claude-led** |

**18 milestones total (M0–M17).** Dependencies flow strictly forward with one explicit exception noted inline (M13 is blocked on your AI provider decision, not on any prior milestone's code). No milestone requires an architectural decision not already locked in this directory — where one genuinely couldn't be locked (AI provider), the blocking dependency is stated explicitly rather than left implicit. No milestone in this roadmap includes a cloud deployment step, a managed-service provisioning step, or an app-store distribution step, per [ADR-012](./adr/ADR-012-local-demo-scope.md)'s locked local-only MVP scope.
