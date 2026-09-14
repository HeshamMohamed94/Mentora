package com.mentora.android.ui.learningpathdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.android.viewmodel.reloadOnLocaleChange
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.LearningPathCourse
import com.mentora.shared.domain.model.LearningPathDetail
import com.mentora.shared.settings.AppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * T16 — `ux/SCREEN_UX_SPECS.md § 5`'s "Completed / Current / Upcoming" per-course sequence status.
 * [Upcoming] deliberately never carries a badge/icon in [LearningPathDetailsScreen] (that spec's own
 * "no special badge, default treatment" line for this one state) — only [Completed]/[Current] do; all
 * three still render a plain status TEXT label regardless (this design system's global "never color
 * alone" accessibility rule, satisfied here by text alone even for the un-badged state).
 */
enum class CourseSequenceStatus { Completed, Current, Upcoming }

/** One member course row's fully-derived display state — see [LearningPathDetailsViewModel]'s own
 *  kdoc for exactly how [status]/[isEnrolled]/[completionPercent] are derived from a per-course
 *  [com.mentora.shared.domain.model.CourseProgress] fetch (or skipped entirely for a guest). */
data class LearningPathMemberCourseUi(
    val course: LearningPathCourse,
    val status: CourseSequenceStatus,
    val isEnrolled: Boolean,
    /** `0..100`, `null` only when [isEnrolled] is `false` (never enrolled, so no progress exists to
     *  read — mirrors [CourseProgress.completionPercent]'s own "a non-enrolled caller never receives
     *  this shape at all" contract). */
    val completionPercent: Int?,
)

/** The path-fetch half of [LearningPathDetailsUiState] — mirrors `CourseDetailsUiState`/
 *  `CourseLoadState`'s exact split-by-load-state shape. */
sealed interface LearningPathLoadState {
    data object Loading : LearningPathLoadState

    /**
     * [followInFlight]/[followError] are carried INSIDE this state (not a sibling top-level field) so
     * a follow/unfollow round trip can never be represented while the path itself hasn't loaded yet —
     * the same "sticky, dismissible error co-located with its own content state" shape
     * `CoursePlayerReadyState.completionError`/`isCompletionInFlight` already established
     * (`CoursePlayerViewModel.kt`), reused here for the identical "a secondary mutating action on an
     * already-successfully-loaded screen can fail independently of the main content" situation.
     */
    data class Success(
        val title: String,
        val description: String,
        /** `null` for a guest, `0..100` for an authenticated student — mirrors
         *  [LearningPathDetail.progressPercent]'s own contract verbatim, never recomputed locally. */
        val progressPercent: Int?,
        val isFollowing: Boolean,
        val courses: List<LearningPathMemberCourseUi>,
        val followInFlight: Boolean = false,
        val followError: ApiErrorCode? = null,
    ) : LearningPathLoadState

    data class Error(val code: ApiErrorCode) : LearningPathLoadState
}

data class LearningPathDetailsUiState(
    val path: LearningPathLoadState = LearningPathLoadState.Loading,
)

/**
 * T16 — Learning Path Details' ViewModel. Same lambda-constructor seam as every prior task's
 * ViewModel (`sdk.learningPaths`/`sdk.progress`'s constructors are `internal` to `:shared`).
 *
 * **[isAuthenticated] is a plain snapshot at construction**, same rationale as
 * `CourseDetailsViewModel`'s own identical parameter (see that class's kdoc) — every full auth-state
 * TRANSITION already resets the entire nav stack in `MentoraNavHost`, so this screen never stays
 * mounted across one.
 *
 * **Deriving Completed/Current/Upcoming (`ux/SCREEN_UX_SPECS.md § 5`, the task's own pre-made design
 * decision).** [getCourseProgress] is called once per course in [LearningPathDetail.courses] — small
 * N, the same N+1-is-fine-at-seed-scale precedent `MyLearningViewModel`'s own Learning-Paths join and
 * `GetMyLearningWithProgressUseCase`'s own G5 join already established; no batch endpoint, no caching
 * layer. [ApiResult.Success] means enrolled ([CourseProgress.completionPercent] tells completion);
 * [ApiResult.Failure] (whether [ApiErrorCode.ForbiddenNotEnrolled] specifically, or any other
 * transient failure) means "treat as not enrolled" — a best-effort fallback, same fail-safe
 * philosophy as `CourseDetailsViewModel.isEnrolledIn`'s own kdoc, never a hard error for a per-course
 * check this screen didn't ask to see directly. A GUEST never calls [getCourseProgress] at all (the
 * endpoint is hard-authenticated server-side — this would only ever fail) — every course is instead
 * treated as not-enrolled/not-completed, which correctly still walks the SAME "first not-yet-completed
 * course is Current" rule below to land Current on course #1, exactly matching what a guest should see.
 *
 * The ordered walk: the first course that is NOT completed is [CourseSequenceStatus.Current];
 * everything strictly before it is [CourseSequenceStatus.Completed]; everything after is
 * [CourseSequenceStatus.Upcoming]. A path where every course is completed has no Current course —
 * [List.indexOfFirst] returning `-1` in that case falls out of the same single `when` naturally
 * (every index is `< currentIndex`'s complement is never true, so every course resolves to
 * [CourseSequenceStatus.Completed] instead — handled, not special-cased).
 */
class LearningPathDetailsViewModel(
    private val pathId: String,
    private val isAuthenticated: Boolean,
    private val getLearningPathDetail: suspend (String) -> ApiResult<LearningPathDetail>,
    private val followLearningPath: suspend (String) -> ApiResult<Boolean>,
    private val unfollowLearningPath: suspend (String) -> ApiResult<Boolean>,
    private val getCourseProgress: suspend (String) -> ApiResult<CourseProgress>,
    val resolveThumbnailUrl: (String) -> String,
    /** T19 — see `com.mentora.android.viewmodel.reloadOnLocaleChange`'s own kdoc. */
    private val observeLocale: () -> StateFlow<AppLocale> = { MutableStateFlow(AppLocale.English) },
) : ViewModel() {

    private val _uiState = MutableStateFlow(LearningPathDetailsUiState())
    val uiState: StateFlow<LearningPathDetailsUiState> = _uiState.asStateFlow()

    init {
        loadPath()
        // T19 — self-review fix: reloading unconditionally here would silently drop the result of an
        // in-flight follow/unfollow call. `loadPath()` resets `path` straight to `Loading`; when that
        // call's response then arrives, `updateSuccess`'s `as? LearningPathLoadState.Success` cast on
        // the now-`Loading` state fails and the update is dropped as a no-op — the tap the student
        // just made would appear to have done nothing. Narrow window (this destination is a PUSH, not
        // a tab root, but its `NavBackStackEntry`/ViewModelStore stays alive while merely covered by a
        // later push — e.g. Settings pushed on top without popping this screen — so a locale change
        // made there can still reach this instance while a toggle it started is still resolving).
        // Skipping the reload entirely for that one window is enough: `onFollowToggleClicked`'s own
        // completion re-renders correctly regardless of which locale's copy briefly showed, and the
        // student's next real re-entry/retry naturally reloads in the new locale anyway.
        viewModelScope.reloadOnLocaleChange(observeLocale) {
            val current = _uiState.value.path as? LearningPathLoadState.Success
            if (current?.followInFlight != true) loadPath()
        }
    }

    fun onRetry() = loadPath()

    /** In-flight guard — a double-tap while a follow/unfollow call is already outstanding is a no-op,
     *  never a second concurrent call. [isAuthenticated] gates whether this is even reachable: the
     *  Screen only wires this to the button's `onClick` when authenticated, routing a guest tap
     *  through its own `onFollowRequiringAuth` callback instead (mirrors `CourseDetailsScreen`'s
     *  identical `onEnrollRequiringAuth` gate for its CTA). */
    fun onFollowToggleClicked() {
        val current = _uiState.value.path as? LearningPathLoadState.Success ?: return
        if (current.followInFlight) return
        val goingToFollow = !current.isFollowing

        updateSuccess { it.copy(followInFlight = true, followError = null) }
        viewModelScope.launch {
            val result = if (goingToFollow) followLearningPath(pathId) else unfollowLearningPath(pathId)
            when (result) {
                is ApiResult.Success -> updateSuccess {
                    it.copy(isFollowing = result.data, followInFlight = false)
                }
                is ApiResult.Failure -> updateSuccess {
                    it.copy(followInFlight = false, followError = result.code)
                }
            }
        }
    }

    /** Dismiss handle for [LearningPathLoadState.Success.followError] — mirrors
     *  `CoursePlayerViewModel.onCompletionErrorDismissed`'s exact shape (the Screen fires this right
     *  after handing the error off to a Snackbar, so it never re-shows on the next recomposition). */
    fun onFollowErrorDismissed() = updateSuccess { it.copy(followError = null) }

    private inline fun updateSuccess(transform: (LearningPathLoadState.Success) -> LearningPathLoadState.Success) {
        _uiState.update { state ->
            val success = state.path as? LearningPathLoadState.Success ?: return@update state
            state.copy(path = transform(success))
        }
    }

    private fun loadPath() {
        _uiState.update { it.copy(path = LearningPathLoadState.Loading) }
        viewModelScope.launch {
            when (val result = getLearningPathDetail(pathId)) {
                is ApiResult.Success -> {
                    val detail = result.data
                    val courses = deriveCourseStatuses(detail.courses)
                    _uiState.update {
                        it.copy(
                            path = LearningPathLoadState.Success(
                                title = detail.title,
                                description = detail.description,
                                progressPercent = detail.progressPercent,
                                isFollowing = detail.isFollowing,
                                courses = courses,
                            ),
                        )
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(path = LearningPathLoadState.Error(result.code)) }
            }
        }
    }

    private suspend fun deriveCourseStatuses(courses: List<LearningPathCourse>): List<LearningPathMemberCourseUi> {
        data class Resolved(val course: LearningPathCourse, val isEnrolled: Boolean, val completionPercent: Int?, val completed: Boolean)

        val resolved = courses.map { course ->
            if (!isAuthenticated) {
                Resolved(course, isEnrolled = false, completionPercent = null, completed = false)
            } else {
                when (val progress = getCourseProgress(course.id)) {
                    // Round-1 review finding (MEDIUM): `completionPercent >= 100` is LESSON-count-only
                    // (`ProgressService.complete`) and can disagree with what "this course is done"
                    // actually means for course-completion purposes — a course with a quiz counts as
                    // complete only once `courseCompletedAt` is set (`courseCompletedAt != null`,
                    // `CertificateService.checkAndIssueIfComplete`: all lessons done AND (no quiz OR
                    // quiz passed)). The path-level `progressPercent` this screen's own hero progress
                    // bar renders is server-computed from the SAME `courseCompletedAt` definition
                    // (`LearningPathService.kt`) — using `completionPercent >= 100` here let a
                    // lessons-done-but-quiz-not-passed course render "Completed" with a Completed badge
                    // while the bar directly above it (correctly) showed the path as not yet
                    // progressed on that course, an internally-contradictory screen. `completed` now
                    // uses the identical `courseCompletedAt != null` definition the server itself uses
                    // for `progressPercent`, so the badge derivation and the progress bar can never
                    // disagree about what "this course is done" means.
                    is ApiResult.Success -> Resolved(
                        course = course,
                        isEnrolled = true,
                        completionPercent = progress.data.completionPercent,
                        completed = progress.data.courseCompletedAt != null,
                    )
                    // ForbiddenNotEnrolled (the expected case) or any other transient failure — both
                    // fall back to "not enrolled", per this class's own kdoc.
                    is ApiResult.Failure -> Resolved(course, isEnrolled = false, completionPercent = null, completed = false)
                }
            }
        }

        val currentIndex = resolved.indexOfFirst { !it.completed }
        return resolved.mapIndexed { index, r ->
            val status = when {
                currentIndex == -1 -> CourseSequenceStatus.Completed
                index < currentIndex -> CourseSequenceStatus.Completed
                index == currentIndex -> CourseSequenceStatus.Current
                else -> CourseSequenceStatus.Upcoming
            }
            LearningPathMemberCourseUi(r.course, status, r.isEnrolled, r.completionPercent)
        }
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `CourseDetailsViewModel.Factory`'s exact idiom. */
    class Factory(
        private val sdk: MentoraSdk,
        private val pathId: String,
        private val isAuthenticated: Boolean,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = LearningPathDetailsViewModel(
            pathId = pathId,
            isAuthenticated = isAuthenticated,
            getLearningPathDetail = sdk.learningPaths.getLearningPathDetail::invoke,
            followLearningPath = sdk.learningPaths.followLearningPath::invoke,
            unfollowLearningPath = sdk.learningPaths.unfollowLearningPath::invoke,
            getCourseProgress = sdk.progress.getCourseProgress::invoke,
            resolveThumbnailUrl = sdk.media.resolveThumbnailUrl::invoke,
            observeLocale = sdk.user.observeLocale::invoke,
        ) as T
    }
}
