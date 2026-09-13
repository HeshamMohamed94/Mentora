package com.mentora.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraRadiusTokens

/** Which side of the conversation a bubble belongs to — drives background/text/radius-tail per
 *  `design-system/COMPONENTS.md` § AITutorBubble (lines 678-689). */
enum class AiTutorSender { Ai, User }

/**
 * `design-system/COMPONENTS.md` § AITutorBubble. AI: `color.surface.variant` background /
 * `color.text.primary` text. User: `color.brand.primaryContainer` background /
 * `color.brand.onPrimaryContainer` text. Radius `radius.large` (16) with the tail corner (bottom-left
 * for AI, bottom-right for user — mirrored for the other sender) reduced to `radius.small` (8). Max
 * width 80% of the chat container: computed via [BoxWithConstraints] + `widthIn(max = ...)` so a
 * short message doesn't stretch to fill 80% — only a message actually long enough to hit that ceiling
 * wraps at it, matching "max width," not "fixed width."
 *
 * "The AI Tutor uses the exact same surfaces, radii, and type scale as the rest of the product" per
 * the spec's own note — every value below is a `MaterialTheme`/`extendedColors` token, no bubble-only
 * literal.
 */
@Composable
fun AITutorBubble(
    text: String,
    sender: AiTutorSender,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val background = when (sender) {
        AiTutorSender.Ai -> colorScheme.surfaceVariant
        AiTutorSender.User -> colorScheme.primaryContainer
    }
    val contentColor = when (sender) {
        AiTutorSender.Ai -> colorScheme.onSurface
        AiTutorSender.User -> colorScheme.onPrimaryContainer
    }
    val shape = when (sender) {
        AiTutorSender.Ai -> RoundedCornerShape(
            topStart = MentoraRadiusTokens.large,
            topEnd = MentoraRadiusTokens.large,
            bottomEnd = MentoraRadiusTokens.large,
            bottomStart = MentoraRadiusTokens.small,
        )
        AiTutorSender.User -> RoundedCornerShape(
            topStart = MentoraRadiusTokens.large,
            topEnd = MentoraRadiusTokens.large,
            bottomStart = MentoraRadiusTokens.large,
            bottomEnd = MentoraRadiusTokens.small,
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val maxBubbleWidth = maxWidth * 0.8f
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (sender == AiTutorSender.User) Arrangement.End else Arrangement.Start,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = maxBubbleWidth),
                shape = shape,
                color = background,
            ) {
                Text(
                    text = text,
                    modifier = Modifier.padding(
                        horizontal = MentoraDimens.spacing.space4,
                        vertical = MentoraDimens.spacing.space3,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            }
        }
    }
}
