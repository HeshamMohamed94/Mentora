package com.mentora.android.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.AttemptBreakdown
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.QuizAttemptResult
import com.mentora.shared.domain.model.QuizQuestion
import com.mentora.shared.domain.usecase.quiz.LatestAttemptLookupResult
import com.mentora.shared.domain.usecase.quiz.QuizLookupResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One per-question breakdown row. [prompt] is `null` when the quiz's own question text couldn't be
 *  resolved (the defensive [QuizLookupResult.NoQuiz] branch below) — the UI falls back to a plain
 *  "Question N" label in that case, never a blank row. */
data class QuizResultsBreakdownRowUi(val questionNumber: Int, val prompt: String?, val isCorrect: Boolean)

data class QuizResultsReadyState(
    val correctCount: Int,
    val totalCount: Int,
    val passed: Boolean,
    val breakdown: List<QuizResultsBreakdownRowUi>,
    /** `true` when [passed] AND this attempt is the reason the course is now fully completed — see
     *  [QuizResultsViewModel]'s own kdoc on how this is derived. */
    val showCompletionBanner: Boolean,
)

sealed interface QuizResultsContentState {
    data object Loading : QuizResultsContentState
    data class Error(val code: ApiErrorCode) : QuizResultsContentState
    data class Ready(val state: QuizResultsReadyState) : QuizResultsContentState
}

data class QuizResultsUiState(val content: QuizResultsContentState = QuizResultsContentState.Loading)

/**
 * Task 14 — Quiz Results' ViewModel. Per this task's decision 1 (re-fetch, don't pass through
 * navigation): fetches [getLatestAttempt] to read back what Quiz's own `onSubmitTapped` just
 * persisted server-side — guaranteed consistent, since submission already completed before
 * navigation to this screen ever fires. Also fetches [getQuiz] (for real question prompt text —
 * [com.mentora.shared.domain.model.AttemptBreakdown] itself carries no prompt, only ids) and
 * [getCourseProgress] (for [QuizResultsReadyState.showCompletionBanner] — see below).
 *
 * **[QuizResultsReadyState.showCompletionBanner] derivation.** `ux/SCREEN_UX_SPECS.md § 12` wants the
 * banner "if passed AND this was the course's last completion condition." No API returns that literal
 * boolean, so this derives it from [CourseProgress.courseCompletedAt]: `passed && courseCompletedAt !=
 * null` on the FRESHLY-fetched progress. Since a certificate/course-completion is only ever issued once
 * every completion condition (all lessons + a passed quiz) is satisfied, a non-null
 * `courseCompletedAt` at the moment this screen loads — right after a passing submission — reliably
 * means this attempt is what completed it. **Disclosed edge case**: re-opening Quiz Results for an
 * already-long-completed course (e.g. re-viewing an old attempt's results, if that ever becomes
 * reachable) would also show the banner, since `courseCompletedAt` stays set forever once issued —
 * accepted as correct-if-imprecise (the course genuinely IS complete and the certificate genuinely IS
 * ready) rather than adding a "was this JUST completed" flag nothing in the API tracks.
 *
 * **Lambda-constructor seam** — same convention as every other ViewModel in this app.
 */
class QuizResultsViewModel(
    private val courseId: String,
    private val getLatestAttempt: suspend (String) -> ApiResult<LatestAttemptLookupResult>,
    private val getQuiz: suspend (String) -> ApiResult<QuizLookupResult>,
    private val getCourseProgress: suspend (String) -> ApiResult<CourseProgress>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuizResultsUiState())
    val uiState: StateFlow<QuizResultsUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onRetry() = load()

    private fun load() {
        _uiState.update { it.copy(content = QuizResultsContentState.Loading) }
        viewModelScope.launch {
            val attempt = when (val result = getLatestAttempt(courseId)) {
                is ApiResult.Success -> when (val lookup = result.data) {
                    is LatestAttemptLookupResult.Found -> lookup.result
                    // Defensive-but-disclosed, same "shouldn't be reachable but must never crash"
                    // pattern as `QuizViewModel`'s own `QuizContentState.NoQuiz` — this screen is only
                    // ever navigated to right after a real submission, but a stray/racing navigation
                    // could still reach here with nothing to show yet.
                    LatestAttemptLookupResult.NoAttemptYet ->
                        return@launch setError(ApiErrorCode.Unknown("NO_ATTEMPT_YET"))
                }
                is ApiResult.Failure -> return@launch setError(result.code)
            }
            val questionsById = when (val result = getQuiz(courseId)) {
                is ApiResult.Success -> when (val lookup = result.data) {
                    is QuizLookupResult.Found -> lookup.quiz.questions.associateBy { it.questionId }
                    QuizLookupResult.NoQuiz -> emptyMap()
                }
                is ApiResult.Failure -> return@launch setError(result.code)
            }
            val progress = when (val result = getCourseProgress(courseId)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> return@launch setError(result.code)
            }
            _uiState.update {
                it.copy(content = QuizResultsContentState.Ready(buildReadyState(attempt, questionsById, progress)))
            }
        }
    }

    private fun buildReadyState(
        attempt: QuizAttemptResult,
        questionsById: Map<String, QuizQuestion>,
        progress: CourseProgress,
    ): QuizResultsReadyState {
        // Round-1 review, LOW: sort by the same QuizQuestion.order QuizViewModel's own
        // orderedQuestions uses, rather than trusting attempt.breakdown's own list order — the
        // backend's latest() builds breakdown from the quiz's raw document order (QuizService.kt),
        // which is not guaranteed to match `order` if the two were ever to diverge. A question
        // missing from `questionsById` (the defensive QuizLookupResult.NoQuiz branch) falls back to
        // breakdown's own position rather than being silently dropped.
        val orderedBreakdown: List<AttemptBreakdown> = attempt.breakdown
            .withIndex()
            .sortedBy { (index, row) -> questionsById[row.questionId]?.order ?: (Int.MAX_VALUE - index) }
            .map { it.value }
        val breakdown = orderedBreakdown.mapIndexed { index, row ->
            QuizResultsBreakdownRowUi(
                questionNumber = index + 1,
                prompt = questionsById[row.questionId]?.prompt,
                isCorrect = row.isCorrect,
            )
        }
        return QuizResultsReadyState(
            // Round-1 review, LOW (disclosed, not changed): `correctCount`/`totalCount` are re-derived
            // from `breakdown` (consistent with what the per-row list above actually displays) rather
            // than trusting the stored attempt's own `score` — the backend's `latest()` recomputes
            // `breakdown` against the CURRENT quiz but returns the STORED `score`/`passed`, so an
            // instructor editing question count after this attempt could in principle produce a
            // mismatch either way; recomputing from `breakdown` keeps the count consistent with the
            // rows the student can see. `passed` itself always comes from the stored attempt, never
            // recomputed — the one server-owned field this screen must never second-guess.
            correctCount = attempt.breakdown.count { it.isCorrect },
            totalCount = attempt.breakdown.size,
            passed = attempt.passed,
            breakdown = breakdown,
            showCompletionBanner = attempt.passed && progress.courseCompletedAt != null,
        )
    }

    private fun setError(code: ApiErrorCode) {
        _uiState.update { it.copy(content = QuizResultsContentState.Error(code)) }
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `CourseDetailsViewModel.Factory`'s exact idiom. */
    class Factory(
        private val sdk: MentoraSdk,
        private val courseId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = QuizResultsViewModel(
            courseId = courseId,
            getLatestAttempt = sdk.quiz.getLatestAttempt::invoke,
            getQuiz = sdk.quiz.getQuiz::invoke,
            getCourseProgress = sdk.progress.getCourseProgress::invoke,
        ) as T
    }
}
