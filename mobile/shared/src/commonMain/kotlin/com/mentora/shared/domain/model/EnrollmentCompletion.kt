package com.mentora.shared.domain.model

/**
 * `POST /api/v1/courses/{id}/checkout/complete` response — mirrors the backend's
 * `EnrollmentCompletion` (`EnrollmentService.kt:37`) exactly: `enrollment, alreadyEnrolled`.
 *
 * The backend responds HTTP 201 the first time a student completes checkout for a course and HTTP
 * 200 on every idempotent repeat call for the SAME course
 * (`EnrollmentRoutes.kt:31`: `if (outcome.created) HttpStatusCode.Created else HttpStatusCode.OK`,
 * `EnrollmentService.kt:61-78`'s `complete()`) — both status codes decode to an ordinary
 * [com.mentora.shared.data.network.ApiResult.Success], never an
 * [com.mentora.shared.data.network.ApiResult.Failure]; [com.mentora.shared.data.network.ApiClient]
 * doesn't even surface the HTTP status to callers on success. [alreadyEnrolled] is the field to
 * branch on to tell first-time completion from a repeat — never re-derive this from a status code
 * `shared` never exposed in the first place.
 */
data class EnrollmentCompletion(
    val enrollment: Enrollment,
    val alreadyEnrolled: Boolean,
)
