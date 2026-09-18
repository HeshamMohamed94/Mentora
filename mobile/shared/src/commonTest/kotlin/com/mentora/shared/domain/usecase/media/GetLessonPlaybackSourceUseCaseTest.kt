package com.mentora.shared.domain.usecase.media

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.PlaybackSource
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetLessonPlaybackSourceUseCaseTest {

    @Test
    fun `resolves the repository's relative url to an absolute one against the environment base url`() = runTest {
        val repository = FakeMediaRepository(
            result = ApiResult.Success(
                PlaybackSource(url = "/api/v1/media/m1/stream?token=abc", expiresAt = Instant.parse("2026-09-12T10:05:00Z")),
            ),
        )
        val useCase = GetLessonPlaybackSourceUseCase(repository, ApiEnvironment.custom("http://10.0.2.2:8080"))

        val result = useCase("m1")

        require(result is ApiResult.Success)
        assertEquals("http://10.0.2.2:8080/api/v1/media/m1/stream?token=abc", result.data.url)
        assertEquals(Instant.parse("2026-09-12T10:05:00Z"), result.data.expiresAt)
        assertEquals("m1", repository.lastRequestedMediaId)
    }

    @Test
    fun `a repository failure passes through unchanged with no url resolution attempted`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.ForbiddenNotEnrolled, "Not enrolled.", null, 403)
        val repository = FakeMediaRepository(result = failure)
        val useCase = GetLessonPlaybackSourceUseCase(repository, ApiEnvironment.custom("http://10.0.2.2:8080"))

        val result = useCase("m1")

        assertEquals(failure, result)
    }
}
