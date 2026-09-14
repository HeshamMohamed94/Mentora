package com.mentora.android.ui.mylearning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.mentora.android.domain.mylearning.GetMyLearningWithProgressUseCase
import com.mentora.android.domain.mylearning.LearningItemWithProgress
import com.mentora.android.domain.mylearning.MyLearningLoadState
import com.mentora.android.viewmodel.reloadOnLocaleChange
import com.mentora.shared.MentoraSdk
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.domain.model.CertificateSummary
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.LearningPath
import com.mentora.shared.domain.model.LearningPathDetail
import com.mentora.shared.settings.AppLocale
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** `ux/SCREEN_UX_SPECS.md § 9`'s literal 3-segment status filter (disclosed resolution vs. the
 *  showcase's own 2-tab "In progress / Completed" mockup, `mobile-my-learning.json` lines 2163-2166 —
 *  rank-1 wins per this phase's established precedent, e.g. Task 10's identical LINEAR-vs-3-tab
 *  resolution for Course Details). */
enum class MyLearningFilter { All, InProgress, Completed }

data class MyLearningUiState(
    val filter: MyLearningFilter = MyLearningFilter.All,
    /** The screen's primary content — the one section with a real, retryable [MyLearningLoadState.Error]. */
    val items: MyLearningLoadState = MyLearningLoadState.Loading,
    /** Followed Learning Paths (`ux/SCREEN_UX_SPECS.md § 9` content item 3) — best-effort, see
     *  [MyLearningViewModel.loadFollowedPaths]'s own kdoc for why this is a plain list, never its own
     *  [MyLearningLoadState]-style error surface. */
    val followedPaths: List<LearningPathDetail> = emptyList(),
    /** The Certificates entry point's inline highlight rows (`mobile-my-learning.json`'s
     *  `certificatesEntry` section) — also best-effort, same rationale as [followedPaths]. */
    val certificates: List<CertificateSummary> = emptyList(),
    /** Best-effort category-name resolution for each [items] row's thumbnail chip — same
     *  non-blocking pattern as `ExploreViewModel`/`CourseDetailsViewModel`'s own `categories` load; a
     *  failure here just leaves this list empty and the chip falls back to the raw `categoryId`. */
    val categories: List<Category> = emptyList(),
)

/**
 * T12 — My Learning's ViewModel. Same lambda-constructor seam as every prior task's ViewModel.
 *
 * **Three independent loads, two different failure philosophies, by design:**
 * - [items] (the course progress list) is the screen's PRIMARY content — a real, retryable
 *   [MyLearningLoadState.Error] surfaces on failure, exactly like every other primary-content load in
 *   this phase (`CourseDetailsViewModel.course`, `ExploreViewModel.courses`).
 * - [MyLearningUiState.followedPaths]/[MyLearningUiState.certificates] are both supplementary modules
 *   (rank-1 module 3 / the Certificates entry point) — a failure anywhere in either load just leaves
 *   that module empty (indistinguishable from "the student has none"), mirroring
 *   `CourseDetailsViewModel.loadCategories`'s already-established "best-effort, never its own error
 *   state" pattern for a supplementary section. This keeps the screen from stacking 3 separate
 *   [com.mentora.android.ui.components.ErrorState]s for one screen's worth of secondary content.
 *
 * **Followed Learning Paths — G5's join** (`execution/PHASE_4_ANDROID_PLAN.md` § 6 G5): `LearningPath`
 * (the list item) carries no `isFollowing` field at all (that model's own kdoc) — [listLearningPaths]
 * returns every path, then [getLearningPathDetail] is called once per path (N+1, "trivial at seed
 * scale — 1 path today" per G5's own text) to learn which ones [LearningPathDetail.isFollowing] is
 * true for.
 *
 * **Certificates entry — no join needed at all.** [CertificateSummary] already carries its own real,
 * opaque certificate id (that model's own kdoc) — each returned certificate IS a real, complete
 * "completed course with a certificate ready" entry on its own; no correlation against [items] is
 * needed (or attempted) to render or navigate from it.
 */
class MyLearningViewModel(
    private val getMyLearningWithProgress: suspend () -> ApiResult<List<LearningItemWithProgress>>,
    private val listLearningPaths: suspend () -> ApiResult<List<LearningPath>>,
    private val getLearningPathDetail: suspend (String) -> ApiResult<LearningPathDetail>,
    private val listCertificates: suspend (String?, Int?) -> ApiResult<CursorPage<CertificateSummary>>,
    private val listCategories: suspend () -> ApiResult<List<Category>>,
    val resolveThumbnailUrl: (String) -> String,
    /** T19 — see `com.mentora.android.viewmodel.reloadOnLocaleChange`'s own kdoc. */
    private val observeLocale: () -> StateFlow<AppLocale> = { MutableStateFlow(AppLocale.English) },
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyLearningUiState())
    val uiState: StateFlow<MyLearningUiState> = _uiState.asStateFlow()

    init {
        loadItems()
        loadFollowedPaths()
        loadCertificates()
        loadCategories()
        viewModelScope.reloadOnLocaleChange(observeLocale) {
            loadItems()
            loadFollowedPaths()
            loadCertificates()
            loadCategories()
        }
    }

    fun onFilterSelected(filter: MyLearningFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun onRetryItems() = loadItems()

    /**
     * T16 — closes a real, proactively-flagged staleness gap: this screen has no refresh-on-return
     * mechanism of its own, but [MyLearningScreen] genuinely leaves and re-enters composition whenever
     * Learning Path Details is pushed on top of `MyLearningGraph`'s own back stack (Navigation-Compose
     * disposes the covered destination's composition while another is pushed on top) and popped back
     * — the exact same mechanism `CoursePlayerViewModel.refreshQuizStatus`'s own kdoc already
     * documents (`DECISIONS_LOG.md` D89). [MyLearningScreen]'s own `LaunchedEffect(Unit)` calls this on
     * every (re-)entry so following/unfollowing a path from the Detail screen and returning here shows
     * the up-to-date followed-paths list immediately, not stale until some unrelated reload. Narrow and
     * additive by design — only [loadFollowedPaths] re-runs, not the other 3 independent loads
     * [init] already kicks off (this class's own kdoc: 3 independent loads, no reason to force a full
     * reload of unrelated content just to refresh one module).
     */
    /** Round-1 review finding (LOW): tracks the in-flight [loadFollowedPaths] 1+N join so a NEWER call
     *  always wins — without this, two overlapping calls (the guaranteed [init]+[refreshFollowedPaths]
     *  double-fire on first entry, or a fast follow/unfollow round trip started while a slower earlier
     *  refresh is still resolving) could resolve out of order and let the OLDER, now-stale result
     *  silently overwrite the newer one (e.g. unfollow -> back (refresh A starts, slow) -> re-enter,
     *  follow again -> back (refresh B starts, fast) -> B writes the correct followed list -> A lands
     *  late and overwrites it back to stale). [Job.cancel] on the previous call before launching a new
     *  one makes only the LATEST call's result ever apply, the same "newer supersedes older" shape
     *  [com.mentora.android.ui.courseplayer.CoursePlayerViewModel.refreshQuizStatus]'s own
     *  `progressBeforeFetch` identity check achieves by a different, field-count-appropriate mechanism
     *  (a single mutable field there vs. a whole list-rebuilding join here). */
    private var followedPathsJob: Job? = null

    fun refreshFollowedPaths() = loadFollowedPaths()

    private fun loadItems() {
        _uiState.update { it.copy(items = MyLearningLoadState.Loading) }
        viewModelScope.launch {
            when (val result = getMyLearningWithProgress()) {
                is ApiResult.Success -> _uiState.update { it.copy(items = MyLearningLoadState.Loaded(result.data)) }
                is ApiResult.Failure -> _uiState.update { it.copy(items = MyLearningLoadState.Error(result.code)) }
            }
        }
    }

    private fun loadFollowedPaths() {
        followedPathsJob?.cancel()
        followedPathsJob = viewModelScope.launch {
            when (val listResult = listLearningPaths()) {
                is ApiResult.Success -> {
                    val followed = mutableListOf<LearningPathDetail>()
                    for (path in listResult.data) {
                        val detail = getLearningPathDetail(path.id)
                        if (detail is ApiResult.Success && detail.data.isFollowing) {
                            followed += detail.data
                        }
                        // A single path's detail failure just excludes that one path — best-effort,
                        // see this class's own kdoc.
                    }
                    _uiState.update { it.copy(followedPaths = followed) }
                }
                is ApiResult.Failure -> Unit // Best-effort — the module simply stays empty.
            }
        }
    }

    private fun loadCertificates() {
        viewModelScope.launch {
            when (val result = listCertificates(null, CertificatesPageLimit)) {
                is ApiResult.Success -> _uiState.update { it.copy(certificates = result.data.items) }
                is ApiResult.Failure -> Unit // Best-effort, same rationale as loadFollowedPaths.
            }
        }
    }

    private fun loadCategories() {
        viewModelScope.launch {
            when (val result = listCategories()) {
                is ApiResult.Success -> _uiState.update { it.copy(categories = result.data) }
                is ApiResult.Failure -> Unit // Best-effort — see this class's own kdoc.
            }
        }
    }

    companion object {
        /** Generously high — the inline highlight-row treatment only ever shows a handful at once;
         *  a "View all" affordance (the always-present Certificates entry point) covers the rest. */
        const val CertificatesPageLimit: Int = 10
    }

    /** Plain [ViewModelProvider.Factory] — mirrors `HomeViewModel.Factory`'s exact idiom, including
     *  constructing the shared [GetMyLearningWithProgressUseCase] the same way. */
    class Factory(private val sdk: MentoraSdk) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = MyLearningViewModel(
            getMyLearningWithProgress = GetMyLearningWithProgressUseCase(
                getMyLearning = sdk.enrollment.getMyLearning::invoke,
                getCourseProgress = sdk.progress.getCourseProgress::invoke,
            )::invoke,
            listLearningPaths = sdk.learningPaths.listLearningPaths::invoke,
            getLearningPathDetail = sdk.learningPaths.getLearningPathDetail::invoke,
            listCertificates = sdk.certificates.listCertificates::invoke,
            listCategories = sdk.catalog.listCategories::invoke,
            resolveThumbnailUrl = sdk.media.resolveThumbnailUrl::invoke,
            observeLocale = sdk.user.observeLocale::invoke,
        ) as T
    }
}
