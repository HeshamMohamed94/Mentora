package com.mentora.android.ui.courseplayer

import androidx.lifecycle.viewModelScope
import com.mentora.android.playback.PlaybackController
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.Lesson
import com.mentora.shared.domain.model.LessonProgressTarget
import com.mentora.shared.domain.model.PlaybackSource
import com.mentora.shared.domain.model.PriceDisplay
import com.mentora.shared.domain.model.Quiz
import com.mentora.shared.domain.model.QuizOption
import com.mentora.shared.domain.model.QuizQuestion
import com.mentora.shared.domain.model.Section
import com.mentora.shared.domain.usecase.progress.LessonCompletionOutcome
import com.mentora.shared.domain.usecase.quiz.QuizLookupResult
import com.mentora.shared.playback.PlaybackState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Hand-built fake [PlaybackController] — same "no mocking framework, plain fakes" convention every
 *  other ViewModel test in this project already uses (see `CheckoutViewModelTest`'s own kdoc). */
private class FakePlaybackController : PlaybackController {
    val stateFlow = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = stateFlow

    val currentPositionFlow = MutableStateFlow(Duration.ZERO)
    override val currentPosition: StateFlow<Duration> = currentPositionFlow

    val durationFlow = MutableStateFlow<Duration?>(null)
    override val duration: StateFlow<Duration?> = durationFlow

    /** The value [currentPositionNow] returns — set directly by a test to simulate "the player is
     *  really at this exact position right now," independent of [currentPositionFlow]'s (possibly
     *  stale) ticker value, mirroring the real class's own documented gap between the two. */
    var positionNow: Duration = Duration.ZERO
    override fun currentPositionNow(): Duration = positionNow

    val prepareLessonCalls = mutableListOf<Triple<String, PlaybackSource, Duration>>()
    override fun prepareLesson(mediaId: String, source: PlaybackSource, startPosition: Duration) {
        prepareLessonCalls += Triple(mediaId, source, startPosition)
    }

    var playCalls = 0
    override fun play() {
        playCalls++
        stateFlow.value = PlaybackState.Playing
    }

    var pauseCalls = 0
    override fun pause() {
        pauseCalls++
        stateFlow.value = PlaybackState.Paused
    }

    val seekCalls = mutableListOf<Duration>()
    override fun seekTo(position: Duration) {
        seekCalls += position
        currentPositionFlow.value = position
    }

    /** Review finding F1 (round 2) — genuinely unloads media, unlike [pause]; resets [positionNow]/
     *  [currentPositionFlow] the same way the real `player.stop()` + `clearMediaItems()` would leave
     *  nothing to report a position for. */
    var stopCalls = 0
    override fun stop() {
        stopCalls++
        stateFlow.value = PlaybackState.Idle
        currentPositionFlow.value = Duration.ZERO
        positionNow = Duration.ZERO
    }

    var released = false
    override fun release() {
        released = true
    }
}

private fun lesson(id: String, order: Int, title: String = "Lesson $id", videoMediaId: String? = "media-$id") = Lesson(
    lessonId = id,
    title = title,
    description = "Description $id",
    order = order,
    videoMediaId = videoMediaId,
    resources = emptyList(),
)

private fun course(
    id: String = "course-1",
    sections: List<Section> = listOf(
        Section(
            sectionId = "section-1",
            title = "Section 1",
            order = 0,
            lessons = listOf(lesson("lesson-1", 0), lesson("lesson-2", 1)),
        ),
    ),
) = Course(
    id = id,
    title = "Course $id",
    description = "Description",
    categoryId = "category-1",
    level = CourseLevel.Beginner,
    contentLanguage = ContentLanguage.English,
    priceDisplay = PriceDisplay(amount = 999, currency = "EGP"),
    thumbnailMediaId = null,
    status = CourseStatus.Published,
    ratingSeed = 4.5,
    instructorId = "instructor-1",
    instructorName = "Instructor",
    sections = sections,
    translations = emptyMap(),
)

private fun progress(
    courseId: String = "course-1",
    completedLessonIds: List<String> = emptyList(),
    currentLessonId: String? = null,
    currentPositionSeconds: Int? = null,
    quizPassed: Boolean? = null,
    completionPercent: Int = 0,
) = CourseProgress(
    courseId = courseId,
    completedLessonIds = completedLessonIds,
    currentLessonId = currentLessonId,
    currentPositionSeconds = currentPositionSeconds,
    quizPassed = quizPassed,
    completionPercent = completionPercent,
    courseCompletedAt = null,
)

private fun playbackSource(mediaId: String = "media-lesson-1") = PlaybackSource(
    url = "https://example.test/media/$mediaId/stream",
    expiresAt = Instant.parse("2026-01-01T00:05:00Z"),
)

private fun quizWithQuestions(count: Int = 3) = Quiz(
    questions = (1..count).map {
        QuizQuestion(
            questionId = "question-$it",
            prompt = "Prompt $it",
            order = it,
            options = listOf(QuizOption("option-$it-a", "A"), QuizOption("option-$it-b", "B")),
        )
    },
)

/**
 * T13 "C2" — [CoursePlayerViewModel]'s lesson-resolution/write-schedule/auto-advance/footer-action
 * logic, as a plain JVM unit test. Wires the ViewModel's constructor lambdas (same lambda
 * -constructor seam as `CourseDetailsViewModel`/`CheckoutViewModel`) to hand-built fakes, and a fake
 * [PlaybackController] (see that class's own kdoc for why an interface extraction from
 * `MediaPlaybackController` was needed) rather than any real network or ExoPlayer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CoursePlayerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        courseId: String = "course-1",
        lessonId: String? = null,
        controller: PlaybackController = FakePlaybackController(),
        course: Course = course(courseId),
        progress: CourseProgress = progress(courseId),
        quiz: QuizLookupResult = QuizLookupResult.NoQuiz,
        getCourseDetails: suspend (String) -> ApiResult<Course> = { ApiResult.Success(course) },
        getCourseProgress: suspend (String) -> ApiResult<CourseProgress> = { ApiResult.Success(progress) },
        resumeCourse: suspend (String) -> ApiResult<LessonProgressTarget> = {
            ApiResult.Success(LessonProgressTarget.LessonTarget("lesson-1"))
        },
        getQuiz: suspend (String) -> ApiResult<QuizLookupResult> = { ApiResult.Success(quiz) },
        getLessonPlaybackSource: suspend (String) -> ApiResult<PlaybackSource> = { mediaId -> ApiResult.Success(playbackSource(mediaId)) },
        reportPlaybackPosition: suspend (String, String, Int) -> ApiResult<CourseProgress>? = { cId, lId, pos ->
            ApiResult.Success(progress.copy(currentLessonId = lId, currentPositionSeconds = pos))
        },
        completeLesson: suspend (String, String) -> ApiResult<LessonCompletionOutcome> = { cId, lId ->
            ApiResult.Success(
                LessonCompletionOutcome(
                    progress = progress.copy(completedLessonIds = progress.completedLessonIds + lId),
                    autoAdvanceTarget = LessonProgressTarget.CourseFinished,
                ),
            )
        },
        writeScope: CoroutineScope = CoroutineScope(SupervisorJob() + testDispatcher),
    ) = CoursePlayerViewModel(
        courseId = courseId,
        initialLessonId = lessonId,
        controller = controller,
        getCourseDetails = getCourseDetails,
        getCourseProgress = getCourseProgress,
        resumeCourse = resumeCourse,
        getQuiz = getQuiz,
        getLessonPlaybackSource = getLessonPlaybackSource,
        reportPlaybackPosition = reportPlaybackPosition,
        completeLesson = completeLesson,
        writeScope = writeScope,
    )

    // ---- Lesson resolution (D85 Decision 7) -----------------------------------------------------

    @Test
    fun nullLessonId_resolvesViaResumeCourse_andPreparesTheResolvedLesson() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(
            lessonId = null,
            controller = controller,
            resumeCourse = { ApiResult.Success(LessonProgressTarget.LessonTarget("lesson-2", positionSeconds = 42)) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-2", ready.currentLessonId)
        assertEquals(1, controller.prepareLessonCalls.size)
        assertEquals(42.seconds, controller.prepareLessonCalls.single().third)
    }

    @Test
    fun nullLessonId_courseFinished_resolvesToCourseCompletedState_neverPreparingAnyLesson() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(
            lessonId = null,
            controller = controller,
            quiz = QuizLookupResult.NoQuiz,
            resumeCourse = { ApiResult.Success(LessonProgressTarget.CourseFinished) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.content is CoursePlayerContentState.CourseCompleted)
        assertTrue(controller.prepareLessonCalls.isEmpty())
    }

    @Test
    fun nonNullLessonId_matchingCurrentLessonId_resumesAtTheServerPosition() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(
            lessonId = "lesson-2",
            controller = controller,
            progress = progress(currentLessonId = "lesson-2", currentPositionSeconds = 77),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-2", ready.currentLessonId)
        assertEquals(77.seconds, controller.prepareLessonCalls.single().third)
    }

    @Test
    fun nonNullLessonId_notMatchingCurrentLessonId_startsAtZero() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(
            lessonId = "lesson-2",
            controller = controller,
            // Server's current pointer is a DIFFERENT lesson than the one requested.
            progress = progress(currentLessonId = "lesson-1", currentPositionSeconds = 77),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-2", ready.currentLessonId)
        assertEquals(Duration.ZERO, controller.prepareLessonCalls.single().third)
    }

    @Test
    fun lessonIdNotInCurriculum_becomesTheErrorState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(lessonId = "does-not-exist")
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.content as CoursePlayerContentState.Error
        assertEquals(ApiErrorCode.LessonNotFound, error.code)
    }

    @Test
    fun courseLoadFailure_becomesTheErrorState_withTheApiErrorCode() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getCourseDetails = { ApiResult.Failure(ApiErrorCode.ForbiddenNotEnrolled, "nope", null, 403) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.content as CoursePlayerContentState.Error
        assertEquals(ApiErrorCode.ForbiddenNotEnrolled, error.code)
    }

    // ---- Write schedule (D85 Decision 6) --------------------------------------------------------

    @Test
    fun lessonLoad_flushesTheResolvedStartPosition_onceImmediately() = runTest(testDispatcher) {
        var reportedPositions = mutableListOf<Int>()
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            progress = progress(currentLessonId = "lesson-1", currentPositionSeconds = 30),
            reportPlaybackPosition = { _, _, pos -> reportedPositions.add(pos); ApiResult.Success(progress()) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(30), reportedPositions)
    }

    @Test
    fun playbackAdvancing15Seconds_triggersAFlush() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val reportedPositions = mutableListOf<Int>()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            reportPlaybackPosition = { _, _, pos -> reportedPositions.add(pos); ApiResult.Success(progress()) },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        reportedPositions.clear()

        controller.currentPositionFlow.value = 14.seconds
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("a sub-15s advance must not flush", reportedPositions.isEmpty())

        controller.currentPositionFlow.value = 15.seconds
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf(15), reportedPositions)
    }

    @Test
    fun userPause_afterPlaying_triggersAFlush_usingCurrentPositionNow() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val reportedPositions = mutableListOf<Int>()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            reportPlaybackPosition = { _, _, pos -> reportedPositions.add(pos); ApiResult.Success(progress()) },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        reportedPositions.clear()

        controller.positionNow = 8.seconds
        controller.stateFlow.value = PlaybackState.Playing
        testDispatcher.scheduler.advanceUntilIdle()
        controller.stateFlow.value = PlaybackState.Paused
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(8), reportedPositions)
    }

    @Test
    fun lessonSwitch_flushesTheOutgoingLesson_beforeLoadingTheIncomingOne() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val flushes = mutableListOf<Pair<String, Int>>()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            reportPlaybackPosition = { _, lId, pos -> flushes.add(lId to pos); ApiResult.Success(progress()) },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        flushes.clear()
        controller.positionNow = 55.seconds

        viewModel.onLessonSelected("lesson-2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("lesson-1" to 55, flushes.first())
        assertEquals("lesson-2" to 0, flushes.last())
        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-2", ready.currentLessonId)
    }

    @Test
    fun onLessonSelected_sameAsActiveLesson_isANoOp() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(controller = controller, lessonId = "lesson-1")
        testDispatcher.scheduler.advanceUntilIdle()
        val prepareCallsBefore = controller.prepareLessonCalls.size

        viewModel.onLessonSelected("lesson-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(prepareCallsBefore, controller.prepareLessonCalls.size)
    }

    /** Review findings F1 + F2 — the two HIGH-severity bugs from `activateLesson` flipping
     *  `activeLessonId` too early, before the incoming lesson's own `getLessonPlaybackSource` call
     *  resolves. This gates that call to hold the switch mid-flight, then simulates a heartbeat
     *  landing in exactly that window. */
    @Test
    fun lessonSwitch_neverAttributesAHeartbeatToTheIncomingLesson_whileItsSourceIsStillLoading() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val sourceGate = CompletableDeferred<Unit>()
        val flushes = mutableListOf<Pair<String, Int>>()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            reportPlaybackPosition = { _, lId, pos -> flushes.add(lId to pos); ApiResult.Success(progress()) },
            getLessonPlaybackSource = { mediaId ->
                if (mediaId == "media-lesson-2") sourceGate.await()
                ApiResult.Success(playbackSource(mediaId))
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        flushes.clear()
        controller.positionNow = 55.seconds

        viewModel.onLessonSelected("lesson-2")
        testDispatcher.scheduler.advanceUntilIdle()
        // The switch is now stuck mid-flight, gated on `sourceGate` — `activeLessonId` must still
        // read "lesson-1" at this point (see `activateLesson`'s own kdoc). A heartbeat crossing the
        // 15s threshold relative to the OUTGOING flush's own baseline (55) must land against
        // "lesson-1", never get fabricated against "lesson-2".
        controller.currentPositionFlow.value = 70.seconds
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            "no heartbeat should ever land against the incoming lesson before its own source " +
                "finishes loading, got: $flushes",
            flushes.none { it.first == "lesson-2" },
        )
        assertTrue("expected the mid-switch heartbeat to still land against the outgoing lesson, got: $flushes", flushes.any { it == "lesson-1" to 70 })

        sourceGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-2", ready.currentLessonId)
    }

    /** Review finding F1 — a lesson with no video source left the OUTGOING media still loaded and
     *  playing. Round 2: `pause()` alone was not enough (a later, unguarded [MediaPlaybackController
     *  .pause]-adjacent tap could still resume and replay it into [PlaybackState.Ended]) —
     *  [PlaybackController.stop] genuinely unloads it. */
    @Test
    fun lessonSwitch_toALessonWithNoVideo_stopsTheOutgoingPlayback() = runTest(testDispatcher) {
        val courseWithAVideolessSecondLesson = course(
            sections = listOf(
                Section(
                    sectionId = "section-1",
                    title = "Section 1",
                    order = 0,
                    lessons = listOf(lesson("lesson-1", 0), lesson("lesson-2", 1, videoMediaId = null)),
                ),
            ),
        )
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(controller = controller, lessonId = "lesson-1", course = courseWithAVideolessSecondLesson)
        testDispatcher.scheduler.advanceUntilIdle()
        controller.stateFlow.value = PlaybackState.Playing
        val stopCallsBefore = controller.stopCalls

        viewModel.onLessonSelected("lesson-2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue("switching to a video-less lesson must stop the still-loaded outgoing media", controller.stopCalls > stopCallsBefore)
        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertFalse(ready.hasVideoSource)
    }

    /** Review finding F1 (round 2): even after [PlaybackController.stop], a stray
     *  [CoursePlayerViewModel.onPlayPauseToggle]/[CoursePlayerViewModel.onSeek] call must not be able
     *  to resume/seek whatever [controller] happens to still hold — proven here on a FAKE that (unlike
     *  the real `ExoPlayer`) does NOT itself forget how to [FakePlaybackController.play] after
     *  [FakePlaybackController.stop], so this genuinely exercises the ViewModel's own guard, not the
     *  controller's. */
    @Test
    fun onPlayPauseToggleAndOnSeek_areNoOps_whenNoVideoIsPreparedForTheCurrentLesson() = runTest(testDispatcher) {
        val courseWithAVideolessLesson = course(
            sections = listOf(
                Section("section-1", "Section 1", 0, listOf(lesson("lesson-1", 0, videoMediaId = null))),
            ),
        )
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(controller = controller, lessonId = "lesson-1", course = courseWithAVideolessLesson)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPlayPauseToggle()
        viewModel.onSeek(10.seconds)

        assertEquals(0, controller.playCalls)
        assertEquals(0, controller.seekCalls.size)
    }

    /** Review finding #3 (round 2) — tapping a DIFFERENT lesson while a switch is already in flight
     *  is a real, legitimate case `pendingLessonId` alone cannot guard (it only catches the SAME
     *  target). Without `switchGeneration`, whichever of the two in-flight `getLessonPlaybackSource`
     *  calls happened to resolve LAST would win — this taps lesson-2 then lesson-3, resolves
     *  lesson-3's fetch FIRST, and proves lesson-2's late-arriving resolution (the user's own EARLIER
     *  tap) never wins over it. */
    @Test
    fun rapidLessonSwitch_toTwoDifferentTargets_alwaysLandsOnTheLastOneTapped() = runTest(testDispatcher) {
        val threeLessonCourse = course(
            sections = listOf(
                Section(
                    sectionId = "section-1",
                    title = "Section 1",
                    order = 0,
                    lessons = listOf(lesson("lesson-1", 0), lesson("lesson-2", 1), lesson("lesson-3", 2)),
                ),
            ),
        )
        val gates = mapOf("media-lesson-2" to CompletableDeferred<Unit>(), "media-lesson-3" to CompletableDeferred<Unit>())
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            course = threeLessonCourse,
            getLessonPlaybackSource = { mediaId ->
                gates[mediaId]?.await()
                ApiResult.Success(playbackSource(mediaId))
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onLessonSelected("lesson-2")
        viewModel.onLessonSelected("lesson-3")
        testDispatcher.scheduler.advanceUntilIdle()
        // lesson-3's fetch resolves FIRST; lesson-2's — the user's own EARLIER tap — resolves late.
        gates.getValue("media-lesson-3").complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        gates.getValue("media-lesson-2").complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-3", ready.currentLessonId)
        assertEquals("media-lesson-3", controller.prepareLessonCalls.last().first)
    }

    /** Review finding (round 3, HIGH) — a call that bails out on a stale `switchGeneration` must
     *  still clear `pendingLessonId`, or the tapped lesson becomes PERMANENTLY unselectable (every
     *  future tap on it silently rejected by [CoursePlayerViewModel.onLessonSelected]'s own
     *  re-entrancy guard). Reuses the exact "lesson-2 superseded by lesson-3" scenario above, then
     *  proves lesson-2 is selectable again afterward. */
    @Test
    fun supersededLessonSwitch_doesNotPermanentlyBlockReselectingItLater() = runTest(testDispatcher) {
        val threeLessonCourse = course(
            sections = listOf(
                Section(
                    sectionId = "section-1",
                    title = "Section 1",
                    order = 0,
                    lessons = listOf(lesson("lesson-1", 0), lesson("lesson-2", 1), lesson("lesson-3", 2)),
                ),
            ),
        )
        val gates = mapOf("media-lesson-2" to CompletableDeferred<Unit>(), "media-lesson-3" to CompletableDeferred<Unit>())
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            course = threeLessonCourse,
            getLessonPlaybackSource = { mediaId -> gates[mediaId]?.await(); ApiResult.Success(playbackSource(mediaId)) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onLessonSelected("lesson-2")
        viewModel.onLessonSelected("lesson-3")
        testDispatcher.scheduler.advanceUntilIdle()
        gates.getValue("media-lesson-3").complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        gates.getValue("media-lesson-2").complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()
        val readyOnLesson3 = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-3", readyOnLesson3.currentLessonId)

        // lesson-2's own switch was superseded and bailed out earlier — it must not be stuck
        // rejected forever.
        viewModel.onLessonSelected("lesson-2")
        testDispatcher.scheduler.advanceUntilIdle()

        val readyOnLesson2 = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-2", readyOnLesson2.currentLessonId)
    }

    /** Review finding (round 5, HIGH) — the round-4 completion-generation guard was ONE-SIDED: it
     *  only caught a switch that STARTED after the completion did (`switchGeneration` equality),
     *  not one that started BEFORE the completion but had not committed yet — that switch's own
     *  `switchGeneration.incrementAndGet()` was already baked into `completionGeneration` itself,
     *  so the equality check alone passed. Reproduces the exact scenario: the student taps a
     *  DIFFERENT lesson (its own source fetch gated, still genuinely in flight) right as the CURRENT
     *  lesson's video reaches its natural end, triggering an auto-advance completion targeting a
     *  THIRD lesson (whatever the server says is "next") — the student's own explicit tap must still
     *  win once it resolves, not get silently overwritten by the completion's own auto-advance. */
    @Test
    fun explicitLessonSwitch_startedBeforeAVideoEndCompletion_stillWinsOverItsAutoAdvanceTarget() = runTest(testDispatcher) {
        val threeLessonCourse = course(
            sections = listOf(
                Section(
                    sectionId = "section-1",
                    title = "Section 1",
                    order = 0,
                    lessons = listOf(lesson("lesson-1", 0), lesson("lesson-2", 1), lesson("lesson-3", 2)),
                ),
            ),
        )
        val sourceGate = CompletableDeferred<Unit>()
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            course = threeLessonCourse,
            progress = progress(completedLessonIds = emptyList()),
            getLessonPlaybackSource = { mediaId ->
                if (mediaId == "media-lesson-3") sourceGate.await()
                ApiResult.Success(playbackSource(mediaId))
            },
            completeLesson = { _, lId ->
                ApiResult.Success(
                    LessonCompletionOutcome(
                        progress = progress(completedLessonIds = listOf(lId)),
                        // The server's own "next lesson" — deliberately NOT what the student tapped.
                        autoAdvanceTarget = LessonProgressTarget.LessonTarget("lesson-2"),
                    ),
                )
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        // The student taps lesson-3 — its own source fetch is gated, still genuinely in flight.
        viewModel.onLessonSelected("lesson-3")
        testDispatcher.scheduler.advanceUntilIdle()

        // While that switch is still pending, lesson-1's video reaches its natural end, triggering
        // an auto-advance completion targeting lesson-2.
        controller.stateFlow.value = PlaybackState.Ended
        testDispatcher.scheduler.advanceUntilIdle()

        // Let the student's own tap finally resolve.
        sourceGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-3", ready.currentLessonId)
    }

    /** Review finding (round 4, HIGH, part of #2) — a slow `completeLesson` call's own
     *  `autoAdvanceTarget` must not override a DIFFERENT, later, EXPLICIT lesson selection the
     *  student made while it was still in flight; otherwise the student would be yanked away from
     *  wherever they just navigated, back to whatever the server decided was "next" for the lesson
     *  they were on when the completion attempt started. */
    @Test
    fun completionInFlight_doesNotOverrideAnExplicitLessonSwitchMadeWhileItWasWaiting() = runTest(testDispatcher) {
        val threeLessonCourse = course(
            sections = listOf(
                Section(
                    sectionId = "section-1",
                    title = "Section 1",
                    order = 0,
                    lessons = listOf(lesson("lesson-1", 0), lesson("lesson-2", 1), lesson("lesson-3", 2)),
                ),
            ),
        )
        val completeGate = CompletableDeferred<Unit>()
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            course = threeLessonCourse,
            progress = progress(completedLessonIds = emptyList()),
            completeLesson = { _, lId ->
                completeGate.await()
                ApiResult.Success(
                    LessonCompletionOutcome(
                        progress = progress(completedLessonIds = listOf(lId)),
                        autoAdvanceTarget = LessonProgressTarget.LessonTarget("lesson-2"),
                    ),
                )
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onMarkCompleteTapped()
        testDispatcher.scheduler.advanceUntilIdle()
        // The completion is stuck mid-flight (gated) — the student explicitly navigates elsewhere.
        viewModel.onLessonSelected("lesson-3")
        testDispatcher.scheduler.advanceUntilIdle()

        completeGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // The completion's own auto-advance target (lesson-2) must NOT override the student's own,
        // later, explicit choice (lesson-3).
        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-3", ready.currentLessonId)
    }

    /** Review finding (round 4, HIGH, part of #4) — the must-land retry's OUTER staleness check
     *  (`if (activeLessonId != lessonId) return@launch`, checked before the retry's actual network
     *  call is even re-enqueued onto [CoursePlayerViewModel.writeQueue]) must actually prevent the
     *  retry from firing a network call for a lesson the student switched away from BEFORE its 5.1s
     *  delay elapsed. **Round 5 correction:** an earlier version of this kdoc claimed this proved the
     *  INNER check (the one right before the re-enqueued job's own network call) instead — it
     *  doesn't; in this exact scenario (switch happens well before the delay elapses) the OUTER check
     *  already short-circuits first, so the job is never even re-enqueued for the inner check to run
     *  against. Verified by mutation: deleting the inner check alone still leaves this test (and the
     *  rest of this file) green. The inner check exists for the narrower window where the student
     *  switches away AFTER the outer check already passed but BEFORE the re-enqueued job is actually
     *  dequeued and run — not separately covered here. */
    @Test
    fun mustLandRetry_outerStalenessCheck_neverFiresItsNetworkCall_ifTheStudentSwitchedAwayFirst() = runTest(testDispatcher) {
        val callsByLesson = mutableListOf<String>()
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            progress = progress(currentLessonId = "lesson-1", currentPositionSeconds = 30),
            reportPlaybackPosition = { _, lId, pos ->
                callsByLesson.add(lId)
                if (lId == "lesson-1" && callsByLesson.count { it == "lesson-1" } == 1) null else ApiResult.Success(progress())
            },
        )
        // `runCurrent()`, not `advanceUntilIdle()` — the latter would also run out the retry's own
        // 5.1s virtual delay in this same step, defeating the point of this test (proving it never
        // fires once the student has switched away FIRST, not merely that it never fires at all).
        testDispatcher.scheduler.runCurrent()
        assertEquals(1, callsByLesson.count { it == "lesson-1" })

        // The student switches away WHILE the retry's delay is still pending. This itself triggers
        // ONE legitimate extra "lesson-1" write (the OUTGOING-lesson flush, write-schedule item (4))
        // — not what this test is about; only the pending RETRY's own call is under test here, so
        // the count is captured AFTER the switch rather than assumed to still be 1.
        viewModel.onLessonSelected("lesson-2")
        testDispatcher.scheduler.runCurrent()
        val countAfterSwitch = callsByLesson.count { it == "lesson-1" }

        // Advance far enough for the retry's original 5.1s delay to have elapsed.
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "the pending retry must never fire a further network call for the abandoned lesson-1, " +
                "got: $callsByLesson",
            countAfterSwitch,
            callsByLesson.count { it == "lesson-1" },
        )
    }

    /** Review finding (round 4, MEDIUM) — [CoursePlayerViewModel.onCleared] must eventually cancel
     *  the injected `writeScope` once its write queue has fully drained, rather than leaving it (and
     *  its single consumer coroutine) alive for the rest of the process after every screen visit. */
    @Test
    fun onCleared_eventuallyCancelsWriteScope_onceTheQueueHasDrained() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val writeScope = CoroutineScope(SupervisorJob() + testDispatcher)
        val viewModel = buildViewModel(controller = controller, lessonId = "lesson-1", writeScope = writeScope)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCleared()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(writeScope.isActive)
    }

    /** Review finding (round 3, MEDIUM) — the must-land retry runs OFF [CoursePlayerViewModel
     *  .writeQueue] specifically so it never blocks the single consumer, which also means it is NOT
     *  covered by that consumer's own catch-all. A throw from the retry attempt itself must not
     *  escape `writeScope` (which has no exception handler of its own) — this test's own
     *  `advanceUntilIdle()` call would itself fail/crash if it did. */
    @Test
    fun mustLandRetry_aThrowFromTheRetryAttempt_doesNotEscapeWriteScope() = runTest(testDispatcher) {
        var callCount = 0
        buildViewModel(
            lessonId = "lesson-1",
            progress = progress(currentLessonId = "lesson-1", currentPositionSeconds = 30),
            reportPlaybackPosition = { _, _, pos ->
                callCount++
                when (callCount) {
                    1 -> null
                    2 -> throw RuntimeException("boom")
                    else -> ApiResult.Success(progress())
                }
            },
        )

        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, callCount)
    }

    /** Review finding (round 3, MEDIUM) — an uncaught throw from `completeLesson` used to latch
     *  `isCompletionInFlight` `true` forever (the write-consumer's catch-all keeps the QUEUE alive,
     *  but does nothing for this class's own in-flight flag), silently reintroducing the dead-footer
     *  symptom finding F6 existed to fix in the first place. */
    @Test
    fun completeLessonThrowing_clearsCompletionInFlight_andSurfacesAnError() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            progress = progress(completedLessonIds = emptyList()),
            completeLesson = { _, _ -> throw RuntimeException("boom") },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onMarkCompleteTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertFalse(ready.isCompletionInFlight)
        assertNotNull(ready.completionError)

        // And a follow-up tap must not be permanently blocked by a flag that never cleared.
        viewModel.onMarkCompleteTapped()
        testDispatcher.scheduler.advanceUntilIdle()
    }

    /** Review finding (round 3, LOW) — reaching [CoursePlayerContentState.CourseCompleted] must stop
     *  playback, not merely null out `activeLessonId`; otherwise the last lesson's audio could keep
     *  playing behind the course-completed UI. */
    @Test
    fun reachingCourseCompleted_stopsPlayback() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-2",
            progress = progress(completedLessonIds = listOf("lesson-1", "lesson-2")),
            quiz = QuizLookupResult.NoQuiz,
        )
        testDispatcher.scheduler.advanceUntilIdle()
        controller.stateFlow.value = PlaybackState.Playing
        val stopCallsBefore = controller.stopCalls

        viewModel.onFinishCourseTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(controller.stopCalls > stopCallsBefore)
        assertTrue(viewModel.uiState.value.content is CoursePlayerContentState.CourseCompleted)
    }

    /** Review finding F3 — [flushProgress] used to discard the fresh [CourseProgress]
     *  `reportPlaybackPosition` returns, leaving the ViewModel's in-memory [progress] stale. Proven
     *  here via the one place that staleness was actually user-visible: a lesson re-selected after
     *  switching away resumed at the WRONG position because the resume rule reads
     *  `currentProgress.currentLessonId`/`currentPositionSeconds` from that same stale object. */
    @Test
    fun flushProgress_adoptsTheFreshReturnedProgress_soASubsequentLessonSwitchResumesAtTheRightPosition() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            progress = progress(currentLessonId = "lesson-1", currentPositionSeconds = 0),
            reportPlaybackPosition = { _, lId, pos ->
                // Lesson-2's own load-flush is deliberately left perpetually throttled (`null`) here
                // so it can never overwrite the in-memory `progress` this test is actually about —
                // isolating F3's fix (does the OUTGOING lesson-1 flush's fresh result get adopted at
                // all?) from D85's own, separately-correct rule that a successful lesson-2 load-flush
                // would legitimately move the server's resume pointer to lesson-2.
                if (lId == "lesson-1") ApiResult.Success(progress(currentLessonId = lId, currentPositionSeconds = pos)) else null
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        controller.positionNow = 88.seconds
        viewModel.onLessonSelected("lesson-2")
        testDispatcher.scheduler.advanceUntilIdle()

        // Switch back to lesson-1 — without F3's fix, the ViewModel's in-memory `progress` would
        // still be the STALE object captured at construction (position 0), so this would incorrectly
        // resume at 0 instead of the 88s the outgoing flush above just reported back.
        viewModel.onLessonSelected("lesson-1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(88.seconds, controller.prepareLessonCalls.last().third)
    }

    /** Review finding F4 — the lesson-load flush ([flushLessonLoad]) must not let itself be dropped
     *  by `shared`'s own 5s throttle the way every OTHER progress write may legitimately be, since it
     *  is what sets the server's `currentLessonId` resume pointer. Simulates a throttled first
     *  attempt (`null`) followed by a successful retry, and — review finding (round 2), a first draft
     *  of this test only checked the call COUNT, which would still pass even if the retry delay were
     *  accidentally shortened to something well under the throttle window — asserts the gap between
     *  the two attempts is genuinely at least the confirmed 5s window. */
    @Test
    fun lessonLoadFlush_retriesOnceAfterTheThrottleWindow_whenTheFirstAttemptIsThrottled() = runTest(testDispatcher) {
        var callCount = 0
        val callTimestamps = mutableListOf<Long>()
        val reportedPositions = mutableListOf<Int>()
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            progress = progress(currentLessonId = "lesson-1", currentPositionSeconds = 30),
            reportPlaybackPosition = { _, _, pos ->
                callCount++
                callTimestamps.add(testDispatcher.scheduler.currentTime)
                reportedPositions.add(pos)
                if (callCount == 1) null else ApiResult.Success(progress())
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(30, 30), reportedPositions)
        assertEquals(2, callTimestamps.size)
        assertTrue(
            "the retry must wait out the confirmed 5s throttle window, gap was ${callTimestamps[1] - callTimestamps[0]}ms",
            callTimestamps[1] - callTimestamps[0] >= 5_000L,
        )
    }

    /** Review finding F4 (round 2) — a first draft of this test drove `writeScope` on
     *  `Dispatchers.Unconfined` with a NEVER-SUSPENDING fake `reportPlaybackPosition` body, so each
     *  write completed synchronously the instant it was created; a standalone probe confirmed the
     *  OLD, pre-fix bare-`writeScope.launch {}` design (with no ordering guarantee at all) passes
     *  that exact assertion too, on the same dispatcher — it proved nothing. Gating the OUTGOING
     *  lesson's own write on a real suspension point ([CompletableDeferred]) is what actually
     *  distinguishes "ordered by construction" from "happened to run in order": while it's gated,
     *  the incoming lesson's load-flush must sit unprocessed behind it in [CoursePlayerViewModel
     *  .writeQueue]'s single consumer — a design with no such queue would let it run immediately in
     *  parallel instead. */
    @Test
    fun writeOrdering_isGuaranteedStructurally_evenOnANonFifoDispatcher() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val order = mutableListOf<Pair<String, Int>>()
        val outgoingFlushGate = CompletableDeferred<Unit>()
        var callCount = 0
        val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            writeScope = writeScope,
            reportPlaybackPosition = { _, lId, pos ->
                callCount++
                // Call #1 is the initial lesson-load flush (unrelated to this test) — only the
                // SECOND call (the outgoing-lesson flush triggered by the switch below) is gated.
                if (callCount == 2) outgoingFlushGate.await()
                order.add(lId to pos)
                ApiResult.Success(progress())
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        order.clear()
        controller.positionNow = 55.seconds

        viewModel.onLessonSelected("lesson-2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(
            "the incoming lesson's own load-flush must not land before the outgoing lesson's " +
                "gated flush resolves, got: $order",
            order.isEmpty(),
        )

        outgoingFlushGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("lesson-1" to 55, "lesson-2" to 0), order)
    }

    @Test
    fun videoEnded_flushesThenCompletesTheLesson_andAutoAdvancesToTheNextLesson() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        var completeCallCount = 0
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            progress = progress(),
            completeLesson = { _, lId ->
                completeCallCount++
                ApiResult.Success(
                    LessonCompletionOutcome(
                        progress = progress(completedLessonIds = listOf(lId)),
                        autoAdvanceTarget = LessonProgressTarget.LessonTarget("lesson-2"),
                    ),
                )
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        controller.positionNow = 20.seconds
        controller.stateFlow.value = PlaybackState.Playing
        testDispatcher.scheduler.advanceUntilIdle()
        controller.stateFlow.value = PlaybackState.Ended
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, completeCallCount)
        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-2", ready.currentLessonId)
        assertEquals("media-lesson-2", controller.prepareLessonCalls.last().first)
    }

    @Test
    fun videoEnded_autoAdvanceToCourseFinished_withAQuiz_exposesTheQuizRow() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            quiz = QuizLookupResult.Found(quizWithQuestions(5)),
            completeLesson = { _, lId ->
                ApiResult.Success(
                    LessonCompletionOutcome(
                        progress = progress(completedLessonIds = listOf(lId), quizPassed = false),
                        autoAdvanceTarget = LessonProgressTarget.CourseFinished,
                    ),
                )
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        controller.stateFlow.value = PlaybackState.Playing
        testDispatcher.scheduler.advanceUntilIdle()
        controller.stateFlow.value = PlaybackState.Ended
        testDispatcher.scheduler.advanceUntilIdle()

        val completed = (viewModel.uiState.value.content as CoursePlayerContentState.CourseCompleted).state
        assertEquals(5, completed.quizRow?.questionCount)
        assertEquals(false, completed.quizPassed)
    }

    @Test
    fun onNextLessonTapped_callsCompleteLesson_forTheActiveLesson_andAutoAdvances() = runTest(testDispatcher) {
        var completedLessonId: String? = null
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            completeLesson = { _, lId ->
                completedLessonId = lId
                ApiResult.Success(
                    LessonCompletionOutcome(
                        progress = progress(completedLessonIds = listOf(lId)),
                        autoAdvanceTarget = LessonProgressTarget.LessonTarget("lesson-2"),
                    ),
                )
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNextLessonTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("lesson-1", completedLessonId)
        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals("lesson-2", ready.currentLessonId)
    }

    /** Review finding (round 2) — a single write job throwing must not kill `writeQueue`'s one
     *  consumer permanently; every write queued after it (including `onCleared()`'s own final flush)
     *  depends on the consumer staying alive. */
    @Test
    fun aThrowingWriteJob_doesNotPermanentlyKillTheWriteConsumer() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        var callCount = 0
        val flushes = mutableListOf<Pair<String, Int>>()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            reportPlaybackPosition = { _, lId, pos ->
                callCount++
                if (callCount == 1) throw RuntimeException("boom")
                flushes.add(lId to pos)
                ApiResult.Success(progress())
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        // Call #1 (the initial lesson-load flush) threw inside the write consumer.
        assertEquals(1, callCount)
        flushes.clear()
        controller.positionNow = 40.seconds

        viewModel.onCleared()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("lesson-1" to 40), flushes)
        assertTrue(controller.released)
    }

    /** Review finding F6/#12 — `isCompletionInFlight` must actually be CHECKED, not merely set: a
     *  second tap while a completion is already in flight must be ignored, not enqueue a second
     *  concurrent `completeLesson` call. */
    @Test
    fun completeLessonAndAdvance_ignoresASecondTap_whileACompletionIsAlreadyInFlight() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        var completeCallCount = 0
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            progress = progress(completedLessonIds = emptyList()),
            completeLesson = { _, lId ->
                completeCallCount++
                gate.await()
                ApiResult.Success(
                    LessonCompletionOutcome(
                        progress = progress(completedLessonIds = listOf(lId)),
                        autoAdvanceTarget = LessonProgressTarget.CourseFinished,
                    ),
                )
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onMarkCompleteTapped()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onMarkCompleteTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, completeCallCount)
    }

    @Test
    fun onFinishCourseTapped_transitionsDirectlyToCourseCompleted_withNoFurtherCompleteLessonCall() = runTest(testDispatcher) {
        var completeCallCount = 0
        val viewModel = buildViewModel(
            lessonId = "lesson-2",
            progress = progress(completedLessonIds = listOf("lesson-1", "lesson-2")),
            quiz = QuizLookupResult.NoQuiz,
            completeLesson = { _, _ -> completeCallCount++; error("must not be called by onFinishCourseTapped") },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        val readyBefore = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals(CoursePlayerFooterAction.FinishCourse, readyBefore.footerAction)

        viewModel.onFinishCourseTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, completeCallCount)
        assertTrue(viewModel.uiState.value.content is CoursePlayerContentState.CourseCompleted)
    }

    @Test
    fun onCompletionErrorDismissed_clearsTheCompletionError() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            progress = progress(completedLessonIds = emptyList()),
            completeLesson = { _, _ -> ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onMarkCompleteTapped()
        testDispatcher.scheduler.advanceUntilIdle()
        val withError = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals(ApiErrorCode.InternalError, withError.completionError)

        viewModel.onCompletionErrorDismissed()

        val dismissed = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertNull(dismissed.completionError)
    }

    /** Review finding F6 — [CoursePlayerReadyState.isCompletionInFlight] is `true` for the duration
     *  of an in-flight `completeLesson` call, so C3 can disable the footer button rather than let a
     *  second tap race a concurrent completion attempt. */
    @Test
    fun onMarkCompleteTapped_setsCompletionInFlight_forTheDurationOfTheCall() = runTest(testDispatcher) {
        val gate = CompletableDeferred<Unit>()
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            progress = progress(completedLessonIds = emptyList()),
            completeLesson = { _, lId ->
                gate.await()
                ApiResult.Success(
                    LessonCompletionOutcome(
                        progress = progress(completedLessonIds = listOf(lId)),
                        autoAdvanceTarget = LessonProgressTarget.CourseFinished,
                    ),
                )
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onMarkCompleteTapped()
        testDispatcher.scheduler.advanceUntilIdle()
        val duringCall = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertTrue(duringCall.isCompletionInFlight)

        gate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        // Auto-advanced straight to CourseCompleted (no quiz) — there is no Ready state left to
        // carry `isCompletionInFlight`, which is itself the correct end state.
        assertTrue(viewModel.uiState.value.content is CoursePlayerContentState.CourseCompleted)
    }

    /** Review finding F6 — a failed `completeLesson` used to leave the footer looking dead (tap,
     *  nothing visibly happens); [CoursePlayerReadyState.completionError] now surfaces it. */
    @Test
    fun completeLessonFailure_surfacesCompletionError_andClearsCompletionInFlight() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            progress = progress(completedLessonIds = emptyList()),
            completeLesson = { _, _ -> ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onMarkCompleteTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertFalse(ready.isCompletionInFlight)
        assertEquals(ApiErrorCode.InternalError, ready.completionError)
        // The lesson/footer state is otherwise untouched — a retry tap can still succeed later.
        assertEquals("lesson-1", ready.currentLessonId)
    }

    /** F12 coverage gap: a lesson whose own `getLessonPlaybackSource` call fails leaves the rest of
     *  [CoursePlayerReadyState] fully usable, per that state's own kdoc on [CoursePlayerReadyState
     *  .videoLoadError]. */
    @Test
    fun videoSourceFetchFailure_exposesVideoLoadError_butKeepsTheRestOfReadyStateUsable() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            getLessonPlaybackSource = { ApiResult.Failure(ApiErrorCode.MediaNotFound, "gone", null, 404) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertFalse(ready.hasVideoSource)
        assertEquals(ApiErrorCode.MediaNotFound, ready.videoLoadError)
        assertEquals("lesson-1", ready.currentLessonId)
    }

    @Test
    fun onRetry_reRunsLoad_andCanRecoverFromAPriorFailure() = runTest(testDispatcher) {
        var shouldFail = true
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            getCourseDetails = { if (shouldFail) ApiResult.Failure(ApiErrorCode.CourseNotFound, "nope", null, 404) else ApiResult.Success(course()) },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.content is CoursePlayerContentState.Error)

        shouldFail = false
        viewModel.onRetry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.content is CoursePlayerContentState.Ready)
    }

    @Test
    fun onPlayPauseToggle_togglesBasedOnCurrentPlaybackState() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(controller = controller, lessonId = "lesson-1")
        testDispatcher.scheduler.advanceUntilIdle()

        controller.stateFlow.value = PlaybackState.Paused
        viewModel.onPlayPauseToggle()
        assertEquals(1, controller.playCalls)

        controller.stateFlow.value = PlaybackState.Playing
        viewModel.onPlayPauseToggle()
        assertEquals(1, controller.pauseCalls)
    }

    @Test
    fun onSeek_forwardsThePositionDirectlyToTheController() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val viewModel = buildViewModel(controller = controller, lessonId = "lesson-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSeek(42.seconds)

        assertEquals(listOf(42.seconds), controller.seekCalls)
    }

    @Test
    fun onScreenLeaving_flushesTheCurrentPosition_withoutPausingOrReleasing() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val flushes = mutableListOf<Pair<String, Int>>()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            reportPlaybackPosition = { _, lId, pos -> flushes.add(lId to pos); ApiResult.Success(progress()) },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        flushes.clear()
        controller.positionNow = 12.seconds

        viewModel.onScreenLeaving()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("lesson-1" to 12), flushes)
        assertEquals(0, controller.pauseCalls)
        assertFalse(controller.released)
    }

    /** Review finding F5 — backgrounding the whole app (as opposed to [onScreenLeaving]'s same-app
     *  tab switch) must also pause playback, not just flush the position. */
    @Test
    fun onScreenStopped_pausesPlayback_inAdditionToFlushing() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val flushes = mutableListOf<Pair<String, Int>>()
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            reportPlaybackPosition = { _, lId, pos -> flushes.add(lId to pos); ApiResult.Success(progress()) },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        flushes.clear()
        controller.stateFlow.value = PlaybackState.Playing
        controller.positionNow = 33.seconds

        viewModel.onScreenStopped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(controller.pauseCalls > 0)
        assertTrue(flushes.contains("lesson-1" to 33))
    }

    /**
     * The single most valuable test in this file, per this task's own brief: proves D85 Decision
     * 6's whole reason for [CoursePlayerViewModel]'s dedicated `writeScope` — that the final flush
     * `onCleared()` launches genuinely lands, driven only by `writeScope`'s own (separate,
     * independently-advanced) dispatcher, never by anything `viewModelScope` provides.
     *
     * **JVM-test limitation, disclosed rather than glossed over:** `androidx.lifecycle.ViewModel
     * .clear()` — the real production trigger that cancels `viewModelScope` and THEN calls
     * `onCleared()` — is `internal` in this project's pinned androidx-lifecycle version, not
     * reachable from this module's tests; [CoursePlayerViewModel.onCleared] is therefore called
     * directly (its visibility is widened to `public` for exactly this reason — see its own kdoc).
     * That means this test cannot literally observe `viewModelScope` already being dead at flush
     * time the way production teardown would leave it. What it DOES prove, precisely: the flush is
     * launched on `writeScope` — a `CoroutineScope` this test constructs completely independently of
     * anything `viewModelScope`-related — and genuinely completes via a real suspension point
     * ([CompletableDeferred], same technique as `CheckoutViewModelTest
     * .completePurchase_whileAlreadyProcessing_isIgnored...`) that only `writeScope`'s own dispatcher
     * resolves. Combined with [onClearedOrderingRule_readsPositionBeforeReleasing] (the read-before-
     * release ordering) and the production `onCleared()` body itself (which launches on `writeScope`
     * unconditionally, with no dependency on `viewModelScope`'s liveness), this is the strongest
     * proof available on this module's plain JVM test surface that D85 Decision 6's "don't lose a
     * completion event on rapid navigation away" risk is actually closed.
     *
     * **Review finding F13, strengthened further:** `writeScope` here now runs on its OWN
     * [TestCoroutineScheduler] — a genuinely separate virtual clock from `testDispatcher`'s (which
     * backs both `Dispatchers.Main` / `viewModelScope` and this test's own `runTest` body) — and
     * [viewModel.viewModelScope] is explicitly cancelled BEFORE [CoursePlayerViewModel.onCleared] is
     * called, mirroring production's real ordering (`ViewModel.clear()` cancels `viewModelScope`,
     * THEN calls `onCleared()`). The final assertion advances ONLY `writeDispatcher`'s scheduler —
     * never `testDispatcher`'s, which stays untouched (and whose backing scope is already dead) from
     * that point on — so this is no longer merely "launched on a scope that happens not to have been
     * cancelled yet"; it genuinely proves the flush survives `viewModelScope` already being gone.
     */
    @Test
    fun onCleared_stillFlushesTheFinalPosition_viaWriteScopeIndependentlyOfViewModelScope() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        val networkGate = CompletableDeferred<Unit>()
        var flushedPosition: Int? = null
        val writeDispatcher = StandardTestDispatcher(TestCoroutineScheduler())
        val writeScope = CoroutineScope(SupervisorJob() + writeDispatcher)
        val viewModel = buildViewModel(
            controller = controller,
            lessonId = "lesson-1",
            writeScope = writeScope,
            reportPlaybackPosition = { _, _, pos ->
                networkGate.await()
                flushedPosition = pos
                ApiResult.Success(progress())
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        writeDispatcher.scheduler.advanceUntilIdle()
        // The initial per-lesson-load flush is already in flight, gated on `networkGate` — let it
        // resolve before simulating rapid playback + an immediate navigation-away, so the assertion
        // below is unambiguously about the onCleared() flush, not the load-time one.
        networkGate.complete(Unit)
        writeDispatcher.scheduler.advanceUntilIdle()
        flushedPosition = null

        controller.positionNow = 91.seconds
        // Rapid navigation away: kill `viewModelScope` first (production's real ordering), then
        // tear down immediately — no delay, no extra advancing in between, exactly the "don't lose
        // it" scenario.
        viewModel.viewModelScope.cancel()
        viewModel.onCleared()
        writeDispatcher.scheduler.advanceUntilIdle()

        assertEquals(91, flushedPosition)
        assertTrue(controller.released)
    }

    @Test
    fun onClearedOrderingRule_readsPositionBeforeReleasing() = runTest(testDispatcher) {
        val controller = FakePlaybackController()
        var releasedBeforeFlushRead = false
        val orderedController = object : PlaybackController by controller {
            override fun currentPositionNow(): Duration {
                if (controller.released) releasedBeforeFlushRead = true
                return controller.positionNow
            }
        }
        val viewModel = buildViewModel(controller = orderedController, lessonId = "lesson-1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCleared()
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(releasedBeforeFlushRead)
        assertTrue(controller.released)
    }

    // ---- Footer primary-action derivation (D85 Decision 9) --------------------------------------

    @Test
    fun footerAction_incompleteLesson_isMarkComplete() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            progress = progress(completedLessonIds = emptyList()),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals(CoursePlayerFooterAction.MarkComplete, ready.footerAction)
    }

    @Test
    fun footerAction_completeLesson_notLast_isNextLesson() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            progress = progress(completedLessonIds = listOf("lesson-1")),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals(CoursePlayerFooterAction.NextLesson, ready.footerAction)
    }

    @Test
    fun footerAction_lastLessonComplete_quizExistsAndUnpassed_isTakeQuiz() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            lessonId = "lesson-2",
            progress = progress(completedLessonIds = listOf("lesson-1", "lesson-2"), quizPassed = false),
            quiz = QuizLookupResult.Found(quizWithQuestions(4)),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals(CoursePlayerFooterAction.TakeQuiz, ready.footerAction)
    }

    @Test
    fun footerAction_lastLessonComplete_quizAlreadyPassed_isFinishCourse_notTakeQuiz() = runTest(testDispatcher) {
        // Review finding F10: the last lesson, complete, with its quiz already passed has nothing
        // left to advance to — `FinishCourse`, not the `NextLesson` a first draft fell through to
        // (there is no next lesson; see `CoursePlayerFooterAction.FinishCourse`'s own kdoc).
        val viewModel = buildViewModel(
            lessonId = "lesson-2",
            progress = progress(completedLessonIds = listOf("lesson-1", "lesson-2"), quizPassed = true),
            quiz = QuizLookupResult.Found(quizWithQuestions(4)),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals(CoursePlayerFooterAction.FinishCourse, ready.footerAction)
    }

    @Test
    fun footerAction_lastLessonComplete_noQuiz_isFinishCourse() = runTest(testDispatcher) {
        // Review finding F10 — the 4th case D85 Decision 9's literal 3-way rule never considered.
        val viewModel = buildViewModel(
            lessonId = "lesson-2",
            progress = progress(completedLessonIds = listOf("lesson-1", "lesson-2")),
            quiz = QuizLookupResult.NoQuiz,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals(CoursePlayerFooterAction.FinishCourse, ready.footerAction)
    }

    // ---- Curriculum sheet assembly (D85 Decision 8) ----------------------------------------------

    @Test
    fun sheetState_assignsGlobalIndexAcrossSections_andOmitsTheQuizRowWhenThereIsNoQuiz() = runTest(testDispatcher) {
        val twoSectionCourse = course(
            sections = listOf(
                Section("section-1", "Section 1", 0, listOf(lesson("lesson-1", 0), lesson("lesson-2", 1))),
                Section("section-2", "Section 2", 1, listOf(lesson("lesson-3", 0))),
            ),
        )
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            course = twoSectionCourse,
            quiz = QuizLookupResult.NoQuiz,
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        val allLessons = ready.sheet.sections.flatMap { it.lessons }
        assertEquals(listOf(1, 2, 3), allLessons.map { it.globalIndex })
        assertEquals("lesson-3", allLessons.last().lessonId)
        assertNull(ready.sheet.quizRow)
    }

    @Test
    fun sheetState_marksCompletedAndCurrentLessons_correctly() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            lessonId = "lesson-2",
            progress = progress(completedLessonIds = listOf("lesson-1")),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        val byId = ready.sheet.sections.flatMap { it.lessons }.associateBy { it.lessonId }
        assertEquals(SheetLessonState.Completed, byId.getValue("lesson-1").state)
        assertEquals(SheetLessonState.Current, byId.getValue("lesson-2").state)
    }

    @Test
    fun sheetState_quizFound_exposesTheQuestionCount() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            lessonId = "lesson-1",
            quiz = QuizLookupResult.Found(quizWithQuestions(7)),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as CoursePlayerContentState.Ready).state
        assertEquals(7, ready.sheet.quizRow?.questionCount)
    }
}
