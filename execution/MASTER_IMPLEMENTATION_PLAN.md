# Mentora — Master Implementation Plan

**Status:** Living document. Updated as phases progress. Do not treat as a one-time artifact.

This plan maps the locked [`architecture/IMPLEMENTATION_ROADMAP.md`](../architecture/IMPLEMENTATION_ROADMAP.md) (18 milestones, M0–M17) into the 8 top-level implementation phases the user has mandated. **The 18-milestone roadmap is not discarded** — every milestone below is tracked as an internal task inside its owning phase(s). Several milestones span more than one phase because the roadmap originally interleaved backend + all three clients per feature, while the phase structure here separates backend-first, then per-client.

Claude (this session) is Lead/Supervisor: reads locked docs, plans, breaks down tasks, reviews Codex diffs, runs validation, resolves integration issues, maintains this continuity layer. Codex is the implementation workhorse for code-heavy, well-scoped, repetitive work, invoked via the `codex-delegate` skill with narrow, file-scoped prompts — never asked to make an architecture decision or rediscover the whole project.

---

## Phase → Milestone Map

### PHASE 1 — Backend Foundation & API
- **M0** (backend-relevant slice only): `backend/` Gradle project skeleton, typed config loader, `.env` strategy, connection to local MongoDB Community (native service, confirmed already running — see DECISIONS_LOG D1), `backend/storage/media/` root. *(Web/Android/iOS scaffolding and the token pipeline are explicitly OUT of Phase 1 — they belong to Phases 2–5.)*
- **M1** — Backend Foundation (Ktor plugin stack, `/healthz`, structured logging) — full.
- **M2** — Auth & RBAC — full (register/login/logout/refresh, JWT + rotating refresh tokens, role gating).
- **M5** (backend slice) — `courses`/`categories` modules: full CRUD, search/filter/text-index, publish/unpublish validation. (Web/mobile UI deferred to Phases 2/4/5.)
- **M6** (backend slice) — `enrollment` module: idempotent demo-checkout completion, `demoPurchases`/`enrollments`.
- **M7** (backend slice) — `progress` module: lesson-complete, playback-position heartbeat, completion recalculation.
- **M8** (backend slice) — `quiz` module: authoring + student-facing `isCorrect`-stripped fetch + grading + attempts.
- **M9** (backend slice) — `certificates` module: denormalized-snapshot issuance, transactional with completion.
- **M10** (backend slice) — `learningpaths` module: admin-seeded paths, follow/unfollow, derived progress.
- **M11** (backend slice only) — `media` module (local filesystem `MediaStorage` + signed playback URLs), course/section/lesson/quiz write endpoints with ownership checks, publish-readiness validation. *(Instructor Web UI is Phase 2.)*
- **M12** (backend slice only) — `admin` module: aggregation reads + unpublish moderation. *(Admin Web UI is Phase 2.)*
- **M13** (interface boundary only) — `aitutor` module scaffold: conversation/message persistence, the `AiProvider` interface + a stub/mock implementation, lesson-context resolution, enrollment gate, rate-limit plugin wiring. **Concrete Anthropic Claude API call is explicitly deferred to Phase 6.**
- **M15** (backend portion) — backend integration/unit test suite for every module above.
- **M16** (backend portion) — local run instructions for backend + MongoDB.

### PHASE 2 — Website
- **M3** — Web App Shell (Next.js, i18n routing, theming, Login/Register).
- **M5, M6, M7, M8, M9, M10** (web UI) — Explore, Course Details, Demo Checkout, Purchase Success, Course Player, Quiz, Certificates, Learning Paths.
- **M11** — Instructor Web (Dashboard, Course Editor, Lesson Editor, Quiz Editor, media upload UI).
- **M12** — Admin Web (Dashboard, Manage Courses/Users/Instructors/Categories).
- **M14** (web slice) — localization completion pass for all Web surfaces.
- **M15 / M16** (web portion) — Playwright E2E suite, web local-run verification.

### PHASE 3 — KMP Shared Mobile Core
- **M4** (shared-module slice) — `mobile/shared` domain models, use cases, Ktor Client networking, auth/session orchestration, `expect`/`actual` secure-storage boundary, platform-agnostic playback interface.

### PHASE 4 — Android
- **M4** (Android shell) — Compose bottom-nav shell, Login/Register.
- **M5–M10, M13** (Android UI) — Explore, Course Details, Checkout, Player, Quiz, Certificates, Learning Paths, AI Tutor chat UI.
- **M14 / M15 / M16** (Android portion) — localization, Compose UI tests, Android local-run verification.

### PHASE 5 — iOS
- **M4** (iOS shell) — SwiftUI tab-bar shell, SKIE, Login/Register.
- **M5–M10, M13** (iOS UI) — same feature set as Android, native SwiftUI.
- **M14 / M15 / M16** (iOS portion) — localization, XCUITest smoke tests, iOS local-run verification.

### PHASE 6 — AI Tutor Integration
- **M13** (completion) — concrete `AiProvider` implementation against the Anthropic Claude API (backend-only key), streaming wired end-to-end, AI Tutor chat UI wired on all three clients (built as shells in Phases 2/4/5, now connected to the real provider), the five quick actions, enrollment-gate + key-handling review.

### PHASE 7 — Full Integration
- Cross-client verification: same backend, same data, consistent behavior across Web/Android/iOS (per `PRODUCT_SPEC.md § 10`).
- **M16** (completion) — full local stack verification across all four networking targets (browser, Android emulator, physical device, iOS simulator), full portfolio-priority flow walked on every client in both languages.

### PHASE 8 — QA, Polish & Portfolio Demo
- **M14** (completion) — full EN/AR coverage audit across all 29 screens / 6 surfaces, RTL QA pass.
- **M15** (completion) — testing hardening, CI green twice in a row, flakiness/regression-catch sanity check.
- **M17** — final polish, accessibility/performance sanity pass, "what this phase did not do" cross-check (confirm no real payment code path ever introduced).

---

## Claude vs. Codex Allocation (carried from the roadmap, applied per-phase)

| Work type | Owner |
|---|---|
| Architecture consistency, module boundaries, transaction-boundary design | Claude |
| Auth/RBAC implementation (M2) | Codex implements, **Claude reviews directly** |
| Demo-checkout "never a real payment path" check (M6) | Codex implements, **Claude reviews the diff specifically** |
| Quiz `isCorrect`-stripping projection (M8) | Codex implements, **Claude reviews the projection specifically** |
| AI Tutor enrollment-gate + key-handling (M13/Phase 6) | Codex implements, **Claude reviews those two boundaries directly** |
| Routine CRUD modules, repositories, DTOs, tests | Codex, Claude reviews diff against this plan + locked architecture |
| Final polish pass (Phase 8 / M17) | **Claude-led**, Codex for larger fixes Claude's audit surfaces |
| Continuity docs (this directory) | Claude, always |

## Sequencing Rule

Do not start the next phase until the previous phase is `COMPLETE` and its `PHASE_HANDOFF.md` entry is complete, unless the user explicitly approves parallel work. Current sequencing: **Phases 1–7 are COMPLETE; Phase 8 is IN_PROGRESS.**

## Local Toolchain (confirmed at Phase 1 start — see DECISIONS_LOG)

- JDK 21 (Temurin) present.
- MongoDB Community 8.3 already installed and running as a local Windows service (`mongodb://localhost:27017`) — no Docker/docker-compose dependency needed for Phase 1.
- No system-wide Gradle/Kotlin CLI — the backend project will vendor the Gradle wrapper (`gradlew.bat`/`gradlew`).
- Codex CLI (`codex-cli 0.151.0`) available for delegated implementation work.
