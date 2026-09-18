import Foundation
import Observation
import shared

// Phase 5 Task T4b (D108) — locale mirroring + once-per-install initial-locale seeding (§ 9/§ 9.1).

/// Mirrors `UserFacade.observeLocale` into `@Observable` state, and seeds the initial locale exactly
/// once per install — skipped entirely once the user is already authenticated (an authenticated
/// user's locale is already known server/Koin-side, so re-seeding would be both redundant and wrong).
/// Constructed exactly once by `AppEnvironment` — never from a view.
@MainActor
@Observable
final class LocaleController {
    private(set) var currentLocale: AppLocale?

    private let client: MentoraClient
    private var localeWatcher: Task<Void, Never>?

    private static let hasSeededInitialLocaleKey = "com.mentora.ios.hasSeededInitialLocale"

    init(client: MentoraClient) {
        self.client = client
        // `observeLocale.invoke()` follows the same façade/use-case shape as `observeAuthState`
        // (`ObserveLocaleUseCase(): StateFlow<AppLocale>` per System Design § 7's table), so it returns
        // a genuine `SkieSwiftStateFlow<AppLocale>` `AsyncSequence` the same way — see `DECISIONS_LOG.md`
        // D108 fix round #4. `LocaleController` is `@MainActor`-isolated and this `Task { }` is created
        // from a `@MainActor` synchronous context (`init`), so no manual thread-hop is needed.
        //
        // T5 (D112): routed through `MentoraClient.localeChanges()` (`FlowBridge.swift`) instead of
        // the raw `sdk.user.observeLocale.invoke()` call, so `.invoke` never appears outside
        // `Support/SharedBridge/` (System Design § 2) — a deliberate, approved refactor, not a
        // behavior change.
        localeWatcher = Task { [weak self] in
            for await locale in client.localeChanges() {
                self?.currentLocale = locale
            }
        }
    }

    /// Called exactly once from `AppEnvironment`'s single bootstrap `Task`, after `restoreSession()`
    /// resolves (§ 9 step 2) — never from a view's `.task`/`onAppear`. `SetLocaleUseCase.invoke(locale:)`
    /// is itself a suspend function (real, CI-run-#6-captured `Skie_Suspend__6__invoke` wrapper — not
    /// the synchronous call the plan text implied), so a failure just leaves the once-per-install flag
    /// unset, retrying on the next cold start rather than looping now.
    ///
    /// T5 (D112): routed through `MentoraClient.setLocale(_:)` instead of the raw
    /// `sdk.user.setLocale.invoke(locale:)` + `onEnum` unwrap, so `.invoke` never appears outside
    /// `Support/SharedBridge/` (System Design § 2) — the once-per-install-flag semantics are unchanged.
    func seedInitialLocaleIfNeeded(isAuthenticated: Bool, defaults: UserDefaults = .standard) async {
        guard !isAuthenticated else { return }
        guard !defaults.bool(forKey: Self.hasSeededInitialLocaleKey) else { return }

        let resolved = LocaleResolverKt.resolveInitialLocale(systemLocales: Locale.preferredLanguages)
        do {
            try await client.setLocale(resolved)
        } catch {
            return
        }
        defaults.set(true, forKey: Self.hasSeededInitialLocaleKey)
        // D108 fix round (Fix 9): flush synchronously rather than relying on `set(_:forKey:)`'s
        // eventual/async flush. Android's own equivalent (`androidApp/.../locale/LocaleController.kt`)
        // deliberately uses `commit()` instead of `apply()` for this exact flag, with its own kdoc
        // explaining why: an async write can be lost to process death before the next flush, silently
        // re-running the seed. `synchronize()` is `UserDefaults`'s direct analogue for that same
        // "must not silently re-run" requirement — soft-deprecated, but still the right tool here.
        defaults.synchronize()
    }
}
