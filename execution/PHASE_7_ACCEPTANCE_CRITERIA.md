# Phase 7 — Full Integration — Acceptance Criteria

**Status:** **CLOSED — T13 final acceptance audit complete, 2026-09-21.** Every row below is PASS,
PASS-with-a-disclosed-exception, or an explicitly recorded accepted gap — nothing was upgraded to PASS
to close the phase. See `execution/PHASE_HANDOFF.md`'s Phase 7 entry for the full 10-subsection account.
**Phase 8 must not begin without the user's own separate, explicit approval.**

Originally authored at Phase 7 kickoff (2026-09-20), before any Phase 7 code change. Derived from
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
| A1 | An enrollment made via demo checkout on one client (Web) is immediately visible in "My Learning" on another client (Android) for the same account, with no client-local caching drift | **PASS** — proven twice: `tools/cross-client-check/check.js` (T8, D156) drives real checkout+enrollment-list calls over both the cookie/CSRF transport (Web) and the Bearer transport (Android/KMP) against one account, response deep-equal; live spot-check T10 (Web, D158) -> T11 (Android, D159) confirms the same account's enrollment for the same real course, no reinstall/cache-clear |
| A2 | Lesson-completion / playback-progress recorded on one client is reflected in progress shown on another client for the same account | **PASS** — T8 A2/E1 group writes a position via one transport and a completion via the other, then reads progress identically via both (D156); T10->T11 live spot-check shows the same 12/12, 100% on both real clients (D158/D159) |
| A3 | A quiz attempt/result recorded on one client is visible identically on another client (same score, same pass/fail, same attempt history) | **PASS** — T8 submits a real attempt via Web transport, reads `attempts/latest` via the SDK transport, deep-equal (score 100, passed true) (D156); T10->T11 live spot-check shows identical passed/100% on both real clients (D158/D159) |
| A4 | A certificate issued (via completion) on one client appears identically in Certificates on another client | **PASS** — T8 genuinely exercises this (all lessons completed before the quiz attempt so `checkAndIssueIfComplete`'s precondition is really met, not left NOT-EXERCISED), certificate list deep-equal across transports (D156); T10->T11 live spot-check shows the exact same certificate, same course, same instructor, same issue date "Sep 6, 2026" on both real clients (D158/D159) |
| A5 | AI Tutor conversation history persists and is visible identically across clients for the same account (already implemented backend-side per Phase 1-6; verify no client-side drift) | **PASS** — T8 posts a stub-mode message via Web transport, reads the conversation via the SDK transport, deep-equal (D156) |
| A6 | No client independently re-derives or re-computes a value the backend already owns (progress %, quiz pass/fail, enrollment status) — every client displays the backend's own computed value | **PASS, with one disclosed exception recorded, not silently accepted.** T9 (D157) confirmed by source inspection: every `completionPercent`/`quizPassed`/`courseCompletedAt` reference on both clients is a straight pass-through from the server's `ProgressResponse` — never recomputed. The one real client-side derivation is `isEnrolled`, which is not a backend-owned value at all (`CourseSummary.kt`'s own kdoc: "never implemented anywhere in the backend") — Web and Android derive it *differently* (Web checks a single `?limit=100` enrollment page; Android pages fully). **F1 decision (D157): recorded as a disclosed, accepted divergence**, unreachable with this project's actual demo data (3 seed courses total), not fixed in Phase 7 |

## B. End-to-End Portfolio-Priority Flow (per `IMPLEMENTATION_ROADMAP.md § M16`)

| # | Criterion | Status |
|---|---|---|
| B1 | Full flow — Discovery → Course Details → Demo Checkout → Purchase Success → Course Player → Progress → Quiz → AI Tutor → Certificate — walked start-to-finish on **Website**, against the local backend, in English | **PASS** — T10 (D158), en-Light, all 9 steps PASS, live Chrome browser automation, screenshotted |
| B2 | Same full flow walked start-to-finish on Website in **Arabic** (RTL) | **PASS** — T10 (D158), ar-Dark, all 9 steps PASS, RTL mirroring concretely verified (nav/sidebar/controls/quiz-bar/AI-bubbles/certificate-grid all correctly mirrored, embedded English content correctly stays LTR) |
| B3 | Same full flow walked start-to-finish on **Android** (emulator, local backend via `10.0.2.2`), in English | **PASS** — T11 (D159), en-Light, real debug APK on `Chatting_Pixel_8_API_36`, all 9 steps PASS |
| B4 | Same full flow walked start-to-finish on Android in **Arabic** (RTL) | **PASS** — T11 (D159), ar-Dark, all 9 steps PASS, RTL mirroring concretely verified, matches T10's own Website RTL description |
| B5 | The demo account/course state used for these walks is genuinely seeded data (`seedDemoData`), not hand-crafted per-run state that would mask a real gap | **PASS** — `seedDemoData`'s real console output captured verbatim immediately before T10's walk (D158); T11 reused the same seeded `student1@mentora.dev` account (D159) |

## C. Authentication / Session Behavior Consistency

| # | Criterion | Status |
|---|---|---|
| C1 | Login/logout/token-refresh behave identically in effect (same role gating, same session lifetime policy) across Website and Android, even though each implements it independently (Web: its own session handling; Android: `MentoraSdk` + Keystore) | **PASS** — T8 C1 (D156): one registration, both transports authenticate as the same student principal/role; T10/T11 both walked full login/logout flows live with identical effective behavior (D158/D159) |
| C2 | A logged-out/guest user hits the same auth gates in the same product-defined places on both clients (per `USER_ROLES.md`/navigation gating already locked in Phases 2/4) | **PASS** — T10 (D158): guest hitting a gated checkout URL redirects to `/login?redirect=...` and lands back exactly there post-login; T11 (D159): guest Course Details correctly shows the login-gated "Login to Enroll"/localized equivalent |
| C3 | Role-gated surfaces (Instructor/Admin, Web-only by product decision) correctly remain absent/inaccessible on Android — verify this is still true, not a regression | **PASS** — T9 (D157): `grep -rniE "instructor|admin"` across Android's navigation package and a filename search both returned empty. No drift |
| C4 | The T7-discovered, disclosed-but-unfixed Android bug (Course Details "Login to Enroll" CTA doesn't immediately refresh to "Enroll" after login via the auth-gate flow, D144) — decide: fix in Phase 7 (it's exactly the kind of cross-cutting auth/session consistency bug this phase targets) or explicitly re-defer with a recorded reason | **PASS — fixed and live-verified.** T6 (D154) fixed both `CourseDetailsViewModel` and `LearningPathDetailsViewModel` via `onAuthenticationChanged`. T11 (D159) reproduced the exact guest -> login -> back scenario live on a real emulator for BOTH Course Details and Learning Path Details' Follow affordance — CTA correctly updates, no stale state, as a real before/after pair |

## D. Enrollment / Demo Checkout Consistency

| # | Criterion | Status |
|---|---|---|
| D1 | Demo checkout completion is idempotent and produces identical `enrollments`/`demoPurchases` records regardless of which client initiated it (already backend-guaranteed per Phase 1 M6; verify no client sends divergent request shapes) | **PASS** — T8 D1 (D156): completes checkout via Web transport, re-completes via SDK transport, enrollment count unchanged both times, confirmed on 2 consecutive fresh-account runs |
| D2 | No real payment code path exists anywhere in the codebase (re-confirm the Phase 1/Phase 8-anticipated check; a scan, not a redesign) | **PASS** — T9 (D157): word-boundary-anchored scan (`\b(stripe\|paypal\|braintree\|...)\b`) across `backend/src web/src mobile/shared/src mobile/androidApp/src` returned exactly 2 benign hits (a test asserting the absence of payment vocabulary, and the unrelated English word "stripe" in a texture description) |

## E. Learning / Progress / Quiz Flow Consistency

| # | Criterion | Status |
|---|---|---|
| E1 | Course Player progress (lesson-complete, playback-position heartbeat) behaves consistently in effect across Website and Android (different playback tech — HTML5/exoplayer — same backend contract and resulting state) | **PASS** — T8 A2/E1 (D156) + live T10->T11 spot-check both showing 12/12, 100% (D158/D159) |
| E2 | Quiz authoring→attempt→grading flow (backend `isCorrect`-stripped fetch, grading, attempts) produces the same visible result on both clients for the same attempt | **PASS.** T8 E2/A3 (D156) proves the actual DATA is correct and identical cross-client: `isCorrect` is stripped pre-attempt on both transports, and the attempt breakdown (score/pass-fail) is identical. **One disclosed, single-client cosmetic finding, not a violation of this criterion:** T10 (D158) found a CSS spacing bug on Web's quiz-results screen making a wrong answer's badge visually read as attached to the wrong option — the underlying score/pass-fail result itself is correct (per T8) and Android's own results screen does not exhibit it (per T11); correctly triaged as out of Phase 7 scope (J5) and left unfixed, disclosed |
| E3 | Learning Path follow/unfollow and derived progress display consistently across clients | **PASS** — T8 E3 (D156): follow via Web transport, path detail (incl. follow state) deep-equal via SDK transport; T11's C4 repro (D159) additionally proves the Follow affordance updates correctly live on a real Android device after login |

## F. AI Tutor Structural Integration (stub/mock only — no real provider, per D146)

| # | Criterion | Status |
|---|---|---|
| F1 | AI Tutor chat flow (quick actions, loading/error/empty states, retry, enrolled-course context) behaves consistently across Website and Android against the stub provider — this is largely already verified in Phase 6 T7 (D144); Phase 7 confirms it still holds true together with everything else touched this phase, not a re-verification from scratch | **PASS** — re-confirmed cheaply, not from scratch, by both T10 and T11 (D158/D159): quick actions render, a real "thinking"/mid-stream state was captured live, in both English and Arabic |
| F2 | No Phase 7 work requires, requests, or assumes a real `AI_PROVIDER_API_KEY` anywhere | **PASS — constraint held throughout, re-confirmed at T9 (D157)**: `.env`/`.env.example` blank, zero `secrets.*` references across all 5 workflow files, `AI_PROVIDER_API_KEY` appears in CI YAML only as the literal empty string, full Phase 7 commit range re-scanned |

## G. Localization / RTL / Theme Consistency (Design System v1.3.2)

| # | Criterion | Status |
|---|---|---|
| G1 | Every screen exercised in the portfolio-priority flow (§ B) renders correctly in English and Arabic, LTR and RTL, on both Website and Android | **PASS** — T10 (en-Light + ar-Dark, Web) and T11 (en-Light + ar-Dark, Android) both walked every step of § B in both languages with RTL mirroring explicitly, concretely inspected on every screen (D158/D159), using the already-decided reduced matrix (F4) |
| G2 | Light and Dark theme render correctly for every screen exercised in § B, on both clients | **PASS** — same T10/T11 runs used Light for the English pass and Dark for the Arabic pass on both clients, per the F4-decided matrix; no broken/overlapping layout observed anywhere |
| G3 | No hardcoded/untranslated string or literal LTR-only layout assumption is newly introduced by any Phase 7 fix | **PASS, held throughout** — every Phase 7 product-code fix (T6's `onAuthenticationChanged`, T11's `clearAllTabBackStacks`) is ViewModel/navigation logic only, introducing zero new UI strings or layout |
| G4 | Design System v1.3.2 token/component usage remains consistent between Website and Android for any screen touched by a Phase 7 fix (no ad hoc styling introduced to solve a cross-client inconsistency) | **PASS, held throughout** — same reasoning as G3: no Phase 7 fix touched styling/components, only ViewModel/navigation behavior |

## I. CI / Demo Environment (`IMPLEMENTATION_ROADMAP.md § M16`)

| # | Criterion | Status |
|---|---|---|
| I1 | `backend-ci.yml` — build + lint + test on every PR/push to `main`, no deploy step | **DONE** — T1 (D149), green real CI run, 127/127 backend tests, `docker run`-based MongoDB replica set. D-LINT: no backend linter, by explicit decision |
| I2 | `web-ci.yml` — build + lint + test on every PR/push to `main`, no deploy step | **DONE** — T2/T3/T7 (D150/D151/D155), two jobs green, real production build + Playwright chromium run against a real local stack. Found and fixed a genuinely serious pre-existing infra bug along the way (D151, the fat jar was never runnable) |
| I3 | `android-ci.yml` — build + lint + test (JVM unit tests; instrumented tests require an emulator and may be scoped out of CI per a documented, explicit decision if runner constraints make them impractical) on every PR/push to `main`, no deploy step | **DONE** — T4 (D152), green on first real run. D-EMU: instrumented tests stay local-only/manual (T11 exercises them for real, on a real emulator, per D159) |
| I4 | `ios-ci.yml` continues to pass, untouched by Phase 7 | **DONE, re-confirmed at T9 (D157)** — zero Phase 7 commits touch `ios-ci.yml`; its last 3 runs (Phase 5, pre-freeze) all `completed/success` |
| I0 | `tokens-ci.yml` — the fifth M16-mandated workflow (design-token drift gate); not originally listed here, added per D147's resolution of System Design finding F2 (build it — traces to M16's literal "all five workflows" text, closes a real gap in Web's token-drift coverage) | **DONE** — T5 (D153), green, empirically proven non-vacuous (a deliberate drift was introduced and caught before being reverted) |
| I5 | `start-mentora.ps1`/`stop-mentora.ps1` and `backend/README.md`/`web/README.md`/`mobile/androidApp/README.md` local-run instructions remain accurate after any Phase 7 change | **PASS** — T12 (D161): both scripts re-read end to end and confirmed accurate; all 3 READMEs given a CI section verified command-for-command against the real workflow files |
| I6 | No cloud deployment, hosting account, or app-store distribution step is introduced anywhere (ADR-012) | **PASS, constraint held throughout** — re-confirmed at T9 (D157) and again at T12 (D161) across all 5 workflows and all edited READMEs |

## J. Quality

| # | Criterion | Status |
|---|---|---|
| J1 | Full existing test suites (backend, `:shared`, `:androidApp` JVM, Playwright if present) continue to pass — no regression introduced by any Phase 7 fix | **PASS, fresh re-run at T13 (`--rerun-tasks`, not cached), vs. T0 baseline:** backend 127/127 (matches), `:shared` 249/249 (matches), `:androidApp` JVM 246/246 (exceeds 241 — 5 new C4-fix tests), `:androidApp:lintDebug` 0 errors (matches), Playwright chromium 19/19 under `CI=true` (matches, one self-healing flake as T0 itself already documented). Zero regressions |
| J2 | New CI workflows (§ I1-I3) themselves pass green on this repository's actual current state before being declared done | **PASS** — confirmed at T13: all 5 workflows' most recent real run is green and covers the current state of every file their own path filters watch (no relevant file has changed since each one's last green run) |
| J3 | Documentation: `CURRENT_STATUS.md`, `DECISIONS_LOG.md`, `PHASE_HANDOFF.md`, relevant READMEs updated to reflect Phase 7's real end state | **PASS** — `CURRENT_STATUS.md` updated after every task (T0-T13); 16 `DECISIONS_LOG.md` entries (D146-D161) covering every decision actually taken; `PHASE_HANDOFF.md` Phase 7 entry added in the established 10-subsection format; all 3 READMEs given CI sections at T12 |
| J4 | Clean Git state at every checkpoint, one commit per task, pushed after each commit (this project's established workflow) | **PASS** — 27 commits, `9b2a1c9`..`eb137d7` plus this T13 commit, every task's own commit(s) pushed immediately after; `git status --porcelain` clean at every checkpoint throughout this phase |
| J5 | Every fix Phase 7 makes is traceable to a genuine cross-client inconsistency or a locked-but-undone M16 deliverable — not new feature work, not a redesign, not scope invented for its own sake | **PASS** — every fix cites its trace explicitly: T6's C4 fix (a disclosed cross-client auth-consistency bug), T0's 2 E2E fixes (I2/B1), T1's gradlew fix / T3's shadowJar fix (both block a locked M16 CI deliverable), T11's cross-account tab-leak fix (a data-integrity defect serious enough to fix despite not being strictly "cross-client", explicitly reasoned in D159). Two candidate fixes were correctly NOT made because they didn't trace to anything in scope: the quiz-badge CSS bug (D158) and the `AppDialog` regression (D160) |

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

## Closing state confirmed at T13 (2026-09-21)

- `main` @ `eb137d7` + this T13 commit, clean, pushed to `origin/main`. 27 commits span Phase 7
  (`9b2a1c9`..`eb137d7`).
- `.github/workflows/` now contains all 5 M16-mandated workflows, all green: `backend-ci.yml`,
  `web-ci.yml`, `android-ci.yml`, `tokens-ci.yml`, `ios-ci.yml` (untouched, pre-existing).
- The C4 bug (§ baseline, above) is FIXED and live-verified on a real device (T6/T11, D154/D159) — no
  longer a carried-forward gap.
- Fresh J1 suite counts: see § J1 below and `PHASE_HANDOFF.md`'s Phase 7 entry § 5.
- Net new disclosed items closing the phase: F1 (`isEnrolled` divergence), the quiz-badge CSS bug, and
  the `AppDialog` scrim regression — all three investigated to ground truth, none silently dropped, none
  blocking (see § A6, § E2, § 6 of the handoff entry).

## Reconciliation note vs. `MASTER_IMPLEMENTATION_PLAN.md`

Its Phase 7 line ("Cross-client verification... across Web/Android/iOS", "all four networking targets")
is **narrowed, not replaced**, by the user's own explicit, later instruction to exclude iOS from Phase 7
(consistent with the already-recorded Phase 5 freeze) — recorded in § H1, not silently overridden. The
CI-workflow gap (§ H2) is the one place this checklist adds concrete scope beyond a strict reading of the
Phase 7 kickoff message's focus-area list — justified because it was already locked as an M16 deliverable
this project simply hadn't gotten to yet, not because this session decided it should exist.
