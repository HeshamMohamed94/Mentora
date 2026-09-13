package com.mentora.android.ui.coursedetails

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.mentora.android.theme.MentoraTheme
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.Lesson
import com.mentora.shared.domain.model.PriceDisplay
import com.mentora.shared.domain.model.Section
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * T10 — real rendering/interaction coverage for [CourseDetailsScreenContent] (the stateless
 * presentation half of [CourseDetailsScreen] — no [com.mentora.shared.MentoraSdk]/
 * [CourseDetailsViewModel]/network needed here, every state is hand-built, same split rationale as
 * `ExploreScreenContentTest`).
 */
class CourseDetailsScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun course(sections: List<Section> = emptyList()) = Course(
        id = "course-1",
        title = "Kotlin for Beginners",
        description = "Learn Kotlin from scratch.",
        categoryId = "cat-1",
        level = CourseLevel.Beginner,
        contentLanguage = ContentLanguage.English,
        priceDisplay = PriceDisplay(amount = 899, currency = "EGP"),
        thumbnailMediaId = null,
        status = CourseStatus.Published,
        ratingSeed = 4.5,
        instructorId = "instructor-1",
        instructorName = "Ada Lovelace",
        sections = sections,
        translations = emptyMap(),
    )

    private fun successState(
        cta: CourseDetailsCtaState,
        isEnrolled: Boolean = false,
        sections: List<Section> = emptyList(),
    ) = CourseDetailsUiState(
        course = CourseLoadState.Success(
            course = course(sections),
            thumbnailUrl = null,
            isEnrolled = isEnrolled,
            cta = cta,
        ),
    )

    private fun setContent(
        uiState: CourseDetailsUiState,
        onRetry: () -> Unit = {},
        onEnrollRequiringAuth: () -> Unit = {},
        onContinueLearning: () -> Unit = {},
        onOpenLesson: (String) -> Unit = {},
    ) {
        composeTestRule.setContent {
            MentoraTheme {
                CourseDetailsScreenContent(
                    uiState = uiState,
                    onRetry = onRetry,
                    onEnrollRequiringAuth = onEnrollRequiringAuth,
                    onContinueLearning = onContinueLearning,
                    onOpenLesson = onOpenLesson,
                )
            }
        }
    }

    @Test
    fun loadingState_rendersTheSkeleton() {
        setContent(uiState = CourseDetailsUiState(course = CourseLoadState.Loading))

        composeTestRule.onNodeWithTag(CourseDetailsLoadingTestTag).assertExists()
    }

    @Test
    fun errorState_rendersTheMappedApiErrorMessage_andRetryInvokesTheCallback() {
        var retryCount = 0
        setContent(
            uiState = CourseDetailsUiState(course = CourseLoadState.Error(ApiErrorCode.CourseNotFound)),
            onRetry = { retryCount++ },
        )

        composeTestRule.onNodeWithText("This course could not be found.").assertExists()
        composeTestRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retryCount)
    }

    // ---- The 3 CTA states ----

    @Test
    fun guestState_ctaReadsLoginToEnroll_showsPrice_andTappingInvokesOnEnrollRequiringAuth() {
        var enrollTapCount = 0
        setContent(
            uiState = successState(cta = CourseDetailsCtaState.LoginToEnroll, isEnrolled = false),
            onEnrollRequiringAuth = { enrollTapCount++ },
        )

        composeTestRule.onNodeWithText("Login to Enroll").assertExists()
        composeTestRule.onNodeWithText("EGP 899").assertExists()
        composeTestRule.onNodeWithText("Demo price").assertExists()
        composeTestRule.onNodeWithTag(CourseDetailsCtaButtonTestTag).performClick()
        assertEquals(1, enrollTapCount)
    }

    @Test
    fun authenticatedNotEnrolledState_ctaReadsEnroll_showsPrice_andTappingInvokesOnEnrollRequiringAuth() {
        var enrollTapCount = 0
        setContent(
            uiState = successState(cta = CourseDetailsCtaState.Enroll, isEnrolled = false),
            onEnrollRequiringAuth = { enrollTapCount++ },
        )

        composeTestRule.onNodeWithText("Enroll").assertExists()
        composeTestRule.onNodeWithText("EGP 899").assertExists()
        composeTestRule.onNodeWithTag(CourseDetailsCtaButtonTestTag).performClick()
        assertEquals(1, enrollTapCount)
    }

    @Test
    fun enrolledState_ctaReadsContinueLearning_showsEnrolledMessage_neverThePrice_andTappingInvokesOnContinueLearning() {
        var continueTapCount = 0
        setContent(
            uiState = successState(cta = CourseDetailsCtaState.ContinueLearning, isEnrolled = true),
            onContinueLearning = { continueTapCount++ },
        )

        composeTestRule.onNodeWithText("Continue Learning").assertExists()
        composeTestRule.onNodeWithText("You're enrolled in this course").assertExists()
        composeTestRule.onNodeWithText("EGP 899").assertDoesNotExist()
        composeTestRule.onNodeWithText("Demo price").assertDoesNotExist()
        composeTestRule.onNodeWithTag(CourseDetailsCtaButtonTestTag).performClick()
        assertEquals(1, continueTapCount)
    }

    // ---- Curriculum locked/unlocked rows ----

    private fun sectionsFixture() = listOf(
        Section(
            sectionId = "s1",
            title = "Getting Started",
            order = 0,
            lessons = listOf(
                Lesson(lessonId = "l1", title = "Installing Kotlin", description = "", order = 0, videoMediaId = "m1", resources = emptyList()),
                Lesson(lessonId = "l2", title = "Your First Program", description = "", order = 1, videoMediaId = "m2", resources = emptyList()),
            ),
        ),
    )

    @Test
    fun curriculum_whenNotEnrolled_lessonRowsHaveNoPlayAffordance_andAreNotClickable() {
        var openedLessonId: String? = null
        setContent(
            uiState = successState(cta = CourseDetailsCtaState.Enroll, isEnrolled = false, sections = sectionsFixture()),
            onOpenLesson = { openedLessonId = it },
        )

        composeTestRule.onNodeWithText("Installing Kotlin").assertExists()
        composeTestRule.onAllNodesWithTag(courseDetailsLessonPlayIconTestTag("l1")).assertCountEquals(0)

        composeTestRule.onNodeWithTag(courseDetailsLessonRowTestTag("l1")).performClick()
        assertEquals(null, openedLessonId)
    }

    @Test
    fun curriculum_whenEnrolled_lessonRowsShowThePlayAffordance_andTappingInvokesOnOpenLesson() {
        var openedLessonId: String? = null
        setContent(
            uiState = successState(cta = CourseDetailsCtaState.ContinueLearning, isEnrolled = true, sections = sectionsFixture()),
            onOpenLesson = { openedLessonId = it },
        )

        // `useUnmergedTree = true`: the row's own `Modifier.clickable` merges its descendants' semantics
        // into one node for accessibility (the whole row reads as a single unit to a screen reader),
        // which would otherwise hide this child icon's own testTag from the default merged-tree query.
        composeTestRule.onNodeWithTag(courseDetailsLessonPlayIconTestTag("l1"), useUnmergedTree = true).assertExists()
        composeTestRule.onNodeWithTag(courseDetailsLessonRowTestTag("l2")).performClick()
        assertEquals("l2", openedLessonId)
    }

    @Test
    fun successState_rendersDescriptionAndInstructorSection() {
        setContent(uiState = successState(cta = CourseDetailsCtaState.Enroll))

        composeTestRule.onNodeWithText("Learn Kotlin from scratch.").assertExists()
        composeTestRule.onNodeWithTag(CourseDetailsInstructorSectionTestTag).assertExists()
        // The instructor's name legitimately renders TWICE by design — once under the title (rank-1
        // item 3, "title + instructor name") and once in the expanded instructor detail block (item 9)
        // — never a bug to dedupe.
        composeTestRule.onAllNodesWithText("Ada Lovelace").assertCountEquals(2)
    }
}
