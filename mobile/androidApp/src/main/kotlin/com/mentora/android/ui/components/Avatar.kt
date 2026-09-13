package com.mentora.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors

/**
 * `design-system/COMPONENTS.md` § Avatar (lines 402-411). No image support yet — task brief scopes
 * that to a later task with real user/course data; this builds the initials-fallback path,
 * parameterized by [name].
 *
 * **Disclosed gap:** `avatar.small/medium/large/xlarge` (24/40/64/96) exist in
 * `design-system/design-tokens.json` § avatar (confirmed present — unlike, say, `border.width`, this
 * key is genuinely in the source) but Task 2's generator never walked an `avatar.*` namespace into
 * [MentoraTokens.kt]/[MentoraDimens], so there is no generated constant to consume. Sized via
 * [MentoraDimens.avatarSmall]/`.avatarMedium`/`.avatarLarge`/`.avatarXLarge` — hand-authored, once,
 * in `MentoraDimens.kt` (see that file's kdoc), not re-hardcoded here.
 *
 * Radius always `radius.full` -> [CircleShape]. Fallback background `color.brand.primaryContainer`,
 * text `color.brand.onPrimaryContainer`, `typography.label.large` scaled to the avatar's size (scaled
 * via `fontSize * (dimension / 40dp-medium-baseline)`, since the spec calls for one scaled label
 * style, not four separate typography tokens). Optional status dot: 25% of avatar diameter,
 * `color.success.default`, ringed in `border.width.default` `color.surface.default`.
 */
enum class MentoraAvatarSize { Small, Medium, Large, XLarge }

private fun MentoraAvatarSize.dimension(): Dp = when (this) {
    MentoraAvatarSize.Small -> MentoraDimens.avatarSmall
    MentoraAvatarSize.Medium -> MentoraDimens.avatarMedium
    MentoraAvatarSize.Large -> MentoraDimens.avatarLarge
    MentoraAvatarSize.XLarge -> MentoraDimens.avatarXLarge
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "?"
    val first = parts.first().firstOrNull()?.uppercaseChar() ?: '?'
    val last = parts.last().firstOrNull()?.uppercaseChar()
    return if (parts.size > 1 && last != null) "$first$last" else "$first"
}

@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: MentoraAvatarSize = MentoraAvatarSize.Medium,
    showStatusDot: Boolean = false,
) {
    val dimension = size.dimension()
    // typography.label.large scaled to the avatar's size, relative to the medium (40dp) baseline —
    // one scaled label style rather than four separate typography tokens (see file kdoc).
    val baseline = MentoraDimens.avatarMedium
    val labelLarge = MaterialTheme.typography.labelLarge
    val scaledFontSize = labelLarge.fontSize * (dimension.value / baseline.value)

    Box(modifier = modifier.size(dimension)) {
        Box(
            modifier = Modifier
                .size(dimension)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = initialsOf(name),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontSize = scaledFontSize,
                fontWeight = labelLarge.fontWeight,
            )
        }
        if (showStatusDot) {
            val dotSize = dimension * 0.25f
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(dotSize)
                    .border(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.surface, CircleShape)
                    .clip(CircleShape)
                    .background(MaterialTheme.extendedColors.success),
            )
        }
    }
}
