package com.mentora.shared.domain.model

/**
 * `GET /api/v1/ai-tutor/conversation` — mirrors `AiConversationResponse`
 * (`backend/src/main/kotlin/com/mentora/backend/aitutor/service/AiTutorService.kt:40-44`)
 * field-for-field, verified from source: `{conversationId, messages[], nextCursor?}`.
 *
 * Deliberately NOT modeled as [com.mentora.shared.data.network.CursorPage] — unlike every other
 * paginated list in this codebase (`respondPage`'s `{data: [...], meta: {nextCursor}}` shape,
 * see [com.mentora.shared.data.network.CursorPage]'s kdoc), this endpoint's `data` payload is
 * itself an object carrying its own `conversationId` alongside the paged `messages` array and
 * `nextCursor` — `AiTutorRoutes.kt`'s `get {}` handler calls `call.respondData(service
 * .conversation(...))`, i.e. `respondData`, not `respondPage`. Forcing this into `CursorPage<T>`
 * would silently drop [conversationId]; it is its own type instead, per
 * `execution/PHASE_3_KMP_PLAN.md` Task 14 AC #1.
 */
data class AiConversation(
    val conversationId: String,
    val messages: List<AiMessage>,
    val nextCursor: String?,
)
