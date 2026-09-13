package com.mentora.android.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** T5 — `design-system/COMPONENTS.md` § Tabs: tapping a tab updates the active state/indicator. */
@RunWith(AndroidJUnit4::class)
class MentoraTabsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun tappingATabUpdatesTheActiveState() {
        var selected by mutableStateOf("home")
        composeTestRule.setContent {
            MentoraTheme {
                MentoraTabs(
                    options = listOf(
                        MentoraTabOption("home", "Home"),
                        MentoraTabOption("explore", "Explore"),
                    ),
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Home").assertIsSelected()
        composeTestRule.onNodeWithText("Explore").assertIsNotSelected()

        composeTestRule.onNodeWithText("Explore").performClick()

        composeTestRule.onNodeWithText("Explore").assertIsSelected()
        composeTestRule.onNodeWithText("Home").assertIsNotSelected()
    }
}
