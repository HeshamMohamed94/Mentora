package com.mentora.shared.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape for `GET /api/v1/learning-paths` — mirrors
 * `backend/src/main/kotlin/com/mentora/backend/learningpaths/service/LearningPathService.kt:13-15`'s
 * `LearningPathSummary` field-for-field.
 *
 * `GET /api/v1/learning-paths` responds with a plain JSON array as the envelope's `data`
 * (`LearningPathRoutes.kt:20`'s bare `get { call.respondData(service.list()) }`, not
 * `authenticate`-wrapped and not `respondPage`) — decoded via
 * `ApiClient.get<List<LearningPathSummaryDto>>(...)`, never `getPage`.
 *
 * Deliberately has no `isFollowing` field — `LearningPathSummary` on the backend genuinely does not
 * carry one; never add one here defaulted to `false`.
 */
@Serializable
data class LearningPathSummaryDto(
    val id: String,
    val title: String,
    val description: String,
    val courseCount: Int,
)

/** Wire shape for one entry of [LearningPathDetailDto.courses] — mirrors `LearningPathService.kt:16-18`'s
 * `LearningPathCourse` field-for-field. */
@Serializable
data class LearningPathCourseDto(
    val id: String,
    val title: String,
    val thumbnailMediaId: String? = null,
)

/**
 * Wire shape for `GET /api/v1/learning-paths/{id}?language=` — mirrors `LearningPathService.kt:19-26`'s
 * `LearningPathResponse` field-for-field. [progressPercent] is genuinely absent (`null`) for a guest
 * caller (optional-auth route) and [isFollowing] is `false` for a guest — see
 * [com.mentora.shared.domain.model.LearningPathDetail]'s kdoc for the full source verification.
 * [courses] arrives in the path's curated (not alphabetical/id-sorted) order and is never re-sorted.
 */
@Serializable
data class LearningPathDetailDto(
    val id: String,
    val title: String,
    val description: String,
    val courses: List<LearningPathCourseDto> = emptyList(),
    val progressPercent: Int? = null,
    val isFollowing: Boolean = false,
)

/** Wire shape for both `POST` and `DELETE /api/v1/learning-paths/{id}/follow` — mirrors
 * `LearningPathService.kt:27`'s `LearningPathFollowResponse`: a single `isFollowing: Boolean` field.
 * `follow()` always responds `{isFollowing: true}` and `unfollow()` always responds
 * `{isFollowing: false}`, unconditionally (not re-queried) — both are naturally idempotent: calling
 * either twice in a row returns the same body and never errors. */
@Serializable
data class LearningPathFollowResponseDto(val isFollowing: Boolean)

/**
 * `POST /api/v1/learning-paths/{id}/follow` reads no request body at all — its handler
 * (`LearningPathRoutes.kt:31-34`) only calls `call.requireCsrfHeader()` and reads the path parameter,
 * never `call.receive()`. Exists solely to satisfy [com.mentora.shared.data.network.ApiClient.post]'s
 * generic `TBody` parameter (same convention as `EmptyCompleteLessonRequestDto`,
 * `data/network/dto/ProgressDto.kt`); the backend never looks at it.
 */
@Serializable
object EmptyFollowRequestDto
