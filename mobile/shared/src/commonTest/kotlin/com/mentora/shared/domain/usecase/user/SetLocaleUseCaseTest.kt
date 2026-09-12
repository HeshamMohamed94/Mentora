package com.mentora.shared.domain.usecase.user

import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.Role
import com.mentora.shared.auth.SessionUser
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.auth.AuthRepository
import com.mentora.shared.data.repository.user.UserRepository
import com.mentora.shared.domain.model.User
import com.mentora.shared.settings.AppLocale
import com.mentora.shared.settings.FakePreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val testUser = User(
    id = "u1", email = "a@b.com", name = "Ada", role = Role.Student,
    avatarMediaId = null, preferredLocale = "ar", createdAt = "2024-01-01T00:00:00Z",
)

private class FakeAuthRepository(initialState: AuthState) : AuthRepository {
    private val _authState = MutableStateFlow(initialState)
    override val authState: StateFlow<AuthState> = _authState

    override suspend fun register(email: String, password: String, name: String): ApiResult<SessionUser> =
        throw NotImplementedError()

    override suspend fun login(email: String, password: String): ApiResult<SessionUser> = throw NotImplementedError()

    override suspend fun logout(): ApiResult<Unit> = throw NotImplementedError()

    override suspend fun refreshSession(): ApiResult<Unit> = throw NotImplementedError()

    override suspend fun restoreSession(): AuthState = throw NotImplementedError()
}

private class FakeUserRepositoryForSetLocale : UserRepository {
    var updateProfileCallCount = 0
        private set
    var lastPreferredLocale: String? = null
        private set

    override suspend fun getProfile(): ApiResult<User> = throw NotImplementedError()

    override suspend fun updateProfile(name: String?, preferredLocale: String?): ApiResult<User> {
        updateProfileCallCount++
        lastPreferredLocale = preferredLocale
        return ApiResult.Success(testUser)
    }
}

class SetLocaleUseCaseTest {

    @Test
    fun `invoke writes PreferenceStore immediately for a guest, with no backend call`() = runTest {
        val preferenceStore = FakePreferenceStore(initialLocale = AppLocale.English)
        val userRepository = FakeUserRepositoryForSetLocale()
        val useCase = SetLocaleUseCase(
            preferenceStore,
            userRepository,
            FakeAuthRepository(AuthState.Unauthenticated),
        )

        val result = useCase(AppLocale.Arabic)

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals(AppLocale.Arabic, preferenceStore.locale.value)
        assertEquals(0, userRepository.updateProfileCallCount)
    }

    @Test
    fun `invoke writes PreferenceStore AND PATCHes the backend when authenticated`() = runTest {
        val preferenceStore = FakePreferenceStore(initialLocale = AppLocale.English)
        val userRepository = FakeUserRepositoryForSetLocale()
        val authState = AuthState.Authenticated(SessionUser("u1", "a@b.com", "Ada", Role.Student, "en"))
        val useCase = SetLocaleUseCase(preferenceStore, userRepository, FakeAuthRepository(authState))

        val result = useCase(AppLocale.Arabic)

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals(AppLocale.Arabic, preferenceStore.locale.value)
        assertEquals(1, userRepository.updateProfileCallCount)
        assertEquals("ar", userRepository.lastPreferredLocale)
    }

    @Test
    fun `invoke never alters AuthState`() = runTest {
        val preferenceStore = FakePreferenceStore(initialLocale = AppLocale.English)
        val userRepository = FakeUserRepositoryForSetLocale()
        val authState = AuthState.Authenticated(SessionUser("u1", "a@b.com", "Ada", Role.Student, "en"))
        val authRepository = FakeAuthRepository(authState)
        val useCase = SetLocaleUseCase(preferenceStore, userRepository, authRepository)

        useCase(AppLocale.Arabic)

        assertEquals(authState, authRepository.authState.value)
    }

    @Test
    fun `onLogin overwrites the local value with the account's own preferredLocale`() {
        val preferenceStore = FakePreferenceStore(initialLocale = AppLocale.Arabic)
        val useCase = SetLocaleUseCase(preferenceStore, FakeUserRepositoryForSetLocale(), FakeAuthRepository(AuthState.Unknown))

        useCase.onLogin(accountPreferredLocale = "en")

        assertEquals(AppLocale.English, preferenceStore.locale.value)
    }

    @Test
    fun `onLogin leaves the local value untouched when the account has no preferredLocale`() {
        val preferenceStore = FakePreferenceStore(initialLocale = AppLocale.Arabic)
        val useCase = SetLocaleUseCase(preferenceStore, FakeUserRepositoryForSetLocale(), FakeAuthRepository(AuthState.Unknown))

        useCase.onLogin(accountPreferredLocale = null)

        assertEquals(AppLocale.Arabic, preferenceStore.locale.value)
    }

    @Test
    fun `onRegister sends the current local value up to seed the new account, no local overwrite`() = runTest {
        val preferenceStore = FakePreferenceStore(initialLocale = AppLocale.Arabic)
        val userRepository = FakeUserRepositoryForSetLocale()
        val useCase = SetLocaleUseCase(preferenceStore, userRepository, FakeAuthRepository(AuthState.Unknown))

        val result = useCase.onRegister()

        assertEquals(ApiResult.Success(Unit), result)
        assertEquals(1, userRepository.updateProfileCallCount)
        assertEquals("ar", userRepository.lastPreferredLocale)
        assertEquals(AppLocale.Arabic, preferenceStore.locale.value)
    }

    @Test
    fun `login-overwrites-local and register-seeds-account are distinct, opposite-direction flows`() = runTest {
        // Scenario 1: LOGIN — the local device had English selected before sign-in, but the
        // existing account's own value is Arabic. Login wins: local becomes Arabic.
        val loginPreferenceStore = FakePreferenceStore(initialLocale = AppLocale.English)
        val loginUseCase = SetLocaleUseCase(
            loginPreferenceStore, FakeUserRepositoryForSetLocale(), FakeAuthRepository(AuthState.Unknown),
        )
        loginUseCase.onLogin(accountPreferredLocale = "ar")
        assertEquals(AppLocale.Arabic, loginPreferenceStore.locale.value)

        // Scenario 2: REGISTER — the local device had Arabic selected pre-signup, and the
        // brand-new account has nothing yet. Register seeds the account: the server receives "ar",
        // and the local value is left exactly as it was (nothing to overwrite it with).
        val registerPreferenceStore = FakePreferenceStore(initialLocale = AppLocale.Arabic)
        val registerUserRepository = FakeUserRepositoryForSetLocale()
        val registerUseCase = SetLocaleUseCase(
            registerPreferenceStore, registerUserRepository, FakeAuthRepository(AuthState.Unknown),
        )
        registerUseCase.onRegister()
        assertEquals("ar", registerUserRepository.lastPreferredLocale)
        assertEquals(AppLocale.Arabic, registerPreferenceStore.locale.value)
    }
}
