package com.mentora.backend

import com.mentora.backend.config.AppConfig
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import com.mongodb.client.model.Updates.unset
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
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
import org.bson.types.ObjectId
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
    fun `course listing filters by contentLanguage without mixing courses across languages`() = testApplication {
        application { module(config()) }
        val admin = provision("language-filter-admin@example.com", "admin")
        val instructor = provision("language-filter-teacher@example.com", "instructor")
        val categoryId = postJson("/api/v1/categories", """{"name":"Language Filtering"}""", admin).dataString("id")
        val englishCourseId = createPublishedCourse(instructor, categoryId, "English Course", 40)
        val arabicCourseId = createPublishedCourse(instructor, categoryId, "Arabic Course", 42)
        assertEquals(
            HttpStatusCode.OK,
            patchJson("/api/v1/courses/$arabicCourseId", """{"contentLanguage":"ar"}""", instructor).status,
        )

        val englishOnly = client.get("/api/v1/courses?language=en").dataArray()
        assertEquals(listOf(englishCourseId), englishOnly.map { it.jsonObject.getValue("id").jsonPrimitive.content })

        val arabicOnly = client.get("/api/v1/courses?language=ar").dataArray()
        assertEquals(listOf(arabicCourseId), arabicOnly.map { it.jsonObject.getValue("id").jsonPrimitive.content })

        val unfiltered = client.get("/api/v1/courses").dataArray()
        assertEquals(
            setOf(englishCourseId, arabicCourseId),
            unfiltered.map { it.jsonObject.getValue("id").jsonPrimitive.content }.toSet(),
        )
    }

    @Test
    fun `course metadata resolves per requested locale with fallback, and stays searchable in both languages`() = testApplication {
        application { module(config()) }
        val admin = provision("i18n-admin@example.com", "admin")
        val instructor = provision("i18n-teacher@example.com", "instructor")
        val categoryId = postJson("/api/v1/categories", """{"name":"Localization"}""", admin).dataString("id")
        val courseId = createPublishedCourse(instructor, categoryId, "أساسيات تصميم تجربة المستخدم", 50)
        val translated = patchJson(
            "/api/v1/courses/$courseId",
            """{"contentLanguage":"ar","translations":{"en":{"title":"User Experience Design Fundamentals","description":"Learn UX design fundamentals."}}}""",
            instructor,
        )
        assertEquals(HttpStatusCode.OK, translated.status)
        // The full translations map round-trips back on the single-course response.
        assertEquals(
            "User Experience Design Fundamentals",
            translated.dataObject().getValue("translations").jsonObject.getValue("en").jsonObject
                .getValue("title").jsonPrimitive.content,
        )

        // Requested locale has a translation -> use it.
        val englishView = client.get("/api/v1/courses/$courseId?language=en").dataObject()
        assertEquals("User Experience Design Fundamentals", englishView.getValue("title").jsonPrimitive.content)
        assertEquals("ar", englishView.getValue("contentLanguage").jsonPrimitive.content, "resolving a title never hides the real content language")

        // Requested locale equals the content language -> base text, no translation entry needed for it.
        val arabicView = client.get("/api/v1/courses/$courseId?language=ar").dataObject()
        assertEquals("أساسيات تصميم تجربة المستخدم", arabicView.getValue("title").jsonPrimitive.content)

        // No `language` param at all -> base/original text, never blank (the instructor-editing default).
        val unresolvedView = client.get("/api/v1/courses/$courseId").dataObject()
        assertEquals("أساسيات تصميم تجربة المستخدم", unresolvedView.getValue("title").jsonPrimitive.content)

        // The list endpoint no longer hides this course from an English-locale query now that an
        // English translation exists, and returns it with the resolved English title.
        val englishList = client.get("/api/v1/courses?language=en").dataArray()
        assertEquals(listOf(courseId), englishList.map { it.jsonObject.getValue("id").jsonPrimitive.content })
        assertEquals("User Experience Design Fundamentals", englishList.single().jsonObject.getValue("title").jsonPrimitive.content)

        // Search matches the English translation...
        val englishSearch = client.get("/api/v1/courses?q=Experience").dataArray()
        assertTrue(englishSearch.map { it.jsonObject.getValue("id").jsonPrimitive.content }.contains(courseId))
        // ...and the Arabic base title, through the same single `q` mechanism.
        val arabicSearch = client.get(
            "/api/v1/courses?q=" + java.net.URLEncoder.encode("تجربة", "UTF-8"),
        ).dataArray()
        assertTrue(arabicSearch.map { it.jsonObject.getValue("id").jsonPrimitive.content }.contains(courseId))
    }

    @Test
    fun `unsupported translation locale and blank translated text are both rejected`() = testApplication {
        application { module(config()) }
        val admin = provision("i18n-validation-admin@example.com", "admin")
        val instructor = provision("i18n-validation-teacher@example.com", "instructor")
        val categoryId = postJson("/api/v1/categories", """{"name":"Localization Validation"}""", admin).dataString("id")
        val courseId = createPublishedCourse(instructor, categoryId, "Validation Course", 55)

        val unsupportedLocale = patchJson(
            "/api/v1/courses/$courseId",
            """{"translations":{"fr":{"title":"Titre","description":"Description"}}}""",
            instructor,
        )
        assertEquals(HttpStatusCode.BadRequest, unsupportedLocale.status)
        assertEquals("UNSUPPORTED", unsupportedLocale.errorFields()["translations.fr"]?.jsonPrimitive?.content)

        val blankTitle = patchJson(
            "/api/v1/courses/$courseId",
            """{"translations":{"ar":{"title":"   ","description":"وصف"}}}""",
            instructor,
        )
        assertEquals(HttpStatusCode.BadRequest, blankTitle.status)
        assertEquals("REQUIRED", blankTitle.errorFields()["translations.ar.title"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a legacy course document with no translations field still resolves to its base title`() = testApplication {
        application { module(config()) }
        val admin = provision("legacy-admin@example.com", "admin")
        val instructor = provision("legacy-teacher@example.com", "instructor")
        val categoryId = postJson("/api/v1/categories", """{"name":"Legacy"}""", admin).dataString("id")
        val courseId = createPublishedCourse(instructor, categoryId, "Legacy Course Without Translations", 60)

        // `courses.create` always writes a `translations` field now (possibly empty) — physically
        // remove it to simulate a document persisted before this field existed at all, proving
        // deserialization and resolution both degrade gracefully rather than erroring.
        MongoClient.create(MONGO_URI).use { client ->
            client.getDatabase(DATABASE).getCollection<Document>("courses")
                .updateOne(eq("_id", ObjectId(courseId)), unset("translations"))
        }

        val response = client.get("/api/v1/courses/$courseId?language=ar").dataObject()
        assertEquals("Legacy Course Without Translations", response.getValue("title").jsonPrimitive.content)
        val listed = client.get("/api/v1/courses?language=en").dataArray()
        assertTrue(listed.map { it.jsonObject.getValue("id").jsonPrimitive.content }.contains(courseId))
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

    @Test
    fun `owner and admin can unpublish while other roles are rejected and enrollment access remains`() = testApplication {
        application { module(config()) }
        val admin = provision("unpublish-admin@example.com", "admin")
        val owner = provision("unpublish-owner@example.com", "instructor")
        val otherInstructor = provision("unpublish-other@example.com", "instructor")
        val student = register("unpublish-student@example.com").dataString("accessToken")
        val categoryId = postJson("/api/v1/categories", """{"name":"Publishing"}""", admin).dataString("id")

        val ownerCourseId = createPublishedCourse(owner, categoryId, "Owner Course", 40)
        val ownerUnpublish = postJson("/api/v1/courses/$ownerCourseId/unpublish", "{}", owner)
        assertEquals(HttpStatusCode.OK, ownerUnpublish.status)
        assertEquals("draft", ownerUnpublish.dataString("status"))

        val protectedCourseId = createPublishedCourse(owner, categoryId, "Protected Course", 50)
        val nonOwnerUnpublish = postJson("/api/v1/courses/$protectedCourseId/unpublish", "{}", otherInstructor)
        assertEquals(HttpStatusCode.Forbidden, nonOwnerUnpublish.status)
        assertEquals("FORBIDDEN_NOT_OWNER", nonOwnerUnpublish.errorCode())
        val studentUnpublish = postJson("/api/v1/courses/$protectedCourseId/unpublish", "{}", student)
        assertEquals(HttpStatusCode.Forbidden, studentUnpublish.status)
        assertEquals("FORBIDDEN_ROLE", studentUnpublish.errorCode())

        putJson(
            "/api/v1/courses/$protectedCourseId/quiz/editor",
            """{"questions":[{"prompt":"Ready?","order":0,"options":[{"text":"Yes","isCorrect":true},{"text":"No","isCorrect":false}]}]}""",
            owner,
        )
        assertEquals(
            HttpStatusCode.Created,
            postJson("/api/v1/courses/$protectedCourseId/checkout/complete", "{}", student).status,
        )

        val adminUnpublish = postJson("/api/v1/courses/$protectedCourseId/unpublish", "{}", admin)
        assertEquals(HttpStatusCode.OK, adminUnpublish.status)
        assertEquals("draft", adminUnpublish.dataString("status"))
        assertFalse(client.get("/api/v1/courses").dataArray().any {
            it.jsonObject.getValue("id").jsonPrimitive.content == protectedCourseId
        })
        assertEquals(
            HttpStatusCode.OK,
            client.get("/api/v1/courses/$protectedCourseId/quiz") { bearerAuth(student) }.status,
        )
    }

    @Test
    fun `course owner can rename section and non owner is rejected`() = testApplication {
        application { module(config()) }
        val admin = provision("rename-admin@example.com", "admin")
        val owner = provision("rename-owner@example.com", "instructor")
        val otherInstructor = provision("rename-other@example.com", "instructor")
        val categoryId = postJson("/api/v1/categories", """{"name":"Renaming"}""", admin).dataString("id")
        val courseId = createCourse(owner, categoryId, objectIdHex(60), "Rename Course").dataString("id")
        val sectionId = addSection(courseId, owner, "Before")

        val renamed = patchJson(
            "/api/v1/courses/$courseId/sections/$sectionId", """{"title":"After"}""", owner,
        )
        assertEquals(HttpStatusCode.OK, renamed.status)
        assertEquals(
            "After",
            renamed.dataObject().getValue("sections").jsonArray.single().jsonObject
                .getValue("title").jsonPrimitive.content,
        )

        val rejected = patchJson(
            "/api/v1/courses/$courseId/sections/$sectionId", """{"title":"Stolen"}""", otherInstructor,
        )
        assertEquals(HttpStatusCode.Forbidden, rejected.status)
        assertEquals("FORBIDDEN_NOT_OWNER", rejected.errorCode())
    }

    @Test
    fun `deleting section removes it and compacts remaining order`() = testApplication {
        application { module(config()) }
        val admin = provision("delete-section-admin@example.com", "admin")
        val owner = provision("delete-section-owner@example.com", "instructor")
        val categoryId = postJson("/api/v1/categories", """{"name":"Section Deletion"}""", admin).dataString("id")
        val courseId = createCourse(owner, categoryId, objectIdHex(70), "Delete Section").dataString("id")
        val sectionIds = (1..3).map { addSection(courseId, owner, "Section $it") }

        val deleted = deleteJson("/api/v1/courses/$courseId/sections/${sectionIds[1]}", owner)
        assertEquals(HttpStatusCode.OK, deleted.status)
        val remaining = deleted.dataObject().getValue("sections").jsonArray
        assertEquals(listOf(sectionIds[0], sectionIds[2]), remaining.map {
            it.jsonObject.getValue("sectionId").jsonPrimitive.content
        })
        assertEquals(listOf(0, 1), remaining.map { it.jsonObject.getValue("order").jsonPrimitive.content.toInt() })
    }

    @Test
    fun `deleting lesson removes it and compacts remaining order`() = testApplication {
        application { module(config()) }
        val admin = provision("delete-lesson-admin@example.com", "admin")
        val owner = provision("delete-lesson-owner@example.com", "instructor")
        val categoryId = postJson("/api/v1/categories", """{"name":"Lesson Deletion"}""", admin).dataString("id")
        val courseId = createCourse(owner, categoryId, objectIdHex(80), "Delete Lesson").dataString("id")
        val sectionId = addSection(courseId, owner, "Lessons")
        val lessonIds = (1..3).map { number ->
            postJson(
                "/api/v1/courses/$courseId/sections/$sectionId/lessons",
                """{"title":"Lesson $number","description":"Body","videoMediaId":"${objectIdHex(80 + number)}"}""",
                owner,
            ).dataObject().getValue("sections").jsonArray.single().jsonObject
                .getValue("lessons").jsonArray.last().jsonObject.getValue("lessonId").jsonPrimitive.content
        }

        val deleted = deleteJson(
            "/api/v1/courses/$courseId/sections/$sectionId/lessons/${lessonIds[1]}", owner,
        )
        assertEquals(HttpStatusCode.OK, deleted.status)
        val remaining = deleted.dataObject().getValue("sections").jsonArray.single().jsonObject
            .getValue("lessons").jsonArray
        assertEquals(listOf(lessonIds[0], lessonIds[2]), remaining.map {
            it.jsonObject.getValue("lessonId").jsonPrimitive.content
        })
        assertEquals(listOf(0, 1), remaining.map { it.jsonObject.getValue("order").jsonPrimitive.content.toInt() })
    }

    private suspend fun ApplicationTestBuilder.createPublishedCourse(
        token: String, categoryId: String, title: String, seed: Int,
    ): String {
        val courseId = createCourse(token, categoryId, objectIdHex(seed), title).dataString("id")
        val sectionId = addSection(courseId, token, "Introduction")
        postJson(
            "/api/v1/courses/$courseId/sections/$sectionId/lessons",
            """{"title":"Welcome","description":"Start here","videoMediaId":"${objectIdHex(seed + 1)}"}""",
            token,
        )
        assertEquals(HttpStatusCode.OK, postJson("/api/v1/courses/$courseId/publish", "{}", token).status)
        return courseId
    }

    private suspend fun ApplicationTestBuilder.addSection(courseId: String, token: String, title: String) =
        postJson("/api/v1/courses/$courseId/sections", """{"title":"$title"}""", token)
            .dataObject().getValue("sections").jsonArray.last().jsonObject
            .getValue("sectionId").jsonPrimitive.content

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
    private suspend fun ApplicationTestBuilder.putJson(path: String, body: String, token: String) =
        client.put(path) { jsonRequest(body, token) }
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
