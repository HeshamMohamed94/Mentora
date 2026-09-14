package com.mentora.android.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.ui.components.AnswerOption
import com.mentora.android.ui.components.EmptyState
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.FullScreenLoadingState
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.components.MentoraSnackbarHost
import com.mentora.android.ui.components.PrimaryButton
import com.mentora.android.ui.components.QuestionCard
import com.mentora.android.ui.components.showMentoraSnackbar
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk

/**
 * Task 14 — the real Quiz screen (`ux/SCREEN_UX_SPECS.md § 11`; no exact-showcase mockup exists for
 * this screen — confirmed, same "design-derived, not pixel-matched" footing as Course Details/other
 * `ux-only`-tagged screens). Replaces the T6-era placeholder in `ui/screens/PlaceholderScreens.kt`.
 *
 * **Minimal chrome, no intro.** The first question renders immediately on entry — no separate "start
 * quiz" screen. This screen reuses the outer `MentoraNavHost` `Scaffold`'s own `MentoraTopBar` (back
 * button + "Quiz" title) rather than building its own; [QuestionCard]'s own "Question N of M" +
 * progress bar is the screen's real framing per the spec's own wording ("the question-progress
 * indicator itself communicating ... sufficient framing"). Bottom nav is already hidden for this
 * destination via `MentoraNavHost`'s `isFocusedLearningScreen` check (unchanged by this task).
 *
 * **Linear, single-select, no Previous** — per the spec's own explicit "no skip, no back-to-
 * previous-question control in MVP." "Next"/"Submit Quiz" is disabled until the current question has
 * a selection ([QuizReadyState.canSubmit]).
 */
@Composable
fun QuizScreen(
    courseId: String,
    sdk: MentoraSdk,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: QuizViewModel = viewModel(factory = QuizViewModel.Factory(sdk, courseId))
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val submitError = (uiState.content as? QuizContentState.Ready)?.state?.submitError
    if (submitError != null) {
        val message = apiErrorMessage(submitError)
        LaunchedEffect(submitError) {
            snackbarHostState.showMentoraSnackbar(message)
            viewModel.onSubmitErrorDismissed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag(QuizScreenTestTag),
        // Round-1 review, MEDIUM-3: this screen keeps the outer `MentoraNavHost` Scaffold's real
        // `MentoraTopBar` (unlike Course Player, which is chromeless and builds its own) — that outer
        // Scaffold already derives its own `contentPadding` from the topBar's REAL height (which
        // itself already reserves the status-bar inset via `MentoraTopBar`'s own
        // `windowInsetsPadding`) and, since this route hides the bottom nav
        // (`isFocusedLearningScreen`), from its own `contentWindowInsets` bottom fallback. Leaving
        // THIS inner Scaffold's `contentWindowInsets` at its default (`systemBars`) — with no topBar/
        // bottomBar of its own — made it fall back to that same systemBars padding a SECOND time on
        // both edges: an extra status-bar-height gap under the top bar, and an extra nav-bar-height
        // gap under the pinned CTA. Same fix/reasoning as `CoursePlayerScreen.kt`'s own round-5 MEDIUM
        // finding on this exact mechanism.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { MentoraSnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        when (val content = uiState.content) {
            is QuizContentState.Loading -> FullScreenLoadingState(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentDescriptionLabel = stringResource(R.string.quiz_loading_content_description),
            )

            is QuizContentState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                ErrorState(
                    title = stringResource(R.string.quiz_error_title),
                    description = apiErrorMessage(content.code),
                    onRetryClick = viewModel::onRetry,
                    retryLabel = stringResource(R.string.quiz_retry_action),
                    modifier = Modifier.padding(horizontal = MentoraDimens.spacing.space4),
                )
            }

            is QuizContentState.NoQuiz -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    // No dedicated "quiz"/"info" glyph exists in the ported 42-icon set (a disclosed
                    // simplification, same precedent as `CourseDetailsScreen.kt`'s own missing-lock-
                    // icon note) — `School` is the closest semantic fit among what exists.
                    icon = MentoraIconName.School,
                    title = stringResource(R.string.quiz_no_quiz_title),
                    description = stringResource(R.string.quiz_no_quiz_description),
                    modifier = Modifier.padding(horizontal = MentoraDimens.spacing.space4),
                )
            }

            is QuizContentState.Ready -> QuizReadyContent(
                state = content.state,
                onOptionSelected = viewModel::onOptionSelected,
                onNextTapped = viewModel::onNextTapped,
                onSubmitTapped = { viewModel.onSubmitTapped(onSubmitted) },
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

@Composable
private fun QuizReadyContent(
    state: QuizReadyState,
    onOptionSelected: (String) -> Unit,
    onNextTapped: () -> Unit,
    onSubmitTapped: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
        ) {
            QuestionCard(
                questionNumber = state.questionNumber,
                totalQuestions = state.totalQuestions,
                questionText = state.questionText,
                // Known gotcha (task brief): QuestionCard's own `progressLabel` default is raw,
                // unlocalized English — always pass a real localized value explicitly, never rely on
                // the default. Locked numerals rule (`design-system/LOCALIZATION.md § 8`): `%1$s`/
                // `%2$s` with `.toString()` args, never `%1$d`/`%2$d`.
                progressLabel = stringResource(
                    R.string.quiz_progress_label,
                    state.questionNumber.toString(),
                    state.totalQuestions.toString(),
                ),
                modifier = Modifier.testTag(QuizQuestionCardTestTag),
            )
            // Round-1 review, MEDIUM-4: `ux/SCREEN_UX_SPECS.md:479` requires "each answer option is a
            // real radio-group member (single-select, keyboard arrow-navigable)". `AnswerOption`
            // itself is pure-presentational (its own kdoc), so the radio-group semantics are supplied
            // here at the call site rather than by modifying that shared Task 8 component:
            // `selectableGroup()` on the container plus `selected`/`Role.RadioButton` per option —
            // layered onto `AnswerOption`'s OWN internal `.semantics {}` block (which still separately
            // sets `disabled`/`stateDescription`), not replacing it, and deliberately NOT
            // `Modifier.selectable()` (that would add a second click handler on top of
            // `AnswerOption`'s own internal `.clickable()`).
            Column(
                modifier = Modifier.selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
            ) {
                state.options.forEach { option ->
                    AnswerOption(
                        text = option.text,
                        isSelected = option.isSelected,
                        // This screen never reaches a submitted-in-place state (results render on a
                        // separate Quiz Results screen, per this task's own routing decision) — always
                        // `false`/`false`, so AnswerOption never renders its Correct/Incorrect states
                        // here.
                        isSubmitted = false,
                        isCorrectAnswer = false,
                        onClick = { onOptionSelected(option.optionId) },
                        // Known gotcha (task brief): AnswerOption's own `correctLabel`/`incorrectLabel`
                        // defaults are raw, unlocalized English — always pass real localized values
                        // explicitly, even though this screen's own `isSubmitted = false` never
                        // actually renders them.
                        correctLabel = stringResource(R.string.quiz_answer_correct_label),
                        incorrectLabel = stringResource(R.string.quiz_answer_incorrect_label),
                        modifier = Modifier
                            .semantics { selected = option.isSelected; role = Role.RadioButton }
                            .testTag(quizAnswerOptionTestTag(option.optionId)),
                    )
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(MentoraDimens.spacing.space4)) {
            PrimaryButton(
                text = stringResource(
                    if (state.isLastQuestion) R.string.quiz_submit_action else R.string.quiz_next_action,
                ),
                onClick = if (state.isLastQuestion) onSubmitTapped else onNextTapped,
                enabled = state.canSubmit && !state.isSubmitting,
                loading = state.isSubmitting,
                disabledReason = if (!state.canSubmit) stringResource(R.string.quiz_next_disabled_reason) else null,
                modifier = Modifier.fillMaxWidth().testTag(QuizPrimaryActionTestTag),
            )
        }
    }
}

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val QuizScreenTestTag = "quiz-screen"
const val QuizQuestionCardTestTag = "quiz-question-card"
const val QuizPrimaryActionTestTag = "quiz-primary-action"

fun quizAnswerOptionTestTag(optionId: String): String = "quiz-answer-option-$optionId"
