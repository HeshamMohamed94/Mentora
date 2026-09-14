package com.mentora.android.ui.home

import com.mentora.android.domain.mylearning.LearningItemWithProgress
import com.mentora.android.domain.mylearning.MyLearningLoadState
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CourseFilters
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.CourseSummary
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun course(id: String) = Course(
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

private fun courseSummary(id: String) = CourseSummary(
    id = id,
    title = "Course $id",
    description = "Description $id",
    categoryId = "cat-1",
    level = CourseLevel.Beginner,
    contentLanguage = ContentLanguage.English,
    priceDisplay = PriceDisplay(amount = 899, currency = "EGP"),
    thumbnailMediaId = null,
    ratingSeed = 4.5,
    instructorId = "instructor-1",
    instructorName = "Instructor",
)

private fun enrollment(courseId: String) = Enrollment(
    id = "enrollment-$courseId",
    courseId = courseId,
    source = EnrollmentSource.DemoCheckout,
    enrolledAt = "2026-01-01T00:00:00Z",
    status = EnrollmentStatus.Active,
)

private fun progress(courseId: String, percent: Int) = CourseProgress(
    courseId = courseId,
    completedLessonIds = emptyList(),
    currentLessonId = null,
    currentPositionSeconds = null,
    quizPassed = null,
    completionPercent = percent,
    courseCompletedAt = null,
)

private fun item(courseId: String, percent: Int) =
    LearningItemWithProgress(enrollment(courseId), course(courseId), progress(courseId, percent))

/**
 * T12 — [HomeViewModel]'s greeting/G3-join/recommended-filtering logic, as a plain JVM unit test.
 * Wires the ViewModel's constructor lambdas to hand-built fakes rather than any real network, mirroring
 * `CourseDetailsViewModelTest`/`ExploreViewModelTest`'s exact style.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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
        userName: String = "Sara Ahmed",
        currentHour: Int = 9,
        getMyLearningWithProgress: suspend () -> ApiResult<List<LearningItemWithProgress>> = { ApiResult.Success(emptyList()) },
        searchCourses: suspend (CourseFilters, String?) -> ApiResult<CursorPage<CourseSummary>> =
            { _, _ -> ApiResult.Success(CursorPage(items = emptyList(), nextCursor = null)) },
        resolveThumbnailUrl: (String) -> String = { "https://example.test/media/$it/file" },
    ) = HomeViewModel(
        userName = userName,
        getMyLearningWithProgress = getMyLearningWithProgress,
        searchCourses = searchCourses,
        resolveThumbnailUrl = resolveThumbnailUrl,
        currentHour = { currentHour },
    )

    @Test
    fun firstNameIsParsedFromTheFullName_andGreetingBucketMatchesTheClientHour() = runTest(testDispatcher) {
        val viewModel = buildViewModel(userName = "Sara Ahmed", currentHour = 9)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Sara", viewModel.uiState.value.firstName)
        assertEquals(GreetingBucket.Morning, viewModel.uiState.value.greetingBucket)
    }

    @Test
    fun greetingBucket_coversAllFourBoundaries() {
        assertEquals(GreetingBucket.Morning, greetingBucketFor(5))
        assertEquals(GreetingBucket.Morning, greetingBucketFor(11))
        assertEquals(GreetingBucket.Afternoon, greetingBucketFor(12))
        assertEquals(GreetingBucket.Afternoon, greetingBucketFor(16))
        assertEquals(GreetingBucket.Evening, greetingBucketFor(17))
        assertEquals(GreetingBucket.Evening, greetingBucketFor(20))
        assertEquals(GreetingBucket.Night, greetingBucketFor(21))
        assertEquals(GreetingBucket.Night, greetingBucketFor(4))
    }

    @Test
    fun myLearningJoinSuccess_populatesLoadedState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getMyLearningWithProgress = { ApiResult.Success(listOf(item("course-1", 40))) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val loaded = viewModel.uiState.value.myLearning as MyLearningLoadState.Loaded
        assertEquals(1, loaded.items.size)
        assertEquals("course-1", loaded.items.first().course.id)
    }

    @Test
    fun myLearningJoinFailure_becomesTheErrorState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getMyLearningWithProgress = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.myLearning as MyLearningLoadState.Error
        assertEquals(ApiErrorCode.InternalError, error.code)
    }

    @Test
    fun recommended_excludesAlreadyEnrolledCourses_andCapsAtFour() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getMyLearningWithProgress = { ApiResult.Success(listOf(item("course-1", 40))) },
            searchCourses = { _, _ ->
                ApiResult.Success(
                    CursorPage(
                        items = listOf("course-1", "course-2", "course-3", "course-4", "course-5").map(::courseSummary),
                        nextCursor = null,
                    ),
                )
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val recommended = viewModel.uiState.value.recommended as RecommendedState.Loaded
        assertEquals(4, recommended.items.size)
        assertTrue(recommended.items.none { it.id == "course-1" })
    }

    @Test
    fun recommended_empty_becomesEmptyState_notLoadedWithZeroItems() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            searchCourses = { _, _ -> ApiResult.Success(CursorPage(items = emptyList(), nextCursor = null)) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.recommended is RecommendedState.Empty)
    }

    @Test
    fun recommendedFailure_becomesItsOwnErrorState_neverBlockingMyLearning() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getMyLearningWithProgress = { ApiResult.Success(listOf(item("course-1", 40))) },
            searchCourses = { _, _ -> ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.myLearning is MyLearningLoadState.Loaded)
        val error = viewModel.uiState.value.recommended as RecommendedState.Error
        assertEquals(ApiErrorCode.InternalError, error.code)
    }

    @Test
    fun onRetryMyLearning_reloadsBothMyLearningAndRecommended() = runTest(testDispatcher) {
        var myLearningCallCount = 0
        val viewModel = buildViewModel(
            getMyLearningWithProgress = {
                myLearningCallCount++
                if (myLearningCallCount == 1) {
                    ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
                } else {
                    ApiResult.Success(listOf(item("course-1", 40)))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.myLearning is MyLearningLoadState.Error)

        viewModel.onRetryMyLearning()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.myLearning is MyLearningLoadState.Loaded)
        assertEquals(2, myLearningCallCount)
    }
}
