package com.mentora.android.ui.learningpathdetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors
import com.mentora.android.ui.components.CourseCardSkeleton
import com.mentora.android.ui.components.CourseThumbnail
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.MentoraIcon
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.components.MentoraProgressBar
import com.mentora.android.ui.components.MentoraSnackbarHost
import com.mentora.android.ui.components.PrimaryButton
import com.mentora.android.ui.components.SecondaryButton
import com.mentora.android.ui.components.SkeletonBlock
import com.mentora.android.ui.components.TonalButton
import com.mentora.android.ui.components.showMentoraSnackbar
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk

/**
 * T16 — the real Learning Path Details screen (`ux/SCREEN_UX_SPECS.md § 5` — no exact-showcase
 * mockup exists, `learning-path-details.json` is `"referenceType": "ux-only"`, confirmed before
 * implementation same as every other ux-only screen this phase). Replaces the T6-era placeholder in
 * `ui/screens/PlaceholderScreens.kt`.
 *
 * **Content order (rank-1):** hero (title/description/course count) → path-level [MentoraProgressBar]
 * IF following, else a "Follow Path" [PrimaryButton] → an ORDERED, numbered list of member courses,
 * each annotated Completed/Current/Upcoming via icon+text+color TOGETHER (never color alone — see
 * [LearningPathDetailsStatusRow]). Member-course list stays single-column even on Desktop (a
 * deliberate exception to the standard course grid, per that spec section's own "Responsive" note) —
 * moot on Android (this is a phone-only build), but the plain [LazyColumn] below is that single-column
 * shape regardless.
 *
 * **`LearningPathCard` header-variant decision (disclosed, the task's own "your call").** Built a
 * bespoke hero block from primitives ([Text]/[MentoraProgressBar]/[PrimaryButton]) instead of
 * extending the shared `LearningPathCard` (Task 8) with an optional progress slot — the locked ASCII
 * mockup (`ux/SCREEN_UX_SPECS.md § 5`) renders this hero as plain page content (title/description/
 * progress directly on the screen background), NOT inside `LearningPathCard`'s own
 * `color.brand.primaryContainer` tinted card shell (that shell is Explore's own list-item treatment,
 * a different context). Building bespoke here avoids widening a Task 8 kit component for a shape it
 * was never designed to render, mirroring the exact "either is fine, disclose whichever you pick"
 * precedent Task 15 used for `CertificatePreviewPlaceholder`.
 *
 * **Member-course card decision (disclosed).** Also NOT `CourseCard`/`CourseProgressCard` directly —
 * both hard-require a `categoryLabel` for their built-in artwork category-chip overlay
 * ([com.mentora.android.ui.components.CourseArtworkWithChip]), but
 * [com.mentora.shared.domain.model.LearningPathCourse] carries no `categoryId` at all (that model's
 * own kdoc — a deliberately narrower backend projection than [com.mentora.shared.domain.model.Course]).
 * Passing an empty label would render a visible, contentless chip pill artifact rather than an
 * honestly omitted one. [LearningPathMemberCourseCard] below instead reuses the shared
 * [CourseThumbnail] primitive directly (no chip requirement) plus [TonalButton]/[MentoraIcon], mirroring
 * `BaseCourseCard`'s own shell (radius/border/padding) — the same "screen-local composable built from
 * primitives, not a new kit component or a component hack" precedent `MyLearningScreen.kt`'s own
 * `CertificateReadyRow` and `CourseDetailsScreen.kt`'s own instructor section already used.
 *
 * **Per-course CTA decision (disclosed).** `ux/SCREEN_UX_SPECS.md § 5`'s own "Exit destinations:
 * Course Details (per member course)" note is read literally here: BOTH the row's own tap target AND
 * its enroll/continue action button navigate to [onOpenCourseDetails] — never straight into Course
 * Player, and never duplicating Course Details' own real enroll-vs-continue/guest-gating network calls
 * a second time on this screen. This keeps the per-course action label a simple enrolled/not-enrolled
 * switch (never a 3-state guest/auth/enrolled machine like `CourseDetailsCtaState`) — the real guest
 * gate is Course Details' own job once the student lands there.
 *
 * **Unfollow affordance (disclosed addition beyond the literal ASCII mock) — see
 * `learning_path_details_unfollow_action`'s own string kdoc for the full rationale.**
 *
 * **Guest gating** mirrors `CourseDetailsScreen`'s established mechanism exactly: [isAuthenticated] is
 * threaded down from `MentoraNavHost` (`authState is AuthState.Authenticated`), and
 * [onFollowRequiringAuth] is the identical `requireAuth(...)`-callback wiring
 * `CourseDetailsScreen.onEnrollRequiringAuth` already uses (same Login nav target).
 */
@Composable
fun LearningPathDetailsScreen(
    pathId: String,
    sdk: MentoraSdk,
    isAuthenticated: Boolean,
    onFollowRequiringAuth: () -> Unit,
    onOpenCourseDetails: (courseId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LearningPathDetailsViewModel = viewModel(
        factory = LearningPathDetailsViewModel.Factory(sdk, pathId, isAuthenticated),
    )
    val uiState by viewModel.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val followError = (uiState.path as? LearningPathLoadState.Success)?.followError
    if (followError != null) {
        val message = apiErrorMessage(followError)
        LaunchedEffect(followError) {
            snackbarHostState.showMentoraSnackbar(message)
            viewModel.onFollowErrorDismissed()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { MentoraSnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        LearningPathDetailsContent(
            uiState = uiState,
            resolveThumbnailUrl = viewModel.resolveThumbnailUrl,
            isAuthenticated = isAuthenticated,
            onRetry = viewModel::onRetry,
            onFollowToggleClicked = viewModel::onFollowToggleClicked,
            onFollowRequiringAuth = onFollowRequiringAuth,
            onOpenCourseDetails = onOpenCourseDetails,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

/** The stateless presentation half of [LearningPathDetailsScreen] — same split rationale as every
 *  other T7+ screen in this phase. */
@Composable
internal fun LearningPathDetailsContent(
    uiState: LearningPathDetailsUiState,
    resolveThumbnailUrl: (String) -> String,
    isAuthenticated: Boolean,
    onRetry: () -> Unit,
    onFollowToggleClicked: () -> Unit,
    onFollowRequiringAuth: () -> Unit,
    onOpenCourseDetails: (courseId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val state = uiState.path) {
        is LearningPathLoadState.Loading -> LearningPathDetailsLoadingSkeleton(modifier = modifier.fillMaxSize())

        is LearningPathLoadState.Error -> Box(
            modifier = modifier.fillMaxSize().testTag(LearningPathDetailsErrorTestTag),
            contentAlignment = Alignment.Center,
        ) {
            ErrorState(
                title = stringResource(R.string.learning_path_details_error_title),
                description = apiErrorMessage(state.code),
                onRetryClick = onRetry,
                modifier = Modifier.padding(horizontal = MentoraDimens.spacing.space4),
            )
        }

        is LearningPathLoadState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize().testTag(LearningPathDetailsContentListTestTag),
            contentPadding = PaddingValues(
                horizontal = MentoraDimens.spacing.space4,
                vertical = MentoraDimens.spacing.space4,
            ),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
        ) {
            item {
                LearningPathDetailsHero(
                    state = state,
                    onFollowToggleClicked = { if (isAuthenticated) onFollowToggleClicked() else onFollowRequiringAuth() },
                )
            }

            itemsIndexed(state.courses) { index, courseItem ->
                LearningPathMemberCourseCard(
                    sequenceNumber = index + 1,
                    item = courseItem,
                    thumbnailUrl = courseItem.course.thumbnailMediaId?.let(resolveThumbnailUrl),
                    onClick = { onOpenCourseDetails(courseItem.course.id) },
                )
            }
        }
    }
}

@Composable
private fun LearningPathDetailsLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag(LearningPathDetailsLoadingTestTag).padding(MentoraDimens.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.6f).height(28.dp))
        SkeletonBlock(modifier = Modifier.fillMaxWidth(0.9f).height(18.dp))
        SkeletonBlock(modifier = Modifier.fillMaxWidth().height(8.dp), shape = MaterialTheme.shapes.small)
        repeat(3) { CourseCardSkeleton(modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun LearningPathDetailsHero(
    state: LearningPathLoadState.Success,
    onFollowToggleClicked: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().testTag(LearningPathDetailsHeroTestTag),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
    ) {
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineLarge, // heading.h1.
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = state.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // `%1$s` + `.toString()`, not `%1$d` — Western-numeral in every locale regardless of the
        // current Locale's own numbering system (design-system/LOCALIZATION.md § 8).
        Text(
            text = stringResource(R.string.learning_path_details_course_count, state.courses.size.toString()),
            style = MaterialTheme.typography.labelSmall, // typography.caption.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (state.isFollowing) {
            val completedCount = state.courses.count { it.status == CourseSequenceStatus.Completed }
            val progressLabel = stringResource(
                R.string.learning_path_details_progress_label,
                completedCount.toString(),
                state.courses.size.toString(),
            )
            MentoraProgressBar(
                progress = (state.progressPercent ?: 0) / 100f,
                contentDescriptionLabel = progressLabel,
                modifier = Modifier.testTag(LearningPathDetailsProgressBarTestTag),
            )
            Text(
                text = progressLabel,
                style = MaterialTheme.typography.labelSmall, // typography.caption.
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Disclosed addition beyond the literal ASCII mock — see this file's own kdoc and
            // `learning_path_details_unfollow_action`'s string kdoc for the full rationale.
            SecondaryButton(
                text = stringResource(R.string.learning_path_details_unfollow_action),
                onClick = onFollowToggleClicked,
                loading = state.followInFlight,
                enabled = !state.followInFlight,
                modifier = Modifier.testTag(LearningPathDetailsFollowButtonTestTag),
            )
        } else {
            PrimaryButton(
                text = stringResource(R.string.learning_path_details_follow_action),
                onClick = onFollowToggleClicked,
                loading = state.followInFlight,
                enabled = !state.followInFlight,
                modifier = Modifier.fillMaxWidth().testTag(LearningPathDetailsFollowButtonTestTag),
            )
        }
    }
}

/**
 * Per-course status row — icon + label + color together, never color alone (this design system's
 * global accessibility rule, e.g. `QuizResultsScreen`'s own `QuizResultsBreakdownRow`).
 * [CourseSequenceStatus.Upcoming] renders no icon (`ux/SCREEN_UX_SPECS.md § 5`'s own "no special
 * badge, default treatment" line for that one state) but still renders the plain status TEXT — text
 * alone already satisfies "never color alone" without needing a badge/icon for every state.
 */
@Composable
private fun LearningPathDetailsStatusRow(sequenceNumber: Int, status: CourseSequenceStatus) {
    val extended = MaterialTheme.extendedColors
    val colorScheme = MaterialTheme.colorScheme
    val (icon, color, labelRes) = when (status) {
        CourseSequenceStatus.Completed -> Triple(MentoraIconName.CheckCircle, extended.success, R.string.learning_path_details_status_completed)
        CourseSequenceStatus.Current -> Triple(MentoraIconName.Play, colorScheme.primary, R.string.learning_path_details_status_current)
        CourseSequenceStatus.Upcoming -> Triple(null, colorScheme.onSurfaceVariant, R.string.learning_path_details_status_upcoming)
    }
    val label = stringResource(labelRes)
    // Sequence numbers are locked Western-numeral text (`design-system/LOCALIZATION.md § 8`) — a plain
    // `Int.toString()` — and read 1→2→3 logically regardless of layout direction
    // (`ux/SCREEN_UX_SPECS.md § 5`'s own RTL note: "numbering is never reversed"); this Row's default
    // start-alignment already mirrors its own child order under RTL without any extra handling.
    //
    // Phase 4 polish follow-up: the "1." Text itself still needs `⁦`/`⁩` (LTR isolate) around
    // it, wrapping it as a single opaque LTR run — without this, the plain digit+period string sits
    // directly inside an RTL-direction Composable/paragraph in Arabic, and the Unicode Bidi Algorithm
    // (UAX #9) reorders the trailing neutral "." to the visual START of that run (rendering ".1"
    // instead of "1.") since a lone digit+neutral run with no explicit embedding takes its resolved
    // direction from the surrounding RTL context. The isolate marks force this exact run to resolve as
    // its own independent LTR unit regardless of the ambient LayoutDirection, matching what the
    // "numbering is never reversed" contract above already assumed was happening.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) { contentDescription = "$sequenceNumber. $label" }
            .testTag(learningPathDetailsStatusRowTestTag(sequenceNumber)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
    ) {
        Text(
            text = "⁦$sequenceNumber.⁩",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        icon?.let {
            MentoraIcon(name = it, contentDescription = null, size = MentoraDimens.iconSize.small, tint = color)
        }
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** See this file's own top-level kdoc ("Member-course card decision") for why this is a bespoke
 *  primitives-built card rather than `CourseCard`/`CourseProgressCard` directly. [item]'s own
 *  [CourseSequenceStatus.Current] is additionally emphasized with a `border.focus`-colored outline
 *  (`ux/SCREEN_UX_SPECS.md § 5`'s own "visually emphasized (e.g. a border.focus-colored outline...)"
 *  option) — exactly one course holds this state at a time, by [LearningPathDetailsViewModel]'s own
 *  construction. */
@Composable
private fun LearningPathMemberCourseCard(
    sequenceNumber: Int,
    item: LearningPathMemberCourseUi,
    thumbnailUrl: String?,
    onClick: () -> Unit,
) {
    val isCurrent = item.status == CourseSequenceStatus.Current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let {
                if (isCurrent) {
                    it.border(
                        BorderStroke(MentoraDimens.borderWidthFocus, MaterialTheme.colorScheme.primary),
                        MaterialTheme.shapes.medium,
                    ).padding(MentoraDimens.spacing.space1)
                } else {
                    it
                }
            },
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
    ) {
        LearningPathDetailsStatusRow(sequenceNumber = sequenceNumber, status = item.status)

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .testTag(learningPathDetailsCourseRowTestTag(item.course.id)),
            shape = MaterialTheme.shapes.medium, // radius.large — matches BaseCourseCard's own shape.
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Column {
                CourseThumbnail(
                    mediaId = item.course.thumbnailMediaId,
                    thumbnailUrl = thumbnailUrl,
                    seed = item.course.id,
                    categoryId = null, // LearningPathCourse carries no categoryId — falls back to seed.
                    contentDescription = stringResource(
                        R.string.learning_path_details_course_thumbnail_content_description,
                        item.course.title,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Column(
                    modifier = Modifier.padding(MentoraDimens.spacing.space4),
                    verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
                ) {
                    Text(
                        text = item.course.title,
                        style = MaterialTheme.typography.titleLarge, // heading.h4.
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (item.isEnrolled && item.completionPercent != null) {
                        val percentLabel = stringResource(
                            R.string.learning_path_details_course_progress_content_description,
                            item.completionPercent.toString(),
                        )
                        MentoraProgressBar(progress = item.completionPercent / 100f, contentDescriptionLabel = percentLabel)
                        Text(text = percentLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TonalButton(
                        text = stringResource(
                            if (item.isEnrolled) {
                                R.string.learning_path_details_course_continue_action
                            } else {
                                R.string.learning_path_details_course_enroll_action
                            },
                        ),
                        onClick = onClick,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val LearningPathDetailsLoadingTestTag = "learning-path-details-loading"
const val LearningPathDetailsErrorTestTag = "learning-path-details-error"
const val LearningPathDetailsContentListTestTag = "learning-path-details-content-list"
const val LearningPathDetailsHeroTestTag = "learning-path-details-hero"
const val LearningPathDetailsProgressBarTestTag = "learning-path-details-progress-bar"
const val LearningPathDetailsFollowButtonTestTag = "learning-path-details-follow-button"

fun learningPathDetailsStatusRowTestTag(sequenceNumber: Int): String = "learning-path-details-status-row-$sequenceNumber"
fun learningPathDetailsCourseRowTestTag(courseId: String): String = "learning-path-details-course-row-$courseId"
