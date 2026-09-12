package com.mentora.shared.domain.usecase.auth

import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.SessionUser
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class RecordingAuthRepository : AuthRepository {
    var registerCallCount = 0
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Unknown)

    override suspend fun register(email: String, password: String, name: String): ApiResult<SessionUser> {
        registerCallCount++
        return ApiResult.Success(SessionUser("1", email, name, com.mentora.shared.auth.Role.Student, null))
    }

    override suspend fun login(email: String, password: String): ApiResult<SessionUser> = throw NotImplementedError()
    override suspend fun logout(): ApiResult<Unit> = throw NotImplementedError()
    override suspend fun refreshSession(): ApiResult<Unit> = throw NotImplementedError()
    override suspend fun restoreSession(): AuthState = throw NotImplementedError()
}

class RegisterUseCaseTest {

    @Test
    fun `an invalid password fails locally with zero network calls`() = runTest {
        val repository = RecordingAuthRepository()
        val useCase = RegisterUseCase(repository)

        val result = useCase("student@example.com", "short", "Student")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.ValidationError, result.code)
        assertEquals(0, result.httpStatus, "a local-only failure must never look like a real network round trip")
        assertEquals(mapOf("password" to "WEAK"), result.fields)
        assertEquals(0, repository.registerCallCount)
    }

    @Test
    fun `valid input normalizes the email and forwards to the repository`() = runTest {
        val repository = RecordingAuthRepository()
        val useCase = RegisterUseCase(repository)

        val result = useCase("  Student@Example.com  ", "password1", "Student")

        assertTrue(result is ApiResult.Success)
        assertEquals(1, repository.registerCallCount)
        assertEquals("student@example.com", result.data.email)
    }
}
