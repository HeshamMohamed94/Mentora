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
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.bson.Document
import org.bson.types.ObjectId
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class InstructorIntegrationTest {
    @BeforeEach fun resetDatabase() = dropDatabase()

    @Test
    fun `dashboard includes only owned courses with enrollment and average progress stats`() = testApplication {
        application { module(config()) }
        val admin = provision("dashboard-admin@example.com", "admin", "Admin")
        val owner = provision("dashboard-owner@example.com", "instructor", "Owner")
        val otherOwner = provision("dashboard-other@example.com", "instructor", "Other Owner")
        val firstStudent = register("dashboard-first@example.com", "First Student").data().string("accessToken")
        val secondStudent = register("dashboard-second@example.com", "Second Student").data().string("accessToken")
        val categoryId = postJson("/api/v1/categories", """{"name":"Dashboard"}""", admin).data().string("id")
        val draftId = course("Draft course", categoryId, owner, published = false)
        val publishedId = course("Published course", categoryId, owner, published = true)
        course("Other course", categoryId, otherOwner, published = true)

        checkout(publishedId, firstStudent)
        checkout(publishedId, secondStudent)
        progress(publishedId, firstStudent)
        progress(publishedId, secondStudent)
        setProgress(publishedId, firstStudent, 20)
        setProgress(publishedId, secondStudent, 81)

        val response = client.get("/api/v1/instructor/dashboard") { bearerAuth(owner) }
        assertEquals(HttpStatusCode.OK, response.status)
        val dashboard = response.data()
        val stats = dashboard.getValue("stats").jsonObject
        assertEquals(2, stats.int("totalCourses"))
        assertEquals(1, stats.int("publishedCount"))
        assertEquals(2, stats.int("totalEnrollments"))

        val courses = dashboard.getValue("courses").jsonArray.map { it.jsonObject }
        assertEquals(listOf(draftId, publishedId), courses.map { it.string("id") })
        assertEquals(listOf("Draft course", "Published course"), courses.map { it.string("title") })
        assertEquals(listOf("draft", "published"), courses.map { it.string("status") })
        assertEquals(listOf(0, 2), courses.map { it.int("enrollmentCount") })
        assertEquals(listOf(0, 51), courses.map { it.int("completionRate") })
    }

    @Test
    fun `dashboard rejects students and unauthenticated callers`() = testApplication {
        application { module(config()) }
        val student = register("dashboard-student@example.com", "Student").data().string("accessToken")

        assertEquals(HttpStatusCode.Forbidden, client.get("/api/v1/instructor/dashboard") {
            bearerAuth(student)
        }.status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/instructor/dashboard").status)
    }

    private suspend fun ApplicationTestBuilder.course(
        title: String, categoryId: String, owner: String, published: Boolean,
    ): String {
        val courseId = postJson(
            "/api/v1/courses",
            """{"title":"$title","description":"Details","categoryId":"$categoryId","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":100,"currency":"EGP"},"thumbnailMediaId":"000000000000000000000001"}""",
            owner,
        ).data().string("id")
        val sectionId = postJson("/api/v1/courses/$courseId/sections", """{"title":"Section"}""", owner)
            .data().getValue("sections").jsonArray.single().jsonObject.string("sectionId")
        postJson(
            "/api/v1/courses/$courseId/sections/$sectionId/lessons",
            """{"title":"Lesson","description":"Details","videoMediaId":"000000000000000000000002"}""",
            owner,
        )
        if (published) postJson("/api/v1/courses/$courseId/publish", "{}", owner)
        return courseId
    }

    private suspend fun ApplicationTestBuilder.checkout(courseId: String, student: String) {
        assertEquals(HttpStatusCode.Created, postJson(
            "/api/v1/courses/$courseId/checkout/complete", "{}", student,
        ).status)
    }

    private suspend fun ApplicationTestBuilder.progress(courseId: String, student: String) {
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/courses/$courseId/progress") {
            bearerAuth(student)
        }.status)
    }

    private suspend fun setProgress(courseId: String, token: String, completionPercent: Int) {
        val userId = tokenUserId(token)
        MongoClient.create(MONGO_URI).use {
            it.getDatabase(DATABASE).getCollection<Document>("progress").updateOne(
                org.bson.Document("userId", userId).append("courseId", ObjectId(courseId)),
                set("completionPercent", completionPercent),
            )
        }
    }

    private fun tokenUserId(token: String) = ObjectId(
        String(java.util.Base64.getUrlDecoder().decode(token.split('.')[1])).let {
            Json.parseToJsonElement(it).jsonObject.getValue("userId").jsonPrimitive.content
        },
    )

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
    private fun io.ktor.client.request.HttpRequestBuilder.json(body: String, token: String?) {
        contentType(ContentType.Application.Json)
        header("X-Requested-With", "mentora-web")
        token?.let { bearerAuth(it) }
        setBody(body)
    }
    private suspend fun HttpResponse.root() = Json.parseToJsonElement(bodyAsText()).jsonObject
    private suspend fun HttpResponse.data(): JsonObject = root().getValue("data").jsonObject
    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.content.toInt()

    companion object {
        private const val MONGO_URI = "mongodb://localhost:27017/?replicaSet=rs0"
        private const val DATABASE = "mentora_instructor_test"
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
