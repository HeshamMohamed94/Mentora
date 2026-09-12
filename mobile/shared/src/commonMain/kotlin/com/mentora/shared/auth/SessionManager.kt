package com.mentora.shared.auth

import com.mentora.shared.data.network.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the single source of truth for the current session: the [authState] `StateFlow` every
 * screen observes, the in-memory access-token cache the auth plugin
 * ([installAuthInterception]) reads on every outgoing request's hot path, and the single-flight
 * refresh coordinator described in `execution/PHASE_3_KMP_PLAN.md` Task 5.
 *
 * [refreshTokens] is injected as a plain function rather than `SessionManager` depending on
 * `ApiClient`/`AuthRepository` directly — it is the one thing [installAuthInterception] needs to
 * actually perform a `POST /auth/refresh` call. Whoever wires this module together supplies it as
 * a call through the ordinary `ApiClient` against the interception-exempt `/auth/refresh` path
 * (see `AuthRepositoryImpl`). Keeping it as a function type keeps `SessionManager` itself free of
 * any networking-layer dependency of its own.
 */
class SessionManager(
    private val tokenStorage: TokenStorage,
    private val refreshTokens: suspend (refreshToken: String) -> ApiResult<AuthTokens>,
) {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    @Volatile
    private var cachedAccessToken: String? = null

    private val refreshMutex = Mutex()

    /**
     * Read by the auth plugin on every request's hot path — cached in memory so a normal request
     * never blocks on [tokenStorage] disk/Keystore I/O; storage is only consulted once (e.g. right
     * after process start, before anything else has populated the cache).
     */
    suspend fun currentAccessToken(): String? {
        cachedAccessToken?.let { return it }
        val token = tokenStorage.readTokens()?.accessToken
        cachedAccessToken = token
        return token
    }

    /**
     * Called after a successful register/login/refresh: persists [tokens] (the ONLY place
     * `shared` persists them — [tokenStorage], never
     * [com.mentora.shared.settings.PreferenceStore]), caches the new access token, and publishes
     * [AuthState.Authenticated].
     */
    suspend fun onAuthenticated(tokens: AuthTokens, user: SessionUser) {
        tokenStorage.saveTokens(tokens)
        cachedAccessToken = tokens.accessToken
        _authState.value = AuthState.Authenticated(user)
    }

    /**
     * Clears storage + the in-memory cache and publishes [AuthState.Unauthenticated] — used both
     * by an explicit logout and by a failed refresh (see [refreshAccessToken]). Never throws, never
     * navigates — the platform layer reacts to [authState] however it sees fit.
     */
    suspend fun onSignedOut() {
        tokenStorage.clearTokens()
        cachedAccessToken = null
        _authState.value = AuthState.Unauthenticated
    }

    /**
     * Directly publishes [state] with no storage side effect — used only by
     * [com.mentora.shared.data.repository.auth.AuthRepositoryImpl.restoreSession] to set the
     * cold-start initial state from already-persisted tokens.
     */
    fun setState(state: AuthState) {
        _authState.value = state
    }

    /**
     * Single-flight refresh coordinator. [staleAccessToken] is the access token the caller
     * observed a `401 AUTH_TOKEN_EXPIRED` for (or `null`/absent if there wasn't one cached at all).
     * Concurrent callers racing on the SAME expired token all pass in that same [staleAccessToken]
     * (each read it from [currentAccessToken] before anything rotated it), so under
     * [refreshMutex]:
     *  - the FIRST caller to acquire the lock finds [cachedAccessToken] still equal to
     *    [staleAccessToken] and performs the real network refresh, rotating [cachedAccessToken].
     *  - every OTHER concurrent caller, once it in turn acquires the lock (after the first
     *    releases it), finds [cachedAccessToken] has already changed — it no longer equals its own
     *    [staleAccessToken] — and short-circuits to the already-rotated tokens instead of calling
     *    the network a second time.
     *
     * This guarantees exactly one `POST /auth/refresh` call for any number of requests that raced
     * on the same expired token, using only a `Mutex` (no extra `CoroutineScope`/`Deferred`
     * machinery, and therefore no risk of one caller's cancellation tearing down another's
     * in-flight refresh). Returns `null` (after clearing the session — see [onSignedOut]) if the
     * refresh itself fails or there is no refresh token to use.
     */
    suspend fun refreshAccessToken(staleAccessToken: String?): AuthTokens? = refreshMutex.withLock {
        val alreadyRotatedByAnotherCaller =
            staleAccessToken != null && cachedAccessToken != null && cachedAccessToken != staleAccessToken
        if (alreadyRotatedByAnotherCaller) {
            tokenStorage.readTokens()
        } else {
            performRefresh()
        }
    }

    private suspend fun performRefresh(): AuthTokens? {
        val refreshToken = tokenStorage.readTokens()?.refreshToken
        if (refreshToken.isNullOrBlank()) {
            onSignedOut()
            return null
        }
        return when (val result = refreshTokens(refreshToken)) {
            is ApiResult.Success -> {
                // The backend rotates the refresh token on every use (AuthService.kt's
                // `refresh()`) — persisting the ROTATED pair here, never the stale one, is
                // load-bearing: a stale refresh token is rejected next use.
                tokenStorage.saveTokens(result.data)
                cachedAccessToken = result.data.accessToken
                result.data
            }
            is ApiResult.Failure -> {
                onSignedOut()
                null
            }
        }
    }
}
