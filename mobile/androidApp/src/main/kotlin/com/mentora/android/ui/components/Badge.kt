package com.mentora.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors

/**
 * `design-system/COMPONENTS.md` § Badge (lines 382-398). Height 20 (text badge, a literal
 * component-table value, same modeling as every other component's `height`/`size` field — not a
 * `spacing.scale` alias) / radius `radius.full` -> [CircleShape] / paddingX `space.2` / typography
 * `label.medium`. All 6 variants' background+text come from `MaterialTheme.colorScheme` (Neutral,
 * Error, Brand — all have native M3 slots) or `MaterialTheme.extendedColors` (Success/Warning/Info —
 * no M3 slot, per that field's kdoc).
 *
 * Per `ACCESSIBILITY.md` § 8 ("Status badges ... always paired with a label ... a bare status dot is
 * acceptable only for low-stakes presence indicators like 'online'"): [MentoraBadge] always renders
 * a text label (the "text badge" variant), matching that rule directly. [MentoraDotBadge] is the
 * separate low-stakes 8dp dot variant the spec's height row calls out ("8 (dot badge)") — deliberately
 * a distinct, smaller composable rather than a `text = null` branch on the same one, so a caller can't
 * accidentally end up with a bare unlabeled dot for a *meaningful* state by omitting text.
 */
enum class MentoraBadgeVariant { Neutral, Success, Warning, Error, Info, Brand }

private data class BadgeTone(val background: Color, val text: Color)

@Composable
private fun badgeToneFor(variant: MentoraBadgeVariant): BadgeTone {
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    return when (variant) {
        MentoraBadgeVariant.Neutral -> BadgeTone(colorScheme.surfaceVariant, colorScheme.onSurfaceVariant)
        MentoraBadgeVariant.Success -> BadgeTone(extended.successContainer, extended.onSuccessContainer)
        MentoraBadgeVariant.Warning -> BadgeTone(extended.warningContainer, extended.onWarningContainer)
        MentoraBadgeVariant.Error -> BadgeTone(colorScheme.errorContainer, colorScheme.onErrorContainer)
        MentoraBadgeVariant.Info -> BadgeTone(extended.infoContainer, extended.onInfoContainer)
        MentoraBadgeVariant.Brand -> BadgeTone(colorScheme.primaryContainer, colorScheme.onPrimaryContainer)
    }
}

@Composable
fun MentoraBadge(
    text: String,
    modifier: Modifier = Modifier,
    variant: MentoraBadgeVariant = MentoraBadgeVariant.Neutral,
) {
    val tone = badgeToneFor(variant)
    Box(
        modifier = modifier
            .height(20.dp) // component.badge.height (20), a literal (see file kdoc).
            .wrapContentWidth()
            .clip(CircleShape)
            .background(tone.background)
            .padding(horizontal = MentoraDimens.spacing.space2),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = tone.text)
    }
}

/** The 8dp "dot badge" variant (`COMPONENTS.md` line 386) — low-stakes presence indicator only. */
@Composable
fun MentoraDotBadge(
    modifier: Modifier = Modifier,
    variant: MentoraBadgeVariant = MentoraBadgeVariant.Neutral,
) {
    val tone = badgeToneFor(variant)
    Box(
        modifier = modifier
            .size(8.dp) // component.badge.height's "8 (dot badge)" literal.
            .clip(CircleShape)
            .background(tone.background),
    )
}
