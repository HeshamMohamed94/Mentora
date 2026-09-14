package com.mentora.android.ui.certificates

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.CertificateSummary
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

private fun certificate(id: String) = CertificateSummary(
    id = id,
    courseTitleSnapshot = "Course for $id",
    instructorNameSnapshot = "Instructor",
    issuedAt = "2026-01-12T10:30:00Z",
)

/**
 * T15 — [CertificatesViewModel]'s cursor-pagination/state logic, as a plain JVM unit test. Wires the
 * ViewModel's constructor lambda (see that class's own kdoc for why it's a lambda, not a `MentoraSdk`)
 * to hand-built fakes rather than any real network — mirrors `ExploreViewModel`'s own equivalent
 * pagination test shape (`ExploreViewModelTest.loadMoreCourses_*`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CertificatesViewModelTest {

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
        listCertificates: suspend (String?, Int?) -> ApiResult<CursorPage<CertificateSummary>> =
            { _, _ -> ApiResult.Success(CursorPage(items = listOf(certificate("c1")), nextCursor = null)) },
    ) = CertificatesViewModel(listCertificates = listCertificates)

    @Test
    fun initialLoad_populatesLoadedState() = runTest(testDispatcher) {
        val viewModel = buildViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        val loaded = viewModel.uiState.value as CertificatesUiState.Loaded
        assertEquals(listOf("c1"), loaded.items.map { it.id })
        assertFalse(loaded.isLoadingMore)
    }

    @Test
    fun emptyResult_becomesTheEmptyState() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            listCertificates = { _, _ -> ApiResult.Success(CursorPage(items = emptyList(), nextCursor = null)) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CertificatesUiState.Empty)
    }

    @Test
    fun loadFailure_becomesTheErrorState_withTheApiErrorCode() = runTest(testDispatcher) {
        val viewModel = buildViewModel(
            listCertificates = { _, _ -> ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500) },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        val error = viewModel.uiState.value as CertificatesUiState.Error
        assertEquals(ApiErrorCode.InternalError, error.code)
    }

    @Test
    fun onLoadMore_appendsToTheExistingPage_usingTheReturnedCursor() = runTest(testDispatcher) {
        var page = 0
        val viewModel = buildViewModel(
            listCertificates = { cursor, _ ->
                page++
                if (page == 1) {
                    ApiResult.Success(CursorPage(items = listOf(certificate("c1")), nextCursor = "cursor-2"))
                } else {
                    assertEquals("cursor-2", cursor)
                    ApiResult.Success(CursorPage(items = listOf(certificate("c2")), nextCursor = null))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onLoadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        val loaded = viewModel.uiState.value as CertificatesUiState.Loaded
        assertEquals(listOf("c1", "c2"), loaded.items.map { it.id })
        assertEquals(null, loaded.nextCursor)
        assertFalse(loaded.isLoadingMore)
    }

    @Test
    fun onLoadMore_onFailure_keepsExistingItems_justStopsTheSpinner() = runTest(testDispatcher) {
        var page = 0
        val viewModel = buildViewModel(
            listCertificates = { _, _ ->
                page++
                if (page == 1) {
                    ApiResult.Success(CursorPage(items = listOf(certificate("c1")), nextCursor = "cursor-2"))
                } else {
                    ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onLoadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        val loaded = viewModel.uiState.value as CertificatesUiState.Loaded
        assertEquals(listOf("c1"), loaded.items.map { it.id })
        assertFalse(loaded.isLoadingMore)
    }

    @Test
    fun onLoadMore_noOpWhenThereIsNoNextCursor() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            listCertificates = { _, _ ->
                callCount++
                ApiResult.Success(CursorPage(items = listOf(certificate("c1")), nextCursor = null))
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, callCount)

        viewModel.onLoadMore()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, callCount)
    }

    @Test
    fun onRetry_reloadsFromScratch() = runTest(testDispatcher) {
        var callCount = 0
        val viewModel = buildViewModel(
            listCertificates = { _, _ ->
                callCount++
                if (callCount == 1) {
                    ApiResult.Failure(ApiErrorCode.InternalError, "boom", null, 500)
                } else {
                    ApiResult.Success(CursorPage(items = listOf(certificate("c1")), nextCursor = null))
                }
            },
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CertificatesUiState.Error)

        viewModel.onRetry()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is CertificatesUiState.Loaded)
        assertEquals(2, callCount)
    }
}
