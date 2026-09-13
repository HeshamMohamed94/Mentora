package com.mentora.android.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp

/*
 * Hand-authored (NOT generated) — re-exposes MentoraTokens.kt's generated spacing/icon/touch-target/
 * state-opacity values under call-site-friendly names. Every value here traces back to a generated
 * token constant; nothing is a new literal.
 */

/**
 * Theme-independent layout dimensions. Spacing/icon-size/touch-target never vary between light and
 * dark theme (only colors do — design-tokens.json § units: "sp scales with system font size...dp
 * does not scale with font size"), so these are plain re-exports, not a CompositionLocal.
 */
object MentoraDimens {
    /** spacing.scale (design-tokens.json), as Dp — e.g. `MentoraDimens.spacing.space4`. */
    val spacing: MentoraSpacingTokens = MentoraSpacingTokens

    /** icon.sizes (design-tokens.json), as Dp — e.g. `MentoraDimens.iconSize.default`. */
    val iconSize: MentoraIconSizeTokens = MentoraIconSizeTokens

    /**
     * touchTarget.android_dp (design-tokens.json) — the Android minimum touch target size.
     * Verified traceable to the source token (not an independently hardcoded 48): this Dp value
     * IS [MentoraTouchTargetMinDp], which the generator writes as `${tokens.touchTarget.android_dp}.dp`
     * straight from design-tokens.json § touchTarget.android_dp (== 48 today).
     */
    val touchTargetMin: Dp = MentoraTouchTargetMinDp
}

/**
 * theme-{light,dark}.json's stateOpacity values (hover/pressed/focus/disabled), wrapped in one
 * data class since — unlike [MentoraDimens]'s spacing/icon/touch-target — these DO vary between
 * light and dark theme. Provided by [com.mentora.android.theme.MentoraTheme] via
 * [LocalMentoraStateOpacities]; access through [MaterialTheme.stateOpacities], mirroring the
 * built-in `MaterialTheme.colorScheme` accessor convention (same idiom as
 * [MaterialTheme.extendedColors]).
 */
data class MentoraStateOpacities(
    val hover: Float,
    val pressed: Float,
    val focus: Float,
    val disabledContent: Float,
    val disabledContainer: Float,
)

internal val LightStateOpacities = MentoraStateOpacities(
    hover = MentoraStateOpacityLight.hoverOpacity,
    pressed = MentoraStateOpacityLight.pressedOpacity,
    focus = MentoraStateOpacityLight.focusOpacity,
    disabledContent = MentoraStateOpacityLight.disabledContentOpacity,
    disabledContainer = MentoraStateOpacityLight.disabledContainerOpacity,
)

internal val DarkStateOpacities = MentoraStateOpacities(
    hover = MentoraStateOpacityDark.hoverOpacity,
    pressed = MentoraStateOpacityDark.pressedOpacity,
    focus = MentoraStateOpacityDark.focusOpacity,
    disabledContent = MentoraStateOpacityDark.disabledContentOpacity,
    disabledContainer = MentoraStateOpacityDark.disabledContainerOpacity,
)

val LocalMentoraStateOpacities = staticCompositionLocalOf { LightStateOpacities }

/** `MaterialTheme.stateOpacities.pressed` etc. — same accessor idiom as `MaterialTheme.colorScheme`. */
val MaterialTheme.stateOpacities: MentoraStateOpacities
    @Composable
    @ReadOnlyComposable
    get() = LocalMentoraStateOpacities.current
