import Foundation
import shared

/// The one boundary layer between SwiftUI and KMP (System Design § 2). `.invoke` appears ONLY in
/// this file -- every other file under `Features/`/`Components/` calls a named method here instead.
///
/// A `struct`, not a class: value semantics, trivially passed into screen-model initializers
/// (System Design § 3.1). NOT `@MainActor`, NOT `Sendable` -- `project.yml` sets
/// `SWIFT_VERSION: "5.0"` (strict concurrency off); do not "fix" this by adding either annotation.
///
/// Cancellation: a suspend call's SKIE-generated `async throws` wrapper converts a Kotlin
/// `CancellationException` into a thrown Swift error, which this bridge does NOT catch or
/// specially wrap -- it just propagates. Callers write
/// `catch let e as MentoraError { ... } catch { /* cancelled, or a lower-level throw */ }`.
///
/// Seam definitions (System Design § 2 item 4 -- per-screen closures/protocols over this client)
/// are deliberately NOT part of this file; each screen task declares its own narrow seam.
///
/// This slice (T5, slice 1 of 2) covers `auth` + `user` only -- the other 8 façades
/// (catalog/enrollment/progress/quiz/certificates/learningPaths/media/aiTutor) are a follow-up
/// slice, added the same way (one method per use case actually used).
///
/// Review fix round (D112, Fix 6): `FlowBridge.swift`'s four flow/state-read methods
/// (`authStates`/`currentAuthState`/`localeChanges`/`currentLocale`) were folded directly into this
/// file (that separate file is deleted) specifically so `sdk` below could become `private` --
/// tightening the `.invoke` boundary from a grep-enforced convention to a compiler-enforced one.
/// Nothing outside this file's own methods reads `sdk` (confirmed by grep before making this change).
struct MentoraClient {
    private let sdk: MentoraSdk

    init(sdk: MentoraSdk) {
        self.sdk = sdk
    }

    // MARK: - auth

    func register(email: String, password: String, name: String) async throws -> SessionUser {
        try ApiResultBridge.unwrap(try await sdk.auth.register_.invoke(email: email, password: password, name: name))
    }

    func login(email: String, password: String) async throws -> SessionUser {
        try ApiResultBridge.unwrap(try await sdk.auth.login.invoke(email: email, password: password))
    }

    func logout() async throws {
        try ApiResultBridge.unwrapVoid(try await sdk.auth.logout.invoke())
    }

    func refreshSession() async throws {
        try ApiResultBridge.unwrapVoid(try await sdk.auth.refreshSession.invoke())
    }

    /// Returns `AuthState` directly -- NOT wrapped in `ApiResult` (verified: `RestoreSessionUseCase`'s
    /// real signature is `invoke() async throws -> shared.AuthState`).
    func restoreSession() async throws -> AuthState {
        try await sdk.auth.restoreSession.invoke()
    }

    // MARK: - user

    func profile() async throws -> User {
        try ApiResultBridge.unwrap(try await sdk.user.getProfile.invoke())
    }

    func updateProfile(name: String) async throws -> User {
        try ApiResultBridge.unwrap(try await sdk.user.updateProfile.invoke(name: name))
    }

    func setLocale(_ locale: AppLocale) async throws {
        try ApiResultBridge.unwrapVoid(try await sdk.user.setLocale.invoke(locale: locale))
    }

    /// Synchronous, no `ApiResult` wrapper -- a local preference write.
    func setTheme(_ theme: ThemePreference) {
        sdk.user.setTheme.invoke(theme: theme)
    }

    // Deliberately NOT exposed (do not add these, even though they exist on `shared`):
    // `SetLocaleUseCase.onLogin(accountPreferredLocale:)` / `.onRegister()` -- these are called by
    // `LoginUseCase`/`RegisterUseCase` internally on the Kotlin side already; exposing and calling
    // them again from Swift would double-apply locale precedence (an F6 violation).

    // MARK: - flow/state reads (formerly `FlowBridge.swift`, folded in per D112 Fix 6)
    //
    // This slice covers auth-state + locale flow adapters only; `aiStream(...)` (Flow-returning,
    // kind (c) in System Design § 7) is slice 2.

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
