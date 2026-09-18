package com.mentora.shared.domain.usecase.user

import com.mentora.shared.auth.Role
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.user.UserRepository
import com.mentora.shared.domain.model.User
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

private val testUser = User(
    id = "u1",
    email = "a@b.com",
    name = "New Name",
    role = Role.Student,
    avatarMediaId = null,
    preferredLocale = null,
    createdAt = "2024-01-01T00:00:00Z",
)

private class FakeUserRepositoryForUpdateProfile : UserRepository {
    var lastName: String? = null
        private set
    var lastPreferredLocale: String? = null
        private set
    var callCount = 0
        private set

    override suspend fun getProfile(): ApiResult<User> = throw NotImplementedError()

    override suspend fun updateProfile(name: String?, preferredLocale: String?): ApiResult<User> {
        callCount++
        lastName = name
        lastPreferredLocale = preferredLocale
        return ApiResult.Success(testUser)
    }
}

class UpdateProfileUseCaseTest {

    @Test
    fun `blank name fails local validation with REQUIRED and no network call`() = runTest {
        val repository = FakeUserRepositoryForUpdateProfile()
        val useCase = UpdateProfileUseCase(repository)

        val result = useCase("   ")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.ValidationError, result.code)
        assertEquals("REQUIRED", result.fields?.get("name"))
        assertEquals(0, repository.callCount)
    }

    @Test
    fun `a name over 120 chars fails local validation with TOO_LONG and no network call`() = runTest {
        val repository = FakeUserRepositoryForUpdateProfile()
        val useCase = UpdateProfileUseCase(repository)

        val result = useCase("a".repeat(121))

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.ValidationError, result.code)
        assertEquals("TOO_LONG", result.fields?.get("name"))
        assertEquals(0, repository.callCount)
    }

    @Test
    fun `a valid trimmed name is sent with no preferredLocale`() = runTest {
        val repository = FakeUserRepositoryForUpdateProfile()
        val useCase = UpdateProfileUseCase(repository)

        val result = useCase("  New Name  ")

        assertEquals(ApiResult.Success(testUser), result)
        assertEquals("New Name", repository.lastName)
        assertNull(repository.lastPreferredLocale)
        assertEquals(1, repository.callCount)
    }
}
