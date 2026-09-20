import XCTest
@testable import iosApp
import shared

// Phase 5 Task T10 -- `LoginModel`'s pure state-machine behavior (B1/B8/B9), independent of any real
// `MentoraSdk`/network. Fakes `LoginSubmitting` directly -- the one narrow seam `LoginModel.init(client:)`
// takes (System Design § 3.1 rule 1), matching this codebase's own established
// construct-model-with-a-fake test convention (`TabRouterTests.swift`/`AuthGateLogicTests.swift`).
@MainActor
final class LoginModelTests: XCTestCase {

    /// A CLASS (not a struct), matching `RegisterModelTests.FakeRegisterClient`'s reasoning -- needed so
    /// `callCount` survives being read back after `LoginModel` stores this fake behind its
    /// `LoginSubmitting` existential. T10 review fix: the original struct fake carried no counter, which
    /// made the blank-field-guard tests below vacuous (a `.success` fake produces the identical
    /// `isLoading == false, generalErrorKey == nil` end state whether or not the guard actually fired).
    private final class FakeLoginClient: LoginSubmitting {
        var result: Result<SessionUser, Error>
        private(set) var callCount = 0

        init(result: Result<SessionUser, Error>) {
            self.result = result
        }

        func login(email: String, password: String) async throws -> SessionUser {
            callCount += 1
            return try result.get()
        }
    }

    /// A minimal, real, constructible `SessionUser` -- `LoginModel` never reads any of its own fields,
    /// so its exact values are unimportant beyond type-checking (`SessionUser`'s real Swift shape is
    /// `id:email:name:role:preferredLocale:`, ported verbatim from
    /// `mobile/shared/.../auth/SessionUser.kt`).
    private static func makeSessionUser() -> SessionUser {
        SessionUser(id: "u1", email: "student@example.com", name: "Student", role: .student, preferredLocale: nil)
    }

    // MARK: - Blank-field guard: no submit, no state change

    func test_submitIsANoOpWhenEmailIsBlank() async {
        let fake = FakeLoginClient(result: .success(Self.makeSessionUser()))
        let model = LoginModel(client: fake)
        model.email = "   "
        model.password = "password1"

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertNil(model.generalErrorKey)
        XCTAssertEqual(fake.callCount, 0, "a blank email must never reach the network")
    }

    func test_submitIsANoOpWhenPasswordIsBlank() async {
        let fake = FakeLoginClient(result: .success(Self.makeSessionUser()))
        let model = LoginModel(client: fake)
        model.email = "student@example.com"
        model.password = ""

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertNil(model.generalErrorKey)
        XCTAssertEqual(fake.callCount, 0, "a blank password must never reach the network")
    }

    // MARK: - Success: clears loading, no error, no navigation of any kind (B8 is AuthGate's job alone)

    func test_successfulLoginClearsLoadingWithNoError() async {
        let model = LoginModel(client: FakeLoginClient(result: .success(Self.makeSessionUser())))
        model.email = "student@example.com"
        model.password = "password1"

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertNil(model.generalErrorKey)
    }

    // MARK: - B1: every failure is ONE general error -- there is no field-specific slot to route to

    func test_authInvalidCredentialsSetsGeneralErrorKey() async {
        let error = MentoraError(code: ApiErrorCode.AuthInvalidCredentials(), message: "m")
        let model = LoginModel(client: FakeLoginClient(result: .failure(error)))
        model.email = "student@example.com"
        model.password = "wrong-password"

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertEqual(model.generalErrorKey, "error_auth_invalid_credentials")
    }

    // MARK: - B9: RATE_LIMITED_AUTH renders its OWN distinct copy, never the credentials one

    func test_rateLimitedAuthSetsADistinctGeneralErrorKeyFromInvalidCredentials() async {
        let error = MentoraError(code: ApiErrorCode.RateLimitedAuth(), message: "m")
        let model = LoginModel(client: FakeLoginClient(result: .failure(error)))
        model.email = "student@example.com"
        model.password = "wrong-password"

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertEqual(model.generalErrorKey, "error_rate_limited_auth")
        XCTAssertNotEqual(model.generalErrorKey, "error_auth_invalid_credentials")
    }

    // MARK: - A non-MentoraError throw (e.g. cancellation) resets loading silently, no message shown

    func test_nonMentoraErrorThrowResetsLoadingWithNoError() async {
        struct NotAMentoraError: Error {}
        let model = LoginModel(client: FakeLoginClient(result: .failure(NotAMentoraError())))
        model.email = "student@example.com"
        model.password = "password1"

        await model.submit()

        XCTAssertFalse(model.isLoading)
        XCTAssertNil(model.generalErrorKey)
    }

    // MARK: - T10 review fix: editing either field clears a stale general error -- mirrors Android's
    // `onLoginEmailChange`/`onLoginPasswordChange` exactly. Without this, "Incorrect email or password"
    // stayed on screen the entire time the user retyped their password.

    func test_editingEmailClearsGeneralError() async {
        let error = MentoraError(code: ApiErrorCode.AuthInvalidCredentials(), message: "m")
        let model = LoginModel(client: FakeLoginClient(result: .failure(error)))
        model.email = "student@example.com"
        model.password = "wrong-password"
        await model.submit()
        XCTAssertEqual(model.generalErrorKey, "error_auth_invalid_credentials", "precondition")

        model.email = "student2@example.com"

        XCTAssertNil(model.generalErrorKey)
    }

    func test_editingPasswordClearsGeneralError() async {
        let error = MentoraError(code: ApiErrorCode.AuthInvalidCredentials(), message: "m")
        let model = LoginModel(client: FakeLoginClient(result: .failure(error)))
        model.email = "student@example.com"
        model.password = "wrong-password"
        await model.submit()
        XCTAssertEqual(model.generalErrorKey, "error_auth_invalid_credentials", "precondition")

        model.password = "correct-password1"

        XCTAssertNil(model.generalErrorKey)
    }
}
