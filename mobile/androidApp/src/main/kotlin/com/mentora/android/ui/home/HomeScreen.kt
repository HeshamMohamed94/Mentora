package com.mentora.android.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.domain.mylearning.CourseLessonPosition
import com.mentora.android.domain.mylearning.LearningItemWithProgress
import com.mentora.android.domain.mylearning.MyLearningLoadState
import com.mentora.android.domain.mylearning.resolveLessonPosition
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.ui.components.Avatar
import com.mentora.android.ui.components.CourseCardSkeleton
import com.mentora.android.ui.components.CourseThumbnail
import com.mentora.android.ui.components.EmptyState
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.MentoraAvatarSize
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.components.MentoraProgressBar
import com.mentora.android.ui.components.MentoraTextButton
import com.mentora.android.ui.components.PrimaryButton
import com.mentora.android.ui.components.SkeletonBlock
import com.mentora.android.ui.components.StatCard
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk
import com.mentora.shared.domain.model.CourseSummary

/**
 * T12 — the real Home screen (`design-to-code/screens/mobile-home.json`, exact-showcase — showcase
 * lines cited per-section below). Single scrolling column: greeting -> Continue Learning module ->
 * stat row -> Recommended, per that spec's own `layout.structure`.
 *
 * **Stat row resolution (disclosed, per `mobile-home.json`'s own `conflicts[1]`).** The showcase's 2
 * cards are "Learning hours" (line 2051, no real backend data source exists anywhere — the identical
 * gap `dashboard.json#/conflicts[1]` already discloses for Web) and "Certificates" (line 2052, real).
 * Per that conflict's own recommendation, "Learning hours" is replaced with a real, truthful metric —
 * "Courses in progress" — keeping the showcase's exact 2-card visual footprint rather than fabricating
 * watch-time data. "Certificates" reuses the completed-item count from the same G3-joined list rather
 * than a separate `GET /certificates` call, mirroring web's own identical `DashboardScreen` precedent
 * ("a course only reaches `courseCompletedAt` at the exact moment its certificate becomes eligible for
 * issuance," so the two counts are equal by construction) — tapping it is this screen's Certificates
 * entry-point affordance (the task brief's "Home and/or My Learning" wording; My Learning's own entry
 * point is the primary one, see that screen's kdoc).
 *
 * **Continue Learning target (disclosed gap).** See [HomeViewModel]'s own kdoc — no true "most
 * recently active" data exists anywhere in `shared`; this picks the LAST in-progress item in
 * `GET /enrollments`' own ascending-by-`_id` return order — i.e. the most recently ENROLLED
 * still-unfinished course, the best available proxy (`.first()` would surface the oldest, most
 * likely already-abandoned one instead).
 */
@Composable
fun HomeScreen(
    sdk: MentoraSdk,
    userName: String,
    onOpenCourseDetails: (String) -> Unit,
    onOpenExplore: () -> Unit,
    onOpenCertificates: () -> Unit,
    onContinueLearning: (courseId: String, lessonId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(sdk, userName))
    val uiState by viewModel.uiState.collectAsState()
    // See HomeViewModel.onUserNameChanged's own kdoc — the constructor-time userName is often blank
    // on a cold start; this re-applies it once AppSessionViewModel's getProfile() follow-up resolves
    // the real name, without needing a new ViewModel instance.
    LaunchedEffect(userName) { viewModel.onUserNameChanged(userName) }

    HomeScreenContent(
        uiState = uiState,
        resolveThumbnailUrl = viewModel.resolveThumbnailUrl,
        onRetryMyLearning = viewModel::onRetryMyLearning,
        onRetryRecommended = viewModel::onRetryRecommended,
        onOpenCourseDetails = onOpenCourseDetails,
        onOpenExplore = onOpenExplore,
        onOpenCertificates = onOpenCertificates,
        onContinueLearning = onContinueLearning,
        modifier = modifier,
    )
}

/** The stateless presentation half of [HomeScreen] — same split rationale as every other T7-T11
 *  screen (see e.g. `ExploreScreenContent`'s own kdoc). */
@Composable
internal fun HomeScreenContent(
    uiState: HomeUiState,
    resolveThumbnailUrl: (String) -> String,
    onRetryMyLearning: () -> Unit,
    onRetryRecommended: () -> Unit,
    onOpenCourseDetails: (String) -> Unit,
    onOpenExplore: () -> Unit,
    onOpenCertificates: () -> Unit,
    onContinueLearning: (courseId: String, lessonId: String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(HomeScreenTestTag),
        contentPadding = PaddingValues(horizontal = MentoraDimens.spacing.space4, vertical = MentoraDimens.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space5),
    ) {
        item { GreetingRow(firstName = uiState.firstName, bucket = uiState.greetingBucket) }

        item {
            MyLearningSection(
                state = uiState.myLearning,
                onRetry = onRetryMyLearning,
                onContinueLearning = onContinueLearning,
                onOpenCertificates = onOpenCertificates,
                onOpenExplore = onOpenExplore,
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.home_recommended_heading),
                    style = MaterialTheme.typography.titleLarge, // heading.h4.
                    color = MaterialTheme.colorScheme.onSurface,
                )
                MentoraTextButton(text = stringResource(R.string.home_recommended_see_all), onClick = onOpenExplore)
            }
        }

        recommendedItems(
            state = uiState.recommended,
            resolveThumbnailUrl = resolveThumbnailUrl,
            onRetry = onRetryRecommended,
            onOpenCourseDetails = onOpenCourseDetails,
        )
    }
}

@Composable
private fun GreetingRow(firstName: String, bucket: GreetingBucket, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().testTag(HomeGreetingRowTestTag),
        horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(name = firstName, size = MentoraAvatarSize.Medium)
        Column {
            Text(
                text = stringResource(greetingCopyFor(bucket)),
                style = MaterialTheme.typography.labelSmall, // typography.caption.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = firstName,
                style = MaterialTheme.typography.titleLarge, // heading.h4.
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun greetingCopyFor(bucket: GreetingBucket): Int = when (bucket) {
    GreetingBucket.Morning -> R.string.home_greeting_morning
    GreetingBucket.Afternoon -> R.string.home_greeting_afternoon
    GreetingBucket.Evening -> R.string.home_greeting_evening
    GreetingBucket.Night -> R.string.home_greeting_night
}

@Composable
private fun MyLearningSection(
    state: MyLearningLoadState,
    onRetry: () -> Unit,
    onContinueLearning: (String, String?) -> Unit,
    onOpenCertificates: () -> Unit,
    onOpenExplore: () -> Unit,
) {
    when (state) {
        is MyLearningLoadState.Loading -> SkeletonBlock(
            // A fixed height, not just `fillMaxWidth()` — `SkeletonBlock` has no intrinsic size on its
            // own (see its own kdoc), and the G3 join this module waits on (1 + N + N sequential
            // round trips) is the slowest load on this screen. 220dp approximates this module's real
            // rendered height (eyebrow + title + meta + progress bar + button, with padding) — not a
            // design token literal, since no skeleton-specific token exists for this module.
            modifier = Modifier.fillMaxWidth().height(220.dp).testTag(HomeMyLearningSkeletonTestTag),
            shape = MaterialTheme.shapes.medium,
        )

        is MyLearningLoadState.Error -> ErrorState(
            title = stringResource(R.string.home_my_learning_error_title),
            description = apiErrorMessage(state.code),
            onRetryClick = onRetry,
        )

        is MyLearningLoadState.Loaded -> {
            val inProgress = state.items.filter { it.progress.completionPercent < 100 }
            val completedCount = state.items.size - inProgress.size

            if (state.items.isEmpty()) {
                // Rank-1's own module-level empty state (`ux/SCREEN_UX_SPECS.md § 8` module 1) covers
                // the genuinely-no-enrollments case. A student with only COMPLETED courses (items
                // nonempty, inProgress empty) is handled below instead — this copy ("No courses yet")
                // would be factually wrong for them, and `mobile-home.json`'s own `statRow` section
                // (order 2, independent of the Continue Learning module) plus web's own
                // `DashboardScreen` (`myLearning.items.length === 0` is its ONLY empty-state gate, the
                // stat row renders unconditionally otherwise) both treat the two modules separately.
                EmptyState(
                    icon = MentoraIconName.MyLearning,
                    title = stringResource(R.string.home_empty_title),
                    description = stringResource(R.string.home_empty_description),
                    actionLabel = stringResource(R.string.home_explore_courses),
                    onActionClick = onOpenExplore,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4)) {
                    if (inProgress.isNotEmpty()) {
                        // The most RECENTLY enrolled still-unfinished course, not the oldest — see
                        // HomeViewModel's own kdoc. `GET /enrollments` sorts ascending by `_id`
                        // (`EnrollmentRepository.kt`), so `.first()` would deterministically surface
                        // the earliest, most-likely-abandoned enrollment instead.
                        val target = inProgress.last()
                        val position = resolveLessonPosition(target.course, target.progress)
                        ContinueLearningCard(
                            item = target,
                            position = position,
                            // The lesson [position] actually resolved to and displays — not the raw,
                            // possibly-null/stale `progress.currentLessonId` — so Resume opens the
                            // exact lesson the card's own "Lesson N of M" text names.
                            onResume = { onContinueLearning(target.course.id, position.currentLesson?.lessonId) },
                        )
                    }
                    // Independent of the Continue Learning module above (see this branch's own
                    // comment) — always shown once the student has at least one enrollment, in
                    // progress or not.
                    Row(
                        modifier = Modifier.fillMaxWidth().testTag(HomeStatRowTestTag),
                        horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
                    ) {
                        StatCard(
                            value = inProgress.size.toString(),
                            label = stringResource(R.string.home_stat_in_progress),
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            value = completedCount.toString(),
                            label = stringResource(R.string.home_stat_certificates),
                            modifier = Modifier.weight(1f)
                                .clickable(onClick = onOpenCertificates)
                                .testTag(HomeCertificatesStatCardTestTag),
                        )
                    }
                }
            }
        }
    }
}

/**
 * `mobile-home.json` lines 2043-2049 — the brand-tinted hero card. Uses `colorScheme.primaryContainer`
 * (not the showcase's literal full-saturation `brand`/`onbrand`) so [MentoraProgressBar]'s existing,
 * unmodified `Active` state (fill `color.brand.primary` on a `surfaceVariant` track) stays visible
 * against it — the same container/on-container pairing [com.mentora.android.ui.components.LearningPathCard]
 * already established, reused here rather than rebuilding [MentoraProgressBar] with a new inverse-tint
 * variant this task's brief does not authorize.
 */
@Composable
private fun ContinueLearningCard(
    item: LearningItemWithProgress,
    position: CourseLessonPosition,
    onResume: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progressFraction = item.progress.completionPercent / 100f

    Surface(
        modifier = modifier.fillMaxWidth().testTag(HomeContinueLearningCardTestTag),
        shape = MaterialTheme.shapes.medium, // radius.large (16).
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
        ) {
            Text(
                text = stringResource(R.string.home_continue_learning_eyebrow),
                style = MaterialTheme.typography.labelSmall, // typography.caption.
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Text(
                text = position.currentLesson?.title ?: item.course.title,
                style = MaterialTheme.typography.titleLarge, // heading.h4.
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.home_continue_learning_meta,
                    item.course.title,
                    position.currentLessonNumber,
                    position.totalLessons,
                ),
                style = MaterialTheme.typography.labelSmall, // typography.caption.
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            MentoraProgressBar(
                progress = progressFraction,
                contentDescriptionLabel = stringResource(
                    R.string.home_continue_learning_progress_content_description,
                    item.progress.completionPercent,
                ),
            )
            PrimaryButton(
                text = stringResource(R.string.home_resume_action),
                onClick = onResume,
                leadingIcon = MentoraIconName.Play,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun LazyListScope.recommendedItems(
    state: RecommendedState,
    resolveThumbnailUrl: (String) -> String,
    onRetry: () -> Unit,
    onOpenCourseDetails: (String) -> Unit,
) {
    when (state) {
        is RecommendedState.Loading -> item { CourseCardSkeleton(modifier = Modifier.fillMaxWidth()) }

        // Catalog-driven — an empty catalog is not expected at seed scale; no fabricated copy for it.
        is RecommendedState.Empty -> Unit

        is RecommendedState.Error -> item {
            ErrorState(
                title = stringResource(R.string.home_recommended_error_title),
                description = apiErrorMessage(state.code),
                onRetryClick = onRetry,
            )
        }

        is RecommendedState.Loaded -> items(state.items, key = { it.id }) { course ->
            RecommendedCourseRow(
                course = course,
                thumbnailUrl = course.thumbnailMediaId?.let(resolveThumbnailUrl),
                onClick = { onOpenCourseDetails(course.id) },
            )
        }
    }
}

/**
 * `mobile-home.json` lines 2055-2058 — the compact 88dp-thumbnail horizontal row (deliberately NOT
 * [com.mentora.android.ui.components.CourseCard]'s full vertical shell, which has no compact-row
 * variant in this kit — built from the same public [CourseThumbnail] primitive that card uses
 * internally, not a duplicate of that card's own layout logic).
 */
@Composable
private fun RecommendedCourseRow(course: CourseSummary, thumbnailUrl: String?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).testTag(HomeRecommendedRowTestTag),
        shape = MaterialTheme.shapes.medium, // radius.large.
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier.padding(MentoraDimens.spacing.space3),
            horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CourseThumbnail(
                mediaId = course.thumbnailMediaId,
                thumbnailUrl = thumbnailUrl,
                seed = course.id,
                categoryId = course.categoryId,
                contentDescription = stringResource(R.string.home_recommended_thumbnail_content_description, course.title),
                modifier = Modifier.width(88.dp).clip(MaterialTheme.shapes.small),
            )
            Column {
                Text(
                    text = course.title,
                    style = MaterialTheme.typography.titleMedium, // label.large-ish per spec's title row.
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = course.instructorName,
                    style = MaterialTheme.typography.labelSmall, // typography.caption.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val HomeScreenTestTag = "home-screen"
const val HomeGreetingRowTestTag = "home-greeting-row"
const val HomeMyLearningSkeletonTestTag = "home-my-learning-skeleton"
const val HomeStatRowTestTag = "home-stat-row"
const val HomeContinueLearningCardTestTag = "home-continue-learning-card"
const val HomeRecommendedRowTestTag = "home-recommended-row"
const val HomeCertificatesStatCardTestTag = "home-certificates-stat-card"
