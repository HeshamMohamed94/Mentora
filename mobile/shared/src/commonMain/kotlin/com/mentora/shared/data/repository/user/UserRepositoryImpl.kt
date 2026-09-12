package com.mentora.shared.data.repository.user

import com.mentora.shared.auth.SessionManager
import com.mentora.shared.auth.SessionUser
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.dto.UpdateProfileRequestDto
import com.mentora.shared.data.network.dto.UserProfileDto
import com.mentora.shared.domain.model.User

/**
 * The real [UserRepository]. [apiClient]'s underlying `HttpClient` is expected to already have
 * [com.mentora.shared.auth.installAuthInterception] installed with the SAME [sessionManager]
 * passed here (same precondition as `AuthRepositoryImpl`). Every successful fetch/update also
 * pushes the freshly-returned [User] into [sessionManager] via [SessionManager.updateUser] — this
 * is both how a `null` session user left by a cold-start restore gets filled in (Task 5's gap,
 * closed here) and how an edited name/locale keeps the cached [SessionUser] from going stale.
 */
class UserRepositoryImpl(
    private val apiClient: ApiClient,
    private val sessionManager: SessionManager,
) : UserRepository {

    override suspend fun getProfile(): ApiResult<User> {
        val result = apiClient.get<UserProfileDto>("/api/v1/users/me")
        return handleProfileResponse(result)
    }

    override suspend fun updateProfile(name: String?, preferredLocale: String?): ApiResult<User> {
        val result = apiClient.patch<UpdateProfileRequestDto, UserProfileDto>(
            "/api/v1/users/me",
            UpdateProfileRequestDto(name = name, preferredLocale = preferredLocale),
        )
        return handleProfileResponse(result)
    }

    private fun handleProfileResponse(result: ApiResult<UserProfileDto>): ApiResult<User> =
        when (result) {
            is ApiResult.Success -> {
                val user = result.data.toDomain()
                sessionManager.updateUser(user.toSessionUser())
                ApiResult.Success(user)
            }
            is ApiResult.Failure -> result
        }

    private fun UserProfileDto.toDomain(): User = User(
        id = id,
        email = email,
        name = name,
        role = role,
        avatarMediaId = avatarMediaId,
        preferredLocale = preferredLocale,
        createdAt = createdAt,
    )

    private fun User.toSessionUser(): SessionUser =
        SessionUser(id = id, email = email, name = name, role = role, preferredLocale = preferredLocale)
}
