import Foundation
import Observation
import shared

// Phase 5 Task T4b (D108) — session lifecycle + B10 Keychain-failure channel (System Design § 9/§ 9.1).

/// T4b's local, `@Observable` mirror of the real `shared.AuthState` sealed hierarchy — named
/// distinctly (not `AuthState`) purely so call sites never need to disambiguate against the
/// Kotlin-bridged protocol of the same short name.
enum SessionAuthState {
    case unknown
    case unauthenticated
    case authenticated(user: SessionUser?)
}

/// Mirrors `AuthFacade.observeAuthState` into `@Observable` state the view tree reads (§ 9 step 3),
/// drives the once-per-transition profile backfill (§ 9 step 4), and captures B10 Keychain failures
/// into observable state for a later UI surface to read (§ 9.1). Constructed exactly once by
/// `AppEnvironment` — never from a view.
@MainActor
@Observable
final class SessionController {
    private(set) var authState: SessionAuthState = .unknown
    private(set) var profile: User?
    private(set) var keychainFailure: KeychainFailure?

    var isAuthenticated: Bool {
        if case .authenticated = authState { true } else { false }
    }

    private let sdk: MentoraSdk
    private var authStateWatcher: Task<Void, Never>?
    private var keychainFailureWatcher: Task<Void, Never>?

    /// Monotonic session-generation counter (D108 fix round #2, Fix A). This is a cross-account
    /// data-isolation guard, not a dedup/perf optimization — do not simplify it away. Bumped on
    /// every `apply(_:)` call (even a same-state re-application) so any in-flight `getProfile` fetch
    /// started under a previous generation can recognize itself as stale and discard its result
    /// instead of writing another account's profile into current state. Concrete scenario this
    /// prevents: user A's `.authenticated(user: nil)` starts a fetch; A logs out and user B logs in
    /// and reaches `.authenticated(user: nil)` too, starting its own fetch; if A's fetch resolves
    /// after B's transition, A's profile must not land in `self.profile` while the UI shows B.
    private var authGeneration = 0
    /// The generation whose `getProfile` fetch is currently in flight, or `nil` if none is. Keyed by
    /// generation (not a bare `Bool`) so a still-in-flight previous generation's task can never block
    /// a new generation's legitimately-needed fetch, and so that task's `defer` only clears this back
    /// to `nil` if it still refers to that same generation (never clobbering a newer one).
    private var fetchingGeneration: Int?

    init(sdk: MentoraSdk) {
        self.sdk = sdk

        // § 9 step 3. `observeAuthState.invoke()` returns a genuine `SkieSwiftStateFlow<any AuthState>`
        // (a real `AsyncSequence`, confirmed by CI run #8's compiler error — see `DECISIONS_LOG.md` D108
        // fix round #4). `SessionController` is `@MainActor`-isolated and this `Task { }` literal is
        // created from a `@MainActor` synchronous context (`init`), so Swift infers the task closure's
        // isolation from its enclosing context — every resumed iteration of `for await` already runs
        // back on the main actor, with no manual thread-hop needed.
        authStateWatcher = Task { [weak self] in
            for await state in sdk.auth.observeAuthState.invoke() {
                self?.apply(state)
            }
        }

        // § 9.1 — sanctioned non-façade entry point #6 (A2), observed exactly once, here.
        // `KeychainStatus.shared.failures` returns a genuine `SkieSwiftSharedFlow<KeychainFailure>` (same
        // real-`AsyncSequence` confirmation as above).
        keychainFailureWatcher = Task { [weak self] in
            for await failure in KeychainStatus.shared.failures {
                self?.keychainFailure = failure
            }
        }
    }

    /// Applies a raw `shared.AuthState` into this controller's `@Observable` mirror. Called from the
    /// live `observeAuthState` subscription above only — `AuthRepositoryImpl.restoreSession` (Kotlin)
    /// already calls `sessionManager.setState(state)` before returning, so that subscription alone
    /// observes a cold-start restore too (D108 fix round, Fix 6: a second, manual call to this method
    /// used to also happen from `AppEnvironment`'s bootstrap task, which was redundant and raced with
    /// this one — removed).
    func apply(_ state: AuthState) {
        authGeneration += 1
        switch onEnum(of: state) {
        case .authenticated(let authenticated):
            authState = .authenticated(user: authenticated.user)
            // Legitimate after a token-only restore — `TokenStorage` carries no identity (§ 9 step 4).
            if authenticated.user == nil {
                fetchProfileIfNeeded()
            }
        case .unauthenticated:
            authState = .unauthenticated
            // D108 fix round (Fix 7): a stale `profile` must not survive logout. Concretely: user A logs
            // out, user B logs in — between B's `.authenticated(user: nil)` and the `getProfile`
            // round-trip completing, any UI reading `profile` would otherwise render user A's data.
            profile = nil
        case .unknown:
            authState = .unknown
            // Symmetry with `.unauthenticated` above — `.unknown` never legitimately carries a previous
            // session's profile either.
            profile = nil
        }
    }

    /// § 9 step 4: called at most once per `.authenticated(user: nil)` transition (guarded by
    /// `fetchingGeneration`). A failure leaves `profile`/`authState` unchanged — no retry, no loop.
    ///
    /// The `generation` capture/guard below is the cross-account data-isolation fix from D108 fix
    /// round #2 (Fix A) — see `authGeneration`'s doc comment. Do not remove it as a "simplification":
    /// without it, a slow fetch from a previous, already-logged-out session can resolve after a new
    /// session has started and silently overwrite that new session's profile with stale data.
    private func fetchProfileIfNeeded() {
        let generation = authGeneration
        guard fetchingGeneration != generation else { return }
        fetchingGeneration = generation
        Task { [weak self] in
            guard let self else { return }
            defer {
                // Only clear this generation's own in-flight marker — never a newer generation's.
                if self.fetchingGeneration == generation {
                    self.fetchingGeneration = nil
                }
            }
            guard let result = try? await self.sdk.user.getProfile.invoke() else { return }
            // Discard silently if a newer session has since started — this is expected/normal, not
            // an error condition.
            guard generation == self.authGeneration else { return }
            if case .success(let success) = onEnum(of: result) {
                self.profile = success.data
            }
        }
    }
}
