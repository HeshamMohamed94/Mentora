package com.mentora.shared.data.repository.auth

import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.AuthTokens
import com.mentora.shared.auth.SessionManager
import com.mentora.shared.auth.SessionUser
import com.mentora.shared.auth.TokenStorage
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.dto.AuthResponseDto
import com.mentora.shared.data.network.dto.EmptyResponseDto
import com.mentora.shared.data.network.dto.LoginRequestDto
import com.mentora.shared.data.network.dto.RefreshRequestDto
import com.mentora.shared.data.network.dto.RegisterRequestDto
import com.mentora.shared.data.network.dto.SessionUserDto
import kotlinx.coroutines.flow.StateFlow

/**
 * The real [AuthRepository]. [apiClient]'s underlying `HttpClient` is expected to already have
 * [com.mentora.shared.auth.installAuthInterception] installed with the SAME [sessionManager]
 * passed here — this class never attaches `Authorization` itself, it only calls the 4 auth
 * endpoints and maps their wire shape to/from the domain types (see
 * `execution/PHASE_3_KMP_PLAN.md` Task 5's "Where to put things").
 */
internal class AuthRepositoryImpl(
    private val apiClient: ApiClient,
    private val sessionManager: SessionManager,
    private val tokenStorage: TokenStorage,
) : AuthRepository {

    override val authState: StateFlow<AuthState> get() = sessionManager.authState

    override suspend fun register(email: String, password: String, name: String): ApiResult<SessionUser> {
        val result = apiClient.post<RegisterRequestDto, AuthResponseDto>(
            "/api/v1/auth/register",
            RegisterRequestDto(email, password, name),
        )
        return handleAuthResponse(result)
    }

    override suspend fun login(email: String, password: String): ApiResult<SessionUser> {
        val result = apiClient.post<LoginRequestDto, AuthResponseDto>(
            "/api/v1/auth/login",
            LoginRequestDto(email, password),
        )
        return handleAuthResponse(result)
    }

    override suspend fun logout(): ApiResult<Unit> {
        val refreshToken = tokenStorage.readTokens()?.refreshToken.orEmpty()
        val result = apiClient.post<RefreshRequestDto, EmptyResponseDto>(
            "/api/v1/auth/logout",
            RefreshRequestDto(refreshToken),
        )
        // Always clear locally, even if the network call itself failed — see AuthRepository kdoc.
        sessionManager.onSignedOut()
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(Unit)
            is ApiResult.Failure -> result
        }
    }

    override suspend fun refreshSession(): ApiResult<Unit> {
        val staleToken = sessionManager.currentAccessToken()
        val refreshed = sessionManager.refreshAccessToken(staleToken)
        return if (refreshed != null) {
            ApiResult.Success(Unit)
        } else {
            ApiResult.Failure(
                code = ApiErrorCode.AuthTokenInvalid,
                message = "Session refresh failed; the session has been signed out.",
                fields = null,
                httpStatus = 401,
            )
        }
    }

    override suspend fun restoreSession(): AuthState {
        val tokens = tokenStorage.readTokens()
        // user = null: TokenStorage carries only AuthTokens, never identity, so a cold-start
        // restore is honestly "authenticated, profile unknown yet" rather than a fabricated
        // placeholder — see AuthState.Authenticated's kdoc. The caller (Task 6+) is expected to
        // follow up with a GET /users/me fetch whenever it observes a null user here.
        val state = if (tokens == null) AuthState.Unauthenticated else AuthState.Authenticated(user = null)
        sessionManager.setState(state)
        return state
    }

    private suspend fun handleAuthResponse(result: ApiResult<AuthResponseDto>): ApiResult<SessionUser> =
        when (result) {
            is ApiResult.Success -> {
                val user = result.data.user.toDomain()
                sessionManager.onAuthenticated(AuthTokens(result.data.accessToken, result.data.refreshToken), user)
                ApiResult.Success(user)
            }
            is ApiResult.Failure -> result
        }

    private fun SessionUserDto.toDomain(): SessionUser =
        SessionUser(id = id, email = email, name = name, role = role, preferredLocale = preferredLocale)
}
