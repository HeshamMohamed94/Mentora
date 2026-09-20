# Phase 7 — Full Integration — System Design

**Status:** Authored by the `architect` subagent on 2026-09-20, at Phase 7 kickoff, before any Phase 7
code change. Derived from `execution/PHASE_7_ACCEPTANCE_CRITERIA.md` (the authoritative, already-
reconciled scope statement — sections A-J, including section H's scope reconciliation) plus direct
inspection of the real codebase. **This document does not re-derive or re-litigate scope.** H1 (iOS
excluded) and H2 (the CI-workflow gap is real, in-scope, already-mandated M16 work) are taken as given.

**Companion document:** `execution/PHASE_7_IMPLEMENTATION_PLAN.md` — the sequenced T0-T13 task list.
Read that for *what to do in what order*; read this for *why, and exactly what shape*.

**Baseline at design time:** `main` @ `60e71b7`, clean, in sync with `origin/main`.
Next decision id: **D147**.

---

## 0. What this design covers

1. **Sections 2-6 — the CI workflows** (acceptance criteria I1-I3, plus a flagged fifth, section 6).
   Concrete, YAML-level: triggers, runner, env, every command, and the real environment problems each
   one has to solve (chiefly: this backend's test suite hard-requires a MongoDB **single-node replica
   set**).
2. **Section 7 — the Android auth-gate staleness bug** (criterion C4 / `DECISIONS_LOG.md` D144): the
   real root cause, found in the code, and a minimal targeted fix — not a redesign of auth state.
3. **Sections 8-9 — the cross-client verification methodology** (criteria A-G): per criterion, exactly
   what mechanism proves it, and what artifact counts as evidence.
4. **Section 10 — status corrections** to the acceptance-criteria file, confirmed against the code.
5. **Sections 11-12 — findings needing a human call, and risk/reversibility.**

Phase 7 introduces **no new product feature**. Every item below traces to an acceptance-criteria row
or to a locked-but-undone M16 deliverable.

---

## 1. Repo state actually verified at design time

Everything in this section was read, not assumed. It is the factual basis for sections 2-9.

### 1.1 Build/toolchain facts

| Fact | Where verified |
|---|---|
| Backend is a **standalone Gradle build** (`backend/settings.gradle.kts`, `rootProject.name = "mentora-backend"`) with its **own wrapper at Gradle 8.11**. It is *not* a subproject of `mobile/`. There is no repo-root Gradle build. | `backend/gradle/wrapper/gradle-wrapper.properties` |
| Mobile is a **separate Gradle build** rooted at `mobile/` (`:shared`, `:androidApp`), wrapper **Gradle 8.11.1**, AGP **8.9.2**, Kotlin **2.0.21**, compileSdk/targetSdk 36, minSdk 26. | `mobile/settings.gradle.kts`, `mobile/gradle/libs.versions.toml` |
| Backend `jvmToolchain(21)`; `:shared` `jvmToolchain(21)`; `:androidApp` source/target Java 11 but builds under the same JDK. **JDK 21 (Temurin) is the one correct CI JDK for both builds** — the same choice `ios-ci.yml` already makes. | `backend/build.gradle.kts`, `mobile/shared/build.gradle.kts`, `mobile/androidApp/build.gradle.kts` |
| `mobile/gradle.properties` sets `kotlin.native.ignoreDisabledTargets=true`, and `mobile/shared/build.gradle.kts` applies SKIE **only** when the host is macOS. **This is why an `ubuntu-latest` Android job can configure and build `:shared` at all** despite its iOS targets — no Kotlin/Native download, no konan cache needed. | `mobile/gradle.properties`; `mobile/shared/build.gradle.kts` lines 28, 214-216 |
| `mobile/local.properties` is **gitignored**; on CI, AGP falls back to `ANDROID_HOME`, which `ubuntu-latest` provides. | `.gitignore`, `mobile/androidApp/README.md` |
| Only stub-mode backend operation is ever needed (no `AI_PROVIDER_API_KEY`). Only `JWT_SIGNING_SECRET` is a *required* config value; everything else has a local default. | `backend/.../config/AppConfig.kt`, `backend/.env.example` |

### 1.2 The single biggest CI constraint — MongoDB must be a replica set

7 of the 20 backend test classes hardcode the connection string `mongodb://localhost:27017/?replicaSet=rs0`
directly in source (`AdminIntegrationTest`, `AiTutorIntegrationTest`, `CertificatesIntegrationTest`,
`InstructorIntegrationTest`, `LearningPathsIntegrationTest`, `MediaIntegrationTest`,
`QuizIntegrationTest`), each using a dedicated disposable database dropped in `@AfterAll`.

Three consequences, all load-bearing for section 3:

1. **There is no skip/assume guard.** No `Assumptions.assumeTrue`, no `@EnabledIf`, no Testcontainers
   lifecycle anywhere in `backend/src/test` (the `org.testcontainers:*` dependencies are declared in
   `backend/build.gradle.kts` but **not used by any test**). Without a reachable replica set these
   suites *fail*; they do not skip. CI must provide the database — there is no "degrade gracefully"
   option, and adding one would be a test-suite redesign Phase 7 has no mandate for.
2. **The URI is in source, not in an env var.** Setting `MONGODB_URI` in the workflow does *not*
   redirect the tests; it only affects `seedDemoData` and the running application. CI must therefore
   expose the replica set at exactly `localhost:27017` with set name exactly `rs0`.
3. **GitHub Actions service containers cannot do this.** A `services:` entry accepts
   image/env/ports/options/volumes but has **no command key**, so `--replSet rs0` cannot be passed and
   the set cannot be initiated at container start. The workflows therefore start Mongo with an explicit
   `docker run` step (Docker is preinstalled on `ubuntu-latest`) — see section 2.3.

The replica set must additionally be initiated with an explicit member host of `localhost:27017`
(`rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})`). A bare `rs.initiate()` records
the *container's* hostname as the member address; the driver on the runner host then discovers an
unresolvable host during topology discovery and hangs until timeout — the classic, silent failure mode
for this exact setup. Pinning the member host removes it.

### 1.3 Lint tiers that actually exist today

| Surface | Lint that exists | Verified in |
|---|---|---|
| Web | `npm run lint` (ESLint 9 flat config, next/core-web-vitals + next/typescript), `npm run lint:logical-properties` (bespoke Node check), `npm run validate:design-to-code` (bespoke Node check), `npm run typecheck` (`tsc --noEmit`) | `web/package.json`, `web/eslint.config.mjs`, `web/scripts/`, `tools/design-to-code/validate.js` |
| Android | AGP's own lint task (`:androidApp:lintDebug`). **No lint config block, no baseline** — never explicitly run as a gate in Phases 3-4. | `mobile/androidApp/build.gradle.kts` |
| Backend | **None.** No ktlint, no detekt, no spotless — across all six completed phases. | `backend/build.gradle.kts` |
| iOS | `tools/ios-checks/*.js` (plain Node source gates) | `.github/workflows/ios-ci.yml` |

**Decision D-LINT (see section 3.4):** Phase 7 does **not** introduce a Kotlin linter to the backend.

### 1.4 Test surfaces

| Suite | Command | Known state |
|---|---|---|
| Backend | `backend> ./gradlew test` | 127/127 green (Phase 6 T9 audit, same commit lineage) |
| KMP shared | `mobile> ./gradlew :shared:testDebugUnitTest` | 249/249; deterministic, offline (MockEngine/fakes only) |
| `:shared:liveBackendIntegrationTest` | its own registered Test task | **Excluded** from testDebugUnitTest/testReleaseUnitTest by design (`shared/build.gradle.kts` lines 218-259); skips cleanly via `org.junit.Assume` with no backend. **Must not run in CI.** |
| `:androidApp` JVM | `mobile> ./gradlew :androidApp:testDebugUnitTest` | 241/241 as of Phase 4 T19 |
| `:androidApp` instrumented | `:androidApp:connectedDebugAndroidTest` | 106/106, **requires a real emulator/device** |
| Web E2E | `web> npx playwright test` | 19 tests. **Chromium 19/19, Firefox 19/19, WebKit 1/19** — WebKit's failure is a real, investigated, disclosed Secure/SameSite-over-plain-HTTP-localhost incompatibility (D64), *not* a product defect. |

### 1.5 Client architecture facts relevant to sections 7-9

- **Two different auth transports against one backend.** `plugins/Security.kt` resolves the JWT from
  `Authorization: Bearer` or, failing that, from the access-token cookie (set by `AuthRoutes.kt`'s
  `setAuthCookies`). Website uses the httpOnly cookie through Next's same-origin `/api/*` rewrite;
  Android/KMP uses Bearer via `MentoraSdk`. Login returns tokens in the body *and* sets cookies, so one
  account can drive both transports simultaneously — this is what makes the section 9 parity harness
  possible and meaningful.
- **Backend owns every computed value both clients display.** `ProgressResponse` carries
  `completionPercent`, `quizPassed`, `courseCompletedAt`; `web/src/lib/api/progress.ts` and Android's
  `MyLearningScreen`/player read them verbatim — no client-side recomputation found anywhere.
- **One genuine exception, on *both* clients:** `isEnrolled` is not a backend field on any course
  model, so each client derives it from the enrollments list. Web: `useIsEnrolled` -> `useEnrollments`
  -> `GET /enrollments?limit=100`, **first page only, never follows nextCursor**. Android:
  `CourseDetailsViewModel.isEnrolledIn` **pages fully** through every nextCursor. Identical in effect
  at demo scale, divergent above 100 enrollments. See section 11, finding F1.

---

## 2. CI — shared design (applies to every workflow in sections 3-6)

### 2.1 Conventions inherited from `ios-ci.yml`

`.github/workflows/ios-ci.yml` is the established pattern in this repo, and the new workflows mirror it
deliberately rather than inventing a second style:

- A **long file header comment** stating *what this is*, *what this is NOT* (the manual/local gates it
  does not replace), the scope boundary, secrets posture, cost posture, and the action-version
  resolution date.
- `workflow_dispatch:` **plus** `push`/`pull_request` on `branches: [main]` with **path filters**,
  including a trailing `- '!**/*.md'` negative pattern so docs-only edits never spend a runner.
- `concurrency: { group: <name>-${{ github.ref }}, cancel-in-progress: true }`.
- `permissions: { contents: read }`.
- `timeout-minutes` on every job.
- `set -euo pipefail` in every bash step; **no step swallows a non-zero exit**; every precondition
  failure prints `::error::` with an actionable message and exits 1.
- `gradle/actions/setup-gradle@v6` with `cache-read-only: ${{ github.ref != 'refs/heads/main' }}` so a
  branch can never poison the shared cache (it also validates the committed wrapper JAR checksum).
- Artifacts uploaded with `if: always()` for reports/logs, `if: failure()` for heavyweight bundles.
- A `Job summary` step writing a small table to `$GITHUB_STEP_SUMMARY`.
- **No `secrets.*` reference anywhere.** Nothing signs, deploys, or distributes (ADR-012, criterion I6).

**Action version pins.** Use the same major tags `ios-ci.yml` resolved on 2026-09-18 —
`actions/checkout@v7`, `actions/setup-java@v6`, `actions/cache@v6`, `actions/upload-artifact@v7`,
`gradle/actions/setup-gradle@v6`. `actions/setup-node`'s current major must be resolved deliberately at
implementation time (against the GitHub API, as that file's own ACTION VERSIONS note instructs) and
recorded in the new file's header — do not float it, and do not guess it.

### 2.2 Scope boundary, stated once, in every new file's header

These workflows are **build/lint/test only**. They never deploy, sign, publish, or call an external
service other than package registries and GitHub itself, and they never reference
`AI_PROVIDER_API_KEY` (criterion F2 / D146 — `backend-ci.yml` and `web-ci.yml` set it to the empty
string *explicitly*, which both documents the stub-mode requirement and makes an accidental future
secret injection a visible diff rather than a silent one).

They also do **not** replace the manual gates: no workflow drives an Android emulator, an iOS
simulator, RTL/visual/theme inspection, video playback, or a real device. Those stay human, exactly as
`ios-ci.yml`'s header already says for its own surface and as sections 8-9 specify for Phase 7's walks.

### 2.3 The reusable "MongoDB single-node replica set" step block

Used verbatim by `backend-ci.yml` (section 3) and `web-ci.yml`'s E2E job (section 4.2). Written inline
in both files rather than factored into a composite action — two call sites do not justify a
`.github/actions/` abstraction, and inline keeps each workflow independently readable.

```yaml
      - name: Start MongoDB as a single-node replica set (rs0)
        shell: bash
        run: |
          set -euo pipefail
          # NOT a `services:` container: GitHub Actions service containers have no `command:` key,
          # so `--replSet rs0` cannot be passed and the set can never be initiated. This backend's
          # integration tests hardcode mongodb://localhost:27017/?replicaSet=rs0 (7 test classes) and
          # its checkout/progress/quiz/certificate paths use multi-document transactions, which a
          # standalone mongod cannot run at all. See backend/README.md "One-time MongoDB setup".
          docker run -d --name mentora-mongo -p 27017:27017 \
            mongo:8.0 --replSet rs0 --bind_ip_all
          echo "Waiting for mongod to accept connections..."
          for i in $(seq 1 60); do
            if docker exec mentora-mongo mongosh --quiet --eval 'db.adminCommand({ping:1})' >/dev/null 2>&1; then
              break
            fi
            sleep 2
          done
          # The member host MUST be pinned to localhost:27017. A bare rs.initiate() records the
          # CONTAINER's hostname as the member address; the driver on the runner host then discovers
          # an unresolvable host and hangs until timeout instead of failing clearly.
          docker exec mentora-mongo mongosh --quiet --eval \
            'rs.initiate({_id:"rs0",members:[{_id:0,host:"localhost:27017"}]})'
          echo "Waiting for PRIMARY..."
          STATE=""
          for i in $(seq 1 60); do
            STATE="$(docker exec mentora-mongo mongosh --quiet --eval 'try{rs.status().myState}catch(e){-1}' | tr -d '[:space:]')"
            [ "${STATE}" = "1" ] && break
            sleep 2
          done
          if [ "${STATE}" != "1" ]; then
            echo "::error::MongoDB replica set rs0 never reached PRIMARY (myState=${STATE}). Backend integration tests hardcode replicaSet=rs0 and cannot run without it."
            docker logs mentora-mongo || true
            exit 1
          fi
          docker exec mentora-mongo mongosh --quiet --eval 'rs.status().set'
```

`mongo:8.0` is pinned deliberately (local dev runs MongoDB Community 8.x per `backend/README.md`);
never `mongo:latest`.

---

## 3. `backend-ci.yml` (criterion I1)

**Covers:** I1. **Runner:** `ubuntu-latest`. **Timeout:** 30 minutes.

### 3.1 Triggers

```yaml
name: Backend CI

on:
  workflow_dispatch:
  push:
    branches: [main]
    paths:
      - 'backend/**'
      - '.github/workflows/backend-ci.yml'
      - '!**/*.md'
  pull_request:
    branches: [main]
    paths:
      - 'backend/**'
      - '.github/workflows/backend-ci.yml'
      - '!**/*.md'

concurrency:
  group: backend-ci-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read
```

### 3.2 Job env

```yaml
    env:
      # Read by seedDemoData / a started application only. The TESTS do NOT read this — they hardcode
      # mongodb://localhost:27017/?replicaSet=rs0 in source (see PHASE_7_SYSTEM_DESIGN.md 1.2). Set
      # here so nothing in this job can silently target a different database than the one the
      # replica-set step created.
      MONGODB_URI: 'mongodb://localhost:27017/mentora?replicaSet=rs0'
      MONGODB_DATABASE: mentora
      # Non-secret, CI-only, deliberately in plaintext: this value signs nothing that leaves this
      # ephemeral runner. A real secret must never appear here (criterion I6 / AUTH_SECURITY.md).
      JWT_SIGNING_SECRET: ci-only-not-a-real-secret-signing-key-at-least-32-bytes
      MEDIA_STORAGE_ROOT: storage/media
      CORS_ALLOWED_ORIGINS: 'http://localhost:3000'
      # Explicitly empty: STUB provider mode, per DECISIONS_LOG.md D146 and criterion F2. No Phase 7
      # workflow may ever reference a real provider credential.
      AI_PROVIDER_API_KEY: ''
```

### 3.3 Steps

| # | Step | Command / action |
|---|---|---|
| 1 | Checkout | `actions/checkout@v7` |
| 2 | MongoDB rs0 | the section 2.3 block |
| 3 | Set up JDK 21 | `actions/setup-java@v6` (`distribution: temurin`, `java-version: '21'`) |
| 4 | Set up Gradle | `gradle/actions/setup-gradle@v6`, `cache-read-only: ${{ github.ref != 'refs/heads/main' }}` |
| 5 | **Compile (main + test)** | `working-directory: backend` -> `./gradlew --stacktrace --warning-mode all classes testClasses` |
| 6 | **Test** | `working-directory: backend` -> `./gradlew --stacktrace test` |
| 7 | **Assemble** | `working-directory: backend` -> `./gradlew --stacktrace assemble` |
| 8 | Upload test reports (`if: always()`) | `actions/upload-artifact@v7`; `backend/build/reports/tests/**`, `backend/build/test-results/**`; retention 7 |
| 9 | Mongo logs on failure (`if: failure()`) | `docker logs mentora-mongo || true` (diagnostic only, non-gating) |
| 10 | Job summary (`if: always()`) | runner image / JDK / Mongo image / task list / "NOT covered: live clients, Playwright, emulator" |

Steps 5-7 are split rather than one `./gradlew build` so a failure attributes cleanly to *compile* vs
*test* vs *packaging* in the Actions UI — the same fail-loudly-and-legibly principle `ios-ci.yml`
applies to its own tiers.

### 3.4 Decision D-LINT — the backend "lint" tier

**Options considered.** (a) Add ktlint or detekt now. (b) Treat the Kotlin compiler as the lint tier
and record that no linter is configured. (c) Turn on `allWarningsAsErrors`.

**Chosen: (b).** No Kotlin linter has existed in this project across six completed phases and roughly a
hundred backend source files. Introducing one at Phase 7 would produce a large mechanical violation set
whose triage is unbounded work with no traceability to any Phase 7 acceptance row — precisely the scope
invention criterion J5 forbids. (c) is worse: it converts unknown pre-existing warnings into a red
pipeline on day one with no way to land the workflow incrementally.

`--warning-mode all` on the compile step surfaces Gradle/Kotlin warnings in the job log without gating
on them. **This decision must be recorded in `DECISIONS_LOG.md`** and stated in the workflow header, so
"backend CI has no lint step" reads as a decision, not an omission. If a linter is ever wanted, M15 /
Phase 8 ("testing hardening") is its home.

---

## 4. `web-ci.yml` (criterion I2)

**Two jobs.** `web-static` is the fast, always-informative gate; `web-e2e` is the heavyweight
full-stack gate that `DEPLOYMENT.md` section 5 explicitly requires ("Playwright E2E ... against a
CI-provisioned backend+Mongo"). Splitting them means a lint/type error fails in ~3 minutes instead of
~25, and the E2E job's real failures are never confused with a static failure.

### 4.1 Triggers and job `web-static`

```yaml
on:
  workflow_dispatch:
  push:
    branches: [main]
    paths:
      - 'web/**'
      - 'design-system/**'
      - 'design-to-code/**'
      - 'tools/token-pipeline/generate.js'
      - 'tools/design-to-code/**'
      - '.github/workflows/web-ci.yml'
      - '!**/*.md'
  pull_request:
    branches: [main]
    paths: [ same as above ]

concurrency:
  group: web-ci-${{ github.ref }}
  cancel-in-progress: true

permissions:
  contents: read

jobs:
  web-static:
    name: Web lint + type-check + build
    runs-on: ubuntu-latest
    timeout-minutes: 15
    defaults:
      run:
        working-directory: web
    env:
      API_BASE_URL: 'http://localhost:8080'
```

| # | Step | Command |
|---|---|---|
| 1 | Checkout | `actions/checkout@v7` |
| 2 | Node | `actions/setup-node@v<resolve>` — `node-version: '22'` (LTS; Next 15 / React 19 supported), `cache: npm`, `cache-dependency-path: web/package-lock.json` |
| 3 | Install | `npm ci` |
| 4 | **ESLint** | `npm run lint` |
| 5 | **Logical-properties check** | `npm run lint:logical-properties` (the repo's own RTL gate — no physical-direction CSS; directly serves criterion G3) |
| 6 | **Design-to-code validation** | `npm run validate:design-to-code` (serves G4) |
| 7 | **Type-check** | `npm run typecheck` |
| 8 | **Production build** | `npm run build` |

Note for the implementer: `npm run lint` has one long-standing pre-existing `<img>` **warning** (not an
error), recorded repeatedly in `DECISIONS_LOG.md`. ESLint exits 0 on warnings, so it does not gate. Do
**not** add `--max-warnings=0` — that converts a known, accepted, documented item into a red pipeline,
which is out of Phase 7 scope.

### 4.2 Job `web-e2e`

```yaml
  web-e2e:
    name: Playwright E2E (chromium) against a real local stack
    needs: web-static
    runs-on: ubuntu-latest
    timeout-minutes: 45
    env:
      MONGODB_URI: 'mongodb://localhost:27017/mentora?replicaSet=rs0'
      MONGODB_DATABASE: mentora
      JWT_SIGNING_SECRET: ci-only-not-a-real-secret-signing-key-at-least-32-bytes
      MEDIA_STORAGE_ROOT: storage/media
      CORS_ALLOWED_ORIGINS: 'http://localhost:3000'
      AI_PROVIDER_API_KEY: ''
      API_BASE_URL: 'http://localhost:8080'
      E2E_BASE_URL: 'http://localhost:3000'
```

| # | Step | Detail |
|---|---|---|
| 1 | Checkout | `actions/checkout@v7` |
| 2 | MongoDB rs0 | the section 2.3 block |
| 3 | JDK 21 + Gradle | `setup-java@v6` + `gradle/actions/setup-gradle@v6` |
| 4 | **Build the backend fat jar** | `working-directory: backend` -> `./gradlew --stacktrace buildFatJar` (the Ktor plugin already names the output `mentora-backend.jar`) |
| 5 | **Seed demo data** | `working-directory: backend` -> `./gradlew --stacktrace seedDemoData`. Idempotent by design; creates the seed accounts (`student1@`/`student2@`/`instructor1@`/`admin@mentora.dev`, password `MentoraDemo1`) and the 6 seed courses the specs address by title. **`working-directory: backend` is load-bearing** — `MEDIA_STORAGE_ROOT=storage/media` is relative, and the seeded media must land where the server will later look for it. |
| 6 | **Start the backend** | `working-directory: backend` -> `nohup java -jar build/libs/mentora-backend.jar > "$RUNNER_TEMP/backend.log" 2>&1 &`, then poll `http://localhost:8080/healthz` for up to 120s; on timeout `::error::` + `cat` the log + exit 1 |
| 7 | Node + `npm ci` | as in 4.1 |
| 8 | **Playwright browsers** | `working-directory: web` -> `npx playwright install --with-deps chromium` |
| 9 | **Build the site** | `working-directory: web` -> `npm run build` |
| 10 | **Start the site** | `working-directory: web` -> `nohup npx next start -p 3000 > "$RUNNER_TEMP/web.log" 2>&1 &`, then poll `http://localhost:3000/en` for up to 120s. `API_BASE_URL` must be in the environment at **both** build and start: `next.config.ts`'s `rewrites()` reads it, and that is what makes the backend same-origin (and therefore its SameSite=Lax cookies first-party). |
| 11 | **Run the suite** | `working-directory: web` -> `npx playwright test --project=chromium` |
| 12 | Upload report (`if: always()`) | `web/playwright-report/**`, `web/test-results/**`, `$RUNNER_TEMP/backend.log`, `$RUNNER_TEMP/web.log`; retention 7 |
| 13 | Job summary | stack versions, spec count, browser project, and an explicit "firefox/webkit not run in CI — see D64" line |

### 4.3 Decision D-E2E-BROWSER — chromium only in CI

**Options.** (a) All three Playwright projects. (b) Chromium + Firefox. (c) Chromium only.

**Chosen: (c).** WebKit is **known-red for a real, investigated, non-product reason** (D64: WebKit
applies a stricter Secure/SameSite interpretation over plain-HTTP `localhost` than Chromium's and
Firefox's "localhost is trustworthy" relaxation, so the session cookie is rejected and every
authenticated flow bounces to `/login`). Including it would make CI permanently red for a cause Phase 7
has no mandate to fix (the fix is local HTTPS or a cookie-policy change — a real security decision, not
a CI decision). Firefox is green but roughly doubles the job's wall-clock for near-zero marginal signal
on a stack whose only browser-specific known issue is the WebKit one. Chromium alone is the honest,
useful gate; the workflow header and `web/README.md` must state plainly that firefox/webkit remain
local/manual, citing D64, so this reads as a recorded decision rather than a quietly narrowed suite.

### 4.4 Known E2E behaviours the implementer must not "fix"

- `playwright.config.ts` already sets `workers: 1`, `retries: 1`, `forbidOnly`, and the `github`
  reporter when `process.env.CI` is set — Actions sets `CI=true` automatically. No config change needed.
- `web/e2e/helpers.ts` deliberately retries registration/login with backoff because the backend's real
  auth rate-limit bucket is 10 req/min. That is intended behaviour, not flake handling to remove.
- The suite creates throwaway `@e2e.mentora.test` accounts and `"E2E "`-prefixed courses. On CI this is
  harmless (the Mongo container dies with the job); **locally it pollutes the shared dev database** —
  see D64's cleanup account. Say so in `web/README.md`.

---

## 5. `android-ci.yml` (criterion I3)

**Runner:** `ubuntu-latest`. **Timeout:** 45 minutes.

### 5.1 Triggers

Path filters mirror `ios-ci.yml`'s shared-module set, with `mobile/androidApp/**` **added** (that file
deliberately excludes it; this one deliberately includes it) and `mobile/iosApp/**` excluded:

```yaml
    paths:
      - 'mobile/shared/**'
      - 'mobile/androidApp/**'
      - 'mobile/gradle/**'
      - 'mobile/gradle.properties'
      - 'mobile/settings.gradle.kts'
      - 'mobile/build.gradle.kts'
      - 'mobile/gradlew'
      - 'tools/token-pipeline/generate.js'
      - '.github/workflows/android-ci.yml'
      - '!**/*.md'
```

A `mobile/shared/**` change legitimately triggers **both** `ios-ci` and `android-ci`. That is correct
and intended: `:shared` is the one module both clients compile.

### 5.2 Steps

| # | Step | Command |
|---|---|---|
| 1 | Checkout | `actions/checkout@v7` |
| 2 | JDK 21 | `actions/setup-java@v6` temurin 21 |
| 3 | **Report Android SDK preconditions** | echo `ANDROID_HOME`; `::error::` + exit 1 if unset/missing (same loud-precondition idiom as `ios-ci.yml`'s own ANDROID_HOME check). `ls "$ANDROID_HOME/platforms"` — if `android-36` is absent, AGP downloads it on demand; note this explicitly so a slow first run is not mistaken for a hang. |
| 4 | Set up Gradle | `gradle/actions/setup-gradle@v6`, `cache-read-only` on non-main |
| 5 | **Lint** | `working-directory: mobile` -> `./gradlew --stacktrace :androidApp:lintDebug` |
| 6 | **Unit tests** | `working-directory: mobile` -> `./gradlew --stacktrace :shared:testDebugUnitTest :androidApp:testDebugUnitTest` |
| 7 | **Compile the instrumented sources (without running them)** | `./gradlew --stacktrace :androidApp:compileDebugAndroidTestKotlin` — keeps the 106 instrumented tests from silently rotting into non-compiling code while they are out of CI (section 5.4) |
| 8 | **Assemble** | `./gradlew --stacktrace :androidApp:assembleDebug` |
| 9 | Upload debug APK (`if: always()`) | `mobile/androidApp/build/outputs/apk/debug/*.apk`, retention 7 — directly useful for the section 8 emulator walks |
| 10 | Upload reports (`if: always()`) | `mobile/*/build/reports/**`, `mobile/*/build/test-results/**` |
| 11 | Job summary | task list plus an explicit "instrumented tests NOT run — see the header" line |

**Never invoked:** `:shared:liveBackendIntegrationTest` (needs a live backend; excluded from
`testDebugUnitTest` by design), any `:androidApp:connected*` task, and anything under `mobile/iosApp/`.

### 5.3 Decision D-ALINT — `:androidApp:lintDebug` may need a baseline

AGP lint has **never been run as a gate** in this repo, and `abortOnError` defaults to true, so a
pre-existing lint *error* would make the workflow red on its first run. The implementation plan
therefore requires running `:androidApp:lintDebug` **locally, before writing the workflow** (task T0).
Resolution rule, in order of preference:

1. Zero errors -> ship the step as-is.
2. A small number of genuinely trivial errors -> fix them in the same commit, if and only if each fix
   is mechanical and touches no product behaviour.
3. Otherwise -> generate `mobile/androidApp/lint-baseline.xml` via `lint { baseline = file(...) }` and
   **record the decision plus the baselined issue list in `DECISIONS_LOG.md`**. A baseline is honest
   ("known, accepted, frozen"); silently deleting the lint step, or setting `abortOnError = false`, is
   not — neither is permitted.

### 5.4 Decision D-EMU — instrumented tests are explicitly OUT of CI (criterion I3 allows this)

**Options.** (a) `reactivecircus/android-emulator-runner` on `ubuntu-latest` (KVM is available).
(b) Defer; run locally only.

**Chosen: (b), explicitly and on the record.** Reasons, in order of weight:

1. **Precedent and consistency.** `ios-ci.yml`'s own header states the equivalent boundary for iOS
   ("never runs XCUITests ... those stay human, on a real Mac"), and `PHASE_5_ACCEPTANCE_CRITERIA.md`
   A7/A8/F4 already locked "Android's gates stay local, manual, on the developer's Windows machine."
   Phase 7 is not the place to reverse a locked gating decision.
2. **Cost/flakiness.** A cold emulator boot plus 106 instrumented tests adds roughly 15-25 minutes and
   is the single most flake-prone construct available on hosted runners — the opposite of what M15's
   "CI green twice in a row" criterion needs from a newly-introduced pipeline.
3. **The tests need more than an emulator.** Several exercise real-Keystore behaviour and full
   navigation-shell flows; making them CI-meaningful would mean CI-provisioning a backend for the
   Android surface too — a second full-stack job, for a surface whose real verification in this phase
   is a human emulator walk (section 8) anyway.

**Mitigation (not optional):** step 7 above compiles the instrumented sources every run, so they can
never silently stop compiling. Criterion I3 explicitly permits this deferral "per a documented,
explicit decision" — that document is this section plus a `DECISIONS_LOG.md` entry, and the reason must
also appear in the workflow header and in `mobile/androidApp/README.md`.

---

## 6. `tokens-ci.yml` — the fifth M16 workflow (**flagged: needs a human call**)

`IMPLEMENTATION_ROADMAP.md` M16 names **five** workflows, and `DEPLOYMENT.md` section 5 enumerates
them: backend, web, android, ios, **tokens** ("runs `tools/token-pipeline` and fails if regenerated
output differs from what's committed"). The acceptance criteria's section I lists only I1-I3 plus I4.

**Recommendation: build it.** It is ~40 lines of plain Node plus `git diff --exit-code`, it costs
seconds, it traces to a literal locked M16 deliverable, and it closes a real uncovered gap: the Android
token output already has a drift unit test (`:androidApp` MentoraTokens drift test, Phase 4 T2) and the
iOS token surface is partly covered by `tools/ios-checks/theme-checks.js`, but **`web/styles/tokens.css`,
`web/styles/tailwind-theme.css` and `web/src/lib/design-tokens.generated.ts` have no drift gate at all.**

```yaml
name: Design Token Pipeline CI
on:
  workflow_dispatch:
  push:
    branches: [main]
    paths:
      - 'design-system/design-tokens.json'
      - 'design-system/themes/*.json'
      - 'tools/token-pipeline/generate.js'
      - 'web/styles/tokens.css'
      - 'web/styles/tailwind-theme.css'
      - 'web/src/lib/design-tokens.generated.ts'
      - 'mobile/androidApp/src/main/kotlin/com/mentora/android/theme/MentoraTokens.kt'
      - 'mobile/iosApp/iosApp/Theme/**'
      - '.github/workflows/tokens-ci.yml'
      - '!**/*.md'
  pull_request:
    branches: [main]
    paths: [ same as above ]

permissions:
  contents: read

jobs:
  tokens:
    name: Regenerate tokens and fail on drift
    runs-on: ubuntu-latest
    timeout-minutes: 10
    steps:
      - uses: actions/checkout@v7
      - uses: actions/setup-node@v<resolve>
        with:
          node-version: '22'
      - name: Regenerate every token output
        run: node tools/token-pipeline/generate.js
      - name: Fail if the committed output drifted
        shell: bash
        run: |
          set -euo pipefail
          if ! git diff --quiet; then
            echo "::error::Generated token output differs from what is committed. Run 'node tools/token-pipeline/generate.js' and commit the result (ADR-011: generated, never hand-edited)."
            git --no-pager diff --stat
            git --no-pager diff
            exit 1
          fi
          echo "OK: every generated token output matches the committed state."
```

**Precondition, must be checked first (plan task T0):** run `node tools/token-pipeline/generate.js`
locally and confirm `git status` is clean afterwards. If there is *pre-existing* drift, that is itself
a real finding and must be resolved properly (regenerate, commit, note what changed in
`DECISIONS_LOG.md`) before the workflow lands — not papered over.

**Why flagged rather than assumed:** acceptance-criteria section I does not list it, and H2 counts the
gap as three files. Adding a fourth is defensible only because M16 literally mandates five. **If the
reviewer or the user prefers a strict section-I reading, drop plan task T5 entirely** — nothing else in
this design depends on it.

---

## 7. Criterion C4 — the Android auth-gate staleness bug: root cause and fix

### 7.1 The reported symptom (D144)

> On Android's Course Details screen, the "Login to Enroll" CTA doesn't immediately refresh to "Enroll"
> right after logging in via the auth-gate flow (a stale auth-state read on that screen).

### 7.2 Root cause — found in the code, not inferred

Three facts, each read directly from source:

**(1) The CTA is computed once, from a value captured at ViewModel construction.**
`mobile/androidApp/src/main/kotlin/com/mentora/android/ui/coursedetails/CourseDetailsViewModel.kt`
takes `private val isAuthenticated: Boolean` as a constructor parameter and branches on it inside
`loadCourse()`:

```kotlin
val isEnrolled = if (isAuthenticated) isEnrolledIn(course.id) else false
val cta = when {
    !isAuthenticated -> CourseDetailsCtaState.LoginToEnroll
    isEnrolled -> CourseDetailsCtaState.ContinueLearning
    else -> CourseDetailsCtaState.Enroll
}
```

**(2) The ViewModel is scoped to the `NavBackStackEntry`**, so it lives as long as that entry is on the
back stack. `CourseDetailsScreen.kt` line 98 obtains it with
`viewModel(factory = CourseDetailsViewModel.Factory(sdk, courseId, isAuthenticated))`, and a
`ViewModelProvider.Factory` is consulted **only when no instance exists yet**. Re-entering the same
back-stack entry returns the *same* instance, carrying the *original* `isAuthenticated`.

**(3) The documented invariant that made (1) safe is false for the auth-gate path.**
`CourseDetailsViewModel`'s kdoc (lines 70-76) — and the mirrored comment at `MentoraNavHost.kt:267-270`
— claims:

> "every full auth-state TRANSITION (login/logout) already resets the entire nav stack in
> `MentoraNavHost`, so this screen never stays mounted across one."

That is true of exactly **two** of the three transitions handled in `MentoraNavHost`'s
`LaunchedEffect(authState)` (lines 131-162):

- login with **no** pending intent -> `navigate(TabGraph.HomeGraph) { popUpTo(navController.graph.id) { inclusive = true } }` — full reset. Invariant holds.
- logout -> `navigate(TabGraph.ExploreGraph) { popUpTo(navController.graph.id) { inclusive = true } }` — full reset. Invariant holds.
- **login WITH a pending intent (lines 139-144)** ->
  ```kotlin
  navController.navigate(intent.destination) {
      popUpTo<Destination.Login> { inclusive = true }
  }
  ```
  — this pops **only the Login entry**. Everything beneath it survives. **Invariant broken.**

The auth-gate flow always takes the third path, by construction: `requireAuth` (lines 165-173) sets
`pendingNavIntent` before navigating to Login, and `courseDetailsContent`'s
`onEnrollRequiringAuth = { requireAuth(Destination.DemoCheckout(courseId)) }` (line 272) is the only
route from this screen to Login.

**Exact repro, therefore:**

1. Guest on Course Details -> CTA reads "Login to Enroll". `CourseDetailsViewModel` constructed with
   `isAuthenticated = false`.
2. Tap it -> pending intent = `DemoCheckout(courseId)` -> navigate to Login. **Course Details stays on
   the back stack; its ViewModel stays alive.**
3. Log in -> auth state flips -> `popUpTo<Login>(inclusive = true)` + push `DemoCheckout`. Course
   Details is *still* on the stack, directly beneath Checkout.
4. Back out of Checkout (`onBackToCourse = { navController.popBackStack() }`, line 343) -> the **same**
   `NavBackStackEntry`, the **same** ViewModel, the **same** `isAuthenticated = false`, the **same**
   already-emitted `CourseLoadState.Success(cta = LoginToEnroll)`.

So the CTA is stale — and, less visibly, so is `isEnrolled`.

**Sibling instance, identical root cause.** `LearningPathDetailsViewModel` captures `isAuthenticated`
the same way (its kdoc at line 78 even says "same rationale as `CourseDetailsScreen`"). Its pending
intent re-pushes `LearningPathDetails` *itself*, so the visible post-login instance is a fresh entry
with a fresh ViewModel — which masks the defect — but the stale guest-mode entry is still sitting
underneath and is reached by one system-back press.

### 7.3 Options considered

| Option | Shape | Verdict |
|---|---|---|
| **A. Reactive seam in the ViewModel** — add `observeIsAuthenticated: () -> Flow<Boolean>` wired to `sdk.auth.observeAuthState()` mapped/distinct, collected in `init`, mirroring the existing `reloadOnLocaleChange` seam | Correct; adds an SDK seam plus a collector to a screen-level ViewModel | Viable fallback |
| **B. Reset the whole nav stack on the pending-intent login branch too** | Would break `ux/NAVIGATION_SPEC.md` section 6's "enroll-gate always returns to intent" and Demo Checkout's documented back-to-course behaviour | **Rejected** |
| **C. Re-key the ViewModel** (`viewModel(key = "$courseId-$isAuthenticated", ...)`) | Leaks the first instance in the entry's ViewModelStore and hides the real invariant break behind a key trick | **Rejected** |
| **D. Push the live value in from the composable** — `LaunchedEffect(isAuthenticated) { viewModel.onAuthenticationChanged(isAuthenticated) }` | Smallest possible change; no new SDK seam; covers both "changed while on the back stack" and "changed while visible" | **Chosen** |

**Why D over A.** The composable **already receives the live value**: `MentoraNavHost` passes
`isAuthenticated = authState is AuthState.Authenticated` into `courseDetailsContent` on every
recomposition, and a back-stack entry that is re-entered is re-composed with the *current* `authState`
(a non-visible back-stack entry is not composed at all, so re-entry is exactly when the update is
needed). The information is therefore already at the call site; option A would add a second, redundant
channel — a Koin-resolved flow plus a collector — to learn something the existing parameter already
carries. D is the minimal correct fix, and it is precisely "a targeted fix, not a redesign of auth
state management."

### 7.4 The fix, concretely

**`mobile/androidApp/src/main/kotlin/com/mentora/android/ui/coursedetails/CourseDetailsViewModel.kt`**

- Change the constructor property to a private mutable backing field seeded from the constructor
  parameter (e.g. keep the parameter name, add `private var isAuthenticated: Boolean = initialIsAuthenticated`).
  `loadCourse()`'s body is otherwise **unchanged** — both branches keep reading `isAuthenticated`.
- Add exactly one public entry point:

```kotlin
/** C4 / D144 fix. The auth-gate login path (MentoraNavHost's pending-intent branch) pops ONLY the
 *  Login entry, so this screen's own back-stack entry — and therefore this ViewModel instance —
 *  survives the guest -> authenticated transition. The construction-time snapshot is NOT sufficient,
 *  contrary to this class's previous kdoc. The call site pushes the live value in via
 *  LaunchedEffect(isAuthenticated); this is a no-op when nothing actually changed, so there is no
 *  double-fetch on first composition. */
fun onAuthenticationChanged(value: Boolean) {
    if (value == isAuthenticated) return
    isAuthenticated = value
    loadCourse()
}
```

- **Correct the class kdoc.** Delete the false invariant claim (lines 70-76) and state the real one.

**`.../ui/coursedetails/CourseDetailsScreen.kt`** — inside the stateful `CourseDetailsScreen`
composable, immediately after `val viewModel = viewModel(...)`:

```kotlin
LaunchedEffect(isAuthenticated) { viewModel.onAuthenticationChanged(isAuthenticated) }
```

`CourseDetailsScreenContent` (the stateless half) is **not** touched — no new parameter, no new state.

**`.../navigation/MentoraNavHost.kt`** — **no behavioural change.** Correct the stale comment at lines
267-270 (and the mirrored one on `learningPathDetailsContent`) so it says what is actually true: only
the no-pending-intent login branch and the logout branch reset the stack; the pending-intent branch
deliberately does not, and the two details screens now handle that themselves.

**`.../ui/learningpathdetails/LearningPathDetailsViewModel.kt` and `LearningPathDetailsScreen.kt`** —
apply the identical two-line pattern, for the identical root cause (7.2's sibling note). Note that
`LearningPathDetailsScreen` line 186 already uses the live `isAuthenticated` parameter directly for its
Follow-vs-gate branch; that is correct already and needs no change.

**Explicitly NOT changed:** `AuthGate.kt`, `decideAuthGate`, `AppSessionViewModel`, `MentoraSdk`,
`SessionManager`, the pending-intent mechanism, and every navigation reset site. No file under
`mobile/shared/`, `backend/`, or `web/` is touched by this fix.

### 7.5 Tests

**New JVM unit tests** (`:androidApp:testDebugUnitTest`). The existing `CourseDetailsViewModelTest` and
`LearningPathDetailsViewModelTest` already build the ViewModel from plain lambdas with a
`StandardTestDispatcher`, so nothing new is needed to exercise this:

1. `guestThenAuthenticated_ctaRefreshesFromLoginToEnrollToEnroll` — build with
   `isAuthenticated = false`, advance, assert `LoginToEnroll` and that `listEnrollments` was never
   called; call `onAuthenticationChanged(true)`, advance, assert `Enroll` and `listEnrollments` called
   exactly once.
2. `guestThenAuthenticated_andAlreadyEnrolled_ctaBecomesContinueLearning` — same, with a fake
   enrollment page containing this course; assert `ContinueLearning` and `isEnrolled == true`.
3. `onAuthenticationChanged_withTheSameValue_doesNotReload` — assert `getCourseDetails` call count is
   still 1 (this is the guard that keeps first composition from double-fetching).
4. The `LearningPathDetails` equivalents of (1) and (3).

**Existing tests must pass unmodified.** All current `CourseDetailsViewModelTest` cases and the
`LearningPathDetailsViewModelTest` cases construct the ViewModel through helpers that know nothing
about the new method, so they should compile and pass untouched. **If any of them needs an edit, that
is a signal the change is larger than designed — stop and re-review rather than editing assertions.**

**Instrumented coverage:** `NavigationShellTest` already drives the gate end-to-end through a fake
`MutableStateFlow<AuthState>` (`AuthGate.kt`'s kdoc documents that seam). An added case there is
welcome but **is not a completion gate** — instrumented tests are out of CI (section 5.4) and must be
run locally if added.

**Live proof (required):** the emulator walk in plan task T11 must reproduce the original D144 repro
step-for-step and confirm the CTA now reads "Enroll" on return from Checkout. A unit test alone does
not close C4, because the defect was a navigation-lifetime defect, not a pure-logic defect.

---

## 8. Cross-client verification methodology (criteria A-G)

### 8.1 The four mechanisms, and when each is the right one

| Mechanism | What it is | What it can and cannot prove |
|---|---|---|
| **M-API** — the parity harness (section 9) | A committed, re-runnable plain-Node script that drives the real local backend over **both** client transports (cookie, as the Website does; Bearer, as `MentoraSdk` does) for one account | Proves the backend is genuinely one source of truth and that both transports see identical state. **Cannot** prove what a client *renders*. Fast, deterministic, re-runnable in Phase 8. |
| **M-WEB** — self-performed Chrome browser automation | The orchestrating agent drives a real browser against the real local stack — the exact mechanism Phase 6 T7 used for the Website half (D144) | Proves rendering, RTL, theme, and real user-visible behaviour on Web |
| **M-AND** — background QA agent on a real Android emulator via adb | A delegated agent installs the debug APK and drives the app, capturing screenshots — the exact mechanism Phase 6 T7 used for Android (D144), per the standing instruction that Android must be genuinely exercised, never statically inspected | Proves the same on Android, including the C4 fix |
| **M-SCAN** — static code scan/read | ripgrep and file reads over the real source | Proves absence-type and architecture-type claims (no payment path, no role-gated Android surface, no client-side recomputation) — and nothing about runtime |

**The decomposition rule that makes section A tractable.** "An enrollment made on Web is visible on
Android" is two claims, and conflating them is exactly what makes such criteria feel unverifiable:
(i) *the backend returns the same state to both request shapes* — provable exactly and cheaply by
**M-API**; and (ii) *each client displays what the backend returned rather than something it computed
itself* — provable by **M-SCAN** plus a live spot-check inside the M-WEB/M-AND walks. Every section-A
row below is verified by **both** halves, never by one alone.

### 8.2 Per-criterion verification matrix

| # | Mechanism | Exactly what is done | Evidence recorded |
|---|---|---|---|
| **A1** | M-API + live spot-check | Harness: complete demo checkout over the **cookie** transport, then `GET /enrollments` over the **Bearer** transport and assert id/courseId/status/enrolledAt match field for field. Live: in the T10 Web walk, enroll in a course; then, in the T11 Android walk for the same seed account, open My Learning and confirm it is there with no reinstall or cache clear. | harness stdout; emulator screenshot |
| **A2** | M-API + live | Harness: POST `/courses/{id}/lessons/{lessonId}/complete` over Bearer, then GET `/courses/{id}/progress` over cookie; assert identical `completedLessonIds`, `completionPercent`, `currentLessonId`. Live: complete a lesson on Android, reload My Learning on Web, confirm the same percentage. | harness stdout; two screenshots |
| **A3** | M-API + live | Harness: submit a quiz attempt over one transport, read the attempt/result over the other; assert identical score, pass/fail and attempt count. Parity is asserted, **not** a specific pass outcome (see 9.3). | harness stdout |
| **A4** | M-API + live | Harness: after driving the course to completion, GET `/certificates` and `/certificates/{id}` over both transports; assert identical id/courseId/issuedAt/serial fields. | harness stdout; Certificates screenshots on both clients |
| **A5** | M-API (stub only) | Harness: POST one AI Tutor message over one transport, GET the conversation over the other; assert identical message list. **Stub provider only** — no API key (F2/D146). | harness stdout |
| **A6** | M-SCAN — **already performed, see section 10** | Confirm no client recomputes `completionPercent`, `quizPassed`, or enrollment status. Re-confirm and record the one real exception (`isEnrolled`, derived on both clients; Web reads page 1 only, Android pages fully). | section 10 table + a DECISIONS_LOG note |
| **B1/B2** | M-WEB | The nine-step portfolio-priority flow walked start to finish in `/en` then `/ar`, against the local backend, as a seeded student. One screenshot per step per language (18 minimum). | screenshot set + a step-by-step PASS/FAIL table |
| **B3/B4** | M-AND | The same nine steps on the emulator against `10.0.2.2:8080`, English then Arabic (in-app Settings selector or system language). | screenshot set + table |
| **B5** | M-SCAN + procedure | `backend> ./gradlew seedDemoData` is run immediately before the walks and its console summary captured; the walks use seed accounts (`student1@`/`student2@mentora.dev`, `MentoraDemo1`) and seed courses (e.g. "Building Reliable REST APIs", the one with a real quiz). No hand-crafted state. | seed task output pasted into the task record |
| **C1** | M-API + M-SCAN | Harness: log in on both transports with one account; assert identical user payload/role; exercise refresh on both; assert a role-gated endpoint returns the same 403 shape for a student on both transports. Plus a read of `AuthRoutes.kt` + `plugins/Security.kt` confirming one policy serves both. | harness stdout |
| **C2** | M-WEB + M-AND | Guest walk on both clients: confirm the gate fires at the same product-defined places (enroll, follow a path, open the player) and that both land back on the intended destination after login. | screenshots |
| **C3** | M-SCAN — **already performed, see section 10** | Android `navigation/Destinations.kt` contains zero instructor/admin routes; `MentoraNavHost` registers exactly five tab graphs. Confirmed clean. | grep output in the task record |
| **C4** | Fix + unit tests + M-AND | Section 7. The live emulator repro is mandatory. | test run + before/after screenshots |
| **D1** | M-API | Harness: complete checkout twice on **each** transport; assert `alreadyEnrolled` flips false to true, HTTP 201 then 200, and exactly **one** enrollment record exists afterwards; assert the two transports differ only in the auth mechanism. | harness stdout |
| **D2** | M-SCAN | A **word-boundary** regex scan across `backend/src`, `web/src`, `mobile/shared/src`, `mobile/androidApp/src` for real payment-processor and card-field identifiers. **Gotcha, already hit during design:** a naive case-insensitive `adyen` alternation matches the substring inside `alreadyEnrolled` — anchor with word boundaries or drop that token, or the scan reports about 20 false positives. | the exact command and its (empty) output |
| **E1** | M-API + live | Harness asserts the position/complete endpoints produce identical server state regardless of caller. Live: confirm both players (HTML5 vs. ExoPlayer) reach the same backend state after the same user actions. | harness stdout + screenshots |
| **E2** | M-API + live | Harness: fetch the quiz on both transports and assert `isCorrect` is stripped in **both** responses (a direct check of the M8 projection); submit; assert identical grading visible to both. | harness stdout |
| **E3** | M-API + live | Harness: follow a learning path on one transport, read it and its derived progress on the other. | harness stdout |
| **F1** | M-WEB + M-AND, re-confirm only | Phase 6 T7 already PASSed this (D144). Re-confirm cheaply during the section-B walks (AI Tutor is step 8 of the flow anyway): quick actions, thinking state, error + retry, enrolled-course context — **stub mode only**. Not a from-scratch re-verification. | screenshots from the walks |
| **F2** | M-SCAN + workflow review | Confirm the local `.env` provider key is empty, that no workflow references a secret, and that no Phase 7 diff introduces one. | grep + workflow diff review |
| **G1/G2** | M-WEB + M-AND | Not a separate pass: the section-B walks carry it. Full matrix is four runs per client (en-Light, en-Dark, ar-Light, ar-Dark). If that is too long, the stated fallback is en-Light + ar-Dark as the two maximally-different combinations, plus targeted spot checks of the other two on the highest-risk screens (Course Player, Quiz, Checkout, AI Tutor). **The choice must be stated in the task record, never silently reduced.** | screenshot matrix |
| **G3/G4** | Review gate | Applied to the Phase 7 diff, not measured at runtime. The C4 fix adds no string and no layout, so it satisfies both trivially — but it must still be stated as checked. | review note |
| **I1-I3** | CI itself | Green on this repository's real current state, twice (J2, and M15's own "green twice in a row" framing). | Actions run links |
| **I4** | M-SCAN | `ios-ci.yml` byte-unchanged (`git log -- .github/workflows/ios-ci.yml`), path filters untouched, last run still green. | git output |
| **I5** | Re-read | After all code/CI changes: re-read `start-mentora.ps1`, `stop-mentora.ps1`, `backend/README.md`, `web/README.md`, `mobile/androidApp/README.md` and correct anything the phase invalidated (at minimum: a new CI section in each README). | diff |

### 8.3 Division of labour

- **M-WEB** is self-performed by the orchestrating agent (browser automation), as in Phase 6 T7.
- **M-AND** is delegated to a background QA agent driving a real debug APK on an emulator via adb, as
  in Phase 6 T7 — the standing instruction is that Android must be genuinely exercised, never
  statically inspected. The APK can come from the `android-ci.yml` artifact (5.2, step 9) or be built
  locally with `:androidApp:assembleDebug` / `installDebug`.
- **M-API** and **M-SCAN** are self-performed and must be re-runnable by anyone afterwards.

---

## 9. The cross-client parity harness (`tools/cross-client-check/`)

### 9.1 Why it exists, and why it is not scope invention

Criteria A1-A5, C1, D1 and E1-E3 are all of the form "X recorded via one client is identical via
another". Walking those on two UIs proves them once, unrepeatably, and mostly proves the *UI*. A small
script proves the *contract* exactly, in seconds, and can be re-run in Phase 8 and after any future
backend change. It follows this repo's own established precedent for exactly this kind of tool — plain
Node, zero dependencies, run manually — like `tools/token-pipeline/generate.js`,
`tools/design-to-code/validate.js` and `tools/ios-checks/*.js` (the D35 precedent).

**It is not wired into CI.** It needs a live backend and seeded data, and `web-ci.yml`'s E2E job
already covers the full-stack-in-CI ground. Keeping it manual also keeps it honest about being a
verification aid, not a gate.

### 9.2 Shape

```
tools/cross-client-check/
  README.md      — what it proves, what it does not, how to run it
  check.js       — plain Node 22 (global fetch), no dependencies, no package.json needed
```

Run as `node tools/cross-client-check/check.js` with the local stack up (`start-mentora.ps1`) and
`seedDemoData` already applied. Optional env: `API_BASE_URL` (default `http://localhost:8080`).

**Two transports, one account:**

- `WebTransport` — POST `/api/v1/auth/login`, keep the Set-Cookie access/refresh cookies, send them on
  every subsequent request, and set `X-Requested-With: mentora-web` on every mutating call (the CSRF
  rule from `INTEGRATION_CONTRACT.md` / `AUTH_SECURITY.md` section 10). This is byte-for-byte how
  `web/src/lib/api/client.ts` behaves.
- `SdkTransport` — same login, but keep `data.accessToken` from the body and send
  `Authorization: Bearer <token>`, with the same `X-Requested-With` header the KMP `ApiClient` sends.
  This is how `MentoraSdk` behaves.

Both are legal against the same backend: `plugins/Security.kt` resolves the JWT from the header first
and falls back to the access cookie.

### 9.3 Assertions, in order

1. **C1** — both transports log in as the same seeded student; `GET /users/me` returns an identical
   payload; a role-gated instructor/admin endpoint returns the same 403 shape for both.
2. **D1** — POST `/courses/{id}/checkout/complete` on the cookie transport gives `alreadyEnrolled:false`;
   repeating it on the Bearer transport gives `alreadyEnrolled:true`, and `GET /enrollments` shows
   exactly one record for that course on both.
3. **A1** — full field-by-field comparison of the `/enrollments` payloads across transports.
4. **A2/E1** — POST lesson-complete and position updates on one transport; GET progress on the other;
   deep-equal the whole `ProgressResponse`.
5. **E2/A3** — GET the quiz on both and assert **no isCorrect field appears in either** response (the
   M8 projection check); submit an attempt; deep-equal the graded result and the attempt list across
   transports.
6. **A4** — drive the course to completion (complete every lesson in the curriculum, then submit the
   quiz answering **option index 0** for every question — `SeedData.kt`'s `question()` helper always
   emits `[correct, incorrect]` in that order, verified in source and already relied on by `web/e2e`
   per D64); then deep-equal `/certificates` and `/certificates/{id}` across transports. **If the
   attempt does not pass, the script must report A4 as NOT EXERCISED and exit non-zero — never
   fabricate it.**
7. **A5** — POST an AI Tutor message (stub mode) on one transport; GET the conversation on the other;
   deep-equal the history. Asserts nothing about the *content*, only about parity.
8. **E3** — follow a learning path on one transport, read it and its derived progress on the other.

Output is a PASS/FAIL line per assertion plus a non-zero exit on any failure; on a mismatch it prints
both payloads side by side. It must **create its own throwaway student** (a unique
`@xclient.mentora.test` address) rather than mutating a seed account's state, and must respect the real
10-request-per-minute auth rate limit with the same backoff `web/e2e/helpers.ts` already uses.

---

## 10. Acceptance-criteria status corrections (confirmed against the code)

| # | The criteria file says | **Actually** | Basis |
|---|---|---|---|
| A6 | PARTIAL — "verify no exception exists" | **True by construction, with one named, documented exception.** No client recomputes `completionPercent`, `quizPassed` or `courseCompletedAt` — `web/src/lib/api/progress.ts` and Android's `MyLearningScreen`/player render the backend's own fields. The exception: **`isEnrolled` is client-derived on both clients** because no backend model carries it, and the two derivations differ (Web: `GET /enrollments?limit=100`, first page only; Android: pages every nextCursor). Equivalent at demo scale. | `web/src/lib/api/enrollment.ts` lines 47-66; `CourseDetailsViewModel.isEnrolledIn` |
| C3 | PARTIAL — "confirm no drift" | **Confirmed DONE.** Android `navigation/Destinations.kt` contains no instructor/admin route of any kind; `MentoraNavHost` registers exactly HomeGraph/ExploreGraph/MyLearningGraph/AiTutorGraph/ProfileGraph. No drift. | grep over `Destinations.kt` / `MentoraNavHost.kt` |
| D2 | NOT STARTED | **Pre-confirmed clean** by a design-time scan of `backend/src`, `web/src`, `mobile/shared/src`, `mobile/androidApp/src` for stripe / paypal / card number / cvv / cvc / payment_intent / braintree / adyen: **zero real hits** (every apparent hit was the `adyEn` substring inside `alreadyEnrolled`). Still requires a formal re-run and recording under Phase 7. | design-time ripgrep scan |
| C4 | NOT STARTED — "recommend fixing" | **Root-caused** (7.2): the pending-intent login branch pops only `Destination.Login`, so the Course Details back-stack entry and its ViewModel survive the transition — contradicting the invariant that ViewModel's own kdoc relies on. **Recommendation: fix in Phase 7**, minimally, per 7.4, and fix the identical latent defect in `LearningPathDetailsViewModel`. | `MentoraNavHost.kt` lines 136-151; `CourseDetailsViewModel.kt` lines 70-88 |
| I1-I3 | NOT STARTED (real gap) | **Confirmed:** `.github/workflows/` contains exactly `ios-ci.yml`. The gap is real. | directory listing |
| I4 | DONE (pre-existing) | **Confirmed**, and nothing in this design touches it. `android-ci.yml`'s path filters deliberately overlap on `mobile/shared/**` — intended, not a regression. | `ios-ci.yml` |
| F1 | PARTIAL (inherited PASS) | **Agreed.** Phase 7 touches Course Details and Learning Path Details, not the AI Tutor surface — so a cheap re-confirm inside the section-B walks (where AI Tutor is step 8 anyway) is sufficient. No from-scratch re-verification. | 7.4's file list |

**Net effect: three rows (A6, C3, D2) are already substantially true and need recording, not building.**
Everything else in A-G genuinely needs the verification work in sections 8-9, and I1-I3 genuinely need
building.

---

## 11. Open questions / things needing a human call

**F1 — `isEnrolled` pagination divergence (A6).** Web reads only the first page of `/enrollments`;
Android pages fully. Identical below 100 enrollments, divergent above. Three options: (i) record it as
a known, accepted, disclosed divergence (cheapest, honest, zero risk); (ii) a one-line Web fix to
follow `nextCursor`; (iii) add an `isEnrolled` field to the backend course/detail response so neither
client derives anything — architecturally cleanest, but a backend contract change affecting three
clients, which is **not** Phase 7 scope. **Recommendation: (i), with (ii) as an easy follow-up if the
reviewer prefers. Do not do (iii) in this phase.**

**F2 — the fifth workflow (section 6).** `tokens-ci.yml` traces to M16's literal "all five GitHub
Actions workflows" but is not in acceptance criteria section I. Recommendation: build it; drop plan
task T5 if a strict section-I reading is preferred.

**F3 — `:androidApp:lintDebug` baseline.** Unknown until run locally (task T0). If it needs a large
baseline, that is a legitimate reason to record the decision and move on (5.3), not to expand Phase 7
into a lint-cleanup phase.

**F4 — G1/G2 walk matrix size.** Four language-by-theme combinations per client, times two clients,
times nine steps, is a lot of manual walking. 8.2 offers a reduced-but-stated alternative. **Someone
should choose explicitly** rather than letting the executing agent silently reduce it.

**F5 — WebKit stays out of CI (4.3).** This narrows `TESTING_STRATEGY.md` section 5's three-browser
requirement for the CI tier specifically. It is defensible and already documented (D64), but it *is* a
narrowing and should be acknowledged in the Phase 7 decision record rather than passed over.

---

## 12. Risks and reversibility

| Item | Risk | Reversibility |
|---|---|---|
| The three (four) new workflows | Low. Additive files; nothing else depends on them; a bad workflow is deleted or reverted in one commit. Worst case is a red badge, not a broken build. | **Fully reversible** |
| Mongo-in-CI via `docker run` | Medium-low. The `rs.initiate` host pin is the one subtle correctness requirement; get it wrong and the job hangs rather than failing fast (mitigated by the explicit PRIMARY poll plus `::error::` and exit 1). | Fully reversible |
| The `web-e2e` job | Medium. The heaviest new construct (roughly 25-40 minutes); the real auth rate limit and a live video-playing test make it the most flake-prone. Mitigations: workers=1 and retries=1 (already configured), chromium only, a fresh database every run. | Fully reversible. If it proves unstable, the honest fallback is to reduce it to the portfolio-priority specs (01-08) **with a recorded decision** — never to silently delete assertions |
| `:androidApp:lintDebug` baseline (if needed) | Low, but it creates a committed baseline file that can rot. | Reversible; must be recorded |
| The C4 fix | **Low, and deliberately so.** Two files (plus two sibling files), one new public method each, one `LaunchedEffect` each; no navigation change, no SDK change, no shared-module change, no string, no layout. Blast radius is exactly two screens. | Fully reversible |
| Token-drift workflow | Low, unless pre-existing drift exists — in which case it exposes a real problem that must be fixed properly before the workflow lands. | Fully reversible |
| Verification tasks (sections 8-9) | **No production-code blast radius at all** — they read and report. The only real risk is E2E/harness accounts polluting the local dev database (D64's precedent); the harness therefore uses a clearly-namespaced throwaway account. | N/A |

**Nothing in this design is hard to reverse.** There is no schema migration, no public API change, no
cross-service contract change, and no change to any locked architecture/product/UX/design-system
document. The single highest-consequence judgement call is 5.4 (instrumented tests out of CI), and it
is a *documented deferral* consistent with an already-locked Phase 5 decision, not a new precedent.
