package com.mentora.android.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finding 2 (reviewer-measured HIGH defect) regression coverage: [MentoraBottomSheet]'s background
 * must render the plain `color.surface.elevated` token, not a purple-tinted `surfaceColorAtElevation`
 * wash caused by a nonzero `tonalElevation` on a `Surface` whose `surfaceTint` is deliberately mapped
 * to `colorScheme.primary` ([MentoraTheme.kt]). Measured via a real captured pixel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(AndroidJUnit4::class)
class MentoraBottomSheetColorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun renderSheet(darkTheme: Boolean) {
        composeTestRule.setContent {
            MentoraTheme(darkTheme = darkTheme) {
                MentoraBottomSheet(onDismissRequest = {}) {
                    Text("Sheet content")
                }
            }
        }
    }

    @Test
    fun lightThemeSheetRendersPureSurfaceElevatedNotATintedWash() {
        renderSheet(darkTheme = false)
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(2000)
        composeTestRule.waitForIdle()
        val surface = composeTestRule.onNodeWithTag(MentoraBottomSheetSurfaceTestTag).captureToImage().toPixelMap()
        // Sampled inside the tagged content Column's own space5 padding (nothing this Column itself
        // draws there, so the pixel reflects the sheet Surface's own containerColor fill showing
        // through) — but offset well clear of the top-left rounded xlarge corner + border stroke
        // (measured: sampling right at the corner picks up anti-aliased corner/border blending, not
        // the flat fill).
        val sample = surface[100, 60]
        // color.surface.elevated (light) — pure white, MentoraTokens.kt's MentoraColorsLight.surfaceElevated.
        assertTrue("expected pure white, got $sample", colorsClose(Color(0xFFFFFFFF), sample))
    }

    @Test
    fun darkThemeSheetRendersExactSurfaceElevatedToken() {
        renderSheet(darkTheme = true)
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.advanceTimeBy(2000)
        composeTestRule.waitForIdle()
        val surface = composeTestRule.onNodeWithTag(MentoraBottomSheetSurfaceTestTag).captureToImage().toPixelMap()
        val sample = surface[100, 60]
        // color.surface.elevated (dark) — MentoraTokens.kt's MentoraColorsDark.surfaceElevated (0xFF24252E).
        assertTrue("expected #24252E, got $sample", colorsClose(Color(0xFF24252E), sample))
    }
}
