import SwiftUI

// Phase 5 Task T4b (D108) — real SDK/session/locale/theme bootstrap, replacing T4c's zero-`shared`
// placeholder (D100). The cold-start sequence itself lives in `Support/AppEnvironment.swift` (System
// Design § 9 steps 1-2); this file only constructs the one process-wide `AppEnvironment` (via `@State`
// so it is created exactly once for the process, never a `.shared` static — A3) and injects it via
// `@Environment`. The former `PlaceholderRootView` (T4b) was replaced by the real `RootView`/`TabShell`
// navigation shell in Task T9 (`Navigation/RootView.swift`) — this file's own job stays unchanged: apply
// the single sanctioned `.mentoraTheme(...)` call site (Check C4) above whatever the real root renders.
@main
struct MentoraApp: App {
    @State private var appEnvironment = AppEnvironment()

    var body: some Scene {
        WindowGroup {
            MentoraRootView()
                .environment(\.appEnvironment, appEnvironment)
        }
    }
}

/// T6 slice 3b (D121) — exists solely so the two `@Observable` reads it needs
/// (`themeController.theme`, `localeController.currentLocale`) happen inside a real `View` body, so
/// SwiftUI's Observation tracking invalidates this view (and re-runs `.mentoraTheme(...)`) correctly
/// when either value changes, and so `.mentoraTheme(...)` sits strictly ABOVE `RootView` (T9) in the
/// tree (per `Theme/MentoraTheme.swift`'s "apply at the true WindowGroup-content root" contract). Holds
/// no other logic — no branching, no session-state awareness; that all stays inside `RootView`/
/// `TabShell`, untouched by this slice.
///
/// `?? .system` is the correct degrade when `appEnvironment` is `nil` (the same atypical/unconfirmed
/// launch context documented on `AppEnvironmentKey` in `AppEnvironment.swift`) — `.system` follows the
/// OS, matching this file's existing debug-assert/release-degrade convention rather than forcing an
/// arbitrary concrete theme. `localeController.currentLocale` degrades to `nil` the same way (no
/// `AppEnvironment` means no known locale at all), which `.mentoraTheme(...)`'s own doc comment
/// explains is a real, handled nil-locale contract, not an oversight.
private struct MentoraRootView: View {
    @Environment(\.appEnvironment) private var appEnvironment

    var body: some View {
        RootView()
            .mentoraTheme(
                theme: appEnvironment?.themeController.theme ?? .system,
                locale: appEnvironment?.localeController.currentLocale
            )
    }
}
