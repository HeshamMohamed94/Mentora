# Phase 7 — Full Integration — Acceptance Criteria

**Status:** Draft, authored at Phase 7 kickoff (2026-09-20), before any Phase 7 code change. Derived from
already-locked authoritative sources, not invented from scratch — see "Authority sources" below. Reconciles
one scope point against `MASTER_IMPLEMENTATION_PLAN.md`'s original Phase 7 line (§ H1).

## Authority sources (read in full before writing this checklist)

1. `execution/MASTER_IMPLEMENTATION_PLAN.md` § "PHASE 7 — Full Integration" — "Cross-client verification:
   same backend, same data, consistent behavior across Web/Android/iOS" + "M16 (completion) — full local
   stack verification across all four networking targets ..., full portfolio-priority flow walked on
   every client in both languages."
2. `product/PRODUCT_SPEC.md § 10` — "Relationship Between Website, Android, iOS, Backend, and Shared
   Data": one shared backend/data model, one shared identity, one shared design system; "what must never
   diverge: what a feature *means* and what data it produces."
3. `architecture/IMPLEMENTATION_ROADMAP.md § M16` ("Local Demo Readiness") — the full local stack (Mongo +
   backend + media storage + clients) verified to build/run/demo end-to-end locally; the
   **portfolio-priority flow**: Discovery → Course Details → Demo Checkout → Purchase Success → Course
   Player → Progress → Quiz → AI Tutor → Certificate, walked on each client, in both languages, against
   the same local backend; "all five GitHub Actions workflows (build/lint/test only, no deploy)" as a
   deliverable.
4. `architecture/adr/ADR-012-local-demo-scope.md` — locked local-only MVP scope; no cloud deployment, no
   hosting account, no app-store distribution step is in scope for this or any phase.
5. `design-system/DESIGN_SYSTEM.md` (current version **v1.3.2**), `LOCALIZATION.md`, `ACCESSIBILITY.md` —
   the one shared design system all clients must render from; RTL/localization and font-scaling rules.
6. `execution/CURRENT_STATUS.md` (Phase 1-6 sections) and `execution/PHASE_HANDOFF.md` (Phase 1-6 entries)
   — what was actually built, live-verified, and disclosed as a known/accepted gap in each prior phase;
   this checklist does not re-litigate any of those, only checks they still hold true together.
7. `execution/DECISIONS_LOG.md` D146 — the user's explicit Phase 6 closure decision (live Anthropic
   verification deferred; AI Tutor Phase 7 integration must use existing mock/fake/stub infrastructure,
   never a real provider call) and the explicit instruction that authorized starting Phase 7.
8. The Phase 7 kickoff message itself (2026-09-20) — the authoritative statement of what is IN scope
   (Backend, Website, Android, KMP shared core; end-to-end flows; cross-platform consistency; auth/session;
   enrollment/demo checkout; learning/progress/quiz; AI Tutor structural integration; EN/AR, LTR/RTL,
   Light/Dark; Design System v1.3.2; demo readiness) and explicitly OUT of scope (iOS/Phase 5 resumption;
   any real Anthropic credential/call; Phase 8).

**Do not re-derive scope from memory or re-litigate any of the above — this checklist assumes them as given.**

---

## H. Scope Reconciliation (read this section first)

| # | Point | Resolution |
|---|---|---|
| H1 | `MASTER_IMPLEMENTATION_PLAN.md`'s original Phase 7 line names **Web/Android/iOS** and M16's roadmap text names **four** local-networking targets (browser, Android emulator, Android/iOS physical device, iOS simulator) as in scope. | **Reconciled, not silently overridden:** per the Phase 5 freeze (`CURRENT_STATUS.md` "PHASE 5 FREEZE" section) and this Phase 7 kickoff's own explicit instruction ("Do NOT resume iOS Phase 5"), **iOS is excluded from Phase 7's cross-client verification scope.** Phase 7 verifies cross-client consistency across exactly the primary supported/demo targets already established for Phases 6-8: **Website, Android, Backend, KMP shared core.** iOS remains frozen exactly as Phase 5 left it (T1-T11 done, T12-T23 not started) and is not touched, re-verified, or counted against in this phase. |
| H2 | M16 lists "all five GitHub Actions workflows (build/lint/test only, no deploy)" as a deliverable, and M15/Phase 8 assumes those workflows already exist by the time Phase 8 hardens them ("CI green twice in a row"). | **Recovery finding:** only `ios-ci.yml` exists today (confirmed at Phase 7 kickoff — `.github/workflows/` contains exactly one file). No `backend-ci.yml`, `web-ci.yml`, or `android-ci.yml` exists. Per the locked M16 deliverable, **standing up build/lint/test-only CI for backend, Website, and Android is genuinely in scope for Phase 7** (not new scope invented for this phase — it was already mandated by the already-locked roadmap and simply not yet done). This is real, valuable, well-scoped work, not gold-plating. See § I below. |
| H3 | Real Anthropic provider verification (Phase 6's T8) remains deferred by explicit user decision (D146). | AI Tutor's role in Phase 7's end-to-end flow verification uses the existing stub/fake/mock provider infrastructure exclusively — never a real key, never requested, never simulated as real. Any Phase 7 criterion touching AI Tutor is scoped to **structural integration and stub-mode behavior only**, exactly as the kickoff message specifies. |

---

## A. Cross-Client Data Consistency (backend as single source of truth)

| # | Criterion | Status |
|---|---|---|
| A1 | An enrollment made via demo checkout on one client (Web) is immediately visible in "My Learning" on another client (Android) for the same account, with no client-local caching drift | NOT STARTED |
| A2 | Lesson-completion / playback-progress recorded on one client is reflected in progress shown on another client for the same account | NOT STARTED |
| A3 | A quiz attempt/result recorded on one client is visible identically on another client (same score, same pass/fail, same attempt history) | NOT STARTED |
| A4 | A certificate issued (via completion) on one client appears identically in Certificates on another client | NOT STARTED |
| A5 | AI Tutor conversation history persists and is visible identically across clients for the same account (already implemented backend-side per Phase 1-6; verify no client-side drift) | NOT STARTED |
| A6 | No client independently re-derives or re-computes a value the backend already owns (progress %, quiz pass/fail, enrollment status) — every client displays the backend's own computed value | PARTIAL — true by construction per `MentoraSdk`/Web API-client architecture (Phases 2-4); verify no exception exists |

## B. End-to-End Portfolio-Priority Flow (per `IMPLEMENTATION_ROADMAP.md § M16`)

| # | Criterion | Status |
|---|---|---|
| B1 | Full flow — Discovery → Course Details → Demo Checkout → Purchase Success → Course Player → Progress → Quiz → AI Tutor → Certificate — walked start-to-finish on **Website**, against the local backend, in English | NOT STARTED |
| B2 | Same full flow walked start-to-finish on Website in **Arabic** (RTL) | NOT STARTED |
| B3 | Same full flow walked start-to-finish on **Android** (emulator, local backend via `10.0.2.2`), in English | NOT STARTED |
| B4 | Same full flow walked start-to-finish on Android in **Arabic** (RTL) | NOT STARTED |
| B5 | The demo account/course state used for these walks is genuinely seeded data (`seedDemoData`), not hand-crafted per-run state that would mask a real gap | NOT STARTED |

## C. Authentication / Session Behavior Consistency

| # | Criterion | Status |
|---|---|---|
| C1 | Login/logout/token-refresh behave identically in effect (same role gating, same session lifetime policy) across Website and Android, even though each implements it independently (Web: its own session handling; Android: `MentoraSdk` + Keystore) | NOT STARTED |
| C2 | A logged-out/guest user hits the same auth gates in the same product-defined places on both clients (per `USER_ROLES.md`/navigation gating already locked in Phases 2/4) | NOT STARTED |
| C3 | Role-gated surfaces (Instructor/Admin, Web-only by product decision) correctly remain absent/inaccessible on Android — verify this is still true, not a regression | PARTIAL — true by construction (Android never implemented these screens); confirm no drift |
| C4 | The T7-discovered, disclosed-but-unfixed Android bug (Course Details "Login to Enroll" CTA doesn't immediately refresh to "Enroll" after login via the auth-gate flow, D144) — decide: fix in Phase 7 (it's exactly the kind of cross-cutting auth/session consistency bug this phase targets) or explicitly re-defer with a recorded reason | NOT STARTED — recommend fixing in Phase 7, see System Design |

## D. Enrollment / Demo Checkout Consistency

| # | Criterion | Status |
|---|---|---|
| D1 | Demo checkout completion is idempotent and produces identical `enrollments`/`demoPurchases` records regardless of which client initiated it (already backend-guaranteed per Phase 1 M6; verify no client sends divergent request shapes) | NOT STARTED |
| D2 | No real payment code path exists anywhere in the codebase (re-confirm the Phase 1/Phase 8-anticipated check; a scan, not a redesign) | NOT STARTED |

## E. Learning / Progress / Quiz Flow Consistency

| # | Criterion | Status |
|---|---|---|
| E1 | Course Player progress (lesson-complete, playback-position heartbeat) behaves consistently in effect across Website and Android (different playback tech — HTML5/exoplayer — same backend contract and resulting state) | NOT STARTED |
| E2 | Quiz authoring→attempt→grading flow (backend `isCorrect`-stripped fetch, grading, attempts) produces the same visible result on both clients for the same attempt | NOT STARTED |
| E3 | Learning Path follow/unfollow and derived progress display consistently across clients | NOT STARTED |

## F. AI Tutor Structural Integration (stub/mock only — no real provider, per D146)

| # | Criterion | Status |
|---|---|---|
| F1 | AI Tutor chat flow (quick actions, loading/error/empty states, retry, enrolled-course context) behaves consistently across Website and Android against the stub provider — this is largely already verified in Phase 6 T7 (D144); Phase 7 confirms it still holds true together with everything else touched this phase, not a re-verification from scratch | PARTIAL — inherited PASS from Phase 6 T7; re-confirm only if Phase 7 work touches anything adjacent |
| F2 | No Phase 7 work requires, requests, or assumes a real `AI_PROVIDER_API_KEY` anywhere | ACTIVE CONSTRAINT — per D146 |

## G. Localization / RTL / Theme Consistency (Design System v1.3.2)

| # | Criterion | Status |
|---|---|---|
| G1 | Every screen exercised in the portfolio-priority flow (§ B) renders correctly in English and Arabic, LTR and RTL, on both Website and Android | NOT STARTED |
| G2 | Light and Dark theme render correctly for every screen exercised in § B, on both clients | NOT STARTED |
| G3 | No hardcoded/untranslated string or literal LTR-only layout assumption is newly introduced by any Phase 7 fix | tracked procedurally, not a one-time item |
| G4 | Design System v1.3.2 token/component usage remains consistent between Website and Android for any screen touched by a Phase 7 fix (no ad hoc styling introduced to solve a cross-client inconsistency) | tracked procedurally |

## I. CI / Demo Environment (`IMPLEMENTATION_ROADMAP.md § M16`)

| # | Criterion | Status |
|---|---|---|
| I1 | `backend-ci.yml` — build + lint + test on every PR/push to `main`, no deploy step | NOT STARTED (real gap, see § H2) |
| I2 | `web-ci.yml` — build + lint + test on every PR/push to `main`, no deploy step | NOT STARTED (real gap, see § H2) |
| I3 | `android-ci.yml` — build + lint + test (JVM unit tests; instrumented tests require an emulator and may be scoped out of CI per a documented, explicit decision if runner constraints make them impractical) on every PR/push to `main`, no deploy step | NOT STARTED (real gap, see § H2) |
| I4 | `ios-ci.yml` continues to pass, untouched by Phase 7 | DONE (pre-existing, Phase 5) — verify no accidental regression |
| I0 | `tokens-ci.yml` — the fifth M16-mandated workflow (design-token drift gate); not originally listed here, added per D147's resolution of System Design finding F2 (build it — traces to M16's literal "all five workflows" text, closes a real gap in Web's token-drift coverage) | NOT STARTED (see `PHASE_7_SYSTEM_DESIGN.md` § 6, plan task T5) |
| I5 | `start-mentora.ps1`/`stop-mentora.ps1` and `backend/README.md`/`web/README.md`/`mobile/androidApp/README.md` local-run instructions remain accurate after any Phase 7 change | tracked procedurally |
| I6 | No cloud deployment, hosting account, or app-store distribution step is introduced anywhere (ADR-012) | ACTIVE CONSTRAINT |

## J. Quality

| # | Criterion | Status |
|---|---|---|
| J1 | Full existing test suites (backend, `:shared`, `:androidApp` JVM, Playwright if present) continue to pass — no regression introduced by any Phase 7 fix | tracked procedurally; verify via full suite re-run before final acceptance |
| J2 | New CI workflows (§ I1-I3) themselves pass green on this repository's actual current state before being declared done | tracked procedurally |
| J3 | Documentation: `CURRENT_STATUS.md`, `DECISIONS_LOG.md`, `PHASE_HANDOFF.md`, relevant READMEs updated to reflect Phase 7's real end state | NOT STARTED |
| J4 | Clean Git state at every checkpoint, one commit per task, pushed after each commit (this project's established workflow) | tracked procedurally |
| J5 | Every fix Phase 7 makes is traceable to a genuine cross-client inconsistency or a locked-but-undone M16 deliverable — not new feature work, not a redesign, not scope invented for its own sake | tracked procedurally |

---

## Baseline confirmed at kickoff (2026-09-20)

- `main` @ `60e71b7`, clean, in sync with `origin/main`.
- Full backend suite: **127/127 passing** (per the Phase 6 T9 audit, same commit lineage).
- `.github/workflows/` contains exactly one file: `ios-ci.yml`. No backend/web/android CI exists yet —
  a real, disclosed gap this phase addresses (§ H2, § I).
- Phase 6 (AI Tutor): implementation COMPLETE; live provider verification DEFERRED BY USER DECISION
  (D146) — not a Phase 7 dependency or blocker.
- Phase 5 (iOS): DEFERRED/PARTIALLY IMPLEMENTED, T1-T11 done, explicitly OUT of Phase 7 scope (§ H1).
- One known, disclosed, not-yet-fixed cross-client bug carried into this phase: Android's Course Details
  "Login to Enroll" CTA staleness after login (D144) — candidate Phase 7 fix, see § C4.

## Reconciliation note vs. `MASTER_IMPLEMENTATION_PLAN.md`

Its Phase 7 line ("Cross-client verification... across Web/Android/iOS", "all four networking targets")
is **narrowed, not replaced**, by the user's own explicit, later instruction to exclude iOS from Phase 7
(consistent with the already-recorded Phase 5 freeze) — recorded in § H1, not silently overridden. The
CI-workflow gap (§ H2) is the one place this checklist adds concrete scope beyond a strict reading of the
Phase 7 kickoff message's focus-area list — justified because it was already locked as an M16 deliverable
this project simply hadn't gotten to yet, not because this session decided it should exist.
