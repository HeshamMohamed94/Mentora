package com.mentora.shared.data.network.dto

import com.mentora.shared.auth.Role
import kotlinx.serialization.Serializable

/**
 * Wire-shape DTOs for `backend/src/main/kotlin/com/mentora/backend/users/routes/UserRoutes.kt`'s
 * two endpoints. Field names/types match the backend's `UserProfile`/`UpdateProfileRequest`
 * (`backend/.../users/service/UserService.kt:12-23`) exactly.
 */
@Serializable
data class UserProfileDto(
    val id: String,
    val email: String,
    val name: String,
    val role: Role,
    val avatarMediaId: String? = null,
    val preferredLocale: String? = null,
    val createdAt: String,
)

/**
 * `PATCH /users/me`'s request body — ONLY these two fields are mutable
 * (`UserService.kt:23`'s `UpdateProfileRequest(val name: String? = null, val preferredLocale: String? = null)`).
 * A `null` here is never sent as a literal wire `null` — [com.mentora.shared.data.network.MentoraJson]
 * has `encodeDefaults = false` (the kotlinx.serialization default), so a field left at its `null`
 * default is omitted from the JSON body entirely, matching the backend's "no updates ⇒ leaves the
 * field unchanged" semantics (`UserRepository.kt:41-44`'s `updateProfile`) and letting callers send
 * only the field(s) that actually changed.
 */
@Serializable
data class UpdateProfileRequestDto(val name: String? = null, val preferredLocale: String? = null)
