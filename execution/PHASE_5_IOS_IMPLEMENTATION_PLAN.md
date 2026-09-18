# Phase 5 — iOS: Implementation Plan

**Status:** Authoritative task plan for Phase 5, derived by the `architect` subagent on 2026-09-18
from `execution/PHASE_5_IOS_SYSTEM_DESIGN.md` (the architecture), `execution/PHASE_5_ACCEPTANCE_CRITERIA.md`
(the acceptance bar, categories A-J), the Phase 3/Phase 4 handoff entries, and the real source of
`mobile/shared` + `mobile/androidApp`. To be logged as `DECISIONS_LOG.md` **D96**. Read this file
before resuming any Phase 5 task — do not re-derive acceptance criteria from memory. This is an
execution planning document, not a locked doc; amend it with a `DECISIONS_LOG.md` entry.

Per-task status is tracked in `execution/CURRENT_STATUS.md`'s "PHASE 5 — Task Breakdown" table; this
file is the acceptance detail that table points to.

---

## 0. Repo state at plan time

Identical to `PHASE_5_ACCEPTANCE_CRITERIA.md § 0` — HEAD `8feacad`, branch `main` clean apart from the
pre-existing `mobile/gradle.properties` local change (left untouched), P1-P4 COMPLETE, next decision
id **D96**, no `mobile/iosApp/` yet, `iosMain` present but unwired, **no macOS/Xcode/Simulator on this
host**.

## 1. Sequencing and host rules

1. **Dependency order is hard:** KMP `iosMain`/`iosTest` + XCFramework wiring (T1) → the
   `IosTokenStorage` Keychain correctness fix (T1b) → generated design system → project scaffolding
   → **[Mac gate: MC-1]** → app bootstrap → bridge → shell/navigation/auth → component kits →
   feature screens → QA/tests → handoff. No SwiftUI screen work starts before T4b, and **T4b itself
   does not start before MC-1**.
2. **One commit per task**, plus a `CURRENT_STATUS.md` continuity commit — never one giant Phase-5
   commit (the Phase 3/4 convention).
3. **Standing regression gate at every checkpoint:** `:shared:testDebugUnitTest` **249/249** and
   `:shared:assembleDebug` clean. T1b's new Kotlin tests live in the **`iosTest`** source set, which
   does not run on this host and does not change the 249 baseline. `:androidApp` is never touched; if
   any Android gate is run it must still be 241/241 unit.
4. **Host honesty.** Every task below carries a host tag: **W** (completable + verifiable on this
   Windows host), **W-auth / M-verify** (authorable here, only real on a Mac), **M** (Mac-only).
5. **The Windows/Mac boundary — stated once, and stated the same way everywhere:**

   > **Phase 5 executes T1, T1b, T2, T3 and T4a now. T4b and everything after it is blocked on Mac
   > access.**

   T1, T2, T3 and T4a produce real, verifiable Windows output (Gradle green, generator idempotent,
   structural checks green, a reviewable `project.yml`). **T1b is the one authored-but-unverified
   exception, and it is deliberately not a counter-example to the rule:** it is Kotlin, not Swift; it
   is written against `platform.Security` APIs that are fully documented and already used by the file
   it edits; nothing in it depends on a generated interface; and it is a required correctness fix for
   a defect found by code review (`PHASE_5_ACCEPTANCE_CRITERIA.md` F3 Category 2). It is recorded as
   PARTIAL until MC-1 and is never reported as PASS on Windows evidence. **T4b is different in kind:**
   its correctness depends on SKIE-generated Swift symbol names that are literally unknown until MC-1,
   so it is tagged **M**, depends on MC-1, and is not authored blind. An earlier draft tagged T4b
   "W-auth / M-verify" while simultaneously requiring MC-1 first; that contradiction is resolved here
   in favor of **M**, which makes **the end of T4a the honest pause boundary** (risk § 5.2).
6. Tasks are sized to be independently committable and reviewable, like Phase 4's 20.

## 2. Task summary (dependency-ordered; T1 gains a sibling T1b and T4 splits into T4a/T4b = 25 executable units)

**Windows-executable right now: T1, T1b (authored only), T2, T3, T4a. Everything from T4b onward is
Mac-gated** (§ 1 rule 5).

| # | Task | Host | Depends on |
|---|---|---|---|
| T1 | `:shared` iOS enablement: `iosMain` + `iosTest` wiring, XCFramework task, gitignore | **W** (+ MC-1 to prove) | — |
| T1b | **`IosTokenStorage` Keychain correctness fix** — `OSStatus` checking, single-item token pair, explicit accessibility attribute, failure publication, injectable test seam (`PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1`; criterion B10; F3 Category 2). **Required, not optional.** | W-auth (**authored, unverified**) / **M-verify (MC-1 tests, MC-2 live)** | T1 |
| T2 | Token pipeline iOS target: `MentoraTokens.swift` + `MentoraColors.xcassets` | **W** | — |
| T3 | Icon set: `MentoraIcons.xcassets` (42 glyphs) + mirroring data | **W** (render at MC-2) | T2 |
| T4a | Project **scaffolding**: `project.yml`, `Packages/MentoraShared/Package.swift`, `scripts/build-shared-xcframework.sh`, `Info.plist` ATS config, `.gitignore` additions | **W** — **the last Windows-executable task; the pause boundary if no Mac appears** | T1, T2 |
| — | **──────── MAC GATE: MC-1. Nothing below this line is executable on the Windows host. ────────** | — | — |
| T4b | Swift **app bootstrap**: `MentoraApp.swift`, `AppEnvironment`, `SessionController` (incl. the B10 Keychain-failure subscription), `LocaleController`, `ThemeController` | **M** | T4a, **MC-1** |
| T5 | `SharedBridge`: `MentoraClient`, `ApiResult` unwrapping, Flow adapters, `MentoraError` | **M** | T4b |
| T6 | Design-system runtime: typography **modifier** (Dynamic Type + § 15.1's line-height ratio formula + Arabic rules), shapes, elevation, theme root | **M** | T2, T4b |
| T7 | Localization foundation: `Localizable.xcstrings` (en+ar, 279 keys, `%N$s`→`%N$@` converted), locale/direction environment, `MentoraStrings`, formatters, Node parity+specifier lint | **M** (the `tools/ios-checks/catalog-parity.js` half also runs on Windows, but the task sits after the MC-1 gate) | T4b |
| T8 | Component Kit A (atoms) + previews | **M** | T6, T7 |
| T9 | Navigation shell: TabView + per-tab stacks, `Route`, `TabRouter`, auth gate, tab-bar hiding | **M** | T4b, T8 |
| T10 | Auth screens: Login, Register | **M** | T5, T9 |
| T11 | Component Kit B (cards, state patterns, sheets, artwork) | **M** | T8 |
| T12 | Explore + Learning Paths segment | **M** | T11, T9 |
| T13 | Course Details | **M** | T12 |
| T14 | Demo Checkout + Purchase Success | **M** | T13 |
| T15 | Home + My Learning (+ Certificates entry, progress join) | **M** | T11, T14 |
| T16 | `AVPlayerLessonPlayer` + Course Player + Curriculum sheet (highest risk) | **M** | T5, T15 |
| T17 | Quiz + Quiz Results | **M** | T16 |
| T18 | Certificates List + Certificate Detail | **M** | T15 |
| T19 | Learning Path Details (follow/unfollow) | **M** | T12 |
| T20 | AI Tutor (streaming chat, quick actions) | **M** | T5, T11 |
| T21 | Profile + Settings (language switch, theme, logout incl. Keychain-failure surfacing) | **M** | T7, T9 |
| T22 | QA sweep: localization/RTL/Dynamic Type/VoiceOver/dark + XCUITest suite | **M (MC-3)** | T10-T21 |
| T23 | Live simulator verification, `mobile/iosApp/README.md`, Phase 5 handoff + acceptance audit | **M (MC-4)** + W (docs) | T22 |

## 3. Mac checkpoints

| ID | After | Must confirm |
|---|---|---|
| **MC-1** | T1, T1b | SKIE 0.9.5 resolves and applies under Kotlin 2.0.21 on macOS; `:shared:compileKotlinIosSimulatorArm64`; `:shared:linkDebugFrameworkIosSimulatorArm64`; the generated Swift API actually exposes `async` methods, Swift enums for sealed types, and `AsyncSequence` for flows; `:shared:assembleSharedDebugXCFramework` (umbrella: `:shared:assembleXCFramework`) produces `shared.xcframework` — **note there is no `assembleSharedXCFramework`; that name was wrong in an earlier draft**; and the non-exported Koin `Module` type bridges usably into Swift for `platformModule()` → `MentoraSdk.create(...)` (fallback: `export(libs.koin.core)`, see `PHASE_5_IOS_SYSTEM_DESIGN.md § 6`). **Plus T1b:** `iosMain` compiles at all (it never has), and `:shared:iosSimulatorArm64Test` runs green including the `IosTokenStorage` failure-injection tests — this is what converts T1b from authored to verified (F3 Category 2). Also confirm the assumption `PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1` K3 rests on: a non-`@Throws` Kotlin exception crossing into Swift terminates the process. **MC-1 must be captured before T4b starts** — the generated Swift interface is T4b's and T5's reference. Closes D69's disclosed limitation. |
| **MC-2** | T4b (and T3's rendering) | `xcodegen generate` produces a buildable project; the app builds and launches on a Simulator; it reaches the local backend over `localhost:8080` (ATS exception in place); `MentoraSdk.create` succeeds; `restoreSession()` runs once; **Keychain round-trip survives app termination**; **and the Keychain failure path behaves — a logout whose delete fails does not present as a completed logout, and a published `KeychainFailure` reaches the UI (B10). A single successful round trip is explicitly not sufficient evidence**; the 42 icons and all color sets render in both appearances; SwiftUI `Text` honors the injected `\.locale`. |
| **MC-3** | T22 | All 18 screens live against the real backend, en+ar, light+dark, default and largest AX Dynamic Type; VoiceOver + Accessibility Inspector passes; XCUITest suite green; playback past the 5-minute URL TTL; offline behavior. |
| **MC-4** | T23 | Independent acceptance audit per `PHASE_5_ACCEPTANCE_CRITERIA.md § 2` — re-walk the real chains rather than trusting the suite, exactly as Phase 4's `90c8af3` audit did. |

---

## 4. Task detail

Each task states: **Scope · Files · Criteria · Depends on · Approach · Tests · Manual verification ·
Completion gate · Host.**

### T1 — `:shared` iOS enablement (`iosMain`/`iosTest` wiring, XCFramework, gitignore)

- **Scope.** Make `:shared` able to produce a consumable iOS framework, and give T1b's tests a source
  set to live in, without breaking the Windows build. No Swift, no app, **no Kotlin source change** —
  the `IosTokenStorage` fix is deliberately T1b so that this task's diff stays build-script-only and
  its completion gate below stays honest.
- **Files.** `mobile/shared/build.gradle.kts` (source-set deps, an `iosTest` source set, XCFramework
  task), `mobile/.gitignore` (XCFramework output, DerivedData), `mobile/shared/README.md` (iOS section
  updated from not-wired to wired-and-verified-at-MC-1).
- **Criteria.** F3, F4, F5, A6 (no `commonMain` change).
- **Depends on.** Nothing.
- **Approach.** Add `sourceSets.findByName("iosMain")?.dependencies { implementation(libs.ktor.client.darwin);
  implementation(libs.multiplatform.settings) }` — the **null-safe** form, because `val iosMain by
  getting {}` is already known to fail configuration on this host (two separate Phase 3 findings). Add
  the same null-safe treatment for **`iosTest`** with `implementation(kotlin("test"))`, so T1b's
  failure-injection tests compile on a Mac; like `iosMain`, that source set does not materialize here,
  which is exactly why T1b is MC-1-verified. Add an `XCFramework("shared")` holder over both iOS
  targets. Leave the SKIE guard, the `binaries.framework` config and
  `kotlin.native.ignoreDisabledTargets` exactly as they are.
  **Task names (verified by applying this exact change on this host, not assumed):** the registered
  tasks are **`assembleXCFramework`**, **`assembleSharedDebugXCFramework`** and
  **`assembleSharedReleaseXCFramework`**. **There is no `assembleSharedXCFramework`** — an earlier
  draft of this plan used that name in four places and it is wrong in all of them. The wrapper script
  (T4a) invokes `:shared:assembleSharedDebugXCFramework`. The same run confirmed both the null-safe
  source-set form and the `XCFramework("shared")` holder configure cleanly on Windows with the Native
  targets disabled: BUILD SUCCESSFUL, `:shared:testDebugUnitTest` 249/249.
- **Tests.** `:shared:testDebugUnitTest` 249/249; `:shared:assembleDebug` clean; `./gradlew :shared:tasks --all`
  lists `assembleXCFramework` / `assembleSharedDebugXCFramework` / `assembleSharedReleaseXCFramework`;
  configuration produces no new warnings.
- **Manual verification.** Diff review confirming `commonMain`/`androidMain`/`iosMain` **sources** are
  untouched.
- **Completion gate.** Windows gates green and the diff is build-script-only. **MC-1 then proves it for
  real**; until MC-1 this task is PARTIAL by definition and is recorded as such.
- **Host.** W (+ MC-1).

### T1b — `IosTokenStorage` Keychain error handling (required correctness fix)

- **Scope.** Fix the defect found by **code review** in the shipped Phase 3 `IosTokenStorage`: every
  Keychain `OSStatus` is discarded, so a failed refresh-token write can leave a mismatched token pair
  and a failed logout delete can report success while the session survives into the next launch. Full
  design, options and reasoning live in `PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1` and are not re-derived
  here. **This is required work, not a deferred hardening**, and it is explicitly *not* blocked by
  F3's "only a defect found on a real Mac" rule — F3 now distinguishes Mac-found (Category 1) from
  review-found (Category 2) fixes, and this is the single sanctioned Category 2 fix.
- **Files.** `mobile/shared/src/iosMain/kotlin/com/mentora/shared/auth/IosTokenStorage.kt` (rewritten
  internals; same class name, same `TokenStorage` contract, same Keychain class and service) and a new
  sibling `.../auth/Keychain.kt` (the `internal KeychainStore` seam, `SecurityFrameworkKeychain`,
  `KeychainFailure`, and the top-level `KeychainStatus` flow); new
  `mobile/shared/src/iosTest/kotlin/com/mentora/shared/auth/{IosTokenStorageTest.kt,FakeKeychain.kt}`.
  **Nothing else in `iosMain` is touched** (`IosPreferenceStore`, `HttpClientEngineFactory.ios.kt`,
  `PlatformModule.ios.kt` unchanged) and `commonMain` is untouched (A6).
- **Criteria.** **B10**, F3 (Category 2), B3, B6, A6, F4.
- **Depends on.** T1 (the `iosMain`/`iosTest` source sets must be wired before this compiles anywhere).
- **Approach.** Exactly the six decisions in `PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1`: **K1** store the
  pair as **one** `kSecClassGenericPassword` item holding a JSON `{accessToken, refreshToken}` object,
  so a partial write is structurally impossible rather than compensated for (fallback: two items plus
  rollback, only if `@Serializable` in `iosMain` misbehaves at MC-1, and then the residual window is
  disclosed); **K2** inspect every add/update/delete/query `OSStatus`, classifying
  `errSecItemNotFound` as the expected "no session" outcome and every other status as a genuine
  failure carrying the raw status and never a token value; **K3** never throw across the Swift
  boundary (a non-`@Throws` Kotlin exception terminates the process, and adding `@Throws` would be a
  `commonMain` change A6 forbids) — instead save purges on failure, clear retries then
  tombstone-overwrites, and any surviving failure is published on `KeychainStatus.failures` for
  `SessionController` (T4b) to surface; **K4** set an explicit
  `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` in the base query; **K5** no reinstall purge (a fresh
  install may resume a stale session — accepted, ADR-012, disclosed in the README); **K6** the
  `internal KeychainStore` seam as a **defaulted** constructor parameter, so `PlatformModule.ios.kt`'s
  `IosTokenStorage()` call site is unchanged and the seam stays out of the generated Swift API.
- **Tests.** New `iosTest` tests against a `FakeKeychain` returning **injected** statuses — the point
  of the task, since a happy-path round trip proves nothing about these paths: add fails; update
  fails; delete fails and the tombstone then succeeds; delete *and* tombstone both fail (a failure is
  published); query returns `errSecItemNotFound` (→ `null`, no failure published) versus a genuine
  status (→ `null`, failure published); a malformed stored payload; and an assertion that no published
  `KeychainFailure` ever contains a token value. Windows regression unchanged:
  `:shared:testDebugUnitTest` **249/249**, `:shared:assembleDebug` clean (the new tests are `iosTest`
  and do not run here).
- **Manual verification (MC-2).** Log in → terminate → relaunch (still authenticated); log out →
  relaunch (still logged out); and a forced-failure logout does **not** present as a completed logout.
- **Completion gate.** On Windows: authored and reviewed, with `git diff mobile/shared/src/iosMain`
  limited to the two files above and 249/249 green. **PARTIAL by definition until MC-1** compiles it
  and runs `:shared:iosSimulatorArm64Test`; MC-2 closes the live half. Recorded as its own
  `DECISIONS_LOG.md` entry, which F3 requires for either fix category.
- **Host.** W-auth (**authored, unverified**) / **M-verify (MC-1 tests, MC-2 live)**.

### T2 — Token pipeline iOS output target

- **Scope.** A fifth generator target emitting the iOS design-token layer. No hand-written token value
  anywhere, ever.
- **Files.** `tools/token-pipeline/generate.js` (additive iOS section mirroring the Android section's
  structure); generated `mobile/iosApp/iosApp/Theme/MentoraTokens.swift`,
  `Theme/Color+Mentora.swift`, `Theme/MentoraColors.xcassets/**/Contents.json`;
  `design-to-code/shared/platform-contract.json` (**additive refresh of the `ios` section only** —
  sanctioned by A8, Phase 4 commit `79fc51d` is the precedent: clear the stale
  `status: "NOT IMPLEMENTED"`, correct `wouldGenerateInto`/`wouldConsume` to the real paths, and stop
  asserting the unimplementable `Font.mentora*` typography shape — see
  `PHASE_5_IOS_SYSTEM_DESIGN.md § 15.1`); `tools/ios-checks/assets-check.js` (the Windows structural
  checker named in `PHASE_5_ACCEPTANCE_CRITERIA.md § 1`).
  **Directory name is `Theme/`, not `DesignSystem/`** — `REPOSITORY_STRUCTURE.md § 4`'s locked tree
  and its § 5 generator-output sentence both name `Theme/`.
- **Criteria.** G1, G2, G3 (values), G4, G5, A8.
- **Depends on.** Nothing — the generator and its `design-system/` inputs already exist.
- **Approach.** Inputs are `design-system/design-tokens.json` + `design-system/themes/theme-{light,dark}.json`
  — **these are authoritative**, not `platform-contract.json`'s own enumeration (§ 15 of the system
  design explains why). Reuse the existing dot-path to camelCase helpers; emit one `.colorset` per
  semantic **color** dot-path — **46 of them** (`design-tokens.json#/color/semantic/light` has 51
  leaves: 46 colors + 5 state opacities, and the 5 opacities are numbers, not colorsets) — with
  `any` + `dark` appearances named `mentora<PascalCase>` per
  `platform-contract.json#/ios/colorMapping`. Emit spacing; radius **including `xlarge` = 24**;
  elevation radius/y per step, with the per-step **shadow colorsets** `mentoraShadowElevation<N>`
  described in `PHASE_5_IOS_SYSTEM_DESIGN.md § 14` (Any = light `elevationShadowBase` at the step's
  opacity, Dark = dark `elevationShadowBase` at opacity x 0.7 — the ~30% dark reduction the token
  file's own `darkModeNote` mandates, applied **in the generator** so no view branches on
  `colorScheme` and no value is hand-written); icon sizes; **typography metrics only** (size /
  lineHeight / weight / tracking — the `Font` itself is composed at runtime by T6's modifier, § 15.1);
  state opacities (**note: these differ per theme** — 0.12 light vs 0.16 dark for
  `pressed`/`focus`/`disabledContainer` — so they cannot be one flat constant set; resolve them the
  way the generator already resolves per-theme values for Android, and record it); and
  `touchTarget.ios_pt = 44`. GENERATED header on every file. Asset catalogs are plain JSON
  directories, so this is fully generatable and checkable on Windows.
- **Tests.** Run the generator twice for a zero diff; `tools/ios-checks/assets-check.js` asserting
  **46** colorsets (one per semantic color dot-path) each with `any` + `dark`, plus the shadow
  colorsets; assert `web/styles/tokens.css`, `web/src/lib/design-tokens.generated.ts`,
  `web/styles/tailwind-theme.css` and `mobile/androidApp/.../MentoraTokens.kt` are **byte-identical**
  to their pre-task state; and a diff review of the `platform-contract.json` change confirming only
  the `ios` section moved (A8).
- **Manual verification.** Spot-check three tokens end to end (a brand color, a surface color, an
  elevation step) against `design-system/themes/*.json`.
- **Completion gate.** Generator idempotent; web and Android outputs byte-identical; all 46 semantic
  colorsets plus the shadow colorsets present with both appearances; `radius.xlarge` = 24 emitted;
  `platform-contract.json`'s `ios` section no longer asserts the unimplementable typography shape.
- **Host.** W (rendering confirmed at MC-2).

### T3 — Icon set (`MentoraIcons.xcassets`) + mirroring data

- **Scope.** The same 42-glyph placeholder silhouettes Web and Android ship, delivered the way
  `platform-mapping.md § 7` specifies for iOS (asset-catalog vectors, template rendering) — **not**
  SF Symbols (`PHASE_5_ACCEPTANCE_CRITERIA.md § 3` item 3 explains why).
- **Files.** `Theme/MentoraIcons.xcassets/<name>.imageset/{<name>.svg, Contents.json}`,
  `Theme/MentoraIcon.swift`, plus the icon half of `tools/ios-checks/assets-check.js`.
- **Criteria.** G10, H5 (mirroring lists), G3/G7 (sizing).
- **Depends on.** T2.
- **Approach.** Transcribe each glyph's path data from the canonical source Android itself transcribed
  from — `web/src/components/ui/icon.tsx`, read-only, never modified — into a 24x24 viewBox SVG with
  `stroke="currentColor"`, stroke width 1.6, round caps/joins, `fill="none"` (plus the one filled
  exception, `moreVert`). `Contents.json` sets template rendering intent and preserves vector
  representation.
  **Mirroring — the two data sources do not correspond, so this is settled here rather than
  improvised.** `design-tokens.json#/icon/directional` lists 15 `mirrorInRtl` + 29 `neverMirror`
  names in **Material-Symbols snake_case** (`chevron_left`, `trending_flat`, ...), while the shipped
  set is the **42 hand-drawn camelCase glyphs** (`dashboard`, `myLearning`, `courseAnalytics`, ...);
  the two barely overlap and cannot be reconciled glyph-by-glyph. Android already resolved this, and
  iOS **ports Android's resolution rather than re-deriving it**:
  `mobile/androidApp/src/main/kotlin/com/mentora/android/ui/components/MentoraIcons.kt` mirrors
  **exactly two** icons — `arrowForward` and `arrowBack` — via `buildIcon(..., autoMirror = true)`.
  `MentoraIcon` therefore applies `.flipsForRightToLeftLayoutDirection(true)` to **those two names
  only**, encoded once as data next to the enum. `design-tokens.json#/icon/directional` is the
  **semantic rule** ("glyphs encoding a left/right direction mirror; others never do"), which
  Android's two-icon set already satisfies after name translation — **not** a second literal inventory.
- **Tests.** `tools/ios-checks/assets-check.js` asserting: 42 imagesets exist; every `Contents.json`
  is valid JSON with template intent; every SVG is well-formed XML with a 24x24 viewBox; the icon-name
  set **equals Android's `MentoraIconName` 42 entries**; and the mirror set **equals Android's exact
  `autoMirror` set (`arrowForward`, `arrowBack`), and nothing else**.
- **Manual verification.** Not possible here — rendering fidelity is an **MC-2** item.
- **Completion gate.** Structural checks green; the icon-name set equals Android's `MentoraIconName`
  (42) one-for-one **and** the mirror set equals Android's `autoMirror` set exactly. Those two
  equalities **are** the gate — an earlier draft required a glyph-by-glyph reconciliation against
  `design-tokens.json#/icon/directional`, which is not achievable and is withdrawn.
  **Pre-decided fallback if Xcode's SVG import mangles stroked paths at MC-2:** convert
  the same path data to filled-path PDFs, or add a small SVG-path parser producing SwiftUI `Path`s.
  Recorded now so it is not improvised later.
- **Host.** **W** — the SVGs, `Contents.json` files and structural checks are real Windows output;
  the one small Swift file (`MentoraIcon.swift`) references no `shared` symbol, so it is authored
  here and its rendering is verified at MC-2.

### T4a — Xcode project scaffolding (the genuinely Windows-completable half)

- **Scope.** Everything about the project *definition* that does not depend on a single SKIE-generated
  Swift symbol. This is the last task that produces real, verifiable Windows output, and it is the
  honest pause boundary if no Mac is available.
- **Files.** `mobile/iosApp/project.yml`, `mobile/iosApp/scripts/build-shared-xcframework.sh`,
  `mobile/iosApp/Packages/MentoraShared/Package.swift`, `mobile/iosApp/iosApp/Resources/Info.plist`,
  `.gitignore` additions.
- **Criteria.** E2 (environment selection is declared, not hardcoded), J2 (prerequisite), A8/J8
  (boundary hygiene).
- **Depends on.** T1, T2.
- **Approach.** `project.yml` declares the app target (deployment target **iOS 17.0**), the unit-test
  and UI-test targets, the local SPM package dependency, the asset catalogs from T2/T3, and a
  **debug-only** App Transport Security `NSAllowsLocalNetworking` exception. It must also define
  `DEBUG` in `SWIFT_ACTIVE_COMPILATION_CONDITIONS` for the Debug configuration only, because
  `enableNetworkLogging` is guarded by `#if DEBUG` in T4b (`PHASE_5_IOS_SYSTEM_DESIGN.md § 8`) — an
  inline "debug builds only" comment is not a guard. `Package.swift` wraps `shared.xcframework` as a
  `binaryTarget`, per `KMP_ARCHITECTURE.md § 4`'s explicit SPM wording.
  `build-shared-xcframework.sh` invokes **`./gradlew :shared:assembleSharedDebugXCFramework`**
  (release: `assembleSharedReleaseXCFramework`; both: `assembleXCFramework`) — **never
  `assembleSharedXCFramework`, which does not exist** (T1).
- **Tests.** `project.yml` parses as valid YAML and declares every target/setting the README will
  document; `Package.swift` references the XCFramework path the Gradle task actually produces;
  `.gitignore` covers `DerivedData/`, `*.xcuserdata*`, `mobile/iosApp/build/`, and the XCFramework
  output path. All checkable here.
- **Manual verification.** File/diff review. No Swift, so nothing is authored blind.
- **Completion gate.** Windows checks green. `xcodegen generate` itself is an MC-2 item.
- **Host.** **W.**

### T4b — Swift app bootstrap (**Mac-only**, starts after MC-1)

- **Scope.** A running app that creates the SDK once, restores the session, seeds the locale,
  subscribes to the B10 Keychain-failure flow, and renders a placeholder root. No feature UI.
  **Every `shared`-side symbol this task touches is SKIE-generated and its exact Swift spelling is
  unknown until MC-1 — which is why this task is Mac-only rather than authored blind.** An earlier
  draft tagged it "W-auth / M-verify" while simultaneously requiring MC-1 first; that was
  self-contradictory, and it is resolved here: **T4b is `M`, and T4a is the Windows stopping point**
  (§ 1 rule 5, § 5.2).
- **Files.** `iosApp/MentoraApp.swift`, `AppEnvironment.swift`, `Support/SessionController.swift`,
  `Support/LocaleController.swift`, `Support/ThemeController.swift`.
- **Criteria.** A1, A2 (the **six** sanctioned non-façade entry points and no more), A3, B3, B4, B5,
  **B10**, E2, F3, H7, J2.
- **Depends on.** T4a **and MC-1** — MC-1 captures the generated Swift interface, which is this task's
  reference (risk § 5.2). T4b does not begin before that capture exists.
- **Approach.** Build `AppEnvironment` with **one** `MentoraSdk.create(environment: iosSimulator(),
  platformModule: platformModule(), enableNetworkLogging: <#if DEBUG-guarded flag>)`; run one ordered
  task `restoreSession()` then `seedInitialLocaleIfNeeded()` (which calls `shared`'s top-level
  `resolveInitialLocale(systemLocales:)` — a sanctioned non-façade entry point, A2); subscribe once
  each to `observeAuthState()`, `observeLocale()`, and **`KeychainStatus.failures`** (the sixth
  sanctioned entry point — `SessionController` owns it, and a published failure must reach the UI so
  login/logout cannot claim a success storage did not deliver, `PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1`
  K3); read `getTheme()` from the app's own single extra `IosPreferenceStore` exactly as
  `PHASE_5_IOS_SYSTEM_DESIGN.md § 8` specifies (never for locale). `AppEnvironment` is injected via
  `@Environment`; no `.shared` static exists anywhere (§ 3.1).
- **Tests (MC-2).** Bootstrap runs `restoreSession` exactly once; `Authenticated(user: nil)` triggers
  exactly one `getProfile`; locale seeding is skipped when already authenticated and when the
  once-per-install flag is set; a published `KeychainFailure` reaches the UI layer rather than being
  swallowed.
- **Manual verification (MC-2).** Launch on Simulator; log in; **terminate and relaunch — still
  authenticated** (the Keychain proof); log out and relaunch — **still logged out**; confirm requests
  reach the local backend.
- **Completion gate.** MC-2 items 1-5 pass. **Documented fallbacks:** if the SPM binary-target +
  static-framework + SKIE combination misbehaves, switch to the Gradle
  `embedAndSignAppleFrameworkForXcode` build phase and record it in `DECISIONS_LOG.md` as a disclosed
  deviation from `KMP_ARCHITECTURE.md § 4`'s SPM wording — never a silent substitution; a second lever
  is `isStatic = false`.
- **Host.** **M** (Mac-only, after MC-1).

### T5 — `SharedBridge` (MentoraClient, ApiResult unwrapping, Flow adapters)

- **Scope.** The one boundary layer between SwiftUI and KMP (`PHASE_5_IOS_SYSTEM_DESIGN.md § 2`).
- **Files.** `Support/SharedBridge/MentoraClient.swift` (one method per use case actually used),
  `ApiResultBridge.swift`, `FlowBridge.swift`, `MentoraError.swift`; `Support/ErrorCopy.swift`.
- **Criteria.** A2, A4, A5, F1, F6, F8, I4.
- **Depends on.** T4b.
- **Approach.** One `async throws` method per domain action, each wrapping
  `sdk.<facade>.<useCase>.invoke(...)`; one generic unwrap turning `Success` into the value and
  `Failure` into a thrown `MentoraError(code:fields:httpStatus:)`; boxed primitives unboxed here;
  optional-returning use cases keep their nil-means-no-op semantics; flow helpers for auth state,
  locale, and the AI stream. `ErrorCopy` maps every `ApiErrorCode` to a localization key with an
  explicit default for `Unknown(raw)` — the iOS analogue of `ui/error/ApiErrorCopy.kt`.
- **Tests.** Unit tests over every unwrap branch, the full code-to-copy table (each code exactly once,
  no duplicates, no missing), and nil-preserving behavior for the two optional use cases.
- **Manual verification.** A one-screen smoke that fetches categories and renders them raw.
- **Completion gate.** No `Features/`/`Components/` file references a Kotlin type (grep, run as a test).
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T6 — Design-system runtime (fonts, shapes, elevation, theme root)

- **Scope.** The hand-authored behavior layer on top of T2's generated values.
- **Files.** `Theme/MentoraTypography.swift`, `Theme/MentoraShape.swift`, `Theme/MentoraElevation.swift`,
  `Theme/MentoraTheme.swift` (root modifiers: color scheme, locale, direction).
- **Criteria.** G3, G4, G5, G6, H8, I1.
- **Depends on.** T2, T4b.
- **Approach.** **Typography is a `ViewModifier`, not a `Font` extension** — this is the shape
  `design-system/platform-mapping.md § 2` (LOCKED) specifies (`.mentoraFont(.h1)`), and the
  `Font.mentora*` shape described in `platform-contract.json`'s prose is unimplementable. Full
  reasoning in `PHASE_5_IOS_SYSTEM_DESIGN.md § 15.1`. Concretely: a `MentoraTextStyle` enum with the 12 cases `.displayLarge`,
  `.displayMedium`, `.h1`-`.h4`, `.bodyLarge/Medium/Small`, `.labelLarge/Medium`, `.caption`
  (**that naming, not `HeadingH1`**), and one `View.mentoraFont(_:)` modifier composing:
  `@ScaledMetric(relativeTo: <the contract's anchor>)` for size (so Dynamic Type works **and**
  responds to an injected `.dynamicTypeSize(...)` in previews/tests — `Font.system(size:)` alone and
  `UIFontMetrics` both fail that), `Font.system(size:weight:)` for the face (no bundled font, so SF
  Arabic substitution stays automatic — H8), `.tracking()` for `letterSpacing` applied **unscaled**,
  and for line height **`PHASE_5_IOS_SYSTEM_DESIGN.md § 15.1`'s ratio formula, copied verbatim rather
  than re-derived**: `lineSpacing = max(0, scaledSize * (ratio - 1.2))`, where
  `ratio = token.lineHeight / token.fontSize` (times 1.10 for Arabic body steps) and `scaledSize` is
  the single `@ScaledMetric` value — so the target line height scales with the font size and the
  result can never go negative. **The earlier `.lineSpacing(lineHeight - scaledSize)` form is
  withdrawn:** it subtracted an unscaled token value from a scaled one, so it reached zero and then
  went negative at accessibility sizes, taking the claimed line-height ratio and the Arabic +10% rule
  with it. Arabic-run adjustments per `LOCALIZATION.md § 4` (tracking forced to 0, body line-height
  +10% applied to the *ratio*, i.e. to the scaled target) live inside that **one** modifier, keyed off
  `@Environment(.locale)`. Then: shape mapping including the top-corners-only **24 pt** sheet/dialog
  shape; a `.mentoraElevation(_:)` modifier pairing the generated `mentoraShadowElevation<N>` colorset
  with a 1 pt border; `.preferredColorScheme` applied at the true root so the status bar and any
  full-screen cover inherit it (the iOS form of the Phase 4 status-bar defect).
- **Tests.** Unit assertions that each of the 12 styles resolves to its token metrics
  (size/weight/tracking/lineSpacing) at the default content-size category; and a Dynamic Type test
  that goes past "the font size changed" — a **multiline** sample rendered at **`.large` and
  `.accessibility5`**, in **both `en` and `ar`**, asserting (a) the leading ratio is preserved at both
  sizes, (b) `lineSpacing >= 0` for all 12 styles in both locales at both sizes, (c) tracking is never
  more negative than its token value (it is not scaled) and is `0` under `ar`, and (d) no overlap —
  rendered height grows with the size. Snapshot- or geometry-reading XCTest, run on the Mac; its
  AX-size half is also what would have caught the `Font`-extension mistake. Plus a test asserting the
  Arabic overrides fire for an `ar` locale and not for `en`, and a drift test that the generated
  values still match `design-tokens.json`.
- **Manual verification (MC-2/MC-3).** A token gallery preview in light+dark, en+ar, default and AX
  text sizes — the iOS analogue of Android's `TokenSwatchPreview`.
- **Completion gate.** No raw `.system(size:)` and no raw system color **outside `Theme/`** (grep
  test) — `Font.system(size:weight:)` appears **exactly once**, inside `MentoraTypography.swift`'s
  modifier, and nowhere else; no `UIFontMetrics` anywhere.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T7 — Localization foundation

- **Scope.** The full string catalog and the runtime-locale mechanism, before any screen hardcodes
  anything.
- **Files.** `Resources/Localizable.xcstrings`, `Support/MentoraStrings.swift`,
  `Support/Formatters.swift`, `Support/LocaleController.swift` (extended), catalog-parity test.
- **Criteria.** H1, H2, H4, H6, H7, H3 (mechanism), C17.
- **Depends on.** T4b.
- **Approach.** Port Android's key set one-for-one (279 EN keys / 278 AR; `app_name` is intentionally
  EN-only) into the String Catalog, preserving key names so the two clients cannot drift in copy.

  **"One-for-one" does NOT mean byte-identical values — every format specifier is converted.**
  `mobile/androidApp/src/main/res/values(-ar)/strings.xml` deliberately uses `%N$s` for **every**
  substitution, including numerals (its own comment: numeral placeholders are `%1$s`/`Int.toString()`,
  never `%1$d`, per `LOCALIZATION.md § 8`). On iOS `%s` means a **C string**; handing it a Swift
  `String` yields garbage or a crash. **The port therefore rewrites `%N$s` to `%N$@` for every
  occurrence, preserving the positional index.** Verified inventory at plan time (XML comments
  excluded): **38 keys carry placeholders; 54 occurrences per locale — 38 `%1$s`, 14 `%2$s`, 2 `%3$s`
  — identical in EN and AR; zero `%N$d` in any string value.** Also verified: **the Android set
  contains zero `<plurals>`**, so there is **no plural-rule porting to do at all** — a real
  simplification, stated explicitly so nobody builds String Catalog plural variations that have no
  source.

  **Substitution always goes through the ported explicit keys** plus `String(format:)` /
  `String(localized:)` with explicit arguments. **SwiftUI's auto-generated String Catalog
  interpolation keys are forbidden** — writing `Text("Hello (name)")` makes Xcode silently extract a
  new key, creating a parallel, un-ported key space that compiles fine, never appears in the Android
  set, and breaks H1/H2 without any visible symptom.

  Root applies `\.locale` (Arabic as `ar-u-nu-latn`, forcing Western numerals per `LOCALIZATION.md § 8`)
  and `\.layoutDirection`. `MentoraStrings.text(_:locale:)` **requires** a locale argument, making the
  D93 out-of-tree resolution bug unrepresentable. Formatters take the active locale explicitly.
- **Tests.** `tools/ios-checks/catalog-parity.js` — **this is the half that genuinely runs on
  Windows**, because `.xcstrings` is JSON and the script is plain Node. It asserts EN/AR key parity,
  per-key format-specifier parity, and a **specifier lint: only iOS-valid specifiers appear — any
  surviving `%N$s` or `%N$d` is a failure**, as is a non-positional `%@` in a multi-argument string.
  An XCTest wrapper asserts the same invariants in-target, but **that half is Mac-only** (J1: no Swift
  compiles here) — "checkable on Windows" refers to the Node script and nothing else. Plus (Mac-only)
  formatter tests asserting Eastern-Arabic digits never appear, and a test that `MentoraStrings`
  follows the injected locale rather than the device locale.
- **Manual verification (MC-2).** Confirm SwiftUI `Text` resolves against the injected `\.locale`. If
  it does not, apply the § 12 bundle-lookup fallback inside `MentoraStrings` only.
- **Completion gate.** The Node parity + specifier lint green on Windows; no user-facing literal in
  the app target; no auto-extracted interpolation key anywhere in the catalog.
- **Host.** **M** — `tools/ios-checks/catalog-parity.js` genuinely runs on Windows and may be run
  here at any time, but the task itself sits after the MC-1 gate (§ 1 rule 5).

### T8 — Component Kit A (atoms)

- **Scope.** The ~14 atoms listed in `PHASE_5_IOS_SYSTEM_DESIGN.md § 16`, each with previews.
- **Files.** `Components/*.swift` + `#Preview`s.
- **Criteria.** G1-G5, G7, H1, H5, I1, I2.
- **Depends on.** T6, T7.
- **Approach.** Build against `design-system/COMPONENTS.md`'s per-component spec and the interaction
  state matrix; every color/type/shape from the generated layer — **composed from semantic/primitive
  tokens at the point of use**, since there is deliberately no generated component-token file
  (`PHASE_5_IOS_SYSTEM_DESIGN.md § 15.2`: Android emitted none either) and **never** by typing a
  literal value, which would violate G2; typography always via `.mentoraFont(_:)`; 44 pt targets; accessibility labels
  as required parameters where a control is icon-only; TextField carries the third non-color error
  signal (`ACCESSIBILITY.md § 7`).
- **Tests.** Unit tests for pure logic (e.g. password visibility state, select option mapping);
  previews double as the visual harness.
- **Manual verification (MC-3).** Preview sweep in light/dark, en/ar, default and AX sizes.
- **Completion gate.** Every atom used by a later task exists with no English default label.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T9 — Navigation shell

- **Scope.** The 5-tab shell, per-tab stacks, route model, auth gate, tab-bar hiding.
- **Files.** `Navigation/Route.swift`, `TabRouter.swift`, `TabShell.swift`, `RootView.swift`,
  `AuthGate.swift`, `Components/MentoraTabBar` styling.
- **Criteria.** D1-D8, B8, D6.
- **Depends on.** T4b, T8.
- **Approach.** `TabView` + one `NavigationStack(path:)` per tab. `TabRouter` holds **five stored
  `[Route]` properties**, one per tab — **not** a `[Tab: [Route]]` dictionary, which cannot be
  projected into the `Binding<[Route]>` `NavigationStack(path:)` requires; a
  `path(for tab:) -> Binding<[Route]>` helper that switches over those five is the only permitted
  indirection (`PHASE_5_IOS_SYSTEM_DESIGN.md § 3.1`). A `Hashable` `Route` enum carrying ids only;
  selection-binding interception for tap-active-tab-pops-to-root; `.toolbar(.hidden, for: .tabBar)` on
  Course Player and Quiz, **with the `fullScreenCover` fallback pre-decided in § 10** if that modifier
  proves unreliable at MC-3. The auth gate stores a pending `Route` and replays it after login, and
  **clears it on any non-authenticating dismissal of the login sheet, including swipe-to-dismiss**
  (implement in `onDismiss:`, not in a Cancel action). **Logout clears all five paths and the pending
  `Route`**, not just the active tab's. Screen models are constructed inside the
  `navigationDestination(for: Route.self)` closure with the client passed in explicitly — never a
  `@State` default reaching a static (§ 3.1; this is where the 18-screen pattern is set). **Do not
  port Android's nested-graph / `saveState` machinery** (§ 10).
- **Tests.** Unit tests over `TabRouter`: push/pop/reset, genuinely independent stacks,
  pending-intent replay, **pending intent cleared on sheet dismissal**, and **logout clearing all five
  stacks** (not only the selected one); XCUITest for D2/D3/D5 once screens exist.
- **Manual verification (MC-3).** Walk every tab, deep-push, switch away and back.
- **Completion gate.** D1-D5 demonstrable with placeholder leaves.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T10 — Auth screens (Login, Register)

- **Scope.** Screens C1, C2.
- **Files.** `Features/Auth/{LoginView,LoginModel,RegisterView,RegisterModel}.swift`.
- **Criteria.** B1, B2, B8, B9, I4, H1.
- **Depends on.** T5, T9.
- **Approach.** Inline validation via `shared`'s `EmailValidator`/`PasswordValidator`; server `fields`
  routed to inline field errors (the D51 pattern); a single generic credential-failure message; the
  gate returns to the pending intent on success.
- **Tests.** Model tests for each validation and failure branch, including `EMAIL_ALREADY_REGISTERED`
  and `RATE_LIMITED_AUTH`.
- **Manual verification (MC-2/MC-3).** Register a fresh account and log in against the real backend,
  in both locales.
- **Completion gate.** Full register→login→authenticated-shell chain works live.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T11 — Component Kit B (cards, state patterns, sheets, artwork)

- **Scope.** The composite components in `PHASE_5_IOS_SYSTEM_DESIGN.md § 16`.
- **Files.** `Components/*.swift` (CourseArtwork, CourseCard, CourseProgressCard, LearningPathCard,
  CertificateCard, StatCard, QuestionCard, AnswerOption, AITutorBubble, AITutorQuickAction,
  CheckoutSummary, LoadingState, EmptyState, ErrorState, SuccessState, MentoraSheet, MentoraDialog).
- **Criteria.** G8, G9, I3, I4, I5, H1, I2 (combined accessibility elements).
- **Depends on.** T8.
- **Approach.** Compose from `design-to-code/screens/mobile-*.json` where an exact-showcase spec
  exists, `COMPONENTS.md` otherwise; component-level values are composed from the semantic/primitive
  tokens at point of use — there is no `MentoraComponentTokens` file to read from and none may be
  hand-written (§ 15.2); the 5-motif artwork system comes from `artwork.json`; the
  category chip sits on the artwork scrim with the **pinned** on-scrim foreground (the `8feacad`
  dark-theme defect); skeletons match their resolved content's footprint.
- **Tests.** Logic tests (motif selection by seed/category, progress formatting); previews per
  component in light/dark, en/ar.
- **Manual verification (MC-3).** Preview sweep; direct comparison against the Android screenshots.
- **Completion gate.** Every component a feature task needs exists; no English default strings.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T12 — Explore + Learning Paths segment

- **Scope.** Screens C4 and C18.
- **Files.** `Features/Explore/{ExploreView,ExploreModel}.swift`.
- **Criteria.** C4, C18, D4, I3-I6, H5.
- **Depends on.** T11, T9.
- **Approach.** `sdk.catalog.listCategories` + `searchCourses` (cursor paging, filters) and
  `sdk.learningPaths.listLearningPaths` (plain list, never paged — `INTEGRATION_CONTRACT.md § 12`
  item 6); a segmented control switches the two lists inside one screen (`MOBILE_UX.md § 3`), not a
  second pushed screen; locale-change reload applies here (locale-sensitive content).
- **Tests.** Model tests: search debouncing, filter composition, paging append, empty vs error states,
  locale-change reload fires exactly once per change.
- **Manual verification (MC-3).** Live search/filter/paging in both locales; confirm the Arabic result
  set differs where seed data differs.
- **Completion gate.** Both segments live; paging and empty/error states correct.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T13 — Course Details

- **Scope.** Screen C5.
- **Files.** `Features/CourseDetails/{CourseDetailsView,CourseDetailsModel}.swift`.
- **Criteria.** C5, B8 (guest enroll gate), I3-I5.
- **Depends on.** T12.
- **Approach.** `sdk.catalog.getCourseDetails` provides curriculum inline — **no second call** (G4's
  `isEnrolled` gap means membership is derived from `sdk.enrollment.listEnrollments()`); lessons render
  without duration (no API field exists, item 8); CTA switches between Enroll (guest → auth gate) and
  Continue (enrolled).
- **Tests.** Model tests for the three CTA states (guest, authenticated-not-enrolled, enrolled) and the
  404-for-draft-course case.
- **Manual verification (MC-3).** Both locales; a course with a translation and one without.
- **Completion gate.** Curriculum renders in section/lesson order; CTA correct in all three states.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T14 — Demo Checkout + Purchase Success

- **Scope.** Screens C7, C8.
- **Files.** `Features/Checkout/{DemoCheckoutView,DemoCheckoutModel,PurchaseSuccessView,PurchaseSuccessModel}.swift`.
- **Criteria.** C7, C8, D8, E3.
- **Depends on.** T13.
- **Approach.** `getCheckoutPreview` then `completeDemoCheckout`; 201 and 200 are both success
  (`alreadyEnrolled` distinguishes them); never retry the POST; Purchase Success back-navigates to My
  Learning. No locale-change reload here (a one-shot transactional flow — the D94 opt-out rule).
- **Tests.** Model tests for first-purchase vs idempotent-repeat; a payment-vocabulary grep test.
- **Manual verification (MC-3).** Complete a real demo purchase, then repeat it to prove idempotency.
- **Completion gate.** Enrollment appears in My Learning afterwards; zero payment vocabulary.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T15 — Home + My Learning (+ Certificates entry)

- **Scope.** Screens C3, C9.
- **Files.** `Features/Home/*`, `Features/MyLearning/*`, `Support/MyLearningWithProgress.swift`.
- **Criteria.** C3, C9, I4 (the fail-whole-call rule), F6.
- **Depends on.** T11, T14.
- **Approach.** `sdk.enrollment.getMyLearning` plus a per-course `sdk.progress.getCourseProgress`
  join — the **Swift duplicate** of Android's `GetMyLearningWithProgressUseCase` (G3); the duplication
  is recorded, not hidden (`PHASE_5_ACCEPTANCE_CRITERIA.md § 3` item 10, which also records *why*
  promoting it into `shared` is not available inside this phase: removing Android's copy would require
  editing `mobile/androidApp/`, which A7 forbids, so promotion would add a **third** implementation
  rather than remove a duplicate).

  **Match Android's shape exactly — read `GetMyLearningWithProgressUseCase.kt` before writing this.**
  (a) It **eagerly drains every page**: `do { getMyLearning(cursor, PageLimit = 50) } while (cursor
  != null)`. This is **not** incremental UI paging, and iOS must **not** introduce incremental paging
  here — that would make the two clients show different data at the same scroll position. (b) The
  **per-course progress join also fails the whole call**: the first `Failure` from *either* the list
  call or *any* `getCourseProgress` call is returned as the join's result, with no partial-list
  fallback. (c) Calls stay **sequential** — no `TaskGroup` parallelization, which would change the
  observed failure ordering. Request count is therefore roughly **2N+1** (list pages + N progress
  calls), **not "N+1"**: paging itself costs more than one request, and `shared`'s own
  `getMyLearning` additionally makes N internal `GET /courses/{id}` calls. `GetMyLearningUseCase`
  failing the whole call renders a retriable error state, never a partial list. Home's
  continue-learning module uses `sdk.progress.resumeCourse`.
- **Tests.** Join composition tests covering: multi-page drain (assert **every** page is fetched, not
  just the first), a failing list page, a failing per-course progress call (assert the **whole** join
  fails, no partial list), sequential ordering; guest-vs-authenticated Home states; empty My Learning.
- **Manual verification (MC-3).** Fresh account (empty states) and seeded account (populated).
- **Completion gate.** Progress values match the backend exactly; Certificates entry navigates.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T16 — `AVPlayerLessonPlayer` + Course Player + Curriculum sheet (highest-risk task)

- **Scope.** Screen C10 plus the playback layer (`PHASE_5_IOS_SYSTEM_DESIGN.md § 18`).
- **Files.** `Playback/{LessonPlayer.swift,AVPlayerLessonPlayer.swift,PlayerSurface.swift,PlaybackUrlResolver.swift}`,
  `Features/CoursePlayer/{CoursePlayerView,CoursePlayerModel,CurriculumSheet,PlayerControls}.swift`.
- **Criteria.** C10, E6, F7, D5, H5 (scrubber stays LTR), I2 (scrubber accessibility).
- **Depends on.** T5, T15.
- **Approach.** Swift-native `LessonPlayer` protocol mirroring `LessonPlaybackController` member for
  member; `AVPlayer` + `AVPlayerLayer` in a `UIViewRepresentable`; periodic time observer for position;
  `AVPlayerItem.status`/KVO for state; URL used as-is with **no** auth header; refresh via
  `sdk.media.refreshPlaybackUrl` before the ~5-minute expiry, re-preparing at the current position;
  heartbeat through `sdk.progress.reportPlaybackPosition` on the single SDK instance (no second
  throttle); `sdk.progress.completeLesson` returns the auto-advance target — never computed locally;
  curriculum in a sheet, never beside the video; no locale-change reload (in-flight playback state).
  **Tab bar hidden (D5), with a pre-decided fallback:** `.toolbar(.hidden, for: .tabBar)` first; if it
  proves unreliable on the Simulator's actual iOS version at MC-3 (a known regression area regardless
  of the 17.0 deployment target — the Simulator runs whatever iOS the Mac's Xcode ships), switch to
  presenting the focused-learning shell — Course Player, Quiz, Quiz Results — as a
  **`fullScreenCover` hosting its own `NavigationStack`**, which structurally has no tab bar. The
  originating tab is recorded on presentation and any pending `Route` is **replayed onto that tab's
  stack** on dismissal, so D4 and D8 still hold. This applies jointly to T16 and T17, and is a
  `DECISIONS_LOG` entry if taken (`PHASE_5_IOS_SYSTEM_DESIGN.md § 10`).
- **Tests.** Player state-machine tests against a fake player; URL-refresh threshold tests;
  auto-advance and section-boundary tests driven by `shared`'s returned target.
- **Manual verification (MC-3).** Play a real seeded lesson; scrub; background/foreground; **play past
  5 minutes to force a refresh**; complete a lesson and confirm auto-advance; RTL chrome with an LTR
  scrubber.
- **Completion gate.** A full course can be completed end to end. **Risk control: land the player layer
  as its own sub-commit before the screen**, exactly as Phase 4 was advised to for its ExoPlayer task
  (which ultimately needed 5 review rounds, D85/D87/D88).
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T17 — Quiz + Quiz Results

- **Scope.** Screens C11, C12.
- **Files.** `Features/Quiz/{QuizView,QuizModel,QuizResultsView,QuizResultsModel,QuizDraftStore}.swift`.
- **Criteria.** C11, C12, D4, D5.
- **Depends on.** T16.
- **Approach.** `sdk.quiz.getQuiz` (options structurally carry no correctness), answers held in a draft
  store owned **above** the pushed screen so they survive back-navigation; `sdk.quiz.submitQuiz`
  validates completeness in `shared`; results re-fetch the latest attempt rather than passing it
  through navigation; `QUIZ_NOT_FOUND` and `ATTEMPT_NOT_FOUND` are legitimate typed states, not errors.
  **Continue must work from every entry path** — the explicit Phase 4 regression (`90c8af3`) — and
  that includes the `fullScreenCover` variant of D5 if T16 took that fallback: the terminal Continue
  action must dismiss the cover **and** replay onto the originating tab's stack, never dead-end inside
  the cover. Quiz inherits whichever D5 mechanism T16 landed; the two are not chosen independently.
- **Tests.** Draft persistence across pop/push; incomplete-submission blocking; both not-found states;
  a UI test for Continue from the multi-tab chain.
- **Manual verification (MC-3).** Complete a real quiz on the seeded course, both pass and fail, both
  locales; verify Continue on both entry paths.
- **Completion gate.** No dead terminal action on any path.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T18 — Certificates List + Certificate Detail

- **Scope.** Screens C13, C14.
- **Files.** `Features/Certificates/{CertificatesView,CertificatesModel,CertificateDetailView,CertificateDetailModel}.swift`.
- **Criteria.** C13, C14, I5.
- **Depends on.** T15.
- **Approach.** `sdk.certificates.listCertificates` (paged) and `getCertificate` (opaque id passed
  verbatim); snapshot fields rendered exactly as issued, never re-resolved against live course data;
  the placeholder document presentation (no real certificate asset exists — inherited limitation);
  share is UI-only (`ShareLink`), generating no PDF/image.
- **Tests.** Id-passthrough test; snapshot-field mapping; empty state.
- **Manual verification (MC-3).** Earn a certificate live (complete all lessons + pass the quiz — the
  backend requires both) and open its detail.
- **Completion gate.** Detail matches the backend payload verbatim.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T19 — Learning Path Details (follow/unfollow)

- **Scope.** Screen C6.
- **Files.** `Features/LearningPathDetails/{LearningPathDetailsView,LearningPathDetailsModel}.swift`.
- **Criteria.** C6, B8 (guest follow gate), F6.
- **Depends on.** T12.
- **Approach.** `getLearningPathDetail` (optional-auth, guest-safe, `progressPercent` nil for guests);
  follow/unfollow are idempotent and return the new state; curated course order preserved;
  server-derived progress never recomputed. Followed-path membership for any list view uses the same
  per-path detail call Android used (G5's accepted N+1 at seed scale).
- **Tests.** Guest vs authenticated states; follow/unfollow round trip; order preservation.
- **Manual verification (MC-3).** Follow and unfollow live as a guest (gated) and as a student.
- **Completion gate.** Both locales; guest path never shows a progress value.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T20 — AI Tutor (streaming chat, quick actions)

- **Scope.** Screen C15.
- **Files.** `Features/AITutor/{AITutorView,AITutorModel}.swift`.
- **Criteria.** C15, H4, I8, I4.
- **Depends on.** T5, T11.
- **Approach.** `sdk.aiTutor.getConversation` (its own non-standard envelope type, paged) and
  `sendMessage` as an `AsyncSequence`, appending chunks live; mid-stream failure preserves partial text;
  pre-stream failures arrive in the same shape whether local validation or a real 403/404/429 rejected
  them; the 5 quick actions come from `shared` with **iOS-supplied localized prompt text resolved
  against the active locale** (the D93 regression class — H4); `courseId`+`lessonContextId` are sent
  both-or-neither; the input stays pinned above the keyboard and open after send.
- **Tests.** Stream accumulation, mid-stream failure, pre-stream failure, the pairing rule, the
  4000-character limit, and a test asserting each quick action maps to a distinct non-empty prompt key.
- **Manual verification (MC-3).** Stream a real reply from the stubbed provider; rotate/keyboard; both
  locales.
- **Completion gate.** Streaming visibly incremental, not a single late blob.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T21 — Profile + Settings

- **Scope.** Screens C16, C17 — including the functional language switch.
- **Files.** `Features/Profile/{ProfileView,ProfileModel,SettingsView,SettingsModel}.swift`.
- **Criteria.** C16, C17, B6, H3, G6, I2.
- **Depends on.** T7, T9.
- **Approach.** Profile shows avatar/initials (with a real accessibility label — the D93 finding),
  name, email, stats, inline name-only editing via `sdk.user.updateProfile`; **no password/account
  fields** (no endpoint); Logout lives on Profile only (`MOBILE_UX.md § 13` overrides the generic
  cross-platform bullet), and **a logout whose Keychain delete genuinely failed is surfaced as a
  failed logout, never as a completed one** (B10, via `SessionController`'s `KeychainStatus`
  subscription). Settings has theme (via `sdk.user.setTheme` + `ThemeController`) and the
  language `Picker` calling `sdk.user.setLocale`, which flips strings and direction immediately.
- **Tests.** Name-validation error routing (`REQUIRED`/`TOO_LONG`); theme persistence across relaunch;
  locale switch updates the controller.
- **Manual verification (MC-3).** Switch language both ways from deep inside a tab and confirm no
  navigation reset, no restart, and that already-loaded locale-sensitive content reloads (the D93/D94
  gap — this is exactly where it shows up).
- **Completion gate.** H3 demonstrably true from at least three different screens.
- **Host.** **M** (Mac-only, after MC-1 — § 1 rule 5).

### T22 — QA sweep + XCUITest suite (MC-3)

- **Scope.** The cross-cutting pass Phase 4 ran as its Task 19, done to a higher bar (Phase 4's own
  sweep was explicitly a spot-check, and the later acceptance audit found what it missed).
- **Files.** `iosAppUITests/*`, plus fixes across `Features/`/`Components/`.
- **Criteria.** H1-H8, I1-I10, D2-D5, D8, G6, G10, J3, J4.
- **Depends on.** T10-T21.
- **Approach.** (a) Localization sweep: every screen in `ar`, hunting hardcoded literals and unexercised
  component string defaults (the `retryLabel` class of gap, D94). (b) RTL sweep: mirroring correctness,
  the LTR scrubber, Western numerals, directional-icon behavior, and a bidi check on any digit+
  punctuation sequence (the `8feacad` Arabic step-numbering defect is the precedent). (c) Dynamic Type
  at the largest AX size on all 18 screens, **including the § 15.1 line-height check on a multiline
  sample in `en` and `ar`: non-negative line spacing, preserved leading ratio, no overlap**. (d) VoiceOver + Accessibility Inspector per screen.
  (e) Dark mode on all 18. (f) XCUITest: the portfolio journey plus the navigation behaviors, including
  the exact multi-tab Continue chain.
- **Tests.** The XCUITest suite runs green against the real backend; all unit tests green.
- **Manual verification.** Recorded per screen and per dimension in `CURRENT_STATUS.md` — with any
  screen/dimension not covered named explicitly rather than implied.
- **Completion gate.** Every finding either fixed or recorded as a disclosed limitation with a reason.
- **Host.** **M (MC-3).**

### T23 — Live verification, README, handoff, acceptance audit (MC-4)

- **Scope.** The Phase 4 Task 20 equivalent, plus the independent acceptance audit that phase only got
  *after* its handoff (and which found two real defects — do it inside the phase this time).
- **Files.** `mobile/iosApp/README.md`, `execution/CURRENT_STATUS.md`, `execution/PHASE_HANDOFF.md`
  (Phase 5 entry, fixed 10-section structure), `execution/DECISIONS_LOG.md` (D96+), and
  `execution/INTEGRATION_CONTRACT.md` only if a genuinely new as-built wire fact surfaced.
- **Criteria.** J5, J6, J7, J8, J9, J10, and a verdict recorded for **every** criterion A1-J10.
- **Depends on.** T22.
- **Approach.** Full live walk of the portfolio-priority flow in both locales and both appearances;
  README in the Android README's shape (prerequisites, build the XCFramework, generate/open the
  project, run against the backend, demo accounts, gates, known limitations including the
  physical-device base-URL gap and the auth rate limit); handoff with honest known limitations; then an
  **independent audit pass** per `PHASE_5_ACCEPTANCE_CRITERIA.md § 2` that re-walks the chains rather
  than trusting the suite.
- **Tests.** Full suite re-run (unit + XCUITest) against the exact final commit — not a remembered
  earlier run (the Phase 4 Task 19 recovery lesson, D94).
- **Manual verification.** The audit itself.
- **Completion gate.** Every criterion has PASS/FAIL/PARTIAL/NOT TESTABLE recorded; working tree clean;
  boundary greps (A7/A8) green.
- **Host.** M for verification, W for documentation.

---

## 5. Execution risks

1. **No Mac = no Phase 5.** The largest risk by far. **T4b-T23 cannot be compiled, run or verified on
   this host, and T1b can be authored but not compiled.** The Windows-executable set is exactly
   **T1, T1b (authoring only), T2, T3, T4a** — § 1 rule 5, and the same sentence appears in
   `PHASE_5_ACCEPTANCE_CRITERIA.md § 4.1`. Resolve that open question before committing to the phase.
2. **Authoring ahead of the compiler — which is exactly why T4b is Mac-only.** SKIE-generated Swift
   symbol names are literally unknown until MC-1, so authoring T4b on Windows would produce a file in
   which every `shared` reference is a guess and a correction pass would be guaranteed rather than
   likely. The plan therefore does **not** author T4b blind: **T4a is the Windows stopping point**, and
   T4b begins on the Mac once MC-1 has captured the generated Swift interface, which is then the
   reference for T4b's bootstrap and T5's bridge signatures. This is why T4 is split at all: **T4a is
   genuinely finishable on Windows** (project definition, SPM manifest, scripts, Info.plist, gitignore
   — no `shared` symbol referenced), while T4b is not, and reporting them as one task would give them
   the same evidentiary standing when they do not have it. **T1b is the one deliberate exception to
   "do not author what you cannot compile", and it is a different situation:** Kotlin rather than
   Swift, documented `platform.Security` APIs the file already uses, no dependency on any generated
   interface, and a required correctness fix for a review-found defect (F3 Category 2). It is recorded
   as PARTIAL until MC-1, never as PASS.
3. **T16 (playback) is the single riskiest task**, exactly as T13 was on Android (5 review rounds,
   D85/D87/D88): concurrency, the 5-minute URL TTL, the RTL scrubber exception, and teardown. Land the
   player layer as its own sub-commit.
4. **SKIE/Kotlin version compatibility** (MC-1). A bump's blast radius includes `:androidApp` and the
   249-test `shared` baseline.
5. **SPM binary-target + static framework + SKIE** may need the fallback in T4b's completion gate.
   Decide it at MC-2 and record it.
6. **SwiftUI environment-locale lookup** is the one untested load-bearing localization assumption
   (T7/MC-2), with a pre-decided fallback.
7. **Runtime RTL and the system pop gesture** may not fully flip without relaunch (§ 13) — disclose,
   do not reimplement the gesture.
8. **Backend auth rate limit (10/min)** will bite an XCUITest suite that registers per test, exactly as
   it does on Android. Design the suite to reuse seeded accounts where possible.
9. **No offline cache exists and none may be added** — connectivity detection only.
10. **Do not touch `mobile/androidApp/`, `backend/`, `web/src/`, or any locked doc.** The sanctioned
    outside-`mobile/iosApp/` edits are exactly five: `mobile/shared/build.gradle.kts` +
    `mobile/.gitignore` + `mobile/shared/README.md` (T1); **the T1b Keychain fix under
    `mobile/shared/src/iosMain/.../auth/` plus its new `iosTest` tests** — the one sanctioned
    `iosMain` source change, under F3 Category 2, with `commonMain` still untouched (A6);
    `tools/token-pipeline/generate.js` (T2); the **additive refresh of the `ios` section of
    `design-to-code/shared/platform-contract.json`** (T2 — sanctioned by the widened A8, precedent
    `79fc51d`); and the new `tools/ios-checks/` scripts (T2/T3/T7), which live outside every path A8
    protects.
11. **The Keychain fix cannot be proved by its own happy path.** T1b's whole value is in paths a
    successful round trip never executes, so its evidence is injected-failure tests (MC-1) plus a live
    forced-failure logout (MC-2) — not "I logged in and it remembered me". Two assumptions it rests on
    must be confirmed at MC-1 rather than assumed: that a non-`@Throws` Kotlin exception crossing into
    Swift really does terminate the process (which is why failures are published on a flow instead of
    thrown), and that `@Serializable` works in `iosMain` for the single-item payload (fallback in
    `PHASE_5_IOS_SYSTEM_DESIGN.md § 9.1` K1). If either turns out otherwise, record it — the design
    does not silently change.

## 6. Scope exclusions (confirmed against `product/MVP_SCOPE.md`)

No Instructor/Admin surfaces · no Landing · no Forgot Password · no standalone Progress screen · no
separate Edit Profile route · no password change (no endpoint) · no avatar upload (dead field) · no
real payments · no real AI provider (Phase 6) · no offline downloads or local relational cache · no
push notifications · no deep links · no iPad-specific layout or multi-window · no PiP/background audio
· no App Store/TestFlight/signing-for-distribution work · no CI pipeline for iOS (none exists for any
platform in this repo).

## 7. Reversibility

Almost entirely additive: `mobile/iosApp/` is a new directory, and the edits outside it (T1's
build-script wiring, T1b's `iosMain` fix, T2's generator target) are independently revertible without
touching Android or Web output. **The one non-additive edit is T1b**, which rewrites the internals of
a shipped Phase 3 file (`IosTokenStorage.kt`) and adds a sibling seam file; it changes no `commonMain`
contract, cannot affect Android or Web, and is a single-commit revert — though reverting it restores
the defect, so it would only ever be reverted in favor of a better fix. The hardest-to-reverse
decisions, because everything after them binds to them, are: the `SharedBridge` shape (T5), the
`Route`/`TabRouter` model (T9), and the XcodeGen-as-source-of-truth project decision (T4a). Each is
contained to a single directory and could be replaced in a later phase without touching `shared` or
any other client. **No schema migration, no public API change to `commonMain`, and no cross-service
contract change occurs anywhere in Phase 5** — T1b's only externally visible addition is the
`iosMain`-scoped `KeychainStatus` flow, which no other client can even see.
