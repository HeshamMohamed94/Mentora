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
private struct PlaceholderRootView: View {
    @Environment(\.appEnvironment) private var appEnvironment

    var body: some View {
        switch appEnvironment.sessionController.authState {
        case .unknown:
            Color.mentoraBackgroundPrimary.ignoresSafeArea()
        case .authenticated, .unauthenticated:
            Color.mentoraBackgroundPrimary.ignoresSafeArea()
        }
    }
}
