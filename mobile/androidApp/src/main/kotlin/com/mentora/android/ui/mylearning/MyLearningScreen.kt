package com.mentora.android.ui.mylearning

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.domain.mylearning.LearningItemWithProgress
import com.mentora.android.domain.mylearning.MyLearningLoadState
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors
import com.mentora.android.ui.components.CourseCardSkeleton
import com.mentora.android.ui.components.CourseProgressCard
import com.mentora.android.ui.components.EmptyState
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.LearningPathCard
import com.mentora.android.ui.components.MentoraIcon
import com.mentora.android.ui.components.MentoraIconButton
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.components.MentoraTabOption
import com.mentora.android.ui.components.MentoraTabs
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk
import com.mentora.shared.domain.model.CertificateSummary
import com.mentora.shared.domain.model.LearningPathDetail

/**
 * T12 — the real My Learning screen (`design-to-code/screens/mobile-my-learning.json`,
 * exact-showcase). Heading -> status filter -> course progress list -> Followed Learning Paths ->
 * Certificates entry, per `ux/SCREEN_UX_SPECS.md § 9`'s content order.
 *
 * **Filter resolution (disclosed).** The showcase mockup shows 2 tabs ("In progress"/"Completed,"
 * lines 2163-2166); rank-1 (`ux/SCREEN_UX_SPECS.md § 9`) calls for a literal 3-segment "All / In
 * Progress / Completed" control. Rank-1 wins per this phase's established precedent (Task 10's
 * identical resolution for Course Details' LINEAR-vs-3-tab conflict) — [MyLearningFilter] has 3 cases,
 * rendered via the same [MentoraTabs] recipe `ExploreViewModel`'s own segment/tab already established.
 *
 * **Certificates entry point (the task brief's own "+ Certificates entry" requirement).** TWO
 * affordances, both real: (1) an always-present [MentoraIconButton] next to the heading — reachable
 * regardless of whether the student has any certificates yet, satisfying the entry-point requirement
 * unconditionally; (2) the showcase's own inline highlight rows (`mobile-my-learning.json`'s
 * `certificatesEntry` section, lines 2183-2186) for each real, already-earned
 * [com.mentora.shared.domain.model.CertificateSummary] — tapping one deep-links straight to ITS OWN
 * real [CertificateSummary.id], never a fabricated/guessed course-to-certificate correlation (no
 * shared field exists to correlate them by course at all — see [MyLearningViewModel]'s own kdoc).
 *
 * **Followed Learning Paths (rank-1 content item 3) — G5's join.** See [MyLearningViewModel]'s own
 * kdoc for the exact N+1 shape; hides entirely when the student follows none, per rank-1's own
 * "hides entirely if no paths are followed" rule (`ux/SCREEN_UX_SPECS.md § 8`, reused here for the
 * identical My Learning content item).
 */
@Composable
fun MyLearningScreen(
    sdk: MentoraSdk,
    onResumeCourse: (courseId: String, lessonId: String?) -> Unit,
    onOpenLearningPathDetails: (pathId: String) -> Unit,
    onOpenCertificates: () -> Unit,
    onOpenCertificateDetail: (certificateId: String) -> Unit,
    onOpenExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: MyLearningViewModel = viewModel(factory = MyLearningViewModel.Factory(sdk))
    val uiState by viewModel.uiState.collectAsState()

    // T16 — re-fires on every (re-)entry into this composable's composition, including a return from a
    // pushed Learning Path Details screen via back (Navigation-Compose disposes the covered
    // destination's composition while another is pushed on top) — see
    // `MyLearningViewModel.refreshFollowedPaths`'s own kdoc for the full rationale. Firing on the very
    // first composition too is harmless — it just re-fetches the same followed-paths join `init`
    // already kicked off moments earlier.
    LaunchedEffect(Unit) { viewModel.refreshFollowedPaths() }

    MyLearningScreenContent(
        uiState = uiState,
        resolveThumbnailUrl = viewModel.resolveThumbnailUrl,
        onFilterSelected = viewModel::onFilterSelected,
        onRetryItems = viewModel::onRetryItems,
        onResumeCourse = onResumeCourse,
        onOpenLearningPathDetails = onOpenLearningPathDetails,
        onOpenCertificates = onOpenCertificates,
        onOpenCertificateDetail = onOpenCertificateDetail,
        onOpenExplore = onOpenExplore,
        modifier = modifier,
    )
}

/** The stateless presentation half of [MyLearningScreen] — same split rationale as every other
 *  T7-T12 screen. */
@Composable
internal fun MyLearningScreenContent(
    uiState: MyLearningUiState,
    resolveThumbnailUrl: (String) -> String,
    onFilterSelected: (MyLearningFilter) -> Unit,
    onRetryItems: () -> Unit,
    onResumeCourse: (courseId: String, lessonId: String?) -> Unit,
    onOpenLearningPathDetails: (pathId: String) -> Unit,
    onOpenCertificates: () -> Unit,
    onOpenCertificateDetail: (certificateId: String) -> Unit,
    onOpenExplore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val categoryNameById = remember(uiState.categories) { uiState.categories.associate { it.id to it.name } }

    val filtered = when (val state = uiState.items) {
        is MyLearningLoadState.Loaded -> state.items.filter { item ->
            when (uiState.filter) {
                MyLearningFilter.All -> true
                MyLearningFilter.InProgress -> item.progress.completionPercent < 100
                MyLearningFilter.Completed -> item.progress.completionPercent >= 100
            }
        }
        else -> emptyList()
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(MyLearningScreenTestTag),
        contentPadding = PaddingValues(horizontal = MentoraDimens.spacing.space4, vertical = MentoraDimens.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.my_learning_heading),
                    style = MaterialTheme.typography.headlineLarge, // heading.h1.
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MentoraIconButton(
                    icon = MentoraIconName.Certificates,
                    contentDescription = stringResource(R.string.my_learning_certificates_entry_content_description),
                    onClick = onOpenCertificates,
                    modifier = Modifier.testTag(MyLearningCertificatesEntryTestTag),
                )
            }
        }

        item {
            MentoraTabs(
                options = listOf(
                    MentoraTabOption(MyLearningFilter.All, stringResource(R.string.my_learning_filter_all)),
                    MentoraTabOption(MyLearningFilter.InProgress, stringResource(R.string.my_learning_filter_in_progress)),
                    MentoraTabOption(MyLearningFilter.Completed, stringResource(R.string.my_learning_filter_completed)),
                ),
                selected = uiState.filter,
                onSelect = onFilterSelected,
                modifier = Modifier.fillMaxWidth().testTag(MyLearningFilterTabsTestTag),
            )
        }

        item {
            MyLearningItemsSection(
                state = uiState.items,
                filtered = filtered,
                categoryNameById = categoryNameById,
                resolveThumbnailUrl = resolveThumbnailUrl,
                onRetry = onRetryItems,
                onResumeCourse = onResumeCourse,
                onOpenExplore = onOpenExplore,
            )
        }

        if (uiState.followedPaths.isNotEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.my_learning_paths_heading),
                    style = MaterialTheme.typography.titleLarge, // heading.h4.
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            items(uiState.followedPaths, key = { it.id }) { path ->
                FollowedPathCard(path = path, onClick = { onOpenLearningPathDetails(path.id) })
            }
        }

        if (uiState.certificates.isNotEmpty()) {
            items(uiState.certificates, key = { it.id }) { certificate ->
                CertificateReadyRow(certificate = certificate, onClick = { onOpenCertificateDetail(certificate.id) })
            }
        }
    }
}

@Composable
private fun MyLearningItemsSection(
    state: MyLearningLoadState,
    filtered: List<LearningItemWithProgress>,
    categoryNameById: Map<String, String>,
    resolveThumbnailUrl: (String) -> String,
    onRetry: () -> Unit,
    onResumeCourse: (String, String?) -> Unit,
    onOpenExplore: () -> Unit,
) {
    when (state) {
        is MyLearningLoadState.Loading -> Column(verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3)) {
            repeat(3) { CourseCardSkeleton(modifier = Modifier.fillMaxWidth()) }
        }

        is MyLearningLoadState.Error -> ErrorState(
            title = stringResource(R.string.my_learning_error_title),
            description = apiErrorMessage(state.code),
            onRetryClick = onRetry,
        )

        is MyLearningLoadState.Loaded -> when {
            state.items.isEmpty() -> EmptyState(
                icon = MentoraIconName.MyLearning,
                title = stringResource(R.string.my_learning_empty_title),
                description = stringResource(R.string.my_learning_empty_description),
                actionLabel = stringResource(R.string.my_learning_explore_courses),
                onActionClick = onOpenExplore,
            )

            filtered.isEmpty() -> EmptyState(
                icon = MentoraIconName.MyLearning,
                title = stringResource(R.string.my_learning_filter_empty_title),
                description = stringResource(R.string.my_learning_filter_empty_description),
            )

            else -> Column(verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3)) {
                filtered.forEach { item ->
                    val isCompleted = item.progress.completionPercent >= 100
                    // Precomputed here (a @Composable context) rather than inside
                    // `progressLabelFormatter` itself — that parameter is a plain, non-@Composable
                    // `(Float) -> String` lambda, so it cannot call `stringResource` directly.
                    // `%1$s` + `.toString()`, not `%1$d` — Western-numeral in every locale regardless
                    // of the current Locale's numbering system (`design-system/LOCALIZATION.md § 8`).
                    val percentLabel = stringResource(R.string.my_learning_percent_complete, item.progress.completionPercent.toString())
                    CourseProgressCard(
                        title = item.course.title,
                        instructorName = item.course.instructorName,
                        seed = item.course.id,
                        categoryId = item.course.categoryId,
                        categoryLabel = categoryNameById[item.course.categoryId] ?: item.course.categoryId,
                        thumbnailContentDescription = stringResource(
                            R.string.my_learning_thumbnail_content_description,
                            item.course.title,
                        ),
                        mediaId = item.course.thumbnailMediaId,
                        thumbnailUrl = item.course.thumbnailMediaId?.let(resolveThumbnailUrl),
                        progress = item.progress.completionPercent / 100f,
                        onResumeClick = { onResumeCourse(item.course.id, item.progress.currentLessonId) },
                        resumeLabel = stringResource(
                            if (isCompleted) R.string.my_learning_review_action else R.string.my_learning_resume_action,
                        ),
                        progressLabelFormatter = { percentLabel },
                        modifier = Modifier.fillMaxWidth().testTag(MyLearningCourseProgressCardTestTag),
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowedPathCard(path: LearningPathDetail, onClick: () -> Unit) {
    LearningPathCard(
        title = path.title,
        description = path.description,
        // `%1$s` + `.toString()`, not `%1$d` — Western-numeral in every locale (same rationale as
        // this file's other `my_learning_percent_complete` call site above).
        metaLabel = stringResource(R.string.my_learning_percent_complete, (path.progressPercent ?: 0).toString()),
        actionLabel = stringResource(R.string.my_learning_path_view_action),
        onActionClick = onClick,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag(MyLearningFollowedPathCardTestTag),
    )
}

/** `mobile-my-learning.json` lines 2183-2186 — the success-container-tinted inline highlight row. Not
 *  [com.mentora.android.ui.components.CertificateCard] (that component's fuller preview+view+share
 *  shell is Certificates List/Detail's own job, Task 15 — this is a compact entry-point row, built
 *  from primitives, same "screen-local composable, not a new kit component" precedent as
 *  `HomeScreen.kt`'s own `RecommendedCourseRow`). */
@Composable
private fun CertificateReadyRow(certificate: CertificateSummary, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(MyLearningCertificateReadyRowTestTag),
        shape = MaterialTheme.shapes.medium, // radius.large.
        color = MaterialTheme.extendedColors.successContainer,
    ) {
        Row(
            modifier = Modifier.padding(MentoraDimens.spacing.space4),
            horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MentoraIcon(
                name = MentoraIconName.Certificates,
                contentDescription = null,
                size = MentoraDimens.iconSize.default,
                tint = MaterialTheme.extendedColors.success,
            )
            Column {
                Text(
                    text = certificate.courseTitleSnapshot,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.extendedColors.onSuccessContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.my_learning_certificate_ready_subtitle),
                    style = MaterialTheme.typography.labelSmall, // typography.caption.
                    color = MaterialTheme.extendedColors.onSuccessContainer,
                )
            }
        }
    }
}

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val MyLearningScreenTestTag = "my-learning-screen"
const val MyLearningFilterTabsTestTag = "my-learning-filter-tabs"
const val MyLearningCertificatesEntryTestTag = "my-learning-certificates-entry"
const val MyLearningCourseProgressCardTestTag = "my-learning-course-progress-card"
const val MyLearningFollowedPathCardTestTag = "my-learning-followed-path-card"
const val MyLearningCertificateReadyRowTestTag = "my-learning-certificate-ready-row"
