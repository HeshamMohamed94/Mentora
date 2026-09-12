package com.mentora.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Who authored an [AiMessage] — mirrors `AiMessageDocument.role`/`AiMessageResponse.role`
 * (`backend/src/main/kotlin/com/mentora/backend/aitutor/repository/AiTutorDocument.kt`,
 * `.../service/AiTutorService.kt`), which is a plain `String` on the wire with no server-side
 * enum/whitelist of its own — but every write site (`AiTutorService.appendMessage`'s two call
 * sites in `prepareMessage`) passes only the literal `"user"` or `"assistant"`, verified from
 * source, so modeling it as a closed, [Serializable] enum here follows the same convention
 * [CourseLevel]/[Role][com.mentora.shared.auth.Role] already use for other backend string sets
 * that are fixed in practice even where the server itself stores a raw `String`.
 */
@Serializable
enum class AiMessageRole {
    @SerialName("user") User,
    @SerialName("assistant") Assistant,
}
