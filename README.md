# Mentora

Mentora is a full-stack, cross-platform e-learning platform built end-to-end as a local-only portfolio
project: a Kotlin/Ktor backend, a Next.js website, a native Android app, and a Kotlin Multiplatform (KMP)
shared core, all sharing one MongoDB-backed domain (courses, enrollment, progress, quizzes, certificates,
an AI tutor) across four student/instructor/admin-facing surfaces. It supports English/Arabic
localization with full RTL layout, light/dark theming, and a locked, versioned design system applied
consistently across every client.

An iOS app exists but is intentionally frozen partway through (see **Scope and known limitations**
below) — it is not part of the supported demo surface.

## Quick start

The whole local stack (MongoDB check + backend + website) starts with two scripts at the repo root:

```powershell
.\start-mentora.ps1     # start everything (safe to re-run — skips anything already running)
.\stop-mentora.ps1      # stop what start-mentora.ps1 started (MongoDB service is left running)
```

If PowerShell blocks running them directly: `powershell -ExecutionPolicy Bypass -File .\start-mentora.ps1`.

This assumes MongoDB is already installed locally as a single-node replica set (required for the real
multi-document transactions the backend uses) and the backend's `.env` is configured. For first-time
setup, prerequisites, and running each piece independently, see:

- [`backend/README.md`](backend/README.md) — backend + MongoDB setup, running, and testing
- [`web/README.md`](web/README.md) — website setup, running, and testing (Playwright E2E + Vitest unit)
- [`mobile/androidApp/README.md`](mobile/androidApp/README.md) — Android app build/run/test

## Architecture and project history

This project was built in explicit, sequential phases, each with its own design doc, implementation
plan, and acceptance criteria under [`execution/`](execution/):

1. **Backend** (Ktor, MongoDB, JWT auth, courses/enrollment/progress/quiz/certificate domains)
2. **Website** (Next.js 15 / React 19 — public, student, instructor, and admin experiences)
3. **KMP shared core** (Kotlin Multiplatform models/networking/business logic shared with Android/iOS)
4. **Android** (native Jetpack Compose app, built on the KMP shared core)
5. **iOS** — *deferred/partial* (see below)
6. **AI Tutor** (an Anthropic-backed, read-and-explain-only tutoring assistant with prompt-injection
   hardening, deployed behind a mock/fake provider for local demo use)
7. **Full integration** (cross-client parity, end-to-end acceptance across Backend/Website/Android/KMP)
8. **QA, polish & portfolio demo** (this phase — the final one; no further phase follows it)

The full design/architecture reasoning lives under [`architecture/`](architecture/) (system design,
API contract, database model, tech stack, testing strategy, deployment model) and
[`design-system/`](design-system/) (the locked, versioned visual language every client conforms to).
`execution/PHASE_HANDOFF.md` and `execution/DECISIONS_LOG.md` record the complete history of what was
built, why, and every material decision made along the way, phase by phase.

## Scope and known limitations

This is a local-only demo/portfolio project, not a deployed production service — there is no real
payment processor, no cloud infrastructure, and no production backend. These are deliberate, disclosed
scope boundaries, not oversights:

- **iOS is deferred/partial by explicit decision.** Early implementation work is done and CI-verified;
  the remaining build-out was intentionally not pursued. iOS is excluded from the supported demo surface
  (Website, Android, Backend, and the KMP shared core are the four supported/demo platforms).
- **Real AI-provider verification is deferred by explicit decision.** The AI Tutor's real Anthropic
  integration is implemented and code-reviewed, but live verification against a real provider credential
  was deliberately not pursued to avoid spending API cost/credentials on a local demo project. The tutor
  runs against a mock/fake provider for local use; no live-provider secret is configured anywhere in this
  repository.
- **No real payment processing exists anywhere** — checkout is a disclosed demo flow, verified by a
  repo-wide scan to contain no real payment-provider code.
- **Seeded demo data intermixes with accumulated automated-test data** in the local database from this
  project's own QA runs (Playwright/Postman/manual repro accounts and throwaway courses). The seed data
  itself is demo-quality; a deliberate cleanup pass (see `web/README.md`'s "Test data cleanup" section)
  should be run against a fresh local database immediately before any live walkthrough.

## Testing

Each client's own README documents its exact test suite and how to run it. As of Phase 8: backend
integration/unit tests, KMP (`:shared`) tests, Android unit tests, and a Playwright end-to-end suite
(chromium) plus a bounded Vitest/React Testing Library unit tier all pass, and are wired into GitHub
Actions CI (`backend-ci.yml`, `web-ci.yml`, `android-ci.yml`, `tokens-ci.yml`; `ios-ci.yml` covers the
iOS app's own frozen scope).
