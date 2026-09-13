package com.mentora.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.stateOpacities

/**
 * `design-system/COMPONENTS.md` § IconButton (lines 113-127). Radius `radius.full` -> [CircleShape]
 * (per `MentoraTheme.kt`'s own comment: full-radius shapes are always applied directly at the call
 * site, never through the shared `Shapes` object). Icon size `icon.default` (24dp,
 * [MentoraDimens.iconSize]).
 *
 * **Touch target: 48dp, not the component table's literal "40×40."** `ACCESSIBILITY.md`'s platform
 * table states plainly "Android | 48dp (Material minimum, **use as default**)" and
 * [MentoraDimens.touchTargetMin] is exactly that verified token — since Android's own accessibility
 * contract in this design system explicitly overrides the cross-platform 40×40 baseline with a
 * platform default, sizing the real tappable area at 48dp (icon still rendered at 24dp, centered)
 * is the token-correct choice here, not a hardcoded deviation from the spec.
 *
 * Hover is skipped (Android is touch-primary, no pointer-hover state to reproduce — the task's own
 * instruction for this component). Pressed reuses this file's [MentoraButton]-style approach: an
 * explicit `interactionSource`-driven background swap to `color.text.primary @ state.pressedOpacity`
 * (Compose's default ripple would use `onSurface`, not necessarily identical to `text.primary`,
 * and it's simpler to stay consistent with every other component in this kit's pressed-state
 * pattern than to mix a plain background swap here with a customized ripple color elsewhere).
 *
 * [selected] represents "an active/selected toggle" (spec line 119) — tints the icon
 * `color.brand.primary` instead of the default `color.text.secondary`.
 */
@Composable
fun MentoraIconButton(
    icon: MentoraIconName,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val colorScheme = MaterialTheme.colorScheme
    val opacities = MaterialTheme.stateOpacities

    val iconTint = when {
        !enabled -> colorScheme.onSurface.copy(alpha = opacities.disabledContent)
        pressed || focused -> colorScheme.onSurface
        selected -> colorScheme.primary
        else -> colorScheme.onSurfaceVariant
    }
    val backgroundColor = when {
        pressed && enabled -> colorScheme.onSurface.copy(alpha = opacities.pressed)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .size(MentoraDimens.touchTargetMin)
            .clip(CircleShape)
            .background(backgroundColor)
            .let {
                // F7 fix: the focus ring itself is `border.width.focus` in `color.border.focus`
                // (-> colorScheme.primary per the locked token mapping), not `colorScheme.onSurface`
                // (color.text.primary) — mirrors MentoraButton.kt's own (already-correct) focus-ring
                // border. The icon's own tint ([iconTint], color.text.primary) is unrelated and stays.
                if (focused) it.border(BorderStroke(MentoraDimens.borderWidthFocus, colorScheme.primary), CircleShape) else it
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .semantics { if (!enabled) disabled() },
        contentAlignment = Alignment.Center,
    ) {
        MentoraIcon(
            name = icon,
            contentDescription = contentDescription,
            size = MentoraDimens.iconSize.default,
            tint = iconTint,
        )
    }
}
