package com.mentora.shared.data.repository.learningpath

import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.dto.EmptyFollowRequestDto
import com.mentora.shared.data.network.dto.LearningPathCourseDto
import com.mentora.shared.data.network.dto.LearningPathDetailDto
import com.mentora.shared.data.network.dto.LearningPathFollowResponseDto
import com.mentora.shared.data.network.dto.LearningPathSummaryDto
import com.mentora.shared.data.network.localeQueryParam
import com.mentora.shared.domain.model.LearningPath
import com.mentora.shared.domain.model.LearningPathCourse
import com.mentora.shared.domain.model.LearningPathDetail
import com.mentora.shared.settings.PreferenceStore

/**
 * The real [LearningPathRepository]. Only [getLearningPath] appends `?language=` (from
 * [preferenceStore]'s current locale, via [localeQueryParam]) — [listLearningPaths] and
 * [follow]/[unfollow] are not among the 4 reads that carry it
 * (`execution/PHASE_3_KMP_PLAN.md`'s D57/C3 list; `follow`/`unfollow` are writes, not reads, anyway).
 */
internal class LearningPathRepositoryImpl(
    private val apiClient: ApiClient,
    private val preferenceStore: PreferenceStore,
) : LearningPathRepository {

    override suspend fun listLearningPaths(): ApiResult<List<LearningPath>> =
        when (val result = apiClient.get<List<LearningPathSummaryDto>>("/api/v1/learning-paths")) {
            is ApiResult.Success -> ApiResult.Success(result.data.map { it.toDomain() })
            is ApiResult.Failure -> result
        }

    override suspend fun getLearningPath(id: String): ApiResult<LearningPathDetail> {
        val (key, value) = localeQueryParam(preferenceStore.locale.value)
        return when (
            val result = apiClient.get<LearningPathDetailDto>("/api/v1/learning-paths/$id", mapOf(key to value))
        ) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }
    }

    override suspend fun follow(id: String): ApiResult<Boolean> =
        when (
            val result = apiClient.post<EmptyFollowRequestDto, LearningPathFollowResponseDto>(
                "/api/v1/learning-paths/$id/follow",
                EmptyFollowRequestDto,
            )
        ) {
            is ApiResult.Success -> ApiResult.Success(result.data.isFollowing)
            is ApiResult.Failure -> result
        }

    override suspend fun unfollow(id: String): ApiResult<Boolean> =
        when (val result = apiClient.delete<LearningPathFollowResponseDto>("/api/v1/learning-paths/$id/follow")) {
            is ApiResult.Success -> ApiResult.Success(result.data.isFollowing)
            is ApiResult.Failure -> result
        }

    private fun LearningPathSummaryDto.toDomain(): LearningPath =
        LearningPath(id, title, description, courseCount)

    private fun LearningPathCourseDto.toDomain(): LearningPathCourse =
        LearningPathCourse(id, title, thumbnailMediaId)

    /** Preserves [LearningPathDetailDto.courses]'s curated wire order exactly — no client-side
     * re-sort (see [LearningPathDetail]'s kdoc for why this differs from Task 7's course
     * sections/lessons, which the backend does not guarantee sorted). */
    private fun LearningPathDetailDto.toDomain(): LearningPathDetail = LearningPathDetail(
        id = id,
        title = title,
        description = description,
        courses = courses.map { it.toDomain() },
        progressPercent = progressPercent,
        isFollowing = isFollowing,
    )
}
