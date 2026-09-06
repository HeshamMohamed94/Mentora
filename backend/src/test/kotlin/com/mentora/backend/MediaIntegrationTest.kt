package com.mentora.backend

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mentora.backend.config.AppConfig
import com.mongodb.client.model.Filters.eq
import com.mongodb.client.model.Updates.set
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
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
import java.io.File
import java.time.Instant
import java.util.Date
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaIntegrationTest {
    @BeforeEach
    fun resetState() {
        dropDatabase()
        storageDirectory.deleteRecursively()
    }

    @Test
    fun `thumbnail and avatar uploads enforce public file and self ownership rules`() = testApplication {
        application { module(config()) }
        val admin = provision("media-admin@example.com", "admin")
        val instructor = provision("media-instructor@example.com", "instructor")
        val student = register("media-student@example.com").dataString("accessToken")
        val otherStudent = register("media-other@example.com").dataString("accessToken")
        val categoryId = createCategory(admin, "Media")
        val course = createCourse(instructor, categoryId, "Media Course")
        val thumbnailBytes = "thumbnail-bytes".encodeToByteArray()

        val thumbnail = upload(
            instructor, thumbnailBytes, "courseThumbnail", course.id, "image/png",
        )
        assertEquals(HttpStatusCode.Created, thumbnail.status)
        val thumbnailId = thumbnail.dataString("mediaId")
        assertTrue(thumbnail.dataString("storageKey").startsWith("courseThumbnail/${course.id}/"))
        val publicFile = client.get("/api/v1/media/$thumbnailId/file")
        assertEquals(HttpStatusCode.OK, publicFile.status)
        assertEquals(ContentType.Image.PNG.withoutParameters(), publicFile.contentType()?.withoutParameters())
        assertContentEquals(thumbnailBytes, publicFile.bodyAsBytes())

        val studentId = JWT.decode(student).getClaim("userId").asString()
        assertEquals(HttpStatusCode.Created, upload(student, byteArrayOf(1, 2), "avatar", studentId, "image/jpeg").status)
        val forbidden = upload(
            student, byteArrayOf(1), "avatar", JWT.decode(otherStudent).getClaim("userId").asString(), "image/jpeg",
        )
        assertEquals(HttpStatusCode.Forbidden, forbidden.status)
        assertEquals("FORBIDDEN_NOT_OWNER", forbidden.errorCode())
    }

    @Test
    fun `upload validation rejects incompatible content and oversized images`() = testApplication {
        application { module(config()) }
        val student = register("validation-student@example.com").dataString("accessToken")
        val studentId = JWT.decode(student).getClaim("userId").asString()

        val wrongType = upload(student, byteArrayOf(1), "avatar", studentId, "video/mp4")
        assertEquals(HttpStatusCode.BadRequest, wrongType.status)
        assertEquals("UNSUPPORTED", wrongType.errorField("contentType"))

        val oversized = upload(student, ByteArray(5 * 1024 * 1024 + 1), "avatar", studentId, "image/png")
        assertEquals(HttpStatusCode.BadRequest, oversized.status)
        assertEquals("TOO_LARGE", oversized.errorField("file"))
        assertFalse(storageDirectory.walkTopDown().any { it.isFile })
    }

    @Test
    fun `instructors can upload video only for their own course lesson`() = testApplication {
        application { module(config()) }
        val admin = provision("ownership-admin@example.com", "admin")
        val owner = provision("ownership-owner@example.com", "instructor")
        val other = provision("ownership-other@example.com", "instructor")
        val categoryId = createCategory(admin, "Ownership")
        val ownedCourse = createCourse(owner, categoryId, "Owned")
        val otherCourse = createCourse(other, categoryId, "Other")

        val thumbnailDenied = upload(other, byteArrayOf(1), "courseThumbnail", ownedCourse.id, "image/png")
        assertEquals(HttpStatusCode.Forbidden, thumbnailDenied.status)
        assertEquals("FORBIDDEN_NOT_OWNER", thumbnailDenied.errorCode())
        val videoDenied = upload(
            other, byteArrayOf(1), "lessonVideo", ownedCourse.lessonId, "video/mp4", ownedCourse.id,
        )
        assertEquals(HttpStatusCode.Forbidden, videoDenied.status)
        assertEquals("FORBIDDEN_NOT_OWNER", videoDenied.errorCode())

        val mismatchedLesson = upload(
            owner, byteArrayOf(1), "lessonVideo", otherCourse.lessonId, "video/mp4", ownedCourse.id,
        )
        assertEquals(HttpStatusCode.NotFound, mismatchedLesson.status)
        assertEquals("LESSON_NOT_FOUND", mismatchedLesson.errorCode())
    }

    @Test
    fun `lesson playback is gated and signed stream tokens are resource scoped`() = testApplication {
        application { module(config()) }
        val admin = provision("playback-admin@example.com", "admin")
        val instructor = provision("playback-instructor@example.com", "instructor")
        val enrolled = register("playback-enrolled@example.com").dataString("accessToken")
        val outsider = register("playback-outsider@example.com").dataString("accessToken")
        val categoryId = createCategory(admin, "Playback")
        val course = createCourse(instructor, categoryId, "Playback Course")
        publishCourse(course, instructor)
        completeEnrollment(course.id, enrolled)
        val videoBytes = "progressive-video".encodeToByteArray()
        val upload = upload(
            instructor, videoBytes, "lessonVideo", course.lessonId, "video/mp4", course.id, "42",
        )
        val mediaId = upload.dataString("mediaId")

        val publicFile = client.get("/api/v1/media/$mediaId/file")
        assertEquals(HttpStatusCode.NotFound, publicFile.status)
        assertEquals("MEDIA_NOT_FOUND", publicFile.errorCode())

        listOf(instructor, enrolled).forEach { token ->
            val playback = client.get("/api/v1/media/$mediaId/playback-url") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, playback.status)
            val url = playback.dataString("url")
            assertTrue(playback.dataObject().containsKey("expiresAt"))
            val stream = client.get(url)
            assertEquals(HttpStatusCode.OK, stream.status)
            assertEquals(ContentType.Video.MP4.withoutParameters(), stream.contentType()?.withoutParameters())
            assertContentEquals(videoBytes, stream.bodyAsBytes())
        }

        val denied = client.get("/api/v1/media/$mediaId/playback-url") { bearerAuth(outsider) }
        assertEquals(HttpStatusCode.Forbidden, denied.status)
        assertEquals("FORBIDDEN_NOT_ENROLLED", denied.errorCode())

        val validUrl = client.get("/api/v1/media/$mediaId/playback-url") { bearerAuth(instructor) }.dataString("url")
        val tampered = client.get(validUrl.dropLast(1) + if (validUrl.last() == 'a') 'b' else 'a')
        assertEquals(HttpStatusCode.Unauthorized, tampered.status)
        val expired = playbackToken(mediaId, Instant.now().minusSeconds(60))
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/media/$mediaId/stream?token=$expired").status)
    }

    private suspend fun ApplicationTestBuilder.upload(
        token: String,
        bytes: ByteArray,
        kind: String,
        ownerRefId: String,
        declaredContentType: String,
        courseId: String? = null,
        durationSeconds: String? = null,
    ): HttpResponse = client.post("/api/v1/media/uploads") {
        bearerAuth(token)
        header("X-Requested-With", "mentora-web")
        setBody(MultiPartFormDataContent(formData {
            append("kind", kind)
            append("ownerRefId", ownerRefId)
            append("contentType", declaredContentType)
            courseId?.let { append("courseId", it) }
            durationSeconds?.let { append("durationSeconds", it) }
            append("file", bytes, Headers.build {
                append(HttpHeaders.ContentDisposition, ContentDisposition.File.withParameter(ContentDisposition.Parameters.FileName, "ignored.bin").toString())
                append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
            })
        }))
    }

    private suspend fun ApplicationTestBuilder.createCategory(token: String, name: String) =
        postJson("/api/v1/categories", """{"name":"$name"}""", token).dataString("id")

    private suspend fun ApplicationTestBuilder.createCourse(token: String, categoryId: String, title: String): TestCourse {
        val response = postJson(
            "/api/v1/courses",
            """{"title":"$title","description":"Course details","categoryId":"$categoryId","level":"beginner","contentLanguage":"en","priceDisplay":{"amount":100,"currency":"USD"},"thumbnailMediaId":"${objectIdHex(80)}"}""",
            token,
        )
        val courseId = response.dataString("id")
        val withSection = postJson("/api/v1/courses/$courseId/sections", """{"title":"Section"}""", token)
        val sectionId = withSection.dataObject().getValue("sections").jsonArray.first().jsonObject.string("sectionId")
        val withLesson = postJson(
            "/api/v1/courses/$courseId/sections/$sectionId/lessons",
            """{"title":"Lesson","description":"Lesson body","videoMediaId":"${objectIdHex(81)}"}""",
            token,
        )
        val lessonId = withLesson.dataObject().getValue("sections").jsonArray.first().jsonObject
            .getValue("lessons").jsonArray.first().jsonObject.string("lessonId")
        return TestCourse(courseId, lessonId)
    }

    private suspend fun ApplicationTestBuilder.publishCourse(course: TestCourse, token: String) {
        assertEquals(HttpStatusCode.OK, postJson("/api/v1/courses/${course.id}/publish", "{}", token).status)
    }

    private suspend fun ApplicationTestBuilder.completeEnrollment(courseId: String, token: String) {
        assertEquals(HttpStatusCode.Created, postJson("/api/v1/courses/$courseId/checkout/complete", "{}", token).status)
    }

    private suspend fun ApplicationTestBuilder.provision(email: String, role: String): String {
        register(email)
        MongoClient.create(MONGO_URI).use { client ->
            client.getDatabase(DATABASE).getCollection<Document>("users").updateOne(eq("email", email), set("role", role))
        }
        return postJson("/api/v1/auth/login", """{"email":"$email","password":"StrongPass1"}""").dataString("accessToken")
    }

    private suspend fun ApplicationTestBuilder.register(email: String) =
        postJson("/api/v1/auth/register", """{"email":"$email","password":"StrongPass1","name":"Media User"}""")

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, token: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            header("X-Requested-With", "mentora-web")
            token?.let { bearerAuth(it) }
            setBody(body)
        }

    private suspend fun HttpResponse.root() = Json.parseToJsonElement(bodyAsText()).jsonObject
    private suspend fun HttpResponse.dataObject(): JsonObject = root().getValue("data").jsonObject
    private suspend fun HttpResponse.dataString(name: String) = dataObject().getValue(name).jsonPrimitive.content
    private suspend fun HttpResponse.errorCode() = root().getValue("error").jsonObject.string("code")
    private suspend fun HttpResponse.errorField(name: String) =
        root().getValue("error").jsonObject.getValue("fields").jsonObject.string(name)
    private fun JsonObject.string(name: String) = getValue(name).jsonPrimitive.content

    private data class TestCourse(val id: String, val lessonId: String)

    companion object {
        private const val MONGO_URI = "mongodb://localhost:27017/?replicaSet=rs0"
        private const val DATABASE = "mentora_media_test"
        private const val SIGNING_SECRET = "fixed-test-signing-secret-at-least-32-bytes"
        private val storageDirectory = File("build/media-integration-storage").absoluteFile

        @JvmStatic
        @AfterAll
        fun cleanUp() {
            dropDatabase()
            storageDirectory.deleteRecursively()
        }

        private fun dropDatabase() = runBlocking {
            MongoClient.create(MONGO_URI).use { it.getDatabase(DATABASE).drop() }
        }

        private fun playbackToken(mediaId: String, expiry: Instant): String = JWT.create()
            .withClaim("mediaId", mediaId)
            .withClaim("purpose", "media-playback")
            .withExpiresAt(Date.from(expiry))
            .sign(Algorithm.HMAC256(SIGNING_SECRET))

        private fun objectIdHex(seed: Int) = seed.toString(16).padStart(24, '0')

        private fun config() = AppConfig(
            mongoUri = MONGO_URI,
            mongoDatabaseName = DATABASE,
            jwtSigningSecret = SIGNING_SECRET,
            jwtIssuer = "mentora-backend-test",
            accessTokenTtlMinutes = 15,
            refreshTokenTtlDays = 30,
            mediaStorageRoot = storageDirectory.path,
            corsAllowedOrigins = listOf("http://localhost:3000"),
            aiProviderApiKey = null,
            aiProviderModel = "test-model",
            logLevel = "DEBUG",
        )
    }
}
