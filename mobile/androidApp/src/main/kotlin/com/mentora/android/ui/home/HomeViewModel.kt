package com.mentora.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.android.domain.mylearning.GetMyLearningWithProgressUseCase
import com.mentora.android.domain.mylearning.LearningItemWithProgress
import com.mentora.android.domain.mylearning.MyLearningLoadState
import com.mentora.android.viewmodel.reloadOnLocaleChange
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CourseFilters
import com.mentora.shared.domain.model.CourseSummary
import com.mentora.shared.settings.AppLocale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime

/** `mobile-home.json` line 2041's exact time-of-day split ("Good morning" eyebrow, name on its own
 *  line below) — same 4-bucket boundaries as web's `DashboardScreen.greetingBucket`
 *  (`web/src/components/screens/dashboard-screen.tsx`), computed from the CLIENT's local time, never
 *  server time, per that function's own acceptance criteria (D54/D55). */
enum class GreetingBucket { Morning, Afternoon, Evening, Night }

fun greetingBucketFor(hour: Int): GreetingBucket = when {
    hour in 5..11 -> GreetingBucket.Morning
    hour in 12..16 -> GreetingBucket.Afternoon
    hour in 17..20 -> GreetingBucket.Evening
    else -> GreetingBucket.Night
}

/** The Recommended module's own independent load state — mirrors `ExploreViewModel`'s
 *  categories-vs-courses independence (a Recommended failure must never block Continue
 *  Learning/stats from rendering, and vice versa). */
sealed interface RecommendedState {
    data object Loading : RecommendedState
    data class Loaded(val items: List<CourseSummary>) : RecommendedState
    data object Empty : RecommendedState
    data class Error(val code: ApiErrorCode) : RecommendedState
}

data class HomeUiState(
    val firstName: String,
    val greetingBucket: GreetingBucket,
    val myLearning: MyLearningLoadState = MyLearningLoadState.Loading,
    val recommended: RecommendedState = RecommendedState.Loading,
)

/**
 * T12 — Home's ViewModel. Same lambda-constructor seam as every prior task's ViewModel
 * (`EnrollmentFacade`/`ProgressFacade`/`CatalogFacade`'s use-case properties are the real,
 * directly-injectable surface — see `ExploreViewModel`'s own kdoc for why this is lambdas, not a
 * `MentoraSdk` directly).
 *
 * **The G3 join** happens via [getMyLearningWithProgress] — see
 * [com.mentora.android.domain.mylearning.GetMyLearningWithProgressUseCase]'s own kdoc for the exact
 * round-trip shape. [Factory] below is the ONE place that use case is actually constructed for
 * production.
 *
 * **Continue Learning target selection (disclosed gap).** Rank-1 (`ux/SCREEN_UX_SPECS.md § 8`) calls
 * for "the most recently active in-progress course," but neither [com.mentora.shared.domain.model.Enrollment]
 * nor [com.mentora.shared.domain.model.CourseProgress] carries a last-accessed timestamp anywhere in
 * `shared` — the same disclosed gap web's own `DashboardScreen.continueItems` already accepts
 * (`inProgress.slice(0, 3)`, preserving `GET /enrollments`' own return order, not a true recency
 * sort). `GET /enrollments` sorts ascending by `_id` (`EnrollmentRepository.kt`, backend), so the
 * FIRST in-progress item in that order is the OLDEST enrollment — most likely an abandoned one, the
 * worst available proxy for "recently active." [HomeScreen] instead picks the LAST in-progress item
 * (most recently enrolled, still unfinished) — a strictly better proxy given the same underlying gap.
 *
 * **Recommended excludes already-enrolled courses** — mirrors web's `DashboardScreen.recommended`
 * filter exactly (`enrolledIds`) — computed from whatever [myLearning] state the join most recently
 * resolved to (an empty set if that join is still loading/failed, so Recommended never blocks on it).
 */
class HomeViewModel(
    userName: String,
    private val getMyLearningWithProgress: suspend () -> ApiResult<List<LearningItemWithProgress>>,
    private val searchCourses: suspend (CourseFilters, String?) -> ApiResult<CursorPage<CourseSummary>>,
    val resolveThumbnailUrl: (String) -> String,
    currentHour: () -> Int = { LocalTime.now().hour },
    /** T19 — see `com.mentora.android.viewmodel.reloadOnLocaleChange`'s own kdoc. */
    private val observeLocale: () -> StateFlow<AppLocale> = { MutableStateFlow(AppLocale.English) },
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        HomeUiState(
            firstName = userName.trim().substringBefore(' '),
            greetingBucket = greetingBucketFor(currentHour()),
        ),
    )
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadMyLearningThenRecommended()
        // Audited — see D94: `loadMyLearningThenRecommended` transitively hits `getCourseDetails`
        // (via `getMyLearningWithProgress`) AND directly hits `searchCourses` for Recommended, both
        // locale-sensitive — nothing here to trim.
        viewModelScope.reloadOnLocaleChange(observeLocale) { loadMyLearningThenRecommended() }
    }

    /**
     * `userName` at construction time is often `""` — a cold start with a stored session composes
     * Home with `Authenticated(user = null)` (`SessionManager.restoreSession`'s own documented gap)
     * before `AppSessionViewModel`'s follow-up `getProfile()` resolves the real name. Since this
     * `ViewModel` instance survives that later recomposition (the `viewModel(factory=...)` call only
     * consults the factory on first creation), [HomeScreen] calls this from a `LaunchedEffect(userName)`
     * so the greeting/[Avatar] update once the real name arrives, instead of staying blank for the
     * life of this back-stack entry.
     */
    fun onUserNameChanged(userName: String) {
        val firstName = userName.trim().substringBefore(' ')
        if (firstName.isNotEmpty() && firstName != _uiState.value.firstName) {
            _uiState.update { it.copy(firstName = firstName) }
        }
    }

    fun onRetryMyLearning() = loadMyLearningThenRecommended()

    fun onRetryRecommended() = loadRecommended(enrolledCourseIds())

    private fun enrolledCourseIds(): Set<String> =
        (_uiState.value.myLearning as? MyLearningLoadState.Loaded)?.items?.map { it.course.id }?.toSet().orEmpty()

    private fun loadMyLearningThenRecommended() {
        _uiState.update { it.copy(myLearning = MyLearningLoadState.Loading) }
        viewModelScope.launch {
            val result = getMyLearningWithProgress()
            _uiState.update {
                it.copy(
                    myLearning = when (result) {
                        is ApiResult.Success -> MyLearningLoadState.Loaded(result.data)
                        is ApiResult.Failure -> MyLearningLoadState.Error(result.code)
                    },
                )
            }
            val enrolledIds = when (result) {
                is ApiResult.Success -> result.data.map { it.course.id }.toSet()
                is ApiResult.Failure -> emptySet()
            }
            loadRecommended(enrolledIds)
        }
    }

    private fun loadRecommended(enrolledIds: Set<String>) {
        _uiState.update { it.copy(recommended = RecommendedState.Loading) }
        viewModelScope.launch {
            when (val result = searchCourses(CourseFilters(), null)) {
                is ApiResult.Success -> {
                    val items = result.data.items.filter { it.id !in enrolledIds }.take(RecommendedLimit)
                    _uiState.update {
                        it.copy(recommended = if (items.isEmpty()) RecommendedState.Empty else RecommendedState.Loaded(items))
                    }
                }
                is ApiResult.Failure -> _uiState.update { it.copy(recommended = RecommendedState.Error(result.code)) }
            }
        }
    }

    companion object {
        /** Rank-1's own "short row (3-4)" (`ux/SCREEN_UX_SPECS.md § 8` module 2). */
        const val RecommendedLimit: Int = 4
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `ExploreViewModel.Factory`'s exact idiom.
     *  [GetMyLearningWithProgressUseCase] is constructed here (the one production call site) and
     *  bound down to a plain lambda, per this class's own constructor seam. */
    class Factory(private val sdk: MentoraSdk, private val userName: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(
            userName = userName,
            getMyLearningWithProgress = GetMyLearningWithProgressUseCase(
                getMyLearning = sdk.enrollment.getMyLearning::invoke,
                getCourseProgress = sdk.progress.getCourseProgress::invoke,
            )::invoke,
            searchCourses = sdk.catalog.searchCourses::invoke,
            resolveThumbnailUrl = sdk.media.resolveThumbnailUrl::invoke,
            observeLocale = sdk.user.observeLocale::invoke,
        ) as T
    }
}
