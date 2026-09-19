# Phase 5 — iOS: Acceptance Criteria

**Status:** Authoritative acceptance-criteria document for Phase 5, derived by the `architect`
subagent on 2026-09-18 from the user's Phase 5 kickoff prompt (which fixes categories **A–J** below
verbatim), the Phase 3/Phase 4 handoff entries in `execution/PHASE_HANDOFF.md`, the real
`mobile/shared` source (façade signatures and `iosMain` read directly, not from docs alone),
`execution/MASTER_IMPLEMENTATION_PLAN.md`'s Phase 5 milestone slice, `execution/INTEGRATION_CONTRACT.md § 12`,
`design-to-code/shared/platform-contract.json#/ios`, the locked Design System v1.3.2, and
`product/SCREEN_INVENTORY.md`. Companion documents: `execution/PHASE_5_IOS_SYSTEM_DESIGN.md` (the
how) and `execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md` (the task sequence). To be logged as
`DECISIONS_LOG.md` **D96** (last recorded decision is D95).

This is an execution planning document, not a locked architecture/product/ux/design-system file — it
may be amended as implementation surfaces new facts, with the amendment recorded in
`DECISIONS_LOG.md`. It does **not** introduce product scope: every criterion traces to an existing
locked/approved source, except the few explicitly tagged **[NEW-P5]** (newly formalized for Phase 5
because no prior document covers the iOS-specific case).

---

## 0. Repo state at plan time (verified, not assumed)

| Check | Result |
|---|---|
| HEAD | `8feacad` "Phase 4 polish follow-up: dark-theme badge contrast, Arabic step-numbering bidi fix" |
| Branch | `main`, in sync with `origin/main` |
| `git status` | one pre-existing uncommitted local change: `mobile/gradle.properties` (**out of scope — left untouched by this plan**) |
| Phase states | P1/P2/P3/P4 COMPLETE; P5 `NOT_STARTED` |
| Last decision id | D95 → Phase 5 decisions start at **D96** |
| `mobile/iosApp/` | does not exist; `mobile/settings.gradle.kts` includes `:shared` and `:androidApp` only |
| `mobile/shared/src/iosMain/` | 4 Kotlin files on disk (`IosTokenStorage`, `IosPreferenceStore`, `HttpClientEngineFactory.ios.kt`, `PlatformModule.ios.kt`), **not wired into `shared/build.gradle.kts`** (D69; `PHASE_HANDOFF.md` P3 § 6 item 1) |
| Toolchain | Kotlin 2.0.21, AGP 8.9.2, Gradle wrapper 8.11.1, SKIE 0.9.5 (declared `apply false`; applied only `if (isMacOs)`) |
| Host | Windows 11 / MINGW64. `which xcodebuild` → not found. **No macOS, no Xcode, no iOS Simulator on this machine.** |
| Standing green gates | `:shared:testDebugUnitTest` **249/249**; `:androidApp:testDebugUnitTest` **241/241**; `:androidApp:connectedDebugAndroidTest` **106/106** |

---

## 1. Verification-host classification (applies to every criterion below)

Phase 5 is the first phase whose primary artifact **cannot be compiled on the development host**.
Every criterion therefore carries a host tag. No criterion may be marked PASS on the strength of a
simulated, imagined, or "should work" verification.

| Tag | Meaning |
|---|---|
| **W** | Fully verifiable on this Windows host (Gradle configuration/compile of `:shared`'s JVM+Android surface, Node generator runs, file/JSON/XML structure checks, greps, diff review, doc review). |
| **M** | Requires a real macOS host with Xcode + iOS Simulator. Cannot be verified here at all. |
| **C** (added by **D100**) | Verified by the automated GitHub Actions macOS pipeline (`.github/workflows/ios-ci.yml`, Task T4c) — a `macos-15` runner with **no human and no backend**. A `C` tag means a real macOS toolchain compiled/ran the thing: Kotlin/Native compile+link, SKIE application, `:shared:iosSimulatorArm64Test`, XCFramework assembly, `xcodegen generate`, `xcodebuild build`, and the `iosAppTests` XCTest target. **`C` is a strict subset of `M` capability, never a substitute for it:** CI has no eyes, no VoiceOver, no backend and no launched app, so nothing visual, live or accessibility-shaped can ever be `C`. |
| **W→M** | Authored and statically reviewable here; **truth is only established on a Mac**. A W-side result for these is never more than "authored and reviewed" — recorded as PARTIAL until the Mac checkpoint clears it. |

**A `W` tag is only honest if the named evidence actually executes here.** Swift never compiles on
this host (J1), so **no XCTest is ever `W` evidence** — a criterion whose only proof is a unit test is
`W→M` even when its subject matter (a string table, a mapping table) looks static. **D100 refinement:**
such a criterion is now **`C`** rather than `M` when its evidence is genuinely a compile or a
fake-driven unit test, because the CI pipeline really does run those. It stays `M` the moment the
evidence needs a screen, a backend, a gesture or a human judgement. Every retag below was made on
that test alone, and deliberately conservatively: **no live, visual, RTL, Dynamic-Type, VoiceOver,
playback or session-persistence criterion was moved.** Where a criterion
genuinely needs a Windows-executable check, this plan names a **standalone Node script** as its own
artifact rather than implying an XCTest runs here:

| Script | Lives at | What it genuinely proves on Windows |
|---|---|---|
| Catalog / mapping checker | `tools/ios-checks/catalog-parity.js` | `Localizable.xcstrings` is valid JSON; EN/AR key parity; per-key format-specifier parity; **only iOS-valid specifiers** (`%N$@`, never `%N$s`); every `ApiErrorCode` has a copy key. Zero-dependency Node, same precedent as `tools/token-pipeline/generate.js`. |
| Asset/structure checker | `tools/ios-checks/assets-check.js` | 46 semantic colorsets + shadow colorsets present with `any`+`dark`; 42 icon imagesets; template rendering intent; SVG well-formedness and 24x24 viewBox; icon-name set equals Android's `MentoraIconName`; mirror set equals Android's `autoMirror` set. |
| Theme/token completion-gate checker | `tools/ios-checks/theme-checks.js` | Task T6's completion gate over comment-stripped `.swift` source text: `Font.system(size:weight:)`/`.system(size:` appears exactly once (in `MentoraTypography.swift`), zero `UIFontMetrics`, zero raw system colors outside a `mentora*` semantic token, the 5 D121 theme-root gate patterns (`preferredColorScheme(`, locale/`layoutDirection` environment writes, `.mentoraTheme(`, the Arabic locale literal) each scoped to `Theme/MentoraTheme.swift` only, plus the token gallery's structural/color-list/preview-name completeness. Zero-dependency Node, same precedent as the other two scripts. |

All three are new files under `tools/`, outside every path A8 protects. Anything **not** provable by
one of those three scripts, by Gradle, by the token generator, or by grep/diff review is not `W`.

**CI-1** (the standing automated gate, D100) plus four named Mac checkpoints are defined in
`PHASE_5_IOS_IMPLEMENTATION_PLAN.md § 3`
(**MC-1** KMP/iOS toolchain, **MC-2** app boots + session/Keychain, **MC-3** feature-complete
simulator pass, **MC-4** final acceptance). Each criterion names the checkpoint that can clear it.

## 2. Audit methodology (mirrors Phase 4's own final acceptance review, commit `90c8af3`)

Phase 4's acceptance audit was an **independent pass that re-ran the real thing and found two live
defects the automated suite had not**: a dead Quiz-Results "Continue" reachable only via the full
guest→login→demo-purchase→player→quiz chain, and illegible status-bar icons under `targetSdk 36`
edge-to-edge. Phase 5's audit inherits that standard exactly:

1. Every criterion is audited as **Criterion → implementation (file:symbol) → evidence
   (test name / command output / live observation) → verdict**.
2. Verdicts are one of: **PASS**, **FAIL**, **PARTIAL** (implemented, evidence incomplete),
   **NOT TESTABLE (HOST)** (needs a Mac, none available), **N/A (SCOPED OUT)** (with the citation
   that scoped it out).
3. "It compiles" is never evidence for a behavioral criterion, and a green unit/UI test is never a
   substitute for walking the real user chain end to end on a real simulator against the real local
   backend — that is exactly what caught both Phase 4 defects.
4. Any criterion that ends the phase as anything other than PASS is copied verbatim into
   `PHASE_HANDOFF.md`'s Phase 5 § 6 "Known limitations" with its verdict and reason. Nothing is
   silently dropped.

---

## A. Platform

Native SwiftUI, real KMP integration, no duplicated business logic, clean SwiftUI/shared boundary.

| ID | Criterion | Source | Evidence required | Host |
|---|---|---|---|---|
| A1 | The iOS app is a real, native **SwiftUI** app (SwiftUI view hierarchy, `NavigationStack`, SwiftUI state primitives). No Compose Multiplatform, no UIKit-first architecture, no web view. UIKit appears only where SwiftUI has no equivalent (e.g. `AVPlayerLayer` hosting via `UIViewRepresentable`), each occurrence justified in a code comment. | `ADR-002` ("UI is never shared"; Compose-MP explicitly rejected); `MASTER_IMPLEMENTATION_PLAN.md` Phase 5 | Grep: zero `compose`/`WKWebView` hits; enumerated list of `UIViewRepresentable`/`UIViewControllerRepresentable` uses with justification | W→M (MC-3) |
| A2 | The app consumes `mobile/shared` **only** through `MentoraSdk` and its 10 façade properties for all **domain** access. No Swift code references a repository, `ApiClient`, `HttpClient`, Ktor, or Koin type beyond passing the opaque platform `Module` value through. **Sanctioned non-façade entry points — this list is exhaustive, and the MC-4 audit checks that it has not grown** (see `PHASE_5_IOS_SYSTEM_DESIGN.md § 8`): (1) `MentoraSdk.Companion.create(environment:platformModule:enableNetworkLogging:)`, once, in `AppEnvironment`; (2) `ApiEnvironment.Companion.iosSimulator()` / `.lan(host:port:)`; (3) `platformModule()`, the `iosMain` top-level function, passed straight into `create`; (4) the top-level `resolveInitialLocale(systemLocales:)` in `mobile/shared/.../settings/LocaleResolver.kt`, which is on **no façade at all** and is the only way to satisfy H7; (5) exactly one additional `IosPreferenceStore()` construction, used **only** for the cold-start `getTheme()` read (never for locale); **(6) the `iosMain` top-level `KeychainStatus.failures` `StateFlow`, observed exactly once, in `SessionController`** — added by the T1b Keychain correctness fix so a Keychain write/delete failure cannot be reported to the user as success (`PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1` K3; criterion B10). It exists because A6 forbids changing `commonMain`'s `TokenStorage` contract, which leaves no in-band channel. None of these six is a violation. | `PHASE_HANDOFF.md` P3 § 8 / P4 § 8; `mobile/shared/README.md`; `PHASE_5_IOS_SYSTEM_DESIGN.md § 8` | Grep of `mobile/iosApp/` for `ApiClient`/`HttpClient`/`Koin`/`Ktor`/`RepositoryImpl` → zero hits outside the single DI bootstrap file; plus an explicit enumeration of the five entry points above showing each appears exactly once | W |
| A3 | Exactly **one** `MentoraSdk` instance exists for the app's process lifetime, created once at app start and injected downward; never re-created per screen or per call. | D76 (`ReportPlaybackPositionUseCase`'s throttle is per-instance); `mobile/shared/README.md` | Grep: exactly one `MentoraSdk.companion.create(...)` call site; code read of the injection path | W |
| A4 | No business logic `shared` already owns is reimplemented in Swift: no HTTP call, no envelope/error parsing, no token refresh, no CSRF header, no `?language=` threading, no email/password rule, no quiz grading, no completion-percent computation. | `INTEGRATION_CONTRACT.md §§ 10, 12`; `PHASE_HANDOFF.md` P3 § 9 | Grep of `mobile/iosApp/` for `URLSession`, `Authorization`, `X-Requested-With`, `language=`, `Bearer` → zero hits; reviewer diff read | W |
| A5 | The SwiftUI↔shared boundary is crossed through one named adapter layer (`Support/SharedBridge/`), not ad hoc: Kotlin interop types (`KotlinBoolean`, `KotlinUnit`, `KotlinInt`, raw `ApiResult`) never appear in a `Features/` or `Components/` file. | **[NEW-P5]** (see `PHASE_5_IOS_SYSTEM_DESIGN.md § 2`) | Grep for `Kotlin[A-Z]`/`ApiResult` under `Features/` + `Components/` → zero hits | W |
| A6 | `mobile/shared` gains **no new public API and no behavior change** for iOS's benefit. The only sanctioned `shared` diff is `build.gradle.kts` source-set/SKIE/XCFramework wiring, plus a genuine `iosMain` defect fix if one is found on a real Mac — recorded as its own decision entry. | `PHASE_HANDOFF.md` P3 § 9 | `git diff --stat mobile/shared/src/commonMain` over the phase range → empty | W |
| A7 | `mobile/androidApp/` is not modified by any Phase 5 commit. | `PHASE_HANDOFF.md` P4 § 9 | `git log --name-only` over the phase range → zero `mobile/androidApp/` paths | W |
| A8 | `architecture/`, `product/`, `ux/`, `design-system/`, `design-review-locked/`, `backend/`, `web/src/` are untouched. Sanctioned additive exceptions, and only these **four** — **(d) was added by D100: `.github/workflows/ios-ci.yml`**, a new top-level path that is CI infrastructure rather than app, product or backend code, introduced by Task T4c. It may contain no application logic, reference no secret, and invoke no Android/Web/backend gate; that boundary is itself audited at MC-4. The original three: **(a)** `tools/token-pipeline/generate.js` (new iOS output target); **(b)** new files under `design-to-code/`; **(c)** an **additive/corrective update to the existing `ios` section of `design-to-code/shared/platform-contract.json`** — that section is stale (`status: "NOT IMPLEMENTED — mapping only"`, a `wouldGenerateInto` path and a `wouldConsume` generator that do not match this plan, and `typographyMapping` prose describing an unimplementable `Font.mentora*` API — see G3). Refreshing it mirrors the **Android precedent exactly**: Phase 4 commit `79fc51d` additively rewrote the `android` section's `readinessNotes` the same way. Constraints on (c): the `ios` section only; no change to the `web`/`android` sections or to `visualParityRule`. All three of (a)-(c) must leave Web's and Android's generated output byte-identical — (d) is CI infrastructure and produces no generated output to compare against. | `PHASE_4_ANDROID_PLAN.md § 6` G6 precedent; commit `79fc51d`; ADR-011 | `git log --name-only`; generator re-run showing zero diff in web/Android outputs; diff review of the `ios` section | W |

## B. Authentication

Reuses `AuthState`/`SessionManager` from `shared`; iOS adds no session logic of its own.

| ID | Criterion | Source | Evidence required | Host |
|---|---|---|---|---|
| B1 | Login works against the real local backend via `sdk.auth.login`; a wrong password surfaces exactly one generic message derived from `ApiErrorCode.AuthInvalidCredentials` — never `Failure.message` verbatim, never a field-level hint. | `PHASE_3_KMP_PLAN.md` T5; `ApiResult` kdoc ("`message` is diagnostic-only") | Live simulator run + a unit test over the error-copy mapper | W→M (MC-2) |
| B2 | Registration works via `sdk.auth.register`; `EmailValidator`/`PasswordValidator` from `shared` drive inline validation (not a Swift reimplementation), and server `fields[...]` errors route to the matching inline field (the D51/D93 precedent). | `PHASE_3_KMP_PLAN.md` T5; D51 | Live run of both failure shapes + unit tests | W→M (MC-2) |
| B3 | The session persists across full app termination and cold relaunch: tokens live in the **iOS Keychain** via the existing `IosTokenStorage` — never `NSUserDefaults`, a plist, or `@AppStorage`. | D70 (Keystore precedent); `AUTH_SECURITY.md § 4`; `PlatformModule.ios.kt` | Live: log in → kill app → relaunch → still authenticated; plus a Keychain round-trip XCTest, **the B10 failure-injection tests — one successful round trip is not sufficient evidence**, and a grep proving no token-shaped value reaches `UserDefaults` | M (MC-2) |
| B4 | Cold start runs exactly one ordered bootstrap — `restoreSession()` once, then first-run locale seeding — from a single app-scoped task, never from a view's `.task`/`onAppear` (which can run twice). | Android `MentoraApplication` F3 fix (D93 context); G2 | Code read (exactly one `restoreSession` call site) + a launch log showing one call | W→M (MC-2) |
| B5 | `AuthState.Authenticated(user: nil)` after cold-start restore is handled by following up with `sdk.user.getProfile()` exactly once — not treated as an error, not treated as logged out, and not looped. | `PHASE_4_ANDROID_PLAN.md § 1`; D71 | Unit test over the session model + live cold-start observation | W→M (MC-2) |
| B6 | Logout clears the Keychain, returns to the unauthenticated surface, and leaves no authenticated screen mounted underneath. **A Keychain delete that genuinely fails must not present as a completed logout** (B10) — the shipped `IosTokenStorage` discards `SecItemDelete`'s status, which is exactly how a logged-out-looking app keeps a live session across relaunch. | `SCREEN_INVENTORY.md § 16`; `MOBILE_UX.md § 13`; `PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1` | Live run; Keychain read-after-logout assertion; plus the injected-delete-failure test from B10 | M (MC-3) |
| B7 | Session expiry is handled **entirely inside `shared`** (401 → single-flight refresh → retry, with both `AUTH_TOKEN_INVALID` and `AUTH_TOKEN_EXPIRED` as triggers). iOS adds no refresh logic and no 401 interceptor, and does not re-litigate D77. On genuine refresh failure the app observes `AuthState.Unauthenticated` and routes to the unauthenticated surface without losing the user's intent. | D77; `INTEGRATION_CONTRACT.md § 12` item 4 | Grep: no refresh logic in Swift; live forced-expiry run (corrupt the stored access token, then act) | W + M (MC-3) |
| B8 | Guest browsing works on the public surfaces, and an auth-gated action (enroll, follow, continue learning) routes to Login and **returns to the original intent** on success. | `PHASE_4_ANDROID_PLAN.md § 2`; `NAVIGATION_SPEC.md § 3` | Live run of all three gated entry points | M (MC-3) |
| B9 | Auth rate limiting (10/min → `RATE_LIMITED_AUTH`, which arrives with no JSON envelope and is synthesized inside `shared`) renders as its own distinct localized message, not a generic failure. | `INTEGRATION_CONTRACT.md § 12` item 3 | XCTest on the `ErrorCopy` mapper; live observation if reproduced. **Retagged W→M (honesty fix):** the only evidence here is a Swift unit test, and per J1 no Swift is compiled on this host — so the Windows-side result is "authored and reviewed", never PASS. A catalog-only check that the `RATE_LIMITED_AUTH` key exists and differs from the generic key **can** run on Windows via `tools/ios-checks/catalog-parity.js`, but key existence is not the criterion. | W→M (MC-3) |
| B10 | **A Keychain write or delete failure is never reported to the UI as success.** Every `OSStatus` from a Keychain add/update/delete/query is inspected; `errSecItemNotFound` is treated as the expected "no stored session" outcome and never as an error; the token pair is stored as **one** item so a partial/mismatched write is structurally impossible; a failed logout delete is retried and then neutralized by tombstone-overwrite; and any surviving genuine failure is published on the `iosMain` `KeychainStatus.failures` flow, which `SessionController` observes so login/logout surface it instead of claiming success (throwing is unavailable — a non-`@Throws` Kotlin exception crossing into Swift terminates the process, and adding `@Throws` would be a `commonMain` change A6 forbids). Items carry an explicit `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`. **Accepted and explicitly not fixed:** a fresh install on a device that previously had Mentora may resume the prior session, because no first-launch install-marker purge is implemented. | **[NEW-P5]** — the defect is in the shipped Phase 3 `IosTokenStorage` and was found by code review, not on a Mac (see F3 Category 2); `AUTH_SECURITY.md § 4`; **ADR-012** for the accepted reinstall behavior; `PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1` | Kotlin failure-injection tests in `iosTest` against a fake `KeychainStore` covering add/update/delete/query failure, `errSecItemNotFound`, the tombstone path, a malformed payload, and "no published failure ever contains a token" (`:shared:iosSimulatorArm64Test`, **CI-1**); **plus** a live MC-2 check that a logout whose delete fails does not present as a completed logout. A single successful round trip is explicitly **not** sufficient evidence for B3/B6/B10. | W-auth / **C (CI-1 runs the failure-injection tests)** / **M (MC-2 live)** |

## C. Student Experience — the reconciled screen list

**Reconciliation (read before implementing).** The user's kickoff prompt names 17 screens.
`product/SCREEN_INVENTORY.md` defines **18** mobile-platform screens (entries 2–19; entry 1 Landing is
Web-only), and Phase 4 shipped exactly those 18 (`PHASE_HANDOFF.md` P4 § 1). **Phase 5 ships the same
18**, with three documented reconciliations and no silent divergence:

- **C-note-1 — "Progress" is not a screen.** `ux/MOBILE_UX.md § 9` and `PHASE_4_ANDROID_PLAN.md § 2`
  are explicit: mobile has no dedicated Progress screen; progress is a property rendered inside Home,
  My Learning, Course Details and Course Player. Phase 5 inherits this.
- **C-note-2 — "Edit Profile" is not a separate screen/route.** `SCREEN_INVENTORY.md § 16` lists it as
  a primary *action* on Profile; Android implements it as inline, name-only editing inside
  `ProfileScreen` (D93 — no password-change endpoint exists at all). Phase 5 does the same.
- **C-note-3 — Learning Paths (list) is an inventory screen (§ 4) the user's list omits.** Android
  satisfies it as a segment inside Explore (`MOBILE_UX.md § 3`), not a second pushed screen. Phase 5
  does the same, so the route count is 17 for 18 screen concepts.

| ID | Screen (inventory §) | Must-have behavior | Host |
|---|---|---|---|
| C1 | Login (§ 6) | B1/B2 error + validation states; link to Register | M (MC-2) |
| C2 | Register (§ 7) | As B2; post-register lands authenticated | M (MC-2) |
| C3 | Home / Dashboard (§ 8) | Continue-learning module with real per-course progress, stats, recommendations; guest variant | M (MC-3) |
| C4 | Explore (§ 2) + Learning Paths segment (§ 4) | Search, category filter, cursor pagination, segment switch; language-driven result set via `shared` | M (MC-3) |
| C5 | Course Details (§ 3) | Curriculum read from the same detail response (no second call, `INTEGRATION_CONTRACT.md § 12` item 5); enrollment membership derived from `listEnrollments()` (G4); enroll/continue CTA | M (MC-3) |
| C6 | Learning Path Details (§ 5) | Follow/unfollow (idempotent), curated course order preserved, guest-safe (progressPercent nil) | M (MC-3) |
| C7 | Demo Checkout (§ 18) | Preview via `getCheckoutPreview`; Complete Demo Purchase calls `completeDemoCheckout` (201 first, 200 on idempotent repeat, never an error); **zero payment vocabulary** | M (MC-3) |
| C8 | Purchase Success (§ 19) | Success state; Start Learning goes to Course Player; back goes to the My Learning tab | M (MC-3) |
| C9 | My Learning (§ 9) | Enrolled courses with progress, Certificates entry point; `GetMyLearningUseCase` fails the whole call on a composition error (P3 § 6 item 6) and must render a retriable error state, never a silently-partial list. **The Swift progress join must match Android's shape exactly** (`GetMyLearningWithProgressUseCase.kt`, read directly): (a) it **eagerly drains every page** of `getMyLearning` (`do { ... } while (cursor != null)`, `PageLimit = 50`) — this is **not** incremental UI paging, and iOS must not introduce incremental paging here or the two clients show different data at the same scroll position; (b) the **per-course progress join also fails the whole call** — the first `Failure` from *either* the list call or *any* `getCourseProgress` call is the join's result, with no partial-list fallback; (c) calls stay **sequential** (no `TaskGroup`, which would change observed failure ordering). Request count is roughly **2N+1** (list pages + N progress calls), not "N+1" — paging itself costs more than one request. | M (MC-3) |
| C10 | Course Player (§ 10) | Real AVPlayer playback of the seeded lesson video; position heartbeat; mark-complete + auto-advance (both from `shared`); playback-URL refresh near the 5-minute TTL; Curriculum **sheet** (never beside the video); tab bar hidden | M (MC-3) |
| C11 | Quiz (§ 11) | Options carry no isCorrect pre-submission; all-answered validation before submit; answers survive back-navigation; tab bar hidden | M (MC-3) |
| C12 | Quiz Results (§ 12) | Score/pass/breakdown from the server; **Continue is a live, non-dead action on every reachable entry path** (the exact defect Phase 4's acceptance audit found, `90c8af3`) | M (MC-3) |
| C13 | Certificates List (§ 13) | Paged list; empty state when none | M (MC-3) |
| C14 | Certificate Detail (§ 14) | Frozen snapshot fields rendered verbatim; the opaque MTR id never parsed or reformatted; share affordance is UI-only | M (MC-3) |
| C15 | AI Tutor (§ 15) | Real streaming chat against the still-stubbed backend provider; paged history; the 5 fixed quick actions with **iOS-supplied localized prompt text**; courseId + lessonContextId send-both-or-neither | M (MC-3) |
| C16 | Profile (§ 16) | Avatar/initials, name, email, stats; inline name-only edit (C-note-2); Logout lives here, not duplicated in Settings (`MOBILE_UX.md § 13`, D93) | M (MC-3) |
| C17 | Settings (§ 17) | Theme override; **functional** Language selector (category H); no password/account fields (no endpoint exists) | M (MC-3) |
| C18 | Learning Paths list (§ 4) | Rendered as the Explore segment (C-note-3) | M (MC-3) |
| C19 | Explicitly out of scope | Landing; all 5 Instructor and all 5 Admin screens; Forgot Password; a standalone Progress screen; a separate Edit Profile route — per `SCREEN_INVENTORY.md`, `MVP_SCOPE.md § 2`, `USER_ROLES.md` | — |

## D. Navigation

| ID | Criterion | Source | Evidence | Host |
|---|---|---|---|---|
| D1 | Root is a 5-tab `TabView` (Home, Explore, **My Learning**, AI Tutor, Profile — locked order, no badges), each tab owning its own `NavigationStack` and path. | `design-to-code/shared/navigation.json#/shells/mobileStudentShell`; `MOBILE_UX.md § 1` | Live run + XCUITest | W→M (MC-3) |
| D2 | Tapping the already-active tab pops that tab's stack to its root. | `MOBILE_UX.md § 1` | XCUITest + live | M (MC-3) |
| D3 | Per-tab back stacks are genuinely independent: switching away and back restores that tab's stack exactly. | `MOBILE_UX.md § 2` | XCUITest + live | M (MC-3) |
| D4 | A screen reachable from more than one tab (Course Details, Course Player, Quiz, Quiz Results, Learning Path Details) pushes onto the **currently active** tab's stack, never another tab's, and its terminal actions behave on **every** reachable path. | `NAVIGATION_SPEC.md § 3`; the Phase 4 acceptance defect (`90c8af3`) | XCUITest reproducing the full guest to login to demo-purchase to player to quiz to results chain | M (MC-3) |
| D5 | The tab bar is fully hidden on Course Player and Quiz (mobile hides it; it does not collapse — the disclosed platform difference recorded in `platform-contract.json`). | `MOBILE_UX.md §§ 6, 8`; `navigation.json` focused-learning shell | Live + XCUITest | M (MC-3) |
| D6 | Authenticated vs. unauthenticated routing is driven solely by `AuthState` observed from `shared`; no duplicated Swift-side logged-in flag. | ADR-002; B7 | Code read + grep | W |
| D7 | No stale destination state: navigation paths are value-typed (a `Hashable` route enum carrying ids only, never live model objects), and a destination whose entity is gone (e.g. after logout) is popped rather than rendered against nil data. | **[NEW-P5]** (SwiftUI `NavigationPath` idiom; Android's analogue is `Destinations.kt`'s id-only serializable routes) | Code read + a logout-while-deep-in-a-stack live check | W→M (MC-3) |
| D8 | Purchase Success back-navigation lands on My Learning, not back inside Checkout. | `NAVIGATION_SPEC.md § 6` | XCUITest + live | M (MC-3) |
| D9 | The interactive edge-swipe-back gesture works on every pushed screen in both locales, or its RTL deviation after a runtime language switch is explicitly disclosed (see H5). | `LOCALIZATION.md § 2` (in RTL the gesture originates from the trailing edge) | Live in both locales | M (MC-3) |

## E. Backend

| ID | Criterion | Source | Evidence | Host |
|---|---|---|---|---|
| E1 | Every screen runs against the **real local Ktor backend** with the real seeded MongoDB. No fixture/fake data path exists in the shipped app target (test doubles live only in test targets). | `PHASE_HANDOFF.md` P4 § 5 discipline | Grep for fixtures in the app target; live run | W + M (MC-3) |
| E2 | The Simulator reaches the backend through the pre-existing `ApiEnvironment.iosSimulator()` (`http://localhost:8080`), not a new preset and not a hardcoded URL. | `mobile/shared/README.md` (the 4 environment targets) | Code read; live 200s | W→M (MC-2) |
| E3 | Demo checkout stays simulated end to end: no payment SDK, no card input, no gateway, no credentials, and no payment vocabulary anywhere in the iOS diff. | `DEMO_PAYMENT_FLOW.md`; ADR-012; the Phase 3 Task 8 grep precedent | A repeatable greppable check over `mobile/iosApp/` returning zero hits | W |
| E4 | Zero direct network calls from Swift: no `URLSession`, no third-party HTTP client, no hardcoded URL beyond the `ApiEnvironment` selection. Remote images load via `AsyncImage` from a URL produced by `sdk.media.resolveThumbnailUrl`. | ADR-002; `INTEGRATION_CONTRACT.md § 12` item 10 | Grep; code read | W |
| E5 | No backend or database change of any kind in Phase 5. | Standing rule since Phase 3 | `git log --name-only` shows zero `backend/` paths | W |
| E6 | The lesson-video stream URL is used as-is with no `Authorization` header attached by the player (its token query parameter is the auth), and is refreshed near its 5-minute expiry via `sdk.media.refreshPlaybackUrl`. | `INTEGRATION_CONTRACT.md § 12` item 10; `LessonPlaybackController` kdoc | Code read + a live play session crossing the 5-minute boundary | W→M (MC-3) |

## F. KMP

| ID | Criterion | Source | Evidence | Host |
|---|---|---|---|---|
| F1 | All **10** façade domains are genuinely exercised by shipped iOS code: `auth`, `user`, `catalog`, `enrollment`, `progress`, `quiz`, `certificates`, `learningPaths`, `media`, `aiTutor`. | `MentoraSdk.kt`; `MASTER_IMPLEMENTATION_PLAN.md` Phase 5 | A per-façade table in the Phase 5 handoff mapping each façade to at least one real call site | W |
| F2 | Domain models are consumed as `shared` types (`Course`, `Lesson`, `Enrollment`, `CourseProgress`, `Quiz`, `Certificate`, `LearningPath`, `AiConversation`, `PlaybackSource`, ...). A Swift view-state struct is a presentation projection only, never a parallel re-declaration that becomes a second source of truth. | ADR-002 shared-vs-not table | Code read of every `Features/*Model.swift` | W |
| F3 | The existing `iosMain` actuals are **wired in and used, not redesigned**: `IosPreferenceStore` (`NSUserDefaults`), the Darwin engine and `platformModule()` are used exactly as Phase 3 left them, and `IosTokenStorage` keeps its Keychain (`kSecClassGenericPassword`) design. **Two categories of fix are permitted, and only these two.** An earlier draft allowed only the first, which was too narrow — it had no way to describe a defect found by *reading* the code, which is exactly what happened to `IosTokenStorage`: **(1) found on a real Mac, after compiling or running it** — fixed there, with its own `DECISIONS_LOG.md` entry; **(2) found by code review before Mac access** — may be **authored on Windows as an unverified Kotlin change**, is tagged W-auth / C-verify (with M for any live-behavior half), is never reported as PASS on Windows evidence, carries its own `DECISIONS_LOG.md` entry, and is verified for real by **CI-1** (its `iosTest` unit tests) and, for any live-behavior half (as B10 has for this one), **MC-2**. **Exactly one Category-2 fix is sanctioned: T1b's `IosTokenStorage` Keychain error handling** (`PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1`, criterion **B10**) — required work, not an optional hardening. `commonMain` is still untouched (A6). | `PHASE_HANDOFF.md` P3 §§ 6, 9; `PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1` | `git diff mobile/shared/src/iosMain` shows **exactly** the T1b fix (`auth/IosTokenStorage.kt` + the new `auth/Keychain.kt` seam) and nothing else — `IosPreferenceStore.kt`, `HttpClientEngineFactory.ios.kt` and `PlatformModule.ios.kt` byte-identical | W (diff) / **C (CI-1 proves it compiles and its failure-injection tests pass)** |
| F4 | The `shared` regression guard holds at every checkpoint after the iOS wiring change: `:shared:testDebugUnitTest` **249/249** and `:shared:assembleDebug` clean, on this Windows host. | `PHASE_4_ANDROID_PLAN.md § 5` precedent | Command output attached to each task commit | W |
| F5 | On a Mac: `:shared:compileKotlinIosSimulatorArm64`, `:shared:linkDebugFrameworkIosSimulatorArm64`, SKIE actually applying, and the XCFramework assembling all succeed — the first genuine verification that D69's disclosed limitation is closed. | D69 / limitation B1 | Full command output captured into the handoff — **since D100 this is a CI run log, re-proved on every qualifying push, not a one-off manual capture** | **C (CI-1)** |
| F6 | No iOS-only reimplementation of shared behavior (the KMP-side restatement of A4): specifically no Swift copy of resume-target resolution, lesson auto-advance, quiz answer-completeness validation, locale precedence, or playback-heartbeat throttling. | `PHASE_3_KMP_PLAN.md` Tasks 9/10; D76 | Reviewer diff read + grep | W |
| F7 | `ReportPlaybackPositionUseCase` is invoked through the single retained `MentoraSdk` instance so its per-instance throttle actually works. | D76 | Code read; live network-log check that heartbeats are throttled rather than per-tick | W→M (MC-3) |
| F8 | SKIE-generated ergonomics are actually used rather than worked around: suspend functions consumed as Swift `async`, `Flow`/`StateFlow` as `AsyncSequence`, sealed types as exhaustive Swift switches. Any place where SKIE output proved unusable is documented with the workaround and the reason. | `KMP_ARCHITECTURE.md § 4`; `PHASE_3_KMP_PLAN.md` Task 15 | Code read + a written note in the handoff for each workaround; the CI `kmp-swift-interface` artifact is the primary evidence of what SKIE actually generated | W (code read) + **C (it compiles against the real generated API)** + M (MC-2 for anything only a running app shows) |

## G. Design

| ID | Criterion | Source | Evidence | Host |
|---|---|---|---|---|
| G1 | Every color a view renders resolves from a generated `mentora*` Color Set in the asset catalog (Any + Dark appearance), per `platform-contract.json#/ios/colorMapping`. **No raw SwiftUI/UIKit system color and no hex literal anywhere in view code.** | `platform-contract.json#/ios`; the D53 visual-parity rule | Grep for `Color(red:`, `Color(.s`, `UIColor.system`, `Color.gray` under `mobile/iosApp/` returning zero hits outside the generated file | W |
| G2 | `MentoraTokens.swift` + `MentoraColors.xcassets` are **generated** by `tools/token-pipeline/generate.js` from `design-system/design-tokens.json` + `themes/*.json`, carry a GENERATED — DO NOT EDIT header, and are never hand-edited — mirroring the Android `MentoraTokens.kt` precedent exactly. | ADR-011; `REPOSITORY_STRUCTURE.md § 5`; `generate.js`'s own note | Re-running the generator yields a zero-diff tree; header present; web + Android outputs byte-identical | W |
| G3 | Typography goes through a single **`.mentoraFont(_:)` view modifier** over a 12-case `MentoraTextStyle` enum (`.displayLarge`, `.displayMedium`, `.h1`-`.h4`, `.bodyLarge/Medium/Small`, `.labelLarge/Medium`, `.caption`), composing `@ScaledMetric(relativeTo:)` for size against the contract's anchors, plus weight, `.tracking()` and `.lineSpacing()` from `design-tokens.json#/typography/scale`, plus the Arabic tracking-zero / +10% body line-height overrides. **The line-height computation is fixed by `PHASE_5_IOS_SYSTEM_DESIGN.md § 15.1`'s ratio formula and must not be re-derived at implementation time:** `lineSpacing = max(0, scaledSize * (ratio - 1.2))`, where `ratio = token.lineHeight / token.fontSize` (times 1.10 for Arabic body steps) and `scaledSize` is the single `@ScaledMetric` value — so the target line height and the font's natural line height scale together and the result can never be negative. The earlier `lineHeight - size` form is **withdrawn**: it subtracted an unscaled token value from a scaled one, reached zero and then went negative at accessibility sizes, and left the Arabic +10% rule defined against a broken base. **A `Font.mentora*` value-typed extension is explicitly NOT the expected shape** — it cannot scale with Dynamic Type without a bundled font (H8 forbids bundling one) and cannot carry tracking or line height at all; `UIFontMetrics` is not a substitute because it ignores an injected `.dynamicTypeSize(...)`. See `PHASE_5_IOS_SYSTEM_DESIGN.md § 15.1`, which records this as a **correction to `platform-contract.json`'s prose**, with `platform-mapping.md § 2`'s `.mentoraFont(.h1)` modifier convention (LOCKED) as the authority. No raw `.system(size:)` and no `UIFontMetrics` in view code. | `platform-mapping.md § 2` (authoritative); `platform-contract.json#/ios/typographyMapping` (`relativeTo:` anchors only) | Grep; file read; and a Dynamic Type test that goes beyond "the font size changed": a **multiline** text sample rendered at **`.large` and `.accessibility5`**, in **both `en` and `ar`**, asserting (a) the leading ratio is preserved at both sizes, (b) `lineSpacing >= 0` for all 12 styles at both sizes in both locales, (c) tracking is never more negative than its token value (it is not scaled) and is `0` under `ar`, and (d) no line overlap — rendered height grows with the size. A **geometry-reading** XCTest (frame/line-count assertions, not pixels) — **runs in CI since D100** (it is a geometry assertion in a unit-test target, not a human visual check). **Pixel-snapshot-style testing is explicitly NOT CI-safe** (device/OS-version-dependent, and CI's simulator OS version is not guaranteed to match a future Mac session's) and stays a manual (M) verification method if ever added; the visual confirmation itself stays MC-3. | W (authored) / **C (run, geometry only)** / M (visual, MC-3) |
| G4 | Shapes/radii follow `platform-contract.json#/ios/shapeMapping` and `design-tokens.json#/shape/radius`: **8 / 12 / 16 / 24 pt** rounded rectangles — `radius.xlarge` = **24** (the dialog and bottom-sheet step) is not optional and was missing from an earlier draft of this criterion — `UnevenRoundedRectangle` **top-corners-only at 24 pt** for sheets/dialogs, `Capsule()`/`Circle()` for `radius.full`. | same | Code read | W |
| G5 | Elevation uses one `.mentoraElevation(_:)` modifier fed by `design-tokens.json`'s `elevation.*.ios` radius/y/opacity triples, paired with a 1 pt border stroke (borders over shadow). | `platform-contract.json#/ios/elevationMapping`; `design-tokens.json § elevation` | Generated values match the token file; code read | W |
| G6 | Light **and** Dark are both first-class: every screen verified in both, and dark is the same semantic structure re-resolved, never an iOS-invented palette. | D53; `platform-contract.json` explicitlyNotAllowed list | Live pass over all 18 screens in both appearances | M (MC-3/MC-4) |
| G7 | Touch targets are at least **44 pt** (`design-tokens.json#/touchTarget/ios_pt` — iOS's number, not Android's 48 dp). | `ACCESSIBILITY.md § 4` | Code read + Accessibility Inspector pass | W→M (MC-3) |
| G8 | The 5-motif course-artwork system, the category-chip-on-scrim treatment, and card identity match Web/Android — no iOS-specific illustration style. | `design-to-code/shared/artwork.json`; D53 | Side-by-side screenshot comparison against Android | M (MC-3) |
| G9 | The 8 `design-to-code/screens/mobile-*.json` exact-showcase specs are the composition reference for their screens (Home, Explore, Course Details, My Learning, AI Tutor, Course Player, Demo Checkout, Purchase Success); remaining screens use `ux/SCREEN_UX_SPECS.md` / `MOBILE_UX.md` and are honestly tagged ux-only, exactly as Phase 4 tagged them. | `PHASE_HANDOFF.md` P4 § 8; `PHASE_4_ANDROID_PLAN.md § 4` | Per-screen reference-type table in the handoff | W |
| G10 | The icon set is the same 42-glyph silhouette set Web and Android ship, delivered as asset-catalog vector assets — **not** SF Symbols (see § 3 item 3 for why substituting Apple's icon language would violate the visual-parity rule). | `design-tokens.json#/icon/family`; `platform-mapping.md § 7`; `platform-contract.json#/visualParityRule` | Asset-catalog inventory (42 entries) + visual comparison | W (inventory) / M (render) |

## H. Localization

**Decision (to be logged with D96): iOS uses a String Catalog (`Localizable.xcstrings`, en + ar) plus a
root-level `\.locale` / `\.layoutDirection` environment override driven by `sdk.user.observeLocale()`.**
This is the iOS-idiomatic *equivalent* of Android's D93 `LocalizedContent` `ContextWrapper` — not a port
of it — and explicitly **not** the `AppleLanguages` + relaunch mechanism, because
`SCREEN_INVENTORY.md § 17` locks immediate application with no restart. Full rationale, the
non-view-tree string-resolution rule, and the fallback if SwiftUI's environment-locale lookup proves
insufficient are in `PHASE_5_IOS_SYSTEM_DESIGN.md § 12`.

| ID | Criterion | Source | Evidence | Host |
|---|---|---|---|---|
| H1 | Every user-facing string comes from `Localizable.xcstrings`; **zero hardcoded user-facing literals**, including default parameter values of reusable components (the exact retryLabel-class gap Phase 4 had to sweep in Task 19, D94). | `LOCALIZATION_ARCHITECTURE.md`; D94 | A repo test scanning `Features/` + `Components/` for string literals reaching a `Text`/accessibility label; reviewer read | W |
| H2 | EN and AR key sets are in exact parity **including format-specifier parity per key** — the direct port of Android's `StringsParityTest`, which exists because a dropped specifier passes key-parity and crashes at runtime (D93 finding 8). Android's current set is 279 EN / 278 AR (`app_name` is intentionally EN-only). **Plus the iOS-specific requirement that is not mere "parity": every ported specifier is converted `%N$s` → `%N$@`.** Android deliberately uses `%N$s` for every substitution including numerals (`strings.xml`'s own comment: numeral placeholders are `%1$s`/`Int.toString()`, never `%1$d`, per `LOCALIZATION.md § 8`); on iOS `%s` means a **C string**, so passing a Swift `String` to it yields garbage or a crash. Verified at plan time: **38 keys carry placeholders, 54 specifier occurrences per locale (38 `%1$s` + 14 `%2$s` + 2 `%3$s`), identical in EN and AR, zero `%N$d` in any string value, and zero `<plurals>` in the Android set** — so there is no plural-rule porting to do at all. The catalog must therefore contain **only iOS-valid specifiers**. | D93/D94; `LOCALIZATION.md § 8` | (i) `tools/ios-checks/catalog-parity.js` — a standalone zero-dependency **Node** script (same precedent as `generate.js`) asserting key parity, per-key specifier parity, and that no `%N$s`/`%N$d`/`%@`-without-index form survives; this is the part that genuinely runs on Windows, because `.xcstrings` is JSON. (ii) An XCTest wrapper asserting the same invariants in-target — **runs in CI since D100**, not only on a Mac. | W (the Node script) + **C (the XCTest)** |
| H3 | Switching language in Settings applies **immediately**: strings, layout direction, and locale-aware formatting all flip with no app restart and no navigation reset. | `SCREEN_INVENTORY.md § 17` (locked MVP); `PRODUCT_SPEC.md § 16` | Live run in both directions | M (MC-3) |
| H4 | Strings resolved **outside** the SwiftUI view tree (view-model error copy, AI-Tutor quick-action prompt text) resolve against the *active app locale*, not the device locale — the iOS analogue of the D93 regression that shipped an Arabic chip label whose prompt text was still English. | D93 | A unit test asserting resolution follows the injected locale; live check of the prompt actually sent | W→M (MC-3) |
| H5 | RTL: the UI mirrors under `ar` via layout-direction propagation, using leading/trailing (never left/right) throughout. The hard cases behave: the **video scrubber stays LTR**, numerals stay **Western 0-9** in both locales, directional icons mirror and non-directional icons do not. **The mirroring assertion is against Android, not against `design-tokens.json`'s literal lists**, because the two do not correspond: `design-tokens.json#/icon/directional` names 15 `mirrorInRtl` + 29 `neverMirror` glyphs in Material-Symbols snake_case (`chevron_left`, `trending_flat`, ...), while the shipped set is the 42 hand-drawn camelCase glyphs (`dashboard`, `myLearning`, `courseAnalytics`, ...), and the two barely overlap. The test asserts: (1) the iOS icon-name set **equals Android's `MentoraIconName` 42 entries**; (2) the iOS mirror set **equals Android's exact `autoMirror` set — `arrowForward` and `arrowBack`, and nothing else** (`mobile/androidApp/.../ui/components/MentoraIcons.kt`). `design-tokens.json#/icon/directional` is the **semantic rule** that Android's two-icon set already satisfies via name translation, **not** a second literal inventory to reconcile glyph-by-glyph. | `LOCALIZATION.md §§ 1, 2, 5, 8`; Android's `MentoraIcons.kt` | Live pass in `ar`; a name-set + mirror-set equality test against Android's two lists | W (lists) + M (visual) |
| H6 | Locale-aware formatting (dates, counts, durations, prices) uses platform formatters bound to the active locale with **Latin numerals forced** (`ar-u-nu-latn`), never hand-built strings. | `LOCALIZATION.md §§ 7, 8` | Formatter unit tests asserting Eastern-Arabic digits never appear | W→M |
| H7 | First-run locale seeding calls `shared`'s `resolveInitialLocale(systemLocales:)` exactly once per install behind an app-owned flag, and never clobbers a locale an already-authenticated user chose (the F2/F3 fixes encoded in Android's `LocaleController`). | G2 (`PHASE_4_ANDROID_PLAN.md § 6`); D93 | Unit test + fresh-install live check | W→M (MC-2) |
| H8 | Arabic typography: **iOS does not inherit Android's missing-Arabic-font limitation.** `LOCALIZATION.md § 4` records that Apple substitutes **SF Arabic automatically** for Arabic runs when system font APIs are used, so nothing is bundled and no placeholder is needed. Letter-spacing is zeroed for Arabic runs and body line-height gets the +10% bump, per the same section. | `LOCALIZATION.md § 4`; `design-tokens.json#/typography/fontFamily/iosArabic` | Live visual check in `ar`; code read of the typography layer | W→M (MC-3) |

## I. Accessibility / Resilience

| ID | Criterion | Source | Evidence | Host |
|---|---|---|---|---|
| I1 | Dynamic Type is supported across the full range **including the AX sizes**; no screen clips, overlaps, or loses an action at the largest accessibility size, and no text container has a fixed height. | `ACCESSIBILITY.md §§ 10, 11`; `CONTENT_RESILIENCE.md §§ 1, 7, 8` | Dynamic Type stress pass across all 18 screens, with screenshots of the worst cases; **plus the G3 typography check** — a multiline sample at `.large` and `.accessibility5` in `en` **and** `ar` asserting non-negative line spacing, preserved leading ratio and no overlap (the evidence the withdrawn `lineHeight - size` formula would have failed) | M (MC-3/MC-4) |
| I2 | VoiceOver: every control has a meaningful label; decorative images are hidden; icon-only buttons are labelled; composite rows (course cards, certificate cards, avatar+name) are combined into one sensible element rather than announced piecemeal (the iOS form of the Android `Avatar` finding, D93 finding 3). | `ACCESSIBILITY.md §§ 5, 15` (Accessibility Inspector + VoiceOver is the named iOS bar) | VoiceOver smoke pass per screen; Accessibility Inspector audit with zero criticals | M (MC-3/MC-4) |
| I3 | Loading states occupy the same footprint as their resolved content (a card skeleton is card-shaped), so nothing reflows on load. | `CONTENT_RESILIENCE.md § 3` | Live observation; component previews | M (MC-3) |
| I4 | Error handling matches the locked rules: a view-blocking failure renders `ErrorState` with friendly localized copy derived from `ApiErrorCode` plus a **real retry**; an item-level failure is inline/toast, never a full-screen replacement; a failed background save (progress heartbeat) is non-blocking. | `CONTENT_RESILIENCE.md § 4`; `ApiResult` kdoc | XCTest over the copy mapper + live failure injection (stop the backend mid-session). **Retagged W→M (honesty fix):** an earlier draft tagged the unit-test half **W**, but those are XCTests and no Swift compiles on this host (J1). The only genuinely-Windows artifact here is the catalog check that every `ApiErrorCode` maps to an existing key, run by `tools/ios-checks/catalog-parity.js`. **D100:** the copy-mapper XCTest half now genuinely runs in CI; the live failure injection does not. | W (catalog check) + **C (the mapper XCTest)** + M (MC-3 live) |
| I5 | Empty states follow the fixed icon-title-description-action structure with per-context copy. | `CONTENT_RESILIENCE.md § 5` | Live: empty My Learning, empty search, no certificates | M (MC-3) |
| I6 | Offline/network-loss is detected and surfaced with the specified copy, implemented directly (e.g. `NWPathMonitor`) and **not** by adding a local cache or SQLDelight. | `UX_STATES.md § 4`; `PHASE_4_ANDROID_PLAN.md § 7`; `PHASE_HANDOFF.md` P3 § 9 | Live airplane-mode check | M (MC-3) |
| I7 | Every scrollable surface scrolls to its end at maximum Dynamic Type, with safe-area/home-indicator insets respected and nothing hidden behind the tab bar or keyboard. | `ACCESSIBILITY.md § 11`; `MOBILE_UX.md § 14` | Live pass | M (MC-3) |
| I8 | Keyboard behavior: the AI Tutor input stays pinned above the keyboard and remains open after send; form fields scroll into view when focused. | `MOBILE_UX.md § 14` | Live | M (MC-3) |
| I9 | The reduced-motion preference is honored (transitions degrade to a cross-fade or an instant cut). | `ACCESSIBILITY.md § 9`; `design-tokens.json#/motion/reducedMotion` | Live with Reduce Motion enabled | M (MC-4) |
| I10 | Contrast: every token pairing used meets the verified ratios in `ACCESSIBILITY.md § 13` in **both** appearances — with specific attention to the scrim-over-artwork chip pairing, which was a real Phase 4 dark-theme defect (`8feacad`). | `ACCESSIBILITY.md §§ 1, 13` | Token-pairing review + Accessibility Inspector contrast check on the artwork chip specifically | W (pairing math) + M (visual) |

## J. Quality

| ID | Criterion | Source | Evidence | Host |
|---|---|---|---|---|
| J1 | **Windows-side build validation is honestly scoped.** What is genuinely achievable here: Gradle configuration plus `:shared:assembleDebug` and `:shared:testDebugUnitTest` 249/249 after the iOS wiring change; Node generator runs; asset-catalog / String-Catalog / `project.yml` structural validation; grep-based boundary checks; diff review. **Swift is never compiled here.** No task may be reported as verified on Windows-side evidence alone. **D100 amendment:** that sentence still holds for *this host*, but "nothing compiles it" no longer does — `.github/workflows/ios-ci.yml` (Task T4c) compiles Swift and Kotlin/Native on a GitHub-hosted `macos-15` runner on every qualifying push. Windows remains the authoring host; **CI is the compile authority**; a Mac with the local backend remains the acceptance authority. | Host fact (§ 0); D100 | Per-task gate output plus the CI run link | W |
| J2 | An `xcodebuild` build of the app scheme against an iOS Simulator destination succeeds with **zero new warnings introduced by Phase 5 code**. | **[NEW-P5]** | Build log — CI archives the full `xcodebuild` log as an artifact and prints the warning count on every run | **C (the build succeeds)** + **W (human read of the archived log for "zero NEW warnings", which no tool decides)** |
| J3 | Automated tests appropriate to the platform exist and pass: unit tests over the `ApiResult` bridge, every error-code-to-copy mapping, each screen model's state transitions through injected seams, formatters (including Latin-numeral forcing), catalog parity, and the RTL mirroring list. Target: a test file per `Features/*Model.swift`, mirroring Android's 241-JVM-test discipline. | `TESTING_STRATEGY.md`; Phase 4 precedent | Test count + per-model coverage table | W (authored) / **C (run — the `iosAppTests` target executes on every CI run)** |
| J4 | An XCUITest smoke suite covers the portfolio-priority journey end to end (guest, Explore, Course Details, login gate, demo checkout, Purchase Success, Course Player, Quiz, Quiz Results, Certificate) plus behaviors D2-D5 and D8 — including the exact multi-tab chain that exposed Phase 4's dead-Continue defect. | `MASTER_IMPLEMENTATION_PLAN.md` Phase 5 (M15 iOS portion); `90c8af3` | Green suite run against the real backend. **Explicitly NOT a CI criterion (D100):** the pipeline runs `-only-testing:iosAppTests` and never the UI-test target, because XCUITests need the real local Ktor backend and seeded MongoDB, which CI deliberately does not stand up. | M (MC-3/MC-4) |
| J5 | Manual live verification per screen in **en + ar** and **light + dark** against the real backend — the standing per-task discipline of every prior phase — recorded per screen, with anything unverified named as unverified (Phase 4 § 6 items 1-3 set the honesty bar). | `PHASE_HANDOFF.md` P4 § 5 | Per-screen verification table in `CURRENT_STATUS.md` | M (MC-3/MC-4) |
| J6 | `mobile/iosApp/README.md` exists and covers: macOS/Xcode prerequisites, how to build the shared XCFramework, how to generate and open the project, how to run against the local backend, the seeded demo accounts, the quality-gate commands, and a **Known limitations** section in the same style as `mobile/androidApp/README.md`. | Phase 4 Task 20 precedent | File review | W (authored) / M (verified) |
| J7 | Continuity docs updated: `CURRENT_STATUS.md` (Phase 5 task table + exact resume point), `PHASE_HANDOFF.md` (Phase 5 entry in the fixed 10-section structure), `DECISIONS_LOG.md` (D96 onward), and `INTEGRATION_CONTRACT.md` only if a genuinely new as-built wire fact is discovered (Phase 4 correctly added none). | Standing phase policy | Doc review | W |
| J8 | Clean git state: one commit per task plus continuity commits (never one giant Phase-5 commit), working tree clean at phase end, and `git log --name-only` proving the A7/A8 boundary rules held. | `PHASE_4_ANDROID_PLAN.md § 5` | `git log` / `git status` | W |
| J9 | Every Phase-4-disclosed limitation carries an explicit Phase 5 verdict (inherited unchanged / diverged / newly solved) per § 3 below — none is silently carried, and none is silently fixed on Android's behalf. | `PHASE_HANDOFF.md` P4 §§ 6, 9 | The § 3 table completed with as-built verdicts | W |
| J10 | The phase-completion report states unambiguously which criteria ended as **NOT TESTABLE (HOST)** if no Mac ever became available, rather than implying coverage that does not exist. **The report must also distinguish `C`-verified from `M`-verified**: "CI is green" is never reported as, or allowed to imply, live verification. | Methodology § 2 | Final acceptance report | W |
| J11 | **The iOS CI pipeline exists, is scoped, and is honest.** `.github/workflows/ios-ci.yml` (a) runs the full tier-`C` gate — KMP iOS compile/link/SKIE, `:shared:iosSimulatorArm64Test`, `assembleSharedDebugXCFramework`, `xcodegen generate`, `xcodebuild build`, `xcodebuild test -only-testing:iosAppTests`; (b) is triggered only by `workflow_dispatch` and path-filtered `push`/`pull_request` on `main`, never on unrelated repository changes; (c) **invokes no `:androidApp` task and never builds `web/` or `backend/`**; (d) references **no secret at all**; (e) fails loudly — no step swallows a non-zero exit; (f) publishes the generated shared-framework interface as a downloadable artifact, which is what lets Swift be authored on Windows against fact rather than guesswork. | **[NEW-P5]**, **D100** | A green run on `main`; the workflow file read against (a)-(f); `grep` showing zero `secrets.` references and zero `androidApp`/`web/`/`backend/` paths in it | W (the file) / **C (a green run)** |

---

## 3. Phase 4 disclosed limitations — Phase 5's per-item verdict

`mobile/androidApp/README.md` and `PHASE_HANDOFF.md` P4 § 6 disclose 12 items. Each carries the
verdict below, investigated against real sources rather than assumed:

| # | Phase 4 limitation | Phase 5 verdict | Why |
|---|---|---|---|
| 1 | No password-change endpoint; `avatarMediaId` is a dead field | **Inherited unchanged** | A real backend gap (D44). Profile/Settings omit both on iOS too. |
| 2 | No real certificate image asset | **Inherited unchanged** | Same token-driven placeholder document treatment (Web's D42 precedent). |
| 3 | Icon set is a hand-drawn 42-glyph placeholder, not Material Symbols Rounded | **Inherited — and SF Symbols does NOT make it moot** | The design system locks Material Symbols Rounded (**`design-tokens.json#/icon/family`**) and **`platform-mapping.md § 7`** specifies iOS delivery as "PDF or SVG assets in the asset catalog (template rendering mode for tinting)" — not SF Symbols. Those two are the correct citations for this decision; an earlier draft also cited `platform-contract.json#/visualParityRule/explicitlyNotAllowed`, **which does not actually mention icons** — that citation is withdrawn. (The visual-parity *principle* — one identity across platforms — still supports the conclusion; it just is not where the icon rule is written.) iOS ports the same 42 silhouettes. Allowed exception: glyphs Apple draws inside its own controls (the NavigationStack back chevron, a Picker menu indicator, the system keyboard) stay native, under the native-control-behavior allowance. |
| 4 | Arabic typography uses a sans-serif placeholder | **Diverged — genuinely solved on iOS** | `LOCALIZATION.md § 4`: Apple substitutes SF Arabic automatically for Arabic runs through the system font APIs. No asset, no placeholder. See H8. |
| 5 | Base URL hardcoded to the emulator alias | **Diverged in value, inherited in shape** | iOS uses the pre-existing `ApiEnvironment.iosSimulator()` (`http://localhost:8080`), which is simply correct for the Simulator. A **physical iPhone** still needs `ApiEnvironment.lan(host:)` and a manual source change; no build-variant/config mechanism is introduced (same D53 reasoning — no production backend exists). Disclose it identically in the iOS README. |
| 6 | No docked AI-Tutor panel on Course Player | **Inherited unchanged** | `RESPONSIVE_BEHAVIOR.md § 9`'s docked variant was never built for Web or Android; Phase 5 must not build it unilaterally (P4 § 9). |
| 7 | Quiz / Quiz Results not re-verified live in Android's final sweep | **Not inherited — iOS verifies them live** | That was an Android time-cost call, not a product gap; the later acceptance audit found a real Quiz-Results defect there, which is exactly why C12/D4/J4 exist. |
| 8 | The RTL/theme/font-scale sweep was a spot-check, not exhaustive | **Not inherited as an excuse** | Phase 5 targets full 18-screen coverage across en/ar and light/dark (J5); any shortfall is disclosed per screen, not generalized. |
| 9 | Some string-resource defaults verified by compilation only | **Not inherited** | H1 makes hardcoded or unexercised string defaults a test-enforced criterion from the start rather than a late sweep. |
| 10 | The My-Learning progress join and the followed-paths join live in `androidApp`, not `shared` (G3/G5) | **Inherited as accepted duplication — flagged for a user decision** | Phase 5 implements the same join in Swift. `PHASE_4_ANDROID_PLAN.md § 6` G3 explicitly named Phase 5 planning as the moment to decide whether to promote it into `shared`. Recommendation: **do not promote it now** — and the decisive reason is stronger than cost: **promotion cannot actually remove the duplicate.** Deleting Android's copy requires editing `mobile/androidApp/`, which **A7 forbids for the entire phase**; leaving Android's copy in place while adding a shared use case yields a **third** implementation of the same join instead of removing one. Reopening `shared`, re-baselining its 249-test suite and re-verifying Android is merely the additional cost on top of that. Record the duplication instead; iOS's copy must match Android's shape exactly (eager full drain, fail-fast on both legs, sequential — see C9). Open question § 4.2. |
| 11 | Narrow wrong-tab-momentarily-associated guest-gate edge case | **Not applicable as-is** | Navigation-Compose-specific. iOS must satisfy D4 on its own terms; the `90c8af3` defect is the concrete regression to mirror in XCUITest. |
| 12 | Possible over-dimmed first frame on dialog open | **Not applicable** | Compose-animation-specific. |

---

## 4. Open questions that need a human call before or inside Phase 5

1. **Is a macOS host with Xcode actually available? — ANSWERED IN PART BY D100.** A GitHub Actions
   `macos-15` runner is now the standing compile/test host (`.github/workflows/ios-ci.yml`, Task
   T4c), so the phase can be **built and unit-tested** without owning a Mac. It still cannot be
   **accepted** without one: CI has no eyes, no VoiceOver, no simulator interaction and no backend,
   so every live/visual/accessibility criterion (most of B, C, D, G, H, I, plus J4/J5) still ends as
   **NOT TESTABLE (HOST)** until a human runs MC-2/MC-3/MC-4 on a real Mac against the real local
   backend. The Windows-only boundary below is superseded accordingly: **T4c is now the last
   authored-on-Windows-only task, and T4b onward is authored on Windows and compiled by CI.** The
   original wording follows.

   1a. *(superseded)* **Is a macOS host with Xcode actually available?** If not, Phase 5 can be
   *authored* but not built, run, tested, or accepted — every M-tagged criterion above (the large majority) would end as
   **NOT TESTABLE (HOST)**. This is the single blocking question for the phase. **The Windows-only
   boundary is exact, and is stated identically in all three Phase 5 documents: Phase 5 executes T1,
   T1b (authored only, never PASS on Windows evidence), T2, T3 and T4a now; T4b and everything after
   it is blocked on Mac access** (`PHASE_5_IOS_IMPLEMENTATION_PLAN.md §§ 1, 2, 5`).
2. **Promote the My-Learning progress join into `shared`?** (§ 3 item 10.) Default recommendation:
   **no** — duplicate it in Swift and record the duplication. The reason is not primarily cost: within
   Phase 5, promotion **cannot remove the duplicate at all**, because removing Android's copy means
   editing `mobile/androidApp/`, which A7 forbids for the whole phase. Promoting now would therefore
   produce **three** implementations (shared + Android's untouched copy + iOS's consumer) rather than
   one. If promotion is genuinely wanted, it is a **separate, later** piece of work that owns both the
   `shared` change and the Android migration together, with its own 249-test re-baseline and Android
   re-verification — not something Phase 5 can do honestly. The iOS copy must therefore match
   Android's observable behavior exactly (C9): eager full page drain at `PageLimit = 50`, fail-fast on
   both the list call and every per-course progress call, sequential requests, roughly 2N+1 round
   trips.
3. **Deployment target.** This plan assumes **iOS 17.0** (enables the `@Observable` macro, mature
   String Catalog tooling, and current `NavigationStack` behavior). Confirm, or state a lower floor
   and accept the `ObservableObject` fallback described in `PHASE_5_IOS_SYSTEM_DESIGN.md § 3`.
4. **Xcode project generation approach.** This plan commits an **XcodeGen `project.yml`** as the
   authoritative project definition, with `iosApp.xcodeproj` generated on the Mac, because a
   `project.pbxproj` cannot be hand-authored safely or reviewed meaningfully on Windows
   (`PHASE_5_IOS_SYSTEM_DESIGN.md § 1`). Confirm the extra tool dependency is acceptable.
5. **SKIE 0.9.5 / Kotlin 2.0.21 compatibility on the real Mac.** If the pinned SKIE version does not
   support that toolchain there, bumping SKIE may force a Kotlin bump whose blast radius includes
   `:androidApp` and the 249-test `shared` baseline. MC-1 resolves this; any bump is a decision-log
   event, not a silent edit.
6. **Keychain hardening — decided, not open; one confirmation wanted.** `IosTokenStorage`'s ignored
   `OSStatus` results are a **defect**, and fixing them is now **required** work (T1b, criterion B10,
   `PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1`) rather than an optional post-MC-2 tweak: a failed refresh-token
   write can leave a mismatched token pair, and a failed logout delete can report success while the
   session survives into the next launch. The accessibility attribute is **decided**:
   `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` — the same unlock class as today's implicit default
   (`kSecAttrAccessibleWhenUnlocked`), plus no iCloud-Keychain sync and no cross-device backup restore,
   which suits a foreground-only session token. The earlier `kSecAttrAccessibleAfterFirstUnlock`
   suggestion is **withdrawn**: it would have *weakened* locked-device protection, and no accessibility
   value has ever purged Keychain items on app deletion. **The one thing that still needs a human
   call:** Phase 5 does **not** implement first-launch-after-install purging, so **a fresh install on a
   device that previously had Mentora may resume the stale session** — accepted here as local-demo
   scope (**ADR-012**) and disclosed in the iOS README's Known limitations. Say so if you want the
   `UserDefaults` install-marker purge instead; that is an app-side (`AppEnvironment`) change, not
   another `iosMain` change.
