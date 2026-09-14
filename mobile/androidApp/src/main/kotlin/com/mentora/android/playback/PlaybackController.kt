package com.mentora.android.playback

import com.mentora.shared.domain.model.PlaybackSource
import com.mentora.shared.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Task 13 "C2" addition — the exact surface [CoursePlayerViewModel][com.mentora.android.ui
 * .courseplayer.CoursePlayerViewModel] needs from a lesson-video player, extracted purely so a JVM
 * unit test can hand the ViewModel a hand-built fake instead of a real [MediaPlaybackController]
 * (which needs a real Android `Context`/`ExoPlayer` and therefore cannot be constructed on this
 * module's plain `testDebugUnitTest` JVM surface — no Robolectric, no mocking library in this
 * module's dependencies, same standing convention every other ViewModel test here already follows
 * with hand-built lambda/fake dependencies).
 *
 * [MediaPlaybackController] implements this ADDITIVELY (`: LessonPlaybackController,
 * PlaybackController`) — every method/property below already exists on that class with this exact
 * shape (see its own kdoc on [MediaPlaybackController.currentPosition]/[MediaPlaybackController
 * .currentPositionNow] for why these are [StateFlow], not the interface-contract [kotlinx.coroutines
 * .flow.Flow] `shared`'s own `LessonPlaybackController` declares). Zero production behavior changed
 * by this extraction.
 */
interface PlaybackController {
    /** See [MediaPlaybackController.currentPosition]'s kdoc — ticker-driven, can lag up to 250ms. */
    val currentPosition: StateFlow<Duration>

    /** See [MediaPlaybackController.duration]'s kdoc. */
    val duration: StateFlow<Duration?>

    /** See [MediaPlaybackController.state]'s kdoc. */
    val state: StateFlow<PlaybackState>

    /** See [MediaPlaybackController.currentPositionNow]'s kdoc — the exact, synchronous read; use
     *  this (never [currentPosition]'s last-collected value) for a one-shot final-position snapshot. */
    fun currentPositionNow(): Duration

    /** See [MediaPlaybackController.prepareLesson]'s kdoc. */
    fun prepareLesson(mediaId: String, source: PlaybackSource, startPosition: Duration)

    fun play()
    fun pause()
    fun seekTo(position: Duration)

    /** See [MediaPlaybackController.stop]'s kdoc — Task 13 C2 review finding F1 (round 2): genuinely
     *  unloads the currently-loaded media, unlike [pause]. */
    fun stop()

    /** See [MediaPlaybackController.release]'s kdoc — idempotent, safe to call more than once. */
    fun release()
}
