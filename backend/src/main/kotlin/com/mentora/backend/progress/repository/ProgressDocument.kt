package com.mentora.backend.progress.repository

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class ProgressDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val userId: ObjectId,
    @Contextual val courseId: ObjectId,
    val completedLessonIds: Map<String, Instant> = emptyMap(),
    val currentLessonId: String? = null,
    val currentPositionSeconds: Int? = null,
    val quizPassed: Boolean? = null,
    val completionPercent: Int = 0,
    val courseCompletedAt: Instant? = null,
    val updatedAt: Instant,
)
