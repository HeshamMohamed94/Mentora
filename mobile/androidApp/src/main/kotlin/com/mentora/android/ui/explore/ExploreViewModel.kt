package com.mentora.android.ui.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.catalog.CourseFilters
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.CourseSummary
import com.mentora.shared.domain.model.LearningPath
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** T9 — which half of the rank-1-required segment/tab (`ux/MOBILE_UX.md § 3`) is currently showing. */
enum class ExploreTab { Courses, LearningPaths }

/** Categories load independently of the course search itself (its own loading/error state, per the
 *  task brief) — a categories failure must never block the course list from rendering. */
sealed interface CategoriesUiState {
    data object Loading : CategoriesUiState
    data class Loaded(val categories: List<Category>) : CategoriesUiState
    data class Error(val code: ApiErrorCode) : CategoriesUiState
}

/** [isLoadingMore] is [Loaded]'s own append-in-flight flag — a failed "load more" (see
 *  [ExploreViewModel.onLoadMoreCourses]) only clears this flag, it never discards [items] already
 *  on screen and never flips the whole section to [Error]. */
sealed interface CoursesUiState {
    data object Loading : CoursesUiState
    data class Loaded(
        val items: List<CourseSummary>,
        val nextCursor: String?,
        val isLoadingMore: Boolean = false,
    ) : CoursesUiState
    data object Empty : CoursesUiState
    data class Error(val code: ApiErrorCode) : CoursesUiState
}

/** `GET /api/v1/learning-paths` is a plain unpaginated list (see that use case's kdoc) — no
 *  cursor/load-more state needed here, unlike [CoursesUiState]. */
sealed interface LearningPathsUiState {
    data object Loading : LearningPathsUiState
    data class Loaded(val items: List<LearningPath>) : LearningPathsUiState
    data object Empty : LearningPathsUiState
    data class Error(val code: ApiErrorCode) : LearningPathsUiState
}

data class ExploreUiState(
    val categories: CategoriesUiState = CategoriesUiState.Loading,
    val selectedCategoryId: String? = null, // null == the "All" chip.
    val searchQuery: String = "",
    val courses: CoursesUiState = CoursesUiState.Loading,
    val selectedTab: ExploreTab = ExploreTab.Courses,
    val learningPaths: LearningPathsUiState = LearningPathsUiState.Loading,
)

/** `ux/MOBILE_UX.md § 3`'s own debounce figure — a plain top-level constant so
 *  [ExploreViewModelTest] can assert against it by name rather than a re-typed magic number. */
const val ExploreSearchDebounceMillis: Long = 300L

/**
 * T9 — Explore's ViewModel. Deliberately takes 3 plain suspend-lambdas rather than a `MentoraSdk`
 * directly: `CatalogFacade`/`LearningPathFacade`'s constructors are `internal`, so `:androidApp`
 * cannot build a fake one for a JVM unit test (mirrors why [com.mentora.android.navigation.AuthGate]
 * takes a plain `authState: AuthState` parameter instead of reading `sdk.auth` itself — the same
 * fake-friendly-seam idea, applied to this ViewModel's constructor instead of a Composable's
 * parameter list). [Factory] below wires the 3 lambdas to the real `sdk.catalog`/`sdk.learningPaths`
 * use cases in production; [ExploreViewModelTest] wires them to counting fakes instead.
 */
class ExploreViewModel(
    private val listCategories: suspend () -> ApiResult<List<Category>>,
    private val searchCourses: suspend (CourseFilters, String?) -> ApiResult<CursorPage<CourseSummary>>,
    private val listLearningPaths: suspend () -> ApiResult<List<LearningPath>>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()

    /** Cancelled/relaunched on every keystroke — the actual debounce mechanism: a search only fires
     *  once [ExploreSearchDebounceMillis] has elapsed with no further [onSearchQueryChange] call. */
    private var searchDebounceJob: Job? = null

    init {
        loadCategories()
        loadCourses(resetting = true)
        loadLearningPaths()
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        searchDebounceJob?.cancel()
        searchDebounceJob = viewModelScope.launch {
            delay(ExploreSearchDebounceMillis)
            loadCourses(resetting = true)
        }
    }

    fun onCategorySelected(categoryId: String?) {
        if (_uiState.value.selectedCategoryId == categoryId) return
        // A chip tap is a discrete selection, not free typing — it searches immediately, no debounce.
        searchDebounceJob?.cancel()
        _uiState.update { it.copy(selectedCategoryId = categoryId) }
        loadCourses(resetting = true)
    }

    fun onTabSelected(tab: ExploreTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    /** The spec's exact `states.empty` action (`"No results" row + "Clear filters" action`). */
    fun onClearFilters() {
        searchDebounceJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", selectedCategoryId = null) }
        loadCourses(resetting = true)
    }

    fun onRetryCategories() = loadCategories()

    fun onRetryCourses() = loadCourses(resetting = true)

    fun onRetryLearningPaths() = loadLearningPaths()

    /** Cursor-based "load more", called as the course list scrolls near its end (see
     *  `ExploreScreen.kt`'s `LaunchedEffect` on the `LazyListState`). A no-op if there is no next
     *  page, a page load isn't showing yet, or one is already in flight. */
    fun onLoadMoreCourses() {
        val current = _uiState.value.courses
        if (current !is CoursesUiState.Loaded || current.nextCursor == null || current.isLoadingMore) return
        _uiState.update { it.copy(courses = current.copy(isLoadingMore = true)) }
        viewModelScope.launch {
            when (val result = searchCourses(currentFilters(), current.nextCursor)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        courses = CoursesUiState.Loaded(
                            items = current.items + result.data.items,
                            nextCursor = result.data.nextCursor,
                        ),
                    )
                }
                // A failed "load more" keeps the page already on screen — never blows it away into
                // a full-screen ErrorState, just stops the in-flight spinner so the user can retry by
                // scrolling again.
                is ApiResult.Failure -> _uiState.update { it.copy(courses = current.copy(isLoadingMore = false)) }
            }
        }
    }

    private fun currentFilters(): CourseFilters {
        val state = _uiState.value
        return CourseFilters(
            category = state.selectedCategoryId,
            query = state.searchQuery.trim().ifBlank { null },
        )
    }

    private fun loadCategories() {
        _uiState.update { it.copy(categories = CategoriesUiState.Loading) }
        viewModelScope.launch {
            when (val result = listCategories()) {
                is ApiResult.Success -> _uiState.update { it.copy(categories = CategoriesUiState.Loaded(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(categories = CategoriesUiState.Error(result.code)) }
            }
        }
    }

    private fun loadCourses(resetting: Boolean) {
        if (resetting) {
            _uiState.update { it.copy(courses = CoursesUiState.Loading) }
        }
        viewModelScope.launch {
            when (val result = searchCourses(currentFilters(), null)) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        courses = if (result.data.items.isEmpty()) {
                            CoursesUiState.Empty
                        } else {
                            CoursesUiState.Loaded(items = result.data.items, nextCursor = result.data.nextCursor)
                        },
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(courses = CoursesUiState.Error(result.code)) }
            }
        }
    }

    private fun loadLearningPaths() {
        _uiState.update { it.copy(learningPaths = LearningPathsUiState.Loading) }
        viewModelScope.launch {
            when (val result = listLearningPaths()) {
                is ApiResult.Success -> _uiState.update {
                    it.copy(
                        learningPaths = if (result.data.isEmpty()) {
                            LearningPathsUiState.Empty
                        } else {
                            LearningPathsUiState.Loaded(result.data)
                        },
                    )
                }
                is ApiResult.Failure -> _uiState.update { it.copy(learningPaths = LearningPathsUiState.Error(result.code)) }
            }
        }
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `AuthViewModel.Factory`/`AppSessionViewModel.Factory`'s
     *  exact idiom. Wires the 3 constructor lambdas to the real `sdk.catalog`/`sdk.learningPaths` use
     *  cases' bound `invoke` — see this class's own kdoc for why the constructor takes lambdas at all. */
    class Factory(private val sdk: MentoraSdk) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ExploreViewModel(
            listCategories = sdk.catalog.listCategories::invoke,
            searchCourses = sdk.catalog.searchCourses::invoke,
            listLearningPaths = sdk.learningPaths.listLearningPaths::invoke,
        ) as T
    }
}
