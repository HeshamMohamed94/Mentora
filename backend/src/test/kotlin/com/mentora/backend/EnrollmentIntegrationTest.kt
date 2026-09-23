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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
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

class EnrollmentIntegrationTest {
    @BeforeEach fun resetDatabase() = dropDatabase()

    @Test
    fun `published preview includes summary while draft preview is hidden`() = testApplication {
        application { module(config()) }
        val admin = provision("preview-admin@example.com", "admin", "Preview Admin")
        val instructor = provision("preview-instructor@example.com", "instructor", "Course Author")
        val student = register("preview-student@example.com", "Preview Student").dataString("accessToken")
        val categoryId = createCategory(admin, "Preview Subjects")
        val publishedId = createPublishedCourse(instructor, categoryId, "Published Course", 49900, "EGP")
        val draftId = createCourse(instructor, categoryId, "Draft Course", 12500, "EGP").dataString("id")

        val preview = client.get("/api/v1/courses/$publishedId/checkout") { bearerAuth(student) }
        assertEquals(HttpStatusCode.OK, preview.status)
        assertEquals("Course Author", preview.dataString("instructorName"))
        assertEquals("Published Course", preview.dataObject().getValue("course").jsonObject.string("title"))
        assertEquals("49900", preview.dataObject().getValue("priceDisplay").jsonObject.string("amount"))
        assertEquals("EGP", preview.dataObject().getValue("priceDisplay").jsonObject.string("currency"))

        val hidden = client.get("/api/v1/courses/$draftId/checkout") { bearerAuth(student) }
        assertEquals(HttpStatusCode.NotFound, hidden.status)
        assertEquals("COURSE_NOT_FOUND", hidden.errorCode())
    }

    @Test
    fun `completion is idempotent and enrollment list is student scoped`() = testApplication {
        application { module(config()) }
        val admin = provision("flow-admin@example.com", "admin", "Flow Admin")
        val instructor = provision("flow-instructor@example.com", "instructor", "Flow Author")
        val firstStudent = register("first-student@example.com", "First Student").dataString("accessToken")
        val secondStudent = register("second-student@example.com", "Second Student").dataString("accessToken")
        val categoryId = createCategory(admin, "Flow Subjects")
        val firstCourse = createPublishedCourse(instructor, categoryId, "First Course", 89900, "EGP")
        val secondCourse = createPublishedCourse(instructor, categoryId, "Second Course", 29900, "EGP")

        val first = complete(firstCourse, firstStudent)
        assertEquals(HttpStatusCode.Created, first.status)
        assertFalse(first.dataObject().getValue("alreadyEnrolled").jsonPrimitive.boolean)
        assertEquals(firstCourse, first.dataObject().getValue("enrollment").jsonObject.string("courseId"))

        val repeated = complete(firstCourse, firstStudent)
        assertEquals(HttpStatusCode.OK, repeated.status)
        assertTrue(repeated.dataObject().getValue("alreadyEnrolled").jsonPrimitive.boolean)
        assertEquals(
            first.dataObject().getValue("enrollment").jsonObject.string("id"),
            repeated.dataObject().getValue("enrollment").jsonObject.string("id"),
        )
        complete(secondCourse, secondStudent)

        MongoClient.create(MONGO_URI).use { client ->
            val database = client.getDatabase(DATABASE)
            assertEquals(2L, database.getCollection<Document>("enrollments").countDocuments())
            assertEquals(2L, database.getCollection<Document>("demoPurchases").countDocuments())
            val firstStudentId = database.getCollection<Document>("users")
                .find(eq("email", "first-student@example.com")).first().getObjectId("_id")
            assertEquals(1L, database.getCollection<Document>("enrollments").countDocuments(eq("userId", firstStudentId)))
        }

        val list = client.get("/api/v1/enrollments") { bearerAuth(firstStudent) }
        assertEquals(HttpStatusCode.OK, list.status)
        assertEquals(listOf(firstCourse), list.dataArray().map { it.jsonObject.string("courseId") })
    }

    @Test
    fun `concurrent duplicate checkout requests never create more than one enrollment`() = testApplication {
        // architecture/TESTING_STRATEGY.md § 1: "a duplicate request must never create two
        // enrollments" -- the sequential `completion is idempotent` test above only exercises
        // EnrollmentService.complete's early `repository.find` short-circuit (the second call
        // already sees the first call's committed enrollment). It never actually drives the
        // MongoWriteException/DUPLICATE_KEY recovery branch a few lines below it, which is the
        // part that matters for a *genuine* race -- two requests both passing the `find` check
        // before either has inserted. Firing many truly concurrent requests at the same
        // student+course is what reaches that branch instead.
        application { module(config()) }
        val admin = provision("race-admin@example.com", "admin", "Race Admin")
        val instructor = provision("race-instructor@example.com", "instructor", "Race Author")
        val student = register("race-student@example.com", "Race Student").dataString("accessToken")
        val categoryId = createCategory(admin, "Race Subjects")
        val courseId = createPublishedCourse(instructor, categoryId, "Race Course", 45000, "EGP")

        val responses = coroutineScope {
            (1..10).map { async { complete(courseId, student) } }.map { it.await() }
        }
        responses.forEach { assertTrue(it.status == HttpStatusCode.Created || it.status == HttpStatusCode.OK) }
        val enrollmentIds = responses.map { it.dataObject().getValue("enrollment").jsonObject.string("id") }.toSet()
        assertEquals(1, enrollmentIds.size)
        assertEquals(1, responses.count { it.status == HttpStatusCode.Created })

        MongoClient.create(MONGO_URI).use { client ->
            val database = client.getDatabase(DATABASE)
            assertEquals(1L, database.getCollection<Document>("enrollments").countDocuments())
            assertEquals(1L, database.getCollection<Document>("demoPurchases").countDocuments())
        }
    }

    @Test
    fun `non student roles cannot use checkout`() = testApplication {
        application { module(config()) }
        val admin = provision("roles-admin@example.com", "admin", "Roles Admin")
        val instructor = provision("roles-instructor@example.com", "instructor", "Roles Author")
        val categoryId = createCategory(admin, "Role Subjects")
        val courseId = createPublishedCourse(instructor, categoryId, "Role Course", 35000, "EGP")

        listOf(admin, instructor).forEach { token ->
            val preview = client.get("/api/v1/courses/$courseId/checkout") { bearerAuth(token) }
            assertEquals(HttpStatusCode.Forbidden, preview.status)
            assertEquals("FORBIDDEN_ROLE", preview.errorCode())
            val completion = complete(courseId, token)
            assertEquals(HttpStatusCode.Forbidden, completion.status)
            assertEquals("FORBIDDEN_ROLE", completion.errorCode())
        }
    }

    private suspend fun ApplicationTestBuilder.createCategory(token: String, name: String) =
        postJson("/api/v1/categories", """{"name":"$name"}""", token).dataString("id")

    private suspend fun ApplicationTestBuilder.createPublishedCourse(
        token: String, categoryId: String, title: String, amount: Int, currency: String,
    ): String {
        val courseId = createCourse(token, categoryId, title, amount, currency).dataString("id")
        val sectionId = postJson(
            "/api/v1/courses/$courseId/sections", """{"title":"Introduction"}""", token,
        ).dataObject().getValue("sections").jsonArray.first().jsonObject.string("sectionId")
        postJson(
            "/api/v1/courses/$courseId/sections/$sectionId/lessons",
            """{"title":"Welcome","description":"Start here","videoMediaId":"${objectIdHex(amount)}"}""",
            token,
        )
        assertEquals(HttpStatusCode.OK, postJson("/api/v1/courses/$courseId/publish", "{}", token).status)
        return courseId
    }

    private suspend fun ApplicationTestBuilder.createCourse(
        token: String, categoryId: String, title: String, amount: Int, currency: String,
    ) = postJson(
        "/api/v1/courses",
        """{"title":"$title","description":"Course details","categoryId":"$categoryId","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":$amount,"currency":"$currency"},"thumbnailMediaId":"${objectIdHex(amount + 1)}"}""",
        token,
    )

    private suspend fun ApplicationTestBuilder.complete(courseId: String, token: String) =
        postJson("/api/v1/courses/$courseId/checkout/complete", "{}", token)

    private suspend fun ApplicationTestBuilder.provision(email: String, role: String, name: String): String {
        register(email, name)
        MongoClient.create(MONGO_URI).use { client ->
            client.getDatabase(DATABASE).getCollection<Document>("users").updateOne(eq("email", email), set("role", role))
        }
        return postJson(
            "/api/v1/auth/login", """{"email":"$email","password":"StrongPass1"}""",
        ).dataString("accessToken")
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
    private suspend fun HttpResponse.dataObject(): JsonObject = root().getValue("data").jsonObject
    private suspend fun HttpResponse.dataArray(): JsonArray = root().getValue("data").jsonArray
    private suspend fun HttpResponse.dataString(name: String) = dataObject().getValue(name).jsonPrimitive.content
    private suspend fun HttpResponse.errorCode() = root().getValue("error").jsonObject.string("code")
    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content

    companion object {
        private const val MONGO_URI = "mongodb://localhost:27017"
        private const val DATABASE = "mentora_enrollment_test"
        @JvmStatic @AfterAll fun cleanUp() = dropDatabase()
        private fun dropDatabase() = runBlocking { MongoClient.create(MONGO_URI).use { it.getDatabase(DATABASE).drop() } }
        private fun objectIdHex(seed: Int) = seed.toString(16).padStart(24, '0').takeLast(24)
        private fun config() = AppConfig(
            mongoUri = MONGO_URI, mongoDatabaseName = DATABASE,
            jwtSigningSecret = "fixed-test-signing-secret-at-least-32-bytes", jwtIssuer = "mentora-backend-test",
            accessTokenTtlMinutes = 15, refreshTokenTtlDays = 30, mediaStorageRoot = "storage/media",
            corsAllowedOrigins = listOf("http://localhost:3000"), aiProviderApiKey = null,
            aiProviderModel = "test-model", logLevel = "DEBUG",
        )
    }
}
