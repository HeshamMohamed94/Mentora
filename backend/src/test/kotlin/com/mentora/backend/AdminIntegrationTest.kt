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

class AdminIntegrationTest {
    @BeforeEach fun resetDatabase() = dropDatabase()

    @Test
    fun `dashboard counts courses and disjoint account roles`() = testApplication {
        application { module(config()) }
        val admin = provision("dashboard-admin@example.com", "admin", "Admin")
        val firstInstructor = provision("dashboard-first-instructor@example.com", "instructor", "First Instructor")
        val secondInstructor = provision("dashboard-second-instructor@example.com", "instructor", "Second Instructor")
        register("dashboard-first-student@example.com", "First Student")
        register("dashboard-second-student@example.com", "Second Student")
        val categoryId = category("Dashboard", admin)
        course("Draft course", categoryId, firstInstructor, published = false)
        course("Published course", categoryId, firstInstructor, published = true)
        course("Other published course", categoryId, secondInstructor, published = true)

        val response = client.get("/api/v1/admin/dashboard") { bearerAuth(admin) }
        assertEquals(HttpStatusCode.OK, response.status)
        val dashboard = response.data()
        assertEquals(3, dashboard.int("totalCourses"))
        assertEquals(2, dashboard.int("publishedCourses"))
        assertEquals(1, dashboard.int("draftCourses"))
        assertEquals(2, dashboard.int("totalStudents"))
        assertEquals(2, dashboard.int("totalInstructors"))
    }

    @Test
    fun `courses include every owner and status with aggregates and text filtering`() = testApplication {
        application { module(config()) }
        val admin = provision("courses-admin@example.com", "admin", "Admin")
        val firstInstructor = provision("courses-first@example.com", "instructor", "Ada Teacher")
        val secondInstructor = provision("courses-second@example.com", "instructor", "Grace Teacher")
        val student = register("courses-student@example.com", "Student").data().string("accessToken")
        val categoryId = category("Courses", admin)
        val draftId = course("Kotlin Foundations", categoryId, firstInstructor, published = false)
        val publishedId = course("Mongo Essentials", categoryId, secondInstructor, published = true)
        checkout(publishedId, student)

        val response = client.get("/api/v1/admin/courses") { bearerAuth(admin) }
        assertEquals(HttpStatusCode.OK, response.status)
        val courses = response.page()
        assertEquals(listOf(draftId, publishedId), courses.map { it.string("id") })
        assertEquals(listOf("Ada Teacher", "Grace Teacher"), courses.map { it.string("instructorName") })
        assertEquals(listOf("draft", "published"), courses.map { it.string("status") })
        assertEquals(listOf(0, 1), courses.map { it.int("enrollmentCount") })

        val filtered = client.get("/api/v1/admin/courses?q=Mongo") { bearerAuth(admin) }.page()
        assertEquals(listOf("Mongo Essentials"), filtered.map { it.string("title") })
    }

    @Test
    fun `course search finds a title beyond the default page limit even when a common word is shared`() = testApplication {
        // Phase 8 B1 regression (D-10): the old `$text` filter OR-matched any shared word (e.g.
        // "Course") across every course, then paginated by `_id` ascending with no relevance sort -
        // so a just-created course whose title also happens to share a common word with 20+ older
        // courses was silently dropped past page 1, even though it was the only genuine title match.
        application { module(config()) }
        val admin = provision("search-page-admin@example.com", "admin", "Admin")
        val instructor = provision("search-page-instructor@example.com", "instructor", "Instructor")
        val categoryId = category("Search Paging", admin)
        repeat(25) { i -> course("Course Number $i", categoryId, instructor, published = true) }
        val targetId = course("Course Zephyr", categoryId, instructor, published = true)

        val filtered = client.get("/api/v1/admin/courses?q=Course%20Zephyr") { bearerAuth(admin) }.page()
        assertEquals(listOf(targetId), filtered.map { it.string("id") })
        assertEquals(listOf("Course Zephyr"), filtered.map { it.string("title") })
    }

    @Test
    fun `users and instructors stay role scoped and include their aggregates`() = testApplication {
        application { module(config()) }
        val admin = provision("roles-admin@example.com", "admin", "Admin Account")
        val firstInstructor = provision("ada.faculty@example.com", "instructor", "Ada Faculty")
        provision("grace.faculty@example.com", "instructor", "Grace Faculty")
        val firstStudent = register("learner.one@example.com", "Unique Learner").data().string("accessToken")
        register("email-match@example.com", "Second Learner")
        val categoryId = category("Roles", admin)
        val publishedId = course("Published", categoryId, firstInstructor, published = true)
        course("Draft", categoryId, firstInstructor, published = false)
        checkout(publishedId, firstStudent)

        val users = client.get("/api/v1/admin/users") { bearerAuth(admin) }.page()
        assertEquals(setOf("Unique Learner", "Second Learner"), users.map { it.string("name") }.toSet())
        assertFalse(users.any { it.string("email").contains("faculty") || it.string("email").contains("admin") })
        assertEquals(1, users.single { it.string("name") == "Unique Learner" }.int("enrollmentCount"))
        assertEquals(0, users.single { it.string("name") == "Second Learner" }.int("enrollmentCount"))
        assertEquals(1, client.get("/api/v1/admin/users?q=unique") { bearerAuth(admin) }.page().size)
        assertEquals(1, client.get("/api/v1/admin/users?q=EMAIL-MATCH%40EXAMPLE.COM") {
            bearerAuth(admin)
        }.page().size)

        val instructors = client.get("/api/v1/admin/instructors") { bearerAuth(admin) }.page()
        assertEquals(setOf("Ada Faculty", "Grace Faculty"), instructors.map { it.string("name") }.toSet())
        assertFalse(instructors.any { it.string("email").contains("learner") || it.string("email").contains("admin") })
        val ada = instructors.single { it.string("name") == "Ada Faculty" }
        assertEquals(2, ada.int("courseCount"))
        assertEquals(1, ada.int("publishedCount"))
        assertEquals(1, client.get("/api/v1/admin/instructors?q=ADA.FACULTY") {
            bearerAuth(admin)
        }.page().size)
    }

    @Test
    fun `all admin routes reject non admins and unauthenticated callers`() = testApplication {
        application { module(config()) }
        val student = register("access-student@example.com", "Student").data().string("accessToken")
        val instructor = provision("access-instructor@example.com", "instructor", "Instructor")

        listOf("dashboard", "courses", "users", "instructors").forEach { endpoint ->
            val path = "/api/v1/admin/$endpoint"
            assertEquals(HttpStatusCode.Forbidden, client.get(path) { bearerAuth(student) }.status)
            assertEquals(HttpStatusCode.Forbidden, client.get(path) { bearerAuth(instructor) }.status)
            assertEquals(HttpStatusCode.Unauthorized, client.get(path).status)
        }
    }

    private suspend fun ApplicationTestBuilder.category(name: String, admin: String) =
        postJson("/api/v1/categories", """{"name":"$name"}""", admin).data().string("id")

    private suspend fun ApplicationTestBuilder.course(
        title: String, categoryId: String, owner: String, published: Boolean,
    ): String {
        val courseId = postJson(
            "/api/v1/courses",
            """{"title":"$title","description":"Searchable $title details","categoryId":"$categoryId","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":100,"currency":"EGP"},"thumbnailMediaId":"000000000000000000000001"}""",
            owner,
        ).data().string("id")
        if (published) {
            val sectionId = postJson("/api/v1/courses/$courseId/sections", """{"title":"Section"}""", owner)
                .data().getValue("sections").jsonArray.single().jsonObject.string("sectionId")
            postJson(
                "/api/v1/courses/$courseId/sections/$sectionId/lessons",
                """{"title":"Lesson","description":"Details","videoMediaId":"000000000000000000000002"}""",
                owner,
            )
            postJson("/api/v1/courses/$courseId/publish", "{}", owner)
        }
        return courseId
    }

    private suspend fun ApplicationTestBuilder.checkout(courseId: String, student: String) {
        assertEquals(HttpStatusCode.Created, postJson(
            "/api/v1/courses/$courseId/checkout/complete", "{}", student,
        ).status)
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
    private fun io.ktor.client.request.HttpRequestBuilder.json(body: String, token: String?) {
        contentType(ContentType.Application.Json)
        header("X-Requested-With", "mentora-web")
        token?.let { bearerAuth(it) }
        setBody(body)
    }
    private suspend fun HttpResponse.root() = Json.parseToJsonElement(bodyAsText()).jsonObject
    private suspend fun HttpResponse.data(): JsonObject = root().getValue("data").jsonObject
    private suspend fun HttpResponse.page(): List<JsonObject> =
        (root().getValue("data") as JsonArray).map { it.jsonObject }
    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content
    private fun JsonObject.int(name: String) = getValue(name).jsonPrimitive.content.toInt()

    companion object {
        private const val MONGO_URI = "mongodb://localhost:27017/?replicaSet=rs0"
        private const val DATABASE = "mentora_admin_test"
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
