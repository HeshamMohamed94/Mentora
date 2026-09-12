package com.mentora.shared.domain.usecase.progress

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.progress.ProgressRepository
import com.mentora.shared.domain.model.CourseProgress

/**
 * `GET /api/v1/courses/{id}/progress`. A non-enrolled caller surfaces as an ordinary
 * [ApiResult.Failure] with [com.mentora.shared.data.network.ApiErrorCode.ForbiddenNotEnrolled] — a
 * typed, distinguishable failure this use case forwards unchanged, never folded into a generic
 * error or a thrown exception.
 */
class GetCourseProgressUseCase(private val repository: ProgressRepository) {
    suspend operator fun invoke(courseId: String): ApiResult<CourseProgress> = repository.getProgress(courseId)
}
