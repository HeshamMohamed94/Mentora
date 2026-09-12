package com.mentora.shared.domain.model

/**
 * `GET /api/v1/courses/{id}/checkout` response's `course` field — mirrors the backend's
 * `CheckoutCourse` (`EnrollmentService.kt:24`) field-for-field: `id, title, thumbnailMediaId?`. A
 * minimal course summary purpose-built for a checkout screen, deliberately never the full
 * [Course]/[CourseSummary] shape.
 */
data class CheckoutCourse(
    val id: String,
    val title: String,
    val thumbnailMediaId: String?,
)

/**
 * `GET /api/v1/courses/{id}/checkout?language=` response — mirrors the backend's
 * `CheckoutPreview` (`EnrollmentService.kt:25-29`) field-for-field: `course, instructorName,
 * priceDisplay`. `?language=` is threaded on this read the same way as [Course]/[CourseSummary]
 * (via `com.mentora.shared.data.network.localeQueryParam`, see
 * `com.mentora.shared.data.repository.enrollment.EnrollmentRepositoryImpl`) — it affects only
 * [CheckoutCourse.title], which locale's translation the backend resolves before building this
 * response (`EnrollmentService.kt:51-59`'s `preview()`).
 *
 * Requires the Student role server-side and 404s `COURSE_NOT_FOUND` for a non-existent OR an
 * unpublished course id (`EnrollmentService.kt:109-112`'s `publishedCourse()` helper, shared by
 * [complete checkout][EnrollmentCompletion]) — never a 403, same shape of behavior as [Course]'s
 * draft-course handling (D64), just without that carve-out: an already-enrolled student who
 * re-opens checkout on a course an instructor has since unpublished still gets 404 here, unlike
 * `GET /courses/{id}`.
 */
data class CheckoutPreview(
    val course: CheckoutCourse,
    val instructorName: String,
    val priceDisplay: PriceDisplay,
)
