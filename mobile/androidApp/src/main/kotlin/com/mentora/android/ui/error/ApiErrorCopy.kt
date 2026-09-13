package com.mentora.android.ui.error

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mentora.android.R
import com.mentora.shared.data.network.ApiErrorCode

/**
 * T7 — the ONE central `ApiErrorCode` -> localized-string mapping, per
 * `execution/PHASE_4_ANDROID_PLAN.md` T7's own instruction that this be built once, completely, here,
 * and reused by every later task that renders an API error (Explore/Course Details/... — Tasks 9+),
 * never re-derived per screen.
 *
 * Covers every one of [ApiErrorCode]'s 22 known codes plus the forward-compatible [ApiErrorCode.Unknown]
 * fallback (which shares [R.string.error_internal]'s copy — an unrecognized code is, from the user's
 * perspective, indistinguishable from any other unexpected server failure; [ApiErrorCode.Unknown.raw]
 * remains available for diagnostics/logging, never surfaced as UI copy itself).
 *
 * [ApiResult.Failure.message][com.mentora.shared.data.network.ApiResult.Failure.message] is
 * diagnostic-only (that type's own kdoc) — every call site must resolve UI copy from
 * [ApiErrorCode] via this function, never from `message` directly.
 *
 * A `@Composable` function (rather than a `Context`-taking plain function) per the task's own
 * "Composable-friendly is probably cleaner given every consumer will be in Compose code" guidance —
 * every real call site (Login/Register here, Explore/Course Details/... later) is already inside a
 * `@Composable` screen, so `stringResource` is the natural, zero-boilerplate fit.
 */
@Composable
fun apiErrorMessage(code: ApiErrorCode): String = when (code) {
    ApiErrorCode.ValidationError -> stringResource(R.string.error_validation)
    ApiErrorCode.AuthInvalidCredentials -> stringResource(R.string.error_auth_invalid_credentials)
    ApiErrorCode.AuthTokenExpired -> stringResource(R.string.error_auth_token_expired)
    ApiErrorCode.AuthTokenInvalid -> stringResource(R.string.error_auth_token_invalid)
    ApiErrorCode.ForbiddenRole -> stringResource(R.string.error_forbidden_role)
    ApiErrorCode.ForbiddenNotOwner -> stringResource(R.string.error_forbidden_not_owner)
    ApiErrorCode.ForbiddenNotEnrolled -> stringResource(R.string.error_forbidden_not_enrolled)
    ApiErrorCode.ForbiddenCsrf -> stringResource(R.string.error_forbidden_csrf)
    ApiErrorCode.CourseNotFound -> stringResource(R.string.error_course_not_found)
    ApiErrorCode.SectionNotFound -> stringResource(R.string.error_section_not_found)
    ApiErrorCode.LessonNotFound -> stringResource(R.string.error_lesson_not_found)
    ApiErrorCode.CategoryNotFound -> stringResource(R.string.error_category_not_found)
    ApiErrorCode.QuizNotFound -> stringResource(R.string.error_quiz_not_found)
    ApiErrorCode.AttemptNotFound -> stringResource(R.string.error_attempt_not_found)
    ApiErrorCode.CertificateNotFound -> stringResource(R.string.error_certificate_not_found)
    ApiErrorCode.LearningPathNotFound -> stringResource(R.string.error_learning_path_not_found)
    ApiErrorCode.MediaNotFound -> stringResource(R.string.error_media_not_found)
    ApiErrorCode.UserNotFound -> stringResource(R.string.error_user_not_found)
    ApiErrorCode.EmailAlreadyRegistered -> stringResource(R.string.error_email_already_registered)
    ApiErrorCode.CategoryInUse -> stringResource(R.string.error_category_in_use)
    ApiErrorCode.RateLimitedAuth -> stringResource(R.string.error_rate_limited_auth)
    ApiErrorCode.RateLimitedAiTutor -> stringResource(R.string.error_rate_limited_ai_tutor)
    ApiErrorCode.InternalError -> stringResource(R.string.error_internal)
    is ApiErrorCode.Unknown -> stringResource(R.string.error_internal)
}
