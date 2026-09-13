package com.mentora.android.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finding 2 (reviewer-measured HIGH defect) regression coverage: [CourseCard]'s resting `Surface`
 * must render the plain `color.surface.default` token — not a purple-tinted `surfaceColorAtElevation`
 * wash caused by a nonzero `tonalElevation` combined with `MentoraTheme.kt`'s deliberate
 * `surfaceTint = colorScheme.primary` mapping. Measured directly via a real captured pixel, not a
 * semantics-level color prop.
 */
@RunWith(AndroidJUnit4::class)
class CourseCardColorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderCard(darkTheme: Boolean) {
        composeTestRule.setContent {
            MentoraTheme(darkTheme = darkTheme) {
                CourseCard(
                    title = "Building Reliable REST APIs",
                    instructorName = "Ada Lovelace",
                    seed = "course-1",
                    categoryId = "Software Development",
                    categoryLabel = "Development",
                    thumbnailContentDescription = "Course thumbnail",
                    actionLabel = "Enroll",
                    onActionClick = {},
                    modifier = Modifier.testTag("card").size(width = 320.dp, height = 600.dp),
                )
            }
        }
    }

    /** Samples a pixel from the card body well below the thumbnail (so the artwork gradient/border
     *  can't contaminate the sample) and asserts it matches the exact spec'd hex, not a tinted
     *  approximation. */
    private fun assertCardBodyColor(expected: Color) {
        val pixelMap = composeTestRule.onNodeWithTag("card").captureToImage().toPixelMap()
        // Thumbnail is 320dp wide -> 180dp tall (16:9); sample well below it, away from the 1dp border.
        val sample = pixelMap[pixelMap.width / 2, pixelMap.height - 10]
        assertTrue(
            "expected card body color close to $expected, was $sample",
            colorsClose(expected, sample),
        )
    }

    @Test
    fun lightThemeCardBodyRendersPureSurfaceDefaultNotATintedWash() {
        renderCard(darkTheme = false)
        // color.surface.default (light) — pure white, per MentoraTokens.kt's MentoraColorsLight.surfaceDefault.
        assertCardBodyColor(Color(0xFFFFFFFF))
    }

    @Test
    fun darkThemeCardBodyRendersPureSurfaceDefaultNotSurfaceVariant() {
        renderCard(darkTheme = true)
        // color.surface.default (dark) — MentoraTokens.kt's MentoraColorsDark.surfaceDefault, distinct
        // from surfaceVariant (0xFF22232B), which is what a tonalElevation tint wash would shift toward.
        assertCardBodyColor(Color(0xFF191A20))
    }
}

/** Tolerant color comparison — captured pixels go through display color-space conversion, so exact
 *  channel equality isn't reliable; this kit's own [CourseArtworkTest] already establishes 0.02f as the
 *  right tolerance for this class of pixel-vs-expected-token comparison. [includeAlpha] additionally
 *  compares the alpha channel — needed for scrim-style translucent tokens ([AppDialogColorAndLayoutTest]),
 *  where the alpha value itself is exactly what's being verified, not just the RGB hue. */
internal fun colorsClose(expected: Color, actual: Color, tolerance: Float = 0.02f, includeAlpha: Boolean = false): Boolean {
    if (includeAlpha && kotlin.math.abs(expected.alpha - actual.alpha) > tolerance) return false
    return kotlin.math.abs(expected.red - actual.red) <= tolerance &&
        kotlin.math.abs(expected.green - actual.green) <= tolerance &&
        kotlin.math.abs(expected.blue - actual.blue) <= tolerance
}
