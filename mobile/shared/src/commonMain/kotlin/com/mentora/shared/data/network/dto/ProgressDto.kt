package com.mentora.shared.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire shape for all three progress endpoints
 * (`backend/src/main/kotlin/com/mentora/backend/progress/{routes/ProgressRoutes.kt,
 * service/ProgressService.kt}`) — `GET /courses/{id}/progress`,
 * `POST /courses/{id}/lessons/{lessonId}/complete`, and
 * `POST /courses/{id}/lessons/{lessonId}/position` all respond with this exact shape
 * (`ProgressService.kt:19-27`'s `ProgressResponse`), verified from source. `currentLessonId`/
 * `currentPositionSeconds`/`quizPassed` are genuinely optional on the wire (`explicitNulls = false`
 * server-side) — defaulted to `null` here per that convention, never assumed present.
 */
@Serializable
data class CourseProgressDto(
    val courseId: String,
    val completedLessonIds: List<String> = emptyList(),
    val currentLessonId: String? = null,
    val currentPositionSeconds: Int? = null,
    val quizPassed: Boolean? = null,
    val completionPercent: Int,
    val courseCompletedAt: String? = null,
)

/** `POST /courses/{id}/lessons/{lessonId}/position`'s request body — mirrors
 * `ProgressService.kt:18`'s `PositionRequest` exactly: a single `positionSeconds: Int` field. The
 * backend itself validates non-negativity server-side (`ProgressService.updatePosition()`) and
 * rejects a negative value with `VALIDATION_ERROR`; `shared` adds no client-side duplicate check. */
@Serializable
data class PositionRequestDto(val positionSeconds: Int)

/**
 * `POST /courses/{id}/lessons/{lessonId}/complete` reads no request body at all — its handler
 * (`ProgressRoutes.kt:27-36`) never calls `call.receive()`, only path parameters and the CSRF
 * header. Exists solely to satisfy [com.mentora.shared.data.network.ApiClient.post]'s generic
 * `TBody` parameter (same convention as `EmptyCheckoutCompleteRequestDto`,
 * `data/network/dto/EnrollmentDto.kt`); the backend never looks at it.
 */
@Serializable
object EmptyCompleteLessonRequestDto
