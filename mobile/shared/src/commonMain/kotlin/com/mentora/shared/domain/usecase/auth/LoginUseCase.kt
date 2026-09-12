package com.mentora.shared.domain.usecase.auth

import com.mentora.shared.auth.SessionUser
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.auth.AuthRepository

/**
 * Logs an existing account in. Deliberately performs no password-strength/email-format local
 * validation beyond the same trim+lowercase normalization the backend itself applies before
 * lookup (`AuthService.login`'s `request.email.trim().lowercase()`) — a wrong email and a wrong
 * password both surface as the exact same generic `AUTH_INVALID_CREDENTIALS` failure the backend
 * returns (`execution/PHASE_3_KMP_PLAN.md` Task 5 AC #12); adding a client-side email-format check
 * here would risk leaking a distinction ("this looks like a real email vs. garbage") the backend
 * itself never makes.
 */
class LoginUseCase(private val repository: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): ApiResult<SessionUser> {
        val normalizedEmail = email.trim().lowercase()
        return repository.login(normalizedEmail, password)
    }
}
