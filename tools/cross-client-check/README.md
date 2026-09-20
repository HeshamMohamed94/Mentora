# Cross-Client Parity Check (Phase 7, Task T8)

`check.js` is a plain Node 22, zero-dependency script that proves the Website and the
Android/KMP-shared-core client observe the **same backend state through the same API**, by
driving two independent HTTP transports against one throwaway student account and asserting
their views agree at every step. Design: `execution/PHASE_7_SYSTEM_DESIGN.md` § 9.

## Why two transports instead of driving the real Android app

There is no way to drive the real Android app headlessly from this script. What actually varies
between Website and Android/KMP is the **auth transport** — browser cookies + a same-origin CSRF
header vs. an `Authorization: Bearer` header (see `KtorHttpClient` in
`shared/src/commonMain/kotlin/com/mentora/shared/network/`) — not the business logic, which is
server-side and identical for both. `SdkTransport` reproduces the Bearer-header transport exactly.
T10/T11 (the real browser/emulator walks) are what exercises each real client's own rendering and
interaction code; this script proves the shared backend contract both clients are built on, which
a UI walk alone cannot.

## What it checks

One throwaway `@xclient.mentora.test` student is registered fresh on every run (never a seed
account — seed accounts' role-defining state must never be mutated). Both transports reuse the
same access token from that one registration. Eight assertion groups run in sequence:

| Group | Proves |
|---|---|
| C1 | Both transports authenticate as the same principal/role |
| D1 | Checkout completion is idempotent (repeat completes as a no-op) |
| A1 | Enrollment list is identical via both transports |
| A2/E1 | Lesson-complete + position writes from one transport are visible via the other |
| E2 | Student quiz fetch strips `isCorrect` on both transports, content identical |
| A3 | Quiz attempt result is identical via both transports |
| A4 | Certificate is identical via both transports — reported **NOT-EXERCISED**, not fabricated, if the quiz didn't pass or no certificate was issued |
| A5 | AI Tutor (stub mode) conversation history is identical via both transports |
| E3 | Learning path follow state is identical via both transports |

To let A4 actually exercise (rather than perpetually report NOT-EXERCISED), the script completes
every lesson in the seeded quiz course before submitting the quiz attempt, satisfying
`CertificateService.checkAndIssueIfComplete`'s `completedLessonCount == totalLessons` precondition.

## Honesty contract

Every group reports `PASS`, `FAIL`, or `NOT-EXERCISED`. Nothing is fabricated: if a precondition
for a group isn't actually met at runtime (e.g. the quiz doesn't pass, or no learning path is
seeded), that group is reported `NOT-EXERCISED` with the reason, never silently skipped or
reported as a false `PASS`.

## Running it

Requires a live local stack: MongoDB replica set `rs0` + the backend running with `seedDemoData`
applied (see `backend/README.md` "One-time MongoDB setup", or run `start-mentora.ps1`). The
Website is not required — this script talks to the backend directly.

```
node tools/cross-client-check/check.js [--base-url http://localhost:8080]
```

Exit code `0` if every group is `PASS` or `NOT-EXERCISED`; `1` if any group `FAIL`s.

## Rate limiting

`plugins/RateLimiting.kt`'s `"auth"` bucket allows 10 req/min across register+login combined. This
script makes exactly one register call per run and reuses the resulting tokens for both
transports — no separate login call is needed. If a 429 is hit anyway (e.g. running the script
repeatedly in a tight loop), requests retry with the same backoff shape `web/e2e/helpers.ts` uses
(4 attempts, `8s * attempt`).

## Scope

This script only touches the fresh throwaway account's own data. It never mutates seed-account
state, never touches instructor/admin-only routes, and performs no cleanup — the throwaway account
and its enrollment/progress/certificate/conversation data are simply left in the local database
(harmless for a local demo environment; not intended to run against a shared/production backend).
