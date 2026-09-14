package com.mentora.android.ui.mylearning

import com.mentora.android.domain.mylearning.LearningItemWithProgress
import com.mentora.android.domain.mylearning.MyLearningLoadState
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.CertificateSummary
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.Enrollment
import com.mentora.shared.domain.model.EnrollmentSource
import com.mentora.shared.domain.model.EnrollmentStatus
import com.mentora.shared.domain.model.LearningPath
import com.mentora.shared.domain.model.LearningPathDetail
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

private fun learningPath(id: String) = LearningPath(id = id, title = "Path $id", description = "Description $id", courseCount = 3)

private fun learningPathDetail(id: String, isFollowing: Boolean) = LearningPathDetail(
    id = id,
    title = "Path $id",
    description = "Description $id",
    courses = emptyList(),
    progressPercent = 40,
    isFollowing = isFollowing,
)

private fun certificate(id: String) = CertificateSummary(
    id = id,
    courseTitleSnapshot = "Course for $id",
    instructorNameSnapshot = "Instructor",
    issuedAt = "2026-01-01T00:00:00Z",
)

/**
 * T12 — [MyLearningViewModel]'s filter/G3-join/G5-followed-paths logic, as a plain JVM unit test.
 * Mirrors `HomeViewModelTest`/`ExploreViewModelTest`'s exact style.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MyLearningViewModelTest {

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
        getMyLearningWithProgress: suspend () -> ApiResult<List<LearningItemWithProgress>> = { ApiResult.Success(emptyList()) },
        listLearningPaths: suspend () -> ApiResult<List<LearningPath>> = { ApiResult.Success(emptyList()) },
        getLearningPathDetail: suspend (String) -> ApiResult<LearningPathDetail> = { ApiResult.Success(learningPathDetail(it, isFollowing = false)) },
        listCertificates: suspend (String?, Int?) -> ApiResult<CursorPage<CertificateSummary>> =
            { _, _ -> ApiResult.Success(CursorPage(items = emptyList(), nextCursor = null)) },
        listCategories: suspend () -> ApiResult<List<Category>> = { ApiResult.Success(emptyList()) },
        resolveThumbnailUrl: (String) -> String = { "https://example.test/media/$it/file" },
    ) = MyLearningViewModel(
        getMyLearningWithProgress = getMyLearningWithProgress,
        listLearningPaths = listLearningPaths,
        getLearningPathDetail = getLearningPathDetail,
        listCertificates = listCertificates,
        listCategories = listCategories,
        resolveThumbnailUrl = resolveThumbnailUrl,
    )

    @Test
    fun itemsLoadSuccessfully_populateLoadedState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getMyLearningWithProgress = { ApiResult.Success(listOf(item("course-1", 40), item("course-2", 100))) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val loaded = viewModel.uiState.value.items as MyLearningLoadState.Loaded
        assertEquals(2, loaded.items.size)
    }

    @Test
    fun itemsLoadFailure_becomesTheErrorState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getMyLearningWithProgress = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.items as MyLearningLoadState.Error
        assertEquals(ApiErrorCode.InternalError, error.code)
    }

    @Test
    fun onRetryItems_reloadsItems() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            getMyLearningWithProgress = {
                callCount++
                if (callCount == 1) {
                    ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
                } else {
                    ApiResult.Success(listOf(item("course-1", 40)))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.items is MyLearningLoadState.Error)

        viewModel.onRetryItems()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.items is MyLearningLoadState.Loaded)
        assertEquals(2, callCount)
    }

    @Test
    fun onFilterSelected_updatesTheUiStatesFilter() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(MyLearningFilter.All, viewModel.uiState.value.filter)

        viewModel.onFilterSelected(MyLearningFilter.Completed)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(MyLearningFilter.Completed, viewModel.uiState.value.filter)
    }

    @Test
    fun followedPaths_g5Join_keepsOnlyPathsWhereIsFollowingIsTrue() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            listLearningPaths = { ApiResult.Success(listOf(learningPath("path-1"), learningPath("path-2"))) },
            getLearningPathDetail = { id -> ApiResult.Success(learningPathDetail(id, isFollowing = id == "path-1")) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.followedPaths.size)
        assertEquals("path-1", viewModel.uiState.value.followedPaths.first().id)
    }

    @Test
    fun followedPaths_aSinglePathDetailFailure_excludesOnlyThatPath_bestEffort() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            listLearningPaths = { ApiResult.Success(listOf(learningPath("path-1"), learningPath("path-2"))) },
            getLearningPathDetail = { id ->
                if (id == "path-1") {
                    ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
                } else {
                    ApiResult.Success(learningPathDetail(id, isFollowing = true))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.followedPaths.size)
        assertEquals("path-2", viewModel.uiState.value.followedPaths.first().id)
    }

    @Test
    fun followedPaths_listFailure_leavesTheModuleEmpty_neverSurfacingItsOwnErrorState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            listLearningPaths = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<LearningPathDetail>(), viewModel.uiState.value.followedPaths)
    }

    @Test
    fun refreshFollowedPaths_reloadsTheFollowedPathsModule_reflectingAFollowUnfollowThatHappenedElsewhere() = runTest(testDispatcher) {
        var isFollowing = false
        val viewModel = buildViewModel(
            listLearningPaths = { ApiResult.Success(listOf(learningPath("path-1"))) },
            getLearningPathDetail = { id -> ApiResult.Success(learningPathDetail(id, isFollowing = isFollowing)) },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(emptyList<LearningPathDetail>(), viewModel.uiState.value.followedPaths)

        // Simulates a follow that happened on Learning Path Details while My Learning was off-screen —
        // this screen's own init-time load already ran with the old (not-following) value above.
        isFollowing = true
        viewModel.refreshFollowedPaths()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.followedPaths.size)
        assertEquals("path-1", viewModel.uiState.value.followedPaths.first().id)
    }

    @Test
    fun certificates_loadSuccessfully_populateTheUiState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            listCertificates = { _, _ -> ApiResult.Success(CursorPage(items = listOf(certificate("MTR-1"), certificate("MTR-2")), nextCursor = null)) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.certificates.size)
    }

    @Test
    fun certificates_failure_leavesTheModuleEmpty_bestEffort() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            listCertificates = { _, _ -> ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(emptyList<CertificateSummary>(), viewModel.uiState.value.certificates)
    }
}
