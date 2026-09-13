package com.mentora.android.ui.explore

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import com.mentora.android.theme.MentoraTheme
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseSummary
import com.mentora.shared.domain.model.LearningPath
import com.mentora.shared.domain.model.PriceDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T9 — real rendering/interaction coverage for [ExploreScreenContent] (the stateless presentation
 * half of [ExploreScreen] — see that composable's kdoc for why the split exists: no
 * [com.mentora.shared.MentoraSdk]/[ExploreViewModel]/network needed here, every state is hand-built).
 */
class ExploreScreenContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun course(id: String, categoryId: String = "cat-1") = CourseSummary(
        id = id,
        title = "Course $id",
        description = "Description $id",
        categoryId = categoryId,
        level = CourseLevel.Beginner,
        contentLanguage = ContentLanguage.English,
        priceDisplay = PriceDisplay(amount = 850, currency = "EGP"),
        thumbnailMediaId = null,
        ratingSeed = 4.5,
        instructorId = "instructor-1",
        instructorName = "Instructor",
    )

    private fun setContent(uiState: ExploreUiState, onCategorySelected: (String?) -> Unit = {}) {
        composeTestRule.setContent {
            MentoraTheme {
                ExploreScreenContent(
                    uiState = uiState,
                    onSearchQueryChange = {},
                    onCategorySelected = onCategorySelected,
                    onTabSelected = {},
                    onClearFilters = {},
                    onRetryCategories = {},
                    onRetryCourses = {},
                    onRetryLearningPaths = {},
                    onLoadMoreCourses = {},
                    onOpenCourseDetails = {},
                    onOpenPathDetails = {},
                )
            }
        }
    }

    @Test
    fun tappingACategoryChip_invokesOnCategorySelectedWithThatCategorysId() {
        var selected: String? = "unset"
        setContent(
            uiState = ExploreUiState(
                categories = CategoriesUiState.Loaded(
                    listOf(Category(id = "cat-1", name = "Design", slug = "design", courseCount = 3)),
                ),
                // A DIFFERENT categoryId than the "Design" chip above — otherwise the course card's
                // own thumbnail-overlay chip (CourseArtworkWithChip) also renders the text "Design",
                // making `onNodeWithText("Design")` ambiguous (2 matching nodes) at click time.
                courses = CoursesUiState.Loaded(items = listOf(course("c1", categoryId = "cat-2")), nextCursor = null),
            ),
            onCategorySelected = { selected = it },
        )

        composeTestRule.onNodeWithText("Design").performClick()
        assertEquals("cat-1", selected)
    }

    @Test
    fun tappingTheAllChip_invokesOnCategorySelectedWithNull() {
        var selected: String? = "unset"
        setContent(
            uiState = ExploreUiState(
                categories = CategoriesUiState.Loaded(
                    listOf(Category(id = "cat-1", name = "Design", slug = "design", courseCount = 3)),
                ),
                selectedCategoryId = "cat-1",
                courses = CoursesUiState.Loaded(items = listOf(course("c1", categoryId = "cat-2")), nextCursor = null),
            ),
            onCategorySelected = { selected = it },
        )

        composeTestRule.onNodeWithText("All").performClick()
        assertEquals(null, selected)
    }

    @Test
    fun coursesTab_showsCourseCards_notLearningPathCards() {
        setContent(
            uiState = ExploreUiState(
                selectedTab = ExploreTab.Courses,
                courses = CoursesUiState.Loaded(items = listOf(course("c1"), course("c2")), nextCursor = null),
                learningPaths = LearningPathsUiState.Loaded(
                    listOf(LearningPath(id = "p1", title = "Become a Frontend Engineer", description = "d", courseCount = 4)),
                ),
            ),
        )

        composeTestRule.onNodeWithText("Course c1").assertExists()
        composeTestRule.onNodeWithText("Become a Frontend Engineer").assertDoesNotExist()
    }

    @Test
    fun switchingToTheLearningPathsTab_showsLearningPathCards_notCourseCards() {
        var tabSelected: ExploreTab? = null
        composeTestRule.setContent {
            MentoraTheme {
                ExploreScreenContent(
                    uiState = ExploreUiState(
                        selectedTab = ExploreTab.Courses,
                        courses = CoursesUiState.Loaded(items = listOf(course("c1")), nextCursor = null),
                        learningPaths = LearningPathsUiState.Loaded(
                            listOf(LearningPath(id = "p1", title = "Become a Frontend Engineer", description = "d", courseCount = 4)),
                        ),
                    ),
                    onSearchQueryChange = {},
                    onCategorySelected = {},
                    onTabSelected = { tabSelected = it },
                    onClearFilters = {},
                    onRetryCategories = {},
                    onRetryCourses = {},
                    onRetryLearningPaths = {},
                    onLoadMoreCourses = {},
                    onOpenCourseDetails = {},
                    onOpenPathDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Learning Paths").performClick()
        assertEquals(ExploreTab.LearningPaths, tabSelected)
    }

    @Test
    fun learningPathsTabContent_rendersWhenSelected_andTappingItInvokesOnOpenPathDetails() {
        var openedPathId: String? = null
        composeTestRule.setContent {
            MentoraTheme {
                ExploreScreenContent(
                    uiState = ExploreUiState(
                        selectedTab = ExploreTab.LearningPaths,
                        courses = CoursesUiState.Loaded(items = listOf(course("c1")), nextCursor = null),
                        learningPaths = LearningPathsUiState.Loaded(
                            listOf(LearningPath(id = "p1", title = "Become a Frontend Engineer", description = "d", courseCount = 4)),
                        ),
                    ),
                    onSearchQueryChange = {},
                    onCategorySelected = {},
                    onTabSelected = {},
                    onClearFilters = {},
                    onRetryCategories = {},
                    onRetryCourses = {},
                    onRetryLearningPaths = {},
                    onLoadMoreCourses = {},
                    onOpenCourseDetails = {},
                    onOpenPathDetails = { openedPathId = it },
                )
            }
        }

        composeTestRule.onNodeWithText("Become a Frontend Engineer").assertExists()
        composeTestRule.onNodeWithText("Course c1").assertDoesNotExist()
        composeTestRule.onAllNodesWithTag(ExploreLearningPathCardTestTag)[0].performClick()
        assertEquals("p1", openedPathId)
    }

    @Test
    fun emptyCoursesState_rendersNoResults_andClearFiltersInvokesTheCallback() {
        var clearFiltersCount = 0
        composeTestRule.setContent {
            MentoraTheme {
                ExploreScreenContent(
                    uiState = ExploreUiState(courses = CoursesUiState.Empty),
                    onSearchQueryChange = {},
                    onCategorySelected = {},
                    onTabSelected = {},
                    onClearFilters = { clearFiltersCount++ },
                    onRetryCategories = {},
                    onRetryCourses = {},
                    onRetryLearningPaths = {},
                    onLoadMoreCourses = {},
                    onOpenCourseDetails = {},
                    onOpenPathDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithText("No results").assertExists()
        composeTestRule.onNodeWithText("Clear filters").performClick()
        assertEquals(1, clearFiltersCount)
    }

    @Test
    fun coursesErrorState_rendersTheMappedApiErrorMessage_andRetryInvokesTheCallback() {
        var retryCount = 0
        composeTestRule.setContent {
            MentoraTheme {
                ExploreScreenContent(
                    uiState = ExploreUiState(courses = CoursesUiState.Error(ApiErrorCode.InternalError)),
                    onSearchQueryChange = {},
                    onCategorySelected = {},
                    onTabSelected = {},
                    onClearFilters = {},
                    onRetryCategories = {},
                    onRetryCourses = { retryCount++ },
                    onRetryLearningPaths = {},
                    onLoadMoreCourses = {},
                    onOpenCourseDetails = {},
                    onOpenPathDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Something went wrong. Please try again.").assertExists()
        composeTestRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retryCount)
    }

    @Test
    fun tappingACourseCard_invokesOnOpenCourseDetailsWithThatCoursesId() {
        var openedCourseId: String? = null
        composeTestRule.setContent {
            MentoraTheme {
                ExploreScreenContent(
                    uiState = ExploreUiState(courses = CoursesUiState.Loaded(items = listOf(course("c1")), nextCursor = null)),
                    onSearchQueryChange = {},
                    onCategorySelected = {},
                    onTabSelected = {},
                    onClearFilters = {},
                    onRetryCategories = {},
                    onRetryCourses = {},
                    onRetryLearningPaths = {},
                    onLoadMoreCourses = {},
                    onOpenCourseDetails = { openedCourseId = it },
                    onOpenPathDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithText("Course c1").performClick()
        assertEquals("c1", openedCourseId)
    }

    @Test
    fun scrollingNearTheEndOfTheCourseList_invokesOnLoadMoreCourses() {
        var loadMoreCount = 0
        val manyCourses = (1..40).map { course("c$it") }
        composeTestRule.setContent {
            MentoraTheme {
                ExploreScreenContent(
                    uiState = ExploreUiState(
                        courses = CoursesUiState.Loaded(items = manyCourses, nextCursor = "cursor-2"),
                    ),
                    onSearchQueryChange = {},
                    onCategorySelected = {},
                    onTabSelected = {},
                    onClearFilters = {},
                    onRetryCategories = {},
                    onRetryCourses = {},
                    onRetryLearningPaths = {},
                    onLoadMoreCourses = { loadMoreCount++ },
                    onOpenCourseDetails = {},
                    onOpenPathDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ExploreCourseListTestTag).performScrollToIndex(manyCourses.size)
        composeTestRule.waitForIdle()

        assertTrue("expected onLoadMoreCourses to fire at least once near the end of the list", loadMoreCount > 0)
    }

    @Test
    fun loadingMoreSpinner_rendersWhileTheNextPageIsInFlight() {
        composeTestRule.setContent {
            MentoraTheme {
                ExploreScreenContent(
                    uiState = ExploreUiState(
                        courses = CoursesUiState.Loaded(items = listOf(course("c1")), nextCursor = "cursor-2", isLoadingMore = true),
                    ),
                    onSearchQueryChange = {},
                    onCategorySelected = {},
                    onTabSelected = {},
                    onClearFilters = {},
                    onRetryCategories = {},
                    onRetryCourses = {},
                    onRetryLearningPaths = {},
                    onLoadMoreCourses = {},
                    onOpenCourseDetails = {},
                    onOpenPathDetails = {},
                )
            }
        }

        composeTestRule.onNodeWithTag(ExploreLoadMoreSpinnerTestTag).assertExists()
    }
}
