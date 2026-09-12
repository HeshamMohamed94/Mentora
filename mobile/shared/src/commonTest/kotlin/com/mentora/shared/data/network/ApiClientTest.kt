package com.mentora.shared.data.network

import com.mentora.shared.config.ApiEnvironment
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Serializable
private data class TestCourse(val id: String, val title: String)

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun clientFor(engine: MockEngine): ApiClient = ApiClient(HttpClientFactory.create(engine, testEnvironment))

class ApiClientTest {

    @Test
    fun `2xx response unwraps to Success with correctly decoded data`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"data":{"id":"c1","title":"Kotlin"},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = clientFor(engine).get<TestCourse>("/api/v1/courses/c1")

        assertEquals(ApiResult.Success(TestCourse("c1", "Kotlin")), result)
    }

    @Test
    fun `4xx response with a valid ApiError body maps to the correct typed Failure`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"COURSE_NOT_FOUND","message":"nope"},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }

        val result = clientFor(engine).get<TestCourse>("/api/v1/courses/missing")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.CourseNotFound, result.code)
        assertEquals(404, result.httpStatus)
    }

    @Test
    fun `5xx response with an unparseable body still maps to a Failure, not a thrown exception`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = "<html><body>Internal Server Error</body></html>",
                status = HttpStatusCode.InternalServerError,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Html.toString()),
            )
        }

        val result = clientFor(engine).get<TestCourse>("/api/v1/courses/c1")

        require(result is ApiResult.Failure)
        assertEquals(500, result.httpStatus)
    }

    @Test
    fun `a network-level exception maps to a Failure, not a thrown exception`() = runBlocking {
        val engine = MockEngine { throw RuntimeException("connection refused") }

        val result = clientFor(engine).get<TestCourse>("/api/v1/courses/c1")

        require(result is ApiResult.Failure)
        assertEquals(0, result.httpStatus)
    }

    @Test
    fun `429 on an auth path routes through synthesizeRateLimitedFailure`() = runBlocking {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.TooManyRequests) }

        val result = clientFor(engine).post<Map<String, String>, TestCourse>(
            "/api/v1/auth/login",
            emptyMap(),
        )

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.RateLimitedAuth, result.code)
        assertEquals(429, result.httpStatus)
    }

    @Test
    fun `429 on an ai-tutor path routes through synthesizeRateLimitedFailure`() = runBlocking {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.TooManyRequests) }

        val result = clientFor(engine).get<TestCourse>("/api/v1/ai-tutor/conversation")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.RateLimitedAiTutor, result.code)
    }

    @Test
    fun `X-Requested-With mentora-web header is present on every verb`() = runBlocking {
        val seenHeaders = mutableListOf<String?>()
        val engine = MockEngine { request ->
            seenHeaders += request.headers["X-Requested-With"]
            respond(
                content = """{"data":{"id":"c1","title":"Kotlin"},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val client = clientFor(engine)

        client.get<TestCourse>("/api/v1/courses/c1")
        client.post<TestCourse, TestCourse>("/api/v1/courses", TestCourse("c1", "Kotlin"))
        client.patch<TestCourse, TestCourse>("/api/v1/courses/c1", TestCourse("c1", "Kotlin"))
        client.delete<TestCourse>("/api/v1/courses/c1")

        assertEquals(4, seenHeaders.size)
        seenHeaders.forEach { assertEquals("mentora-web", it) }
    }

    @Test
    fun `a GET request that fails transiently is retried`() = runBlocking {
        var requestCount = 0
        val engine = MockEngine {
            requestCount++
            respond(content = "", status = HttpStatusCode.ServiceUnavailable)
        }

        clientFor(engine).get<TestCourse>("/api/v1/courses/c1")

        assertTrue(requestCount > 1, "expected the GET to be retried, but MockEngine only saw $requestCount request(s)")
    }

    @Test
    fun `a POST request that fails the same way is NOT retried`() = runBlocking {
        var requestCount = 0
        val engine = MockEngine {
            requestCount++
            respond(content = "", status = HttpStatusCode.ServiceUnavailable)
        }

        clientFor(engine).post<TestCourse, TestCourse>("/api/v1/courses", TestCourse("c1", "Kotlin"))

        assertEquals(1, requestCount, "expected POST to never be retried")
    }

    @Test
    fun `no Cookie header is ever sent, even after a response carries Set-Cookie`() = runBlocking {
        var requestCount = 0
        var sawCookieHeader = false
        val engine = MockEngine { request ->
            requestCount++
            if (request.headers.contains(HttpHeaders.Cookie)) sawCookieHeader = true
            respond(
                content = """{"data":{"id":"c1","title":"Kotlin"},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(
                    HttpHeaders.ContentType to listOf(ContentType.Application.Json.toString()),
                    HttpHeaders.SetCookie to listOf("mentora_access_token=stale-token; Path=/; HttpOnly"),
                ),
            )
        }
        val client = clientFor(engine)

        client.get<TestCourse>("/api/v1/courses/c1")
        client.get<TestCourse>("/api/v1/courses/c1")

        assertEquals(2, requestCount)
        assertFalse(sawCookieHeader, "the client must never replay a captured Set-Cookie as a Cookie header")
    }

    @Test
    fun `getPage assembles a CursorPage from the data array and meta nextCursor when present`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"data":[{"id":"c1","title":"Kotlin"},{"id":"c2","title":"Swift"}],"meta":{"requestId":"r1","nextCursor":"cursor-2"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = clientFor(engine).getPage<TestCourse>("/api/v1/courses")

        require(result is ApiResult.Success)
        assertEquals(listOf(TestCourse("c1", "Kotlin"), TestCourse("c2", "Swift")), result.data.items)
        assertEquals("cursor-2", result.data.nextCursor)
    }

    @Test
    fun `getPage assembles a CursorPage with a null nextCursor when absent`() = runBlocking {
        val engine = MockEngine {
            respond(
                content = """{"data":[{"id":"c1","title":"Kotlin"}],"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = clientFor(engine).getPage<TestCourse>("/api/v1/courses")

        require(result is ApiResult.Success)
        assertEquals(listOf(TestCourse("c1", "Kotlin")), result.data.items)
        assertNull(result.data.nextCursor)
    }
}
