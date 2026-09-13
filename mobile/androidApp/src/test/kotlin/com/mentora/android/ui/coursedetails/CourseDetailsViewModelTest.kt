package com.mentora.android.ui.coursedetails

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.Enrollment
import com.mentora.shared.domain.model.EnrollmentSource
import com.mentora.shared.domain.model.EnrollmentStatus
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

private fun course(id: String = "course-1") = Course(
    id = id,
    title = "Course $id",
    description = "Description $id",
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

private fun enrollment(courseId: String) = Enrollment(
    id = "enrollment-$courseId",
    courseId = courseId,
    source = EnrollmentSource.DemoCheckout,
    enrolledAt = "2026-01-01T00:00:00Z",
    status = EnrollmentStatus.Active,
)

/**
 * T10 — [CourseDetailsViewModel]'s CTA/enrollment-derivation logic, as a plain JVM unit test. Wires
 * the ViewModel's constructor lambdas (see that class's own kdoc for why they're lambdas, not a
 * `MentoraSdk`) to hand-built fakes rather than any real network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CourseDetailsViewModelTest {

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
        isAuthenticated: Boolean = false,
        getCourseDetails: suspend (String) -> ApiResult<Course> = { ApiResult.Success(course(it)) },
        listEnrollments: suspend (String?, Int?) -> ApiResult<CursorPage<Enrollment>> =
            { _, _ -> ApiResult.Success(CursorPage(items = emptyList(), nextCursor = null)) },
        listCategories: suspend () -> ApiResult<List<Category>> = { ApiResult.Success(emptyList()) },
        resolveThumbnailUrl: (String) -> String = { "https://example.test/media/$it/file" },
    ) = CourseDetailsViewModel(
        courseId = courseId,
        isAuthenticated = isAuthenticated,
        getCourseDetails = getCourseDetails,
        listEnrollments = listEnrollments,
        listCategories = listCategories,
        resolveThumbnailUrl = resolveThumbnailUrl,
    )

    @Test
    fun guest_neverCallsListEnrollments_andCtaIsLoginToEnroll() = runTest(testDispatcher) {
        var listEnrollmentsCallCount = 0
        val viewModel = buildViewModel(
            isAuthenticated = false,
            listEnrollments = { _, _ ->
                listEnrollmentsCallCount++
                ApiResult.Success(CursorPage(items = listOf(enrollment("course-1")), nextCursor = null))
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, listEnrollmentsCallCount)
        val success = viewModel.uiState.value.course as CourseLoadState.Success
        assertFalse(success.isEnrolled)
        assertEquals(CourseDetailsCtaState.LoginToEnroll, success.cta)
    }

    @Test
    fun authenticatedNotEnrolled_ctaIsEnroll() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            isAuthenticated = true,
            listEnrollments = { _, _ -> ApiResult.Success(CursorPage(items = emptyList(), nextCursor = null)) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.course as CourseLoadState.Success
        assertFalse(success.isEnrolled)
        assertEquals(CourseDetailsCtaState.Enroll, success.cta)
    }

    @Test
    fun authenticatedAndEnrolled_onTheFirstPage_ctaIsContinueLearning() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            courseId = "course-1",
            isAuthenticated = true,
            listEnrollments = { _, _ ->
                ApiResult.Success(CursorPage(items = listOf(enrollment("course-1")), nextCursor = null))
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.course as CourseLoadState.Success
        assertTrue(success.isEnrolled)
        assertEquals(CourseDetailsCtaState.ContinueLearning, success.cta)
    }

    @Test
    fun authenticatedAndEnrolled_onlyOnASubsequentPage_pagesFullyThroughNextCursor() = runTest(testDispatcher) {
        val recordedCursors = mutableListOf<String?>()
        val viewModel = buildViewModel(
            courseId = "course-target",
            isAuthenticated = true,
            listEnrollments = { cursor, _ ->
                recordedCursors += cursor
                if (cursor == null) {
                    ApiResult.Success(CursorPage(items = listOf(enrollment("course-other")), nextCursor = "cursor-2"))
                } else {
                    assertEquals("cursor-2", cursor)
                    ApiResult.Success(CursorPage(items = listOf(enrollment("course-target")), nextCursor = null))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.course as CourseLoadState.Success
        assertTrue(success.isEnrolled)
        assertEquals(CourseDetailsCtaState.ContinueLearning, success.cta)
        assertEquals(listOf(null, "cursor-2"), recordedCursors)
    }

    @Test
    fun enrollmentCheckFailure_failsSafeToNotEnrolled_neverBlocksTheCourseFromRendering() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            isAuthenticated = true,
            listEnrollments = { _, _ -> ApiResult.Failure(ApiErrorCode.AuthTokenInvalid, "no session", null, 401) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.course as CourseLoadState.Success
        assertFalse(success.isEnrolled)
        assertEquals(CourseDetailsCtaState.Enroll, success.cta)
    }

    @Test
    fun courseFetchFailure_becomesTheErrorState_withTheApiErrorCode() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getCourseDetails = { ApiResult.Failure(ApiErrorCode.CourseNotFound, "not found", null, 404) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.course as CourseLoadState.Error
        assertEquals(ApiErrorCode.CourseNotFound, error.code)
    }

    @Test
    fun onRetry_reloadsTheCourse() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            getCourseDetails = {
                callCount++
                if (callCount == 1) {
                    ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
                } else {
                    ApiResult.Success(course(it))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.course is CourseLoadState.Error)

        viewModel.onRetry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.course is CourseLoadState.Success)
        assertEquals(2, callCount)
    }

    @Test
    fun thumbnailMediaId_null_resolvesToANullThumbnailUrl_neverCallingTheResolver() = runTest(testDispatcher) {
        var resolveCallCount = 0
        val viewModel = buildViewModel(
            resolveThumbnailUrl = { resolveCallCount++; "unused" },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.course as CourseLoadState.Success
        assertEquals(null, success.thumbnailUrl)
        assertEquals(0, resolveCallCount)
    }

    @Test
    fun categoriesLoad_independentlyOfTheCourse_andACategoriesFailureDoesNotBlockTheCourse() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            listCategories = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.course is CourseLoadState.Success)
        assertEquals(emptyList<Category>(), viewModel.uiState.value.categories)
    }

    @Test
    fun categoriesLoad_succeeds_andArePlacedInTheUiState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            listCategories = { ApiResult.Success(listOf(Category(id = "cat-1", name = "Design", slug = "design", courseCount = 3))) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Design", viewModel.uiState.value.categories.first { it.id == "cat-1" }.name)
    }
}
