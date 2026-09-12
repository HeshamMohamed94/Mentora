package com.mentora.shared.data.network

import com.mentora.shared.config.ApiEnvironment
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpMethod
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException

private const val CSRF_HEADER_NAME = "X-Requested-With"
private const val CSRF_HEADER_VALUE = "mentora-web"

/** Bounded, GET-only retry policy — see [installRetryPolicy] doc for the full rationale. */
private const val MAX_RETRIES = 2

/**
 * Builds the single, centrally-configured Ktor [HttpClient] shared by every [ApiClient] call.
 * Base URL, timeouts, and retry policy are all wired up ONCE here — callers never construct their
 * own client or override these per-repository (`execution/PHASE_3_KMP_PLAN.md` Task 3).
 *
 * The HTTP engine itself is injected by the platform (OkHttp on Android, Darwin on iOS, `MockEngine`
 * in tests) so this function stays pure Kotlin/commonMain.
 */
object HttpClientFactory {

    fun create(
        engine: HttpClientEngine,
        environment: ApiEnvironment,
        enableLogging: Boolean = false,
    ): HttpClient = HttpClient(engine) {
        // Non-2xx responses are returned as normal HttpResponses, not thrown as exceptions —
        // ApiClient decodes the ApiError envelope from them itself. Set explicitly since this is
        // load-bearing for how ApiClient is written, not just relying on the Ktor 3 default.
        expectSuccess = false

        install(ContentNegotiation) {
            // Reuses Task 2's single shared Json instance — never a second Json{} config.
            json(MentoraJson)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = environment.timeouts.connectTimeoutMillis
            requestTimeoutMillis = environment.timeouts.requestTimeoutMillis
            socketTimeoutMillis = environment.timeouts.socketTimeoutMillis
        }

        if (enableLogging) {
            install(Logging) {
                logger = RedactingLogger
                // HEADERS (not BODY/ALL): request/response bodies are never logged, even in debug
                // builds — a login/register/refresh body carries a password or refresh token, and
                // a lesson/AI-tutor body can carry user content. Headers are logged, but
                // RedactingLogger scrubs the Authorization value before it ever reaches the log.
                level = LogLevel.HEADERS
            }
        }

        installRetryPolicy()

        defaultRequest {
            url(environment.baseUrl)
            // Backend's `common/Csrf.kt` demands this literal value on every mutating route. It is
            // harmless on GET/HEAD (the backend only checks it on POST/PATCH/PUT/DELETE), so it is
            // simplest and matches the plan to send it on every request rather than branching on verb.
            header(CSRF_HEADER_NAME, CSRF_HEADER_VALUE)
        }

        // Deliberately NOT installing `HttpCookies` here.
        //
        // `backend/src/main/kotlin/com/mentora/backend/plugins/Security.kt:27-31` resolves the JWT
        // by preferring the `mentora_access_token` COOKIE over the `Authorization` header. If this
        // client captured and replayed cookies (as `HttpCookies` would), a stale, already-rotated
        // access-token cookie from an earlier response would silently outrank a freshly-refreshed
        // Bearer token attached by Task 5's auth plugin — resurrecting an expired/rotated session's
        // identity on every subsequent request. Mobile auth is Bearer-header-only; no cookie jar.
    }
}

/**
 * Retries ONLY safe, idempotent GET requests on transient failures (a 5xx response, or a transport
 * exception such as a dropped connection) — never a mutating verb (POST/PATCH/PUT/DELETE), since
 * those are not safe to blindly re-send (e.g. `POST /courses/{id}/checkout/complete`, quiz attempt
 * submission). Bounded to [MAX_RETRIES] retries with exponential backoff.
 */
private fun HttpClientConfig<*>.installRetryPolicy() {
    install(HttpRequestRetry) {
        maxRetries = MAX_RETRIES
        retryIf { request, response ->
            request.method == HttpMethod.Get && response.status.value >= 500
        }
        retryOnExceptionIf { request, cause ->
            request.method == HttpMethod.Get && cause is IOException
        }
        // Short base delay: a mobile GET retry should not make the user wait multiple seconds for
        // a screen to fail; 300ms/600ms is enough spacing for a transient blip to clear.
        exponentialDelay(baseDelayMs = 300, maxDelayMs = 3_000, randomizationMs = 100)
    }
}

/**
 * Redacts token-shaped values before they ever reach the console/log sink. Covers the
 * `Authorization` header line the [Logging] plugin prints at [LogLevel.HEADERS], plus any bearer
 * token or `accessToken`/`refreshToken` field shape that might otherwise leak through a future
 * change to the logged level.
 */
private object RedactingLogger : Logger {
    private val redactions = listOf(
        Regex("(?i)(authorization:\\s*)\\S+") to "$1[REDACTED]",
        Regex("(?i)(bearer\\s+)[A-Za-z0-9._-]+") to "$1[REDACTED]",
        Regex("(?i)(\"?(?:access|refresh)Token\"?\\s*[:=]\\s*\"?)[A-Za-z0-9._-]+") to "$1[REDACTED]",
    )

    override fun log(message: String) {
        var redacted = message
        for ((pattern, replacement) in redactions) {
            redacted = pattern.replace(redacted, replacement)
        }
        println(redacted)
    }
}
