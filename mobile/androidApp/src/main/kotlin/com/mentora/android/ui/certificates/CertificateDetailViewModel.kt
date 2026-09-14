package com.mentora.android.ui.certificates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.CertificateDetail
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface CertificateDetailUiState {
    data object Loading : CertificateDetailUiState
    data class Loaded(val certificate: CertificateDetail) : CertificateDetailUiState

    /** Unlike Quiz's `NoQuiz`/`NoAttemptYet` pattern, this is a GENUINE error state, never a
     *  legitimate-empty-state — see [com.mentora.shared.domain.usecase.certificate.GetCertificateUseCase]'s
     *  own kdoc: a certificate id belonging to another student (or any nonexistent id) is forwarded
     *  unchanged as an ordinary [ApiResult.Failure], not folded into a fake success. */
    data class Error(val code: ApiErrorCode) : CertificateDetailUiState
}

/**
 * T15 — Certificate Detail's ViewModel. Same lambda-constructor seam as `CourseDetailsViewModel`'s own
 * (single-id) [Factory] idiom — [certificateId] is captured once at construction, exactly the same
 * pattern as `CourseDetailsViewModel.courseId`.
 *
 * **T19 — deliberately excluded from the phase's locale-reload sweep** — see
 * [CertificatesViewModel]'s own kdoc for why (this read never carries `?language=` either; a
 * locale-triggered reload here would be a real, extra network call with zero visible effect).
 */
class CertificateDetailViewModel(
    private val certificateId: String,
    private val getCertificate: suspend (String) -> ApiResult<CertificateDetail>,
) : ViewModel() {

    private val _uiState = MutableStateFlow<CertificateDetailUiState>(CertificateDetailUiState.Loading)
    val uiState: StateFlow<CertificateDetailUiState> = _uiState.asStateFlow()

    init {
        loadCertificate()
    }

    fun onRetry() = loadCertificate()

    private fun loadCertificate() {
        _uiState.update { CertificateDetailUiState.Loading }
        viewModelScope.launch {
            when (val result = getCertificate(certificateId)) {
                is ApiResult.Success -> _uiState.update { CertificateDetailUiState.Loaded(result.data) }
                is ApiResult.Failure -> _uiState.update { CertificateDetailUiState.Error(result.code) }
            }
        }
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `CourseDetailsViewModel.Factory`'s exact
     *  single-id idiom. */
    class Factory(
        private val sdk: MentoraSdk,
        private val certificateId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CertificateDetailViewModel(
            certificateId = certificateId,
            getCertificate = sdk.certificates.getCertificate::invoke,
        ) as T
    }
}
