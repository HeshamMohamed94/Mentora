package com.mentora.backend

import com.mentora.backend.config.AppConfig
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
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
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bson.Document
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QuizIntegrationTest {
    @BeforeEach fun resetDatabase() = dropDatabase()

    @Test
    fun `student wire response strips correctness while editor includes it`() = testApplication {
        application { module(config()) }
        val fixture = fixture("projection")
        checkout(fixture.courseId, fixture.student)
        putQuiz(fixture.courseId, fixture.owner)

        val studentResponse = client.get("/api/v1/courses/${fixture.courseId}/quiz") {
            bearerAuth(fixture.student)
        }
        assertEquals(HttpStatusCode.OK, studentResponse.status)
        assertFalse(studentResponse.bodyAsText().contains("\"isCorrect\""))

        val editorResponse = client.get("/api/v1/courses/${fixture.courseId}/quiz/editor") {
            bearerAuth(fixture.owner)
        }
        assertEquals(HttpStatusCode.OK, editorResponse.status)
        assertTrue(editorResponse.bodyAsText().contains("\"isCorrect\""))
    }

    @Test
    fun `editor enforces ownership and exactly one correct option`() = testApplication {
        application { module(config()) }
        val fixture = fixture("editor")
        val nonOwner = provision("editor-other@example.com", "instructor", "Other")
        val forbidden = putJson("/api/v1/courses/${fixture.courseId}/quiz/editor", quizBody(), nonOwner)
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals("FORBIDDEN_NOT_OWNER", forbidden.errorCode())

        val invalid = putJson(
            "/api/v1/courses/${fixture.courseId}/quiz/editor",
            """{"questions":[{"prompt":"Invalid","order":0,"options":[{"text":"A","isCorrect":false},{"text":"B","isCorrect":false}]}]}""",
            fixture.owner,
        )
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals("VALIDATION_ERROR", invalid.errorCode())
    }

    @Test
    fun `attempt grading latest lookup and latest result overwrite progress`() = testApplication {
        application { module(config()) }
        val fixture = fixture("attempt")
        checkout(fixture.courseId, fixture.student)
        putQuiz(fixture.courseId, fixture.owner)

        val missing = client.get("/api/v1/courses/${fixture.courseId}/quiz/attempts/latest") {
            bearerAuth(fixture.student)
        }
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("ATTEMPT_NOT_FOUND", missing.errorCode())

        val passed = submit(fixture, """{"answers":[{"questionId":"q1","selectedOptionId":"q1-c"},{"questionId":"q2","selectedOptionId":"q2-c"},{"questionId":"q3","selectedOptionId":"q3-c"},{"questionId":"q4","selectedOptionId":"q4-w"}]}""")
        assertEquals(75, passed.data().int("score"))
        assertTrue(passed.data().boolean("passed"))
        assertEquals(3, passed.data().getValue("breakdown").jsonArray.count { it.jsonObject.boolean("isCorrect") })
        assertTrue(progress(fixture).data().boolean("quizPassed"))

        val failed = submit(fixture, """{"answers":[{"questionId":"q1","selectedOptionId":"q1-c"},{"questionId":"q2","selectedOptionId":"q2-w"},{"questionId":"q3","selectedOptionId":"q3-w"},{"questionId":"q4","selectedOptionId":"q4-w"}]}""")
        assertEquals(25, failed.data().int("score"))
        assertFalse(failed.data().boolean("passed"))
        assertFalse(progress(fixture).data().boolean("quizPassed"))

        val latest = client.get("/api/v1/courses/${fixture.courseId}/quiz/attempts/latest") {
            bearerAuth(fixture.student)
        }
        assertEquals(25, latest.data().int("score"))
        assertFalse(latest.data().boolean("passed"))
    }

    @Test
    fun `unenrolled student cannot fetch or submit quiz`() = testApplication {
        application { module(config()) }
        val fixture = fixture("gate")
        putQuiz(fixture.courseId, fixture.owner)
        val responses = listOf(
            client.get("/api/v1/courses/${fixture.courseId}/quiz") { bearerAuth(fixture.student) },
            submit(fixture, """{"answers":[]}"""),
        )
        responses.forEach {
            assertEquals(HttpStatusCode.Forbidden, it.status)
            assertEquals("FORBIDDEN_NOT_ENROLLED", it.errorCode())
        }
    }

    private data class Fixture(val courseId: String, val owner: String, val student: String)

    private suspend fun ApplicationTestBuilder.fixture(suffix: String): Fixture {
        val admin = provision("$suffix-admin@example.com", "admin", "Admin")
        val owner = provision("$suffix-owner@example.com", "instructor", "Owner")
        val student = register("$suffix-student@example.com", "Student").data().string("accessToken")
        val categoryId = postJson("/api/v1/categories", """{"name":"Category $suffix"}""", admin).data().string("id")
        val courseId = postJson(
            "/api/v1/courses",
            """{"title":"Course $suffix","description":"Details","categoryId":"$categoryId","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":100,"currency":"EGP"},"thumbnailMediaId":"000000000000000000000001"}""",
            owner,
        ).data().string("id")
        val sectionId = postJson("/api/v1/courses/$courseId/sections", """{"title":"Section"}""", owner)
            .data().getValue("sections").jsonArray.first().jsonObject.string("sectionId")
        postJson(
            "/api/v1/courses/$courseId/sections/$sectionId/lessons",
            """{"title":"Lesson","description":"Details","videoMediaId":"000000000000000000000002"}""",
            owner,
        )
        assertEquals(HttpStatusCode.OK, postJson("/api/v1/courses/$courseId/publish", "{}", owner).status)
        return Fixture(courseId, owner, student)
    }

    private fun quizBody() = """{"questions":[${(1..4).joinToString(",") { number ->
        """{"questionId":"q$number","prompt":"Question $number","order":${number - 1},"options":[{"optionId":"q$number-c","text":"Correct","isCorrect":true},{"optionId":"q$number-w","text":"Wrong","isCorrect":false}]}"""
    }}]}"""

    private suspend fun ApplicationTestBuilder.putQuiz(courseId: String, token: String) =
        putJson("/api/v1/courses/$courseId/quiz/editor", quizBody(), token)

    private suspend fun ApplicationTestBuilder.submit(fixture: Fixture, body: String) =
        postJson("/api/v1/courses/${fixture.courseId}/quiz/attempts", body, fixture.student)

    private suspend fun ApplicationTestBuilder.progress(fixture: Fixture) =
        client.get("/api/v1/courses/${fixture.courseId}/progress") { bearerAuth(fixture.student) }

    private suspend fun ApplicationTestBuilder.checkout(courseId: String, token: String) =
        postJson("/api/v1/courses/$courseId/checkout/complete", "{}", token)

    private suspend fun ApplicationTestBuilder.provision(email: String, role: String, name: String): String {
        register(email, name)
        MongoClient.create(MONGO_URI).use {
            it.getDatabase(DATABASE).getCollection<Document>("users").updateOne(eq("email", email), set("role", role))
        }
        return postJson("/api/v1/auth/login", """{"email":"$email","password":"StrongPass1"}""").data().string("accessToken")
    }

    private suspend fun ApplicationTestBuilder.register(email: String, name: String) =
        postJson("/api/v1/auth/register", """{"email":"$email","password":"StrongPass1","name":"$name"}""")

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, token: String? = null) =
        client.post(path) { json(body, token) }

    private suspend fun ApplicationTestBuilder.putJson(path: String, body: String, token: String) =
        client.put(path) { json(body, token) }

    private fun io.ktor.client.request.HttpRequestBuilder.json(body: String, token: String?) {
        contentType(ContentType.Application.Json)
        header("X-Requested-With", "mentora-web")
        token?.let { bearerAuth(it) }
        setBody(body)
    }

    private suspend fun HttpResponse.root() = Json.parseToJsonElement(bodyAsText()).jsonObject
    private suspend fun HttpResponse.data(): JsonObject = root().getValue("data").jsonObject
    private suspend fun HttpResponse.errorCode() = root().getValue("error").jsonObject.string("code")
    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.int
    private fun JsonObject.boolean(name: String) = getValue(name).jsonPrimitive.boolean

    companion object {
        private const val MONGO_URI = "mongodb://localhost:27017/?replicaSet=rs0"
        private const val DATABASE = "mentora_quiz_test"
        @JvmStatic @AfterAll fun cleanUp() = dropDatabase()
        private fun dropDatabase() = runBlocking { MongoClient.create(MONGO_URI).use { it.getDatabase(DATABASE).drop() } }
        private fun config() = AppConfig(
            mongoUri = MONGO_URI, mongoDatabaseName = DATABASE,
            jwtSigningSecret = "fixed-test-signing-secret-at-least-32-bytes", jwtIssuer = "mentora-backend-test",
            accessTokenTtlMinutes = 15, refreshTokenTtlDays = 30, mediaStorageRoot = "storage/media",
            corsAllowedOrigins = listOf("http://localhost:3000"), aiProviderApiKey = null,
            aiProviderModel = "test-model", logLevel = "DEBUG",
        )
    }
}
