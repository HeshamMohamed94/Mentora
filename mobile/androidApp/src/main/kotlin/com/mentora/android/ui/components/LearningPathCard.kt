package com.mentora.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.mentora.android.theme.MentoraDimens

/**
 * `design-system/COMPONENTS.md` § LearningPathCard (lines 309-321). `color.brand.primaryContainer`
 * background (a deliberate differentiator from the plain-surface [CourseCard]/[StatCard]/
 * [CertificateCard]) — title/description/meta all render in `color.brand.onPrimaryContainer` at
 * **full opacity**, hierarchy coming only from the type-scale step (`heading.h4` → `body.small` →
 * `caption`), never from fading the text color, per design principle 4 ("hierarchy via type scale
 * and spacing, not extra colors") — quoted directly in the spec's own note.
 *
 * **Action button choice (disclosed deviation from the literal "TextButton" wording).** The spec
 * offers two explicit alternatives: "`TextButton` in `color.brand.onPrimaryContainer` OR a small
 * `PrimaryButton`." [MentoraTextButton] (this kit's `TextButton`, Task 5) hardcodes its content
 * color to `color.brand.primary` with no color-override parameter — giving it one would mean
 * modifying a Task 5 atom, which this task's brief says not to rebuild. [PrimaryButton] is used
 * instead, taking the spec's own explicitly-permitted second option rather than extending an
 * existing atom's API.
 */
@Composable
fun LearningPathCard(
    title: String,
    description: String,
    metaLabel: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = MaterialTheme.shapes.medium, // radius.large (16).
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(MentoraDimens.spacing.space5),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge, // heading.h4.
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metaLabel, // e.g. "6 courses • 12h total" — caller formats/localizes.
                style = MaterialTheme.typography.labelSmall, // typography.caption.
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            PrimaryButton(text = actionLabel, onClick = onActionClick)
        }
    }
}
