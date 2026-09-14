package com.mentora.android.ui.quiz

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.AttemptBreakdown
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.Quiz
import com.mentora.shared.domain.model.QuizAttemptResult
import com.mentora.shared.domain.model.QuizOption
import com.mentora.shared.domain.model.QuizQuestion
import com.mentora.shared.domain.usecase.quiz.LatestAttemptLookupResult
import com.mentora.shared.domain.usecase.quiz.QuizLookupResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun quiz() = Quiz(
    questions = listOf(
        QuizQuestion("question-1", "Prompt 1", 1, listOf(QuizOption("a", "A"), QuizOption("b", "B"))),
        QuizQuestion("question-2", "Prompt 2", 2, listOf(QuizOption("a", "A"), QuizOption("b", "B"))),
    ),
)

private fun attempt(passed: Boolean = true, breakdown: List<AttemptBreakdown> = listOf(
    AttemptBreakdown("question-1", "a", "a", true),
    AttemptBreakdown("question-2", "b", "a", false),
)) = QuizAttemptResult(score = if (passed) 100 else 50, passed = passed, breakdown = breakdown)

private fun progress(courseCompletedAt: String? = null) = CourseProgress(
    courseId = "course-1",
    completedLessonIds = emptyList(),
    currentLessonId = null,
    currentPositionSeconds = null,
    quizPassed = true,
    completionPercent = 100,
    courseCompletedAt = courseCompletedAt,
)

/**
 * Task 14 — [QuizResultsViewModel]'s re-fetch/breakdown-derivation/completion-banner logic, as a
 * plain JVM unit test. Same lambda-constructor-seam + hand-built-fake conventions as every other
 * ViewModel test in this project.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizResultsViewModelTest {

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
        latestAttempt: LatestAttemptLookupResult = LatestAttemptLookupResult.Found(attempt()),
        getLatestAttempt: suspend (String) -> ApiResult<LatestAttemptLookupResult> = { ApiResult.Success(latestAttempt) },
        quizLookup: QuizLookupResult = QuizLookupResult.Found(quiz()),
        getQuiz: suspend (String) -> ApiResult<QuizLookupResult> = { ApiResult.Success(quizLookup) },
        courseProgress: CourseProgress = progress(),
        getCourseProgress: suspend (String) -> ApiResult<CourseProgress> = { ApiResult.Success(courseProgress) },
    ) = QuizResultsViewModel(
        courseId = courseId,
        getLatestAttempt = getLatestAttempt,
        getQuiz = getQuiz,
        getCourseProgress = getCourseProgress,
    )

    @Test
    fun load_success_derivesScoreAndBreakdown_fromTheLatestAttempt() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as QuizResultsContentState.Ready).state
        assertEquals(1, ready.correctCount)
        assertEquals(2, ready.totalCount)
        assertTrue(ready.passed)
        assertEquals(2, ready.breakdown.size)
        assertEquals("Prompt 1", ready.breakdown[0].prompt)
        assertTrue(ready.breakdown[0].isCorrect)
        assertEquals("Prompt 2", ready.breakdown[1].prompt)
        assertFalse(ready.breakdown[1].isCorrect)
    }

    @Test
    fun load_passedAndCourseCompleted_showsTheCompletionBanner() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            latestAttempt = LatestAttemptLookupResult.Found(attempt(passed = true)),
            courseProgress = progress(courseCompletedAt = "2026-09-14T00:00:00Z"),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as QuizResultsContentState.Ready).state
        assertTrue(ready.showCompletionBanner)
    }

    @Test
    fun load_passedButCourseNotYetCompleted_hidesTheCompletionBanner() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            latestAttempt = LatestAttemptLookupResult.Found(attempt(passed = true)),
            courseProgress = progress(courseCompletedAt = null),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as QuizResultsContentState.Ready).state
        assertFalse(ready.showCompletionBanner)
    }

    @Test
    fun load_failed_neverShowsTheCompletionBanner_evenIfCourseCompletedAtIsSet() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            latestAttempt = LatestAttemptLookupResult.Found(attempt(passed = false)),
            courseProgress = progress(courseCompletedAt = "2026-09-14T00:00:00Z"),
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as QuizResultsContentState.Ready).state
        assertFalse(ready.passed)
        assertFalse(ready.showCompletionBanner)
    }

    @Test
    fun load_noQuizForPrompts_fallsBackToNullPrompts_ratherThanFailing() = runTest(testDispatcher) {
        val viewModel = buildViewModel(quizLookup = QuizLookupResult.NoQuiz)
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as QuizResultsContentState.Ready).state
        assertTrue(ready.breakdown.all { it.prompt == null })
    }

    @Test
    fun load_noAttemptYet_becomesTheDefensiveErrorState_ratherThanCrashing() = runTest(testDispatcher) {
        val viewModel = buildViewModel(latestAttempt = LatestAttemptLookupResult.NoAttemptYet)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.content is QuizResultsContentState.Error)
    }

    @Test
    fun load_attemptFetchFailure_becomesTheErrorState_withTheApiErrorCode() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getLatestAttempt = { ApiResult.Failure(ApiErrorCode.ForbiddenNotEnrolled, "nope", null, 403) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.content as QuizResultsContentState.Error
        assertEquals(ApiErrorCode.ForbiddenNotEnrolled, error.code)
    }

    @Test
    fun onRetry_reloads() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            getLatestAttempt = {
                callCount++
                ApiResult.Success(LatestAttemptLookupResult.Found(attempt()))
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onRetry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, callCount)
    }
}
