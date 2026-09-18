import Foundation
import shared

// Phase 5 Task T5 (slice 1 of 2) — see `SharedBridge/MentoraError.swift`'s header for the
// `SWIFT_VERSION: "5.0"` / no-strict-concurrency note; it applies to this file too.

/// The ONE `ApiErrorCode` -> localization-key mapping (System Design § 17; the iOS analogue of
/// Android's `ui/error/ApiErrorCopy.kt`). Returns KEYS ONLY -- `Localizable.xcstrings` arrives at a
/// later task, and `MentoraStrings.text(_:locale:)` (not yet built) is what will resolve these.
/// Keys are ported from Android's own `values/strings.xml` names verbatim so the two clients cannot
/// drift in copy (System Design § 12) -- Android's `ApiErrorCopy.kt` kdoc claims "22 known codes",
/// which is WRONG (the real Kotlin source has 23); trust the source, not that comment.
enum ErrorCopy {

    static func key(for code: ApiErrorCode) -> String {
        switch onEnum(of: code) {
        case .validationError:          return "error_validation"
        case .authInvalidCredentials:   return "error_auth_invalid_credentials"
        case .authTokenExpired:         return "error_auth_token_expired"
        case .authTokenInvalid:         return "error_auth_token_invalid"
        case .forbiddenRole:            return "error_forbidden_role"
        case .forbiddenNotOwner:        return "error_forbidden_not_owner"
        case .forbiddenNotEnrolled:     return "error_forbidden_not_enrolled"
        case .forbiddenCsrf:            return "error_forbidden_csrf"
        case .courseNotFound:           return "error_course_not_found"
        case .sectionNotFound:          return "error_section_not_found"
        case .lessonNotFound:           return "error_lesson_not_found"
        case .categoryNotFound:         return "error_category_not_found"
        case .quizNotFound:             return "error_quiz_not_found"
        case .attemptNotFound:          return "error_attempt_not_found"
        case .certificateNotFound:      return "error_certificate_not_found"
        case .learningPathNotFound:     return "error_learning_path_not_found"
        case .mediaNotFound:            return "error_media_not_found"
        case .userNotFound:             return "error_user_not_found"
        case .emailAlreadyRegistered:   return "error_email_already_registered"
        case .categoryInUse:            return "error_category_in_use"
        case .rateLimitedAuth:          return "error_rate_limited_auth"
        case .rateLimitedAiTutor:       return "error_rate_limited_ai_tutor"
        case .internalError:            return "error_internal"
        // Documented default (confirmed decision, do not add a dedicated NETWORK_ERROR key here --
        // Android has no offline/network string key at all, and the catalog must stay key-for-key
        // with Android per § 12). An unrecognized/synthesized code is, to the user, indistinguishable
        // from any other unexpected failure; `Unknown.raw` stays available for logs, never as copy.
        case .unknown:                  return "error_internal"
        }
    }

    static func key(for error: MentoraError) -> String { key(for: error.code) }

    /// `ApiClient` synthesizes transport/connectivity failures as `Unknown("NETWORK_ERROR")`.
    /// Exposed as a PREDICATE (not a copy key) so the offline affordance can be driven by connectivity
    /// state (e.g. `NWPathMonitor`, a later task) rather than by growing an iOS-only copy key that
    /// would need whitelisting in the future catalog-parity check.
    static func isConnectivityFailure(_ code: ApiErrorCode) -> Bool {
        if case .unknown(let u) = onEnum(of: code) { return u.raw == "NETWORK_ERROR" }
        return false
    }

    /// The retry affordance's key, ported from Android's `error_state_retry_label`.
    static let retryLabelKey = "error_state_retry_label"

    /// Every key this mapper can ever return -- consumed by a later task's catalog-parity check.
    static let allKeys: Set<String> = [
        "error_validation", "error_auth_invalid_credentials", "error_auth_token_expired",
        "error_auth_token_invalid", "error_forbidden_role", "error_forbidden_not_owner",
        "error_forbidden_not_enrolled", "error_forbidden_csrf", "error_course_not_found",
        "error_section_not_found", "error_lesson_not_found", "error_category_not_found",
        "error_quiz_not_found", "error_attempt_not_found", "error_certificate_not_found",
        "error_learning_path_not_found", "error_media_not_found", "error_user_not_found",
        "error_email_already_registered", "error_category_in_use", "error_rate_limited_auth",
        "error_rate_limited_ai_tutor", "error_internal",
    ]
}
