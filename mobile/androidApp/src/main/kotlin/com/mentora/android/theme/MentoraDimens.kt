package com.mentora.android.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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

    /**
     * touchTarget.web_px (design-tokens.json § touchTarget, == 44 today) — deliberately NOT
     * [touchTargetMin] (48, android_dp). `COMPONENTS.md` § Select's option-row height and § Inputs'
     * Select option-list explicitly cite `touchTarget.web_px` (44), not the Android minimum, for
     * dropdown option rows on every platform including Android — Task 2's generator only emitted
     * `touchTarget.android_dp` into [MentoraTouchTargetMinDp], so this sibling value has no
     * generated constant. Disclosed gap (Task 5): hardcoded here, once, traceable to
     * design-tokens.json § touchTarget.web_px, rather than re-typed at each Select call site.
     */
    val touchTargetWeb: Dp = 44.dp

    /**
     * border.width (design-tokens.json § border, `{ "default": 1, "focus": 2 }`) — disclosed gap
     * (Task 5): Task 2's generator never emitted a border-width Kotlin object (only the border
     * *colors* — `MentoraColorsLight/Dark.border*` — were generated), so these two values have no
     * generated constant to re-export. Hardcoded here, once, traceable to design-tokens.json
     * § border.width, rather than re-typed as a raw `1.dp`/`2.dp` literal at every input/button call
     * site that needs a default vs. focused border stroke width.
     */
    val borderWidthDefault: Dp = 1.dp
    val borderWidthFocus: Dp = 2.dp

    /**
     * avatar.* (design-tokens.json § avatar, `{ "small": 24, "medium": 40, "large": 64, "xlarge": 96 }`)
     * — confirmed present in design-tokens.json (unlike border.width, this key legitimately exists
     * in the source) but, like border.width, disclosed gap (Task 5): Task 2's generator never walked
     * an `avatar.*` namespace into a Kotlin object, so [MentoraTokens.kt] has nothing to re-export.
     * Hardcoded here, once, traceable to design-tokens.json § avatar / `COMPONENTS.md` § Avatar,
     * rather than re-typed at the Avatar composable's call sites.
     */
    val avatarSmall: Dp = 24.dp
    val avatarMedium: Dp = 40.dp
    val avatarLarge: Dp = 64.dp
    val avatarXLarge: Dp = 96.dp
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
