package com.mentora.shared.domain.usecase.auth

import com.mentora.shared.auth.SessionUser
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.auth.AuthRepository
import com.mentora.shared.domain.usecase.user.SetLocaleUseCase

/**
 * Logs an existing account in. Deliberately performs no password-strength/email-format local
 * validation beyond the same trim+lowercase normalization the backend itself applies before
 * lookup (`AuthService.login`'s `request.email.trim().lowercase()`) — a wrong email and a wrong
 * password both surface as the exact same generic `AUTH_INVALID_CREDENTIALS` failure the backend
 * returns (`execution/PHASE_3_KMP_PLAN.md` Task 5 AC #12); adding a client-side email-format check
 * here would risk leaking a distinction ("this looks like a real email vs. garbage") the backend
 * itself never makes.
 *
 * On success, applies Task 6's "login overwrites local" locale precedence
 * ([SetLocaleUseCase.onLogin]) — a pure local [com.mentora.shared.settings.PreferenceStore] write,
 * never a reason to fail the login itself.
 */
class LoginUseCase(
    private val repository: AuthRepository,
    private val setLocaleUseCase: SetLocaleUseCase,
) {
    suspend operator fun invoke(email: String, password: String): ApiResult<SessionUser> {
        val normalizedEmail = email.trim().lowercase()
        val result = repository.login(normalizedEmail, password)
        if (result is ApiResult.Success) {
            setLocaleUseCase.onLogin(result.data.preferredLocale)
        }
        return result
    }
}
