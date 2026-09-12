package com.mentora.shared.domain.usecase.auth

import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.SessionUser
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.auth.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class FakeAuthRepository(private val restoredState: AuthState) : AuthRepository {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    override val authState: StateFlow<AuthState> = _authState

    override suspend fun register(email: String, password: String, name: String): ApiResult<SessionUser> =
        throw NotImplementedError()

    override suspend fun login(email: String, password: String): ApiResult<SessionUser> = throw NotImplementedError()

    override suspend fun logout(): ApiResult<Unit> = throw NotImplementedError()

    override suspend fun refreshSession(): ApiResult<Unit> = throw NotImplementedError()

    override suspend fun restoreSession(): AuthState {
        _authState.value = restoredState
        return restoredState
    }
}

class RestoreSessionUseCaseTest {

    @Test
    fun `token present yields Authenticated`() = runTest {
        val useCase = RestoreSessionUseCase(
            FakeAuthRepository(AuthState.Authenticated(SessionUser("1", "", "", com.mentora.shared.auth.Role.Student, null))),
        )

        val state = useCase()

        assertEquals(true, state is AuthState.Authenticated)
    }

    @Test
    fun `no token yields Unauthenticated`() = runTest {
        val useCase = RestoreSessionUseCase(FakeAuthRepository(AuthState.Unauthenticated))

        val state = useCase()

        assertEquals(AuthState.Unauthenticated, state)
    }
}
