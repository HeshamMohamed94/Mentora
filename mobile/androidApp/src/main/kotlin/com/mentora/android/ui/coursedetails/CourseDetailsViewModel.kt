package com.mentora.android.ui.coursedetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.Enrollment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * T10 — which primary CTA the sticky footer renders, per `ux/SCREEN_UX_SPECS.md § 3`'s 3 states
 * (guest / authenticated-not-enrolled / authenticated-enrolled). [LoginToEnroll] and [Enroll] are
 * deliberately wired to the SAME click callback at the call site
 * ([com.mentora.android.ui.coursedetails.CourseDetailsScreen]'s `onEnrollRequiringAuth`) —
 * `navigation/AuthGate.kt`'s `decideAuthGate` already branches on the live `AuthState` to either gate
 * to Login or navigate straight to Demo Checkout, so only the button's LABEL differs between these
 * two states, never the destination or the wiring.
 */
enum class CourseDetailsCtaState { LoginToEnroll, Enroll, ContinueLearning }

/** The course-fetch half of [CourseDetailsUiState] — kept separate from [CourseDetailsUiState.categories]
 *  since a categories failure must never block the course itself from rendering (mirrors
 *  `ExploreViewModel`'s own categories-vs-courses independence). */
sealed interface CourseLoadState {
    data object Loading : CourseLoadState

    /** [thumbnailUrl] is `null` when [Course.thumbnailMediaId] itself is `null` — [CourseThumbnail]
     *  already falls back to the deterministic artwork system in that case, no special-casing needed
     *  here beyond passing `null` through. */
    data class Success(
        val course: Course,
        val thumbnailUrl: String?,
        val isEnrolled: Boolean,
        val cta: CourseDetailsCtaState,
    ) : CourseLoadState

    data class Error(val code: ApiErrorCode) : CourseLoadState
}

data class CourseDetailsUiState(
    val course: CourseLoadState = CourseLoadState.Loading,
    /** Best-effort category-name resolution for the category chip (same non-blocking pattern as
     *  `ExploreViewModel`'s own `categories` load) — a failure here just leaves this list empty, and
     *  the chip falls back to the raw `categoryId` (see `CourseDetailsScreen.kt`). */
    val categories: List<Category> = emptyList(),
)

/** A generously high per-page size for the enrollment-membership check below — see
 *  [CourseDetailsViewModel]'s own kdoc on G4. */
const val CourseDetailsEnrollmentPageLimit: Int = 100

/**
 * T10 — Course Details' ViewModel. Deliberately takes plain suspend-lambdas rather than a
 * `MentoraSdk` directly, mirroring `ExploreViewModel`'s own seam (`CatalogFacade`/`EnrollmentFacade`/
 * `MediaFacade`'s constructors are `internal`, so `:androidApp` cannot build a fake one for a JVM unit
 * test otherwise). [Factory] wires the lambdas to the real `sdk.catalog`/`sdk.enrollment`/`sdk.media`
 * use cases in production.
 *
 * [courseId]/[isAuthenticated] are both captured once at construction. [isAuthenticated] is a plain
 * snapshot of the live `AuthState` at the moment
 * [MentoraNavHost][com.mentora.android.navigation.MentoraNavHost] pushed this destination — correct
 * because every full auth-state TRANSITION (login/logout) already resets the entire nav stack in
 * `MentoraNavHost` (see that file's kdoc), so this screen never stays mounted across one; there is no
 * "guest opened this screen, then logged in without leaving it" case to handle.
 *
 * **G4 — deriving `isEnrolled`, since neither [Course] nor `CourseSummary` carry that field (never
 * implemented anywhere in the backend).** [isEnrolledIn] pages FULLY through `GET /api/v1/enrollments`
 * — following every `nextCursor`, never just the first page — checking each page's
 * [Enrollment.courseId] against this course's id, with a generously high per-page
 * [CourseDetailsEnrollmentPageLimit] so that at current seed-data scale (a handful of enrollments per
 * demo account) this resolves in a single request in practice, while staying correct if a real
 * account ever has more. A guest ([isAuthenticated] `false`) never calls this at all — a guest has no
 * enrollments to check, per the task brief.
 */
class CourseDetailsViewModel(
    private val courseId: String,
    private val isAuthenticated: Boolean,
    private val getCourseDetails: suspend (String) -> ApiResult<Course>,
    private val listEnrollments: suspend (String?, Int?) -> ApiResult<CursorPage<Enrollment>>,
    private val listCategories: suspend () -> ApiResult<List<Category>>,
    private val resolveThumbnailUrl: (String) -> String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CourseDetailsUiState())
    val uiState: StateFlow<CourseDetailsUiState> = _uiState.asStateFlow()

    init {
        loadCourse()
        loadCategories()
    }

    fun onRetry() = loadCourse()

    private fun loadCourse() {
        _uiState.update { it.copy(course = CourseLoadState.Loading) }
        viewModelScope.launch {
            when (val result = getCourseDetails(courseId)) {
                is ApiResult.Success -> {
                    val course = result.data
                    val isEnrolled = if (isAuthenticated) isEnrolledIn(course.id) else false
                    val cta = when {
                        !isAuthenticated -> CourseDetailsCtaState.LoginToEnroll
                        isEnrolled -> CourseDetailsCtaState.ContinueLearning
                        else -> CourseDetailsCtaState.Enroll
                    }
                    val thumbnailUrl = course.thumbnailMediaId?.let(resolveThumbnailUrl)
                    _uiState.update {
                        it.copy(course = CourseLoadState.Success(course, thumbnailUrl, isEnrolled, cta))
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(course = CourseLoadState.Error(result.code)) }
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            when (val result = listCategories()) {
                is ApiResult.Success -> _uiState.update { it.copy(categories = result.data) }
                // Best-effort only, per this class's own kdoc — never surfaced as its own error state.
                is ApiResult.Failure -> Unit
            }
        }
    }

    /** A failed page (a network hiccup mid-pagination, or a guest-shaped/expired session hitting the
     *  auth-required enrollments endpoint) resolves to "not enrolled" rather than blocking the whole
     *  course details load — the CTA simply falls back to "Enroll" in that edge case, never a hard
     *  error for a check the caller didn't ask to see directly. */
    private suspend fun isEnrolledIn(targetCourseId: String): Boolean {
        var cursor: String? = null
        do {
            when (val page = listEnrollments(cursor, CourseDetailsEnrollmentPageLimit)) {
                is ApiResult.Success -> {
                    if (page.data.items.any { it.courseId == targetCourseId }) return true
                    cursor = page.data.nextCursor
                }
                is ApiResult.Failure -> return false
            }
        } while (cursor != null)
        return false
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `ExploreViewModel.Factory`'s exact idiom. */
    class Factory(
        private val sdk: MentoraSdk,
        private val courseId: String,
        private val isAuthenticated: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CourseDetailsViewModel(
            courseId = courseId,
            isAuthenticated = isAuthenticated,
            getCourseDetails = sdk.catalog.getCourseDetails::invoke,
            listEnrollments = sdk.enrollment.listEnrollments::invoke,
            listCategories = sdk.catalog.listCategories::invoke,
            resolveThumbnailUrl = sdk.media.resolveThumbnailUrl::invoke,
        ) as T
    }
}
