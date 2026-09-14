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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.stateOpacities

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
    // T17 fix (MEDIUM): without this, a tap while a turn is already in flight silently no-ops against
    // the ViewModel's own re-entrancy guard with zero visible feedback (this chip's own `indication =
    // null` means not even a ripple) — the control looks live and isn't. Defaults to `true` so every
    // pre-existing call site (none yet — T17 is the first real consumer) is unaffected.
    enabled: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme
    val opacities = MaterialTheme.stateOpacities

    val background = if (pressed && enabled) colorScheme.primaryContainer else colorScheme.surface
    val borderColor = if (pressed && enabled) colorScheme.primary else colorScheme.outlineVariant
    val textColor = if (enabled) colorScheme.primary else colorScheme.primary.copy(alpha = opacities.disabledContent)

    Box(
        modifier = modifier
            // T17 fix: `clickable` + `minimumInteractiveComponentSize()` applied outermost (before the
            // visual `.height(36.dp)`) so the tappable region inflates to the 48dp Android minimum
            // (`ACCESSIBILITY.md § 4` names chips explicitly) while the visual pill stays exactly 36dp
            // — same ordering as `CategoryChip`'s own identical F4 fix.
            .clickable(enabled = enabled, interactionSource = interactionSource, indication = null, onClick = onClick)
            .minimumInteractiveComponentSize()
            .height(36.dp) // component.aiTutorQuickAction.height (36), a literal (see kdoc above).
            .wrapContentWidth()
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(MentoraDimens.borderWidthDefault, borderColor), CircleShape)
            .padding(horizontal = MentoraDimens.spacing.space4),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}
