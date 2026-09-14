@file:OptIn(UnstableApi::class)

package com.mentora.android.playback

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.PlaybackSource
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * `execution/DECISIONS_LOG.md` D85 Decision 2's implementation of the 5-minute playback-URL TTL:
 * rewrites the stream token on every NEW HTTP connection ExoPlayer opens for this lesson (a seek
 * outside the buffer, a re-buffer, a network-blip retry, a resume after a long pause) — never a
 * periodic re-prepare timer, which would force a visible buffer discard on every refresh. See that
 * decision's own rationale for why [ResolvingDataSource.Resolver.resolveDataSpec] — "whenever a new
 * request is about to be opened" — is exactly the right hook, and why a `ResolvingDataSource` beats
 * the alternative (a coroutine timer + `setMediaItem`/`prepare()` re-prepare).
 *
 * [resolveDataSpec] itself is a deliberately thin wrapper — the ONLY place in this class that touches
 * a real Android/Media3 type ([DataSpec]/[Uri]) — over [resolveCurrentUrl], which holds the entire
 * decision (the branch logic D85's own verification plan calls out as JVM-unit-testable). That split
 * exists because this module's plain `testDebugUnitTest` JVM surface has no real Android runtime
 * behind `Uri`/`DataSpec` (no Robolectric, per this module's standing convention — see
 * `AndroidTokenStorageInstrumentedTest`'s own kdoc): `DataSpec`'s constructor itself
 * `Assertions.checkNotNull`s its `Uri`, so there is no way to construct even a placeholder `DataSpec`
 * on that JVM at all, mocking framework or not. [resolveCurrentUrl] needs no such type — testing it
 * directly (this class's own JVM unit tests, `PlaybackUrlResolverTest`) genuinely exercises the real
 * branch logic; [resolveDataSpec]'s thin Media3 plumbing is proven for real by the instrumented
 * playback test instead (D85's verification plan point (a) vs the temporary-expired-source probe).
 *
 * [refreshPlaybackUrl] mirrors [com.mentora.shared.domain.usecase.media.RefreshPlaybackUrlUseCase]'s
 * own `invoke(mediaId, current)` contract exactly, including its three-way return, handled below:
 * `null` — [current] is still comfortably valid (the use case's own near/past-expiry check, a 30 s
 * buffer inside the real 5-minute TTL, already decided this), reuse it unchanged; [ApiResult.Success]
 * — adopt the fresh [PlaybackSource]; [ApiResult.Failure] — surfaced as a thrown [IOException] (never
 * swallowed), which Media3 turns into a [androidx.media3.common.PlaybackException] that
 * `MediaPlaybackController` maps to [com.mentora.shared.playback.PlaybackState.Error]. [current]'s
 * URL is what is actually used on every resolution — never the incoming [DataSpec]'s own `uri` —
 * since that incoming value is whatever the original [androidx.media3.common.MediaItem] was built
 * with, which may already be stale by the time a later connection opens.
 *
 * [resolveDataSpec] is called via [runBlocking] on ExoPlayer's own loader thread — a background
 * thread built for blocking IO, never the main thread `MediaPlaybackController` otherwise confines
 * itself to — which is correct and intentional here, not an oversight (see Decision 2's own tradeoff
 * analysis).
 *
 * [current] is exposed `internal` purely so this class's own JVM unit tests can assert on it
 * directly. Never read by production code outside this file.
 *
 * **Bounded, not unbounded — reviewer finding, Task 13 C1 review.** [resolveDataSpec] wraps
 * [resolveCurrentUrl] in a 15s [withTimeout]. Without it, a permanent failure (e.g. the account lost
 * its enrollment mid-session) still costs media3's own retry policy (`DefaultLoadErrorHandlingPolicy`:
 * 3 retries) layered on top of `ApiClient`'s own GET retry-on-`IOException` (2 retries) and a 30s
 * per-request timeout each — worst case minutes of [PlaybackState.Buffering] with a blocked loader
 * thread before any error/retry affordance ever surfaces. A [TimeoutCancellationException] is
 * re-thrown as an [IOException] so it flows through the exact same path as a real refresh failure.
 *
 * **Accepted, undocumented-elsewhere race — reviewer finding, Task 13 C1 review.** Within one
 * `ProgressiveMediaPeriod`, media3's `Loader` serializes loads onto a single-thread executor, so
 * [resolveDataSpec] cannot be called concurrently for the SAME period. ACROSS periods (e.g. a
 * re-prepare after an error creates a new period against this same resolver instance) two overlapping
 * resolutions ARE possible, and [current]'s read-modify-write at [resolveCurrentUrl] is not atomic
 * (`@Volatile` gives visibility, not atomicity) — so both could see a stale [current], both refresh,
 * and the loser's write clobbers the winner's. This is accepted, not fixed with a `Mutex`: the
 * backend mints independent, equally-valid 5-minute tokens per refresh, so the only real cost of the
 * race is one extra, harmless round trip — never a correctness bug (both resolutions still succeed
 * with an equally-fresh source).
 */
class PlaybackUrlResolver(
    private val mediaId: String,
    initialSource: PlaybackSource,
    private val refreshPlaybackUrl: suspend (mediaId: String, current: PlaybackSource) -> ApiResult<PlaybackSource>?,
) : ResolvingDataSource.Resolver {

    @Volatile
    internal var current: PlaybackSource = initialSource
        private set

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        val resolvedUrl = runBlocking {
            try {
                withTimeout(RESOLVE_TIMEOUT_MILLIS) { resolveCurrentUrl() }
            } catch (timeout: TimeoutCancellationException) {
                throw IOException("Playback URL refresh timed out for mediaId=$mediaId", timeout)
            }
        }
        return dataSpec.withUri(Uri.parse(resolvedUrl))
    }

    /** The entire still-valid/refreshed/failed decision, with no Media3/Android type in its
     *  signature — see this class's own kdoc for why. Package-visible (`internal`) purely for
     *  `PlaybackUrlResolverTest`. */
    internal suspend fun resolveCurrentUrl(): String {
        val refreshed = refreshPlaybackUrl(mediaId, current) ?: return current.url

        return when (refreshed) {
            is ApiResult.Success -> {
                current = refreshed.data
                current.url
            }

            is ApiResult.Failure -> throw IOException(
                "Playback URL refresh failed for mediaId=$mediaId: ${refreshed.code.wire} (${refreshed.message})",
            )
        }
    }

    private companion object {
        /** See this class's own kdoc ("Bounded, not unbounded"). */
        const val RESOLVE_TIMEOUT_MILLIS = 15_000L
    }
}
