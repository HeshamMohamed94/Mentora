package com.mentora.android.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.Quiz
import com.mentora.shared.domain.model.QuizAnswer
import com.mentora.shared.domain.model.QuizAttemptResult
import com.mentora.shared.domain.usecase.quiz.QuizLookupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One answer option, flattened to exactly what [com.mentora.android.ui.components.AnswerOption]
 *  needs — this screen never shows the Correct/Incorrect post-submit states (those only ever render
 *  on Quiz Results, a separate screen/fetch per this task's own routing decision), so [isSelected] is
 *  the only per-option signal this state carries. */
data class QuizOptionUi(val optionId: String, val text: String, val isSelected: Boolean)

/** [QuizContentState]'s loaded-and-answerable shape — deliberately flattened from the raw [Quiz]
 *  domain model (mirrors `CoursePlayerReadyState`'s own "UI-ready, not a raw domain passthrough"
 *  convention) so [QuizScreenContent] never has to re-derive "which question is current" itself. */
data class QuizReadyState(
    val questionNumber: Int,
    val totalQuestions: Int,
    val questionText: String,
    val options: List<QuizOptionUi>,
    val isLastQuestion: Boolean,
    val canSubmit: Boolean,
    val isSubmitting: Boolean,
    val submitError: ApiErrorCode?,
)

sealed interface QuizContentState {
    data object Loading : QuizContentState
    data class Error(val code: ApiErrorCode) : QuizContentState

    /** [QuizLookupResult.NoQuiz] — defensive-but-disclosed, same "shouldn't be reachable but must
     *  never crash" pattern this codebase already uses elsewhere (e.g. `CoursePlayerViewModel`'s own
     *  `SheetQuizRow?` handling): nav only ever routes here from Course Player's own `onTakeQuiz`,
     *  which itself only renders when `CoursePlayerFooterAction.TakeQuiz`/the curriculum sheet's quiz
     *  row exist — i.e. a quiz already confirmed present — but a stray/racing navigation could still
     *  reach this state, so it gets a real, non-crashing render rather than an assumed-unreachable
     *  `!!`/crash. */
    data object NoQuiz : QuizContentState

    data class Ready(val state: QuizReadyState) : QuizContentState
}

data class QuizUiState(val content: QuizContentState = QuizContentState.Loading)

/**
 * Task 14 — Quiz's ViewModel. Single-question-at-a-time flow (`ux/SCREEN_UX_SPECS.md § 11`): loads
 * the whole [Quiz] once, then walks its questions locally one at a time via [onNextTapped], never a
 * per-question network fetch.
 *
 * **Answers live in [QuizAttemptDraftStore], not in `rememberSaveable`/Compose `remember`/a plain
 * ViewModel field.** Round-1 review finding (HIGH-2): a plain ViewModel field only survives
 * recomposition of the still-mounted Quiz composable (a configuration change, a tab-switch-away-and
 * -back) — it does NOT survive a plain pop (the back button, or system back), which destroys this
 * `NavBackStackEntry`'s `ViewModelStore` entirely. `ux/NAVIGATION_SPEC.md § 6`'s "Quiz answers survive
 * back-navigation" rule is explicitly about THAT case — "Backing out of Quiz mid-attempt... preserves
 * selected answers" (`NAVIGATION_SPEC.md:116`) — so answers/[currentQuestionIndex] are read from and
 * written straight through to [QuizAttemptDraftStore] (a process-scoped, courseId-keyed singleton —
 * see its own kdoc), not this ViewModel's own state. A genuine new attempt (after "Retry Quiz" from
 * Quiz Results, or after a successful submission) correctly starts from a blank slate because
 * [QuizAttemptDraftStore.clear] is called from exactly those two paths — see [onSubmitTapped] and
 * `MentoraNavHost.kt`'s `quizResultsContent.onRetry` — matching `product/USER_FLOWS.md § 14`'s "a
 * fresh Quiz entry, fresh attempt".
 *
 * **Lambda-constructor seam** — same convention as `CourseDetailsViewModel`/`CoursePlayerViewModel`
 * (`QuizFacade`'s constructor is `internal`, so `:androidApp` cannot fake a [MentoraSdk] directly in a
 * JVM test); [Factory] wires these lambdas to the real `sdk.quiz.*` use cases in production.
 */
class QuizViewModel(
    private val courseId: String,
    private val getQuiz: suspend (String) -> ApiResult<QuizLookupResult>,
    private val submitQuiz: suspend (String, Quiz, List<QuizAnswer>) -> ApiResult<QuizAttemptResult>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    @Volatile private var quiz: Quiz? = null
    private var currentQuestionIndex = 0

    /** questionId -> selectedOptionId. Backed by [QuizAttemptDraftStore] (see this class's own kdoc,
     *  "Answers live in QuizAttemptDraftStore") — a live reference, so mutating this map mutates the
     *  store directly with no separate write-through step needed. */
    private val answers: MutableMap<String, String> = QuizAttemptDraftStore.answersFor(courseId)

    init {
        load()
    }

    fun onRetry() = load()

    private fun load() {
        _uiState.update { it.copy(content = QuizContentState.Loading) }
        viewModelScope.launch {
            when (val result = getQuiz(courseId)) {
                is ApiResult.Success -> when (val lookup = result.data) {
                    is QuizLookupResult.Found -> {
                        quiz = lookup.quiz
                        // Resume from the draft store's saved position (round-1 review, HIGH-2) rather
                        // than always restarting at question 1 — clamped defensively in case the quiz
                        // itself changed shape server-side since the draft was written.
                        val questions = orderedQuestions(lookup.quiz)
                        currentQuestionIndex = QuizAttemptDraftStore.currentQuestionIndexFor(courseId)
                            .coerceIn(0, (questions.size - 1).coerceAtLeast(0))
                        rebuildReadyState()
                    }
                    QuizLookupResult.NoQuiz -> _uiState.update { it.copy(content = QuizContentState.NoQuiz) }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(content = QuizContentState.Error(result.code)) }
            }
        }
    }

    private fun orderedQuestions(loadedQuiz: Quiz) = loadedQuiz.questions.sortedBy { it.order }

    private fun rebuildReadyState() {
        val loadedQuiz = quiz ?: return
        val questions = orderedQuestions(loadedQuiz)
        if (currentQuestionIndex !in questions.indices) return
        val question = questions[currentQuestionIndex]
        val selectedOptionId = answers[question.questionId]
        _uiState.update {
            it.copy(
                content = QuizContentState.Ready(
                    QuizReadyState(
                        questionNumber = currentQuestionIndex + 1,
                        totalQuestions = questions.size,
                        questionText = question.prompt,
                        options = question.options.map { option ->
                            QuizOptionUi(
                                optionId = option.optionId,
                                text = option.text,
                                isSelected = option.optionId == selectedOptionId,
                            )
                        },
                        isLastQuestion = currentQuestionIndex == questions.lastIndex,
                        canSubmit = selectedOptionId != null,
                        isSubmitting = false,
                        submitError = null,
                    ),
                ),
            )
        }
    }

    /** Answer-option tap — single-select, so this simply overwrites whatever was previously recorded
     *  for the current question. Enabled on every question, including one already answered (revisiting
     *  a question is not possible in this linear MVP flow, but changing the current question's own
     *  selection before tapping Next/Submit is always allowed). */
    fun onOptionSelected(optionId: String) {
        val loadedQuiz = quiz ?: return
        val questions = orderedQuestions(loadedQuiz)
        if (currentQuestionIndex !in questions.indices) return
        answers[questions[currentQuestionIndex].questionId] = optionId
        rebuildReadyState()
    }

    /** "Next" tap — disabled by the UI (per `ux/SCREEN_UX_SPECS.md § 11`'s own accessibility rule)
     *  until [QuizReadyState.canSubmit] is true, but this also no-ops defensively if called on the
     *  last question (where the UI should be showing "Submit Quiz"/[onSubmitTapped] instead) or with
     *  no selection recorded yet. */
    fun onNextTapped() {
        val ready = (_uiState.value.content as? QuizContentState.Ready)?.state ?: return
        if (ready.isLastQuestion || !ready.canSubmit) return
        currentQuestionIndex += 1
        QuizAttemptDraftStore.setCurrentQuestionIndex(courseId, currentQuestionIndex)
        rebuildReadyState()
    }

    /**
     * "Submit Quiz" tap (last question only). [answers] is passed as-built — the UI's disabled-CTA
     * gate already makes an incomplete submission structurally unreachable, and
     * `SubmitQuizUseCase`'s own local completeness validation is defense-in-depth on top of that (see
     * this class's own kdoc / that use case's kdoc), not the only guard.
     *
     * On success, [onSubmitted] is the caller's cue to navigate to Quiz Results — this ViewModel does
     * not navigate itself, same convention as every other screen's ViewModel in this app
     * (`AuthViewModel`'s own kdoc: navigation is the nav host's job, never a ViewModel's).
     */
    fun onSubmitTapped(onSubmitted: () -> Unit) {
        val loadedQuiz = quiz ?: return
        val ready = (_uiState.value.content as? QuizContentState.Ready)?.state ?: return
        if (ready.isSubmitting || !ready.canSubmit) return
        setSubmitting(true)
        viewModelScope.launch {
            val answerList = answers.map { (questionId, optionId) -> QuizAnswer(questionId, optionId) }
            when (val result = submitQuiz(courseId, loadedQuiz, answerList)) {
                is ApiResult.Success -> {
                    // Round-1 review, HIGH-2: this attempt is over — a later fresh "Take Quiz" push
                    // must not silently resume this already-submitted draft.
                    QuizAttemptDraftStore.clear(courseId)
                    // Round-1 review, LOW: `onSubmitted` is a plain forward `navigate` — Quiz itself
                    // stays on the back stack (`MentoraNavHost.kt`'s own comment on why) — so without
                    // this, a system back FROM Quiz Results lands on a Quiz screen whose CTA is stuck
                    // disabled-and-spinning forever (nothing else ever flips `isSubmitting` back).
                    setSubmitting(false)
                    onSubmitted()
                }
                is ApiResult.Failure -> {
                    setSubmitting(false)
                    setSubmitError(result.code)
                }
            }
        }
    }

    /** Dismiss handle for [QuizReadyState.submitError] — mirrors
     *  `CoursePlayerViewModel.onCompletionErrorDismissed`'s identical sticky-error convention: the
     *  screen shows it (a snackbar), then calls this once shown, rather than it auto-clearing. */
    fun onSubmitErrorDismissed() = setSubmitError(null)

    private fun setSubmitting(submitting: Boolean) {
        _uiState.update { state ->
            val ready = state.content as? QuizContentState.Ready ?: return@update state
            state.copy(content = ready.copy(state = ready.state.copy(isSubmitting = submitting)))
        }
    }

    private fun setSubmitError(code: ApiErrorCode?) {
        _uiState.update { state ->
            val ready = state.content as? QuizContentState.Ready ?: return@update state
            state.copy(content = ready.copy(state = ready.state.copy(submitError = code)))
        }
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `CourseDetailsViewModel.Factory`'s exact idiom. */
    class Factory(
        private val sdk: MentoraSdk,
        private val courseId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = QuizViewModel(
            courseId = courseId,
            getQuiz = sdk.quiz.getQuiz::invoke,
            submitQuiz = sdk.quiz.submitQuiz::invoke,
        ) as T
    }
}
