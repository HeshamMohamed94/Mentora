package com.mentora.backend

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
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

class ProgressIntegrationTest {
    @BeforeEach fun resetDatabase() = dropDatabase()

    @Test
    fun `enrollment gate rejects every progress route`() = testApplication {
        application { module(config()) }
        val fixture = courseFixture("gate")
        val lessonId = fixture.lessonIds.first()
        listOf(
            client.get("/api/v1/courses/${fixture.courseId}/progress") { bearerAuth(fixture.student) },
            postJson("/api/v1/courses/${fixture.courseId}/lessons/$lessonId/complete", "{}", fixture.student),
            postJson("/api/v1/courses/${fixture.courseId}/lessons/$lessonId/position", """{"positionSeconds":1}""", fixture.student),
        ).forEach {
            assertEquals(HttpStatusCode.Forbidden, it.status)
            assertEquals("FORBIDDEN_NOT_ENROLLED", it.errorCode())
        }
    }

    @Test
    fun `lazy progress completion percentages idempotency and scope fields`() = testApplication {
        application { module(config()) }
        val fixture = courseFixture("completion")
        checkout(fixture.courseId, fixture.student)

        val initial = client.get("/api/v1/courses/${fixture.courseId}/progress") { bearerAuth(fixture.student) }
        assertEquals(HttpStatusCode.OK, initial.status)
        assertEquals(0, initial.data().int("completionPercent"))
        assertEquals(0, initial.data().getValue("completedLessonIds").jsonArray.size)
        assertScopeNulls(initial)
        MongoClient.create(MONGO_URI).use {
            assertEquals(1L, it.getDatabase(DATABASE).getCollection<Document>("progress").countDocuments())
        }

        val first = completeLesson(fixture, fixture.lessonIds[0])
        assertEquals(25, first.data().int("completionPercent"))
        assertEquals(listOf(fixture.lessonIds[0]), first.completedIds())
        assertScopeNulls(first)

        val repeated = completeLesson(fixture, fixture.lessonIds[0])
        assertEquals(25, repeated.data().int("completionPercent"))
        assertEquals(1, repeated.completedIds().size)
        assertScopeNulls(repeated)

        val second = completeLesson(fixture, fixture.lessonIds[1])
        assertEquals(50, second.data().int("completionPercent"))
        assertEquals(setOf(fixture.lessonIds[0], fixture.lessonIds[1]), second.completedIds().toSet())
        assertScopeNulls(second)

        val missing = completeLesson(fixture, "not-a-course-lesson")
        assertEquals(HttpStatusCode.NotFound, missing.status)
        assertEquals("LESSON_NOT_FOUND", missing.errorCode())
    }

    @Test
    fun `position heartbeat updates resume state and rejects negatives`() = testApplication {
        application { module(config()) }
        val fixture = courseFixture("position")
        checkout(fixture.courseId, fixture.student)
        val lessonId = fixture.lessonIds[2]

        val updated = postJson(
            "/api/v1/courses/${fixture.courseId}/lessons/$lessonId/position",
            """{"positionSeconds":87}""", fixture.student,
        )
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals(lessonId, updated.data().string("currentLessonId"))
        assertEquals(87, updated.data().int("currentPositionSeconds"))
        assertScopeNulls(updated)

        val invalid = postJson(
            "/api/v1/courses/${fixture.courseId}/lessons/$lessonId/position",
            """{"positionSeconds":-1}""", fixture.student,
        )
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals("VALIDATION_ERROR", invalid.errorCode())
    }

    private data class Fixture(val courseId: String, val lessonIds: List<String>, val student: String)

    private suspend fun ApplicationTestBuilder.courseFixture(suffix: String): Fixture {
        val admin = provision("$suffix-admin@example.com", "admin", "Admin")
        val instructor = provision("$suffix-instructor@example.com", "instructor", "Instructor")
        val student = register("$suffix-student@example.com", "Student").data().string("accessToken")
        val categoryId = postJson("/api/v1/categories", """{"name":"Category $suffix"}""", admin).data().string("id")
        val course = postJson(
            "/api/v1/courses",
            """{"title":"Course $suffix","description":"Details","categoryId":"$categoryId","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":100,"currency":"EGP"},"thumbnailMediaId":"000000000000000000000001"}""",
            instructor,
        )
        val courseId = course.data().string("id")
        val section = postJson("/api/v1/courses/$courseId/sections", """{"title":"Section"}""", instructor)
        val sectionId = section.data().getValue("sections").jsonArray.first().jsonObject.string("sectionId")
        val lessonIds = (1..4).map { number ->
            postJson(
                "/api/v1/courses/$courseId/sections/$sectionId/lessons",
                """{"title":"Lesson $number","description":"Details","videoMediaId":"${number.toString().padStart(24, '0')}"}""",
                instructor,
            ).data().getValue("sections").jsonArray.first().jsonObject.getValue("lessons").jsonArray.last()
                .jsonObject.string("lessonId")
        }
        assertEquals(HttpStatusCode.OK, postJson("/api/v1/courses/$courseId/publish", "{}", instructor).status)
        return Fixture(courseId, lessonIds, student)
    }

    private suspend fun ApplicationTestBuilder.completeLesson(fixture: Fixture, lessonId: String) =
        postJson("/api/v1/courses/${fixture.courseId}/lessons/$lessonId/complete", "{}", fixture.student)

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
        client.post(path) {
            contentType(ContentType.Application.Json)
            header("X-Requested-With", "mentora-web")
            token?.let { bearerAuth(it) }
            setBody(body)
        }

    private suspend fun HttpResponse.root() = Json.parseToJsonElement(bodyAsText()).jsonObject
    private suspend fun HttpResponse.data(): JsonObject = root().getValue("data").jsonObject
    private suspend fun HttpResponse.errorCode() = root().getValue("error").jsonObject.string("code")
    private suspend fun HttpResponse.completedIds() = data().getValue("completedLessonIds").jsonArray.map { it.jsonPrimitive.content }
    private suspend fun assertScopeNulls(response: HttpResponse) {
        assertFalse(response.data().containsKey("quizPassed"))
        assertFalse(response.data().containsKey("courseCompletedAt"))
    }
    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.int

    companion object {
        private const val MONGO_URI = "mongodb://localhost:27017"
        private const val DATABASE = "mentora_progress_test"
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
