import SwiftUI

// Phase 5 Task T4b (D108) — real SDK/session/locale/theme bootstrap, replacing T4c's zero-`shared`
// placeholder (D100). The cold-start sequence itself lives in `Support/AppEnvironment.swift` (System
// Design § 9 steps 1-2); this file only constructs the one process-wide `AppEnvironment` (via `@State`
// so it is created exactly once for the process, never a `.shared` static — A3), injects it via
// `@Environment`, and renders the placeholder root driven by `SessionController`'s observed state
// (§ 9 step 5). No feature UI yet (T9/T10+) — no string literals.
@main
struct MentoraApp: App {
    @State private var appEnvironment = AppEnvironment()

    var body: some Scene {
        WindowGroup {
            PlaceholderRootView()
                .environment(\.appEnvironment, appEnvironment)
        }
    }
}

/// Renders the same full-bleed background for every session state today; the per-case branches exist
/// so `.unknown` can never flash anything but this splash placeholder once real screens replace the
/// `.authenticated`/`.unauthenticated` branches.
///
/// D108 fix round — `appEnvironment` is `AppEnvironment?` (see `AppEnvironmentKey`'s doc comment in
/// `AppEnvironment.swift` for the real, CI-run-#9-observed crash this replaced). In the normal launch
/// path (`MentoraApp.body` above always injects before this view renders), `appEnvironment` is never
/// `nil`. The `nil` branch exists only for the atypical/unconfirmed launch context that CI run #9 hit
/// (a unit-test-hosted app launch) and renders the identical splash background — indistinguishable from
/// `.unknown`, per System Design § 9 step 5's "never a flash of the wrong thing" rule — while raising a
/// debug-only `assertionFailure` (once) so a genuine wiring omission is still caught loudly in a normal
/// Debug build/manual run, without ever crashing a release build or a test host.
private struct PlaceholderRootView: View {
    @Environment(\.appEnvironment) private var appEnvironment

    var body: some View {
        if let appEnvironment {
            switch appEnvironment.sessionController.authState {
            case .unknown, .authenticated, .unauthenticated:
                Color.mentoraBackgroundPrimary.ignoresSafeArea()
            }
        } else {
            #if DEBUG
            Self.assertNotYetInjectedOnce()
            #endif
            Color.mentoraBackgroundPrimary.ignoresSafeArea()
        }
    }

    #if DEBUG
    /// Fires at most once per process — a genuine omission would otherwise re-trip this on every
    /// re-render of a `nil`-environment tree, which would be noisy without being any more informative.
    private static var hasAssertedMissingEnvironment = false

    private static func assertNotYetInjectedOnce() {
        guard !hasAssertedMissingEnvironment else { return }
        hasAssertedMissingEnvironment = true
        assertionFailure(
            "PlaceholderRootView rendered before AppEnvironment was injected via " +
            "`.environment(\\.appEnvironment, ...)` — see MentoraApp.swift. This should never happen in " +
            "the real app launch path; only a genuine wiring omission, or an atypical launch context " +
            "(e.g. a unit-test host launch — see AppEnvironment.swift's AppEnvironmentKey doc comment), " +
            "should ever reach here."
        )
    }
    #endif
}
