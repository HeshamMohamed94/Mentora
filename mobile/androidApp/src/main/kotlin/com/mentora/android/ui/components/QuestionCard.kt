package com.mentora.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mentora.android.theme.MentoraDimens

/**
 * `design-system/COMPONENTS.md` § QuestionCard (line 716-720): "same shell as a StatCard" (radius
 * `radius.large`, border `color.border.default`, padding `space.5`), containing a "Question N of M"
 * progress indicator (`typography.caption`) paired with a slim [MentoraProgressBar], then the
 * question text (`heading.h4`).
 *
 * Pure presentational — [questionNumber]/[totalQuestions]/[questionText] are plain values; Task 14
 * wires real quiz domain models into these parameters, not this component's job.
 */
@Composable
fun QuestionCard(
    questionNumber: Int,
    totalQuestions: Int,
    questionText: String,
    modifier: Modifier = Modifier,
    progressLabel: String = "Question $questionNumber of $totalQuestions",
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium, // radius.large (16).
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(MentoraDimens.spacing.space5),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
        ) {
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelSmall, // typography.caption.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MentoraProgressBar(
                progress = if (totalQuestions > 0) questionNumber.toFloat() / totalQuestions.toFloat() else 0f,
                contentDescriptionLabel = progressLabel,
            )
            Text(
                text = questionText,
                style = MaterialTheme.typography.titleLarge, // heading.h4.
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
