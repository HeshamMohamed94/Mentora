import Foundation
import Observation
import shared

// Phase 5 Task T4b (D108) — cold-start theme read + write-through theme state (System Design § 8/G1).

/// Reads the persisted theme once at cold start (before first paint), then routes every subsequent
/// write through `UserFacade.setTheme` so Koin's own `IosPreferenceStore` instance stays the single
/// source of truth. `AppEnvironment` supplies the cold-start value from its own throwaway
/// `IosPreferenceStore().getTheme()` read (sanctioned non-façade entry point #5, A2) — there is no
/// `observeTheme` on the façade, so that one-shot read is the only way to know the persisted theme
/// before the first frame renders. Constructed exactly once by `AppEnvironment` — never from a view.
@MainActor
@Observable
final class ThemeController {
    private(set) var theme: ThemePreference

    private let client: MentoraClient

    init(client: MentoraClient, coldStartTheme: ThemePreference) {
        self.client = client
        self.theme = coldStartTheme
    }

    /// Updates local `@Observable` state immediately, then writes through the façade — never through
    /// the cold-start reader's own `IosPreferenceStore` instance (G1).
    ///
    /// T5 (D112): routed through `MentoraClient.setTheme(_:)` instead of the raw
    /// `sdk.user.setTheme.invoke(theme:)` call, so `.invoke` never appears outside
    /// `Support/SharedBridge/` (System Design § 2) — a deliberate, approved refactor, not a
    /// behavior change.
    func setTheme(_ theme: ThemePreference) {
        self.theme = theme
        client.setTheme(theme)
    }
}
