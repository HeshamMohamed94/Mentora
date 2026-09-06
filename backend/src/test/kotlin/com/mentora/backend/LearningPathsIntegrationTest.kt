package com.mentora.backend

import com.mentora.backend.config.AppConfig
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
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
import kotlinx.serialization.json.JsonArray
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LearningPathsIntegrationTest {
    @BeforeEach fun resetDatabase() = dropDatabase()

    @Test
    fun `catalog and guest detail expose seeded path with ordered courses`() = testApplication {
        application { module(config()) }
        val courses = publishedCourses("guest")
        val pathId = seedPath(courses.map { ObjectId(it.courseId) })

        val catalog = client.get("/api/v1/learning-paths")
        assertEquals(HttpStatusCode.OK, catalog.status)
        val summary = catalog.dataArray().single().jsonObject
        assertEquals(pathId.toHexString(), summary.string("id"))
        assertEquals(2, summary.int("courseCount"))

        val detail = client.get("/api/v1/learning-paths/$pathId")
        assertEquals(HttpStatusCode.OK, detail.status)
        val body = detail.data()
        assertEquals(courses.map { it.courseId }, body.courses().map { it.string("id") })
        assertFalse(body.containsKey("progressPercent"))
        assertFalse(body.boolean("isFollowing"))
    }

    @Test
    fun `student completion yields fifty percent path progress`() = testApplication {
        application { module(config()) }
        val courses = publishedCourses("progress")
        val pathId = seedPath(courses.map { ObjectId(it.courseId) })
        val student = register("progress-student@example.com", "Progress Student").data().string("accessToken")
        checkoutAndComplete(courses.first(), student)

        val detail = client.get("/api/v1/learning-paths/$pathId") { bearerAuth(student) }
        assertEquals(HttpStatusCode.OK, detail.status)
        assertEquals(50, detail.data().int("progressPercent"))
    }

    @Test
    fun `follow and unfollow are idempotent without duplicate documents`() = testApplication {
        application { module(config()) }
        val pathId = seedPath(emptyList())
        val student = register("follow-student@example.com", "Follow Student").data().string("accessToken")

        repeat(2) {
            assertEquals(HttpStatusCode.OK, postJson("/api/v1/learning-paths/$pathId/follow", "{}", student).status)
        }
        assertEquals(1L, followCount(pathId))
        assertTrue(client.get("/api/v1/learning-paths/$pathId") { bearerAuth(student) }.data().boolean("isFollowing"))

        repeat(2) {
            assertEquals(HttpStatusCode.OK, deleteJson("/api/v1/learning-paths/$pathId/follow", student).status)
        }
        assertEquals(0L, followCount(pathId))
        assertFalse(client.get("/api/v1/learning-paths/$pathId") { bearerAuth(student) }.data().boolean("isFollowing"))
    }

    @Test
    fun `dangling course is omitted from detail and progress denominator`() = testApplication {
        application { module(config()) }
        val course = publishedCourses("dangling", count = 1).single()
        val pathId = seedPath(listOf(ObjectId(course.courseId), ObjectId()))
        val student = register("dangling-student@example.com", "Dangling Student").data().string("accessToken")
        checkoutAndComplete(course, student)

        val detail = client.get("/api/v1/learning-paths/$pathId") { bearerAuth(student) }
        assertEquals(HttpStatusCode.OK, detail.status)
        assertEquals(listOf(course.courseId), detail.data().courses().map { it.string("id") })
        assertEquals(100, detail.data().int("progressPercent"))
    }

    private data class CourseFixture(val courseId: String, val lessonId: String)

    private suspend fun ApplicationTestBuilder.publishedCourses(suffix: String, count: Int = 2): List<CourseFixture> {
        val admin = provision("$suffix-admin@example.com", "admin", "Admin")
        val owner = provision("$suffix-owner@example.com", "instructor", "Instructor")
        val categoryId = postJson("/api/v1/categories", """{"name":"Category $suffix"}""", admin)
            .data().string("id")
        return (1..count).map { number -> publishedCourse("$suffix-$number", categoryId, owner) }
    }

    private suspend fun ApplicationTestBuilder.publishedCourse(
        suffix: String, categoryId: String, owner: String,
    ): CourseFixture {
        val courseId = postJson(
            "/api/v1/courses",
            """{"title":"Course $suffix","description":"Details","categoryId":"$categoryId","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":100,"currency":"EGP"},"thumbnailMediaId":"000000000000000000000001"}""",
            owner,
        ).data().string("id")
        val sectionId = postJson("/api/v1/courses/$courseId/sections", """{"title":"Section"}""", owner)
            .data().getValue("sections").jsonArray.single().jsonObject.string("sectionId")
        val lessonId = postJson(
            "/api/v1/courses/$courseId/sections/$sectionId/lessons",
            """{"title":"Lesson","description":"Details","videoMediaId":"000000000000000000000002"}""",
            owner,
        ).data().getValue("sections").jsonArray.single().jsonObject.getValue("lessons").jsonArray.single()
            .jsonObject.string("lessonId")
        assertEquals(HttpStatusCode.OK, postJson("/api/v1/courses/$courseId/publish", "{}", owner).status)
        return CourseFixture(courseId, lessonId)
    }

    private suspend fun ApplicationTestBuilder.checkoutAndComplete(course: CourseFixture, student: String) {
        assertEquals(HttpStatusCode.Created, postJson("/api/v1/courses/${course.courseId}/checkout/complete", "{}", student).status)
        assertEquals(HttpStatusCode.OK, postJson(
            "/api/v1/courses/${course.courseId}/lessons/${course.lessonId}/complete", "{}", student,
        ).status)
    }

    private suspend fun seedPath(courseIds: List<ObjectId>): ObjectId = MongoClient.create(MONGO_URI).use {
        val id = ObjectId()
        it.getDatabase(DATABASE).getCollection<Document>("learningPaths").insertOne(Document(mapOf(
            "_id" to id,
            "title" to "Backend Developer Path",
            "description" to "An ordered learning path",
            "courseIds" to courseIds,
            "createdAt" to java.time.Instant.now().toString(),
        )))
        id
    }

    private suspend fun followCount(pathId: ObjectId): Long = MongoClient.create(MONGO_URI).use {
        it.getDatabase(DATABASE).getCollection<Document>("learningPathFollows")
            .countDocuments(eq("pathId", pathId))
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
    private suspend fun ApplicationTestBuilder.deleteJson(path: String, token: String) =
        client.delete(path) { json("{}", token) }
    private fun io.ktor.client.request.HttpRequestBuilder.json(body: String, token: String?) {
        contentType(ContentType.Application.Json)
        header("X-Requested-With", "mentora-web")
        token?.let { bearerAuth(it) }
        setBody(body)
    }
    private suspend fun HttpResponse.root() = Json.parseToJsonElement(bodyAsText()).jsonObject
    private suspend fun HttpResponse.data() = root().getValue("data").jsonObject
    private suspend fun HttpResponse.dataArray(): JsonArray = root().getValue("data").jsonArray
    private fun JsonObject.courses() = getValue("courses").jsonArray.map { it.jsonObject }
    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.content.toInt()
    private fun JsonObject.boolean(name: String) = getValue(name).jsonPrimitive.content.toBoolean()

    companion object {
        private const val MONGO_URI = "mongodb://localhost:27017/?replicaSet=rs0"
        private const val DATABASE = "mentora_learning_paths_test"
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
