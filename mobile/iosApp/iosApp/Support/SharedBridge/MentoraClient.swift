import Foundation
import shared

/// The one boundary layer between SwiftUI and KMP (System Design § 2). `.invoke` appears ONLY in
/// this file and `FlowBridge.swift` -- every other file under `Features/`/`Components/` calls a
/// named method here instead.
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
struct MentoraClient {
    /// `internal` (not `private`) so `FlowBridge.swift`'s extension (same file target) can reach it.
    let sdk: MentoraSdk

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
}
