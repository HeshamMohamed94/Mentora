package com.mentora.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors
import com.mentora.android.theme.stateOpacities

/**
 * `design-system/COMPONENTS.md` § Buttons — PrimaryButton/SecondaryButton/TonalButton/TextButton
 * (lines 51-111), one parameterized composable + four named wrappers below (both call-site shapes
 * the task allows). All four share height/radius/padding/typography per that spec; only their
 * per-state color table differs, computed in [buttonColorsFor].
 *
 * Every color/dp/sp value below traces to a theme token:
 * - height 48 / radius `radius.medium` (`MaterialTheme.shapes.small`) / paddingX `space.6` /
 *   typography `label.large` — all four variants, all from the theme.
 * - Min width 88 (line 57) has **no** matching `spacing.scale` step (0/4/8/12/16/20/24/32/40/48/64) —
 *   disclosed gap, hardcoded once here citing `COMPONENTS.md` line 57 directly, per the task's own
 *   guidance for a spec value with no token.
 * - Disabled container overlay / pressed state-layer overlays are computed from
 *   `MaterialTheme.colorScheme.*` + `MaterialTheme.stateOpacities.*` (both theme accessors), never a
 *   raw alpha literal.
 * - PrimaryButton's Pressed state uses the theme's `extendedColors.primaryPressed` (an explicit
 *   distinct color per the spec, not an opacity overlay) — see that field's kdoc in `MentoraTheme.kt`
 *   for why it needed a small, disclosed theme extension.
 *
 * Hover is intentionally not implemented: `COMPONENTS.md`'s own Interaction State Matrix marks every
 * button's Hover row "(pointer platforms)" — Android is touch-primary and has no hover state to
 * reproduce, the same reasoning the task text calls out explicitly for [MentoraIconButton]. Pressed
 * is implemented as an explicit `interactionSource`-driven color swap to the exact token color for
 * every variant (not Compose's default ripple), so the rendered color always matches the spec table
 * exactly rather than Android's default onSurface-tinted ripple tone.
 */
enum class MentoraButtonVariant { Primary, Secondary, Tonal, Text }

/** Test-only hook (`ui.test.onNodeWithTag`) for asserting the loading spinner renders. */
const val MentoraButtonSpinnerTestTag = "mentora-button-spinner"

// Deviation: the task lists "TextButton" as one of the four wrapper names, but that name collides
// with androidx.compose.material3.TextButton (a different composable, imported by name elsewhere
// in a Compose codebase) — the wrapper below is named [MentoraTextButton] to avoid that clash,
// consistent with every other component in this file being Mentora-prefixed.

private data class ButtonColors(
    val container: Color,
    val content: Color,
    val border: Color?,
)

@Composable
private fun buttonColorsFor(
    variant: MentoraButtonVariant,
    enabled: Boolean,
    pressed: Boolean,
): ButtonColors {
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val opacities = MaterialTheme.stateOpacities
    val disabledText = colorScheme.onSurface.copy(alpha = opacities.disabledContent)

    return when (variant) {
        MentoraButtonVariant.Primary -> when {
            !enabled -> ButtonColors(
                container = colorScheme.primary.copy(alpha = opacities.disabledContainer).compositeOver(colorScheme.surface),
                content = disabledText,
                border = null,
            )
            pressed -> ButtonColors(extended.primaryPressed, colorScheme.onPrimary, null)
            else -> ButtonColors(colorScheme.primary, colorScheme.onPrimary, null)
        }

        MentoraButtonVariant.Secondary -> when {
            !enabled -> ButtonColors(Color.Transparent, disabledText, colorScheme.outlineVariant)
            pressed -> ButtonColors(
                colorScheme.primary.copy(alpha = opacities.pressed),
                colorScheme.primary,
                colorScheme.primary,
            )
            else -> ButtonColors(Color.Transparent, colorScheme.primary, colorScheme.primary)
        }

        MentoraButtonVariant.Tonal -> when {
            !enabled -> ButtonColors(colorScheme.surfaceVariant, disabledText, null)
            pressed -> ButtonColors(
                colorScheme.onPrimaryContainer.copy(alpha = opacities.pressed).compositeOver(colorScheme.primaryContainer),
                colorScheme.onPrimaryContainer,
                null,
            )
            else -> ButtonColors(colorScheme.primaryContainer, colorScheme.onPrimaryContainer, null)
        }

        MentoraButtonVariant.Text -> when {
            !enabled -> ButtonColors(Color.Transparent, disabledText, null)
            pressed -> ButtonColors(colorScheme.primary.copy(alpha = opacities.pressed), colorScheme.primary, null)
            else -> ButtonColors(Color.Transparent, colorScheme.primary, null)
        }
    }
}

/**
 * The base parameterized button. [loading] hides the label behind a centered spinner while keeping
 * the button's width (label rendered at `alpha = 0f`, not removed from the layout — `COMPONENTS.md`
 * line 70's "label hidden ... width preserved") and makes the button non-interactive.
 * [disabledReason] becomes the button's accessibility `stateDescription` when disabled, so a
 * screen-reader user hears *why* rather than just that the button is off — a placeholder for a
 * future tooltip UI, not a substitute for one.
 */
@Composable
fun MentoraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: MentoraButtonVariant = MentoraButtonVariant.Primary,
    enabled: Boolean = true,
    loading: Boolean = false,
    disabledReason: String? = null,
    leadingIcon: MentoraIconName? = null,
    trailingIcon: MentoraIconName? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    // `enabled` (not `interactive`) drives the color lookup: loading must render like the
    // normal/default-state button (COMPONENTS.md line 70 — background `color.brand.primary`,
    // spinner in the variant's normal content color), never the disabled palette. `interactive`
    // is only for hit-testing/interaction-blocking below (clickable's `enabled` param).
    val interactive = enabled && !loading
    val colors = buttonColorsFor(variant, enabled, pressed)
    val shape = MaterialTheme.shapes.small // radius.medium (12) — platform-contract.json mapping.
    // 48/40 are component.button.{primary,secondary,tonal}.height / component.button.text.height in
    // design-tokens.json itself — a plain literal there (not a `{spacing.scale...}` alias), same as
    // every other component's `height`/`size` field in this file; only color/radius/spacing/
    // typography are token *aliases* needing an indirection through MentoraTokens/MaterialTheme.
    val height = if (variant == MentoraButtonVariant.Text) 40.dp else 48.dp

    Box(
        modifier = modifier
            // F4 fix: `clickable` outermost, `minimumInteractiveComponentSize()` immediately inside
            // it, and the real visual size (`height`, below) innermost — mirrors M3's own
            // Checkbox/RadioButton pattern. Verified by measurement (captureToImage-style bounds
            // probe): with this ordering, MentoraTextButton's 40dp visual height still resolves to a
            // 48dp *tappable* region (Android's ACCESSIBILITY.md § 4 minimum); the visual pill stays
            // exactly `height` (40/48dp), centered inside the invisible hit-slop margin. The
            // Primary/Secondary/Tonal 48dp variants are already at the minimum, so this is a no-op
            // for them (`max(48, 48) == 48`).
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = interactive,
                onClick = onClick,
            )
            .minimumInteractiveComponentSize()
            .defaultMinSize(minWidth = 88.dp) // COMPONENTS.md line 57 — no matching spacing token, see kdoc above.
            .height(height)
            .clip(shape)
            .background(colors.container)
            .let {
                if (colors.border != null) it.border(BorderStroke(MentoraDimens.borderWidthDefault, colors.border), shape) else it
            }
            .let {
                if (focused) it.border(BorderStroke(MentoraDimens.borderWidthFocus, MaterialTheme.colorScheme.primary), shape) else it
            }
            .semantics {
                if (!enabled) {
                    disabled()
                    disabledReason?.let { stateDescription = it }
                }
            }
            .padding(horizontal = MentoraDimens.spacing.space6),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .testTag(MentoraButtonSpinnerTestTag)
                    .size(MentoraDimens.iconSize.medium),
                color = colors.content,
                strokeWidth = MentoraDimens.borderWidthFocus,
            )
        }
        Row(
            modifier = Modifier.alpha(if (loading) 0f else 1f),
            horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingIcon?.let { MentoraIcon(name = it, contentDescription = null, size = MentoraDimens.iconSize.medium, tint = colors.content) }
            Text(text = text, style = MaterialTheme.typography.labelLarge, color = colors.content)
            trailingIcon?.let { MentoraIcon(name = it, contentDescription = null, size = MentoraDimens.iconSize.medium, tint = colors.content) }
        }
    }
}

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    disabledReason: String? = null,
    leadingIcon: MentoraIconName? = null,
    trailingIcon: MentoraIconName? = null,
) = MentoraButton(text, onClick, modifier, MentoraButtonVariant.Primary, enabled, loading, disabledReason, leadingIcon, trailingIcon)

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    disabledReason: String? = null,
    leadingIcon: MentoraIconName? = null,
    trailingIcon: MentoraIconName? = null,
) = MentoraButton(text, onClick, modifier, MentoraButtonVariant.Secondary, enabled, loading, disabledReason, leadingIcon, trailingIcon)

@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    disabledReason: String? = null,
    leadingIcon: MentoraIconName? = null,
    trailingIcon: MentoraIconName? = null,
) = MentoraButton(text, onClick, modifier, MentoraButtonVariant.Tonal, enabled, loading, disabledReason, leadingIcon, trailingIcon)

@Composable
fun MentoraTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    disabledReason: String? = null,
    leadingIcon: MentoraIconName? = null,
    trailingIcon: MentoraIconName? = null,
) = MentoraButton(text, onClick, modifier, MentoraButtonVariant.Text, enabled, loading, disabledReason, leadingIcon, trailingIcon)
