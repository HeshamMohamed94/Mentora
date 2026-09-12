package com.mentora.shared.data.repository.progress

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun OutgoingContent.readText(): String = when (this) {
    is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
    else -> error("Unsupported OutgoingContent for test body capture: $this")
}

private fun repositoryFor(engine: MockEngine): ProgressRepository = ProgressRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)))

private fun progressJson(
    completedLessonIds: String = "[]",
    currentLessonId: String? = null,
    currentPositionSeconds: Int? = null,
    quizPassed: Boolean? = null,
    completionPercent: Int = 0,
    courseCompletedAt: String? = null,
) = """
{"data":{"courseId":"c1","completedLessonIds":$completedLessonIds,
"currentLessonId":${currentLessonId?.let { "\"$it\"" }},"currentPositionSeconds":$currentPositionSeconds,
"quizPassed":$quizPassed,"completionPercent":$completionPercent,
"courseCompletedAt":${courseCompletedAt?.let { "\"$it\"" }}},"meta":{"requestId":"r1"}}
""".trimIndent()

class ProgressRepositoryImplTest {

    // ---- getProgress ----

    @Test
    fun `getProgress hits the exact endpoint and maps every field`() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Get, request.method)
            respond(
                content = progressJson(
                    completedLessonIds = """["l000","l001"]""", currentLessonId = "l002",
                    currentPositionSeconds = 42, quizPassed = true, completionPercent = 17,
                    courseCompletedAt = null,
                ),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getProgress("c1")

        assertEquals("/api/v1/courses/c1/progress", seenPath)
        require(result is ApiResult.Success)
        assertEquals("c1", result.data.courseId)
        assertEquals(listOf("l000", "l001"), result.data.completedLessonIds)
        assertEquals("l002", result.data.currentLessonId)
        assertEquals(42, result.data.currentPositionSeconds)
        assertEquals(true, result.data.quizPassed)
        assertEquals(17, result.data.completionPercent)
        assertEquals(null, result.data.courseCompletedAt)
    }

    @Test
    fun `getProgress surfaces 403 as a typed distinguishable ForbiddenNotEnrolled failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"FORBIDDEN_NOT_ENROLLED","message":"Not enrolled."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.Forbidden,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getProgress("c1")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.ForbiddenNotEnrolled, result.code)
        assertEquals(403, result.httpStatus)
    }

    @Test
    fun `getProgress tolerates omitted optional fields defaulting to null`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"courseId":"c1","completedLessonIds":[],"completionPercent":0},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getProgress("c1")

        require(result is ApiResult.Success)
        assertEquals(null, result.data.currentLessonId)
        assertEquals(null, result.data.currentPositionSeconds)
        assertEquals(null, result.data.quizPassed)
        assertEquals(null, result.data.courseCompletedAt)
    }

    // ---- completeLesson ----

    @Test
    fun `completeLesson posts to the exact endpoint and maps the updated progress`() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Post, request.method)
            respond(
                content = progressJson(completedLessonIds = """["l000"]""", currentLessonId = "l000", completionPercent = 8),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).completeLesson("c1", "l000")

        assertEquals("/api/v1/courses/c1/lessons/l000/complete", seenPath)
        require(result is ApiResult.Success)
        assertEquals(listOf("l000"), result.data.completedLessonIds)
        assertEquals(8, result.data.completionPercent)
    }

    @Test
    fun `completeLesson is safe to call twice on an already-complete lesson`() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            respond(
                content = progressJson(completedLessonIds = """["l000"]""", currentLessonId = "l000", completionPercent = 8),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = repositoryFor(engine)

        val first = repository.completeLesson("c1", "l000")
        val second = repository.completeLesson("c1", "l000")

        require(first is ApiResult.Success)
        require(second is ApiResult.Success)
        assertEquals(first.data, second.data)
        assertEquals(2, callCount)
    }

    // ---- reportPosition ----

    @Test
    fun `reportPosition posts to the exact endpoint with the non-negative body`() = runTest {
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Post, request.method)
            seenBody = request.body.readText()
            respond(
                content = progressJson(currentLessonId = "l001", currentPositionSeconds = 90),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).reportPosition("c1", "l001", 90)

        assertEquals("/api/v1/courses/c1/lessons/l001/position", seenPath)
        assertTrue(seenBody!!.contains("\"positionSeconds\":90"))
        require(result is ApiResult.Success)
        assertEquals(90, result.data.currentPositionSeconds)
        assertEquals("l001", result.data.currentLessonId)
    }
}
