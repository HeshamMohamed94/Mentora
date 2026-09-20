import Foundation
import Observation
import shared

// Phase 5 Task T10 — Register's screen model (`ux/SCREEN_UX_SPECS.md § 7`, D51,
// `PHASE_5_ACCEPTANCE_CRITERIA.md` B2/B8/B9). `@MainActor @Observable final class`, same established
// shape as `LoginModel`/`SessionController`/`TabRouter`/`LocaleController`/`ThemeController`.

/// `RegisterModel`'s narrow dependency seam over `MentoraClient` (System Design § 3.1 rule 1) — mirrors
/// `LoginSubmitting`'s precedent, one method wide.
protocol RegisterSubmitting {
    func register(email: String, password: String, name: String) async throws -> SessionUser
}

extension MentoraClient: RegisterSubmitting {}

/// Register's own field values + in-flight/error state. `emailErrorKey`/`passwordErrorKey` are the two
/// actionable, field-specific slots `ux/SCREEN_UX_SPECS.md § 7`/D51 define; everything else falls
/// through to `generalErrorKey` (B9's distinctness, e.g. `RATE_LIMITED_AUTH`, falls out of routing
/// through `ErrorCopy.key(for:)` rather than a hand-written mapping). Every key here is a
/// `Localizable.xcstrings` KEY, never resolved text.
@MainActor
@Observable
final class RegisterModel {
    /// T10 review fix: clears the general banner on edit, mirroring `onRegisterNameChange`
    /// (`AuthViewModel.kt`) exactly -- a name change is unrelated to the email/password field slots,
    /// so only `generalErrorKey` clears here, not `emailErrorKey`/`passwordErrorKey`.
    var name: String = "" {
        didSet { generalErrorKey = nil }
    }
    /// T10 review fix: clears its own field error (and the general banner, in case a prior failure
    /// happened to be the general kind) the instant the user edits it again — mirrors Android's
    /// `onRegisterEmailChange` (`AuthViewModel.kt`) exactly; without this, a stale "Email already
    /// registered" error stayed pinned under the field while the user typed a different address.
    var email: String = "" {
        didSet {
            emailErrorKey = nil
            generalErrorKey = nil
        }
    }
    /// Same fix, mirroring `onRegisterPasswordChange`.
    var password: String = "" {
        didSet {
            passwordErrorKey = nil
            generalErrorKey = nil
        }
    }
    private(set) var isLoading = false
    private(set) var emailErrorKey: String?
    private(set) var passwordErrorKey: String?
    private(set) var generalErrorKey: String?

    private let client: RegisterSubmitting

    init(client: RegisterSubmitting) {
        self.client = client
    }

    /// Mirrors Android's `AuthViewModel.register()` exactly — including its blank-field guard AND its
    /// deliberate absence of any Swift/Kotlin-caller-side pre-validation.
    ///
    /// T10 REVIEW FIX: an earlier draft of this method called `EmailValidator.shared.validate(raw:)`/
    /// `PasswordValidator.shared.validate(password:)` directly from Swift before the network call, on
    /// the theory that this was a genuine fast-fail UX improvement Android itself lacks. Mandatory
    /// review found that theory false: `mobile/shared/.../domain/usecase/auth/RegisterUseCase.kt`
    /// ALREADY runs both validators, before touching the network, and already returns a
    /// `ApiErrorCode.ValidationError` failure with `httpStatus = 0` and the exact same
    /// `fields["email"]="INVALID"`/`fields["password"]="WEAK"` shape `route(_:)` below already handles
    /// — confirmed by reading that Kotlin file directly. The Swift-side pre-validation this method used
    /// to perform was therefore a real, needless duplication of business logic `shared` already owns
    /// (this project's own standing KMP-parity rule: "never duplicate business logic in Swift where KMP
    /// already owns it"), AND a real, undisclosed 7th non-façade `shared` entry point beyond criterion
    /// A2's stated-exhaustive 6-entry allowlist (`EmailValidator.shared`/`PasswordValidator.shared`
    /// calls direct from `Features/`). Removed entirely — `client.register(email:password:name:)` is
    /// now called with EXACTLY the raw typed values (matching `AuthViewModel.kt`'s own
    /// `sdk.auth.register(current.email, ...)` call precisely), and `RegisterUseCase`'s own internal
    /// email normalization happens invisibly to this Swift layer, exactly as it always has for every
    /// other `MentoraClient` call in this codebase. B2's "inline validation via shared's
    /// EmailValidator/PasswordValidator" requirement is still fully satisfied — those validators still
    /// drive every inline field error a user ever sees, they now just run inside `shared` (where B2
    /// already places them) rather than a redundant second time in Swift.
    ///
    /// On failure, `route(_:)` below replicates Android's `mapRegisterFailure` exactly (the D51
    /// pattern) — unchanged by this fix, since `RegisterUseCase`'s local-validation failure and the
    /// server's own authoritative failure arrive through the identical `MentoraError`/`fields` shape,
    /// indistinguishable to this method and correctly so. On success, or on a non-`MentoraError` throw
    /// (cancellation), only `isLoading` resets — same B8/cancellation conventions as
    /// `LoginModel.submit()`.
    func submit() async {
        guard !isLoading,
              !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !password.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        else { return }

        emailErrorKey = nil
        passwordErrorKey = nil
        generalErrorKey = nil
        isLoading = true

        do {
            _ = try await client.register(email: email, password: password, name: name)
            isLoading = false
        } catch let error as MentoraError {
            isLoading = false
            route(error)
        } catch {
            // Cancellation (or another lower-level throw) — reset silently, never surface a message.
            isLoading = false
        }
    }

    /// Register's error-routing rule (`ux/SCREEN_UX_SPECS.md § 7`, D51), replicated EXACTLY from
    /// Android's `mapRegisterFailure`: `fields["email"] == "INVALID"` routes to the email slot;
    /// `EmailAlreadyRegistered` ALSO routes to the email slot even though it is never present in
    /// `fields` (the D51 precedent); `fields["password"] == "WEAK"` routes to the password slot;
    /// everything else — and ONLY if neither field slot was set — becomes `generalErrorKey` via
    /// `ErrorCopy.key(for:)` (B9).
    private func route(_ error: MentoraError) {
        var didSetEmailError = false
        var didSetPasswordError = false

        if error.fields?["email"] == "INVALID" {
            emailErrorKey = "auth_email_invalid"
            didSetEmailError = true
        } else if case .emailAlreadyRegistered = onEnum(of: error.code) {
            emailErrorKey = "error_email_already_registered"
            didSetEmailError = true
        }

        if error.fields?["password"] == "WEAK" {
            passwordErrorKey = "auth_password_weak"
            didSetPasswordError = true
        }

        if !didSetEmailError, !didSetPasswordError {
            generalErrorKey = ErrorCopy.key(for: error)
        }
    }
}
