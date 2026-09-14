package com.mentora.android.ui.checkout

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.PriceDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

private fun course(id: String) = Course(
    id = id,
    title = "Building Reliable REST APIs",
    description = "Description",
    categoryId = "cat-1",
    level = CourseLevel.Beginner,
    contentLanguage = ContentLanguage.English,
    priceDisplay = PriceDisplay(amount = 899, currency = "EGP"),
    thumbnailMediaId = null,
    status = CourseStatus.Published,
    ratingSeed = 4.5,
    instructorId = "instructor-1",
    instructorName = "Instructor",
    sections = emptyList(),
    translations = emptyMap(),
)

/**
 * T11 — [PurchaseSuccessViewModel]'s course-title fetch, as a plain JVM unit test. See that class's
 * own kdoc for why a failed fetch here fails safe to `null` (the generic fallback description)
 * rather than a whole-screen error state — the enrollment itself already succeeded before this
 * screen exists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PurchaseSuccessViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun buildViewModel(
        courseId: String = "course-1",
        getCourseDetails: suspend (String) -> ApiResult<Course> = { ApiResult.Success(course(it)) },
    ) = PurchaseSuccessViewModel(courseId = courseId, getCourseDetails = getCourseDetails)

    @Test
    fun courseFetchSucceeds_setsCourseTitle() = runTest(testDispatcher) {
        val viewModel = buildViewModel(getCourseDetails = { ApiResult.Success(course(it)) })
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Building Reliable REST APIs", viewModel.uiState.value.courseTitle)
    }

    @Test
    fun courseFetchFails_leavesCourseTitleNull_neverSurfacingAnErrorState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getCourseDetails = { ApiResult.Failure(ApiErrorCode.CourseNotFound, "not found", null, 404) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.courseTitle)
    }
}
