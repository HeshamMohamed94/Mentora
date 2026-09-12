package com.mentora.shared.data.repository.aitutor

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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
private fun textPlainHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())

private fun OutgoingContent.readText(): String = when (this) {
    is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
    else -> error("Unsupported OutgoingContent for test body capture: $this")
}

private fun repositoryFor(engine: MockEngine): AiTutorRepository {
    val client = HttpClientFactory.create(engine, testEnvironment)
    return AiTutorRepositoryImpl(ApiClient(client), client)
}

class AiTutorRepositoryImplTest {

    // ---- getConversation ----

    @Test
    fun `getConversation hits the exact endpoint and maps the non-standard envelope shape`() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Get, request.method)
            respond(
                content = """
                {"data":{"conversationId":"conv1","messages":[
                    {"id":"m1","role":"user","content":"Explain this lesson","createdAt":"2026-09-12T10:00:00Z"},
                    {"id":"m2","role":"assistant","content":"Sure — here is an explanation.","lessonContextId":"l1","createdAt":"2026-09-12T10:00:05Z"}
                ],"nextCursor":"cursor-2"},"meta":{"requestId":"r1"}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getConversation()

        assertEquals("/api/v1/ai-tutor/conversation", seenPath)
        require(result is ApiResult.Success)
        assertEquals("conv1", result.data.conversationId)
        assertEquals("cursor-2", result.data.nextCursor)
        assertEquals(2, result.data.messages.size)
        assertEquals("l1", result.data.messages[1].lessonContextId)
    }

    @Test
    fun `getConversation threads cursor and limit as query params when present`() = runTest {
        var seenQuery: Map<String, String>? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenQuery = request.url.parameters.names().associateWith { name -> request.url.parameters[name]!! }
            respond(
                content = """{"data":{"conversationId":"conv1","messages":[]},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        repositoryFor(engine).getConversation(cursor = "abc", limit = 20)

        assertEquals("abc", seenQuery?.get("cursor"))
        assertEquals("20", seenQuery?.get("limit"))
    }

    @Test
    fun `getConversation surfaces 429 as a typed RATE_LIMITED_AI_TUTOR failure with no envelope`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.TooManyRequests) }

        val result = repositoryFor(engine).getConversation()

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.RateLimitedAiTutor, result.code)
        assertEquals(429, result.httpStatus)
    }

    // ---- sendMessage ----

    @Test
    fun `sendMessage posts the exact endpoint with the request body shape and streams the full response as Chunks`() = runTest {
        var seenPath: String? = null
        var seenBody: String? = null
        var seenContentType: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Post, request.method)
            seenBody = request.body.readText()
            seenContentType = request.body.contentType?.toString()
            respond(
                content = "This is a placeholder AI Tutor response.",
                status = HttpStatusCode.OK,
                headers = textPlainHeaders(),
            )
        }

        val results = repositoryFor(engine).sendMessage("Explain this lesson", "c1", "l1").toList()

        assertEquals("/api/v1/ai-tutor/conversation/messages", seenPath)
        assertTrue(seenContentType!!.startsWith("application/json"))
        assertTrue(seenBody!!.contains(""""content":"Explain this lesson""""))
        assertTrue(seenBody!!.contains(""""courseId":"c1""""))
        assertTrue(seenBody!!.contains(""""lessonContextId":"l1""""))

        assertTrue(results.none { it is AiStreamResult.PreStreamFailure || it is AiStreamResult.StreamFailed })
        val accumulated = results.filterIsInstance<AiStreamResult.Chunk>().joinToString("") { it.text }
        assertEquals("This is a placeholder AI Tutor response.", accumulated)
    }

    @Test
    fun `sendMessage surfaces a pre-stream 403 FORBIDDEN_NOT_ENROLLED as PreStreamFailure with zero Chunks`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"FORBIDDEN_NOT_ENROLLED","message":"You are not enrolled in this course."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.Forbidden,
                headers = jsonHeaders(),
            )
        }

        val results = repositoryFor(engine).sendMessage("Explain this lesson", "c1", "l1").toList()

        assertEquals(emptyList(), results.filterIsInstance<AiStreamResult.Chunk>())
        val failure = results.filterIsInstance<AiStreamResult.PreStreamFailure>().single()
        assertEquals(ApiErrorCode.ForbiddenNotEnrolled, failure.failure.code)
        assertEquals(403, failure.failure.httpStatus)
    }

    @Test
    fun `sendMessage surfaces a pre-stream 429 as a typed RATE_LIMITED_AI_TUTOR PreStreamFailure`() = runTest {
        val engine = MockEngine { respond(content = "", status = HttpStatusCode.TooManyRequests) }

        val results = repositoryFor(engine).sendMessage("Explain this lesson", null, null).toList()

        val failure = results.filterIsInstance<AiStreamResult.PreStreamFailure>().single()
        assertEquals(ApiErrorCode.RateLimitedAiTutor, failure.failure.code)
        assertEquals(429, failure.failure.httpStatus)
    }
}
