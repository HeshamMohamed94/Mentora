import Foundation
import shared

// Phase 5 Task T5 (slice 1 of 2). This slice covers auth-state + locale flow adapters only;
// `aiStream(...)` (Flow-returning, kind (c) in System Design § 7) is slice 2.

extension MentoraClient {

    /// System Design § 2 item 3 / § 7(c). PROVEN real: `SkieSwiftStateFlow<any AuthState>` is a
    /// genuine `AsyncSequence` -- confirmed by a real CI compiler error and already consumed with
    /// `for await` by `SessionController` in CI-green code. `AuthState` is a sealed Kotlin
    /// interface/class hierarchy, hence `any`.
    func authStates() -> SkieSwiftStateFlow<any AuthState> {
        sdk.auth.observeAuthState.invoke()
    }

    /// Synchronous current-value read -- `SkieSwiftStateFlow` exposes `final public var value: T { get }`
    /// per the real captured `.swiftinterface`. Verify at CI; if it fails to compile, delete this
    /// method -- nothing in this slice depends on it existing.
    func currentAuthState() -> any AuthState {
        sdk.auth.observeAuthState.invoke().value
    }

    /// `AppLocale` is a Kotlin `enum class` -> a genuine Obj-C class, so no `any` needed here.
    func localeChanges() -> SkieSwiftStateFlow<AppLocale> {
        sdk.user.observeLocale.invoke()
    }

    func currentLocale() -> AppLocale {
        sdk.user.observeLocale.invoke().value
    }
}
