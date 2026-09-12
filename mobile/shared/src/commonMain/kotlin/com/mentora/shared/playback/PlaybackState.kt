package com.mentora.shared.playback

/**
 * The lifecycle states a [LessonPlaybackController] implementation (Phase 4's ExoPlayer binding,
 * Phase 5's AVPlayer binding — neither exists in `shared`) reports through
 * [LessonPlaybackController.state]. Deliberately a closed, exhaustive sealed type rather than an
 * open one — a `when` over [PlaybackState] on the platform side is meant to be exhaustive, so a new
 * state added later is a compile error at every call site, not a silently-ignored `else` branch.
 */
sealed class PlaybackState {
    /** Nothing loaded yet, or [LessonPlaybackController] was just constructed/reset. */
    data object Idle : PlaybackState()

    /** A source has been handed to the player and it is loading/rebuffering before playback can
     * (re)start — includes the initial load and any mid-playback stall while scrubbing/re-buffering. */
    data object Buffering : PlaybackState()

    /** Actively playing. */
    data object Playing : PlaybackState()

    /** Loaded and playable, but not advancing — either the caller paused it, or the player itself is
     * momentarily at rest between other states. */
    data object Paused : PlaybackState()

    /** Playback reached the end of the source. */
    data object Ended : PlaybackState()

    /** The player failed to load or play the source. [message] is diagnostic-only, mirroring
     * [com.mentora.shared.data.network.ApiResult.Failure.message] — never assumed localized or safe
     * to show verbatim as UI copy. */
    data class Error(val message: String) : PlaybackState()
}
