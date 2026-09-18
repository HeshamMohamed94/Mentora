package com.mentora.shared.auth

import com.mentora.shared.data.network.ApiResult
import kotlin.concurrent.Volatile
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

    /**
     * Bumped only by [onAuthenticated]/[onSignedOut] — the two identity TRANSITION points (a brand
     * new session established, or the current one torn down) — never by [performRefreshLocked]'s
     * successful branch (that rotates the token in place but stays the SAME logical session). Paired
     * with a token in [SessionSnapshot] so [refreshAccessToken] can tell "this token was already
     * refreshed by a sibling caller of the SAME session" (legitimate short-circuit — see that
     * method's own kdoc) apart from "the session changed identity entirely while my request was in
     * flight" (Codex-identified gap, T11 fix-up — see [refreshAccessToken]'s kdoc for the concrete
     * failure scenario this closes).
     */
    @Volatile
    private var sessionGeneration: Int = 0

    /**
     * Guards every WRITE to [cachedAccessToken]/[sessionGeneration] — the disk-fallback populate
     * below, [onAuthenticated], [onSignedOut], and [refreshAccessToken]/[performRefreshLocked].
     * `@Volatile` alone only gives visibility, not atomicity: [currentAccessToken]'s disk-fallback
     * branch used to read [tokenStorage] and write the result back unconditionally, with no
     * re-check — a concrete failing interleaving this now closes: caller A finds the cache empty and
     * starts the (comparatively slow, real Keystore-backed) disk read; while A is suspended there, a
     * real login/register/logout elsewhere correctly rotates [cachedAccessToken] to a NEW value; A
     * then resumes and overwrites that fresh value with the stale one it read before the rotation
     * even happened — silently reverting the session to a previous identity with no error, no 401,
     * nothing to self-heal it. Renamed from the original `refreshMutex` (still fine to use for
     * [refreshAccessToken]/[performRefreshLocked]'s own single-flight coordination — none of these
     * methods call each other while already holding it, so there is no re-entrancy/deadlock risk from
     * sharing one lock across all of them).
     */
    private val sessionMutex = Mutex()

    /**
     * [accessToken] paired with the session generation it was read under, atomically (a single
     * `@Volatile` read each, back-to-back with no suspension point between them — see
     * [currentAccessToken]). [refreshAccessToken] needs both together: the token alone can't
     * distinguish a same-session sibling refresh from an unrelated identity change (T11 fix-up).
     */
    data class SessionSnapshot(val accessToken: String?, val generation: Int)

    /**
     * Read by the auth plugin on every request's hot path — cached in memory so a normal request
     * never blocks on [tokenStorage] disk/Keystore I/O; storage is only consulted once (e.g. right
     * after process start, before anything else has populated the cache), under [sessionMutex] with
     * a re-check after acquiring it (double-checked locking — see that property's own kdoc for why
     * the earlier unguarded version was unsafe).
     */
    suspend fun currentAccessToken(): SessionSnapshot {
        cachedAccessToken?.let { return SessionSnapshot(it, sessionGeneration) }
        return sessionMutex.withLock {
            val token = cachedAccessToken ?: tokenStorage.readTokens()?.accessToken?.also { cachedAccessToken = it }
            SessionSnapshot(token, sessionGeneration)
        }
    }

    /**
     * Called after a successful register/login/refresh: persists [tokens] (the ONLY place
     * `shared` persists them — [tokenStorage], never
     * [com.mentora.shared.settings.PreferenceStore]), caches the new access token, and publishes
     * [AuthState.Authenticated].
     */
    suspend fun onAuthenticated(tokens: AuthTokens, user: SessionUser) {
        sessionMutex.withLock {
            tokenStorage.saveTokens(tokens)
            cachedAccessToken = tokens.accessToken
            sessionGeneration++
        }
        _authState.value = AuthState.Authenticated(user)
    }

    /**
     * Clears storage + the in-memory cache and publishes [AuthState.Unauthenticated] — used both
     * by an explicit logout and by a failed refresh (see [refreshAccessToken]). Never throws, never
     * navigates — the platform layer reacts to [authState] however it sees fit.
     */
    suspend fun onSignedOut() {
        sessionMutex.withLock {
            tokenStorage.clearTokens()
            cachedAccessToken = null
            sessionGeneration++
        }
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
     * Updates the CURRENTLY authenticated session's [AuthState.Authenticated.user] in place — no
     * token/storage side effect at all (contrast [onAuthenticated]). This is how
     * [com.mentora.shared.data.repository.user.UserRepositoryImpl] closes the `user = null` gap
     * [com.mentora.shared.data.repository.auth.AuthRepositoryImpl.restoreSession] deliberately
     * leaves after a cold-start restore (see [AuthState.Authenticated]'s kdoc): once a
     * `GET /users/me`/`PATCH /users/me` call succeeds, the session's identity becomes (or stays)
     * known without re-authenticating (`execution/PHASE_3_KMP_PLAN.md` Task 6).
     *
     * A no-op if [authState] is not currently [AuthState.Authenticated] (e.g. a stale in-flight
     * profile fetch completing after a logout) — this never resurrects or creates an authenticated
     * session on its own.
     */
    fun updateUser(user: SessionUser) {
        if (_authState.value is AuthState.Authenticated) {
            _authState.value = AuthState.Authenticated(user)
        }
    }

    /**
     * Single-flight refresh coordinator. [staleToken] is the [SessionSnapshot] the caller read
     * (via [currentAccessToken]) before its request observed a `401 AUTH_TOKEN_EXPIRED`. Concurrent
     * callers racing on the SAME expired token all pass in an equal [staleToken] (each read it
     * before anything rotated it), so under [sessionMutex]:
     *  - the FIRST caller to acquire the lock finds [cachedAccessToken] still equal to
     *    [staleToken]'s token and performs the real network refresh, rotating [cachedAccessToken].
     *  - every OTHER concurrent caller, once it in turn acquires the lock (after the first
     *    releases it), finds [cachedAccessToken] has already changed — it no longer equals its own
     *    stale token — and short-circuits to the already-rotated tokens instead of calling the
     *    network a second time.
     *
     * This guarantees exactly one `POST /auth/refresh` call for any number of requests that raced
     * on the same expired token, using only a `Mutex` (no extra `CoroutineScope`/`Deferred`
     * machinery, and therefore no risk of one caller's cancellation tearing down another's
     * in-flight refresh). Returns `null` (after clearing the session) if the refresh itself fails or
     * there is no refresh token to use — [performRefreshLocked] clears storage/cache itself rather
     * than calling the public [onSignedOut] for this, since [Mutex] isn't reentrant and this whole
     * call chain already runs inside [sessionMutex]'s hold; publishing [AuthState.Unauthenticated] is
     * done here, once the lock is released, so every [authState] publish stays outside the lock
     * (matching [onAuthenticated]/[onSignedOut]) even though the failure is detected inside it.
     *
     * **Generation guard (Codex-identified gap, T11 fix-up).** The "already rotated by another
     * caller" short-circuit above used to compare tokens ALONE — but a token mismatch is also
     * exactly what a full identity change looks like: if the session logs out and a DIFFERENT
     * account logs in while this caller's request was in flight, [cachedAccessToken] now holds that
     * OTHER account's fresh token, which is not [staleToken]'s value either. The old code could not
     * tell those two cases apart, and would hand this caller's request that OTHER account's tokens
     * to retry with — silently completing an original request under the wrong identity. [staleToken]
     * now carries the [SessionSnapshot.generation] it was read under, and this only takes the
     * short-circuit path if [sessionGeneration] is UNCHANGED (still the same session, just refreshed
     * by a sibling); a changed generation instead fails this call outright (`null`, no state
     * published — the CURRENT, different session is left alone) rather than ever adopting a
     * different identity's tokens for a request that was issued under the old one.
     */
    suspend fun refreshAccessToken(staleToken: SessionSnapshot): AuthTokens? {
        val (tokens, shouldPublishSignedOut) = sessionMutex.withLock {
            val identityChangedSinceRead = staleToken.generation != sessionGeneration
            val alreadyRotatedByAnotherCallerOfTheSameSession = !identityChangedSinceRead &&
                staleToken.accessToken != null && cachedAccessToken != null && cachedAccessToken != staleToken.accessToken
            when {
                identityChangedSinceRead -> null to false
                alreadyRotatedByAnotherCallerOfTheSameSession -> tokenStorage.readTokens() to false
                else -> performRefreshLocked()
            }
        }
        if (shouldPublishSignedOut) {
            _authState.value = AuthState.Unauthenticated
        }
        return tokens
    }

    /** Only ever called from inside [refreshAccessToken]'s [sessionMutex] hold — never acquires the
     *  lock itself (that would deadlock; [Mutex] is not reentrant). Returns the refreshed tokens (or
     *  `null`) alongside whether the session was cleared, so [refreshAccessToken] can publish
     *  [authState] after releasing the lock. */
    private suspend fun performRefreshLocked(): Pair<AuthTokens?, Boolean> {
        val refreshToken = tokenStorage.readTokens()?.refreshToken
        if (refreshToken.isNullOrBlank()) {
            tokenStorage.clearTokens()
            cachedAccessToken = null
            return null to true
        }
        return when (val result = refreshTokens(refreshToken)) {
            is ApiResult.Success -> {
                // The backend rotates the refresh token on every use (AuthService.kt's
                // `refresh()`) — persisting the ROTATED pair here, never the stale one, is
                // load-bearing: a stale refresh token is rejected next use.
                tokenStorage.saveTokens(result.data)
                cachedAccessToken = result.data.accessToken
                result.data to false
            }
            is ApiResult.Failure -> {
                tokenStorage.clearTokens()
                cachedAccessToken = null
                null to true
            }
        }
    }
}
