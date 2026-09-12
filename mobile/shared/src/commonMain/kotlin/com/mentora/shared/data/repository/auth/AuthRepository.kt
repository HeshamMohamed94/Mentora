package com.mentora.shared.data.repository.auth

import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.SessionUser
import com.mentora.shared.data.network.ApiResult
import kotlinx.coroutines.flow.StateFlow

/**
 * The only auth-network surface `domain/usecase/auth` use cases are allowed to depend on — a use
 * case never touches [com.mentora.shared.data.network.ApiClient]/`AuthRepositoryImpl`/
 * [com.mentora.shared.auth.SessionManager] directly (`execution/PHASE_3_KMP_PLAN.md` Task 5).
 */
interface AuthRepository {
    /** Current session state, ultimately sourced from [com.mentora.shared.auth.SessionManager]. */
    val authState: StateFlow<AuthState>

    /** `POST /auth/register` → 201. On success, persists tokens and publishes
     * [AuthState.Authenticated] as a side effect (see [com.mentora.shared.auth.SessionManager]). */
    suspend fun register(email: String, password: String, name: String): ApiResult<SessionUser>

    /** `POST /auth/login` → 200. Same persistence side effect as [register]. A wrong email or
     * wrong password both surface as the same generic `AUTH_INVALID_CREDENTIALS` failure — the
     * backend itself never distinguishes the two (timing-safe by design), and this repository adds
     * no client-side hint on top of it. */
    suspend fun login(email: String, password: String): ApiResult<SessionUser>

    /** `POST /auth/logout`. Clears the local session (storage + [authState]) regardless of whether
     * the network call itself succeeds — a user pressing "log out" should never remain
     * locally-authenticated because of a transient network blip. */
    suspend fun logout(): ApiResult<Unit>

    /** Forces an out-of-band refresh (e.g. on app foreground) — distinct from the 401-triggered
     * auto-refresh [com.mentora.shared.auth.installAuthInterception] performs transparently on
     * every request. Still funnels through the same single-flight coordinator. */
    suspend fun refreshSession(): ApiResult<Unit>

    /** Reads persisted tokens on cold start and sets the initial [AuthState] accordingly — a token
     * presence is presumed valid until a real request proves otherwise (a 401 will drive the auth
     * plugin's normal refresh-or-sign-out path). No network call is made here. */
    suspend fun restoreSession(): AuthState
}
