package com.mentora.android.ui.quiz

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.FullScreenLoadingState
import com.mentora.android.ui.components.MentoraBadge
import com.mentora.android.ui.components.MentoraBadgeVariant
import com.mentora.android.ui.components.MentoraIcon
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.components.PrimaryButton
import com.mentora.android.ui.components.StatCard
import com.mentora.android.ui.components.TonalButton
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk

/**
 * Task 14 — the real Quiz Results screen (`ux/SCREEN_UX_SPECS.md § 12`; no exact-showcase mockup
 * exists — same design-derived footing as Quiz). Replaces the T6-era placeholder in
 * `ui/screens/PlaceholderScreens.kt`.
 *
 * Fetches via [QuizResultsViewModel]'s own `getLatestAttempt` (this task's decision 1 — re-fetch,
 * don't pass the just-submitted [com.mentora.shared.domain.model.QuizAttemptResult] through
 * navigation) rather than taking one as a parameter. Reuses the outer `MentoraNavHost` `Scaffold`'s
 * own `MentoraTopBar` (back button + "Quiz Results" title), same as [QuizScreen].
 */
@Composable
fun QuizResultsScreen(
    courseId: String,
    sdk: MentoraSdk,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onOpenCertificates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: QuizResultsViewModel = viewModel(factory = QuizResultsViewModel.Factory(sdk, courseId))
    val uiState by viewModel.uiState.collectAsState()

    // Round-1 review, MEDIUM-3: same double-inset mechanism/fix as `QuizScreen.kt`'s own Scaffold —
    // see that file's own comment for the full mechanism (outer `MentoraNavHost` Scaffold is already
    // the sole inset source for this chromeless-bottom-nav, real-`MentoraTopBar` route).
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(QuizResultsScreenTestTag),
        contentWindowInsets = WindowInsets(0),
    ) { innerPadding ->
        when (val content = uiState.content) {
            is QuizResultsContentState.Loading -> FullScreenLoadingState(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentDescriptionLabel = stringResource(R.string.quiz_results_loading_content_description),
            )

            is QuizResultsContentState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                ErrorState(
                    title = stringResource(R.string.quiz_results_error_title),
                    description = apiErrorMessage(content.code),
                    onRetryClick = viewModel::onRetry,
                    retryLabel = stringResource(R.string.quiz_results_retry_action),
                    modifier = Modifier.padding(horizontal = MentoraDimens.spacing.space4),
                )
            }

            is QuizResultsContentState.Ready -> QuizResultsReadyContent(
                state = content.state,
                onContinue = onContinue,
                onRetry = onRetry,
                onOpenCertificates = onOpenCertificates,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

@Composable
private fun QuizResultsReadyContent(
    state: QuizResultsReadyState,
    onContinue: () -> Unit,
    onRetry: () -> Unit,
    onOpenCertificates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space5),
        ) {
            QuizResultsScoreSection(state = state)
            QuizResultsBreakdownSection(state = state)
            if (state.showCompletionBanner) {
                QuizResultsCompletionBanner(onOpenCertificates = onOpenCertificates)
            }
        }

        Box(modifier = Modifier.fillMaxWidth().padding(MentoraDimens.spacing.space4)) {
            PrimaryButton(
                text = stringResource(
                    if (state.passed) R.string.quiz_results_continue_action else R.string.quiz_results_retry_quiz_action,
                ),
                onClick = if (state.passed) onContinue else onRetry,
                modifier = Modifier.fillMaxWidth().testTag(QuizResultsPrimaryActionTestTag),
            )
        }
    }
}

@Composable
private fun QuizResultsScoreSection(state: QuizResultsReadyState) {
    // "The score is announced as text ('8 out of 10, passed') not just visually" (`ux/SCREEN_UX_SPECS
    // .md § 12`'s own accessibility rule) — merged into one accessible name on the wrapping row so a
    // screen reader announces the score and pass/fail state together, rather than the StatCard's own
    // value/label and the Badge's own text being read as two disconnected fragments.
    val scoreDescription = stringResource(
        if (state.passed) R.string.quiz_results_score_content_description_passed else R.string.quiz_results_score_content_description_failed,
        state.correctCount.toString(),
        state.totalCount.toString(),
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = scoreDescription }
            .testTag(QuizResultsScoreSectionTestTag),
    ) {
        StatCard(
            value = stringResource(
                R.string.quiz_results_score_value,
                state.correctCount.toString(),
                state.totalCount.toString(),
            ),
            label = stringResource(R.string.quiz_results_score_label),
            modifier = Modifier.weight(1f),
        )
        MentoraBadge(
            text = stringResource(if (state.passed) R.string.quiz_results_badge_passed else R.string.quiz_results_badge_failed),
            variant = if (state.passed) MentoraBadgeVariant.Success else MentoraBadgeVariant.Error,
        )
    }
}

@Composable
private fun QuizResultsBreakdownSection(state: QuizResultsReadyState) {
    Column(verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2)) {
        Text(
            text = stringResource(R.string.quiz_results_breakdown_heading),
            style = MaterialTheme.typography.headlineSmall, // heading.h3.
            color = MaterialTheme.colorScheme.onSurface,
        )
        state.breakdown.forEach { row -> QuizResultsBreakdownRow(row = row) }
    }
}

/**
 * Per-question breakdown row — icon + label + color together, never color alone
 * (`ux/SCREEN_UX_SPECS.md § 12`'s "never color-only" rule, the same rigor
 * [com.mentora.android.ui.components.AnswerOption] already applies per-option). Each row's own
 * accessible name carries the correctness state directly (that same section's accessibility note),
 * not conveyed by icon color alone.
 */
@Composable
private fun QuizResultsBreakdownRow(row: QuizResultsBreakdownRowUi) {
    val extended = MaterialTheme.extendedColors
    val colorScheme = MaterialTheme.colorScheme
    val label = row.prompt?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.quiz_results_question_fallback, row.questionNumber.toString())
    val correctnessLabel = stringResource(
        if (row.isCorrect) R.string.quiz_results_correct_label else R.string.quiz_results_incorrect_label,
    )
    val rowDescription = stringResource(
        if (row.isCorrect) R.string.quiz_results_row_correct_content_description else R.string.quiz_results_row_incorrect_content_description,
        label,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = rowDescription }
            .testTag(quizResultsBreakdownRowTestTag(row.questionNumber)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
    ) {
        MentoraIcon(
            name = if (row.isCorrect) MentoraIconName.CheckCircle else MentoraIconName.Cancel,
            contentDescription = null,
            size = MentoraDimens.iconSize.medium,
            tint = if (row.isCorrect) extended.success else colorScheme.error,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = correctnessLabel,
            style = MaterialTheme.typography.labelMedium,
            color = if (row.isCorrect) extended.success else colorScheme.error,
        )
    }
}

/**
 * The inline course-completion confirmation — "composed using the same `success.*` tokens as
 * SuccessState but embedded within this screen rather than a full-screen takeover, since the quiz
 * results themselves remain relevant context" (`ux/SCREEN_UX_SPECS.md § 12`). Deliberately a plain
 * `Surface`-based block here (not a reused `SuccessState`, which is a full-width, no-siblings,
 * one-PrimaryButton composable per its own kdoc precedent — `CoursePlayerCompletedContent` already
 * establishes "do NOT grow SuccessState a secondary-action slot") — this banner's own "View
 * Certificate" button is exactly this screen's spec'd Secondary action, embedded in the banner rather
 * than floating separately, per the spec's own literal wording.
 */
@Composable
private fun QuizResultsCompletionBanner(onOpenCertificates: () -> Unit) {
    val extended = MaterialTheme.extendedColors
    Surface(
        modifier = Modifier.fillMaxWidth().testTag(QuizResultsCompletionBannerTestTag),
        shape = MaterialTheme.shapes.medium, // radius.large.
        color = extended.successContainer,
        border = BorderStroke(MentoraDimens.borderWidthDefault, extended.success),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MentoraDimens.spacing.space4),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
        ) {
            MentoraIcon(
                name = MentoraIconName.CheckCircle,
                contentDescription = null,
                size = MentoraDimens.iconSize.large,
                tint = extended.success,
            )
            Text(
                text = stringResource(R.string.quiz_results_completion_banner_title),
                style = MaterialTheme.typography.headlineSmall, // heading.h3.
                color = extended.onSuccessContainer,
            )
            Text(
                text = stringResource(R.string.quiz_results_completion_banner_description),
                style = MaterialTheme.typography.bodySmall,
                color = extended.onSuccessContainer,
            )
            TonalButton(
                text = stringResource(R.string.quiz_results_view_certificate_action),
                onClick = onOpenCertificates,
                modifier = Modifier.testTag(QuizResultsViewCertificateButtonTestTag),
            )
        }
    }
}

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val QuizResultsScreenTestTag = "quiz-results-screen"
const val QuizResultsScoreSectionTestTag = "quiz-results-score-section"
const val QuizResultsPrimaryActionTestTag = "quiz-results-primary-action"
const val QuizResultsCompletionBannerTestTag = "quiz-results-completion-banner"
const val QuizResultsViewCertificateButtonTestTag = "quiz-results-view-certificate-button"

fun quizResultsBreakdownRowTestTag(questionNumber: Int): String = "quiz-results-breakdown-row-$questionNumber"
