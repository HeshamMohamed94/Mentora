package com.mentora.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors
import com.mentora.android.theme.stateOpacities

/**
 * `design-system/COMPONENTS.md` § CategoryChip (lines 366-380). Height 28 (literal, see `Badge.kt`'s
 * kdoc on why component `height` fields are plain literals rather than a `spacing.scale` alias),
 * radius `radius.full` -> [CircleShape], paddingX `space.3`, typography `label.medium`.
 *
 * [MentoraCategoryChipVariant.OnImageOverlay] is the "on image overlay (course thumbnail)" state —
 * `color.overlay.chipScrim` background / `color.text.inverse` text per the spec table, realized here
 * as `extended.chipScrim` / `extended.onChipScrim` (NOT `colorScheme.inverseOnSurface` — see
 * `MentoraExtendedColors`' own kdoc "onChipScrim" entry for why that would be illegible in dark
 * theme: the scrim never changes with the app theme, but `inverseOnSurface` does).
 */
enum class MentoraCategoryChipState { Default, Selected, OnImageOverlay, Disabled }

@Composable
fun CategoryChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onImageOverlay: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val state = when {
        !enabled -> MentoraCategoryChipState.Disabled
        onImageOverlay -> MentoraCategoryChipState.OnImageOverlay
        selected -> MentoraCategoryChipState.Selected
        else -> MentoraCategoryChipState.Default
    }
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val opacities = MaterialTheme.stateOpacities
    val disabledText = colorScheme.onSurface.copy(alpha = opacities.disabledContent)

    val (background, text) = when (state) {
        MentoraCategoryChipState.Default -> colorScheme.surfaceVariant to colorScheme.onSurfaceVariant
        MentoraCategoryChipState.Selected -> colorScheme.primaryContainer to colorScheme.onPrimaryContainer
        MentoraCategoryChipState.OnImageOverlay -> extended.chipScrim to extended.onChipScrim
        MentoraCategoryChipState.Disabled -> colorScheme.surfaceVariant to disabledText
    }

    Box(
        modifier = modifier
            // F4 fix: `clickable` + `minimumInteractiveComponentSize()` applied outermost (before
            // the visual `.height(28.dp)`) so the tappable region inflates to the 48dp Android
            // minimum (ACCESSIBILITY.md § 4 names chips explicitly) while the visual pill stays
            // exactly 28dp, centered inside the invisible hit-slop margin — measured via a bounds
            // probe (this ordering mirrors M3's own Checkbox/RadioButton pattern; the reverse order
            // measured only ~28dp, no expansion). Only applied when the chip is actually tappable.
            .let {
                if (onClick != null) {
                    it.clickable(enabled = enabled, onClick = onClick).minimumInteractiveComponentSize()
                } else {
                    it
                }
            }
            .height(28.dp) // component.chip.height (28), a literal.
            .wrapContentWidth()
            .clip(CircleShape)
            .background(background)
            .semantics {
                if (onClick != null) role = Role.Button
                this.selected = selected
                if (!enabled) disabled()
            }
            .padding(horizontal = MentoraDimens.spacing.space3),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = text)
    }
}
