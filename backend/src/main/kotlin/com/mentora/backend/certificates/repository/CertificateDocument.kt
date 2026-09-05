package com.mentora.backend.certificates.repository

import kotlinx.datetime.Instant
import kotlinx.serialization.Contextual
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class CertificateDocument(
    @SerialName("_id") @Contextual val id: ObjectId? = null,
    @Contextual val userId: ObjectId,
    @Contextual val courseId: ObjectId,
    val issuedAt: Instant,
    val studentNameSnapshot: String,
    val courseTitleSnapshot: String,
    val instructorNameSnapshot: String,
    val completionDateSnapshot: Instant,
)
