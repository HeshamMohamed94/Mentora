package com.mentora.android.playback

import android.view.SurfaceView
import com.mentora.shared.domain.model.PlaybackSource
import com.mentora.shared.playback.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Duration

/** Task 13 "C3" addition — [PlaybackController.videoAspectRatio]'s default value for callers that
 *  never override it (i.e. the JVM-only `FakePlaybackController` test double, which has no real video
 *  surface to report a size for). A single top-level singleton so the interface's default-getter
 *  returns a stable [StateFlow] instance rather than allocating a fresh, never-emitting one per call. */
private val NoVideoAspectRatio: StateFlow<Float?> = MutableStateFlow<Float?>(null).asStateFlow()

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

    /**
     * Task 13 "C3" addition — D85 Decision 3's video-surface plumbing, the one hook neither
     * `shared`'s `LessonPlaybackController` contract nor the C1/C2-era shape of this interface
     * exposed: a way for [com.mentora.android.ui.courseplayer.PlayerSurface]'s `AndroidView`-hosted
     * `SurfaceView` to actually reach the player. Additive only, mirroring [stop]'s own precedent for
     * growing this interface without touching any existing member — every method/property below has
     * a default (no-op / never-emitting) body so [MediaPlaybackController] (the only real
     * implementation, overriding all three for real) is the only class that MUST provide one; the JVM
     * `FakePlaybackController` test double (`CoursePlayerViewModelTest.kt`, no real Android
     * `SurfaceView`/`ExoPlayer` on that test's plain-JVM classpath) needs no change at all.
     *
     * [attachVideoSurface]/[detachVideoSurface] are deliberately the ONLY new surface reachable here —
     * not the whole underlying player — so a caller can never bypass [com.mentora.android.ui
     * .courseplayer.CoursePlayerViewModel]'s own state machine (`play`/`pause`/`seekTo`/`prepareLesson`
     * stay reachable exclusively through that class's own actions).
     */
    fun attachVideoSurface(surfaceView: SurfaceView) {}

    /** See [attachVideoSurface]'s kdoc. D85 Decision 3's own "`clearVideoSurface()` in `onDispose`"
     *  instruction — called from [com.mentora.android.ui.courseplayer.PlayerSurface]'s
     *  `DisposableEffect`. */
    fun detachVideoSurface() {}

    /** See [attachVideoSurface]'s kdoc — `width / height` (adjusted for
     *  `VideoSize.pixelWidthHeightRatio`) once `Player.Listener.onVideoSizeChanged` fires for the
     *  currently-attached surface's media, `null` before that (or once no video is loaded) so
     *  [com.mentora.android.ui.courseplayer.PlayerSurface] falls back to filling the outer 16:9 frame
     *  rather than applying a bogus aspect ratio. */
    val videoAspectRatio: StateFlow<Float?> get() = NoVideoAspectRatio
}
