package com.mentora.shared.auth

/**
 * The three (and only three) states of the current session, exposed as a
 * `StateFlow<AuthState>` from [SessionManager.authState]. `shared` has no UI concept — this type
 * is the entire surface a platform layer watches to decide what to show. `shared` never navigates
 * or emits any UI-framework-flavored event of its own (`execution/PHASE_3_KMP_PLAN.md` Task 5
 * "Must NOT").
 */
sealed interface AuthState {
    /** Not yet determined — before [com.mentora.shared.domain.usecase.auth.RestoreSessionUseCase]
     * has run once on cold start. */
    data object Unknown : AuthState

    /** [user] is `null` only immediately after [com.mentora.shared.data.repository.auth.AuthRepository.restoreSession]
     * restores a session from a persisted token with no cached profile (`TokenStorage` carries only
     * tokens, never identity) — honest about "authenticated, but identity not yet known" rather
     * than fabricating placeholder values a caller could mistake for real data. Always non-null
     * after a real register/login response, and expected to be filled in by a follow-up
     * `GET /users/me` fetch (Task 6) whenever it is `null`. */
    data class Authenticated(val user: SessionUser?) : AuthState

    data object Unauthenticated : AuthState
}
