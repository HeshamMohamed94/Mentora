# Mentora Backend — Local Run Instructions

Phase 1 scope only: this covers running the **backend + MongoDB** locally. There is no Web/Android/iOS
client yet (Phases 2–5) — everything here is verified via `curl`/an HTTP client and the automated test
suite. See `architecture/DEPLOYMENT.md` for the full (all-phases) local-only deployment design this
follows; this file is the concrete, as-built "do this" version of that architecture for Phase 1.

## Quick start: one-command local environment (backend + website)

Two scripts at the repo root start/stop MongoDB check, backend, and website together, waiting for both
`/healthz` (backend) and `/en` (website) to actually respond before opening the site in your browser:

```
.\start-mentora.ps1     # start everything (safe to re-run — skips anything already running)
.\stop-mentora.ps1      # stop the backend/website processes these scripts started (MongoDB is left running)
```

If PowerShell's execution policy blocks running them directly, either run once with:
```
powershell -ExecutionPolicy Bypass -File .\start-mentora.ps1
```
or set the policy for your user permanently: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`.

These scripts track the processes they start under `.local-runtime/` (git-ignored) so `stop-mentora.ps1`
only ever stops what it started. They don't replace the manual steps below — read on if you're setting up
the backend for the first time (MongoDB replica set, `.env`) or want to run/test it directly.

## Prerequisites

- **JDK 21** (Temurin or equivalent). Verify: `java -version`.
- **MongoDB Community 8.3+**, installed and running as a **native local service** — not Docker (see
  `execution/DECISIONS_LOG.md` D1: this project deliberately doesn't require Docker for Phase 1).
- No global Gradle/Kotlin install is needed — the project vendors its own Gradle wrapper
  (`gradlew`/`gradlew.bat`, D2). Always invoke the backend through the wrapper, never a bare `gradle`.

## One-time MongoDB setup: convert to a single-node replica set

This backend uses real multi-document MongoDB transactions (demo checkout, lesson-completion →
progress → certificate issuance, quiz submission) — a **standalone** MongoDB cannot run transactions at
all. If your local MongoDB was installed with default settings, it's standalone; convert it to a
single-node replica set once (see `execution/DECISIONS_LOG.md` D12 for why this is safe and reversible —
it only adds transaction/oplog support, existing CRUD behavior for any other local project sharing this
MongoDB install is unaffected):

1. Find your `mongod.cfg` (Windows default: `C:\Program Files\MongoDB\Server\<version>\bin\mongod.cfg`).
2. Add (or uncomment) this section:
   ```yaml
   replication:
     replSetName: rs0
   ```
3. Restart the MongoDB Windows service (Services app → "MongoDB Server" → Restart, or
   `net stop MongoDB && net start MongoDB` from an elevated prompt).
4. Initiate the replica set **once**:
   ```
   mongosh --eval "rs.initiate()"
   ```
5. Verify it's healthy:
   ```
   mongosh --eval "rs.status()"
   ```
   Look for `"stateStr" : "PRIMARY"`.

If this step is skipped, the backend will start and `/healthz` will report OK, but any request that opens
a transaction (checkout, lesson completion, quiz submission) will fail.

## Configure

```
cd backend
copy .env.example .env        (PowerShell: Copy-Item .env.example .env)
```

Open `.env` and set `JWT_SIGNING_SECRET` to a real random value (it's the one required variable with no
default — the app fails fast at startup without it):

```
openssl rand -base64 48
```

Every other variable in `.env.example` has a working local default — see that file's comments for what
each one does. `.env` is never committed (see `.gitignore`); `.env.example` is the source of truth for
which variables exist.

## AI Tutor provider

The AI Tutor (`/api/v1/ai-tutor/*`) runs in one of two modes, selected entirely by whether
`AI_PROVIDER_API_KEY` is set — there is no separate feature flag:

- **STUB mode (default, `AI_PROVIDER_API_KEY` unset/blank).** The endpoint is fully real —
  authentication, per-user rate limiting, MongoDB persistence, the enrollment gate for
  lesson-context questions — but every reply is fixed placeholder text, no network call is ever
  made, and it costs nothing. This is the right mode for ordinary local development.
- **ANTHROPIC mode (`AI_PROVIDER_API_KEY` set to a real key).** Every AI Tutor message becomes a
  real, streaming, **billable** call to the Anthropic Claude Messages API
  (`AI_PROVIDER_MODEL`, default `claude-sonnet-4-5` — must be a currently-valid Anthropic model id,
  or every message fails with a 500). Cost exposure is bounded by the existing per-user rate limits
  (`AI_TUTOR_MESSAGES_PER_MINUTE`/`_PER_DAY`) and `AI_PROVIDER_MAX_RESPONSE_TOKENS`, all in
  `.env.example`.

The backend **always starts** regardless of which mode is active — a missing/invalid key never
crashes the app, it just falls back to stub mode. The active mode is always printed at startup:

```
AI Tutor provider mode: STUB — AI_PROVIDER_API_KEY is not set, so AI Tutor replies are placeholder text. ...
```
or
```
AI Tutor provider mode: ANTHROPIC (model=claude-sonnet-4-5, maxResponseTokens=1024)
```

**No client ever holds or sees a provider key.** Web, Android, and the KMP shared module talk only
to Mentora's own backend endpoint over the same authenticated connection as everything else — the
`AiProvider` interface and the Anthropic-specific request/SSE-parsing code
(`aitutor/provider/Anthropic*.kt`) are the only places in the entire codebase that reference the key
or talk to `api.anthropic.com`, per `architecture/adr/ADR-009-ai-provider-abstraction.md`.

Architecture and implementation detail: `architecture/AI_TUTOR_ARCHITECTURE.md`,
`execution/PHASE_6_SYSTEM_DESIGN.md`, `execution/PHASE_6_ACCEPTANCE_CRITERIA.md`.

## A known local Gradle quirk on Windows

If a Gradle command fails with something like
`Could not create parent directory for lock file C:\.gradle\wrapper\dists\...` (an unwritable default
Gradle user home on this kind of machine setup), point Gradle at a writable directory instead — the
repo's own `.gradle` folder works:

**PowerShell:**
```
$env:GRADLE_USER_HOME = "$PWD\..\.gradle"
```
**bash / Git Bash:**
```
export GRADLE_USER_HOME="$(pwd)/../.gradle"
```
Set it once per shell session before running any `gradlew`/`gradlew.bat` command below. If your machine
doesn't hit this, you can ignore it entirely.

## Build, test, run

All commands below run from the `backend/` directory.

```
# Build + compile (does not run tests)
.\gradlew.bat build

# Run the full automated test suite (unit + integration; integration tests need MongoDB reachable,
# per the replica-set setup above — they use a dedicated, disposable database per test class)
.\gradlew.bat test

# Start the backend (Ctrl+C to stop)
.\gradlew.bat run
```

Once running, verify it's up:
```
curl http://localhost:8080/healthz
```
Expect `{"status":"ok","mongo":"ok"}` (`200 OK`) — this one route is unwrapped, not the standard
`{"data":...}` envelope every other endpoint uses (it's a plain infrastructure health check, not an API
resource). A `503` with a Mongo error message means
MongoDB isn't reachable at the configured `MONGODB_URI`, or the replica-set step above wasn't completed.

The server listens on port `8080` by default (override with the `PORT` env var), bound to `0.0.0.0` so a
mobile emulator or another device on the same LAN can reach it once those clients exist (Phases 2–5) —
see `architecture/DEPLOYMENT.md § 4a` for the per-client base-URL table.

## Seed / demo data

Populate the running database with a realistic small dataset (categories, courses across Draft/Published
status and `en`/`ar` content languages, a course with a quiz, a Learning Path, and one seeded account per
role) so there's something to explore beyond an empty database:

```
.\gradlew.bat seedDemoData
```

Safe to run more than once — it skips anything it finds already seeded rather than erroring or
duplicating data. It prints the demo account credentials to the console when it finishes; as seeded, they
are:

| Role | Email | Password |
|---|---|---|
| Admin | `admin@mentora.dev` | `MentoraDemo1` |
| Instructor | `instructor1@mentora.dev` | `MentoraDemo1` |
| Instructor | `instructor2@mentora.dev` | `MentoraDemo1` |
| Student | `student1@mentora.dev` | `MentoraDemo1` |
| Student | `student2@mentora.dev` | `MentoraDemo1` |

Log in via `POST /api/v1/auth/login` with any of the above (see `architecture/API_CONTRACT.md` for the
full request/response shape). These are local-only demo credentials with no special privileges beyond
their role — never reuse this password for anything real.

## CI

`.github/workflows/backend-ci.yml` (Phase 7) runs on every push/PR touching `backend/**`: it starts
MongoDB as a real single-node replica set (`docker run mongo:8.0 --replSet rs0 --bind_ip_all`, then
`rs.initiate()` pinned to `localhost:27017` — a plain `services:` container can't take a `--replSet`
flag, and this backend's transactions and 7 integration test classes require a real replica set, not
a standalone `mongod`, the same requirement as the "One-time MongoDB setup" section above), then runs
`./gradlew --stacktrace --warning-mode all classes testClasses`, `./gradlew --stacktrace test`, and
`./gradlew --stacktrace assemble` against JDK 21 — compile, then the full test suite, then assemble.
Test reports are uploaded as a build artifact.

**D-LINT — no backend linter, by decision.** No Kotlin linter (ktlint/detekt/spotless) has existed
anywhere in this project across every phase, and CI deliberately doesn't introduce one now — doing
so would produce a large, untriaged violation set with no traceability to any specific acceptance
requirement. `--warning-mode all` surfaces Gradle/Kotlin warnings in the job log without gating on
them. See `execution/DECISIONS_LOG.md` D147/D149 for the full reasoning.

This workflow never starts the Website or an Android/iOS client and proves nothing about
cross-client behavior — see `web-ci.yml`'s E2E job and `execution/PHASE_7_IMPLEMENTATION_PLAN.md`
T10/T11 for that. It never deploys, signs, or publishes anything (ADR-012).

## Project layout (backend-only, Phase 1)

```
backend/
  src/main/kotlin/com/mentora/backend/   one package per module (auth, users, courses, categories,
                                          enrollment, progress, quiz, certificates, learningpaths,
                                          media, instructor, admin, aitutor, plugins, common, config)
  src/test/kotlin/com/mentora/backend/   one *IntegrationTest.kt per module (+ unit tests for
                                          pure business logic — see architecture/TESTING_STRATEGY.md)
  storage/media/                         local filesystem media root (gitignored — created at runtime)
```

See `architecture/BACKEND_ARCHITECTURE.md` for the module-boundary rules, `architecture/API_CONTRACT.md`
for the full endpoint list, and `execution/INTEGRATION_CONTRACT.md` for the as-built request/response
shapes (the source of truth when it differs in a small way from the original architecture docs — every
such difference is recorded there and in `execution/DECISIONS_LOG.md` with its reasoning).
