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

class FollowLearningPathUseCaseTest {

    @Test
    fun `invoke twice in a row is idempotent, both calls succeeding with true`() = runTest {
        var requestCount = 0
        val engine = MockEngine {
            requestCount++
            respond(
                content = """{"data":{"isFollowing":true},"meta":{"requestId":"r$requestCount"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val useCase = FollowLearningPathUseCase(
            LearningPathRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)), FakePreferenceStore()),
        )

        val first = useCase("lp1")
        val second = useCase("lp1")

        require(first is ApiResult.Success)
        require(second is ApiResult.Success)
        assertEquals(true, first.data)
        assertEquals(true, second.data)
        assertEquals(2, requestCount)
    }
}
