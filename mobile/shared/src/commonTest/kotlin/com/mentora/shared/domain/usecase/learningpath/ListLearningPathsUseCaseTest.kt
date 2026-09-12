package com.mentora.shared.domain.usecase.learningpath

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.HttpClientFactory
import com.mentora.shared.data.repository.learningpath.LearningPathRepositoryImpl
import com.mentora.shared.domain.model.LearningPath
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

class ListLearningPathsUseCaseTest {

    @Test
    fun `invoke maps a plain unpaginated array of LearningPath with no isFollowing field`() = runTest {
        val engine = MockEngine {
            respond(
                content = """{"data":[{"id":"lp1","title":"Backend Path","description":"d","courseCount":3}],"meta":{"requestId":"r1"}}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val useCase = ListLearningPathsUseCase(
            LearningPathRepositoryImpl(ApiClient(HttpClientFactory.create(engine, testEnvironment)), FakePreferenceStore()),
        )

        val result = useCase()

        require(result is ApiResult.Success)
        assertEquals(listOf(LearningPath("lp1", "Backend Path", "d", 3)), result.data)
    }
}
