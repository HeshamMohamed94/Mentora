package com.mentora.android.ui.quiz

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.Quiz
import com.mentora.shared.domain.model.QuizAnswer
import com.mentora.shared.domain.model.QuizAttemptResult
import com.mentora.shared.domain.model.QuizOption
import com.mentora.shared.domain.model.QuizQuestion
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun quiz(count: Int = 2) = Quiz(
    questions = (1..count).map {
        QuizQuestion(
            questionId = "question-$it",
            prompt = "Prompt $it",
            order = it,
            options = listOf(QuizOption("option-$it-a", "A$it"), QuizOption("option-$it-b", "B$it")),
        )
    },
)

/**
 * Task 14 — [QuizViewModel]'s single-question-at-a-time flow/answer-persistence/submit logic, as a
 * plain JVM unit test. Mirrors `CourseDetailsViewModelTest`/`CoursePlayerViewModelTest`'s own
 * lambda-constructor-seam + hand-built-fake conventions (no mocking framework).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class QuizViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Round-1 review, HIGH-2 fix follow-up: QuizAttemptDraftStore is a genuine JVM-process-wide
        // singleton (see its own kdoc) — every test here builds a QuizViewModel with the same default
        // courseId, so a draft left behind by one test leaks into the next without this.
        QuizAttemptDraftStore.clearAllForTests()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        QuizAttemptDraftStore.clearAllForTests()
    }

    private fun buildViewModel(
        courseId: String = "course-1",
        lookup: QuizLookupResult = QuizLookupResult.Found(quiz()),
        getQuiz: suspend (String) -> ApiResult<QuizLookupResult> = { ApiResult.Success(lookup) },
        submittedAnswers: MutableList<List<QuizAnswer>> = mutableListOf(),
        submitResult: ApiResult<QuizAttemptResult> = ApiResult.Success(QuizAttemptResult(100, true, emptyList())),
        submitQuiz: suspend (String, Quiz, List<QuizAnswer>) -> ApiResult<QuizAttemptResult> = { _, _, answers ->
            submittedAnswers.add(answers)
            submitResult
        },
    ) = QuizViewModel(courseId = courseId, getQuiz = getQuiz, submitQuiz = submitQuiz)

    @Test
    fun load_success_rendersTheFirstQuestion_withNothingSelectedYet() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as QuizContentState.Ready).state
        assertEquals(1, ready.questionNumber)
        assertEquals(2, ready.totalQuestions)
        assertEquals("Prompt 1", ready.questionText)
        assertFalse(ready.isLastQuestion)
        assertFalse(ready.canSubmit)
        assertTrue(ready.options.none { it.isSelected })
    }

    @Test
    fun load_noQuiz_becomesTheDefensiveNoQuizState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(lookup = QuizLookupResult.NoQuiz)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.content is QuizContentState.NoQuiz)
    }

    @Test
    fun load_failure_becomesTheErrorState_withTheApiErrorCode() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getQuiz = { ApiResult.Failure(ApiErrorCode.ForbiddenNotEnrolled, "nope", null, 403) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.content as QuizContentState.Error
        assertEquals(ApiErrorCode.ForbiddenNotEnrolled, error.code)
    }

    @Test
    fun onOptionSelected_marksThatOptionSelected_andEnablesCanSubmit() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onOptionSelected("option-1-b")

        val ready = (viewModel.uiState.value.content as QuizContentState.Ready).state
        assertTrue(ready.canSubmit)
        assertTrue(ready.options.single { it.optionId == "option-1-b" }.isSelected)
        assertFalse(ready.options.single { it.optionId == "option-1-a" }.isSelected)
    }

    @Test
    fun onNextTapped_withNoSelection_isANoOp() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onNextTapped()

        val ready = (viewModel.uiState.value.content as QuizContentState.Ready).state
        assertEquals(1, ready.questionNumber)
    }

    @Test
    fun onNextTapped_advancesToTheNextQuestion_andTheLastQuestionOffersSubmit() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onOptionSelected("option-1-a")
        viewModel.onNextTapped()

        val ready = (viewModel.uiState.value.content as QuizContentState.Ready).state
        assertEquals(2, ready.questionNumber)
        assertTrue(ready.isLastQuestion)
        // A fresh question starts with nothing selected — this class's own per-question state, not a
        // stale carry-over from question 1.
        assertFalse(ready.canSubmit)
    }

    @Test
    fun answers_persistAcrossQuestions_andAreAllIncludedOnSubmit() = runTest(testDispatcher) {
        val submitted = mutableListOf<List<QuizAnswer>>()
        val viewModel = buildViewModel(submittedAnswers = submitted)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onOptionSelected("option-1-b")
        viewModel.onNextTapped()
        viewModel.onOptionSelected("option-2-a")
        var submittedCalled = false
        viewModel.onSubmitTapped { submittedCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(submittedCalled)
        val answers = submitted.single().associate { it.questionId to it.selectedOptionId }
        assertEquals(mapOf("question-1" to "option-1-b", "question-2" to "option-2-a"), answers)
    }

    @Test
    fun onSubmitTapped_failure_setsSubmitError_andResetsIsSubmitting_withoutInvokingOnSubmitted() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            submitResult = ApiResult.Failure(ApiErrorCode.Unknown("SUBMIT_FAILED"), "boom", null, 500),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onOptionSelected("option-1-a")
        viewModel.onNextTapped()
        viewModel.onOptionSelected("option-2-a")

        var submittedCalled = false
        viewModel.onSubmitTapped { submittedCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(submittedCalled)
        val ready = (viewModel.uiState.value.content as QuizContentState.Ready).state
        assertFalse(ready.isSubmitting)
        assertEquals(ApiErrorCode.Unknown("SUBMIT_FAILED"), ready.submitError)
    }

    @Test
    fun onSubmitErrorDismissed_clearsTheSubmitError() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            submitResult = ApiResult.Failure(ApiErrorCode.Unknown("SUBMIT_FAILED"), "boom", null, 500),
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onOptionSelected("option-1-a")
        viewModel.onNextTapped()
        viewModel.onOptionSelected("option-2-a")
        viewModel.onSubmitTapped {}
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSubmitErrorDismissed()

        val ready = (viewModel.uiState.value.content as QuizContentState.Ready).state
        assertNull(ready.submitError)
    }

    @Test
    fun onRetry_reloadsTheQuiz() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            getQuiz = {
                callCount++
                ApiResult.Success(QuizLookupResult.Found(quiz()))
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onRetry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, callCount)
    }

    // ---- Round-1 review, HIGH-2: answers must survive a plain pop-then-fresh-push of Quiz ---------

    @Test
    fun answersAndPosition_surviveAPopThenFreshPushOfQuiz_viaTheDraftStore() = runTest(testDispatcher) {
        val first = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        first.onOptionSelected("option-1-b")
        first.onNextTapped()
        // `first` is now "destroyed" (a plain back-button pop tears down its NavBackStackEntry's own
        // ViewModelStore) — no explicit teardown call exists to simulate that on a plain ViewModel, so
        // this constructs a second instance with the SAME courseId, exactly what a fresh push of
        // `Destination.Quiz(courseId)` after popping does.
        val second = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (second.uiState.value.content as QuizContentState.Ready).state
        // Resumed on question 2 (where `first` left off), not restarted at question 1.
        assertEquals(2, ready.questionNumber)

        // question 1's own answer also survived — submitting from `second` still includes it, proving
        // the draft store (not `first`'s own now-gone in-memory state) is what carried it forward.
        second.onOptionSelected("option-2-a")
        var submittedCalled = false
        second.onSubmitTapped { submittedCalled = true }
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(submittedCalled)
    }

    @Test
    fun onSubmitTapped_success_clearsTheDraftStore_soALaterFreshQuizStartsBlank() = runTest(testDispatcher) {
        val first = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        first.onOptionSelected("option-1-a")
        first.onNextTapped()
        first.onOptionSelected("option-2-a")
        first.onSubmitTapped {}
        testDispatcher.scheduler.advanceUntilIdle()

        // A later, genuinely fresh "Take Quiz" push (e.g. after a course-completion auto-navigate,
        // or a second course entirely reusing the same courseId in a hypothetical) must not resume
        // the already-submitted attempt's own leftover draft.
        val second = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (second.uiState.value.content as QuizContentState.Ready).state
        assertEquals(1, ready.questionNumber)
        assertTrue(ready.options.none { it.isSelected })
    }

    @Test
    fun onSubmitTapped_success_resetsIsSubmitting_soABackFromResultsDoesNotLandOnAStuckSpinner() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onOptionSelected("option-1-a")
        viewModel.onNextTapped()
        viewModel.onOptionSelected("option-2-a")
        viewModel.onSubmitTapped {}
        testDispatcher.scheduler.advanceUntilIdle()

        val ready = (viewModel.uiState.value.content as QuizContentState.Ready).state
        assertFalse(ready.isSubmitting)
    }
}
