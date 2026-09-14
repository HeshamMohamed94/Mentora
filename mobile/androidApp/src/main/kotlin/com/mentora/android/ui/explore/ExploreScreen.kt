package com.mentora.android.ui.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.ui.components.CategoryChip
import com.mentora.android.ui.components.CourseCard
import com.mentora.android.ui.components.CourseCardSkeleton
import com.mentora.android.ui.components.EmptyState
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.LearningPathCard
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.components.MentoraTabOption
import com.mentora.android.ui.components.MentoraTabs
import com.mentora.android.ui.components.MentoraTextButton
import com.mentora.android.ui.components.SearchField
import com.mentora.android.ui.components.SkeletonBlock
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk
import com.mentora.shared.domain.model.Category
import com.mentora.shared.domain.model.CourseSummary
import com.mentora.shared.domain.model.LearningPath

/**
 * T9 — the real Explore screen (`design-to-code/screens/mobile-explore.json`). Heading -> full-width
 * [SearchField] -> horizontally-scrollable [CategoryChip] row -> the rank-1-required Learning Paths
 * segment/tab ([MentoraTabs], per that json's own `conflicts[1]` — the showcase itself shows no
 * visible tab control, so this is built exactly per `ux/MOBILE_UX.md § 3`'s literal requirement,
 * reusing the existing Tabs recipe rather than inventing a new segmented-control visual) -> either the
 * course list or the Learning Paths list, one column, full-width, depending on which tab is active.
 *
 * **Learning-Paths-destination-vs-Explore-tab resolution (disclosed, per the task brief's own "your
 * call"):** the old `Destination.LearningPaths` PUSH destination (and its `LearningPathsScreen`
 * placeholder) is removed entirely — Learning Paths now lives ONLY as this screen's own internal tab
 * state, exactly matching the rank-1 "segment/tab... inside Explore" requirement literally (a pushed
 * destination for the exact same list would have been a redundant second way to reach the identical
 * content). [Destination.LearningPathDetails] remains its own pushed destination (via
 * [onOpenPathDetails]) — only the LIST view folded into this tab, never the per-path detail screen.
 */
@Composable
fun ExploreScreen(
    sdk: MentoraSdk,
    onOpenCourseDetails: (String) -> Unit,
    onOpenPathDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ExploreViewModel = viewModel(factory = ExploreViewModel.Factory(sdk))
    val uiState by viewModel.uiState.collectAsState()

    ExploreScreenContent(
        uiState = uiState,
        onSearchQueryChange = viewModel::onSearchQueryChange,
        onCategorySelected = viewModel::onCategorySelected,
        onTabSelected = viewModel::onTabSelected,
        onClearFilters = viewModel::onClearFilters,
        onRetryCategories = viewModel::onRetryCategories,
        onRetryCourses = viewModel::onRetryCourses,
        onRetryLearningPaths = viewModel::onRetryLearningPaths,
        onLoadMoreCourses = viewModel::onLoadMoreCourses,
        onOpenCourseDetails = onOpenCourseDetails,
        onOpenPathDetails = onOpenPathDetails,
        modifier = modifier,
    )
}

/**
 * The stateless presentation half of [ExploreScreen] — every rendering/interaction state is driven by
 * a hand-built [ExploreUiState], with no [MentoraSdk]/[ExploreViewModel] involved (same split
 * rationale as `LoginScreen`/`LoginScreenContent` — see that composable's kdoc).
 *
 * One [LazyColumn] for the WHOLE screen (heading through the course/path list) — the spec's own
 * "single scrolling column" structure — so pagination (the [LaunchedEffect] below, watching
 * [listState]'s last-visible-item position) and the loading skeleton naturally participate in the
 * same scroll container as the header, rather than needing a second nested scrollable.
 */
@Composable
internal fun ExploreScreenContent(
    uiState: ExploreUiState,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onTabSelected: (ExploreTab) -> Unit,
    onClearFilters: () -> Unit,
    onRetryCategories: () -> Unit,
    onRetryCourses: () -> Unit,
    onRetryLearningPaths: () -> Unit,
    onLoadMoreCourses: () -> Unit,
    onOpenCourseDetails: (String) -> Unit,
    onOpenPathDetails: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    // Cursor-based "load more": fires once the last visible item is within 3 of the end of the
    // CURRENT list. onLoadMoreCourses() itself is idempotent (guards on nextCursor/isLoadingMore), so
    // this recomposing repeatedly while already near the bottom is harmless.
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
    }
    LaunchedEffect(shouldLoadMore, uiState.selectedTab) {
        if (shouldLoadMore && uiState.selectedTab == ExploreTab.Courses) {
            onLoadMoreCourses()
        }
    }

    val categoryNameById = remember(uiState.categories) {
        (uiState.categories as? CategoriesUiState.Loaded)?.categories?.associate { it.id to it.name }.orEmpty()
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize().testTag(ExploreCourseListTestTag),
        contentPadding = PaddingValues(horizontal = MentoraDimens.spacing.space4, vertical = MentoraDimens.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
    ) {
        item {
            ExploreHeader(
                uiState = uiState,
                onSearchQueryChange = onSearchQueryChange,
                onCategorySelected = onCategorySelected,
                onTabSelected = onTabSelected,
                onRetryCategories = onRetryCategories,
            )
        }

        when (uiState.selectedTab) {
            ExploreTab.Courses -> courseListItems(
                coursesState = uiState.courses,
                categoryNameById = categoryNameById,
                onRetryCourses = onRetryCourses,
                onClearFilters = onClearFilters,
                onOpenCourseDetails = onOpenCourseDetails,
            )
            ExploreTab.LearningPaths -> learningPathListItems(
                learningPathsState = uiState.learningPaths,
                onRetryLearningPaths = onRetryLearningPaths,
                onOpenPathDetails = onOpenPathDetails,
            )
        }
    }
}

@Composable
private fun ExploreHeader(
    uiState: ExploreUiState,
    onSearchQueryChange: (String) -> Unit,
    onCategorySelected: (String?) -> Unit,
    onTabSelected: (ExploreTab) -> Unit,
    onRetryCategories: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4)) {
        Text(
            text = stringResource(R.string.explore_heading),
            // heading.h1 -> MaterialTheme.typography.headlineLarge (MentoraTheme.kt's own mapping) —
            // the spec allows either "typography.display.medium or heading.h1"; h1 is used here.
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        SearchField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChange,
            label = stringResource(R.string.explore_search_label),
            clearContentDescription = stringResource(R.string.explore_search_clear),
            placeholder = stringResource(R.string.explore_search_placeholder),
            modifier = Modifier.fillMaxWidth().testTag(ExploreSearchFieldTestTag),
        )

        CategoryChipRow(
            categoriesState = uiState.categories,
            selectedCategoryId = uiState.selectedCategoryId,
            onCategorySelected = onCategorySelected,
            onRetry = onRetryCategories,
        )

        MentoraTabs(
            options = listOf(
                MentoraTabOption(ExploreTab.Courses, stringResource(R.string.explore_tab_courses)),
                MentoraTabOption(ExploreTab.LearningPaths, stringResource(R.string.explore_tab_learning_paths)),
            ),
            selected = uiState.selectedTab,
            onSelect = onTabSelected,
            modifier = Modifier.fillMaxWidth().testTag(ExploreTabsTestTag),
        )
    }
}

/**
 * An "All" chip (id `null`) plus one per real [Category]. [LazyRow] scrolls from the layout's logical
 * START by default (Compose's own [LocalLayoutDirection][androidx.compose.ui.platform.LocalLayoutDirection]-
 * aware behavior, never a hardcoded physical left) — satisfies the RTL requirement with no extra code;
 * not independently re-verified live for mobile RTL in this pass (no mobile Arabic Explore frame
 * exists in the showcase — see that json's own `knownGaps`).
 */
@Composable
private fun CategoryChipRow(
    categoriesState: CategoriesUiState,
    selectedCategoryId: String?,
    onCategorySelected: (String?) -> Unit,
    onRetry: () -> Unit,
) {
    when (categoriesState) {
        is CategoriesUiState.Loading -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
            modifier = Modifier.testTag(ExploreCategoryChipRowTestTag),
        ) {
            items(3) {
                SkeletonBlock(modifier = Modifier.width(72.dp).height(28.dp), shape = CircleShape)
            }
        }

        is CategoriesUiState.Error -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
            modifier = Modifier.testTag(ExploreCategoryChipRowTestTag),
        ) {
            Text(
                text = apiErrorMessage(categoriesState.code),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MentoraTextButton(text = stringResource(R.string.explore_categories_retry), onClick = onRetry)
        }

        is CategoriesUiState.Loaded -> LazyRow(
            horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
            modifier = Modifier.testTag(ExploreCategoryChipRowTestTag),
        ) {
            item {
                CategoryChip(
                    label = stringResource(R.string.explore_category_all),
                    selected = selectedCategoryId == null,
                    onClick = { onCategorySelected(null) },
                )
            }
            items(categoriesState.categories, key = { it.id }) { category ->
                CategoryChip(
                    label = category.name,
                    selected = selectedCategoryId == category.id,
                    onClick = { onCategorySelected(category.id) },
                )
            }
        }
    }
}

/** The Courses tab's content, appended as plain [LazyColumn] items (this function's receiver) so it
 *  shares the outer scroll/pagination container — see [ExploreScreenContent]'s kdoc. */
private fun LazyListScope.courseListItems(
    coursesState: CoursesUiState,
    categoryNameById: Map<String, String>,
    onRetryCourses: () -> Unit,
    onClearFilters: () -> Unit,
    onOpenCourseDetails: (String) -> Unit,
) {
    when (coursesState) {
        is CoursesUiState.Loading -> items(6) {
            CourseCardSkeleton(modifier = Modifier.fillMaxWidth())
        }

        is CoursesUiState.Empty -> item {
            EmptyStateHost {
                EmptyState(
                    icon = MentoraIconName.Search,
                    title = stringResource(R.string.explore_empty_title),
                    description = stringResource(R.string.explore_empty_description),
                    actionLabel = stringResource(R.string.explore_clear_filters),
                    onActionClick = onClearFilters,
                )
            }
        }

        is CoursesUiState.Error -> item {
            EmptyStateHost {
                ErrorState(
                    title = stringResource(R.string.explore_error_title),
                    description = apiErrorMessage(coursesState.code),
                    onRetryClick = onRetryCourses,
                )
            }
        }

        is CoursesUiState.Loaded -> {
            items(coursesState.items, key = { it.id }) { course ->
                ExploreCourseCard(
                    course = course,
                    categoryLabel = categoryNameById[course.categoryId] ?: course.categoryId,
                    onClick = { onOpenCourseDetails(course.id) },
                )
            }
            if (coursesState.isLoadingMore) {
                item { LoadMoreSpinner() }
            }
        }
    }
}

private fun LazyListScope.learningPathListItems(
    learningPathsState: LearningPathsUiState,
    onRetryLearningPaths: () -> Unit,
    onOpenPathDetails: (String) -> Unit,
) {
    when (learningPathsState) {
        is LearningPathsUiState.Loading -> items(3) {
            CourseCardSkeleton(modifier = Modifier.fillMaxWidth())
        }

        is LearningPathsUiState.Empty -> item {
            EmptyStateHost {
                EmptyState(
                    icon = MentoraIconName.LearningPaths,
                    title = stringResource(R.string.explore_learning_paths_empty_title),
                    description = stringResource(R.string.explore_learning_paths_empty_description),
                )
            }
        }

        is LearningPathsUiState.Error -> item {
            EmptyStateHost {
                ErrorState(
                    title = stringResource(R.string.explore_error_title),
                    description = apiErrorMessage(learningPathsState.code),
                    onRetryClick = onRetryLearningPaths,
                )
            }
        }

        is LearningPathsUiState.Loaded -> items(learningPathsState.items, key = { it.id }) { path ->
            ExploreLearningPathCard(path = path, onClick = { onOpenPathDetails(path.id) })
        }
    }
}

@Composable
private fun ExploreCourseCard(course: CourseSummary, categoryLabel: String, onClick: () -> Unit) {
    CourseCard(
        title = course.title,
        instructorName = course.instructorName,
        seed = course.id,
        categoryId = course.categoryId,
        // The overlay chip on the card's own thumbnail (CourseArtworkWithChip) — resolved to the
        // real Category.name via the categories load when available, falling back to the raw id so
        // this card never depends on the categories call having already succeeded.
        categoryLabel = categoryLabel,
        thumbnailContentDescription = stringResource(R.string.explore_course_thumbnail_content_description, course.title),
        actionLabel = stringResource(R.string.explore_course_action),
        onActionClick = onClick,
        onClick = onClick,
        // course.ratingSeed is static seed data, never a real aggregated rating (CourseSummary's own
        // kdoc) — rendered as a plain star+number per CourseMetaRow's existing precedent, with no
        // review-count text that would imply real reviews exist. studentCount/durationLabel are left
        // null: CourseSummary has neither field, and neither may be fabricated.
        rating = course.ratingSeed.toFloat(),
        modifier = Modifier.fillMaxWidth().testTag(ExploreCourseCardTestTag),
    )
}

@Composable
private fun ExploreLearningPathCard(path: LearningPath, onClick: () -> Unit) {
    LearningPathCard(
        title = path.title,
        description = path.description,
        // `%1$s` + `.toString()`, not `%1$d` — Western-numeral in every locale regardless of the
        // current Locale's numbering system (`design-system/LOCALIZATION.md § 8`).
        metaLabel = stringResource(R.string.explore_learning_path_course_count, path.courseCount.toString()),
        actionLabel = stringResource(R.string.explore_learning_path_action),
        onActionClick = onClick,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(ExploreLearningPathCardTestTag),
    )
}

/** `space.10` vertical breathing room around an Empty/Error section dropped into the middle of an
 *  otherwise card-filled list — mirrors [EmptyState]/[ErrorState]'s own container padding intent. */
@Composable
private fun EmptyStateHost(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        content()
    }
}

@Composable
private fun LoadMoreSpinner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = MentoraDimens.spacing.space4)
            .testTag(ExploreLoadMoreSpinnerTestTag),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

// Test-only hooks (`ui.test.onNodeWithTag`/`onAllNodesWithTag`), unused by production code otherwise.
const val ExploreSearchFieldTestTag = "explore-search-field"
const val ExploreCategoryChipRowTestTag = "explore-category-chip-row"
const val ExploreTabsTestTag = "explore-tabs"
const val ExploreCourseListTestTag = "explore-course-list"
const val ExploreCourseCardTestTag = "explore-course-card"
const val ExploreLearningPathCardTestTag = "explore-learning-path-card"
const val ExploreLoadMoreSpinnerTestTag = "explore-load-more-spinner"
