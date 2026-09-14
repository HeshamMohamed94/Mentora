package com.mentora.android.ui.courseplayer

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.android.playback.MediaPlaybackController
import com.mentora.android.playback.PlaybackController
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.Lesson
import com.mentora.shared.domain.model.LessonProgressTarget
import com.mentora.shared.domain.model.PlaybackSource
import com.mentora.shared.domain.usecase.progress.LessonCompletionOutcome
import com.mentora.shared.domain.usecase.quiz.QuizLookupResult
import com.mentora.shared.playback.PlaybackState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** `execution/DECISIONS_LOG.md` D85 Decision 9's footer state machine — never a client-side guess
 *  beyond the 3-way rule that decision spells out literally: [MarkComplete] while the CURRENT
 *  lesson is incomplete; [NextLesson] once it is complete; [TakeQuiz] specifically when the current
 *  lesson is the LAST one in curriculum order, a quiz exists ([QuizLookupResult.Found]), and
 *  [CourseProgress.quizPassed] is not `true`. **[FinishCourse] (Task 13 C2 review finding F10) —
 *  the 4th case D85's literal wording doesn't list**: the last lesson, complete, with NO quiz. D85
 *  never considered this combination (its 3-way rule has no fallback for it), not decided against a
 *  4th state — [NextLesson]'s own label would read as a broken navigation promise here (there is no
 *  next lesson), so this gets its own, correctly-labeled state rather than silently reusing
 *  [NextLesson]'s copy for a tap that actually finishes the course. */
sealed interface CoursePlayerFooterAction {
    data object MarkComplete : CoursePlayerFooterAction
    data object NextLesson : CoursePlayerFooterAction
    data object TakeQuiz : CoursePlayerFooterAction
    data object FinishCourse : CoursePlayerFooterAction
}

/** D85 Decision 8's per-lesson completion state — icon PLUS text on the C3 side, never colour alone
 *  (`ux/SCREEN_UX_SPECS.md:421`, cited by that decision). */
enum class SheetLessonState { Completed, Current, NotStarted }

/** D85 Decision 8's per-lesson sheet row data. Deliberately carries NO duration label — see that
 *  decision's own "Three content gaps" section: no read endpoint anywhere exposes lesson duration,
 *  so inventing one here would be exactly the fabrication that section forbids. [hasVideo] is the
 *  one real, honest signal [Lesson.videoMediaId] gives about whether this row plays anything at
 *  all. */
data class SheetLesson(
    val lessonId: String,
    val globalIndex: Int,
    val title: String,
    val state: SheetLessonState,
    val hasVideo: Boolean,
)

data class SheetSection(
    val sectionId: String,
    val title: String,
    val order: Int,
    val lessons: List<SheetLesson>,
)

/** D85 Decision 8's "Three content gaps" section: the backend has exactly ONE quiz per COURSE, not
 *  one per section — [SheetQuizRow] is therefore rendered once, after the last section, never
 *  duplicated per section. `null` on [CurriculumSheetState.quizRow] means
 *  [QuizLookupResult.NoQuiz] — a legitimate state (at least one seeded course has no quiz), not a
 *  loading/error placeholder. [questionCount] is the one real trailing label the sheet's quiz row
 *  gets (`quiz.questions.size`) — everything else about "N Q" is C3's rendering concern. */
data class SheetQuizRow(val questionCount: Int)

data class CurriculumSheetState(
    val sections: List<SheetSection>,
    val currentLessonId: String,
    val quizRow: SheetQuizRow?,
)

/** [CoursePlayerContentState]'s loaded-and-playable shape. [videoLoadError] is a narrow, separate
 *  signal from the top-level [CoursePlayerContentState.Error] — course/progress/quiz all loaded
 *  fine, but the CURRENT lesson's own `getLessonPlaybackSource` call failed (e.g. a stale/expired
 *  `mediaId`, or a genuine network hiccup on a lesson switch); the rest of this state (curriculum
 *  sheet, footer, lesson title) stays fully usable, only the player surface itself has nothing
 *  loaded. `null` on a lesson with no [Lesson.videoMediaId] at all (a resource-only lesson) is NOT
 *  an error — [hasVideoSource] distinguishes that legitimate case from a real load failure. */
data class CoursePlayerReadyState(
    val courseId: String,
    val courseTitle: String,
    val currentLessonId: String,
    val currentLessonTitle: String,
    val lessonNumber: Int,
    val totalLessons: Int,
    val completionPercent: Int,
    val isCurrentLessonCompleted: Boolean,
    val hasVideoSource: Boolean,
    val videoLoadError: ApiErrorCode?,
    val sheet: CurriculumSheetState,
    val footerAction: CoursePlayerFooterAction,
    /** Task 13 C2 review finding F6: a network blip on [CompleteLessonUseCase] used to leave the
     *  footer looking dead (tap, nothing visibly happens) with no way to tell a slow call from a
     *  failed one. While `true`, C3 should disable the footer button rather than let a second tap
     *  launch a concurrent completion attempt. */
    val isCompletionInFlight: Boolean = false,
    /** Task 13 C2 review finding F6 — set after a failed [CompleteLessonUseCase] call (video-end
     *  auto-advance or a footer tap), so C3 can show a transient inline error/retry affordance
     *  instead of the previous silent no-op. **Sticky, not auto-clearing**: stays set across
     *  recompositions/[uiState] emissions until either the next completion attempt resolves (success
     *  clears it, another failure replaces it) or C3 explicitly calls
     *  `CoursePlayerViewModel.onCompletionErrorDismissed`. */
    val completionError: ApiErrorCode? = null,
)

/** The whole course is already finished — reached either because [lessonId] was `null` at
 *  construction and `sdk.progress.resumeCourse` resolved straight to
 *  [LessonProgressTarget.CourseFinished] (D85 Decision 7's own instruction: "routes to the
 *  completion state, not to a lesson"), or because an in-screen [onLessonEnded]/[onNextLessonTapped]
 *  auto-advance resolved to the same target. [quizRow] lets C3 route to Quiz when one exists and
 *  is unpassed (`ux/NAVIGATION_SPEC.md:71`, cited by D85 Decision 9), or render the inline
 *  completion state otherwise — routing itself is C3's concern, this is only the data. */
data class CoursePlayerCourseCompletedState(
    val courseId: String,
    val courseTitle: String,
    val quizRow: SheetQuizRow?,
    val quizPassed: Boolean?,
)

sealed interface CoursePlayerContentState {
    data object Loading : CoursePlayerContentState
    data class Error(val code: ApiErrorCode) : CoursePlayerContentState
    data class Ready(val state: CoursePlayerReadyState) : CoursePlayerContentState
    data class CourseCompleted(val state: CoursePlayerCourseCompletedState) : CoursePlayerContentState
}

data class CoursePlayerUiState(
    val content: CoursePlayerContentState = CoursePlayerContentState.Loading,
)

/**
 * Task 13 "C2" — Course Player's ViewModel. Builds and tests the whole non-UI half of `ux
 * /SCREEN_UX_SPECS.md`'s Course Player screen: lesson resolution (D85 Decision 7), the ExoPlayer
 * binding's lifecycle (D85 Decision 1, via [controller]), the progress-write schedule (D85 Decision
 * 6), the Curriculum Bottom Sheet's data shape (D85 Decision 8), and the footer's primary-action
 * state machine (D85 Decision 9). No Compose UI — that is Task 13 "C3".
 *
 * **Lambda-constructor seam** — same convention as `CourseDetailsViewModel`/`CheckoutViewModel`
 * (see the former's own kdoc for why): the `ProgressFacade`/`CatalogFacade`/`QuizFacade`/
 * `MediaFacade` constructors are `internal`, so `:androidApp` cannot fake a [MentoraSdk] directly in
 * a JVM test; [Factory] wires these lambdas to the real `sdk.*` use cases in production.
 *
 * **[controller] is accepted already-constructed, not built by this class itself.** D85 Decision 1
 * says this ViewModel "constructs" the [PlaybackController]/[MediaPlaybackController] — in practice
 * that construction (which needs a real Android application `Context`) happens in [Factory],
 * exactly where every other `sdk.*` dependency is already wired for this exact reason: a JVM unit
 * test can then hand this constructor a hand-built fake [PlaybackController] instead (see
 * `CoursePlayerViewModelTest`), the same "accept it as a constructor seam" approach this project
 * already uses for every other platform dependency, rather than reaching for a mocking framework
 * this module has never depended on. This ViewModel still OWNS the instance exclusively from that
 * point on — every `prepareLesson`/`play`/`pause`/`seekTo` call, and the [onCleared] teardown, are
 * exclusively this class's responsibility, never C3's.
 *
 * **Thread confinement across [writeScope] and [controller] — the one subtlety D85 does not spell
 * out at this level of detail.** [MediaPlaybackController]'s own kdoc requires every call into it to
 * stay on the main thread (`ExoPlayer` is single-threaded by contract, pinned to the main
 * `Looper`). [writeScope] runs on [Dispatchers.Default] specifically so its writes are NEVER
 * cancelled by [viewModelScope] going away (D85 Decision 6's whole point) — but that means any
 * [controller] call reached FROM a [writeScope]-launched coroutine (the auto-advance path after
 * [onLessonEnded]/[onMarkCompleteTapped]/[onNextLessonTapped] resolve a fresh lesson to load) must
 * explicitly hop back to [Dispatchers.Main.immediate] first ([activateLesson] does this
 * unconditionally, so it is safe to call from either scope). The progress-WRITE calls themselves
 * ([flushProgress], [flushLessonLoad], `completeLesson`) touch no [controller] method and stay on
 * [writeScope] throughout, per Decision 6 — routed through the single-consumer [writeQueue] (review
 * finding F4) rather than independent `writeScope.launch {}` calls, so writes also land in strict
 * enqueue order, not merely "eventually, on the same never-cancelled scope".
 */
class CoursePlayerViewModel(
    private val courseId: String,
    private val initialLessonId: String?,
    private val controller: PlaybackController,
    private val getCourseDetails: suspend (String) -> ApiResult<Course>,
    private val getCourseProgress: suspend (String) -> ApiResult<CourseProgress>,
    private val resumeCourse: suspend (String) -> ApiResult<LessonProgressTarget>,
    private val getQuiz: suspend (String) -> ApiResult<QuizLookupResult>,
    private val getLessonPlaybackSource: suspend (String) -> ApiResult<PlaybackSource>,
    private val reportPlaybackPosition: suspend (String, String, Int) -> ApiResult<CourseProgress>?,
    private val completeLesson: suspend (String, String) -> ApiResult<LessonCompletionOutcome>,
    /** D85 Decision 6's dedicated, never-cancelled scope — a plain constructor param (defaulting to
     *  production's [Dispatchers.Default]) exactly so a JVM test can inject one driven by its own
     *  `TestDispatcher`, per this class's own testing convention. */
    private val writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ViewModel() {

    private val _uiState = MutableStateFlow(CoursePlayerUiState())
    val uiState: StateFlow<CoursePlayerUiState> = _uiState.asStateFlow()

    /** Raw pass-through of [controller]'s own flows — deliberately NOT folded into [uiState], per
     *  D85 Decision 1's own caution: "Collect [currentPosition] inside the scrubber composable
     *  only — never at the screen root, or every 250ms recomposes the whole player screen including
     *  the curriculum list." C3 collects these directly where each is actually needed. */
    val playbackState: StateFlow<PlaybackState> get() = controller.state
    val playbackPosition: StateFlow<Duration> get() = controller.currentPosition
    val playbackDuration: StateFlow<Duration?> get() = controller.duration

    // Review finding F7: these fields are written from BOTH `viewModelScope` (Main) and
    // `writeScope` (Dispatchers.Default, via the auto-advance path inside `completeLessonAndAdvance`
    // and `flushProgress`'s own progress-reconciliation) and read from Main-thread collectors —
    // `@Volatile` for the same reason `MediaPlaybackController.released` is (visibility across
    // threads for simple last-write-wins fields; none of these need compound-operation atomicity
    // beyond that).
    @Volatile private var course: Course? = null
    @Volatile private var progress: CourseProgress? = null
    @Volatile private var quiz: QuizLookupResult? = null
    @Volatile private var activeLessonId: String? = null
    /** Review finding F1/F2: distinct from [activeLessonId] — set SYNCHRONOUSLY at the start of a
     *  lesson switch purely to guard against a re-entrant tap on the same target lesson while a
     *  switch is already in flight (see [onLessonSelected]). [activeLessonId] itself only flips once
     *  [activateLesson] has made the controller's own position genuinely consistent with the new
     *  lesson — see that function's own kdoc for why. Purely an optimization to skip a redundant
     *  fetch for the exact-same target; [switchGeneration] is what actually makes a DIFFERENT-target
     *  race correct. */
    @Volatile private var pendingLessonId: String? = null
    /** Review finding F1/F2 (round 2) — the lesson id [controller] actually has media PREPARED for
     *  right now, as opposed to [activeLessonId] (the lesson the UI/write-schedule currently
     *  considers "current"). These two can genuinely differ for a real window: while
     *  [activateLesson] is resolving a lesson with no video (or a failed source fetch), and briefly
     *  while [activateLesson]'s own network call for a NEW lesson is still in flight. Every read of
     *  [PlaybackController.currentPositionNow] and every call to [PlaybackController.play]/[seekTo]
     *  this class performs is gated on this matching [activeLessonId] (see
     *  [currentControllerPositionSecondsFor]) — structurally, rather than relying on [controller]
     *  itself having no stale media left to misbehave with. `null` means "no video is currently
     *  loaded for [activeLessonId]", set that way by [activateLesson]'s own `stop()` branch. */
    @Volatile private var preparedLessonId: String? = null
    @Volatile private var lastFlushedPositionSeconds: Int = 0
    private var previousPlaybackState: PlaybackState = controller.state.value

    /**
     * Review finding F1/F2/F3 (round 2, "the last tap wins" race) — a monotonic counter, incremented
     * at the very top of every [activateLesson] call, BEFORE its first suspension point. Two switches
     * started close together (e.g. tapping lesson A then lesson B in quick succession, both while
     * lesson A's own `getLessonPlaybackSource` call is still in flight) used to let whichever fetch
     * happened to resolve LAST win, regardless of which the user tapped last. [activateLesson] checks
     * `switchGeneration.get() == <the value it captured at entry>` immediately before ever touching
     * [controller] and again immediately before committing [activeLessonId]/[preparedLessonId] — a
     * call that finds itself stale (a later call has since started) bails out having made no
     * observable change, so the call that was started LAST always wins deterministically.
     */
    private val switchGeneration = AtomicLong(0L)

    /** Clears [pendingLessonId] if it still points at [lessonId] — review finding (round 3, HIGH):
     *  [activateLesson] must call this on EVERY exit path, not just its final commit. A first draft
     *  of the [switchGeneration] guard only cleared it on the commit path, so a call that instead
     *  bailed out early (superseded by a later switch) left its own target lesson id stuck in
     *  [pendingLessonId] forever — [onLessonSelected]'s re-entrancy guard would then silently reject
     *  every future tap on that exact lesson, with no way to recover except selecting a different
     *  lesson first. */
    private fun clearPendingIfStillMine(lessonId: String) {
        if (pendingLessonId == lessonId) pendingLessonId = null
    }

    /** [controller]'s current position for [lessonId] specifically — `null` when [preparedLessonId]
     *  doesn't match (no video is actually loaded for that lesson right now: a resource-only lesson,
     *  a failed source fetch, or an outgoing lesson whose media has since been [PlaybackController
     *  .stop]ped). Review findings F1/F2 (round 2): every position read this class performs goes
     *  through this now, never a bare [PlaybackController.currentPositionNow] call, since [controller]
     *  may not actually hold [lessonId]'s media at all at the moment of the call. */
    private fun currentControllerPositionSecondsFor(lessonId: String): Int? =
        if (preparedLessonId == lessonId) controller.currentPositionNow().inWholeSeconds.toInt() else null

    /**
     * Review finding F4: every progress WRITE used to be an independent `writeScope.launch {}` —
     * correct per-call, but with no ordering guarantee ACROSS calls (`Dispatchers.Default` is a
     * thread pool, not FIFO). D85 Decision 6 write-schedule item (4) depends on the OUTGOING lesson's
     * flush actually landing before the INCOMING lesson's own load-flush (`POST .../position`'s
     * `currentLessonId` side effect is what makes a lesson load the server-side resume point) — a
     * property the shipped tests only appeared to prove because the injected test `writeScope`
     * happened to use a FIFO `StandardTestDispatcher`. This queue makes the ordering real by
     * construction: every write enqueues a job here, and exactly ONE consumer coroutine (started
     * below, on [writeScope], so it is never cancelled either) drains it strictly in enqueue order.
     * `Channel.UNLIMITED` — writes must never be dropped for capacity reasons; the only acceptable
     * drop is `shared`'s own documented throttle, never a queue overflow.
     */
    private val writeQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    /** The single write-queue consumer's own [Job] — review finding (round 4, MEDIUM): tracked as a
     *  property (rather than a fire-and-forget `writeScope.launch {}` inside [init]) specifically so
     *  [onCleared] can wait for it to finish draining before cancelling [writeScope] itself. A first
     *  draft never cancelled [writeScope] at all, leaving the consumer (and anything else launched on
     *  it, like a still-pending [flushLessonLoad] must-land retry) alive for the rest of the process
     *  after every visit to this screen. */
    private val consumerJob: Job = writeScope.launch {
        for (job in writeQueue) {
            try {
                job()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Review finding (round 2): a single write job throwing used to kill this loop
                // permanently — every write queued AFTER it (including `onCleared()`'s own final
                // flush, whenever it eventually happens) would then silently vanish into a channel
                // with no reader. The previous per-write `writeScope.launch {}` design isolated
                // each write via its own `SupervisorJob`; this restores the same isolation for the
                // single-consumer queue.
            }
        }
    }

    init {
        observeController()
        load()
    }

    /** Enqueues [job] onto [writeQueue] — see that property's own kdoc for why this replaces a bare
     *  `writeScope.launch {}` for every progress write. */
    private fun enqueueWrite(job: suspend () -> Unit) {
        writeQueue.trySend(job)
    }

    fun onRetry() = load()

    // ---- Load / lesson resolution (D85 Decision 7) -------------------------------------------

    private fun load() {
        _uiState.update { it.copy(content = CoursePlayerContentState.Loading) }
        viewModelScope.launch {
            try {
                val loadedCourse = when (val result = getCourseDetails(courseId)) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Failure -> return@launch setError(result.code)
                }
                val loadedProgress = when (val result = getCourseProgress(courseId)) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Failure -> return@launch setError(result.code)
                }
                val loadedQuiz = when (val result = getQuiz(courseId)) {
                    is ApiResult.Success -> result.data
                    is ApiResult.Failure -> return@launch setError(result.code)
                }
                course = loadedCourse
                progress = loadedProgress
                quiz = loadedQuiz

                val targetLessonId: String
                val startPositionSeconds: Int
                if (initialLessonId == null) {
                    when (val result = resumeCourse(courseId)) {
                        is ApiResult.Success -> when (val target = result.data) {
                            is LessonProgressTarget.LessonTarget -> {
                                targetLessonId = target.lessonId
                                startPositionSeconds = target.positionSeconds ?: 0
                            }
                            LessonProgressTarget.CourseFinished -> return@launch setCourseCompleted(loadedCourse, loadedQuiz, loadedProgress)
                        }
                        is ApiResult.Failure -> return@launch setError(result.code)
                    }
                } else {
                    targetLessonId = initialLessonId
                    // D85 Decision 7's exact rule (`course-player-screen.tsx:127-129`): resume
                    // position only applies when the server's OWN current-lesson pointer matches the
                    // requested lesson id, never blindly for any requested lesson.
                    startPositionSeconds = if (loadedProgress.currentLessonId == targetLessonId) {
                        loadedProgress.currentPositionSeconds ?: 0
                    } else {
                        0
                    }
                }

                if (findLesson(loadedCourse, targetLessonId) == null) {
                    return@launch setError(ApiErrorCode.LessonNotFound)
                }
                activateLesson(loadedCourse, targetLessonId, startPositionSeconds)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Review finding (round 4, HIGH): none of the calls above are wrapped in `ApiResult`
                // at the transport layer — `shared`'s `ApiClient` reads the response body OUTSIDE its
                // own try/catch (confirmed during round 2's investigation of a related finding), so a
                // connection drop mid-body is a genuine, reachable uncaught exception here, not a
                // theoretical one. Left uncaught, it would propagate out of this `viewModelScope
                // .launch` root coroutine with no handler — on Android, generally a process crash.
                setError(ApiErrorCode.Unknown("LOAD_FAILED"))
            }
        }
    }

    private fun setError(code: ApiErrorCode) {
        _uiState.update { it.copy(content = CoursePlayerContentState.Error(code)) }
    }

    /** Review finding (round 3, LOW): now `suspend` to [PlaybackController.stop] whatever media is
     *  still loaded — a first draft only nulled [activeLessonId] (which does block the write-schedule
     *  collectors and transport-control guards, so there is no CORRECTNESS gap), but left the last
     *  lesson's audio free to keep playing behind the course-completed UI if the student reaches this
     *  state (e.g. [CoursePlayerViewModel.onFinishCourseTapped]) while it is still playing. */
    private suspend fun setCourseCompleted(course: Course, quiz: QuizLookupResult, progress: CourseProgress) {
        // Review finding (round 4, HIGH, part of #2): invalidates any [activateLesson] call already
        // in flight (a lesson switch that was tapped just before the course finished, say). Without
        // this, that call could still reach its own commit afterward — [switchGeneration] would not
        // catch it (nothing here previously touched that counter) — and silently replace this
        // [CoursePlayerContentState.CourseCompleted] state back with [CoursePlayerContentState.Ready].
        switchGeneration.incrementAndGet()
        // Review finding (round 5, LOW/MEDIUM): the rest of this commit is now inside ONE
        // non-suspending `Main.immediate` block, same pattern as [activateLesson]'s own round-4 fix
        // — a first draft split `controller.stop()` (Main-confined) from `activeLessonId`/
        // `preparedLessonId`/`_uiState.update` (not), which on the `writeScope`-originated path is a
        // genuine Main->Default dispatch gap a concurrent commit could land inside.
        withContext(Dispatchers.Main.immediate) {
            controller.stop()
            activeLessonId = null
            preparedLessonId = null
            _uiState.update {
                it.copy(
                    content = CoursePlayerContentState.CourseCompleted(
                        CoursePlayerCourseCompletedState(
                            courseId = course.id,
                            courseTitle = course.title,
                            quizRow = quizRow(quiz),
                            quizPassed = progress.quizPassed,
                        ),
                    ),
                )
            }
        }
    }

    /**
     * Loads [lessonId] into [controller] (if it has a video) and rebuilds [uiState] around it.
     * Also performs D85 Decision 6 write-schedule item (1) — "once per lesson load, with the
     * resolved start position" — via [flushLessonLoad], which is also what makes this the
     * server-side resume point (`POST .../position` sets `currentLessonId`).
     *
     * Safe to call from either [viewModelScope] (the initial load, [onLessonSelected]) or
     * [writeScope] (the post-`completeLesson` auto-advance path) — see this class's own kdoc on
     * thread confinement for why the [controller] call is always explicitly hopped to
     * [Dispatchers.Main.immediate] here rather than assumed from the caller's context.
     *
     * **[activeLessonId]/[lastFlushedPositionSeconds]/[preparedLessonId] flip LATE — review findings
     * F1 + F2, not at the top of this function like a first draft had them.** Flipping them
     * immediately, BEFORE the `getLessonPlaybackSource` network call resolves, created two real bugs:
     * (F2) the heartbeat collector in [observeController] reads [activeLessonId] to decide who a
     * position update belongs to — with it already pointing at the NEW lesson while [controller] was
     * still playing the OLD lesson's still-loaded media, a heartbeat landing in that window wrote a
     * fabricated position against the wrong lesson; (F1) if no new media ends up being prepared at all
     * (no [Lesson.videoMediaId], or the fetch fails), the OLD media is left loaded and can still reach
     * [PlaybackState.Ended] on its own — misattributed to the NEW [activeLessonId], marking a lesson
     * the student never watched as complete.
     *
     * **Round 2 — [preparedLessonId] + [switchGeneration], because pausing and flipping late alone
     * were still not enough.** A reviewer probe against the real class found: (a) [PlaybackController
     * .pause] alone left the outgoing media loaded and resumable via the unguarded [onPlayPauseToggle]
     * — one extra tap could still replay it into `Ended` under the new lesson's id, so this now calls
     * [PlaybackController.stop] instead, which genuinely unloads it; (b) EVERY position read this
     * class performs (heartbeats, pause-flushes, [onLessonSelected]'s outgoing flush,
     * [onScreenLeaving]/[onCleared]) is now gated through [currentControllerPositionSecondsFor] —
     * structural protection that does not depend on [controller] having no stale media left to
     * misbehave with; (c) tapping a DIFFERENT lesson while a switch is already in flight is a real,
     * legitimate case [pendingLessonId] alone cannot guard (it only catches the SAME target) — without
     * [switchGeneration], whichever of the two in-flight `getLessonPlaybackSource` calls happened to
     * resolve LAST would win, not whichever the user tapped last. (The ordering guarantee this relies
     * on — the LAST-started call's generation is always the highest — depends on `viewModelScope`'s
     * dispatcher itself being FIFO, true for the real `Main.immediate` looper in production, the same
     * kind of dispatcher assumption F4 was raised about elsewhere in this class.)
     *
     * **Round 3 — a reviewer probe found this round's own fixes left three new gaps.** (i)
     * [pendingLessonId] was cleared only on the COMMIT path, never on either generation bail-out —
     * a superseded call's own target lesson id got stuck in [pendingLessonId] forever, permanently
     * rejecting every future tap on it in [onLessonSelected]. (ii)/(iii) the no-video branch's own
     * `preparedLessonId = null` and its generation re-check were each one step removed from the
     * `withContext` that actually mattered.
     *
     * **Round 4 — a THIRD reviewer probe found round 3's own multi-check design still had a gap
     * between checking and committing.** Splitting "check the generation" from "touch `controller`
     * and commit state" across a suspension point — even a SECOND, closer check right after that
     * hop — still left a genuine window: on the `writeScope`-originated auto-advance path,
     * `withContext(Main.immediate)` is a real dispatch, so a fully INDEPENDENT, later-started switch
     * (from `viewModelScope`, e.g. an explicit tap) could run its own ENTIRE check-and-commit to
     * completion inside that gap, and this call's now-stale `prepareLesson`/`stop()` would still
     * physically execute afterward and overwrite what the newer switch had just committed —
     * `preparedLessonId` and the real player state specifically — leaving `activeLessonId` pointing
     * at the newer lesson while the PLAYER was actually showing this older, abandoned one.
     *
     * The fix: there is now exactly ONE `withContext(Main.immediate)` block, and it contains the
     * ENTIRE commit — the final generation check, the `prepareLesson`/`stop()` call, and every field
     * write this function makes (including [flushLessonLoad] and [rebuildReadyState], neither of
     * which itself suspends). `Main.immediate` never yields mid-block, so once execution is inside
     * it, nothing else can interleave with this specific commit — it is atomic with respect to every
     * OTHER `activateLesson` call's own commit, regardless of which scope started either one. The
     * whole function is also now wrapped in `try`/`finally`, so [pendingLessonId] cleanup
     * ([clearPendingIfStillMine]) runs on literally every exit — the generation bail-out inside the
     * block, AND an uncaught exception from [getLessonPlaybackSource] (review finding, round 4,
     * part of #3) — rather than needing a separate call at each one.
     */
    private suspend fun activateLesson(course: Course, lessonId: String, startPositionSeconds: Int) {
        val myGeneration = switchGeneration.incrementAndGet()
        try {
            val lesson = findLesson(course, lessonId)
            val mediaId = lesson?.videoMediaId
            var videoLoadError: ApiErrorCode? = null
            var freshSource: Pair<String, PlaybackSource>? = null
            if (mediaId != null) {
                when (val sourceResult = getLessonPlaybackSource(mediaId)) {
                    is ApiResult.Success -> freshSource = mediaId to sourceResult.data
                    is ApiResult.Failure -> videoLoadError = sourceResult.code
                }
            }
            withContext(Dispatchers.Main.immediate) {
                // A later-started switch may have already superseded this one while the network call
                // above was in flight — bail out before ever touching `controller`, so the LAST tap
                // always wins regardless of which fetch happens to resolve first.
                if (switchGeneration.get() != myGeneration) return@withContext
                val source = freshSource
                val hasVideoSource: Boolean
                if (source != null) {
                    val (freshMediaId, playbackSource) = source
                    controller.prepareLesson(freshMediaId, playbackSource, startPositionSeconds.seconds)
                    preparedLessonId = lessonId
                    hasVideoSource = true
                } else {
                    // `stop()`, not `pause()` — see this function's own kdoc, round 2 / (a): genuinely
                    // unloads the outgoing media so an unguarded transport-control tap can never
                    // resume/replay it under the wrong lesson.
                    controller.stop()
                    preparedLessonId = null
                    hasVideoSource = false
                }
                activeLessonId = lessonId
                lastFlushedPositionSeconds = startPositionSeconds
                flushLessonLoad(lessonId, startPositionSeconds)
                rebuildReadyState(course, lessonId, hasVideoSource, videoLoadError)
            }
        } finally {
            clearPendingIfStillMine(lessonId)
        }
    }

    private fun rebuildReadyState(course: Course, lessonId: String, hasVideoSource: Boolean, videoLoadError: ApiErrorCode?) {
        val currentProgress = progress ?: return
        val currentQuiz = quiz ?: return
        val orderedLessons = orderedLessons(course)
        val lessonIndex = orderedLessons.indexOfFirst { it.lessonId == lessonId }
        if (lessonIndex == -1) return setError(ApiErrorCode.LessonNotFound)
        val lesson = orderedLessons[lessonIndex]
        val isComplete = lessonId in currentProgress.completedLessonIds
        val isLast = lessonIndex == orderedLessons.lastIndex
        val footerAction = deriveFooterAction(isComplete, isLast, currentQuiz, currentProgress.quizPassed)
        val sheet = buildSheetState(course, currentProgress, lessonId, currentQuiz)
        _uiState.update {
            it.copy(
                content = CoursePlayerContentState.Ready(
                    CoursePlayerReadyState(
                        courseId = course.id,
                        courseTitle = course.title,
                        currentLessonId = lessonId,
                        currentLessonTitle = lesson.title,
                        lessonNumber = lessonIndex + 1,
                        totalLessons = orderedLessons.size,
                        completionPercent = currentProgress.completionPercent,
                        isCurrentLessonCompleted = isComplete,
                        hasVideoSource = hasVideoSource,
                        videoLoadError = videoLoadError,
                        sheet = sheet,
                        footerAction = footerAction,
                    ),
                ),
            )
        }
    }

    // ---- Curriculum Bottom Sheet data (D85 Decision 8) ----------------------------------------

    private fun orderedLessons(course: Course): List<Lesson> =
        course.sections.sortedBy { it.order }.flatMap { section -> section.lessons.sortedBy { it.order } }

    private fun findLesson(course: Course, lessonId: String): Lesson? =
        orderedLessons(course).firstOrNull { it.lessonId == lessonId }

    private fun quizRow(quiz: QuizLookupResult): SheetQuizRow? =
        (quiz as? QuizLookupResult.Found)?.let { SheetQuizRow(questionCount = it.quiz.questions.size) }

    private fun buildSheetState(
        course: Course,
        progress: CourseProgress,
        currentLessonId: String,
        quiz: QuizLookupResult,
    ): CurriculumSheetState {
        var globalIndex = 0
        val sections = course.sections.sortedBy { it.order }.map { section ->
            SheetSection(
                sectionId = section.sectionId,
                title = section.title,
                order = section.order,
                lessons = section.lessons.sortedBy { it.order }.map { lesson ->
                    globalIndex += 1
                    SheetLesson(
                        lessonId = lesson.lessonId,
                        globalIndex = globalIndex,
                        title = lesson.title,
                        // "Current" wins over "Completed" when both are true (re-opening an already
                        // -finished lesson from the sheet) — a design call, not spelled out at this
                        // granularity by D85 Decision 8; flagged in the C2 report.
                        state = when {
                            lesson.lessonId == currentLessonId -> SheetLessonState.Current
                            lesson.lessonId in progress.completedLessonIds -> SheetLessonState.Completed
                            else -> SheetLessonState.NotStarted
                        },
                        hasVideo = lesson.videoMediaId != null,
                    )
                },
            )
        }
        return CurriculumSheetState(sections = sections, currentLessonId = currentLessonId, quizRow = quizRow(quiz))
    }

    // ---- Footer primary action (D85 Decision 9) ------------------------------------------------

    private fun deriveFooterAction(
        isCurrentLessonComplete: Boolean,
        isLastLesson: Boolean,
        quiz: QuizLookupResult,
        quizPassed: Boolean?,
    ): CoursePlayerFooterAction = when {
        !isCurrentLessonComplete -> CoursePlayerFooterAction.MarkComplete
        isLastLesson && quiz is QuizLookupResult.Found && quizPassed != true -> CoursePlayerFooterAction.TakeQuiz
        // Review finding F10: the last lesson, complete, with no quiz — D85 Decision 9's literal
        // 3-way rule has no case for this, not because it decided against one. `FinishCourse` so
        // the footer's own label matches what the tap actually does (see that case's own kdoc).
        isLastLesson -> CoursePlayerFooterAction.FinishCourse
        else -> CoursePlayerFooterAction.NextLesson
    }

    // ---- User actions ---------------------------------------------------------------------------

    /** Curriculum Bottom Sheet row tap — D85 Decision 7: "pure ViewModel state, never a new
     *  navigation destination." Flushes the OUTGOING lesson's progress first (write-schedule item
     *  (4)), then loads [lessonId]. Resume position for the newly-selected lesson follows the SAME
     *  rule [load] applies to the route's own `lessonId` (only if the server's current-lesson
     *  pointer already matches it) — an extension of D85 Decision 7's literal wording (which only
     *  states that rule for the initial route arg), not a separate invented behavior; flagged in the
     *  C2 report. */
    fun onLessonSelected(lessonId: String) {
        val loadedCourse = course ?: return
        val currentProgress = progress ?: return
        // Review finding F1/F2: also guard on `pendingLessonId`, not just `activeLessonId` — a
        // second tap on the same row while the first switch's `activateLesson` is still suspended
        // on the network (before `activeLessonId` itself has flipped) used to sail straight through
        // this check and race a second `activateLesson` against the first.
        if (lessonId == activeLessonId || lessonId == pendingLessonId) return
        pendingLessonId = lessonId
        val outgoingLessonId = activeLessonId
        if (outgoingLessonId != null) {
            currentControllerPositionSecondsFor(outgoingLessonId)?.let { flushProgress(outgoingLessonId, it) }
        }
        val startPositionSeconds = if (currentProgress.currentLessonId == lessonId) {
            currentProgress.currentPositionSeconds ?: 0
        } else {
            0
        }
        viewModelScope.launch {
            try {
                activateLesson(loadedCourse, lessonId, startPositionSeconds)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                // Review finding (round 4, HIGH, part of #3) — same reasoning as `load`'s own catch.
                // `activateLesson` itself already clears `pendingLessonId` unconditionally in its own
                // `finally`, so this only needs to surface the failure to the UI.
                setError(ApiErrorCode.Unknown("LESSON_SWITCH_FAILED"))
            }
        }
    }

    /** Footer tap while [CoursePlayerFooterAction.MarkComplete] is showing. */
    fun onMarkCompleteTapped() {
        val lessonId = activeLessonId ?: return
        completeLessonAndAdvance(lessonId)
    }

    /** Footer tap while [CoursePlayerFooterAction.NextLesson] is showing — the current lesson is
     *  already complete, so this reuses the SAME `completeLesson` call (idempotent by contract, per
     *  that use case's own kdoc) rather than re-deriving "what's next" locally; `shared`'s
     *  `CurriculumLessonResolver` stays the single source of truth for that either way. */
    fun onNextLessonTapped() {
        val lessonId = activeLessonId ?: return
        completeLessonAndAdvance(lessonId)
    }

    /** Footer tap while [CoursePlayerFooterAction.FinishCourse] is showing (review finding F10/#10:
     *  this 4th state previously had no dedicated entry point, leaving C3 to guess that
     *  [onNextLessonTapped] was the right call). The current (last) lesson is already complete and
     *  there is no quiz — unlike [onMarkCompleteTapped]/[onNextLessonTapped] this makes no further
     *  `completeLesson` call (there is nothing left to mark complete); it transitions straight to
     *  [CoursePlayerContentState.CourseCompleted] using the already-current [course]/[quiz]/
     *  [progress]. */
    fun onFinishCourseTapped() {
        val loadedCourse = course ?: return
        val currentQuiz = quiz ?: return
        val currentProgress = progress ?: return
        viewModelScope.launch { setCourseCompleted(loadedCourse, currentQuiz, currentProgress) }
    }

    /** Review finding F1 (round 2): no-ops when [preparedLessonId] doesn't match [activeLessonId] —
     *  there is no video prepared for the current lesson (a resource-only lesson, or the current
     *  lesson's own source fetch failed), so there is nothing for a transport-control tap to
     *  play/pause; without this guard a stray tap could resume whatever stale media [controller]
     *  physically still held before its [PlaybackController.stop] call. */
    fun onPlayPauseToggle() {
        if (activeLessonId == null || preparedLessonId != activeLessonId) return
        when (controller.state.value) {
            PlaybackState.Playing -> controller.pause()
            else -> controller.play()
        }
    }

    /** Same guard as [onPlayPauseToggle] — see that function's own kdoc. */
    fun onSeek(position: Duration) {
        if (activeLessonId == null || preparedLessonId != activeLessonId) return
        controller.seekTo(position)
    }

    /** Called by C3 from a `DisposableEffect`'s `onDispose` (leaving composition — a bottom-nav tab
     *  switch, which keeps this ViewModel and [controller] alive per D85's own note) or a
     *  `LifecycleEventObserver`'s `ON_STOP` (backgrounding) — D85 Decision 6 write-schedule item
     *  (5), "when the screen becomes invisible/disposed." Flushes only — never pauses or releases
     *  [controller]; a genuine, permanent teardown goes through [onCleared] instead. */
    fun onScreenLeaving() {
        val lessonId = activeLessonId ?: return
        val positionSeconds = currentControllerPositionSecondsFor(lessonId) ?: return
        flushProgress(lessonId, positionSeconds)
    }

    /** Same base behavior as [onScreenLeaving] (the flush), plus — review finding F5 — an explicit
     *  [PlaybackController.pause]: unlike [onScreenLeaving] (a same-app tab switch, where D85 wants
     *  playback to keep running per its own note), backgrounding the whole app is exactly the case
     *  D85 Decision 1's own audio-attributes note assumes will not silently keep a video's audio
     *  playing behind the home screen.
     *
     *  **C3 must gate its own `ON_STOP` call site on `!activity.isChangingConfigurations`**
     *  (`execution/DECISIONS_LOG.md` D85 Decision 6's own requirement) — a plain rotation delivers
     *  `ON_STOP` too, and this class has no [android.app.Activity] reference to enforce that guard
     *  itself. Before the F5 fix an unguarded call site only cost a redundant flush on rotation; now
     *  it would also pause playback on every rotation, which is a real regression C3 must not
     *  introduce. Called on the main thread by C3's `ON_STOP` lifecycle hook, same as every other
     *  direct [controller] call this class makes from a `viewModelScope` callback. */
    fun onScreenStopped() {
        controller.pause()
        onScreenLeaving()
    }

    // ---- Progress write schedule (D85 Decision 6) ----------------------------------------------

    private fun observeController() {
        viewModelScope.launch {
            controller.currentPosition.collect { position ->
                val lessonId = activeLessonId ?: return@collect
                // Review finding F2 (round 2): a position emission belongs to whatever media
                // [controller] currently holds, not necessarily [activeLessonId] — gate on
                // [preparedLessonId] so a tick that arrives mid-switch (or against a lesson with no
                // video at all) is never attributed to the wrong lesson.
                if (preparedLessonId != lessonId) return@collect
                val positionSeconds = position.inWholeSeconds.toInt()
                // Write-schedule item (2): every 15s of playback advance.
                if (positionSeconds - lastFlushedPositionSeconds >= FLUSH_INTERVAL_SECONDS) {
                    flushProgress(lessonId, positionSeconds)
                }
            }
        }
        viewModelScope.launch {
            controller.state.collect { newState ->
                val lessonId = activeLessonId
                val previous = previousPlaybackState
                // Bookkeeping stays unconditional (needed to correctly detect a FUTURE lesson's own
                // Playing->Paused transition), even though the actions below are gated.
                previousPlaybackState = newState
                if (lessonId == null || preparedLessonId != lessonId) return@collect
                when {
                    // Write-schedule item (3): on user pause.
                    newState is PlaybackState.Paused && previous is PlaybackState.Playing ->
                        currentControllerPositionSecondsFor(lessonId)?.let { flushProgress(lessonId, it) }
                    // Write-schedule item (6): on Ended, immediately followed by completeLesson.
                    newState is PlaybackState.Ended -> onLessonEnded(lessonId)
                    else -> Unit
                }
            }
        }
    }

    private fun onLessonEnded(lessonId: String) {
        currentControllerPositionSecondsFor(lessonId)?.let { flushProgress(lessonId, it) }
        completeLessonAndAdvance(lessonId)
    }

    /** Review finding F6: toggles [CoursePlayerReadyState.isCompletionInFlight] in place on whatever
     *  [uiState] currently holds — a no-op if a lesson switch has already moved [uiState] on to
     *  [CoursePlayerContentState.Loading]/[CoursePlayerContentState.CourseCompleted] by the time this
     *  lands, which is the correct behavior (there is no footer left to disable). */
    private fun setCompletionInFlight(inFlight: Boolean) {
        _uiState.update { state ->
            val ready = state.content as? CoursePlayerContentState.Ready ?: return@update state
            state.copy(content = ready.copy(state = ready.state.copy(isCompletionInFlight = inFlight)))
        }
    }

    /** Review finding F6 — see [CoursePlayerReadyState.completionError]'s own kdoc. */
    private fun setCompletionError(code: ApiErrorCode?) {
        _uiState.update { state ->
            val ready = state.content as? CoursePlayerContentState.Ready ?: return@update state
            state.copy(content = ready.copy(state = ready.state.copy(completionError = code)))
        }
    }

    /** Review finding F6/#11 — dismiss handle for [CoursePlayerReadyState.completionError]: C3
     *  should call this once its own transient error UI (a snackbar/inline banner) has been shown,
     *  since the error is otherwise sticky — it is not auto-cleared on a timer or recomposition,
     *  only by the next completion attempt succeeding or failing again. */
    fun onCompletionErrorDismissed() = setCompletionError(null)

    /** The one place `completeLesson` is called from — video-end auto-advance, "Mark Complete", and
     *  "Next lesson" (already-complete case) all resolve identically, since the use case is
     *  idempotent and its `autoAdvanceTarget` is `shared`'s single source of truth for "what's
     *  next" (D85 Decision 9 — "never a client-side guess"). Routed through [enqueueWrite] (review
     *  finding F4) rather than a bare `writeScope.launch {}` — `completeLesson` is itself a progress
     *  WRITE, D85 Decision 6's ordering guarantee applies to it exactly the same as
     *  [reportPlaybackPosition], and the [controller] load for the resolved next lesson via
     *  [activateLesson] (which itself hops back to the main thread internally) must run only after
     *  every write already queued for the OUTGOING lesson has actually landed. */
    private fun completeLessonAndAdvance(lessonId: String) {
        val loadedCourse = course ?: return
        // Review finding F6/#12: `isCompletionInFlight` used to be advisory-only — set, but never
        // actually checked — so two quick taps (or a tap racing the video-end auto-advance) could
        // enqueue two `completeLesson` calls for the same lesson, the second re-running
        // `activateLesson` for whatever the first's auto-advance target was and restarting playback.
        val alreadyInFlight = (_uiState.value.content as? CoursePlayerContentState.Ready)?.state?.isCompletionInFlight == true
        if (alreadyInFlight) return
        setCompletionInFlight(true)
        // Review finding (round 4, HIGH, part of #2): captured BEFORE the (possibly slow)
        // `completeLesson` network call. If the user explicitly selects a DIFFERENT lesson while
        // this call is still in flight, `switchGeneration` will have moved on by the time it
        // resolves — applying this call's own `autoAdvanceTarget` unconditionally would yank the
        // student away from wherever they just navigated, back to whatever the SERVER decided was
        // "next" for the lesson they were on when THIS completion attempt started. Progress itself
        // is still applied unconditionally below — that part is never stale, it's a real update
        // that must be sent regardless; only the NAVIGATION is guarded on this.
        //
        // Round 5 — a reviewer probe found `switchGeneration` equality alone is ONE-SIDED: it only
        // catches a switch that STARTS after this completion started, since `switchGeneration`
        // increments at the TOP of `activateLesson`, before that call's own (possibly slow)
        // `getLessonPlaybackSource` fetch. A switch that started BEFORE this completion but has not
        // committed YET already bumped the counter into `completionGeneration` itself — so the
        // switch's own eventual commit and this completion's auto-advance could both still land,
        // whichever resolves second silently overwriting the other's screen. `pendingLessonId` is
        // the load-bearing addition: it is non-null for the ENTIRE duration a switch is in flight
        // (set synchronously in `onLessonSelected`, cleared unconditionally in `activateLesson`'s own
        // `finally`), so `pendingLessonId == null` genuinely means "no switch is in flight right now"
        // — not merely "no switch started since this completion did".
        val completionGeneration = switchGeneration.get()
        enqueueWrite {
            // Review finding (round 3, MEDIUM): the write-consumer's own catch-all (see `init`'s
            // kdoc) keeps the QUEUE alive if `completeLesson` throws, but does nothing for THIS
            // class's own `isCompletionInFlight` flag — round 2's #12 fix made that flag load-bearing
            // (a true value now blocks every further tap), so an uncaught throw here used to latch it
            // true forever, silently re-introducing the exact dead-footer symptom F6 existed to fix,
            // just through a different door. `finally` guarantees it always clears; the `catch`
            // surfaces the failure the same way an explicit `ApiResult.Failure` already does.
            try {
                when (val result = completeLesson(courseId, lessonId)) {
                    is ApiResult.Success -> {
                        val outcome = result.data
                        progress = outcome.progress
                        setCompletionError(null)
                        if (switchGeneration.get() == completionGeneration && activeLessonId == lessonId && pendingLessonId == null) {
                            when (val target = outcome.autoAdvanceTarget) {
                                is LessonProgressTarget.LessonTarget ->
                                    activateLesson(loadedCourse, target.lessonId, target.positionSeconds ?: 0)
                                LessonProgressTarget.CourseFinished -> {
                                    val currentQuiz = quiz
                                    if (currentQuiz != null) setCourseCompleted(loadedCourse, currentQuiz, outcome.progress)
                                }
                            }
                        }
                    }
                    // Review finding F6: surfaced via `completionError` now, instead of a silent
                    // no-op — the lesson/footer state otherwise stays as it was, so a retry tap (or
                    // the next 15s heartbeat) can still succeed later; this only makes the failure
                    // visible in between.
                    is ApiResult.Failure -> setCompletionError(result.code)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                setCompletionError(ApiErrorCode.Unknown("COMPLETION_FAILED"))
            } finally {
                setCompletionInFlight(false)
            }
        }
    }

    /** The single choke point every ordinary progress WRITE goes through — always [enqueueWrite]
     *  (never a bare `writeScope.launch {}`, review finding F4), and — review finding F3 — now
     *  actually consumes the fresh [CourseProgress] [reportPlaybackPosition] returns on a genuine
     *  send, rather than discarding it. Discarding it left [progress] stale in memory in a way that
     *  was not merely "slightly out of date": [onLessonSelected]'s own resume-position rule
     *  (`currentProgress.currentLessonId == lessonId`) would then read a stale `currentLessonId` and
     *  compute the WRONG resume position for a re-selected lesson, and a subsequent flush built from
     *  that stale [progress] risked writing an OLDER position over a newer one server-side (the
     *  backend's `updatePosition` is a blind `set()`, not a `max()`). A throttled call
     *  (`reportPlaybackPosition` returning `null`) leaves [progress] untouched, which is correct —
     *  nothing changed server-side to reconcile. */
    private fun flushProgress(lessonId: String, positionSeconds: Int) {
        lastFlushedPositionSeconds = positionSeconds
        enqueueWrite {
            val result = reportPlaybackPosition(courseId, lessonId, positionSeconds)
            if (result is ApiResult.Success) progress = result.data
        }
    }

    /**
     * Write-schedule item (1) — the one progress write [activateLesson] depends on to actually
     * BECOME the server-side resume point (`POST .../position` is what sets `currentLessonId`).
     * Review finding F4: unlike every other progress write, this one cannot be allowed to silently
     * no-op against `shared`'s own 5-second throttle ([ReportPlaybackPositionUseCase]'s
     * `throttleWindow`, confirmed at `5.seconds` in its own source) — if the OUTGOING lesson's flush
     * ([onLessonSelected]'s write-schedule item (4), queued immediately ahead of this one via the
     * same [writeQueue]) landed less than 5 real seconds ago, this call would return `null` and the
     * server's `currentLessonId` pointer would silently keep pointing at the lesson the student just
     * left.
     *
     * **The retry runs OFF [writeQueue], not enqueued behind it (round 2).** A first draft ran the
     * `delay` + retry INSIDE the enqueued job, blocking the single consumer for the whole delay — a
     * reviewer probe measured a "Mark complete" tap landing 5 real seconds late because it queued
     * behind a throttled load-flush's retry, and worse, [onCleared]'s own final flush would queue
     * behind it too, risking losing that flush entirely if the process doesn't survive the wait. The
     * retry is scheduled as an independent [writeScope] coroutine instead — D85 Decision 6's ordering
     * guarantee is about writes relative to EACH OTHER at enqueue time, and by the time this retry
     * fires, every write that was actually enqueued at the same moment is long done.
     *
     * **Two staleness guards, because an unconditional retry can itself regress the server.** If
     * [activeLessonId] is no longer [lessonId] by the time the retry FIRES, the student has already
     * switched away — landing this retry now would move the server's resume pointer BACK to a lesson
     * they already left, exactly the bug this mechanism exists to prevent, just aimed at the wrong
     * lesson. And rather than resending the (by now possibly stale) [positionSeconds] this call was
     * originally invoked with, the retry reads [lastFlushedPositionSeconds] at fire time — the
     * freshest position anything has asked to flush for [lessonId] since — so a heartbeat that
     * already advanced the position in between can never get regressed back down by this retry
     * resending an old number. **Best-effort, not a hard guarantee** (round 3 wording correction — an
     * earlier draft of this kdoc, and `execution/DECISIONS_LOG.md` D87, both overstated this as
     * "never"): the guard checks [activeLessonId] before the retry's own network call, not after it
     * lands, so a switch away that happens WHILE the retry's own request is in flight can still leave
     * the server briefly pointing at the abandoned lesson — self-healing within `shared`'s own 5s
     * throttle once the NEW lesson's own load-flush (and, if needed, its own retry) lands. The single
     * retry can itself be throttled and silently dropped too, since `ReportPlaybackPositionUseCase`'s
     * throttle is per-instance (shared across lessons, not per-lesson) — any intervening write
     * re-arms it. "Must land" here means "one extra, staleness-guarded attempt", not a retry loop.
     *
     * **The DELAY runs off-queue; the retry's actual NETWORK CALL goes back onto it (round 4,
     * HIGH).** A round-2 draft fired the retry's `reportPlaybackPosition` call directly from the
     * detached, delayed coroutine below, entirely outside [writeQueue]. A reviewer probe found that
     * still regresses the server: the `activeLessonId` guard only prevents STARTING a stale retry,
     * not one that OVERLAPS IN FLIGHT with a newer write — a genuinely slow original request (or
     * this retry's own request) could complete AFTER a later lesson's own successful, already-landed
     * write, and its blind `set()` would silently move `currentLessonId` back to this abandoned
     * lesson. Re-enqueuing the actual send restores [writeQueue]'s single-consumer ordering guarantee
     * for the mutation itself (and its own try/catch protection, via [init]'s consumer loop — no
     * longer needing one here of its own), while the 5.1s DELAY — the part that must never block the
     * consumer — stays off-queue exactly as round 2 intended.
     */
    private fun flushLessonLoad(lessonId: String, positionSeconds: Int) {
        lastFlushedPositionSeconds = positionSeconds
        enqueueWrite {
            val result = reportPlaybackPosition(courseId, lessonId, positionSeconds)
            if (result is ApiResult.Success) {
                progress = result.data
            } else if (result == null) {
                writeScope.launch {
                    delay(MUST_LAND_RETRY_DELAY_MILLIS)
                    if (activeLessonId != lessonId) return@launch
                    enqueueWrite {
                        if (activeLessonId != lessonId) return@enqueueWrite
                        val retryResult = reportPlaybackPosition(courseId, lessonId, lastFlushedPositionSeconds)
                        if (retryResult is ApiResult.Success) progress = retryResult.data
                    }
                }
            }
        }
    }

    /**
     * D85 Decision 6's "Ordering rule for `onCleared()`": read the final position from the player
     * FIRST (via [PlaybackController.currentPositionNow] — the exact, synchronous read, never
     * [PlaybackController.currentPosition]'s possibly-stale last-collected value), THEN release the
     * player, with the flush itself launched on [writeScope] — which [viewModelScope]'s cancellation
     * (already underway by the time this method runs) can never reach. Releasing [controller] first
     * would lose the number.
     */
    /** Visibility deliberately widened from `protected` — [androidx.lifecycle.ViewModel.clear] (the
     *  usual production trigger for `onCleared()`) is `internal` in this androidx-lifecycle version,
     *  not callable from a JVM test in this module; making this override `public` lets
     *  `CoursePlayerViewModelTest` invoke it directly instead. */
    public override fun onCleared() {
        val lessonId = activeLessonId
        if (lessonId != null) {
            currentControllerPositionSecondsFor(lessonId)?.let { flushProgress(lessonId, it) }
        }
        controller.release()
        // Review finding (round 4, MEDIUM): a first draft never cancelled [writeScope] at all,
        // leaving [consumerJob] (and anything else launched on it, like a still-pending
        // [flushLessonLoad] must-land retry) alive for the rest of the process after every visit to
        // this screen. Closing [writeQueue] stops accepting new writes (a `trySend` after `close()`
        // silently no-ops) while letting the consumer drain everything already enqueued — including
        // the final flush just above — and exit its loop naturally; only once [consumerJob] actually
        // finishes does this cancel [writeScope] itself, which is what releases anything else it was
        // still keeping alive (a pending detached retry, most notably) — no longer meaningful once
        // this screen is gone for good. [MediaPlaybackController]'s own `released` guard already
        // makes every [controller] call a safe no-op post-[release], so a write still draining at
        // this point touching it is harmless, not a correctness risk — this is about not leaking the
        // scope/consumer/channel, not about a further data hazard.
        writeQueue.close()
        writeScope.launch {
            consumerJob.join()
            writeScope.cancel()
        }
    }

    private companion object {
        const val FLUSH_INTERVAL_SECONDS = 15

        /** See [flushLessonLoad]'s own kdoc — padded slightly past `ReportPlaybackPositionUseCase`'s
         *  confirmed `throttleWindow = 5.seconds`. */
        const val MUST_LAND_RETRY_DELAY_MILLIS = 5_100L
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `CourseDetailsViewModel.Factory`'s exact idiom.
     *  [application] must genuinely be the application [android.content.Context] (D85 Decision 1:
     *  "never the Activity — it outlives Activity re-creation"); [MediaPlaybackController] itself
     *  also defensively calls `.applicationContext` internally, but this Factory passes the already
     *  -application-scoped instance explicitly rather than relying on that alone. */
    class Factory(
        private val sdk: MentoraSdk,
        private val application: Application,
        private val courseId: String,
        private val lessonId: String?,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CoursePlayerViewModel(
            courseId = courseId,
            initialLessonId = lessonId,
            controller = MediaPlaybackController(
                context = application,
                refreshPlaybackUrl = sdk.media.refreshPlaybackUrl::invoke,
            ),
            getCourseDetails = sdk.catalog.getCourseDetails::invoke,
            getCourseProgress = sdk.progress.getCourseProgress::invoke,
            resumeCourse = sdk.progress.resumeCourse::invoke,
            getQuiz = sdk.quiz.getQuiz::invoke,
            getLessonPlaybackSource = sdk.media.getLessonPlaybackSource::invoke,
            reportPlaybackPosition = sdk.progress.reportPlaybackPosition::invoke,
            completeLesson = sdk.progress.completeLesson::invoke,
        ) as T
    }
}
