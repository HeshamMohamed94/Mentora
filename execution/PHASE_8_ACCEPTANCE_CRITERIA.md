# Phase 8 — QA, Polish & Portfolio Demo — Acceptance Criteria

**Status:** OPEN — authored at Phase 8 kickoff (2026-09-22), before any Phase 8 code change. This is the
FINAL Mentora phase; there is no Phase 9. Every row below must resolve to PASS, FAIL, PARTIAL,
NOT TESTABLE, or ACCEPTED LIMITATION before this phase can close — none may be left unmarked.

No formal Phase 8 acceptance-criteria document existed anywhere in the repository prior to this one
(confirmed by full-repo search). This checklist is derived strictly from already-locked authoritative
sources, not invented — see "Authority sources" below.

## Authority sources

1. `execution/MASTER_IMPLEMENTATION_PLAN.md` — Phase 8 definition (M14 + M15 + M17).
2. `architecture/IMPLEMENTATION_ROADMAP.md` — M14 "Localization & RTL Completion" (line 261),
   M15 "Testing Hardening" (line 279), M16 "Local Demo Readiness" (line 295, largely closed by Phase 7,
   residue only), M17 "Final Polish & Acceptance" (line 315).
3. `architecture/ARCHITECTURE.md § 5` — "What This Phase Explicitly Did Not Do" — the scope-discipline
   checklist M17 requires cross-checking against everything actually built.
4. `architecture/TESTING_STRATEGY.md` — the locked target test coverage M15 must reach.
5. `design-system/DESIGN_SYSTEM.md` (v1.3.2, **LOCKED — audited, never edited**), `COMPONENTS.md`,
   `ACCESSIBILITY.md`, `LOCALIZATION.md`, `DESIGN_RULES.md`, `CONTENT_RESILIENCE.md`.
6. `product/SCREEN_INVENTORY.md` — the 29-screen surface Phase 8's QA pass must cover.
7. `execution/PHASE_HANDOFF.md` §§ 6 (Phases 1/2/3/4/6/7 "Known limitations") — every carried-forward gap
   this phase must give an explicit disposition, not silently re-carry.
8. `execution/DECISIONS_LOG.md` D146 (AI provider live-verification deferral, unaffected by this phase)
   and the Phase 5 freeze (iOS T1-T11 done, T12-T23 not started, untouched by this phase).
9. The Phase 8 kickoff message itself (2026-09-22) — QA/defect cleanup/visual polish/Design System
   compliance/acceptance verification/regression testing/demo readiness/documentation cleanup/portfolio
   readiness; explicitly NOT a redesign or new-feature phase; iOS stays deferred; no real AI provider call.

**Do not re-derive scope from memory or re-litigate any of the above — this checklist assumes them as given.**

---

## H. Scope Reconciliation (resolve first — blocks grading of H-dependent rows below)

| # | Point | Resolution |
|---|---|---|
| H1 | M14/M16/M17 name six surfaces and "all three clients," including iOS. | **Resolved, per explicit user decision at Phase 8 kickoff:** Phase 8 grades exactly five surfaces / two clients — Public Web, Student Web, Instructor Web, Admin Web, Android. iOS's share of any M14/M16/M17 row is recorded as **ACCEPTED LIMITATION (Phase 5 deferred)**, never PASS. Consistent with Phase 7 § H1. |
| H2 | `TESTING_STRATEGY.md § 5` mandates a Vitest + React Testing Library unit/component test tier for Web. It has never existed — Web has Playwright E2E only. | **Resolved, per explicit user decision:** build a **bounded** Vitest+RTL tier covering the highest-value/highest-risk components only (quiz scoring/results, demo-checkout flow, auth forms) — not exhaustive coverage. Closes the locked-doc gap without open-ended scope. |
| H3 | Real Anthropic provider verification (D146) is deferred. | Unchanged, not reopened. Any Phase 8 row touching live AI behavior is **NOT TESTABLE — deferred by user decision**. No `AI_PROVIDER_API_KEY` requested, referenced, or simulated as real. Only existing mock/fake/test-provider infrastructure is used. |
| H4 | No accessibility/performance measurement tooling (axe/Lighthouse) exists in the repo. | **Resolved, per explicit user decision:** manual audit only. A11y against `ACCESSIBILITY.md` §§1-15 via browser a11y tree / keyboard nav / TalkBack / Accessibility Scanner spot-checks. Performance via stated-method manual timing. No new tooling dependency added. |
| H5 | Design System v1.3.2 is LOCKED. | Phase 8 audits **conformance to** it only. A real DS defect found here is recorded, never patched inside this phase. Zero files under `design-system/`, `product/`, `ux/`, `architecture/` are modified without the user's own explicit approval (small factual doc corrections — D-4, D-5 below — are flagged, not silently made). |
| H6 | The AppDialog scrim-dimming regression (§ A1) survived two prior bounded fix attempts with no root cause found and has zero real screen consumers. | **Resolved, per explicit user decision:** time-boxed investigation (one focused session) attempting a real fix; if not root-caused in that window, re-record as ACCEPTED LIMITATION and move on — no further time spent chasing it. |

---

## A. Known-Issue Disposition

| # | Criterion | Status |
|---|---|---|
| A1 | AppDialog scrim-dimming regression: re-reproduced on a real emulator, root-caused, and either FIXED (both `AppDialogColorAndLayoutTest` color assertions green) or re-recorded as ACCEPTED LIMITATION per H6's time-box. | NOT STARTED |
| A2 | Quiz Results answer-badge spacing (`web/src/components/screens/quiz-results-screen.tsx`) fixed; verified live in EN-LTR, AR-RTL, Light, Dark; no regression to the 19/19 Playwright suite. | NOT STARTED |
| A3 | `isEnrolled` Web/Android pagination divergence: explicit verdict — unified, or re-affirmed ACCEPTED LIMITATION with a reason restated against Phase 8 scope. | NOT STARTED |
| A4 | Phase 7 §6.4 quiz-recompletion routing quirk (re-passing an already-passed quiz routes to Course Player instead of a completion screen): reproduced, verdict recorded (fixed or ACCEPTED LIMITATION). | NOT STARTED |
| A5 | Phase 6 F6 (prompt-block "data, not instructions" preamble + case-sensitive tag matching) and F8 (`stream_failed` outcome bucket): verdict recorded, stub-verifiable without any provider key. | NOT STARTED |
| A6 | Every open item in `PHASE_HANDOFF.md` §§6 (Phases 1/2/3/4/6/7) re-read and assigned: STILL OPEN-ACCEPTED / FIXED IN PHASE 8 / NO LONGER APPLICABLE. | NOT STARTED |
| A7 | No known issue closed as "fixed" without a real, reproducible before/after verification on the affected client. | NOT STARTED |

## B. Localization & RTL Completion (M14)

| # | Criterion | Status |
|---|---|---|
| B1 | All 29 `SCREEN_INVENTORY.md` screens audited for EN/AR string coverage on each in-scope surface; no hardcoded English string reachable in `ar` locale. | NOT STARTED |
| B2 | Mechanical translation-key parity check exists and passes for Web (`messages/en.json` ↔ `ar.json`) and Android (`values/strings.xml` ↔ `values-ar/strings.xml`) — closes D-7 (locked M14 deliverable). | NOT STARTED |
| B3 | Parity check wired into CI (extends `web-ci.yml`/`android-ci.yml`, not a parallel workflow), proven non-vacuous by a deliberate temporary drift. | NOT STARTED |
| B4 | RTL QA pass: every in-scope screen inspected under `ar`/RTL for mirroring correctness per `LOCALIZATION.md`/`DESIGN_RULES.md`. | NOT STARTED |
| B5 | Video scrubber and Mentora wordmark confirmed still LTR/unmirrored under `ar` (named explicitly in M14). | NOT STARTED |
| B6 | Locale-aware formatting (dates, prices, durations, numerals) verified on real screens in both locales. | NOT STARTED |
| B7 | Embedded English content inside an Arabic page still renders LTR (re-confirmed across the wider screen set). | NOT STARTED |
| B8 | `08-language-switch` Playwright flow extended to assert a sampling of screens beyond Settings. | NOT STARTED |
| B9 | Arabic typography placeholder on Android (`FontFamily.SansSerif`): verdict recorded — real asset added or ACCEPTED LIMITATION. | NOT STARTED |

## C. Testing Hardening (M15)

| # | Criterion | Status |
|---|---|---|
| C1 | Backend/`:shared`/`:androidApp` JVM/lint/Playwright suites pass at or above the Phase 7 T13 baseline (127/249/246/0 lint errors/19 chromium) — zero regressions. | NOT STARTED |
| C2 | Android instrumented suite (106 tests, local emulator) run and accounted for test-by-test; the 2 AppDialog failures resolved per A1; any transient failure characterized, not hand-waved. | NOT STARTED |
| C3 | Bounded Web Vitest+RTL tier (H2) exists and runs in CI. | NOT STARTED |
| C4 | High-risk-area coverage (demo-checkout idempotency, completion→certificate transaction, quiz `isCorrect` stripping) re-confirmed genuinely covered. | NOT STARTED |
| C5 | CI green on a clean run, twice in a row, across all in-scope workflows — run IDs recorded. | NOT STARTED |
| C6 | Deliberate-regression sanity check: introduced on a scratch branch, confirmed caught by the suite, reverted. | NOT STARTED |
| C7 | Every flaky test observed is stabilized or documented with root cause and reason for acceptance. | NOT STARTED |
| C8 | D-5 (dead Testcontainers dependency vs. locked `TESTING_STRATEGY.md` claim) reconciled. | NOT STARTED |
| C9 | WebKit E2E remains ACCEPTED LIMITATION (D64) — not reopened, not silently dropped. | NOT STARTED |

## D. Design System v1.3.2 Conformance Audit (LOCKED — audit only)

| # | Criterion | Status |
|---|---|---|
| D1 | `tokens-ci.yml` green: zero drift between `design-tokens.json` and generated outputs. | PROVISIONAL PASS (B2) — local equivalent `npm run validate:design-to-code` (`web/`) run 2026-09-22: "Design-to-Code validation: 37 screens, 6 patterns, 11 shared files checked. Validation PASSED." Actual `tokens-ci.yml` workflow not re-run in this static-only batch; full CI confirmation deferred to C5/B11. |
| D2 | Android `MentoraTokens` drift test green. | PROVISIONAL PASS (B2) — `./gradlew :androidApp:testDebugUnitTest --tests "com.mentora.android.theme.MentoraTokensDriftTest"` run 2026-09-22: BUILD SUCCESSFUL, all drift assertions green (colors, typography, shape, elevation, spacing, icon size, touch target). CI confirmation deferred to C5/B11. |
| D3 | Spot-audit of portfolio-flow screens for ad-hoc/hardcoded styling bypassing a token or specified component. | NOT STARTED |
| D4 | Web ↔ Android visual-parity audit on the portfolio-priority flow; divergence corrected or disclosed. | NOT STARTED |
| D5 | Each documented DS-drift item (icon placeholders, `DataTable` breakpoint conflict D62, no real certificate asset, Arabic font placeholder, capped Instructor/Admin fidelity) given explicit verdict. | NOT STARTED |
| D6 | Zero files under `design-system/`, `product/`, `ux/`, `architecture/` modified without explicit user approval. | NOT STARTED |

## E. Accessibility Pass (M17 / manual, per H4)

| # | Criterion | Status |
|---|---|---|
| E1 | Contrast spot-checked against `ACCESSIBILITY.md §1`/§13, Light and Dark. | NOT STARTED |
| E2 | Focus states visible/correct on Web; full keyboard navigation of portfolio flow, no trap, no unreachable control. | NOT STARTED |
| E3 | Touch targets meet §4 minimum on Android and Web mobile viewport. | NOT STARTED |
| E4 | Screen-reader labels present/meaningful on portfolio flow (browser a11y tree; TalkBack/Accessibility Scanner). | NOT STARTED |
| E5 | Color never the only signal — re-verified on Quiz Results correct/incorrect breakdown, tied to A2. | NOT STARTED |
| E6 | Disabled-state contrast and error accessibility verified on both clients. | NOT STARTED |
| E7 | Text scaling (browser zoom; Android font scaling) — no clipping/unreachable content on portfolio flow. | NOT STARTED |
| E8 | Reduced-motion behavior honored where motion is used. | NOT STARTED |
| E9 | Long-translated-content resilience spot-checked in Arabic on text-dense screens. | NOT STARTED |

## F. Performance Sanity (M17, per H4)

| # | Criterion | Status |
|---|---|---|
| F1 | Page-load sanity measured for portfolio-flow Web screens, stated method, recorded numbers. | NOT STARTED |
| F2 | Video start time measured on Web and Android against local media storage. | NOT STARTED |
| F3 | AI Tutor response latency measured in stub mode only (H3/D146 stands). | NOT STARTED |
| F4 | Any unacceptable result fixed or recorded with a reason; no number reported without its method. | NOT STARTED |

## G. Cross-Client Parity & Regression

| # | Criterion | Status |
|---|---|---|
| G1 | `tools/cross-client-check/check.js` re-run against the live local stack; all 8 assertion groups PASS after Phase 8 changes. | NOT STARTED |
| G2 | Full nine-step portfolio-priority flow re-walked end-to-end on Website and Android after all fixes land — EN/AR, Light/Dark. | NOT STARTED |
| G3 | No Phase 8 fix reintroduces a Phase 7-fixed defect (C4 auth staleness, cross-account tab-state leak, shadowJar packaging, `keepalive` progress save). | NOT STARTED |
| G4 | `clearAllTabBackStacks()` remains correctly invoked at every account-switch site. | NOT STARTED |

## I. Portfolio Demo Readiness (M16 residue + M17)

| # | Criterion | Status |
|---|---|---|
| I1 | Clean-checkout dry run of `start-mentora.ps1`/`stop-mentora.ps1` + READMEs brings up the full local stack with no undocumented step. | NOT STARTED |
| I2 | `seedDemoData` produces demo-quality data: no empty state, no broken thumbnail, no unplayable video, no lorem-ipsum on a reviewer-visible screen. | NOT STARTED |
| I3 | Android demo path verified against local backend via `10.0.2.2`; hardcoded-base-URL limitation re-stated or resolved. | NOT STARTED |
| I4 | Nine-step portfolio-priority flow demo-ready end-to-end, both languages, every in-scope client (H1). | NOT STARTED |
| I5 | iOS simulator run-verification (M16) recorded as ACCEPTED LIMITATION — Phase 5 deferred, never PASS. | NOT STARTED |
| I6 | Repository presents well to a portfolio reviewer: no stray artifacts, no dead files, READMEs accurate, `git status` clean. | NOT STARTED |

## J. Documentation Accuracy & Scope Discipline (M17 cross-check)

| # | Criterion | Status |
|---|---|---|
| J1 | No real payment code path exists anywhere — word-boundary scan re-run across backend/web/mobile source. | PASS (B2) — word-boundary scan (stripe/paypal/creditcard/credit_card/payment_intent/braintree/square.js/adyen, case-insensitive) across `backend/src`, `web/src`, `mobile/shared/src`, `mobile/androidApp/src` re-run 2026-09-22: zero real matches. Only false-positive substring hits: "stripe" inside a code comment about a visual "vertical stripe texture" (`mobile/androidApp/.../CourseArtwork.kt`), and "adyen" as a case-insensitive substring of "alreadyEnrolled" in enrollment DTOs/services (backend, web, mobile shared). Corroborated by the existing `mobile/shared/src/commonTest/.../NoPaymentVocabularyTest.kt`. Mirrors Phase 7 D2. |
| J2 | Every bullet of `ARCHITECTURE.md §5` re-verified against what was actually built. | PASS (B2) — re-verified 2026-09-22: (1) `design-system/`, `product/`, `ux/` confirmed untouched since the initial commit (`git log -- design-system/ product/ ux/` shows only `c3a2ed5 Initial Mentora project setup`) aside from this batch's own two pre-approved D-4/D-5 one-line corrections; (2)/(3) "did not write production code" / AI-provider-resolved-not-implemented bullets describe the scope of the architecture-authoring phase itself at time of writing (historical), not an ongoing claim about the repo — not a discrepancy; (4) `product/SCREEN_INVENTORY.md` still lists exactly 29 `### N.` screen headings; (5) no cloud-infra config found repo-wide (`*.tf`, `terraform*`, `*cloudformation*`, `k8s*`, `kubernetes*` — zero matches). All bullets still hold. |
| J3 | Stale docs corrected in `execution/`: D-1 (sequencing line), D-2 (Android test count), D-3 (decision count off-by-one). | FIXED (B2) — `execution/MASTER_IMPLEMENTATION_PLAN.md:79` corrected to "Phases 1-7 are COMPLETE; Phase 8 is IN_PROGRESS"; `mobile/androidApp/README.md:70` corrected to 246/246 (confirmed live via `./gradlew :androidApp:testDebugUnitTest`, 0 failures/errors); `execution/PHASE_HANDOFF.md:419` corrected "D146-D161"/"Sixteen" to "D146-D162"/"Seventeen" and D162 added to the enumeration (confirmed last decision in `DECISIONS_LOG.md` is D162). Commit: "Phase 8 B2: fix stale doc facts (J3)". |
| J4 | D-4 (`SCREEN_INVENTORY.md` stale "v1.2" reference, a locked doc) — flagged to user, one-line factual correction applied since it is non-design-affecting. | NOT STARTED |
| J5 | `CURRENT_STATUS.md`, `DECISIONS_LOG.md`, `PHASE_HANDOFF.md` updated with a complete Phase 8 record in the established 10-subsection format. | NOT STARTED |
| J6 | Clean git state at every checkpoint, one commit per task, pushed after each. | NOT STARTED |
| J7 | Every Phase 8 change traces to a row in this matrix — no new features, no redesign, no invented scope. | NOT STARTED |
| J8 | Closing statement of what the project deliberately did not do (final phase — no gap left looking like an oversight). | NOT STARTED |

---

## Known discrepancies carried into Phase 8 (from repository recovery, 2026-09-22)

| # | Discrepancy | Disposition |
|---|---|---|
| D-1 | `MASTER_IMPLEMENTATION_PLAN.md:79` stale sequencing line ("Phase 1 IN_PROGRESS; Phases 2-8 NOT_STARTED"). | Fix under J3 (writable `execution/` doc). |
| D-2 | `mobile/androidApp/README.md:70` documents 241/241 unit tests; actual is 246/246. | Fix under J3. |
| D-3 | `PHASE_HANDOFF.md:419` says "D146-D161"; D162 exists (the entry itself). | Fix under J3. |
| D-4 | `product/SCREEN_INVENTORY.md:3` says COMPONENTS.md "is now v1.2"; actual is v1.3.2. Locked doc. | RESOLVED (B2) — flagged under J4, one-line factual correction applied 2026-09-22 (confirmed `design-system/DESIGN_SYSTEM.md:1` = "v1.3.2"). Per-screen "(added DS v1.2)" historical markers left untouched — they correctly record when those 7 components were added, not the current doc version. Commit: "Phase 8 B2: fix stale COMPONENTS.md version reference (D-4)". |
| D-5 | `backend/build.gradle.kts` declares unused Testcontainers deps; `TESTING_STRATEGY.md §1` describes Testcontainers-based integration tests that don't exist (real tests hardcode local Mongo). | RESOLVED (B2) — confirmed zero `testcontainers` imports/usage anywhere in `backend/src`; removed the 3 dead `org.testcontainers:*` test dependency declarations from `backend/build.gradle.kts`; verified `./gradlew compileTestKotlin` still succeeds. Corrected `TESTING_STRATEGY.md §1`'s Backend Integration row (flagged, non-design-affecting factual correction) to describe the actual mechanism: a real local MongoDB instance at `mongodb://localhost:27017/?replicaSet=rs0` (confirmed in `HealthCheckIntegrationTest.kt` and siblings). Contributes to C8 (full C8 grading, including a fresh backend test run, deferred to B6). Commit: "Phase 8 B2: remove dead Testcontainers dep + correct locked-doc claim (D-5)". |
| D-6 | `TESTING_STRATEGY.md §5` mandates Web Vitest+RTL tier — never built. | Resolved under H2/C3 (bounded tier). |
| D-7 | M14's "lint/CI check for missing translation keys per platform" never built for Web/Android. | Resolved under B2/B3. |
| D-8 | M14/M16/M17 name iOS as in-scope. | Resolved under H1. |
| D-9 | No a11y/performance tooling exists. | Resolved under H4. |

---

**Do not silently skip any row.** Every row above must be revisited before Phase 8 closes.
