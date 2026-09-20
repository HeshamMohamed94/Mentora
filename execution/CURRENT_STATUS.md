# Mentora — Current Implementation Status

**Last updated:** 2026-09-20 — **PHASE 5 (iOS) — DEFERRED / PARTIALLY IMPLEMENTED**, by explicit user
project decision (see "PHASE 5 FREEZE" box immediately below) — NOT a task failure, NOT abandoned, and
NOT to be read as PASS/COMPLETE. iOS is no longer a blocking priority for the remaining Mentora phases
because no Mac/iPhone is available for meaningful interactive validation. **T11 (Component Kit B, 4
slices) is fully DONE and real-CI-green — slice 4's previously-unverified CI run (35482199062) has since
been confirmed `success` (314/314 tests, 0 failures), closing out T11 in full.** T12 onward (T12-T23) are
NOT started. Phase 4 — Android — **COMPLETE**, all 20 tasks done, approved by the user before Phase 5
began. **Phases 6-8 now proceed with Website / Android / Backend / KMP shared core as the primary
supported/demo platforms — see the "PHASE 5 FREEZE" box for the full decision.**

---

## PHASE 5 FREEZE — 2026-09-20 — DEFERRED / PARTIALLY IMPLEMENTED, BY EXPLICIT USER PROJECT DECISION

**Read this box first.** This supersedes the "SESSION CHECKPOINT" box below (kept intact underneath for
its full historical detail — everything in it is still accurate as a record of what happened, it is just
no longer the live resume plan). Two things happened back to back: (1) the prior session stopped mid-slice
to preserve weekly usage (the checkpoint below), and (2) on resume, the user made an explicit **project
decision** to defer all further iOS implementation, not just pause for usage — because this machine has no
Mac/iPhone for meaningful interactive validation, and iOS work should not gate the rest of the project.

- **First action on this resume:** `gh run view 35482199062 --json status,conclusion,url` was re-run per
  the checkpoint's own "EXACT FIRST ACTION ON RESUME" instructions. Result: `"conclusion":"success"`,
  `"status":"completed"`. Test-count evidence pulled from the run log: `Executed 314 tests, with 0
  failures (0 unexpected)` — same count as slice 3 (slice 4 added no new tests; `LoadingState`/
  `EmptyState`/`ErrorState`/`SuccessState`/`MentoraSheet`/`MentoraDialog` are view-only components with no
  dedicated unit-test file, consistent with several earlier slices). **T11 (Component Kit B) is therefore
  fully DONE, real-CI-confirmed, all 4 slices — see the updated T11 task-table row.** No D137 narrative
  write-up beyond this note and the task-table update; no code was touched. See `DECISIONS_LOG.md` D137.
- **Repository state at this freeze:** `main`, latest commit `6bb0d90` (prior session's checkpoint-doc
  commit) before this freeze's own doc commit. Working tree was clean and fully in sync with
  `origin/main` (`git status` empty, `+0/-0` ahead/behind) at the start of this freeze — no uncommitted
  iOS work existed to preserve or checkpoint beyond what commit `adb8876` (T11 slice 4) already captured.
  No local background agents, watchers, or polling loops were running (checked: no matching processes, no
  scheduled cron jobs, no active sub-agents) — the prior checkpoint's `TaskStop` on the CI-watch loop
  already covered this.
- **Project decision recorded:** iOS completion and manual iOS validation are **explicitly deferred** and
  are **NOT blockers** for continuing Mentora's remaining portfolio/demo phases. For **Phases 6-8**, the
  primary supported/demo platforms are **Website, Android, Backend, and the KMP shared core**. Existing
  iOS code must remain build-compatible where reasonably possible (i.e. don't gratuitously break
  `mobile/iosApp/`), but new Phase 6-8 work must **not** be blocked waiting on iOS-specific UI completion
  or manual iOS validation. Any shared/KMP (`mobile/shared/`) changes made in Phases 6-8 should still
  avoid unnecessarily breaking the existing iOS integration (i.e. don't remove/rename `expect`/`actual`
  surface the iOS side depends on without checking), but iOS-side follow-through on such a change is not
  required to land the Phase 6-8 work itself.
- **PHASE 5 status is now `DEFERRED / PARTIALLY IMPLEMENTED`** — explicitly NOT `PASS`, NOT `COMPLETE`,
  NOT `IN_PROGRESS`. It may resume later at the user's own explicit request; nothing about this decision
  discards, resets, or invalidates any work already done (T1-T11 stand as real, CI-confirmed complete).

### What is complete (Phase 5, as of this freeze)

T1, T1b (compile/unit-test scope), T2, T3, T4a, T4b, T4c, T5, T6, T7, T8, T9, T10, and now **T11 in
full (all 4 slices)** — see the task table below for each task's own detailed status line. All of this is
real, CI-confirmed-green on GitHub Actions macOS runners (never simulated/fabricated), with heavy
independent Opus/Codex review throughout (see `DECISIONS_LOG.md` D96 onward). What is **not** complete
for the already-DONE tasks: MC-2 (live simulator behavior against a real backend) and MC-3 (visual/RTL/
Dynamic-Type/VoiceOver verification) remain Mac-gated and unstarted for every task that needs them — this
was always the plan given no Mac was available, not a new gap introduced by this freeze.

### What is deferred (Phase 5, as of this freeze)

- **T12-T23** (Explore/Learning Paths through the final acceptance audit) — NOT started, zero
  `Features/Explore/*.swift` or later screen files exist. Some read-only research toward T12 (reading
  Android's `ExploreViewModel.kt`/`ExploreScreen.kt`) happened in the prior session but was never
  persisted to any file or doc — a future resume should re-read those Android files fresh rather than
  assume any prior research context survives.
- **MC-1 through MC-4** (Mac-dependent manual verification passes) for every task, past and future —
  blocked on real Mac/iPhone hardware, which does not exist on this project currently.
- The Phase 5 completion audit, final acceptance sign-off, and the F1 ("all 10 façades exercised")
  per-façade evidence table (T5 built the bridge for all 10 façades, but F1's own required evidence is a
  T23 handoff artifact) are all correspondingly deferred along with T12-T23.

### Exact resume point for a future iOS return

1. Re-read this "PHASE 5 FREEZE" box and the "SESSION CHECKPOINT" box below it in full — do not resume
   from memory of a past conversation.
2. Confirm nothing has drifted: `git log --oneline -5` should still show `adb8876` (T11 slice 4) as the
   latest real iOS commit unless further Phase 6-8 work has since touched `mobile/iosApp/` or
   `mobile/shared/` incidentally.
3. Next unstarted task is **T12 — Explore + Learning Paths segment**. Re-read Android's real
   `ui/explore/{ExploreScreen,ExploreViewModel}.kt` fresh (Android is reference-only, not source of truth
   — see `PHASE_5_IOS_IMPLEMENTATION_PLAN.md` § 1 and D96/D132) and confirm the real Swift-bridged
   `MentoraClient` catalog/learningPaths surface against a current `kmp-swift-interface` CI artifact before
   writing any Swift, per this project's established "real API grounding" discipline.
4. Continue under the same parity-focused completion mode (D132) and tiered review policy already in use
   for T9-T11, unless the user gives different instructions at that time.
5. Do **not** treat "iOS was deferred" as license to skip CI/review discipline once resumed — the same
   real-CI-green, real-review bar applies to any future iOS work as applied to T1-T11.

**macOS CI status at this freeze:** green — the `ios-ci.yml` GitHub Actions macOS workflow is fully
working end to end (compile, link, `xcodebuild build`, `xcodebuild test`) and its last real run (commit
`adb8876`, run 35482199062) succeeded. There is no known broken/red CI state being left behind.

---

## SESSION CHECKPOINT — 2026-09-20 — STOPPED BY EXPLICIT USER REQUEST (preserve weekly usage)

**Read this box first if resuming after this checkpoint.** The user explicitly asked to stop all Mentora
work immediately (computer shutdown, preserving weekly Claude usage) — NOT a task failure, NOT a natural
stopping point in the plan, just an intentional pause. Nothing was lost: the working tree was clean and
fully pushed to `origin/main` at the moment of this request; no WIP/checkpoint commit was needed.

- **Branch:** `main`. **Latest local commit:** `adb8876` — "Phase 5 T11 slice 4 (final):
  LoadingState/EmptyState/ErrorState/SuccessState/MentoraSheet/MentoraDialog". **origin/main
  relationship:** even (`+0 -0` ahead/behind) — fully pushed, nothing local-only.
- **Working tree at checkpoint:** clean (`git status --porcelain` empty). No uncommitted changes existed.
- **Current task/slice:** Phase 5, Task **T11** (Component Kit B), **slice 4 of 4 — the FINAL slice**.
  Slices 1-3 are fully done, real-CI-confirmed-green, and documented (D134, D135, D136). Slice 4
  (`LoadingState`/`EmptyState`/`ErrorState`/`SuccessState`/`MentoraSheet`/`MentoraDialog`) was
  implemented, self-verified (all 6 `npm run check` Node gates passed locally before commit), committed
  as `adb8876`, and pushed to `origin/main` — but its remote CI run was **NOT waited on or confirmed**
  before this checkpoint, per the user's explicit "do NOT wait around consuming time/tokens for CI
  completion" instruction.
- **Remote CI run for commit `adb8876` (UNVERIFIED at checkpoint time):** run ID **35482199062**,
  https://github.com/HeshamMohamed94/Mentora/actions/runs/35482199062 — status was `in_progress` (build
  had already compiled successfully and was mid-way through the XCTest unit target) at the moment this
  checkpoint was written. This run was intentionally NOT waited on, NOT polled further, and NOT stopped
  — it may finish (green or red) on GitHub's own infrastructure independent of this session ending. The
  local background shell that had been polling it (`gh run watch`-equivalent loop) was explicitly
  stopped via `TaskStop` as part of this checkpoint, per the user's "stop all local background agents,
  polling commands, shell jobs" instruction — this does NOT cancel the actual remote GitHub Actions run,
  only the local session's own watcher.
- **Review status:** T11 is ordinary Component Kit B work under the tiered review policy (not
  mandatory-review) — self-review + Node gates + CI is the applicable bar, already applied to slices 1-3;
  slice 4 received the same self-review + local Node-gate pass before push, but has NOT yet had its CI
  result confirmed (see above).
- **Docs status:** D134 (slice 1), D135 (slice 2), D136 (slice 3) are all written and pushed. **No
  DECISIONS_LOG.md entry (would be D137) or "T11 SLICE 4" CURRENT_STATUS section exists yet for slice 4**
  — deliberately not written at this checkpoint, since D-numbered entries in this project always record
  a CONFIRMED CI-green outcome with real test-count evidence, and slice 4's CI was never confirmed. Do
  not write D137 from assumption on resume — verify the real run result first.
- **What was NOT started:** T11's own closing "parity matrix update"/task-table completion note was not
  yet written (still correctly shows "slices 1-3 of 4 DONE" as of this checkpoint, since slice 4 isn't
  confirmed). T12 (Explore + Learning Paths segment) was NOT started — no `Features/Explore/*.swift`
  files exist. Some read-only research toward T12 happened this session (Android's real
  `ExploreViewModel.kt`/`ExploreScreen.kt` were read for future grounding) but **zero Swift code for T12
  was written** — this research is not persisted anywhere except this conversation's own history, so a
  fresh resume should re-read those Android files again rather than assume prior context survives.

### EXACT FIRST ACTION ON INTENTIONAL RESUME

1. Run `gh run view 35482199062 --json status,conclusion,url` to see whether the slice-4 CI run finished
   while this session was stopped, and what it concluded.
   - **If `conclusion: "success"`:** pull test-count evidence
     (`gh run view 35482199062 --log 2>&1 | grep -iE "Executed [0-9]+ tests, with 0 failures.*seconds$"`),
     then write **D137** (T11 slice 4 completion) following the exact same structure as D134/D135/D136,
     update `CURRENT_STATUS.md`'s T11 task-table row to "T11 COMPLETE — all 4 slices done" plus a new
     "T11 SLICE 4" section, commit and push those two docs, and — since that closes out Task T11 in
     full — also produce T11's own closing note before moving to T12.
   - **If `conclusion: "failure"`:** inspect the real failure log directly
     (`gh run view 35482199062 --log-failed`) per this project's own standing "never blindly retry" rule,
     diagnose the actual root cause, fix it, re-run the 6 local Node gates, commit the fix, push, and
     watch the new run through to a real confirmed conclusion before writing any docs.
   - **If still `in_progress` / `queued`:** just wait for it normally (watch + independently re-verify,
     the same pattern used for every other CI run this whole task).
2. Once T11 is fully closed out (slice 4 confirmed + documented), proceed to **T12 — Explore + Learning
   Paths segment** per the parity-focused completion mode's "continue automatically" instruction — this
   is a real screen task (not component work), involving actual KMP `sdk.catalog`/`sdk.learningPaths`
   calls, search debounce, cursor paging, and locale-change reload; Android's real
   `ui/explore/{ExploreScreen,ExploreViewModel}.kt` are the reference files to read again first (their
   content is summarized in this session's own transcript but not persisted to any file), and the real
   Swift-bridged `MentoraClient` catalog/learningPaths surface should be confirmed against a real
   `kmp-swift-interface` CI artifact before writing any Swift, per this project's own established "real
   API grounding" discipline (already applied for T10's auth API surface).
3. Do NOT start Phase 6 under any circumstance without the user's own explicit decision, per the
   phase's own standing hard boundary.

---

## EXACT RESUME POINT

**Read this section first when resuming.**

- **PHASE 5 (iOS) — READ THIS FIRST.** Phase 5 is `IN_PROGRESS`. Planning (Acceptance Criteria/System
  Design/Implementation Plan) is done and reviewed twice (Opus + Codex, D96). **This machine has no
  macOS/Xcode/iOS Simulator.** **Changed by D100 (2026-09-18):** a real GitHub Actions macOS CI
  pipeline (`.github/workflows/ios-ci.yml`, new Task **T4c**) is now the compile/verification
  mechanism for all remaining iOS work. T1/T1b/T2/T3/T4a/T4c are the Windows-authored set; **from
  T4b onward, author on Windows in small slices and let CI compile each slice** (never a large batch
  authored ahead of feedback), using the `kmp-swift-interface` artifact (Obj-C header, whatever SKIE
  Swift output actually exists, and a `swift-api-digester` JSON dump of the real Swift-visible API
  surface) as the SKIE reference instead of guessing — **gated**: if that artifact does not actually
  contain a usable Swift API surface, T4b does not start until that gap is fixed, even if the rest
  of CI is green; the first real CI-1 run decides this, it is not assumed. **CI does not unblock
  acceptance:** every live, visual,
  RTL, Dynamic-Type, VoiceOver, playback and session-persistence criterion still needs a human on a
  real Mac with the local backend running (MC-2/MC-3/MC-4). The workflow has **not been pushed or
  run yet** — its first run is the shakedown. See "PHASE 5 — iOS" section near the end of this file for the
  exact resume point and task table, and `execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md` for full task
  detail. The Phase 4 history below (Task 19 recovery, etc.) is retained for context only — Phase 4 is
  COMPLETE, do not redo it.
- **TASK 19 RECOVERY (2026-09-15).** Task 19 is now **DONE**. This bullet records a
  session-interruption recovery for future reference — see `DECISIONS_LOG.md` D94 for the full account.
  Sequence of events: (1) Task 19 was paused mid-work for a planned user shutdown at WIP checkpoint
  commit `1fc9fae` (on top of Task 18's real completion commit `b19a0ce`); (2) the user resumed and told
  the session to continue automatically; that resumed session made real further progress (`StringsParityTest.kt` regex widening, `MentoraRootScreen` loading-label fix, a 3-ViewModel locale-reload
  trim) but its own last piece of work — 6 new `retryLabel`-class string resources added to both
  `strings.xml` files — was never wired into the 4 components those strings were meant for
  (`AnswerOption`/`QuestionCard`/`CertificateCard`/`CourseProgressCard` still hardcoded their English
  defaults), and the terminal closed before any of this was committed; (3) a recovery session audited
  `git log`/`git status` (zero commits after `1fc9fae`; all the above sitting as uncommitted tracked
  changes), confirmed nothing was lost, finished the incomplete component wiring, fixed one stale test
  the ViewModel trim had broken (`ExploreViewModelTest`), ran the full instrumented suite for the first
  time against this commit (105/105, real emulator + real local backend), did a representative
  RTL/Dark-theme/font-scale live spot-check across 9 of the 18 screens, wrote D94, and committed. No
  work was redone or discarded at any point in this sequence.
  - **Final verified state:** `:shared:testDebugUnitTest` 249/249 (zero diff in `mobile/shared`);
    `:androidApp:testDebugUnitTest` 241/241; `:androidApp:connectedDebugAndroidTest` 105/105 on the real
    `Chatting_Pixel_8_API_36` emulator. Full detail, including the honestly-disclosed spot-check-vs-
    exhaustive-sweep and font-scale-check limitations, is in D94 — read it before assuming Task 19's
    RTL/theme/font-scale coverage is exhaustive; it explicitly is not (a spot-check across the
    highest-risk 9 screens, not all 18).
  - **Next:** Task 20 (Live emulator verification, `androidApp/README.md`, Phase 4 → Phase 5 handoff) —
    the final Phase 4 task. Per the phase's own standing hard boundary, Phase 5 (iOS) still requires
    explicit user approval after Phase 4 completes, regardless of how Task 20 goes.

- **Phase:** PHASE 4 — Android — `COMPLETE` (started 2026-09-13, completed 2026-09-15, pending explicit user approval before Phase 5 begins; task plan derived by the `architect` subagent — see `execution/PHASE_4_ANDROID_PLAN.md` and `DECISIONS_LOG.md` D78). Phase 3 — KMP Shared Mobile Core — `COMPLETE` (all 17 tasks done, gates green, working tree clean through Task 16's commit). Phase 1 and Phase 2 are both `COMPLETE` (approved 2026-09-06 and 2026-09-11 respectively); the same-day PRE-PHASE-3 content passes (D67/D68) are also `COMPLETE`. See "PHASE 3 — Task Breakdown" near the end of this file for the full Phase 3 per-task account and `execution/PHASE_HANDOFF.md`'s Phase 3 entry for the complete write-up (implementation summary, files/contracts produced, tests/verification, known limitations, decisions, and what Phase 4 depends on/must not redo). See "PHASE 4 — Task Breakdown" for the full Phase 4 per-task account and `execution/PHASE_HANDOFF.md`'s new Phase 4 entry for the complete write-up.
- **Exact resume point:** Phase 4 is COMPLETE — all 20 tasks done, gates green, working tree clean, pushed to `origin/main`. Per the standing Phase Execution Policy below, do not start Phase 5 (iOS) or Phase 6 (real AI provider integration) under any circumstances before explicit user approval of the completed Phase 4.
- **Phase Execution Policy (standing, applies to this and every future phase):** within an active phase, continue task-by-task automatically without stopping for per-task approval — verify (functional + visual) and commit each task as its own checkpoint, run the full phase-completion gate at the end, update the continuity docs (this file, `PHASE_HANDOFF.md`, `DECISIONS_LOG.md`, `INTEGRATION_CONTRACT.md`) with an implementation-ready handoff, then STOP and wait for explicit user approval before the next phase. Only a genuine blocker or an irreversible product decision pauses mid-phase. This policy itself must be carried forward into every future phase's continuity docs, not just Phase 2's.
- **Historical note (superseded — retained for context only, see the Phase 3 section for what actually happened next):** the following "Current task" bullet was accurate as of the moment Phase 3 kicked off (i.e. describes Phase 2's own final state); it predates all of Phase 3 and has intentionally not been rewritten so this document's Phase 2 kickoff-time snapshot stays intact. All Phase 2 tasks (1-17) are DONE. Tasks 12-17 (Admin Web; Localization completion pass + RTL QA sweep; Playwright E2E suite; `web/README.md`; Phase 2 quality gate verification; `PHASE_HANDOFF.md` Phase 2 write-up) completed 2026-09-11 (D62/D63/D64/D65/D66) — see the PHASE 2 task table for the full account. **Phase 2 is COMPLETE, pending explicit user approval before Phase 3 begins**, per the standing Phase Execution Policy below. Tasks 1-11 are DONE and verified end-to-end (real browser + real backend, en+ar) — foundation, auth, public discovery, demo checkout/purchase success, dashboard/my learning + the authenticated Sidebar shell, Course Player/Quiz/Quiz Results, Certificates List/Detail, Learning Paths follow/unfollow (already complete since task 3), AI Tutor chat UI (streaming), Profile + Settings (incl. functional language selector), and Instructor Web (Dashboard, Course Editor Overview/Curriculum, Lesson Editor, Quiz Editor). Tasks 6-7-9-10-11 were delegated to Codex via the `codex-delegate` skill (D41/D42/D43/D44/D47) and independently reviewed/verified/landed by Claude. **Two dedicated UI-fidelity correction passes across Tasks 1-10** were done before Task 11, per explicit user request: D45 (2026-09-07) fixed `TextField`'s missing floating-label behavior, added the required `PasswordField` visibility toggle, and rebuilt Login/Register from unstyled scaffolding into a proper card surface with a minimal logo-only header; D46 (2026-09-07, a stricter follow-up pass) fixed every course thumbnail in the local demo rendering as a broken-image icon, restored `CourseCard`/`LearningPathCard`'s spec'd-but-missing primary-action element, and fixed a widespread "error retry button shows the error sentence" bug. **A third, strictest fidelity pass (2026-09-07, D48) is also DONE**, this time comparing directly against the locked `design-review-locked/Mentora Showcase.dc.html` (not only `design-system/*.md` prose) per explicit user instruction: implemented the governed 5-motif course-artwork gradient system for the first time (`CourseThumbnail`'s new `seed`/`categoryId`/`badge` props, used everywhere a course thumbnail renders), moved the category chip onto the artwork as a scrim overlay, gave `LearningPathCard` its missing icon/eyebrow/arrow, rebuilt `CourseProgressCard` as the showcase's horizontal row (Dashboard/My Learning), rebuilt the Landing hero as a two-column layout with a real stats row and course-artwork collage, added a 4th Dashboard stat + an AI Tutor nudge card, and gave the Instructor Dashboard course list a real table header row. **Awaiting explicit user approval of this pass before Task 12 starts** — this is a standing instruction, not a default that erodes over time. Icon set is a hand-drawn inline-SVG placeholder for the real self-hosted Material Symbols Rounded font (D40) — swap later behind the same `Icon` component API, no call-site changes needed. Landing page's own `CourseCard`/`LearningPathCard` usage still hardcodes the Guest `basePath` (unchanged from D39) — low priority since Landing is conceptually Guest-only. Two spec-vs-backend gaps disclosed and scoped out in task 10 (D44): no password-change endpoint exists, and `avatarMediaId` is a dead field nothing ever writes — Profile shows initials-only, no upload control. `DataTable` (needed for Task 12's Manage Courses/Users/Instructors/Categories tables) is still unbuilt — deliberately out of scope per D47. D48 deliberately left the Instructor Course Editor's showcase-vs-locked-spec IA conflict (persistent rail vs. two tabs) unresolved — see D48 for the full reasoning — and did not add a public language-toggle or guest AI-Tutor nav link (neither is real functionality yet). **A fourth, targeted pass (2026-09-07, D49) is also DONE**: a full visual audit (`D:\Work\MentoraVisualAudit\`) had scored 24 screens against the locked showcase and found only 7 with a genuine EXACT assembled reference (Landing, Explore, Dashboard, Course Player, Instructor Dashboard, Course Editor Overview/Curriculum); D49 corrected those 7 specifically rather than chasing the showcase everywhere. The highest-priority fix was Course Player, rebuilt from a single-column/sidebar-visible/tab-less layout (55% fidelity) into the locked two-column "focused learning shell" (top bar, video-left/curriculum-right split, Overview/Resources tabs, Previous/Mark Complete/Next row) — now ≈88%. Dashboard gained a real-data-driven "Up Next" right-rail module (72%→91%). Landing's hero heading copy and collage grid were corrected (78%→90%). Instructor Dashboard's table header was made uppercase (74%→78%) and Course Editor's panel was given a bounded max-width (60%→66% Overview, 68%→72% Curriculum) — both capped below 90% by the same disclosed showcase-vs-locked-spec conflicts D48 already identified (Instructor Dashboard's 4th stat card/table columns, Course Editor's Media tab/persistent rail), which D49 re-confirms as intentionally unresolved, not missed. Explore was re-verified but received no structural change (88%→89%). Full re-scoring, side-by-side images, and the disclosed-conflict list are in `D:\Work\MentoraVisualAuditExactPass\EXACT_PASS_REPORT.md`. One operational incident during this pass: running `npm run build` while `npm run dev` was still active corrupted the dev server's `.next` cache (`Cannot find module './vendor-chunks/@formatjs.js'`); fixed by killing the dev process, deleting `.next`, and restarting `npm run dev` clean — no application code was at fault. **A dedicated Design-to-Code source-of-truth phase (2026-09-07, D50) is also DONE**, sitting between Task 11 and Task 12 (NOT a numbered Phase 2 product task — Task 12 has still not started). Built `design-to-code/` (README, `SOURCE_MANIFEST.json` with a locked precedence rule — Product/UX > Design System > Showcase > current web implementation-as-evidence-only; `shared/` — tokens/typography/spacing/shape/elevation/components/navigation/artwork/responsive/localization/platform-contract, all normalized FROM the existing locked sources, never re-invented; `screens/` — one JSON spec per all 24 implemented MVP screens, each tagged `exact-showcase`/`approved-pattern`/`ux-only` honestly, 7/5/12 respectively; `patterns/` — 6 reusable layout patterns for screens with no exact mockup; `validation/` — EXTRACTION_REPORT.md, COVERAGE_REPORT.md, MAPPING_REPORT.md). Deliberately did NOT generate Admin (screens 25-29) specs even though the locked docs already define them, to avoid any appearance of starting Task 12. Added `tools/design-to-code/{validate.js,generate.js}` (plain Node, no new dependency, same D35 precedent as `tools/token-pipeline/`) — validation passes clean (24 screens/6 patterns/11 shared files, 0 errors/warnings) and generation writes `web/src/lib/design-to-code.generated.ts` plus two audit-trail JSON files under `design-to-code/generated/web/`. Migrated exactly two genuine hardcoded-duplication cases onto the new generated source with verified byte-identical output: the Student/Instructor Sidebar nav-item arrays (`app-shell.tsx`/`instructor-shell.tsx`) and the 5-motif course-artwork gradient array (`course-thumbnail.tsx`) — confirmed via live re-verification (Dashboard/Explore/Instructor Dashboard, EN+AR/dark) that rendering is unchanged. All four gates re-run clean (`typecheck`/`lint`/`lint:logical-properties`/`build`, same single pre-existing `<img>` warning). See `execution/DECISIONS_LOG.md` D50 and `design-to-code/validation/*.md` for the full account.
- **What exists in `web/` right now (tasks 1–2):**
  - Next.js 15 App Router + TypeScript, `[locale]` routing (`en`/`ar`, `localePrefix: "always"`) via `next-intl`, `src/middleware.ts` combining locale detection with the `/app`, `/instructor`, `/admin` auth gate (redirects to `/login?redirect=<intent>` when the `mentora_refresh_token` cookie is absent).
  - `tools/token-pipeline/generate.js` (plain Node, see D35) generates `web/styles/tokens.css` (semantic + component-layer CSS custom properties, light/dark via `[data-theme]` + `prefers-color-scheme`), `web/styles/tailwind-theme.css` (Tailwind v4 `@theme inline` color mapping + custom `tablet`/`desktop`/`large-desktop` breakpoints), and `web/src/lib/design-tokens.generated.ts`. Re-run `npm run generate-tokens` (from `web/`) after any `design-tokens.json` change.
  - `web/src/app/components.css` — hand-authored Button (primary/secondary/tonal/text)/TextField CSS classes consuming only generated tokens; `web/src/components/ui/` has the React wrappers.
  - `web/src/lib/api/client.ts` — typed fetch client: CSRF header on writes, response-envelope unwrapping, 401→refresh→retry, `mentora:force-logout` event on unrecoverable 401. `web/src/lib/auth/` — `login`/`register`/`logout`/`getCurrentUser` + `useCurrentUser` TanStack Query hook. See D36: login/register's embedded `user` is a narrower shape than `/users/me`'s.
  - Real pages: Landing (`(public)/page.tsx`, placeholder hero only), Login, Register (both fully functional — react-hook-form + zod, wired to the live backend), and a minimal `/app` Dashboard shell (proves the session loop; not the real Dashboard screen from `SCREEN_INVENTORY.md § 8`, that's task 5).
  - Verified live: register → cookies set → `/app` accessible → `/users/me` succeeds → logout → cookies cleared → `/app` redirects to `/login?redirect=%2Fen%2Fapp`. Verified in an actual Chrome tab too (screenshots), both `en` (LTR) and `ar` (RTL, mirrored layout, IBM Plex Sans Arabic font) — see chat history for screenshots.
  - Gates green: `npm run typecheck`, `npm run lint`, `npm run lint:logical-properties`, `npm run build` (both locales prerender).
  - Backend is running locally for this work (`backend/.env` created with a generated `JWT_SIGNING_SECRET`; MongoDB replica set already existed from Phase 1). A test account exists in the dev DB: `phase2tester@example.com` / `MentoraDemo1`.
- **Not yet built (do not assume these exist):** Sidebar (authenticated shell chrome), Explore/Course Details/Learning Paths real content, Course Player, Quiz, Certificates, AI Tutor, Instructor Web, Admin Web, Settings/language-selector UI (routing supports `ar` already; no in-app switcher control yet), Playwright E2E, `web/README.md`. Icon system (Material Symbols) is not wired up yet — `TextField`'s error state is currently border+text only, missing the third "icon" signal `ACCESSIBILITY.md § 7` requires (tracked in a comment in `text-field.tsx`, not yet a DECISIONS_LOG entry since it's an open gap, not a resolved one).
- **Next immediate action:** Start Phase 2 task 3 (Explore/Course Details/Learning Paths) — needs `lib/api/courses.ts`/`categories.ts`/`learningpaths.ts` TanStack Query hook modules following the `execution/INTEGRATION_CONTRACT.md` shapes, plus the CourseCard/SearchField/CategoryChip components from `design-system/COMPONENTS.md`. `execution/PHASE_HANDOFF.md`'s Phase 1 entry remains the authoritative Phase 1 account — do not re-read backend code, do not modify `backend/`.

### What is COMPLETE (committed, reviewed, gates green) — tasks 1–23, all of Phase 1

All of: execution continuity docs, backend Gradle scaffold, Ktor foundation (M1), Auth & RBAC (M2), Users module, Courses & Categories (M5 slice), Enrollment/Demo Checkout (M6 slice), Progress (M7 slice), Quiz (M8 slice), Certificates + completion-crossing wiring (M9 slice), Learning Paths (M10 slice), Media/local filesystem storage (M11 slice), Instructor aggregation endpoint (M11 tail), Admin aggregation endpoints (M12 slice), AI Tutor scaffold (M13 boundary only). Last commit: see `git log` on `main` — "Phase 1: AI Tutor scaffold (M13 boundary only)".

Task 15 (Media): Codex-authored, Claude-reviewed closely. Implements the `MediaStorage` abstraction + local-filesystem impl with canonical-path-escape protection, streaming upload with per-kind size caps (images 5 MB / video 500 MB, aborted-and-cleaned-up mid-stream on overflow, never buffering the full file), public thumbnail/avatar serving, and a gated lesson-video playback flow (a second, purpose-scoped 5-minute JWT, verified by hand — not a second Authentication provider — plus Ktor's `PartialContent` plugin for range-request seeking). Ownership/enrollment authorization reuses the already-exposed `CourseService.requireOwnership`/`EnrollmentService.requireEnrollment` (no new cross-module surface). One correctly-identified and fixed implementation-time conflict: `media.ownerRefId` had to become a `String` (not the originally-assumed `ObjectId`) because `courses` generates lesson ids as UUID strings, not Mongo ObjectIds — see `DECISIONS_LOG.md` D21/D25 for the full reasoning, D22–D24 for the size-limit/token/status-lifecycle decisions, D26 for a real Phase-2-relevant contract note (multipart form fields must precede the file part in the upload request). Verified independently: re-ran `./gradlew test --rerun` myself (not just trusting Codex's self-report) — fresh, non-cached run against live MongoDB, 29 tests/9 suites, 0 failures/errors. Reviewed the diff directly (not just the test outcome): storage-key generation is always server-generated, never client-filename-derived; content-type/kind cross-validation happens before any disk write; `courseId` additive field correctly resolves both the upload-ownership and playback-enrollment checks without a new reverse lookup; public `/file` route correctly 404s for `lessonVideo`-kind media.

Task 16 (Instructor aggregation endpoint): Codex-authored, Claude-reviewed. Implements `GET /api/v1/instructor/dashboard` per `architecture/BACKEND_ARCHITECTURE.md § 3`'s explicit design for this module (no collection of its own — reads directly across `courses`/`enrollments`/`progress`, the one documented exception to the usual reuse-another-module's-service-method boundary rule): owner-scoped `stats` (`totalCourses`, `publishedCount`, `totalEnrollments`) plus a per-course list (`enrollmentCount`, `completionRate` — defined as the average of `progress.completionPercent` across that course's progress documents, `0` when there are none). Codex's dispatch was cut off mid-run by an OpenAI usage-limit error, after writing the module and test but before self-verifying — Claude ran the gates independently instead of waiting for quota reset, found and fixed one test-fixture-only bug (JWT claim name mismatch, `sub` vs. the codebase's actual `userId` claim), and re-verified clean. See `DECISIONS_LOG.md` D27 for the full account. All gates green (31 tests, 10 suites).

Task 18 (AI Tutor scaffold, M13 boundary only): Codex-authored, Claude-reviewed. Implements `GET /api/v1/ai-tutor/conversation` and `POST /api/v1/ai-tutor/conversation/messages`, the `AiProvider` interface, and a `StubAiProvider` binding (Phase 1 streams fixed placeholder text, genuinely chunked over the wire — never a real LLM completion; Phase 6 swaps only the Koin binding). Real enrollment gate, real persistence (`aiConversations`/`aiMessages`, both newly-owned collections with their own indexes), real per-minute-and-daily rate limiting (both now `AppConfig`-driven, closing a gap where only a hardcoded per-minute cap existed). One implementation-time gap resolved before dispatch (not discovered mid-review this time): lesson ids have no reverse lookup to their owning course, so the request pairs `courseId` with `lessonContextId` — the same fix already established for `media`'s `lessonVideo` upload (D21), applied here proactively. See `DECISIONS_LOG.md` D30 for this and three other implementation-time specifics (system prompt scope, streaming wire format, rate-limit config). All gates green (41 tests, 12 suites) — independently re-verified, not just Codex's self-report.

**Also fixed during task 18's independent verification, as its own separate commit:** a pre-existing flaky test in `MediaIntegrationTest` (task 15), unrelated to `aitutor` — see `DECISIONS_LOG.md` D31.

Task 19 (backend test suite completeness review, M15 portion): a fork-based audit found two concrete,
bounded gaps against `architecture/TESTING_STRATEGY.md § 1` (not a rewrite — every module already had a
passing integration suite): (1) zero unit tests existed anywhere despite the strategy doc naming three
explicit examples — added `QuizServiceTest.kt`/`CourseServiceTest.kt`/`ProgressServiceTest.kt` (MockK,
no live Mongo); (2) `POST /courses/{id}/unpublish` had never been called by any test despite being named
in two milestones' roadmap acceptance criteria — added coverage (owner/Admin/wrong-role/enrolled-student-
keeps-access) plus three other never-exercised courses routes (section rename/delete, lesson delete, both
asserting order re-compaction). Zero production bugs found — every new test asserts already-correct
behavior. See `DECISIONS_LOG.md` D32. All gates green (64 tests, 15 suites), independently re-verified.

**Also found and fixed during task 22's audit, as its own separate commit:** `POST /media/uploads` was
missing the CSRF header check every other mutating route already has — a genuine, real security gap, not
a pre-existing documented limitation. See `DECISIONS_LOG.md` D33.

Task 20 (seed/demo data script, M16 portion): a backend-native Kotlin entry point (`SeedData.kt`) + Gradle
`seedDemoData` task — not `infra/docker/mongo-init` (D34: no Docker/infra exists in Phase 1 at all, per
D1). Reuses the real service layer for every entity it can (register/category/course/media/quiz), with
two narrow justified exceptions (Instructor/Admin role-flip, Learning Path insert — both because no other
write path exists for them, by design). Idempotent, verified by running it twice. Seeds 6 demo accounts
(`@mentora.dev` / `MentoraDemo1`), 4 categories, 6 courses (4 published/2 draft, real en/ar content), a
quiz, a Learning Path. Independently re-verified beyond the self-report: `mongosh` count spot-check, and a
real end-to-end check — started the actual server, logged in over HTTP as the seeded admin account.

Task 21 (local run instructions, M16 portion): `backend/README.md` + `backend/.env.example`, Claude-authored
directly (pure documentation, not delegated). Covers the one-time MongoDB replica-set conversion (D12,
previously undocumented anywhere outside the decisions log), env setup, build/test/run/seed commands, and
the exact demo credentials task 20 produced.

Task 22 (Phase 1 quality gate verification): Claude-led, no delegation. A from-scratch clean build
(`gradlew.bat clean` then `test build --rerun-tasks`) confirmed 65 tests / 15 suites / 0 failures / 0
errors — the definitive number for the Phase 1 report, not a cached result. Full audit: RBAC/auth coverage
(every route file checked for `authenticate()`/CSRF coverage — found and fixed the one gap, D33), a
whole-backend grep sweep for payment vocabulary (zero matches), hardcoded secrets (zero), stray debug
logging (zero), TODO/FIXME markers (zero), module/index/route wiring completeness (all 13 modules
correctly registered), and a `git log --name-only` confirmation that no commit this phase ever touched
`architecture/`, `product/`, `ux/`, or `design-system/`. Compiled the full known-limitations list (7 items,
all pre-existing/deliberate, none blocking — see `PHASE_HANDOFF.md`'s Phase 1 entry § 6).

Task 23 (`PHASE_HANDOFF.md` final write-up): Claude-authored, the fixed structure the file's header
specifies (status, what was implemented, files/modules, API/contracts, database changes, tests/verification,
known limitations, decisions, next-phase dependencies, what not to redo, git reference) — see that file
directly for the full account; not duplicated here.

**Phase 1 is COMPLETE.** All 23 tasks done, gates green, working tree clean. Awaiting the user's explicit
approval before any Phase 2 work begins.

### What is PARTIAL / uncommitted

Nothing. All of Phase 1 is committed and clean.

### Exact next steps on resume

1. Wait for the user's explicit approval of the Phase 1 report delivered this session.
2. Do not start Phase 2 (or any later phase) under any circumstances before that approval — this is an
   explicit, standing instruction, not a default that erodes over a long gap between sessions.
3. Once approved: Phase 2 — Website (see `execution/MASTER_IMPLEMENTATION_PLAN.md`'s Phase → Milestone map).

---

Allowed phase states: `NOT_STARTED`, `IN_PROGRESS`, `BLOCKED`, `COMPLETE`.

| Phase | Status | Notes |
|---|---|---|
| PHASE 1 — Backend Foundation & API | **COMPLETE** | Started 2026-09-05, completed 2026-09-06, approved by the user 2026-09-06. See `PHASE_HANDOFF.md` for the full write-up. |
| PHASE 2 — Website | **IN_PROGRESS** | Started 2026-09-06. See task breakdown below. |
| PHASE 3 — KMP Shared Mobile Core | **COMPLETE** | Started 2026-09-12, completed 2026-09-12, pending explicit user approval before Phase 4 begins. See `PHASE_HANDOFF.md` for the full write-up and the task breakdown near the end of this file. |
| PHASE 4 — Android | **COMPLETE** | Started 2026-09-13, completed 2026-09-15, pending explicit user approval before Phase 5 begins. See `PHASE_HANDOFF.md` for the full write-up and `execution/PHASE_4_ANDROID_PLAN.md` / the task breakdown near the end of this file for detail. |
| PHASE 5 — iOS | **IN_PROGRESS** | Started 2026-09-18. Acceptance criteria, system design, and a 23-task implementation plan authored and reviewed twice (Opus + Codex) before any code — see `PHASE_5_ACCEPTANCE_CRITERIA.md`/`PHASE_5_IOS_SYSTEM_DESIGN.md`/`PHASE_5_IOS_IMPLEMENTATION_PLAN.md` and `DECISIONS_LOG.md` D96. No Mac/Xcode on this machine; **D100 (2026-09-18) added Task T4c — a GitHub Actions `macos-15` CI pipeline (`.github/workflows/ios-ci.yml`) — as the compile/unit-test authority**, so T4b onward is authored on Windows in small slices and compiled by CI rather than blocked. Live/visual/accessibility acceptance still requires a real Mac (MC-2/MC-3/MC-4). See "PHASE 5 — Task Breakdown" near the end of this file. |
| PHASE 6 — AI Tutor Integration | NOT_STARTED | Blocked on Phases 2, 4, 5 (client shells) + Phase 1 (aitutor scaffold). |
| PHASE 7 — Full Integration | NOT_STARTED | Blocked on all prior phases. |
| PHASE 8 — QA, Polish & Portfolio Demo | NOT_STARTED | Blocked on Phase 7. |

---

## PHASE 1 — Task Breakdown (COMPLETE — all 23 tasks done)

| # | Task | Status |
|---|---|---|
| 1 | Read locked docs (architecture, product, ux, design-system) | DONE |
| 2 | Confirm git state (branch `main`, clean tree) | DONE |
| 3 | Create `execution/` continuity documents | DONE |
| 4 | Audit local toolchain (JDK, MongoDB, Gradle, Codex) | DONE |
| 5 | `backend/` Gradle project scaffold (M0 slice) | DONE — Claude-authored (Gradle Kotlin DSL, wrapper 8.11, package tree per REPOSITORY_STRUCTURE.md) |
| 6 | Ktor plugin stack, config, `/healthz` (M1) | DONE — common/config foundation Claude-authored; plugin wiring + Mongo connectivity Codex-authored (run 01), Claude-reviewed, fixed 2 build-breaking issues (jbcrypt version, koin/Ktor 3 incompatibility), verified `./gradlew build`/`test` green. Committed `18040a0`. |
| 7 | Auth & RBAC (M2) | DONE — Codex-authored (run 02), Claude-reviewed line-by-line (security-critical per roadmap): reuse-detection breach path, timing-safe login response (dummy BCrypt hash), atomic conditional revocation, SHA-256 refresh-token hashing, CSRF enforcement. Claude extracted a duplicated CSRF check into common/Csrf.kt. All gates green. |
| 8 | Users module | DONE — implemented alongside M2 (GET/PATCH /users/me), Claude-reviewed |
| 9 | Courses/Categories module (M5 slice) | DONE — Codex-authored (run 03), Claude-reviewed: verified published-only filter can't be bypassed via query param, draft visibility returns 404 not 403, ownership/role gating correct at route layer, category counters atomic ($inc), nested sections/lessons round-trip through BSON codec. All gates green. |
| 10 | Enrollment / demo checkout (M6 slice) | DONE — Codex-authored (run 04), Claude-reviewed (no-real-payment-path boundary per roadmap): verified zero payment vocabulary via independent grep, real MongoDB transaction with correct abort-on-exception, idempotent double-completion proven both sequentially and via duplicate-key fallback. One known minor limitation documented (D14, true-concurrency edge case, non-blocking). All gates green. |
| 11 | Progress module (M7 slice) | DONE — Codex-authored (run 05), Claude-reviewed: verified enrollment gate, idempotent lesson-completion map, correct integer percentage math, lazy find-or-create via atomic upsert, and that quizPassed/courseCompletedAt stay null as scoped (D15). All gates green. |
| 12 | Quiz module (M8 slice) | DONE — Codex-authored (run 06), Claude-reviewed (isCorrect-stripping per roadmap): confirmed `StudentQuizOption` is structurally a distinct type with no `isCorrect` property (not a serialization-omitted field), verified via raw-JSON substring test; grading math, editor validation, and the authorized `progress.setQuizPassed` addition all verified. Certificate/completion-crossing wiring deferred to next task (D16). All gates green. |
| 13 | Certificates module + course-completion wiring (M9 slice + D15/D16/D17 follow-through) | DONE — Codex-authored (run 07), Claude-reviewed closely: independently verified via grep that progress/quiz services have zero dependency on certificates (route-layer-only wiring, no circular dependency), confirmed the eligibility check, the reversible human-readable public ID, and order-independence (quiz-first and lessons-first both correctly trigger issuance) with genuine direct-collection-count proof of no double-issuance. All gates green. |
| 14 | Learning paths module (M10 slice) | DONE — Codex-authored (run 08c, resumed after a usage-limit pause and a machine-shutdown pause, D18/D19), Claude-reviewed: production code correct on first read (order-preserving course resolution, dangling-course omission from detail + progress denominator, idempotent follow/unfollow via unique index + upsert, guest-safe optional auth). Found and fixed 3 test-only bugs (ambiguous helper overload emptying register/login bodies, BSON date-type mismatch in a raw-seed fixture, a null-encoding assertion mismatched to the app's `explicitNulls = false` config) — see D20. All gates green (25 tests, 8 classes). |
| 15 | Media module — local filesystem storage (M11 slice) | DONE — Codex-authored, Claude-reviewed: verified the `MediaStorage` abstraction's path-escape defense, streaming size-cap-with-cleanup, server-generated (never client-derived) storage keys, public-thumbnail vs. gated-lesson-video route split, the two-JWT (session vs. purpose-scoped playback) design, and that ownership/enrollment checks reuse existing cross-module methods with zero new surface. One correctly-caught implementation conflict (lesson ids are UUID strings, not ObjectIds — D25). Independently re-ran the test suite myself (fresh, non-cached, live MongoDB) rather than trusting the self-report. All gates green (29 tests, 9 classes). |
| 16 | Instructor aggregation endpoints (M11 slice) | DONE — Codex-authored, Claude-reviewed: `GET /api/v1/instructor/dashboard` reads directly across `courses`/`enrollments`/`progress` per the module's documented no-own-collection design; verified owner-scoping (another Instructor's courses never leak in), stats math, and the completion-rate definition (average `progress.completionPercent` per course). Codex's dispatch was interrupted by an OpenAI usage-limit error before it could self-verify; Claude ran the gates independently, found and fixed one test-fixture-only bug (JWT claim name), re-verified green — see D27. All gates green (31 tests, 10 suites). |
| 17 | Admin aggregation endpoints (M12 slice) | DONE — Codex-authored, Claude-reviewed: `GET /api/v1/admin/{dashboard,courses,users,instructors}` reading directly across `courses`/`users`/`enrollments` per the module's no-own-collection design (same pattern as `instructor`, task 16). `users`/`instructors` are disjoint role-scoped lists (`role == student` / `role == instructor`, never "all accounts"); `q` search on both escapes untrusted input via `Pattern.quote` before building the Mongo regex filter. Reused the already-implemented `categories` CRUD and `POST /courses/{id}/unpublish` (already Admin-capable) rather than duplicating either. Codex's dispatch hit the same OpenAI usage-limit wall as task 16 twice before a third immediate retry succeeded cleanly — see D28/D29. Independently re-verified: fresh `gradlew.bat test --rerun-tasks` (35 tests, 11 suites, 0 failures) and `gradlew.bat build`, plus a full diff read. |
| 18 | AI Tutor scaffold + `AiProvider` interface + stub impl (M13 boundary only) | DONE — Codex-authored, Claude-reviewed: `GET /ai-tutor/conversation` + `POST /ai-tutor/conversation/messages`, real persistence/enrollment-gate/rate-limiting, bound to a stub `AiProvider` (placeholder text, genuinely chunked streaming, no real LLM call — Phase 6 swaps only the Koin binding). `courseId`+`lessonContextId` paired in the request (no lesson→course reverse lookup exists — same fix as media's D21). Added both a per-minute and a per-day rate cap, both `AppConfig`-driven (previously only a hardcoded per-minute cap existed). See D30. Independently re-verified (41 tests, 12 suites, 0 failures) rather than trusting the self-report; that re-verification also surfaced and fixed an unrelated pre-existing flaky test in `MediaIntegrationTest` (task 15) — see D31, landed as its own separate commit. |
| 19 | Backend test suite (M15 portion) | DONE — Codex-authored, Claude-reviewed: added the previously-missing unit-test layer (MockK, quiz scoring/publish-validation/completion-percentage) and closed 4 never-exercised `courses` routes (`unpublish` — the highest-priority gap, plus section rename/delete and lesson delete). Zero production bugs found. See D32. 64 tests, 15 suites, independently re-verified. |
| 20 | Seed data script | DONE — Codex-authored, Claude-reviewed: backend-native `SeedData.kt` + Gradle `seedDemoData` task (not `infra/docker/mongo-init` — see D34), idempotent, reuses the real service layer. Seeds 6 accounts/4 categories/6 courses (4 published/2 draft, en+ar)/1 quiz/1 Learning Path. Independently verified via `mongosh` + a real HTTP login as the seeded admin. |
| 21 | Local run instructions (M16 portion) | DONE — Claude-authored directly (documentation, not delegated): `backend/README.md` + `backend/.env.example`, covering the MongoDB replica-set one-time setup (D12), env config, build/test/run/seed commands, and the seed script's exact demo credentials. |
| 22 | Phase 1 quality gate verification | DONE — Claude-led, no delegation: from-scratch clean build (65 tests, 15 suites, 0 failures), full RBAC/CSRF/secrets/payment-vocabulary/module-wiring audit (found and fixed the D33 CSRF gap), locked-docs-untouched confirmation, known-limitations compilation. |
| 23 | `PHASE_HANDOFF.md` write-up | DONE — Claude-authored final Phase 1 entry in the file's required fixed structure; status marked COMPLETE. |

## PHASE 2 — Task Breakdown

Web app lives in `web/` at repo root (sibling to `backend/`), per `REPOSITORY_STRUCTURE.md`. Consumes `execution/INTEGRATION_CONTRACT.md` as the authoritative API shape — never re-derives it from `architecture/API_CONTRACT.md` alone where the two differ.

| # | Task | Status |
|---|---|---|
| 1 | Foundation: Next.js scaffold, Tailwind v4 + token pipeline, i18n routing, API proxy, auth/CSRF/TanStack Query plumbing, base Navbar + core component kit (Button, TextField) | DONE |
| 2 | Auth screens (Login, Register) + middleware auth gate | DONE |
| 3 | Public discovery: Landing, Explore, Course Details, Learning Paths (+ Learning Path Details) | DONE — real content against live `courses`/`categories`/`learningpaths` endpoints; shared screens under `components/screens/` rendered from both `(public)/...` and `app/...` route trees per the "same screens, not duplicate screens" IA rule; Landing server-fetches featured courses/paths/categories in parallel (`revalidate = 300`); enrollment-aware CTA on Course Details (Login to Enroll / Enroll / Continue Learning); course level labels centralized in `lib/i18n/course-labels.ts` and translated everywhere (D38's `explicitNulls=false` gotcha caught and fixed here — see D38). Verified via real browser against live seeded backend in both en (LTR) and ar (RTL). Clean `lint`, `lint:logical-properties`, and `build` (both locales, 0 errors, only pre-existing `<img>` warnings). |
| 4 | Demo Checkout + Purchase Success | DONE — `CheckoutScreen`/`PurchaseSuccessScreen` at `/app/checkout/:id` and `/app/checkout/:id/success` against the live `enrollment` endpoints; exact `Checkout/OrderSummary` + `SuccessState` component specs (design-system/COMPONENTS.md), zero payment vocabulary/fields (product/DEMO_PAYMENT_FLOW.md § 1 verified by inspection), idempotent re-checkout on an already-enrolled course confirmed live. Found and fixed a real pre-existing bug while browser-testing the entry path — see D39 (`CourseCard`/`LearningPathCard` `basePath` prop). Verified via real browser in en+ar end to end (Explore → Course Details → Enroll → Checkout → Complete Demo Purchase → Purchase Success → enrollment persisted). Clean lint/RTL-check/build. |
| 5 | Student Dashboard + My Learning | DONE — also built the first real authenticated `Sidebar` shell (design-system/COMPONENTS.md § Sidebar), replacing the bare chrome-less `<main>` every `/app/*` page had until now (see D39/D40). Dashboard (`/app`) has stats, Continue Learning, followed Learning Paths in progress, and recommendations; My Learning (`/app/my-learning`) has the full filterable enrollment grid. No dedicated aggregate backend endpoint exists for either — both compose `GET /enrollments`/`GET /learning-paths` with per-item detail fetches client-side (D40), an accepted N+1-at-small-scale tradeoff given the seed data's size. Verified live in en+ar (Sidebar collapse/expand, RTL mirroring, real stats/progress from the backend); mobile drawer verified by code review only — the browser automation environment's window resize did not change the rendered viewport, so the off-canvas breakpoint behavior itself is unverified live (see D40). Clean lint/RTL-check/build. |
| 6 | Course Player + Quiz + Quiz Results | DONE — delegated to Codex via the `codex-delegate` skill (D41), Claude reviewed and landed. `/app/learn/:id` (curriculum + real VideoPlayer/PlaybackControls against the media playback-url/stream endpoints + Mark Complete/auto-advance), `/app/learn/:id/quiz` (question-by-question QuestionCard/AnswerOption flow), `/app/learn/:id/quiz/results` (fresh-fetched score/breakdown + completion confirmation). Sidebar auto-collapses on both screens via a new `SidebarForceCollapseContext`, reverting on navigate-away without touching the user's saved preference. Verified live end-to-end as the seeded student: completed both lessons of a real course, took its real 3-question quiz, saw the graded breakdown, reached the certificate-issued completion confirmation, and confirmed My Learning/Dashboard stats updated correctly. Independently re-ran typecheck/lint/RTL-check/build (all matched Codex's own claims exactly) and read every diff against the brief — zero out-of-scope changes. |
| 7 | Certificates List + Certificate Detail | DONE — delegated to Codex via `codex-delegate` (D42), Claude reviewed and landed. `/app/certificates` (grid + `EmptyState`) and `/app/certificates/:id` (`:id` is the backend's public `MTR-XXXX-...` format, passed through verbatim). No real certificate-image asset exists in the backend, so both screens build a polished, fully token-driven placeholder/"document" presentation instead of pointing at a nonexistent image endpoint — see D42. "Share" is confirmed UI-only (zero network calls). Verified live: the certificate earned during task 6's live test appears correctly in both screens, en+ar, including the not-found path for an invalid id. Independently re-ran every gate; matched Codex's own claims exactly. |
| 8 | Learning Paths (student-aware) follow/unfollow wiring | DONE — already fully implemented as part of task 3's `learning-path-details-screen.tsx` (`useFollowLearningPath`/`useUnfollowLearningPath`, login-gated for guests, `path.isFollowing`-driven button state). Discovered already complete while planning task 6/7 — noted here so it isn't redone. |
| 9 | AI Tutor chat UI (streaming) | DONE — delegated to Codex via `codex-delegate` (D43), Claude reviewed and landed. `/app/ai-tutor` chat screen against the real `AiTutorRoutes`/`StubAiProvider` chunked `text/plain` streaming endpoint (architecturally distinct from every other endpoint's JSON envelope — a dedicated `streamAiMessage()` in `lib/api/ai-tutor.ts` reads the raw `ReadableStream` via `getReader()`/`TextDecoder`, bypassing the shared `apiFetch` abstraction on purpose). Thinking-dots indicator, token-by-token streaming render with blinking cursor, quick-action chips that send immediately, IME-safe Enter-to-send, partial-reply preservation on mid-stream failure with a separate retryable error bubble, accessible `role="log"` thread plus a completion-only `sr-only` live region. Sidebar stays visible (not force-collapsed, unlike Course Player/Quiz). Verified live as the seeded student: sent a typed message and a quick action, watched the stub reply stream and complete correctly, then confirmed `/ar/app/ai-tutor` mirrors correctly via logical properties (bubble sides swap, Arabic strings render). Independently re-ran every gate; matched Codex's own claims exactly. |
| 10 | Profile + Settings (incl. language selector) | DONE — delegated to Codex via `codex-delegate` (D44), Claude reviewed and landed. `/app/profile` (Avatar with initials fallback, name, email, real Courses Completed/Certificates stats reusing Dashboard's exact derivation, inline name editing via `PATCH /users/me`) and `/app/settings` (Theme Light/Dark/System via the pre-existing, previously-unused `useTheme()` hook; Language English/العربية switching the UI immediately in place via `next-intl` navigation AND persisting to the account, per `product/USER_FLOWS.md` flow 28). Two new reusable components: `Select` (accessible combobox, keyboard nav + typeahead) and `Avatar`. Two real spec-vs-backend gaps disclosed and scoped out (D44): no password-change endpoint exists in the backend; `avatarMediaId` is a dead field nothing ever writes, so no avatar-upload control was built. Theme modeled as a 3-state `Select`, not a 2-state Toggle, to match the already-built `useTheme` state model. Verified live as the seeded student: name edit round-tripped and propagated to the Sidebar top-bar via shared query-cache invalidation; Theme switch re-themed the whole app instantly; Language switch changed URL/direction/strings immediately with no reload and persisted across a fresh reload; RTL mirroring confirmed correct on both screens including the non-mirrored `expand_more`/`expand_less` Select indicator. Independently re-ran every gate; matched Codex's own claims exactly. |
| 11 | Instructor Web: Dashboard, Course Editor (Overview/Curriculum), Lesson Editor, Quiz Editor | DONE — delegated to Codex via `codex-delegate` (D47), Claude reviewed and landed. `/instructor` (Dashboard: stats + owned-course list + Create Course), `/instructor/courses/new` + `/instructor/courses/:id` (Course Editor, Overview/Curriculum as sibling tabs, category/level/content-language Selects, thumbnail FileUpload, Draft/Published Toggle always paired with a visible text label, a publish-readiness checklist driven by the backend's exact per-field validation codes), Lesson Editor, and Quiz Editor (a single atomic `PUT` replace of the whole question set, matching the backend's real replace-not-patch contract). New reusable components: `Toggle`, `FileUpload`, `Tabs`, `ReorderableList` (drag handle + a mandatory always-visible, never-mirrored Move Up/Down non-drag alternative), `AppDialog` + a shared `useUnsavedChanges` guard used by all four authoring screens. `AppShell`'s nav items are now parameterized (Student behavior unchanged by default) so the new `InstructorShell` reuses the same chrome with its own nav list and a client-side role redirect for non-instructors; Instructor Profile/Settings reuse the existing Student screen components (student-only stats disabled), per the product spec calling these "minimal equivalents," not new screens. One brief inaccuracy Codex caught and correctly overrode: every section/lesson mutation actually returns the full `CourseResponse`, not a narrower per-resource shape — see D47. `courses.ts`/`quiz.ts`/`media.ts` extended additively; new `instructor.ts`. Full en/ar key parity (349/349). No backend changes. Independently re-ran every gate (matched Codex's claims exactly) and verified live as the seeded `instructor1@mentora.dev` account in en+ar — Dashboard, both Course Editor tabs, Lesson Editor, and Quiz Editor all against real seeded course/quiz data, including RTL mirroring. |
| 12 | Admin Web: Dashboard, Manage Courses/Users/Instructors/Categories | DONE — built directly against the real, read-mostly-by-design backend contract (no invented endpoint/filter/mutation anywhere). New shared `DataTable` component (design-system/COMPONENTS.md § DataTable v1.2, a D47-deferred gap now closed); `AdminShell` reusing the Task 11 `AppShell`/`navItems` pattern; a genuine showcase mockup for "Admin Course Management" found and incorporated (subtitle/status-filter/info-banner) with two disclosed, deliberate deviations (5-item nav incl. Instructors; read-only Badge not a bidirectional Toggle, since no admin-capable publish endpoint exists). A real CSS regression (row-action popover clipped by the table's `overflow: hidden`) found and fixed live in the browser. 5 new `design-to-code/screens/admin-*.json` specs authored (screen count 24→29, validation clean). Live-verified EN/AR-RTL/Light/Dark. See `DECISIONS_LOG.md` D62. |
| 13 | Localization completion pass (M14 web slice) — full en/ar coverage + RTL QA sweep | DONE — automated audit confirmed 491/491 en/ar key parity, zero empty values, zero hardcoded UI strings (JSX text/aria-label/placeholder/title), zero physical-direction CSS anywhere (`lint:logical-properties`); live RTL regression spot-check of the surfaces most recently touched by D57-D62 (Landing/Explore/Dashboard) found no regression. No code changed — audit-only. See `DECISIONS_LOG.md` D63. |
| 14 | Playwright E2E suite (M15 web portion) | DONE — `web/e2e/` (10 spec files, one per `TESTING_STRATEGY.md § 6` priority flow) + `web/playwright.config.ts` (3 browser projects), run against the real local dev backend/MongoDB, never mocked. Found and fixed a genuine locked-UX-spec violation while building it: `CourseService.get()` blocked an already-enrolled student from an unpublished course entirely, contradicting `ux/INSTRUCTOR_ADMIN_UX.md`'s explicit "enrolled students keep access" rule — fixed (`EnrollmentRepository` injected into `CourseService`), backend gates re-verified (77 tests/17 suites). **Chromium: 19/19 passing. Firefox: 19/19 passing.** WebKit: 1/19 — root-caused to a real, non-product `Secure`/`SameSite` cookie-policy difference over plain-HTTP `localhost` (not a defect in the app's correct cookie security config), thoroughly investigated and disclosed, not faked as passing. See `DECISIONS_LOG.md` D64. |
| 15 | `web/README.md` local run instructions (M16 web portion) | DONE — mirrors `backend/README.md`'s structure (Prerequisites/Install/Run/Quality gates/layout); documents the real `npm run build`-while-`dev`-is-live `.next`-corruption hazard prominently; full "End-to-end tests" section states Chromium/Firefox as verified-passing and WebKit as a disclosed, investigated blocker (not silently glossed over), plus a ready-to-adapt test-data cleanup snippet. See `DECISIONS_LOG.md` D65. |
| 16 | Phase 2 quality gate verification | DONE — from-scratch, Claude-led re-run of every gate (same rigor as Task 22's Phase 1 gate): backend `gradlew clean test --rerun-tasks` (77 tests/17 suites/0 failures, fresh non-cached), `typecheck`/`lint`/`lint:logical-properties`/`validate:design-to-code` all clean, a stopped-dev-server production build (47 routes, both locales, 0 errors), and Playwright re-run on Chromium (19/19 clean) + Firefox (19/19 clean after root-causing one transient flake to a shared-rate-limit/back-to-back-runs testing-environment characteristic, not a product or test defect — see D66). Git tree confirmed clean, every Phase 2 commit present in history. Full known-limitations list compiled for the Phase 2 report. See `DECISIONS_LOG.md` D66. |
| 17 | `PHASE_HANDOFF.md` Phase 2 write-up | DONE — full Phase 2 entry appended to `execution/PHASE_HANDOFF.md` in the file's required fixed structure (status, implementation summary, files/modules, API/contracts, database changes, tests/verification, known limitations, decisions, Phase 3+ dependencies, what not to redo, git reference), matching the same rigor as the Phase 1 entry. Status marked COMPLETE, pending explicit user approval. |

Task 9 note: built as the standalone full-navigation `/app/ai-tutor` screen only — the
`ux/RESPONSIVE_BEHAVIOR.md` § 9 docked-panel-alongside-the-player variant for Course Player was
**not** built (Course Player's "Ask AI Tutor" link still full-navigates away, same forward-reference
behavior as before task 9 existed). Not a defect, just an unbuilt refinement — flag it if a future
task revisits Course Player.

**UI-fidelity correction pass (2026-09-07, D45) — done before Task 11, by explicit user request:**
a cross-cutting visual-fidelity pass across Tasks 1-10 (not a numbered task itself), closing real
gaps against the locked `design-system/COMPONENTS.md` spec found via a live-browser audit: `TextField`
now has the documented floating-label behavior (was a static always-visible label); a new
`PasswordField` component ships the spec-required visibility toggle (Login/Register previously had
none); Login and Register were rebuilt with a real card surface, a minimal logo-only header (was
the full `PublicNavbar`, contradicting `ux/SCREEN_UX_SPECS.md §§ 6-7`), and corrected title copy;
`color.text.link` (previously defined in tokens but consumed nowhere) now backs a new `.mtx-link`
class; `SearchField` uses the real icon system instead of a raw Unicode glyph and has its spec'd
clear button. Zero product behavior, routes, or backend contracts changed. Full detail, the
Select-label regression caught and fixed mid-pass, and the verification performed: see D45.

**Second, stricter UI-fidelity pass (2026-09-07, D46) — done before Task 11, by explicit user
follow-up request** ("still does NOT visually match... not merely like a functional app using the
same purple palette"): fixed a real, high-impact bug — every course thumbnail in the local demo
rendered as a broken-image icon, because `SeedData.kt` uploads placeholder bytes with an
`image/jpeg` content-type that Chrome cannot decode (same category as D41's lesson-video
placeholders, never fixed for course artwork until now). New shared `CourseThumbnail` component
(`web/src/components/ui/course-thumbnail.tsx`) renders a branded fallback (the existing
`myLearning` book icon on `brand.primaryContainer`) at all five places a course thumbnail is
rendered. Also restored `CourseCard`/`LearningPathCard`'s spec'd-but-missing "primary action"
content-hierarchy item (COMPONENTS.md item 7 for CourseCard), and fixed a widespread bug where 7
screens' `ErrorState` retry buttons displayed the full error sentence instead of "Try again" (both
props were accidentally passed the same translation key). Zero product behavior, routes, or
backend contracts changed. Full detail and verification performed: see D46.

**Login/Register design-derived auth-screen refinement (2026-09-07, D51) — done after the D50
post-pipeline visual audit, by explicit user follow-up request** ("rebuild them as DESIGN-DERIVED
screens... not exact-reference"): reworked strictly from `design-system/COMPONENTS.md` §
Inputs/§ Buttons and `ux/SCREEN_UX_SPECS.md` §§ 6-7, no showcase mockup exists for either screen.
Fixed real defects: field-level errors previously rendered the field's own label text instead of
a message; Register's password strength hint (already called for in `register.json`) had no
`PasswordField` support at all; Register's "email already registered" server error rendered as a
generic banner instead of inline under the Email field as this project's own spec already said;
`.mtx-auth-page` didn't vertically center on tall viewports. Both screens' `referenceType`
reclassified `"ux-only"` → `"approved-pattern"` in `design-to-code/screens/{login,register}.json`.
Zero product behavior, routes, backend, or `design-system`/`product`/`ux` document changes. Full
detail and verification performed: see D51.

**Final targeted visual correction pass (2026-09-07, D52) — done after manual side-by-side
review of the D50 audit's comparison images (not just automated scores), by explicit user
request.** Corrected 7 screens: Demo Checkout (regressed to ~75%, rebuilt to include thumbnail/
instructor/itemized row/total per its own already-existing spec), Purchase Success (icon
32→96px, vertical centering), Course Details (curriculum now card-contained sections, semantic
list preserved), AI Tutor (identity header + pill composer + circular send button), Instructor
Dashboard (byline + "+" icon + a `.mtx-instructor-card` styling regression fixed), Course Editor
Overview (Save/Publish moved to a top action bar, real `CourseThumbnail` preview wired into
FileUpload), Course Editor Curriculum (explicit section-card surface color — the same one-line
fix also applied to Quiz Editor, which shares the CSS class). Purchase Success's `shell: "none"`
gap (shared AppShell still renders chrome) deliberately left unresolved and disclosed, not
silently fixed — see D52. The already-locked Instructor Dashboard (3-vs-4 stat cards) and Course
Editor (2-tab vs. showcase's 3-tab/persistent-rail) conflicts from D47/D48/D49 were re-confirmed,
not re-litigated. New external audit: `D:\Work\MentoraFinalVisualAudit\` (zipped to
`MentoraFinalVisualAudit.zip`) — exact-showcase average ≈94.1%, 4 of 7 exact-showcase screens
≥95%, one screen (Course Editor Overview, 88%) below 90% due to the disclosed locked-spec
conflict. Zero product behavior, routes, backend, or `design-system`/`product`/`ux` document
changes. Full detail and verification performed: see D52.

**Primary Light Visual Alignment pass (2026-09-07, D53) — done by explicit user request to make
Light Mode the primary visual validation baseline.** Investigated first rather than assuming a
defect: byte-level comparison of `theme-light.json` against generated `tokens.css` (identical),
a repo-wide grep for hardcoded/generic-gray colors (zero matches), and live verification of all
24 screens in freshly-reset Light Mode — **no CSS/token defect was found**. Login/Register
already share the same page background as every other screen (`.mtx-auth-page` has no
background rule of its own). Likely explanation for the "feels dark" perception: this dev
machine's OS/browser reports `prefers-color-scheme: dark`, and the app correctly defers to
system preference when no explicit override exists (unchanged this pass, per instruction not to
remove system-preference support). Since no CSS fix was needed, the concrete work was extending
`design-to-code/shared/platform-contract.json` with an explicit "MENTORA VISUAL PARITY RULE" plus
Android (Compose ColorScheme/Typography/Shapes) and iOS (SwiftUI Color/Font/shape) mapping
tables — mapping only, no native code written. New external audit:
`D:\Work\MentoraLightVisualAudit\` (zipped to `MentoraLightVisualAudit.zip`) — Light-mode overall
consistency 97%, exact-showcase Light average ≈93.9% (materially unchanged from D52, since no
screen structure changed). Zero product behavior, routes, backend, or
`design-system`/`product`/`ux` document changes. Full detail and verification performed: see D53.

**Student Dashboard Acceptance Criteria — Search + Theme Toggle + Title (2026-09-09, D54) — done
per an explicit user acceptance-criteria ticket, not a Task 12 start.** Investigated first:
neither `design-to-code/screens/dashboard.json` nor the locked `Mentora Showcase.dc.html` Student
Dashboard mockup (light or dark) specs a distinct "Dashboard" title or a theme-toggle control —
the showcase's top row is only an eyebrow + "Welcome back, {name}" greeting (already the page's
h1) next to an inline search box and an avatar. This gap was disclosed to the user before
implementation (not silently resolved); the user made the product call: "Dashboard" becomes the
page h1 (matching the h1-as-screen-name pattern already used by Explore/Settings), the existing
greeting demotes to a secondary line beneath it, and the search bar + a new theme-toggle
`IconButton` sit in the same top-right control area, toggle directly after the search bar in
DOM/logical order (mirrors correctly in RTL). Implementation reused existing pieces throughout:
the approved `SearchField` component (previously only used on Explore), the locked `IconButton`
spec (`design-system/COMPONENTS.md § IconButton`) via the existing `.mtx-icon-button` class (which
was missing its specced `:active`/pressed state — added, benefiting every existing consumer, not
just this control), and the existing `useTheme()` hook/`mentora-theme` localStorage mechanism
(extended, non-breaking, with a `resolvedTheme` field so the toggle can show the icon for the
theme actually in effect, including live system-preference changes when no explicit override is
set — Settings' theme `Select` is unaffected). Two new hand-drawn `Icon` entries (`darkMode`
moon / `lightMode` sun) added following the existing inline-SVG icon pattern; both are
non-directional and were left out of `design-system/design-tokens.json`'s `icon.directional`
lists on purpose (that locked file was not touched) — the documented default for an undeclared
icon is already `neverMirror`, which is correct here. Dashboard's search submits on Enter to
`/app/explore?q=<value>` (the only place Mentora actually filters courses/categories by text);
`ExploreScreen` gained a one-line `useSearchParams()` read to seed its existing search state from
that `?q=`, wrapped in the `Suspense` boundary Next.js requires for it in both places `ExploreScreen`
is mounted — the only screen besides Dashboard this change touched, and only for this reason.
Zero changes to Dashboard stats, Continue Learning, Learning Paths, AI Tutor, Sidebar,
authentication, or any `design-system`/`product`/`ux` document. Full detail and verification
performed: see D54.

**Student Dashboard Full Acceptance Criteria Alignment (2026-09-10, D55) — done per a follow-up
acceptance-criteria ticket extending D54, not a Task 12 start.** Restructured the header: search
bar + theme toggle now sit in their own row above a standalone `Dashboard` h1 (previously the
title/greeting shared a row with search+toggle); search placeholder text changed to the ticket's
exact new string ("Search for courses, skills or anything...") and the field widened
(`tablet:max-w-[440px]`, up from 280px) so it renders unclipped. Replaced the static "Welcome
back, {name}" greeting with a dynamic, client-local-time greeting (`new Date().getHours()`, never
server time) across 4 buckets — morning/afternoon/evening/night — using the authenticated user's
first name (parsed client-side from the existing single `name` field; no backend change) plus a
new supporting subtitle line, both via new i18n keys in `en.json`/`ar.json` (old unused
`dashboard.greeting` key removed, confirmed no other references first). Investigated the 4th stat
card before touching it: the showcase's actual 4th metric is "Learning hours," and a full
backend+frontend sweep (Progress/Enrollment/Lesson/Media models, all DTOs) found **no real
watch-time data anywhere in the system** — fabricating an hours figure was explicitly disallowed
by the ticket, so the existing, real Avg. progress metric was kept in that slot instead and the
gap disclosed here and in `design-to-code/screens/dashboard.json`'s `conflicts[]`, rather than
inventing a number. `design-to-code/screens/dashboard.json` (not a locked source — explicitly
permitted to be kept current per this ticket's own instruction) updated to record the new header
composition, typography hierarchy, and both conflicts; `design-system/product/ux` documents
themselves were not touched. Verified live in Chrome as two seeded students (`student1`/
`student2@mentora.dev`): dynamic greeting with real first name and correct time bucket in both EN
and AR, theme toggle Light↔Dark with refresh persistence in both locales, search (case-insensitive
partial match via "koTLIN", no-result query, empty-Enter no-op) landing on Explore pre-filled, a
real Continue Learning card (thumbnail/progress/Resume) produced by enrolling and partially
completing a course through the actual demo-checkout flow, accessible name for the search field
confirmed via DOM inspection to come from its associated `<label>` (not the placeholder). Narrow-
viewport responsive behavior could **not** be live-verified this session — `resize_window`
returned success but the tab's `window.innerWidth` never changed from 2048px in this environment;
the new header row reuses the same shrinkable-flex-item pattern already relied on elsewhere in the
app, but this is disclosed as unverified rather than claimed. `typecheck`/`lint`/
`lint:logical-properties`/`validate:design-to-code` (24 screens, re-passed after the
`dashboard.json` edit)/production `build` (37 routes) all clean. Full detail and verification
performed: see D55.

**Dashboard Course Card Language correction (2026-09-10, D56) — done per a follow-up
acceptance-criteria ticket, not a Task 12 start.** The English Dashboard's "Recommended for
you" section could surface the single Arabic-only seeded course (`SeedData.kt`'s
"أساسيات تصميم تجربة المستخدم", `contentLanguage: "ar"`) inside the English UI. Investigated
the seed/backend data first per the ticket's instruction: the course model has one title per
course (no bilingual title pair anywhere in the schema), so the course is genuinely Arabic-only
by seed design — the real defect was that `GET /api/v1/courses` had no `contentLanguage` filter
at all, so `DashboardScreen`'s recommended-courses query returned courses regardless of content
language. Fixed at the data-selection layer, end-to-end: `PublishedCourseFilter`/
`CourseRepository.listPublished` gained a `contentLanguage` filter, `CourseListQuery`/
`CourseService.list`/`CourseRoutes` plumbed a new `language` query param through the existing
`validateLanguage()` allow-list, and the frontend `CourseListFilters`/`buildQuery` gained a
matching `language` param; `DashboardScreen` now calls `useCourses({ limit: 8, language: locale })`
instead of the previously unfiltered `useCourses({ limit: 8 })`. No title was hardcoded, no CSS
hid anything — `CourseCard` renders `course.title` unchanged. Added a new backend integration
test asserting `?language=en`/`?language=ar` each return only the matching course; full
`gradlew test` clean, `tsc --noEmit`/`lint` clean. Live-verified in Chrome (backend+website
restarted via `stop-mentora.ps1`/`start-mentora.ps1`, required since this is a compiled Kotlin
change): English Dashboard now shows only English-titled recommended cards, Arabic Dashboard
still correctly shows the Arabic course with instructor/rating/level/price intact. See
`DECISIONS_LOG.md` D56 for the full account.

**Course Localized Metadata — EN/AR (2026-09-10, D57) — done per a follow-up acceptance-criteria
ticket extending D56, not a Task 12 start.** Added first-class per-locale title/description
translations to the course model: `CourseDocument` gained `translations: Map<String,
CourseTranslation> = emptyMap()` (keyed by locale — the entry for the course's own
`contentLanguage` is never stored, `title`/`description` already are that language's text), with
`CourseDocument.resolvedTitle(language)`/`.resolvedDescription(language)` as the single
resolution rule (requested locale's translation if present, else the base text — never blank)
reused everywhere a course renders: `CourseService.list/get`, `EnrollmentService.preview`
(Checkout), `LearningPathService.get`, and `InstructorService.dashboard`, each newly threading an
optional `language` query param down to it — no duplicated resolution logic per endpoint. The
list endpoint's inclusion rule changed from "`contentLanguage` equals the requested locale" to
"equals OR has a translation for it" (`Filters.or(eq(...), exists("translations.$it"))`), so a
translated course is never hidden from its translated locale's catalog/recommendations — while a
single specific course (Course Details, Checkout, My Learning, Learning Path, Instructor's own
course) is never hidden regardless, since only list/catalog contexts filter at all. The seeded
Arabic UX course now carries a real English translation ("User Experience Design Fundamentals" +
an English description) via `SeedData.kt`'s `CourseSeed.translations`, flowing through the real
`CourseService.create`/`update` — not hardcoded in React. `CourseCard`/Course Details gained an
explicit "Course content: {language}" indicator (new `explore.contentLanguageBadge` + `en`/`ar`
key, `CONTENT_LANGUAGE_LABEL_KEYS`) shown only when `contentLanguage !== locale`, so a translated
title is never mistaken for translated lesson content. Mongo's single text index was widened to
cover `translations.{en,ar}.{title,description}` (search matches localized metadata) via a named,
drop-and-recreate-safe index in `CoursesIndexes.kt` — necessary because a differently-specced text
index already existed in this dev database from before this change, and Mongo allows only one per
collection. Backward compatibility: `translations` defaults to `emptyMap()` so a legacy document
with no such field at all still deserializes and resolves to its base title (test-covered by
directly `$unset`-ing the field on a real document, not just relying on the default). Validation
reuses `validateLanguage()` (now field-name-parameterized) for translation locale keys and
`required()` for blank-string rejection — no second validation implementation. Three new backend
integration tests (locale resolution + fallback + list inclusion + search, unsupported-locale/
blank-text rejection, legacy-document compatibility); full `gradlew build` (test+assemble) clean;
frontend `tsc --noEmit`/`lint`/`lint:logical-properties`/`validate:design-to-code`/`build` all
clean; en/ar key parity manually re-verified (395/395). Live-verified in Chrome (backend+website
restarted, required for the compiled Kotlin change, then `gradlew seedDemoData` re-run to apply
the new translation to the already-seeded dev course — required a second fix, `allSeedDataExists`
gained a narrow existence-isn't-enough check for courses whose seed spec carries translations not
yet on the persisted document, since the top-level idempotency gate was short-circuiting before
`seedCourses`'s own update-if-existing branch could run): English Dashboard/Explore/Course Details
all show "User Experience Design Fundamentals" with the Arabic content-language badge and intact
instructor/rating/price; Arabic Dashboard/Course Details show the original Arabic title with no
badge; Dashboard search for "Experience" correctly lands on the translated course via Explore.
Admin course-list localization was deliberately left untouched — no Admin Web UI exists yet to
consume it (Task 12 not started), so a `language` param there would be unverifiable dead code.
See `DECISIONS_LOG.md` D57 for the full account.

**Global Course Metadata Localization — EN/AR (2026-09-10, D58) — done per a follow-up
acceptance-criteria ticket auditing D57's coverage for completeness, not a Task 12 start.**
Re-inspected every frontend call site of `listCourses`/`useCourses`/`getCourse`/`useCourse`
across the whole app (Landing, Explore, Dashboard, Course Details, Course Player, Learning
Paths, My Learning, Checkout, Instructor Dashboard, Course/Lesson Editors) against D57's backend
`language` mechanism — found exactly one gap: `(public)/page.tsx` (Landing/Home)'s server-side
`listCourses({ limit: 4 })` call for both the hero collage and the Popular Courses grid had never
been updated to pass `language: locale`, so it silently still returned raw/base titles while
every other screen resolved them (a real API-consistency gap the ticket asked to inspect for).
Fixed with the same one-line pattern already used everywhere else, plus the same
`contentLanguageLabel` badge already used on Explore/Dashboard, added to the Popular Courses
`CourseCard` grid (the hero collage stays badge-free, matching the already-established compact-
surface precedent — `CourseProgressCard` on My Learning/Continue Learning — since it's a small
thumbnail-strip tile, not a decision point). No backend change was needed at all — D57's single
`resolvedTitle`/`resolvedDescription` resolution function and widened list-inclusion filter
already covered every endpoint correctly; the gap was purely a missed frontend call site, not a
duplicated/inconsistent backend implementation. Confirmed no other surface had the same gap via
an exhaustive grep of every course-fetching call site — Course/Lesson Editor screens correctly
still omit `language` on purpose (an instructor editing their course must see its base text, not
a translation). One incidental operational issue hit and fixed during verification: running
`npm run build` while the restarted `npm run dev` was still live corrupted `.next` again (the
exact D49-documented failure mode, `Cannot find module` on a webpack chunk) — fixed the same way
D49 did, by stopping both processes, deleting `.next`, and restarting `npm run dev` clean; no
further `npm run build` was run afterward while dev was live, to avoid re-corrupting it (the
build that already ran cleanly, pre-corruption, stands as the required build-gate evidence).
`tsc --noEmit`/`lint` both re-confirmed clean after the fix. Live-verified in a fresh Chrome
session exactly per the ticket's checklist — `/en` Landing (hero collage + Popular Courses both
show "User Experience Design Fundamentals" with the Arabic content-language badge and intact
instructor/rating/price), `/en/explore`, `/en/app` Dashboard, `/en/app/courses/:id` Course
Details, `/en/app/paths` — then `/ar` Landing and `/ar/app/courses/:id` Course Details confirmed
the Arabic title/no-badge is unaffected (and that Arabic-locale catalog/hero surfaces correctly
still exclude the three English-only courses that have no Arabic translation, the same inclusion
rule already verified for Explore in D57 — not a new behavior, just newly observed on Landing
too). No new backend tests were added — no backend behavior changed, and D57's existing test
suite already covers the resolution/filter/search logic this fix merely reused; this project has
no frontend component-test harness yet (Playwright E2E is Phase 2 task 14, `NOT_STARTED`), so
live-Chrome verification is the established test method for a frontend-only fix at this phase,
consistent with D54–D57. See `DECISIONS_LOG.md` D58 for the full account.

**Course Player Real Lesson Video — Acceptance Criteria (2026-09-10, D59) — done per a follow-up
ticket, not a Task 12 start.** The Course Player component itself needed zero changes — Task 6
(D41) already built it fully real-data-driven. The actual gap: every seeded lesson video
(including both lessons of `Building Reliable REST APIs`) was a fake placeholder (tiny non-video
bytes uploaded through `MediaService`). Added one new idempotent seed step,
`ensureRestApiLessonVideo` (`SeedData.kt`), that uploads a real, locally-`ffmpeg`-generated,
on-topic MP4 (~27s, 1280×720, H.264, ~1.5MB — a slideshow covering resource design/HTTP
methods/status codes/validation/error handling/idempotency) through the exact same
`MediaService.upload()` → `MediaStorage` path every other seed asset uses, then renames that one
lesson to "REST API Reliability Fundamentals" via the existing `CourseService.updateLesson`,
preserving its original `lessonId`/course identity. The video source lives at
`backend/src/main/resources/seed-media/rest-api-fundamentals.mp4` (a committed seed *fixture*,
distinct from the gitignored `backend/storage/` runtime media root) and is loaded via classpath
resource stream, never a hardcoded filesystem path — the React `<video>` element only ever sees
the existing signed `/media/{id}/stream` URL. `ffmpeg` was installed via `winget` (not previously
present) after an in-browser `MediaRecorder`/WebM approach was tried and diagnosed as blocked by
an unrelated browser-automation environment limit, not a real defect (full account: `ffmpeg`-
encoded MP4s load and play correctly the moment they reach a visible/foregrounded tab; every tab
this session's Chrome tooling controls reports `document.hidden === true`, which stalls `<video>`
resource loading specifically — proven with a known-good external reference video stalling
identically, while plain `fetch()` against the exact same real-video URL, including a ranged
request, returns correct `200`/`206` responses with byte-exact `Content-Length`). Backend/media
pipeline, auth/token flow, and every other checklist item were verified live as the seeded
`student2@mentora.dev`: real course/lesson title and description, real 50%-complete progress and
curriculum state, Previous/Next lesson navigation, Overview/Resources-only tabs, AI Tutor link,
the pre-existing focused-shell sidebar-collapse behavior unregressed, Light/Dark themes, English
and Arabic/RTL (content correctly stays English — no translation exists for this course — while
UI chrome mirrors/translates and the video timeline/volume controls stay LTR per the locked
decision), and a hard refresh on the Arabic/dark state reproducing identical server-authoritative
progress. Gates: backend `gradlew test` 69/69 green; `gradlew seedDemoData` re-run twice confirms
idempotency (`1 real lesson video(s) created` then `All demo seed data already exists`);
`typecheck`/`lint` (same single pre-existing `<img>` warning)/`lint:logical-properties`/`build`
all clean; `design-to-code` validation clean (unchanged, no screen spec touched). No
`design-system`/`product`/`ux`/`architecture` document changed; Task 12 not started. See
`DECISIONS_LOG.md` D59 for the full account, including the diagnosed video-frame-rendering
limitation.

**All Courses Playable Lesson Media — Acceptance Criteria (2026-09-11, D60) — done per a
follow-up ticket generalizing D59 from one course to every seeded course, not a Task 12 start.**
Audited the live dev database directly and found 4 published, enrollable courses (Building
Reliable REST APIs, Practical MongoDB for Application Developers, Kotlin Coroutines in Practice,
أساسيات تصميم تجربة المستخدم) plus 2 draft courses with zero sections/lessons at all — the drafts
are correctly out of scope since they cannot be enrolled in or opened in the Course Player.
D59's one-course mechanism was generalized into a data-driven `LESSON_VIDEO_SEEDS` list + a single
`ensureLessonVideos` seed step covering all 4 published courses; re-running `seedDemoData`
against the already-seeded dev database picked up the 3 new upgrades (REST API's was already
converged from D59) with zero duplicate uploads. Three new topic-relevant real videos generated
the same proven `ffmpeg` way as D59 (MongoDB: documents/CRUD/schema/indexes/queries/aggregation;
Kotlin: suspend functions/scopes/dispatchers/structured concurrency/cancellation; UX Design: a
genuinely **Arabic**-language video — this course's real content language is Arabic, only its
course-level title/description have an English translation, so per the ticket's own "do not fake
that an Arabic-content course is English-content" instruction, the video and lesson
title/description are authentically Arabic, RTL-shaped correctly via `ffmpeg`'s
libfribidi/libharfbuzz + a real Arabic-capable font). Live-verified all three as
`student3@mentora.dev` (enrolled, 0% progress): real titles/descriptions/curriculum, working
navigation, the pre-existing focused-shell sidebar-collapse unregressed, and for each course a
direct `fetch()` against the resolved stream URL confirmed `200`/byte-exact `Content-Length` and a
ranged `fetch()` confirmed correct `206`/`Content-Range` — the same evidence class D59 established
for the backend/proxy/token layer, now proven per-course. The Arabic course was additionally
verified in `/ar/...` (original Arabic title, full RTL mirroring, authentic Arabic content) and
Light/Dark + hard refresh were re-confirmed. New tests added per the ticket's testing
requirement: `SeedDataLessonVideosTest.kt` (4 fast, DB-free structural tests — every published
course has exactly one video-seed entry and vice versa, sections resolve, bundled resources exist
and are real-sized, titles aren't the generic placeholder) and a new
`MediaIntegrationTest` case covering byte-range/seeking support end-to-end (a genuine
previously-uncovered gap). Gates: backend `gradlew test` 74/74 green (up from 69, 16 suites, up
from 15); `gradlew seedDemoData` re-run twice confirms idempotency; `typecheck`/`lint` (same
single pre-existing `<img>` warning)/`lint:logical-properties`/`build` all clean; `design-to-code`
validation clean (unchanged). No `design-system`/`product`/`ux`/`architecture` document changed;
the 2 draft courses were deliberately left untouched (no fake curriculum invented for them); Task
12 not started. See `DECISIONS_LOG.md` D60 for the full account.

**Course Artwork Identity — Acceptance Criteria (2026-09-11, D61) — done per a follow-up ticket, not
a Task 12 start.** Every course's artwork looked like the same generic purple visual because the
governed 5-motif fallback system (`design-to-code/shared/artwork.json`) assigns per **category**,
and 2 of the 4 real published courses (Building Reliable REST APIs, Kotlin Coroutines in Practice)
share category "Software Development" — plus every seeded `courseThumbnail` was still the original
fake placeholder (never touched by D59/D60, which only fixed `lessonVideo`), so `CourseThumbnail`'s
`<img>` path always silently failed and the category-collided fallback is what every user actually
saw. Fixed without touching the fallback system, `CourseThumbnail`, or any of the 8 consuming
screens/components: a new `ensureCourseArtwork` seed step (same idempotent pattern as D60's
`ensureLessonVideos`, converging on the current thumbnail's stored byte size) uploads one real,
topic-specific JPEG per targeted course through the existing `MediaService` `courseThumbnail`
pipeline and sets `thumbnailMediaId` — which every screen already reads into `CourseThumbnail`'s
`mediaId` prop. The 4 images (REST API: network/API hub topology; MongoDB: stacked documents;
Kotlin: parallel flow streams; UX Design: a wireframe/mockup screen) keep the exact governed
composition recipe (dark purple/indigo/violet gradient + light-source highlight + one centered
white icon, no embedded text) so they read as the same Mentora family, generated via Chrome Canvas
2D (no stock art, no network dependency) and committed under `backend/src/main/resources/seed-media/`.
`design-to-code/shared/artwork.json` gained one additive `realCourseArtwork` section documenting the
mechanism and root cause; the governed motif system itself is unmodified and still governs every
other/future/draft course. Live-verified across Landing, Explore, Course Details, Dashboard,
My Learning, Course Player, and Learning Path Details, in both EN/AR and Light/Dark, with no
cross-course mixups and no regression to the D59/D60 lesson-video/progress architecture. New
`SeedDataCourseArtworkTest.kt` (3 DB-free structural tests). Gates: backend `gradlew test` all green
(17/17 suites); `gradlew seedDemoData` re-run twice confirms idempotency (`4 real course artwork(s)
created` then `no changes made`); `typecheck`/`lint` (same single pre-existing, unrelated `<img>`
warning)/`lint:logical-properties` all clean — **zero frontend source files were modified**, since
the existing `mediaId`-first rendering path already did everything needed; `design-to-code`
validation clean. No `design-system`/`product`/`ux`/`architecture` document changed; Task 12 not
started. See `DECISIONS_LOG.md` D61 for the full account.

**Task 12: Admin Web — Dashboard, Manage Courses/Users/Instructors/Categories (2026-09-11, D62) — DONE.**
Read the real backend source before implementing anything (same discipline as every prior task):
`backend/src/main/kotlin/com/mentora/backend/admin/{service/AdminService.kt,routes/AdminRoutes.kt,
repository/AdminRepository.kt}` for the four read-only admin aggregation/list endpoints (`GET
/api/v1/admin/{dashboard,courses,users,instructors}` — confirmed no instructor/status filter
param exists on any of them), `courses/routes/CourseRoutes.kt` (confirmed Admin's only course
mutation is `unpublish` — no admin-capable publish, no delete), `users/routes/UserRoutes.kt`
(confirmed zero admin user-mutation routes), and the already-built `categories` CRUD (full
create/update/delete, server-generated slug, `CATEGORY_IN_USE` 409) which Manage Categories
reuses. New shared `DataTable` component built (`web/src/components/ui/data-table.tsx`,
`design-system/COMPONENTS.md § DataTable v1.2 in full — responsive table/card switch, row-action
popover, skeleton/empty/error states, pagination), closing the D47-deferred gap; a genuine
spec conflict between `COMPONENTS.md` (collapses below `desktop`) and `design-tokens.json`
(collapses below `tablet`) was found and resolved in favor of the more detailed component-specific
prose, disclosed non-blocking. `AdminShell` reuses Task 11's `AppShell`/`navItems` pattern exactly
(`shared/navigation.json#/shells/adminWeb` → codegen → `adminNavItems`). A genuine, previously-
unconsulted exact showcase mockup for "Admin Course Management" (`Mentora Showcase.dc.html` § 16,
~lines 2896-3099) was found via a full grep sweep and incorporated into Manage Courses (course-
count subtitle, Status filter, info banner — copy taken verbatim from the mockup), with two
deliberate, disclosed deviations kept in favor of locked Product/UX and real backend-capability
truthfulness: the 5-item sidebar (incl. Instructors) over the mockup's 4-item version, and a
read-only status Badge + one-way Unpublish action over the mockup's bidirectional Toggle (no
admin-capable publish endpoint exists). The same grep confirmed no dedicated mockup exists for
Dashboard/Users/Instructors/Categories, and incidentally corroborated the no-approval-workflow
design via the showcase's own "POST-MVP / deliberately absent" chip list. A real CSS regression
(the table's `overflow: hidden` clipping the row-action popover near the table edge) was found and
fixed live in the browser. 5 new `design-to-code/screens/admin-{dashboard,courses,users,
instructors,categories}.json` specs authored (screen count 24→29; `validate.js`/`generate.js` both
clean). Live-verified as the seeded `admin@mentora.dev`: Dashboard, all 4 management screens,
functional status-filter/search/pagination/row-actions, EN, AR/RTL (full mirroring incl. the fixed
popover), and Dark mode. `typecheck`/`node tools/design-to-code/validate.js` both clean. See
`DECISIONS_LOG.md` D62 for the full account, including both disclosed conflicts.

**Task 16: Phase 2 quality gate verification (2026-09-11, D66) — DONE.** A from-scratch, Claude-led
re-run of every gate, not a reuse of any prior task's self-report (same rigor as Task 22's Phase 1
gate): backend `gradlew.bat clean test --rerun-tasks` (77 tests/17 suites/0 failures, fresh
non-cached — matches D64's last count exactly, no regression); `typecheck`/`lint`/
`lint:logical-properties`/`validate:design-to-code` all clean; a stopped-dev-server production
`build` (47 routes, both locales, 0 errors); Playwright re-run on Chromium (19/19 clean first try)
and Firefox (one transient single-test timeout on the first full run, root-caused rather than
retried blindly — the failing spec passed in isolation in 4.1s, backend response time for the exact
query was re-verified via `curl` at ~3ms, and CPU load was 8%, ruling out an infrastructure cause;
attributed to the suite's shared real auth rate-limit bucket plus this session stacking several
full-suite runs back-to-back against the same live backend, exactly the variance `helpers.ts`
already documents; a clean, non-stacked re-run then passed 19/19). Test data accumulated during
this verification's own reruns (106 accounts/12 courses) cleaned up via the same D64 script.
`git status`/`git log` confirmed a clean tree with every Phase 2 task's commit present, in order.
Compiled the full known-limitations list for the Phase 2 report (WebKit E2E blocker, lesson-2
seed-video placeholders, the mobile-drawer code-review-only limitation, the DataTable breakpoint
spec conflict, the Course Editor/Instructor Dashboard showcase-vs-locked-spec conflicts, the Admin
Course Management showcase deviations, the no-password-change/dead-avatarMediaId gaps, the AI Tutor
docked-panel-variant gap, the Purchase Success `shell:"none"` gap, the Phase 1 concurrency edge
case — each already individually documented at the decision that found it; see D66 for the full
list). See `DECISIONS_LOG.md` D66 for the complete account.

## PRE-PHASE-3: Realistic Course Seed Data Expansion (2026-09-11, D67) — DONE

**Not a Phase 3 task and not renumbered into the Phase 2 task list** — a standalone content/data
completion pass requested explicitly as "PRE-PHASE-3," done entirely inside the locked
Product/UX/Design System/Architecture/Acceptance Criteria, touching only `backend/src/main/kotlin/
com/mentora/backend/SeedData.kt`, its two structural tests, and three `web/e2e/` specs whose
assertions hardcoded the old 2-lesson curriculum. No `product`/`ux`/`design-system`/`architecture`
document was modified; no Course Player/Course Details/Dashboard/My Learning component code changed.

Each of the 4 published seed courses (`Building Reliable REST APIs`, `Practical MongoDB for
Application Developers`, `Kotlin Coroutines in Practice`, `أساسيات تصميم تجربة المستخدم`) was expanded
from its old 2-section/2-lesson generic-placeholder curriculum (`"Core concepts"`/`"Applied
workshop"` lesson titles, repeated verbatim across every course; a `"Reliability and Evolution"`
section holding nothing but the `"Applied workshop"` placeholder) to a realistic 3-section/12-lesson
curriculum with real, topic-specific, non-copy-pasted lesson titles and descriptions — see D67 for
the full per-course breakdown. Every one of the resulting 48 lessons (across all 4 courses) now
uploads a real, small, browser-playable demo video through the exact same `MediaService`/
`MediaStorage` pipeline D59/D60 established (reusing that course's existing real seed-media MP4 —
topic-relevant, not per-lesson unique footage, disclosed as such in `SeedData.kt`'s own doc
comments) — **the lesson-2 placeholder-video gap D59/D60/D64 disclosed is now fully resolved**: no
seeded lesson anywhere opens the Course Player into missing/broken media. A new `ensureCurriculum`
seed step converges an already-seeded dev database's stale curriculum deterministically (delete old
sections, rebuild from the current spec) and clears that course's now-stale `progress` rows,
verified idempotent by re-running `seedDemoData` twice against this session's live dev database (`4
course curriculum upgrade(s)` the first run, `All demo seed data already exists` the second).

Live-verified in a real browser as `student2@mentora.dev`: Course Details curriculum block (3
sections/12 lessons, no clipping/overflow), Course Player curriculum panel/lesson switching/
Previous-Next (first-lesson Previous disabled, last-lesson Next disabled, real title/description/
video/Resources swap correctly on every click), per-lesson progress (`Mark Complete` → exactly `8%`
= 1/12, isolated to that one lesson), Dashboard (`2% complete` avg. across 4 courses, real "Up Next"
lesson name), My Learning, Light/Dark, and the Arabic UX course in `/ar` RTL (mirrored layout, real
Arabic section/lesson titles, curriculum panel correctly on the mirrored side). A direct `fetch()`
against the resolved `<video>` `src` confirmed a real `206`/`video/mp4`/correct `Content-Range`
response end-to-end (same evidence class as D59/D60), since this automation environment's
previously-diagnosed `document.hidden` video-stall limitation (D59) still prevents visible on-screen
frame rendering here.

**Gates:** backend `gradlew compileKotlin/compileTestKotlin` clean; `gradlew test` — **78 tests/17
suites/0 failures** (up from 77/17 — `SeedDataCurriculumTest` replaces the now-obsolete
`SeedDataLessonVideosTest`, net +1 test); frontend `typecheck`/`lint` (same single pre-existing
`<img>` warning)/`lint:logical-properties`/`node tools/design-to-code/validate.js` (29 screens/6
patterns/11 shared files, unchanged) all clean; a stopped-dev-server production `build` (47 routes,
both locales, 0 errors). Playwright: every test passes when run in isolation or with reduced worker
concurrency; a full default (16-worker, matching this machine's core count) local run shows a
different, non-reproducing subset of transient timeouts each time — the same pre-existing
shared-account/rate-limit local-parallelism flakiness D64/D66 already documented, now more
pronounced because every lesson (not just one per course) legitimately fetches a real ~1.5 MB video,
multiplying concurrent local I/O across 16 workers. Not a functional regression: every failing test
was independently re-run standalone and passed. E2E test-data debris this session's own repeated
verification runs created (75 `@e2e.mentora.test` accounts, 11 `E2E`-titled courses, and their
enrollments/progress/certificates/media) was cleaned up via a scratch `mongosh` script targeting
exactly that pattern — pre-existing non-seed accounts (`heshamohamed94@gmail.com`,
`phase2tester@example.com`, `postman.student.*`) were left untouched.

**Known limitation from this pass, since superseded by D68 below:** ~~reused-per-lesson demo video~~
— every course's 12 lessons shared one clip. D68 (same day) replaced this with a genuinely unique
video per lesson; see D68 for the current state.

See `DECISIONS_LOG.md` D67 for the complete account, including the full per-course curriculum
listing. `PHASE_HANDOFF.md`'s known-limitations list and its "what later phases must not redo"
section item about the lesson-2 placeholder gap have been updated to reflect this resolution.

## PRE-PHASE-3: Unique Lesson Demo Videos + Realistic Seed Content (2026-09-11, D68) — DONE

**A same-day follow-up to D67 above**, requested explicitly as a further PRE-PHASE-3 content/media
polish pass: D67 gave every lesson real, topic-specific titles/descriptions but every lesson within
a course still shared that one course's single real demo clip (disclosed as a known limitation at
the time). This pass generates one small, unique, topic-referencing DEMO video per lesson — 48
total, all distinct — and wires each into its own lesson non-destructively. Touches only
`backend/src/main/kotlin/com/mentora/backend/SeedData.kt`, `SeedDataCurriculumTest.kt`, and adds
`tools/seed-media/generate-lesson-videos.js` (a new one-time/rerunnable asset generator, same role
as `tools/token-pipeline`/`tools/design-to-code`) plus 48 committed `.mp4` files under
`backend/src/main/resources/seed-media/lessons/<course>/`. No `product`/`ux`/`design-system`/
`architecture` document changed; no Course Player/Course Details component code changed — this is
data/media-only.

**1. 48 unique, small (~25–46 KB each, 1.7 MB total), real, browser-playable clips generated via
`ffmpeg`** (same offline `drawtext`/`drawbox`/`fade` technique D59/D60/D67 established — no stock
footage, no network dependency at runtime): a 640×360, ~19–27s slideshow per lesson showing that
course's identity color (REST API blue, MongoDB green, Kotlin violet, UX rose), the course name, the
lesson's own real title, that lesson's own 4 topic-specific keywords cycling in (e.g. HTTP Methods →
`GET`/`POST`/`PUT`/`DELETE`; MongoDB Indexes → `FASTER READS`/`SLOWER WRITES`/`B-TREE`/`QUERY PLAN`),
and a small persistent on-screen `"DEMO PREVIEW — not real footage"` watermark (Arabic
`"معاينة تجريبية"` for the UX course) burned directly into the video itself — truthful even if the raw
file were ever viewed outside the app. The Arabic UX course's 12 clips use real RTL-shaped Arabic
text (`fribidi`/`harfbuzz`, Tahoma, right-aligned), same proven method as D60.

**2. `LessonSeed` gained `videoResource`/`videoDurationSeconds` (moved down from the course level);
`addLesson` now uploads each lesson's own unique resource.** `durationSeconds` is each clip's own
real ffprobe-measured duration (19, 23, or 27s, cycling per lesson index — deterministic variety,
never fabricated), resolving D67's disclosed duration-truthfulness tension without contradiction:
every lesson's video really is that lesson's own distinct file, so its real duration is legitimately
lesson-specific now, not a shared course-level value.

**3. New non-destructive convergence step, `ensureUniqueLessonVideos`.** Unlike D67's
`ensureCurriculum` (which deletes and rebuilds sections/lessons wholesale), this only ever swaps a
lesson's `videoMediaId` when its current media's byte size doesn't match its own expected resource's
real size — section/lesson ids and any real per-lesson `progress` survive untouched. Re-running
`seedDemoData` against this session's already-D67-seeded dev database (still on the old
one-video-per-course design) reported `48 unique lesson video upgrade(s)`, `0 course curriculum
upgrade(s)` — confirmed via direct `mongosh` inspection that `student2@mentora.dev`'s prior 8%
REST API progress (from D67's own live verification) survived byte-for-byte across the upgrade. An
immediate second `seedDemoData` run reported `All demo seed data already exists; no changes made` —
idempotent.

**4. Uniqueness proven three ways, not just asserted.** (a) A new DB-free test,
`every lesson has its own unique video resource — no two lessons share the same clip`, asserts all
48 `LessonSeed.videoResource` paths are distinct strings. (b) Direct `mongosh` aggregation over the
live dev database: 48 lesson-media relationships, 48 unique `ObjectId`s, **48 unique `sizeBytes`
values** (i.e., genuinely different binary content, not just different records pointing at
identical bytes). (c) Live browser spot-check, 3 lessons × 4 courses = 12 total: for each, fetched
the resolved signed `<video>` `src` and confirmed its `Content-Length` byte-exact-matches that
lesson's own file in the generator's manifest (e.g. REST API "Authentication and Authorization
Concepts" → `31943` bytes; MongoDB "Practical Application Patterns" → `46257`; Kotlin "Flow
Fundamentals" → `27208`; UX "تسليم تصميم تجربة المستخدم" (Handoff) → `38561`) — proving the full
seed → Mongo → API → signed-URL → real-HTTP-stream path end to end for a real sample, not just the
aggregate count.

**5. Gates:** backend `compileKotlin`/`compileTestKotlin` clean; `gradlew test` — **79 tests/17
suites/0 failures** (78→79, net +1 new uniqueness test). Frontend: `typecheck`/`lint` (same single
pre-existing `<img>` warning)/`lint:logical-properties`/`node tools/design-to-code/validate.js` (29
screens/6 patterns/11 shared files, unchanged) all clean; a stopped-dev-server production `build`
clean (47 routes, both locales, 0 errors). Playwright/Chromium: `04-start-resume-course`/
`05-complete-lesson` (the two specs most directly touching per-lesson video/progress) both green;
`06`/`07` show the same pre-existing shared-account contention flake under parallel workers D64/D66/
D67 already documented, confirmed passing standalone; `08-language-switch` showed an unrelated,
pre-existing locator ambiguity (`getByText("الإعدادات")` matches both the Settings page's `<h1>` and
its own sidebar nav item — visible in the failure screenshot, which shows the Arabic RTL page
rendering completely correctly) — not caused by this pass (no Settings/translation/navigation code
touched) and out of this pass's scope to fix. E2E test-data debris from this session's own repeated
verification runs was cleaned up via the same scratch `mongosh` script D67 used.

**Impact:** All 48 seeded lessons across the 4 published courses now each have their own genuinely
distinct, small, playable DEMO video — not just distinct titles over identical footage. Every
lesson switch in the Course Player now loads real, different media, verified byte-exact against the
generator's own manifest. The generator (`tools/seed-media/generate-lesson-videos.js`) is
committed and rerunnable for any future course/lesson addition. No `product`/`ux`/`design-system`/
`architecture` document changed; Phase 3 was not started.

## PHASE 3 — KMP Shared Mobile Core (started 2026-09-12, completed 2026-09-12) — COMPLETE

**Standing Phase Execution Policy applies unchanged**: continue task-by-task automatically, verify
+ commit each task as its own checkpoint, update this section after every task, run the full
completion gate at the end, update all four continuity docs with an implementation-ready handoff,
then STOP for explicit user approval before Phase 4 (Android). Scope, module structure, and the
full 17-task sequence with acceptance criteria were derived by the `architect` subagent from
`architecture/KMP_ARCHITECTURE.md`, `architecture/adr/ADR-002-kmp-sharing-boundary.md`, the other
locked architecture/product/ux docs, `execution/INTEGRATION_CONTRACT.md`, and the actual backend
route source (not docs alone) — see `DECISIONS_LOG.md` D69 for the full plan summary, the toolchain
findings, and how each flagged conflict (CSRF header value, `429` envelope gap, `?language=`
semantics, absent lesson-duration field, unpaginated `categories`/`learning-paths`) was resolved
without any backend change, per the standing "Phase 3 makes zero backend changes" rule. **No
Android/iOS UI, wishlist/favorites, notifications, real payments, or real AI provider integration
is in scope** — confirmed against `product/MVP_SCOPE.md`.

**Known constraint, disclosed now rather than at Phase 5 kickoff:** this machine is Windows —
Kotlin/Native iOS targets and the SKIE Gradle plugin both require macOS. `iosMain` `actual` source
will be written per the architecture's `expect`/`actual` boundary but **cannot be compiled or
verified on this machine**; SKIE is applied host-guarded (macOS only). Phase 5 (iOS) will need a
Mac regardless of anything done in Phase 3 — this is an environmental fact, not a Phase 3 defect.

**Local toolchain confirmed before Task 1:** JDK 21 (Temurin), Gradle 8.11 (same wrapper version as
`backend/`), Android SDK present at `%LOCALAPPDATA%\Android\Sdk` with platform `android-36` and
`build-tools 36.0.0` installed (no `android-35`) — `compileSdk = 36` is used; `ANDROID_HOME` is not
set in the shell environment, so `mobile/local.properties` (gitignored) pins `sdk.dir` explicitly.

### PHASE 3 — Task Breakdown

| # | Task | Status |
|---|---|---|
| 1 | `mobile/` Gradle root + `:shared` KMP module scaffold | DONE — commit `5c2e739`. New independent Gradle KMP project (`androidTarget`/`iosArm64`/`iosSimulatorArm64`, full package layout, version catalog aligned to `backend/`'s Ktor/serialization/datetime/coroutines/Koin versions). AGP 8.9.2 + Kotlin 2.0.21 (Gradle wrapper bumped to 8.11.1, AGP's minimum). `iosMain` source set exists on disk but isn't wired into `shared/build.gradle.kts` — Kotlin never creates that source-set object when iOS targets are disabled via `kotlin.native.ignoreDisabledTargets=true` on this Windows host (limitation B1). `:shared:assembleDebug`/`:shared:testDebugUnitTest` verified green independently. |
| 2 | Wire contract primitives: envelope, error taxonomy, `ApiResult`, `CursorPage` | DONE — commit `3078565`. `ApiSuccess`/`ApiError` mirror `backend/common/ApiResponse.kt` byte-exactly; 22-code `ApiErrorCode` grep-verified against every real `ApiException` call site + `Unknown(raw)` fallback; sealed `ApiResult<T>`; `CursorPage<T>`; shared `Json` matching backend's `explicitNulls=false`. Review caught and fixed a real bug in the 429-synthesis path matcher (`startsWith("/auth/")` never matches the real `/api/v1/auth/...` route shape — changed to `contains(...)`, regression test added). 14/14 tests green. |
| 3 | Environment config + Ktor `HttpClient` factory + `ApiClient` | DONE — commit `ef07bb2`. `ApiEnvironment` (androidEmulator/iosSimulator/lan/custom presets); `HttpClientFactory` installs `ContentNegotiation`(Task 2's `MentoraJson`)/bounded GET-only retry/redacting header-only debug logging/mandatory CSRF header/deliberately no `HttpCookies` (documented why); `ApiClient` exposes `get/post/patch/delete/getPage` → `ApiResult<T>` only, never a raw `HttpResponse`. OkHttp wired for Android; Darwin `actual` written but unwired in the build script (same iOS limitation as T1/T2). 26/26 tests green, independently re-verified. |
| 4 | `TokenStorage` / `PreferenceStore` `expect`/`actual` boundary | DONE — commit `c6f63f1`, decisions in D70. `TokenStorage` (secrets) and `PreferenceStore` (non-secret locale/theme) as plain common interfaces (not literal `expect`/`actual`, since Android needs a `Context` and iOS doesn't — D70). Android: AES-256-GCM under an Android-Keystore-resident key + Jetpack DataStore (not `EncryptedSharedPreferences`, years-stalled alpha — D70). `resolveInitialLocale` pure function. 41/41 tests green, independently re-verified line-by-line (crypto correctness checked). |
| 5 | `SessionManager`, auth plugin (401→refresh→retry), auth repository + use cases + validation | DONE — commit `de83eff`, decisions in D71. Mutex-based single-flight refresh (verified with 9 concurrent-coroutine test runs, zero flakes); `/auth/refresh`/`/auth/logout` exempt from recursion; `EmailValidator`/`PasswordValidator` verified byte-exact against `AuthService.kt`. Review caught and fixed a real issue: cold-start restore was fabricating a placeholder `SessionUser` (empty id/email/name) inside `AuthState.Authenticated` — now `user` is nullable and honestly `null` until Task 6's profile fetch. 64/64 tests green, independently re-verified line-by-line (highest-consequence task in the phase). |
| 6 | User profile, preferences & locale sync | DONE — commit `5338f7f`, decisions in D72. Full `User` model (`GET /users/me`) distinct from `SessionUser`; `PATCH /users/me` partial-update, verified against `UserService.kt`'s exact 120-char/en-ar rules. `SessionManager.updateUser` closes D71's null-user restore gap. Review caught and fixed a real gap: the login-overwrites-local/register-seeds-account locale precedence was built+tested but never wired into any real login/register call — now wired into `LoginUseCase`/`RegisterUseCase` directly, with a best-effort failure policy on the register side. 88/88 tests green, independently re-verified. |
| 7 | Catalog domain: categories, courses, curriculum, search/filter/pagination | DONE — commit `439e99f`, decisions in D73. `Category`/`Course`/`CourseSummary`/`Section`/`Lesson` verified field-exact against `CourseService.kt` (no duration/isEnrolled/slug). `CourseLevel`/`ContentLanguage` `@Serializable` enums with explicit `@SerialName` per entry (wire values are lowercase, Kotlin names aren't). New shared `localeQueryParam()` helper in `data/network/` for Tasks 8/12 to reuse — **do not re-derive `?language=` threading in those tasks, call this function**. `?language=`'s dual metadata-resolver/result-filter role on the course list verified character-for-character against `CourseRepository.kt`. No defects found this pass. 113/113 tests green, independently re-verified. |
| 8 | Enrollment & demo checkout | DONE — commit `4163643`, decisions in D74. `CheckoutPreview`/`Enrollment`/`EnrollmentCompletion` verified field-exact against `EnrollmentService.kt`; both 201-first-time and 200-repeat checkout/complete map to `Success` with correct `alreadyEnrolled`. `GetMyLearningUseCase` reuses Task 7's `CatalogRepository`+`localeQueryParam()` per D73; fails fast on the first per-course composition error rather than silently dropping a row (D74 — Phase 4/5 UI must handle this). Zero payment vocabulary, grep-verified independently. 131/131 tests green, no defects found this pass. |
| 9 | Progress (lesson/course, resume, mark-complete) | DONE — commit `d8d10aa`. `CourseProgress` verified field-exact against `ProgressService.kt`. `CurriculumLessonResolver` (internal) is the single shared helper both `ResumeCourseUseCase` and `CompleteLessonUseCase` use for curriculum-order resolution — handles zero-progress/valid-resume/stale-currentLessonId/all-complete correctly, crosses section boundaries transparently. `ReportPlaybackPositionUseCase` throttles via an injected `TimeSource` (1 heartbeat/5s, never retried/queued). No defects found this pass. 155/155 tests green, independently re-verified. |
| 10 | Quiz (load/submit/result) | DONE — commit `4cb3a44`. `QuizOption` verified structurally absent of `isCorrect`, confirmed as a genuinely distinct backend type (`StudentQuizOption` vs. instructor-only `EditorQuizOption`). `score`/`passed` server-computed only (`PASS_PERCENT=70`), forwarded verbatim. "No quiz"/"no attempt yet" modeled as legitimate sealed lookup-result states, not generic failures. `SubmitQuizUseCase` validates completeness locally with zero network calls on an incomplete submission. No defects found this pass. 172/172 tests green, independently re-verified against real backend source. |
| 11 | Certificates | DONE — commit `6a7c82b`. `CertificateSummary`/`CertificateDetail` verified field-exact against `CertificateService.kt`. `{id}` is the public `MTR-...` code, treated as a fully opaque string (verbatim URL interpolation, no reformatting). Confirmed via grep: zero mutating certificate routes exist — issuance is exclusively a server-triggered side effect from progress/quiz completion, no issuance use case built. Snapshot fields confirmed frozen-at-issuance, never re-resolved. No defects found this pass. 179/179 tests green, independently re-verified against real backend source. |
| 12 | Learning Paths | DONE — commit `c92dee5`. `LearningPath`/`LearningPathDetail`/`LearningPathCourse` verified field-exact against `LearningPathService.kt`. List is genuinely unauthenticated + unpaginated (plain `get<List<T>>`, not `getPage`); `isFollowing` structurally absent from the list item. Detail uses optional auth (guest-safe: `progressPercent` null, `isFollowing` false for guests); reused Task 7's `localeQueryParam()`, completing both tasks it was built for (D73). No defensive re-sort on path courses (backend's curated order is already authoritative, unlike Task 7's sections/lessons). Follow/unfollow both idempotent by construction. No defects found this pass. 198/198 tests green, independently re-verified. **This completes all Phase 3 pure-data catalog/domain tasks (7-12).** |
| 13 | Media / lesson-video playback contract + platform playback interface | DONE — commit `9a4ac75`. `PlaybackSource(url, expiresAt)` verified field-exact against `MediaService.kt`'s `PlaybackUrlResponse`; TTL confirmed exactly 5 minutes (`PLAYBACK_TTL_MINUTES`). New `ApiEnvironment.resolveUrl()` (slash-safe relative→absolute resolver) shared by `GetLessonPlaybackSourceUseCase`/`RefreshPlaybackUrlUseCase`. `/media/{id}/file` confirmed to 404-by-design for `lessonVideo`-kind media; `ResolveThumbnailUrlUseCase` is structurally incapable of routing a lesson video through it (zero `MediaRepository` dependency, thumbnail-id-typed param only). `LessonPlaybackController` is genuinely interface-only in `commonMain` (`prepare`/`play`/`pause`/`seekTo`/`currentPosition`/`duration`/`state`) — zero real implementation, only a clearly-labeled shape-verification test double; ExoPlayer/AVPlayer remain entirely Phase 4/5's job. `RefreshPlaybackUrlUseCase`'s near/past-expiry check is driven by an injected `Clock`, deterministically testable. First task to add `kotlinx-datetime` as a real `commonMain` dependency (genuine `Instant` arithmetic, not just opaque-string display). No defects found this pass. 218/218 tests green, independently re-verified against real backend source. |
| 14 | AI Tutor (paged conversation + streaming send + quick actions) | DONE — commit `c87d631`, decisions in D75. `AiConversation({conversationId, messages[], nextCursor?})` verified field-exact against `AiTutorService.kt`'s `AiConversationResponse` — its own type, not `CursorPage<T>` (would silently drop `conversationId`). `sendMessage()` genuinely bypasses `ApiClient` (D-E) via a directly-injected `HttpClient`; pre-stream 403/404/429 failures decoded before any byte of the body is touched vs. mid-stream failures (connection drop) preserving already-streamed partial text as a distinct `AiStreamResult.StreamFailed` — the clean-EOF-vs-failure distinction required an empirically-verified Ktor 3.0.1 `ByteChannel` behavior (`closedCause`, not a thrown exception). Hand-written `Utf8ChunkDecoder` holds back trailing incomplete multi-byte UTF-8 sequences across reads (bilingual EN/AR text). `AiQuickAction` (exactly 5 values) carries zero localized prompt text (D-D honored); `SendAiTutorMessageUseCase`'s zero-`QuizRepository`-dependency architecture boundary is enforced by a real JVM-reflection test in `androidUnitTest` (D-B). No AI provider SDK/key/model name anywhere in `shared`. No defects found this pass. 243/243 tests green, independently re-verified against real backend source with a from-scratch (non-cached) rerun. |
| 15 | Public façade, Koin wiring, iOS framework export + SKIE (host-guarded) | DONE — commit `421b9bc`, decisions in D76. `initKoin(environment, platformModule, enableNetworkLogging)` is the single DI entry point (non-global `koinApplication{}`), wiring all 10 domains from Tasks 5-14 plus the interdependent httpClient/apiClient/SessionManager networking bundle. `SessionManager` verified `single`; every repository `single`; every use case `factory`. All 10 repository `Impl` classes made `internal` — verified this compiles with zero changes needed in any existing `commonTest`. `MentoraSdk` is the one public façade (10 per-domain facade properties over already-built use cases) — verified by direct code read to never reference `ApiClient`/`HttpClient`/any repository `Impl`. A real file-content-scan `androidUnitTest` proves `commonMain` has zero Compose/`android.*`/SwiftUI-equivalent imports; `InitKoinTest` resolves all ~35 use cases from a real graph plus asserts singleton/factory behavior directly. iOS `binaries.framework{}` (export `kotlinx-datetime`) configures cleanly on Windows; SKIE's plugin `apply()` call verified genuinely host-guarded (`if (isMacOs)`), not just commented as such. No defects found this pass; no earlier-task wiring gaps found. 248/248 tests green, independently re-verified via a from-scratch `clean` build + `--rerun`. **This completes all of Tasks 1-15 — every domain is now built AND wired into a consumable public SDK.** |
| 16 | Live integration verification against the running local backend | DONE — commit `2dca5f9`, decisions in D77. `LiveBackendIntegrationTest` drives the real `MentoraSdk` façade through the full student journey against the actual local backend + seeded MongoDB (register→logout→login→categories→search→course detail (3 sections/12 lessons, verified live)→checkout preview→demo checkout (first + idempotent repeat)→progress→all 12 lessons completed→playback url→quiz (auto-resubmits with server-revealed correct answers if needed)→certificate (verified live that issuance requires BOTH lessons-complete AND quiz-passed for a course with a quiz)→learning path follow→AI Tutor stream→locale PATCH with a real `?language=ar` content-difference assertion→forced token refresh→logout). **Real contract drift found and fixed**: the backend's JWT `challenge` handler always throws `AUTH_TOKEN_INVALID` for any bad access token — never `AUTH_TOKEN_EXPIRED`, which is emitted only for an expired *refresh* token inside `/auth/refresh` itself — meaning Task 5's original 401→refresh→retry mechanism was unreachable in production despite passing 64/64 mocked tests; `AuthPlugin.kt` now treats both codes as refresh triggers, verified live (exactly 1 refresh + 2 `/users/me` calls for one corrupted token). A cross-test-suite flake (`UNPARSEABLE_RESPONSE`/200, reproduced in 3 of 4 full-suite reruns, never in isolation) was found during independent review and fixed by giving this test its own isolated `:shared:liveBackendIntegrationTest` Gradle task, excluded from `testDebugUnitTest` — also the architecturally correct split per this project's own "prefer not requiring live backend for every unit test" requirement. All `@kmp.mentora.test` test data (from both the implementer's iteration and review verification) cleaned up via a live `mongosh` pass mirroring the D67 precedent; every pre-existing account independently verified untouched. `:shared:testDebugUnitTest` 249/249 green; `:shared:liveBackendIntegrationTest` 4/4 clean isolated runs; `:shared:assembleDebug` clean. **The `AUTH_TOKEN_INVALID`/`AUTH_TOKEN_EXPIRED` drift must be recorded in Task 17's `INTEGRATION_CONTRACT.md` Phase 3 as-built section.** |
| 17 | Documentation & Phase 3 → Phase 4 handoff | DONE — `mobile/README.md` + `mobile/shared/README.md` (new); `execution/INTEGRATION_CONTRACT.md` § 12 (new, additive-only Phase 3 as-built section covering the CSRF header value, the no-cookie-jar decision, 429 synthesis, the `AUTH_TOKEN_INVALID`/`AUTH_TOKEN_EXPIRED` drift from D77, `?language=`'s exact 4-endpoint dual-role semantics, unpaginated `categories`/`learning-paths`, AI Tutor's non-standard envelope + raw streaming send, the absent lesson `duration` field, opaque certificate ids, and the media playback token-in-URL pattern); `execution/PHASE_HANDOFF.md`'s new Phase 3 entry (the file's fixed 10-section structure, matching Phase 1/2's depth); `execution/CURRENT_STATUS.md` (this file) updated to Phase 3 → COMPLETE with the Phase 4 resume point set. No new `DECISIONS_LOG.md` entry was needed — D69-D77 already fully covered every Phase 3 decision. `git log --oneline --name-only 5c2e739^..229f8ca` gate run and confirmed clean: every touched path falls under `mobile/` or `execution/` only, zero touches to `architecture/`, `product/`, `ux/`, `design-system/`, `design-review-locked/`, `backend/`, or `web/`. Documentation-only — zero changes to `mobile/shared/src/`. |

**Phase 3 is now COMPLETE — all 17 tasks done, gates green** (`:shared:testDebugUnitTest` 249/249,
`:shared:liveBackendIntegrationTest` 4/4 isolated, `:shared:assembleDebug` clean), working tree clean
through commit `229f8ca` plus this Task 17 documentation pass staged on top. See
`execution/PHASE_HANDOFF.md`'s new Phase 3 entry for the full write-up (implementation summary,
files/contracts produced, tests/verification, known limitations, decisions, and what Phase 4
depends on/must not redo) and `execution/INTEGRATION_CONTRACT.md` § 12 for the as-built mobile wire
contract.

**Exact resume point: Phase 4 (Android) awaits explicit user approval before it begins — do not
start Android Compose/navigation/UI work without it.** When approved, Phase 4 builds the real
Android app on top of `mobile/shared` exclusively via `MentoraSdk.create(...)` (never a repository/
`ApiClient`/`HttpClient` directly — structurally enforced, see `mobile/shared/README.md`), supplying
its own `androidMain` `platformModule(context)` (already built and ready to call as-is) and its own
`LessonPlaybackController` implementation (ExoPlayer/Media3 — `shared` provides the interface only,
no implementation). Known limitations carried into Phase 4 (full list in `PHASE_HANDOFF.md` § 6):
iOS/SKIE never compiled/linked/verified on this Windows machine (Phase 5's job, on a real Mac);
`androidMain`'s real AES-256-GCM/Keystore token-storage code path has been reviewed line-by-line but
never run against a real Android Keystore provider — no Robolectric, no instrumented test yet (needs
a real emulator, Phase 4's job); no offline cache/local persistence anywhere in `shared`; the
`AUTH_TOKEN_INVALID`/`AUTH_TOKEN_EXPIRED` mobile-side refresh-trigger workaround (D77); and
`LiveBackendIntegrationTest`'s isolated Gradle task (`:shared:liveBackendIntegrationTest`, not part
of the fast default suite).

**Post-Phase-3 verification follow-up (2026-09-12) — small standalone fix, not a Phase 4 task.**
Running the root `.\gradlew.bat test` aggregate (which drives both `testDebugUnitTest` and
`testReleaseUnitTest`) surfaced the exact D77-documented `UNPARSEABLE_RESPONSE`/200 flake still
reachable via `testReleaseUnitTest` — Task 16/D77's exclusion (`shared/build.gradle.kts`) had only
been applied to `testDebugUnitTest`, leaving the release variant still running
`LiveBackendIntegrationTest` inside the shared ~250-test JVM. Fixed by mirroring the same
`exclude("**/LiveBackendIntegrationTest.class")` onto `testReleaseUnitTest` (plus updated comments/
task description); `liveBackendIntegrationTest` remains the sole, isolated way to run it. Re-ran
`.\gradlew.bat test` clean after the fix. Single-file change (`mobile/shared/build.gradle.kts`), no
other files touched, Phase 4 not started.

## PHASE 4 — Android (started 2026-09-13) — IN_PROGRESS

Explicitly approved by the user 2026-09-13. **Standing Phase Execution Policy applies unchanged**:
continue task-by-task automatically, verify + commit each task as its own checkpoint, update this
section after every task, run the full completion gate at the end, update all four continuity docs
with an implementation-ready handoff, then STOP for explicit user approval before Phase 5 (iOS).
Full 20-task plan with per-task acceptance criteria, the verified KMP `shared` public API surface,
the authoritative 18-screen mobile inventory (assembled from `product/SCREEN_INVENTORY.md` +
`INFORMATION_ARCHITECTURE.md § 3` + `ux/MOBILE_UX.md` + `NAVIGATION_SPEC.md § 3` — not from the web
app's screen list), the locked Android token-mapping table, and 11 disclosed gaps/risks with
in-plan resolutions (none rising to a blocker) were derived by the `architect` subagent — see
`execution/PHASE_4_ANDROID_PLAN.md` and `DECISIONS_LOG.md` D78 for the full detail. **No Favorites/
Wishlist, notifications, forums/Q&A, Notes/Transcript tabs, offline downloads, real payments, real
AI provider integration, new certificate types, subscription pricing, or forgot/reset password is
in scope** — confirmed against `product/MVP_SCOPE.md`. iOS (Phase 5) and real AI Tutor provider
integration (Phase 6) are explicitly not started.

**Key finding from planning:** the locked `design-review-locked/Mentora Showcase.dc.html` contains
genuine exact mobile device mockups (bottom nav in LTR+RTL, an "MOBILE PREVIEW" frame set covering
8 of the 18 in-scope screens) that Phase 2's `design-to-code` extraction pass never captured —
`design-to-code/shared/platform-contract.json` currently calls Android "mapping only, not
implemented." Task 3 extracts these into `design-to-code/screens/mobile-*.json` before any of those
8 screens are built, to avoid the same build-first-compare-later rework Phase 2 needed for web
(D48/D49).

### PHASE 4 — Task Breakdown

| # | Task | Status |
|---|---|---|
| 1 | `:androidApp` module scaffold + version catalog + toolchain verification | DONE — new AGP `application` module `com.mentora.android` (`compileSdk`/`targetSdk` 36, `minSdk` 26 matching `:shared`'s androidTarget), Compose wired via the Kotlin 2.0+ K2 `org.jetbrains.kotlin.plugin.compose` plugin (not the old `composeOptions` mechanism), Compose BOM 2024.10.01, `koin-android`/`koin-androidx-compose` pinned to the existing `koin=4.1.0` (no second Koin version). Debug-only `network_security_config.xml` scopes cleartext to `10.0.2.2` only via a `src/debug/AndroidManifest.xml` merge — never `usesCleartextTraffic` app-wide; `supportsRtl="true"` set. Dependency set deliberately minimal (no Media3/Coil/Navigation-Compose yet — later tasks). Verified: `:androidApp:assembleDebug` green; `:shared:testDebugUnitTest` unchanged at 249/249 (confirms `:shared` untouched); installed and launched on the `Chatting_Pixel_8_API_36` AVD, placeholder screen rendered with no crash (logcat-verified, screenshot-confirmed). One in-scope fix: added `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)` to the placeholder text since targetSdk 36 enforces edge-to-edge by default regardless of `enableEdgeToEdge()` being called. `git status` scope-verified to `mobile/` only. |
| 2 | Token pipeline Android target + `MentoraTheme` | DONE — `tools/token-pipeline/generate.js` gained a purely-additive 4th output target (`mobile/androidApp/.../theme/MentoraTokens.kt`: colors light+dark, typography scale, radius, elevation, spacing, icon sizes, touch target); byte-identical verification (SHA-256) confirms all 3 existing Web outputs unchanged. Hand-authored `MentoraTheme.kt`/`MentoraDimens.kt` build Material3 `ColorScheme`/`Typography`/`Shapes` strictly per `design-to-code/shared/platform-contract.json#/android`'s locked slot mapping (light/dark reuse the identical slot map); `success`/`warning`/`info` via a `MentoraExtendedColors` CompositionLocal (M3 has no built-in slot); Material You dynamic color never called, per D53. Arabic typography adjustments (letterSpacing=0, body line-height ×1.1) implemented unconditionally in code; Arabic font is an honestly-disclosed `FontFamily.SansSerif` placeholder (no Noto Sans Arabic asset exists anywhere in the repo — real font resource deferred to a later task). `Shapes.large`'s top-corners-only sheet/dialog override left as a disclosed TODO (no sheet exists yet). 14 new drift tests assert generated values match source JSON directly. Verified: `:androidApp:assembleDebug`/`testDebugUnitTest` (14/14) green; `:shared:testDebugUnitTest` unchanged at 249/249; `node tools/design-to-code/validate.js` clean (29/6/11, unchanged); web `typecheck`/`lint`/`lint:logical-properties` clean (one pre-existing unrelated `<img>` lint warning). `git status` scoped to `mobile/androidApp/**`, `mobile/gradle/libs.versions.toml`, `tools/token-pipeline/generate.js` only — zero diff under `web/`, `design-system/`, `design-to-code/`. |
| 3 | Extract locked mobile visual references into `design-to-code/screens/mobile-*.json` | DONE — 8 new exact-showcase screen specs (`mobile-{home,explore,course-details,my-learning,ai-tutor,course-player,demo-checkout,purchase-success}.json`) extracted from real, re-verified line citations in `design-review-locked/Mentora Showcase.dc.html` §§ 17/19/20/22 (screens/count 29→37). New `mobileStudentShell` entry in `design-to-code/shared/navigation.json` (5 items Home/Explore/My Learning/AI Tutor/Profile, no badges, tap-active-pops-to-root, independent per-tab back stacks, fully hidden — not merely collapsed, a disclosed Web-vs-mobile platform difference — on Course Player/Quiz, real showcase visual values, LTR+RTL with real Arabic labels). Resolved the showcase's actual (narrower-than-assumed) "Learning"/"My Learning" inconsistency — confined to §§ 20/22's English illustration-frame microcopy, not § 17's own nav component (which already said "My Learning") — to **"My Learning"** per rank-1 precedence, disclosed in `navigation.json#/shells/mobileStudentShell/labelConflict` + 3 screen specs' `conflicts[]`. `platform-contract.json`'s Android `readinessNotes` updated to point at the new shell instead of calling it "not-yet-specified" — nothing else in that file touched. 5 additional genuine conflicts found and honestly disclosed (nav-visibility ambiguity on Course Details' own frame, Learning Paths segment has no visual evidence, Purchase Success secondary-button copy divergence, AI Tutor's captured frame is contextual-only). The 10 remaining in-scope mobile screens with no showcase mockup deliberately NOT given fabricated specs — left `ux-only`, to be built directly from `ux/SCREEN_UX_SPECS.md`/`MOBILE_UX.md` in their own later tasks. Verified: `node tools/design-to-code/validate.js` clean (37/6/11); `node tools/design-to-code/generate.js` re-run confirmed purely additive (byte-diffed — every pre-existing entry for the original 29 screens unchanged, `component-index.json` byte-identical); web `typecheck`/`lint` clean. `git status` scoped to the 8 new screen files + `navigation.json`/`platform-contract.json`/`EXTRACTION_REPORT.md` + the two sanctioned generated artifacts — zero touches to `mobile/`, `architecture/`, `product/`, `ux/`, `design-system/`, `design-review-locked/`, `backend/`, `web/src/`. |
| 4 | App bootstrap: SDK wiring, session restore, locale/theme bootstrap, Keystore instrumented test | DONE — `MentoraApplication` owns the one `MentoraSdk`/`AndroidPreferenceStore` instance for the process lifetime (G1: app retains its own `AndroidPreferenceStore`, hands the same instance to Koin, reads it directly for theme since the façade is write-only) and the one ordered application-scoped bootstrap sequence (`restoreSession()` fully completes, then `LocaleController.seedInitialLocaleIfNeeded()` — G2: first-run flag in its own dedicated `SharedPreferences` file, gated on auth state, written via `commit()`). `AppSessionViewModel` purely observes `observeAuthState()` and follows up `Authenticated(user=null)` with `getProfile()`; `ThemeController` computes `MentoraTheme(darkTheme=...)` from `ThemePreference`. New `AndroidTokenStorageInstrumentedTest` (3 tests) closes the one Phase 3 limitation explicitly assigned to Phase 4 — the real AES-256-GCM/Android-Keystore path verified end-to-end on the `Chatting_Pixel_8_API_36` AVD for the first time (save/read/clear round-trip; tamper-detection sub-case honestly disclosed as not testable from `:androidApp` without reaching into `AndroidTokenStorage`'s private implementation). **Two review passes, both fixed before commit**: an Opus review found and fixed 2 medium bugs (Activity-scoped session restore causing a downgrade-and-reflash on every app re-entry; async `SharedPreferences.apply()` locale-seed flag with no auth-state guard, risking overwriting a real user's locale choice) plus hygiene items (dead `MentoraApplication.instance` holder removed, `ThemeController` hoisted to a true application-scoped singleton, undeclared `androidx-core-ktx` dependency made explicit). An additional independent Codex review (security-sensitive code, per the routing policy) then found and fixed a HIGH-severity gap the Opus pass missed — **the app manifest was completely missing `android.permission.INTERNET`**, which would have silently blocked every real network call from Task 7 onward (undetected because the earlier cold-start smoke test never actually hit the network, since `restoreSession()` short-circuits locally with no stored tokens) — verified fixed end-to-end with a real, then-removed, temporary network call against the live local backend (real category data returned). Codex also found the release build variant hardcodes the emulator-only environment — disclosed as a deliberate, `BuildConfig.DEBUG`-gated limitation (no production backend exists anywhere in this project) rather than fabricating a fake HTTPS endpoint; and closed the session-restore/locale-seed race categorically by moving both to the single application-scoped bootstrap coroutine (ViewModels/Composables no longer call `restoreSession()`/the locale controller directly). Verified: `:androidApp:assembleDebug`/`testDebugUnitTest` green; `:shared:testDebugUnitTest` unchanged at 249/249; `:androidApp:connectedDebugAndroidTest` 3/3 passing on the real emulator; fresh cold-start smoke test shows the bootstrap sequence running exactly once with no crash and no raw token in logcat. `git status` scoped to `mobile/androidApp/**` + `mobile/gradle/libs.versions.toml` only. |
| 5 | Core component kit A (atoms) | DONE — `MentoraButton`/`MentoraTextButton` (Primary/Secondary/Tonal/Text), `MentoraIconButton`, `MentoraTextField`, `PasswordField`, `SearchField` (built here rather than its originally-planned T8 slot — harmless, T8 should reuse it, not rebuild it), `MentoraSelect` (M3 `ExposedDropdownMenuBox`), `Badge`, `CategoryChip`, `Avatar`, `MentoraProgressBar`, `MentoraTabs`, `MentoraSnackbar`, and `MentoraIcons` (all ~42 web icons ported to Compose `ImageVector` via `PathParser`, D80 executed). Every value traces to `MentoraTheme`/`MentoraTokens`/`MentoraDimens`, with a small number of disclosed, cited gaps (avatar sizes, border widths, motion values — present in `design-tokens.json` but never walked by Task 2's generator; a 88dp button min-width and 40/48dp heights that are plain literals in `design-tokens.json`'s own component layer, not aliases). **One reviewer pass + fix cycle, both pixel-measured**: found and fixed a HIGH bug (a "loading" button rendered pixel-identical to "disabled" — the `enabled && !loading` combined flag was feeding both the color lookup and the interaction gate, so every submit button in the app would have shown a near-invisible ~38%-alpha spinner on a washed-out container instead of the normal container with a real spinner; fixed by driving colors from the real `enabled` flag only) plus several medium/low fidelity gaps (disabled `Select` text not dimmed; `Select` dropping typography-token properties + no 1-line truncation, verified via a 57-char value growing the field 64dp→77dp before the fix; three controls below Android's 48dp touch-target minimum, fixed via `minimumInteractiveComponentSize()`/`heightIn(min=48dp)`; `Tabs`' 44dp row vs. its children's real 48dp height, resolved to a disclosed 48dp; `IconButton`'s focus ring using the wrong token; disabled `TextField` label/supporting-text color; error text not semantically linked for screen readers). Also disclosed rather than silently accepted: `MentoraTextField`/`MentoraSelect`'s real rendered height is 64dp, not the spec's 52dp (M3's `OutlinedTextField` floor) — a future task could rebuild via `BasicTextField`+custom `DecorationBox` for exact fidelity if this matters later. Verified: `:androidApp:assembleDebug`/`testDebugUnitTest` green; `:shared:testDebugUnitTest` unchanged at 249/249; `:androidApp:connectedDebugAndroidTest` 13/13 passing on the real `Chatting_Pixel_8_API_36` emulator (including a strengthened error-color pixel assertion). `git status` scoped to `mobile/androidApp/**` only. |
| 6 | Navigation shell: 5-tab bottom nav, per-tab back stacks, guest mode, auth gate | DONE — one `NavHost`/`NavController` with 5 nested tab graphs (Google's documented multiple-back-stacks pattern: `popUpTo(anchor){saveState=true}`+`launchSingleTop`+`restoreState` on tab switch), `Destinations.kt` (18-screen `@Serializable` sealed route model), `AuthGate.kt` (pure `decideAuthGate` + a `PendingNavIntent` that now survives rotation/process death via `rememberSaveable`+kotlinx-serialization), `MobileBottomNavigation`/`MentoraTopBar` (real EN+AR string resources, correct "My Learning" label), placeholder screens for every route not yet built (real screens land in Tasks 7/9-18 without touching this graph). **Two full review cycles, both with real-device verification, given this is the navigation backbone every later screen depends on**: cycle 1 found and fixed a HIGH bug the implementer's own tests didn't catch — `findStartDestination()` (a *structural* tab-switch anchor) silently broke the entire multiple-back-stacks mechanism for the rest of the session after any full-stack reset (reachable today via completing a demo purchase), because Navigation-Compose 2.8.3 no-ops a `popBackStack` to a destination no longer on the stack; fixed by replacing the structural anchor with an explicit, `rememberSaveable`-tracked "current anchor tab" updated at all 3 reset sites (login/logout/purchase-success) — reproduced broken, then reproduced fixed, on the real `Chatting_Pixel_8_API_36` emulator via real `currentBackStack.value` inspection (added as new test infrastructure, since the original 5 tests only asserted surface-level text visibility). Also fixed in the same cycle: the pending-intent rotation-loss bug above, a stale pending-intent silently firing on a later unrelated login, and one placeholder ("Continue Learning") that bypassed the auth gate. **Disclosed, not fixed** (both low-impact, both recorded for later): a one-tap dead-input quirk immediately after any full-stack reset (a separate navigation-runtime null-saved-state-key quirk, not the isolation-breaking defect itself); and a narrow, self-correcting "wrong tab momentarily associated" edge case when a *guest* triggers the Continue-Learning gate specifically (multi-graph-registered destinations resolve to whichever tab-graph is declared first when reached from a tab-less context like Login) — back-press and nav-hiding both still behave correctly regardless. Verified: `:androidApp:assembleDebug`/`testDebugUnitTest` green; `:shared:testDebugUnitTest` unchanged at 249/249; `:androidApp:connectedDebugAndroidTest` 19/19 on the real emulator (run twice). `git status` scoped to `mobile/androidApp/**` + `mobile/gradle/libs.versions.toml` only. |
| 7 | Auth screens (Login, Register) | DONE — real `LoginScreen`/`RegisterScreen` (card surface + minimal logo-only header, no full nav chrome, `ux/SCREEN_UX_SPECS.md §§ 6-7` design-derived per `approved-pattern` — no exact showcase mockup exists for either) using Task 5's `MentoraTextField`/`PasswordField`/`PrimaryButton`. New central `ApiErrorCopy.kt` mapping all 22 known `ApiErrorCode`s + `Unknown` to localized EN/AR copy (reusable by every later task that handles API errors — Tasks 9+ should import this, not build their own). `AuthViewModel` calls `sdk.auth.login/register` and never navigates itself — `MentoraNavHost`'s existing `LaunchedEffect(authState)` (built and tested in Task 6) handles the actual post-auth navigation, confirmed by real-backend verification. Login routes every failure (including the generic `AUTH_INVALID_CREDENTIALS`) to a general banner, never to a field — a deliberate security property, not an oversight (a wrong email and wrong password must stay indistinguishable). Register routes `fields["email"]="INVALID"`/`fields["password"]="WEAK"` to their own fields and `EmailAlreadyRegistered` under the Email field even though it's never literally in `fields` (the D51 precedent). No "Forgot password?" link (correctly out of MVP scope, no backend endpoint exists). Verified against the REAL local backend on the real emulator: a real register (fresh throwaway account, deleted afterward via `mongosh`, all 9 pre-existing accounts confirmed untouched), a real wrong-password login (rendered "Incorrect email or password." from the real `AUTH_INVALID_CREDENTIALS` response), and a real correct-password login — all correctly triggered `MentoraNavHost`'s pending-intent/Home navigation. Keyboard-avoidance (`imePadding()`) confirmed via screenshots at every fill step on both screens with the IME open. `:androidApp:assembleDebug`/`testDebugUnitTest` green; `:shared:testDebugUnitTest` unchanged at 249/249; `:androidApp:connectedDebugAndroidTest` 33/33 on the real emulator. `git status` scoped to `mobile/androidApp/**` only. |
| 8 | Core component kit B (cards, state patterns, sheets) | DONE — `CourseArtwork`/`CourseThumbnail` (the governed 5-motif system: exact hash+mod5 deterministic assignment cross-verified byte-for-byte against web's own generator, real `mediaId` image via Coil with the artwork system itself as the broken-image fallback — never a generic broken-image glyph; fine CSS texture layers disclosed-omitted, base+highlight gradient + 5 distinct icons retained), `CourseCard`/`CourseProgressCard`, `LearningPathCard`, `StatCard`, `CertificateCard` (disclosed placeholder "document" preview, same D42 precedent as web — no real certificate asset exists), `AITutorBubble`/`AITutorQuickAction`, `QuestionCard`/`AnswerOption` (all 5 states — Correct/Incorrect verified icon+text+color together via real pixel capture, never color alone), `LoadingState`/`EmptyState`/`ErrorState`/`SuccessState`, `AppDialog`, `MentoraBottomSheet` (finally resolves Task 2's disclosed top-corners-only TODO). **Two full review cycles plus an extended, independently-re-verified fix chain on one specific finding**, given this kit is the foundation 7+ later screens build on: cycle 1 found and fixed two more HIGH bugs — (a) `CourseCard`/`CourseProgressCard` had no self-determined height, so in any bounded-height container (a 2-up `Row` grid, a `LazyRow` carousel, a plain non-scrolling `Column` — i.e. everything except a scrolling list) the card filled the entire available height with its title/instructor/CTA pushed off-screen entirely; fixed by making the thumbnail area genuinely self-size to 16:9 via `fillMaxWidth().aspectRatio(...)` rather than inheriting an unbounded parent height, verified in all 4 container shapes on the real device; (b) `AppDialog`/`MentoraBottomSheet`/`CourseCard` rendered a visible lavender tonal wash instead of the spec'd plain `surface.elevated`/`surface.default`, because this theme deliberately leaves `surfaceTint` unmapped (defaults to `primary`) and any nonzero `tonalElevation` on an M3 `Surface` composites that tint — fixed by switching to `shadowElevation`/borders (the "borders over shadow" pattern already established elsewhere in this kit), re-measured to exact spec hex in both themes. Also fixed in the same cycle: a factually-backwards reduced-motion platform claim in `SuccessState`'s kdoc (Compose's own animations DO already respect the system animator-duration-scale setting automatically — corrected, not a behavior change), `AppDialog`'s actions row was right-aligned when the spec requires full-width-stacked on mobile (Android IS the mobile platform — the kdoc had inverted the two spec branches), a hard 2-line description clamp on `EmptyState`/`SuccessState` that contradicts the locked "wraps freely, never truncated" rule (removed, now consistent with `ErrorState`), and 2 missing single-line clamps (`CourseCard` instructor name, `StatCard` label) that the spec requires for grid-rhythm reasons. **`AppDialog`'s scrim color fix specifically required 3 attempts, each independently re-measured via real full-screen device screenshots** (not just `captureToImage()` on the dialog's own tagged node, which is structurally blind to a residual platform window dim underneath — the exact flaw that let 2 consecutive "fixed" claims pass their own tautological tests): attempt 1 (`usePlatformDefaultWidth = false`) did nothing to the platform's default ~60%-black window dim, measured 57-60% too dark in both themes; attempt 2's self-report of an exact match was independently disproven the same way; attempt 3 (`window.setDimAmount(0f)` + `DialogProperties(decorFitsSystemWindows = false)` + `FLAG_LAYOUT_NO_LIMITS`, all via a real `Window`/`DialogWindowProvider` reference, plus a rewritten test using genuine full-screen capture) measured within ~3 units of the token in both themes across the general scrim area AND the status/nav-bar strips — confirmed correct for the steady state by an independent Codex second-opinion pass. **Disclosed, not fixed** (Codex's own finding on attempt 3, accepted rather than pursuing a 4th attempt): a possible brief over-dimmed flash on dialog OPEN specifically, before the window-correcting `SideEffect` runs post-first-composition — the steady-state color (what a user sees for the duration the dialog stays open) is correct; only the transient first-frame-or-two during the open animation is unverified and could theoretically flash darker. Verified: `:androidApp:assembleDebug`/`testDebugUnitTest` green; `:shared:testDebugUnitTest` unchanged at 249/249; `:androidApp:connectedDebugAndroidTest` 65/65 on the real emulator. `git status` scoped to `mobile/androidApp/**` (+ Coil 2.7.0 in the version catalog) only. |
| 9 | Explore (+ Learning Paths segment) | DONE — real `ExploreScreen` (heading, `SearchField` with 300ms-debounced search, horizontally-scrollable real `CategoryChip` row, `MentoraTabs` "Courses"/"Learning Paths" segment, cursor-paginated `CourseCard`/`LearningPathCard` lists, loading skeletons, empty/error states) per the exact-showcase `mobile-explore.json` spec. `Destination.LearningPaths` (the T6-era push destination) removed and folded into Explore's own tab state — the literal, non-redundant satisfaction of the rank-1 "segment/tab inside Explore" requirement; `LearningPathDetails` unchanged. `?language=` never manually threaded — reused `shared`'s automatic locale threading as instructed. Verified against the REAL local backend as a guest: real seeded categories/courses/artwork render; search "kotlin" narrows to exactly the one matching seeded course; category+query empty state renders the exact spec copy + working "Clear filters"; Learning Paths tab shows the real seeded path; course/path taps navigate with real ids. **Concrete locale-sensitivity verification** (not just asserted): the same course id's title was confirmed to literally change between `"User Experience Design Fundamentals"` (EN) and `"أساسيات تصميم تجربة المستخدم"` (AR) through the exact code path `ExploreViewModel` uses, cross-checked against a direct backend curl. Disclosed gap inherited from Task 8 (not introduced here): `CourseCard` has no price slot, so `PriceDisplay` goes unrendered in Explore. Verified: `:androidApp:assembleDebug`/`testDebugUnitTest` green (9 new JVM tests); `:shared:testDebugUnitTest` unchanged at 249/249; `:androidApp:connectedDebugAndroidTest` 75/75 on the real emulator. `git status` scoped to `mobile/androidApp/**` only. |
| 10 | Course Details | DONE — real `CourseDetailsScreen` per `ux/SCREEN_UX_SPECS.md § 3`'s rank-1 LINEAR structure (hero → category chip → title/instructor → rating meta → price-or-enrolled-state → sticky CTA → description → curriculum outline → instructor block) — NOT the showcase's 3-tab presentation, a deliberate, disclosed resolution in favor of rank-1. Header's bookmark icon deliberately omitted entirely (Favorites/Wishlist is categorically out of MVP scope — a bookmark icon here would be exactly that forbidden feature); share also omitted for this task (rank-1 marks it optional/future; adding it would require a shell-shape change to the shared `MentoraTopBar`, more scope than warranted here) — back button reuses the nav shell's existing real back arrow, not rebuilt. Bottom nav confirmed to coexist with the sticky CTA footer (verified live) — Course Details is not a nav-hidden screen. `isEnrolled` derived by fully paging `listEnrollments` (no `isEnrolled` field exists on `Course`/`CourseSummary` — G4) rather than trusting a single page. No fabricated progress bar when enrolled (real per-course progress needs `sdk.progress`, deferred to later tasks) — just hides price and shows a plain enrolled confirmation. Curriculum lessons show a locked icon/no play affordance until enrolled, gated on real enrollment only (never on `Course.status`, since an already-enrolled student can see `Draft` after unpublish — D64). Verified against the REAL local backend, all 3 CTA states confirmed live on-device: guest → "Login to Enroll" + locked curriculum + no bottom nav (existing rule); authenticated-not-enrolled → real "Enroll" + real formatted price + sticky footer above visible bottom nav; authenticated-and-enrolled (real seeded account, real pre-existing enrollment, no synthetic data created) → "Continue Learning" + unlocked curriculum + real navigation to Course Player with real ids. `:androidApp:assembleDebug`/`testDebugUnitTest` green (12 new JVM tests); `:shared:testDebugUnitTest` unchanged at 249/249; `:androidApp:connectedDebugAndroidTest` green (isolated re-run after one emulator-flakiness-under-load full-suite hiccup, confirmed unrelated to this task). `git status` scoped to `mobile/androidApp/**` only. **Disclosed, closed same-session**: this task (and Task 9 before it) shipped new `values/strings.xml` entries with no `values-ar` translation — a real, MVP-required gap (EN/AR are both real MVP languages) that would have compounded across the remaining screen tasks; backfilled immediately as a same-session follow-up rather than deferred entirely to Task 19's sweep (see below). |
| 11 | Demo Checkout + Purchase Success | DONE — real `DemoCheckoutScreen`/`PurchaseSuccessScreen` per `design-to-code/screens/mobile-demo-checkout.json`/`mobile-purchase-success.json` (exact-showcase), backed by `CheckoutViewModel`/`PurchaseSuccessViewModel`. Purchase Success is genuinely `shell: "none"` — no top bar, no bottom nav — verified via `MentoraNavHost`'s `isChromeless`/`showBottomNav` flags, not merely a body slotted into a shell that looks unstyled. `onStartLearning` pops `PurchaseSuccess` off (`popUpTo<Destination.PurchaseSuccess>{inclusive=true}`) before pushing Course Player, per `ux/NAVIGATION_SPEC.md`'s "Course Player always backs to My Learning, never Course Details" rule — verified the button-tap and system-back paths land identically. This session was recovered mid-interruption (an unexpected shutdown) — the screens/ViewModels already existed from the interrupted prior session; recovery added the missing ~19 `strings.xml`/`values-ar/strings.xml` entries the build was failing on, the `MentoraNavHost` wiring, 13 new JVM ViewModel tests, and fixed 3 stale `NavigationShellTest` assertions. **A real, genuine cross-session identity bug was found and fixed, chased through 3 attempts (2 by an Opus review, the actual root cause by an independent Codex second opinion)** — see `DECISIONS_LOG.md` D83 for the full account: (1) an unguarded read-then-write race in `SessionManager.currentAccessToken()`'s disk-fallback path, fixed with a `sessionMutex` (a near-miss reentrant-`Mutex` deadlock in the first draft was caught and fixed before shipping); (2) `NavigationShellTest` building a fresh `MentoraSdk` per test method while all shared one on-disk token file, fixed with one shared `sdk` for the whole test class; (3) — the actual root cause, found by Codex after (1)+(2) failed to eliminate the flake — `MentoraApplication.onCreate()` still runs in every instrumented test's process regardless of what the test itself constructs, bootstrapping a SECOND, always-present `MentoraSdk`/`SessionManager` against the same token file; fixed with `NoOpApplicationTestRunner` (a custom `AndroidJUnitRunner` substituting a plain `Application`), the officially-documented mechanism for this exact problem — a first attempt (an `androidTest/AndroidManifest.xml` override) was verified to be a no-op against the merged manifest and discarded before landing the real fix. A second, independent Codex finding (a `refreshAccessToken()` gap that could hand a stale request a DIFFERENT account's tokens if an account switch raced an in-flight refresh) was also fixed, via a `sessionGeneration` guard. An interim self-report during this investigation claiming "not a production bug, fully investigated" was WRONG and is explicitly retracted in D83 — the defect was real and is now closed at its true root cause. Verified: `:shared:testDebugUnitTest` 249/249; `:androidApp:testDebugUnitTest` 60/60 (13 new); `shared:liveBackendIntegrationTest` green against the live backend; `:androidApp:connectedDebugAndroidTest` 84/84 with zero failures, `NavigationShellTest` itself 7/7 including both previously-flaky tests. `git status` scoped to `mobile/androidApp/**` + `mobile/shared/src/commonMain/kotlin/com/mentora/shared/auth/**` + `mobile/shared/src/androidUnitTest/**`'s one call-site fix. |
| 12 | Home + My Learning (+ Certificates entry) | DONE — real `HomeScreen`/`MyLearningScreen` per `design-to-code/screens/mobile-home.json`/`mobile-my-learning.json` (exact-showcase), replacing their placeholders. **The G3/G5 joins** (`GetMyLearningWithProgressUseCase` in `domain/mylearning/`) deliberately layered in `androidApp`, not `shared`, per `PHASE_4_ANDROID_PLAN.md` § 6's own decision: `sdk.enrollment.getMyLearning()` + one `sdk.progress.getCourseProgress()` call per enrollment (and an analogous per-path join for followed Learning Paths). Certificates entry point: an always-present header icon button plus real "certificate ready" highlight rows. Delegated to an implementer sub-agent then the primary Opus reviewer per the standing routing policy (a substantial completed feature) — **4 real medium-severity bugs found and fixed**: Home's greeting name/avatar stayed permanently blank after a cold start with a restored session (`HomeViewModel`'s `firstName` was only read once at construction, before `AppSessionViewModel`'s async `getProfile()` follow-up resolved the real name — fixed with a `LaunchedEffect(userName)`); the stat row (and its Certificates entry point) vanished with factually-wrong "No courses yet" copy for students who'd completed every course they started (was nested inside the Continue-Learning module's own empty-state branch instead of being independent — fixed, hoisted out); the loading skeleton rendered at 0dp (invisible — `SkeletonBlock` has no intrinsic size, this was the one call site missing an explicit height); "Continue Learning" always picked the OLDEST stalled enrollment, not the most recent (`GET /enrollments` sorts ascending by `_id`, so `.first()` surfaced the most-likely-abandoned course — fixed to `.last()`). Also fixed: My Learning's dead `resolveThumbnailUrl` parameter (never threaded through, so every enrolled course showed generated artwork instead of its real thumbnail); Home's Resume button could open a different lesson than the card's own "Lesson N of M" text named; a factually-wrong nav-runtime kdoc claim ("unreachable-by-name") the reviewer disproved by disassembling navigation-runtime 2.8.3 — real behavior is the route resolves under the WRONG tab, not an error — corrected, and `DemoCheckout` registered under `HomeGraph` too since Home can now reach it via Course Details' Enroll action; 6 new tests for previously-uncovered `resolveLessonPosition` edge cases; an Arabic terminology alignment (`my_learning_heading`, same D83 precedent). One genuinely-external flake disclosed, not chased: the reviewer's own independent run hit one `EmailAlreadyRegistered` 409 on a fresh throwaway email with evidence of a possible double-dispatched register call — did not reproduce across 3 further runs in this session; pre-existing `shared`/backend territory, out of scope here. Verified: `:shared:testDebugUnitTest` unchanged; `:androidApp:testDebugUnitTest` 88/88 (6 new); `:androidApp:assembleDebug` clean; `NavigationShellTest` 9/9 on the real emulator; full `:androidApp:connectedDebugAndroidTest` 86/86 with zero failures on a freshly-relaunched emulator (an interim run showed 3 unrelated screenshot-capture flakes in Task 5/8 kit-component tests this task never touched — confirmed as host-resource-contention, not a regression, by an isolated re-run on a fresh emulator instance). `git status` scoped to `mobile/androidApp/**` only — `shared` untouched. See `DECISIONS_LOG.md` D84. |
| 13 | `LessonPlaybackController` (ExoPlayer/Media3) + Course Player + Curriculum Bottom Sheet | DONE — three sub-commits per the pre-implementation plan (D85): C1 `MediaPlaybackController`/`PlaybackController` (ExoPlayer/Media3 binding), C2 `CoursePlayerViewModel` (the concurrency-critical state machine — 5 review rounds, 3 HIGH bugs found and fixed across rounds 1-3, a HIGH race found by an independent Codex pass in round 4, a final targeted Opus round 5 catching a still-one-sided completion/navigation guard; full account in D87), C3 the Compose UI (`CoursePlayerScreen`/`PlayerControls`/`PlayerSurface`/`CurriculumBottomSheet`/`MentoraPlayerChrome`, delegated to an implementer then Opus-reviewed twice — round 5 found and fixed 2 HIGH bugs (an inescapable Quiz auto-navigate back-loop; the wrong `LifecycleOwner` collapsing D85 Decision 6's two visibility hooks onto one trigger) + 6 MEDIUM (icon RTL mirroring, double/triple window-insets, missing lesson description render, scrubber accessibility label, plus 2 deferred/disclosed); round 6's targeted re-verification found the round-5 `LifecycleOwner` fix still incomplete (composition-scoped, so whole-app-backgrounding stopped pausing playback once the screen left composition via a tab switch or a push to Quiz) and fixed it by moving whole-app-background detection into the ViewModel itself via `ProcessLifecycleOwner` (new `lifecycle-process` dependency) — full account in D88. C4 (fullscreen) deliberately omitted per D85's own recommendation and the "never ship a no-op icon" precedent (Task 10) — not part of this task's locked scope. Verified (final state): `:shared:testDebugUnitTest` 249/249 (zero diff in `mobile/shared` across all of Task 13); `:androidApp:testDebugUnitTest` 142/142; `:androidApp:assembleDebug` clean; `:androidApp:connectedDebugAndroidTest` 87/87 on the real `Chatting_Pixel_8_API_36` emulator (one mid-suite run aborted on a confirmed emulator `system_server`/zygote crash unrelated to this task's code, clean reruns after all fixes passed 87/87 twice). `git status` scoped to `mobile/androidApp/**` + `mobile/gradle/libs.versions.toml` only — `shared` untouched. See `DECISIONS_LOG.md` D85/D87/D88. |
| 14 | Quiz + Quiz Results | DONE — real `QuizScreen`/`QuizResultsScreen` built from `ux/SCREEN_UX_SPECS.md` §§ 11-12 directly (no exact-showcase mockup exists for either), delegated to an implementer then Opus-reviewed once. Quiz Results re-fetches via `getLatestAttempt` rather than passing `QuizAttemptResult` through navigation (traced race-free end to end against the backend's own transaction ordering); `Destination.QuizResults`'s vestigial `attemptId` param removed. Review found and fixed 2 HIGH (a stale `NavigationShellTest` placeholder assertion; quiz answers not surviving a mid-attempt back-out — a LOCKED `ux/NAVIGATION_SPEC.md` requirement — fixed with a new process-scoped `QuizAttemptDraftStore`) + 4 MEDIUM (a passive `CoursePlayerViewModel.refreshQuizStatus()` — the narrow additive fix this task needed to close Task 13's own disclosed staleness gap — silently resetting `isCompletionInFlight`/`completionError` and risking a regressed `progress` behind a newer concurrent write; the identical nested-`Scaffold` double-inset bug Task 13 already fixed once, reintroduced on both new screens; missing radio-group accessibility semantics on answer options) + 3 LOW, all fixed same-session. Full account, including a deliberate resolution of a genuine 3-way spec conflict on "Retry Quiz"'s target, in `DECISIONS_LOG.md` D89. Verified: `:shared:testDebugUnitTest` 249/249 (zero diff in `mobile/shared`); `:androidApp:testDebugUnitTest` 169/169; `:androidApp:assembleDebug` clean; `:androidApp:connectedDebugAndroidTest` 87/87 on a freshly-relaunched real emulator (one run showed 3 failures, all confirmed pre-existing/unrelated screenshot-capture flakes under emulator load, per the same documented pattern Task 12 already recorded). `git status` scoped to `mobile/androidApp/**` only — `shared` untouched. |
| 15 | Certificates List + Certificate Detail | DONE — real `CertificatesScreen`/`CertificateDetailScreen` built from `ux/SCREEN_UX_SPECS.md` §§ 13-14 directly (no exact-showcase mockup exists for either), delegated to an implementer then Opus-reviewed once. `CertificateSummary`/`CertificateDetail` carry no `courseId` field (a real, pre-existing, disclosed backend/`shared` gap, confirmed again here, not this task's to fix) — `MyLearningScreen`'s already-correct per-certificate deep link (Task 12) started working automatically once the Detail screen stopped being a placeholder. Review found and fixed 1 HIGH (`Destination.CertificateDetail` was never registered under `TabGraph.ExploreGraph` — a card tap reached from Explore silently mis-anchored the bottom nav to Home and could destroy the entire Explore back stack on the next Home tap; the same class of navigation-registration bug D81/D85 already found and fixed elsewhere, now caught a third time before ever shipping) + 1 MEDIUM (untranslated English retry button on both new screens) + 4 LOW (a misleading numeral-pinning kdoc/test pair, an overstated RTL-independence claim, scoped `mergeDescendants` accessibility improvement on `CertificateCard`). Full account in `DECISIONS_LOG.md` D90. Verified: `:shared:testDebugUnitTest` 249/249 (zero diff in `mobile/shared`); `:androidApp:testDebugUnitTest` 183/183; `:androidApp:assembleDebug` clean; `:androidApp:connectedDebugAndroidTest` 87/87 on the real emulator. `git status` scoped to `mobile/androidApp/**` only — `shared` untouched. |
| 16 | Learning Path Details (follow/unfollow) | DONE — real `LearningPathDetailsScreen` built from `ux/SCREEN_UX_SPECS.md § 5` directly (no exact-showcase mockup exists), delegated to an implementer then Opus-reviewed once. Completed/Current/Upcoming derived client-side (one `getCourseProgress` call per member course, no server-side per-course status field exists). The D90 navigation-registration checklist (see Task 15's own entry) was applied proactively for this task's new `CourseDetails`/`DemoCheckout` push chain under `MyLearningGraph`, and independently re-verified clean by the reviewer via the FULL transitive push closure — no repeat of D90's own HIGH finding. Review found and fixed 1 MEDIUM (the hero progress bar and per-course Completed badges used two different "course completed" definitions and could visibly contradict each other — a course with every lesson watched but its quiz not yet passed showed a Completed badge while the bar above it correctly showed no progress; now both use the identical server-side `courseCompletedAt != null` definition) + 1 LOW (a staleness race in the proactively-added `MyLearningViewModel.refreshFollowedPaths()`, fixed with a tracked cancel-and-replace `Job`) + 2 disclosed-not-fixed LOW items + 1 disclosed, deliberate spec deviation (an Unfollow button added since the locked spec text has no secondary action at all, which would have left half this task's own name unimplementable). Full account in `DECISIONS_LOG.md` D91. Verified: `:shared:testDebugUnitTest` 249/249 (zero diff in `mobile/shared`); `:androidApp:testDebugUnitTest` 195/195; `:androidApp:assembleDebug` clean; `:androidApp:connectedDebugAndroidTest` 87/87 on the real emulator. `git status` scoped to `mobile/androidApp/**` only — `shared` untouched. |
| 17 | AI Tutor (streaming chat, stub provider) | DONE — real `AiTutorScreen`/`AiTutorViewModel` built from `ux/SCREEN_UX_SPECS.md § 15`/`ux/MOBILE_UX.md §§ 10, 14` directly (no exact-showcase mockup covers the TAB-ROOT case — `mobile-ai-tutor.json` is an exact-showcase of the CONTEXTUAL Course-Player-launched variant instead, its own disclosed `conflicts[0]`), delegated to an implementer then Opus-reviewed once. Streams `MentoraSdk.aiTutor.sendMessage` — a cold `Flow<AiStreamResult>` (`Chunk`/`PreStreamFailure`/`StreamFailed`, the last's partial text real and never discarded) — against the backend's still-genuinely-stubbed `StubAiProvider` (confirmed, no real LLM call). All 5 `AiQuickAction`s offered with `courseId=null, lessonContextId=null` always — the Course-Player-contextual/docked variant is explicitly out of scope, an inherited limitation matching Phase 2 Task 9's own disclosed Web gap. `Destination.AiTutor`'s transitive push closure is empty (it takes no navigation lambdas at all) — independently re-verified by the reviewer via a direct `MentoraNavHost.kt` grep, no repeat of D90/D91's registration-gap category. Review found and fixed 4 HIGH (the implementer's own disclosed `AITutorBubble` 80%→85% max-width change was itself a regression against the locked rank-2 `COMPONENTS.md` contract, reverted; disabling the composer field while sending silently closed the keyboard, violating the one behavior `MOBILE_UX.md § 14` names for this screen by name; missing `.imePadding()` let the IME overlay the composer under this app's edge-to-edge `targetSdk`; the history load unconditionally overwrote `items`, capable of destroying an in-flight turn sent before it resolved) + 7 MEDIUM (retry of a `StreamFailed` partial bubble discarded the real partial text; no auto-scroll on new/growing content; the accessibility live region announced nothing — `mergeDescendants` fix, scoped to fire once on completion not per-chunk; a retry-row `Text` with no `weight` could squeeze the Retry button to 0dp at large font scale; quick-action chips stayed enabled and silently no-opped while sending; the same chips sat below the 48dp touch-target minimum; only the oldest 20 history messages were ever loaded, disagreeing with the backend's own true-recent-history AI context) + 5 LOW, all HIGH/MEDIUM and most LOW fixed same-session. Full account in `DECISIONS_LOG.md` D92. Verified: `:shared:testDebugUnitTest` 249/249 (zero diff in `mobile/shared`); `:androidApp:testDebugUnitTest` 211/211; `:androidApp:assembleDebug` clean; `:androidApp:connectedDebugAndroidTest` 87/87 on the real emulator (two prior attempts hit unrelated Gradle/Windows tooling errors — a stale-file MD5-hash failure, then a locked logcat file from an orphaned daemon — resolved by stopping the Gradle daemons and clearing stale `androidTest-results`/`androidTests` report directories, not a test or product defect). `git status` scoped to `mobile/androidApp/**` only — `shared` untouched. |
| 18 | Profile + Settings (language selector, theme, logout) | DONE — real `ProfileScreen`/`SettingsScreen` built from `ux/SCREEN_UX_SPECS.md §§ 16-17`/`ux/MOBILE_UX.md § 13` directly (no exact-showcase mockup exists for either). Two disclosed scope resolutions (both re-verified against the source docs during review): no account/password fields (no backend endpoint exists, same D44 precedent); Logout lives on Profile only, not duplicated on Settings (`MOBILE_UX.md § 13`'s own literal mobile-specific resolution overriding `SCREEN_UX_SPECS.md`'s generic Settings-Logout bullet). **The central deliverable**: new `locale/LocalizedContent.kt` makes Settings' Language selector genuinely functional for the first time in this phase — before this task, `sdk.user.setLocale()` never actually changed what any `stringResource` resolved to anywhere in the app (governed entirely by the device's OS locale), a real, previously-undocumented gap for a LOCKED MVP requirement. Implemented via a pure-Compose `LocalContext`/`LocalConfiguration`/`LocalLayoutDirection` override (a `ContextWrapper`, never a raw `createConfigurationContext` result — the two are NOT interchangeable, see D93 for the exact, real consequences of getting this wrong), deliberately not `AppCompatDelegate` (this app has no `AppCompatActivity`/AppCompat dependency anywhere, and that API's below-API-33 correctness requires it). A `LocalAppLocale`/`WithCurrentAppLocale` pair closes the one real live gap this creates (`Dialog`/`Popup`/`ModalBottomSheet` reset `LocalContext` for their own sub-composition) — applied to `MentoraBottomSheet` only, the one shared overlay primitive with a real affected call site; `AppDialog`/`MentoraSelect` verified unaffected. A same-session architect handoff (from a teammate's independently-dispatched subagent) caught a real regression this task was about to introduce into Task 17's `AiTutorViewModel` (quick-action prompt text resolving off a stale `Application` context the new mechanism can't reach) — fixed same-session, `AiTutorScreen`'s own call site now resolves it correctly. One Opus review round (after a first dispatch stalled/timed out and was retried) found 4 MEDIUM (4 more hardcoded English nav titles now visibly wrong post-switch, fixed; server content doesn't refresh on a language switch, disclosed and handed to Task 19 below; `Avatar` had no accessible name, fixed; `onRetryTapped` didn't retry stats, fixed) + 4 LOW (name-save errors now route to inline field copy per the D51 precedent; `MentoraSelect`'s hardcoded `loadingOptionsLabel` disclosed to Task 19; a regression test for the AiTutor fix; a new format-specifier-parity check added to the new `StringsParityTest`). Running the full instrumented suite (not just review) caught one more real bug review missed: `SettingsScreen`'s original `applicationContext as MentoraApplication` cast crashed under every instrumented test (this module's `testInstrumentationRunner` is globally `NoOpApplicationTestRunner`, which substitutes a plain `Application`) — fixed by threading `ThemeController` through `MentoraNavHost`'s own parameters instead, the same way `sdk` already is; a second, unrelated stale `"Settings (placeholder)"` test assertion was also found and fixed. Full account in `DECISIONS_LOG.md` D93. Verified: `:shared:testDebugUnitTest` 249/249 (zero diff in `mobile/shared`); `:androidApp:testDebugUnitTest` 233/233; `:androidApp:assembleDebug` clean; `:androidApp:connectedDebugAndroidTest` 91/91 on the real emulator (a first full run surfaced the `SettingsScreen` crash above plus 4 unrelated screenshot-capture flakes in Task 8's own component-kit tests — confirmed as the same documented emulator-load pattern by killing/relaunching the AVD fresh and re-running clean). `PlaceholderScreens.kt` deleted entirely — every one of Phase 4's 18 in-scope screens now has a real implementation. `git status` scoped to `mobile/androidApp/**` only — `shared` untouched. |
| 19 | Localization/RTL/theme/font-scale QA sweep + Compose UI test suite completion | DONE — WIP checkpoint `1fc9fae` (2026-09-15) finished after a session-interruption recovery: the resumed session's own uncommitted work (StringsParityTest regex widening, `MentoraRootScreen` loading-label fix, 3-ViewModel locale-reload trim) was recovered and its own left-incomplete piece (6 new `retryLabel`-class string resources added to `strings.xml` but never wired into `AnswerOption`/`QuestionCard`/`CertificateCard`/`CourseProgressCard`) finished; one stale `ExploreViewModelTest` assertion (testing the old pre-trim reload behavior) fixed; full `:androidApp:connectedDebugAndroidTest` run for the first time against this commit (105/105, real emulator + real local backend); a representative (not exhaustive) RTL/Dark-theme/font-scale live spot-check across 9 of 18 screens. See `DECISIONS_LOG.md` D94 for the full account, including honestly-disclosed spot-check-not-exhaustive-sweep limitations. |
| 20 | Live emulator verification, `androidApp/README.md`, Phase 4 → Phase 5 handoff | DONE — live-verified 16 of 18 screens this session (Task 19's 9-screen spot-check + Course Player/AI Tutor/Learning Paths segment/Learning Path Details/Certificate Detail here), Quiz/Quiz Results disclosed as not re-verified this session (already passing in the 105/105 instrumented run, own Task 14 review round). New `mobile/androidApp/README.md`. See `DECISIONS_LOG.md` D95 and `PHASE_HANDOFF.md`'s new Phase 4 entry. **Phase 4 is now COMPLETE.** |

**Next immediate action:** Finish Task 19 per the "TASK 19 CHECKPOINT" bullet at the top of this file's
EXACT RESUME POINT section (the authoritative, most current account of exactly what remains). The
scope description below is Task 19's original, still-accurate planned scope as handed off from Task 18
(D93), kept for reference — the checkpoint bullet above additionally covers what's already been done
against it and what a fresh review pass should re-verify:
1. **Stale-locale-content gap**: server-provided content (Explore's course list, My Learning, Course
   Details, ...) does not refresh when a student switches language via Settings — the relevant
   ViewModels don't observe `sdk.user.observeLocale()`, and `MentoraNavHost`'s `popUpTo{saveState=true}`
   tab-switch mechanism means an already-loaded screen's ViewModel survives a locale change untouched,
   so its stale-locale payload keeps rendering (worst case: a paginated list can end up half-English/
   half-Arabic if a later page loads after the switch). Needs either locale-keyed ViewModel invalidation
   across the affected screens, or an explicit, disclosed decision that this is acceptable for MVP scope
   with a documented workaround (leave-and-re-enter the tab, or restart the app).
2. **`MentoraSelect`'s `loadingOptionsLabel` default** (`ui/components/MentoraSelect.kt`) is a raw,
   hardcoded English literal — pre-existing since Task 5, unreachable from any current call site (every
   Select in the app today is static), but worth sweeping to a real string resource while doing the
   broader localization pass.

Also verify live, in the browser/emulator, what D93's own code review could only reason about
statically: with the OS device locale set to English and the in-app language switched to Arabic via
Settings (now genuinely possible for the first time this phase, via `locale/LocalizedContent.kt`) —
bottom nav + `MentoraTopBar` back-arrow mirroring, Explore's chip/filter row, Course Player's
`CurriculumBottomSheet` + `PlayerControls` (which deliberately forces LTR — confirm that override still
wins), Quiz, Certificates (mirrored timeline + `CertificateDetailScreen`'s forced-LTR widget, plus the
share sheet), AI Tutor bubble alignment, and IME/text-selection handles on `MentoraTextField` (View-layer
popups that stay LTR regardless of the in-app override). `LocalizedContentTest.kt`'s 4 assertions were
verified once on a real device during Task 18 (91/91 clean, including that file), but a full per-screen
visual pass across the whole app in this exact configuration has not been done.
**Navigation-registration checklist, now proven four times (D90, D91, D92, D93)**: before wiring any new
destination reachable from more than one already-multi-graph screen, compute the FULL transitive push
closure from that destination (not just its immediate child) and grep every graph block in
`MentoraNavHost.kt` that registers each screen in that closure, confirming every one is registered under
every graph the CHAIN is reachable from — not just the one the implementer happened to test from. This
has now caught one real HIGH bug (D90) and been proactively applied and verified clean three times since
(D91, D92, D93) — keep doing it. **Instrumented-test-run note (from Tasks 17-18):** a
`connectedDebugAndroidTest` run can fail on a Gradle/Windows tooling error unrelated to any test itself
(an MD5-hash failure on a stale output file, or a locked logcat-output file left by an orphaned daemon
after a prior aborted run) — if the failure is in Gradle's own task execution (not a JUnit test result)
immediately after a prior interrupted/aborted instrumented run, try `./gradlew.bat --stop` + clearing
`androidApp/build/outputs/androidTest-results`/`androidApp/build/reports/androidTests` before assuming a
real regression and re-diagnosing further. **Standing requirement for every remaining screen task**: add
real `values-ar/strings.xml` translations for every new string resource AS PART OF that task's own
deliverable — held clean through Task 18 (`StringsParityTest.kt`, new this task, now enforces this
programmatically in the JVM unit gate, not just by discipline). Known disclosed limitation to
carry forward: `AppDialog`'s scrim may flash briefly over-dimmed on open before settling to the
correct steady-state color (see Task 8 entry) — not blocking, not yet re-verified further.
`CourseCard` has no price slot (Task 8 gap) — Course Details built its own inline price formatting
rather than extending that component. **Test-infrastructure note for any future instrumented test
work**: `NavigationShellTest`'s `registerFreshRealStudent()` accumulates throwaway
`t11-navshelltest-*@example.com`/`t12-navshelltest-*@example.com` accounts in the shared local dev
MongoDB on every real run (no delete-account/unenroll endpoint exists to call from an instrumented
test) — sweep manually via `mongosh` before using that database for a demo, same precedent as Task
7's own cleanup. A rare, unreproduced `AUTH_TOKEN`-unrelated `EmailAlreadyRegistered` 409 was observed
once during Task 12's review (possible double-dispatched `POST /auth/register`, disclosed in D84) —
if it recurs with a reproducible pattern, it's `shared`/backend territory, not a `NavigationShellTest`
bug. Long instrumented-test sessions (many consecutive `connectedDebugAndroidTest` runs) can degrade
the emulator's own responsiveness (`adb shell uptime`'s load average climbing) and cause
`PixelCopyException`/`ComposeTimeoutException` flakes in screenshot-capture-based tests unrelated to
any real code change — confirmed again in Tasks 12 and 18 (D84, D93); kill and relaunch the AVD fresh
if a full-suite run shows failures only in tests unrelated to what you just changed, before assuming a
regression.

---

## PHASE 5 — iOS

**Status:** **DEFERRED / PARTIALLY IMPLEMENTED** (started 2026-09-18, deferred 2026-09-20 by explicit user
project decision — see the "PHASE 5 FREEZE" box near the top of this file). T1-T11 are real, CI-confirmed
DONE; T12-T23 are NOT started and are not currently planned to be worked on. This is not a PASS/COMPLETE
and not an abandonment — iOS may resume at the user's own future request. Planning complete:
`execution/PHASE_5_ACCEPTANCE_CRITERIA.md`
(categories A-J), `execution/PHASE_5_IOS_SYSTEM_DESIGN.md` (24 sections), `execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md`
(dependency-ordered task list). Reviewed twice before any code — Opus (7 substantive + 9 minor findings,
all fixed) then Codex as an independent second opinion (3 more findings, all fixed). See `DECISIONS_LOG.md`
D96 for the full account.

**EXACT RESUME POINT.** This machine has no macOS/Xcode/iOS Simulator (Windows host, confirmed). The user
was asked directly whether Mac access exists and answered: not yet, will get it later. Per that answer,
this phase executes only the tasks genuinely completable and verifiable on Windows —
**T1, T1b, T2, T3, T4a** — then stops cleanly. **T4b and every task after it (T5-T23) are blocked** until
the user confirms Mac access; do not author them blind. See `PHASE_5_IOS_IMPLEMENTATION_PLAN.md § 1`/`§ 5`
for the full host-tagging rationale (every task from T4b onward needs a real Mac to compile/run/verify
Swift at all).

### PHASE 5 — Task Breakdown

| # | Task | Status |
|---|---|---|
| T1 | `:shared` iOS enablement (`iosMain` wiring, XCFramework, gitignore) | **DONE — CI-1 GREEN** (Windows gates green as before; **genuinely compiled and linked for iOS for the first time in CI-1 run #6**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35386243611 — `compileKotlinIosSimulatorArm64`/`compileKotlinIosArm64`/`linkDebugFrameworkIosSimulatorArm64`/`linkDebugFrameworkIosArm64`/`assembleSharedDebugXCFramework` all succeeded for real) |
| T1b | `IosTokenStorage` Keychain hardening (checked `OSStatus`, injectable failure seam) — added after the Codex review round, D96 | **PARTIAL** (authored/reviewed on Windows per D97: K1-K6 implemented — single-JSON-item Keychain storage, every `OSStatus` inspected, no thrown exceptions across the Swift boundary, `KeychainStatus.failures` publish channel, `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`, defaulted `KeychainStore` test seam; new `iosTest` FakeKeychain-driven failure-injection tests written. Windows gates green — `:shared:testDebugUnitTest` 249/249 unchanged, `:shared:assembleDebug` clean, `git diff mobile/shared/src/iosMain` limited to `IosTokenStorage.kt`/`Keychain.kt`, `commonMain` untouched; `:shared:compileKotlinIosSimulatorArm64`/`:shared:iosSimulatorArm64Test` both SKIPPED (no Kotlin/Native toolchain here, expected). **Review round 2 (Opus) applied per D98**: fixed a compile-blocking `EXPOSED_PARAMETER_TYPE` error (public constructor defaulting an `internal`-typed parameter), the `saveTokens` existence-check race and its unconditional-purge-on-update-failure bug, the `clearTokens` tombstone `errSecItemNotFound` misclassification, two K3 throw-across-the-boundary violations, an inherited Phase 3 `CFBridgingRelease` cinterop leak, and `KeychainStatus.failures`'s `StateFlow` equality-conflation bug (now a `SharedFlow`); `FakeKeychain` rewritten stateful and test coverage expanded accordingly. Windows gates still green after these fixes. Still Mac-unverified — that has not changed. **MC-1 must still compile it and run `:shared:iosSimulatorArm64Test`; MC-2 closes the live round-trip/logout-failure verification** before this can become DONE. **Review round 3 (a second Opus verification pass) applied per D99**: closed two D98 claims that were only partially true (the `saveTokens` existence-probe-failure edge case, and the silent Keychain-read bridging-failure case), plus one newly-found pre-existing `kSecAttrAccessible` search-scoping bug (present since D97, not introduced by D98). All low-severity/fail-safe, not security holes. Windows gates still green after these fixes — `:shared:testDebugUnitTest` 249/249, `:shared:assembleDebug` clean. **CI-1 run #6 (https://github.com/HeshamMohamed94/Mentora/actions/runs/35386243611) genuinely compiled `IosTokenStorage.kt`/`Keychain.kt` for iOS and ran `:shared:iosSimulatorArm64Test` for real — all 18 Keychain failure-injection tests passed on actual Kotlin/Native, zero failures reported.** Upgraded to **DONE** for the compile/unit-test half; the live round-trip (log in, terminate, relaunch; forced-failure logout) still needs MC-2 on a real simulator. **CI run #11 then found the honest limit of that "DONE": all 18 tests exercise `IosTokenStorage` against `FakeKeychain`, never `SecurityFrameworkKeychain` itself — the real `platform.Security`-calling code had never actually executed until T4b's `restoreSession()` called into it for the first time on real hardware, and it crashed immediately and deterministically (`kotlin.TypeCastException: class kotlinx.cinterop.CPointer cannot be cast to class platform.Foundation.NSString` in `baseQuery()` — every `kSecXxx` constant is a raw un-bridged cinterop `CPointer`, not a genuine Kotlin/Native `NSString`, even though `CFStringRef`/`NSString*` are toll-free-bridged at the ABI level). Fixed per D109: all 13 raw-constant usages (9 explicit `as NSString` casts plus 4 previously-uncast dictionary-value usages of the same bug class) now go through a `kotlinx.cinterop.interpretObjCPointer`-based non-retaining reinterpret cast, deliberately not `CFBridgingRelease` (which would wrongly release a borrowed, process-lifetime framework constant — see D109 for the full "Get Rule" vs "Create Rule" reasoning). `:shared:testDebugUnitTest` re-confirmed 249/249 unchanged; this fix is Kotlin/Native-only and cannot be compile-verified on Windows — **CI run #12 is the real verification**, of both this fix and of whether `restoreSession()` now completes instead of crashing. The compile/unit-test-half "DONE" from CI run #6 was accurate for what it actually tested; it did not mean the real Security-framework code path had been exercised, and D109 is explicit that this gap existed and was only closed by this fix, not previously. **Pre-push Opus review of the D109 commit then found that fix was correct but incomplete (D110): six more `... as CFDictionaryRef` casts in the same file (`add`/`update`/`delete`/`exists`/`copyMatching`) are the mirror-image bug — destination type is a plain Kotlin `CPointer`, not an Objective-C type, so Kotlin/Native's `as` takes its non-ObjC-aware path and an `NSMutableDictionary` fails that check too, predicted to crash the same call chain one step further on the next real run. Fixed per D110 before pushing: `baseQuery()`/`add()`/`update()` now build genuine `CFDictionary`s via `CFDictionaryCreateMutable`/`CFDictionaryAddValue` instead of `NSMutableDictionary` (no dictionary cast needed anywhere any more), `kSecReturnData` now uses the real `kCFBooleanTrue` constant instead of a bridged Kotlin `true` literal (a second, independently-found issue fixed for free by the same rewrite), and all five `KeychainStore` methods now `runCatching` their bodies so a future interop mistake degrades to an `errSecParam`/`KeychainReadResult` failure instead of crashing the process (K3). D110 also corrects two D109 documentation inaccuracies (`interpretObjCPointer`'s retain/release characterization, and which parts of the toolchain are readable-from-source on this Windows host) and one count correction — `IosTokenStorageTest.kt` has **20** `@Test` functions, not 18, confirmed by direct count with no `@Ignore`s. `:shared:testDebugUnitTest` re-confirmed 249/249, `:androidApp:testDebugUnitTest` re-confirmed 241/241, both unaffected (this fix is entirely inside `iosMain`). Same as D109: finding #1 here was never an observed CI crash, only strong pre-emptive reasoning. **A second pre-push review then found `runCatching`'s `getOrElse` was silently discarding the caught exception — fixed by logging interop failures (`01317e4`) so a remaining bug would still be diagnosable instead of silently degrading CI to a false green.** **CI run #12 (https://github.com/HeshamMohamed94/Mentora/actions/runs/35402451149, `main`@`01317e4`) is GREEN — every step succeeded, including `xcodebuild test` for the first time ever.** `restoreSession()` → `IosTokenStorage.readTokens()` → `SecurityFrameworkKeychain.copyMatching()` ran for real against the actual Security framework with zero crashes. **Upgraded to DONE** for the compile/unit-test/real-execution scope (see D111) — the live round-trip (log in against a real backend, terminate/relaunch, forced-failure logout) and the standing "no dedicated real-`SecurityFrameworkKeychain` `iosTest`" coverage gap both still need MC-2. |
| T2 | Token pipeline iOS output target (`MentoraTokens.swift`, `MentoraColors.xcassets`) | **DONE — CI-1 GREEN** (Windows-verified as before; **`xcodebuild` genuinely compiled `MentoraTokens.swift`/`Color+Mentora.swift` and `actool` genuinely compiled `MentoraColors.xcassets`' 46+5 colorsets in CI-1 run #6**; rendering fidelity in the actual simulator UI still pending MC-3) |
| T3 | Icon set (`MentoraIcons.xcassets`, 42 glyphs) + mirroring data | **DONE — CI-1 GREEN** (Windows-verified as before; **`actool` genuinely compiled all 42 SVG imagesets in CI-1 run #6** with zero asset-catalog errors; rendering fidelity in the actual simulator UI still pending MC-3) |
| T4a | Xcode project scaffold (`project.yml`, SPM wrapper, scripts, `Info.plist`, `.gitignore`) | **DONE — CI-1 GREEN** (Windows gates green as before; **`xcodegen generate` produced a working `iosApp.xcodeproj` and `xcodebuild build` genuinely linked the app for the Simulator in CI-1 run #6** — the SPM `binaryTarget` path, the debug-only ATS exception, and the `SWIFT_ACTIVE_COMPILATION_CONDITIONS: DEBUG` config all resolved correctly; one real gap found and fixed along the way, see D106 — an unrequested XcodeGen `ASSETCATALOG_COMPILER_APPICON_NAME` default required disabling since no app icon has been designed yet) |
| T4c | **GitHub Actions macOS CI** (`.github/workflows/ios-ci.yml`) | **DONE — first green run achieved** (run #6, https://github.com/HeshamMohamed94/Mentora/actions/runs/35386243611, `main`@`4c5c132` — every real step succeeded: Gradle KMP compile/link/**iosSimulatorArm64Test** (T1b's 18 tests, real pass)/XCFramework assembly, `xcodegen generate`, `xcodebuild build`, `xcodebuild test` (the placeholder XCTest target), the `swift-api-digester` Swift-surface dump, and the interface-artifact upload. Runs #1-5 found and fixed 5 real, previously-invisible defects along the way — see D102-D106. This is the CI-1 checkpoint closing for real.) |
| T4b | Swift app bootstrap (SDK/session/locale/theme wiring) | **IMPLEMENTED, FIX ROUNDS APPLIED — PENDING CI (not DONE).** Authored on Windows against the real CI-run-#6 `kmp-swift-interface` artifact, not guessed. `MentoraApp.swift` rewritten; `Support/AppEnvironment.swift`, `SessionController.swift`, `LocaleController.swift`, `ThemeController.swift`. See `DECISIONS_LOG.md` D108 for full detail, including a self-discovered deviation (`SetLocaleUseCase.invoke(locale:)` is itself a suspend function). **A pre-CI Opus review found 9 genuine bugs (fix round #1)**; **a Codex review found 1 more real bug (fix round #2, Fix A: cross-account `getProfile` data-isolation guard)**; **CI run #7's real compiler error (fix round #3) found a SKIE-internal `__emit` naming detail no static header could show**; **CI run #8's real compiler error (fix round #4) then proved the original D108 finding that motivated a manual `KotlinFlowBridge.swift` Flow-bridging workaround was itself wrong** — `observeAuthState`/`observeLocale`/`KeychainStatus.failures` really return genuine SKIE `SkieSwiftStateFlow`/`SkieSwiftSharedFlow` `AsyncSequence`s (the original claim was based on reading an intermediate SKIE build-cache header, not the real shipped XCFramework's `.swiftinterface`). Fix round #4 deleted `KotlinFlowBridge.swift` entirely and rewrote all three subscriptions as plain `for await` loops inside each controller's existing `@MainActor`-isolated `Task { }`; this also makes the fix round #1/#2 findings about the manual bridge moot (the code they were about no longer exists). `:shared:testDebugUnitTest` (249/249)/`:androidApp:testDebugUnitTest` (241/241) re-confirmed unaffected across every fix round (no Kotlin touched). **CI run #9 (commit `5f21c53`) then compiled and linked the entire `iosApp` Swift target for the first time and `xcodebuild build` passed** — fix round #4's `for await` rewrite is now compiler-confirmed, including `LocaleController.swift`'s `observeLocale` subscription. The job then failed at "xcodebuild - run the XCTest unit target" with a genuine runtime crash (not a compile error): `AppEnvironment.swift:96`'s `AppEnvironmentKey.defaultValue` hard `fatalError()` fired for real during `xcodebuild test`'s launch of the real `iosApp` binary as `iosAppTests`'s test host, before any test method ran. **Fix round #5 (D108)** replaced that trap with a non-crashing `AppEnvironment?`/`nil`-default pattern (`PlaceholderRootView` treats `nil` as visually identical to `.unknown`, with a debug-only `assertionFailure` — never a release crash — the first time the `nil` case is actually rendered); see D108 fix round #5 for the full verbatim CI log and the confirmed-vs-not-confirmed root-cause discussion. `:shared:testDebugUnitTest` (249/249)/`:androidApp:testDebugUnitTest` (241/241) re-confirmed unaffected. No Swift can compile/link/run on this Windows host — a real green `ios-ci.yml` run (CI run #10, MC-1) is required before this moves to DONE, specifically to confirm `xcodebuild test` now gets past app launch and executes `ScaffoldPlaceholderTests.testScaffoldCompiles()`, and whether a zero-arg `iosSimulator()` SKIE overload exists (still open per D108); live behavior (Keychain round-trip, session restore, actual simulator rendering) still needs MC-2/MC-3 after that. **Fix round #6 (CI run #10, `@ViewBuilder` compile error in fix round #5's own debug-assertion code) fixed and pushed.** **CI run #11 (commit `8d607ea`) then reached a genuine milestone: `xcodebuild build` succeeded for the entire `iosApp` Swift target for the first time, and the app actually launched in the simulator (`FirstFramePresentationMetric` confirms a real first frame rendered) — T4b's own Swift bootstrap code is confirmed sound.** The run then crashed, but in T1b's `Keychain.kt` (a pre-existing D97 bug, not a T4b defect) when `AppEnvironment`'s cold-start `Task` called `restoreSession()` for the first time on real hardware — see D109 for the full root cause and fix; no T4b file changed for it. **CI run #12 (https://github.com/HeshamMohamed94/Mentora/actions/runs/35402451149, `main`@`01317e4`) is GREEN — every step succeeded in 10m 18s, including `xcodebuild test` for the first time ever.** T4b's cold-start sequence (SDK creation, `restoreSession()`, locale seeding, the three `for await` subscriptions) runs to completion with zero crashes. **Upgraded to DONE** for the `W-auth`/`C-verify` scope the Implementation Plan specifies (see D111 for the full 5-run arc). MC-2 (live simulator behavior against a real backend — login, session persistence across relaunch, forced Keychain-failure UI surfacing) and MC-3 (visual/RTL/Dynamic-Type/VoiceOver) remain Mac-gated and unstarted, exactly as always planned. T5 has not been started. |
| T5 | `SharedBridge` (`MentoraClient`, `ApiResult` unwrapping, Flow adapters, `MentoraError`, `ErrorCopy`) | **DONE for slice 1 of 2 (auth+user) — CI run #17 GREEN (https://github.com/HeshamMohamed94/Mentora/actions/runs/35412512167), every step succeeded including `xcodebuild test`, xcresult upload correctly skipped (no failures).** New `Support/SharedBridge/{MentoraError,ApiResultBridge,MentoraClient}.swift`, `Support/ErrorCopy.swift`, `iosAppTests/{ApiResultBridgeTests,ErrorCopyTests}.swift`; `mobile/iosApp/project.yml`'s `iosAppTests` target gained the `MentoraShared`/`Shared` package dependency the new tests need. `MentoraClient` this slice covers `auth` (register/login/logout/refreshSession/restoreSession/observeAuthState) and `user` (getProfile/updateProfile/observeLocale/setLocale/setTheme) only — the other 8 façades (catalog, enrollment, progress, quiz, certificates, learningPaths, media, aiTutor) plus AI-stream Flow adapters are an explicit, separate **slice 2** follow-up task, not started here. T4b's already-CI-green `AppEnvironment`/`SessionController`/`LocaleController`/`ThemeController` were refactored (deliberate, approved) to route through `MentoraClient` instead of raw `sdk.<facade>.<useCase>.invoke(...)` calls, preserving all existing behavior/comments; grep-audited afterward — zero remaining `.invoke(` outside `Support/SharedBridge/` in real code (only doc-comment references remain elsewhere). See `DECISIONS_LOG.md` D112 for the full account, including the real `ApiErrorCode.kt` `.wire` strings, the `register_` naming risk, and the "no SKIE default-argument overloads" finding that also confirmed T4b's `ApiTimeouts` comment. **Review-fix round applied against commit `2eb8b23` (D112's "review-fix round" subsection)**: `project.yml`'s `iosAppTests` package dependency gained `link: false` (avoid double-linking the static `shared.xcframework` into the test bundle); `unwrapList`/`unwrapPage` now throw a real `MentoraError.elementCastFailed` on a cast-count mismatch instead of only `assert`ing (Release-build silent-failure fix, I4); both bridge-self-generated `MentoraError` cases now log via `NSLog` before returning (mirroring `Keychain.kt`'s `logInteropFailure` pattern); `ApiResultBridgeTests.swift`'s two `testUnwrapPage*` tests' compile-blocking `AnyObject`-constraint type errors fixed (`CursorPage<NSString>`/`Page<NSString>`, not bare `String`); `ErrorCopyTests.swift` gained a real `ErrorCopy.allKeys`-vs-`key(for:)` invariant test (the old "23 distinct keys" test only checked its own fixture); `FlowBridge.swift` folded into `MentoraClient.swift` and deleted, with both `MentoraClient.sdk` and `AppEnvironment.sdk` now `private` (compiler-enforced `.invoke` boundary, not just grep-enforced). `link: false`'s effectiveness in this exact position and the slice-2 `ApiResult<NSArray<T>>` variance question are both open, flagged for the next real CI log / slice-2 author respectively. `:shared:testDebugUnitTest` (249/249)/`:androidApp:testDebugUnitTest` (241/241) re-confirmed unaffected (no Kotlin touched, this round either). No Swift compile/run possible on this Windows host — the next real `ios-ci.yml` run is this slice's actual verification. **F1 ("all 10 façades exercised") stays PARTIAL, not PASS** — only 2 of 10 façades are covered by this slice; F1 closes at the T23 project handoff once slice 2 lands. **CI run #15 crashed the test host on all 4 tests that pushed a real `ApiResult.Failure` through `ApiResultBridge.unwrap`, initially suspected as a possible PRODUCTION bug in SKIE's `onEnum(of:)` dispatch.** An Opus review and an independent Codex second opinion both caught that the initial investigation overclaimed its causal diagnosis; `unwrap` was rewritten regardless (an explicit `as?`-chain, `Failure` checked first, instead of `onEnum(of:)`) since both reviews judged it independently sound. **CI run #16 then RESOLVED the investigation for real**: all 12 `ApiResultBridgeTests` passed (0 failures) — the D115 production rewrite is real-CI-confirmed correct — and a new CI diagnostic step captured the actual crash report for the first time, naming the exact cause: a genuine Swift runtime "failed cast" trap on the diagnostic ladder's own `as! ApiResult<NSString>` force-cast (the identical operation the original D114 test code performed). **Root cause confirmed: a real, reproducible Swift/SKIE interop defect in that specific cast pattern — but with zero production call sites** (every real `ApiResult<T>` value arrives already correctly typed from a genuine SKIE call site; production code never force-casts one). SKIE's `onEnum(of:)` dispatch was never actually implicated — the two ladder tests that could have tested that (Stage 0, Stage 4b) both crashed on the cast itself, before dispatch was ever reached; they've been deleted, and the remaining cast-free stages (1-5) are kept as a permanent regression suite. See `DECISIONS_LOG.md` D115 section (g) for the full resolution, including the specific reviewer claim (SE-0057 "erasure means the cast can't fail") that this real evidence overturned. **T5 slice 1 is now CI-GREEN and DONE** for its own scope (auth+user façades) — F1 stays PARTIAL pending slice 2's remaining 8 façades. **T5 slice 2 (7 of 8 remaining façades — `catalog`/`enrollment`/`learningPaths`/`media`/`progress`/`quiz`/`certificates` — plus `aiTutor`'s `aiConversation(...)`) is now DONE — CI run #19 GREEN (https://github.com/HeshamMohamed94/Mentora/actions/runs/35415732362), every step succeeded including `xcodebuild test`, xcresult upload correctly skipped (no failures).** CI run #18 first caught one genuine, real compile error (`categories() -> [Category]`: `'Category' is ambiguous for type lookup` — Kotlin's exported `Category` collides with `objc/runtime.h`'s own `typedef struct objc_category *Category`, always implicitly visible via Obj-C interop; fixed identically to the existing `Section`/SwiftUI collision, module-qualified to `[shared.Category]`) — see `DECISIONS_LOG.md` D116's final addendum. `aiTutor`'s `aiStream(...)` (the Flow-returning send-message use case) is explicitly deferred to a follow-up commit for ordinary slice hygiene — its real signature (`SkieSwiftFlow<AiStreamResult>`) is in fact already known via `shared-api.json`, not unknown as an earlier draft of this entry claimed (corrected in `DECISIONS_LOG.md` D116(d)). 24 of the 25 new methods were copied verbatim from the real CI-run-#6-captured shipped SKIE `.swiftinterface`, not guessed; the 25th (`thumbnailURLString`, non-suspend) is instead sourced from `shared-api.json`/`shared.apinotes` in that same artifact; `ApiResultBridge.swift` gained `boxedInt(_:)` (the parameter-direction twin of `unwrapInt`, `Int32(clamping:)` per I4); `ApiResultBridgeTests.swift` gained real `unwrapInt`/`boxedInt` tests (previously deliberately left untested in slice 1) and a real `unwrapPage` element-cast-mismatch test (previously untested because no production code called `unwrapPage` at all — slice 2 gives it four real call sites). D112's open slice-2 `ApiResult<NSArray<T>>` variance question is now RESOLVED — no variance problem ever existed (see D116(b)). `project.yml` needed no change — `iosAppTests`'s `MentoraShared`/`Shared` dependency with `link: false` was already present from slice 1. Post-edit grep re-audit: zero remaining real (non-doc-comment) `.invoke(` outside `Support/SharedBridge/MentoraClient.swift`; `Features/`/`Components/` directories still don't exist (no screens built yet), so the "no `Kotlin*`/`ApiResult` outside the bridge" check is vacuously satisfied. `git diff --stat` confirms only `mobile/iosApp/` and `execution/` files changed — no `mobile/shared/` (Kotlin) file touched, so `:shared:testDebugUnitTest`/`:androidApp:testDebugUnitTest` are unaffected by construction. No Swift compile/run possible on this Windows host — the next real `ios-ci.yml` run is this slice's actual verification. **F1 ("all 10 façades exercised") stays PARTIAL, not PASS** even once this CI run is green — see `DECISIONS_LOG.md` D116(f): F1's required evidence is a per-façade table produced at T23, and requires a real screen-level call site, not just a compiled bridge method; all 10 façades now have a compiled call site (36/37 use cases, 37/37 once `aiStream` lands), removing the last structural blocker to F1 without itself satisfying it. See `DECISIONS_LOG.md` D116 for the full account. **T5 is now DONE in full**: `aiTutor`'s `aiStream(...)` (the Flow-returning send-message use case, the one deferral from slice 2) is now implemented in `MentoraClient.swift`, its real signature (`SkieSwiftFlow<AiStreamResult>`) confirmed via `shared-api.json` and cross-checked by an independent Opus review — all 10 façades / all 37 use cases are now bridged, closing T5's own task-level scope. No new test added (matches the `authStates()`/`localeChanges()` precedent — needs a live SDK). **F1 ("all 10 façades exercised") still stays PARTIAL**, not PASS: F1's required evidence (a per-façade table in the Phase 5 handoff mapping each façade to a real screen-level call site) is only produced at T23, and no `Features/`/`Components/` directory exists yet to call these bridge methods from a real screen — T5 DONE removes the last structural blocker to F1 but does not itself satisfy it. See `DECISIONS_LOG.md` D117. **CI run #20 GREEN (https://github.com/HeshamMohamed94/Mentora/actions/runs/35416234426), every step succeeded including `xcodebuild test`** — `aiStream`'s `shared-api.json`-sourced signature compiled correctly on the first try. **Task T5 is now fully DONE and CI-confirmed across all three commits** (slice 1, slice 2, the `aiStream` follow-up); T6 is the next unstarted task. |
| T6 | Design-system runtime (`MentoraTypography`/`MentoraShape`/`MentoraElevation`/theme-root wiring) | **✅ TASK T6 FULLY COMPLETE — all of 3a + 3b + 3c reviewed and real-CI-green.** **SLICE 3c (token gallery + completion-gate script + `ios-ci.yml` step) DONE — CI run #27 GREEN on the FIRST attempt** (https://github.com/HeshamMohamed94/Mentora/actions/runs/35450960410), no repeat of slice 3b's multi-round saga: the new "iOS source gates" step succeeded on macOS too, `xcodebuild build` succeeded (confirming `MentoraTokenGallery.swift`'s `#Preview` macros — the first anywhere in this repo — compiled clean with no `ENABLE_PREVIEWS` setting needed), `xcodebuild test` stayed green. Opus review before push found no blocking bugs (independently re-verified all 20 checks, hand-parsed the gallery's color/preview lists with a second parser, traced every Swift API against real declarations, YAML-parsed `ios-ci.yml`); minor fixes applied (a weakened Check E2 assertion strengthened, E2-E4 switched from raw to comment-stripped scanning for consistency, a soft-deprecated `.foregroundColor` call fixed, stale line-number citations corrected). Pushed as `657298f`. See the dedicated "T6 SLICE 3c" resume-note section appended after this row's history, and `DECISIONS_LOG.md` D122 for the full account. **✅ SLICE 3b (theme-root wiring) DONE — CI run #26 GREEN** (https://github.com/HeshamMohamed94/Mentora/actions/runs/35445837764), after 3 CI rounds (2 red on one test's own numeral-formatting methodology, never an app defect — see `DECISIONS_LOG.md` D121 for the full account). New `Theme/MentoraTheme.swift` (`MentoraThemeRules` pure namespace + `.mentoraTheme(theme:locale:)` `View` extension); `Support/LocaleController.swift`'s `currentLocale` changed from `AppLocale?` to non-optional `AppLocale`, seeded synchronously in `init` via `client.currentLocale()` before `localeWatcher` starts; `MentoraApp.swift`'s `WindowGroup` now renders a new private `MentoraRootView` (reads `@Environment(\.appEnvironment)`, applies `.mentoraTheme(...)`) above the untouched `PlaceholderRootView`; one doc-comment fix in `Theme/MentoraTypography.swift` (T7 → T6 slice 3b attribution for `ar-u-nu-latn`). `ThemePreference`/`AppLocale`'s real SKIE Swift shape (plain frozen enums, `Hashable, CaseIterable`, no sealed-class workaround) was verified from a CI artifact before writing any switch statement over them. `.system → nil` for `ColorScheme?` (deliberately diverges from Android's `resolveDarkTheme()` snapshot-resolution — `nil` keeps `.preferredColorScheme` tracking live OS changes); Arabic locale resolves through the one named `MentoraThemeRules.arabicLocaleIdentifier = "ar-u-nu-latn"` constant; layout direction is an explicit 2-case switch, not derived from `Locale.Language.characterDirection`; nil-locale handling uses `.transformEnvironment` (not `if/else`, to avoid a `_ConditionalContent` identity split). New `iosAppTests/MentoraThemeTests.swift` (6 test cases: color-scheme mapping, enum-case-count guard, layout-direction mapping, Arabic-locale language-subtag + cross-slice `MentoraTypographyRules.isArabic` integration, Arabic Western-numeral `NumberFormatter` formatting, and a real `UIHostingController`-hosted `\.locale`/`\.layoutDirection` environment-propagation round-trip reusing `MentoraTypographyGeometryTests.swift`'s harness pattern) — deliberately does not round-trip `.preferredColorScheme` itself (a SwiftUI preference, not a plain environment write; left to review + MC-3). Sheet/`fullScreenCover` inheritance: per System Design § 12, SwiftUI's `\.locale`/`\.layoutDirection` environment already propagates into sheets/alerts presented from the same hierarchy (a deliberate divergence from Android, which needed extra machinery to cross that boundary) — only a detached-context presentation is a real open risk, and § 14's "sheets/covers must inherit `.preferredColorScheme`" is an explicit MC-3 verification requirement, not yet a proven fact. Neither case is solved by this slice; an earlier draft of this note and of the code's own doc comments overclaimed a "every sheet must re-apply `.mentoraTheme`" contract that contradicted § 12 — corrected after Opus review (D121). See `DECISIONS_LOG.md` D121 for the full account, including the gate-pattern list recorded for slice 3c to consume later (scoped to production sources only — several of these patterns legitimately already appear in `iosAppTests/`). Self-verified on Windows only: generator re-run with zero drift (this slice doesn't touch the generator), and a manual grep of `mobile/iosApp/iosApp/**` production sources (excluding tests) confirmed every gate pattern (`preferredColorScheme(`, `environment(\.locale`/`transformEnvironment(\.locale`, `environment(\.layoutDirection`/`transformEnvironment(\.layoutDirection`, `.mentoraTheme(`, `Locale(identifier: "ar`) appears only where expected there. Independently reviewed to completion by Opus before push — no blocking bugs; fixes applied: a missing `import UIKit` in the test file, the D6 sheet/cover doc overclaim, an overclaimed "eliminates the [locale] race" framing (a residual first-launch English/LTR window on a truly fresh install is real but non-blocking, since nothing renders text/direction yet — recorded in D121), the gate-pattern test-scope correction, an overclaimed `.xcstrings`-lookup comment, and two test-quality gaps (a vacuous-pass guard, an Arabic-numeral negative control). No `mobile/shared/**` or `mobile/androidApp/**` touched; no `Info.plist` change; no token gallery or completion-gate script (that's slice 3c). **CI-GREEN on the third push — run #26** (https://github.com/HeshamMohamed94/Mentora/actions/runs/35445837764). CI runs #24 and #25 were both RED on the same test's own numeral-formatting methodology, never an app defect — the actual production code (`MentoraTheme.swift`, `LocaleController.swift`, `MentoraApp.swift`) compiled clean and 5/6 `MentoraThemeTests` cases passed on the very first attempt (run #24). Round 3's diagnostics-only probes (logged, never asserted) revealed the real story: round 2's negative control failed because `Locale(identifier: "ar-u-nu-arab")` is malformed by Foundation's parser on this platform (canonicalizes to `"ar-u-NU"`, drops the `arab` value, `numberingSystem` reports garbage, formatter returns `nil`) — not because of any `.none`/`.decimal` distinction; the legacy `@numbers=arab` locale-extension syntax works correctly where the modern `-u-nu-arab` form did not. Full three-round account in `DECISIONS_LOG.md` D121. **Slice 3a (shapes/elevation/generator addendum) DONE — CI run #23 GREEN** (https://github.com/HeshamMohamed94/Mentora/actions/runs/35440525334). See the dedicated resume note appended after this row's history (search `T6 SLICE 3a — DONE, CI-GREEN`) for the full account. In short: slice 3a's code is complete, self-verified (generator idempotency proven, one real defect found and fixed pre-review — elevation `radius`/`y` were hand-duplicating generated token values instead of delegating to them), independently reviewed to completion by Opus (D120, no blocking bugs, three medium findings all fixed: dark-mode elevation default, an alpha-0 RGB assertion, and a structural shadow-color-accessor drift risk), pushed as `7bfc4f3`, and passed real macOS CI on the first attempt — `xcodebuild` build + `xcodebuild test` both succeeded, crash-diagnostic clean, xcresult upload correctly skipped (no failures). This is the first real compile of any slice-3a Swift code. Slices 3b (theme-root wiring, touches KMP) and 3c (token gallery + completion-gate grep script) are separate, later sub-slices, not started, per the architect's own recommended 3-way split of slice 3 for CI-round hygiene. **Slice 2 (Dynamic Type geometry tests) DONE — CI run #22 GREEN (https://github.com/HeshamMohamed94/Mentora/actions/runs/35419859729), all 21 XCTest cases passed (14 slice-1 + 7 slice-2) on the first real CI attempt, including the fixed tracking algebra and the previously-unverified Arabic-branch tolerance.** New `iosAppTests/MentoraTypographyGeometryTests.swift` (7 XCTest cases) — the first test in this codebase to host and measure real rendered SwiftUI geometry (`UIHostingController.sizeThatFits(in:)`), proving slice 1's pure-function formulas actually reach rendered `Text` end to end. Every assertion is a same-run ratio/differential or an en-vs-ar difference over identical ASCII text, never an absolute font metric, since CI's simulator OS version isn't pinned. Opus review caught and fixed one confirmed blocking bug (the tracking test's original single-differential algebra didn't isolate tracking from glyph-advance width — would have failed on every style; fixed via a double en/ar differential) plus minor doc/harness fixes. One master-plan requirement ("leading ratio preserved") was reinterpreted as measured scale-invariance rather than literal token-ratio equality, since the literal reading is unachievable against real font metrics — documented as an explicit engineering call in `DECISIONS_LOG.md` D119, which has the full account including the accepted/monitored Arabic-tolerance risk and the now-dead-code follow-up item for slice 3. No `mobile/shared` (Kotlin) or `mobile/androidApp/` file touched. **Slice 1 (typography arithmetic) DONE — CI run #21 GREEN (https://github.com/HeshamMohamed94/Mentora/actions/runs/35418390771), every step succeeded including `xcodebuild test` (all 14 `MentoraTypographyTests` cases), xcresult upload correctly skipped (no failures).** New `Theme/MentoraTypography.swift` (`MentoraTextStyle`, `MentoraTypographyRules`, `MentoraFontModifier`, `.mentoraFont(_:)`) and `iosAppTests/MentoraTypographyTests.swift` (14 XCTest cases). Named `MentoraTypographyRules` (not `MentoraTypography`) to avoid a redeclaration collision with T2's already-generated `Theme/MentoraTokens.swift#MentoraTypography`. The `§ 15.1` line-height formula is copied verbatim, not re-derived — an earlier, subtly-wrong form of it was found to go negative at accessibility sizes and was explicitly withdrawn in the design document; the new tests are its regression guard. **Opus review (before any CI push) caught one real compile-breaking bug (missing `import SwiftUI` in the test file — module imports aren't transitive) and one real coverage gap (no original test exercised the accessibility-scale regime where the withdrawn formula actually failed) — both fixed**: `import SwiftUI` added, and `test_lineSpacingScalesCorrectlyAtAccessibilitySizes` added (8 scale factors × 12 styles × 2 locales, independently hand-verified by the reviewer). Also fixed: two doc-comment citations of a non-existent `theme-checks.js` enforcement script (now cite the T6 completion-gate grep instead), an off-by-one Android line citation, an inaccurate Android locale-detection characterization, and an added informational note on `naturalLineHeightFactor`'s Latin-vs-Arabic-face imprecision (MC-3 item, not a defect — review confirmed the core arithmetic and all 12 metric rows are correct). See `DECISIONS_LOG.md` D118 for the full account, including one real discrepancy adapted during implementation (`MentoraTypographyMetrics.fontWeight` is `CGFloat`, not the assumed `Int`). No `mobile/shared` (Kotlin) file touched. No Swift compile/run possible on this Windows host — the next real `ios-ci.yml` run is this slice's actual verification. **Slices 2 (Dynamic Type tests) and 3 (shape/elevation/theme-root wiring) are separate follow-up work, not started.** |
| T7 | Localization foundation: `Localizable.xcstrings` (en+ar, 279 keys), locale/direction environment, `MentoraStrings`, formatters, Node parity+specifier lint | **✅ TASK T7 FULLY COMPLETE — all 4 slices done, real-CI-green.** See "T7 SLICE 1/2/3/4" sections below. |
| T8 | Component Kit A (atoms — MentoraButton, MentoraIconButton, MentoraTextField, PasswordField, SearchField, MentoraToggle, MentoraSelect, Badge, CategoryChip, MentoraProgressBar, Avatar, MentoraTabs, MentoraSnackbar; `MentoraIcon`, the plan's 14th atom, was already built in T3) | **✅ TASK T8 FULLY COMPLETE — all 7 slices done, real-CI-green.** See "T8 SLICE 1"/"T8 SLICE 2"/"T8 SLICE 3"/"T8 SLICES 4+5"/"T8 SLICES 6+7" sections below. |
| T9 | Navigation shell (5-tab `TabView`, `TabRouter`, `AuthGate`/B8) | **✅ TASK T9 COMPLETE — mandatory Opus review applied, real-CI-green.** See "T9 — NAVIGATION SHELL" section below. |
| T10 | Auth screens (Login, Register — B1/B2/B8/B9) | **✅ TASK T10 COMPLETE — mandatory Opus review + a follow-up verification pass applied, real-CI-green.** See "T10 — LOGIN/REGISTER SCREENS" section below. |
| T11 | Component Kit B (17 composite components, 4 slices) | **✅ TASK T11 FULLY COMPLETE — all 4 slices done, real-CI-green.** Slice 1 = CourseArtwork system. Slice 2 = CourseCard family. Slice 3 = QuestionCard/AnswerOption/AITutorBubble/AITutorQuickAction/CheckoutSummary. Slice 4 = LoadingState/EmptyState/ErrorState/SuccessState/MentoraSheet/MentoraDialog — pushed as `adb8876`, CI run 35482199062 confirmed `success` (314/314 tests, 0 failures) at the "PHASE 5 FREEZE" resume (see that box near the top of this file, and D137). See "T11 SLICE 1"/"2"/"3" sections below for slices 1-3's full detail. |
| T12-T23 | Remaining screens through final acceptance audit | **NOT STARTED — Phase 5 DEFERRED as of 2026-09-20** (see "PHASE 5 FREEZE" box near the top of this file). Was previously planned under the parity-focused completion mode (D132): continuing automatically task-by-task without per-task approval stops, small-batch implementation, tiered review (mandatory for auth/session/architecture/cross-cutting infra, lighter self-review+CI for ordinary screen work), authored on Windows, compiled by CI. That plan still applies if/when the user resumes iOS work; it is not currently active. Live/visual verification still needs a Mac (MC-2/MC-3/MC-4) and is recorded as NOT TESTABLE, never fabricated. |

## T6 SLICE 3a — DONE, CI-GREEN (2026-09-19)

**Exact task/slice:** Phase 5, Task T6 (Design-system runtime), Slice 3 of 3 (shapes/elevation/theme-root wiring), sub-slice **3a** of the architect's own 3a/3b/3c split (3a = generator addendum + `MentoraShape` + `MentoraElevation`, zero KMP surface; 3b = theme-root wiring, the only KMP-touching part, not started; 3c = token gallery + completion-gate grep script + `ios-ci.yml` step, not started).

**What is complete:**
- `tools/token-pipeline/generate.js`: additive generator changes — new `iosBorderWidthLines()` + `MentoraBorderWidth` enum template block; new `iosShadowColorExtensionLines()` + 5 `mentoraShadowElevation0..4` Swift accessors appended to the existing `Color` extension template. Regenerated output verified idempotent (ran twice, zero further diff, re-confirmed independently by the reviewer on this same host) and correctly scoped (`git status --porcelain` after regeneration touches only the iOS-target generated files, no web/Android drift).
- `mobile/iosApp/iosApp/Theme/MentoraTokens.swift`, `Color+Mentora.swift` — regenerated from the above.
- `mobile/iosApp/iosApp/Theme/MentoraTypography.swift` — dead `#if DEBUG MentoraScaledSizeProbe` block deleted (unused after slice 2 built its own geometric probe); rest byte-identical.
- `mobile/iosApp/iosApp/Theme/MentoraShape.swift` (new) — `MentoraShape: InsettableShape`, radius-step-keyed, `.sheetTop` uses `UnevenRoundedRectangle` for the real top-corners-only bottom-sheet shape (distinct from `.xlarge`'s all-corners dialog shape). Reviewer hand-verified every geometry probe point and the `InsettableShape`/`inset(by:)` conformance — correct as implemented.
- `mobile/iosApp/iosApp/Theme/MentoraElevation.swift` (new) — `MentoraElevationLevel`, `.mentoraElevation(_:in:fill:border:)` view modifier. Elevation-delegation fix (radius/y read through generated `MentoraTokens.swift#MentoraElevation` steps) confirmed complete — zero remaining duplicated literals.
- `mobile/iosApp/iosAppTests/MentoraShapeTests.swift`, `MentoraElevationTests.swift` (new) — drift tests, geometry probes, real asset-catalog resolution test. The `Bundle.main`-in-`iosAppTests` resolution strategy was reviewed and judged safe (host-based unit-test target, confirmed empirically by slice 1/2's already-CI-green `@testable import iosApp` tests, which cannot link without `BUNDLE_LOADER` pointing at the host app).
- Completion-gate grep clean, scope correct — no `mobile/shared/**`, `mobile/androidApp/**`, `project.yml`, or `ios-ci.yml` touched.

**Review status: DONE.** A full from-scratch Opus review (D120) ran after this session's resume — no blocking bugs found; generator additivity, token values, `.sheetTop`/`.xlarge` distinction, and the elevation-delegation fix were all independently re-verified, not just trusted. Three medium findings, all fixed:
1. `MentoraElevationTests.swift`'s alpha-0 (`.level0`) RGB assertions were semantically meaningless and a possible false-failure risk — removed; alpha tolerance widened `0.002` → `0.005`.
2. `.mentoraElevation(...)`'s default `fill` was `.mentoraSurfaceDefault`, which silently defeated dark-mode tonal elevation per `design-tokens.json#/elevation/darkModeNote` — changed to `.mentoraSurfaceElevated` (no-op in light mode, real fix in dark mode).
3. `shadowColor` and `shadowColorAssetName` were two independently hand-maintained mappings that could have silently diverged — `shadowColor` now derives from `shadowColorAssetName` structurally, closing the gap by construction rather than by adding a test.

Also fixed (low-severity): `[MentoraElevationLevel: Double]` opacity dicts retyped to `CGFloat` (removes reliance on an unprecedented implicit conversion in a generic `accuracy:` binding); `UITraitCollection(userInterfaceStyle:)` (deprecated on iOS 17) replaced with `UITraitCollection(mutations:)`. Full account in `DECISIONS_LOG.md` D120.

Generator idempotency and the completion-gate grep were re-verified after applying all fixes.

**CI: GREEN — run #23** (https://github.com/HeshamMohamed94/Mentora/actions/runs/35440525334), triggered by push of `7bfc4f3` (review fixes, on top of the `d9d97b8` checkpoint) to `origin/main`. This is the first real compile of slice 3a's Swift code (none had compiled on the Windows authoring host) and it passed clean on the first attempt: `xcodebuild - build the app for the Simulator` and `xcodebuild - run the XCTest unit target` both succeeded (covering `MentoraShapeTests`/`MentoraElevationTests` plus slice 1/2's suites in the same target), the crash-diagnostic step was clean, and the xcresult-upload-on-failure step correctly skipped (no failures).

**What remains (as of this slice):**
- Sub-slices 3b (theme-root wiring — the only part of slice 3 with a real KMP-shape risk, since no
  Swift code anywhere in this repo had yet verified how SKIE bridges a Kotlin `enum class` like
  `ThemePreference`/`AppLocale`) and 3c (token gallery, completion-gate grep script) were both fully
  unstarted at this point. **See the "T6 SLICE 3b" section below for 3b's status — it is no longer
  unstarted.**

Full per-task scope/files/criteria/tests/verification/completion-gate detail lives in
`execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md § 4` — read it before starting any task, do not re-derive
scope from memory.

## T6 SLICE 3b — DONE, CI-GREEN AFTER 3 ROUNDS (2026-09-19)

**Exact task/slice:** Phase 5, Task T6 (Design-system runtime), sub-slice **3b** of the architect's
3a/3b/3c split — theme-root wiring, the only part of slice 3 that touches a KMP-bridged enum type.

**Real SKIE shape verified before writing any switch statement (per this slice's own precondition):**
`ThemePreference` (`.light`/`.dark`/`.system`) and `AppLocale` (`.english`/`.arabic`) are both plain
frozen Swift enums, `Hashable, CaseIterable` — independently confirmed from a CI artifact, not guessed.
No sealed-class workaround needed (unlike `ApiResult`, D115) — `Category` was ambiguous with Obj-C
runtime types (D116), but neither `ThemePreference` nor `AppLocale` collides with anything.

**What is complete:**
- `mobile/iosApp/iosApp/Support/LocaleController.swift` — `currentLocale` changed from `AppLocale?` to
  non-optional `AppLocale`, seeded synchronously in `init` from `client.currentLocale()` before
  `localeWatcher` starts (closes the "no ordering guarantee the watcher runs before first paint" gap).
- `mobile/iosApp/iosApp/Theme/MentoraTheme.swift` (new) — `MentoraThemeRules` (pure namespace:
  `colorScheme(for:)`, `arabicLocaleIdentifier`, `foundationLocale(for:)`, `layoutDirection(for:)`) and
  `extension View { func mentoraTheme(theme:locale:) }`. `Theme/` stays free of `AppEnvironment`,
  matching every other file in that directory.
- `mobile/iosApp/iosApp/MentoraApp.swift` — `WindowGroup`'s content is now a new private
  `MentoraRootView` (reads `@Environment(\.appEnvironment)`, applies `.mentoraTheme(...)`) wrapping the
  byte-identical, untouched `PlaceholderRootView`.
- `mobile/iosApp/iosApp/Theme/MentoraTypography.swift` — one doc-comment fix (mis-attribution to "T7"
  corrected to T6 slice 3b), no behavior change.
- `mobile/iosApp/iosAppTests/MentoraThemeTests.swift` (new, 6 test cases, plus two review-round
  hardening additions) — color-scheme mapping, enum-case-count guard
  (`ThemePreference.allCases.count == 3`, `AppLocale.allCases.count == 2`), layout-direction mapping,
  Arabic-locale language-subtag retention + cross-slice `MentoraTypographyRules.isArabic` integration,
  Arabic Western-numeral `NumberFormatter` formatting (now with a negative control proving plain `"ar"`
  DOES produce an Eastern Arabic-Indic digit, so the positive assertions are proven to depend on the
  `-u-nu-latn` extension rather than passing by platform coincidence), and a real
  `UIHostingController`-hosted `\.locale`/`\.layoutDirection` propagation round-trip (reuses
  `MentoraTypographyGeometryTests.swift`'s harness pattern, now with a `bareSink`-is-live assertion
  closing a vacuous-pass risk in the nil-locale leg). Deliberately does not round-trip
  `.preferredColorScheme` itself — see the test file's own header comment.
- Gate-pattern self-check (manual grep, no script — that's 3c's job) over `mobile/iosApp/iosApp/**`
  production sources (excluding `iosAppTests/`, where several patterns legitimately already appear —
  see D121): `preferredColorScheme(`, `environment(\.locale`/`transformEnvironment(\.locale`,
  `environment(\.layoutDirection`/`transformEnvironment(\.layoutDirection`, and
  `Locale(identifier: "ar` all appear ONLY in `Theme/MentoraTheme.swift`; `.mentoraTheme(` has exactly
  one real production call site (`MentoraApp.swift`).
- Generator re-run twice on Windows, zero further diff — this slice does not touch the generator, so
  this is a pure no-drift confirmation, not a real idempotency test of new generator code.
- Scope respected: no `mobile/shared/**` or `mobile/androidApp/**` touched; no `Info.plist` change (no
  `UIUserInterfaceStyle` key — would hard-pin appearance and defeat `.preferredColorScheme`); no token
  gallery; no completion-gate script; no T7 (localization strings/`.xcstrings`) work started.

**Open items, correctly scoped after review (D6):** per System Design § 12, sheets/alerts presented
from the SAME hierarchy already inherit `\.locale`/`\.layoutDirection` for free (a deliberate iOS/
Android divergence — Android needed extra machinery, iOS's SwiftUI environment does not). The real open
items are narrower than an earlier draft of this note claimed: (a) a **detached**-context presentation
(rare, avoidable) for locale/direction, and (b) § 14's "`.preferredColorScheme` must reach sheets/covers'
own chrome" as an explicit **MC-3 verification requirement**, not yet a proven fact — the iOS form of a
real defect class Phase 4 already shipped once. Neither is solved by this slice; both are to verify at
MC-3, not to pre-build a re-application mechanism against.

**Review status: REVIEWED (Opus) — no blocking bugs.** Every architect decision (D1-D7) was
independently confirmed against the real code, including tracing `LocaleController`'s init-ordering and
actor-isolation reasoning through real Swift concurrency rules and the KMP-bridge chain
(`client.currentLocale()` → `MentoraClient` → `IosPreferenceStore`) through real Kotlin source. Fixed
before push: a missing `import UIKit` in the test file; the D6 sheet/cover overclaim (above); an
overclaimed "eliminates the race" framing around the non-optional `currentLocale` seed (a residual
first-launch English/LTR window on a truly fresh install is real, non-blocking, and now documented in
D121 — nothing renders text/direction yet, and Android has the identical shape); the gate-pattern
test-scope correction (above); an overclaimed `.xcstrings`-lookup doc comment (now correctly deferred to
§ 12's own MC-2 verification step); and the two test-quality hardening fixes listed above. Full account
in `DECISIONS_LOG.md` D121.

**CI: DONE — GREEN on the third push, run #26** (https://github.com/HeshamMohamed94/Mentora/actions/runs/35445837764). Runs #24 and #25 were both RED (https://github.com/HeshamMohamed94/Mentora/actions/runs/35443721152, https://github.com/HeshamMohamed94/Mentora/actions/runs/35444726674) — both `xcodebuild` steps passed all three times, and 5 of 6 `MentoraThemeTests` cases passed all three times; only `test_arabicLocaleForcesWesternNumerals` failed, twice, on two different assertions within itself. The real story, per round 3's diagnostics-only probes (logged to the CI log, never asserted — see `MENTORA-NUMFMT` lines in run #26): round 2's negative control failed because `Locale(identifier: "ar-u-nu-arab")` is malformed by Foundation's parser on this platform (canonicalizes to `"ar-u-NU"`, silently drops the `arab` value, `numberingSystem` reports garbage, the formatter returns `nil`) — not because of any `.none`/`.decimal` style distinction as round 3's own initial hypothesis assumed. Round 3's actual fix (switching to `.decimal` plus a self-contained non-vacuity check instead of a risky round-trip) is correct and now CI-proven, even though the theory behind it needed its own follow-up correction. Full three-round account in `DECISIONS_LOG.md` D121, including an informational note on the legacy `@numbers=arab` locale syntax (CI-proven to work, unlike `-u-nu-arab`) for a future slice that wants a hard negative control.

See `DECISIONS_LOG.md` D121 for the full account, including the D2/D3/D4/D5/D6 decisions, all three CI
rounds, and the exact gate-pattern list recorded (not enforced) for slice 3c.

**T6 slice 3b is DONE.** See the "T6 SLICE 3c" section below for 3c's status — it is no longer
unstarted, though it is not yet CI-verified. Next:
1. Push slice 3c, wait for a real `ios-ci.yml` run, and resolve anything it surfaces (see the two named
   open risks in `DECISIONS_LOG.md` D122 point 11 — neither is pre-solved).
2. **Do not start T7** until all of T6 (3a + 3b + 3c) is real-CI-green and its DECISIONS_LOG/CURRENT_STATUS
   entries are finalized.

Full per-task scope/files/criteria/tests/verification/completion-gate detail lives in
`execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md § 4` — read it before starting any task, do not re-derive
scope from memory.

## T6 SLICE 3c — DONE, CI-GREEN FIRST ATTEMPT (2026-09-19) — TASK T6 FULLY COMPLETE

**Exact task/slice:** Phase 5, Task T6 (Design-system runtime), sub-slice **3c** of the architect's
3a/3b/3c split — token gallery + completion-gate grep script + `ios-ci.yml` step. This is T6's final
sub-slice; once it is real-CI-green, Task T6 as a whole is complete.

**What is complete (full account in `DECISIONS_LOG.md` D122):**
- `tools/ios-checks/theme-checks.js` (new) — the Windows-runnable completion-gate checker, 20 named
  checks across 6 groups: A (typography: `.system(size:` exactly once, zero `UIFontMetrics`, zero
  `Font.custom(`, `.font(` scoped to `MentoraTypography.swift`), B (raw system color: zero
  `Color.<forbiddenName>`, zero raw color in a color-bearing modifier call, zero UIKit/raw-construction
  bridging, `Color("...")` scoped to the generated file), C (the 5 D121 theme-root gate patterns, now
  automated), D (layout resilience: zero `.minimumScaleFactor(`, zero `.dynamicTypeSize(` range
  argument), E (gallery completeness: file exists, `#if DEBUG`/`#endif` structure, 46-color-list parity
  with `Color+Mentora.swift`, exactly the 8 required `#Preview` names), F (a self-guard against its own
  Swift-raw-string-literal blind spot). Runs against comment-STRIPPED source text (a hand-rolled,
  nesting-aware comment stripper preserving string-literal contents and line numbers) — a naive raw-text
  grep was confirmed wrong on this exact tree before writing the real checker (concrete counts in D122
  point 2).
- `tools/ios-checks/package.json` (new) — mirrors `tools/token-pipeline/package.json`'s shape; `npm run
  check` runs `theme-checks.js` then `assets-check.js`.
- `mobile/iosApp/iosApp/Theme/MentoraTokenGallery.swift` (new) — the token gallery named in T6's Manual
  verification / MC-2 item. `#if DEBUG`-gated, never referenced from `MentoraApp.swift` or any
  Feature/Component. All 46 non-shadow semantic color accessors (from `Color+Mentora.swift`, read
  directly, not reconstructed from memory) as swatches; all 12 `MentoraTextStyle` cases (via
  `allCases`) with a multiline Latin sample rendered through `.mentoraFont(_:)`; an adjacent Arabic/Latin
  multiline `.bodyMedium` comparison section; all 7 `MentoraShape.Step` cases and all 5
  `MentoraElevationLevel` cases (via `allCases`), each rendered through the real `.mentoraElevation(_:)`
  modifier; all 42 `MentoraIconName` cases (via `allCases`) in a grid. Every preview routes through the
  REAL production `.mentoraTheme(theme:locale:)` entry point (T6 slice 3b), not raw environment
  injection. Exactly 8 `#Preview`s — the full light/dark × en/ar × default/AX5 matrix. Two open risks
  (Preview-canvas `.preferredColorScheme` honoring, `#Preview`-macro build-setting needs) are recorded in
  the file's own header comment and in D122 point 11, deliberately NOT pre-solved.
- `mobile/iosApp/iosApp/Theme/MentoraTypography.swift`, `Theme/MentoraTheme.swift` — one doc-comment fix
  each (both now cite `theme-checks.js`'s real, automated checks instead of "no automated script exists
  yet" / "the Step 6 grep in DECISIONS_LOG.md D121"), zero logic change, confirmed by re-running the
  checker clean after each edit.
- `.github/workflows/ios-ci.yml` — a new pre-toolchain step ("iOS source gates ... plain Node, no
  toolchain") inserted immediately after `Checkout`, running both `theme-checks.js` and
  `assets-check.js` (the latter's first-ever CI coverage) with a loud Node-availability guard;
  `tools/ios-checks/**` added to both `push.paths` and `pull_request.paths` (before the `!**/*.md`
  negative pattern); one new Job-summary row.
- `execution/PHASE_5_ACCEPTANCE_CRITERIA.md` § 1 — new table row for `theme-checks.js`; "two scripts"
  wording updated to "three".

**Windows-side verification performed:**
- `npm run check` (both scripts) green on the final tree.
- Full negative-control round (Step 5): A1, A2, B1, B2, B3, C1, C4, D1 each deliberately broken via a
  throwaway, untracked scratch `.swift` file (never an edit to a tracked file), confirmed to fire with a
  correct file/line/message, then deleted — `git status --porcelain` confirmed zero leftover diff
  afterward. Full list and results in D122 point 8.
- `node tools/token-pipeline/generate.js` re-run, zero unexpected `git status --porcelain` drift (this
  slice does not touch the generator — a pure no-drift confirmation, not a real idempotency test of new
  code).
- Manual read-through confirmed: zero logic changes to `MentoraShape.swift`/`MentoraElevation.swift`
  (untouched entirely) and to `MentoraTheme.swift`/`MentoraTypography.swift` beyond their one named
  doc-comment fix each; zero `mobile/shared/**` or `mobile/androidApp/**` touched; `project.yml` not
  touched; `ios-ci.yml`'s new step/path-filter edits match the surrounding steps' exact indentation and
  style (no YAML linter available on this host, per this task's own acknowledged limitation).

**Review status: REVIEWED (Opus) — no blocking bugs.** Independently re-verified rather than trusted:
ran the checker directly, wrote a second independent parser to hand-check the gallery's 46-color
completeness against `Color+Mentora.swift`, YAML-parsed `ios-ci.yml`, negative-tested all 20 checks
(not just the 8 the implementation report claimed), and traced every Swift symbol the gallery
references against its real declaration — no API mismatch found. Fixed before push: Check E2's
structural assertion was too weak (didn't prove `#endif` is the file's actual last line, so a future
edit could shrink the `#if DEBUG` block without E2 noticing); Checks E2-E4 scanned raw text instead of
comment-stripped text, inconsistent with every other check in the file; the gallery's one
`.foregroundColor(...)` call was the app target's only use of that soft-deprecated API, switched to
`.foregroundStyle`; the checker's header comment had a scope-ambiguous count and hardcoded line numbers
that had already drifted after this slice's own doc-comment edits. Full account in `DECISIONS_LOG.md`
D122's review-round paragraph. Not CI-verified yet, per this task's explicit instruction not to commit,
push, or trigger CI.

**CI: DONE — GREEN on the first attempt, run #27** (https://github.com/HeshamMohamed94/Mentora/actions/runs/35450960410), pushed as `657298f`. No repeat of slice 3b's multi-round saga: the new "iOS source gates" step succeeded on macOS (not just Windows), `xcodebuild build` succeeded — meaning `MentoraTokenGallery.swift`'s `#Preview` macros (the first anywhere in this repo) compiled cleanly with no `ENABLE_PREVIEWS` build-setting change needed, resolving that open risk — and `xcodebuild test` stayed green. The one remaining open item (whether the Xcode preview *canvas* honors `.preferredColorScheme` for live asset-resolution) is an MC-2 human-check concern, not something a green CI run settles either way.

**TASK T6 IS NOW FULLY COMPLETE.** All three sub-slices reviewed and real-CI-green:
- **3a** (shapes/elevation/generator addendum) — CI run #23 (https://github.com/HeshamMohamed94/Mentora/actions/runs/35440525334)
- **3b** (theme-root wiring) — CI run #26 (https://github.com/HeshamMohamed94/Mentora/actions/runs/35445837764), after 3 CI rounds (2 red on one test's own numeral-formatting methodology, never an app defect)
- **3c** (token gallery + completion-gate script) — CI run #27 (https://github.com/HeshamMohamed94/Mentora/actions/runs/35450960410), green on the first attempt

T7 (localization foundation) may begin once explicitly started — not automatically, per this project's standing rule against auto-advancing past a completed task.

Full per-task scope/files/criteria/tests/verification/completion-gate detail lives in
`execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md § 4` — read it before starting any task, do not re-derive
scope from memory.

## T7 SLICE 1 — DONE, CI-GREEN (2026-09-19)

**Exact task/slice:** Phase 5, Task T7 (Localization foundation), slice 1 of 4 (architect-authored split:
1 = plumbing probe, 2 = full 279-key catalog + `catalog-parity.js`, 3 = `MentoraStrings`/`Formatters`, 4 =
`localization-checks.js` source-policy gate). Slice 1's only job was to resolve two unknowns —
`PHASE_5_IOS_SYSTEM_DESIGN.md § 12`'s "single riskiest untested assumption" — before committing to the
real 279-key catalog and a public Swift API: (1) does a manually-authored `Localizable.xcstrings` actually
produce a discoverable `ar.lproj` in the built bundle, and (2) which Foundation/SwiftUI string-resolution
mechanism honors an explicitly-injected Arabic locale on this toolchain.

**What is complete:** `mobile/iosApp/iosApp/Resources/Localizable.xcstrings` (4-key probe catalog,
`nav_home`/`my_learning_percent_complete`/`error_internal`/`app_name`, ported verbatim from Android's
`values(-ar)/strings.xml`); `Resources/Info.plist` gained `CFBundleLocalizations` (`en`, `ar`);
`mobile/iosApp/iosAppTests/LocalizationPlumbingTests.swift` (new, 4 test cases, `MENTORA-L10N:`-prefixed
diagnostics). Opus review before first push found and fixed 2 blocking bugs (a `Text(String)` vs.
`Text(LocalizedStringKey)` overload-resolution trap, and a vacuous-pass risk in the width-differential
success criterion) plus 1 high-severity gap (only bare `"ar"` was tested, not production's real
`MentoraThemeRules.arabicLocaleIdentifier`). CI round 1 (`f7604d1`) found one more real compile error the
review missed (`Text(...).environment(...)` returns `some View`, not `Text`) — fixed in `4245b96`. CI
round 2 hit a transient, unrelated runner infrastructure flake (a broken-pipe crash in the toolchain-report
step's own `xcodebuild -version`, before any of this slice's files were touched) — re-ran with zero code
change and it went green.

**Both unknowns resolved, with a real, unanticipated finding:** `ar.lproj` DOES land in the built bundle
via XcodeGen's directory-glob `sources:`, zero `project.yml` change needed. But the resolution-mechanism
answer is not what § 12 assumed: `String(localized:table:bundle:locale:)` — the "just works" API — FAILS
to honor an explicit `locale:` argument on this toolchain (always returns English, for both bare `"ar"`
and production's `"ar-u-nu-latn"`). `LocalizedStringResource(_:locale:bundle:)`, explicit bundle-scoped
lookup (`Bundle.localizedString(forKey:...)`), and — critically — SwiftUI's own `Text` under
`.environment(\.locale, ...)` (exactly what `MentoraTheme.swift`'s already-shipped root wiring applies)
all correctly resolve Arabic. `app_name` (deliberately EN-only) falls back cleanly to English under an
Arabic locale, no crash. `%%` survives `String(localized:)` alone unprocessed, confirming
`String(format:)` is still required downstream — matches the plan's already-known `%%`/Arabic-percent-sign
handling rule for slice 2/3. See `DECISIONS_LOG.md` D123 for the full account, including the direct
consequence for slice 3: `MentoraStrings` must be built on `LocalizedStringResource`/bundle-scoped lookup,
never the naive `String(localized:locale:)` call.

**CI: DONE — GREEN**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35455407103 (commit
`4245b96`, 6m28s). No `mobile/shared`/`mobile/androidApp` file touched.

**Open item carried to slice 3, not this slice's job (D-b in the architect's plan):** whether
`MentoraStrings` should be the sole sanctioned string-resolution path app-wide (forbidding raw
`Text("key")` catalog lookup), given mechanism 4 is now proven to genuinely work. This is a
design-consistency/gate-enforceability call, not a "does it work" question — flagging for a quick human
check before slice 3's API is shaped.

**Next: T7 slice 2** — the full 279-key catalog port + `tools/ios-checks/catalog-parity.js` (three-way
parity: catalog EN/AR vs. Android's `values`/`values-ar` XML).

## T7 SLICE 2 — DONE, CI-GREEN (2026-09-19)

**Exact task/slice:** Phase 5, Task T7, slice 2 of 4 — replace slice 1's 4-key probe catalog with the
full 279 EN / 278 AR key port from Android's `values(-ar)/strings.xml`, plus `tools/ios-checks/catalog-parity.js`
(the Windows-runnable parity/specifier lint) and `iosAppTests/CatalogParityTests.swift` (its in-target
compiled-bundle counterpart).

**What is complete:** `Localizable.xcstrings` now carries all 279 keys, converted only by the documented
`%N$s`→`%N$@` rule (`%%`/Arabic percent sign `٪` left untouched in the 5 keys that mix them). New
`catalog-parity.js` (6 check groups A–F) wired into `package.json` and `ios-ci.yml`. Opus review before
push found Groups A/B never compared value TEXT (only key sets/specifier-index sets) — a copy-paste error
would have passed silently; added Group F (exact per-key/per-language value comparison against Android,
mutation-tested by the reviewer against 6 corruption scenarios, 0 false positives on the real 557 units).
Also reformatted the catalog to Xcode's own `"key" : value` separator style.

**A real, twice-reproduced CI infrastructure flake was found and fixed along the way** (not a code
defect in either T7 slice): `xcodebuild -version | head -n 1`, used in two places in `ios-ci.yml`, races
`head`'s early pipe-close against `xcodebuild`'s second line of output — on this toolchain that EPIPE
surfaces as an uncaught `NSFileHandleOperationException` crash (exit 134) instead of a quiet SIGPIPE. Hit
identically on two independent commits (slice 1's round 2 and slice 2's round 1), proving it wasn't a
one-off. Fixed by capturing `xcodebuild -version`'s full output into a variable first (command
substitution reads to EOF regardless), then piping the already-captured string — removes the race
entirely. See `DECISIONS_LOG.md` D124 for the full account.

**CI: DONE — GREEN**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35457811418 (commit
`275b8e6`, 4m53s, first attempt after the flake fix). `Test Suite 'All tests' passed` — both
`CatalogParityTests` (new) and `LocalizationPlumbingTests` (slice 1) passed together; the reviewer's one
flagged uncertainty (`String.LocalizationValue(key)` from a runtime `String`) compiled and ran correctly.

**Next: T7 slice 3** — `MentoraStrings`/`Formatters`, the public Swift API, built on slice 1's proven
`LocalizedStringResource(_:locale:bundle:)` mechanism. Per the user's explicit decision, `MentoraStrings`
will be the SOLE sanctioned string-resolution path app-wide (plain `Text("key")` forbidden at call
sites) — enforced later by slice 4's `localization-checks.js`.

## T7 SLICE 3 — DONE, CI-GREEN (2026-09-19)

**Exact task/slice:** Phase 5, Task T7, slice 3 of 4 — `Support/MentoraStrings.swift` (the sole
sanctioned string-resolution entry point app-wide) and `Support/Formatters.swift` (locale-explicit
date/count formatters), built on slice 1's CI-proven `LocalizedStringResource(_:locale:bundle:)`
mechanism.

**What is complete:** `MentoraStrings.text(_:locale:)`/`text(_:locale:_:args)` — every future screen
(T8-T23) resolves strings through this one API, never plain `Text("key")`. The formatted overload
substitutes `%N$@` via `String(format:locale:arguments:)`, which also collapses the catalog's literal
`%%` to a single `%`. `MentoraFormatters.date`/`.count`, both locale-explicit, reusing
`MentoraThemeRules.foundationLocale(for:)` for the Western-numeral guarantee (H6); duration/price
deliberately deferred to T16/T14. Opus review removed a `@MainActor`-isolation-risking
`EnvironmentValues.mentoraLocale` convenience (nothing depended on it yet) rather than guess at a fix,
and added debug-only assertions for a dropped-specifier crash class (D93/H2) and a missing/typo'd key.

**CI found a real, unanticipated Foundation behavior on round 1:** `String(format:locale:arguments:)`
under the Arabic locale wraps each substituted `%@` argument in Unicode bidi-isolate marks (U+2068/
U+2069) — correct, intentional behavior (keeps embedded LTR digits from visually disordering RTL text),
not a bug. Fixed by stripping bidi control characters before comparing in the two affected tests, rather
than hardcoding the exact isolate characters (which Apple has changed across OS versions for this exact
scenario). See `DECISIONS_LOG.md` D125 for the full account.

**CI: DONE — GREEN**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35460603063 (commit
`345e0a5`, 9m25s, 85/85 tests, 0 failures).

**Next: T7 slice 4** — `tools/ios-checks/localization-checks.js`, the source-policy gate (no hardcoded/
auto-extracted string anywhere in the app target except the sanctioned `MentoraTokenGallery.swift`
carve-out; `String(localized:...)`/`LocalizedStringResource` usage confined to `Support/MentoraStrings.swift`;
RTL-safe layout — no physical `.leading`/`.trailing` edges). This is T7's last slice.

## T7 SLICE 4 — DONE, CI-GREEN FIRST ATTEMPT (2026-09-19) — TASK T7 FULLY COMPLETE

**Exact task/slice:** Phase 5, Task T7, slice 4 of 4 — the LAST slice. `tools/ios-checks/localization-checks.js`,
a Windows-runnable Node lint mechanizing D124/D125's already-made decision (`MentoraStrings` is the sole
sanctioned string-resolution path app-wide) so it stays enforced across T8-T23's 18 future screens.

**What is complete:** 6 check groups — **A** (zero `String(localized:`/`NSLocalizedString(`/
`LocalizedStringResource(` outside `Support/MentoraStrings.swift`), **B1/B2** (zero hardcoded
`Text("literal")`, plus a separately-named auto-extraction guard for interpolated `Text("...\(x)...")`
per the plan's own T7 approach section), **B3** (additive — `Label`/`.accessibilityLabel`/
`.navigationTitle`/`.confirmationDialog`/`.alert`/`.help`, forward-looking, zero real call sites yet),
**B4** (additive, D94 heuristic — no string-literal default parameter value, the exact `retryLabel`-class
gap Phase 4/Android's Task 19 swept after the fact), **C** (no physical `.left`/`.right` layout API,
confirmed against `PHASE_5_IOS_SYSTEM_DESIGN.md § 13`'s literal text — SwiftUI's `.leading`/`.trailing`
are the RTL-aware logical forms here, opposite of CSS), **D1** (raw-string-literal stripper self-guard,
same precedent as `theme-checks.js`'s F1). One carve-out matching existing precedent:
`MentoraTokenGallery.swift`'s debug-only section-header literals are exempt from B1 but not B2.

**Verified independently** — ran the checker directly (6 groups over 19 production files, clean) and
performed a separate, independent negative-control test: a throwaway untracked scratch file with 6
deliberate violations all fired correctly with clean file/line/message, the legitimate
`Text(MentoraStrings.text(...))` call correctly did not false-positive, scratch file deleted with zero
leftover diff. One factual correction made before push: an earlier draft's comment incorrectly claimed
SwiftUI's `TextAlignment` has physical `.left`/`.right` cases — confirmed it only has
`.leading`/`.center`/`.trailing`, corrected the comment rather than leave an unverified API claim in the
codebase.

**CI: DONE — GREEN on the first attempt**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35461669013
(commit `53e031b`, 7m45s, 85/85 tests, 0 failures). See `DECISIONS_LOG.md` D126 for the full account.

**TASK T7 IS NOW FULLY COMPLETE.** All four slices real-CI-green:
- **Slice 1** (localization plumbing probe) — CI run 35455407103 (`4245b96`)
- **Slice 2** (full 279-key catalog + `catalog-parity.js`) — CI run 35457811418 (`275b8e6`), plus a real
  twice-reproduced CI infrastructure flake found and fixed
- **Slice 3** (`MentoraStrings`/`MentoraFormatters`) — CI run 35460603063 (`345e0a5`)
- **Slice 4** (`localization-checks.js`) — CI run 35461669013 (`53e031b`), green on the first attempt

T8 (the first component kit) may begin once explicitly started — not automatically, per this project's
standing rule against auto-advancing past a completed task.

Full per-task scope/files/criteria/tests/verification/completion-gate detail lives in
`execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md § 4` — read it before starting any task, do not re-derive
scope from memory.

## T8 — Component Kit A (atoms): IN PROGRESS

**Scope correction, confirmed by research before starting:** `PHASE_5_IOS_SYSTEM_DESIGN.md § 16` lists
14 Kit A atoms, but `MentoraIcon` (the 14th) was already fully built in Task T3
(`Theme/MentoraIcon.swift`, CI-green since commit `4f1ff36`). **T8's real remaining scope is 13 atoms.**

**Architect-authored 7-slice plan** (in dependency order, each resolving a specific first-use-of-a-
mechanism risk before the next slice depends on it):
1. Foundations (preview-host mechanism, hand-authored motion/avatar tokens, scaled-font support) +
   Badge/CategoryChip/Avatar — **DONE, see below**.
2. `MentoraButton` (4 variants) + `MentoraIconButton` — first custom `ButtonStyle`.
3. `MentoraTextField` + `PasswordField` + `SearchField` — first `@FocusState`/text input.
4. `MentoraToggle` — first custom `ToggleStyle` (no Android file-level precedent exists for this atom).
5. `MentoraSelect` — first `Menu`, highest uncertainty in T8 (native menu surface vs. token spec).
6. `MentoraProgressBar` + `MentoraTabs` — first looping/positional animations.
7. `MentoraSnackbar` + the T8 completion-gate script (`component-checks.js`), mirroring T6 slice 3c's
   "gallery + gate + CI step in one slice" shape.

## T8 SLICE 1 — DONE, CI-GREEN FIRST ATTEMPT (2026-09-19)

**Exact task/slice:** Phase 5, Task T8, slice 1 of 7 — the structural blocker every later slice's
`#Preview` depends on (a second sanctioned `.mentoraTheme(` call site, since `theme-checks.js`'s C1-C4
otherwise restrict all theming/environment calls to `Theme/MentoraTheme.swift` and the gallery), plus
the 3 lowest-risk atoms (Badge, CategoryChip, Avatar) as the first real payload through it.

**What is complete:** `Components/Support/MentoraPreviewHost.swift` (new) + a `theme-checks.js` Check C4
amendment (not a weakening — C1/C2/C3/C5 untouched); `Theme/MentoraMotion.swift` and
`Theme/MentoraDimens.swift` (hand-authored, disclosed-gap, Android-precedented, for the `motion`/`avatar`
token families the generator never walks into a Swift constant); an additive `scale:` parameter on
`Theme/MentoraTypography.swift`'s `MentoraFontModifier` (T6-completed file, provably a no-op at default
for all 16 pre-existing call sites); `Components/Badge.swift` (6 variants), `Components/CategoryChip.swift`
(4 states), `Components/Avatar.swift` (4 sizes + status dot, initials-fallback only) — all composing
colors/shape/type from the semantic/primitive token layer directly, per `COMPONENTS.md`'s per-component
spec.

**Opus review before push found and fixed two real issues:** three `Components/*.swift` files used
`shared`-module enum cases in `#Preview` calls with no `import shared` (compiles today, violates this
project's own import-transitivity rule, would have propagated across the remaining 6 slices); 10 new test
assertions rested on an unverified `Color == Color` equality assumption with zero prior CI-green
precedent — added an explicit guard test proving it before relying on it, confirmed correct on the real
CI run. Also fixed a stale doc-comment count, a missing test import, a test-helper name shadowing
`XCTestCase`'s own `measure(_:)`, and disclosed two real scope gaps (`CategoryChip` has no tap handler/
a11y trait/44pt target yet; `Avatar` applies no VoiceOver combination) for later tasks to notice rather
than silently inherit. See `DECISIONS_LOG.md` D127 for the full account.

**CI: DONE — GREEN on the first attempt**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35465030804
(commit `33004c2`, 7m35s, 122/122 tests — up from T7's 85, 0 failures).

**Next: T8 slice 2** — `MentoraButton` (4 variants via a `ButtonStyle`) + `MentoraIconButton`.

## T8 SLICE 2 — DONE, CI-GREEN FIRST ATTEMPT (2026-09-19)

**Exact task/slice:** Phase 5, Task T8, slice 2 of 7 — the first custom `ButtonStyle` in this codebase,
per `COMPONENTS.md`'s Buttons section (PrimaryButton/SecondaryButton/TonalButton/TextButton, IconButton).

**What is complete:** `Components/MentoraButton.swift` (pure `MentoraButtonVariant`/`MentoraButtonMetrics`/
`MentoraButtonColorSet` resolvers, a shared `MentoraButtonChrome` `ViewModifier`, the `MentoraButtonStyle`,
`MentoraButton`, and 4 named wrapper views) + `Components/MentoraIconButton.swift` (same pattern,
icon-only, required `accessibilityLabel`). Focus state is a hoisted `@FocusState` passed into the
`ButtonStyle` as a plain stored property — not a guessed `@Environment(\.isFocused)` key.

**Opus review before push found and fixed 4 real issues:** a `CONTENT_RESILIENCE.md § 8` Locked Rule
violation (fixed `.frame(height:)` instead of the mandated grow-to-fit/2-line-wrap behavior for button
labels); a focus ring that rendered flush against the button edge (0pt gap) instead of the spec'd 2px
offset, because `strokeBorder`'s own internal `lineWidth/2` inset wasn't accounted for; a leaking
`MentoraIcon` inside `MentoraButton` not marked `.accessibilityHidden` (I2); and a missing `import shared`
in both new files. Also fixed as cheap insurance: an explicit `init` on `MentoraIconButton`, and a tuple
key-path `ForEach` replaced with an `Identifiable` struct. **The same review pass caught the identical
§ 8 violation already shipped in T8 slice 1's `Badge.swift`/`CategoryChip.swift`** (both missing their own
required `.lineLimit(1)`) — fixed in the same commit. See `DECISIONS_LOG.md` D128 for the full account.

**CI: DONE — GREEN on the first attempt**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35467412638
(commit `7719f8e`, ~8m, 170/170 tests — up from T8 slice 1's 122, 0 failures).

**Next: T8 slice 3** — `MentoraTextField` (base) + `PasswordField` + `SearchField`.

## T8 SLICE 3 — DONE, CI-GREEN (2026-09-19)

**Exact task/slice:** Phase 5, Task T8, slice 3 of 7 — `COMPONENTS.md § Inputs` (TextField/PasswordField/
SearchField). Completed under the user's new "parity-focused completion mode" instructions, which keep
mandatory Opus review for cross-cutting Design System infrastructure (this slice) but relax it for
ordinary screen work starting T9.

**What is complete:** `Components/MentoraTextField.swift` (pure resolvers + base view + 2 wrapper views),
using one persistent `TextField`/`SecureField` identity at all times with the floating label as a
separate, animated `Text` overlay — never branching the input's own subtree on float state.

**Opus review before push found and fixed two real issues:** a copy-paste guard test that would have
failed on the very first CI run (keyed on `borderColor` alone, but `disabled`/`default` deliberately share
one per spec — rekeyed on the full color-set triple); and a genuine criterion I1 violation — the floating
label's position was computed from static, unscaled token constants while its font was Dynamic-Type-scaled,
so it visually overlapped the input at any Dynamic Type size above the smallest (traced: +2.8pt at default,
+38.5pt at `.accessibility5`). Redesigned with `.topLeading` ZStack alignment and a style-swap
(`.bodyMedium`↔`.labelMedium`) instead of manual offset math, so the geometry can never drift out of sync
again. Also fixed: a raw `.easeInOut` literal (the first `.animation()` call site in the codebase, and the
exact approximation `MentoraMotion.swift` was built to prevent); VoiceOver double/triple-reading the field.
Two spec-internal deviations disclosed (trailing-icon-button size and real field height), both traced to
`MentoraIconButton`'s own T8 slice 2 reading of the spec. See `DECISIONS_LOG.md` D129 for the full account.

**CI: DONE — GREEN on the first attempt after the review round**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35469462062
(commit `cbafe75`, ~4m, 188/188 tests — up from T8 slice 2's 170, 0 failures).

**Next: T8 slice 4** — `MentoraToggle` (custom `ToggleStyle` over native SwiftUI `Toggle`).

## T8 SLICES 4+5 — DONE, CI-GREEN (2026-09-19)

**Exact task/slices:** Phase 5, Task T8, slices 4 and 5 of 7 — batched into one implementation/review/push
cycle, the first slice batching under the user's new "parity-focused completion mode" instructions.
`MentoraToggle` (`COMPONENTS.md § Toggle / Switch`) + `MentoraSelect` (`§ Select / Dropdown`).

**What is complete:** `Components/MentoraToggle.swift` — a custom `ToggleStyle` wrapping SwiftUI's native
`Toggle` per the spec's explicit native-control mandate, with an optional adjacent label for the v1.3
"never color alone" locked rule. `Components/MentoraSelect.swift` — reuses `MentoraTextField`'s field
chrome/color resolver directly, built on native `Menu` per the spec's iOS native-mapping note, with a
disclosed, accepted deviation for the system-owned popover surface/row-background styling `Menu` doesn't
expose.

**Opus review before push found and fixed two real accessibility defects, both more severe than typical
gaps because they directly contradicted this design system's own written accessibility contract:**
`MentoraToggle`'s custom `Button`-based chrome silently dropped the native switch trait and on/off value
VoiceOver needs (fixed via `.accessibilityRepresentation`, substituting a genuine native `Toggle` for
accessibility purposes only — also avoids hand-writing localized "On"/"Off" text this component has no
clean way to supply); `MentoraSelect`'s `.accessibilityLabel` override left no accessibility value at all,
so VoiceOver never announced the selected value (fixed with an explicit `.accessibilityValue`). Also fixed:
Select's field label wasn't dimming when disabled; the selected-option checkmark needed an explicit
`.isSelected` trait since it's the sole remaining selection signal once the popover deviation is accounted
for; a self-caught bug (missing `.mentoraFont(.bodyMedium)` on option rows) fixed before review even started.
Two gaps disclosed and deferred rather than fixed here: the spec's "Loading options…" menu row is
structurally unreachable (field disables the whole menu while loading, a deliberate simplicity choice), and
Reduce Motion isn't respected by any animation anywhere in this codebase yet (cross-cutting, predates this
slice). See `DECISIONS_LOG.md` D130 for the full account.

**CI: DONE — GREEN on the first attempt after the review round**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35471262629
(commit `a84ab7b`, ~5m, 219/219 tests — up from T8 slice 3's 188, 0 failures).

**Next: T8 slices 6+7** — `MentoraProgressBar` + `MentoraTabs`, then `MentoraSnackbar` + the
`component-checks.js` completion gate closing out Task T8.

## T8 SLICES 6+7 — DONE, CI-GREEN — CLOSES OUT TASK T8 (2026-09-19)

**Exact task/slices:** Phase 5, Task T8, the final batch (slices 6+7 of 7). `MentoraProgressBar`
(determinate + a separate indeterminate variant), `MentoraTabs` (horizontally-scrollable strip with a
`matchedGeometryEffect`-driven moving indicator), `MentoraSnackbar` (a `.mentoraSnackbar(isPresented:)`
presentation modifier, first direct `UIKit` import in this codebase for a VoiceOver announcement), plus
`tools/ios-checks/component-checks.js` — a 5th Node gate confirming all 14 Component Kit A atoms exist.

**Opus review before push found and fixed one real `COMPONENTS.md` violation and one real accessibility
gap, both consequential because they'd have propagated into every future T9+ call site:** `MentoraTabs`
only animated its indicator on the internal tap-driven mutation (justified by a factually wrong header
comment claiming SwiftUI has an "implicit animation rule" for state changes — it does not), so an
externally-driven `selection` change (a paged view syncing back, restored navigation state) would have
jumped the indicator instantly with no animation, violating the spec's unconditional animation requirement
— fixed by moving the animation to the container so it covers both paths. `MentoraProgressBar`'s
determinate view exposed an accessibility value with no label and no way for a caller to supply one, while
its own sibling indeterminate variant already required one — fixed with a required `accessibilityLabel`
parameter, mirroring `MentoraIconButton`'s precedent. Also fixed: `MentoraSnackbar`'s inconsistent
width (narrow pill without an action, full-width with one); a third instance of `.frame(height:)` instead
of `.frame(minHeight:)` on a text-bearing control in `MentoraTabs`. Two gaps disclosed and deferred: no
auto-scroll to an off-screen selected tab, and Reduce Motion not respected by this batch's two new
animation categories (extends the same cross-cutting gap from T8 slices 4+5). See `DECISIONS_LOG.md` D131
for the full account.

**CI: DONE — GREEN on the first attempt after the review round**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35473216980
(commit `24d61d9`, ~5m, 241/241 tests — up from T8 slices 4+5's 219, 0 failures).

**✅ TASK T8 FULLY COMPLETE.** All 7 slices reviewed/self-verified and real-CI-green; all 14 Component
Kit A atoms built and confirmed present by `component-checks.js`. Next task: **T9 — Navigation shell**
(`TabView` + per-tab `NavigationStack`, `TabRouter`, `AuthGate`) — per the user's explicit instruction, may
now begin.

## T9 — NAVIGATION SHELL — DONE, CI-GREEN (2026-09-19)

**Task:** Phase 5, Task T9 (Navigation shell). First task built under the user's **parity-focused
completion mode** directive (see `DECISIONS_LOG.md` D132) — mandatory Opus review still applied (navigation
foundation + architecture + auth/session touchpoint qualifies under the new tiered review policy), but
implemented as one batched dispatch (3 slices' worth of work in one implementer pass) rather than the
per-slice cadence T6-T8 used, per the policy's "faster execution" instruction.

**What was built:** `Navigation/Route.swift` (`Tab`: 5 locked-order cases home/explore/myLearning/aiTutor/
profile; `Route`: courseDetails/coursePlayer/quiz, id-only payloads per D7; `PendingIntent`) —
`Navigation/TabRouter.swift` (`@MainActor @Observable`, 5 stored `[Route]` properties per System Design
§ 3.1, `path(for tab:) -> Binding<[Route]>` as the one permitted indirection, push/popToRoot/setPath/
selectTab (D2)/resetAllForLogout (D7)/requestGatedRoute/authenticationObserved/loginSheetDismissed (B8)) —
`Navigation/TabShell.swift` (the `TabView` root + `MentoraRouteDestinations` ViewModifier, the one shared
`.navigationDestination(for:)` table per D4, `.toolbar(.hidden, for: .tabBar)` for Course Player/Quiz per
D5) — `Navigation/RootView.swift` (replaces the old `PlaceholderRootView`; branches only on `.unknown` vs.
everything else, since guest and authenticated users share one shell) — `Navigation/TabRootPlaceholders.swift`
(8 `// TEMPORARY (T9)` placeholder views for T10-T21 to replace, including the real B8 demonstration on
Course Details' guest-only "Login to enroll" CTA) — `Navigation/AuthGate.swift` (the guest auth-gate
sheet/dismiss/replay wiring) — `Components/MentoraTabBar.swift` (`MentoraTabBarSpec` constants + tint/
background chrome modifiers) — 3 new test files (`TabRouterTests.swift`, `AuthGateLogicTests.swift`,
`MentoraTabBarSpecTests.swift`) — `tools/ios-checks/navigation-checks.js` (a 6th Node source-policy gate,
10 check groups: file existence, the 5-stored-properties rule, no `[Tab: [Route]]` dictionary, exactly-one
`.navigationDestination(for:)` call, D5's tab-bar-hiding wiring present, no `.shared` singleton access,
`AppEnvironment`/`MentoraClient`'s single-construction-site guarantee, placeholder-file/marker discipline).
`AppEnvironment.swift` gained `let router: TabRouter`, constructed once alongside the other 3 controllers.

**A project-owner architectural override, documented and review-scrutinized:** the architect's plan called
for a separate guest-specific "GuestShell." This was overridden in favor of ONE 5-tab `TabShell` used
identically for guest and authenticated users. Mandatory review found the override's originally-stated
justification ("D1's literal text is silent about guests") does not actually hold up — D1's own cited
source (`design-to-code/shared/navigation.json#/shells/mobileStudentShell`) is explicitly scoped to
authenticated Students, and `ux/NAVIGATION_SPEC.md:82` directly contradicts a shared guest/authenticated
shell. The override itself is kept — review independently confirmed a stronger, correct justification: one
shell keeps `.unauthenticated`/`.authenticated` in the SAME `RootView` branch, so the tab whose
`NavigationStack` originated a gated action survives login untouched, which is exactly what B8's "replay
onto the original intent" needs; a GuestShell/TabShell swap at login would tear down that stack at the
worst possible moment. Fully reversible (`Route`/`TabRouter`/`MentoraRouteDestinations` are shell-agnostic)
if a future task needs to close the disclosed product gap (a guest currently sees My Learning/AI Tutor/
Profile tabs with placeholder-only content, `ux/NAVIGATION_SPEC.md:82`'s guest-IA intent not yet
implemented). `Route.swift`'s header comment now carries the corrected rationale. See D132 for full detail.

**Mandatory Opus review found and fixed two real defects before push, plus one documentation-only
correction:**
1. **`mentoraTabBarChrome()` applied `.toolbarBackground(...)` to the `TabView` itself instead of inside
   each tab's `NavigationStack` content — a silent no-op.** `.toolbarBackground` is a preference-propagating
   modifier exactly like `.toolbar`/`.navigationTitle` (the very class of mistake this same file's own
   `MentoraRouteDestinations` doc comment already warns against for `.navigationDestination`, applied here
   in the mirror-image direction) — it must sit inside the bar-hosting container for the preference to
   travel upward to it. No compile error, no gate failure; would have first surfaced as an unthemed
   translucent tab bar at MC-3. Fixed by splitting the one modifier into `.mentoraTabBarTint()` (correctly
   kept at the `TabView` root — `.tint` is a plain environment value, not a preference) and
   `.mentoraTabBarBackgroundChrome()` (moved into `TabShell.stack(for:)`, applied once per tab's own stack
   content, 5 call sites).
2. **The B8 pending-route replay had a real, latent ordering race that would break at T10.** The original
   design deferred the replay to the sheet's `onDismiss:` closure, sampling `isAuthenticated` at that
   moment. `SessionController.isAuthenticated` updates asynchronously (via its own `authStates()`
   subscription, crossing a Kotlin-coroutine → Swift-`AsyncSequence` boundary) with no ordering guarantee
   against a real login screen's own `dismiss()` call on success — the idiomatic thing for T10's `LoginView`
   to do. If `dismiss()` fired before auth was observed true, the pending intent would be silently dropped
   (a direct B8 violation, and intermittent, since it depends on async arrival order). Fixed by having
   `TabRouter.authenticationObserved()` perform the replay SYNCHRONOUSLY the instant auth is observed,
   decoupled entirely from whenever/however the sheet's own dismissal happens; `loginSheetDismissed()`
   (renamed, dropped its now-unused `isAuthenticated:` parameter) is now the user-cancelled path only,
   unconditionally safe to call a second time from `onDismiss:` after a successful replay (double-fire
   defense — `AuthGateLogicTests.swift`'s new explicit regression case for exactly this).
3. **Doc-only:** `TabRouter.path(for:)`'s original comment overclaimed that `Binding`'s closures are
   provably non-isolated at the type-system level (a claim that could not actually be confirmed one way or
   the other from static reading alone, given no other `Binding(get:set:)` existed anywhere in this target
   before this task). Corrected to disclose the real uncertainty, and — since `MainActor.assumeIsolated` is
   correct and harmless under either outcome — now applied consistently at all 3 hand-rolled
   `Binding(get:set:)` sites this task introduces (`TabRouter.path(for:)`, `TabShell.selectionBinding`,
   `AuthGate.isPresentingLoginBinding`), not only the first one. `MentoraTabBarSpec`'s header also gained a
   one-line correction: `height`/`iconSize` are recorded spec numbers, not yet consumed by any rendering
   code (native `TabView` chrome takes neither as a parameter) — the passing spec-constant test proves the
   constants match `COMPONENTS.md`, not that the rendered bar measures 64pt/24pt; that remains an MC-3 item.

**Review explicitly confirmed clean (verified against real source, not just the design intent), reported
here per the parity-focused completion mode's instruction to record what was checked, not only what
failed:** the pending-intent-on-`TabRouter` (not `AppEnvironment`) placement soundly satisfies System
Design § 10's actual intent despite diverging from its literal wording; `resetAllForLogout()` cannot
spuriously fire on cold launch (`SessionController.isAuthenticated` is `false` for both `.unknown` and
`.unauthenticated`, confirmed against real source, so that transition is a non-change and `onChange`
without `initial:` never fires on it); `.navigationDestination(for:)`'s placement inside each stack's root
content (not chained onto `NavigationStack(...)` from outside) is correct, and is in fact the most robust
choice available since the root view never leaves the stack; D3 (per-tab stack independence), D4 (deep
screens push onto the tab selected at tap time, not build time), D6 (no duplicated Swift-side auth flag),
D7 (id-only `Route`, full 5-stack logout clear), and § 3.1's five-stored-properties compliance (`path(for:)`
exists precisely because `private(set)` blocks `@Bindable`'s dictionary-style sugar, exactly as § 3.1
itself anticipates) all hold. One informational, non-blocking note recorded for the future: D2's
tap-active-tab-pops-to-root relies on legacy `.tabItem`-based `TabView` writing through the selection
binding even when re-tapping the already-selected tab — the long-established, spec-matching pattern, but
specifically an MC-3 device-verify item, and would need re-verification from scratch if a later task ever
migrates to iOS 18's `TabView { Tab(...) }` builder syntax.

**Verified before push:** all 6 Node gates (`theme`/`assets`/`catalog-parity`/`localization`/`component`/
`navigation`) run clean via `npm run check`; `git status --porcelain` scope matched the expected 15-file
list exactly (10 new, 5 edited) both before and after the review-fix round; every localization key the new
placeholder/tab-bar code resolves (15 keys) confirmed pre-existing in `Localizable.xcstrings` by direct
grep, zero new keys (catalog-parity's 279-key pin unaffected); all 5 tab icon glyphs confirmed real
`MentoraIconName` cases including `.dashboard` for Home (no `.home` case exists, matching Android's own
`MobileBottomNavigation.kt` mapping); `project.yml`'s directory-glob sourcing confirmed to need zero edits
for the new `Navigation/` directory or the 3 new test files.

**CI: DONE — GREEN on the first attempt after the review round**,
https://github.com/HeshamMohamed94/Mentora/actions/runs/35476041934 (commit `4d9ecd2`, all 6 Node gates
PASSED, 261/261 tests — up from T8's 241, 0 failures, 0 unexpected). `:shared:testDebugUnitTest`/
`:androidApp:testDebugUnitTest` unaffected — zero `mobile/shared`/`mobile/androidApp` files touched.

**TASK T9 IS NOW FULLY COMPLETE.** Next task: **T10 — Auth screens** (Login/Register), which the tiered
review policy marks mandatory-review (auth/session logic). Per the parity-focused completion mode's
explicit "continue automatically" instruction, T10 begins now without stopping for per-task approval.

## T10 — LOGIN/REGISTER SCREENS — DONE, CI-GREEN (2026-09-20)

**Task:** Phase 5, Task T10 (Login/Register screens). `PHASE_5_ACCEPTANCE_CRITERIA.md` B1, B2, B8, B9,
I4, H1. Second task under the parity-focused completion mode (D133) — mandatory-review category
(auth/session logic).

**What was built:** `Features/Auth/LoginModel.swift`/`LoginView.swift` (Login surfaces every failure as
one generic, non-field-specific message per B1, with `RATE_LIMITED_AUTH` still getting its own distinct
copy per B9 via `ErrorCopy.key(for:)`) — `Features/Auth/RegisterModel.swift`/`RegisterView.swift`
(Register routes `shared`'s `EmailValidator`/`PasswordValidator`-driven field failures to inline field
errors exactly matching Android's `mapRegisterFailure`, the D51 pattern) — 2 new test files. `Navigation/AuthGate.swift`
edited: the T9 placeholder `LoginSheetPlaceholderView` replaced by a real `AuthFlowView` (a local,
sheet-scoped `NavigationStack`). Neither model navigates on success — `AuthGate`'s own
`.onChange(of: isAuthenticated)` watcher stays the sole reactor (B8), unchanged from T9. Every real
Kotlin-bridged API used (`EmailValidator`/`PasswordValidator`/`SessionUser`/`Role`) was confirmed
directly against the actual CI-captured `kmp-swift-interface` artifact before implementation began, not
guessed.

**Mandatory Opus review plus a follow-up verification pass on the fixes found and fixed 8 real issues
(counting the follow-up pass's own 2 findings on the first round's fixes) — see `DECISIONS_LOG.md` D133
for the full account of each. Most consequential:**
1. **A likely CI compile break**: `PasswordField` had no explicit `init`, defaulting to a `private`
   implicit memberwise initializer — this exact bug class already had 6 defensive precedents elsewhere
   in this kit, but this one atom was missed, and T10 was the first task to ever construct it
   cross-file. Fixed, and CONFIRMED correct by the next real CI compile (not merely argued).
2. **A real KMP-parity violation**: `RegisterModel` originally called `EmailValidator`/`PasswordValidator`
   directly from Swift, duplicating validation `RegisterUseCase.kt` (`shared`) already performs before
   any network call, and constituting an undisclosed 7th non-façade `shared` entry point beyond
   criterion A2's stated-exhaustive 6. Removed entirely — the existing `fields`-based routing already
   handles both the local-Kotlin and server failure cases identically.
3. **A missing VoiceOver announcement for submit failures** (a real, specified `ACCESSIBILITY.md`/
   `SCREEN_UX_SPECS.md` requirement) — added via the same `UIAccessibility.post` pattern
   `MentoraSnackbar.swift` already established, later corrected to a single combined announcement when
   Register's email and password errors both fire from one failure (the first attempt's two separate
   `.onChange` handlers could drop one of the two messages).
4. **A real close-button defect**: `RegisterView`'s own "Close" affordance silently popped back to Login
   instead of dismissing the sheet (`DismissAction` is context-sensitive to a pushed `NavigationStack`
   level) — fixed by reading `dismiss()` once at `AuthFlowView`'s own sheet-root level and threading it
   down as an explicit `onClose` closure to both screens.
5. Stale errors persisting through edits (fixed with `didSet` clearing mirroring Android's
   `onEmailChange`/`onPasswordChange` exactly — verified against the actual Swift `ObservationMacros`
   source that this does not break `@Observable` tracking), a `canSubmit`/`submit()` guard mismatch on
   whitespace-only input, and a missing form-width cap (`ux/SCREEN_UX_SPECS.md`'s locked "centered form
   card" rule).

**Two gaps disclosed rather than fixed, per the parity-focused completion mode's own instruction:** a
narrow repeat-identical-failure re-announcement edge case (device/simulator-unconfirmable from this
Windows host), and Register's missing "focus moves to the first invalid field" half of its live-region
requirement (the live-region half is built; the focus-move half needs its own `@FocusState` wiring
verified live at MC-3).

**Verified before push:** all 6 Node gates run clean via `npm run check`, both before and after the full
two-round fix cycle; `git status --porcelain` scope matched the expected 8-file list exactly; zero new
`Localizable.xcstrings` keys (every key used was confirmed pre-existing before implementation began);
every `Components/*.swift` symbol referenced (`MentoraIconButton`'s real init, color/icon/shape tokens)
confirmed real against current definitions.

**CI: DONE — GREEN on the first attempt after the full two-round review-fix cycle**,
https://github.com/HeshamMohamed94/Mentora/actions/runs/35479005842 (commit `80a1049`, all 6 Node gates
PASSED, 282/282 tests — up from T9's 261, 0 failures, 0 unexpected). This is the first real compile of
`PasswordField`'s new explicit `init` and of every other fix in this task — the compile-risk theory in
finding 1 is now CONFIRMED correct. `:shared:testDebugUnitTest`/`:androidApp:testDebugUnitTest`
unaffected — zero `mobile/shared`/`mobile/androidApp` files touched.

**TASK T10 IS NOW FULLY COMPLETE.** Next task: **T11 — Component Kit B** (cards, state patterns, sheets,
artwork). Per the parity-focused completion mode's explicit "continue automatically" instruction, T11
begins now without stopping for per-task approval.

---

## T11 SLICE 1 — COURSEARTWORK — DONE, CI-GREEN (2026-09-20)

Full account in `DECISIONS_LOG.md` D134. Ordinary component work under the tiered review policy (not
mandatory-review) — grounded directly in `design-to-code/shared/artwork.json` (the governed 5-motif
spec) and Android's own already-CI-green `CourseArtwork.kt`/`CourseArtworkTest.kt`/
`CourseArtworkHashTest.kt`, front-loading correctness rather than relying on a review pass to catch drift.

**Built:** `Components/CourseArtwork.swift` (new) — `CourseMotif` enum (5 cases, values transcribed
verbatim from `artwork.json`), `courseArtworkHash(seed:)` (32-bit wrapping arithmetic replicating
Kotlin's `Int` overflow / JS `>>> 0` truncation over `seed.utf16`), `motifFor(seed:)`, `CourseArtwork`
(gradient+icon view), `CourseThumbnail` (`AsyncImage` with `CourseArtwork` fallback), `CourseArtworkWithChip`
(+ `CategoryChip` overlay). Two deliberate simplifications carried over from Android's own disclosed
comment: no fine CSS texture layers, 135deg gradient approximated as a diagonal. `iosAppTests/
CourseArtworkTests.swift` (new) — determinism/range tests + a golden-vector test porting Android's
independently-Node-computed `CourseArtworkHashTest.kt` values exactly, proving iOS and Android agree on
the same real formula.

**Design-system discovery:** `theme-checks.js` B1/B2 bans every raw system color name across ALL
production Swift (including `Theme/`) with no carve-out for spec-literal hex values — worked around with
a private `Color(mentoraArtworkHex:)` initializer for every literal color including white.

**Real CI compile failure caught and fixed:** run 35480156929 (commit `d92b6be`) failed with
`type 'any View' cannot conform to 'View'` — `thumbnailShape: (any Shape)?` passed to `.clipShape(_:)`,
which requires a concrete `S: Shape`; an existential `any Shape` can't satisfy that (`Shape: Animatable`
has an associated type). Fixed by changing the property to `AnyShape` (iOS 17's concrete type-erased
`Shape` wrapper, which does conform to plain `Shape`) — committed as `bdf60d9`, confirmed CI-green next
run, no other change needed.

**CI: DONE — GREEN**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35480420960 (commit
`bdf60d9`, all 6 Node gates PASSED, 285/285 tests — up from T10's 282, +3 for `CourseArtworkTests`, 0
failures, 0 unexpected).

**Next:** T11 slice 2 (CourseCard + CourseProgressCard + LearningPathCard + CertificateCard + StatCard),
continuing automatically.

---

## T11 SLICE 2 — COURSECARD/COURSEPROGRESSCARD/LEARNINGPATHCARD/CERTIFICATECARD/STATCARD — DONE, CI-GREEN (2026-09-20)

Full account in `DECISIONS_LOG.md` D135. Ported directly from Android's own already-CI-green
`CourseCard.kt`/`CourseProgressCard.kt`/`LearningPathCard.kt`/`CertificateCard.kt`/`StatCard.kt`.

**Built:** `Components/CourseCard.swift` (`CourseMetaRow`, `BaseCourseCard` shell shared with
`CourseProgressCard`, `CourseCardRules.effectiveProgress`, `CourseCard`), `Components/
CourseProgressCard.swift`, `Components/LearningPathCard.swift`, `Components/CertificateCard.swift`
(+ reusable `CertificatePreviewPlaceholder`), `Components/StatCard.swift`. `iosAppTests/
CourseCardTests.swift` (pure-logic coverage; Android has no dedicated unit tests for any of these 5).

**Self-verify caught 2 real localization-gate violations before any push:** `Text("★")` (Check B1) fixed
with `Text(verbatim: "★")`; `Text("\(studentCount)")` (Check B2, interpolation) fixed with
`Text(String(studentCount))`. Also disclosed: `resumeLabel`/`viewLabel`/`shareLabel` are REQUIRED params
here (no default) since `Components/*.swift` has no `AppEnvironment` to resolve `MentoraStrings` from —
unlike Android's `stringResource(...)` default. Zero new localization keys added.

**CI: DONE — GREEN**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35481265220 (commit
`81e194c`, all 6 Node gates PASSED, 292/292 tests — up from slice 1's 285, +7, 0 failures, 0 unexpected).

**Next:** T11 slice 3 (QuestionCard + AnswerOption + AITutorBubble + AITutorQuickAction +
CheckoutSummary), continuing automatically.

---

## T11 SLICE 3 — QUESTIONCARD/ANSWEROPTION/AITUTORBUBBLE/AITUTORQUICKACTION/CHECKOUTSUMMARY — DONE, CI-GREEN (2026-09-20)

Full account in `DECISIONS_LOG.md` D136. First-attempt CI-green (no fix round needed).

**Built:** `Components/QuestionCard.swift`, `Components/AnswerOption.swift` (5-state answer-option
component, real `Button`+`.disabled(_:)`), `Components/AITutorBubble.swift` (chat bubble, RTL-correct
`UnevenRoundedRectangle` tail corner, `.background(GeometryReader{})`+`PreferenceKey` max-width — a
bare `GeometryReader` collapses variable-height chat text, disclosed in-file), `Components/
AITutorQuickAction.swift` (custom `ButtonStyle`), `Components/CheckoutSummary.swift`. 4 new test files
(22 tests).

**Real finding:** `CheckoutSummary` has no Android `Components/*.kt` equivalent at all — Android keeps
it screen-private inside `DemoCheckoutScreen.kt`. Built here as a real reusable component per the
System Design/Implementation Plan's own T11 file list (ranks 2-3 outrank Android's file layout, rank 7),
sourced from Android's real `CheckoutOrderSummaryCard` content.

**Self-caught bug:** an early draft had a stored property literally named `body: String`, colliding with
`View`'s own required `body` property — fixed before commit (renamed `bodyText`).

**CI: DONE — GREEN, first attempt**, https://github.com/HeshamMohamed94/Mentora/actions/runs/35481725381
(commit `5482ad2`, all 6 Node gates PASSED, 314/314 tests — up from slice 2's 292, +22).

**Next:** T11 slice 4 (LoadingState + EmptyState + ErrorState + SuccessState + MentoraSheet +
MentoraDialog) — the final Component Kit B slice, closing out T11 — continuing automatically.
