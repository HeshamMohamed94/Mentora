package com.mentora.shared.domain.model

import kotlinx.datetime.Instant

/**
 * One message in the student's AI Tutor conversation — mirrors `AiMessageResponse`
 * (`backend/src/main/kotlin/com/mentora/backend/aitutor/service/AiTutorService.kt:31-37`)
 * field-for-field, verified from source: `id, role, content, lessonContextId?, createdAt`.
 *
 * [lessonContextId] is whatever lesson (if any) was active when THIS message was sent — it is
 * per-message, not per-conversation, since a single conversation can range across multiple
 * lessons/courses over time.
 */
data class AiMessage(
    val id: String,
    val role: AiMessageRole,
    val content: String,
    val lessonContextId: String?,
    val createdAt: Instant,
)
