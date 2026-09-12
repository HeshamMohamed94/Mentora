package com.mentora.shared.data.repository.progress

import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.dto.CourseProgressDto
import com.mentora.shared.data.network.dto.EmptyCompleteLessonRequestDto
import com.mentora.shared.data.network.dto.PositionRequestDto
import com.mentora.shared.domain.model.CourseProgress

/** The real [ProgressRepository]. No `?language=` on any of these three endpoints — progress is
 * never one of the 4 reads that carries it (`execution/PHASE_3_KMP_PLAN.md`'s D57/C3 list). */
class ProgressRepositoryImpl(private val apiClient: ApiClient) : ProgressRepository {

    override suspend fun getProgress(courseId: String): ApiResult<CourseProgress> =
        when (val result = apiClient.get<CourseProgressDto>("/api/v1/courses/$courseId/progress")) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }

    override suspend fun completeLesson(courseId: String, lessonId: String): ApiResult<CourseProgress> =
        when (
            val result = apiClient.post<EmptyCompleteLessonRequestDto, CourseProgressDto>(
                "/api/v1/courses/$courseId/lessons/$lessonId/complete",
                EmptyCompleteLessonRequestDto,
            )
        ) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }

    override suspend fun reportPosition(
        courseId: String,
        lessonId: String,
        positionSeconds: Int,
    ): ApiResult<CourseProgress> = when (
        val result = apiClient.post<PositionRequestDto, CourseProgressDto>(
            "/api/v1/courses/$courseId/lessons/$lessonId/position",
            PositionRequestDto(positionSeconds),
        )
    ) {
        is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
        is ApiResult.Failure -> result
    }

    private fun CourseProgressDto.toDomain(): CourseProgress = CourseProgress(
        courseId = courseId,
        completedLessonIds = completedLessonIds,
        currentLessonId = currentLessonId,
        currentPositionSeconds = currentPositionSeconds,
        quizPassed = quizPassed,
        completionPercent = completionPercent,
        courseCompletedAt = courseCompletedAt,
    )
}
