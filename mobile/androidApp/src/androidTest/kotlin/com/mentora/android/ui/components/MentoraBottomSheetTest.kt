package com.mentora.android.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** T8 — `design-system/COMPONENTS.md` § BottomSheet. Open/dismiss round-trip, same caller-owned-flag
 *  pattern as [AppDialogTest]. */
@RunWith(AndroidJUnit4::class)
class MentoraBottomSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @OptIn(ExperimentalMaterial3Api::class)
    @Test
    fun opensShowsContentAndDismissesViaCallerAction() {
        composeTestRule.setContent {
            MentoraTheme {
                var showSheet by remember { mutableStateOf(true) }
                if (showSheet) {
                    MentoraBottomSheet(onDismissRequest = { showSheet = false }) {
                        Text("Sheet content", modifier = Modifier.testTag("sheet-content"))
                        MentoraTextButton(text = "Close", onClick = { showSheet = false })
                    }
                }
            }
        }

        composeTestRule.onNodeWithTag("sheet-content").assertExists()

        composeTestRule.onNodeWithText("Close").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("sheet-content").assertDoesNotExist()
    }
}
