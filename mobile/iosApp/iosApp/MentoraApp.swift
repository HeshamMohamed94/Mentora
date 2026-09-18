import SwiftUI

// Phase 5 Task T4c (D100) — minimal placeholder entry point. At HEAD the app target has no `@main`
// at all, so `xcodebuild build`/`test` cannot link and the Xcode half of the CI pipeline could never
// go green. Task T4b (Mac-gated, later) replaces this body with the real SDK/session/locale/theme
// bootstrap once the CI-captured Swift interface exists. Zero `shared`/`MentoraSdk` references here
// on purpose.
@main
struct MentoraApp: App {
    var body: some Scene {
        WindowGroup {
            Color.mentoraBackgroundPrimary
                .ignoresSafeArea()
        }
    }
}
