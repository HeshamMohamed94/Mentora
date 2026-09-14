package com.mentora.android.ui.checkout

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.CheckoutCourse
import com.mentora.shared.domain.model.CheckoutPreview
import com.mentora.shared.domain.model.Enrollment
import com.mentora.shared.domain.model.EnrollmentCompletion
import com.mentora.shared.domain.model.EnrollmentSource
import com.mentora.shared.domain.model.EnrollmentStatus
import com.mentora.shared.domain.model.PriceDisplay
import kotlinx.coroutines.CompletableDeferred
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

private fun preview(courseId: String = "course-1", thumbnailMediaId: String? = null) = CheckoutPreview(
    course = CheckoutCourse(id = courseId, title = "Course $courseId", thumbnailMediaId = thumbnailMediaId),
    instructorName = "Instructor",
    priceDisplay = PriceDisplay(amount = 899, currency = "EGP"),
)

private fun completion(courseId: String) = EnrollmentCompletion(
    enrollment = Enrollment(
        id = "enrollment-$courseId",
        courseId = courseId,
        source = EnrollmentSource.DemoCheckout,
        enrolledAt = "2026-01-01T00:00:00Z",
        status = EnrollmentStatus.Active,
    ),
    alreadyEnrolled = false,
)

/**
 * T11 — [CheckoutViewModel]'s preview-load/completion logic, as a plain JVM unit test. Wires the
 * ViewModel's constructor lambdas (same lambda-constructor seam as `CourseDetailsViewModel`, see
 * that class's own kdoc for why) to hand-built fakes rather than any real network.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {

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
        getCheckoutPreview: suspend (String) -> ApiResult<CheckoutPreview> = { ApiResult.Success(preview(it)) },
        completeDemoCheckout: suspend (String) -> ApiResult<EnrollmentCompletion> = { ApiResult.Success(completion(it)) },
        resolveThumbnailUrl: (String) -> String = { "https://example.test/media/$it/file" },
    ) = CheckoutViewModel(
        courseId = courseId,
        getCheckoutPreview = getCheckoutPreview,
        completeDemoCheckout = completeDemoCheckout,
        resolveThumbnailUrl = resolveThumbnailUrl,
    )

    @Test
    fun previewLoads_successfully_intoSuccessState() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.preview as CheckoutLoadState.Success
        assertEquals("course-1", success.preview.course.id)
    }

    @Test
    fun thumbnailMediaId_null_resolvesToANullThumbnailUrl_neverCallingTheResolver() = runTest(testDispatcher) {
        var resolveCallCount = 0
        val viewModel = buildViewModel(
            getCheckoutPreview = { ApiResult.Success(preview(it, thumbnailMediaId = null)) },
            resolveThumbnailUrl = { resolveCallCount++; "unused" },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.preview as CheckoutLoadState.Success
        assertEquals(null, success.thumbnailUrl)
        assertEquals(0, resolveCallCount)
    }

    @Test
    fun thumbnailMediaId_present_resolvesViaTheResolver() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getCheckoutPreview = { ApiResult.Success(preview(it, thumbnailMediaId = "media-1")) },
            resolveThumbnailUrl = { "https://example.test/media/$it/file" },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val success = viewModel.uiState.value.preview as CheckoutLoadState.Success
        assertEquals("https://example.test/media/media-1/file", success.thumbnailUrl)
    }

    @Test
    fun previewLoadFailure_becomesTheErrorState_withTheApiErrorCode() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getCheckoutPreview = { ApiResult.Failure(ApiErrorCode.CourseNotFound, "not found", null, 404) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value.preview as CheckoutLoadState.Error
        assertEquals(ApiErrorCode.CourseNotFound, error.code)
    }

    @Test
    fun onRetryPreview_reloadsThePreview() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            getCheckoutPreview = {
                callCount++
                if (callCount == 1) {
                    ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
                } else {
                    ApiResult.Success(preview(it))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.preview is CheckoutLoadState.Error)

        viewModel.onRetryPreview()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.preview is CheckoutLoadState.Success)
        assertEquals(2, callCount)
    }

    @Test
    fun completePurchase_onSuccess_invokesTheCallback_andLeavesCompletionFailedFalse() = runTest(testDispatcher) {
        val viewModel = buildViewModel(completeDemoCheckout = { ApiResult.Success(completion(it)) })
        testDispatcher.scheduler.advanceUntilIdle()

        var callbackResult: EnrollmentCompletion? = null
        viewModel.completePurchase(onSuccess = { callbackResult = it })
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("course-1", callbackResult?.enrollment?.courseId)
        assertFalse(viewModel.uiState.value.isProcessing)
        assertFalse(viewModel.uiState.value.completionFailed)
    }

    @Test
    fun completePurchase_onFailure_setsCompletionFailed_andNeverInvokesTheCallback() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            completeDemoCheckout = { ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        var callbackInvoked = false
        viewModel.completePurchase(onSuccess = { callbackInvoked = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(callbackInvoked)
        assertFalse(viewModel.uiState.value.isProcessing)
        assertTrue(viewModel.uiState.value.completionFailed)
    }

    @Test
    fun completePurchase_aFreshRetryAfterFailure_clearsCompletionFailedOnStart() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            completeDemoCheckout = {
                callCount++
                if (callCount == 1) {
                    ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
                } else {
                    ApiResult.Success(completion(it))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.completePurchase(onSuccess = {})
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value.completionFailed)

        var callbackInvoked = false
        viewModel.completePurchase(onSuccess = { callbackInvoked = true })
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(callbackInvoked)
        assertFalse(viewModel.uiState.value.completionFailed)
        assertEquals(2, callCount)
    }

    @Test
    fun completePurchase_whileAlreadyProcessing_isIgnored_neverCallingTheNetworkTwice() = runTest(testDispatcher) {
        var callCount = 0
        // A real suspension point the test controls precisely — a fake that returns immediately
        // wouldn't exercise the guard at all (the first call would already be done, `isProcessing`
        // back to `false`, before the "second tap" ever ran).
        val networkGate = CompletableDeferred<Unit>()
        val viewModel = buildViewModel(
            completeDemoCheckout = { callCount++; networkGate.await(); ApiResult.Success(completion(it)) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.completePurchase(onSuccess = {})
        testDispatcher.scheduler.runCurrent() // let the first tap start and suspend on the gate
        assertTrue(viewModel.uiState.value.isProcessing)

        viewModel.completePurchase(onSuccess = {}) // a "double tap" while the first is still in flight
        testDispatcher.scheduler.runCurrent()

        networkGate.complete(Unit)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, callCount)
        assertFalse(viewModel.uiState.value.isProcessing)
    }
}
