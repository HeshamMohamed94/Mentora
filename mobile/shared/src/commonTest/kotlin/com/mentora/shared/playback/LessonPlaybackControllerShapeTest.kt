package com.mentora.shared.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration

/**
 * NOT a real player. This is a minimal, in-memory SHAPE-VERIFICATION DEVICE only — it exists purely
 * to prove [LessonPlaybackController]'s interface compiles and is usable from Kotlin with the
 * signatures `execution/PHASE_3_KMP_PLAN.md` Task 13 AC #6 asks for. It does no decoding, no actual
 * media loading, and must never be mistaken for (or grown into) a real implementation — that is
 * explicitly Phase 4/5's job (ExoPlayer/AVPlayer), never `shared`'s.
 */
private class ShapeVerificationOnlyFakeController : LessonPlaybackController {
    val stateFlow = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val positionFlow = MutableStateFlow(Duration.ZERO)
    val durationFlow = MutableStateFlow<Duration?>(null)

    override fun prepare(url: String) {
        stateFlow.value = PlaybackState.Buffering
    }

    override fun play() {
        stateFlow.value = PlaybackState.Playing
    }

    override fun pause() {
        stateFlow.value = PlaybackState.Paused
    }

    override fun seekTo(position: Duration) {
        positionFlow.value = position
    }

    override val currentPosition: StateFlow<Duration> = positionFlow
    override val duration: StateFlow<Duration?> = durationFlow
    override val state: StateFlow<PlaybackState> = stateFlow
}

class LessonPlaybackControllerShapeTest {

    @Test
    fun `the interface is usable end to end with the expected member shapes`() = runTest {
        val fake = ShapeVerificationOnlyFakeController()
        val controller: LessonPlaybackController = fake

        assertEquals(PlaybackState.Idle, fake.stateFlow.value)

        controller.prepare("http://10.0.2.2:8080/api/v1/media/m1/stream?token=abc")
        assertEquals(PlaybackState.Buffering, fake.stateFlow.value)

        controller.play()
        assertEquals(PlaybackState.Playing, fake.stateFlow.value)

        controller.pause()
        assertEquals(PlaybackState.Paused, fake.stateFlow.value)

        controller.seekTo(Duration.ZERO)
        assertEquals(Duration.ZERO, fake.positionFlow.value)
    }
}
