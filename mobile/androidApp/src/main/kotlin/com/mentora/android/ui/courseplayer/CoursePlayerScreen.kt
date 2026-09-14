package com.mentora.android.ui.courseplayer

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.stateOpacities
import com.mentora.android.ui.components.ErrorState
import com.mentora.android.ui.components.FullScreenLoadingState
import com.mentora.android.ui.components.MentoraIcon
import com.mentora.android.ui.components.MentoraIconButton
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.components.MentoraProgressBar
import com.mentora.android.ui.components.MentoraSnackbarHost
import com.mentora.android.ui.components.MentoraTextButton
import com.mentora.android.ui.components.PrimaryButton
import com.mentora.android.ui.components.SuccessState
import com.mentora.android.ui.components.showMentoraSnackbar
import com.mentora.android.ui.error.apiErrorMessage
import com.mentora.shared.MentoraSdk

/**
 * Task 13 "C3" — the real Course Player screen (`design-to-code/screens/mobile-course-player.json`,
 * `ux/SCREEN_UX_SPECS.md § 10`). Replaces the T6-era placeholder in `ui/screens/PlaceholderScreens.kt`.
 * Wires [CoursePlayerViewModel] via [CoursePlayerViewModel.Factory] — the same
 * `viewModel(factory = X.Factory(...))` idiom as every other real screen — and renders its OWN top bar
 * (`execution/DECISIONS_LOG.md` D85 Decision 10: Course Player is chromeless at the shell level,
 * per-screen data the shared `MentoraTopBar` cannot carry).
 *
 * **First screen in this app needing an `Application`, not just a [MentoraSdk]** — [CoursePlayerViewModel
 * .Factory] needs one (D85 Decision 1: the `ExoPlayer` binding is built from the APPLICATION `Context`,
 * never the Activity, so it outlives Activity re-creation on rotation). `LocalContext.current
 * .applicationContext as Application` is the plain, correct cast (see that Factory's own kdoc — it
 * genuinely wants the already-application-scoped instance, not a `MentoraApplication` downcast, which
 * would `ClassCastException` under the instrumented test harness's `NoOpApplicationTestRunner`, T11/D83).
 *
 * **Root test tag present in EVERY [CoursePlayerContentState], including [CoursePlayerContentState
 * .Error]** — D85's own explicit requirement: `NavigationShellTest`'s harness performs no real login,
 * so `getCourseProgress` genuinely fails with `ForbiddenNotEnrolled` there (same pattern
 * `MyLearningScreenTestTag` already established, "present regardless of async load state"). Achieved
 * here by tagging the outer [Scaffold] itself (an ancestor of every branch below), not each branch
 * individually.
 *
 * **Lifecycle wiring (D85 Decision 6's "Visibility hooks"), split across two different lifetimes
 * (round 6 review finding).** A [DisposableEffect]'s `onDispose`, scoped to THIS composable's own
 * composition, flushes progress on leaving composition (a same-app tab switch, which keeps the
 * ViewModel — and the player — alive per that decision's own note) — guarded by
 * `!activity.isChangingConfigurations` (D85's own requirement; this app's `AndroidManifest.xml` sets
 * neither `screenOrientation` nor `configChanges`, so a genuine rotation recreates this Activity and
 * would otherwise fire a redundant flush on every rotation). Pausing playback on backgrounding the
 * whole app is NOT wired here at all — [CoursePlayerViewModel] registers that itself against
 * `ProcessLifecycleOwner` (see its `registerProcessBackgroundListener` constructor param kdoc), a
 * lifetime that matches the ViewModel/player rather than this composable's composition, so it keeps
 * working even after a tab switch or a footer-driven push to Quiz disposes this composable.
 */
@Composable
fun CoursePlayerScreen(
    courseId: String,
    lessonId: String?,
    sdk: MentoraSdk,
    onBack: () -> Unit,
    onOpenAiTutor: () -> Unit,
    onTakeQuiz: () -> Unit,
    onBackToMyLearning: () -> Unit,
    onOpenCertificates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: CoursePlayerViewModel = viewModel(
        factory = CoursePlayerViewModel.Factory(sdk, application, courseId, lessonId),
    )
    val uiState by viewModel.uiState.collectAsState()

    // Task 14 — closes the gap `quizNotYetPassed`'s own kdoc below discloses: this re-fires on EVERY
    // (re-)entry into this composable's composition, including a return from a pushed Quiz destination
    // via back (Navigation-Compose disposes the covered destination's composition while another is
    // pushed on top, the same "leaving composition on a push" assumption `onScreenLeaving`'s own
    // `DisposableEffect(Unit)` above already relies on — verified against this exact file's own
    // established convention, not a new assumption). Firing on the very first composition too is
    // harmless — `CoursePlayerViewModel.refreshQuizStatus` just re-fetches the same progress/quiz
    // `load()` already fetched moments earlier.
    LaunchedEffect(Unit) { viewModel.refreshQuizStatus() }

    CoursePlayerLifecycleEffects(onScreenLeaving = viewModel::onScreenLeaving)

    val snackbarHostState = remember { SnackbarHostState() }
    val completionError = (uiState.content as? CoursePlayerContentState.Ready)?.state?.completionError
    if (completionError != null) {
        val message = apiErrorMessage(completionError)
        LaunchedEffect(completionError) {
            snackbarHostState.showMentoraSnackbar(message)
            viewModel.onCompletionErrorDismissed()
        }
    }

    var sheetVisible by rememberSaveable { mutableStateOf(false) }
    val onLessonPicked: (String) -> Unit = { lesson -> viewModel.onLessonSelected(lesson); sheetVisible = false }
    val onQuizFromSheet: () -> Unit = { onTakeQuiz(); sheetVisible = false }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag(CoursePlayerScreenTestTag),
        // Review finding (round 5, MEDIUM): this screen is chromeless (`MentoraNavHost.kt`'s
        // `isChromeless` set), hosted directly by the outer app content with no enclosing Scaffold of
        // its own consuming system-bar insets first — but `CoursePlayerTopBar` ALSO applied its own
        // `WindowInsets.statusBars` padding (see its own kdoc below), so leaving this Scaffold's
        // `contentWindowInsets` at its default (`WindowInsets.systemBars`) double-applied the status-bar
        // inset: once via `innerPadding` (from this Scaffold, since `topBar`'s own height doesn't
        // consume it) and once via the top bar's explicit padding. Zeroing it here makes the top bar's
        // own explicit inset handling the single source of truth.
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CoursePlayerTopBar(
                content = uiState.content,
                onBack = onBack,
                onOpenAiTutor = onOpenAiTutor,
            )
        },
        snackbarHost = { MentoraSnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        when (val content = uiState.content) {
            is CoursePlayerContentState.Loading -> FullScreenLoadingState(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentDescriptionLabel = stringResource(R.string.course_player_loading_content_description),
            )

            is CoursePlayerContentState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                ErrorState(
                    title = stringResource(R.string.course_player_error_title),
                    description = apiErrorMessage(content.code),
                    onRetryClick = viewModel::onRetry,
                    retryLabel = stringResource(R.string.course_player_retry_action),
                    modifier = Modifier.padding(horizontal = MentoraDimens.spacing.space4),
                )
            }

            is CoursePlayerContentState.Ready -> CoursePlayerReadyContent(
                state = content.state,
                viewModel = viewModel,
                onTakeQuiz = onTakeQuiz,
                sheetVisible = sheetVisible,
                onOpenSheet = { sheetVisible = true },
                onDismissSheet = { sheetVisible = false },
                onLessonPicked = onLessonPicked,
                onQuizFromSheet = onQuizFromSheet,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )

            is CoursePlayerContentState.CourseCompleted -> CoursePlayerCompletedContent(
                state = content.state,
                onBackToMyLearning = onBackToMyLearning,
                onOpenCertificates = onOpenCertificates,
                onTakeQuiz = onTakeQuiz,
                modifier = Modifier.fillMaxSize().padding(innerPadding),
            )
        }
    }
}

/**
 * Review finding (round 5, HIGH; amended round 6): a first draft observed [LocalLifecycleOwner] for
 * BOTH hooks — but inside a `NavHost` destination that owner is the **`NavBackStackEntry`'s own**
 * `Lifecycle` (navigation-compose's `LocalOwnersProvider`), which drops to `CREATED` (dispatching
 * `ON_STOP`) on ANY forward navigation, INCLUDING a same-app bottom-nav tab switch (`popUpTo(saveState
 * = true)` saves the entry rather than destroying it, but its `Lifecycle` still pauses). That
 * collapsed D85 Decision 6's two deliberately different hooks — `onScreenStopped` for backgrounding
 * the whole app, [onScreenLeaving] for a same-app tab switch that D85 explicitly wants to keep
 * playback running through — onto the exact same trigger.
 *
 * A round-5 fix moved `onScreenStopped` to watch the real host [Activity]'s own `Lifecycle` instead —
 * correct in isolation, but still registered from a `DisposableEffect` **scoped to this composable's
 * own composition**, which a same-app tab switch or a footer-driven push to Quiz also disposes (this
 * screen's content stops being the visible back-stack entry's content) even though
 * [CoursePlayerViewModel]/its [PlaybackController] stay alive. Backgrounding the whole app AFTER
 * leaving this screen that way then reached no observer at all — `controller.pause()` never ran,
 * leaving audio playing behind the home screen indefinitely (round-6 review finding, MEDIUM: exactly
 * the failure D85 Decision 1's audio-attributes note says must not happen).
 *
 * Round 6's fix: `onScreenStopped` moved entirely into [CoursePlayerViewModel] itself, observing
 * `ProcessLifecycleOwner` (see that class's `registerProcessBackgroundListener` constructor param
 * kdoc) — a lifetime that matches the ViewModel/controller, not this composable's composition. C3 no
 * longer calls it at all. [onScreenLeaving] alone remains this composable's own responsibility, since
 * *its* intended lifetime genuinely IS "this composable is no longer the visible content" — a same-app
 * tab switch must still flush (D85 write-schedule item (5)) even though playback keeps running.
 */
@Composable
private fun CoursePlayerLifecycleEffects(onScreenLeaving: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isChangingConfigurations != true) {
                onScreenLeaving()
            }
        }
    }
}

/** Unwraps a possible `ContextWrapper` chain (e.g. a Compose `ViewTreeLifecycleOwner`/theme
 *  wrapper) to find the real host [Activity] — [LocalContext.current] is not guaranteed to BE the
 *  Activity directly (review finding, round 5: a bare `context as? Activity` silently returning
 *  `null` here would make `activity?.isChangingConfigurations != true` evaluate `true` — the guard
 *  failing OPEN, exactly backwards from its purpose of suppressing both hooks on a plain rotation). */
private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}

@Composable
private fun CoursePlayerReadyContent(
    state: CoursePlayerReadyState,
    viewModel: CoursePlayerViewModel,
    onTakeQuiz: () -> Unit,
    sheetVisible: Boolean,
    onOpenSheet: () -> Unit,
    onDismissSheet: () -> Unit,
    onLessonPicked: (String) -> Unit,
    onQuizFromSheet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth()) {
            PlayerSurface(
                videoAspectRatio = viewModel.videoAspectRatio,
                onAttachSurface = viewModel::attachVideoSurface,
                onDetachSurface = viewModel::detachVideoSurface,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.hasVideoSource) {
                PlayerControls(
                    playbackStateFlow = viewModel.playbackState,
                    playbackPositionFlow = viewModel.playbackPosition,
                    playbackDurationFlow = viewModel.playbackDuration,
                    onPlayPauseToggle = viewModel::onPlayPauseToggle,
                    onSeek = viewModel::onSeek,
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                )
            } else if (state.videoLoadError != null) {
                // `mobile-course-player.json`'s `states.error`: "inline error sized to video frame,
                // icon+title+retry (mirrors Web)". `onRetry()` is the only retry entry point
                // CoursePlayerViewModel exposes for a failed lesson-video fetch — `onLessonSelected`
                // no-ops for the ALREADY-current lesson (see that function's own re-entrancy guard) —
                // so this re-runs the whole course load rather than a narrower per-lesson retry; a
                // disclosed, heavier-than-ideal mechanism given the ViewModel's already-locked surface.
                Box(modifier = Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                    ErrorState(
                        title = stringResource(R.string.course_player_video_error_title),
                        description = apiErrorMessage(state.videoLoadError),
                        onRetryClick = viewModel::onRetry,
                        retryLabel = stringResource(R.string.course_player_retry_action),
                        modifier = Modifier.padding(horizontal = MentoraDimens.spacing.space4),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(MentoraDimens.spacing.space4),
            verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
        ) {
            Text(
                text = state.currentLessonTitle,
                style = MaterialTheme.typography.headlineSmall, // heading.h4-ish per showcase.
                color = MaterialTheme.colorScheme.onSurface,
            )
            MentoraProgressBar(
                progress = state.completionPercent / 100f,
                contentDescriptionLabel = stringResource(
                    R.string.course_player_progress_content_description,
                    state.completionPercent.toString(),
                ),
            )
            // Review finding (round 5, MEDIUM): `Lesson.description` had no render path anywhere in
            // C3 — `state.currentLessonDescription` now surfaces it (additive fix to
            // `CoursePlayerReadyState`, see its own kdoc). A lesson's description is backend-authored
            // free text (`CourseService.kt`'s `LessonResponse`), not a locked/formatted numeric
            // string, so it renders as-is with no `stringResource` wrapping.
            if (state.currentLessonDescription.isNotBlank()) {
                Text(
                    text = state.currentLessonDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            CurriculumTriggerRow(
                lessonNumber = state.lessonNumber,
                totalLessons = state.totalLessons,
                expanded = sheetVisible,
                onClick = onOpenSheet,
            )
        }

        CoursePlayerFooter(
            footerAction = state.footerAction,
            isCompletionInFlight = state.isCompletionInFlight,
            hasPrevious = previousLessonId(state.sheet) != null,
            onPrevious = { previousLessonId(state.sheet)?.let(onLessonPicked) },
            onPrimaryAction = {
                when (state.footerAction) {
                    CoursePlayerFooterAction.MarkComplete -> viewModel.onMarkCompleteTapped()
                    CoursePlayerFooterAction.NextLesson -> viewModel.onNextLessonTapped()
                    CoursePlayerFooterAction.TakeQuiz -> onTakeQuiz()
                    CoursePlayerFooterAction.FinishCourse -> viewModel.onFinishCourseTapped()
                }
            },
        )
    }

    if (sheetVisible) {
        CurriculumBottomSheet(
            sheet = state.sheet,
            onLessonSelected = onLessonPicked,
            onTakeQuiz = onQuizFromSheet,
            onDismissRequest = onDismissSheet,
        )
    }
}

/** D85 Decision 7's own mechanism ("lesson switching is ViewModel state") reused for the footer's
 *  "Previous" control — the ViewModel exposes no dedicated previous-lesson action, but
 *  [CurriculumSheetState] already carries every lesson in curriculum order, so the lesson immediately
 *  before [CurriculumSheetState.currentLessonId] in that flattened order IS "Previous"; tapping it goes
 *  through the exact same [CoursePlayerViewModel.onLessonSelected] the Curriculum Bottom Sheet's own
 *  row taps use, never a separate/duplicated switch mechanism. */
private fun previousLessonId(sheet: CurriculumSheetState): String? {
    val flatLessons = sheet.sections.flatMap { it.lessons }
    val currentIndex = flatLessons.indexOfFirst { it.lessonId == sheet.currentLessonId }
    return flatLessons.getOrNull(currentIndex - 1)?.lessonId
}

@Composable
private fun CurriculumTriggerRow(lessonNumber: Int, totalLessons: Int, expanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MentoraDimens.touchTargetMin)
            .clip(MaterialTheme.shapes.small)
            .border(BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant), MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = MentoraDimens.spacing.space4)
            .testTag(CoursePlayerCurriculumTriggerTestTag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // No `list` leading glyph — no equivalent exists in the ported 42-icon set (D85's own
        // disclosure, citing G8/D80).
        Text(
            text = stringResource(R.string.course_player_curriculum_trigger, lessonNumber.toString(), totalLessons.toString()),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        MentoraIcon(
            name = if (expanded) MentoraIconName.ExpandLess else MentoraIconName.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CoursePlayerFooter(
    footerAction: CoursePlayerFooterAction,
    isCompletionInFlight: Boolean,
    hasPrevious: Boolean,
    onPrevious: () -> Unit,
    onPrimaryAction: () -> Unit,
) {
    val label = stringResource(
        when (footerAction) {
            CoursePlayerFooterAction.MarkComplete -> R.string.course_player_mark_complete_action
            CoursePlayerFooterAction.NextLesson -> R.string.course_player_next_lesson_action
            CoursePlayerFooterAction.TakeQuiz -> R.string.course_player_take_quiz_action
            CoursePlayerFooterAction.FinishCourse -> R.string.course_player_finish_course_action
        },
    )
    val trailingIcon = if (footerAction == CoursePlayerFooterAction.NextLesson) MentoraIconName.ArrowForward else null
    // isCompletionInFlight only ever gates MarkComplete/NextLesson — those are the only two footer
    // actions that route through CoursePlayerViewModel's own completeLessonAndAdvance (see that
    // class's own kdoc); TakeQuiz is a plain nav callback and FinishCourse never toggles this flag.
    val loadingGate = isCompletionInFlight &&
        (footerAction == CoursePlayerFooterAction.MarkComplete || footerAction == CoursePlayerFooterAction.NextLesson)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().testTag(CoursePlayerFooterTestTag),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(MentoraDimens.spacing.space4),
            horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
        ) {
            PreviousLessonButton(enabled = hasPrevious, onClick = onPrevious)
            PrimaryButton(
                text = label,
                onClick = onPrimaryAction,
                enabled = !loadingGate,
                loading = loadingGate,
                trailingIcon = trailingIcon,
                modifier = Modifier.weight(1f).testTag(CoursePlayerPrimaryActionTestTag),
            )
        }
    }
}

@Composable
private fun PreviousLessonButton(enabled: Boolean, onClick: () -> Unit) {
    val borderColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = MaterialTheme.stateOpacities.disabledContent)
    }
    Box(
        modifier = Modifier
            .size(MentoraDimens.touchTargetMin)
            .clip(MaterialTheme.shapes.small)
            .border(BorderStroke(MentoraDimens.borderWidthDefault, borderColor), MaterialTheme.shapes.small)
            .clickable(enabled = enabled, onClick = onClick)
            .testTag(CoursePlayerPreviousButtonTestTag),
        contentAlignment = Alignment.Center,
    ) {
        MentoraIcon(
            name = MentoraIconName.ArrowBack,
            contentDescription = stringResource(R.string.course_player_previous_lesson_content_description),
            tint = contentColor,
        )
    }
}

@Composable
private fun CoursePlayerCompletedContent(
    state: CoursePlayerCourseCompletedState,
    onBackToMyLearning: () -> Unit,
    onOpenCertificates: () -> Unit,
    onTakeQuiz: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // D85 Decision 9: "CourseFinished routing to Quiz ... or, when there is no quiz, to the inline
    // completion state" — the routing decision itself is C3's to make from this state's own data.
    //
    // Round-6 review finding (INFO) — CLOSED by Task 14. `state.quizPassed` used to be captured at
    // course-load time only (`CoursePlayerViewModel`'s own `load()`/`rebuildReadyState` path), with
    // nothing re-fetching it when this composable re-entered composition after a Quiz round trip — a
    // user who actually PASSED the quiz and popped back would have still seen this "quiz pending"
    // branch (stale `quizPassed = false`) instead of "View Certificate". Task 14 closes this: the real
    // `CoursePlayerScreen` composable now calls `viewModel.refreshQuizStatus()` from a
    // `LaunchedEffect(Unit)` at its own top level, which re-fires on every (re-)entry into this
    // composable's composition — including a return from Quiz via back — and re-fetches
    // `progress`/`quiz` so `state.quizPassed` here is current, not stale. Was previously unreachable
    // in practice anyway because `Destination.Quiz` still resolved to `PlaceholderScreens.kt`'s
    // placeholder (`quizPassed` could never flip in-app) — Task 14 replaces that placeholder with the
    // real `QuizScreen`/`QuizResultsScreen`, making this branch reachable for the first time, with the
    // refresh mechanism above already in place to render it correctly.
    val quizNotYetPassed = state.quizRow != null && state.quizPassed != true
    // Review finding (round 5, HIGH): a first draft's `LaunchedEffect` re-fired on EVERY
    // recomposition reaching this branch, including a return from Quiz via system back — Quiz is a
    // separately-pushed destination, so THIS composable leaves and re-enters composition across that
    // push/pop, and the retained ViewModel still reports the identical `CourseCompleted`/`quizRow`/
    // `quizPassed` combination, re-triggering the auto-navigate and turning system back into an
    // inescapable loop (`ux/NAVIGATION_SPEC.md`'s Quiz row: "Back behavior: Pop to Course Player,
    // answers preserved" — a loop instead). `rememberSaveable` survives exactly that push/pop cycle
    // (backed by this destination's own `NavBackStackEntry` saved-state registry, not plain
    // composition memory) while still resetting for a genuinely different course (keyed on
    // `state.courseId`), so "automatic after last lesson" (D85 Decision 9, `ux/NAVIGATION_SPEC.md:71`)
    // still fires exactly once per course-completion, never again on that same completion's own
    // back-navigation.
    var hasAutoNavigatedToQuiz by rememberSaveable(state.courseId) { mutableStateOf(false) }
    if (quizNotYetPassed && !hasAutoNavigatedToQuiz) {
        LaunchedEffect(state.courseId) {
            hasAutoNavigatedToQuiz = true
            onTakeQuiz()
        }
        Box(modifier = modifier)
        return
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(MentoraDimens.spacing.space4)
                .testTag(CoursePlayerCompletedContentTestTag),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // A separate MentoraTextButton for "Back to My Learning" alongside SuccessState's own
            // single PrimaryButton action — D85 Decision 9's own instruction ("do NOT grow SuccessState
            // a secondary-action slot — T11's PurchaseSuccessScreen already set that precedent").
            if (quizNotYetPassed) {
                // Reachable now that the auto-navigate above is one-shot: returning from an UNPASSED
                // quiz (system back) lands here instead of looping. The course's lessons are all
                // finished, but the quiz — a real prerequisite the certificate flow depends on — is
                // not; offering "View Certificate" here would be outright wrong, not just premature,
                // so this branch offers "Take Quiz" again instead.
                SuccessState(
                    title = stringResource(R.string.course_player_completion_title),
                    description = stringResource(R.string.course_player_quiz_pending_description),
                    actionLabel = stringResource(R.string.course_player_take_quiz_action),
                    onActionClick = onTakeQuiz,
                )
            } else {
                // SuccessState's own primary action is "View Certificate" (`onOpenCertificates`, D85
                // Decision 10's own optional wiring) — no courseId->certificateId join exists anywhere
                // in this app (the same disclosed gap `MyLearningScreen.kt`'s own kdoc records), so
                // this opens the general Certificates list, never a specific, guessed certificate.
                SuccessState(
                    title = stringResource(R.string.course_player_completion_title),
                    description = stringResource(R.string.course_player_completion_description),
                    actionLabel = stringResource(R.string.course_player_view_certificate_action),
                    onActionClick = onOpenCertificates,
                )
            }
            MentoraTextButton(
                text = stringResource(R.string.course_player_back_to_my_learning_action),
                onClick = onBackToMyLearning,
                modifier = Modifier.fillMaxWidth().testTag(CoursePlayerBackToMyLearningButtonTestTag),
            )
        }
    }
}

@Composable
private fun CoursePlayerTopBar(
    content: CoursePlayerContentState,
    onBack: () -> Unit,
    onOpenAiTutor: () -> Unit,
) {
    val title = when (content) {
        is CoursePlayerContentState.Ready -> content.state.courseTitle
        is CoursePlayerContentState.CourseCompleted -> content.state.courseTitle
        else -> stringResource(R.string.course_player_nav_title_fallback)
    }
    val meta = (content as? CoursePlayerContentState.Ready)?.state?.let { ready ->
        stringResource(
            R.string.course_player_top_bar_meta,
            ready.lessonNumber.toString(),
            ready.totalLessons.toString(),
            ready.completionPercent.toString(),
        )
    }

    // Review finding (round 5, MEDIUM): this screen is a chromeless destination inside
    // `MentoraNavHost`'s own Scaffold (which already reserves system-bar space for every route via
    // its own default `contentWindowInsets`), so this top bar applying `WindowInsets.statusBars`
    // padding ON TOP of that — and on top of this screen's own inner `Scaffold` doing the same before
    // its `contentWindowInsets` was zeroed above — triple-applied the identical status-bar inset. The
    // outer NavHost shell's own inset handling is the single source of truth; this top bar renders
    // flush with the content area the shell already carved out.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .heightIn(min = 56.dp)
            .padding(horizontal = MentoraDimens.spacing.space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MentoraIconButton(
            icon = MentoraIconName.ArrowBack,
            contentDescription = stringResource(R.string.course_player_back_content_description),
            onClick = onBack,
        )
        Column(
            modifier = Modifier.weight(1f).padding(start = MentoraDimens.spacing.space2),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            meta?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall, // typography.caption.
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        MentoraIconButton(
            icon = MentoraIconName.AiTutor,
            contentDescription = stringResource(R.string.course_player_ask_ai_tutor_content_description),
            onClick = onOpenAiTutor,
            modifier = Modifier.testTag(CoursePlayerAskAiTutorButtonTestTag),
        )
    }
}

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val CoursePlayerScreenTestTag = "course-player-screen"
const val CoursePlayerCurriculumTriggerTestTag = "course-player-curriculum-trigger"
const val CoursePlayerFooterTestTag = "course-player-footer"
const val CoursePlayerPrimaryActionTestTag = "course-player-primary-action"
const val CoursePlayerPreviousButtonTestTag = "course-player-previous-button"
const val CoursePlayerCompletedContentTestTag = "course-player-completed-content"
const val CoursePlayerBackToMyLearningButtonTestTag = "course-player-back-to-my-learning-button"
const val CoursePlayerAskAiTutorButtonTestTag = "course-player-ask-ai-tutor-button"
