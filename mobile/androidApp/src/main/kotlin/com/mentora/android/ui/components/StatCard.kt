package com.mentora.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors

/** `success.default` up / `error.default` down — `design-system/COMPONENTS.md` § StatCard trend row. */
enum class StatTrendDirection { Up, Down }

/**
 * `design-system/COMPONENTS.md` § StatCard (lines 337-347) — also [QuestionCard]'s shell per that
 * section's own "same shell as a StatCard" note, though [QuestionCard] is its own composable (its
 * content differs enough — a progress bar + question text rather than a value/label/trend — that
 * sharing this composable directly would mean overloading its parameter shape past readability).
 *
 * Trend indicator pairs [MentoraIconName.ArrowUpward]/[MentoraIconName.ArrowDownward] (`icon.small`)
 * with [trendLabel] text in the matching semantic color — never a bare colored arrow, so the signal
 * still reads correctly for a color-blind user (`ACCESSIBILITY.md` § 8).
 */
@Composable
fun StatCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    trendDirection: StatTrendDirection? = null,
    trendLabel: String? = null,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium, // radius.large (16).
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium, // heading.h2.
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, // CONTENT_RESILIENCE.md § 1 — a long label must not grow the card taller.
                overflow = TextOverflow.Ellipsis,
            )
            if (trendDirection != null && trendLabel != null) {
                val trendColor = when (trendDirection) {
                    StatTrendDirection.Up -> MaterialTheme.extendedColors.success
                    StatTrendDirection.Down -> MaterialTheme.colorScheme.error
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1),
                ) {
                    MentoraIcon(
                        name = if (trendDirection == StatTrendDirection.Up) MentoraIconName.ArrowUpward else MentoraIconName.ArrowDownward,
                        contentDescription = null,
                        size = MentoraDimens.iconSize.small,
                        tint = trendColor,
                    )
                    Text(text = trendLabel, style = MaterialTheme.typography.labelSmall, color = trendColor) // typography.caption.
                }
            }
        }
    }
}
