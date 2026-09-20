# Mentora Web — Local Run Instructions

Phase 2 scope: the Next.js 15 App Router website (public/Student/Instructor/Admin surfaces, `en`/`ar`
locales). It talks to the Phase 1 backend over HTTP — nothing here runs standalone. See
`backend/README.md` for backend setup; this file assumes that's already done.

## Quick start: one-command local environment (backend + website)

Two scripts at the repo root start/stop MongoDB check, backend, and website together, waiting for both
`/healthz` (backend) and `/en` (website) to actually respond before the environment is reported ready:

```
.\start-mentora.ps1     # start everything (safe to re-run — skips anything already running)
.\stop-mentora.ps1      # stop the backend/website processes these scripts started (MongoDB is left running)
```

If PowerShell's execution policy blocks running them directly, either run once with:
```
powershell -ExecutionPolicy Bypass -File .\start-mentora.ps1
```
or set the policy for your user permanently: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

They don't replace the manual steps below — read on if you're setting up the website for the first time
(dependencies, token/design-to-code generation) or want to run its own commands (lint, build, E2E) directly.

## Prerequisites

- **Node.js 20+** (Next.js 15's minimum). Verify: `node --version`.
- The Phase 1 backend running and reachable at `http://localhost:8080` (see `backend/README.md`) —
  `next.config.ts`'s `rewrites()` proxies `/api/*` there. Most of this app is unusable without it (every
  screen beyond the static shell fetches real data).

## Install

```
cd web
npm install
```

## Generate tokens / design-to-code artifacts

Two codegen steps produce checked-in generated files from the locked/maintained source-of-truth JSON
under `design-tokens.json`/`design-to-code/`. Both are safe to re-run any time those source files change;
neither needs to run for a plain `npm run dev`/`npm run build` unless you've edited one of those sources:

```
npm run generate-tokens           # design-tokens.json -> styles/tokens.css, tailwind-theme.css, design-tokens.generated.ts
npm run generate:design-to-code   # design-to-code/{shared,screens}/*.json -> src/lib/design-to-code.generated.ts
npm run validate:design-to-code   # checks the design-to-code/ source tree is internally consistent (no codegen)
```

## Run

```
npm run dev     # dev server, http://localhost:3000, hot reload
npm run build   # production build (all locales) — stop the dev server first, see the note below
npm run start   # serve a production build (run `build` first)
```

**Never run `npm run build` while `npm run dev` is already running against the same `.next` directory** —
this corrupts the dev server's build cache (`Cannot find module './vendor-chunks/...'` on the next request)
and the only fix is stopping both, deleting `.next`, and restarting `dev` clean. This has been hit and
documented several times during Phase 2 (`execution/DECISIONS_LOG.md` D49/D58/D59/D64) — `stop-mentora.ps1`
before a `build`, `start-mentora.ps1` after, is the safe sequence.

## Quality gates

```
npm run typecheck               # tsc --noEmit
npm run lint                    # eslint . (one pre-existing warning on course-thumbnail.tsx is expected — see its own comment)
npm run lint:logical-properties # fails if any physical-direction (left/right) CSS exists under src/ — this app is RTL-first
```

## End-to-end tests (Playwright)

`web/e2e/` has one spec file per priority flow in `architecture/TESTING_STRATEGY.md § 6` (Register/Login,
Explore → Course Details, Demo Purchase → Enrollment, Start/Resume Course, Complete a Lesson, Complete a
Quiz, Course Completion → Certificate, Language switch, Instructor Course Authoring, Admin Course
Management). These run against a **real** backend + MongoDB — never mocked — so the full local environment
(`start-mentora.ps1`) must already be up first.

```
npx playwright install chromium firefox webkit --with-deps   # one-time browser download
npm run test:e2e -- --project=chromium                       # or --project=firefox / --project=webkit
```

**Browser status, as of Task 14 (`execution/DECISIONS_LOG.md` D64):**

| Project | Status |
|---|---|
| `chromium` | ✅ All 19 tests passing |
| `firefox` | ✅ All 19 tests passing |
| `webkit` | ❌ Blocked — see below, don't run this one expecting a clean result |

WebKit fails almost the entire suite for a real, investigated, non-product reason: the backend correctly
sets session cookies with `secure=true`/`SameSite=Lax` (the right choice for a real HTTPS deployment), but
Playwright's WebKit engine applies a stricter interpretation of `Secure`/`SameSite` cookies over this local
stack's plain-HTTP `http://localhost` than Chromium/Firefox's well-known "localhost is trustworthy"
relaxation — the cookie is set but the very next request doesn't reliably see it as `Lax`, so every
authenticated flow bounces back to `/login`. This is not something to fix by weakening the cookie
configuration. It would very likely resolve itself once the local stack is served over HTTPS, or in a real
CI environment behind a TLS-terminating proxy — neither is set up yet. `web/e2e/helpers.ts` carries the
same explanation next to the code it affects.

The suite also shares a real, intentional backend rate limit (`backend/.../plugins/RateLimiting.kt`'s
`"auth"` bucket, 10 requests/minute) — running the full suite registers ~15-19 fresh accounts, so
`registerNewStudent`/`login` in `helpers.ts` retry with backoff exactly as a real client would rather than
the limit being raised for tests. A full run can take longer than its raw step count suggests if it's run
back-to-back with another recent run (e.g. immediately after another `--project=` run against the same
backend) — this is expected, not a hang.

Test data cleanup: each run creates fresh Student accounts (`*@e2e.mentora.test`) and, in the Instructor
Authoring and Admin flows, real courses (titled `"E2E ..."`). These accumulate in the local dev MongoDB
across repeated runs — there's no dedicated ephemeral test database in this project (see
`execution/DECISIONS_LOG.md` D34: no Docker/infra in Phase 1). If the accumulated data starts noticeably
slowing down the Instructor Dashboard or cluttering Explore, clear it directly, matched strictly by the
suite's own naming convention so real seed data is never touched:

```
mongosh mongodb://localhost:27017/mentora?replicaSet=rs0
> const userIds = db.users.find({email: /@e2e\.mentora\.test$/}, {_id:1}).toArray().map(d => d._id)
> const courseIds = db.courses.find({title: /^E2E /}, {_id:1}).toArray().map(d => d._id)
> db.enrollments.deleteMany({$or: [{userId: {$in: userIds}}, {courseId: {$in: courseIds}}]})
> // ...and similarly for progress/certificates/quizAttempts/refreshTokens/demoPurchases/media, then courses and users last
```

## CI

`.github/workflows/web-ci.yml` (Phase 7) runs two jobs on every push/PR touching `web/**`,
`backend/**`, or `design-system/**`:

- **`web-static`** — `npm ci`, `npm run lint`, `npm run lint:logical-properties`,
  `npm run validate:design-to-code`, `npm run typecheck`. Fast, no backend needed. Deliberately has
  **no `npm run build` step** — the landing page is ISR (`export const revalidate = 300`) and
  `next build` performs one real, live server-side fetch during its initial prerender pass even for
  an "ISR" route, so a real build needs a live backend (see `execution/DECISIONS_LOG.md` D150).
- **`web-e2e`** — starts MongoDB as a real replica set + the backend (same as `backend-ci.yml`),
  builds the backend's fat jar (`./gradlew buildFatJar`), seeds demo data, starts it, then builds
  the site for real (`npm run build`, the one real production build in this project's CI) and runs
  the Playwright suite against it: `npx playwright test --project=chromium`.

**CI runs `chromium` only** (D64, D-E2E-BROWSER) — WebKit is known-red for the real, investigated,
non-product reason described above (Secure/SameSite cookie handling over plain-HTTP `localhost`);
Firefox is green locally but roughly doubles the job's wall-clock for near-zero marginal signal
beyond what chromium already proves. **Firefox/WebKit remain local/manual only** — run them
yourself with the commands above, don't expect either in a CI run.

The same test-data-pollution warning above applies doubly to a local E2E run against your own dev
database (`*@e2e.mentora.test` accounts, `E2E ...`-titled courses accumulate) — `web-e2e`'s own run
is isolated to a fresh CI-only MongoDB container and never touches your local dev database.

Design-token drift has its own separate, fifth workflow, `.github/workflows/tokens-ci.yml` — see
"Generate tokens / design-to-code artifacts" above; it regenerates every token output from
`design-system/design-tokens.json`/`themes/*.json` and fails if the committed files disagree.

## Project layout (Phase 2, web-only)

```
web/
  src/app/[locale]/...        Next.js App Router pages — thin wrappers around src/components/screens/*
  src/components/screens/     one screen component per product/SCREEN_INVENTORY.md entry
  src/components/ui/          design-system component kit (Button, TextField, DataTable, ...)
  src/components/navigation/  per-role shell components (AppShell/InstructorShell/AdminShell)
  src/lib/api/                one module per backend resource (TanStack Query hooks + typed fetch)
  src/lib/auth/                login/register/logout/current-user
  src/lib/design-to-code.generated.ts   generated — do not hand-edit, see generate:design-to-code above
  messages/{en,ar}.json       next-intl translation catalogs — kept in exact key parity (see D63)
  e2e/                        Playwright specs (see "End-to-end tests" above)
```

See `design-to-code/README.md` for the locked-source precedence rule this codebase follows
(Product/UX > Design System > Showcase > current implementation-as-evidence-only), and
`execution/INTEGRATION_CONTRACT.md` for the as-built API shapes this app's `src/lib/api/*` consumes.
