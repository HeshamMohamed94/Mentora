// GENERATED — DO NOT EDIT.
// Source: design-system/design-tokens.json, design-system/themes/theme-{light,dark}.json
// Regenerate with: npm run generate (from tools/token-pipeline/), or node tools/token-pipeline/generate.js

package com.mentora.android.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One typography.scale entry (design-tokens.json § typography.scale), in Compose-native units. */
data class MentoraTypographyStyle(
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val fontWeight: FontWeight,
    val letterSpacing: TextUnit,
)

/** color.semantic.light (design-tokens.json / theme-light.json) — one property per flattened
 *  dot-path in the color tree. */
object MentoraColorsLight {
    val backgroundPrimary: Color = Color(0xFFF8F9FC)
    val backgroundSecondary: Color = Color(0xFFF1F2F7)
    val surfaceDefault: Color = Color(0xFFFFFFFF)
    val surfaceElevated: Color = Color(0xFFFFFFFF)
    val surfaceVariant: Color = Color(0xFFF0F1F6)
    val surfaceInverse: Color = Color(0xFF1A1B20)
    val textPrimary: Color = Color(0xFF1A1B20)
    val textSecondary: Color = Color(0xFF5F6069)
    val textDisabled: Color = Color(0xFF9A9BA3)
    val textInverse: Color = Color(0xFFFFFFFF)
    val textLink: Color = Color(0xFF6558D3)
    val borderDefault: Color = Color(0xFFE1E2E8)
    val borderStrong: Color = Color(0xFF8B8C96)
    val borderFocus: Color = Color(0xFF6558D3)
    val borderError: Color = Color(0xFFBA1A1A)
    val brandPrimary: Color = Color(0xFF6558D3)
    val brandPrimaryHover: Color = Color(0xFF5548C2)
    val brandPrimaryPressed: Color = Color(0xFF4A3EB0)
    val brandPrimaryContainer: Color = Color(0xFFE8E5FF)
    val brandOnPrimary: Color = Color(0xFFFFFFFF)
    val brandOnPrimaryContainer: Color = Color(0xFF2B2170)
    val brandOnSurfaceInverse: Color = Color(0xFFC8C2FF)
    val secondaryDefault: Color = Color(0xFF4A62F0)
    val secondaryHover: Color = Color(0xFF4058E0)
    val secondaryContainer: Color = Color(0xFFE2E7FF)
    val secondaryOnSecondary: Color = Color(0xFFFFFFFF)
    val secondaryOnSecondaryContainer: Color = Color(0xFF1C2470)
    val accentDefault: Color = Color(0xFF7C4DFF)
    val successDefault: Color = Color(0xFF2E7D32)
    val successContainer: Color = Color(0xFFE3F5E5)
    val successOnSuccess: Color = Color(0xFFFFFFFF)
    val successOnSuccessContainer: Color = Color(0xFF0D3312)
    val warningDefault: Color = Color(0xFFED8B00)
    val warningContainer: Color = Color(0xFFFFEDD6)
    val warningOnWarning: Color = Color(0xFF3A2200)
    val warningOnWarningContainer: Color = Color(0xFF4A2C00)
    val errorDefault: Color = Color(0xFFBA1A1A)
    val errorContainer: Color = Color(0xFFFFDAD6)
    val errorOnError: Color = Color(0xFFFFFFFF)
    val errorOnErrorContainer: Color = Color(0xFF410002)
    val infoDefault: Color = Color(0xFF1976D2)
    val infoContainer: Color = Color(0xFFDCEBFC)
    val infoOnInfo: Color = Color(0xFFFFFFFF)
    val infoOnInfoContainer: Color = Color(0xFF0B2E4E)
    val overlayScrim: Color = Color(0x7A111217)
    val overlayChipScrim: Color = Color(0xB8111217)
}

/** color.semantic.dark (design-tokens.json / theme-dark.json) — the SAME property set as
 *  MentoraColorsLight, dark-theme values. */
object MentoraColorsDark {
    val backgroundPrimary: Color = Color(0xFF111217)
    val backgroundSecondary: Color = Color(0xFF15161C)
    val surfaceDefault: Color = Color(0xFF191A20)
    val surfaceElevated: Color = Color(0xFF24252E)
    val surfaceVariant: Color = Color(0xFF22232B)
    val surfaceInverse: Color = Color(0xFFF2F0F7)
    val textPrimary: Color = Color(0xFFF2F0F7)
    val textSecondary: Color = Color(0xFFC7C5CF)
    val textDisabled: Color = Color(0xFF777780)
    val textInverse: Color = Color(0xFF1A1B20)
    val textLink: Color = Color(0xFFC8C2FF)
    val borderDefault: Color = Color(0xFF383941)
    val borderStrong: Color = Color(0xFF71727D)
    val borderFocus: Color = Color(0xFFC8C2FF)
    val borderError: Color = Color(0xFFFFB4AB)
    val brandPrimary: Color = Color(0xFFC8C2FF)
    val brandPrimaryHover: Color = Color(0xFFB4AEEF)
    val brandPrimaryPressed: Color = Color(0xFFA29BE0)
    val brandPrimaryContainer: Color = Color(0xFF4E449E)
    val brandOnPrimary: Color = Color(0xFF2B2170)
    val brandOnPrimaryContainer: Color = Color(0xFFE8E5FF)
    val brandOnSurfaceInverse: Color = Color(0xFF6558D3)
    val secondaryDefault: Color = Color(0xFFBBC3FF)
    val secondaryHover: Color = Color(0xFFA7B0F5)
    val secondaryContainer: Color = Color(0xFF3B4278)
    val secondaryOnSecondary: Color = Color(0xFF1C2470)
    val secondaryOnSecondaryContainer: Color = Color(0xFFE2E7FF)
    val accentDefault: Color = Color(0xFFB69CFF)
    val successDefault: Color = Color(0xFF81C784)
    val successContainer: Color = Color(0xFF1E4620)
    val successOnSuccess: Color = Color(0xFF0D3312)
    val successOnSuccessContainer: Color = Color(0xFFE3F5E5)
    val warningDefault: Color = Color(0xFFFFB74D)
    val warningContainer: Color = Color(0xFF5C3D00)
    val warningOnWarning: Color = Color(0xFF4A2C00)
    val warningOnWarningContainer: Color = Color(0xFFFFEDD6)
    val errorDefault: Color = Color(0xFFFFB4AB)
    val errorContainer: Color = Color(0xFF93000A)
    val errorOnError: Color = Color(0xFF410002)
    val errorOnErrorContainer: Color = Color(0xFFFFDAD6)
    val infoDefault: Color = Color(0xFF90CAF9)
    val infoContainer: Color = Color(0xFF0D47A1)
    val infoOnInfo: Color = Color(0xFF0B2E4E)
    val infoOnInfoContainer: Color = Color(0xFFDCEBFC)
    val overlayScrim: Color = Color(0xA3000000)
    val overlayChipScrim: Color = Color(0xB8111217)
}

/** theme-light.json's stateOpacity — hover/pressed/focus/disabled interaction-state opacities. */
object MentoraStateOpacityLight {
    val hoverOpacity: Float = 0.08f
    val pressedOpacity: Float = 0.12f
    val focusOpacity: Float = 0.12f
    val disabledContentOpacity: Float = 0.38f
    val disabledContainerOpacity: Float = 0.12f
}

/** theme-dark.json's stateOpacity — same properties as MentoraStateOpacityLight, dark values. */
object MentoraStateOpacityDark {
    val hoverOpacity: Float = 0.08f
    val pressedOpacity: Float = 0.16f
    val focusOpacity: Float = 0.16f
    val disabledContentOpacity: Float = 0.38f
    val disabledContainerOpacity: Float = 0.16f
}

/** typography.scale (design-tokens.json) — one MentoraTypographyStyle per scale entry. */
object MentoraTypographyTokens {
    val displayLarge: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 48.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight(700),
        letterSpacing = (-0.25).sp,
    )

    val displayMedium: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 40.sp,
        lineHeight = 48.sp,
        fontWeight = FontWeight(700),
        letterSpacing = (-0.25).sp,
    )

    val headingH1: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 32.sp,
        lineHeight = 40.sp,
        fontWeight = FontWeight(700),
        letterSpacing = 0.sp,
    )

    val headingH2: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 28.sp,
        lineHeight = 36.sp,
        fontWeight = FontWeight(700),
        letterSpacing = 0.sp,
    )

    val headingH3: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight(600),
        letterSpacing = 0.sp,
    )

    val headingH4: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight(600),
        letterSpacing = 0.15.sp,
    )

    val bodyLarge: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 18.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight(400),
        letterSpacing = 0.15.sp,
    )

    val bodyMedium: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight(400),
        letterSpacing = 0.25.sp,
    )

    val bodySmall: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight(400),
        letterSpacing = 0.25.sp,
    )

    val labelLarge: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight(600),
        letterSpacing = 0.1.sp,
    )

    val labelMedium: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight(600),
        letterSpacing = 0.5.sp,
    )

    val caption: MentoraTypographyStyle = MentoraTypographyStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight(400),
        letterSpacing = 0.4.sp,
    )
}

/** shape.radius (design-tokens.json), as Dp. */
object MentoraRadiusTokens {
    val none: Dp = 0.dp
    val small: Dp = 8.dp
    val medium: Dp = 12.dp
    val large: Dp = 16.dp
    val xlarge: Dp = 24.dp
    val full: Dp = 999.dp
}

/** elevation.0..4 (design-tokens.json), using each level's Compose-recommended compose_dp value. */
object MentoraElevationTokens {
    val level0: Dp = 0.dp
    val level1: Dp = 1.dp
    val level2: Dp = 3.dp
    val level3: Dp = 6.dp
    val level4: Dp = 12.dp
}

/** spacing.scale (design-tokens.json), as Dp. */
object MentoraSpacingTokens {
    val space0: Dp = 0.dp
    val space1: Dp = 4.dp
    val space2: Dp = 8.dp
    val space3: Dp = 12.dp
    val space4: Dp = 16.dp
    val space5: Dp = 20.dp
    val space6: Dp = 24.dp
    val space8: Dp = 32.dp
    val space10: Dp = 40.dp
    val space12: Dp = 48.dp
    val space16: Dp = 64.dp
}

/** icon.sizes (design-tokens.json), as Dp. */
object MentoraIconSizeTokens {
    val small: Dp = 16.dp
    val medium: Dp = 20.dp
    val default: Dp = 24.dp
    val large: Dp = 32.dp
}

/** touchTarget.android_dp (design-tokens.json) — minimum touch target size. */
val MentoraTouchTargetMinDp: Dp = 48.dp
