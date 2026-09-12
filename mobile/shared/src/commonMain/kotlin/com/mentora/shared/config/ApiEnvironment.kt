package com.mentora.shared.config

/**
 * Connect/request/socket timeout configuration for the shared Ktor [io.ktor.client.HttpClient].
 * Defaults are generous enough for a mobile connection while still failing fast enough that a
 * dead network doesn't hang a screen indefinitely.
 */
data class ApiTimeouts(
    val connectTimeoutMillis: Long = 15_000,
    val requestTimeoutMillis: Long = 30_000,
    val socketTimeoutMillis: Long = 30_000,
)

/**
 * The single source of truth for where the shared networking layer points, and how patient it is.
 * No URL is hardcoded anywhere else in `shared` — every call site obtains an [ApiEnvironment] from
 * one of the factories below (or the fully custom [custom] override) and threads it through
 * `HttpClientFactory`/`ApiClient` construction, per `execution/PHASE_3_KMP_PLAN.md` Task 3.
 *
 * The backend listens on port 8080 locally (`backend/src/main/resources/application.conf`); every
 * route is mounted under `/api/v1/...` (see e.g. `AuthRoutes.kt:29`), so [baseUrl] here is the bare
 * host root — callers (repositories, from Task 5 onward) pass the `/api/v1/...`-prefixed path to
 * `ApiClient`.
 */
data class ApiEnvironment(
    val baseUrl: String,
    val timeouts: ApiTimeouts = ApiTimeouts(),
) {
    companion object {
        /** The Android emulator's alias for the host machine's `localhost`. */
        fun androidEmulator(timeouts: ApiTimeouts = ApiTimeouts()): ApiEnvironment =
            ApiEnvironment(baseUrl = "http://10.0.2.2:8080", timeouts = timeouts)

        /** The iOS simulator shares the host machine's network namespace, so plain `localhost` works. */
        fun iosSimulator(timeouts: ApiTimeouts = ApiTimeouts()): ApiEnvironment =
            ApiEnvironment(baseUrl = "http://localhost:8080", timeouts = timeouts)

        /** A physical device on the same LAN as a backend running on a developer machine. */
        fun lan(host: String, port: Int = 8080, timeouts: ApiTimeouts = ApiTimeouts()): ApiEnvironment =
            ApiEnvironment(baseUrl = "http://$host:$port", timeouts = timeouts)

        /** Fully custom override — e.g. a staging/production deployment's public base URL. */
        fun custom(baseUrl: String, timeouts: ApiTimeouts = ApiTimeouts()): ApiEnvironment =
            ApiEnvironment(baseUrl = baseUrl, timeouts = timeouts)
    }
}

/**
 * The ONE place `shared` turns a backend-issued RELATIVE url (e.g. a `PlaybackSource.url`,
 * `/api/v1/media/{id}/stream?token=...`) into an ABSOLUTE one a platform player/image loader can be
 * handed directly, per `execution/PHASE_3_KMP_PLAN.md` Task 13 AC #2. Every caller that needs this —
 * `GetLessonPlaybackSourceUseCase`, `ResolveThumbnailUrlUseCase` — goes through this single function
 * rather than re-implementing string concatenation at its own call site.
 *
 * Defensive against the two sources of double/missing slashes: a trailing slash on [ApiEnvironment.baseUrl]
 * and/or a missing/present leading slash on [relativePath] never produce `//` or a missing `/` in the
 * result.
 */
fun ApiEnvironment.resolveUrl(relativePath: String): String =
    "${baseUrl.trimEnd('/')}/${relativePath.trimStart('/')}"
