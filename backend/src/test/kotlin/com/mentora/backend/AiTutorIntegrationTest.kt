package com.mentora.backend

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.mentora.backend.aitutor.provider.AiCompletionRequest
import com.mentora.backend.aitutor.provider.AiProvider
import com.mentora.backend.aitutor.provider.AiToken
import com.mentora.backend.aitutor.provider.AiUsage
import com.mentora.backend.aitutor.provider.StubAiProvider
import com.mentora.backend.aitutor.service.AiTutorService
import com.mentora.backend.common.ApiException
import com.mentora.backend.config.AppConfig
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bson.Document
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiTutorIntegrationTest {
    @BeforeEach fun resetDatabase() = dropDatabase()

    @Test
    fun `conversation is lazily created empty and remains unique`() = testApplication {
        application { module(config()) }
        val student = register("empty@example.com", "Empty Student").data().string("accessToken")

        val first = client.get(CONVERSATION) { bearerAuth(student) }.data()
        assertTrue(first.getValue("messages").jsonArray.isEmpty())
        val conversationId = first.string("conversationId")
        assertEquals(conversationId, client.get(CONVERSATION) { bearerAuth(student) }.data().string("conversationId"))
        assertEquals(24, conversationId.length)
    }

    @Test
    fun `messages stream and persist with and without valid lesson context`() = testApplication {
        application { module(config()) }
        val admin = provision("message-admin@example.com", "admin", "Admin")
        val instructor = provision("message-instructor@example.com", "instructor", "Instructor")
        val student = register("message-student@example.com", "Student").data().string("accessToken")
        val categoryId = postJson("/api/v1/categories", """{"name":"Tutor"}""", admin).data().string("id")
        val (courseId, lessonId) = course(categoryId, instructor)
        checkout(courseId, student)

        assertEquals(StubAiProvider.PLACEHOLDER, postMessage("""{"content":" Explain Kotlin "}""", student).bodyAsText())
        val contextual = postMessage(
            """{"content":"Explain this lesson","courseId":"$courseId","lessonContextId":"$lessonId"}""",
            student,
        )
        assertEquals(HttpStatusCode.OK, contextual.status)
        assertEquals(StubAiProvider.PLACEHOLDER, contextual.bodyAsText())

        val conversation = client.get(CONVERSATION) { bearerAuth(student) }.data()
        val messages = conversation.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(listOf("user", "assistant", "user", "assistant"), messages.map { it.string("role") })
        assertEquals("Explain Kotlin", messages[0].string("content"))
        assertEquals(StubAiProvider.PLACEHOLDER, messages[1].string("content"))
        assertEquals(lessonId, messages[2].string("lessonContextId"))
        assertEquals(lessonId, messages[3].string("lessonContextId"))
        messages.forEach { assertNotNull(it["createdAt"]) }
    }

    @Test
    fun `lesson context validates paired ids and enrollment before persistence`() = testApplication {
        application { module(config()) }
        val admin = provision("gate-admin@example.com", "admin", "Admin")
        val instructor = provision("gate-instructor@example.com", "instructor", "Instructor")
        val student = register("gate-student@example.com", "Student").data().string("accessToken")
        val categoryId = postJson("/api/v1/categories", """{"name":"Gate"}""", admin).data().string("id")
        val (courseId, lessonId) = course(categoryId, instructor)

        assertEquals(HttpStatusCode.BadRequest, postMessage(
            """{"content":"Question","lessonContextId":"$lessonId"}""", student,
        ).status)
        assertEquals(HttpStatusCode.BadRequest, postMessage(
            """{"content":"Question","courseId":"$courseId"}""", student,
        ).status)
        assertEquals(HttpStatusCode.Forbidden, postMessage(
            """{"content":"Question","courseId":"$courseId","lessonContextId":"$lessonId"}""", student,
        ).status)

        val conversation = client.get(CONVERSATION) { bearerAuth(student) }.data()
        assertTrue(conversation.getValue("messages").jsonArray.isEmpty())
    }

    @Test
    fun `oversized content and unauthorized roles are rejected from both routes`() = testApplication {
        application { module(config()) }
        val student = register("validation-student@example.com", "Student").data().string("accessToken")
        val instructor = provision("validation-instructor@example.com", "instructor", "Instructor")
        val admin = provision("validation-admin@example.com", "admin", "Admin")

        assertEquals(HttpStatusCode.BadRequest, postMessage("""{"content":"${"x".repeat(4001)}"}""", student).status)
        listOf(instructor, admin).forEach { token ->
            assertEquals(HttpStatusCode.Forbidden, client.get(CONVERSATION) { bearerAuth(token) }.status)
            assertEquals(HttpStatusCode.Forbidden, postMessage("""{"content":"Question"}""", token).status)
        }
        assertEquals(HttpStatusCode.Unauthorized, client.get(CONVERSATION).status)
        assertEquals(HttpStatusCode.Unauthorized, postMessage("""{"content":"Question"}""").status)
    }

    @Test
    fun `per minute message limit returns 429`() = testApplication {
        application { module(config(messagesPerMinute = 2)) }
        val student = register("limit-student@example.com", "Student").data().string("accessToken")

        repeat(2) {
            assertEquals(HttpStatusCode.OK, postMessage("""{"content":"Question $it"}""", student).status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, postMessage("""{"content":"One too many"}""", student).status)
    }

    @Test
    fun `daily message limit returns 429 independently`() = testApplication {
        application { module(config(messagesPerMinute = 100, messagesPerDay = 2)) }
        val student = register("daily-limit-student@example.com", "Student").data().string("accessToken")

        repeat(2) {
            assertEquals(HttpStatusCode.OK, postMessage("""{"content":"Daily question $it"}""", student).status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, postMessage("""{"content":"Daily excess"}""", student).status)
    }

    @Test
    fun `pre-stream provider failure returns 503 and persists no assistant message`() = testApplication {
        application { module(config(), FailBeforeStreamAiProvider()) }
        val student = register("prestream-student@example.com", "Student").data().string("accessToken")

        val logger = LoggerFactory.getLogger(AiTutorService::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        val response = try {
            postMessage("""{"content":"Will the tutor answer this?"}""", student)
        } finally {
            logger.detachAppender(appender)
        }

        assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
        assertEquals("AI_TUTOR_UNAVAILABLE", response.errorCode())

        val conversation = client.get(CONVERSATION) { bearerAuth(student) }.data()
        val messages = conversation.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(listOf("user"), messages.map { it.string("role") })

        // A10: the aiTutor.message log line fires exactly once, is categorized by the failure
        // bucket, and never carries the user's message content.
        val logLines = appender.list.map { it.formattedMessage }
        val logLine = logLines.singleOrNull { it.startsWith("aiTutor.message") }
        assertNotNull(logLine)
        assertTrue(logLine.contains("outcome=provider_unavailable"))
        assertFalse(logLine.contains("Will the tutor answer this?"))
    }

    @Test
    fun `mid-stream provider failure truncates the response and persists no assistant message`() = testApplication {
        application { module(config(), FailMidStreamAiProvider()) }
        val student = register("midstream-student@example.com", "Student").data().string("accessToken")

        // The flow emits two tokens, then throws, mid-write. Under `testApplication`'s in-process test
        // host, `respondTextWriter`'s body is buffered synchronously rather than streamed over a real
        // socket, so the exception is caught by StatusPages BEFORE anything reaches the test client,
        // and the client observes a clean 500 rather than a truncated 200 body (a test-host artifact —
        // a real Netty deployment would have already flushed the 200 + partial chunks by this point,
        // per Design § 1.4's "MID-STREAM failure" note). Either way, the response is NOT the clean
        // success it would be had the stream completed, and the invariant this test exists to prove —
        // no assistant message is ever persisted for an incomplete stream (A7) — is server-side and
        // unaffected by which HTTP status the client happens to observe.
        val response = postMessage("""{"content":"Tell me something long"}""", student)
        assertEquals(HttpStatusCode.InternalServerError, response.status)
        runCatching { response.bodyAsText() }

        val conversation = client.get(CONVERSATION) { bearerAuth(student) }.data()
        val messages = conversation.getValue("messages").jsonArray.map { it.jsonObject }
        assertEquals(listOf("user"), messages.map { it.string("role") })
    }

    @Test
    fun `retrying after a pre-stream failure never sends two adjacent same-role turns to the provider`() = testApplication {
        // F1: the first attempt persists a "user" turn but fails before any assistant reply is
        // persisted (Design § 1.3). The retry's request to the provider must never contain that
        // stale "user" turn directly adjacent to the new "user" message.
        val provider = FailOnceThenCaptureAiProvider()
        application { module(config(), provider) }
        val student = register("retry-student@example.com", "Student").data().string("accessToken")

        assertEquals(HttpStatusCode.ServiceUnavailable, postMessage("""{"content":"First attempt"}""", student).status)
        assertEquals(HttpStatusCode.OK, postMessage("""{"content":"Second attempt"}""", student).status)

        val turns = requireNotNull(provider.captured).history
        for (i in 1 until turns.size) {
            assertTrue(turns[i - 1].role != turns[i].role, "adjacent same-role turns: ${turns.map { it.role }}")
        }
        // The two turns must have been merged into one, not left as two adjacent "user" turns.
        assertEquals(listOf("user"), turns.map { it.role })
        assertTrue(turns.single().content.contains("First attempt"))
        assertTrue(turns.single().content.contains("Second attempt"))
    }

    @Test
    fun `enrolled course context is injected into the system prompt`() = testApplication {
        val capturing = CapturingAiProvider()
        application { module(config(), capturing) }
        val admin = provision("context-admin@example.com", "admin", "Admin")
        val instructor = provision("context-instructor@example.com", "instructor", "Instructor")
        val student = register("context-student@example.com", "Student").data().string("accessToken")
        val categoryId = postJson("/api/v1/categories", """{"name":"Context"}""", admin).data().string("id")

        val (enrolledCourseIdA, _) = course(categoryId, instructor, title = "Astonishing Astrophysics")
        val (enrolledCourseIdB, _) = course(categoryId, instructor, title = "Bewildering Botany")
        val (_, _) = course(categoryId, instructor, title = "Curious Chemistry")
        checkout(enrolledCourseIdA, student)
        checkout(enrolledCourseIdB, student)

        assertEquals(HttpStatusCode.OK, postMessage("""{"content":"What should I learn next?"}""", student).status)

        val systemPrompt = requireNotNull(capturing.captured).systemPrompt
        assertTrue(systemPrompt.contains("Astonishing Astrophysics"))
        assertTrue(systemPrompt.contains("Bewildering Botany"))
        assertFalse(systemPrompt.contains("Curious Chemistry"))
    }

    /** Throws before ever invoking [onStream] — Design § 1.3's pre-stream failure path. */
    private class FailBeforeStreamAiProvider : AiProvider {
        override suspend fun complete(request: AiCompletionRequest, onStream: suspend (Flow<AiToken>) -> Unit): AiUsage {
            throw ApiException.ServiceUnavailable()
        }
    }

    /** Emits two tokens, then throws out of the flow — Design § 11 row 12's mid-stream failure. */
    private class FailMidStreamAiProvider : AiProvider {
        override suspend fun complete(request: AiCompletionRequest, onStream: suspend (Flow<AiToken>) -> Unit): AiUsage {
            onStream(
                flow {
                    emit(AiToken("partial "))
                    emit(AiToken("text"))
                    throw RuntimeException("simulated mid-stream failure")
                },
            )
            return AiUsage(null, null)
        }
    }

    /** F1: throws [ApiException.ServiceUnavailable] on its first call (simulating a failed first
     * attempt), then captures the [AiCompletionRequest] of every subsequent call and completes
     * like a trivial stub — used to prove a retry never sends two adjacent same-role turns. */
    private class FailOnceThenCaptureAiProvider : AiProvider {
        private var callCount = 0
        var captured: AiCompletionRequest? = null
        override suspend fun complete(request: AiCompletionRequest, onStream: suspend (Flow<AiToken>) -> Unit): AiUsage {
            callCount++
            if (callCount == 1) throw ApiException.ServiceUnavailable()
            captured = request
            onStream(flow { emit(AiToken("ok")) })
            return AiUsage(1, 1)
        }
    }

    /** Captures the [AiCompletionRequest] it was given, then completes like a trivial stub. */
    private class CapturingAiProvider : AiProvider {
        var captured: AiCompletionRequest? = null
        override suspend fun complete(request: AiCompletionRequest, onStream: suspend (Flow<AiToken>) -> Unit): AiUsage {
            captured = request
            onStream(flow { emit(AiToken("ok")) })
            return AiUsage(1, 1)
        }
    }

    private suspend fun ApplicationTestBuilder.course(
        categoryId: String,
        instructor: String,
        title: String = "Tutor course",
    ): Pair<String, String> {
        val courseId = postJson(
            "/api/v1/courses",
            """{"title":"$title","description":"Details","categoryId":"$categoryId","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":100,"currency":"EGP"},"thumbnailMediaId":"000000000000000000000001"}""",
            instructor,
        ).data().string("id")
        val sectionId = postJson("/api/v1/courses/$courseId/sections", """{"title":"Section"}""", instructor)
            .data().getValue("sections").jsonArray.single().jsonObject.string("sectionId")
        val course = postJson(
            "/api/v1/courses/$courseId/sections/$sectionId/lessons",
            """{"title":"Lesson title","description":"Lesson description","videoMediaId":"000000000000000000000002"}""",
            instructor,
        ).data()
        val lessonId = course.getValue("sections").jsonArray.single().jsonObject
            .getValue("lessons").jsonArray.single().jsonObject.string("lessonId")
        postJson("/api/v1/courses/$courseId/publish", "{}", instructor)
        return courseId to lessonId
    }

    private suspend fun ApplicationTestBuilder.checkout(courseId: String, student: String) {
        assertEquals(HttpStatusCode.Created, postJson("/api/v1/courses/$courseId/checkout/complete", "{}", student).status)
    }

    private suspend fun ApplicationTestBuilder.provision(email: String, role: String, name: String): String {
        register(email, name)
        MongoClient.create(MONGO_URI).use {
            it.getDatabase(DATABASE).getCollection<Document>("users").updateOne(eq("email", email), set("role", role))
        }
        return postJson("/api/v1/auth/login", """{"email":"$email","password":"StrongPass1"}""")
            .data().string("accessToken")
    }

    private suspend fun ApplicationTestBuilder.register(email: String, name: String) =
        postJson("/api/v1/auth/register", """{"email":"$email","password":"StrongPass1","name":"$name"}""")
    private suspend fun ApplicationTestBuilder.postMessage(body: String, token: String? = null) =
        postJson("$CONVERSATION/messages", body, token)
    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, token: String? = null) =
        client.post(path) { json(body, token) }
    private fun io.ktor.client.request.HttpRequestBuilder.json(body: String, token: String?) {
        contentType(ContentType.Application.Json)
        header("X-Requested-With", "mentora-web")
        token?.let { bearerAuth(it) }
        setBody(body)
    }
    private suspend fun HttpResponse.data(): JsonObject =
        Json.parseToJsonElement(bodyAsText()).jsonObject.getValue("data").jsonObject
    private suspend fun HttpResponse.errorCode(): String =
        Json.parseToJsonElement(bodyAsText()).jsonObject.getValue("error").jsonObject.string("code")
    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content

    companion object {
        private const val CONVERSATION = "/api/v1/ai-tutor/conversation"
        private const val MONGO_URI = "mongodb://localhost:27017/?replicaSet=rs0"
        private const val DATABASE = "mentora_ai_tutor_test"
        @JvmStatic @AfterAll fun cleanUp() = dropDatabase()
        private fun dropDatabase() = runBlocking { MongoClient.create(MONGO_URI).use { it.getDatabase(DATABASE).drop() } }
        private fun config(messagesPerMinute: Int = 20, messagesPerDay: Int = 100) = AppConfig(
            mongoUri = MONGO_URI, mongoDatabaseName = DATABASE,
            jwtSigningSecret = "fixed-test-signing-secret-at-least-32-bytes", jwtIssuer = "mentora-backend-test",
            accessTokenTtlMinutes = 15, refreshTokenTtlDays = 30, mediaStorageRoot = "storage/media",
            corsAllowedOrigins = listOf("http://localhost:3000"), aiProviderApiKey = null,
            aiProviderModel = "test-model", logLevel = "DEBUG",
            aiTutorMessagesPerMinute = messagesPerMinute, aiTutorMessagesPerDay = messagesPerDay,
            aiProviderMaxResponseTokens = 1024, aiProviderTimeoutSeconds = 120,
        )
    }
}
