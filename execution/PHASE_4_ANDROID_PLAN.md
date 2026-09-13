# Phase 4 — Android: Implementation Plan

**Status:** Authoritative task plan for Phase 4, derived by the `architect` subagent on 2026-09-13
from the Phase 3 → Phase 4 handoff, the actual KMP `shared` public API surface (source-verified, not
docs alone), `product/SCREEN_INVENTORY.md` + `product/INFORMATION_ARCHITECTURE.md § 3` +
`ux/MOBILE_UX.md` + `ux/NAVIGATION_SPEC.md § 3` (the authoritative mobile screen inventory —
assembled from these four rank-1 sources, since no single "mobile screen inventory" file exists),
`design-to-code/shared/platform-contract.json` (the locked Android token-mapping table), and the
locked `design-review-locked/Mentora Showcase.dc.html` (which contains genuine exact mobile device
mockups Phase 2's `design-to-code` pass never captured — see § 4 below). Logged as
`DECISIONS_LOG.md` D78. Read this file before resuming any Phase 4 task — do not re-derive
acceptance criteria from memory. This is an execution planning document, not a locked
architecture/product/ux/design-system file — it may be amended as implementation surfaces new
facts, with the amendment recorded in `DECISIONS_LOG.md`.

Per-task status is tracked in `execution/CURRENT_STATUS.md`'s "PHASE 4 — Task Breakdown" table;
this file is the acceptance-criteria detail that table points to.

---

## 0. Repo state at plan time (verified, not assumed)

| Check | Result |
|---|---|
| HEAD | `adc3099` "Fix KMP release live integration test exclusion" |
| Branch | `main`, in sync with `origin/main` |
| `git status` | clean |
| Phase states | P1/P2/P3 COMPLETE; P4 was `NOT_STARTED` |
| Last decision id | D77 → Phase 4 decisions start at **D78** |
| `mobile/androidApp/` | does not exist yet — `mobile/settings.gradle.kts` includes only `:shared` |
| Local toolchain | Android SDK `android-36`/`android-37.0` platforms, build-tools 35/36, an existing AVD `Chatting_Pixel_8_API_36` — emulator-based verification is feasible |

## 1. KMP public API surface Android consumes

Single entry point: `mobile/shared/src/commonMain/kotlin/com/mentora/shared/MentoraSdk.kt` —
`MentoraSdk.create(environment, platformModule, enableNetworkLogging)` returns one instance exposing
10 façades: `auth`, `user`, `catalog`, `enrollment`, `progress`, `quiz`, `certificates`,
`learningPaths`, `media`, `aiTutor`. Hold exactly one instance for the app's process lifetime —
`ReportPlaybackPositionUseCase`'s throttle state is per-instance (D76). Every façade/use-case
signature, every domain model shape, `ApiResult`/`AuthState`/`ApiEnvironment`/`AppLocale`/
`ThemePreference`/`LocaleResolver`/`AiQuickAction`, the secure-storage classes
(`AndroidTokenStorage`, `AndroidPreferenceStore`, `HttpClientEngineFactory.android`,
`di/PlatformModule.android.kt`), and the exact `LessonPlaybackController`/`PlaybackState` contract
Android must implement are catalogued in full in the architect's research report (preserved in
session history / `DECISIONS_LOG.md` D78) — implementers must read the actual source files cited
there rather than re-deriving signatures from memory.

Key hard constraints carried forward from Phase 3 (do not re-litigate):
- `LessonPlaybackController.prepare(url)` — attach **no** `Authorization` header; the `?token=` in
  the URL is the auth. URL TTL ≈ 5 min; call `refreshPlaybackUrl` + re-`prepare` near expiry.
  `duration` comes only from the player, never the API.
- `Failure.message` is diagnostic-only; UI copy must derive from `ApiErrorCode`.
- `AuthState.Authenticated(user = null)` is legitimate after cold-start restore — follow with
  `getProfile()`.
- `AiQuickAction` carries no localized text; the platform supplies the prompt string; the 5 actions
  are fixed; `courseId`+`lessonContextId` are a send-both-or-neither pair.
- `?language=` threading, CSRF header value, no-cookie-jar rule, `AUTH_TOKEN_INVALID` vs
  `_EXPIRED` — all already handled inside `shared`. Do not reimplement in Android.

## 2. Authoritative Android/Mobile screen inventory (18 screens in scope)

Assembled from `product/SCREEN_INVENTORY.md` (per-screen `Platform:` field),
`product/INFORMATION_ARCHITECTURE.md § 3` (mobile IA tree), `ux/MOBILE_UX.md` (whole file), and
`ux/NAVIGATION_SPEC.md § 3` (per-screen mobile nav graph) — **not** derived from
`web/src/components/screens/`, which includes 10 Web-only Instructor/Admin screens plus Landing.

**In scope:** Explore (+ Learning Paths segment), Course Details, Learning Paths, Learning Path
Details, Login, Register, Home, My Learning (+ Certificates entry), Course Player, Quiz, Quiz
Results, Certificates List, Certificate Detail, AI Tutor, Profile, Settings, Demo Checkout,
Purchase Success.

**Explicitly out of scope:** Landing ("mobile has no marketing landing page" —
`SCREEN_INVENTORY.md`), all 5 Instructor screens and all 5 Admin screens (`Platform: Web`;
Instructor/Admin native mobile is Post-MVP per `MVP_SCOPE.md`), a standalone "Progress" screen
(doesn't exist — progress is a property of other screens), Forgot Password (Post-MVP).

**Locked non-screen behaviors:** 5-item `MobileBottomNavigation` (Home, Explore, **My Learning**,
AI Tutor, Profile — no badges); tap-active-tab pops to root; independent per-tab back stacks; nav
hidden on Course Player + Quiz; Curriculum Bottom Sheet (never beside the video); scrubber never
mirrors in RTL; keyboard-avoidance with AI Tutor input pinned above keyboard and staying open after
send; guest enroll/follow gate returns to original intent; quiz answers survive back-navigation;
Purchase Success back → My Learning tab.

## 3. Design token → Compose mapping

Already locked in `design-to-code/shared/platform-contract.json`'s `"android"` block — do not
re-derive. Colors map to M3 `ColorScheme` slots (light and dark reuse the *identical* slot map, only
values differ); `success`/`warning`/`info` need a custom `CompositionLocal` (no M3 slot). Typography:
`display/heading/body/label/caption` → `displayLarge…labelSmall`. Shape: `radius.small/medium/large/
xlarge/full` → `Shapes.extraSmall/small/medium/large/CircleShape` (xlarge = top-corners-only for
sheets/dialogs). Elevation: prefer `BorderStroke` + low tonal elevation over heavy shadow. **Material
You dynamic color is explicitly forbidden** (visual parity rule, D53). Units: `sp` for text, `dp` for
layout, 48dp minimum touch target. Fonts: Roboto (Latin, system), Noto Sans Arabic (Arabic,
`letterSpacing 0`, body line-height ×1.1). Western numerals always, both locales. Generator route is
pre-decided: `tools/token-pipeline/generate.js` gains an Android output target
(`mobile/androidApp/.../theme/MentoraTokens.kt`), per the script's own note and ADR-011/D35.

## 4. Mobile visual references exist in the locked showcase — Phase 2 missed them

`design-to-code/screens/*.json` are Web-composed and `platform-contract.json` calls Android
mapping-only, but `design-review-locked/Mentora Showcase.dc.html` contains genuine exact mobile
device mockups: § 17 (`MobileBottomNavigation`, LTR **and** RTL with real Arabic labels), § 20
("MOBILE · 360" Explore composition), § 22 ("MOBILE PREVIEW" 340×788 frames for Home, Explore,
Course Details, My Learning, AI Tutor, Course Player [curriculum in a Bottom Sheet], Demo Checkout,
Purchase Success, and Home · Arabic RTL), § 19 (`PLAYER · EN` / `PLAYER · AR` — chrome mirrors,
scrubber stays LTR). **Task 3 extracts these into `design-to-code/screens/mobile-*.json` before any
of those 8 screens are built** — skipping this repeats the D48/D49 web rework cycle at mobile scale.
One showcase-internal inconsistency (bottom-nav item 3 labelled "Learning" in §§20/22 vs "My
Learning" in §17/rank-1 docs) resolves to **"My Learning"** per rank-1 precedence.

## 5. Task list (20 tasks, dependency-ordered)

Full acceptance criteria, KMP APIs consumed, produced files, tests, visual-reference classification,
and git-checkpoint message for each task are recorded in `DECISIONS_LOG.md` D78 (the architect's full
report) and restated per-task in `CURRENT_STATUS.md` as each task starts/completes. Summary:

| # | Task | Reference type |
|---|---|---|
| T1 | `:androidApp` module scaffold + version catalog + toolchain verification | n/a (infra) |
| T2 | Token pipeline Android target + `MentoraTheme` | exact-showcase (values) |
| T3 | Extract locked mobile visual references into `design-to-code/screens/mobile-*.json` | exact-showcase |
| T4 | App bootstrap: SDK wiring, session restore, locale/theme bootstrap, Keystore instrumented test | ux-only |
| T5 | Core component kit A (atoms: Button, TextField, PasswordField, Icon, Badge, Chip, ProgressBar, Avatar, Tabs, Select, Snackbar) | exact-showcase/approved-pattern |
| T6 | Navigation shell: 5-tab bottom nav, per-tab back stacks, guest mode, auth gate | exact-showcase (nav) |
| T7 | Auth screens (Login, Register) | approved-pattern |
| T8 | Core component kit B (CourseCard/Thumbnail, CourseProgressCard, LearningPathCard, CertificateCard, StatCard, SearchField, Quiz cards, AI Tutor bubbles, state patterns, sheets) | exact-showcase |
| T9 | Explore (+ Learning Paths segment) | exact-showcase |
| T10 | Course Details | exact-showcase |
| T11 | Demo Checkout + Purchase Success | exact-showcase |
| T12 | Home + My Learning (+ Certificates entry) | exact-showcase |
| T13 | `LessonPlaybackController` (ExoPlayer/Media3) + Course Player + Curriculum Bottom Sheet | exact-showcase + ux-only (highest-risk task) |
| T14 | Quiz + Quiz Results | ux-only (honest gap, no mobile mockup) |
| T15 | Certificates List + Certificate Detail | ux-only (honest gap) |
| T16 | Learning Path Details (follow/unfollow) | ux-only (honest gap) |
| T17 | AI Tutor (streaming chat, stub provider) | exact-showcase + approved-pattern |
| T18 | Profile + Settings (language selector, theme, logout) | ux-only (honest gap) |
| T19 | Localization/RTL/theme/font-scale QA sweep + Compose UI test suite completion | n/a (audit) |
| T20 | Live emulator verification, `androidApp/README.md`, Phase 4 → Phase 5 handoff | n/a |

Convention: one git commit per task (functional + visual verification, tests, diff review) plus a
`CURRENT_STATUS.md` continuity commit — never one giant Phase-4 commit. Gates re-checked at every
checkpoint: `:androidApp:assembleDebug`, `:androidApp:testDebugUnitTest`, and
`:shared:testDebugUnitTest` (must stay 249/249 — Phase 3 regression guard).

## 6. Known gaps and their resolutions (none rise to a blocker — all have a clean in-plan resolution)

- **G1 (theme read path).** `UserFacade.setTheme` is write-only; no `observeTheme`. Resolution: the
  app constructs and retains `AndroidPreferenceStore(context)` itself (public class) in its own
  platform module instead of calling `platformModule(context)`, giving it read access — zero
  `shared` change.
- **G2 (first-run locale).** `AndroidPreferenceStore` defaults to `"en"`; `resolveInitialLocale` is
  never called by `shared` (by design, deferred to Phase 4/5). Resolution: T4 calls it explicitly on
  first run behind an app-owned flag.
- **G3 (My Learning progress join).** `GetMyLearningUseCase`/`MyLearningItem` omit per-course
  progress; Home/My Learning need it. **Decision: join in `androidApp` (T12), mirroring web's
  accepted N+1-at-demo-scale pattern (D40/D37), not by extending `shared`.** Tradeoff: Phase 5 (iOS)
  will have to repeat this join and the two clients could drift — flagged for the user to decide at
  Phase 5 planning time whether to promote it into `shared` then.
- **G4 (no `isEnrolled` on `Course`).** Course Details derives enrollment membership from
  `listEnrollments()` (T10).
- **G5 (`LearningPath` list lacks `isFollowing`).** Followed-paths modules call
  `getLearningPathDetail` per path (N+1, trivial at seed scale — 1 path today).
- **G6 (Phase 4 touches two non-`mobile/` directories).** T2 extends
  `tools/token-pipeline/generate.js`; T3 adds files under `design-to-code/`. Both are the
  already-documented route (D35, `generate.js`'s own note, `REPOSITORY_STRUCTURE.md`,
  `platform-contract.json`) — not scope creep. Guardrail: T2/T3 must leave web's generated output
  byte-identical (or additively changed with the web gate re-verified green); `architecture/`,
  `product/`, `ux/`, `design-system/`, `design-review-locked/`, `backend/`, `web/src/` stay
  untouched.
- **G7 (missed mobile showcase mockups).** Resolved by T3 — see § 4.
- **G8 (icon source).** No Material Symbols asset exists anywhere in the repo. Resolution (D80,
  recorded at T5): port web's existing 42-icon hand-drawn `ImageVector` set to Compose rather than
  adding `material-icons-extended` — keeps Web/Android visually identical and inherits, not creates,
  the disclosed icon-fidelity gap.
- **G9 (no certificate image asset).** Same disclosed placeholder-document treatment as web's D42.
- **G10 (no password-change endpoint).** Locked-spec-vs-backend conflict inherited from web (D44);
  Settings omits the field again, disclosed in the Phase 4 handoff.
- **G11 (showcase-internal nav-label inconsistency).** Resolved to "My Learning" (§ 4).

## 7. Execution risks to carry into implementation

- T13 (ExoPlayer + Curriculum Bottom Sheet + 5-minute URL TTL + RTL scrubber exception) is the
  single riskiest task — land the playback controller as its own sub-commit before the full player
  screen if it grows large.
- No offline cache exists anywhere; `UX_STATES.md § 4` still requires offline detection with specific
  copy — implement connectivity detection directly, do **not** add SQLDelight/Room.
- Do not attempt to compile `iosMain` on this Windows host. Do not start Phase 5 or Phase 6 provider
  work under any circumstances before explicit approval.
- `:shared:testDebugUnitTest` must stay 249/249 at every checkpoint; `:shared:liveBackendIntegrationTest`
  stays isolated, never folded into an Android gate.
