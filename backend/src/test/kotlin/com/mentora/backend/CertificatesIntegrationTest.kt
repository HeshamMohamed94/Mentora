package com.mentora.backend

import com.mentora.backend.config.AppConfig
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bson.Document
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CertificatesIntegrationTest {
    @BeforeEach fun resetDatabase() = dropDatabase()

    @Test
    fun `no quiz completion issues immutable certificate with public id and private detail`() = testApplication {
        application { module(config()) }
        val fixture = fixture("no-quiz", withQuiz = false)
        checkout(fixture.courseId, fixture.student)

        complete(fixture, fixture.lessonIds.first())
        assertTrue(certificates(fixture.student).isEmpty())
        complete(fixture, fixture.lessonIds.last())

        val listed = certificates(fixture.student)
        assertEquals(1, listed.size)
        val publicId = listed.single().jsonObject.string("id")
        assertTrue(PUBLIC_ID.matches(publicId))
        assertFalse(RAW_OBJECT_ID.matches(publicId))
        assertNotNull(progress(fixture).data().getValue("courseCompletedAt"))

        val detail = client.get("/api/v1/certificates/$publicId") { bearerAuth(fixture.student) }
        assertEquals(HttpStatusCode.OK, detail.status)
        assertEquals("Course no-quiz", detail.data().string("courseTitleSnapshot"))

        val otherStudent = register("no-quiz-other@example.com", "Other Student").data().string("accessToken")
        val hidden = client.get("/api/v1/certificates/$publicId") { bearerAuth(otherStudent) }
        assertEquals(HttpStatusCode.NotFound, hidden.status)
        assertEquals("CERTIFICATE_NOT_FOUND", hidden.errorCode())

        patchJson("/api/v1/courses/${fixture.courseId}", """{"title":"Renamed Course"}""", fixture.owner)
        val afterRename = client.get("/api/v1/certificates/$publicId") { bearerAuth(fixture.student) }
        assertEquals("Course no-quiz", afterRename.data().string("courseTitleSnapshot"))
    }

    @Test
    fun `quiz gates completion until a passing attempt and never double issues`() = testApplication {
        application { module(config()) }
        val fixture = fixture("quiz-last", withQuiz = true)
        checkout(fixture.courseId, fixture.student)
        fixture.lessonIds.forEach { complete(fixture, it) }
        assertTrue(certificates(fixture.student).isEmpty())
        assertFalse(progress(fixture).data().containsKey("courseCompletedAt"))

        submitPassing(fixture)
        assertEquals(1, certificates(fixture.student).size)
        assertNotNull(progress(fixture).data().getValue("courseCompletedAt"))

        complete(fixture, fixture.lessonIds.last())
        submitPassing(fixture)
        assertEquals(1L, certificateCount())
    }

    @Test
    fun `passing quiz first issues certificate when last lesson completes`() = testApplication {
        application { module(config()) }
        val fixture = fixture("quiz-first", withQuiz = true)
        checkout(fixture.courseId, fixture.student)
        submitPassing(fixture)
        assertTrue(certificates(fixture.student).isEmpty())

        complete(fixture, fixture.lessonIds.first())
        assertTrue(certificates(fixture.student).isEmpty())
        complete(fixture, fixture.lessonIds.last())
        assertEquals(1, certificates(fixture.student).size)
        assertEquals(1L, certificateCount())
    }

    private data class Fixture(
        val courseId: String,
        val lessonIds: List<String>,
        val owner: String,
        val student: String,
    )

    private suspend fun ApplicationTestBuilder.fixture(suffix: String, withQuiz: Boolean): Fixture {
        val admin = provision("$suffix-admin@example.com", "admin", "Admin")
        val owner = provision("$suffix-owner@example.com", "instructor", "Instructor $suffix")
        val student = register("$suffix-student@example.com", "Student $suffix").data().string("accessToken")
        val categoryId = postJson("/api/v1/categories", """{"name":"Category $suffix"}""", admin)
            .data().string("id")
        val courseId = postJson(
            "/api/v1/courses",
            """{"title":"Course $suffix","description":"Details","categoryId":"$categoryId","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":100,"currency":"EGP"},"thumbnailMediaId":"000000000000000000000001"}""",
            owner,
        ).data().string("id")
        val sectionId = postJson("/api/v1/courses/$courseId/sections", """{"title":"Section"}""", owner)
            .data().getValue("sections").jsonArray.first().jsonObject.string("sectionId")
        val lessonIds = (1..2).map { number ->
            postJson(
                "/api/v1/courses/$courseId/sections/$sectionId/lessons",
                """{"title":"Lesson $number","description":"Details","videoMediaId":"${number.toString().padStart(24, '0')}"}""",
                owner,
            ).data().getValue("sections").jsonArray.first().jsonObject.getValue("lessons").jsonArray.last()
                .jsonObject.string("lessonId")
        }
        if (withQuiz) putJson("/api/v1/courses/$courseId/quiz/editor", quizBody(), owner)
        assertEquals(HttpStatusCode.OK, postJson("/api/v1/courses/$courseId/publish", "{}", owner).status)
        return Fixture(courseId, lessonIds, owner, student)
    }

    private fun quizBody() = """{"questions":[{"questionId":"q1","prompt":"Question","order":0,"options":[{"optionId":"correct","text":"Correct","isCorrect":true},{"optionId":"wrong","text":"Wrong","isCorrect":false}]}]}"""
    private suspend fun ApplicationTestBuilder.submitPassing(fixture: Fixture) = postJson(
        "/api/v1/courses/${fixture.courseId}/quiz/attempts",
        """{"answers":[{"questionId":"q1","selectedOptionId":"correct"}]}""",
        fixture.student,
    )
    private suspend fun ApplicationTestBuilder.complete(fixture: Fixture, lessonId: String) =
        postJson("/api/v1/courses/${fixture.courseId}/lessons/$lessonId/complete", "{}", fixture.student)
    private suspend fun ApplicationTestBuilder.checkout(courseId: String, token: String) =
        postJson("/api/v1/courses/$courseId/checkout/complete", "{}", token)
    private suspend fun ApplicationTestBuilder.progress(fixture: Fixture) =
        client.get("/api/v1/courses/${fixture.courseId}/progress") { bearerAuth(fixture.student) }
    private suspend fun ApplicationTestBuilder.certificates(token: String): JsonArray =
        client.get("/api/v1/certificates") { bearerAuth(token) }.root().getValue("data").jsonArray
    private suspend fun certificateCount(): Long = MongoClient.create(MONGO_URI).use {
        it.getDatabase(DATABASE).getCollection<Document>("certificates").countDocuments()
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
    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, token: String? = null) =
        client.post(path) { json(body, token) }
    private suspend fun ApplicationTestBuilder.putJson(path: String, body: String, token: String) =
        client.put(path) { json(body, token) }
    private suspend fun ApplicationTestBuilder.patchJson(path: String, body: String, token: String) =
        client.patch(path) { json(body, token) }
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

    companion object {
        private const val MONGO_URI = "mongodb://localhost:27017/?replicaSet=rs0"
        private const val DATABASE = "mentora_certificates_test"
        private val RAW_OBJECT_ID = Regex("^[0-9a-fA-F]{24}$")
        private val PUBLIC_ID = Regex("^MTR-(?:[0-9A-F]{4}-){5}[0-9A-F]{4}$")
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
