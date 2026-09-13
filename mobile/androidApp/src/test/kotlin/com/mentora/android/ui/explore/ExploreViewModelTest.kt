package com.mentora.android.ui.explore

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CourseFilters
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseSummary
import com.mentora.shared.domain.model.LearningPath
import com.mentora.shared.domain.model.PriceDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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

/**
 * T9 — [ExploreViewModel]'s debounce/pagination/filter logic, as a plain JVM unit test. Wires the
 * ViewModel's 3 constructor lambdas (see that class's own kdoc for why they're lambdas, not a
 * `MentoraSdk`) to counting fakes below rather than any real network — deterministic, no emulator/
 * backend needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ExploreViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private class FakeSearch {
        var callCount = 0
        val recordedFilters = mutableListOf<CourseFilters>()
        val recordedCursors = mutableListOf<String?>()
        var result: (CourseFilters, String?) -> ApiResult<CursorPage<CourseSummary>> = { _, _ ->
            ApiResult.Success(CursorPage(items = listOf(course("c1")), nextCursor = null))
        }

        suspend fun invoke(filters: CourseFilters, cursor: String?): ApiResult<CursorPage<CourseSummary>> {
            callCount++
            recordedFilters += filters
            recordedCursors += cursor
            return result(filters, cursor)
        }
    }

    private fun buildViewModel(
        listCategories: suspend () -> ApiResult<List<Category>> = { ApiResult.Success(emptyList()) },
        search: FakeSearch = FakeSearch(),
        listLearningPaths: suspend () -> ApiResult<List<LearningPath>> = { ApiResult.Success(emptyList()) },
    ): Pair<ExploreViewModel, FakeSearch> {
        val viewModel = ExploreViewModel(
            listCategories = listCategories,
            searchCourses = search::invoke,
            listLearningPaths = listLearningPaths,
        )
        return viewModel to search
    }

    @Test
    fun searchIsDebounced_rapidTypingOnlyCallsTheApiOnceAfterTheDebounceWindow() = runTest(testDispatcher) {
        val (viewModel, search) = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle() // Resolve init's own initial course load.
        assertEquals(1, search.callCount)

        // Rapid typing — each keystroke arrives well inside the debounce window, so it must cancel
        // and restart the pending search rather than firing one per keystroke.
        viewModel.onSearchQueryChange("k")
        testDispatcher.scheduler.advanceTimeBy(100)
        viewModel.onSearchQueryChange("ko")
        testDispatcher.scheduler.advanceTimeBy(100)
        viewModel.onSearchQueryChange("kot")
        testDispatcher.scheduler.advanceTimeBy(100)
        viewModel.onSearchQueryChange("kotlin")

        // Still inside the debounce window since the LAST keystroke — no extra call yet.
        testDispatcher.scheduler.advanceTimeBy(ExploreSearchDebounceMillis - 1)
        assertEquals(1, search.callCount)

        // Now past the debounce window with no further typing — exactly one more call fires.
        testDispatcher.scheduler.advanceTimeBy(2)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, search.callCount)
        assertEquals("kotlin", search.recordedFilters.last().query)
    }

    @Test
    fun categorySelection_searchesImmediately_noDebounce() = runTest(testDispatcher) {
        val (viewModel, search) = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, search.callCount)

        viewModel.onCategorySelected("cat-2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, search.callCount)
        assertEquals("cat-2", search.recordedFilters.last().category)
    }

    @Test
    fun loadMoreCourses_appendsToTheExistingPage_usingTheReturnedCursor() = runTest(testDispatcher) {
        val search = FakeSearch()
        var page = 0
        search.result = { _, cursor ->
            page++
            if (page == 1) {
                ApiResult.Success(CursorPage(items = listOf(course("c1")), nextCursor = "cursor-2"))
            } else {
                assertEquals("cursor-2", cursor)
                ApiResult.Success(CursorPage(items = listOf(course("c2")), nextCursor = null))
            }
        }
        val (viewModel, _) = buildViewModel(search = search)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onLoadMoreCourses()
        testDispatcher.scheduler.advanceUntilIdle()

        val loaded = viewModel.uiState.value.courses as CoursesUiState.Loaded
        assertEquals(listOf("c1", "c2"), loaded.items.map { it.id })
        assertEquals(null, loaded.nextCursor)
        assertFalse(loaded.isLoadingMore)
    }

    @Test
    fun loadMoreCourses_onFailure_keepsExistingItems_justStopsTheSpinner() = runTest(testDispatcher) {
        val search = FakeSearch()
        var page = 0
        search.result = { _, _ ->
            page++
            if (page == 1) {
                ApiResult.Success(CursorPage(items = listOf(course("c1")), nextCursor = "cursor-2"))
            } else {
                ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
            }
        }
        val (viewModel, _) = buildViewModel(search = search)
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onLoadMoreCourses()
        testDispatcher.scheduler.advanceUntilIdle()

        val loaded = viewModel.uiState.value.courses as CoursesUiState.Loaded
        assertEquals(listOf("c1"), loaded.items.map { it.id })
        assertFalse(loaded.isLoadingMore)
    }

    @Test
    fun emptySearchResult_becomesTheEmptyState() = runTest(testDispatcher) {
        val search = FakeSearch()
        search.result = { _, _ -> ApiResult.Success(CursorPage(items = emptyList(), nextCursor = null)) }
        val (viewModel, _) = buildViewModel(search = search)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.courses is CoursesUiState.Empty)
    }

    @Test
    fun courseSearchFailure_becomesTheErrorState_withTheApiErrorCode() = runTest(testDispatcher) {
        val search = FakeSearch()
        search.result = { _, _ -> ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) }
        val (viewModel, _) = buildViewModel(search = search)
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.courses as CoursesUiState.Error
        assertEquals(ApiErrorCode.InternalError, error.code)
    }

    @Test
    fun categoriesFailure_doesNotBlockTheCourseListFromLoading() = runTest(testDispatcher) {
        val (viewModel, _) = buildViewModel(
            listCategories = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.categories is CategoriesUiState.Error)
        assertTrue(viewModel.uiState.value.courses is CoursesUiState.Loaded)
    }

    @Test
    fun onClearFilters_resetsSearchAndCategory_andReloadsImmediately() = runTest(testDispatcher) {
        val (viewModel, search) = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCategorySelected("cat-2")
        viewModel.onSearchQueryChange("kotlin")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onClearFilters()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.searchQuery)
        assertEquals(null, viewModel.uiState.value.selectedCategoryId)
        val lastFilters = search.recordedFilters.last()
        assertEquals(null, lastFilters.category)
        assertEquals(null, lastFilters.query)
    }

    @Test
    fun learningPathsLoad_independentlyOfCourses_andBecomesEmptyWhenTheListIsEmpty() = runTest(testDispatcher) {
        val (viewModel, _) = buildViewModel(listLearningPaths = { ApiResult.Success(emptyList()) })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.learningPaths is LearningPathsUiState.Empty)
    }
}
