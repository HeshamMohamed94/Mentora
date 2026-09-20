import Foundation
import Observation
import shared

// Phase 5 Task T10 — Login's screen model (`ux/SCREEN_UX_SPECS.md § 6`, `product/USER_FLOWS.md § 2`,
// `PHASE_5_ACCEPTANCE_CRITERIA.md` B1/B8/B9). `@MainActor @Observable final class`, matching this
// codebase's own established shape for every other screen/controller-level model
// (`SessionController`/`TabRouter`/`LocaleController`/`ThemeController`).

/// `LoginModel`'s narrow dependency seam over `MentoraClient` (System Design § 3.1 rule 1 — "either
/// `MentoraClient` itself, or a narrow per-domain protocol seam over it"). Lets `LoginModelTests.swift`
/// fake exactly one method, without needing a real `MentoraSdk`.
protocol LoginSubmitting {
    func login(email: String, password: String) async throws -> SessionUser
}

extension MentoraClient: LoginSubmitting {}

/// Login's own field values + in-flight/error state. Deliberately carries NO field-specific error slot
/// at all — `ux/SCREEN_UX_SPECS.md § 6`/`product/USER_FLOWS.md § 2` are explicit that a wrong email and
/// a wrong password are indistinguishable BY DESIGN (security), so EVERY failure — including the
/// generic `AuthInvalidCredentials` most login attempts will actually hit — becomes `generalErrorKey`,
/// never attributed to the email or password field alone (B1). This mirrors Android's own
/// `mapLoginFailure(failure) = failure.code` exactly: not hardcoded to always show the same copy —
/// `RATE_LIMITED_AUTH` still renders its own distinct message (B9) — just never field-specific. That
/// distinctness falls out for free from routing every failure through `ErrorCopy.key(for:)` (which
/// already maps `RATE_LIMITED_AUTH` to its own key) rather than this model hand-writing a second
/// code -> message mapping of its own.
@MainActor
@Observable
final class LoginModel {
    /// T10 review fix: clears `generalErrorKey` the instant the user edits either field again --
    /// mirrors Android's `onLoginEmailChange`/`onLoginPasswordChange` (`AuthViewModel.kt`) exactly;
    /// without this, a stale "Incorrect email or password" banner stayed on screen the entire time the
    /// user retyped their password.
    var email: String = "" {
        didSet { generalErrorKey = nil }
    }
    var password: String = "" {
        didSet { generalErrorKey = nil }
    }
    private(set) var isLoading = false
    /// A `Localizable.xcstrings` KEY, never resolved text — the view resolves it via
    /// `MentoraStrings.text(_:locale:)`.
    private(set) var generalErrorKey: String?

    private let client: LoginSubmitting

    init(client: LoginSubmitting) {
        self.client = client
    }

    /// Mirrors Android's `AuthViewModel.login()` guard/flow exactly: a no-op while already loading or
    /// either field is blank; on success, only `isLoading` resets — no navigation of any kind (B8:
    /// `AuthGate`'s own `.onChange(of: isAuthenticated)` watcher is the SOLE reactor to the resulting
    /// auth-state transition, once `SessionController`'s own state updates on its own). A non-
    /// `MentoraError` throw (e.g. a cancellation) resets `isLoading` silently, never shows a message —
    /// this codebase's own established `MentoraClient.swift`-documented convention:
    /// `catch let e as MentoraError { ... } catch { /* cancelled */ }`.
    func submit() async {
        guard !isLoading,
              !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return }

        isLoading = true
        generalErrorKey = nil

        do {
            _ = try await client.login(email: email, password: password)
            isLoading = false
        } catch let error as MentoraError {
            isLoading = false
            generalErrorKey = ErrorCopy.key(for: error)
        } catch {
            // Cancellation (or another lower-level throw) — reset silently, never surface a message.
            isLoading = false
        }
    }
}
