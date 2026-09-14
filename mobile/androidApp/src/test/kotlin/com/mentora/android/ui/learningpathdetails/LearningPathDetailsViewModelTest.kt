package com.mentora.android.ui.learningpathdetails

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.LearningPathCourse
import com.mentora.shared.domain.model.LearningPathDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun pathCourse(id: String) = LearningPathCourse(id = id, title = "Course $id", thumbnailMediaId = null)

private fun pathDetail(
    id: String = "path-1",
    courses: List<LearningPathCourse> = listOf(pathCourse("course-1"), pathCourse("course-2"), pathCourse("course-3")),
    progressPercent: Int? = null,
    isFollowing: Boolean = false,
) = LearningPathDetail(id = id, title = "Path $id", description = "Description $id", courses = courses, progressPercent = progressPercent, isFollowing = isFollowing)

// Round-1 review finding (MEDIUM): `LearningPathDetailsViewModel` now derives "completed" from
// `courseCompletedAt != null` (never `completionPercent >= 100`, which is lesson-count-only and can
// disagree with the server's own course-completion definition) — `completed` here defaults to
// mirroring `percent >= 100` ONLY for existing call-site convenience, but every test that cares about
// the Completed/Current boundary must pass it explicitly, not rely on this default.
private fun progress(courseId: String, percent: Int, completed: Boolean = percent >= 100) = CourseProgress(
    courseId = courseId,
    completedLessonIds = emptyList(),
    currentLessonId = null,
    currentPositionSeconds = null,
    quizPassed = null,
    completionPercent = percent,
    courseCompletedAt = if (completed) "2026-01-01T00:00:00Z" else null,
)

/**
 * T16 — [LearningPathDetailsViewModel]'s Completed/Current/Upcoming derivation + follow/unfollow
 * logic, as a plain JVM unit test. Mirrors `CourseDetailsViewModelTest`'s exact style (hand-built
 * fakes wired to the ViewModel's own constructor lambdas, no real network/`MentoraSdk`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LearningPathDetailsViewModelTest {

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
        pathId: String = "path-1",
        isAuthenticated: Boolean = false,
        getLearningPathDetail: suspend (String) -> ApiResult<LearningPathDetail> = { ApiResult.Success(pathDetail(it)) },
        followLearningPath: suspend (String) -> ApiResult<Boolean> = { ApiResult.Success(true) },
        unfollowLearningPath: suspend (String) -> ApiResult<Boolean> = { ApiResult.Success(false) },
        getCourseProgress: suspend (String) -> ApiResult<CourseProgress> =
            { ApiResult.Failure(ApiErrorCode.ForbiddenNotEnrolled, "not enrolled", null, 403) },
        resolveThumbnailUrl: (String) -> String = { "https://example.test/media/$it/file" },
    ) = LearningPathDetailsViewModel(
        pathId = pathId,
        isAuthenticated = isAuthenticated,
        getLearningPathDetail = getLearningPathDetail,
        followLearningPath = followLearningPath,
        unfollowLearningPath = unfollowLearningPath,
        getCourseProgress = getCourseProgress,
        resolveThumbnailUrl = resolveThumbnailUrl,
    )

    @Test
    fun guest_neverCallsGetCourseProgress_andTreatsTheFirstCourseAsCurrent() = runTest(testDispatcher) {
        var progressCallCount = 0
        val viewModel = buildViewModel(
            isAuthenticated = false,
            getLearningPathDetail = { ApiResult.Success(pathDetail(it, progressPercent = null, isFollowing = false)) },
            getCourseProgress = { progressCallCount++; ApiResult.Success(progress("unused", 100)) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, progressCallCount)
        val success = viewModel.uiState.value.path as LearningPathLoadState.Success
        assertNull(success.progressPercent)
        assertFalse(success.isFollowing)
        assertEquals(CourseSequenceStatus.Current, success.courses[0].status)
        assertEquals(CourseSequenceStatus.Upcoming, success.courses[1].status)
        assertEquals(CourseSequenceStatus.Upcoming, success.courses[2].status)
        assertFalse(success.courses[0].isEnrolled)
    }

    @Test
    fun following_withAMixOfCompletedCurrentAndUpcomingCourses_derivesStatusesCorrectly() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            isAuthenticated = true,
            getLearningPathDetail = { ApiResult.Success(pathDetail(it, progressPercent = 33, isFollowing = true)) },
            getCourseProgress = { courseId ->
                when (courseId) {
                    "course-1" -> ApiResult.Success(progress(courseId, 100))
                    "course-2" -> ApiResult.Success(progress(courseId, 40))
                    else -> ApiResult.Failure(ApiErrorCode.ForbiddenNotEnrolled, "not enrolled", null, 403)
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.path as LearningPathLoadState.Success
        assertTrue(success.isFollowing)
        assertEquals(CourseSequenceStatus.Completed, success.courses[0].status)
        assertTrue(success.courses[0].isEnrolled)
        assertEquals(CourseSequenceStatus.Current, success.courses[1].status)
        assertTrue(success.courses[1].isEnrolled)
        assertEquals(CourseSequenceStatus.Upcoming, success.courses[2].status)
        assertFalse(success.courses[2].isEnrolled)
    }

    /** Round-1 review finding (MEDIUM), regression test: a course with every lesson watched
     *  (`completionPercent = 100`) but its quiz not yet passed (`courseCompletedAt = null`) must NOT
     *  render as Completed — that combination is exactly what let the badge derivation disagree with
     *  the path-level progress bar (server-computed from the same `courseCompletedAt` definition). */
    @Test
    fun lessonsDoneButQuizNotPassed_isNOTTreatedAsCompleted() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            isAuthenticated = true,
            getLearningPathDetail = { ApiResult.Success(pathDetail(it, progressPercent = 0, isFollowing = true)) },
            getCourseProgress = { courseId ->
                ApiResult.Success(progress(courseId, percent = 100, completed = false))
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.path as LearningPathLoadState.Success
        // The first course is Current (not Completed), even though its lessons are 100% watched.
        assertEquals(CourseSequenceStatus.Current, success.courses[0].status)
        assertTrue(success.courses.none { it.status == CourseSequenceStatus.Completed })
    }

    @Test
    fun everyCourseCompleted_noCourseGetsTheCurrentBadge() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            isAuthenticated = true,
            getCourseProgress = { courseId -> ApiResult.Success(progress(courseId, 100)) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.path as LearningPathLoadState.Success
        assertTrue(success.courses.all { it.status == CourseSequenceStatus.Completed })
        assertTrue(success.courses.none { it.status == CourseSequenceStatus.Current })
    }

    @Test
    fun aTransientProgressFailure_fallsBackToNotEnrolled_neverBlocksTheScreen() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            isAuthenticated = true,
            getCourseProgress = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.path as LearningPathLoadState.Success
        assertTrue(success.courses.all { !it.isEnrolled })
        assertEquals(CourseSequenceStatus.Current, success.courses[0].status)
    }

    @Test
    fun pathFetchFailure_becomesTheErrorState_withTheApiErrorCode() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getLearningPathDetail = { ApiResult.Failure(ApiErrorCode.LearningPathNotFound, "not found", null, 404) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.path as LearningPathLoadState.Error
        assertEquals(ApiErrorCode.LearningPathNotFound, error.code)
    }

    @Test
    fun onRetry_reloadsThePath() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            getLearningPathDetail = {
                callCount++
                if (callCount == 1) {
                    ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
                } else {
                    ApiResult.Success(pathDetail(it))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.path is LearningPathLoadState.Error)

        viewModel.onRetry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.path is LearningPathLoadState.Success)
        assertEquals(2, callCount)
    }

    @Test
    fun followToggle_whenNotFollowing_callsFollow_andFlipsIsFollowingOnSuccess() = runTest(testDispatcher) {
        var followCallCount = 0
        var unfollowCallCount = 0
        val viewModel = buildViewModel(
            isAuthenticated = true,
            getLearningPathDetail = { ApiResult.Success(pathDetail(it, isFollowing = false)) },
            followLearningPath = { followCallCount++; ApiResult.Success(true) },
            unfollowLearningPath = { unfollowCallCount++; ApiResult.Success(false) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFollowToggleClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, followCallCount)
        assertEquals(0, unfollowCallCount)
        val success = viewModel.uiState.value.path as LearningPathLoadState.Success
        assertTrue(success.isFollowing)
        assertFalse(success.followInFlight)
        assertNull(success.followError)
    }

    @Test
    fun followToggle_whenAlreadyFollowing_callsUnfollow() = runTest(testDispatcher) {
        var followCallCount = 0
        var unfollowCallCount = 0
        val viewModel = buildViewModel(
            isAuthenticated = true,
            getLearningPathDetail = { ApiResult.Success(pathDetail(it, isFollowing = true)) },
            followLearningPath = { followCallCount++; ApiResult.Success(true) },
            unfollowLearningPath = { unfollowCallCount++; ApiResult.Success(false) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFollowToggleClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, followCallCount)
        assertEquals(1, unfollowCallCount)
        val success = viewModel.uiState.value.path as LearningPathLoadState.Success
        assertFalse(success.isFollowing)
    }

    @Test
    fun followToggle_doubleTapWhileInFlight_neverFiresASecondConcurrentCall() = runTest(testDispatcher) {
        var followCallCount = 0
        val viewModel = buildViewModel(
            isAuthenticated = true,
            getLearningPathDetail = { ApiResult.Success(pathDetail(it, isFollowing = false)) },
            followLearningPath = { followCallCount++; ApiResult.Success(true) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFollowToggleClicked()
        // Deliberately NOT advancing the dispatcher here — the first call is still in flight.
        viewModel.onFollowToggleClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, followCallCount)
    }

    @Test
    fun followToggle_failure_surfacesAStickyFollowError_andDismissClearsIt() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            isAuthenticated = true,
            getLearningPathDetail = { ApiResult.Success(pathDetail(it, isFollowing = false)) },
            followLearningPath = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onFollowToggleClicked()
        testDispatcher.scheduler.advanceUntilIdle()

        var success = viewModel.uiState.value.path as LearningPathLoadState.Success
        assertEquals(ApiErrorCode.InternalError, success.followError)
        assertFalse(success.followInFlight)
        // isFollowing must NOT have flipped on a failed call.
        assertFalse(success.isFollowing)

        viewModel.onFollowErrorDismissed()
        success = viewModel.uiState.value.path as LearningPathLoadState.Success
        assertNull(success.followError)
    }
}
