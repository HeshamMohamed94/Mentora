package com.mentora.shared.data.repository.progress

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.CourseProgress

/**
 * The only progress network surface `domain/usecase/progress` use cases are allowed to depend on —
 * mirrors [com.mentora.shared.data.repository.enrollment.EnrollmentRepository]'s "interface + Impl"
 * pattern (`execution/PHASE_3_KMP_PLAN.md` Task 9).
 *
 * Every method returns the server's freshly-computed [CourseProgress] verbatim. `shared` builds no
 * persistent local progress cache anywhere behind this interface — a caller that wants an
 * optimistic UI update must treat it as a TRANSIENT, UI-layer-only value that gets reconciled
 * against the next real response from one of these three methods, never a substitute source of
 * truth (Task 9 AC #7).
 */
interface ProgressRepository {
    /**
     * `GET /api/v1/courses/{id}/progress` → [CourseProgress], lazily created server-side on first
     * call. Student + enrolled required server-side; a non-enrolled caller surfaces as an ordinary
     * [ApiResult.Failure] with [com.mentora.shared.data.network.ApiErrorCode.ForbiddenNotEnrolled] —
     * a typed, distinguishable failure, never folded into a generic error.
     */
    suspend fun getProgress(courseId: String): ApiResult<CourseProgress>

    /**
     * `POST /api/v1/courses/{id}/lessons/{lessonId}/complete` → the updated [CourseProgress].
     * Idempotent: completing an already-complete lesson a second time is an ordinary
     * [ApiResult.Success] with the SAME [CourseProgress.completedLessonIds] entry, never an error
     * or a double-count (`ProgressService.complete()`'s `if (lessonId in current.completedLessonIds)
     * current.completedLessonIds else ...`). Never retried by `shared` itself.
     */
    suspend fun completeLesson(courseId: String, lessonId: String): ApiResult<CourseProgress>

    /**
     * `POST /api/v1/courses/{id}/lessons/{lessonId}/position` with body `{positionSeconds}` → the
     * updated [CourseProgress]. This is the raw, unthrottled network call — throttling/debouncing
     * lives one layer up, in
     * `com.mentora.shared.domain.usecase.progress.ReportPlaybackPositionUseCase`, not here. Never
     * retried on failure by `shared` — a dropped heartbeat is fine, the next one catches up.
     */
    suspend fun reportPosition(courseId: String, lessonId: String, positionSeconds: Int): ApiResult<CourseProgress>
}
