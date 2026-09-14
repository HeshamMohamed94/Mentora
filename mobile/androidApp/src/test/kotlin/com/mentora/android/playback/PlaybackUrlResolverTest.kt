package com.mentora.android.playback

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.PlaybackSource
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

private val fixedExpiry = Instant.fromEpochSeconds(1_700_000_000)

private fun source(token: String) = PlaybackSource(
    url = "http://10.0.2.2:8080/api/v1/media/media-1/stream?token=$token",
    expiresAt = fixedExpiry,
)

/**
 * Task 13 C1 — [PlaybackUrlResolver]'s three branches per D85 Decision 2, exercised as a plain JVM
 * unit test (`testDebugUnitTest`) with a hand-built fake `refreshPlaybackUrl` lambda and a fabricated
 * [PlaybackSource] — the same lambda-constructor-seam convention as `CheckoutViewModelTest`/
 * `CourseDetailsViewModel`, no mocking framework. Drives [PlaybackUrlResolver.resolveCurrentUrl]
 * directly (never [PlaybackUrlResolver.resolveDataSpec], which needs a real `android.net.Uri`/
 * `androidx.media3.datasource.DataSpec` this module's plain JVM test surface cannot construct at all
 * — no Robolectric, per this module's standing convention; see that class's own kdoc for the full
 * reasoning) — [resolveCurrentUrl] carries the entire still-valid/refreshed/failed decision with no
 * Media3/Android type in its signature, so this genuinely exercises the real branch logic.
 */
class PlaybackUrlResolverTest {

    @Test
    fun stillValid_refreshLambdaReturnsNull_currentSourceIsReusedUnchanged() = runTest {
        var callCount = 0
        val initial = source("still-valid")
        val resolver = PlaybackUrlResolver(
            mediaId = "media-1",
            initialSource = initial,
            refreshPlaybackUrl = { mediaId, current ->
                callCount++
                assertEquals("media-1", mediaId)
                assertEquals(initial, current)
                null
            },
        )

        val resolvedUrl = resolver.resolveCurrentUrl()

        assertEquals(1, callCount)
        assertEquals(initial.url, resolvedUrl)
        assertEquals(initial, resolver.current)
    }

    @Test
    fun refreshed_successAdoptsTheFreshSource() = runTest {
        val initial = source("old")
        val fresh = source("fresh")
        var secondCallCurrent: PlaybackSource? = null
        var callCount = 0
        val resolver = PlaybackUrlResolver(
            mediaId = "media-1",
            initialSource = initial,
            refreshPlaybackUrl = { _, current ->
                callCount++
                if (callCount == 1) {
                    ApiResult.Success(fresh)
                } else {
                    // Reviewer finding (Task 13 C1 review): calling `resolveCurrentUrl()` a SECOND
                    // time on the SAME resolver instance — not a freshly-constructed second resolver
                    // seeded with `resolver.current` — is what actually proves `current` drives
                    // subsequent calls; a second resolver only re-proves the constructor argument
                    // drives the first call, which the assertion below on the first call already
                    // covers.
                    secondCallCurrent = current
                    null
                }
            },
        )

        val resolvedUrl = resolver.resolveCurrentUrl()
        assertEquals(fresh.url, resolvedUrl)
        assertEquals(fresh, resolver.current)

        resolver.resolveCurrentUrl()
        assertEquals(fresh, secondCallCurrent)
    }

    @Test
    fun failed_throwsIOException_andLeavesCurrentSourceUnchanged() = runTest {
        val initial = source("old")
        val resolver = PlaybackUrlResolver(
            mediaId = "media-1",
            initialSource = initial,
            refreshPlaybackUrl = { _, _ ->
                ApiResult.Failure(code = ApiErrorCode.AuthTokenExpired, message = "expired", fields = null, httpStatus = 401)
            },
        )

        try {
            resolver.resolveCurrentUrl()
            fail("expected an IOException from a failed refresh")
        } catch (error: IOException) {
            assertTrue(
                "expected the failure's ApiErrorCode in the thrown IOException's message, was: ${error.message}",
                error.message!!.contains("AUTH_TOKEN_EXPIRED"),
            )
        }

        assertEquals(
            "a failed refresh must never adopt a partial/invalid source",
            initial,
            resolver.current,
        )
    }
}
