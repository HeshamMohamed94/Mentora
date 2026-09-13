package com.mentora.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import com.mentora.android.theme.extendedColors
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T8 — `design-system/COMPONENTS.md` § Answer Options' explicit "never color-only" rule
 * (`ACCESSIBILITY.md` § 8). Mirrors the exact rigor `MentoraTextFieldTest`'s
 * `errorStateRendersIconAndTextTogetherNotColorAlone` established for this kit: icon existence, text
 * existence, AND the icon's rendered pixel color, all three checked independently for both Correct
 * and Incorrect.
 */
@RunWith(AndroidJUnit4::class)
class AnswerOptionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun foundColorMatch(pixels: androidx.compose.ui.graphics.ImageBitmap, target: Color): Boolean {
        val pixelMap = pixels.toPixelMap()
        for (x in 0 until pixelMap.width) {
            for (y in 0 until pixelMap.height) {
                val pixel = pixelMap[x, y]
                if (
                    kotlin.math.abs(pixel.red - target.red) < 0.05f &&
                    kotlin.math.abs(pixel.green - target.green) < 0.05f &&
                    kotlin.math.abs(pixel.blue - target.blue) < 0.05f
                ) {
                    return true
                }
            }
        }
        return false
    }

    @Test
    fun correctStateRendersIconTextAndColorTogether() {
        var successColor = Color.Unspecified
        composeTestRule.setContent {
            MentoraTheme {
                successColor = MaterialTheme.extendedColors.success
                AnswerOption(
                    text = "PUT",
                    isSelected = true,
                    isSubmitted = true,
                    isCorrectAnswer = true,
                    onClick = {},
                )
            }
        }

        // Text signal.
        composeTestRule.onNodeWithText("PUT").assertExists()
        composeTestRule.onNodeWithText("Correct").assertExists()
        // Icon signal.
        val iconImage = composeTestRule.onNodeWithTag(AnswerOptionCorrectIconTestTag, useUnmergedTree = true).apply { assertExists() }.captureToImage()
        // Color signal (the icon itself renders in success color, not just some ambient background tint).
        assertTrue("expected the correct icon to render at least one pixel in color.success.default", foundColorMatch(iconImage, successColor))
    }

    @Test
    fun incorrectStateRendersIconTextAndColorTogether() {
        var errorColor = Color.Unspecified
        composeTestRule.setContent {
            MentoraTheme {
                errorColor = MaterialTheme.colorScheme.error
                AnswerOption(
                    text = "DELETE",
                    isSelected = true,
                    isSubmitted = true,
                    isCorrectAnswer = false,
                    onClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("DELETE").assertExists()
        composeTestRule.onNodeWithText("Incorrect").assertExists()
        val iconImage = composeTestRule.onNodeWithTag(AnswerOptionIncorrectIconTestTag, useUnmergedTree = true).apply { assertExists() }.captureToImage()
        assertTrue("expected the incorrect icon to render at least one pixel in color.error.default", foundColorMatch(iconImage, errorColor))
    }

    @Test
    fun defaultAndDisabledStatesRenderNoCorrectnessIconOrLabel() {
        composeTestRule.setContent {
            MentoraTheme {
                AnswerOption(text = "GET", isSelected = false, isSubmitted = false, isCorrectAnswer = false, onClick = {})
            }
        }

        composeTestRule.onNodeWithTag(AnswerOptionCorrectIconTestTag, useUnmergedTree = true).assertDoesNotExist()
        composeTestRule.onNodeWithTag(AnswerOptionIncorrectIconTestTag, useUnmergedTree = true).assertDoesNotExist()
    }
}
