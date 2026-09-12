package com.mentora.shared.domain.usecase.auth

import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.SessionUser
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.auth.AuthRepository
import com.mentora.shared.data.repository.user.UserRepository
import com.mentora.shared.domain.model.User
import com.mentora.shared.domain.usecase.user.SetLocaleUseCase
import com.mentora.shared.settings.AppLocale
import com.mentora.shared.settings.FakePreferenceStore
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

/** Records whether/how [SetLocaleUseCase.onRegister] called through to `updateProfile` — the
 * precedence behavior itself is covered exhaustively by `SetLocaleUseCaseTest`; these tests only
 * need to confirm `RegisterUseCase` actually wires the call. [shouldFail] simulates the PATCH
 * itself failing, to prove that never fails the overall registration result. */
private class RecordingUserRepository(private val shouldFail: Boolean = false) : UserRepository {
    var updateProfileCallCount = 0
        private set

    override suspend fun getProfile(): ApiResult<User> = throw NotImplementedError()
    override suspend fun updateProfile(name: String?, preferredLocale: String?): ApiResult<User> {
        updateProfileCallCount++
        return if (shouldFail) {
            ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
        } else {
            ApiResult.Success(User("1", "a@b.com", "A", com.mentora.shared.auth.Role.Student, null, preferredLocale, ""))
        }
    }
}

private fun testSetLocaleUseCase(authRepository: AuthRepository, userRepository: UserRepository = RecordingUserRepository()) =
    SetLocaleUseCase(FakePreferenceStore(initialLocale = AppLocale.English), userRepository, authRepository)

class RegisterUseCaseTest {

    @Test
    fun `an invalid password fails locally with zero network calls`() = runTest {
        val repository = RecordingAuthRepository()
        val useCase = RegisterUseCase(repository, testSetLocaleUseCase(repository))

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
        val useCase = RegisterUseCase(repository, testSetLocaleUseCase(repository))

        val result = useCase("  Student@Example.com  ", "password1", "Student")

        assertTrue(result is ApiResult.Success)
        assertEquals(1, repository.registerCallCount)
        assertEquals("student@example.com", result.data.email)
    }

    @Test
    fun `a successful registration seeds the account's preferredLocale from the current local value`() = runTest {
        val repository = RecordingAuthRepository()
        val userRepository = RecordingUserRepository()
        val useCase = RegisterUseCase(repository, testSetLocaleUseCase(repository, userRepository))

        useCase("student@example.com", "password1", "Student")

        assertEquals(1, userRepository.updateProfileCallCount, "onRegister must actually be invoked after a successful register")
    }

    @Test
    fun `a failure while seeding the account's locale never fails the registration result`() = runTest {
        val repository = RecordingAuthRepository()
        val userRepository = RecordingUserRepository(shouldFail = true)
        val useCase = RegisterUseCase(repository, testSetLocaleUseCase(repository, userRepository))

        val result = useCase("student@example.com", "password1", "Student")

        assertTrue(result is ApiResult.Success, "the account was created successfully - a locale-seed failure is not the caller's problem")
        assertEquals(1, userRepository.updateProfileCallCount)
    }
}
