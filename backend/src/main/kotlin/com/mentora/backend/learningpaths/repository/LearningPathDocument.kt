package com.mentora.backend.learningpaths.repository

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class LearningPathDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    val title: String,
    val description: String,
    val courseIds: List<@Contextual ObjectId>,
    val createdAt: Instant,
)

@Serializable
data class LearningPathFollowDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val userId: ObjectId,
    @Contextual val pathId: ObjectId,
    val followedAt: Instant,
)
