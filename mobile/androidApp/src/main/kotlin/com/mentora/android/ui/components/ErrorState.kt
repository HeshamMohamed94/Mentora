package com.mentora.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens

/** Test-only hook (`ui.test.onNodeWithTag`) for asserting the retry action fires its callback. */
const val ErrorStateRetryButtonTestTag = "mentora-error-state-retry"

/**
 * `design-system/COMPONENTS.md` § ErrorState (lines 595-606). Icon (`icon.large`,
 * `color.error.default`) -> friendly title ("Something went wrong" style) -> plain-language
 * description -> retry action labeled "Try again", `space.10` vertical container padding.
 *
 * **Never a raw backend error string.** [title]/[description] are plain caller-supplied strings —
 * every real call site must resolve them from `ui/error/ApiErrorCopy.kt`'s
 * `apiErrorMessage(ApiErrorCode)` (Task 7's ONE central error-copy mapping, per that file's own
 * kdoc), never from `ApiResult.Failure.message` directly. This component builds no second error-copy
 * mechanism — it only renders whatever friendly copy the caller already resolved.
 */
@Composable
fun ErrorState(
    title: String,
    description: String,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
    // T19 review fix (HIGH, D94): this default was a raw English literal, live and reachable at 7
    // production call sites (CourseDetailsScreen/MyLearningScreen/ExploreScreen(x2)/HomeScreen(x2)/
    // LearningPathDetailsScreen) that never passed their own `retryLabel` override — every OTHER call
    // site already does, following the exact lesson `CertificatesScreen.kt`'s own "Round-1 review,
    // MEDIUM-1" comment records, but that lesson was never generalized back to the component's own
    // default. Real and reachable since Task 18's LocalizedContent.kt made an in-app language switch
    // genuinely flip `stringResource` app-wide: an Arabic-language student hitting any network error
    // on one of those 7 screens saw an otherwise-fully-Arabic error card with an English "Try again"
    // button.
    retryLabel: String = stringResource(R.string.error_state_retry_label),
    retryVariant: MentoraButtonVariant = MentoraButtonVariant.Tonal,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MentoraDimens.spacing.space10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
    ) {
        MentoraIcon(
            name = MentoraIconName.Cancel,
            contentDescription = null,
            size = MentoraDimens.iconSize.large,
            tint = MaterialTheme.colorScheme.error,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge, // heading.h4.
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        MentoraButton(
            text = retryLabel,
            onClick = onRetryClick,
            variant = retryVariant,
            modifier = Modifier.testTag(ErrorStateRetryButtonTestTag),
        )
    }
}
