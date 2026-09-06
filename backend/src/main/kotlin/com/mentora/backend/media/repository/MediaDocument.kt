package com.mentora.backend.media.repository

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class MediaDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val uploadedByUserId: ObjectId,
    val kind: String,
    val ownerRefId: String,
    @Contextual val courseId: ObjectId? = null,
    val storageKey: String,
    val contentType: String,
    val sizeBytes: Long,
    val durationSeconds: Int? = null,
    val status: String = "ready",
    val createdAt: Instant,
)
