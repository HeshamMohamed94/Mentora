package com.mentora.android.ui.checkout

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.Course
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PurchaseSuccessUiState(
    /** `null` until the course-title fetch below resolves. Never an error state of its own — see
     *  [PurchaseSuccessViewModel]'s own kdoc on why a failed fetch here falls back to a generic
     *  placeholder rather than blocking this celebratory, chromeless screen. */
    val courseTitle: String? = null,
)

/**
 * T11 — Purchase Success's ViewModel. This screen is reached as a FRESH push
 * (`MentoraNavHost.navigateToPurchaseSuccess`'s two-step, full-stack-reset mechanism) carrying only
 * [courseId] as its nav argument — [com.mentora.shared.domain.model.EnrollmentCompletion] itself is
 * never threaded through nav args, so the real, enrolled course's title (needed for this screen's
 * personalized "{Course title} is in My Learning..." copy) has to be fetched here independently, via
 * the same `sdk.catalog.getCourseDetails` call `CourseDetailsScreen` already made moments earlier —
 * an accepted N+1-at-demo-scale duplication, same disclosed shape as G3/G5
 * (`execution/PHASE_4_ANDROID_PLAN.md § 6`), not a new pattern.
 *
 * **Never blocks the celebratory moment on this fetch failing.** Unlike `CourseDetailsViewModel`'s
 * course fetch (a genuine [com.mentora.shared.domain.model.CourseLoadState.Error] state,
 * whole-screen), a failure here — a network hiccup, an unpublished/deleted course between checkout
 * completing and this screen mounting — simply leaves [PurchaseSuccessUiState.courseTitle] `null`;
 * `PurchaseSuccessScreen.kt` falls back to a generic, un-personalized description in that case. This
 * screen's own spec documents no error state at all (`design-to-code/screens/mobile-purchase-success.json`'s
 * `states` field only names "entrance") — the enrollment itself already succeeded server-side by the
 * time this screen exists, so there is nothing here actually worth failing the screen over.
 */
class PurchaseSuccessViewModel(
    courseId: String,
    private val getCourseDetails: suspend (String) -> ApiResult<Course>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseSuccessUiState())
    val uiState: StateFlow<PurchaseSuccessUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            when (val result = getCourseDetails(courseId)) {
                is ApiResult.Success -> _uiState.update { it.copy(courseTitle = result.data.title) }
                is ApiResult.Failure -> Unit // Fails safe to the generic fallback — see this class's own kdoc.
            }
        }
    }

    class Factory(
        private val sdk: MentoraSdk,
        private val courseId: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PurchaseSuccessViewModel(
            courseId = courseId,
            getCourseDetails = sdk.catalog.getCourseDetails::invoke,
        ) as T
    }
}
