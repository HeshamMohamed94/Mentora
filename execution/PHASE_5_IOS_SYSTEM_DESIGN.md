# Phase 5 — iOS: System Design

**Status:** Authoritative system-design document for Phase 5 (iOS / SwiftUI), derived by the
`architect` subagent on 2026-09-18. Companion documents: `execution/PHASE_5_ACCEPTANCE_CRITERIA.md`
(the what) and `execution/PHASE_5_IOS_IMPLEMENTATION_PLAN.md` (the task sequence). To be logged as
`DECISIONS_LOG.md` **D96**. Sources it is grounded in — all read directly, not from memory:
`architecture/KMP_ARCHITECTURE.md`, `architecture/adr/ADR-002`, `ADR-011`,
`architecture/REPOSITORY_STRUCTURE.md § 4`, `execution/PHASE_HANDOFF.md` (Phase 3 + Phase 4 entries),
`execution/INTEGRATION_CONTRACT.md § 12`, `execution/DECISIONS_LOG.md` D69-D95,
`design-to-code/shared/platform-contract.json#/ios`, `design-system/*` (v1.3.2, locked),
`product/SCREEN_INVENTORY.md`, `ux/MOBILE_UX.md`/`NAVIGATION_SPEC.md`, and the real source of
`mobile/shared` and `mobile/androidApp`.

This is an execution planning document, not a locked doc — it may be amended as implementation
surfaces new facts, with the amendment recorded in `DECISIONS_LOG.md`.

---

## 0. Ground truth this design is built on (verified at plan time, not assumed)

| Finding | Where verified | Consequence for this design |
|---|---|---|
| `MentoraSdk` exposes 10 façades; each façade property is a **use-case object** with `operator fun invoke(...)`, not a plain method. | `mobile/shared/.../facade/*.kt` (read) | Swift call sites read `sdk.auth.login.invoke(email:password:)`. § 2 defines a thin Swift wrapper so `Features/` code never writes `.invoke`. |
| 37 use cases total. **32 are `suspend`**, 3 are Flow/StateFlow-returning (`ObserveAuthStateUseCase`, `ObserveLocaleUseCase`, `SendAiTutorMessageUseCase`), 2 are plain synchronous (`ResolveThumbnailUrlUseCase`, `SetThemeUseCase`). Two suspend ones return an **optional** result (`RefreshPlaybackUrlUseCase`, `ReportPlaybackPositionUseCase` — nil means no-op/throttled). | grep over `domain/usecase/**` | § 7 defines exactly three interop patterns, not a per-call improvisation. |
| `ApiResult<out T>` is a generic sealed class whose `Failure` is `ApiResult<Nothing>`. | `data/network/ApiResult.kt` | § 5: the single most important bridging surface; `Failure` carries no `T`, so a Swift generic `Result` conversion must be written once, centrally. |
| Repository `Impl`s are `internal`; façade constructors are `internal`. | `facade/*.kt`, `data/repository/**` | **Swift cannot subclass or fake a façade.** Testability therefore comes from closure/protocol seams in each screen model (§ 3), exactly as Android's ViewModels already do. |
| `commonMain` contains **no ViewModel-shaped code** — only `SessionManager` and `PreferenceStore` expose `StateFlow`. | grep for `ViewModel`/`StateFlow<` over `commonMain` | § 4: iOS owns 100% of its presentation layer. Confirmed, not assumed. |
| `iosMain` exists with 4 files (`IosTokenStorage`, `IosPreferenceStore`, `HttpClientEngineFactory.ios.kt`, `PlatformModule.ios.kt`) but is **not wired into `shared/build.gradle.kts`**; adding `val iosMain by getting {}` fails configuration on Windows. | `shared/build.gradle.kts` comment + D69 | § 6 specifies a host-agnostic wiring form that configures cleanly on both hosts. |
| SKIE 0.9.5 is declared `apply false` and applied only `if (isMacOs)`; iOS framework config (`baseName = "shared"`, `isStatic = true`, `export(kotlinx-datetime)`) already exists. | `shared/build.gradle.kts` | § 6: Phase 5 adds the XCFramework assembly task and the `iosMain` dependencies; it does not redesign the framework config. |
| `ApiEnvironment.iosSimulator()` already exists and returns `http://localhost:8080`. | `mobile/shared/README.md`, `config/ApiEnvironment.kt` | § 19: no new environment preset is needed. |
| `LessonPlaybackController` is an interface `shared` itself never consumes (only a shape test double exists). | grep across `mobile/shared/src` | § 18: iOS mirrors the contract in a Swift-native protocol instead of conforming to the generated Obj-C protocol. |
| The host is Windows; `xcodebuild` is absent. | `which xcodebuild` | Everything Swift in this document is **authored here, verified on a Mac**. Stated per section, never glossed. |

---

## 1. SwiftUI application structure

**Decision: one Xcode project at `mobile/iosApp/` with a single app target (plus a unit-test and a
UI-test target), organized by folder groups — not a set of local SPM packages.**

Options considered:

| Option | Pros | Cons | Verdict |
|---|---|---|---|
| Single app target, folder groups (chosen) | Matches Android's single-`:androidApp`-module precedent (~18k LOC, no module split, no friction); zero package-boundary ceremony; asset catalogs and String Catalogs resolve from the main bundle with no `Bundle.module` indirection; simplest linkage against the shared XCFramework | No compiler-enforced layering between Theme / Components / Features | **Chosen.** Layering is enforced by review + grep rules (A5/A2), the same way Android enforces it. |
| Local SPM packages (`MentoraDesignSystem`, `MentoraComponents`, `MentoraCore`) | Compile-enforced boundaries; faster incremental builds in theory | Generated color/icon assets must move into a package resource bundle (`Bundle.module`), which complicates the generator output path and every call site; the shared XCFramework then needs re-exporting through a package; more moving parts to debug on a host we cannot test on iteratively | Rejected for Phase 5 — cost lands squarely on the part of the work we can least afford to debug remotely. |
| Tuist | Powerful project generation, good for multi-module | Heavier dependency and learning surface than the single generated project needs | Rejected. |

**Decision: the Xcode project is generated from a checked-in XcodeGen `project.yml`; `iosApp.xcodeproj`
is generated output, never hand-edited.** Rationale: a `project.pbxproj` is a large, opaque,
UUID-keyed plist that cannot be authored safely on Windows and cannot be reviewed meaningfully in a
diff. `project.yml` is a ~120-line YAML file that *can* be authored and reviewed here, and it makes
target settings (deployment target, framework search paths, build phases, entitlements) explicit and
diffable. This mirrors the repository's existing discipline that generated artifacts are regenerated,
never edited (ADR-011, `platform-contract.json#/pipeline`). `REPOSITORY_STRUCTURE.md § 4` lists
`iosApp.xcodeproj` in the tree — it will exist; it is simply produced by `xcodegen generate` at the
first Mac checkpoint and committed from there. **This is a newly formalized Phase 5 choice
([NEW-P5]); confirm per `PHASE_5_ACCEPTANCE_CRITERIA.md § 4.4`.**

**Deployment target: iOS 17.0** — enables the `@Observable` macro (§ 3), first-class String Catalog
tooling (§ 12), and current `NavigationStack`/`TabView` behavior. Fallback if a lower floor is
mandated: `ObservableObject` + `@Published`, mechanical and localized to the model layer.

Feature folders mirror Android's `ui/<screen>/` convention conceptually (§ 22 has the full tree), but
the file naming is SwiftUI-idiomatic: `Features/CourseDetails/CourseDetailsView.swift` +
`CourseDetailsModel.swift` — not `Screen`/`ViewModel`, which are Android vocabulary.

---

## 2. SwiftUI to KMP boundary

**All traffic crosses through `Support/SharedBridge/`.** No `Features/` or `Components/` file imports
a Kotlin interop type. The bridge has exactly four responsibilities:

1. **`MentoraClient`** — a thin `struct`/`final class` holding the single `MentoraSdk` and exposing
   `async throws` methods named for the domain action, e.g.
   `func courseDetails(id: String) async throws -> Course`. Each method calls
   `sdk.catalog.getCourseDetails.invoke(courseId: id)`, then funnels the result through (2). This is
   the only place `.invoke` ever appears.
2. **`ApiResult` unwrapping** — one generic function converting `ApiResult<T>` into a Swift
   `throws` return: `Success` yields `data`; `Failure` is rethrown as a Swift `MentoraError` value
   type carrying `code: ApiErrorCode`, `fields: [String: String]?`, `httpStatus: Int32`. `message` is
   carried for logging only and is never rendered (the `ApiResult` kdoc is explicit that `code` is the
   contract). Boxed primitives (`KotlinBoolean`, `KotlinInt`, `KotlinUnit`) are unboxed here and never
   escape the bridge.
3. **Flow adaptation** — `StateFlow`/`Flow` to Swift `AsyncSequence` via SKIE, wrapped in small named
   helpers (`authStates()`, `localeChanges()`, `aiStream(...)`) so screen models never touch the
   generated types directly.
4. **Seam definitions** — because façade constructors are `internal` and therefore un-fakeable from
   Swift (§ 0), every screen model takes its dependencies as **closures or a tiny per-screen Swift
   protocol**, defaulted to the live `MentoraClient` call. This is the same pattern Android already
   uses (its ViewModels take lambda seams such as `observeLocale: () -> StateFlow<AppLocale>`), and it
   is what makes the model layer unit-testable without a mocking framework.

**Deliberate divergence from Android:** Android's ViewModels take `sdk: MentoraSdk` directly and add
lambda seams only where a test needs them. iOS pushes *all* access behind `MentoraClient` instead,
because Swift cannot subclass the façade at all and because unboxing/`invoke`/optional-result handling
would otherwise be copy-pasted into 18 screen models. This is the single largest structural difference
from the Android app, and it is deliberate.

**What the bridge must NOT become:** a parallel repository layer. It adds no caching, no retry, no
error translation beyond code passthrough, no request composition beyond what a use case already does.
If a bridge method ever grows logic, that logic belongs in `shared` (and therefore is out of Phase 5's
scope) or in a screen model.

---

## 3. State-management strategy

**One `@Observable` model class per screen. The model is *constructed* where `@Environment` is
readable, and *owned* by the screen view via `@State`.**

### 3.1 Model construction and ownership — stated once here, before 18 screens repeat it

The naive SwiftUI shape (`@State private var model = CourseDetailsModel()`, with the model's
dependencies "defaulted to the live `MentoraClient` call") **does not compose with the single-SDK
rule and must not be used.** A `@State` property's default initializer runs with **no `@Environment`
access**, so the only way a default argument could reach the live client is a static/global
singleton — which is exactly the hidden second `MentoraSdk` instance that A3 / D76 / F7 exist to
prevent. The pattern is therefore fixed, and it is fixed here rather than re-derived per screen:

1. **Every screen model takes its dependencies explicitly in its initializer** — either
   `MentoraClient` itself, or a narrow per-domain protocol seam over it where a test wants to fake
   fewer methods (§ 2 item 4). **No dependency parameter has a default value that resolves a client.**
   Defaults are allowed only for plain values (ids, an initial state).
2. **The model is constructed where `@Environment` IS readable** — inside the
   `navigationDestination(for: Route.self)` closure for pushed screens, or in the tab-root view for a
   tab root. That closure body sits in a view that already read
   `@Environment(AppEnvironment.self) private var env`, so it can pass `env.client` (and `env`'s
   controllers) straight into the model's `init`.
3. **The destination view owns the instance via `@State`**, initialized through its own
   `init(client:...)` that assigns `_model = State(wrappedValue: CourseDetailsModel(client: client))`.
   SwiftUI then keeps that one instance for the pushed view's lifetime — the desired scope, and the
   reason ownership semantics still matter under `@Observable`.
4. **Tests** construct the model directly with fakes; **previews** construct it with a throwaway
   `AppEnvironment` — which is safe, for the reason recorded in § 8 (`initKoin` uses
   `koinApplication`, not global `startKoin`).

**Explicitly forbidden, and a review-stopper if it appears:** any `MentoraClient.shared` /
`AppEnvironment.shared` static, or any model initializer whose dependency default reaches a client.
That is an A3 violation regardless of whether it appears to work.

**`NavigationStack(path:)` needs a real `Binding<[Route]>`, which a dictionary will not give you.**
A `[Tab: [Route]]` stored inside an `@Observable` class does not hand out a binding through a
subscript for free. `TabRouter` therefore declares **five stored array properties**, one per tab
(`home`, `explore`, `myLearning`, `aiTutor`, `profile`), so `@Bindable var router = env.router`
gives `NavigationStack(path: $router.myLearning)` directly. The only permitted indirection is a small
`func path(for tab: Tab) -> Binding<[Route]>` helper that `switch`es over those five properties.
Cross-tab operations (logout's clear-all, § 10) enumerate all five explicitly — which is also why the
exhaustive-switch form is preferred over a collection that can be iterated incompletely.

### 3.2 State shape and lifetime

- `@Observable final class CourseDetailsModel` holds a single `state` value plus intent methods
  (`load()`, `retry()`, `enrollTapped()`); the view renders a `switch` over the state.
- Screen state is modelled as an explicit enum where the screen has genuinely exclusive phases
  (`case loading / loaded(Course) / failed(MentoraError) / empty`) rather than a bag of booleans —
  this is what makes I3/I4/I5 (loading/error/empty) structurally impossible to forget.
- Models are `@MainActor`-isolated. All `await` calls hop to the background inside the shared Ktor
  client already; the model only ever mutates state on the main actor.
- Long-lived, app-scoped state (the SDK, session, locale, theme) lives in one `AppEnvironment`
  injected through `@Environment`, never re-created per view (§ 8/§ 9).
- Tab-root models survive tab switches (SwiftUI keeps the tab's view tree alive), which reproduces
  exactly the condition D93/D94 documented on Android: a locale change while a backgrounded model
  holds stale, server-localized content. The iOS equivalent of `reloadOnLocaleChange` is
  `observeLocaleChanges(onChange:)` in the bridge, applied **only** to models whose primary content is
  locale-sensitive, and deliberately **not** to models holding in-flight user input (Course Player
  playback state, Quiz answers, Checkout's one-shot transaction). That opt-out list is copied from
  D94's reasoning, not re-derived.

**Deliberate divergence from Android:** no `ViewModelProvider.Factory`, no `viewModelScope`, no
`SavedStateHandle`. Task lifetime is the view's `.task {}`; process-death restoration is not a
first-class iOS concern the way it is on Android, so no state-saving layer is built. Quiz answer
persistence across back-navigation (a locked behavior) is therefore handled by hoisting the draft
answers into a model owned **above** the pushed screen (the tab's stack root), mirroring Android's
`QuizAttemptDraftStore` in intent but not in mechanism.

---

## 4. Shared ViewModel / use-case consumption strategy

**Finding (verified, § 0): `mobile/shared/src/commonMain` contains no ViewModel-shaped code.** A grep
for `ViewModel` returns only kdoc prose; the only `StateFlow`s in `commonMain` are
`SessionManager.authState` and `PreferenceStore.locale`, both intentionally platform-agnostic session/
preference state, not presentation state. `shared` stops at repositories, use cases and façades,
exactly as ADR-002 specifies.

Consequences, stated so no one re-opens them mid-phase:

- iOS owns **100%** of its presentation layer. There is no shared ViewModel to reuse, and Phase 5 must
  not introduce one into `shared` (that would be a new public API — forbidden by acceptance criterion
  A6 and by `PHASE_HANDOFF.md` P3 § 9).
- Any behavior that must not diverge between Android and iOS is already expressed as a **use case**,
  not as a ViewModel: `ResumeCourseUseCase` (resume target resolution), `CompleteLessonUseCase`
  (returns the auto-advance target precisely so the two clients cannot diverge),
  `SubmitQuizUseCase` (all-questions-answered validation), `SetLocaleUseCase` (locale precedence),
  `GetMyLearningUseCase` (fail-whole-call composition). iOS calls these; it never re-derives them.
- The two known client-side compositions Android layered on top (`GetMyLearningWithProgressUseCase`
  and the followed-learning-paths join, G3/G5) are duplicated in Swift by design, with the duplication
  recorded — see `PHASE_5_ACCEPTANCE_CRITERIA.md § 3` item 10 and § 4.2. **The decisive reason not to
  promote them into `shared` now is not cost, it is that promotion cannot actually remove the
  duplicate:** deleting Android's copy would require editing `mobile/androidApp/`, which acceptance
  criterion **A7 forbids for the whole phase**; leaving Android's copy in place while adding a shared
  use case would produce a **third** implementation of the same join rather than removing one. The
  `shared` reopen + 249-test re-baseline is merely the additional cost on top of that.
  See § 17 for the exact behavioral shape the Swift copy must match.

---

## 5. Swift-friendly KMP APIs (what SKIE must deliver)

| Kotlin shape in `shared` | Raw Kotlin/Native to Obj-C result | With SKIE | Where it appears |
|---|---|---|---|
| `suspend fun invoke(...): ApiResult<T>` | completion-handler method, callback-based | Swift `async throws`-compatible `async` method | 32 of 37 use cases |
| `sealed class ApiResult<out T>` with `Failure : ApiResult<Nothing>` | open class hierarchy, `is`-checks, no exhaustiveness | Swift `enum` via `onEnum(of:)`, exhaustive `switch` | every call |
| `StateFlow<AuthState>` / `StateFlow<AppLocale>` | opaque `Kotlinx_coroutines_coreStateFlow` | `AsyncSequence` (`for await`) + a current-value read | session + locale |
| `Flow<AiStreamResult>` | same | `AsyncSequence` | AI Tutor streaming |
| `sealed class AuthState` (Unknown / Authenticated(user) / Unauthenticated) | open classes | Swift enum with associated values | session routing |
| `sealed`/`enum` result types: `AiStreamResult`, `LessonProgressTarget`, `QuizLookupResult`, `LatestAttemptLookupResult`, `LessonCompletionOutcome`, `PlaybackState`, `ApiErrorCode`, `AiQuickAction`, `AppLocale`, `ThemePreference` | awkward bridges | native Swift enums | everywhere |
| Default arguments (`cursor: String? = null, limit: Int? = null`) | must pass every argument | SKIE generates default-argument overloads | paging call sites |
| `kotlinx.datetime.Instant` / `Duration` | already `export`ed by `binaries.framework` | usable Swift types | playback expiry, certificate dates |

Nothing in `shared` needs to change to make these work — Phase 3 Task 15 deliberately shaped the
façade around sealed types and enums for this reason (see `MentoraSdk.kt`'s own kdoc). Phase 5's job is
to *verify* it on a real Mac (acceptance criterion F8), and to document — not silently work around —
any place SKIE's output is unusable.

Known bridging sharp edges to check explicitly at MC-1, each with a pre-decided fallback:

1. **Generic sealed class.** `ApiResult<T>` is generic and `Failure` erases `T` to `Nothing`. If
   SKIE's sealed-enum generation does not handle that cleanly, the fallback is a hand-written
   `unwrap<T>(_ result: ApiResult<T>) throws -> T` in the bridge using `is`-checks — 15 lines, written
   once, invisible to callers. **The bridge exists partly so this fallback costs nothing.**
2. **`ApiResult<Unit>` / `ApiResult<Boolean>`** unbox to `KotlinUnit`/`KotlinBoolean`; the bridge maps
   them to `Void`/`Bool`.
3. **Optional-returning suspend functions** (`RefreshPlaybackUrlUseCase`, `ReportPlaybackPositionUseCase`)
   arrive as Swift optionals; the bridge preserves the nil-means-no-op semantics rather than flattening
   it into an error.

---

## 6. SKIE — current state and the exact change that activates it

**Already true today** (`mobile/shared/build.gradle.kts`, read directly):

- `alias(libs.plugins.skie) apply false` in the `plugins {}` block, so the marker resolves on every
  host and the version is pinned once in `libs.versions.toml` (`skie = "0.9.5"`).
- `if (isMacOs) { apply(plugin = "co.touchlab.skie") }` at the bottom of the file — guarding the
  `apply` call itself is load-bearing, not defensive style (its own comment explains why).
- `iosArm64()` / `iosSimulatorArm64()` are declared, with `binaries.framework { baseName = "shared";
  isStatic = true; export(libs.kotlinx.datetime) }`.
- `kotlin.native.ignoreDisabledTargets=true` in `mobile/gradle.properties` is what lets Gradle
  configure at all on Windows.

**What Phase 5 changes — and nothing more:**

1. Wire the `iosMain` source set **host-agnostically**, so the same build file configures on both
   Windows (where the source set object does not exist) and macOS (where it does):
   `sourceSets.findByName("iosMain")?.dependencies { implementation(libs.ktor.client.darwin);
   implementation(libs.multiplatform.settings) }`. A literal `val iosMain by getting {}` must **not**
   be used — it throws `KotlinSourceSet with name 'iosMain' not found` on this host, a failure mode
   already hit twice in Phase 3 (Task 1 and Task 3). The null-safe form is verifiable here: Gradle
   configuration succeeds and `:shared:assembleDebug` / `:shared:testDebugUnitTest` stay green.
2. Add an XCFramework assembly task so the Swift side has something to link:
   an `XCFramework("shared")` holder over both iOS targets, producing
   `shared/build/XCFrameworks/{debug,release}/shared.xcframework`. Guarded the same way — it
   configures everywhere, links only on macOS.

   **Task names — empirically verified on this host, not assumed.** Applying exactly this change and
   listing the registered tasks yields **`assembleXCFramework`** (the umbrella),
   **`assembleSharedDebugXCFramework`** and **`assembleSharedReleaseXCFramework`**. There is **no
   `assembleSharedXCFramework` task** — an earlier draft of this plan named one; that name is wrong
   and is corrected everywhere it appeared (here, § 22's wrapper script, MC-1's checklist below, and
   Implementation Plan T1). Local development builds `:shared:assembleSharedDebugXCFramework`;
   `:shared:assembleXCFramework` builds both variants. The same run also confirmed that the null-safe
   `sourceSets.findByName("iosMain")?.dependencies { ... }` form and the `XCFramework("shared")`
   holder both **configure cleanly on Windows** with the Native targets disabled — BUILD SUCCESSFUL,
   `:shared:testDebugUnitTest` still 249/249.
3. Nothing else. SKIE's `apply` guard stays exactly as is; on a Mac it fires automatically because
   `isMacOs` is true. **There is no config flag to flip** — the phrase "activate SKIE" means "run this
   build on macOS".

**MC-1 must confirm, in this order:** plugin resolution at 0.9.5 against Kotlin 2.0.21;
`:shared:compileKotlinIosSimulatorArm64`; `:shared:linkDebugFrameworkIosSimulatorArm64`; SKIE
generation actually producing Swift-facing API (inspect the generated interface for `async` methods
and enums); `:shared:assembleSharedDebugXCFramework` (and `:shared:assembleXCFramework` for both
variants) actually producing `shared.xcframework`. If 0.9.5 proves incompatible with the pinned
Kotlin on that host, a version bump is a `DECISIONS_LOG` event with an explicit blast-radius note
(`:androidApp` and the 249-test `shared` baseline both rebuild against any Kotlin change).

**MC-1 must also confirm that the non-exported Koin `Module` type bridges.** `koin-core` is an
`implementation` dependency of `:shared` and is deliberately **not** `export`ed, yet both
`platformModule(): Module` and
`MentoraSdk.Companion.create(environment:platformModule:enableNetworkLogging:)` name
`org.koin.core.module.Module` in their signatures. Kotlin/Native should bridge it **opaquely** — Swift
never constructs or inspects it, it only passes the value straight from `platformModule()` into
`create(...)` — but "should" is not "verified", and this is the kind of thing that only fails at link
time. **Pre-decided fallback:** add `export(libs.koin.core)` to `binaries.framework` — a
`build.gradle.kts`-only change that stays inside A6's sanctioned diff, recorded as its own decision
entry.

---

## 7. Coroutine / Flow interoperability — one pattern per façade method kind

There are exactly three kinds, enumerated from the real signatures (§ 0), plus the synchronous pair:

**(a) Suspend, returning `ApiResult<T>` — 32 use cases.**
Bridge method is `async throws`; the screen model calls it inside `.task {}` or an intent method,
catches `MentoraError`, and maps to its state enum. Cancellation is automatic: SKIE propagates Swift
task cancellation into the Kotlin coroutine, so a view disappearing cancels its in-flight load.

**(b) Suspend, returning an optional `ApiResult<T>?` — 2 use cases.**
`RefreshPlaybackUrlUseCase` returns nil when no refresh is needed yet; `ReportPlaybackPositionUseCase`
returns nil when the heartbeat was throttled. The bridge signature is `async throws -> T?` and callers
treat nil as no-op — never as failure, never as a reason to retry.

**(c) Flow-returning — 3 use cases.**
- `ObserveAuthStateUseCase(): StateFlow<AuthState>` — consumed once, app-wide, by `SessionController`
  (§ 9) in a long-lived `Task`; screens read the resulting Swift-side state, not the flow.
- `ObserveLocaleUseCase(): StateFlow<AppLocale>` — consumed once by `LocaleController` (§ 12); screen
  models subscribe to the Swift-side change signal.
- `SendAiTutorMessageUseCase(content:courseId:lessonContextId:): Flow<AiStreamResult>` — consumed
  per send with `for await chunk in ...`, appending to the in-progress message. Mid-stream failure must
  preserve the partial text (`shared` already distinguishes clean end from failure via `closedCause`,
  D75); a pre-stream failure arrives as `AiStreamResult.PreStreamFailure` — the same shape whether the
  rejection was local validation or a real 403/404/429, so the UI needs no special case.

**(d) Synchronous — 2 use cases.** `ResolveThumbnailUrlUseCase` (pure string composition) and
`SetThemeUseCase` (local write) are called directly, no async ceremony.

**Rule:** a `Flow` is subscribed **once per owner**, never once per view body evaluation. Every
subscription lives in a `Task` whose lifetime is explicitly owned (the app environment, or a view's
`.task {}`), never started from a computed property or `body`.

---

## 8. Dependency wiring

`iosMain`'s `platformModule(): Module` already exists and binds the three things `shared` cannot
construct itself: `TokenStorage` → `IosTokenStorage()`, `PreferenceStore` → `IosPreferenceStore()`,
`HttpClientEngine` → Darwin. The iOS app therefore needs only:
```swift
#if DEBUG
let networkLogging = true
#else
let networkLogging = false
#endif

let sdk = MentoraSdk.companion.create(environment: ApiEnvironment.companion.iosSimulator(),
                                      platformModule: PlatformModule_iosKt.platformModule(),   // iosMain, no-arg
                                      enableNetworkLogging: networkLogging)
let themeReader = IosPreferenceStore()                 // cold-start getTheme() only — see the G1 note below

```

The `#if DEBUG` is a **compile-time guard, not a comment convention**: `enableNetworkLogging` makes
`shared`'s Ktor client log request/response detail, which must not be reachable in a Release build.
A literal `true` with an adjacent "debug builds only" comment is exactly how that ships by accident.
T4a's `project.yml` must therefore define `DEBUG` in `SWIFT_ACTIVE_COMPILATION_CONDITIONS` for the
Debug configuration only.

**G1 (theme read path) — the iOS resolution, which is simpler than Android's.** `UserFacade.setTheme`
is write-only; `shared` exposes no `observeTheme` through the façade, but `PreferenceStore` itself has
a synchronous `getTheme()`. Android solved this by constructing `AndroidPreferenceStore` in the app,
retaining it, and handing that same instance to Koin. **iOS cannot build a Koin `Module` from Swift
(the `module { }` DSL is Kotlin-only), and must not add Kotlin to `shared` to work around it
(acceptance criterion A6).** The resolution:

- Call `iosMain`'s existing no-arg `platformModule()` as-is — it constructs `IosTokenStorage`,
  `IosPreferenceStore` and the Darwin engine internally, exactly as designed.
- The app additionally constructs **one** `IosPreferenceStore()` of its own, used for exactly one
  thing: the synchronous `getTheme()` read at cold start, before the first frame renders.
- This is safe precisely because `IosPreferenceStore` is a thin wrapper over
  `NSUserDefaults.standardUserDefaults` — two instances read the same store — **with one exception**:
  its `locale` `MutableStateFlow` is per-instance in-memory state. The app's own instance therefore
  must **never** be used to read or observe locale. Locale is always read through
  `sdk.user.observeLocale()`, which reads the instance Koin holds.
- Theme *writes* go through `sdk.user.setTheme(...)` (which persists via Koin's instance); the app's
  Swift `ThemeController` updates its own `@Observable` state from the value it just wrote, so the
  cold-start read is the only time the second instance is touched. No staleness window exists.

That distinction is written down here because it is the one piece of this wiring that is subtly wrong
if copied carelessly from the Android precedent.

One `MentoraSdk` per process, held by `AppEnvironment` and injected via `@Environment` — required, not
stylistic (D76's per-instance throttle).

**Positive finding, verified in `mobile/shared/src/commonMain/.../di/InitKoin.kt`: `initKoin` builds a
fresh `koinApplication { ... }`, never the global `startKoin`** — its own kdoc states the reason ("a
library module should not silently claim the global Koin context"). Two consequences, written down
because they are easy to get backwards:

- Constructing more than one `MentoraSdk` does **not** crash, and does not corrupt a global
  container. A3 / D76 / F7 are therefore about **behavioral correctness** — the per-instance
  playback-report throttle, one session/refresh owner, one `AuthState` `StateFlow` — **not** about
  crash-safety. Reviewers should enforce them on that basis, not on a "second instance will blow up"
  belief that is simply false here.
- Consequently a SwiftUI `#Preview` that constructs a **throwaway `AppEnvironment`** (and hence a
  throwaway `MentoraSdk`) is **safe**, and is the sanctioned way to preview a screen that needs the
  environment. The one-instance rule binds the shipped app process, not previews or tests.

**Sanctioned non-façade entry points into `shared` — the complete list (A2's carve-outs).** A2's
"only through `MentoraSdk` and its 10 façade properties" governs **domain** access. These six are
bootstrap/platform entry points, each used exactly once, and are **not** violations. MC-4's audit
checks that the set is exactly this and no larger:

1. `MentoraSdk.Companion.create(environment:platformModule:enableNetworkLogging:)` — once, in
   `AppEnvironment`.
2. `ApiEnvironment.Companion.iosSimulator()` (or `.lan(host:port:)` for a device run, § 19).
3. `platformModule()` — the `iosMain` top-level function, passed straight into `create`.
4. `resolveInitialLocale(systemLocales:)` — a **top-level function** in
   `mobile/shared/.../settings/LocaleResolver.kt`; it is on no façade at all, and H7's first-run
   seeding has no other way to call it.
5. A second standalone `IosPreferenceStore()` for the cold-start `getTheme()` read only, exactly as
   constrained by the G1 note above (never for locale).
6. `KeychainStatus.failures` — the `iosMain` top-level `StateFlow` added by the T1b Keychain
   correctness fix (§ 9.1 K3), observed **once**, in `SessionController`, so a Keychain write/delete
   failure cannot be reported to the user as success (criterion B10). It exists only because A6
   forbids changing `commonMain`'s `TokenStorage` contract, which leaves no in-band channel; it
   carries no domain data — an operation name and a raw `OSStatus`, never a token.

---

## 9. Authentication / session lifecycle

**Cold start sequence (single, ordered, app-scoped — the F3 lesson from Android's `MentoraApplication`):**

1. `AppEnvironment` is constructed at app launch: `MentoraSdk.create(...)`, the theme reader, the
   locale controller, the session controller.
2. Exactly one `Task` runs `await sdk.auth.restoreSession()` (suspend, returns `AuthState`) and then
   `await localeController.seedInitialLocaleIfNeeded()`. Both run from that one task, in that order —
   never from a view's `.task`/`onAppear`, which can fire twice and race.
3. `SessionController` subscribes once to `sdk.auth.observeAuthState()` and mirrors it into an
   `@Observable` Swift enum the view tree reads.
4. When the observed state is `Authenticated(user: nil)` — legitimate after a token-only restore,
   since `TokenStorage` carries no identity — the controller calls `sdk.user.getProfile()` exactly
   once. `GetProfileUseCase` updates `SessionManager`'s current user as a side effect, so the enriched
   state arrives back through the same flow. A failure leaves the state unchanged and does **not**
   loop (D71's reasoning, verified in `AppSessionViewModel`'s kdoc).
5. The root view renders the authenticated tab shell or the unauthenticated surface off that state;
   `AuthState.Unknown` renders a launch/splash state, never a flash of the login screen.

**Keychain.** `IosTokenStorage` (Keychain, `kSecClassGenericPassword`, service
`com.mentora.shared.tokenStorage`) is the storage implementation, wired in by `platformModule()` — but
it does **not** ship into Phase 5 as-is. Reading it turned up a real correctness defect; § 9.1 is the
fix.

### 9.1 `IosTokenStorage` Keychain error handling — a REQUIRED correctness fix

**Status: required, not optional, and not deferred to "if MC-2 shows a problem".** Scheduled as its
own task — `PHASE_5_IOS_IMPLEMENTATION_PLAN.md` **T1b** — with its own acceptance criterion
(`PHASE_5_ACCEPTANCE_CRITERIA.md` **B10**) and an amended **F3**. An earlier draft of this section
treated one successful round trip at MC-2 as sufficient evidence and floated a
`kSecAttrAccessibleAfterFirstUnlock` change; both of those are corrected below.

**The defect (read from the file, not inferred).** `IosTokenStorage` discards the `OSStatus` of every
Keychain mutation: `setKeychainValue` ignores the result of both `SecItemAdd` and `SecItemUpdate`, and
`deleteKeychainValue` ignores `SecItemDelete` entirely. Only `getKeychainValue` inspects a status, and
only to collapse every failure into `null`. Two concrete failure modes follow, neither of which a
happy-path round-trip test can detect:

1. **Mismatched token pair.** `saveTokens` writes the access token and the refresh token as **two
   separate items**. If the first write succeeds and the second fails, the Keychain is left holding a
   *new* access token beside the *old, already-rotated* refresh token. `readTokens()` cannot tell —
   both items are present — so `restoreSession()` reports `Authenticated`, the first 401 triggers a
   refresh with a dead refresh token, and the user is signed out mid-session with no explanation.
2. **Logout that only appears to succeed.** A failed `SecItemDelete` is invisible, so
   `SessionManager.onSignedOut()` publishes `Unauthenticated` and the UI shows the logged-out surface —
   while the tokens are still in the Keychain and the session returns on the next cold start. That is a
   security-relevant false success, not a cosmetic bug.

**K1 — one Keychain item for the pair, not two.**

| Option | Verdict |
|---|---|
| (a) Keep the two-item layout, check both statuses, and roll back (delete both) when the second write fails | **Rejected as primary.** The rollback is itself a Keychain call that can fail; when it does, the mismatched pair survives anyway. The failure mode is compensated for, never closed. |
| (b) Store the pair as **one** `kSecClassGenericPassword` item (account `authTokens`) whose value is a small JSON object holding both tokens | **Chosen.** One mutation per `saveTokens`, so a partial pair is structurally impossible rather than cleaned up afterwards. It also matches `AndroidTokenStorage`, which already persists a single blob (`AuthTokensPayload`) for exactly this reason, and `kotlinx.serialization` is already applied to this module. |

There is **no migration concern**: `iosMain` has never been compiled, shipped or run, so no device
anywhere holds a Phase-3-shaped pair of items. If `@Serializable` in `iosMain` misbehaves at MC-1, fall
back to (a) **and disclose the residual window** — do not silently keep the two-item layout without it.

**K2 — every `OSStatus` is inspected, and `errSecItemNotFound` is not an error.**

- `errSecSuccess` -> success.
- `errSecItemNotFound` -> the expected "there is no stored session" outcome: `readTokens()` returns
  `null` and `clearTokens()` treats it as a completed delete. It is never reported as a failure.
- **Any other status** (`errSecMissingEntitlement`, `errSecInteractionNotAllowed`, `errSecAuthFailed`,
  `errSecDuplicateItem`, ...) is a genuine failure carrying the raw `OSStatus`. A failure value never
  contains a token (`AUTH_SECURITY.md § 4`'s never-log-a-token rule applies to it too).

**K3 — how a failure reaches the user, given that `IosTokenStorage` cannot throw across the Swift
boundary.** This constraint shapes the whole fix, so it is stated here rather than discovered on the
Mac: `TokenStorage.saveTokens`/`clearTokens` are plain `commonMain` `suspend` functions with **no
`@Throws`**, and Kotlin/Native terminates the process when a non-`@Throws` exception crosses into
Swift. Adding `@Throws` (or a `Result`-returning signature) would be a `commonMain` public-API change,
which **A6 forbids for the entire phase**. So "just throw" — what the Android implementation does — is
not available on iOS. The chosen shape instead:

- **`saveTokens`** — one write. On genuine failure: best-effort delete of the item (so nothing stale or
  mismatched survives), then publish the failure. Never leaves a half-written pair.
- **`clearTokens`** — `SecItemDelete`; on genuine failure retry once; if it still fails, **overwrite**
  the item with a tombstone value via `SecItemUpdate`, which `readTokens()` maps to `null` — two
  independent ways to neutralize a stored session. Only if both fail is the failure published.
- **`readTokens`** — `errSecItemNotFound`, a malformed payload, or the tombstone -> `null`; any other
  genuine failure -> `null` **plus** a published failure, so "silently not logged in after relaunch"
  becomes an observable event instead of a mystery.
- **Publication** — an `iosMain` top-level `object KeychainStatus` exposing
  `val failures: StateFlow<KeychainFailure?>` (operation + raw `OSStatus`, never a token).
  `SessionController` (T4b) subscribes **once**, and the UI surfaces it: a logout whose deletion could
  not be neutralized must not present itself as a completed logout, and a login whose tokens could not
  be persisted must say the session will not survive relaunch. This adds **one** entry to A2's
  otherwise-exhaustive list of sanctioned non-façade entry points (now six) — a disclosed, deliberate
  consequence of A6 leaving no in-band channel.

**K4 — `kSecAttrAccessible`: set it explicitly to `kSecAttrAccessibleWhenUnlockedThisDeviceOnly`.**
The earlier `kSecAttrAccessibleAfterFirstUnlock` suggestion is **withdrawn — it was backwards.**
Omitting the attribute (today's code) already defaults to the **more** restrictive
`kSecAttrAccessibleWhenUnlocked`, so `AfterFirstUnlock` would have *weakened* locked-device protection
in exchange for nothing. The correct change keeps the same unlock class and adds non-migratability:

- **`WhenUnlocked...`** — Mentora's session tokens are only ever used by a foreground app. Phase 5
  ships no background refresh, no background audio/PiP, no push and no background `URLSession`
  (`PHASE_5_IOS_IMPLEMENTATION_PLAN.md § 6` scopes all of them out), so nothing needs Keychain access
  while the device is locked.
- **`...ThisDeviceOnly`** — keeps session tokens out of iCloud Keychain sync and out of encrypted
  backups restored onto a different device. A bearer/refresh pair is device-local session state, not a
  credential a user expects to roam.

Net effect versus today: same unlock requirement, strictly less exposure. It is set once, in the base
query every call starts from, so add/update/read all agree.

**K5 — an accessibility attribute has nothing to do with app deletion, and Phase 5 does not implement
reinstall purging.** The earlier claim that `AfterFirstUnlock` would deliver "survives app deletion"
cleanup was wrong in both directions: no `kSecAttrAccessible` value purges anything on uninstall, and
Keychain items generally **do** survive app deletion regardless of accessibility class. Real reinstall
cleanup requires the app to detect first-launch-after-install — a flag in `UserDefaults`, which *is*
removed on uninstall — and to call `clearTokens()` proactively at cold start when the flag is absent.
**Phase 5 deliberately does not do that.** Consequence, stated plainly so it is not left ambiguous:
**a fresh install on a device that previously had Mentora may resume the previous session.** That is
accepted at this project's scope — a local, single-developer portfolio demo with no production
deployment and no real user data (**ADR-012**) — where an install-marker purge would add a cold-start
code path with its own failure modes for no demo-visible benefit. It is disclosed in the iOS README's
Known limitations. If it is ever wanted, it is an app-side (`AppEnvironment`) change, not another
`iosMain` change.

**K6 — the test seam, and how it changes the file's internal shape.** The raw `SecItem*` C APIs cannot
be mocked, so every failure path above is untestable as the file is written today. T1b therefore
introduces a thin seam **inside `iosMain`**: an `internal interface KeychainStore` with
`add`/`update`/`delete`/`copyMatching`, each returning the raw `OSStatus`; a production implementation
(`SecurityFrameworkKeychain`) that is the only code calling `platform.Security`; and
`IosTokenStorage(keychain: KeychainStore = SecurityFrameworkKeychain())` taking it as a **defaulted**
constructor parameter, so `PlatformModule.ios.kt`'s `IosTokenStorage()` call site is unchanged. The
seam is `internal`, so it never appears in the generated Swift API (A2 is unaffected), and `iosTest`
sees it through the normal test-compilation association. **This is a real change to the internal shape
of a Phase-3 file** — stated rather than glossed — and it is exactly what F3's carve-out now covers.

**The F3 carve-out, stated precisely.** F3 ("the existing `iosMain` actuals are wired in and used, not
rewritten") exists to stop Phase 5 from *redesigning* working Phase 3 code it cannot compile, and as
written it allows exactly one exception: "a defect found on a real Mac". That is too narrow — it has no
way to describe a defect found by **reading** the code, which is what happened here. Phase 5 recognizes
two categories and only two:

1. **Found on the Mac, after compiling or running it.** Requires the Mac, is fixed there, carries its
   own `DECISIONS_LOG.md` entry. Unchanged from F3 as originally written.
2. **Found by code review before Mac access.** May be **authored on Windows as an unverified Kotlin
   change**, is tagged W-auth / M-verify and can never be reported as PASS on Windows evidence, carries
   its own `DECISIONS_LOG.md` entry, and is verified for real at the first Mac checkpoint that can
   exercise it — **MC-1** for the failure-injection unit tests (`:shared:iosSimulatorArm64Test`),
   **MC-2** for the live round trip and the live logout-failure path.

**T1b is a Category 2 fix, and it is the only one.** Nothing else in `iosMain` is touched:
`IosPreferenceStore`, `HttpClientEngineFactory.ios.kt` and `PlatformModule.ios.kt` stay exactly as
Phase 3 left them, and `commonMain` is not edited at all (A6).

### 9.2 Session expiry

Entirely `shared`'s job (401 → single-flight refresh → retry; both
`AUTH_TOKEN_INVALID` and `AUTH_TOKEN_EXPIRED` trigger refresh, D77). iOS writes no interceptor, no
retry, no refresh call. When refresh genuinely fails, `shared` clears storage and emits
`Unauthenticated`; the root view routes there, and the pending intent (the destination the user was
trying to reach) is preserved in the same value-typed form the guest auth gate uses (§ 10).

---

## 10. Navigation architecture

- **Root:** `TabView` with 5 tabs in the locked order (Home, Explore, My Learning, AI Tutor, Profile),
  each tab containing its own `NavigationStack(path:)`.
- **Routes:** one `enum Route: Hashable` carrying **ids only** (`case courseDetails(courseId: String)`,
  `case coursePlayer(courseId: String, lessonId: String?)`, …), mirroring Android's `Destinations.kt`
  in intent. Never a model object in a path — that is what guarantees D7 (no stale destination state).
- **Per-tab paths:** a `@Observable TabRouter` holds **five stored `[Route]` properties**, one per
  tab — not a `[Tab: [Route]]` dictionary, which cannot be projected into the
  `Binding<[Route]>` that `NavigationStack(path:)` requires (see § 3.1 for the exact shape and the
  `path(for:)` helper). Because each tab's stack is a separate array, independent back stacks come
  for free — SwiftUI's own structure does what Android needed nested graphs and
  `saveState`/`restoreState` gymnastics for.
  **This is a deliberate divergence: do not port Android's tab-graph machinery; it is solving a
  Navigation-Compose problem that does not exist here.**
- **Tap-active-tab-pops-to-root:** intercept the `TabView` selection binding — if the new selection
  equals the current one, clear that tab's path.
- **Shared deep screens** (Course Details, Course Player, Quiz, Quiz Results, Learning Path Details)
  are pushed onto the **current** tab's path, which structurally cannot mis-anchor the way Phase 4's
  `90c8af3` defect did. The XCUITest for that exact chain (D4/J4) still exists, because the *product*
  behavior — "Continue works no matter how you got here" — is what is being protected, not the
  mechanism.
- **Hiding the tab bar** on Course Player and Quiz: `.toolbar(.hidden, for: .tabBar)` on those
  destinations.
- **Pre-decided fallback for D5 (decide it here, not at MC-3).** D5 is *locked* behavior —
  `navigation.json`'s focused-learning shell says mobile **fully hides** the tab bar, it does not
  collapse it — and `.toolbar(.hidden, for: .tabBar)` is a known regression area across iOS runtime
  versions **independent of the deployment target** (the Simulator runs whatever iOS the eventual
  Mac's Xcode ships, which need not be 17.0). If it proves unreliable at MC-3 — the bar reappears on
  push/pop, animates back in, or leaves a dead safe-area inset — **present the focused-learning shell
  via `fullScreenCover` instead of a push**. The cover hosts its own `NavigationStack` for
  Player → Quiz → Quiz Results, which structurally has no tab bar at all. The originating tab is
  recorded when the cover is presented, and on dismissal **any pending `Route` is replayed onto that
  originating tab's stack**, so D4 (terminal actions live on every reachable path) and D8 (Purchase
  Success back-navigates to My Learning) still hold. Taking this fallback is a `DECISIONS_LOG` entry,
  never a silent substitution. Applies to T16/T17.
- **Auth gate:** a guest-triggered gated action stores the pending `Route` on `AppEnvironment`,
  presents Login (modally or pushed — a sheet keeps the origin stack intact and is the iOS-idiomatic
  choice), and on success pops the gate and replays the stored route.
- **The pending `Route` is cleared whenever the login sheet is dismissed without authenticating** —
  including **swipe-to-dismiss**, which fires no button handler. Implement the clear in the sheet's
  `onDismiss:` (checking that auth did not succeed), never only in a Cancel action, so every
  dismissal path is covered. Without this, a user who swipes the sheet away and logs in later for an
  unrelated reason is teleported into a stale intent.
- **Logout clears every tab's stack, not just the active one.** On observing
  `AuthState.Unauthenticated`, `TabRouter` resets **all five** paths and the pending `Route`, and
  selection returns to Home. D7 already tests this behavior; it is stated here as a requirement so it
  is not left as a test-only expectation. This is a second reason `TabRouter` stores five explicit
  properties (§ 3.1) rather than a collection that can be iterated incompletely.
- **Deep links:** not in Phase 5 scope (no product requirement exists). The `Route` enum is
  `Codable`-friendly so a future deep-link phase can add a URL mapping without redesigning navigation —
  noted as a non-goal, not built.

---

## 11. API / backend integration

`MentoraSdk` handles **all** of it. The iOS app makes **zero** direct network calls, with exactly one
narrow exception that is not an API call: `AsyncImage` loading an image URL that
`sdk.media.resolveThumbnailUrl(...)` already produced (public thumbnail route, no auth header, by
design — `INTEGRATION_CONTRACT.md § 12` item 10) and the AVPlayer loading the already-resolved,
already-tokenized stream URL (§ 18). Both consume a URL `shared` composed; neither constructs one.

Everything in `INTEGRATION_CONTRACT.md § 12` is already solved inside `shared` and must not be
re-derived on iOS: the literal `X-Requested-With: mentora-web` CSRF value, the deliberate no-cookie-jar
rule, the 429-without-envelope synthesis, the `AUTH_TOKEN_INVALID`/`_EXPIRED` refresh-trigger
adaptation, `?language=` threading on exactly four read endpoints, the unpaginated
`categories`/`learning-paths` lists, the AI Tutor non-standard envelope plus raw chunked stream, the
permanently absent lesson `duration`, opaque certificate ids, and the media token-in-URL pattern.

The only wire-shaped decision iOS makes is *which* `ApiEnvironment` preset to construct (§ 19).

---

## 12. Localization architecture

**Decision: String Catalog (`Localizable.xcstrings`) with `en` + `ar`, plus a root-level
environment override for the active locale — not `AppleLanguages` + relaunch, and not a Bundle
swizzle.**

Mechanism:

- `LocaleController` (`@Observable`) subscribes once to `sdk.user.observeLocale()` and publishes the
  active `AppLocale`.
- The root view applies `.environment(\.locale, activeLocale.foundationLocale)` and
  `.environment(\.layoutDirection, activeLocale.layoutDirection)`. SwiftUI resolves
  `Text("some.key")` against the environment locale, so every string and the whole layout flip
  together, immediately, with no restart — which is what `SCREEN_INVENTORY.md § 17` locks
  ("applies immediately… flips layout direction… no app restart").
- `activeLocale.foundationLocale` for Arabic is **`Locale(identifier: "ar-u-nu-latn")`** so every
  `FormatStyle`/`DateFormatter` derived from the environment produces **Western numerals**, which
  `LOCALIZATION.md § 8` locks. The language subtag is still `ar`, so catalog lookup is unaffected.
- **Strings resolved outside the view tree** (model-side error copy, AI Tutor quick-action prompt
  text) go through one accessor — `MentoraStrings.text(_ key:, locale:)` wrapping
  `String(localized:table:bundle:locale:)` — that **requires** the active locale as an argument.
  This is the structural fix for the exact class of bug D93 found on Android, where a quick-action's
  visible label was Arabic while the prompt actually sent to the model was English because it was
  resolved off a context outside the composition. On iOS, making `locale` a required parameter makes
  that bug unrepresentable rather than merely unlikely.
- **Verification obligation (MC-2):** confirm that SwiftUI's `Text` localization honors
  `\.locale` on the chosen deployment target. If it does not behave as expected for some surface, the
  pre-decided fallback is an explicit bundle lookup — `Bundle(path: Bundle.main.path(forResource:
  localeCode, ofType: "lproj")!)` injected through the same environment — funnelled through the same
  `MentoraStrings` accessor, so the change is contained to one file. **This is the single riskiest
  untested assumption in the localization design and is called out as such.**
- Catalog parity (keys + format specifiers) is enforced by a test, ported from Android's
  `StringsParityTest` (D93 finding 8). The source key set is Android's 279 EN / 278 AR
  (`app_name` is intentionally EN-only); iOS ports key-for-key so the two clients cannot drift in copy.

**Deliberate divergence from Android:** Android needed a `ContextWrapper` subclass overriding
`getResources()`/`getAssets()` plus a custom `CompositionLocal` to cross `Dialog`/`Popup`/
`ModalBottomSheet` sub-composition boundaries (D93). SwiftUI's environment already propagates into
sheets and alerts presented from the same hierarchy, so none of that machinery is ported. Sheets
presented from a **detached** context (rare, and avoidable) are the only case to re-check at MC-3.

---

## 13. RTL handling

- Direction is set once at the root from the active locale (`\.layoutDirection`), exactly as
  `LOCALIZATION.md § 1` requires ("components never read the locale directly to decide their own
  mirroring — they respond to the inherited layout direction").
- Every layout uses leading/trailing (`.padding(.leading,)`, `HStack` alignments,
  `.multilineTextAlignment(.leading)`) — never `.left`/`.right`. A lint-style grep is part of the QA
  sweep, mirroring web's `lint:logical-properties` gate.
- **Icons:** `.flipsForRightToLeftLayoutDirection(true)` on exactly the directional glyphs, encoded
  as data next to the icon enum, never decided per call site. **Which glyphs, precisely — the two
  data sources do not correspond, so this is resolved here rather than at T3.**
  `design-tokens.json#/icon/directional` lists 15 `mirrorInRtl` + 29 `neverMirror` names in
  **Material-Symbols snake_case** (`chevron_left`, `trending_flat`, ...), while the actually shipped
  set is the **42 hand-drawn camelCase glyphs** Web and Android carry (`dashboard`, `myLearning`,
  `courseAnalytics`, ...) — the two lists barely overlap, and reconciling them glyph-by-glyph is not
  possible. Android already resolved this pragmatically and iOS **ports that resolution rather than
  re-deriving it**: `mobile/androidApp/.../ui/components/MentoraIcons.kt` mirrors exactly **two**
  icons — `arrowForward` and `arrowBack` — via `buildIcon(..., autoMirror = true)`. iOS therefore
  applies `.flipsForRightToLeftLayoutDirection(true)` to exactly those two names and to nothing else.
  `design-tokens.json#/icon/directional` is treated as the **semantic rule** ("glyphs that encode a
  left/right direction mirror; glyphs that do not, never mirror"), which Android's two-icon set
  already satisfies after name translation — **not** as a literal second inventory to reconcile.
- **Locked exceptions that must NOT mirror:** the video scrubber stays LTR in every locale
  (`LOCALIZATION.md §§ 2, 8`) — implemented by pinning that subtree's `layoutDirection` back to
  `.leftToRight`; numerals stay Western (§ 12); the wordmark never mirrors.
- **Honest caveat to verify at MC-3:** the *system* interactive pop gesture and some UIKit-backed
  chrome derive their direction from the app's effective UI layout direction, which a runtime
  environment override may not retroactively change. If the edge-swipe-back gesture stays on the
  physical-left edge after an in-app switch to Arabic, that is a disclosed limitation (D9), not
  something to paper over by reimplementing the gesture. `UIView.appearance().semanticContentAttribute`
  is the only lever available, and it is not retroactive for an already-built view tree.

---

## 14. Light / Dark theme architecture

- Colors live in **`MentoraColors.xcassets`**, one Color Set per semantic dot-path, with an
  *Any Appearance* (light) and a *Dark Appearance* value — exactly as
  `platform-contract.json#/ios/colorMapping` specifies. SwiftUI resolves the appearance automatically;
  **no view ever branches on `colorScheme`** (the contract calls that out explicitly).
- The in-app theme override (Settings: Light / Dark / System) is applied by setting
  `.preferredColorScheme(...)` at the root from `ThemeController`, whose value comes from
  `PreferenceStore.getTheme()` at cold start (§ 8) and from `sdk.user.setTheme(...)` writes
  thereafter. System mode passes `nil`.
- Status-bar/edge-to-edge contrast: the Phase 4 acceptance audit found illegible status-bar icons
  because the app never told the system what its background luminance was. The iOS analogue is that
  `.preferredColorScheme` must be applied at the true root (covering the status bar area and any
  full-screen cover), and sheets/covers must inherit it. This is an explicit MC-3 check, not an
  assumption — it is a real defect class this project has already shipped once.
- **Dark-mode shadows — decided, not left open.** `design-tokens.json#/elevation/*/ios` carries a
  **single** radius/y/opacity triple per step (it is the light value), while the same file's
  `elevation.darkModeNote` states the rule: shadow color stays black, opacity is reduced ~30% in
  dark because dark surfaces already read as elevated via `surface.elevated` tone, and
  "components never branch on theme to decide shadow strength, the token value already differs per
  theme." **Decision: the generator applies the reduction; the view layer never does.** For each
  elevation step N, T2 emits a Color Set `mentoraShadowElevation<N>` whose *Any Appearance* is
  `themes/theme-light.json#/elevationShadowBase` at alpha = `elevation.N.ios.opacity` and whose
  *Dark Appearance* is `themes/theme-dark.json#/elevationShadowBase` at alpha =
  `opacity x 0.7`. `.mentoraElevation(_:)` then reads that one colorset plus the step's radius/y, so
  the appearance switch happens in the asset catalog and **no view reads `colorScheme`** (§ 14's own
  rule). The 0.7 factor is the locked token file's documented rule applied once in the generator — it
  is not a hand-written token value, so G2 holds. The 1 pt border is applied in **both** appearances
  and is what actually carries elevation in dark, per "borders over shadow".
- Theme-invariant colors (e.g. the artwork scrim and the text that sits on it) must be pinned on
  **both** sides, not one: the exact dark-mode defect fixed in `8feacad` was a theme-invariant scrim
  paired with a theme-flipping foreground. The generated token layer carries both values, so the iOS
  component kit must consume the pinned pair, not re-derive a foreground from the current scheme.

---

## 15. Design-system token mapping to SwiftUI

**Source of authority — stated before anything else, because two files disagree.** The authoritative
inputs for what the iOS generator emits are **`design-system/design-tokens.json` plus
`design-system/themes/theme-{light,dark}.json`** — the same inputs Web and Android already generate
from. `design-to-code/shared/platform-contract.json#/ios`'s own enumerations are **descriptive, and
in places stale**: that section still declares `status: "NOT IMPLEMENTED — mapping only"`, names a
`wouldGenerateInto` path (`ios/Mentora/Design/MentoraTokens.swift`) that does not match this plan's
layout, points `wouldConsume` at `design-to-code/shared/*.json` rather than the generator this plan
actually extends (`tools/token-pipeline/generate.js`), and enumerates a semantic color set that does
not line up leaf-for-leaf with the token files. Concretely, verified at plan time:
`design-tokens.json#/color/semantic/light` carries **51 leaves = 46 colors + 5 state opacities**, and
`themes/theme-light.json#/color` carries exactly those **46** color paths. **So T2 emits 46
colorsets; the 5 state opacities are numbers, not colorsets.** Where the contract file and the token
files disagree, **the token files win**. T2 additively refreshes `platform-contract.json`'s `ios`
section to match as-built reality — the same additive move Phase 4 commit `79fc51d` made on the
`android` section's `readinessNotes` — sanctioned by acceptance criterion A8.

**Generation, not hand-authoring.** `tools/token-pipeline/generate.js` gains a **fifth output target**
(web CSS, web Tailwind theme, web TS constants, Android Kotlin — now iOS), following the same plain-Node,
zero-dependency precedent (D35/ADR-011) and the same GENERATED — DO NOT EDIT header discipline as
`MentoraTokens.kt`. It emits, into `mobile/iosApp/iosApp/Theme/` (the directory name
`REPOSITORY_STRUCTURE.md § 4` already reserves, and the one its § 5 generator-output sentence already
points at — see § 22):

1. **`MentoraTokens.swift`** — `MentoraSpacing`, `MentoraRadius` (including **`radius.xlarge` = 24**,
   the dialog / bottom-sheet step), `MentoraElevation` (radius/y per step from
   `design-tokens.json#/elevation/*/ios`; the opacity lands in the shadow colorsets, § 14),
   `MentoraIconSize`, `MentoraTypographyMetrics` (size / lineHeight / weight / tracking per scale
   step — **metrics, not `Font` values**, see § 15.1), `MentoraStateOpacity`, and
   `MentoraTouchTarget.min = 44` (from `touchTarget.ios_pt` — **not** Android's 48).
   **T2 implementation note (plan-time finding, resolve while implementing):** `stateOpacity` is
   **per-theme** in `themes/*.json` (`pressed`/`focus`/`disabledContainer` are 0.12 light vs 0.16
   dark), so `MentoraStateOpacity` cannot be a single flat constant set. Resolve it the same way the
   generator already resolves per-theme values for Android rather than inventing a third answer, and
   record what was done.
2. **`MentoraColors.xcassets/`** — one `<name>.colorset/Contents.json` per semantic **color**
   dot-path (46) with `any` + `dark` appearances, named `mentora<PascalCase>` per the contract's
   `assetNamingConvention`; plus the per-step `mentoraShadowElevation<N>` sets specified in § 14;
   plus a generated `Color+Mentora.swift` extension exposing `Color.mentoraBrandPrimary` etc.
3. Hand-authored (behavior, not values — T6's scope):
   `MentoraTypography.swift` (the typography **`ViewModifier`** of § 15.1, including the Arabic
   letter-spacing-zero and +10% body line-height rules from `LOCALIZATION.md § 4`),
   `MentoraShape.swift` (the contract's `shapeMapping`, including the top-corners-only
   `UnevenRoundedRectangle` at 24 pt for sheets/dialogs), and `MentoraElevation.swift`'s
   `.mentoraElevation(_:)` modifier (shadow colorset + 1 pt border, borders-over-shadow).

**Windows-verifiable** (this is one of the few genuinely verifiable iOS-side tasks here): the
generator runs under Node on this host; asset catalogs are plain directories of JSON, so structure,
naming, appearance coverage, and value correctness can all be asserted here. Only the *rendering* needs
a Mac. A drift test (Android already has one for `MentoraTokens.kt`) asserts the generated Swift/asset
values still match `design-tokens.json`.

**Fonts:** no font file is bundled. `platform-mapping.md § 2`'s "Font family binding" table is
explicit for iOS — **SF Pro via `Font.system`/Dynamic Type APIs, "do not bundle"** — which is also
what makes SF Arabic substitution automatic (§ 12/H8). This is the one place iOS is strictly better
off than Android, which still carries a sans-serif placeholder for Arabic. (`platform-mapping.md § 6`
is Motion, not Typography; an earlier draft of this document cited it here in error.)

### 15.1 Typography is a `ViewModifier`, not a `Font` value — CORRECTION

**This corrects the prose in `design-to-code/shared/platform-contract.json#/ios/typographyMapping`,
which is unimplementable as written and which an earlier draft of this plan hardened into acceptance
criterion G3. It is a correction to that file's prose, not a new design decision:**
`design-system/platform-mapping.md § 2` — a **LOCKED** design-system file, and therefore
authoritative over the design-to-code JSON's prose — already specifies the correct shape, a **view
modifier**: `.mentoraFont(.displayLarge)`, `.mentoraFont(.h1)` ... `.mentoraFont(.caption)`.
Android's `MentoraTheme.typography.h1` is the same convention expressed on the other platform.

**Why the `Font.mentora<Name>` shape cannot deliver what G3 / I1 / H8 require:**

1. **`relativeTo:` exists only on `Font.custom(_:size:relativeTo:)`.** There is no
   `Font.system(size:relativeTo:)`. H8 requires that **no font be bundled** (that is exactly what
   makes Apple substitute SF Arabic automatically for Arabic runs), so `Font.custom` is unavailable —
   and a static `Font` extension built on `Font.system(size:weight:)` therefore yields a
   **fixed-size font that does not scale with Dynamic Type at all**, silently failing I1 on every
   screen.
2. **A `Font` cannot carry tracking or line height.** `.tracking(_:)` and `.lineSpacing(_:)` are
   `Text`/`View` modifiers, not `Font` properties — so `letterSpacing` and `lineHeight` from
   `design-tokens.json#/typography/scale`, and the Arabic overrides, have nowhere to live in a
   `Font`-shaped API.
3. **`UIFontMetrics` is not the workaround.** It reads the trait collection directly, so it does not
   respond to `.dynamicTypeSize(...)` injected in a SwiftUI `#Preview` or an XCTest — which is
   precisely how this plan's own Dynamic Type harness (T6, T8, T22) drives its assertions.

**The shape that does work.** One `ViewModifier` per scale step, behind a single
`View.mentoraFont(_:)` entry point over a `MentoraTextStyle` enum. The modifier composes:

- **size** — `@ScaledMetric(relativeTo: <anchor>) private var size: CGFloat = <token fontSize>`.
  `@ScaledMetric` honors both the system setting **and** an injected `.dynamicTypeSize(...)`, so
  previews and unit tests can assert AX-size behavior;
- **font** — `Font.system(size: size, weight: <token fontWeight>)`: system font, so SF Arabic
  substitution stays automatic (H8);
- **tracking** — `.tracking(<token letterSpacing>)`, applied **unscaled** (see the note under the
  formula);
- **line height** — `.lineSpacing(...)` computed by the ratio formula specified immediately below.
  The naive `lineHeight - size` form used in an earlier draft is **wrong and is withdrawn**: it
  subtracts an *unscaled* token value from a *scaled* one, so at accessibility sizes the scaled size
  meets and then exceeds the fixed `lineHeight` and the computed spacing falls to zero and then goes
  negative — taking the claimed line-height ratio, and the Arabic +10% rule built on top of it, with
  it. No fixed-height container either (`CONTENT_RESILIENCE.md § 8`);
- **Arabic overrides (`LOCALIZATION.md § 4`)** — the same modifier reads `@Environment(.locale)` and,
  for Arabic, forces tracking to **0** and applies the **+10%** line-height bump to the *ratio* (and
  therefore to the *scaled* target, not to the original token value), body steps only. Because it is
  one modifier, that rule lives in exactly one place instead of being repeated 12 times.

**The line-height formula — specified exactly here so nobody re-derives it on the Mac.**

Two facts drive it. First, SwiftUI's `.lineSpacing(_:)` adds **extra** space *on top of* the font's own
natural line height; it is not a way to set an absolute total line height, so "target minus point size"
is not even the right subtraction. Second, `@ScaledMetric` scales one number — if the target line
height is not scaled by the same factor, the two quantities drift apart as Dynamic Type grows. The
modifier therefore works in a **dimensionless ratio**, which is scale-invariant by construction:

```swift
// token = the generated (unscaled) MentoraTypographyMetrics entry for this scale step
@ScaledMetric(relativeTo: anchor) private var scaledSize: CGFloat = token.fontSize  // the ONLY scaled value

private let naturalLineHeightFactor: CGFloat = 1.2   // SF Pro renders at ~1.2 x point size

var ratio: CGFloat {                       // dimensionless: 1.167 (display.large) ... 1.5 (body.medium)
    let base = token.lineHeight / token.fontSize
    return (isArabic && isBodyStep) ? base * 1.10 : base
}
var scaledTargetLineHeight: CGFloat { scaledSize * ratio }
var lineSpacing: CGFloat {
    max(0, scaledTargetLineHeight - scaledSize * naturalLineHeightFactor)
    //  == scaledSize * max(0, ratio - naturalLineHeightFactor)
}
```

- **It can never be negative.** The result is an explicit `max(0, ...)` over a product of a positive
  scaled size and a constant; there is no input, and no Dynamic Type step, on which it can go below 0.
- **Both quantities scale together.** Target and natural line height are both multiples of the same
  single `@ScaledMetric` value, so the intended leading ratio holds identically at `.large` and at
  `.accessibility5`.
- **The Arabic +10% applies to the scaled target**, because it multiplies the dimensionless ratio — not
  the original token value.
- **Where it clamps, and why that is correct.** `display.large` (56/48 = 1.167) and `display.medium`
  (48/40 = 1.20) ask for leading *tighter* than the system font's own; `.lineSpacing` is additive and
  cannot tighten, so those two steps land at `0` — the closest legal value — and render at the font's
  natural leading. Every other step gets positive extra spacing. This is a bounded, deliberate
  deviation on two display steps, compared against Android's rendering at MC-3 rather than assumed away.
- **`naturalLineHeightFactor` is one named constant**, not a number sprinkled through the file. If the
  MC-2 token gallery shows drift it may be replaced by a measured
  `UIFont.systemFont(ofSize: scaledSize, weight: ...).lineHeight / scaledSize` — same formula, same
  non-negativity guarantee, one line changed. That is **not** `UIFontMetrics`, which stays banned for
  the separate reason in point 3 above (it ignores an injected `.dynamicTypeSize`).
- **Tracking is deliberately not scaled.** Token `letterSpacing` is intentionally negative on the two
  display steps (-0.25); scaling it would amplify that negative value at AX sizes and is the most
  plausible route to glyph overlap. It stays at its token point value, and is forced to `0` for Arabic.

**Naming — one convention, chosen once.** The case names are `platform-mapping.md § 2`'s (which
Android also uses), **not** the JSON prose's `HeadingH1` form. The 12 cases are `.displayLarge`,
`.displayMedium`, `.h1`, `.h2`, `.h3`, `.h4`, `.bodyLarge`, `.bodyMedium`, `.bodySmall`,
`.labelLarge`, `.labelMedium`, `.caption` — matching `design-tokens.json#/typography/scale`'s 12
steps one-for-one. The `relativeTo:` **anchors** from the contract's `typographyMapping` are kept
verbatim (`.largeTitle`, `.largeTitle`, `.title`, `.title2`, `.title3`, `.headline`, `.body`,
`.body`, `.subheadline`, `.subheadline`, `.footnote`, `.caption`); the anchors were never the
problem — only the API shape and the `Font.mentoraHeadingH1` naming were.

### 15.2 No generated component-token layer — deliberate, and matching Android

`platform-mapping.md § 7.1` describes a third token layer (`design-tokens.json#/component/*`) whose
iOS handle would be `MentoraComponentTokens.Button.Primary.background(for: .default)`. **T2 does not
emit it, and Component Kits A/B (T8/T11) must not hand-write it.** `tools/token-pipeline/generate.js`
today resolves `component.*` only into Web CSS variables; Android's generated `MentoraTokens.kt`
emits no component-token object either, and Phase 4's components composed component-level values from
the **semantic/primitive** tokens at the point of use. iOS does exactly the same. This is written down
so nobody "fills the gap" by typing a literal into a component, which would violate G2's "no
hand-written token value anywhere, ever". If the component layer is ever wanted, it is a generator
change for **all three** platforms, not an iOS-local one.

---

## 16. Reusable SwiftUI Mentora components

Two kits, mirroring Android's split (atoms, then cards/state-patterns/sheets) because that split
sequenced dependencies well there — every component cross-referenced to `design-system/COMPONENTS.md`
and, where one exists, to the matching `design-to-code/screens/mobile-*.json` composition.

**Kit A — atoms:** `MentoraButton` (primary/secondary/tonal/text variants via a `ButtonStyle`),
`MentoraIconButton`, `MentoraTextField` (floating label, error state with icon — the third non-color
signal `ACCESSIBILITY.md § 7` requires), `PasswordField` (visibility toggle), `SearchField`,
`MentoraToggle`, `MentoraSelect` (`Picker`/`Menu`, per `COMPONENTS.md § Select`'s own native mapping),
`Badge`, `CategoryChip`, `MentoraProgressBar`, `Avatar`, `MentoraTabs`, `MentoraSnackbar`,
`MentoraIcon` (asset-catalog glyph + size + RTL mirroring rule).

**Kit B — composites:** `CourseArtwork` (the 5-motif gradient system from `artwork.json`),
`CourseCard`, `CourseProgressCard`, `LearningPathCard`, `CertificateCard`, `StatCard`, `QuestionCard`,
`AnswerOption`, `AITutorBubble`, `AITutorQuickAction`, `CheckoutSummary`, plus the state patterns
`LoadingState` (skeletons), `EmptyState`, `ErrorState`, `SuccessState`, and the container patterns
`MentoraSheet` (top-corners-only radius) and `MentoraDialog`.

Rules: every component takes already-localized strings or a localization key — never a raw English
default (H1); every component's colors/typography/shape come from the generated token layer only —
composed from **semantic/primitive** tokens at the point of use, since there is deliberately no
generated component-token file to read from (§ 15.2), and **never** by typing a literal value;
every interactive element meets 44 pt; every component has a SwiftUI `#Preview` in light+dark and
en+ar, which is the iOS analogue of Android's preview files and the cheapest way to make the MC-3
visual sweep fast.

---

## 17. Error / loading / empty-state handling

- Each screen model exposes an explicit phase enum; the view `switch`es over it. There is no
  `isLoading` boolean plus a nullable error — that shape is how "loaded but also failed" states get
  shipped.
- **Copy derives from `ApiErrorCode`, never from `Failure.message`** (the `ApiResult` kdoc makes this a
  contract). One `ErrorCopy` mapper (the iOS analogue of Android's `ui/error/ApiErrorCopy.kt`) maps
  each code to a localized key, with a documented default for `Unknown(raw)`. Codes that carry real UI
  meaning and must be distinguishable: `AUTH_INVALID_CREDENTIALS`, `EMAIL_ALREADY_REGISTERED`,
  `VALIDATION_ERROR` (with `fields` routing to inline field errors), `FORBIDDEN_NOT_ENROLLED`,
  `QUIZ_NOT_FOUND` ("this course has no quiz" — a legitimate state, at least one seeded course has
  none), `ATTEMPT_NOT_FOUND` ("no attempt yet"), `RATE_LIMITED_AUTH`, `RATE_LIMITED_AI_TUTOR`,
  `NETWORK_ERROR`.
- Severity routing per `CONTENT_RESILIENCE.md § 4`: view-blocking failure → `ErrorState` with a real
  retry; item-level failure → inline affordance/snackbar; background sync failure (the playback
  heartbeat) → silent or snackbar, never a blocking dialog and never a retry storm.
- Skeletons match the resolved content's footprint (§ 3 of that file) so nothing reflows.
- Empty states always carry icon → title → description → action, with per-context copy (§ 5).
- **`GetMyLearningUseCase` fails the whole call on the first per-course composition error** (D74,
  deliberate). My Learning must render that as a retriable error, not an empty list — this is
  explicitly flagged in `PHASE_HANDOFF.md` P3 § 6 item 6 as something a client can get wrong.
- **The Swift progress join must match Android's shape exactly, including two behaviors that are easy
  to "improve" into a divergence.** Read from
  `mobile/androidApp/.../domain/mylearning/GetMyLearningWithProgressUseCase.kt`:
  (a) it **eagerly drains every page** — `do { getMyLearning(cursor, PageLimit = 50) } while (cursor
  != null)` — it does **not** do incremental UI paging, and iOS must not introduce incremental paging
  here either, because that would make the two clients show different data at the same scroll
  position; (b) the **per-course progress join also fails the whole call**: the first `Failure` from
  *either* the list call or *any* `getCourseProgress` call is returned as the join's result, with no
  partial-list fallback. Request count is therefore roughly **2N+1**, not "N+1" — list pages (1 at
  seed scale, more if an account grows) plus N sequential `getCourseProgress` calls, on top of the N
  `GET /courses/{id}` calls `shared` already makes inside `getMyLearning`. Calls stay **sequential**,
  matching Android; no Swift-side `TaskGroup` parallelization, which would change the observed
  failure ordering.
- Offline detection uses `NWPathMonitor` directly. **No cache, no SQLDelight** — `shared` ships none
  by design and Phase 5 must not add one (P3 § 9).

---

## 18. Media handling (the AVPlayer side of `LessonPlaybackController`)

**Decision: iOS does not conform to the generated Obj-C protocol for `LessonPlaybackController`; it
implements a Swift-native protocol with the same contract.**

Why: `LessonPlaybackController` exposes Kotlin `Flow<Duration>`/`Flow<PlaybackState>` properties.
Conforming from Swift would require *producing* Kotlin flows from Swift — the direction SKIE does not
bridge — for an interface that `shared` itself never consumes (verified: the only references are kdoc
and a `commonTest` shape double). Android had the same freedom and used it: it added its own richer
`PlaybackController` interface (`StateFlow`, `currentPositionNow()`, `prepareLesson`, `stop`,
`release`) and had `MediaPlaybackController` implement both. iOS takes the Swift half of that
precedent.

`LessonPlayer` (Swift protocol) mirrors the contract member-for-member: `prepare(url:)`, `play()`,
`pause()`, `seek(to:)`, `currentPosition`, `duration`, `state` — with `AsyncStream`/`@Observable`
properties instead of `Flow`, and `PlaybackState` mapped 1:1 onto `shared`'s sealed type so the two
platforms cannot drift on what states exist.

Hard contract points carried over verbatim (all from `INTEGRATION_CONTRACT.md § 12` item 10 and the
interface kdoc):

- The URL from `sdk.media.getLessonPlaybackSource` is **already absolute and already tokenized** — use
  it as-is, attach **no** `Authorization` header (`AVURLAsset` gets no custom headers).
- TTL is ~5 minutes: the player owner watches `expiresAt`, calls `sdk.media.refreshPlaybackUrl(...)`
  before expiry, and re-prepares at the current position. A nil return means "no refresh needed".
- `duration` comes from the player only, never from the API (no endpoint exposes it).
- Range requests are supported server-side, so native scrubbing works; the client implements no range
  logic of its own.
- The heartbeat goes through `sdk.progress.reportPlaybackPosition` on the single SDK instance so the
  shared throttle applies (D76) — the app adds no second throttle and no retry.
- Implementation: `AVPlayer` + `AVPlayerLayer` hosted in a `UIViewRepresentable` (the justified UIKit
  use of A1), a periodic time observer driving position, KVO/`AVPlayerItem.status` driving state, and
  explicit teardown on disappear. Audio session category is set for playback; PiP/background audio are
  **not** in scope (no product requirement).

Course Player chrome mirrors in RTL except the scrubber, which stays LTR (§ 13).

---

## 19. Local-development / backend connectivity

- **iOS Simulator:** shares the Mac's network namespace, so `http://localhost:8080` reaches a
  backend running on that same Mac directly — no alias trickery, no `10.0.2.2` equivalent needed.
  `ApiEnvironment.iosSimulator()` already encodes exactly this and is used as-is.
- **ATS:** iOS blocks cleartext HTTP by default. A **debug-only** `NSAppTransportSecurity` exception
  (`NSAllowsLocalNetworking`, which covers `localhost`/`.local`/link-local without disabling ATS
  wholesale) goes in the Info.plist via `project.yml`. This is a real, easily-forgotten step that
  otherwise presents as "every request fails on the simulator" — call it out in the README.
- **Backend on a different machine from the Mac** (e.g. the backend keeps running on this Windows box
  while the Mac runs Xcode): use `ApiEnvironment.lan(host:port:)` with the Windows host's LAN IP, and
  the same ATS exception plus a firewall rule for 8080. Document it; do not build a settings UI for it.
- **Physical iPhone:** same `lan(...)` path. **The Android limitation is inherited in shape** — there
  is no build-variant/config mechanism for base URL anywhere in this project (D53: no production
  backend exists), so a device run needs a one-line source change. Disclose it in
  `mobile/iosApp/README.md` exactly as `androidApp/README.md` discloses its own.
- Seeded demo accounts are the same ones every other client uses (`student1@mentora.dev` /
  `MentoraDemo1`, per `backend/README.md`).
- The backend's auth routes are rate-limited to 10 requests/minute; a test suite that registers a fresh
  account per test will trip it on rapid re-runs. Android's README documents this; the iOS README must
  too, so an isolated `RATE_LIMITED_AUTH` failure is not misread as a regression.

---

## 20. Testing strategy — what is genuinely runnable where

**On this Windows host (real, not theatre):**

- `:shared:assembleDebug` and `:shared:testDebugUnitTest` **249/249** after the `build.gradle.kts`
  change — proof the iOS wiring did not break the Android/common surface.
- Gradle configuration of the new XCFramework task (evaluates without a Kotlin/Native compile).
- `node tools/token-pipeline/generate.js` — full run, plus byte-identity checks on web and Android
  outputs, plus structural validation of the generated Swift and asset catalogs.
- String Catalog parity/format-specifier checks (`.xcstrings` is JSON).
- `project.yml` YAML validity and target/setting review.
- Grep-based architecture gates: no Kotlin interop types outside the bridge; no `URLSession`; no
  payment vocabulary; no physical left/right layout modifiers; no raw system colors; no hardcoded
  user-facing literals.
- Diff review of every Swift file against this design document.

**Only on macOS (no substitute exists) — and, since D100, split between the automated CI tier and a
human Mac session; see § 20.1 for which is which):**

- Any Swift compilation at all — including whether the SKIE-generated API actually has the shapes § 5
  assumes.
- XCTest unit tests (bridge conversions, error copy, model state transitions via seams, formatters,
  parity, mirroring lists).
- `:shared:iosSimulatorArm64Test` — the Kotlin `iosTest` source set, including T1b's
  `IosTokenStorage` failure-injection tests (§ 9.1 K6). It does not exist on this host and is not part
  of the 249-test `:shared:testDebugUnitTest` baseline, which T1b leaves unchanged.
- XCUITest smoke suite (J4) against the real backend.
- Every visual, RTL, Dynamic Type, VoiceOver, dark-mode and playback verification — including the AX5
  multiline line-height check that § 15.1's formula owes its evidence to.

**Consequence, stated plainly:** the Swift portion of Phase 5 is *authored* on Windows and *becomes
real* on a Mac. A plan that authors ~15k lines of Swift with no compiler feedback carries genuine risk
of a large, late error backlog. The implementation plan therefore front-loads every genuinely
Windows-completable task — **T1, T2, T3 and T4a**, plus **T1b**, which is *authored* on Windows
(Kotlin, no generated-symbol dependency) and verified at MC-1 — and gates **everything from T4b
onward** on the Mac, because T4b is the first task that binds to SKIE-generated Swift symbols whose
spelling is unknown until MC-1. If no Mac is available, the honest outcome is that Phase 5 stops
cleanly after **T4a**.

**Amended by D100 (§ 20.1):** that stopping point is now cleared by the GitHub Actions macOS
pipeline (`.github/workflows/ios-ci.yml`, Task T4c) rather than only by a personal Mac — the first
green CI run captures the generated Swift interface as a downloadable artifact, so T4b onward is
authored on Windows against a real interface and compile-verified by CI instead of blind. What CI
does **not** unblock is any live, visual or accessibility criterion; those still stop at
MC-2/MC-3/MC-4 and still need a human on a Mac with the local backend running — see
`PHASE_5_ACCEPTANCE_CRITERIA.md § 4.1`.

### 20.1 The CI tier (Task T4c, decision **D100**) — an automated macOS compile gate

**What changed.** The two-way Windows/Mac split above is now a **three-way** split. A real macOS
toolchain is in the loop on every push that touches the iOS surface, via
`.github/workflows/ios-ci.yml` (Task **T4c** in the Implementation Plan). CI does **not** replace the
Mac checkpoints; it takes the mechanical, automatable part of **MC-1** away from them and runs it on
every change instead of once.

| Tier | Where it runs | What it genuinely proves | What it can never prove |
|---|---|---|---|
| **W** | this Windows host | unchanged: `:shared` JVM/Android Gradle surface, Node generators, JSON/XML/YAML structure checks, greps, diff review | anything needing a Kotlin/Native or Swift compiler (J1) |
| **C** (new) | GitHub-hosted `macos-15` runner, unattended | Kotlin/Native compile + link of `iosMain`, SKIE actually applying, `:shared:iosSimulatorArm64Test` (T1b's Keychain failure-injection tests), `assembleSharedDebugXCFramework`, `xcodegen generate`, `xcodebuild build` for a Simulator destination, the `iosAppTests` XCTest target | anything requiring a human eye, a live backend, or an interactive app: visual/RTL/dark-mode fidelity, Dynamic Type at AX5, VoiceOver, playback, Keychain-survives-relaunch, offline behavior, XCUITests |
| **M** | a real Mac, human at the keyboard, real local backend running | everything in the **C** column plus everything in its "never" column — MC-2, MC-3, MC-4 in full | — |

**Exactly which MC-1 items CI closes, and which it does not.** MC-1's checklist
(`PHASE_5_IOS_IMPLEMENTATION_PLAN.md § 3`) splits cleanly:

- **Closed by a green CI run (tier C):** SKIE 0.9.5 resolving and applying under Kotlin 2.0.21 on
  macOS; `:shared:compileKotlinIosSimulatorArm64`; `:shared:linkDebugFrameworkIosSimulatorArm64`;
  `:shared:assembleSharedDebugXCFramework` producing a real `shared.xcframework`; `iosMain`
  compiling at all (it never has); `:shared:iosSimulatorArm64Test` green, which is what converts
  **T1b from authored to verified** (F3 Category 2, criterion B10's test half). CI additionally
  closes two items MC-1 never owned because they were unreachable: `xcodegen generate` against
  `project.yml`, and whether `Packages/MentoraShared/Package.swift`'s hardcoded relative
  `binaryTarget` path is actually where Gradle writes the framework.
- **Not closed by CI — still a human reading, though CI now supplies the evidence:** whether the
  generated Swift API really exposes `async` methods, Swift enums for sealed types and
  `AsyncSequence` for flows (§ 5's assumptions). CI cannot judge API *shape*; it can only produce
  it. It therefore uploads the generated interface as a build artifact (below).
- **Not closed by CI at all:** that the non-exported Koin `Module` bridges usably into Swift (proved
  only by T4b actually calling `create(...)`), and § 9.1 K3's assumption that a non-`@Throws` Kotlin
  exception crossing into Swift terminates the process (a runtime behavior, MC-2).

**The artifact that changes how Windows authoring works — stated precisely, not as a foregone
conclusion.** Every CI run (`if: always()` on the capture steps, so this happens even if a later
step failed) uploads a `kmp-swift-interface` artifact containing: the framework's generated Obj-C
header; whatever SKIE Swift output genuinely exists under `mobile/shared/build/` (its exact
location has never been confirmed on a real run, so the capture step searches broadly —
`*.swift`/`*.swiftinterface`/`*.swiftmodule`/any `skie`-named path — rather than asserting a
layout); and a `swift-api-digester`-generated JSON dump of the framework's real Swift-visible API
surface, which does not depend on guessing SKIE's intermediate output location at all. **A Windows
host can download and read it.** **Explicit gate:** if the artifact does not actually contain a
usable Swift API surface — via the digester JSON or genuine SKIE Swift files — Task T4b does not
start until that gap is fixed, even if the rest of CI is green. The first real CI-1 run determines
whether this gate is met; it is not assumed here. If it is met, this removes the specific blocker
§ 20's "Consequence" paragraph and Implementation Plan risk § 5.2 named — "SKIE-generated Swift
symbol names are literally unknown until MC-1" — without a Mac ever being touched, and Swift from
T4b onward is authored against the real captured interface and compiled for real by CI in the same
slice.

**Why CI deliberately does not run the backend.** Phase 5's live criteria (E1, B3/B6, C3-C18, the
whole of I) assume the real local Ktor + seeded MongoDB. Standing that up on a GitHub-hosted macOS
runner is possible (Homebrew `mongodb-community`, a single-node replica-set init, a fabricated
`.env`, a second Gradle build, `seedDemoData`) but is **out of scope for this pipeline's first
version**, for four reasons, in order of weight: (1) nothing consumes it yet — the XCUITest suite
that would exercise a live backend does not exist until T22; (2) the criteria that need a live
backend are overwhelmingly *visual/behavioral* and need a human observer anyway, so a green headless
run would close none of them; (3) it requires inventing CI-side secrets (a `JWT_SIGNING_SECRET`)
where this pipeline currently needs **none at all**; (4) GitHub-hosted macOS runners have no Docker
daemon, so the Linux-CI MongoDB-service-container pattern does not transfer, and the Homebrew
replica-set path is materially more fragile than the compile gate it would be bolted onto. Revisit
at T22, as a second CI tier, not as a change to this one.

**Cost posture.** `HeshamMohamed94/Mentora` is a **public** repository and GitHub Actions on standard
hosted runners is free for public repositories, so today this costs no billed minutes. The workflow
is nonetheless written as if minutes were scarce — path-filtered triggers, `workflow_dispatch`,
`concurrency` cancel-in-progress, a 60-minute hard timeout, `~/.konan` + Gradle User Home caching —
because macOS minutes bill at a **10x multiplier** the moment a repository is private, and because
a stuck macOS job is expensive in wall-clock feedback terms regardless of billing. Rough expectation:
**20-30 minutes wall clock on a cold cache, 10-15 warm** for today's tiny app target, growing toward
15-20 warm once the full Swift app exists.


---

## 21. Accessibility / Dynamic Type approach

- Typography goes through the `.mentoraFont(_:)` **view modifier** (§ 15.1), whose size comes from
  `@ScaledMetric(relativeTo:)` against the token's system text-style anchor, so Dynamic Type scales
  everything including the AX sizes (`ACCESSIBILITY.md § 10`'s explicit iOS bar) **and** an injected
  `.dynamicTypeSize(...)` in a preview or XCTest scales it too, which is what makes the AX behavior
  testable at all. No raw `.system(size:)` in view code, and no `.dynamicTypeSize(...max)` clamp used
  to "fix" a layout — the layout gives way, per § 11's reflow-don't-clip rule.
- **Line height scales with the size, by ratio** (§ 15.1's formula): the target line height is derived
  from the same `@ScaledMetric` value as the font size, so the intended leading ratio holds at every
  Dynamic Type step, and the `.lineSpacing` value is clamped at `0` and can never go negative. The
  evidence for this is not "the font got bigger": it is a multiline sample rendered at **`.large` and
  `.accessibility5`, in both `en` and `ar`**, asserting a preserved ratio, non-negative spacing and no
  line overlap (criteria G3/I1; runs on the Mac).
- No fixed-height text containers (`CONTENT_RESILIENCE.md § 8`, a locked rule).
- Minimum touch target 44 pt, never shrinking to make room for larger text.
- VoiceOver: labels on every control; `.accessibilityHidden(true)` on decorative artwork;
  `.accessibilityElement(children: .combine)` on composite cards so a course card announces as one
  element; a real label on the avatar (the D93 finding, in its iOS form); the video scrubber exposes
  `.accessibilityValue` as a time, adjustable via `.accessibilityAdjustableAction`.
- Error accessibility: an error is never conveyed by color alone — icon + text + the field's
  `.accessibilityValue`, per `ACCESSIBILITY.md §§ 7, 8`.
- Reduced motion honored via `@Environment(\.accessibilityReduceMotion)`.
- Bottom-nav icon-only fallback at extreme text scale is pre-specified, not improvised
  (`CONTENT_RESILIENCE.md § 1`), and still carries full accessible labels.
- Validation tooling per `ACCESSIBILITY.md § 15`: Accessibility Inspector audit + VoiceOver smoke +
  largest-AX-size stress pass. All three are Mac-only.

---

## 22. Project / file / module organization

Anchored to `architecture/REPOSITORY_STRUCTURE.md § 4`, which already reserves `mobile/iosApp/` with
`MentoraApp.swift`, `Theme/`, `Navigation/`, `Features/`, `Resources/Localizable.xcstrings`. This
design keeps those names exactly where the locked doc names them and adds only what it leaves
unspecified.

**Naming decision (corrected):** an earlier draft of this document proposed `DesignSystem/` instead of
the locked tree's `Theme/`, on the grounds that the folder also holds icons, shapes and elevation.
**That deviation is withdrawn — the directory is `Theme/`.** `REPOSITORY_STRUCTURE.md § 4`'s tree and
its § 5 generator-output sentence both name `mobile/iosApp/.../Theme/MentoraTokens.swift`, and
matching a locked doc is worth more than a marginally better folder name; it also means **no locked
file needs a clarifying edit**, which would otherwise have been an unwanted dependency of Phase 5.
Android's own analogue is `theme/` for the same reason.

```
mobile/
├── settings.gradle.kts            UNCHANGED — :shared + :androidApp only; iosApp is NOT a Gradle module
├── shared/                        Phase 3 KMP module (build.gradle.kts gains iosMain wiring + XCFramework task)
└── iosApp/
    ├── project.yml                XcodeGen spec — AUTHORITATIVE, hand-authored, reviewable on Windows
    ├── iosApp.xcodeproj/          GENERATED on the Mac by `xcodegen generate`, never hand-edited
    ├── README.md                  local-run instructions + known limitations (Phase 4 Task 20 precedent)
    ├── scripts/
    │   └── build-shared-xcframework.sh   wraps `./gradlew :shared:assembleSharedDebugXCFramework`
    │                                     (release: assembleSharedReleaseXCFramework; both:
    │                                     assembleXCFramework). NOT assembleSharedXCFramework - § 6.
    ├── Packages/
    │   └── MentoraShared/Package.swift   local SPM package wrapping shared.xcframework as a binaryTarget
    └── iosApp/
        ├── MentoraApp.swift              @main App; builds AppEnvironment; applies locale/direction/colorScheme
        ├── AppEnvironment.swift          the single MentoraSdk + SessionController + LocaleController + ThemeController
        ├── Navigation/                   Route.swift, TabRouter.swift, RootView.swift, AuthGate.swift, TabShell.swift
        ├── Theme/                        name taken from REPOSITORY_STRUCTURE.md § 4 (locked)
        │   ├── MentoraTokens.swift           GENERATED (spacing/radius/elevation/typographyMetrics/iconSize/stateOpacity/touchTarget)
        │   ├── Color+Mentora.swift           GENERATED (asset accessors)
        │   ├── MentoraColors.xcassets/       GENERATED (46 semantic colorsets + shadow colorsets, any + dark)
        │   ├── MentoraIcons.xcassets/        42 vector glyphs (template rendering)
        │   ├── MentoraIcon.swift             icon enum + size + RTL mirroring rule (arrowForward/arrowBack only)
        │   ├── MentoraTypography.swift       .mentoraFont(_:) ViewModifier, @ScaledMetric (+ Arabic rules) - § 15.1
        │   ├── MentoraShape.swift            radius mapping incl. top-corners-only 24 pt sheet shape
        │   └── MentoraElevation.swift        .mentoraElevation(_:) modifier (shadow colorset + border)
        ├── Components/                   Kit A (atoms) + Kit B (cards, state patterns, sheets) — § 16
        ├── Features/                     one folder per screen: <Name>View.swift + <Name>Model.swift
        │   ├── Auth/ Home/ Explore/ CourseDetails/ LearningPathDetails/ Checkout/
        │   ├── MyLearning/ CoursePlayer/ Quiz/ Certificates/ AITutor/ Profile/
        ├── Playback/                     LessonPlayer protocol + AVPlayerLessonPlayer + PlayerSurface (UIViewRepresentable)
        ├── Support/
        │   ├── SharedBridge/             MentoraClient, ApiResult unwrapping, Flow adapters, MentoraError
        │   ├── ErrorCopy.swift           ApiErrorCode -> localized key
        │   ├── Formatters.swift          locale-aware date/number/duration/price (Latin numerals forced)
        │   ├── MentoraStrings.swift      locale-required string accessor (§ 12)
        │   └── Connectivity.swift        NWPathMonitor
        └── Resources/
            ├── Localizable.xcstrings     en + ar String Catalog
            └── Info.plist                incl. debug-only ATS local-networking exception
    ├── iosAppTests/                XCTest: bridge, models, copy, formatters, parity, mirroring
    └── iosAppUITests/              XCUITest: portfolio journey + navigation behaviors
```

`mobile/settings.gradle.kts` is **not** modified — the iOS app is an Xcode project, not a Gradle
module; it consumes `:shared`'s built XCFramework. `.gitignore` gains `mobile/iosApp/build/`,
`DerivedData/`, `*.xcuserdata*`, and the XCFramework output path.

---

## 23. Android / iOS parity boundaries

| Concern | MUST be identical | MAY legitimately differ | Authority |
|---|---|---|---|
| Business logic (auth/session, catalog, enrollment, progress, quiz, certificates, paths, media, AI Tutor) | Identical — same `shared` use cases, same instances of the same code | nothing | ADR-002; `PHASE_HANDOFF.md` P3 § 9 |
| Wire contract (endpoints, envelope, error codes, CSRF value, no cookie jar, `?language=` threading, 429 synthesis, token-in-URL media) | Identical — enforced by both clients going through `shared` | nothing | `INTEGRATION_CONTRACT.md § 12` |
| Screen scope | Identical 18 screens, identical in/out-of-scope list | how a screen is composed internally | `SCREEN_INVENTORY.md`; `PHASE_5_ACCEPTANCE_CRITERIA.md § C` |
| Design tokens (color values, type scale, spacing, radius, elevation) | Identical values, same semantic names, both themes | the platform type of the generated constant (`Color` vs `Color`, `sp` vs Dynamic-Type pt) | D53; ADR-011; `platform-contract.json` |
| Visual identity (artwork motifs, card identity, chip treatment, icon silhouettes, dark-mode structure) | Identical | rendering technology | `platform-contract.json#/visualParityRule` |
| Locked non-screen behaviors (5-tab order, tap-active-tab-pops, independent back stacks, nav hidden on Player/Quiz, curriculum in a sheet, scrubber LTR, guest-gate returns to intent, quiz answers survive back, Purchase Success back to My Learning) | Identical behavior | the mechanism that achieves it | `MOBILE_UX.md`; `NAVIGATION_SPEC.md` |
| Localization content (key set, copy, EN/AR parity, Western numerals) | Identical key-for-key | the storage/lookup mechanism (`strings.xml` vs `.xcstrings`) and the runtime-switch mechanism (`ContextWrapper` vs environment locale) | ADR-002 (localized strings deliberately NOT shared); D93 |
| Touch target minimum | Same *rule* (never shrink for text) | the number: 48 dp Android, **44 pt iOS** | `design-tokens.json#/touchTarget`; `ACCESSIBILITY.md § 4` |
| Arabic font | Same type-system feel | Android bundles/placeholders a family; iOS uses automatic SF Arabic | `LOCALIZATION.md § 4` |
| Navigation idiom | Same user-visible model | nested nav graphs + `saveState` (Android) vs per-tab `NavigationStack` paths (iOS) | § 10 |
| Presentation layer | Same states rendered (loading/error/empty/success) | ViewModel+`StateFlow`+`viewModelScope` vs `@Observable`+`async` | ADR-002 § "Android ViewModels and iOS ObservableObjects are both thin adapters" |
| Player implementation | Same contract, same URL/TTL/auth rules, same `PlaybackState` values | ExoPlayer/Media3 vs AVPlayer; Kotlin `Flow` vs Swift `AsyncStream` | § 18; `LessonPlaybackController` kdoc |
| Testing tooling | Same standard of evidence (real backend, per-screen live verification) | JUnit/Compose-UI/instrumented vs XCTest/XCUITest | `TESTING_STRATEGY.md`; § 20 |
| Local base URL | Same fact that it is hardcoded per environment with no config UI | `10.0.2.2` vs `localhost` | § 19; D53 |
| Disclosed limitations | Same list, with an explicit per-item verdict | which items apply | `PHASE_5_ACCEPTANCE_CRITERIA.md § 3` |

---

## 24. Deliberate divergences from the Android implementation (summary)

Every item here is a conscious "do not transliterate Compose into SwiftUI" call, collected so a
reviewer can check intent rather than infer it:

1. **All SDK access behind `MentoraClient`** rather than passing `MentoraSdk` into every model —
   forced by `internal` façade constructors (Swift cannot fake them) and by the need to unbox
   `ApiResult`/`Kotlin*` types exactly once (§ 2).
2. **No nested navigation graphs / `saveState`-`restoreState` machinery** — per-tab `NavigationStack`
   paths give independent back stacks natively (§ 10).
3. **No `ContextWrapper`/`CompositionLocal` locale plumbing** — the SwiftUI environment already
   propagates locale and direction, including into sheets (§ 12).
4. **Swift-native `LessonPlayer` protocol instead of conforming to the Kotlin interface** — Flow
   cannot be produced from Swift, and `shared` never consumes the interface anyway (§ 18).
5. **No bundled Arabic font, no icon-font dependency** — SF Arabic is automatic; icons are
   asset-catalog vectors per `platform-mapping.md § 7` (§ 15, and `PHASE_5_ACCEPTANCE_CRITERIA.md § 3`
   items 3-4).
6. **`@Observable` + `.task {}` instead of ViewModel factories, `viewModelScope`, `SavedStateHandle`**
   — process-death state restoration is not an iOS-equivalent concern (§ 3).
7. **44 pt touch targets, not 48 dp** — the design system already specifies both numbers (§ 23).
8. **The project file itself is generated from `project.yml`** — a Windows-authorability requirement
   with no Android analogue (§ 1).
9. **Typography is a `ViewModifier` (`.mentoraFont(_:)`), not a `Font` extension** (§ 15.1). This is
   *not* a divergence from Android — it is the **same** convention Android uses
   (`MentoraTheme.typography.h1`) and the one `design-system/platform-mapping.md § 2` (LOCKED)
   already specifies. It is listed here only because it **corrects the prose** in
   `design-to-code/shared/platform-contract.json#/ios/typographyMapping`, which describes a
   `Font.mentoraHeadingH1` shape that cannot carry Dynamic Type, tracking or line height at all. The
   authority order is explicit: **`platform-mapping.md § 2`'s modifier convention governs; the JSON
   file's prose does not.** `platform-contract.json`'s `ios` section is refreshed additively by T2 to
   stop asserting the unimplementable shape (A8 sanctions this; Phase 4's `79fc51d` is the
   precedent).

10. **A Keychain failure is published through an `iosMain` `StateFlow`, not thrown** (§ 9.1 K3).
    `AndroidTokenStorage` simply lets a storage exception propagate; iOS cannot, because a
    non-`@Throws` Kotlin exception crossing into Swift terminates the process and adding `@Throws`
    would be a `commonMain` change A6 forbids. Same requirement (a storage failure is never reported
    as success), different mechanism — and it is the one place Phase 5 adds `iosMain`-only public API.
Everything *not* on this list is expected to match Android's behavior exactly. Where Phase 5 finds a
new reason to diverge, it goes in `DECISIONS_LOG.md` with the reason — never silently.
