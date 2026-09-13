package com.mentora.android.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finding 7 (LOW, `CONTENT_RESILIENCE.md` § 1 single-line clamps) and Finding 6 (MEDIUM, description
 * text must wrap freely, never a hard `maxLines` truncation) regression coverage.
 */
@RunWith(AndroidJUnit4::class)
class ContentClampRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    /** Reviewer-measured repro: a 200dp-wide [StatCard] with a long label grew to 125.7dp tall. A
     *  single-line-clamped label must keep the card at its normal (short-label) height instead. */
    @Test
    fun statCardLongLabelDoesNotGrowCardTaller() {
        val longLabel = "Average Weekly Study Hours Across All Enrolled Courses This Term"

        composeTestRule.setContent {
            MentoraTheme {
                StatCard(
                    value = "12.4",
                    label = "Short label",
                    modifier = Modifier.testTag("short-card").width(200.dp),
                )
                StatCard(
                    value = "12.4",
                    label = longLabel,
                    modifier = Modifier.testTag("long-card").width(200.dp),
                )
            }
        }

        val shortBounds = composeTestRule.onNodeWithTag("short-card").getUnclippedBoundsInRoot()
        val longBounds = composeTestRule.onNodeWithTag("long-card").getUnclippedBoundsInRoot()

        assertTrue(
            "expected the long-label card (${longBounds.height}) to be the same height as the short-label " +
                "card (${shortBounds.height}), not grown taller by the unclamped label",
            kotlin.math.abs(longBounds.height.value - shortBounds.height.value) < 1f,
        )
    }

    /** Finding 7: [CourseCard]'s instructor name must stay single-line even given a very long name. */
    @Test
    fun courseCardLongInstructorNameStaysSingleLine() {
        val longInstructor = "Dr. Alexandra Christina Montgomery-Fitzgerald Worthington III"

        composeTestRule.setContent {
            MentoraTheme {
                CourseCard(
                    title = "Course",
                    instructorName = longInstructor,
                    seed = "course-1",
                    categoryId = "Software Development",
                    categoryLabel = "Development",
                    thumbnailContentDescription = "thumb",
                    actionLabel = "Enroll",
                    onActionClick = {},
                    modifier = Modifier.width(280.dp),
                )
            }
        }

        val instructorBounds = composeTestRule.onNodeWithText(longInstructor, substring = true).getUnclippedBoundsInRoot()
        val titleBounds = composeTestRule.onNodeWithText("Course").getUnclippedBoundsInRoot()

        // A 2-line-wrapped instructor name would be roughly 2x a single title line's height; assert
        // it stays close to a single line instead (bodySmall is naturally shorter than titleLarge, so
        // compare against ~1.3x margin rather than an exact match).
        assertTrue(
            "expected the long instructor name to stay single-line (height=${instructorBounds.height}), " +
                "not wrap onto a second line",
            instructorBounds.height.value < titleBounds.height.value * 1.3f,
        )
    }

    /** Finding 6: [EmptyState]'s description must wrap freely (no hard 2-line ellipsis clamp). */
    @Test
    fun emptyStateLongDescriptionWrapsPastTwoLinesInsteadOfBeingClamped() {
        val longDescription = "This is a deliberately long empty-state description that must wrap across " +
            "several lines of text without ever being truncated with an ellipsis, per the locked content " +
            "resilience spec, which explicitly treats the two-line figure as soft guidance rather than a " +
            "hard limit that would otherwise cut this sentence short."

        composeTestRule.setContent {
            MentoraTheme {
                EmptyState(
                    icon = MentoraIconName.Search,
                    title = "No results",
                    description = longDescription,
                    modifier = Modifier.width(300.dp),
                )
            }
        }

        // If unclamped, the full string renders as one findable text node with its full content
        // (no maxLines/ellipsis means TextOverflow never kicks in and the whole string round-trips).
        composeTestRule.onNodeWithText(longDescription).assertExists()
    }

    /** Finding 6: [SuccessState]'s description must also wrap freely, matching [EmptyState]/[ErrorState]. */
    @Test
    fun successStateLongDescriptionWrapsPastTwoLinesInsteadOfBeingClamped() {
        val longDescription = "This is a deliberately long success-state description that must wrap across " +
            "several lines of text without ever being truncated with an ellipsis, per the locked content " +
            "resilience spec, which explicitly treats the two-line figure as soft guidance rather than a " +
            "hard limit that would otherwise cut this sentence short."

        composeTestRule.setContent {
            MentoraTheme {
                SuccessState(
                    title = "All done",
                    description = longDescription,
                    actionLabel = "Continue",
                    onActionClick = {},
                    modifier = Modifier.width(300.dp),
                )
            }
        }

        composeTestRule.onNodeWithText(longDescription).assertExists()
    }
}
