package com.mentora.android.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

/*
 * Hand-authored theme setup — NOT generated (mirrors Web's split between generated tokens.css and
 * hand-authored components.css). Builds Material3 ColorScheme/Typography/Shapes exclusively from
 * MentoraTokens.kt's generated constants, per the exact slot mapping locked in
 * design-to-code/shared/platform-contract.json#/android. No color/dp/sp literal appears in this
 * file outside that mapping — every value traces back to the generated token objects.
 *
 * Material You dynamic color is explicitly forbidden (visualParityRule, D53) — this file never
 * calls dynamicLightColorScheme()/dynamicDarkColorScheme().
 */

// ---------- success/warning/info: M3 ColorScheme has no built-in slot for these ----------
// (platform-contract.json#/android/colorSchemeMapping/light's "success.*"/"warning.*"/"info.*"
// notes — "custom ColorScheme extension, same pattern web uses for --color-success-*").

/** success/warning/info colors — the one visual-semantic group Material3's ColorScheme has no
 *  built-in slot for. Accessed via [MaterialTheme.extendedColors], mirroring the built-in
 *  `MaterialTheme.colorScheme` accessor convention. */
data class MentoraExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccess: Color,
    val onSuccessContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarning: Color,
    val onWarningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val onInfo: Color,
    val onInfoContainer: Color,
)

private val LightExtendedColors = MentoraExtendedColors(
    success = MentoraColorsLight.successDefault,
    successContainer = MentoraColorsLight.successContainer,
    onSuccess = MentoraColorsLight.successOnSuccess,
    onSuccessContainer = MentoraColorsLight.successOnSuccessContainer,
    warning = MentoraColorsLight.warningDefault,
    warningContainer = MentoraColorsLight.warningContainer,
    onWarning = MentoraColorsLight.warningOnWarning,
    onWarningContainer = MentoraColorsLight.warningOnWarningContainer,
    info = MentoraColorsLight.infoDefault,
    infoContainer = MentoraColorsLight.infoContainer,
    onInfo = MentoraColorsLight.infoOnInfo,
    onInfoContainer = MentoraColorsLight.infoOnInfoContainer,
)

private val DarkExtendedColors = MentoraExtendedColors(
    success = MentoraColorsDark.successDefault,
    successContainer = MentoraColorsDark.successContainer,
    onSuccess = MentoraColorsDark.successOnSuccess,
    onSuccessContainer = MentoraColorsDark.successOnSuccessContainer,
    warning = MentoraColorsDark.warningDefault,
    warningContainer = MentoraColorsDark.warningContainer,
    onWarning = MentoraColorsDark.warningOnWarning,
    onWarningContainer = MentoraColorsDark.warningOnWarningContainer,
    info = MentoraColorsDark.infoDefault,
    infoContainer = MentoraColorsDark.infoContainer,
    onInfo = MentoraColorsDark.infoOnInfo,
    onInfoContainer = MentoraColorsDark.infoOnInfoContainer,
)

val LocalMentoraExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** `MaterialTheme.extendedColors.success` etc. — same accessor idiom as `MaterialTheme.colorScheme`. */
val MaterialTheme.extendedColors: MentoraExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMentoraExtendedColors.current

// ---------- ColorScheme (light/dark) ----------
// Every named parameter below is one row of platform-contract.json#/android/colorSchemeMapping.
// Slots NOT listed in that mapping (tertiary*, surfaceBright/Dim/Container*, surfaceTint, ...) are
// deliberately left at Material3's own defaults — filling them would mean inventing a mapping the
// contract doesn't specify. text.disabled ("ColorScheme.onSurface @ disabledContentOpacity"),
// border.focus/text.link (both already equal brand.primary's value in theme-{light,dark}.json, so
// they resolve for free via the `primary` slot) are usage-site guidance, not separate ColorScheme
// constructor parameters — M3 doesn't have such slots.

private fun mentoraLightColorScheme(): ColorScheme = lightColorScheme(
    primary = MentoraColorsLight.brandPrimary,
    onPrimary = MentoraColorsLight.brandOnPrimary,
    primaryContainer = MentoraColorsLight.brandPrimaryContainer,
    onPrimaryContainer = MentoraColorsLight.brandOnPrimaryContainer,
    inversePrimary = MentoraColorsLight.brandOnSurfaceInverse,
    secondary = MentoraColorsLight.secondaryDefault,
    onSecondary = MentoraColorsLight.secondaryOnSecondary,
    secondaryContainer = MentoraColorsLight.secondaryContainer,
    onSecondaryContainer = MentoraColorsLight.secondaryOnSecondaryContainer,
    background = MentoraColorsLight.backgroundPrimary,
    onBackground = MentoraColorsLight.textPrimary,
    surface = MentoraColorsLight.surfaceDefault,
    onSurface = MentoraColorsLight.textPrimary,
    surfaceVariant = MentoraColorsLight.surfaceVariant,
    onSurfaceVariant = MentoraColorsLight.textSecondary,
    surfaceContainerHigh = MentoraColorsLight.surfaceElevated,
    inverseSurface = MentoraColorsLight.surfaceInverse,
    inverseOnSurface = MentoraColorsLight.textInverse,
    error = MentoraColorsLight.errorDefault,
    onError = MentoraColorsLight.errorOnError,
    errorContainer = MentoraColorsLight.errorContainer,
    onErrorContainer = MentoraColorsLight.errorOnErrorContainer,
    outline = MentoraColorsLight.borderStrong,
    outlineVariant = MentoraColorsLight.borderDefault,
    scrim = MentoraColorsLight.overlayScrim,
)

private fun mentoraDarkColorScheme(): ColorScheme = darkColorScheme(
    // Same slot map as mentoraLightColorScheme() — only the source object (MentoraColorsDark
    // instead of MentoraColorsLight) differs, per platform-contract.json's "no slot is ever
    // re-mapped between light/dark" rule.
    primary = MentoraColorsDark.brandPrimary,
    onPrimary = MentoraColorsDark.brandOnPrimary,
    primaryContainer = MentoraColorsDark.brandPrimaryContainer,
    onPrimaryContainer = MentoraColorsDark.brandOnPrimaryContainer,
    inversePrimary = MentoraColorsDark.brandOnSurfaceInverse,
    secondary = MentoraColorsDark.secondaryDefault,
    onSecondary = MentoraColorsDark.secondaryOnSecondary,
    secondaryContainer = MentoraColorsDark.secondaryContainer,
    onSecondaryContainer = MentoraColorsDark.secondaryOnSecondaryContainer,
    background = MentoraColorsDark.backgroundPrimary,
    onBackground = MentoraColorsDark.textPrimary,
    surface = MentoraColorsDark.surfaceDefault,
    onSurface = MentoraColorsDark.textPrimary,
    surfaceVariant = MentoraColorsDark.surfaceVariant,
    onSurfaceVariant = MentoraColorsDark.textSecondary,
    surfaceContainerHigh = MentoraColorsDark.surfaceElevated,
    inverseSurface = MentoraColorsDark.surfaceInverse,
    inverseOnSurface = MentoraColorsDark.textInverse,
    error = MentoraColorsDark.errorDefault,
    onError = MentoraColorsDark.errorOnError,
    errorContainer = MentoraColorsDark.errorContainer,
    onErrorContainer = MentoraColorsDark.errorOnErrorContainer,
    outline = MentoraColorsDark.borderStrong,
    outlineVariant = MentoraColorsDark.borderDefault,
    scrim = MentoraColorsDark.overlayScrim,
)

// ---------- Typography ----------
// Latin: FontFamily.Default resolves to Roboto on Android (the system default) — never overridden
// away from it, per PHASE_4_ANDROID_PLAN.md § 3 ("Fonts: Roboto (Latin, system)").
//
// Arabic: design-tokens.json's typography.fontFamily.androidArabic names "Noto Sans Arabic"
// (LOCALIZATION.md § 4). No Noto Sans Arabic font file exists anywhere in this repo (checked
// web/, design-system/ — nothing bundled) and this environment cannot download the asset, so
// FontFamily.SansSerif is used as an honestly-disclosed PLACEHOLDER below until the real font is
// added as an Android font resource in a later task. This placeholder affects only which glyphs
// render the Arabic text — the structural adjustments LOCALIZATION.md § 4 requires (letterSpacing
// zeroed, body line-height x1.1 for Arabic runs) are pure code, implemented unconditionally in
// mentoraTypography() below, independent of which font backs it.
private val MentoraArabicFontFamily: FontFamily = FontFamily.SansSerif

/**
 * Builds a Compose [Typography] from the generated [MentoraTypographyTokens], per
 * platform-contract.json#/android/typographyMapping. Slots the contract doesn't map
 * (displaySmall, titleMedium, titleSmall) are left at Typography()'s own Material3 defaults.
 *
 * [arabicScript] applies LOCALIZATION.md § 4's Arabic-only adjustments on top of the SAME shared
 * scale values (never a separate type scale): letterSpacing zeroed (Arabic is a cursive/connected
 * script; tracking breaks glyph joining) and `body.*` line-height multiplied by 1.1 (Arabic
 * diacritics/ascenders need more vertical room than the Latin metrics assume).
 */
private fun mentoraTypography(fontFamily: FontFamily, arabicScript: Boolean): Typography {
    fun style(token: MentoraTypographyStyle, isBody: Boolean = false): TextStyle {
        val letterSpacing = if (arabicScript) 0.sp else token.letterSpacing
        val lineHeight = if (arabicScript && isBody) (token.lineHeight.value * 1.1f).sp else token.lineHeight
        return TextStyle(
            fontFamily = fontFamily,
            fontSize = token.fontSize,
            lineHeight = lineHeight,
            fontWeight = token.fontWeight,
            letterSpacing = letterSpacing,
        )
    }

    return Typography(
        displayLarge = style(MentoraTypographyTokens.displayLarge),
        displayMedium = style(MentoraTypographyTokens.displayMedium),
        headlineLarge = style(MentoraTypographyTokens.headingH1),
        headlineMedium = style(MentoraTypographyTokens.headingH2),
        headlineSmall = style(MentoraTypographyTokens.headingH3),
        titleLarge = style(MentoraTypographyTokens.headingH4),
        bodyLarge = style(MentoraTypographyTokens.bodyLarge, isBody = true),
        bodyMedium = style(MentoraTypographyTokens.bodyMedium, isBody = true),
        bodySmall = style(MentoraTypographyTokens.bodySmall, isBody = true),
        labelLarge = style(MentoraTypographyTokens.labelLarge),
        labelMedium = style(MentoraTypographyTokens.labelMedium),
        labelSmall = style(MentoraTypographyTokens.caption),
    )
}

// ---------- Shapes ----------
// platform-contract.json#/android/shapeMapping. radius.full (chips/badges/avatar/progress bar) is
// applied directly at each component's own call site via CircleShape/RoundedCornerShape(percent =
// 50), never through this shared Shapes object — Material3's Shapes has no slot for it, and the
// contract describes it as a per-component shape, not a theme-wide one. `extraLarge` is likewise
// left at Material3's own default since the contract doesn't map anything to it.
private val MentoraShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(MentoraRadiusTokens.small),
    small = RoundedCornerShape(MentoraRadiusTokens.medium),
    medium = RoundedCornerShape(MentoraRadiusTokens.large),
    // TODO(later Curriculum Bottom Sheet / dialog task, T13+): radius.xlarge -> Shapes.large is
    // correct for the base slot, but platform-contract.json calls for TOP-CORNERS-ONLY on actual
    // sheets/dialogs (RoundedCornerShape(topStart=24dp, topEnd=24dp, bottomStart=0, bottomEnd=0)),
    // not this all-corner default. No sheet/dialog composable exists yet, so this base slot is
    // left as the plain (all-corner) shape; apply the top-corners-only override at the sheet/dialog
    // call site when that task lands, rather than changing this shared slot.
    large = RoundedCornerShape(MentoraRadiusTokens.xlarge),
)

// ---------- MentoraTheme ----------

/**
 * Mentora's Material3 theme wrapper. Wrap the app's (or a preview's) content in this instead of
 * a raw `MaterialTheme { }` so colors/typography/shapes always resolve from the generated design
 * tokens, never Material3's own baseline palette/type scale.
 *
 * Arabic-script detection currently reads the system configuration's active locale — this is a
 * structurally-correct placeholder for Task 4's app-owned locale/theme bootstrap (G1/G2 in
 * PHASE_4_ANDROID_PLAN.md § 6), which will thread the user's chosen in-app UI locale through
 * instead of relying on the device's system locale.
 */
@Composable
fun MentoraTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) mentoraDarkColorScheme() else mentoraLightColorScheme()
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    val stateOpacities = if (darkTheme) DarkStateOpacities else LightStateOpacities

    val configuration = LocalConfiguration.current
    val arabicScript = configuration.locales.get(0)?.language == "ar"
    val typography = mentoraTypography(
        fontFamily = if (arabicScript) MentoraArabicFontFamily else FontFamily.Default,
        arabicScript = arabicScript,
    )

    CompositionLocalProvider(
        LocalMentoraExtendedColors provides extendedColors,
        LocalMentoraStateOpacities provides stateOpacities,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = MentoraShapes,
            content = content,
        )
    }
}
