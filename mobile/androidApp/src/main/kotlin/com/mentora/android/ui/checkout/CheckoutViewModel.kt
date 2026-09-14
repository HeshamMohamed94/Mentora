package com.mentora.android.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.CheckoutPreview
import com.mentora.shared.domain.model.EnrollmentCompletion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The preview-fetch half of [CheckoutUiState] — mirrors `CourseLoadState`'s own 3-state shape
 *  (`CourseDetailsViewModel.kt`), same rationale. */
sealed interface CheckoutLoadState {
    data object Loading : CheckoutLoadState

    /** [thumbnailUrl] is `null` when [CheckoutPreview.course]'s `thumbnailMediaId` is itself `null` —
     *  [com.mentora.android.ui.components.CourseThumbnail] already falls back to the deterministic
     *  artwork system in that case, same as `CourseDetailsViewModel`'s identical field. */
    data class Success(val preview: CheckoutPreview, val thumbnailUrl: String?) : CheckoutLoadState

    data class Error(val code: ApiErrorCode) : CheckoutLoadState
}

data class CheckoutUiState(
    val preview: CheckoutLoadState = CheckoutLoadState.Loading,
    /** Confirm button's own in-place Loading state (`ux/UX_STATES.md § 11`) — never a route change,
     *  never touched by [CheckoutLoadState]. */
    val isProcessing: Boolean = false,
    /** Set on a [ApiResult.Failure] from `completeDemoCheckout`, cleared at the start of the next
     *  attempt. Drives the inline "Demo checkout could not be completed. Try again." copy
     *  (`ux/UX_STATES.md § 11`). */
    val completionFailed: Boolean = false,
)

/**
 * T11 — Demo Checkout's ViewModel. Same lambda-constructor seam as `CourseDetailsViewModel`
 * (`EnrollmentFacade`/`MediaFacade`'s use-case properties are the real, directly-injectable surface —
 * see that class's own kdoc for why this is lambdas, not a `MentoraSdk` directly).
 *
 * **Lesson count — disclosed omission, not fetched.** [CheckoutPreview] carries no lesson count
 * (`GetCheckoutPreviewUseCase`'s own kdoc), and the only way to obtain one —
 * `sdk.catalog.getCourseCurriculum`/`getCourseDetails` — is, per [com.mentora.shared.domain.usecase.catalog.GetCourseCurriculumUseCase]'s
 * own kdoc, "the SAME fetch [GetCourseDetailsUseCase] performs, never a second network call" — i.e.
 * there is no lighter endpoint; obtaining a lesson count here would mean a second FULL
 * `GET /courses/{id}` fetch (duplicating the exact fetch Course Details, the screen the user was just
 * on, already made seconds earlier) purely to render one number. Web's own real Checkout screen
 * (`web/src/components/screens/checkout-screen.tsx`) already omits the lesson count from its line item
 * entirely for this same reason — this mirrors that existing, shipped precedent rather than diverging
 * from it. The line item below therefore renders `"{instructor}"` only, never `"{instructor} · {N}
 * lessons"`.
 */
class CheckoutViewModel(
    private val courseId: String,
    private val getCheckoutPreview: suspend (String) -> ApiResult<CheckoutPreview>,
    private val completeDemoCheckout: suspend (String) -> ApiResult<EnrollmentCompletion>,
    private val resolveThumbnailUrl: (String) -> String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CheckoutUiState())
    val uiState: StateFlow<CheckoutUiState> = _uiState.asStateFlow()

    init {
        loadPreview()
    }

    fun onRetryPreview() = loadPreview()

    private fun loadPreview() {
        _uiState.update { it.copy(preview = CheckoutLoadState.Loading) }
        viewModelScope.launch {
            when (val result = getCheckoutPreview(courseId)) {
                is ApiResult.Success -> {
                    val thumbnailUrl = result.data.course.thumbnailMediaId?.let(resolveThumbnailUrl)
                    _uiState.update { it.copy(preview = CheckoutLoadState.Success(result.data, thumbnailUrl)) }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(preview = CheckoutLoadState.Error(result.code)) }
            }
        }
    }

    /**
     * Calls `completeDemoCheckout` exactly ONCE per invocation — the [CheckoutUiState.isProcessing]
     * guard below only prevents a second concurrent call while one is already in flight (e.g. a
     * double-tap), it is never an automatic retry of a failed call; a genuine retry only ever happens
     * from a fresh, distinct user tap (the confirm button becoming re-tappable on failure — see
     * `DemoCheckoutScreen.kt`), per `CompleteDemoCheckoutUseCase`'s own kdoc/this task's explicit "no
     * client-side retry loop" instruction.
     */
    fun completePurchase(onSuccess: (EnrollmentCompletion) -> Unit) {
        if (_uiState.value.isProcessing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, completionFailed = false) }
            when (val result = completeDemoCheckout(courseId)) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isProcessing = false) }
                    onSuccess(result.data)
                }
                is ApiResult.Failure -> _uiState.update { it.copy(isProcessing = false, completionFailed = true) }
            }
        }
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `CourseDetailsViewModel.Factory`'s exact idiom. */
    class Factory(
        private val sdk: MentoraSdk,
        private val courseId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CheckoutViewModel(
            courseId = courseId,
            getCheckoutPreview = sdk.enrollment.getCheckoutPreview::invoke,
            completeDemoCheckout = sdk.enrollment.completeDemoCheckout::invoke,
            resolveThumbnailUrl = sdk.media.resolveThumbnailUrl::invoke,
        ) as T
    }
}
