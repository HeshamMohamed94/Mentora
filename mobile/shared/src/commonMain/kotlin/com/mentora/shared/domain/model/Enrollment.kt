package com.mentora.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * How a given [Enrollment] came to exist. The backend hardcodes exactly one wire value today —
 * `"demoCheckout"` (`EnrollmentService.kt:101`'s `createEnrollment()`, the ONLY call site anywhere
 * in the backend that ever constructs an `EnrollmentDocument`) — the MVP has no other enrollment
 * path (no external checkout provider integration, no instructor-granted access, no admin import).
 * Modeled as a typed enum rather than a raw `String` per `execution/PHASE_3_KMP_PLAN.md` Task 8
 * AC #6, so a caller can never accidentally pass/compare against a mistyped string literal.
 */
@Serializable
enum class EnrollmentSource {
    @SerialName("demoCheckout") DemoCheckout,
}

/**
 * Whether a given [Enrollment] is currently active. The backend hardcodes exactly one wire value
 * today — `"active"` (`EnrollmentService.kt:104`) — nothing on the backend ever writes any other
 * value (no unenroll/refund/expire endpoint exists anywhere). Modeled as a typed enum for the same
 * reason as [EnrollmentSource].
 */
@Serializable
enum class EnrollmentStatus {
    @SerialName("active") Active,
}

/**
 * A single item of `GET /api/v1/enrollments` (and the `enrollment` field of an
 * [EnrollmentCompletion]) — mirrors the backend's `EnrollmentResponse`
 * (`EnrollmentService.kt:30-36`) field-for-field: `id, courseId, source, enrolledAt, status`.
 * Deliberately minimal — this is NOT a [Course]/[CourseSummary] with an embedded course detail;
 * [com.mentora.shared.domain.usecase.enrollment.GetMyLearningUseCase] is what composes this with a
 * separate `GET /courses/{id}` fetch when a full "my learning" view is needed.
 *
 * [enrolledAt] is kept as the raw ISO-8601 wire string, the same convention as [User.createdAt] —
 * see that field's kdoc for why (`shared` has no `kotlinx-datetime` dependency yet).
 */
data class Enrollment(
    val id: String,
    val courseId: String,
    val source: EnrollmentSource,
    val enrolledAt: String,
    val status: EnrollmentStatus,
)
