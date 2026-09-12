package com.mentora.shared.data.repository.quiz

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.domain.model.QuizAnswer
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun OutgoingContent.readText(): String = when (this) {
    is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
    else -> error("Unsupported OutgoingContent for test body capture: $this")
}

private fun repositoryFor(engine: MockEngine): QuizRepository = QuizRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)))

class QuizRepositoryImplTest {

    // ---- getQuiz ----

    @Test
    fun `getQuiz hits the exact endpoint and maps a multi-question quiz with no isCorrect anywhere`() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Get, request.method)
            respond(
                content = """
                {"data":{"courseId":"c1","questions":[
                    {"questionId":"q2","prompt":"Second?","order":1,"options":[
                        {"optionId":"o2a","text":"A"},{"optionId":"o2b","text":"B"}]},
                    {"questionId":"q1","prompt":"First?","order":0,"options":[
                        {"optionId":"o1a","text":"A"},{"optionId":"o1b","text":"B"},{"optionId":"o1c","text":"C"}]}
                ]},"meta":{"requestId":"r1"}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getQuiz("c1")

        assertEquals("/api/v1/courses/c1/quiz", seenPath)
        require(result is ApiResult.Success)
        assertEquals(listOf("q1", "q2"), result.data.questions.map { it.questionId }, "questions must be order-sorted")
        assertEquals(3, result.data.questions.first { it.questionId == "q1" }.options.size)
        assertEquals(
            listOf("o1a", "o1b", "o1c"),
            result.data.questions.first { it.questionId == "q1" }.options.map { it.optionId },
        )
    }

    @Test
    fun `getQuiz surfaces a 404 QUIZ_NOT_FOUND as a typed distinguishable failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"QUIZ_NOT_FOUND","message":"The quiz was not found."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getQuiz("c1")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.QuizNotFound, result.code)
        assertEquals(404, result.httpStatus)
    }

    // ---- submitAttempt ----

    @Test
    fun `submitAttempt posts the exact endpoint with the answers body shape and maps the graded result`() = runTest {
        var seenPath: String? = null
        var seenBody: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Post, request.method)
            seenBody = request.body.readText()
            respond(
                content = """
                {"data":{"score":67,"passed":false,"breakdown":[
                    {"questionId":"q1","selectedOptionId":"o1b","correctOptionId":"o1b","isCorrect":true},
                    {"questionId":"q2","selectedOptionId":"o2a","correctOptionId":"o2b","isCorrect":false}
                ]},"meta":{"requestId":"r1"}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).submitAttempt(
            "c1",
            listOf(QuizAnswer("q1", "o1b"), QuizAnswer("q2", "o2a")),
        )

        assertEquals("/api/v1/courses/c1/quiz/attempts", seenPath)
        assertTrue(seenBody!!.contains(""""answers":[""") )
        assertTrue(seenBody!!.contains(""""questionId":"q1""""))
        assertTrue(seenBody!!.contains(""""selectedOptionId":"o1b""""))
        require(result is ApiResult.Success)
        assertEquals(67, result.data.score)
        assertEquals(false, result.data.passed)
        assertEquals(2, result.data.breakdown.size)
        assertEquals(true, result.data.breakdown.first { it.questionId == "q1" }.isCorrect)
    }

    // ---- getLatestAttempt ----

    @Test
    fun `getLatestAttempt hits the exact endpoint and maps an unanswered question with a null selectedOptionId`() = runTest {
        var seenPath: String? = null
        val engine = MockEngine { request: HttpRequestData ->
            seenPath = request.url.encodedPath
            assertEquals(HttpMethod.Get, request.method)
            respond(
                content = """
                {"data":{"score":50,"passed":false,"breakdown":[
                    {"questionId":"q1","selectedOptionId":"o1b","correctOptionId":"o1b","isCorrect":true},
                    {"questionId":"q2","selectedOptionId":null,"correctOptionId":"o2b","isCorrect":false}
                ]},"meta":{"requestId":"r1"}}
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getLatestAttempt("c1")

        assertEquals("/api/v1/courses/c1/quiz/attempts/latest", seenPath)
        require(result is ApiResult.Success)
        val unanswered = result.data.breakdown.first { it.questionId == "q2" }
        assertNull(unanswered.selectedOptionId)
        assertEquals(false, unanswered.isCorrect)
    }

    @Test
    fun `getLatestAttempt surfaces a 404 ATTEMPT_NOT_FOUND as a typed distinguishable failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"ATTEMPT_NOT_FOUND","message":"No quiz attempt was found."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }

        val result = repositoryFor(engine).getLatestAttempt("c1")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.AttemptNotFound, result.code)
        assertEquals(404, result.httpStatus)
    }
}
