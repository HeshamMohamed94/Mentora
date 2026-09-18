package com.mentora.shared.data.repository.auth

import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.AuthTokens
import com.mentora.shared.auth.FakeTokenStorage
import com.mentora.shared.auth.Role
import com.mentora.shared.auth.SessionManager
import com.mentora.shared.auth.SessionUser
import com.mentora.shared.auth.installAuthInterception
import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.data.network.dto.RefreshRequestDto
import com.mentora.shared.data.network.dto.RefreshResponseDto
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

private fun repositoryFor(engine: MockEngine, tokenStorage: FakeTokenStorage): AuthRepository {
    val httpClient = HttpClientFactory.create(engine, testEnvironment)
    val apiClient = ApiClient(httpClient)
    val sessionManager = SessionManager(tokenStorage) { refreshToken ->
        when (val result = apiClient.post<RefreshRequestDto, RefreshResponseDto>(
            "/api/v1/auth/refresh",
            RefreshRequestDto(refreshToken),
        )) {
            is ApiResult.Success -> ApiResult.Success(AuthTokens(result.data.accessToken, result.data.refreshToken))
            is ApiResult.Failure -> result
        }
    }
    httpClient.installAuthInterception(sessionManager)
    return AuthRepositoryImpl(apiClient, sessionManager, tokenStorage)
}

class AuthRepositoryImplTest {

    @Test
    fun `register success persists tokens and publishes Authenticated`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"accessToken":"a1","refreshToken":"r1","user":{"id":"u1","email":"a@b.com","name":"Ada","role":"student"}},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.Created,
                headers = jsonHeaders(),
            )
        }
        val tokenStorage = FakeTokenStorage()
        val repository = repositoryFor(engine, tokenStorage)

        val result = repository.register("A@B.com", "password1", "Ada")

        require(result is ApiResult.Success)
        assertEquals(SessionUser("u1", "a@b.com", "Ada", Role.Student, null), result.data)
        assertEquals(AuthTokens("a1", "r1"), tokenStorage.readTokens())
        assertEquals(AuthState.Authenticated(result.data), repository.authState.value)
    }

    @Test
    fun `login success persists tokens and publishes Authenticated`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"accessToken":"a2","refreshToken":"r2","user":{"id":"u1","email":"a@b.com","name":"Ada","role":"student"}},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val tokenStorage = FakeTokenStorage()
        val repository = repositoryFor(engine, tokenStorage)

        val result = repository.login("a@b.com", "password1")

        require(result is ApiResult.Success)
        assertEquals(AuthTokens("a2", "r2"), tokenStorage.readTokens())
        assertEquals(AuthState.Authenticated(result.data), repository.authState.value)
    }

    @Test
    fun `login with AUTH_INVALID_CREDENTIALS surfaces one generic failure with no field hint`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"AUTH_INVALID_CREDENTIALS","message":"Invalid email or password."},"meta":{"requestId":"r"}}""",
                status = HttpStatusCode.Unauthorized,
                headers = jsonHeaders(),
            )
        }
        val repository = repositoryFor(engine, FakeTokenStorage())

        val result = repository.login("nobody@example.com", "wrong-password")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.AuthInvalidCredentials, result.code)
        assertEquals(null, result.fields, "the backend gives no field-specific hint and neither must the client")
    }

    @Test
    fun `logout clears TokenStorage and publishes Unauthenticated`() = runTest {
        val engine = MockEngine {
            respond(content = "{\"data\":{},\"meta\":{\"requestId\":\"r\"}}", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val tokenStorage = FakeTokenStorage(AuthTokens("a", "r"))
        val repository = repositoryFor(engine, tokenStorage)

        val result = repository.logout()

        assertTrue(result is ApiResult.Success)
        assertNull(tokenStorage.readTokens())
        assertEquals(AuthState.Unauthenticated, repository.authState.value)
    }

    @Test
    fun `restoreSession is Authenticated when a token is present`() = runTest {
        val tokenStorage = FakeTokenStorage(AuthTokens("a", "r"))
        val repository = repositoryFor(MockEngine { respond("", HttpStatusCode.OK) }, tokenStorage)

        val state = repository.restoreSession()

        assertTrue(state is AuthState.Authenticated)
        // user is honestly null on a bare-token restore (TokenStorage carries no identity) rather
        // than a fabricated placeholder a caller could mistake for a real profile.
        assertEquals(null, (state as AuthState.Authenticated).user)
        assertEquals(state, repository.authState.value)
    }

    @Test
    fun `restoreSession is Unauthenticated when no token is present`() = runTest {
        val repository = repositoryFor(MockEngine { respond("", HttpStatusCode.OK) }, FakeTokenStorage())

        val state = repository.restoreSession()

        assertEquals(AuthState.Unauthenticated, state)
        assertEquals(AuthState.Unauthenticated, repository.authState.value)
    }
}
