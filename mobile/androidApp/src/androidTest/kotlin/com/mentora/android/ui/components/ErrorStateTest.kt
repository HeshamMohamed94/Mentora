package com.mentora.android.ui.components

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
 * T8 — `design-system/COMPONENTS.md` § ErrorState. Confirms the retry action actually fires its
 * callback (not just that the button renders) and that the component's copy is caller-supplied
 * friendly text, never a raw backend string (see `ErrorState.kt`'s own kdoc — this test uses plain
 * friendly copy, not a code/status-string, to keep the assertion focused on the callback wiring).
 */
@RunWith(AndroidJUnit4::class)
class ErrorStateTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun retryButtonFiresCallback() {
        var retryCount = 0
        composeTestRule.setContent {
            MentoraTheme {
                ErrorState(
                    title = "Something went wrong",
                    description = "We couldn't load this page. Check your connection and try again.",
                    onRetryClick = { retryCount++ },
                )
            }
        }

        composeTestRule.onNodeWithText("Something went wrong").assertExists()
        composeTestRule.onNodeWithTag(ErrorStateRetryButtonTestTag).performClick()

        assertTrue("expected onRetryClick to have fired exactly once", retryCount == 1)
    }

    @Test
    fun retryLabelDefaultsToTryAgain() {
        composeTestRule.setContent {
            MentoraTheme {
                ErrorState(title = "Something went wrong", description = "Please try again.", onRetryClick = {})
            }
        }

        composeTestRule.onNodeWithText("Try again").assertExists()
    }
}
