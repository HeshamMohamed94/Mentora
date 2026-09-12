package com.mentora.shared.domain.usecase.progress

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.data.repository.progress.ProgressRepository
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.LessonProgressTarget

/**
 * The result of successfully completing a lesson: the fresh, server-authoritative [progress]
 * (never recomputed client-side — see [CourseProgress]'s kdoc) plus the resolved
 * [autoAdvanceTarget] both platforms should navigate to next, so Android and iOS can never
 * auto-advance to two different lessons from the same server data.
 */
data class LessonCompletionOutcome(
    val progress: CourseProgress,
    val autoAdvanceTarget: LessonProgressTarget,
)

/**
 * `POST /api/v1/courses/{id}/lessons/{lessonId}/complete` — idempotent server-side (completing an
 * already-complete lesson again is an ordinary [ApiResult.Success], never an error or a
 * double-count; see [com.mentora.shared.data.repository.progress.ProgressRepository
 * .completeLesson]'s kdoc), so this use case adds no extra client-side idempotency guard of its
 * own — calling it twice on the same lesson is safe by construction.
 *
 * After a successful completion, re-fetches the course's curriculum
 * (`GET /courses/{id}`, Task 7) and resolves the auto-advance target — the next lesson after
 * [lessonId] in curriculum order, or [LessonProgressTarget.CourseFinished] if [lessonId] was the
 * last lesson of the whole course — via [CurriculumLessonResolver], the SAME helper
 * [ResumeCourseUseCase] uses for resume resolution (Task 9 AC #5: computed once in `shared`, never
 * duplicated per-platform). If either network call fails, that failure is returned as-is with no
 * partial result.
 *
 * Issues no certificate and checks none — certificate issuance is an entirely server-triggered
 * side effect of the backend's own complete-lesson handler
 * (`ProgressRoutes.kt`'s `certificates.checkAndIssueIfComplete(...)`), not this use case's concern
 * (Task 11 is the read-only certificate surface).
 */
class CompleteLessonUseCase(
    private val progressRepository: ProgressRepository,
    private val catalogRepository: CatalogRepository,
) {
    suspend operator fun invoke(courseId: String, lessonId: String): ApiResult<LessonCompletionOutcome> {
        val progress = when (val result = progressRepository.completeLesson(courseId, lessonId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return result
        }
        val course = when (val result = catalogRepository.getCourseDetails(courseId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return result
        }
        val autoAdvanceTarget = CurriculumLessonResolver.resolveNextAfter(course, lessonId)
        return ApiResult.Success(LessonCompletionOutcome(progress, autoAdvanceTarget))
    }
}
