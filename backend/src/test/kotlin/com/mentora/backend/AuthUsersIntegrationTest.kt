package com.mentora.backend

import com.mentora.backend.config.AppConfig
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AuthUsersIntegrationTest {
    @BeforeEach
    fun resetDatabase() = dropDatabase()

    @Test
    fun `register login and authenticated profile never expose password hash`() = testApplication {
        application { module(config()) }

        val registered = register("learner@example.com", "StrongPass1", "Learner")
        assertEquals(HttpStatusCode.Created, registered.status)
        val registeredText = registered.bodyAsText()
        assertFalse(registeredText.contains("passwordHash"))
        val cookies = registered.headers.getAll(HttpHeaders.SetCookie).orEmpty().joinToString(";")
        assertTrue(cookies.contains("HttpOnly"))
        assertTrue(cookies.contains("Secure"))
        assertTrue(cookies.contains("SameSite=Lax"))

        val login = postJson("/api/v1/auth/login", """{"email":"LEARNER@example.com","password":"StrongPass1"}""")
        assertEquals(HttpStatusCode.OK, login.status)
        val access = login.dataString("accessToken")
        val profile = client.get("/api/v1/users/me") { bearerAuth(access) }
        assertEquals(HttpStatusCode.OK, profile.status)
        val profileText = profile.bodyAsText()
        assertFalse(profileText.contains("passwordHash"))
        val data = Json.parseToJsonElement(profileText).jsonObject.getValue("data").jsonObject
        assertEquals("learner@example.com", data.getValue("email").jsonPrimitive.content)
        assertEquals("Learner", data.getValue("name").jsonPrimitive.content)
        assertEquals("student", data.getValue("role").jsonPrimitive.content)
    }

    @Test
    fun `duplicate registration is rejected and login failures are identical`() = testApplication {
        application { module(config()) }
        register("duplicate@example.com", "StrongPass1", "First")

        val duplicate = register("DUPLICATE@example.com", "StrongPass1", "Second")
        assertEquals(HttpStatusCode.Conflict, duplicate.status)
        assertEquals("EMAIL_ALREADY_REGISTERED", duplicate.errorCode())

        val wrong = postJson(
            "/api/v1/auth/login", """{"email":"duplicate@example.com","password":"WrongPass1"}""", FIXED_ID,
        )
        val unknown = postJson(
            "/api/v1/auth/login", """{"email":"unknown@example.com","password":"WrongPass1"}""", FIXED_ID,
        )
        assertEquals(HttpStatusCode.Unauthorized, wrong.status)
        assertEquals(HttpStatusCode.Unauthorized, unknown.status)
        assertEquals(wrong.bodyAsText(), unknown.bodyAsText())
        assertEquals("AUTH_INVALID_CREDENTIALS", wrong.errorCode())
    }

    @Test
    fun `refresh rotation detects reuse and revokes the entire family`() = testApplication {
        application { module(config()) }
        val original = register("rotate@example.com", "StrongPass1", "Rotate").dataString("refreshToken")

        val rotated = postJson("/api/v1/auth/refresh", """{"refreshToken":"$original"}""")
        assertEquals(HttpStatusCode.OK, rotated.status)
        val replacement = rotated.dataString("refreshToken")
        assertNotEquals(original, replacement)
        val newAccess = rotated.dataString("accessToken")
        assertEquals(HttpStatusCode.OK, client.get("/api/v1/users/me") { bearerAuth(newAccess) }.status)

        val reuse = postJson("/api/v1/auth/refresh", """{"refreshToken":"$original"}""")
        assertEquals(HttpStatusCode.Unauthorized, reuse.status)
        assertEquals("AUTH_TOKEN_INVALID", reuse.errorCode())

        val familyRevoked = postJson("/api/v1/auth/refresh", """{"refreshToken":"$replacement"}""")
        assertEquals(HttpStatusCode.Unauthorized, familyRevoked.status)
        assertEquals("AUTH_TOKEN_INVALID", familyRevoked.errorCode())
    }

    @Test
    fun `logout revokes refresh while missing and tampered access tokens are rejected`() = testApplication {
        application { module(config()) }
        val registration = register("logout@example.com", "StrongPass1", "Logout")
        val refresh = registration.dataString("refreshToken")
        val access = registration.dataString("accessToken")

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/users/me").status)
        val tampered = client.get("/api/v1/users/me") { bearerAuth("${access}x") }
        assertEquals(HttpStatusCode.Unauthorized, tampered.status)
        assertEquals("AUTH_TOKEN_INVALID", tampered.errorCode())

        val logout = postJson("/api/v1/auth/logout", """{"refreshToken":"$refresh"}""")
        assertEquals(HttpStatusCode.OK, logout.status)
        val cleared = logout.headers.getAll(HttpHeaders.SetCookie).orEmpty().joinToString(";")
        assertTrue(cleared.contains("mentora_access_token="))
        assertTrue(cleared.contains("mentora_refresh_token="))
        assertTrue(cleared.contains("Max-Age=0"))

        val afterLogout = postJson("/api/v1/auth/refresh", """{"refreshToken":"$refresh"}""")
        assertEquals(HttpStatusCode.Unauthorized, afterLogout.status)
        assertEquals("AUTH_TOKEN_INVALID", afterLogout.errorCode())
    }

    @Test
    fun `patch profile persists name and locale`() = testApplication {
        application { module(config()) }
        val access = register("profile@example.com", "StrongPass1", "Before").dataString("accessToken")

        val patched = client.patch("/api/v1/users/me") {
            bearerAuth(access)
            header("X-Requested-With", "mentora-web")
            contentType(ContentType.Application.Json)
            setBody("""{"name":"After","preferredLocale":"ar"}""")
        }
        assertEquals(HttpStatusCode.OK, patched.status)
        assertEquals("After", patched.dataObject().getValue("name").jsonPrimitive.content)
        assertEquals("ar", patched.dataObject().getValue("preferredLocale").jsonPrimitive.content)

        val fetched = client.get("/api/v1/users/me") { bearerAuth(access) }
        assertEquals("After", fetched.dataObject().getValue("name").jsonPrimitive.content)
        assertEquals("ar", fetched.dataObject().getValue("preferredLocale").jsonPrimitive.content)
    }

    private suspend fun ApplicationTestBuilder.register(email: String, password: String, name: String) =
        postJson("/api/v1/auth/register", """{"email":"$email","password":"$password","name":"$name"}""")

    private suspend fun ApplicationTestBuilder.postJson(path: String, body: String, requestId: String? = null) =
        client.post(path) {
            contentType(ContentType.Application.Json)
            header("X-Requested-With", "mentora-web")
            requestId?.let { header(HttpHeaders.XRequestId, it) }
            setBody(body)
        }

    private suspend fun HttpResponse.dataObject(): JsonObject =
        Json.parseToJsonElement(bodyAsText()).jsonObject.getValue("data").jsonObject
    private suspend fun HttpResponse.dataString(name: String): String = dataObject().getValue(name).jsonPrimitive.content
    private suspend fun HttpResponse.errorCode(): String = Json.parseToJsonElement(bodyAsText()).jsonObject
        .getValue("error").jsonObject.getValue("code").jsonPrimitive.content

    companion object {
        private const val MONGO_URI = "mongodb://localhost:27017"
        private const val DATABASE = "mentora_auth_users_test"
        private const val FIXED_ID = "identical-login-error"

        @JvmStatic @AfterAll fun cleanUp() = dropDatabase()
        private fun dropDatabase() = runBlocking {
            MongoClient.create(MONGO_URI).use { it.getDatabase(DATABASE).drop() }
        }
        private fun config() = AppConfig(
            mongoUri = MONGO_URI, mongoDatabaseName = DATABASE,
            jwtSigningSecret = "fixed-test-signing-secret-at-least-32-bytes", jwtIssuer = "mentora-backend-test",
            accessTokenTtlMinutes = 15, refreshTokenTtlDays = 30, mediaStorageRoot = "storage/media",
            corsAllowedOrigins = listOf("http://localhost:3000"), aiProviderApiKey = null,
            aiProviderModel = "test-model", logLevel = "DEBUG",
        )
    }
}
