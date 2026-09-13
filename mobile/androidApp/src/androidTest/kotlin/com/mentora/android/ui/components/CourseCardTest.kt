package com.mentora.android.ui.components

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T8 — `design-system/COMPONENTS.md` § CourseCard line 295: "Progress bar (only if enrolled)."
 * Verifies [CourseCard]'s `isEnrolled` flag is the actual gate for whether a progress bar node
 * exists, matching the rigor `MentoraTextFieldTest`'s error-state coverage already established for
 * this kit.
 */
@RunWith(AndroidJUnit4::class)
class CourseCardTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setCourseCard(isEnrolled: Boolean) {
        composeTestRule.setContent {
            MentoraTheme {
                CourseCard(
                    title = "Building Reliable REST APIs",
                    instructorName = "Ada Lovelace",
                    seed = "course-1",
                    categoryId = "Software Development",
                    categoryLabel = "Development",
                    thumbnailContentDescription = "Course thumbnail",
                    actionLabel = if (isEnrolled) "Continue" else "Enroll",
                    onActionClick = {},
                    isEnrolled = isEnrolled,
                    progress = 0.42f,
                )
            }
        }
    }

    @Test
    fun notEnrolledRendersNoProgressBar() {
        setCourseCard(isEnrolled = false)

        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(0)
        composeTestRule.onNodeWithText("Enroll").assertExists()
    }

    @Test
    fun enrolledRendersExactlyOneProgressBarAndResumeAction() {
        setCourseCard(isEnrolled = true)

        composeTestRule
            .onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))
            .assertCountEquals(1)
        composeTestRule.onNodeWithText("42% complete").assertExists()
        composeTestRule.onNodeWithText("Continue").assertExists()
    }
}
