package com.mentora.android.ui.courseplayer

import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.flow.StateFlow

/**
 * Task 13 "C3" — `execution/DECISIONS_LOG.md` D85 Decision 3: no `media3-ui`, a plain `SurfaceView` in
 * an `AndroidView`, 100% Compose control chrome (built separately, `PlayerControls.kt`). Outer [Box]
 * is the spec's 16:9 (`fillMaxWidth().aspectRatio(16f/9f)`, the exact T8 `CourseArtwork` idiom) on a
 * black background — safe here specifically because the video block never scrolls (this composable's
 * own caller, `CoursePlayerScreen.kt`, pins it between the top bar and the scrollable middle section,
 * per Decision 3's own "safe specifically because" caveat). The inner `SurfaceView` takes
 * [videoAspectRatio] once known (from `Player.Listener.onVideoSizeChanged`, surfaced by
 * [com.mentora.android.playback.PlaybackController.videoAspectRatio]), so a non-16:9 source
 * letterboxes instead of stretching; until the first size arrives (or when nothing is loaded at all —
 * a resource-only lesson, or a still-loading course) the surface just fills the 16:9 box.
 *
 * **Surface-attach mechanism — the one piece D85 left unpinned for C3.** Neither `shared`'s
 * `LessonPlaybackController` contract nor the C1/C2-era [com.mentora.android.playback.PlaybackController]
 * exposed any way to reach the real `ExoPlayer` instance a Compose `AndroidView` could hand a surface
 * to. [onAttachSurface]/[onDetachSurface]/[videoAspectRatio] are plain lambda/flow params here
 * (this composable never touches [com.mentora.android.playback.PlaybackController] or
 * [com.mentora.android.ui.courseplayer.CoursePlayerViewModel] directly, matching this codebase's own
 * "stateless presentation composable" convention) — wired by `CoursePlayerScreen.kt` to three new,
 * additive members closing this gap: `PlaybackController.attachVideoSurface`/`detachVideoSurface`/
 * `videoAspectRatio` (mirroring [com.mentora.android.playback.PlaybackController.stop]'s own
 * "additive method on the interface" precedent, default no-op/empty-flow bodies so the existing JVM
 * `FakePlaybackController` test double needed no change), plus three matching one-line pass-throughs
 * added to [com.mentora.android.ui.courseplayer.CoursePlayerViewModel] itself (see that class's own
 * kdoc on them) — the narrow, disclosed exception to this task's "don't modify
 * `CoursePlayerViewModel.kt`" instruction, since without it this composable would have structurally no
 * path to the controller instance the ViewModel exclusively owns.
 *
 * Attach happens once, in the `AndroidView` factory — create/destroy of the `SurfaceHolder` itself
 * stays ExoPlayer's own job per Decision 3 ("ExoPlayer registers its own `SurfaceHolder.Callback`");
 * detach happens in [DisposableEffect]'s `onDispose`, per that decision's own literal
 * "`clearVideoSurface()` in `onDispose`" instruction. No re-attach on a lesson switch is needed — the
 * same [SurfaceView] stays attached across lesson switches within one screen visit; only the
 * underlying media source changes (via `CoursePlayerViewModel`'s own `prepareLesson` calls).
 */
@Composable
fun PlayerSurface(
    videoAspectRatio: StateFlow<Float?>,
    onAttachSurface: (SurfaceView) -> Unit,
    onDetachSurface: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val aspectRatio by videoAspectRatio.collectAsState()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black)
            .testTag(PlayerSurfaceTestTag),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context -> SurfaceView(context).also(onAttachSurface) },
            modifier = aspectRatio?.let { Modifier.aspectRatio(it) } ?: Modifier.fillMaxSize(),
        )
    }

    DisposableEffect(Unit) {
        onDispose { onDetachSurface() }
    }
}

const val PlayerSurfaceTestTag = "course-player-surface"
