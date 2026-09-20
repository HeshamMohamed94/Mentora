import XCTest
@testable import iosApp
import shared

// Phase 5 Task T10 -- `RegisterModel`'s pure state-machine behavior (B2/B8/B9, the D51 field-routing
// pattern), independent of any real `MentoraSdk`/network. Fakes `RegisterSubmitting` directly -- the one
// narrow seam `RegisterModel.init(client:)` takes (System Design § 3.1 rule 1).
@MainActor
final class RegisterModelTests: XCTestCase {

    /// A CLASS (not a struct) fake -- needed so `callCount`/`lastEmail` survive being read back after
    /// `RegisterModel` stores this fake behind its `RegisterSubmitting` existential (a struct fake, as
    /// used by `LoginModelTests`, has no such need since none of Login's tests assert call counts).
    private final class FakeRegisterClient: RegisterSubmitting {
        var result: Result<SessionUser, Error>
        private(set) var callCount = 0
        private(set) var lastEmail: String?

        init(result: Result<SessionUser, Error>) {
            self.result = result
        }

        func register(email: String, password: String, name: String) async throws -> SessionUser {
            callCount += 1
            lastEmail = email
            return try result.get()
        }
    }

    private static func makeSessionUser() -> SessionUser {
        SessionUser(id: "u1", email: "ada@example.com", name: "Ada", role: .student, preferredLocale: nil)
    }

    /// A password satisfying `PasswordValidator`'s real rule (>= 8 chars, >= 1 letter, >= 1 digit).
    private static let validPassword = "password1"

    // MARK: - Blank-field guard: no submit, no state change

    func test_submitIsANoOpWhenAnyFieldIsBlank() async {
        let fake = FakeRegisterClient(result: .success(Self.makeSessionUser()))
        let model = RegisterModel(client: fake)
        model.name = ""
        model.email = "ada@example.com"
        model.password = Self.validPassword

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertNil(model.emailErrorKey)
        XCTAssertNil(model.passwordErrorKey)
        XCTAssertNil(model.generalErrorKey)
        XCTAssertEqual(fake.callCount, 0)
    }

    // MARK: - T10 review fix: local Swift-side pre-validation was REMOVED (see `RegisterModel.submit()`'s
    // own doc comment) -- `RegisterUseCase.kt` (`shared`) already runs `EmailValidator`/`PasswordValidator`
    // before any network call and returns the exact same `fields["email"]="INVALID"`/
    // `fields["password"]="WEAK"` shape a real server rejection would, indistinguishable to this model
    // and correctly so. The former `test_locallyInvalidEmailIsRejectedWithoutAnyNetworkCall`/
    // `test_locallyWeakPasswordIsRejectedWithoutAnyNetworkCall` tests asserted a Swift-side fast-fail
    // that no longer exists -- `test_serverFieldsEmailInvalidRoutesToEmailSlot`/
    // `test_serverFieldsPasswordWeakRoutesToPasswordSlot` below already cover the identical user-visible
    // outcome (the `fields`-routing behavior), which is now the ONLY path either kind of validation
    // failure reaches this model through.

    // MARK: - Success with valid inputs: clears loading, no field/general errors, real network call made

    func test_successfulRegisterWithValidInputsClearsLoadingWithNoErrors() async {
        let fake = FakeRegisterClient(result: .success(Self.makeSessionUser()))
        let model = RegisterModel(client: fake)
        model.name = "Ada"
        model.email = "ada@example.com"
        model.password = Self.validPassword

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertNil(model.emailErrorKey)
        XCTAssertNil(model.passwordErrorKey)
        XCTAssertNil(model.generalErrorKey)
        XCTAssertEqual(fake.callCount, 1)
    }

    // MARK: - T10 review fix: the RAW typed email is sent, matching Android's own `sdk.auth.register(...)`
    // call exactly -- any normalization is `RegisterUseCase.kt`'s (`shared`) job, invisible to this layer,
    // never this Swift model's own responsibility.

    func test_rawTypedEmailIsSentUnmodifiedNeverNormalizedInSwift() async {
        let fake = FakeRegisterClient(result: .success(Self.makeSessionUser()))
        let model = RegisterModel(client: fake)
        model.name = "Ada"
        model.email = "  Ada@Example.com  "
        model.password = Self.validPassword

        await model.submit()

        XCTAssertEqual(fake.lastEmail, "  Ada@Example.com  ", "RegisterModel must never normalize the email itself -- that is RegisterUseCase.kt's (shared) job.")
    }

    // MARK: - Server-side fields: ["email": "INVALID"] routes to the email slot

    func test_serverFieldsEmailInvalidRoutesToEmailSlot() async {
        let error = MentoraError(code: ApiErrorCode.ValidationError(), message: "m", fields: ["email": "INVALID"])
        let model = RegisterModel(client: FakeRegisterClient(result: .failure(error)))
        model.name = "Ada"
        model.email = "ada@example.com"
        model.password = Self.validPassword

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertEqual(model.emailErrorKey, "auth_email_invalid")
        XCTAssertNil(model.passwordErrorKey)
        XCTAssertNil(model.generalErrorKey)
    }

    // MARK: - Server-side fields: ["password": "WEAK"] routes to the password slot

    func test_serverFieldsPasswordWeakRoutesToPasswordSlot() async {
        let error = MentoraError(code: ApiErrorCode.ValidationError(), message: "m", fields: ["password": "WEAK"])
        let model = RegisterModel(client: FakeRegisterClient(result: .failure(error)))
        model.name = "Ada"
        model.email = "ada@example.com"
        model.password = Self.validPassword

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertNil(model.emailErrorKey)
        XCTAssertEqual(model.passwordErrorKey, "auth_password_weak")
        XCTAssertNil(model.generalErrorKey)
    }

    // MARK: - EMAIL_ALREADY_REGISTERED routes to the email slot (D51), despite never appearing in fields

    func test_emailAlreadyRegisteredRoutesToEmailSlotNotGeneral() async {
        let error = MentoraError(code: ApiErrorCode.EmailAlreadyRegistered(), message: "m")
        let model = RegisterModel(client: FakeRegisterClient(result: .failure(error)))
        model.name = "Ada"
        model.email = "ada@example.com"
        model.password = Self.validPassword

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertEqual(model.emailErrorKey, "error_email_already_registered")
        XCTAssertNil(model.passwordErrorKey)
        XCTAssertNil(model.generalErrorKey)
    }

    // MARK: - An unrelated failure code routes to the general slot, with neither field key set (B9-style)

    func test_unrelatedFailureRoutesToGeneralSlotWithNeitherFieldSet() async {
        let error = MentoraError(code: ApiErrorCode.InternalError(), message: "m")
        let model = RegisterModel(client: FakeRegisterClient(result: .failure(error)))
        model.name = "Ada"
        model.email = "ada@example.com"
        model.password = Self.validPassword

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertNil(model.emailErrorKey)
        XCTAssertNil(model.passwordErrorKey)
        XCTAssertEqual(model.generalErrorKey, "error_internal")
    }

    // MARK: - RATE_LIMITED_AUTH also routes to the general slot, with its OWN distinct copy (B9)

    func test_rateLimitedAuthRoutesToGeneralSlotWithDistinctCopy() async {
        let error = MentoraError(code: ApiErrorCode.RateLimitedAuth(), message: "m")
        let model = RegisterModel(client: FakeRegisterClient(result: .failure(error)))
        model.name = "Ada"
        model.email = "ada@example.com"
        model.password = Self.validPassword

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertNil(model.emailErrorKey)
        XCTAssertNil(model.passwordErrorKey)
        XCTAssertEqual(model.generalErrorKey, "error_rate_limited_auth")
        XCTAssertNotEqual(model.generalErrorKey, "error_internal")
    }

    // MARK: - A non-MentoraError throw (e.g. cancellation) resets loading silently, no message shown

    func test_nonMentoraErrorThrowResetsLoadingWithNoErrors() async {
        struct NotAMentoraError: Error {}
        let model = RegisterModel(client: FakeRegisterClient(result: .failure(NotAMentoraError())))
        model.name = "Ada"
        model.email = "ada@example.com"
        model.password = Self.validPassword

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertNil(model.emailErrorKey)
        XCTAssertNil(model.passwordErrorKey)
        XCTAssertNil(model.generalErrorKey)
    }

    // MARK: - T10 review fix: editing a field clears its own error (and the general banner) --
    // mirrors Android's `onRegisterEmailChange`/`onRegisterPasswordChange`/`onRegisterNameChange`
    // exactly. Without this, a stale error stayed pinned on screen while the user retyped.

    func test_editingEmailClearsEmailErrorAndGeneralError() async {
        let error = MentoraError(code: ApiErrorCode.EmailAlreadyRegistered(), message: "m")
        let model = RegisterModel(client: FakeRegisterClient(result: .failure(error)))
        model.name = "Ada"
        model.email = "ada@example.com"
        model.password = Self.validPassword
        await model.submit()
        XCTAssertEqual(model.emailErrorKey, "error_email_already_registered", "precondition")

        model.email = "ada2@example.com"

        XCTAssertNil(model.emailErrorKey)
        XCTAssertNil(model.generalErrorKey)
    }

    func test_editingPasswordClearsPasswordErrorAndGeneralError() async {
        let error = MentoraError(code: ApiErrorCode.ValidationError(), message: "m", fields: ["password": "WEAK"])
        let model = RegisterModel(client: FakeRegisterClient(result: .failure(error)))
        model.name = "Ada"
        model.email = "ada@example.com"
        model.password = Self.validPassword
        await model.submit()
        XCTAssertEqual(model.passwordErrorKey, "auth_password_weak", "precondition")

        model.password = "anotherPassword1"

        XCTAssertNil(model.passwordErrorKey)
        XCTAssertNil(model.generalErrorKey)
    }

    func test_editingNameClearsGeneralError() async {
        let error = MentoraError(code: ApiErrorCode.InternalError(), message: "m")
        let model = RegisterModel(client: FakeRegisterClient(result: .failure(error)))
        model.name = "Ada"
        model.email = "ada@example.com"
        model.password = Self.validPassword
        await model.submit()
        XCTAssertEqual(model.generalErrorKey, "error_internal", "precondition")

        model.name = "Ada Lovelace"

        XCTAssertNil(model.generalErrorKey)
    }

    /// T10 follow-up review fix: an earlier version of this test never set up a FIELD error at all
    /// (only `generalErrorKey`), so it could not actually detect the failure it claimed to guard
    /// against -- a name edit wrongly clearing `emailErrorKey`/`passwordErrorKey` too. This version sets
    /// up a real email-slot failure first, so `emailErrorKey`'s survival is genuinely exercised.
    func test_editingNameDoesNotClearEmailOrPasswordFieldErrors() async {
        let error = MentoraError(code: ApiErrorCode.EmailAlreadyRegistered(), message: "m")
        let model = RegisterModel(client: FakeRegisterClient(result: .failure(error)))
        model.name = "Ada"
        model.email = "ada@example.com"
        model.password = Self.validPassword
        await model.submit()
        XCTAssertEqual(model.emailErrorKey, "error_email_already_registered", "precondition")

        model.name = "Ada Lovelace"

        XCTAssertEqual(model.emailErrorKey, "error_email_already_registered", "a name edit must not clear an unrelated field's error.")
    }
}
