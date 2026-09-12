package com.mentora.shared.domain.usecase.user

import com.mentora.shared.auth.Role
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.user.UserRepository
import com.mentora.shared.domain.model.User
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val testUser = User(
    id = "u1",
    email = "a@b.com",
    name = "Ada",
    role = Role.Student,
    avatarMediaId = null,
    preferredLocale = "en",
    createdAt = "2024-01-01T00:00:00Z",
)

/** The `SessionManager`-updating side effect lives in `UserRepositoryImpl` (see
 * `UserRepositoryImplTest`) — this fake proves [GetProfileUseCase] itself stays a thin,
 * repository-interface-only pass-through, per `execution/PHASE_3_KMP_PLAN.md` Task 6. */
private class FakeUserRepositoryForGetProfile(private val profileResult: ApiResult<User>) : UserRepository {
    var getProfileCallCount = 0
        private set

    override suspend fun getProfile(): ApiResult<User> {
        getProfileCallCount++
        return profileResult
    }

    override suspend fun updateProfile(name: String?, preferredLocale: String?): ApiResult<User> =
        throw NotImplementedError()
}

class GetProfileUseCaseTest {

    @Test
    fun `invoke delegates to the repository and returns its result unchanged`() = runTest {
        val repository = FakeUserRepositoryForGetProfile(ApiResult.Success(testUser))
        val useCase = GetProfileUseCase(repository)

        val result = useCase()

        assertEquals(ApiResult.Success(testUser), result)
        assertEquals(1, repository.getProfileCallCount)
    }
}
