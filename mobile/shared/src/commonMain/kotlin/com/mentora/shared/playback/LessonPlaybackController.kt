package com.mentora.shared.playback

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * The platform-agnostic contract a real video player binds to — **interface only**, per
 * `execution/PHASE_3_KMP_PLAN.md` Task 13 AC #6. `shared` ships NO implementation of this
 * interface: no ExoPlayer, no AVPlayer, and no fake/stub beyond a trivial, clearly-labeled
 * shape-verification test double in `commonTest` (never anything resembling a real player). The
 * real Android (ExoPlayer/Media3) and iOS (AVPlayer) bindings are Phase 4/5 concerns, living in
 * `androidMain`/`iosMain` app modules outside `shared` entirely — not this module, and not this task.
 *
 * [prepare] is handed the ALREADY-ABSOLUTE, already-resolved stream url from
 * `com.mentora.shared.domain.usecase.media.GetLessonPlaybackSourceUseCase`/
 * `RefreshPlaybackUrlUseCase` (`com.mentora.shared.domain.model.PlaybackSource.url`) — it must be
 * used AS-IS. **The implementation must attach no `Authorization` header of its own**: the
 * `?token=` query parameter embedded in the url IS the auth mechanism the backend's
 * `/media/{id}/stream` route checks (see [com.mentora.shared.domain.model.PlaybackSource]'s kdoc).
 * The server supports HTTP range requests on that route for scrubbing — server-side behavior an
 * implementation may rely on (e.g. to enable native scrubbing/seek-ahead buffering), but `shared`
 * implements no client-side range-request logic itself; that is entirely the platform player's job.
 *
 * A fresh url is valid for ~5 minutes (`MediaService.PLAYBACK_TTL_MINUTES`) — a long-running
 * playback session is expected to call `RefreshPlaybackUrlUseCase` and [prepare] again with the
 * refreshed url before/at expiry; this interface has no built-in re-prepare/retry behavior of its
 * own.
 *
 * [duration] is `null` until the player itself determines it after loading the source — it is
 * NEVER sourced from the API (no lesson/media response carries a duration field, Decision C4).
 */
interface LessonPlaybackController {
    /** Loads [url] (an absolute, already-resolved stream url) as the current source. Implementations
     * must not attach an `Authorization` header — see this interface's kdoc. */
    fun prepare(url: String)

    /** Starts/resumes playback of the currently prepared source. No-op if nothing is prepared. */
    fun play()

    /** Pauses playback. No-op if not currently playing. */
    fun pause()

    /** Seeks to [position] within the currently prepared source. */
    fun seekTo(position: Duration)

    /** The current playback position, updated as playback advances. */
    val currentPosition: Flow<Duration>

    /** The prepared source's total duration, once the player has determined it; `null` before then
     * or if nothing is prepared. Never sourced from the API — see this interface's kdoc. */
    val duration: Flow<Duration?>

    /** The player's current lifecycle state. */
    val state: Flow<PlaybackState>
}
