package com.mentora.shared.domain.usecase.learningpath

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiErrorCode
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
import kotlin.test.assertNull

private val testEnvironment = ApiEnvironment.custom("http://localhost")

private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())

class GetLearningPathDetailUseCaseTest {

    @Test
    fun `invoke maps a guest response with null progressPercent and false isFollowing`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"id":"lp1","title":"t","description":"d","courses":[],"progressPercent":null,"isFollowing":false},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val useCase = GetLearningPathDetailUseCase(
            LearningPathRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)), FakePreferenceStore()),
        )

        val result = useCase("lp1")

        require(result is ApiResult.Success)
        assertNull(result.data.progressPercent)
        assertEquals(false, result.data.isFollowing)
    }

    @Test
    fun `invoke maps an authenticated-student response with a real progressPercent and isFollowing true`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":{"id":"lp1","title":"t","description":"d","courses":[],"progressPercent":75,"isFollowing":true},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val useCase = GetLearningPathDetailUseCase(
            LearningPathRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)), FakePreferenceStore()),
        )

        val result = useCase("lp1")

        require(result is ApiResult.Success)
        assertEquals(75, result.data.progressPercent)
        assertEquals(true, result.data.isFollowing)
    }

    @Test
    fun `invoke forwards a nonexistent id as an ordinary 404 LEARNING_PATH_NOT_FOUND failure`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"error":{"code":"LEARNING_PATH_NOT_FOUND","message":"The learning path was not found."},"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.NotFound,
                headers = jsonHeaders(),
            )
        }
        val useCase = GetLearningPathDetailUseCase(
            LearningPathRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)), FakePreferenceStore()),
        )

        val result = useCase("missing")

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.LearningPathNotFound, result.code)
    }
}
