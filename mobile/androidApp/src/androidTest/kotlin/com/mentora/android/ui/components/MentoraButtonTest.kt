package com.mentora.android.ui.components

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T5 — `design-system/COMPONENTS.md` § Buttons. Covers: click fires the callback; a disabled button
 * doesn't fire it; a loading button doesn't fire it either and shows the spinner (not the label).
 */
@RunWith(AndroidJUnit4::class)
class MentoraButtonTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun clickingAnEnabledButtonFiresTheCallback() {
        var clickCount = 0
        composeTestRule.setContent {
            MentoraTheme {
                PrimaryButton(text = "Continue", onClick = { clickCount++ })
            }
        }

        composeTestRule.onNodeWithText("Continue").performClick()

        assertEquals(1, clickCount)
    }

    @Test
    fun disabledButtonNeverFiresTheCallback() {
        var clickCount = 0
        composeTestRule.setContent {
            MentoraTheme {
                PrimaryButton(text = "Continue", onClick = { clickCount++ }, enabled = false)
            }
        }

        composeTestRule.onNodeWithText("Continue").assertIsNotEnabled()
        // A real touch-gesture simulation (not the semantics-driven performClick(), which requires
        // an OnClick action a disabled node may not even expose) — proves an actual tap does nothing.
        composeTestRule.onNodeWithText("Continue").performTouchInput { click() }

        assertEquals(0, clickCount)
    }

    @Test
    fun loadingButtonShowsSpinnerAndNeverFiresTheCallback() {
        var clickCount = 0
        composeTestRule.setContent {
            MentoraTheme {
                PrimaryButton(text = "Continue", onClick = { clickCount++ }, loading = true)
            }
        }

        composeTestRule.onNodeWithTag(MentoraButtonSpinnerTestTag).assertExists()
        composeTestRule.onNodeWithText("Continue").performTouchInput { click() }

        assertEquals(0, clickCount)
    }
}
