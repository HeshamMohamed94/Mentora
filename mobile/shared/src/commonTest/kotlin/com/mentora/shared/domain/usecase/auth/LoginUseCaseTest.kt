package com.mentora.shared.domain.usecase.auth

import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.Role
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

private class LoginRecordingAuthRepository(private val loginResult: ApiResult<SessionUser>) : AuthRepository {
    var loginCallCount = 0
    var lastEmail: String? = null
    override val authState: StateFlow<AuthState> = MutableStateFlow(AuthState.Unknown)

    override suspend fun register(email: String, password: String, name: String): ApiResult<SessionUser> =
        throw NotImplementedError()

    override suspend fun login(email: String, password: String): ApiResult<SessionUser> {
        loginCallCount++
        lastEmail = email
        return loginResult
    }

    override suspend fun logout(): ApiResult<Unit> = throw NotImplementedError()
    override suspend fun refreshSession(): ApiResult<Unit> = throw NotImplementedError()
    override suspend fun restoreSession(): AuthState = throw NotImplementedError()
}

private class NoopUserRepository : UserRepository {
    override suspend fun getProfile(): ApiResult<User> = throw NotImplementedError()
    override suspend fun updateProfile(name: String?, preferredLocale: String?): ApiResult<User> =
        ApiResult.Success(User("1", "a@b.com", "A", Role.Student, null, preferredLocale, ""))
}

class LoginUseCaseTest {

    @Test
    fun `normalizes the email and forwards to the repository`() = runTest {
        val repository = LoginRecordingAuthRepository(
            ApiResult.Success(SessionUser("1", "student@example.com", "Student", Role.Student, null)),
        )
        val preferenceStore = FakePreferenceStore(initialLocale = AppLocale.English)
        val useCase = LoginUseCase(repository, SetLocaleUseCase(preferenceStore, NoopUserRepository(), repository))

        val result = useCase("  Student@Example.com  ", "password1")

        assertTrue(result is ApiResult.Success)
        assertEquals(1, repository.loginCallCount)
        assertEquals("student@example.com", repository.lastEmail)
    }

    @Test
    fun `a successful login applies the account's preferredLocale to the local PreferenceStore`() = runTest {
        val repository = LoginRecordingAuthRepository(
            ApiResult.Success(SessionUser("1", "a@b.com", "A", Role.Student, preferredLocale = "ar")),
        )
        val preferenceStore = FakePreferenceStore(initialLocale = AppLocale.English)
        val useCase = LoginUseCase(repository, SetLocaleUseCase(preferenceStore, NoopUserRepository(), repository))

        useCase("a@b.com", "password1")

        assertEquals(AppLocale.Arabic, preferenceStore.locale.value, "login overwrites local, per Task 6's precedence rule")
    }

    @Test
    fun `a failed login never touches the local PreferenceStore`() = runTest {
        val repository = LoginRecordingAuthRepository(
            ApiResult.Failure(ApiErrorCode.AuthInvalidCredentials, "Invalid credentials.", null, 401),
        )
        val preferenceStore = FakePreferenceStore(initialLocale = AppLocale.English)
        val useCase = LoginUseCase(repository, SetLocaleUseCase(preferenceStore, NoopUserRepository(), repository))

        val result = useCase("a@b.com", "wrong-password")

        assertTrue(result is ApiResult.Failure)
        assertEquals(AppLocale.English, preferenceStore.locale.value)
    }
}
