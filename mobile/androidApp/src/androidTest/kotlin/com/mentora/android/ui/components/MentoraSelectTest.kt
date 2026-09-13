package com.mentora.android.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T5 — `design-system/COMPONENTS.md` § Select / Dropdown. Covers: opening shows options; selecting
 * an option updates the displayed value and closes the menu; a disabled option is not selectable.
 */
@RunWith(AndroidJUnit4::class)
class MentoraSelectTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val options = listOf(
        MentoraSelectOption("dev", "Development"),
        MentoraSelectOption("design", "Design"),
    )

    @Test
    fun openingShowsOptionsAndSelectingUpdatesValueAndClosesMenu() {
        composeTestRule.setContent {
            MentoraTheme {
                var selected by remember { mutableStateOf<String?>(null) }
                MentoraSelect(
                    label = "Category",
                    options = options,
                    selected = selected,
                    onSelect = { selected = it },
                    placeholder = "Choose a category",
                    modifier = Modifier.testTag("category-select"),
                )
            }
        }

        composeTestRule.onNodeWithText("Development").assertDoesNotExist()

        composeTestRule.onNodeWithTag("category-select").performClick()

        composeTestRule.onNodeWithText("Development").assertExists()

        composeTestRule.onNodeWithText("Development").performClick()

        // Selected value now shown in the field; the menu is closed (only one "Development" node
        // left — the field's own displayed value, not also an option row).
        composeTestRule.onNodeWithText("Development").assertExists()
        composeTestRule.onNodeWithText("Design").assertDoesNotExist()
    }

    @Test
    fun disabledOptionIsNotSelectable() {
        val optionsWithDisabled = listOf(
            MentoraSelectOption("dev", "Development"),
            MentoraSelectOption("design", "Design", enabled = false),
        )
        composeTestRule.setContent {
            MentoraTheme {
                var selected by remember { mutableStateOf<String?>(null) }
                MentoraSelect(
                    label = "Category",
                    options = optionsWithDisabled,
                    selected = selected,
                    onSelect = { selected = it },
                    placeholder = "Choose a category",
                    modifier = Modifier.testTag("category-select"),
                )
            }
        }

        composeTestRule.onNodeWithTag("category-select").performClick()
        composeTestRule.onNodeWithText("Design").assertIsNotEnabled()

        // Real touch simulation rather than the semantics-driven performClick(), which a disabled
        // row's semantics may not even expose an OnClick action for.
        composeTestRule.onNodeWithText("Design").performTouchInput { click() }

        // Selection never changed — the field still shows the placeholder, not "Design".
        composeTestRule.onNodeWithText("Choose a category").assertExists()
    }
}
