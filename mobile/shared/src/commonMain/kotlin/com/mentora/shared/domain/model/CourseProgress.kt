package com.mentora.shared.domain.model

/**
 * `GET /api/v1/courses/{id}/progress` (and the response of the complete/position-heartbeat
 * endpoints, which return the same shape) — mirrors the backend's `ProgressResponse`
 * (`backend/src/main/kotlin/com/mentora/backend/progress/service/ProgressService.kt:19-27`)
 * field-for-field: `courseId, completedLessonIds, currentLessonId?, currentPositionSeconds?,
 * quizPassed?, completionPercent, courseCompletedAt?`, verified from source (not paraphrased).
 *
 * Lazily created server-side on first access — `ProgressService.get()`/`.updatePosition()` both
 * call `ProgressRepository.findOrCreate(...)`, upserting a zeroed document
 * (`completedLessonIds = {}`, `completionPercent = 0`, everything else `null`) the very first time
 * an enrolled student's progress is read or a position is reported for a course they have never
 * touched before — `shared` never needs to special-case "no progress yet", the server always
 * returns a well-formed [CourseProgress].
 *
 * [completionPercent] is ALWAYS the server's own computation
 * (`ProgressService.complete()`: `completed.size * 100 / total`, using the course's OWN full lesson
 * count) — `shared` must never recompute this locally from [completedLessonIds]'s size against a
 * client-side lesson count, since that value is the single completion-percent source of truth the
 * UI is allowed to trust.
 *
 * [courseCompletedAt] is kept as the raw ISO-8601 wire string (or `null` before the course is
 * fully finished) — the same convention as [User.createdAt]/[Enrollment.enrolledAt] — `shared` has
 * no `kotlinx-datetime` dependency yet and no Phase 3 use case needs anything beyond displaying
 * this value verbatim.
 *
 * [quizPassed] is `null` until a quiz attempt exists at all (Task 10's concern to set); this task
 * only reads it through unchanged.
 *
 * A student who is not enrolled in [courseId] never receives this shape at all — every endpoint
 * that returns it enforces enrollment server-side first and responds `403 FORBIDDEN_NOT_ENROLLED`
 * instead (`EnrollmentService.requireEnrollment()`), surfaced by `shared` as an ordinary
 * [com.mentora.shared.data.network.ApiResult.Failure] with
 * [com.mentora.shared.data.network.ApiErrorCode.ForbiddenNotEnrolled] — a typed, distinguishable
 * failure a caller can branch on, never folded into a generic error.
 */
data class CourseProgress(
    val courseId: String,
    val completedLessonIds: List<String>,
    val currentLessonId: String?,
    val currentPositionSeconds: Int?,
    val quizPassed: Boolean?,
    val completionPercent: Int,
    val courseCompletedAt: String?,
)

/**
 * The resolved "where should the player point at" target shared by [ResumeCourseUseCase]-style
 * resume resolution and [CompleteLessonUseCase]-style auto-advance resolution — see
 * `com.mentora.shared.domain.usecase.progress.CurriculumLessonResolver`'s kdoc for how both use
 * cases derive this from the SAME curriculum-order logic, so Android and iOS can never resolve two
 * different "next lesson"s from identical server data.
 *
 * [LessonTarget.positionSeconds] is only ever non-null for a genuine mid-lesson RESUME (the
 * server's [CourseProgress.currentPositionSeconds] for that exact lesson) — an auto-advance target
 * (the next lesson after one just completed) or a "first incomplete lesson" fallback always starts
 * a fresh lesson from the beginning, so it carries `null`.
 */
sealed class LessonProgressTarget {
    data class LessonTarget(val lessonId: String, val positionSeconds: Int? = null) : LessonProgressTarget()

    /** Every lesson in the curriculum is in [CourseProgress.completedLessonIds] — there is no
     * "next" lesson to resume into or auto-advance to. Certificate issuance (if earned) is an
     * entirely server-triggered side effect of the preceding complete-lesson call; this sealed
     * type carries no certificate information at all (Task 11's read-only concern, not this
     * task's). */
    data object CourseFinished : LessonProgressTarget()
}
