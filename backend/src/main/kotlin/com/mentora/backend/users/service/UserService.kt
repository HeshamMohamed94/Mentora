package com.mentora.backend.users.service

import com.mentora.backend.common.ApiException
import com.mentora.backend.common.Role
import com.mentora.backend.users.repository.UserDocument
import com.mentora.backend.users.repository.UserRepository
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

@Serializable
data class UserProfile(
    val id: String,
    val email: String,
    val name: String,
    val role: Role,
    val avatarMediaId: String?,
    val preferredLocale: String?,
    val createdAt: Instant,
)

@Serializable
data class UpdateProfileRequest(val name: String? = null, val preferredLocale: String? = null)

class UserService(private val repository: UserRepository) {
    suspend fun getProfile(userId: ObjectId): UserProfile = repository.findById(userId)?.toProfile()
        ?: throw ApiException.NotFound("USER_NOT_FOUND", "The user was not found.")

    suspend fun updateProfile(userId: ObjectId, request: UpdateProfileRequest): UserProfile {
        val name = request.name?.trim()?.also {
            if (it.isBlank()) throw ApiException.Validation(fields = mapOf("name" to "REQUIRED"))
            if (it.length > 120) throw ApiException.Validation(fields = mapOf("name" to "TOO_LONG"))
        }
        val locale = request.preferredLocale?.trim()?.lowercase()?.also {
            if (it !in SUPPORTED_LOCALES) throw ApiException.Validation(
                fields = mapOf("preferredLocale" to "UNSUPPORTED")
            )
        }
        return repository.updateProfile(userId, name, locale)?.toProfile()
            ?: throw ApiException.NotFound("USER_NOT_FOUND", "The user was not found.")
    }

    /** Cross-module name resolution (e.g. `courses` denormalizing `instructorName`) —
     * the established service-layer reuse pattern (see PHASE_HANDOFF.md § 8), not a new
     * direct-collection query from the calling module. */
    suspend fun getNamesByIds(ids: List<ObjectId>): Map<ObjectId, String> =
        repository.findByIds(ids).associate { requireNotNull(it.id) to it.name }

    private fun UserDocument.toProfile() = UserProfile(
        id = requireNotNull(id).toHexString(), email = email, name = name, role = role,
        avatarMediaId = avatarMediaId?.toHexString(), preferredLocale = preferredLocale, createdAt = createdAt,
    )

    private companion object { val SUPPORTED_LOCALES = setOf("en", "ar") }
}
