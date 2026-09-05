package com.mentora.backend

import com.mentora.backend.config.AppConfig
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CoursesCategoriesIntegrationTest {
    @BeforeEach fun resetDatabase() = dropDatabase()

    @Test
    fun `admin category RBAC owner guards publication visibility and nested curriculum round trip`() = testApplication {
        application { module(config()) }
        val admin = provision("admin@example.com", "admin")
        val instructor = provision("teacher@example.com", "instructor")
        val otherInstructor = provision("other@example.com", "instructor")
        val student = register("student@example.com").dataString("accessToken")

        val forbiddenCategory = postJson("/api/v1/categories", """{"name":"Unauthorized"}""", student)
        assertEquals(HttpStatusCode.Forbidden, forbiddenCategory.status)
        assertEquals("FORBIDDEN_ROLE", forbiddenCategory.errorCode())

        val categoryResponse = postJson("/api/v1/categories", """{"name":"Software Engineering"}""", admin)
        assertEquals(HttpStatusCode.Created, categoryResponse.status)
        val categoryId = categoryResponse.dataString("id")
        assertEquals("software-engineering", categoryResponse.dataString("slug"))

        val draft = createCourse(instructor, categoryId, thumbnail = null, title = "Kotlin Foundations")
        val courseId = draft.dataString("id")
        assertEquals("draft", draft.dataString("status"))

        val otherPatch = patchJson("/api/v1/courses/$courseId", """{"title":"Stolen"}""", otherInstructor)
        assertEquals(HttpStatusCode.Forbidden, otherPatch.status)
        assertEquals("FORBIDDEN_NOT_OWNER", otherPatch.errorCode())
        val otherPublish = postJson("/api/v1/courses/$courseId/publish", "{}", otherInstructor)
        assertEquals(HttpStatusCode.Forbidden, otherPublish.status)
        assertEquals("FORBIDDEN_NOT_OWNER", otherPublish.errorCode())

        assertEquals(HttpStatusCode.OK, client.get("/api/v1/courses/$courseId") { bearerAuth(instructor) }.status)
        val hiddenDraft = client.get("/api/v1/courses/$courseId") { bearerAuth(student) }
        assertEquals(HttpStatusCode.NotFound, hiddenDraft.status)
        assertEquals("COURSE_NOT_FOUND", hiddenDraft.errorCode())

        val sectionIds = mutableListOf<String>()
        repeat(2) { sectionIndex ->
            val section = postJson(
                "/api/v1/courses/$courseId/sections", """{"title":"Section ${sectionIndex + 1}"}""", instructor,
            )
            val sectionId = section.dataObject().getValue("sections").jsonArray.last().jsonObject
                .getValue("sectionId").jsonPrimitive.content
            sectionIds += sectionId
            repeat(2) { lessonIndex ->
                val video = if (sectionIndex == 1 && lessonIndex == 1) "" else ",\"videoMediaId\":\"${objectIdHex(sectionIndex * 2 + lessonIndex + 1)}\""
                val lesson = postJson(
                    "/api/v1/courses/$courseId/sections/$sectionId/lessons",
                    """{"title":"Lesson ${lessonIndex + 1}","description":"Lesson body"$video,"resources":[{"label":"Docs","url":"https://example.com"}]}""",
                    instructor,
                )
                assertEquals(HttpStatusCode.Created, lesson.status)
            }
        }

        val firstPublish = postJson("/api/v1/courses/$courseId/publish", "{}", instructor)
        assertEquals(HttpStatusCode.BadRequest, firstPublish.status)
        val fields = firstPublish.errorFields()
        assertEquals("REQUIRED", fields["thumbnail"]?.jsonPrimitive?.content)
        assertTrue(fields.entries.any { (key, value) -> key.endsWith(".videoMediaId") && value.jsonPrimitive.content == "REQUIRED" })

        val fullDraft = client.get("/api/v1/courses/$courseId") { bearerAuth(instructor) }.dataObject()
        val sections = fullDraft.getValue("sections").jsonArray
        assertEquals(2, sections.size)
        assertTrue(sections.all { it.jsonObject.getValue("lessons").jsonArray.size == 2 })
        val missingLessonId = sections[1].jsonObject.getValue("lessons").jsonArray[1].jsonObject
            .getValue("lessonId").jsonPrimitive.content
        patchJson(
            "/api/v1/courses/$courseId/sections/${sectionIds[1]}/lessons/$missingLessonId",
            """{"videoMediaId":"${objectIdHex(20)}"}""", instructor,
        )
        patchJson(
            "/api/v1/courses/$courseId", """{"thumbnailMediaId":"${objectIdHex(21)}"}""", instructor,
        )
        val published = postJson("/api/v1/courses/$courseId/publish", "{}", instructor)
        assertEquals(HttpStatusCode.OK, published.status)
        assertEquals("published", published.dataString("status"))

        val secondDraft = createCourse(instructor, categoryId, objectIdHex(22), "Private Draft")
        assertEquals(HttpStatusCode.Created, secondDraft.status)
        val publicList = client.get("/api/v1/courses").dataArray()
        assertEquals(listOf(courseId), publicList.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        val ignoredStatus = client.get("/api/v1/courses?status=draft").dataArray()
        assertEquals(listOf(courseId), ignoredStatus.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        assertFalse(client.get("/api/v1/courses").bodyAsText().contains("sections"))

        val blockedDelete = deleteJson("/api/v1/categories/$categoryId", admin)
        assertEquals(HttpStatusCode.Conflict, blockedDelete.status)
        assertEquals("CATEGORY_IN_USE", blockedDelete.errorCode())
    }

    @Test
    fun `section reorder persists contiguous order and rejects incomplete ids`() = testApplication {
        application { module(config()) }
        val admin = provision("reorder-admin@example.com", "admin")
        val instructor = provision("reorder-teacher@example.com", "instructor")
        val categoryId = postJson("/api/v1/categories", """{"name":"Reordering"}""", admin).dataString("id")
        val courseId = createCourse(instructor, categoryId, objectIdHex(30), "Ordering").dataString("id")
        val ids = (1..3).map { number ->
            postJson("/api/v1/courses/$courseId/sections", """{"title":"Section $number"}""", instructor)
                .dataObject().getValue("sections").jsonArray.last().jsonObject.getValue("sectionId").jsonPrimitive.content
        }
        val desired = ids.reversed()
        val reordered = patchJson(
            "/api/v1/courses/$courseId/sections/reorder",
            """{"sectionIds":["${desired[0]}","${desired[1]}","${desired[2]}"]}""", instructor,
        )
        assertEquals(HttpStatusCode.OK, reordered.status)
        val fetched = client.get("/api/v1/courses/$courseId") { bearerAuth(instructor) }.dataObject()
        val fetchedSections = fetched.getValue("sections").jsonArray
        assertEquals(desired, fetchedSections.map { it.jsonObject.getValue("sectionId").jsonPrimitive.content })
        assertEquals(listOf(0, 1, 2), fetchedSections.map { it.jsonObject.getValue("order").jsonPrimitive.content.toInt() })

        val invalid = patchJson(
            "/api/v1/courses/$courseId/sections/reorder", """{"sectionIds":["${ids[0]}"]}""", instructor,
        )
        assertEquals(HttpStatusCode.BadRequest, invalid.status)
        assertEquals("VALIDATION_ERROR", invalid.errorCode())

        val lessonIds = (1..3).map { number ->
            postJson(
                "/api/v1/courses/$courseId/sections/${desired[0]}/lessons",
                """{"title":"Lesson $number","description":"Body","videoMediaId":"${objectIdHex(30 + number)}"}""",
                instructor,
            ).dataObject().getValue("sections").jsonArray.first().jsonObject.getValue("lessons").jsonArray
                .last().jsonObject.getValue("lessonId").jsonPrimitive.content
        }
        val desiredLessons = lessonIds.reversed()
        val lessonReorder = patchJson(
            "/api/v1/courses/$courseId/sections/${desired[0]}/lessons/reorder",
            """{"lessonIds":["${desiredLessons[0]}","${desiredLessons[1]}","${desiredLessons[2]}"]}""",
            instructor,
        )
        assertEquals(HttpStatusCode.OK, lessonReorder.status)
        val reorderedLessons = lessonReorder.dataObject().getValue("sections").jsonArray.first().jsonObject
            .getValue("lessons").jsonArray
        assertEquals(desiredLessons, reorderedLessons.map { it.jsonObject.getValue("lessonId").jsonPrimitive.content })
        assertEquals(listOf(0, 1, 2), reorderedLessons.map { it.jsonObject.getValue("order").jsonPrimitive.content.toInt() })
    }

    private suspend fun ApplicationTestBuilder.createCourse(
        token: String, categoryId: String, thumbnail: String?, title: String,
    ): HttpResponse {
        val thumbnailJson = thumbnail?.let { ",\"thumbnailMediaId\":\"$it\"" } ?: ""
        return postJson(
            "/api/v1/courses",
            """{"title":"$title","description":"A complete course description","categoryId":"$categoryId","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":2500,"currency":"USD"}$thumbnailJson}""",
            token,
        )
    }

    private suspend fun ApplicationTestBuilder.provision(email: String, role: String): String {
        register(email)
        MongoClient.create(MONGO_URI).use { client ->
            client.getDatabase(DATABASE).getCollection<Document>("users")
                .updateOne(eq("email", email), set("role", role))
        }
        return postJson("/api/v1/auth/login", """{"email":"$email","password":"StrongPass1"}""").dataString("accessToken")
    }

    private suspend fun ApplicationTestBuilder.register(email: String) =
        postJson("/api/v1/auth/register", """{"email":"$email","password":"StrongPass1","name":"Test User"}""")
    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, token: String? = null) =
        client.post(path) { jsonRequest(body, token) }
    private suspend fun ApplicationTestBuilder.patchJson(path: String, body: String, token: String) =
        client.patch(path) { jsonRequest(body, token) }
    private suspend fun ApplicationTestBuilder.deleteJson(path: String, token: String) =
        client.delete(path) { header("X-Requested-With", "mentora-web"); bearerAuth(token) }
    private fun io.ktor.client.request.HttpRequestBuilder.jsonRequest(body: String, token: String?) {
        contentType(ContentType.Application.Json)
        header("X-Requested-With", "mentora-web")
        token?.let { bearerAuth(it) }
        setBody(body)
    }
    private suspend fun HttpResponse.root() = Json.parseToJsonElement(bodyAsText()).jsonObject
    private suspend fun HttpResponse.dataObject(): JsonObject = root().getValue("data").jsonObject
    private suspend fun HttpResponse.dataArray(): JsonArray = root().getValue("data").jsonArray
    private suspend fun HttpResponse.dataString(name: String) = dataObject().getValue(name).jsonPrimitive.content
    private suspend fun HttpResponse.errorCode() = root().getValue("error").jsonObject.getValue("code").jsonPrimitive.content
    private suspend fun HttpResponse.errorFields() = root().getValue("error").jsonObject.getValue("fields").jsonObject

    companion object {
        private const val MONGO_URI = "mongodb://localhost:27017"
        private const val DATABASE = "mentora_courses_categories_test"
        @JvmStatic @AfterAll fun cleanUp() = dropDatabase()
        private fun dropDatabase() = runBlocking { MongoClient.create(MONGO_URI).use { it.getDatabase(DATABASE).drop() } }
        private fun objectIdHex(seed: Int) = seed.toString(16).padStart(24, '0')
        private fun config() = AppConfig(
            mongoUri = MONGO_URI, mongoDatabaseName = DATABASE,
            jwtSigningSecret = "fixed-test-signing-secret-at-least-32-bytes", jwtIssuer = "mentora-backend-test",
            accessTokenTtlMinutes = 15, refreshTokenTtlDays = 30, mediaStorageRoot = "storage/media",
            corsAllowedOrigins = listOf("http://localhost:3000"), aiProviderApiKey = null,
            aiProviderModel = "test-model", logLevel = "DEBUG",
        )
    }
}
