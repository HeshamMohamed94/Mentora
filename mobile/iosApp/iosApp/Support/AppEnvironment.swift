import Foundation
import SwiftUI
import shared

// Phase 5 Task T4b (D108) — the single process-wide assembly root (System Design § 3.1/§ 8). Creates
// the ONE `MentoraSdk` instance for the shipped app process, the cold-start theme reader, and the
// three controllers, then runs the single cold-start bootstrap task (§ 9 steps 1-2). Constructed
// exactly once by `MentoraApp` (`@State`-held) and injected via `@Environment` below — never a
// `.shared`/singleton static (A3). Real signatures below are grounded in the CI-run-#6-captured
// `shared.h`/SKIE artifact, not guessed — see `execution/DECISIONS_LOG.md` D108.
@MainActor
final class AppEnvironment {
    /// `private` (review fix round, D112 Fix 6) -- confirmed by grep that nothing outside this
    /// `init` reads `sdk` directly; every real call goes through `client` below. A3's
    /// single-instance guarantee still lives here, it just no longer needs to be externally visible.
    private let sdk: MentoraSdk
    let client: MentoraClient
    let sessionController: SessionController
    let localeController: LocaleController
    let themeController: ThemeController
    /// Phase 5 Task T9 -- the navigation shell's single source of truth (`Navigation/TabRouter.swift`).
    /// Constructed here, alongside the other three controllers, never from a view (A3).
    let router: TabRouter

    /// Owns the single cold-start bootstrap task's lifetime (§ 9 steps 1-2) — never re-created, never
    /// started from a view.
    private var bootstrapTask: Task<Void, Never>?

    init() {
        #if DEBUG
        let enableNetworkLogging = true
        #else
        let enableNetworkLogging = false
        #endif

        // `ApiTimeouts` has no zero-arg Swift initializer (Kotlin default parameter values do not
        // survive Obj-C export) — these three values are `mobile/shared`'s own real defaults
        // (`ApiEnvironment.kt`'s `ApiTimeouts()` data class), not invented here.
        //
        // D108 fix round (Fix 8): checked for a SKIE-generated zero-arg `iosSimulator()` overload (SKIE
        // 0.9.x's default-arguments feature can generate one for a Kotlin default parameter) that would
        // let this hardcoded block be deleted in favor of `ApiEnvironment.companion.iosSimulator()`. No
        // captured `shared.h`/SKIE-artifact exists on this Windows host to check against (this repo has
        // never had a macOS build run), so this could not be verified either way at the time.
        //
        // T5 (D112): CONFIRMED, not merely a fallback — real-artifact research for T5's `SharedBridge`
        // work found SKIE 0.9.5 generated NO default-argument overloads anywhere in this framework, for
        // any Kotlin default parameter. There is no zero-arg `iosSimulator()` overload to switch to; this
        // hardcoded `ApiTimeouts` block is the permanent, correct shape, not a pending fallback.
        let timeouts = ApiTimeouts(
            connectTimeoutMillis: 15_000,
            requestTimeoutMillis: 30_000,
            socketTimeoutMillis: 30_000
        )

        // Sanctioned non-façade entry points #1-#3 (A2): the one `MentoraSdk.create`, the one
        // `ApiEnvironment.iosSimulator`, the one `platformModule()`.
        let sdk = MentoraSdk.companion.create(
            environment: ApiEnvironment.companion.iosSimulator(timeouts: timeouts),
            platformModule: PlatformModule_iosKt.platformModule(),
            enableNetworkLogging: enableNetworkLogging
        )

        // Sanctioned non-façade entry point #5 (A2) — a second, throwaway `IosPreferenceStore` used
        // for exactly one thing: the synchronous cold-start `getTheme()` read before the first frame
        // renders (there is no `observeTheme` on `UserFacade`). Its `.locale` is per-instance,
        // in-memory state completely disconnected from Koin's own `IosPreferenceStore` and must never
        // be read/observed (G1) — only `getTheme()` is ever called on it, here, once.
        let coldStartTheme = IosPreferenceStore().getTheme()

        self.sdk = sdk
        let client = MentoraClient(sdk: sdk)
        self.client = client
        self.sessionController = SessionController(client: client)
        self.localeController = LocaleController(client: client)
        self.themeController = ThemeController(client: client, coldStartTheme: coldStartTheme)
        self.router = TabRouter()

        // System Design § 9 steps 1-2 — exactly one task, `restoreSession()` then
        // `seedInitialLocaleIfNeeded()`, in that order, never from a view's `.task`/`onAppear`.
        //
        // D108 fix round (Fix 6): no manual `sessionController.apply(restoredState)` call here anymore.
        // `AuthRepositoryImpl.restoreSession` (Kotlin) already calls `sessionManager.setState(state)`
        // before returning, so `SessionController`'s own live `observeAuthState` subscription receives
        // the restored state on its own — a second, manual `apply()` call from here was redundant and
        // raced with it (two independent call paths could each trigger the `.authenticated(user: nil)`
        // profile backfill). `isAuthenticated` for the locale seed below is derived directly from
        // `restoreSession`'s own return value instead.
        //
        // T5 (D112): routed through `MentoraClient.restoreSession()` instead of the raw
        // `sdk.auth.restoreSession.invoke()` call, so `.invoke` never appears outside
        // `Support/SharedBridge/` (System Design § 2) — a deliberate, approved refactor of this
        // already-CI-green line, not a behavior change.
        let localeController = self.localeController
        bootstrapTask = Task {
            let restored = try? await client.restoreSession()
            let isAuthenticated: Bool
            if let restored, case .authenticated = onEnum(of: restored) {
                isAuthenticated = true
            } else {
                isAuthenticated = false
            }
            await localeController.seedInitialLocaleIfNeeded(isAuthenticated: isAuthenticated)
        }
    }
}

/// Required-dependency `@Environment` key (System Design § 3.1: `@Environment` injection, never a
/// static singleton). `MentoraApp` always injects the real, single `AppEnvironment` before
/// `RootView`'s body (`Navigation/RootView.swift`, Task T9 -- the former `PlaceholderRootView` this key's
/// history below refers to) is expected to read it in the normal app-launch path.
///
/// D108 fix round — CI run #9 (commit `5f21c53`) caught this key's original `defaultValue` — a hard
/// `fatalError()`, on the theory that `MentoraApp` always injects before any view reads it — actually
/// crashing for real, every time, in a genuinely reproducible launch path. Verbatim from that CI log:
/// ```
/// AppEnvironment.swift:96: Fatal error: AppEnvironment must be injected via
/// `.environment(\.appEnvironment, ...)` before any view reads it — see MentoraApp.swift.
/// Testing failed: iosApp (8481) encountered an error (Early unexpected exit, operation never finished
/// bootstrapping - no restart will be attempted. (Underlying Error: Test crashed with signal trap
/// before starting test execution.))
/// ```
/// Confirmed: the trap fired before any `iosAppTests` test method ran, during `xcodebuild test`'s own
/// launch of the real `iosApp` binary as `iosAppTests`'s test host — not a mock, not a compile error,
/// and not `ScaffoldPlaceholderTests.swift` (a trivial `XCTAssertTrue(true)`, confirmed unrelated) or an
/// `#Preview` macro (none exist anywhere in `mobile/iosApp/`). Not confirmed: the exact SwiftUI/Xcode
/// mechanism that let `PlaceholderRootView`'s `@Environment` read reach this default in that launch
/// context. A unit-test-hosted app launch plausibly has no real, visible `UIWindowScene` attached, and
/// SwiftUI's environment-population guarantee is tied to the render graph actually running — which may
/// not fully happen in a headless test-host launch — but this is a plausible contributing factor, not a
/// proven root cause.
///
/// Regardless of the exact mechanism, a hard `fatalError()` default is the wrong contract for a value
/// that a real, reproducible (if atypical) launch path can reach — it takes down the entire test host
/// (and would take down a real user's app, if the same path were ever hit there) instead of degrading.
/// Fixed per this codebase's own established convention for exactly this situation (D108 Fix 5's
/// Keychain-flow-bridge pattern: don't crash on an unexpected state — assert in debug, degrade
/// gracefully in release). `appEnvironment` below is now `AppEnvironment?`, defaulting to `nil` instead
/// of constructing (or crashing while trying to construct) a real value — `defaultValue` must never call
/// `AppEnvironment()`'s real initializer, since that would spin up a second `MentoraSdk` instance
/// (System Design § 3.1, acceptance criterion A3 — forbidden). `RootView` (`Navigation/RootView.swift`,
/// Task T9 -- the former `PlaceholderRootView` this key's history above refers to)
/// treats `nil` as visually identical to `.unknown` — both are the launch splash — and raises a
/// debug-only `assertionFailure` (never a release-mode crash) the first time it actually renders the
/// `nil` case, so a genuine wiring omission is still caught loudly in a normal Debug build/manual test
/// run.
private struct AppEnvironmentKey: EnvironmentKey {
    static var defaultValue: AppEnvironment? { nil }
}

extension EnvironmentValues {
    var appEnvironment: AppEnvironment? {
        get { self[AppEnvironmentKey.self] }
        set { self[AppEnvironmentKey.self] = newValue }
    }
}
