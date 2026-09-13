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
import androidx.compose.ui.text.style.TextAlign
import com.mentora.android.theme.MentoraDimens

/**
 * `design-system/COMPONENTS.md` § EmptyState (lines 585-593). Centered icon (`icon.large`,
 * `color.text.secondary`) -> title (`heading.h4`) -> description (`body.small`, max ~2 lines) ->
 * optional primary/tonal action, `space.10` vertical container padding.
 */
@Composable
fun EmptyState(
    icon: MentoraIconName,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionVariant: MentoraButtonVariant = MentoraButtonVariant.Tonal,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MentoraDimens.spacing.space10),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
    ) {
        MentoraIcon(
            name = icon,
            contentDescription = null,
            size = MentoraDimens.iconSize.large,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge, // heading.h4.
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            // No maxLines/ellipsis clamp — CONTENT_RESILIENCE.md § 1's "~2 lines" is soft guidance for
            // typical content length, not a hard truncation rule; this wraps freely, matching
            // ErrorState.kt's already-correct unclamped treatment.
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onActionClick != null) {
            MentoraButton(text = actionLabel, onClick = onActionClick, variant = actionVariant)
        }
    }
}
