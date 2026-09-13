package com.mentora.android.theme

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Guards against silent drift between the GENERATED `MentoraTokens.kt` (tools/token-pipeline/
 * generate.js's Android output target) and its source-of-truth JSON — parses
 * design-system/design-tokens.json + design-system/themes/theme-{light,dark}.json directly, at
 * test time, and compares against the generated Kotlin constants. Not exhaustive — a
 * representative sample across colors (including one rgba() conversion per theme), typography,
 * shape, elevation, spacing, icon size, and touch target.
 *
 * [parseColorLiteral] is a deliberately INDEPENDENT re-implementation of generate.js's
 * `colorLiteralToKotlinArgb()` (not a call into the generator) so this test actually exercises
 * two separate readings of the same source value, rather than re-asserting the generator's own
 * arithmetic against itself.
 */
class MentoraTokensDriftTest {

    private val repoRoot: File = findRepoRoot()

    private val designTokens: JsonObject by lazy {
        Json.parseToJsonElement(File(repoRoot, "design-system/design-tokens.json").readText()).jsonObject
    }
    private val themeLight: JsonObject by lazy {
        Json.parseToJsonElement(File(repoRoot, "design-system/themes/theme-light.json").readText()).jsonObject
    }
    private val themeDark: JsonObject by lazy {
        Json.parseToJsonElement(File(repoRoot, "design-system/themes/theme-dark.json").readText()).jsonObject
    }

    // ---------- colors ----------

    @Test
    fun `light backgroundPrimary matches theme-light json`() {
        val raw = themeLight["color"]!!.jsonObject["background"]!!.jsonObject["primary"]!!.jsonPrimitive.content
        assertEquals(parseColorLiteral(raw), MentoraColorsLight.backgroundPrimary)
    }

    @Test
    fun `light brandPrimary matches theme-light json`() {
        val raw = themeLight["color"]!!.jsonObject["brand"]!!.jsonObject["primary"]!!.jsonPrimitive.content
        assertEquals(parseColorLiteral(raw), MentoraColorsLight.brandPrimary)
    }

    @Test
    fun `light overlayScrim (rgba conversion) matches theme-light json`() {
        val raw = themeLight["color"]!!.jsonObject["overlay"]!!.jsonObject["scrim"]!!.jsonPrimitive.content
        assertEquals(parseColorLiteral(raw), MentoraColorsLight.overlayScrim)
    }

    @Test
    fun `dark surfaceElevated matches theme-dark json`() {
        val raw = themeDark["color"]!!.jsonObject["surface"]!!.jsonObject["elevated"]!!.jsonPrimitive.content
        assertEquals(parseColorLiteral(raw), MentoraColorsDark.surfaceElevated)
    }

    @Test
    fun `dark overlayScrim (rgba conversion) matches theme-dark json`() {
        val raw = themeDark["color"]!!.jsonObject["overlay"]!!.jsonObject["scrim"]!!.jsonPrimitive.content
        assertEquals(parseColorLiteral(raw), MentoraColorsDark.overlayScrim)
    }

    @Test
    fun `light stateOpacity pressed matches theme-light json`() {
        val expected = themeLight["stateOpacity"]!!.jsonObject["pressed"]!!.jsonPrimitive.double
        assertEquals(expected, MentoraStateOpacityLight.pressedOpacity.toDouble(), 0.0001)
    }

    @Test
    fun `dark stateOpacity pressed matches theme-dark json (differs from light)`() {
        val expected = themeDark["stateOpacity"]!!.jsonObject["pressed"]!!.jsonPrimitive.double
        assertEquals(expected, MentoraStateOpacityDark.pressedOpacity.toDouble(), 0.0001)
    }

    // ---------- typography ----------

    @Test
    fun `typography display large matches design-tokens json`() {
        val style = designTokens["typography"]!!.jsonObject["scale"]!!.jsonObject["display.large"]!!.jsonObject
        val actual = MentoraTypographyTokens.displayLarge
        assertEquals(style["fontSize"]!!.jsonPrimitive.double, actual.fontSize.value.toDouble(), 0.001)
        assertEquals(style["lineHeight"]!!.jsonPrimitive.double, actual.lineHeight.value.toDouble(), 0.001)
        assertEquals(style["fontWeight"]!!.jsonPrimitive.int, actual.fontWeight.weight)
        assertEquals(style["letterSpacing"]!!.jsonPrimitive.double, actual.letterSpacing.value.toDouble(), 0.001)
    }

    @Test
    fun `typography body medium matches design-tokens json`() {
        val style = designTokens["typography"]!!.jsonObject["scale"]!!.jsonObject["body.medium"]!!.jsonObject
        val actual = MentoraTypographyTokens.bodyMedium
        assertEquals(style["fontSize"]!!.jsonPrimitive.double, actual.fontSize.value.toDouble(), 0.001)
        assertEquals(style["fontWeight"]!!.jsonPrimitive.int, actual.fontWeight.weight)
        assertEquals(style["letterSpacing"]!!.jsonPrimitive.double, actual.letterSpacing.value.toDouble(), 0.001)
    }

    // ---------- shape / elevation / spacing / icon / touch target ----------

    @Test
    fun `radius large matches design-tokens json`() {
        val expected = designTokens["shape"]!!.jsonObject["radius"]!!.jsonObject["large"]!!.jsonPrimitive.double
        assertEquals(expected, MentoraRadiusTokens.large.value.toDouble(), 0.001)
    }

    @Test
    fun `elevation level 2 matches design-tokens json compose_dp`() {
        val expected = designTokens["elevation"]!!.jsonObject["2"]!!.jsonObject["compose_dp"]!!.jsonPrimitive.double
        assertEquals(expected, MentoraElevationTokens.level2.value.toDouble(), 0.001)
    }

    @Test
    fun `spacing space6 matches design-tokens json`() {
        val expected = designTokens["spacing"]!!.jsonObject["scale"]!!.jsonObject["space.6"]!!.jsonPrimitive.double
        assertEquals(expected, MentoraSpacingTokens.space6.value.toDouble(), 0.001)
    }

    @Test
    fun `icon default size matches design-tokens json`() {
        val expected = designTokens["icon"]!!.jsonObject["sizes"]!!.jsonObject["default"]!!.jsonPrimitive.double
        assertEquals(expected, MentoraIconSizeTokens.default.value.toDouble(), 0.001)
    }

    @Test
    fun `touch target min is traceable to touchTarget androidDp, not an independent 48`() {
        val expected = designTokens["touchTarget"]!!.jsonObject["android_dp"]!!.jsonPrimitive.double
        assertEquals(48.0, expected, 0.0) // sanity: confirms today's source value really is 48
        assertEquals(expected, MentoraTouchTargetMinDp.value.toDouble(), 0.001)
        assertEquals(expected, MentoraDimens.touchTargetMin.value.toDouble(), 0.001)
    }

    // ---------- helpers ----------

    /** Independent re-implementation of generate.js's colorLiteralToKotlinArgb() — parses
     *  "#RRGGBB" / "rgba(r,g,b,a)" into a Compose [Color]. */
    private fun parseColorLiteral(raw: String): Color {
        val v = raw.trim()
        if (v.startsWith("#")) {
            val hex = v.removePrefix("#")
            val rgb = hex.toLong(16)
            val argb = if (hex.length == 6) (0xFFL shl 24) or rgb else rgb
            return Color(argb)
        }
        val m = Regex("""rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*(?:,\s*([\d.]+)\s*)?\)""").find(v)
            ?: error("cannot parse color literal: $raw")
        val r = m.groupValues[1].toLong()
        val g = m.groupValues[2].toLong()
        val b = m.groupValues[3].toLong()
        val a = m.groupValues[4].takeIf { it.isNotBlank() }?.toDouble() ?: 1.0
        val alpha = Math.round(a * 255).toLong()
        val argb = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        return Color(argb)
    }

    /**
     * Resolves the repo root (the directory containing `design-system/design-tokens.json`)
     * regardless of the exact working directory Gradle happens to run this test task from —
     * walks upward from `user.dir`, mirroring the convention already used by
     * `shared/src/androidUnitTest/kotlin/com/mentora/shared/di/NoUiImportBoundaryTest.kt`.
     */
    private fun findRepoRoot(): File {
        val startDir = requireNotNull(System.getProperty("user.dir")) { "system property user.dir is unset" }
        var dir: File? = File(startDir).absoluteFile
        while (dir != null) {
            if (File(dir, "design-system/design-tokens.json").isFile) return dir
            dir = dir.parentFile
        }
        error("could not locate repo root (design-system/design-tokens.json) by walking up from $startDir")
    }
}
