package com.mentora.shared.data.network

/**
 * The full error-code taxonomy the backend actually emits, grep-verified against every
 * `ApiException.*` construction call site under `backend/src/main/kotlin/com/mentora/backend/`
 * (see `execution/PHASE_3_KMP_PLAN.md` Task 2 for the exact codes and how they were derived).
 *
 * `RATE_LIMITED_AUTH` and `RATE_LIMITED_AI_TUTOR` are never present in a real backend JSON body —
 * a `429` response has no envelope at all (Ktor's `RateLimit` plugin, no `StatusPages` handler for
 * it). They exist here because the shared client *synthesizes* them client-side from
 * status+request-path per Decision C2 — see [synthesizeRateLimitedFailure].
 *
 * [Unknown] is the forward-compatible fallback for any `code` string not in this list, so a future
 * backend-added code never crashes deserialization/mapping.
 *
 * `code` (this type) is the contract every caller is expected to branch on. The sibling
 * `message` carried on [ApiResult.Failure] is diagnostic-only — never the primary signal, and
 * never assumed to be localized, stable, or safe to show verbatim as UI copy.
 */
sealed class ApiErrorCode(val wire: String) {
    data object ValidationError : ApiErrorCode("VALIDATION_ERROR")
    data object AuthInvalidCredentials : ApiErrorCode("AUTH_INVALID_CREDENTIALS")
    data object AuthTokenExpired : ApiErrorCode("AUTH_TOKEN_EXPIRED")
    data object AuthTokenInvalid : ApiErrorCode("AUTH_TOKEN_INVALID")
    data object ForbiddenRole : ApiErrorCode("FORBIDDEN_ROLE")
    data object ForbiddenNotOwner : ApiErrorCode("FORBIDDEN_NOT_OWNER")
    data object ForbiddenNotEnrolled : ApiErrorCode("FORBIDDEN_NOT_ENROLLED")
    data object ForbiddenCsrf : ApiErrorCode("FORBIDDEN_CSRF")
    data object CourseNotFound : ApiErrorCode("COURSE_NOT_FOUND")
    data object SectionNotFound : ApiErrorCode("SECTION_NOT_FOUND")
    data object LessonNotFound : ApiErrorCode("LESSON_NOT_FOUND")
    data object CategoryNotFound : ApiErrorCode("CATEGORY_NOT_FOUND")
    data object QuizNotFound : ApiErrorCode("QUIZ_NOT_FOUND")
    data object AttemptNotFound : ApiErrorCode("ATTEMPT_NOT_FOUND")
    data object CertificateNotFound : ApiErrorCode("CERTIFICATE_NOT_FOUND")
    data object LearningPathNotFound : ApiErrorCode("LEARNING_PATH_NOT_FOUND")
    data object MediaNotFound : ApiErrorCode("MEDIA_NOT_FOUND")
    data object UserNotFound : ApiErrorCode("USER_NOT_FOUND")
    data object EmailAlreadyRegistered : ApiErrorCode("EMAIL_ALREADY_REGISTERED")
    data object CategoryInUse : ApiErrorCode("CATEGORY_IN_USE")
    data object RateLimitedAuth : ApiErrorCode("RATE_LIMITED_AUTH")
    data object RateLimitedAiTutor : ApiErrorCode("RATE_LIMITED_AI_TUTOR")
    data object InternalError : ApiErrorCode("INTERNAL_ERROR")

    /** Forward-compatible fallback — [raw] preserves the exact wire string for diagnostics. */
    data class Unknown(val raw: String) : ApiErrorCode(raw)

    companion object {
        private val known: List<ApiErrorCode> = listOf(
            ValidationError, AuthInvalidCredentials, AuthTokenExpired, AuthTokenInvalid,
            ForbiddenRole, ForbiddenNotOwner, ForbiddenNotEnrolled, ForbiddenCsrf,
            CourseNotFound, SectionNotFound, LessonNotFound, CategoryNotFound, QuizNotFound,
            AttemptNotFound, CertificateNotFound, LearningPathNotFound, MediaNotFound,
            UserNotFound, EmailAlreadyRegistered, CategoryInUse, RateLimitedAuth,
            RateLimitedAiTutor, InternalError,
        )

        /** Maps a raw wire `code` string to a known [ApiErrorCode], or [Unknown] if unrecognized. */
        fun fromWire(raw: String): ApiErrorCode = known.firstOrNull { it.wire == raw } ?: Unknown(raw)
    }
}
