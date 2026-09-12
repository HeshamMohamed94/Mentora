package com.mentora.shared.data.network.dto

import com.mentora.shared.domain.model.AiMessageRole
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Wire shapes for `GET /api/v1/ai-tutor/conversation` and
 * `POST /api/v1/ai-tutor/conversation/messages` — mirror
 * `backend/src/main/kotlin/com/mentora/backend/aitutor/service/AiTutorService.kt:23-44`'s
 * `SendAiMessageRequest`/`AiMessageResponse`/`AiConversationResponse` field-for-field, verified
 * from source (not paraphrased). Re-verify against that file if this ever needs to change.
 *
 * [AiMessageRole] is used directly as [AiMessageDto.role]'s field type (the same convention
 * `CourseDto` already uses for `level: CourseLevel` — see that file's kdoc).
 */
@Serializable
data class AiMessageDto(
    val id: String,
    val role: AiMessageRole,
    val content: String,
    val lessonContextId: String? = null,
    val createdAt: Instant,
)

/** `GET /api/v1/ai-tutor/conversation`'s envelope `data` shape — mirrors
 * `AiConversationResponse` (`AiTutorService.kt:40-44`). NOT the standard `respondPage`
 * `{data: [...], meta: {nextCursor}}` shape — see [com.mentora.shared.domain.model.AiConversation]'s
 * kdoc for why this is its own type rather than [com.mentora.shared.data.network.CursorPage]. */
@Serializable
data class AiConversationDto(
    val conversationId: String,
    val messages: List<AiMessageDto> = emptyList(),
    val nextCursor: String? = null,
)

/** Request body for `POST /api/v1/ai-tutor/conversation/messages` — mirrors
 * `SendAiMessageRequest` (`AiTutorService.kt:24-28`). [courseId]/[lessonContextId] are a strict
 * pair server-side (`AiTutorService.validatedContent()`'s `(courseId == null) != (lessonContextId
 * == null)` check) — [com.mentora.shared.domain.usecase.aitutor.SendAiTutorMessageUseCase]
 * enforces the same rule client-side before this DTO is ever constructed. */
@Serializable
data class SendAiMessageRequestDto(
    val content: String,
    val courseId: String? = null,
    val lessonContextId: String? = null,
)
