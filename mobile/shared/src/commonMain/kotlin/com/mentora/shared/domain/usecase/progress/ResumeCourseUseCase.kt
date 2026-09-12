package com.mentora.shared.domain.usecase.progress

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.catalog.CatalogRepository
import com.mentora.shared.data.repository.progress.ProgressRepository
import com.mentora.shared.domain.model.LessonProgressTarget

/**
 * Resolves where a student should resume [courseId] by combining SERVER-AUTHORITATIVE progress
 * (`GET /courses/{id}/progress`) with the real curriculum order from Task 7's
 * `GET /courses/{id}` (`Course.sections`, each section's `lessons` in `order`) — never a
 * client-side guess. Both network calls are independent reads; the first [ApiResult.Failure]
 * encountered (course details or progress) is returned as-is, same "fail fast, no partial result"
 * convention as [com.mentora.shared.domain.usecase.enrollment.GetMyLearningUseCase].
 *
 * The actual resolution — `currentLessonId` if present and still valid, else the first incomplete
 * lesson in curriculum order, else [LessonProgressTarget.CourseFinished] — is delegated entirely to
 * [CurriculumLessonResolver], the SAME helper [CompleteLessonUseCase] uses for its auto-advance
 * target, so resume and auto-advance can never disagree given identical server data.
 */
class ResumeCourseUseCase(
    private val catalogRepository: CatalogRepository,
    private val progressRepository: ProgressRepository,
) {
    suspend operator fun invoke(courseId: String): ApiResult<LessonProgressTarget> {
        val course = when (val result = catalogRepository.getCourseDetails(courseId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return result
        }
        val progress = when (val result = progressRepository.getProgress(courseId)) {
            is ApiResult.Success -> result.data
            is ApiResult.Failure -> return result
        }
        return ApiResult.Success(
            CurriculumLessonResolver.resolveResumeTarget(
                course = course,
                completedLessonIds = progress.completedLessonIds.toSet(),
                currentLessonId = progress.currentLessonId,
                currentPositionSeconds = progress.currentPositionSeconds,
            ),
        )
    }
}
