package com.mentora.shared.domain.usecase.user

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.user.UserRepository
import com.mentora.shared.domain.model.User

/**
 * Edits the account's display name via `PATCH /users/me` — sends ONLY `{name}`, never
 * `preferredLocale` (see [SetLocaleUseCase] for that field). Mirrors the backend's own name
 * validation (`UserService.kt:30-33`: trimmed, non-blank, ≤120 chars) as a zero-round-trip UX
 * nicety; the server's own check remains authoritative — same precedent as
 * `EmailValidator`/`PasswordValidator` (Task 5).
 */
class UpdateProfileUseCase(private val repository: UserRepository) {
    suspend operator fun invoke(name: String): ApiResult<User> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return ApiResult.Failure(
                code = ApiErrorCode.ValidationError,
                message = "Name must not be blank.",
                fields = mapOf("name" to "REQUIRED"),
                httpStatus = 0,
            )
        }
        if (trimmed.length > MAX_NAME_LENGTH) {
            return ApiResult.Failure(
                code = ApiErrorCode.ValidationError,
                message = "Name is too long.",
                fields = mapOf("name" to "TOO_LONG"),
                httpStatus = 0,
            )
        }
        return repository.updateProfile(name = trimmed, preferredLocale = null)
    }

    private companion object {
        const val MAX_NAME_LENGTH = 120
    }
}
