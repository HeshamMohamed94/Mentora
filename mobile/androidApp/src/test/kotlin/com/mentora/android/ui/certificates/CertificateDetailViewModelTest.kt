package com.mentora.android.ui.certificates

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.CertificateDetail
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

private fun certificateDetail(id: String = "MTR-AAAA-BBBB-CCCC-DDDD-EEEE-FFFF") = CertificateDetail(
    id = id,
    studentNameSnapshot = "Jane Student",
    courseTitleSnapshot = "Kotlin Fundamentals",
    instructorNameSnapshot = "Instructor",
    completionDateSnapshot = "2026-01-12T10:30:00Z",
    issuedAt = "2026-01-12T10:30:00Z",
)

/**
 * T15 — [CertificateDetailViewModel]'s load/error logic, as a plain JVM unit test. Wires the
 * ViewModel's constructor lambda to hand-built fakes, mirroring `CourseDetailsViewModel`'s own
 * single-id `Factory`/constructor test shape.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CertificateDetailViewModelTest {

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
        certificateId: String = "MTR-AAAA-BBBB-CCCC-DDDD-EEEE-FFFF",
        getCertificate: suspend (String) -> ApiResult<CertificateDetail> =
            { ApiResult.Success(certificateDetail(it)) },
    ) = CertificateDetailViewModel(certificateId = certificateId, getCertificate = getCertificate)

    @Test
    fun initialLoad_populatesLoadedState_withTheRealCertificateId() = runTest(testDispatcher) {
        var recordedId: String? = null
        val viewModel = buildViewModel(
            certificateId = "MTR-1111-2222-3333-4444-5555-6666",
            getCertificate = {
                recordedId = it
                ApiResult.Success(certificateDetail(it))
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("MTR-1111-2222-3333-4444-5555-6666", recordedId)
        val loaded = viewModel.uiState.value as CertificateDetailUiState.Loaded
        assertEquals("MTR-1111-2222-3333-4444-5555-6666", loaded.certificate.id)
    }

    @Test
    fun notFound_becomesAGenuineErrorState_neverAFoldedEmptyState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            getCertificate = { ApiResult.Failure(ApiErrorCode.CertificateNotFound, "not found", null, 404) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value as CertificateDetailUiState.Error
        assertEquals(ApiErrorCode.CertificateNotFound, error.code)
    }

    @Test
    fun onRetry_reloadsFromScratch() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            getCertificate = {
                callCount++
                if (callCount == 1) {
                    ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
                } else {
                    ApiResult.Success(certificateDetail(it))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CertificateDetailUiState.Error)

        viewModel.onRetry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CertificateDetailUiState.Loaded)
        assertEquals(2, callCount)
    }
}
