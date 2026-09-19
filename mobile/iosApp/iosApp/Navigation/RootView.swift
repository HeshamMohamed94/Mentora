import SwiftUI

// Phase 5 Task T9 slice 2 -- replaces `MentoraApp.swift`'s former `PlaceholderRootView`. Branches ONLY
// on `SessionController.authState == .unknown` (splash) vs. everything else (the one `TabShell`) -- per
// this task's own guest-shell override (see `Route.swift`'s header), there is no second, guest-specific
// branch: `.unauthenticated` and `.authenticated` both render the identical `TabShell`.
struct RootView: View {
    @Environment(\.appEnvironment) private var appEnvironment

    var body: some View {
        if let appEnvironment {
            content(for: appEnvironment)
                .mentoraAuthGate(environment: appEnvironment)
        } else {
            // Carries over VERBATIM the former `PlaceholderRootView`'s nil-environment debug-assert-once
            // behavior (`AppEnvironment.swift`'s `AppEnvironmentKey` doc comment; the real CI run #9
            // crash, D108) -- this atypical/unconfirmed launch context still renders the same splash
            // background, and still raises a debug-only `assertionFailure` (once) so a genuine wiring
            // omission is caught loudly in a normal Debug build/manual run, without ever crashing a
            // Release build or a test host.
            SplashView()
                .onAppear {
                    #if DEBUG
                    Self.assertNotYetInjectedOnce()
                    #endif
                }
        }
    }

    @ViewBuilder
    private func content(for environment: AppEnvironment) -> some View {
        switch environment.sessionController.authState {
        case .unknown:
            SplashView()
        case .unauthenticated, .authenticated:
            TabShell(environment: environment)
        }
    }

    #if DEBUG
    /// Fires at most once per process -- see `PlaceholderRootView`'s identical former guard
    /// (`MentoraApp.swift`, deleted by this task) for why a re-render must not re-trip this.
    private static var hasAssertedMissingEnvironment = false

    private static func assertNotYetInjectedOnce() {
        guard !hasAssertedMissingEnvironment else { return }
        hasAssertedMissingEnvironment = true
        assertionFailure(
            "RootView rendered before AppEnvironment was injected via " +
            "`.environment(\\.appEnvironment, ...)` -- see MentoraApp.swift. This should never happen in " +
            "the real app launch path; only a genuine wiring omission, or an atypical launch context " +
            "(e.g. a unit-test host launch -- see AppEnvironment.swift's AppEnvironmentKey doc comment), " +
            "should ever reach here."
        )
    }
    #endif
}

/// The same full-bleed background the former `PlaceholderRootView` rendered for every session state --
/// `.unknown` and the nil-`AppEnvironment` degrade path both show this, so neither can ever flash
/// anything else before a real screen is ready.
struct SplashView: View {
    var body: some View {
        Color.mentoraBackgroundPrimary.ignoresSafeArea()
    }
}
