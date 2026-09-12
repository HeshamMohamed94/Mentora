package com.mentora.shared.auth

import com.mentora.shared.data.network.ApiError
import com.mentora.shared.data.network.MentoraJson
import io.ktor.client.HttpClient
import io.ktor.client.call.save
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode

/**
 * Path suffixes exempt from BOTH bearer-token attachment and the 401-triggered auto-refresh loop.
 * `/auth/refresh` and `/auth/logout` authenticate with a refresh token carried in their own JSON
 * body, not a Bearer access token — and refreshing-the-refresh-call would recurse forever
 * (`execution/PHASE_3_KMP_PLAN.md` Task 5, AC #6).
 */
private val AUTH_INTERCEPTION_EXEMPT_SUFFIXES = listOf("/auth/refresh", "/auth/logout")

private const val AUTH_TOKEN_EXPIRED_CODE = "AUTH_TOKEN_EXPIRED"

/**
 * Real contract drift, discovered live against the running backend during
 * `execution/PHASE_3_KMP_PLAN.md` Task 16 (see that task's report for the full account): the JWT
 * `Authentication` plugin's `challenge` handler
 * (`backend/src/main/kotlin/com/mentora/backend/plugins/Security.kt:39`) throws
 * `ApiException.TokenInvalid()` UNCONDITIONALLY whenever a protected route's Bearer token fails
 * verification for ANY reason — missing, malformed, wrong signature, OR genuinely expired. The
 * backend's `AUTH_TOKEN_EXPIRED` code is emitted from exactly one place in the whole codebase
 * (`AuthService.kt:78`), and only when the *refresh token itself* (not the access token) is
 * expired inside `POST /auth/refresh` — a call this plugin never re-intercepts (`/auth/refresh` is
 * in [AUTH_INTERCEPTION_EXEMPT_SUFFIXES]). So a real expired/invalid access token on any ordinary
 * protected endpoint ALWAYS arrives here as `AUTH_TOKEN_INVALID`, never `AUTH_TOKEN_EXPIRED` — the
 * original single-code check below silently never fired against the real backend. Both codes are
 * therefore treated as "attempt a refresh" triggers; this is safe because the JWT plugin's
 * `challenge` only ever fires when the attached token itself is the reason a protected route
 * rejected the request (never for an unrelated 401 cause), so there is no over-broad match here.
 */
private val REFRESH_TRIGGERING_CODES = setOf(AUTH_TOKEN_EXPIRED_CODE, "AUTH_TOKEN_INVALID")

/**
 * Installs the 401→single-flight-refresh→retry interception described in
 * `execution/PHASE_3_KMP_PLAN.md` Task 5 onto an already-built [HttpClient] — the same instance
 * [com.mentora.shared.data.network.ApiClient] wraps. Mutates [httpClient] in place (via Ktor's
 * always-installed [HttpSend] send-pipeline) and returns it, so callers don't need to rebuild
 * `ApiClient` around a new client instance.
 *
 * [HttpSend]'s `intercept` is used (rather than a custom `createClientPlugin`) because this needs
 * to both mutate the outgoing request (attach `Authorization`) AND conditionally re-execute the
 * SAME request after an awaited refresh — exactly [HttpSend]'s documented use case (the same
 * mechanism Ktor's own `HttpRequestRetry` plugin is built on).
 *
 * Behavior, per request:
 *  - Exempt paths ([AUTH_INTERCEPTION_EXEMPT_SUFFIXES]): passed straight through, untouched.
 *  - Every other path: attaches `Authorization: Bearer <token>` from
 *    [SessionManager.currentAccessToken]. If the response is `401` with wire body
 *    `error.code` in [REFRESH_TRIGGERING_CODES] (see that constant's kdoc for why both codes are
 *    checked, not just `AUTH_TOKEN_EXPIRED`), calls [SessionManager.refreshAccessToken]
 *    (single-flight — see its kdoc) and, on success, retries the SAME request once with the new
 *    token. A refresh failure, or any other 401 reason (e.g. a login's `AUTH_INVALID_CREDENTIALS`,
 *    which never reaches this plugin attached to a Bearer token in the first place), returns the
 *    original response untouched — never a silent retry loop, never a second refresh attempt for
 *    the same failure.
 */
fun HttpClient.installAuthInterception(sessionManager: SessionManager): HttpClient {
    plugin(HttpSend).intercept { request ->
        val path = request.url.build().encodedPath
        if (AUTH_INTERCEPTION_EXEMPT_SUFFIXES.any { path.endsWith(it) }) {
            return@intercept execute(request)
        }

        val attachedToken = sessionManager.currentAccessToken()
        attachedToken?.let { request.headers.set(HttpHeaders.Authorization, "Bearer $it") }

        var call = execute(request)
        if (call.response.status == HttpStatusCode.Unauthorized) {
            // .save() buffers the whole body so both this interceptor AND the eventual caller
            // (ApiClient's envelope decoding) can each read it once — an HttpResponse body can
            // otherwise only be consumed a single time.
            call = call.save()
            val errorCode = runCatching {
                MentoraJson.decodeFromString(ApiError.serializer(), call.response.bodyAsText()).error.code
            }.getOrNull()

            if (errorCode in REFRESH_TRIGGERING_CODES) {
                val refreshed = sessionManager.refreshAccessToken(attachedToken)
                if (refreshed != null) {
                    request.headers.set(HttpHeaders.Authorization, "Bearer ${refreshed.accessToken}")
                    call = execute(request)
                }
            }
        }
        call
    }
    return this
}
