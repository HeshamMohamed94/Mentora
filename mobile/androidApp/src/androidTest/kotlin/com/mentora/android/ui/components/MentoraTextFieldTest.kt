package com.mentora.android.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T5 — `design-system/COMPONENTS.md` § Inputs. Covers the D45-relevant floating-label behavior
 * (proven indirectly: `placeholder` only appears once focused, exactly M3's native "label acts as
 * placeholder at rest, floats on focus" contract — see `MentoraTextField.kt`'s kdoc) and the
 * `ACCESSIBILITY.md` § 7 "never color alone" error rule (icon + text render together).
 */
@RunWith(AndroidJUnit4::class)
class MentoraTextFieldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun placeholderIsHiddenAtRestAndAppearsOnceFocused() {
        composeTestRule.setContent {
            MentoraTheme {
                var value by remember { mutableStateOf("") }
                MentoraTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = "Email",
                    placeholder = "you@example.com",
                    modifier = Modifier.testTag("email-field"),
                )
            }
        }

        // At rest (unfocused, empty): the label alone occupies the field — never a statically
        // visible label AND placeholder stacked together (the exact D45 bug class).
        composeTestRule.onNodeWithText("you@example.com").assertDoesNotExist()

        composeTestRule.onNodeWithTag("email-field").performClick()

        composeTestRule.onNodeWithText("you@example.com").assertExists()
    }

    @Test
    fun errorStateRendersIconAndTextTogetherNotColorAlone() {
        var errorColor = androidx.compose.ui.graphics.Color.Unspecified
        composeTestRule.setContent {
            MentoraTheme {
                errorColor = MaterialTheme.colorScheme.error
                MentoraTextField(
                    value = "",
                    onValueChange = {},
                    label = "Email",
                    errorText = "Email is required",
                )
            }
        }

        composeTestRule.onNodeWithText("Email is required").assertExists()
        // M3's OutlinedTextField merges its descendants (label/input/supporting text/icons) into one
        // accessibility node, so the icon's own testTag only survives in the unmerged tree.
        composeTestRule.onNodeWithTag(MentoraFieldErrorIconTestTag, useUnmergedTree = true).assertExists()

        // F9: close the "never color alone" verification gap — icon presence + text presence were
        // already asserted above; this closes the third signal by confirming the icon actually
        // *renders* in `color.error.default`, not merely that it exists in the tree.
        val iconBitmap = composeTestRule
            .onNodeWithTag(MentoraFieldErrorIconTestTag, useUnmergedTree = true)
            .captureToImage()
            .toPixelMap()
        // The icon glyph doesn't fill every pixel of its bounding box (anti-aliased edges,
        // transparent background around the shape), so scan for at least one pixel that's a close
        // match to `color.error.default` rather than sampling a single fixed coordinate.
        var foundErrorColoredPixel = false
        for (x in 0 until iconBitmap.width) {
            for (y in 0 until iconBitmap.height) {
                val pixel = iconBitmap[x, y]
                if (
                    kotlin.math.abs(pixel.red - errorColor.red) < 0.05f &&
                    kotlin.math.abs(pixel.green - errorColor.green) < 0.05f &&
                    kotlin.math.abs(pixel.blue - errorColor.blue) < 0.05f
                ) {
                    foundErrorColoredPixel = true
                }
            }
        }
        assertTrue("expected the error icon to render at least one pixel in color.error.default", foundErrorColoredPixel)
    }

    @Test
    fun noErrorMeansNoErrorIconOrText() {
        composeTestRule.setContent {
            MentoraTheme {
                MentoraTextField(value = "", onValueChange = {}, label = "Email", helperText = "We'll never share this")
            }
        }

        composeTestRule.onNodeWithTag(MentoraFieldErrorIconTestTag, useUnmergedTree = true).assertDoesNotExist()
    }
}
