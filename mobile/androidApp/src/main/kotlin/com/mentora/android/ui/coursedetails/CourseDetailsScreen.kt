package com.mentora.android.ui.coursedetails

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors
import com.mentora.android.theme.stateOpacities
import com.mentora.android.ui.components.Avatar
import com.mentora.android.ui.components.CategoryChip
import com.mentora.android.ui.components.CourseMetaRow
import com.mentora.android.ui.components.CourseThumbnail
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.MentoraAvatarSize
import com.mentora.android.ui.components.MentoraIcon
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.components.PrimaryButton
import com.mentora.android.ui.components.SkeletonBlock
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.Lesson
import com.mentora.shared.domain.model.Section
import java.util.Locale

/**
 * T10 — the real Course Details screen (`design-to-code/screens/mobile-course-details.json`,
 * `ux/SCREEN_UX_SPECS.md § 3`). Replaces the T6-era placeholder in
 * `ui/screens/PlaceholderScreens.kt`.
 *
 * **Tab-vs-linear-structure resolution (disclosed).** The showcase's own mobile frame shows a 3-tab
 * (Overview/Curriculum/Instructor) presentation — this screen deliberately does NOT build that. It
 * builds rank-1's (`ux/SCREEN_UX_SPECS.md § 3`) single linear scrolling structure instead: hero →
 * category chip → title/instructor → rating meta row → price-or-enrolled-state + primary CTA (sticky
 * footer) → description → curriculum outline → instructor detail block. This exact resolution is
 * anticipated and pre-authorized by the spec's own `knownGaps` note.
 *
 * **Header-icon resolution (disclosed).** The showcase's header row shows back+bookmark+share icons.
 * No bookmark/save/favorite affordance is built anywhere on this screen — "Favorites/Wishlist/Saved
 * Courses" is categorically out of MVP scope (`product/MVP_SCOPE.md`) and rank-1
 * (`ux/SCREEN_UX_SPECS.md § 3`) itself confirms "Secondary: none required in MVP (share is
 * optional/future)." The back button itself is NOT rebuilt here — `MentoraNavHost`'s existing
 * `MentoraTopBar` (real, `popBackStack()`-wired) already renders it for every pushed destination
 * including this one, satisfying rank-1's own "Mobile — pushed screen with a back affordance" header
 * requirement; duplicating a second back control inside this screen's own content would just be a
 * redundant control, not a more correct one. Share is OMITTED for this task (rank-1 calls it
 * optional/future) — building a real one would mean adding a new top-bar "actions" slot to the shared
 * `MentoraTopBar`/`MentoraNavHost` shell (used by every other screen too) purely for this one screen,
 * which is more shell-shape change than this task's scope warrants; a decorative no-op share icon is
 * explicitly disallowed by the task brief, so omitting it entirely is the honest choice here.
 *
 * **Bottom-nav-visibility.** Verified, not changed: `MentoraNavHost.showBottomNav` only hides the nav
 * for `CoursePlayer`/`Quiz` (`isFocusedLearningScreen`) — `CourseDetails` is not in that list, so the
 * nav stays visible here, stacked below this screen's own sticky CTA footer, exactly per
 * `mobile-course-details.json`'s `conflicts[0]` resolution.
 */
@Composable
fun CourseDetailsScreen(
    courseId: String,
    sdk: MentoraSdk,
    isAuthenticated: Boolean,
    onEnrollRequiringAuth: () -> Unit,
    onContinueLearning: () -> Unit,
    onOpenLesson: (lessonId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: CourseDetailsViewModel = viewModel(
        factory = CourseDetailsViewModel.Factory(sdk, courseId, isAuthenticated),
    )
    val uiState by viewModel.uiState.collectAsState()

    CourseDetailsScreenContent(
        uiState = uiState,
        onRetry = viewModel::onRetry,
        onEnrollRequiringAuth = onEnrollRequiringAuth,
        onContinueLearning = onContinueLearning,
        onOpenLesson = onOpenLesson,
        modifier = modifier,
    )
}

/** The stateless presentation half of [CourseDetailsScreen] — every rendering/interaction state is
 *  driven by a hand-built [CourseDetailsUiState], with no [MentoraSdk]/[CourseDetailsViewModel]
 *  involved (same split rationale as `ExploreScreen`/`ExploreScreenContent`). */
@Composable
internal fun CourseDetailsScreenContent(
    uiState: CourseDetailsUiState,
    onRetry: () -> Unit,
    onEnrollRequiringAuth: () -> Unit,
    onContinueLearning: () -> Unit,
    onOpenLesson: (lessonId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val courseState = uiState.course) {
        is CourseLoadState.Loading -> CourseDetailsLoadingSkeleton(modifier = modifier.fillMaxSize())

        is CourseLoadState.Error -> Box(
            modifier = modifier.fillMaxSize().testTag(CourseDetailsErrorTestTag),
            contentAlignment = Alignment.Center,
        ) {
            ErrorState(
                title = stringResource(R.string.course_details_error_title),
                description = apiErrorMessage(courseState.code),
                onRetryClick = onRetry,
                modifier = Modifier.padding(horizontal = MentoraDimens.spacing.space4),
            )
        }

        is CourseLoadState.Success -> {
            val categoryLabel = remember(uiState.categories, courseState.course.categoryId) {
                uiState.categories.find { it.id == courseState.course.categoryId }?.name
                    ?: courseState.course.categoryId
            }
            CourseDetailsSuccessContent(
                state = courseState,
                categoryLabel = categoryLabel,
                onEnrollRequiringAuth = onEnrollRequiringAuth,
                onContinueLearning = onContinueLearning,
                onOpenLesson = onOpenLesson,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun CourseDetailsLoadingSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.testTag(CourseDetailsLoadingTestTag),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
    ) {
        SkeletonBlock(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f))
        Column(
            modifier = Modifier.padding(horizontal = MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
        ) {
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.4f).height(20.dp))
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.8f).height(28.dp))
            SkeletonBlock(modifier = Modifier.fillMaxWidth(0.5f).height(18.dp))
            SkeletonBlock(modifier = Modifier.fillMaxWidth().height(80.dp), shape = MaterialTheme.shapes.small)
        }
    }
}

@Composable
private fun CourseDetailsSuccessContent(
    state: CourseLoadState.Success,
    categoryLabel: String,
    onEnrollRequiringAuth: () -> Unit,
    onContinueLearning: () -> Unit,
    onOpenLesson: (lessonId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val course = state.course

    // A plain `Column` with the scrolling body given `weight(1f)` and the price/CTA footer as its
    // un-weighted second child — the footer is thereby pinned to the bottom of THIS screen's own
    // bounds (never part of the scrollable content), and this screen's own bounds already sit
    // between `MentoraTopBar` and `MobileBottomNavigation` (both rendered one level up, by
    // `MentoraNavHost`'s own `Scaffold`) — so the footer visually lands directly ABOVE the bottom
    // nav, both simultaneously visible, exactly per `mobile-course-details.json`'s resolved
    // `conflicts[0]` ("render both the sticky CTA bar AND the bottom nav, stacked").
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth().testTag(CourseDetailsContentListTestTag),
            contentPadding = PaddingValues(bottom = MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
        ) {
            item { CourseDetailsHero(course = course, thumbnailUrl = state.thumbnailUrl) }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MentoraDimens.spacing.space4),
                    verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
                ) {
                    CategoryChip(label = categoryLabel, selected = false)
                    Text(
                        text = course.title,
                        style = MaterialTheme.typography.headlineLarge, // heading.h1.
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = course.instructorName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // ratingSeed is static seed data, never a real aggregated rating (Course's own
                    // kdoc) — rendered via the same plain "★ X.X" glyph CourseCard's own
                    // CourseMetaRow already established as this design system's disclosed treatment,
                    // never labeled as real reviews. The contentDescription below carries the
                    // "(demo rating)" disclosure for assistive tech, without changing the visible copy.
                    val ratingDescription = ratingContentDescription(course.ratingSeed)
                    CourseMetaRow(
                        rating = course.ratingSeed.toFloat(),
                        studentCount = null,
                        durationLabel = null,
                        modifier = Modifier.semantics(mergeDescendants = true) {
                            contentDescription = ratingDescription
                        },
                    )
                }
            }

            item {
                Text(
                    text = course.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = MentoraDimens.spacing.space4),
                )
            }

            item { CourseDetailsCurriculumHeading(course = course) }

            course.sections.forEach { section ->
                item { CourseDetailsSectionHeader(section = section) }
                items(section.lessons, key = { lesson -> lesson.lessonId }) { lesson ->
                    CourseDetailsLessonRow(
                        lesson = lesson,
                        isEnrolled = state.isEnrolled,
                        onClick = { onOpenLesson(lesson.lessonId) },
                    )
                }
            }

            item { CourseDetailsInstructorSection(instructorName = course.instructorName) }
        }

        CourseDetailsStickyFooter(
            course = course,
            isEnrolled = state.isEnrolled,
            cta = state.cta,
            onEnrollRequiringAuth = onEnrollRequiringAuth,
            onContinueLearning = onContinueLearning,
        )
    }
}

@Composable
private fun CourseDetailsHero(course: Course, thumbnailUrl: String?) {
    Box(modifier = Modifier.fillMaxWidth().testTag(CourseDetailsHeroTestTag)) {
        CourseThumbnail(
            mediaId = course.thumbnailMediaId,
            thumbnailUrl = thumbnailUrl,
            seed = course.id,
            categoryId = course.categoryId,
            contentDescription = stringResource(R.string.course_details_hero_content_description, course.title),
            modifier = Modifier.fillMaxWidth(),
        )
        // Decorative center play-preview overlay (mobile-course-details.json § hero: "a play-preview
        // treatment"). Purely visual — tapping it does not play anything; Course Player's real
        // playback wiring is a later task. Not clickable at all, so there is nothing to wire.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(56.dp)
                .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                .semantics { contentDescription = "" },
            contentAlignment = Alignment.Center,
        ) {
            MentoraIcon(
                name = MentoraIconName.Play,
                contentDescription = null,
                size = MentoraDimens.iconSize.large,
                tint = Color.White,
            )
        }
    }
}

/**
 * The sticky price/CTA footer — a sibling of (not an item inside) the scrolling `LazyColumn` in
 * [CourseDetailsSuccessContent], per that composable's own kdoc: pinned to this screen's own bottom
 * edge, never part of the scrollable body, so it stays reachable "without scrolling past the fold"
 * (`ux/MOBILE_UX.md § 4`) and renders directly above `MobileBottomNavigation`, both simultaneously
 * visible — `mobile-course-details.json`'s resolved `conflicts[0]`.
 */
@Composable
private fun CourseDetailsStickyFooter(
    course: Course,
    isEnrolled: Boolean,
    cta: CourseDetailsCtaState,
    onEnrollRequiringAuth: () -> Unit,
    onContinueLearning: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag(CourseDetailsPriceCtaSectionTestTag),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
        ) {
            if (isEnrolled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
                ) {
                    MentoraIcon(
                        name = MentoraIconName.CheckCircle,
                        contentDescription = null,
                        size = MentoraDimens.iconSize.medium,
                        tint = MaterialTheme.extendedColors.success,
                    )
                    Text(
                        text = stringResource(R.string.course_details_enrolled_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                // Deliberately no ProgressBar here — a real per-course progress bar needs
                // `sdk.progress` (a later task's wiring); this task does not fabricate a percentage
                // (task brief's own explicit instruction).
            } else {
                Text(
                    text = formatDemoPrice(course.priceDisplay.amount, course.priceDisplay.currency),
                    style = MaterialTheme.typography.headlineSmall, // heading.h3.
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.course_details_demo_price_caption),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val (label, onClick) = when (cta) {
                CourseDetailsCtaState.LoginToEnroll -> stringResource(R.string.course_details_cta_login_to_enroll) to onEnrollRequiringAuth
                CourseDetailsCtaState.Enroll -> stringResource(R.string.course_details_cta_enroll) to onEnrollRequiringAuth
                CourseDetailsCtaState.ContinueLearning -> stringResource(R.string.course_details_cta_continue_learning) to onContinueLearning
            }
            PrimaryButton(
                text = label,
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().testTag(CourseDetailsCtaButtonTestTag),
            )
        }
    }
}

@Composable
private fun CourseDetailsCurriculumHeading(course: Course) {
    val lessonCount = remember(course) { course.sections.sumOf { it.lessons.size } }
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MentoraDimens.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1),
    ) {
        Text(
            text = stringResource(R.string.course_details_curriculum_heading),
            style = MaterialTheme.typography.headlineSmall, // heading.h3.
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            // `%1$s` + a plain `.toString()`, not `%1$d` — a bare Int-to-string is trivially
            // Western-numeral in every locale (`design-system/LOCALIZATION.md § 8`), while `%d`
            // formats through the current Locale's own numbering system (Arabic-Indic digits under
            // `ar`) — same fix/rationale as `formatDemoPrice`'s own kdoc below.
            text = stringResource(R.string.course_details_curriculum_lesson_count, lessonCount.toString()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CourseDetailsSectionHeader(section: Section) {
    Text(
        text = section.title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxWidth().padding(horizontal = MentoraDimens.spacing.space4),
    )
}

/**
 * A lesson row shows a play affordance and is tappable ONLY when [isEnrolled] — "no video access/
 * play affordance until enrolled" (`ux/SCREEN_UX_SPECS.md § 3`). No lock-icon glyph exists in the
 * ported 42-icon set (G8) — per that gap's own precedent (a disclosed simplification, not a missing
 * feature), the locked state is rendered by simply OMITTING the play affordance and dimming the row's
 * text color, rather than fabricating a new icon this task's brief doesn't authorize adding.
 */
@Composable
private fun CourseDetailsLessonRow(lesson: Lesson, isEnrolled: Boolean, onClick: () -> Unit) {
    val opacities = MaterialTheme.stateOpacities
    val textColor = if (isEnrolled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = opacities.disabledContent)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isEnrolled) it.clickable(onClick = onClick) else it }
            .padding(horizontal = MentoraDimens.spacing.space4, vertical = MentoraDimens.spacing.space2)
            .testTag(courseDetailsLessonRowTestTag(lesson.lessonId)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
    ) {
        Text(
            text = lesson.title,
            style = MaterialTheme.typography.bodySmall,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        if (isEnrolled) {
            MentoraIcon(
                name = MentoraIconName.Play,
                contentDescription = null,
                size = MentoraDimens.iconSize.small,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag(courseDetailsLessonPlayIconTestTag(lesson.lessonId)),
            )
        }
    }
}

/**
 * The instructor detail block (rank-1 item 9, "InstructorCard, expanded"). Built inline here rather
 * than as a new reusable `ui/components/InstructorCard.kt` — [Course]/`CourseSummary` carry only
 * `instructorName`/`instructorId`, no title/expertise/course-count field anywhere in the backend
 * (same disclosed gap Web's own `InstructorCard` already discloses, D37) — so a fuller reusable card
 * would have nothing more to show than [Avatar] (`large`) + name, which does not earn a new shared
 * component file over reusing [Avatar] directly, per the task brief's own "your call" allowance.
 */
@Composable
private fun CourseDetailsInstructorSection(instructorName: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = MentoraDimens.spacing.space4),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
    ) {
        Text(
            text = stringResource(R.string.course_details_about_instructor_heading),
            style = MaterialTheme.typography.headlineSmall, // heading.h3.
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
            modifier = Modifier.testTag(CourseDetailsInstructorSectionTestTag),
        ) {
            Avatar(name = instructorName, size = MentoraAvatarSize.Large)
            Text(
                text = instructorName,
                style = MaterialTheme.typography.titleLarge, // heading.h4.
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** `web/src/lib/i18n/format.ts`'s `formatPrice` does no minor-unit division — `PriceDisplay.amount`
 *  is already the whole display amount (verified: `SeedData.kt`/backend fixtures pass e.g. `amount =
 *  49900` for a real ~499 EGP-range course price, and web's own `Intl.NumberFormat` call formats it
 *  directly with zero fraction digits, producing "EGP 49900" — not "EGP 499.00"). This mirrors that
 *  exact behavior with a plain, disclosed "{CODE} {amount}" format rather than
 *  `NumberFormat.getCurrencyInstance` — the seed catalog's only currency (`EGP`) has no widely
 *  recognized symbol, so `Intl`'s own output already reads as "EGP <amount>" in `en`, and a bare
 *  Int-to-string is trivially Western-numeral in every locale (`design-system/LOCALIZATION.md § 8`),
 *  with no ICU/Locale-numbering-system plumbing needed to guarantee that.
 */
private fun formatDemoPrice(amount: Int, currency: String): String = "$currency $amount"

// T19 review fix (MEDIUM, D94): `course_details_rating_content_description` used to embed a raw
// `%1$.1f` placeholder — `Resources.getString(id, args)` formats using the CONFIGURATION's own
// locale (genuinely Arabic under `LocalizedContent.kt` since Task 18, not just the device's), which
// renders Arabic-Indic digits for this one argument while the string's own hardcoded "out of 5"
// stayed Western — a single sentence mixing two numbering systems. Pre-formatting with `Locale.US`
// (same fix shape as `CourseCard`'s identical rating text) and passing the result through as `%1$s`
// keeps this argument Western regardless of UI locale, matching every other numeral in this app.
@Composable
private fun ratingContentDescription(ratingSeed: Double): String =
    stringResource(R.string.course_details_rating_content_description, String.format(Locale.US, "%.1f", ratingSeed))

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val CourseDetailsLoadingTestTag = "course-details-loading"
const val CourseDetailsErrorTestTag = "course-details-error"
const val CourseDetailsContentListTestTag = "course-details-content-list"
const val CourseDetailsHeroTestTag = "course-details-hero"
const val CourseDetailsPriceCtaSectionTestTag = "course-details-price-cta-section"
const val CourseDetailsCtaButtonTestTag = "course-details-cta-button"
const val CourseDetailsInstructorSectionTestTag = "course-details-instructor-section"

fun courseDetailsLessonRowTestTag(lessonId: String): String = "course-details-lesson-$lessonId"
fun courseDetailsLessonPlayIconTestTag(lessonId: String): String = "course-details-lesson-$lessonId-play"
