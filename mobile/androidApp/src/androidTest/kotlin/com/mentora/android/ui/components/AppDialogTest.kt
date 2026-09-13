package com.mentora.android.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** T8 — `design-system/COMPONENTS.md` § AppDialog. Open/dismiss round-trip via a real caller-owned
 *  `showDialog` flag, the same pattern any real screen would use. */
@RunWith(AndroidJUnit4::class)
class AppDialogTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun opensShowsContentAndConfirmDismissesIt() {
        composeTestRule.setContent {
            MentoraTheme {
                var showDialog by remember { mutableStateOf(true) }
                if (showDialog) {
                    AppDialog(
                        title = "Discard changes?",
                        body = "You have unsaved changes.",
                        confirmLabel = "Discard",
                        onConfirm = { showDialog = false },
                        onDismissRequest = { showDialog = false },
                        dismissLabel = "Cancel",
                    )
                }
            }
        }

        composeTestRule.onNodeWithText("Discard changes?").assertExists()
        composeTestRule.onNodeWithText("You have unsaved changes.").assertExists()

        composeTestRule.onNodeWithTag(AppDialogConfirmButtonTestTag).performClick()

        composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
    }

    @Test
    fun dismissActionDismissesWithoutConfirming() {
        var confirmed = false
        composeTestRule.setContent {
            MentoraTheme {
                var showDialog by remember { mutableStateOf(true) }
                if (showDialog) {
                    AppDialog(
                        title = "Discard changes?",
                        body = "You have unsaved changes.",
                        confirmLabel = "Discard",
                        onConfirm = { confirmed = true; showDialog = false },
                        onDismissRequest = { showDialog = false },
                        dismissLabel = "Cancel",
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(AppDialogDismissButtonTestTag).performClick()

        composeTestRule.onNodeWithText("Discard changes?").assertDoesNotExist()
        org.junit.Assert.assertFalse("expected onConfirm NOT to have fired when Cancel was tapped", confirmed)
    }
}
