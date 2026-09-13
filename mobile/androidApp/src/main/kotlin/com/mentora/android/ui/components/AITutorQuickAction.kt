package com.mentora.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mentora.android.theme.MentoraDimens

/**
 * `design-system/COMPONENTS.md` § AITutorQuickAction (lines 691-710). Height 36 (literal component-
 * table value — same modeling as [Badge]/[CategoryChip]'s literal `height`, not a `spacing.scale`
 * alias), radius `radius.full` -> [CircleShape], paddingX `space.4`, typography `label.medium`, text
 * `color.brand.primary`.
 *
 * The 5 default quick-action labels ("Explain this lesson", "Summarize", ...) are the shared
 * `AiQuickAction` domain concern (`mobile/shared`) per the task brief, NOT hardcoded here — this
 * composable only renders whatever [label] string it's given; a later task (T17, AI Tutor) supplies
 * the real label/prompt pairing.
 */
@Composable
fun AITutorQuickAction(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    val background = if (pressed) colorScheme.primaryContainer else colorScheme.surface
    val borderColor = if (pressed) colorScheme.primary else colorScheme.outlineVariant

    Box(
        modifier = modifier
            .height(36.dp) // component.aiTutorQuickAction.height (36), a literal (see kdoc above).
            .wrapContentWidth()
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(MentoraDimens.borderWidthDefault, borderColor), CircleShape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = MentoraDimens.spacing.space4),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
    }
}
