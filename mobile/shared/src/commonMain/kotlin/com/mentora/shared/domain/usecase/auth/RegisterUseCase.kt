package com.mentora.shared.domain.usecase.auth

import com.mentora.shared.auth.SessionUser
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.auth.AuthRepository
import com.mentora.shared.domain.usecase.user.SetLocaleUseCase
import com.mentora.shared.domain.validation.EmailValidator
import com.mentora.shared.domain.validation.PasswordValidator
import com.mentora.shared.domain.validation.ValidationResult

/**
 * Registers a new student account. Runs [EmailValidator]/[PasswordValidator] first as a UX nicety
 * — fails fast with zero network round trip for obviously-invalid input (surfaced as a
 * [ApiErrorCode.ValidationError] failure with `httpStatus = 0`, so callers can't mistake it for a
 * real server round trip). The server's own validation in `AuthService.register` remains
 * authoritative regardless — see those validators' kdoc: a client-side pass is never assumed
 * sufficient.
 *
 * On success, applies Task 6's "register seeds account" locale precedence
 * ([SetLocaleUseCase.onRegister]) best-effort: a failure to seed the brand-new account's
 * `preferredLocale` is never surfaced as a registration failure — the account itself was created
 * successfully, and the locale preference simply falls back to the account's default (unset)
 * until the next explicit locale change.
 */
class RegisterUseCase(
    private val repository: AuthRepository,
    private val setLocaleUseCase: SetLocaleUseCase,
) {
    suspend operator fun invoke(email: String, password: String, name: String): ApiResult<SessionUser> {
        val (normalizedEmail, emailResult) = EmailValidator.validate(email)
        val passwordResult = PasswordValidator.validate(password)

        val fieldErrors = buildList {
            (emailResult as? ValidationResult.Invalid)?.errors?.let(::addAll)
            (passwordResult as? ValidationResult.Invalid)?.errors?.let(::addAll)
        }
        if (fieldErrors.isNotEmpty()) {
            return ApiResult.Failure(
                code = ApiErrorCode.ValidationError,
                message = "Registration input failed local validation.",
                fields = fieldErrors.associate { it.field to it.reason },
                httpStatus = 0,
            )
        }

        val result = repository.register(normalizedEmail, password, name)
        if (result is ApiResult.Success) {
            setLocaleUseCase.onRegister()
        }
        return result
    }
}
