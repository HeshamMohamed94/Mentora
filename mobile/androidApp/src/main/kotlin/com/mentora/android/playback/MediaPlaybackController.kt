@file:OptIn(UnstableApi::class)

package com.mentora.android.playback

import android.content.Context
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.PlaybackSource
import com.mentora.shared.playback.LessonPlaybackController
import com.mentora.shared.playback.PlaybackState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * `execution/DECISIONS_LOG.md` D85 Decision 1 — the ExoPlayer/Media3 binding of `shared`'s
 * [LessonPlaybackController]. This is the "C1" sub-commit `PHASE_4_ANDROID_PLAN.md § 7` calls for:
 * this class, proven alone (JVM tests over [PlaybackUrlResolver] + a real instrumented playback
 * test), BEFORE any screen or ViewModel is built on top of it.
 *
 * **Ownership, once it exists.** Task 13's later "C2" sub-commit (`CoursePlayerViewModel`, not yet
 * built) owns this class exclusively from then on: constructs exactly one instance per Course Player
 * screen visit, from the **application** [Context] (never an Activity — this outlives Activity
 * re-creation on rotation, see Decision 1's own rationale against an Activity-scoped or
 * Application-scoped-singleton alternative), and calls [release] from `onCleared()` — reading the
 * final playback position FIRST (via [currentPositionNow], an exact synchronous read — not by
 * collecting [currentPosition], which can lag up to 250ms), then releasing (D85's "Ordering rule for
 * onCleared()": releasing first loses the number). Until that ViewModel exists, whatever constructs
 * an instance of this class owns calling [release] when done with it — safe to call more than once
 * (see that function's own kdoc).
 *
 * **Thread confinement — stated plainly so a later contributor doesn't "helpfully" move work off
 * it.** Every method here, and every [Player.Listener] callback the internal player fires, stays on
 * the main thread: [scope] is [Dispatchers.Main.immediate], never [Dispatchers.Default]/IO. ExoPlayer
 * itself is single-threaded by contract (the `Looper` it was built on, here the main looper). The one
 * deliberate exception is [PlaybackUrlResolver], which runs on ExoPlayer's own background loader
 * thread by design — see that class's own kdoc.
 *
 * [refreshPlaybackUrl] mirrors [com.mentora.shared.domain.usecase.media.RefreshPlaybackUrlUseCase]'s
 * `invoke(mediaId, current)` contract exactly — callers pass that use case directly
 * (`sdk.media.refreshPlaybackUrl::invoke`); this class holds no `MentoraSdk` reference of its own,
 * the same lambda-constructor seam every other `androidApp` ViewModel already uses (e.g.
 * `com.mentora.android.ui.coursedetails.CourseDetailsViewModel`) — here on the controller itself,
 * since `PlaybackUrlResolver` (built fresh per [prepareLesson] call) is the thing that actually needs
 * it, not a ViewModel.
 */
class MediaPlaybackController(
    context: Context,
    private val refreshPlaybackUrl: suspend (mediaId: String, current: PlaybackSource) -> ApiResult<PlaybackSource>?,
) : LessonPlaybackController, PlaybackController {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            val newState = derivedState()
            _state.value = newState
            _duration.value = player.duration.takeIf { it != C.TIME_UNSET }?.milliseconds
            if (events.contains(Player.EVENT_POSITION_DISCONTINUITY)) {
                _currentPosition.value = player.currentPosition.milliseconds
            }
            // Reviewer finding (Task 13 C1 review): the 250ms ticker only runs while `isPlaying`, and
            // no discontinuity fires for a single-item source reaching its natural end — without this,
            // `currentPosition` stalls up to 250ms short of `duration` forever once playback ends,
            // which a scrubber built against these flows would visibly show (e.g. "19.8 / 20.0").
            if (newState is PlaybackState.Ended) {
                _duration.value?.let { _currentPosition.value = it }
            }
            if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
                restartOrStopTicker()
            }
        }
    }

    // Reviewer finding (Task 13 C1 review): `ExoPlayer.Builder` otherwise binds to
    // `Util.getCurrentOrMainLooper()` — whichever thread happens to call this constructor — which
    // would make this class's kdoc "every listener callback stays on the main thread" claim
    // conditional rather than actually true. Pinning it explicitly (`.setLooper`) makes the
    // invariant real regardless of which thread constructs this class.
    private val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setLooper(Looper.getMainLooper())
        .build()
        .also { exoPlayer ->
            // Decision 1's "Audio behaviour set once at construction" — cheap, and the alternative
            // (ignoring audio focus / not pausing on a headphone-unplug "becoming noisy" event) is a
            // real defect on a phone.
            exoPlayer.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            exoPlayer.setHandleAudioBecomingNoisy(true)
            exoPlayer.addListener(playerListener)
        }

    private var tickerJob: Job? = null
    private var released = false

    // Reviewer finding (Task 13 C1 review): narrowed to StateFlow (a legal covariant override of
    // the interface's `Flow`-typed properties) so a caller — specifically C2's ViewModel, per D85's
    // "read the final position FIRST, then release" onCleared() ordering rule — can read the CURRENT
    // value synchronously rather than needing to collect. [currentPositionNow] goes one step
    // further: it reads the live player position directly, so it is never up to 250ms stale the way
    // [currentPosition]'s own ticker-driven value can be.
    private val _currentPosition = MutableStateFlow(Duration.ZERO)
    override val currentPosition: StateFlow<Duration> = _currentPosition.asStateFlow()

    /** The exact current position, read directly from the player — use this for a one-shot read
     *  (e.g. a final position snapshot before [release]); [currentPosition] is for observing. */
    override fun currentPositionNow(): Duration = player.currentPosition.milliseconds

    private val _duration = MutableStateFlow<Duration?>(null)
    override val duration: StateFlow<Duration?> = _duration.asStateFlow()

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    /** [PlaybackState] derivation as a single function, so [state] is always internally consistent —
     *  an error takes priority over the raw [Player.getPlaybackState] value, and
     *  [PlaybackState.Playing] vs [PlaybackState.Paused] are both derived from [Player.STATE_READY]
     *  disambiguated by [Player.isPlaying] — rather than tracked via ad hoc booleans mutated across
     *  several different listener callbacks. */
    private fun derivedState(): PlaybackState {
        // Reviewer finding (Task 13 C1 review, decompiled `ExoPlaybackException.deriveMessage`):
        // `player.playerError?.message` is the near-useless literal "Source error" for every
        // TYPE_SOURCE failure (which is what `PlaybackUrlResolver`'s thrown `IOException` becomes) —
        // the actually-useful message (including the wire `ApiErrorCode`, see that class's own kdoc)
        // lives on the CAUSE chain instead. Walk it and prefer the deepest non-null message, so a
        // real "FORBIDDEN_NOT_ENROLLED"-style message survives into the publicly observable [state]
        // rather than being silently flattened to "Source error".
        player.playerError?.let { error ->
            val deepestMessage = generateSequence<Throwable>(error) { it.cause }.lastOrNull()?.message
            return PlaybackState.Error(deepestMessage ?: error.message ?: "Playback error")
        }
        return when (player.playbackState) {
            Player.STATE_IDLE -> PlaybackState.Idle
            Player.STATE_BUFFERING -> PlaybackState.Buffering
            Player.STATE_ENDED -> PlaybackState.Ended
            Player.STATE_READY -> if (player.isPlaying) PlaybackState.Playing else PlaybackState.Paused
            else -> PlaybackState.Idle
        }
    }

    /** D85's 250 ms position ticker, gated on [Player.isPlaying] — only runs while actually playing,
     *  so a paused/buffering/ended player emits no wasted ticks. The other half of [currentPosition]'s
     *  contract — the immediate push on a position discontinuity — happens directly in
     *  [playerListener]'s `onEvents`. */
    private fun restartOrStopTicker() {
        if (player.isPlaying) {
            if (tickerJob?.isActive != true) {
                tickerJob = scope.launch {
                    while (isActive) {
                        _currentPosition.value = player.currentPosition.milliseconds
                        delay(250)
                    }
                }
            }
        } else {
            tickerJob?.cancel()
            tickerJob = null
        }
    }

    /** [LessonPlaybackController]'s interface contract, implemented exactly — attaches no
     *  `Authorization` header, [url] is used as-is (see that interface's own kdoc). This is
     *  deliberately the NO-AUTO-REFRESH form: [url] carries neither a `mediaId` nor a
     *  [PlaybackSource.expiresAt], so no [PlaybackUrlResolver] is wired in — a long-running session
     *  using this path has no self-healing against Decision 2's 5-minute playback-URL TTL. Real
     *  lesson loads (every one of them has a real [PlaybackSource]/`mediaId`) should use
     *  [prepareLesson] instead; this form exists only because it is `shared`'s actual interface
     *  contract (D85's Open Question 3). */
    override fun prepare(url: String) {
        if (released) return
        _currentPosition.value = Duration.ZERO
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
    }

    override fun play() {
        if (released) return
        player.play()
    }

    override fun pause() {
        if (released) return
        player.pause()
    }

    override fun seekTo(position: Duration) {
        if (released) return
        player.seekTo(position.inWholeMilliseconds)
    }

    /** Task 13 C2 review finding F1 (round 2) — genuinely unloads whatever media is currently
     *  loaded, rather than merely [pause]ing it. A lesson switch to a lesson with no video (or whose
     *  source fetch fails) needs this: [pause] alone leaves the OUTGOING lesson's media still loaded
     *  and playable — a later, unguarded [play] (e.g. from [androidx.lifecycle.ViewModel] callers
     *  reachable from C3's transport controls) would resume it, and it can still reach
     *  [PlaybackState.Ended] on its own. `player.stop()` moves the player to [Player.STATE_IDLE]
     *  with nothing to play; `clearMediaItems()` drops the timeline entirely so a stray [play] call
     *  genuinely has nothing to resume. */
    override fun stop() {
        if (released) return
        player.stop()
        player.clearMediaItems()
        _currentPosition.value = Duration.ZERO
        _duration.value = null
    }

    /**
     * Android-only addition (Decision 1's "API shape" / Open Question 3) — load-bearing, not
     * convenience: [prepare] structurally cannot support Decision 2's TTL refresh, since `url` alone
     * carries neither [mediaId] nor [PlaybackSource.expiresAt].
     *
     * Builds a fresh [PlaybackUrlResolver] bound to [mediaId]/[source]/[refreshPlaybackUrl] (one per
     * call — a lesson switch calls this again with a new resolver, never reuses a stale one), wraps
     * it in a [ResolvingDataSource.Factory] over a [DefaultDataSource.Factory], and prepares a
     * [ProgressiveMediaSource] built from it — so every NEW HTTP connection this lesson's playback
     * opens (a seek outside the buffer, a re-buffer, a resume after a pause), not just the very first
     * one, is preceded by a URL-freshness check.
     */
    override fun prepareLesson(mediaId: String, source: PlaybackSource, startPosition: Duration) {
        if (released) return
        // Reviewer finding (Task 13 C1 review): no EVENT_POSITION_DISCONTINUITY fires on a FIRST
        // prepare (the player's timeline is empty, so media3's own discontinuity check never trips —
        // verified against the decompiled `ExoPlayerImpl.setMediaSourcesInternal`), so without this
        // explicit seed, `currentPosition` would read 0 while the player sits at a real resume point
        // until the ticker's first 250ms tick (which itself never runs unless `play()` is also
        // called) — a scrubber built against this flow would show "0:00" for a lesson genuinely
        // resuming partway through, and a progress flush firing before that first tick would report 0.
        _currentPosition.value = startPosition
        val resolver = PlaybackUrlResolver(mediaId, source, refreshPlaybackUrl)
        val upstreamFactory = DefaultDataSource.Factory(appContext)
        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory, resolver)
        val mediaSource = ProgressiveMediaSource.Factory(resolvingFactory)
            .createMediaSource(MediaItem.fromUri(source.url))
        player.setMediaSource(mediaSource, startPosition.inWholeMilliseconds)
        player.prepare()
    }

    /** Releases the underlying [ExoPlayer] and cancels [scope] — see this class's own kdoc for who
     *  owns calling this and when. Idempotent — a second call is a no-op, since D85's own teardown
     *  plan gives C2 two potentially-overlapping teardown paths (`onCleared()` plus an `ON_STOP`/
     *  `DisposableEffect` hook) that could plausibly both fire (reviewer finding, Task 13 C1 review;
     *  [ExoPlayer.release]'s own contract makes no idempotency claim either way, so this class
     *  provides its own rather than relying on one). Every other method on this class no-ops once
     *  [released], rather than risking an `IllegalStateException`/use-after-release crash from a
     *  straggling call on a teardown race. */
    override fun release() {
        if (released) return
        released = true
        tickerJob?.cancel()
        player.removeListener(playerListener)
        player.release()
        scope.cancel()
    }
}
