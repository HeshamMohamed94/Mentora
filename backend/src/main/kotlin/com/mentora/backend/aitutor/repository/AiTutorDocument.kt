package com.mentora.backend.aitutor.repository

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class AiConversationDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val userId: ObjectId,
    val createdAt: Instant,
    val lastMessageAt: Instant? = null,
)

@Serializable
data class AiMessageDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val conversationId: ObjectId,
    val role: String,
    val content: String,
    val lessonContextId: String? = null,
    val createdAt: Instant,
)
