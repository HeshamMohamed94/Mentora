package com.mentora.shared.domain.usecase.media

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.PlaybackSource
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}

private val environment = ApiEnvironment.custom("http://10.0.2.2:8080")

class RefreshPlaybackUrlUseCaseTest {

    // ---- pure isNearOrPastExpiry table ----

    @Test
    fun `well before expiry is not near`() {
        val expiresAt = Instant.parse("2026-09-12T10:05:00Z")
        val now = expiresAt - 4.minutes
        assertFalse(isNearOrPastExpiry(expiresAt, now, buffer = 30.seconds))
    }

    @Test
    fun `exactly at the buffer boundary counts as near`() {
        val expiresAt = Instant.parse("2026-09-12T10:05:00Z")
        val now = expiresAt - 30.seconds
        assertTrue(isNearOrPastExpiry(expiresAt, now, buffer = 30.seconds))
    }

    @Test
    fun `inside the buffer counts as near`() {
        val expiresAt = Instant.parse("2026-09-12T10:05:00Z")
        val now = expiresAt - 10.seconds
        assertTrue(isNearOrPastExpiry(expiresAt, now, buffer = 30.seconds))
    }

    @Test
    fun `already past expiry counts as near`() {
        val expiresAt = Instant.parse("2026-09-12T10:05:00Z")
        val now = expiresAt + 1.minutes
        assertTrue(isNearOrPastExpiry(expiresAt, now, buffer = 30.seconds))
    }

    // ---- use case behavior ----

    @Test
    fun `a source well before expiry is not refreshed, repository never called`() = runTest {
        val expiresAt = Instant.parse("2026-09-12T10:05:00Z")
        val current = PlaybackSource(url = "http://10.0.2.2:8080/api/v1/media/m1/stream?token=old", expiresAt = expiresAt)
        val repository = FakeMediaRepository(result = ApiResult.Success(current))
        val getSource = GetLessonPlaybackSourceUseCase(repository, environment)
        val useCase = RefreshPlaybackUrlUseCase(getSource, clock = FixedClock(expiresAt - 4.minutes), refreshBuffer = 30.seconds)

        val result = useCase("m1", current)

        assertNull(result)
        assertNull(repository.lastRequestedMediaId)
    }

    @Test
    fun `a source near or past expiry is refreshed via a fresh network call`() = runTest {
        val expiresAt = Instant.parse("2026-09-12T10:05:00Z")
        val current = PlaybackSource(url = "http://10.0.2.2:8080/api/v1/media/m1/stream?token=old", expiresAt = expiresAt)
        val fresh = PlaybackSource(url = "/api/v1/media/m1/stream?token=new", expiresAt = expiresAt + 5.minutes)
        val repository = FakeMediaRepository(result = ApiResult.Success(fresh))
        val getSource = GetLessonPlaybackSourceUseCase(repository, environment)
        val useCase = RefreshPlaybackUrlUseCase(getSource, clock = FixedClock(expiresAt - 10.seconds), refreshBuffer = 30.seconds)

        val result = useCase("m1", current)

        require(result is ApiResult.Success)
        assertEquals("http://10.0.2.2:8080/api/v1/media/m1/stream?token=new", result.data.url)
        assertEquals("m1", repository.lastRequestedMediaId)
    }

    @Test
    fun `a refresh failure surfaces as an ordinary typed Failure`() = runTest {
        val expiresAt = Instant.parse("2026-09-12T10:05:00Z")
        val current = PlaybackSource(url = "http://10.0.2.2:8080/api/v1/media/m1/stream?token=old", expiresAt = expiresAt)
        val failure = ApiResult.Failure(
            code = com.mentora.shared.data.network.ApiErrorCode.ForbiddenNotEnrolled,
            message = "Not enrolled.",
            fields = null,
            httpStatus = 403,
        )
        val repository = FakeMediaRepository(result = failure)
        val getSource = GetLessonPlaybackSourceUseCase(repository, environment)
        val useCase = RefreshPlaybackUrlUseCase(getSource, clock = FixedClock(expiresAt + 1.minutes), refreshBuffer = 30.seconds)

        val result = useCase("m1", current)

        assertEquals(failure, result)
    }
}
