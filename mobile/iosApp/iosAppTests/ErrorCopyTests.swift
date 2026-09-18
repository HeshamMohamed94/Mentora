import XCTest
@testable import iosApp
import shared

// Phase 5 Task T5 (slice 1 of 2) — a hardcoded table of all 24 real `ApiErrorCode` cases (23 known +
// `.Unknown(raw:)`), asserting `ErrorCopy.key(for:)` against the mapping in `Support/ErrorCopy.swift`.
// `.wire` values below are read verbatim from the real source: `mobile/shared/src/commonMain/kotlin/
// com/mentora/shared/data/network/ApiErrorCode.kt`.
final class ErrorCopyTests: XCTestCase {

    private static let knownCodesAndExpectations: [(code: ApiErrorCode, wire: String, key: String)] = [
        (ApiErrorCode.ValidationError(), "VALIDATION_ERROR", "error_validation"),
        (ApiErrorCode.AuthInvalidCredentials(), "AUTH_INVALID_CREDENTIALS", "error_auth_invalid_credentials"),
        (ApiErrorCode.AuthTokenExpired(), "AUTH_TOKEN_EXPIRED", "error_auth_token_expired"),
        (ApiErrorCode.AuthTokenInvalid(), "AUTH_TOKEN_INVALID", "error_auth_token_invalid"),
        (ApiErrorCode.ForbiddenRole(), "FORBIDDEN_ROLE", "error_forbidden_role"),
        (ApiErrorCode.ForbiddenNotOwner(), "FORBIDDEN_NOT_OWNER", "error_forbidden_not_owner"),
        (ApiErrorCode.ForbiddenNotEnrolled(), "FORBIDDEN_NOT_ENROLLED", "error_forbidden_not_enrolled"),
        (ApiErrorCode.ForbiddenCsrf(), "FORBIDDEN_CSRF", "error_forbidden_csrf"),
        (ApiErrorCode.CourseNotFound(), "COURSE_NOT_FOUND", "error_course_not_found"),
        (ApiErrorCode.SectionNotFound(), "SECTION_NOT_FOUND", "error_section_not_found"),
        (ApiErrorCode.LessonNotFound(), "LESSON_NOT_FOUND", "error_lesson_not_found"),
        (ApiErrorCode.CategoryNotFound(), "CATEGORY_NOT_FOUND", "error_category_not_found"),
        (ApiErrorCode.QuizNotFound(), "QUIZ_NOT_FOUND", "error_quiz_not_found"),
        (ApiErrorCode.AttemptNotFound(), "ATTEMPT_NOT_FOUND", "error_attempt_not_found"),
        (ApiErrorCode.CertificateNotFound(), "CERTIFICATE_NOT_FOUND", "error_certificate_not_found"),
        (ApiErrorCode.LearningPathNotFound(), "LEARNING_PATH_NOT_FOUND", "error_learning_path_not_found"),
        (ApiErrorCode.MediaNotFound(), "MEDIA_NOT_FOUND", "error_media_not_found"),
        (ApiErrorCode.UserNotFound(), "USER_NOT_FOUND", "error_user_not_found"),
        (ApiErrorCode.EmailAlreadyRegistered(), "EMAIL_ALREADY_REGISTERED", "error_email_already_registered"),
        (ApiErrorCode.CategoryInUse(), "CATEGORY_IN_USE", "error_category_in_use"),
        (ApiErrorCode.RateLimitedAuth(), "RATE_LIMITED_AUTH", "error_rate_limited_auth"),
        (ApiErrorCode.RateLimitedAiTutor(), "RATE_LIMITED_AI_TUTOR", "error_rate_limited_ai_tutor"),
        (ApiErrorCode.InternalError(), "INTERNAL_ERROR", "error_internal"),
    ]

    func testEachKnownCodeMapsToExpectedKey() {
        for entry in Self.knownCodesAndExpectations {
            XCTAssertEqual(ErrorCopy.key(for: entry.code), entry.key, "wrong key for \(entry.wire)")
        }
    }

    func testEachKnownCodeWireMatchesRealKotlinSource() {
        for entry in Self.knownCodesAndExpectations {
            XCTAssertEqual(entry.code.wire, entry.wire)
        }
    }

    func test23KnownCodesMapToDistinctKeys() {
        let keys = Self.knownCodesAndExpectations.map(\.key)
        XCTAssertEqual(Set(keys).count, 23, "the 23 known codes must map to 23 distinct keys")
    }

    // Review fix round (D112): the two tests above only check a property of this file's OWN fixture
    // table (its literal `key` column), never `ErrorCopy` itself -- a future person could add an
    // `ApiErrorCode` case, forget to update `ErrorCopy.allKeys`, and every test here would stay green.
    // This is the real invariant: every key `ErrorCopy.key(for:)` genuinely returns for the 23 known
    // codes must equal `ErrorCopy.allKeys` exactly (as sets).
    func testKeyForRealMatchesAllKeysExactly() {
        let realKeys = Set(Self.knownCodesAndExpectations.map { ErrorCopy.key(for: $0.code) })
        XCTAssertEqual(realKeys, ErrorCopy.allKeys)
    }

    func testUnknownDeliberatelySharesErrorInternalWithInternalError() {
        let unknown = ApiErrorCode.Unknown(raw: "SOME_FUTURE_CODE")
        XCTAssertEqual(ErrorCopy.key(for: unknown), "error_internal")
        XCTAssertEqual(ErrorCopy.key(for: unknown), ErrorCopy.key(for: ApiErrorCode.InternalError()),
                        "Unknown and InternalError are the one sanctioned key collision")
    }

    func testAllKeysCountIs23AndEveryKeyMatchesPattern() {
        XCTAssertEqual(ErrorCopy.allKeys.count, 23)
        let pattern = try! NSRegularExpression(pattern: "^error_[a-z_]+$")
        for key in ErrorCopy.allKeys {
            let range = NSRange(key.startIndex..., in: key)
            XCTAssertNotNil(pattern.firstMatch(in: key, range: range), "\(key) does not match error_[a-z_]+")
        }
    }

    func testIsConnectivityFailureTrueOnlyForSynthesizedNetworkError() {
        XCTAssertTrue(ErrorCopy.isConnectivityFailure(ApiErrorCode.Unknown(raw: "NETWORK_ERROR")))
        XCTAssertFalse(ErrorCopy.isConnectivityFailure(ApiErrorCode.Unknown(raw: "SOME_OTHER_UNKNOWN")))
        XCTAssertFalse(ErrorCopy.isConnectivityFailure(ApiErrorCode.InternalError()))
    }

    func testKeyForMentoraErrorDelegatesToCode() {
        let error = MentoraError(code: ApiErrorCode.CourseNotFound(), message: "m")
        XCTAssertEqual(ErrorCopy.key(for: error), "error_course_not_found")
    }
}
