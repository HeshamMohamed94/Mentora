package com.mentora.shared.domain.usecase.media

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.PlaybackSource
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Re-requests a fresh [PlaybackSource] once the current one's [PlaybackSource.expiresAt] is near or
 * past — a 5-minute-lived token (`MediaService.PLAYBACK_TTL_MINUTES`) is too short-lived to fetch
 * once per lesson view and never revisit, per `execution/PHASE_3_KMP_PLAN.md` Task 13 AC #5.
 *
 * Delegates the actual re-fetch (and the relative→absolute url resolution) to
 * [GetLessonPlaybackSourceUseCase] — never duplicates that logic here.
 *
 * Testable by construction, same approach as
 * [com.mentora.shared.domain.usecase.progress.ReportPlaybackPositionUseCase]'s throttle check: "now"
 * is never read via a hardcoded `Clock.System.now()` inline in the expiry check. Instead it comes
 * exclusively through the injected [clock] (a [kotlinx.datetime.Clock], defaulting to
 * [Clock.System] in production) — tests inject a fixed/fake [Clock] to deterministically land calls
 * before/at/after the expiry threshold with no real waiting. The near/past-expiry decision itself is
 * the standalone, directly-testable [isNearOrPastExpiry] function below.
 *
 * Returns `null` when [current] is still comfortably valid — nothing was re-requested, the caller
 * keeps using [current] unchanged. Returns the fresh [ApiResult] (success or failure) when a refresh
 * was actually attempted.
 */
class RefreshPlaybackUrlUseCase(
    private val getLessonPlaybackSource: GetLessonPlaybackSourceUseCase,
    private val clock: Clock = Clock.System,
    private val refreshBuffer: Duration = DEFAULT_REFRESH_BUFFER,
) {
    suspend operator fun invoke(mediaId: String, current: PlaybackSource): ApiResult<PlaybackSource>? {
        if (!isNearOrPastExpiry(current.expiresAt, clock.now(), refreshBuffer)) return null
        return getLessonPlaybackSource(mediaId)
    }

    companion object {
        /** How long before the real expiry a [PlaybackSource] is already considered "near" it —
         * comfortably inside the 5-minute TTL so a refresh has time to complete before the old token
         * actually stops working. */
        val DEFAULT_REFRESH_BUFFER: Duration = 30.seconds
    }
}

/**
 * `true` when [now] is within [buffer] of [expiresAt], or already at/past it. A standalone, pure
 * function (rather than logic buried inside [RefreshPlaybackUrlUseCase.invoke]) so the exact
 * threshold behavior is directly unit-testable without an [ApiResult]/repository round trip.
 */
fun isNearOrPastExpiry(expiresAt: Instant, now: Instant, buffer: Duration): Boolean =
    now >= expiresAt - buffer
