package com.mentora.shared.data.repository.user

import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.AuthTokens
import com.mentora.shared.auth.FakeTokenStorage
import com.mentora.shared.auth.Role
import com.mentora.shared.auth.SessionManager
import com.mentora.shared.auth.SessionUser
import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.domain.model.User
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun OutgoingContent.readText(): String = when (this) {
    is OutgoingContent.ByteArrayContent -> bytes().decodeToString()
    else -> error("Unsupported OutgoingContent for test body capture: $this")
}

private fun repositoryAndSessionFor(
    engine: MockEngine,
    initialState: AuthState = AuthState.Unauthenticated,
): Pair<UserRepository, SessionManager> {
    val httpClient = HttpClientFactory.create(engine, testEnvironment)
    val apiClient = ApiClient(httpClient)
    val sessionManager = SessionManager(FakeTokenStorage(AuthTokens("a", "r"))) { throw NotImplementedError() }
    sessionManager.setState(initialState)
    return UserRepositoryImpl(apiClient, sessionManager) to sessionManager
}

class UserRepositoryImplTest {

    @Test
    fun `getProfile maps every field when all are present`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"id":"u1","email":"a@b.com","name":"Ada","role":"student","avatarMediaId":"m1","preferredLocale":"ar","createdAt":"2024-01-01T00:00:00Z"},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val (repository, _) = repositoryAndSessionFor(engine)

        val result = repository.getProfile()

        require(result is ApiResult.Success)
        assertEquals(
            User(
                id = "u1",
                email = "a@b.com",
                name = "Ada",
                role = Role.Student,
                avatarMediaId = "m1",
                preferredLocale = "ar",
                createdAt = "2024-01-01T00:00:00Z",
            ),
            result.data,
        )
    }

    @Test
    fun `getProfile maps omitted-optional avatarMediaId and preferredLocale to null`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"id":"u1","email":"a@b.com","name":"Ada","role":"student","createdAt":"2024-01-01T00:00:00Z"},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val (repository, _) = repositoryAndSessionFor(engine)

        val result = repository.getProfile()

        require(result is ApiResult.Success)
        assertEquals(null, result.data.avatarMediaId)
        assertEquals(null, result.data.preferredLocale)
    }

    @Test
    fun `getProfile fills in a null session user after a cold-start restore`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"id":"u1","email":"a@b.com","name":"Ada","role":"student","createdAt":"2024-01-01T00:00:00Z"},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val (repository, sessionManager) = repositoryAndSessionFor(
            engine,
            initialState = AuthState.Authenticated(user = null),
        )

        val result = repository.getProfile()

        require(result is ApiResult.Success)
        val state = sessionManager.authState.value
        require(state is AuthState.Authenticated)
        assertEquals(SessionUser("u1", "a@b.com", "Ada", Role.Student, null), state.user)
    }

    @Test
    fun `getProfile does not resurrect a session that has since signed out`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"id":"u1","email":"a@b.com","name":"Ada","role":"student","createdAt":"2024-01-01T00:00:00Z"},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val (repository, sessionManager) = repositoryAndSessionFor(engine, initialState = AuthState.Unauthenticated)

        repository.getProfile()

        assertEquals(AuthState.Unauthenticated, sessionManager.authState.value)
    }

    @Test
    fun `updateProfile PATCH body contains only the name field when only name changed`() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = request.body.readText()
            respond(
                content = """{"data":{"id":"u1","email":"a@b.com","name":"New Name","role":"student","preferredLocale":"en","createdAt":"2024-01-01T00:00:00Z"},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val (repository, _) = repositoryAndSessionFor(engine)

        repository.updateProfile(name = "New Name")

        assertNotNull(capturedBody)
        assertEquals("""{"name":"New Name"}""", capturedBody)
    }

    @Test
    fun `updateProfile PATCH body contains only the preferredLocale field when only locale changed`() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            capturedBody = request.body.readText()
            respond(
                content = """{"data":{"id":"u1","email":"a@b.com","name":"Ada","role":"student","preferredLocale":"ar","createdAt":"2024-01-01T00:00:00Z"},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val (repository, _) = repositoryAndSessionFor(engine)

        repository.updateProfile(preferredLocale = "ar")

        assertNotNull(capturedBody)
        assertEquals("""{"preferredLocale":"ar"}""", capturedBody)
    }

    @Test
    fun `updateProfile keeps the session user in sync with the newly returned profile`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"id":"u1","email":"a@b.com","name":"New Name","role":"student","preferredLocale":"ar","createdAt":"2024-01-01T00:00:00Z"},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val existingUser = SessionUser("u1", "a@b.com", "Ada", Role.Student, "en")
        val (repository, sessionManager) = repositoryAndSessionFor(
            engine,
            initialState = AuthState.Authenticated(existingUser),
        )

        repository.updateProfile(name = "New Name", preferredLocale = "ar")

        val state = sessionManager.authState.value
        require(state is AuthState.Authenticated)
        assertEquals(SessionUser("u1", "a@b.com", "New Name", Role.Student, "ar"), state.user)
    }
}
