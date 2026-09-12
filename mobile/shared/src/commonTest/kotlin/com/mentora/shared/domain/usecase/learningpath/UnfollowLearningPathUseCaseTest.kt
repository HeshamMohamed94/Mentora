package com.mentora.shared.domain.usecase.learningpath

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.data.repository.learningpath.LearningPathRepositoryImpl
import com.mentora.shared.settings.FakePreferenceStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

class UnfollowLearningPathUseCaseTest {

    @Test
    fun `invoke twice in a row is idempotent, both calls succeeding with false`() = runTest {
        var requestCount = 0
        val engine = MockEngine {
            requestCount++
            respond(
                content = """{"data":{"isFollowing":false},"meta":{"requestId":"r$requestCount"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val useCase = UnfollowLearningPathUseCase(
            LearningPathRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)), FakePreferenceStore()),
        )

        val first = useCase("lp1")
        val second = useCase("lp1")

        require(first is ApiResult.Success)
        require(second is ApiResult.Success)
        assertEquals(false, first.data)
        assertEquals(false, second.data)
        assertEquals(2, requestCount)
    }

    @Test
    fun `round trip follow then unfollow reflects true then false`() = runTest {
        var callCount = 0
        val engine = MockEngine {
            callCount++
            val isFollowing = callCount == 1
            respond(
                content = """{"data":{"isFollowing":$isFollowing},"meta":{"requestId":"r$callCount"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val repository = LearningPathRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)), FakePreferenceStore())
        val followUseCase = FollowLearningPathUseCase(repository)
        val unfollowUseCase = UnfollowLearningPathUseCase(repository)

        val followed = followUseCase("lp1")
        val unfollowed = unfollowUseCase("lp1")

        require(followed is ApiResult.Success)
        require(unfollowed is ApiResult.Success)
        assertEquals(true, followed.data)
        assertEquals(false, unfollowed.data)
    }
}
