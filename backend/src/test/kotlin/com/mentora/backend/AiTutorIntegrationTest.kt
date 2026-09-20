package com.mentora.backend

import com.mentora.backend.aitutor.provider.StubAiProvider
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
import kotlin.test.assertEquals
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

    private suspend fun ApplicationTestBuilder.course(categoryId: String, instructor: String): Pair<String, String> {
        val courseId = postJson(
            "/api/v1/courses",
            """{"title":"Tutor course","description":"Details","categoryId":"$categoryId","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":100,"currency":"EGP"},"thumbnailMediaId":"000000000000000000000001"}""",
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
