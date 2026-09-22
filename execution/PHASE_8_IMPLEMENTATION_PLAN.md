# Phase 8 — QA, Polish & Portfolio Demo — Implementation Plan

Authored at kickoff (2026-09-22), before any Phase 8 code change. Batches map to
`PHASE_8_ACCEPTANCE_CRITERIA.md` rows — every change must trace to a row (J7). Host legend:
**S** = static/local analysis, **L** = live local stack (Mongo+backend+Website), **E** = Android emulator,
**B** = real browser, **CI** = GitHub Actions.

## B0 — Kickoff & baseline (S+L+E+CI)
Write this plan + the acceptance criteria doc. Re-run every suite fresh to establish the Phase 8 baseline
(backend, `:shared`, `:androidApp` unit, lint, Playwright, instrumented) before any change, so a later
failure is never misattributed to a pre-existing condition.

## B1 — Known-issue reproduction & triage (S, then E/B) — parallel with B2
Reproduce each §A item against ground truth. AppDialog needs the emulator (time-boxed per H6); quiz-results
spacing and the quiz-recompletion routing quirk need a live browser; F1/isEnrolled and Phase 6 F6/F8 are
static/stub-verifiable. Output: triaged punch-list, each item FIX / ACCEPT / NO LONGER APPLIES.

## B2 — Static audit sweep (S) — parallel with B1
Design-token drift (D1/D2), J1 payment-path scan, J2 `ARCHITECTURE.md §5` cross-check, doc-staleness
fixes (D-1/D-2/D-3), D-5 Testcontainers reconciliation, D-4 flagged for the locked-doc correction,
Web/Android translation-key parity audit (pre-B3 tooling).

## B3 — Localization tooling + M14 audit (S→CI, then B/E for the RTL walk)
Build per-platform translation-key parity checks, prove non-vacuous, wire into `web-ci.yml`/`android-ci.yml`.
Then the 29-screen EN/AR coverage audit and RTL QA pass.

## B4 — Website QA pass (B+L)
Screen-by-screen walk of all Web surfaces, EN-Light + AR-Dark minimum, higher-risk screens spot-checked in
the other combinations (not exhaustive 4-way, per Phase 7 precedent). Includes A2 fix verification.

## B5 — Android QA pass (E) — may serialize against B4 for attention
Equivalent walk on Android; full 106-test instrumented run; A1 AppDialog verdict; G3/G4 regression re-checks.

## B6 — Backend/KMP QA (S+L+CI) — parallel with B4/B5
Error-path/edge-case gap analysis, bounded Web test-tier build (H2/C3), D-5 reconciliation, re-run
`tools/cross-client-check/check.js` (G1).

## B7 — Cross-client parity audit (L+B+E) — after B4/B5/B6
Re-run parity harness after client-side fixes; Web/Android side-by-side on portfolio flow (G2); F1 verdict
(A3); §D4 visual-parity audit.

## B8 — Accessibility pass (B+E) — after B4/B5
Full §E matrix, manual per H4.

## B9 — Visual polish + performance sanity (B+E) — after B8
Fix punch-list items from B4/B5/B8 (§D3/D4), then §F measurements.

## B10 — Independent review checkpoint (S) — mandatory gate before final regression
Opus review of everything B1-B9 changed. `codex-reviewer` second opinion only if the change set meets the
standing high-risk criteria (large multi-module refactor, or a serious/uncertain Opus finding) — never
speculatively.

## B11 — Final regression + CI green twice (CI+L+E+B)
Full suite re-run; all in-scope workflows green twice consecutively (C5); deliberate-regression sanity
check on a scratch branch, reverted (C6).

## B12 — Portfolio & docs readiness + final handoff (S+L)
Clean-checkout demo dry run (I1-I4); repo hygiene sweep (I6); doc accuracy pass (J3/J4); final acceptance
audit of the whole matrix; `PHASE_HANDOFF.md` Phase 8 entry; closing "what this project deliberately did
not do" statement (J8, since this is the final phase).

**Parallelism:** B1 ∥ B2. B3's tooling half ∥ B1/B2. B4 ∥ B6 and B5 ∥ B6. B7-B12 strictly sequential.
**Device/environment gating:** B1 (partly), B3 (walk half), B4, B5, B7, B8, B9, B11, B12 need real
runtime. B2, B6 (mostly), B10 are static.

## Kickoff decisions (resolved with the user, 2026-09-22)
- H1: iOS excluded from grading; recorded ACCEPTED LIMITATION wherever named.
- H2: bounded Web Vitest+RTL tier for highest-risk components only.
- H4: manual accessibility/performance audit, no new tooling.
- H6: AppDialog — one time-boxed investigation session, fallback to ACCEPTED LIMITATION.
- D-4/D-5: trivial one-line factual corrections to locked docs are in scope (not design changes);
  flagged explicitly in commit messages.

## Risk notes
- Scope creep disguised as polish is the primary risk — every change must cite a matrix row (J7).
- A1 (AppDialog) and H2 (Web test tier) are the two genuine time sinks; both are explicitly bounded above.
- Nothing in this phase is architecturally irreversible; the only non-trivially-reversible actions are
  locked-doc edits (D-4/D-5), which stay minimal and disclosed.
