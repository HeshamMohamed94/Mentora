package com.mentora.android.ui.profile

import com.mentora.android.domain.mylearning.LearningItemWithProgress
import com.mentora.shared.auth.Role
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.CertificateSummary
import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.Enrollment
import com.mentora.shared.domain.model.EnrollmentSource
import com.mentora.shared.domain.model.EnrollmentStatus
import com.mentora.shared.domain.model.PriceDisplay
import com.mentora.shared.domain.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun user(name: String = "Sarah Ahmed") = User(
    id = "user-1",
    email = "sarah@example.com",
    name = name,
    role = Role.Student,
    avatarMediaId = null,
    preferredLocale = "en",
    createdAt = "2026-01-01T00:00:00Z",
)

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

private fun progress(courseId: String, completed: Boolean) = CourseProgress(
    courseId = courseId,
    completedLessonIds = emptyList(),
    currentLessonId = null,
    currentPositionSeconds = null,
    quizPassed = null,
    completionPercent = if (completed) 100 else 40,
    courseCompletedAt = if (completed) "2026-01-01T00:00:00Z" else null,
)

private fun item(courseId: String, completed: Boolean) =
    LearningItemWithProgress(enrollment(courseId), course(courseId), progress(courseId, completed))

private fun certificate(id: String) = CertificateSummary(
    id = id,
    courseTitleSnapshot = "Course $id",
    instructorNameSnapshot = "Instructor",
    issuedAt = "2026-01-01T00:00:00Z",
)

/**
 * T18 — [ProfileViewModel]'s profile-load/stats/edit-name/logout logic, as a plain JVM unit test.
 * Mirrors `MyLearningViewModelTest`'s exact fixture style (hand-built fakes, no mocking framework).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

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
        getProfile: suspend () -> ApiResult<User> = { ApiResult.Success(user()) },
        updateProfile: suspend (String) -> ApiResult<User> = { name -> ApiResult.Success(user(name)) },
        getMyLearningWithProgress: suspend () -> ApiResult<List<LearningItemWithProgress>> =
            { ApiResult.Success(emptyList()) },
        listCertificates: suspend (String?, Int?) -> ApiResult<CursorPage<CertificateSummary>> =
            { _, _ -> ApiResult.Success(CursorPage(emptyList(), null)) },
        logout: suspend () -> ApiResult<Unit> = { ApiResult.Success(Unit) },
    ) = ProfileViewModel(getProfile, updateProfile, getMyLearningWithProgress, listCertificates, logout)

    // ---- Profile load -------------------------------------------------------------------------

    @Test
    fun load_success_showsTheLoadedUser() = runTest(testDispatcher) {
        val viewModel = buildViewModel(getProfile = { ApiResult.Success(user("Sarah Ahmed")) })
        testDispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as ProfileContentState.Loaded
        assertEquals("Sarah Ahmed", content.user.name)
    }

    @Test
    fun load_failure_showsARetryableError() = runTest(testDispatcher) {
        val viewModel = buildViewModel(getProfile = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.content is ProfileContentState.Error)
    }

    @Test
    fun onRetryTapped_afterAFailure_reloadsSuccessfully() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            getProfile = {
                callCount++
                if (callCount == 1) ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) else ApiResult.Success(user())
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.content is ProfileContentState.Error)

        viewModel.onRetryTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.content is ProfileContentState.Loaded)
    }

    @Test
    fun onRetryTapped_alsoRetriesStats_notJustTheProfileLoad() = runTest(testDispatcher) {
        // Review fix (MEDIUM): a stats failure almost always co-occurs with a primary-content
        // failure (the same outage), so a retry that only re-ran the profile load left `stats`
        // permanently `null` even after connectivity returned.
        var statsCallCount = 0
        val viewModel = buildViewModel(
            getProfile = { ApiResult.Success(user()) },
            getMyLearningWithProgress = {
                statsCallCount++
                if (statsCallCount == 1) ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) else ApiResult.Success(emptyList())
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.stats)

        viewModel.onRetryTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, statsCallCount)
        assertEquals(ProfileStats(coursesCompleted = 0, certificatesEarned = 0), viewModel.uiState.value.stats)
    }

    // ---- Stats ----------------------------------------------------------------------------------

    @Test
    fun stats_countsOnlyCoursesWithCourseCompletedAtSet_neverJustCompletionPercent() = runTest(testDispatcher) {
        // Review-precedent regression: D91 established `courseCompletedAt != null`, never
        // `completionPercent >= 100` alone (a course can be 100% lesson-watched with its quiz still
        // unpassed) — this stat must use the identical definition.
        val viewModel = buildViewModel(
            getMyLearningWithProgress = {
                ApiResult.Success(listOf(item("c1", completed = true), item("c2", completed = false)))
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.stats?.coursesCompleted)
    }

    @Test
    fun stats_certificateCount_pagesThroughEveryPage_neverTrustsPageOneAlone() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            listCertificates = { cursor, _ ->
                callCount++
                if (cursor == null) {
                    ApiResult.Success(CursorPage(listOf(certificate("cert-1")), "cursor-2"))
                } else {
                    ApiResult.Success(CursorPage(listOf(certificate("cert-2")), null))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, callCount)
        assertEquals(2, viewModel.uiState.value.stats?.certificatesEarned)
    }

    @Test
    fun stats_failure_leavesStatsNull_neverBlocksThePrimaryProfileContent() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getMyLearningWithProgress = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.stats)
        assertTrue(viewModel.uiState.value.content is ProfileContentState.Loaded)
    }

    // ---- Edit name --------------------------------------------------------------------------------

    @Test
    fun onEditProfileTapped_seedsTheEditedNameFromTheCurrentlyLoadedUser() = runTest(testDispatcher) {
        val viewModel = buildViewModel(getProfile = { ApiResult.Success(user("Sarah Ahmed")) })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onEditProfileTapped()

        assertTrue(viewModel.uiState.value.isEditing)
        assertEquals("Sarah Ahmed", viewModel.uiState.value.editedName)
    }

    @Test
    fun onSaveNameTapped_success_updatesTheLoadedUser_andExitsEditMode() = runTest(testDispatcher) {
        val viewModel = buildViewModel(updateProfile = { name -> ApiResult.Success(user(name)) })
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEditProfileTapped()
        viewModel.onNameChanged("New Name")

        viewModel.onSaveNameTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        val content = viewModel.uiState.value.content as ProfileContentState.Loaded
        assertEquals("New Name", content.user.name)
        assertTrue(!viewModel.uiState.value.isEditing)
        assertTrue(!viewModel.uiState.value.isSavingName)
    }

    @Test
    fun onSaveNameTapped_tooLongField_staysInEditMode_andSurfacesTheFieldSpecificError() = runTest(testDispatcher) {
        // Review fix (LOW): `fields["name"] == "TOO_LONG"` routes to its own field-specific error,
        // same D51-established pattern `AuthViewModel`'s Register field-error routing already uses —
        // not the generic `ApiErrorCode` fallback.
        val viewModel = buildViewModel(
            updateProfile = { ApiResult.Failure(ApiErrorCode.ValidationError, "too long", mapOf("name" to "TOO_LONG"), 0) },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEditProfileTapped()
        viewModel.onNameChanged("x".repeat(500))

        viewModel.onSaveNameTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEditing)
        assertEquals(NameFieldError.TooLong, viewModel.uiState.value.saveNameFieldError)
        assertNull(viewModel.uiState.value.saveNameError)
    }

    @Test
    fun onSaveNameTapped_requiredField_surfacesTheFieldSpecificError() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            updateProfile = { ApiResult.Failure(ApiErrorCode.ValidationError, "blank", mapOf("name" to "REQUIRED"), 0) },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEditProfileTapped()

        viewModel.onSaveNameTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(NameFieldError.Required, viewModel.uiState.value.saveNameFieldError)
        assertNull(viewModel.uiState.value.saveNameError)
    }

    @Test
    fun onSaveNameTapped_nonFieldFailure_fallsBackToTheGenericErrorSlot() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            updateProfile = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEditProfileTapped()

        viewModel.onSaveNameTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.saveNameFieldError)
        assertEquals(ApiErrorCode.InternalError, viewModel.uiState.value.saveNameError)
    }

    @Test
    fun onCancelEditTapped_exitsEditMode_withoutSaving() = runTest(testDispatcher) {
        var saveCallCount = 0
        val viewModel = buildViewModel(updateProfile = { saveCallCount++; ApiResult.Success(user()) })
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.onEditProfileTapped()
        viewModel.onNameChanged("Discarded Name")

        viewModel.onCancelEditTapped()

        assertTrue(!viewModel.uiState.value.isEditing)
        assertEquals(0, saveCallCount)
    }

    // ---- Logout -----------------------------------------------------------------------------------

    @Test
    fun onLogoutTapped_callsLogout_exactlyOnce_evenOnADoubleTap() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(logout = { callCount++; ApiResult.Success(Unit) })
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onLogoutTapped()
        viewModel.onLogoutTapped()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, callCount)
    }
}
