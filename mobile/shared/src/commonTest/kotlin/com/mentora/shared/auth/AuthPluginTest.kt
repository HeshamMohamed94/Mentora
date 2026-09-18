package com.mentora.shared.auth

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.data.network.dto.RefreshRequestDto
import com.mentora.shared.data.network.dto.RefreshResponseDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class TestCourse(val id: String, val title: String)

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

/** Wires an [ApiClient] whose underlying client has [installAuthInterception] installed, plus the
 * [SessionManager] it shares — the exact same construction order a real DI wiring (Task 15) would
 * use: build the client, wrap it in [ApiClient], build [SessionManager] with a refresh lambda that
 * calls back through that SAME [ApiClient] (the `/auth/refresh` path is interception-exempt, so
 * this never recurses), then attach the interceptor last. */
private fun wire(engine: MockEngine, tokenStorage: TokenStorage): Pair<ApiClient, SessionManager> {
    val httpClient = HttpClientFactory.create(engine, testEnvironment)
    val apiClient = ApiClient(httpClient)
    val sessionManager = SessionManager(tokenStorage) { refreshToken ->
        when (val result = apiClient.post<RefreshRequestDto, RefreshResponseDto>(
            "/api/v1/auth/refresh",
            RefreshRequestDto(refreshToken),
        )) {
            is ApiResult.Success -> ApiResult.Success(AuthTokens(result.data.accessToken, result.data.refreshToken))
            is ApiResult.Failure -> result
        }
    }
    httpClient.installAuthInterception(sessionManager)
    return apiClient to sessionManager
}

class AuthPluginTest {

    @Test
    fun `401 AUTH_TOKEN_EXPIRED triggers exactly one refresh then a successful retry`() = runTest {
        var refreshCallCount = 0
        var protectedCallCount = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/auth/refresh") -> {
                    refreshCallCount++
                    respond(
                        content = """{"data":{"accessToken":"new-access","refreshToken":"new-refresh"},"meta":{"requestId":"r"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
                else -> {
                    protectedCallCount++
                    val authHeader = request.headers[HttpHeaders.Authorization]
                    if (authHeader == "Bearer old-access") {
                        respond(
                            content = """{"error":{"code":"AUTH_TOKEN_EXPIRED","message":"expired"},"meta":{"requestId":"r"}}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders(),
                        )
                    } else {
                        respond(
                            content = """{"data":{"id":"c1","title":"Kotlin"},"meta":{"requestId":"r"}}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders(),
                        )
                    }
                }
            }
        }
        val tokenStorage = FakeTokenStorage(AuthTokens("old-access", "old-refresh"))
        val (apiClient, _) = wire(engine, tokenStorage)

        val result = apiClient.get<TestCourse>("/api/v1/courses/c1")

        require(result is ApiResult.Success)
        assertEquals(TestCourse("c1", "Kotlin"), result.data)
        assertEquals(1, refreshCallCount)
        assertEquals(2, protectedCallCount, "expected the original 401 attempt plus one retry")
        assertEquals(AuthTokens("new-access", "new-refresh"), tokenStorage.readTokens(), "the ROTATED pair must be persisted")
    }

    /**
     * Regression test for a real contract-drift bug found live against the running backend in
     * `execution/PHASE_3_KMP_PLAN.md` Task 16 (see [REFRESH_TRIGGERING_CODES]'s kdoc for the full
     * account): the backend's JWT `Authentication` plugin never actually emits `AUTH_TOKEN_EXPIRED`
     * for an expired/invalid ACCESS token on a protected route — it always emits
     * `AUTH_TOKEN_INVALID` instead. Without this code also triggering a refresh attempt, the whole
     * 401→refresh→retry flow this task exists to build would never fire against the real backend.
     */
    @Test
    fun `401 AUTH_TOKEN_INVALID - the code the real backend actually emits for an expired access token - also triggers exactly one refresh then a successful retry`() = runTest {
        var refreshCallCount = 0
        var protectedCallCount = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/auth/refresh") -> {
                    refreshCallCount++
                    respond(
                        content = """{"data":{"accessToken":"new-access","refreshToken":"new-refresh"},"meta":{"requestId":"r"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
                else -> {
                    protectedCallCount++
                    val authHeader = request.headers[HttpHeaders.Authorization]
                    if (authHeader == "Bearer old-access") {
                        respond(
                            content = """{"error":{"code":"AUTH_TOKEN_INVALID","message":"invalid"},"meta":{"requestId":"r"}}""",
                            status = HttpStatusCode.Unauthorized,
                            headers = jsonHeaders(),
                        )
                    } else {
                        respond(
                            content = """{"data":{"id":"c1","title":"Kotlin"},"meta":{"requestId":"r"}}""",
                            status = HttpStatusCode.OK,
                            headers = jsonHeaders(),
                        )
                    }
                }
            }
        }
        val tokenStorage = FakeTokenStorage(AuthTokens("old-access", "old-refresh"))
        val (apiClient, _) = wire(engine, tokenStorage)

        val result = apiClient.get<TestCourse>("/api/v1/courses/c1")

        require(result is ApiResult.Success)
        assertEquals(TestCourse("c1", "Kotlin"), result.data)
        assertEquals(1, refreshCallCount)
        assertEquals(2, protectedCallCount, "expected the original 401 attempt plus one retry")
        assertEquals(AuthTokens("new-access", "new-refresh"), tokenStorage.readTokens(), "the ROTATED pair must be persisted")
    }

    @Test
    fun `a 401 from refresh or logout itself is never re-intercepted`() = runTest {
        var refreshHitCount = 0
        val engine = MockEngine {
            refreshHitCount++
            respond(
                content = """{"error":{"code":"AUTH_TOKEN_EXPIRED","message":"expired"},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders(),
            )
        }
        val tokenStorage = FakeTokenStorage(AuthTokens("access", "refresh"))
        val httpClient = HttpClientFactory.create(engine, testEnvironment)
        val apiClient = ApiClient(httpClient)
        val sessionManager = SessionManager(tokenStorage) {
            error("refreshTokens must never be invoked when the /auth/refresh call itself 401s")
        }
        httpClient.installAuthInterception(sessionManager)

        val result = apiClient.post<RefreshRequestDto, RefreshResponseDto>(
            "/api/v1/auth/refresh",
            RefreshRequestDto("refresh"),
        )

        require(result is ApiResult.Failure)
        assertEquals(1, refreshHitCount, "the exempt /auth/refresh path must never be re-intercepted/retried")
    }

    @Test
    fun `logout 401 is also exempt from interception`() = runTest {
        var logoutHitCount = 0
        val engine = MockEngine {
            logoutHitCount++
            respond(
                content = """{"error":{"code":"AUTH_TOKEN_EXPIRED","message":"expired"},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders(),
            )
        }
        val tokenStorage = FakeTokenStorage(AuthTokens("access", "refresh"))
        val httpClient = HttpClientFactory.create(engine, testEnvironment)
        val apiClient = ApiClient(httpClient)
        val sessionManager = SessionManager(tokenStorage) {
            error("refreshTokens must never be invoked for the exempt /auth/logout path")
        }
        httpClient.installAuthInterception(sessionManager)

        val result = apiClient.post<RefreshRequestDto, com.mentora.shared.data.network.dto.EmptyResponseDto>(
            "/api/v1/auth/logout",
            RefreshRequestDto("refresh"),
        )

        require(result is ApiResult.Failure)
        assertEquals(1, logoutHitCount)
    }

    @Test
    fun `a failed refresh clears TokenStorage and sets Unauthenticated with no retry`() = runTest {
        var protectedCallCount = 0
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/auth/refresh")) {
                respond(
                    content = """{"error":{"code":"AUTH_TOKEN_INVALID","message":"bad refresh token"},"meta":{"requestId":"r"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders(),
                )
            } else {
                protectedCallCount++
                respond(
                    content = """{"error":{"code":"AUTH_TOKEN_EXPIRED","message":"expired"},"meta":{"requestId":"r"}}""",
                    status = HttpStatusCode.Unauthorized,
                    headers = jsonHeaders(),
                )
            }
        }
        val tokenStorage = FakeTokenStorage(AuthTokens("access", "refresh"))
        val (apiClient, sessionManager) = wire(engine, tokenStorage)
        sessionManager.setState(AuthState.Authenticated(SessionUser("1", "a@b.com", "A", Role.Student, null)))

        val result = apiClient.get<TestCourse>("/api/v1/courses/c1")

        require(result is ApiResult.Failure)
        assertEquals(1, protectedCallCount, "must not retry the original request after a failed refresh")
        assertNull(tokenStorage.readTokens())
        assertEquals(AuthState.Unauthenticated, sessionManager.authState.value)
    }

    /**
     * The most important test in this task (`execution/PHASE_3_KMP_PLAN.md` Task 5 AC #3): fires N
     * GENUINELY concurrent requests (`async {}` + `awaitAll()`, not sequential calls that happen
     * not to race) that all observe the SAME expired access token, and asserts the mock
     * `/auth/refresh` endpoint was hit exactly once — never N times.
     *
     * Every mock response `delay(1)`s before responding, forcing a real suspension point on EVERY
     * request (the initial 401, the refresh call, and the retry). Under `runTest`'s cooperative,
     * deterministic virtual-time scheduler this guarantees all N requests reach "attach the stale
     * token and call the server" before any single one of them can complete a full refresh+retry
     * cycle — i.e. it proves the race is actually exercised, not merely that requests happened to
     * run one at a time (which would trivially only ever see the stale token once).
     */
    @Test
    fun `N concurrent 401s coalesce into exactly one refresh call`() = runTest {
        val concurrentRequestCount = 8
        var refreshCallCount = 0
        var protectedCallCount = 0
        val engine = MockEngine { request ->
            delay(1)
            if (request.url.encodedPath.endsWith("/auth/refresh")) {
                refreshCallCount++
                respond(
                    content = """{"data":{"accessToken":"new-access","refreshToken":"new-refresh"},"meta":{"requestId":"r"}}""",
                    status = HttpStatusCode.OK,
                    headers = jsonHeaders(),
                )
            } else {
                protectedCallCount++
                val authHeader = request.headers[HttpHeaders.Authorization]
                if (authHeader == "Bearer old-access") {
                    respond(
                        content = """{"error":{"code":"AUTH_TOKEN_EXPIRED","message":"expired"},"meta":{"requestId":"r"}}""",
                        status = HttpStatusCode.Unauthorized,
                        headers = jsonHeaders(),
                    )
                } else {
                    respond(
                        content = """{"data":{"id":"c1","title":"Kotlin"},"meta":{"requestId":"r"}}""",
                        status = HttpStatusCode.OK,
                        headers = jsonHeaders(),
                    )
                }
            }
        }
        val tokenStorage = FakeTokenStorage(AuthTokens("old-access", "old-refresh"))
        val (apiClient, _) = wire(engine, tokenStorage)

        val results = (1..concurrentRequestCount)
            .map { index -> async { apiClient.get<TestCourse>("/api/v1/courses/c$index") } }
            .awaitAll()

        assertEquals(
            1,
            refreshCallCount,
            "expected exactly one refresh call for $concurrentRequestCount concurrent 401s, saw $refreshCallCount",
        )
        assertTrue(
            results.all { it is ApiResult.Success },
            "expected every concurrent request to eventually succeed after the single refresh: $results",
        )
        // Each request makes either 2 calls (hit the stale-token 401, then retried) or — if it
        // happened to be scheduled after the single winning refresh already rotated the cached
        // token — just 1 (its only attempt already carries the new token). Either is correct
        // behavior; what matters (and is asserted above) is that the refresh itself only ever
        // happened once and every request still ends up successful.
        assertTrue(
            protectedCallCount in concurrentRequestCount..(concurrentRequestCount * 2),
            "protectedCallCount=$protectedCallCount out of the expected [$concurrentRequestCount, ${concurrentRequestCount * 2}] range",
        )
        assertEquals(AuthTokens("new-access", "new-refresh"), tokenStorage.readTokens())
    }
}
