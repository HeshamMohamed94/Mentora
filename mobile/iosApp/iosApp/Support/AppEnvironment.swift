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
    let sdk: MentoraSdk
    let sessionController: SessionController
    let localeController: LocaleController
    let themeController: ThemeController

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
        // never had a macOS build run), so this could not be verified either way. Kept as the disclosed,
        // hardcoded fallback pending the next real CI compile confirming (or refuting) the zero-arg
        // overload's existence — see D108's "needs CI to confirm" note.
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
        self.sessionController = SessionController(sdk: sdk)
        self.localeController = LocaleController(sdk: sdk)
        self.themeController = ThemeController(sdk: sdk, coldStartTheme: coldStartTheme)

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
        let localeController = self.localeController
        bootstrapTask = Task {
            let restored = try? await sdk.auth.restoreSession.invoke()
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
/// static singleton). `MentoraApp` always injects the real, single `AppEnvironment` before any view's
/// body runs, so `defaultValue` below is a fail-fast trap, not a fallback instance — it is never
/// actually constructed in the shipped app.
private struct AppEnvironmentKey: EnvironmentKey {
    static var defaultValue: AppEnvironment {
        fatalError(
            "AppEnvironment must be injected via `.environment(\\.appEnvironment, ...)` before any " +
            "view reads it — see MentoraApp.swift."
        )
    }
}

extension EnvironmentValues {
    var appEnvironment: AppEnvironment {
        get { self[AppEnvironmentKey.self] }
        set { self[AppEnvironmentKey.self] = newValue }
    }
}
