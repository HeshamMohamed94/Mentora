package com.mentora.android.ui.certificates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.CertificateSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** [isLoadingMore] mirrors `ExploreViewModel`'s own `CoursesUiState.Loaded` shape exactly — the same
 *  cursor-append pagination pattern (a failed "load more" only clears this flag, never discards
 *  [items] already on screen or flips the whole section to [Error]). */
sealed interface CertificatesUiState {
    data object Loading : CertificatesUiState
    data class Loaded(
        val items: List<CertificateSummary>,
        val nextCursor: String?,
        val isLoadingMore: Boolean = false,
    ) : CertificatesUiState
    data object Empty : CertificatesUiState
    data class Error(val code: ApiErrorCode) : CertificatesUiState
}

/**
 * T15 — Certificates List's ViewModel. Same lambda-constructor seam as every prior task's ViewModel
 * (`CertificateFacade`'s constructor is `internal`, so `:androidApp` cannot build a fake one for a JVM
 * unit test otherwise — mirrors `ExploreViewModel`/`MyLearningViewModel`'s own established reason).
 *
 * Cursor-paginated, following `ExploreViewModel.CoursesUiState`'s exact pattern (`onLoadMore` guards
 * on `nextCursor`/`isLoadingMore`, a failed page load never discards what's already on screen) — no new
 * pagination shape invented here.
 */
class CertificatesViewModel(
    private val listCertificates: suspend (String?, Int?) -> ApiResult<CursorPage<CertificateSummary>>,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CertificatesUiState>(CertificatesUiState.Loading)
    val uiState: StateFlow<CertificatesUiState> = _uiState.asStateFlow()

    init {
        loadCertificates()
    }

    fun onRetry() = loadCertificates()

    /** Cursor-based "load more" — a no-op if there is no next page, a page load isn't showing yet, or
     *  one is already in flight (same guard shape as `ExploreViewModel.onLoadMoreCourses`). */
    fun onLoadMore() {
        val current = _uiState.value
        if (current !is CertificatesUiState.Loaded || current.nextCursor == null || current.isLoadingMore) return
        _uiState.update { current.copy(isLoadingMore = true) }
        viewModelScope.launch {
            when (val result = listCertificates(current.nextCursor, null)) {
                is ApiResult.Success -> _uiState.update {
                    CertificatesUiState.Loaded(
                        items = current.items + result.data.items,
                        nextCursor = result.data.nextCursor,
                    )
                }
                is ApiResult.Failure -> _uiState.update { current.copy(isLoadingMore = false) }
            }
        }
    }

    private fun loadCertificates() {
        _uiState.update { CertificatesUiState.Loading }
        viewModelScope.launch {
            when (val result = listCertificates(null, null)) {
                is ApiResult.Success -> _uiState.update {
                    if (result.data.items.isEmpty()) {
                        CertificatesUiState.Empty
                    } else {
                        CertificatesUiState.Loaded(items = result.data.items, nextCursor = result.data.nextCursor)
                    }
                }
                is ApiResult.Failure -> _uiState.update { CertificatesUiState.Error(result.code) }
            }
        }
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `ExploreViewModel.Factory`'s exact idiom. */
    class Factory(private val sdk: MentoraSdk) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CertificatesViewModel(
            listCertificates = sdk.certificates.listCertificates::invoke,
        ) as T
    }
}
