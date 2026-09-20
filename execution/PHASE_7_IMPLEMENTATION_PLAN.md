# Phase 7 — Full Integration: Implementation Plan

**Status:** Authoritative task plan for Phase 7, derived by the `architect` subagent on 2026-09-20 from
`execution/PHASE_7_ACCEPTANCE_CRITERIA.md` (the acceptance bar, categories A-J plus the section-H scope
reconciliation), `execution/PHASE_7_SYSTEM_DESIGN.md` (the architecture — read it first), the locked
`architecture/IMPLEMENTATION_ROADMAP.md` M16 deliverables, and the real source of `backend/`, `web/`,
`mobile/shared/`, `mobile/androidApp/` and `.github/workflows/ios-ci.yml`. To be logged as
`DECISIONS_LOG.md` **D147**. Read this file before resuming any Phase 7 task — do not re-derive
acceptance criteria from memory.

Per-task status is tracked in `execution/CURRENT_STATUS.md`'s Phase 7 table; this file is the
acceptance detail that table points to. This is an execution planning document, not a locked doc;
amend it with a `DECISIONS_LOG.md` entry.

---

## 0. Repo state at plan time

- `main` @ `60e71b7`, clean, in sync with `origin/main`.
- Backend suite **127/127**; `:shared` **249/249**; `:androidApp` JVM **241/241**; `:androidApp`
  instrumented **106/106** (local only); Playwright **chromium 19/19, firefox 19/19, webkit 1/19**
  (the last being a real, disclosed, non-product cookie-policy incompatibility, D64).
  **These are the regression baselines for every task below.**
- `.github/workflows/` contains exactly one file: `ios-ci.yml`. **No backend/web/android CI exists.**
- Phase 6 (AI Tutor): implementation complete; live provider verification deferred by user decision
  (D146). Stub mode only, everywhere, always, in this phase.
- Phase 5 (iOS): frozen. **No task in this plan touches `mobile/iosApp/` or `ios-ci.yml`.**
- One known, disclosed, unfixed cross-client bug carried in: D144 / criterion C4 — root-caused in
  `PHASE_7_SYSTEM_DESIGN.md` section 7.
- Next decision id: **D147**.

## 1. Sequencing and execution rules

1. **Dependency order:** baseline audit (T0) -> CI workflows (T1-T5) -> the C4 fix, now CI-guarded
   (T6) -> **review checkpoint (T7)** -> scripted/static verification (T8, T9) -> live client walks
   (T10, T11) -> demo-environment and docs accuracy (T12) -> final acceptance audit and handoff (T13).
   CI comes before the fix deliberately: the fix then lands with a real automated gate underneath it.
2. **Standing regression gate at every single task.** Nothing may go red that was green at T0: backend
   127/127, `:shared` 249/249, `:androidApp` JVM 241/241, Playwright chromium 19/19. A task is not
   done if it changed an existing assertion to make itself pass (J1, J5).
3. **One commit per task**, plus a `CURRENT_STATUS.md` continuity commit — the Phase 1-6 convention.
   Push after each commit. Never one giant Phase-7 commit (J4).
4. **Host tags.** **CI** = a GitHub Actions workflow file; **W** = fully completable on this Windows
   host; **M** = requires a human/agent driving a real client (browser or emulator); **D** =
   documentation only.
5. **Scope discipline, restated (J5).** Every task below traces to a named acceptance-criteria row or
   to a named locked M16 deliverable. If executing a task surfaces work that traces to neither,
   **record it as a finding and stop** — do not fold it in. The known candidates for this are already
   listed in `PHASE_7_SYSTEM_DESIGN.md` section 11.
6. **AI Tutor constraint, restated (F2/D146).** No task requests, configures, simulates or assumes a
   real `AI_PROVIDER_API_KEY`. Workflows set it to the empty string *explicitly*.
7. **iOS constraint, restated (H1).** `ios-ci.yml` is read-only input. If any task would modify it,
   the task is wrong.

## 2. Task summary (dependency-ordered)

| # | Task | Host | Covers | Depends on |
|---|---|---|---|---|
| T0 | Baseline green-run + CI precondition audit (no code change) | **W** | J1 baseline, preconditions for T1-T5 | — |
| T1 | `backend-ci.yml` — build + test on push/PR to main, MongoDB rs0 in the job | **CI** | I1, I6, J2 | T0 |
| T2 | `web-ci.yml` job 1: `web-static` — ESLint + logical-properties + design-to-code + typecheck + build | **CI** | I2 (static half), G3, G4, J2 | T0 |
| T3 | `web-ci.yml` job 2: `web-e2e` — full local stack (Mongo rs0 + seeded backend + Next) + Playwright chromium | **CI** | I2 (E2E half), B-flow coverage on Web, J2 | T1, T2 |
| T4 | `android-ci.yml` — lint + `:shared`/`:androidApp` JVM tests + instrumented-source compile + assembleDebug | **CI** | I3, J2 | T0 |
| T5 | `tokens-ci.yml` — token-pipeline drift gate (**flagged, droppable — see Design section 6**) | **CI** | M16 "all five workflows" | T0 |
| T6 | C4 fix: Android Course Details / Learning Path Details auth-state staleness (D144) | **W** | C4, C1, A6-adjacent, J1 | T4 |
| T7 | **Review checkpoint** — Opus review of T1-T6; `codex-reviewer` second opinion only if the criteria are met | **W** | J5, quality | T6 |
| T8 | Cross-client parity harness (`tools/cross-client-check/`) + first full run | **W** | A1-A5, C1, D1, E1-E3 (contract half) | T7 |
| T9 | Static confirmations pass: A6, C3, D2, F2, I4 (no code change) | **W** | A6, C3, D2, F2, I4 | T7 |
| T10 | Website portfolio-priority flow walk: EN + AR, Light + Dark | **M** | B1, B2, B5, C2, E1-E3 (web half), F1 (web half), G1, G2 | T8, T9 |
| T11 | Android portfolio-priority flow walk on the emulator: EN + AR, Light + Dark, **including the C4 live repro** | **M** | B3, B4, B5, C2, C4, E1-E3 (android half), F1 (android half), G1, G2, A1-A4 live spot-checks | T6, T8, T9 |
| T12 | Demo-environment + documentation accuracy pass (READMEs, start/stop scripts, CI sections) | **D** | I5, I6, J3 | T10, T11 |
| T13 | Final acceptance audit (A-J, criterion by criterion) + `PHASE_HANDOFF.md` Phase 7 entry + `CURRENT_STATUS.md` closure | **W/D** | J1-J5 | T12 |

**Sizing note.** 14 units for a phase that is mostly verification: 5 CI units (each independently
landable and independently revertible), 1 small code fix, 1 review checkpoint, 2 scripted/static
verification units, 2 live-walk units, 2 documentation/audit units. This matches the project's
established granularity (Phase 6 used 9 for a smaller phase; Phase 4 used 20 for a much larger one).

---

## 3. Task detail

### T0 — Baseline green-run + CI precondition audit  *(Host: W)*

**Scope.** No code change. Establish the regression baseline every later task is measured against, and
resolve the three unknowns that would otherwise make a CI task fail on its first run.

- Re-run and record, fresh (not from Gradle's up-to-date cache — use `--rerun-tasks` or `clean`):
  `backend> ./gradlew test`; `mobile> ./gradlew :shared:testDebugUnitTest :androidApp:testDebugUnitTest`.
- Record `web> npm run lint`, `npm run lint:logical-properties`, `npm run validate:design-to-code`,
  `npm run typecheck`, `npm run build` — including the known single pre-existing `<img>` **warning**
  (it must stay a warning; see Design 4.1).
- **Precondition 1 (blocks T4):** run `mobile> ./gradlew :androidApp:lintDebug` and record the result.
  Apply Design 5.3's three-option resolution rule. If a baseline is required, create it in T4 and
  record it in `DECISIONS_LOG.md` — do not silently disable the lint step.
- **Precondition 2 (blocks T5):** run `node tools/token-pipeline/generate.js`, then `git status`.
  Pre-existing drift is a real finding: regenerate, commit, and note what changed. If drift exists and
  cannot be cleanly resolved, T5 is deferred with a recorded reason rather than shipped red.
- **Precondition 3 (blocks T3):** run the Playwright suite locally on chromium against the local stack
  and record the pass count, so a CI failure later can be attributed to the CI environment rather than
  to a pre-existing suite problem.
- Confirm `.github/workflows/` still contains exactly `ios-ci.yml`, and resolve `actions/setup-node`'s
  current major tag deliberately (Design 2.1).

**Files touched.** None (possibly `execution/DECISIONS_LOG.md` for a precondition finding).

**Tests.** The baseline runs themselves.

**Completion gate.** Every baseline figure recorded in the task's own notes/commit message; all three
preconditions resolved to a definite answer (green, or a recorded, decided plan); no source file
changed by this task except a documentation/decision entry.

---

### T1 — `backend-ci.yml`  *(Host: CI)*  **covers I1**

**Scope.** Exactly `PHASE_7_SYSTEM_DESIGN.md` section 3 — no more. Triggers (workflow_dispatch + push
+ pull_request on `main`, path-filtered on `backend/**` and the workflow file itself, with the
`'!**/*.md'` negative pattern), concurrency group, `permissions: contents: read`, `ubuntu-latest`,
`timeout-minutes: 30`, the section-2.3 MongoDB replica-set block, JDK 21 + `setup-gradle`, then
compile / test / assemble as three separate steps, test-report artifact, mongo-logs-on-failure, job
summary.

**Must be written into the file header** (the `ios-ci.yml` convention): what this is; what it is NOT
(no emulator, no browser, no live client, no deploy); why MongoDB is started with `docker run` and not
`services:`; that the tests hardcode the replica-set URI in source, so the env var is belt-and-braces;
**D-LINT** (no backend linter exists and Phase 7 deliberately does not add one, Design 3.4); the
explicitly-empty `AI_PROVIDER_API_KEY` and why (D146/F2); the action-version resolution date.

**Files touched.** `.github/workflows/backend-ci.yml` (new).

**Tests.** The workflow's own run is the test. No source or test file changes.

**Completion gate.**
(a) The workflow runs green on the repository's actual current state — **127/127**, not a reduced set.
(b) It is triggered by a real `main` push (or `workflow_dispatch`) and the run link is recorded.
(c) The replica-set step reaches PRIMARY and logs it; a deliberate local sanity check confirms that
removing the `rs.initiate` host pin would break it (understanding, not a committed experiment).
(d) `git diff` touches exactly one file.
(e) No `secrets.*` reference anywhere in the file.

---

### T2 — `web-ci.yml`, job `web-static`  *(Host: CI)*  **covers I2 (static half), G3, G4**

**Scope.** Exactly Design 4.1. File-level triggers cover both jobs, so this task creates
`web-ci.yml` with the `on:`/`concurrency:`/`permissions:` block **plus the `web-static` job only**;
T3 adds the second job to the same file.

Steps: checkout, `setup-node` (node 22, npm cache keyed on `web/package-lock.json`), `npm ci`,
`npm run lint`, `npm run lint:logical-properties`, `npm run validate:design-to-code`,
`npm run typecheck`, `npm run build` with `API_BASE_URL=http://localhost:8080`.

**Do not** add `--max-warnings=0` (Design 4.1) — the pre-existing `<img>` warning is a known, recorded,
accepted item and turning it red is out of scope.

**Files touched.** `.github/workflows/web-ci.yml` (new).

**Tests.** The workflow's own run.

**Completion gate.** Green on the current repository state; all five checks genuinely executed (verify
in the log, not just by exit code); one file changed; job completes in under ~10 minutes.

---

### T3 — `web-ci.yml`, job `web-e2e`  *(Host: CI)*  **covers I2 (E2E half), J2**  **<- highest-risk CI task**

**Scope.** Exactly Design 4.2-4.4: add a second job to `web-ci.yml`, `needs: web-static`,
`timeout-minutes: 45` — MongoDB rs0, JDK 21 + Gradle, `buildFatJar`, `seedDemoData` (with
`working-directory: backend`, which is load-bearing for `MEDIA_STORAGE_ROOT`), start the jar and poll
`/healthz`, `npm ci`, `playwright install --with-deps chromium`, `npm run build`, `next start` and poll
`/en`, `playwright test --project=chromium`, artifact upload, job summary.

**Decisions to state in the file header:** **D-E2E-BROWSER** — chromium only, because WebKit is
known-red for a real non-product reason (D64) and Firefox roughly doubles wall-clock for near-zero
marginal signal; firefox/webkit remain local/manual.

**Files touched.** `.github/workflows/web-ci.yml` (modified).

**Tests.** The suite itself: **19/19 on chromium**.

**Completion gate.**
(a) Green, 19/19, with no spec skipped, deleted, or weakened.
(b) Green **twice consecutively** (this is the one task where the M15 "green twice in a row" standard
is applied immediately, because it is the flake-prone one).
(c) If it cannot be made green twice without weakening assertions, **stop and record a decision** —
the sanctioned fallback is narrowing to the portfolio-priority specs `01`-`08` with an explicit
`DECISIONS_LOG.md` entry, never silently deleting assertions or adding blanket retries.
(d) No change to `playwright.config.ts`, `web/e2e/**`, or any product source file.

---

### T4 — `android-ci.yml`  *(Host: CI)*  **covers I3**

**Scope.** Exactly Design section 5. Path filters include `mobile/shared/**` **and**
`mobile/androidApp/**` and exclude `mobile/iosApp/**`; `ubuntu-latest`; `timeout-minutes: 45`; JDK 21;
an ANDROID_HOME precondition step that fails loudly; `setup-gradle`; then `:androidApp:lintDebug`,
`:shared:testDebugUnitTest :androidApp:testDebugUnitTest`,
`:androidApp:compileDebugAndroidTestKotlin`, `:androidApp:assembleDebug`; APK + report artifacts; job
summary.

If T0's precondition 1 found lint errors, apply Design 5.3 here (fix trivially, or add
`mobile/androidApp/lint-baseline.xml` **with a `DECISIONS_LOG.md` entry listing what was baselined**).

**Must be written into the file header:** **D-EMU** — instrumented tests are deliberately out of CI,
with all three reasons from Design 5.4 and the explicit note that criterion I3 permits exactly this
"per a documented, explicit decision"; plus the mitigation (instrumented sources are compiled every
run); plus why `:shared`'s iOS targets do not break a Linux runner
(`kotlin.native.ignoreDisabledTargets` + the host-guarded SKIE application); plus that
`:shared:liveBackendIntegrationTest` must never be invoked here.

**Files touched.** `.github/workflows/android-ci.yml` (new); possibly
`mobile/androidApp/build.gradle.kts` + `mobile/androidApp/lint-baseline.xml` (only if 5.3 option 3
applies).

**Tests.** `:shared` 249/249 and `:androidApp` 241/241 in CI, matching T0's local baseline exactly.

**Completion gate.** Green; both unit-test counts match the T0 baseline; the debug APK artifact is
downloadable (T11 can use it); `connectedDebugAndroidTest` appears nowhere in the file; `ios-ci.yml`
untouched.

---

### T5 — `tokens-ci.yml`  *(Host: CI)*  **covers M16's "all five workflows"** — **FLAGGED, DROPPABLE**

**Scope.** Exactly Design section 6: regenerate every token output with
`node tools/token-pipeline/generate.js` and fail on any `git diff`. Nothing else.

**This task is flagged.** It traces to a literal locked M16 deliverable but is **not** listed in
`PHASE_7_ACCEPTANCE_CRITERIA.md` section I. **If the T7 reviewer or the user prefers a strict
section-I reading, delete this task** — nothing else in the plan depends on it.

**Precondition.** T0's precondition 2 must have found (or made) the working tree clean after a
regeneration. Shipping this workflow on top of pre-existing drift is not permitted.

**Files touched.** `.github/workflows/tokens-ci.yml` (new).

**Completion gate.** Green on the current state; a deliberate local experiment (edit a token value,
regenerate, observe the diff, revert) confirms the gate actually catches drift rather than passing
vacuously; one file changed.

---

### T6 — C4 fix: Android auth-state staleness on Course Details / Learning Path Details  *(Host: W)*  **covers C4**

**Scope.** Exactly `PHASE_7_SYSTEM_DESIGN.md` section 7.4 — Option D, the minimal fix. **Not** a
redesign of auth-state management, **not** a navigation change, **not** an SDK change.

- `mobile/androidApp/src/main/kotlin/com/mentora/android/ui/coursedetails/CourseDetailsViewModel.kt`:
  turn the captured `isAuthenticated` into a private mutable field seeded from the constructor
  parameter; add `fun onAuthenticationChanged(value: Boolean)` with the equality guard and a
  `loadCourse()` re-run; **correct the false invariant claim in the class kdoc** (lines 70-76).
- `.../ui/coursedetails/CourseDetailsScreen.kt`: add
  `LaunchedEffect(isAuthenticated) { viewModel.onAuthenticationChanged(isAuthenticated) }` immediately
  after the `viewModel(...)` call. `CourseDetailsScreenContent` is not touched.
- `.../ui/learningpathdetails/LearningPathDetailsViewModel.kt` + `LearningPathDetailsScreen.kt`: the
  identical two-line pattern, for the identical root cause (Design 7.2's sibling note).
- `.../navigation/MentoraNavHost.kt`: **comment-only** correction at lines 267-270 and on
  `learningPathDetailsContent` — state the real invariant (only the no-pending-intent login branch and
  the logout branch reset the stack). **No behavioural change in this file.**

**Explicitly out of scope for this task:** `AuthGate.kt`, `decideAuthGate`, `AppSessionViewModel`,
`MentoraSdk`, `SessionManager`, the pending-intent mechanism, every navigation reset site, and
anything under `mobile/shared/`, `backend/` or `web/`.

**Files touched.** 5 Android files (4 real changes + 1 comment-only), plus 2 test files.

**Tests.** Four new JVM unit tests per Design 7.5, in the existing
`CourseDetailsViewModelTest`/`LearningPathDetailsViewModelTest`. **Every existing case in both files
must pass unmodified** — if one needs editing, stop and re-review, do not edit the assertion.
Optionally add a `NavigationShellTest` case (instrumented, local-only, **not** a completion gate).

**Completion gate.**
(a) `:androidApp:testDebugUnitTest` green at **241 + the new cases**, with zero pre-existing tests
modified.
(b) `:shared:testDebugUnitTest` still 249/249.
(c) `android-ci.yml` (T4) green on the commit.
(d) `git diff --stat` shows **no** file outside `mobile/androidApp/src/` — this is the mechanical proof
that the fix stayed minimal.
(e) The live emulator repro is deferred to T11 and explicitly listed there; **C4 is not marked done
until T11 confirms it on a real device.**
(f) G3/G4 trivially satisfied and stated: the diff adds no user-facing string and no layout.

---

### T7 — Review checkpoint  *(Host: W)*

**Scope.** An independent Opus review (`reviewer` subagent) of everything T1-T6 produced, against this
plan and the System Design: the CI workflows (secrets posture, scope boundary, failure-loudness, no
deploy step), the C4 fix (minimality, correctness of the root-cause claim, test honesty), and
compliance with J5 (nothing landed that traces to neither an acceptance row nor an M16 deliverable).

**Whether to also run `codex-reviewer`:** T6 is an auth/session-adjacent change, which is one of the
listed criteria for an additional independent second opinion — but it is a 4-line, 2-screen,
no-SDK-change fix with no permission/RBAC/data-integrity implication. **Recommendation: invoke
`codex-reviewer` only if the Opus review finds something serious or uncertain in the C4 fix, or if T3's
E2E job required a fallback decision.** Do not invoke it speculatively. If invoked, report its findings
separately and clearly labelled, never merged into the Opus review.

**Files touched.** None (findings may generate follow-up commits).

**Completion gate.** Every HIGH/MEDIUM finding either fixed or explicitly, reasonedly declined in
`DECISIONS_LOG.md`; full suites re-run green after any fix; no finding left silently open.

---

### T8 — Cross-client parity harness + first full run  *(Host: W)*  **covers A1-A5, C1, D1, E1-E3 (contract half)**

**Scope.** Build and run `tools/cross-client-check/` exactly per `PHASE_7_SYSTEM_DESIGN.md` section 9:
plain Node, zero dependencies, two transports (cookie as the Website sends, Bearer as `MentoraSdk`
sends), one throwaway `@xclient.mentora.test` student, the eight ordered assertion groups, PASS/FAIL
per assertion, non-zero exit on any failure, side-by-side payload printing on mismatch, backoff that
respects the real 10-req/min auth bucket.

**Preconditions for the run:** local stack up (`start-mentora.ps1`), `backend> ./gradlew seedDemoData`
applied, stub AI provider mode (empty key), MongoDB replica set healthy.

**Hard honesty rules for this task:**
- The script asserts **parity**, not a specific outcome, everywhere except D1's idempotency (which has
  a defined correct outcome) and E2's `isCorrect`-stripping (which has a defined correct outcome).
- If the A4 certificate path cannot be reached because the seeded quiz attempt does not pass, the
  script reports **A4 NOT EXERCISED** and exits non-zero. It must never synthesise a certificate or
  claim A4 passed.
- It must not mutate a seed account's state.

**Files touched.** `tools/cross-client-check/check.js` (new), `tools/cross-client-check/README.md`
(new). **No production source file.**

**Tests.** The harness run is the test. Its output is pasted into the task record.

**Completion gate.** All eight assertion groups reported explicitly (PASS or an honest FAIL/NOT
EXERCISED); every FAIL either fixed (if it is a real product defect traceable to an acceptance row) or
recorded as a disclosed finding with a decision; `git diff --stat` shows only the two new `tools/`
files; the script is re-runnable from a clean local stack by someone else.

---

### T9 — Static confirmations pass  *(Host: W)*  **covers A6, C3, D2, F2, I4**  — no code change

**Scope.** Formally re-run and record the checks the System Design already pre-confirmed (section 10),
so they move from "architect asserted it" to "Phase 7 verified and recorded it".

- **A6** — confirm no client recomputes `completionPercent`, `quizPassed`, `courseCompletedAt` or
  enrollment status; record the single documented exception (`isEnrolled` derived on both clients, with
  the Web-first-page vs. Android-full-pagination difference) and **take the Design section 11 finding
  F1 decision explicitly** (recommended: record as a disclosed, accepted divergence).
- **C3** — confirm Android still has zero instructor/admin destinations; paste the grep.
- **D2** — re-run the payment scan with **word-boundary anchors** (Design 8.2's `alreadyEnrolled`
  false-positive gotcha) across `backend/src`, `web/src`, `mobile/shared/src`, `mobile/androidApp/src`;
  paste the exact command and its empty output.
- **F2** — confirm the local provider key is empty, that no workflow file references any secret, and
  that no Phase 7 diff introduced one.
- **I4** — `git log -- .github/workflows/ios-ci.yml` shows no Phase 7 commit; the last iOS CI run is
  still green.

**Files touched.** `execution/DECISIONS_LOG.md` (the F1 decision) only.

**Completion gate.** Each of the five items has a pasted, reproducible command and its real output in
the task record; the F1 divergence decision is recorded, not left implicit; no production file changed.

---

### T10 — Website portfolio-priority flow walk  *(Host: M)*  **covers B1, B2, B5, C2, E1-E3 (web half), F1 (web half), G1, G2**

**Scope.** Self-performed Chrome browser automation (mechanism **M-WEB**, the same one Phase 6 T7 used)
against the live local stack, as a **seeded** account, walking all nine steps start to finish:

Discovery -> Course Details -> Demo Checkout -> Purchase Success -> Course Player -> Progress -> Quiz
-> AI Tutor -> Certificate.

- Run `backend> ./gradlew seedDemoData` immediately before and capture its summary (**B5**).
- Walk `/en` and `/ar` (**B1/B2**), covering Light and Dark per the Design 8.2 G1/G2 matrix — **state
  in the task record which matrix option was used** (full four-run, or the en-Light + ar-Dark fallback
  plus targeted spot checks). Do not silently reduce it.
- One screenshot per step per run; RTL mirroring explicitly inspected on Course Details, Checkout,
  Player, Quiz and AI Tutor.
- **C2:** a separate short guest walk confirming the auth gates fire where the product says they do and
  return to the intended destination after login.
- **A1/A2 live half:** enroll and complete a lesson here as the account T11 will then open on Android.
- **F1:** cheap re-confirm of the AI Tutor surface (quick actions, thinking state, error + retry) in
  stub mode only — not a from-scratch re-verification.

**Files touched.** None.

**Completion gate.** A step-by-step PASS/FAIL table for each language/theme run, with screenshots; any
FAIL triaged into (a) a real cross-client inconsistency traceable to an acceptance row -> fix it, or
(b) a pre-existing disclosed limitation -> cite where it was already disclosed, or (c) a genuinely new
finding -> record it and stop rather than folding a fix in (J5). No product code changed by this task
unless (a) applies, in which case the fix is its own commit.

---

### T11 — Android portfolio-priority flow walk (emulator)  *(Host: M)*  **covers B3, B4, B5, C2, C4, E1-E3 (android half), F1 (android half), A1-A4 live spot-checks**

**Scope.** Delegated to a background QA agent driving a real debug APK on an emulator via `adb`
(mechanism **M-AND**, the same one Phase 6 T7 used, per the standing instruction that Android must be
genuinely exercised, never statically inspected). Backend reached at `10.0.2.2:8080`.

- APK from `android-ci.yml`'s artifact (T4) or a local `:androidApp:installDebug`.
- Same nine steps, English and Arabic (**B3/B4**), Light and Dark per the same stated matrix choice as
  T10 (**G1/G2**).
- **C4 live repro (mandatory, and the gate for criterion C4):** as a guest, open Course Details, tap
  "Login to Enroll", complete login, land on Demo Checkout, press back, and **confirm the CTA now reads
  "Enroll"** (or "Continue Learning" if already enrolled) rather than the stale "Login to Enroll".
  Repeat for Learning Path Details' Follow affordance.
- **A1-A4 live spot-checks:** log in as the account T10 used and confirm the enrollment, the lesson
  progress percentage, the quiz result and the certificate all appear, matching what the Website showed
  — no reinstall, no cache clear.
- **F1:** the same cheap AI Tutor re-confirm, stub mode only.

**Files touched.** None.

**Completion gate.** A step-by-step PASS/FAIL table per run with screenshots; the C4 repro captured as
a before/after pair (the "before" may be cited from D144 rather than re-broken); the A1-A4 spot-checks
each explicitly recorded as matching the Website's values; the same FAIL triage rule as T10.

---

### T12 — Demo-environment and documentation accuracy pass  *(Host: D)*  **covers I5, I6, J3 (partial)**

**Scope.** Bring the runnable-demo documentation back into exact agreement with what Phase 7 changed.

- Re-read `start-mentora.ps1` and `stop-mentora.ps1` end to end and confirm they are still accurate
  (Phase 7 changes nothing they touch — confirm that, do not assume it).
- `backend/README.md` — add a short **CI** section describing `backend-ci.yml`, including the fact that
  CI starts its own MongoDB replica set the same way the local one-time setup does, and the D-LINT
  note (no backend linter by decision).
- `web/README.md` — add a **CI** section describing both `web-ci.yml` jobs; state plainly that CI runs
  **chromium only** and why (D64), that firefox/webkit stay local/manual, and repeat the existing local
  warning that a local E2E run pollutes the shared dev database.
- `mobile/androidApp/README.md` — add a **CI** section describing `android-ci.yml`, and state the
  **D-EMU** decision (instrumented tests are local-only, by explicit decision, with the reason) plus
  the fact that the debug APK is downloadable from the workflow artifact.
- If T5 shipped, add a one-line pointer to `tokens-ci.yml` wherever the token pipeline is documented.
- **I6 re-confirm:** no workflow, script or README introduced a cloud deployment, hosting account, or
  app-store distribution step (ADR-012).

**Files touched.** `backend/README.md`, `web/README.md`, `mobile/androidApp/README.md`, possibly
`design-system`/token docs. **No source file.**

**Completion gate.** Every README's CI section matches the workflow file it describes, command for
command (verified by reading both side by side, not from memory); the start/stop scripts confirmed
accurate; the I6 statement made explicitly; no cloud/deploy/store step anywhere.

---

### T13 — Final acceptance audit + handoff  *(Host: W/D)*  **covers J1-J5**

**Scope.** The Phase 6 T9 pattern, applied to Phase 7: a criterion-by-criterion matrix over every row
of `execution/PHASE_7_ACCEPTANCE_CRITERIA.md` sections A-J, updated **in place**, each row backed by
implementation evidence -> test/verification evidence -> PASS / PASS-STRUCTURAL / N/A / ACCEPTED GAP.
Not a blanket "looks done" declaration.

- **J1:** re-run every suite fresh (`--rerun-tasks`/`clean`, not from cache): backend, `:shared`,
  `:androidApp` JVM, Playwright chromium. Every count must equal or exceed the T0 baseline.
- **J2:** confirm all new workflows are green on the repository's real final state.
- **J3:** update `execution/CURRENT_STATUS.md` (top banner + a Phase 7 task table), add
  `execution/DECISIONS_LOG.md` entries for every decision this phase actually took (D-LINT, D-ALINT if
  a baseline was added, D-EMU, D-E2E-BROWSER, the C4 fix, the F1 `isEnrolled` divergence, and the T5
  keep-or-drop call), and add a Phase 7 entry to `execution/PHASE_HANDOFF.md` in the established
  10-subsection format.
- **J5:** state explicitly, per change, which acceptance row or M16 deliverable it traces to.
- Mark the three already-true-by-construction rows (**A6**, **C3**, **D2**) as verified-and-recorded,
  citing T9's evidence — not as newly built work.
- Any criterion that is genuinely not met must be recorded honestly as an ACCEPTED GAP or NOT MET with
  its reason, exactly as Phase 6's audit did for its seven blocked items. **Nothing may be upgraded to
  PASS to close the phase.**

**Files touched.** `execution/PHASE_7_ACCEPTANCE_CRITERIA.md`, `execution/CURRENT_STATUS.md`,
`execution/DECISIONS_LOG.md`, `execution/PHASE_HANDOFF.md`.

**Completion gate.** Every A-J row has a status and evidence; all suites green and recorded; all
workflows green and linked; clean git tree; one commit per task present in history; the Phase 7
handoff entry complete. **Phase 8 must not begin without the user's own separate, explicit approval**,
per the standing phase-execution policy.

---

## 4. What this plan deliberately does NOT do

- **Does not resume iOS** in any form. `mobile/iosApp/` and `ios-ci.yml` are read-only (H1).
- **Does not touch the AI provider.** Stub mode only; no key requested, configured, simulated or
  assumed (H3/F2/D146).
- **Does not add a backend linter** (Design 3.4), **does not run instrumented tests in CI**
  (Design 5.4), and **does not run WebKit in CI** (Design 4.3) — each is a recorded decision with a
  stated reason, not an omission.
- **Does not redesign Android auth-state management.** T6 is four lines of behaviour change across two
  screens (Design 7.3-7.4).
- **Does not change the backend API contract**, including the tempting `isEnrolled`-on-course change
  (Design section 11, finding F1, option iii — explicitly out of scope for this phase).
- **Does not introduce any cloud deployment, hosting, or store-distribution step** (ADR-012, I6).
- **Does not add product features.** Every task traces to a named acceptance row or a named M16
  deliverable (J5).
