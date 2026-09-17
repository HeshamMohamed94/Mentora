package com.mentora.android.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
// Wildcard (in addition to the explicit imports above, kept for readability at call sites below):
// the type-safe `popUpTo<T>()`/`popBackStack<T>()`/`toRoute<T>()` extension functions' exact
// declaring file inside androidx.navigation isn't part of its own stable public-API surface to
// name precisely — importing the whole package is the robust way to pull them in.
import androidx.navigation.*
import androidx.compose.ui.res.stringResource
import com.mentora.android.R
import com.mentora.android.ui.aitutor.AiTutorScreen
import com.mentora.android.ui.certificates.CertificateDetailScreen
import com.mentora.android.ui.certificates.CertificatesScreen
import com.mentora.android.ui.coursedetails.CourseDetailsScreen
import com.mentora.android.ui.courseplayer.CoursePlayerScreen
import com.mentora.android.ui.checkout.DemoCheckoutScreen
import com.mentora.android.ui.explore.ExploreScreen
import com.mentora.android.ui.home.HomeScreen
import com.mentora.android.ui.auth.LoginScreen
import com.mentora.android.ui.auth.RegisterScreen
import com.mentora.android.ui.learningpathdetails.LearningPathDetailsScreen
import com.mentora.android.ui.mylearning.MyLearningScreen
import com.mentora.android.ui.profile.ProfileScreen
import com.mentora.android.ui.checkout.PurchaseSuccessScreen
import com.mentora.android.ui.quiz.QuizAttemptDraftStore
import com.mentora.android.ui.quiz.QuizResultsScreen
import com.mentora.android.ui.quiz.QuizScreen
import com.mentora.android.ui.profile.SettingsScreen
import com.mentora.android.theme.ThemeController
import com.mentora.android.ui.shell.MentoraTopBar
import com.mentora.android.ui.shell.MobileBottomNavigation
import com.mentora.shared.MentoraSdk
import com.mentora.shared.auth.AuthState

/**
 * T6 — the whole app's navigation shell. ONE [androidx.navigation.NavHostController] /
 * [androidx.navigation.compose.NavHost] for every tab-root and pushed/nested destination, per
 * `execution/PHASE_4_ANDROID_PLAN.md` T6's explicit "single-graph, multiple-back-stacks" mandate
 * (Google's own documented `NavigationBar` + `saveState`/`restoreState` pattern, also used by the
 * "Now in Android" sample — never 5 separate `NavHost`s/`NavController`s).
 *
 * **Guest mode.** [NavHost]'s own `startDestination` (below) is chosen ONCE, from whichever
 * [authState] this composable first sees: [TabGraph.HomeGraph] if already `Authenticated` (Home is
 * Student-only, `product/SCREEN_INVENTORY.md` § 8 — never a guest's landing screen), else
 * [TabGraph.ExploreGraph] (guest browsing — `ux/NAVIGATION_SPEC.md § 3`'s "the app opens straight
 * into a Guest-mode Explore" option). This has to be a plain, synchronous `remember` rather than a
 * `navigate()` call inside a `LaunchedEffect` — `NavHostController.graph`/`navigate()` both throw
 * ("You must call setGraph() before calling getGraph()") if invoked before `NavHost` itself has
 * mounted and set the graph, and a `LaunchedEffect`'s coroutine racing that first mount is exactly
 * the kind of ordering `NavHost`'s own `startDestination` parameter exists to avoid needing at all.
 *
 * **Auth-state-driven routing.** The one `LaunchedEffect(authState)` below reacts to the two
 * transitions that happen *after* this composable (and therefore `NavHost`) is already mounted:
 * Unauthenticated/Unknown → Authenticated (consume a recorded [PendingNavIntent] if the gate set
 * one, else land on Home) and Authenticated → Unauthenticated (logout — reset back to guest-mode
 * Explore). See `AuthGate.kt`'s kdoc for why [authState] is a plain parameter here rather than read
 * from `sdk.auth` directly (the seam that makes this whole composable instrumented-testable without
 * a real `MentoraSdk`).
 *
 * **T6 fix-up (Finding 1, Option B).** [onTabTapped]'s tab-switch `popUpTo` used to target
 * `navController.graph.findStartDestination().id` — a purely STRUCTURAL lookup that only correctly
 * identifies "the start tab's leaf" as long as that leaf is still actually present on the back
 * stack. The three full-stack-reset call sites below (login-with-no-pending-intent, logout,
 * [navigateToPurchaseSuccess]) each pop the ENTIRE stack via `popUpTo(navController.graph.id)
 * { inclusive = true }`, which removes that structural anchor from the stack entirely — after which
 * `findStartDestination()` still structurally resolves to the same id, but that id no longer
 * matches any real back-stack entry, so navigation-compose's `popBackStackInternal` silently no-ops
 * on every subsequent tab switch (nothing pops, nothing saves, nothing restores) instead of
 * switching tabs. Rather than re-navigate to the structural start tab on every reset just to keep
 * that lookup valid (Option A — rejected here because two of the three resets land on a DIFFERENT
 * tab than the structural start tab, e.g. logout always lands on Explore even when the app started
 * authenticated/Home-first, and re-inserting a hidden Home entry under a logged-out Explore stack
 * would itself violate "Home is Student-only, never a guest's landing screen" the moment the user
 * pressed back), [anchorTab] instead explicitly TRACKS which [TabGraph] is actually sitting at the
 * bottom of the stack right now, updated at each of the three reset sites to whatever tab the reset
 * actually lands on. [onTabTapped] pops to that tracked tab's own leaf route (via the type-safe
 * `popUpTo<Destination.Home>()`-style API, same one [onTabTapped]'s same-tab branch already uses)
 * instead of the stale structural lookup — always a currently-valid back-stack anchor by
 * construction, never stale.
 */
@Composable
fun MentoraNavHost(
    authState: AuthState,
    sdk: MentoraSdk,
    // T18 review fix (HIGH — a real instrumented-test crash, not just a style preference): threaded
    // explicitly from `MainActivity`'s own `app.themeController`, the same way `sdk` already is,
    // rather than `SettingsScreen` resolving it itself via `LocalContext.current.applicationContext
    // as MentoraApplication`. This module's `testInstrumentationRunner` is globally
    // `NoOpApplicationTestRunner` (`NoOpApplicationTestRunner.kt`'s own kdoc: "No instrumented test in
    // this module launches MainActivity or otherwise depends on MentoraApplication's real bootstrap
    // sequence... substituting the plain base Application for the whole instrumentation process is
    // therefore safe module-wide") — that cast throws `ClassCastException` under EVERY instrumented
    // test that ever composes `SettingsScreen`, confirmed by a real `NavigationShellTest` failure this
    // exact way before this fix. `AiTutorViewModel`'s own T18 fix (this task, see that file's kdoc)
    // hit the identical class of mistake for a different reason (a stale, non-reactive Context) —
    // this is the same lesson applied preemptively to the one remaining `Application`-cast call site.
    themeController: ThemeController,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    // T6 fix-up (Finding 2): survives rotation/process death — see PendingNavIntentSaver's kdoc in
    // AuthGate.kt for why a plain `remember` here was the root cause of a pending intent silently
    // disappearing across a rotation.
    var pendingNavIntent by rememberSaveable(stateSaver = PendingNavIntentSaver) { mutableStateOf<PendingNavIntent?>(null) }
    val startGraph = remember { if (authState is AuthState.Authenticated) TabGraph.HomeGraph else TabGraph.ExploreGraph }
    var previousAuthState by remember { mutableStateOf(authState) }
    // T6 fix-up (Finding 1) — see this composable's own kdoc above. Starts at `startGraph` (correct:
    // that's genuinely the bottom of the stack at first mount) and is reassigned at every full-stack
    // reset site below to whichever tab that reset actually lands on.
    var anchorTab by rememberSaveable(stateSaver = TabGraphSaver) { mutableStateOf(startGraph) }

    LaunchedEffect(authState) {
        val previous = previousAuthState
        previousAuthState = authState
        val isAuthenticatedNow = authState is AuthState.Authenticated
        when {
            previous !is AuthState.Authenticated && isAuthenticatedNow -> {
                // Login/Register success — consume the pending intent (guest enroll/follow gate) if
                // one was recorded, else land on Home (plain login, no specific prior intent).
                val intent = pendingNavIntent
                pendingNavIntent = null
                if (intent != null) {
                    navController.navigate(intent.destination) {
                        popUpTo<Destination.Login> { inclusive = true }
                    }
                } else {
                    navController.navigate(TabGraph.HomeGraph) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                    anchorTab = TabGraph.HomeGraph
                }
            }

            previous is AuthState.Authenticated && !isAuthenticatedNow -> {
                // Logout — reset back to guest mode.
                pendingNavIntent = null
                navController.navigate(TabGraph.ExploreGraph) {
                    popUpTo(navController.graph.id) { inclusive = true }
                }
                anchorTab = TabGraph.ExploreGraph
            }
        }
    }

    // The real `requireAuth` mechanism (`ux/NAVIGATION_SPEC.md § 6`) — see AuthGate.kt's kdoc.
    val requireAuth: (Destination) -> Unit = { destination ->
        when (val decision = decideAuthGate(authState, destination)) {
            is AuthGateDecision.NavigateDirect -> navController.navigate(decision.destination)
            is AuthGateDecision.GateToLogin -> {
                pendingNavIntent = decision.pendingIntent
                navController.navigate(Destination.Login)
            }
        }
    }

    // Shared across every tab-graph that registers Destination.CoursePlayer (Home/Explore/
    // MyLearning, per ux/NAVIGATION_SPEC.md § 3 — "pushed from either" Explore or My Learning, plus
    // Home's own "Continue" affordance) — one content lambda, passed to composable<T>() 3 times
    // rather than 3 copy-pasted bodies.
    // Task 13 C3: Course Player now owns its own top bar (D85 Decision 10) and needs several nav
    // callbacks the T6-era placeholder never wired — `onBack` (its own top bar's back affordance,
    // `navController.popBackStack()`, per D85 Decision 10's own "pop to whichever tab/screen pushed
    // it" reading of `ux/NAVIGATION_SPEC.md:70` — the WEB-only "always back to My Learning" rule does
    // NOT apply to this top-bar back button), `onOpenAiTutor` (a real tab switch to AiTutorGraph,
    // reusing HomeScreen's own `onOpenExplore` pattern — T13->T17 handoff for the AI Tutor screen's own
    // context-passing params, per D85 Decision 10), `onBackToMyLearning` (the CourseCompleted state's
    // own secondary action — an explicit tab switch to MyLearningGraph, NOT `popBackStack()`, since
    // unlike PurchaseSuccess this screen can be reached from Home/Explore too, so popping the back
    // stack cannot be guaranteed to land on My Learning), and `onOpenCertificates` (the CourseCompleted
    // state's "View Certificate" primary action — the general Certificates list, D85 Decision 10's own
    // optional wiring; no courseId->certificateId join exists anywhere in this app, same disclosed gap
    // `MyLearningScreen.kt`'s own kdoc records).
    val coursePlayerContent: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = { entry ->
        val args = entry.toRoute<Destination.CoursePlayer>()
        CoursePlayerScreen(
            courseId = args.courseId,
            lessonId = args.lessonId,
            sdk = sdk,
            onBack = { navController.popBackStack() },
            onOpenAiTutor = { onTabTapped(navController, TabGraph.AiTutorGraph, anchorTab = anchorTab) },
            onTakeQuiz = { navController.navigate(Destination.Quiz(args.courseId)) },
            onBackToMyLearning = {
                onTabTapped(navController, TabGraph.MyLearningGraph, anchorTab = anchorTab)
            },
            onOpenCertificates = { navController.navigate(Destination.Certificates) },
        )
    }
    // Task 14: real Quiz/Quiz Results screens, replacing the T6-era placeholders. `onSubmitted` is a
    // plain forward push (never popping Quiz off) — Quiz Results' own "Back behavior: Pop to Quiz
    // (rare)" (`ux/NAVIGATION_SPEC.md § 3`) depends on Quiz's own back-stack entry still being there
    // underneath, which is also what makes a system-back-then-back from Results land correctly back on
    // Course Player with Quiz's own in-memory answers (`QuizViewModel`'s own kdoc) untouched.
    val quizContent: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = { entry ->
        val args = entry.toRoute<Destination.Quiz>()
        QuizScreen(
            courseId = args.courseId,
            sdk = sdk,
            onSubmitted = { navController.navigate(Destination.QuizResults(args.courseId)) },
        )
    }
    val quizResultsContent: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = { entry ->
        val args = entry.toRoute<Destination.QuizResults>()
        QuizResultsScreen(
            courseId = args.courseId,
            sdk = sdk,
            // ux/NAVIGATION_SPEC.md § 3 — passed: "Continue" lands on My Learning, never back to
            // Course Player. Same tab-switch mechanism as `coursePlayerContent`'s own
            // `onBackToMyLearning` (Quiz Results is reachable from Home/Explore/My Learning's own
            // Course Player push, so a plain `popBackStack()` cannot be guaranteed to land there).
            // [onTabTapped] itself now decides same-tab-vs-switch-tab (see that function's own kdoc,
            // "Bug 1" fix-up) — this call site no longer has to (and must NOT) assert that.
            onContinue = {
                onTabTapped(navController, TabGraph.MyLearningGraph, anchorTab = anchorTab)
            },
            // failed: "Retry Quiz" pops BOTH Quiz and Quiz Results off (back to the Course Player entry
            // directly underneath Quiz — always present, since Quiz's own only entry point is Course
            // Player's "Take Quiz") and pushes a genuinely FRESH `Destination.Quiz`, so the new attempt
            // gets its own fresh `QuizViewModel` (blank answers) rather than resuming the just-failed
            // attempt's own stale, already-submitted one — and so a system back from the fresh attempt
            // correctly pops to Course Player, never to the old exhausted Quiz screen underneath it.
            // `product/USER_FLOWS.md § 14`'s own "a fresh Quiz entry, fresh attempt" wording.
            onRetry = {
                // Round-1 review, HIGH-2: an explicit new attempt — must not silently resume the
                // just-failed attempt's own stale draft (see `QuizAttemptDraftStore`'s own kdoc).
                QuizAttemptDraftStore.clear(args.courseId)
                navController.navigate(Destination.Quiz(args.courseId)) {
                    popUpTo<Destination.CoursePlayer> { inclusive = false }
                }
            },
            // "View Certificate" (only offered when the completion banner shows) — same general
            // Certificates list target as `coursePlayerContent`'s own `onOpenCertificates` (no
            // courseId->certificateId join exists anywhere in this app, same disclosed gap).
            onOpenCertificates = { navController.navigate(Destination.Certificates) },
        )
    }
    // T12: Course Details is now reachable from BOTH Explore (Task 10's original wiring) and Home
    // (a Recommended-card tap, `ux/SCREEN_UX_SPECS.md § 8` module 2's own "Course Details" exit) — one
    // shared content lambda registered under both `navigation<TabGraph.ExploreGraph>` and
    // `navigation<TabGraph.HomeGraph>`, mirroring [coursePlayerContent]/[quizContent]'s own established
    // shared-lambda-across-multiple-graphs pattern (so `currentTab`'s `hierarchy.any { hasRoute<...>() }`
    // check resolves the CORRECT tab regardless of which tab this destination was reached from, rather
    // than always resolving to whichever graph happened to declare it first).
    val courseDetailsContent: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = { entry ->
        val courseId = entry.toRoute<Destination.CourseDetails>().courseId
        CourseDetailsScreen(
            courseId = courseId,
            sdk = sdk,
            // T10 — a plain snapshot of the live AuthState at push time (never re-read reactively
            // inside the screen itself): every full auth-state TRANSITION already resets the entire
            // nav stack above (this LaunchedEffect(authState)), so this destination never stays
            // mounted across one — see CourseDetailsViewModel's own kdoc.
            isAuthenticated = authState is AuthState.Authenticated,
            onEnrollRequiringAuth = { requireAuth(Destination.DemoCheckout(courseId)) },
            // T6 fix-up (Finding 5): Course Player is enrollment-gated per
            // `ux/NAVIGATION_SPEC.md § 3` — this used to `navigate()` straight there, bypassing the
            // auth gate every other gated action goes through.
            onContinueLearning = { requireAuth(Destination.CoursePlayer(courseId)) },
            // T10 — a curriculum lesson row tap (enrolled only) into Course Player for that specific
            // lesson, through the same auth-gate mechanism as Continue Learning above, for the same
            // reason.
            onOpenLesson = { lessonId -> requireAuth(Destination.CoursePlayer(courseId, lessonId)) },
        )
    }
    // T16: Learning Path Details is registered under BOTH ExploreGraph (Task 9's original wiring, the
    // Learning Paths tab's own card tap) and MyLearningGraph (My Learning's own followed-paths row) —
    // one shared content lambda, same established pattern as [courseDetailsContent]/[coursePlayerContent]
    // above. `onFollowRequiringAuth` mirrors `courseDetailsContent`'s own `onEnrollRequiringAuth` gate
    // exactly: `requireAuth(Destination.LearningPathDetails(pathId))` re-pushes this SAME destination
    // (never a different one) as the pending intent, so a guest who logs in lands back on a fresh
    // instance of this screen — now authenticated, with the Follow button wired for real — rather than
    // auto-following on their behalf (mirrors Course Details' own guest flow: login lands back on the
    // checkout PREVIEW, never auto-completes a purchase).
    val learningPathDetailsContent: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = { entry ->
        val pathId = entry.toRoute<Destination.LearningPathDetails>().pathId
        LearningPathDetailsScreen(
            pathId = pathId,
            sdk = sdk,
            isAuthenticated = authState is AuthState.Authenticated,
            onFollowRequiringAuth = { requireAuth(Destination.LearningPathDetails(pathId)) },
            onOpenCourseDetails = { courseId -> navController.navigate(Destination.CourseDetails(courseId)) },
        )
    }
    // T12: Certificates (+ Detail) is registered under MyLearningGraph (its conceptual IA home,
    // `product/INFORMATION_ARCHITECTURE.md § 3`'s routing table: "Certificates + Detail | My Learning
    // (pushed)") AND HomeGraph, for the identical "reachable from more than one tab" reason as
    // [courseDetailsContent] above — Home's own Certificates entry-point affordance
    // (`HomeScreen.kt`'s kdoc) pushes it directly from within the Home tab; without this second
    // registration the push would still succeed (see [Destinations]'s own kdoc for why an
    // unregistered destination resolves via the PARENT graph rather than erroring) but would
    // mis-anchor under MyLearningGraph instead of Home.
    // T15: real screens, replacing the T6-era placeholders. `onOpenMyLearning` (the Empty state's own
    // CTA) is the same explicit tab-switch mechanism `onBackToMyLearning`/`onContinue` above already
    // use, not `popBackStack()` — Certificates is reachable from Home/Explore/MyLearning alike, so a
    // plain pop cannot be guaranteed to land on My Learning.
    val certificatesContent: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = {
        CertificatesScreen(
            sdk = sdk,
            onOpenCertificateDetail = { certificateId -> navController.navigate(Destination.CertificateDetail(certificateId)) },
            onOpenMyLearning = {
                onTabTapped(navController, TabGraph.MyLearningGraph, anchorTab = anchorTab)
            },
        )
    }
    val certificateDetailContent: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = { entry ->
        CertificateDetailScreen(
            certificateId = entry.toRoute<Destination.CertificateDetail>().certificateId,
            sdk = sdk,
        )
    }
    // T12 fix-up: [courseDetailsContent]'s own `onEnrollRequiringAuth` pushes `Destination.DemoCheckout`
    // by `navigate()`, and Course Details is now reachable from Home too — registered under BOTH
    // `HomeGraph` and `ExploreGraph`, same shared-lambda-across-multiple-graphs pattern as
    // [courseDetailsContent] above, so an Enroll tap reached via Home anchors DemoCheckout under
    // HomeGraph rather than silently resolving to ExploreGraph's own registration (see [Destinations]'s
    // own kdoc for exactly why an unregistered destination still resolves, just under the wrong tab).
    val demoCheckoutContent: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = { entry ->
        val courseId = entry.toRoute<Destination.DemoCheckout>().courseId
        DemoCheckoutScreen(
            courseId = courseId,
            sdk = sdk,
            onCompletePurchase = {
                navigateToPurchaseSuccess(navController, courseId) { anchorTab = it }
            },
            onBackToCourse = { navController.popBackStack() },
        )
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // T6 fix-up (Finding 3): an abandoned pending intent must never persist past the specific login
    // attempt it was created for. The only path that clears `pendingNavIntent` was previously a
    // SUCCESSFUL auth transition (or logout) — if the user instead backs OUT of Login/Register
    // without completing it, the intent was never cleared, so a later, completely unrelated login
    // would silently consume and act on it. This fires whenever the current destination stops being
    // Login/Register; on the successful-auth path `pendingNavIntent` is already nulled synchronously
    // (above) before that navigation even happens, so this is a no-op there — it only ever has an
    // effect on the abandoned-login path.
    LaunchedEffect(currentDestination) {
        val isLoginOrRegister = currentDestination?.hasRoute<Destination.Login>() == true ||
            currentDestination?.hasRoute<Destination.Register>() == true
        if (!isLoginOrRegister && pendingNavIntent != null) {
            pendingNavIntent = null
        }
    }

    val currentTab = remember(currentDestination) {
        AllTabGraphs.firstOrNull { tab -> currentDestination.isInTab(tab) }
    }
    // Task 14: `Destination.QuizResults` joins this set — `ux/SCREEN_UX_SPECS.md § 12`'s own "Header
    // structure: same minimal chrome as Quiz" (§ 11's own "bottom nav hidden on Mobile"), so Quiz
    // Results hides the bottom nav for the identical reason Quiz itself already does.
    val isFocusedLearningScreen = currentDestination?.hierarchy?.any {
        it.hasRoute<Destination.CoursePlayer>() || it.hasRoute<Destination.Quiz>() || it.hasRoute<Destination.QuizResults>()
    } ?: false
    // T11: DemoCheckout/PurchaseSuccess also hide the bottom nav — DemoCheckout per
    // `mobile-demo-checkout.json`'s shell note ("bottom nav not shown on this pushed screen"),
    // PurchaseSuccess per its own `shell: "none"` (which additionally drops the top bar too, see
    // `isChromeless` below). Kept as its own flag, distinct from [isFocusedLearningScreen] (a
    // Course-Player/Quiz-specific concept per that val's own name/kdoc), rather than folded into it.
    val isDemoCheckout = currentDestination?.hasRoute<Destination.DemoCheckout>() == true
    val isPurchaseSuccess = currentDestination?.hasRoute<Destination.PurchaseSuccess>() == true
    // Bottom nav hidden on Course Player/Quiz/Demo Checkout/Purchase Success (fully removed, never
    // just dimmed — ux/MOBILE_UX.md § 1) AND while a guest (the bottom nav only ever shows once
    // authenticated).
    val showBottomNav = authState is AuthState.Authenticated && !isFocusedLearningScreen && !isDemoCheckout && !isPurchaseSuccess
    val isTabRoot = currentDestination?.let { destination ->
        destination.hasRoute<Destination.Home>() ||
            destination.hasRoute<Destination.Explore>() ||
            destination.hasRoute<Destination.MyLearning>() ||
            destination.hasRoute<Destination.AiTutor>() ||
            destination.hasRoute<Destination.Profile>()
    } ?: true
    // T7 fix-up: Login/Register sit OUTSIDE the tab bar with their own minimal logo-only header
    // (`ux/SCREEN_UX_SPECS.md §§ 6-7`: "no Navbar link row/full app-shell chrome — nothing to
    // navigate to from a focused auth form"). MentoraTopBar's generic title-plus-back-button chrome
    // used to render for both anyway (neither is a tab root, so showBackButton fell out `true`) —
    // that's exactly the "full app-shell/nav chrome" this screen must NOT show; system back (the
    // device back gesture/button) still works regardless of whether this app-level bar renders.
    val isAuthRoute = currentDestination?.let { destination ->
        destination.hasRoute<Destination.Login>() || destination.hasRoute<Destination.Register>()
    } ?: false
    // T11: Purchase Success is `shell: "none"` — genuinely NO app chrome at all, a step further than
    // DemoCheckout (which keeps its top bar, only loses the bottom nav, above). Folded into the same
    // "suppress MentoraTopBar" check as [isAuthRoute] (mirrors that exact established pattern) rather
    // than a parallel boolean of its own.
    // Task 13 (D85 Decision 10): Course Player is chromeless at THIS shell level too — it renders its
    // own top bar (back + course title + "Lesson N of M · X%" + Ask-AI), which `MentoraTopBar`'s
    // generic shape cannot carry (T10 already declined to add an actions slot to that shared bar for
    // exactly this reason). The bottom nav is already hidden for it via [isFocusedLearningScreen]
    // above — unchanged, [isCoursePlayer] only controls the top bar.
    val isCoursePlayer = currentDestination?.hasRoute<Destination.CoursePlayer>() == true
    val isChromeless = isAuthRoute || isPurchaseSuccess || isCoursePlayer
    // T15: `ux/SCREEN_UX_SPECS.md § 13`'s own header structure — "page title 'Certificates'," no back
    // affordance, even though this is technically a pushed (non-tab-root) destination reachable from
    // 3 different tabs (Home/Explore/My Learning) — unlike every other pushed screen, whose back
    // button target is unambiguous. System back (the device gesture/button) still works regardless,
    // same precedent as every other suppressed-affordance case in this file. Certificate DETAIL is
    // unaffected — it keeps the standard back button via the plain `!isTabRoot` rule below (its own
    // spec explicitly calls for "minimal chrome, back affordance").
    val isCertificatesList = currentDestination?.hasRoute<Destination.Certificates>() == true

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isChromeless) {
                MentoraTopBar(
                    title = titleFor(currentDestination),
                    showBackButton = !isTabRoot && !isCertificatesList,
                    onBackClick = { navController.popBackStack() },
                )
            }
        },
        bottomBar = {
            val tab = currentTab
            if (showBottomNav && tab != null) {
                MobileBottomNavigation(
                    selectedTab = tab,
                    onTabSelected = { tapped -> onTabTapped(navController, tapped, anchorTab = anchorTab) },
                )
            }
        },
    ) { contentPadding ->
        NavHost(
            navController = navController,
            startDestination = startGraph,
            modifier = Modifier.padding(contentPadding),
        ) {
            navigation<TabGraph.HomeGraph>(startDestination = Destination.Home) {
                composable<Destination.Home> {
                    // T12 — a plain snapshot of the live AuthState at the moment this tab-root
                    // destination is composed (Home only ever renders once Authenticated — see
                    // MentoraNavHost's own kdoc/startGraph logic — so `user` is expected non-null by
                    // the time bootstrap completes; falls back to an empty first name in the rare
                    // `Authenticated(user = null)` window rather than crashing).
                    val user = (authState as? AuthState.Authenticated)?.user
                    HomeScreen(
                        sdk = sdk,
                        userName = user?.name.orEmpty(),
                        onOpenCourseDetails = { courseId -> navController.navigate(Destination.CourseDetails(courseId)) },
                        // Explore is a TAB ROOT (TabGraph.ExploreGraph's own `startDestination`), not a
                        // plain pushed screen — a bare `navigate(Destination.Explore)` here would skip
                        // the `saveState`/`restoreState` dance that preserves Explore's own back stack
                        // (it WOULD still resolve, just under the wrong tab context — see
                        // `Destinations.kt`'s own kdoc). Reuses the exact same `onTabTapped` the bottom
                        // nav itself calls — its own internally-computed `isCurrentTab` always resolves
                        // false here since Home and Explore are different tabs.
                        onOpenExplore = { onTabTapped(navController, TabGraph.ExploreGraph, anchorTab = anchorTab) },
                        onOpenCertificates = { navController.navigate(Destination.Certificates) },
                        onContinueLearning = { courseId, lessonId ->
                            navController.navigate(Destination.CoursePlayer(courseId, lessonId))
                        },
                    )
                }
                composable<Destination.CourseDetails>(content = courseDetailsContent)
                composable<Destination.Certificates>(content = certificatesContent)
                composable<Destination.CertificateDetail>(content = certificateDetailContent)
                composable<Destination.DemoCheckout>(content = demoCheckoutContent)
                composable<Destination.CoursePlayer>(content = coursePlayerContent)
                composable<Destination.Quiz>(content = quizContent)
                composable<Destination.QuizResults>(content = quizResultsContent)
            }

            navigation<TabGraph.ExploreGraph>(startDestination = Destination.Explore) {
                composable<Destination.Explore> {
                    ExploreScreen(
                        sdk = sdk,
                        onOpenCourseDetails = { courseId -> navController.navigate(Destination.CourseDetails(courseId)) },
                        onOpenPathDetails = { pathId -> navController.navigate(Destination.LearningPathDetails(pathId)) },
                    )
                }
                composable<Destination.LearningPathDetails>(content = learningPathDetailsContent)
                composable<Destination.CourseDetails>(content = courseDetailsContent)
                composable<Destination.DemoCheckout>(content = demoCheckoutContent)
                composable<Destination.CoursePlayer>(content = coursePlayerContent)
                composable<Destination.Quiz>(content = quizContent)
                composable<Destination.QuizResults>(content = quizResultsContent)
                // Task 13: Course Player's own CourseCompleted state offers a "View Certificate" action
                // (`onOpenCertificates`, D85 Decision 10) — Course Player is reachable from Explore too
                // (`coursePlayerContent` registered above), so Certificates needs its own registration
                // here as well, same shared-lambda-across-multiple-graphs pattern already used for
                // [courseDetailsContent]/[demoCheckoutContent] (see [Destinations]'s own kdoc for why an
                // unregistered destination would otherwise fail to resolve under this tab).
                composable<Destination.Certificates>(content = certificatesContent)
                // Task 15 review finding (round 1, HIGH): `certificatesContent` (registered immediately
                // above) pushes `Destination.CertificateDetail` on a card tap — omitting ITS OWN
                // registration here left that push resolving under the WRONG tab (comprehensive-match
                // falls through to `HomeGraph`, the first tab in `AllTabGraphs` that has it), silently
                // mis-anchoring the bottom-nav highlight to Home and letting a Home tap's
                // `isCurrentTab = true` branch pop this whole Explore sub-stack with no `saveState`.
                // Exactly the same class of bug [Destinations]'s own kdoc already warns about — every
                // destination reachable from a shared, multi-graph-registered content lambda must be
                // registered under every one of those graphs, not just the lambda's own top-level route.
                composable<Destination.CertificateDetail>(content = certificateDetailContent)
            }

            navigation<TabGraph.MyLearningGraph>(startDestination = Destination.MyLearning) {
                composable<Destination.MyLearning> {
                    MyLearningScreen(
                        sdk = sdk,
                        onResumeCourse = { courseId, lessonId ->
                            navController.navigate(Destination.CoursePlayer(courseId, lessonId))
                        },
                        onOpenLearningPathDetails = { pathId -> navController.navigate(Destination.LearningPathDetails(pathId)) },
                        onOpenCertificates = { navController.navigate(Destination.Certificates) },
                        onOpenCertificateDetail = { certificateId -> navController.navigate(Destination.CertificateDetail(certificateId)) },
                        // T12: Explore is a different tab ROOT — reuses the same `onTabTapped`
                        // save/restore tab-switch mechanism the bottom nav itself calls, same
                        // rationale as Home's identical `onOpenExplore` (see that composable's own
                        // comment) — a bare `navigate(Destination.Explore)` would skip the
                        // `saveState`/`restoreState` dance (`Destinations.kt`'s own kdoc).
                        onOpenExplore = { onTabTapped(navController, TabGraph.ExploreGraph, anchorTab = anchorTab) },
                    )
                }
                composable<Destination.CoursePlayer>(content = coursePlayerContent)
                composable<Destination.Quiz>(content = quizContent)
                composable<Destination.QuizResults>(content = quizResultsContent)
                composable<Destination.LearningPathDetails>(content = learningPathDetailsContent)
                // T16: [learningPathDetailsContent] (registered immediately above) pushes
                // `Destination.CourseDetails` on a member-course tap, and that screen's own
                // `onEnrollRequiringAuth` can in turn push `Destination.DemoCheckout` — both need their
                // own registration here, same navigation-registration checklist [Destinations]'s own
                // kdoc and this task's own brief call out (the exact class of bug D90 found and fixed
                // for `CertificateDetail`/`ExploreGraph`): omitting either would let a tap reached via
                // My Learning → Learning Path Details silently mis-anchor the bottom nav to whichever
                // OTHER tab-graph happens to declare it first, risking a whole-tab-stack loss on the
                // next same-tab tap.
                composable<Destination.CourseDetails>(content = courseDetailsContent)
                composable<Destination.DemoCheckout>(content = demoCheckoutContent)
                composable<Destination.Certificates>(content = certificatesContent)
                composable<Destination.CertificateDetail>(content = certificateDetailContent)
                composable<Destination.PurchaseSuccess> { entry ->
                    val courseId = entry.toRoute<Destination.PurchaseSuccess>().courseId
                    PurchaseSuccessScreen(
                        courseId = courseId,
                        sdk = sdk,
                        // `ux/NAVIGATION_SPEC.md`'s per-screen table: Course Player's back target is
                        // ALWAYS My Learning, "not Course Details, once enrolled" — regardless of
                        // entry point (Purchase Success/My Learning/Dashboard/Course Details all name
                        // My Learning as the single back target, § "Why Course Player backs to My
                        // Learning, not Course Details"). Popping [Destination.PurchaseSuccess] off
                        // (inclusive) before pushing Course Player is what makes that true here too —
                        // a plain `navigate(...)` would leave Purchase Success underneath, so system
                        // back from the player would return to the just-completed celebratory screen
                        // instead.
                        onStartLearning = {
                            navController.navigate(Destination.CoursePlayer(courseId, lessonId = null)) {
                                popUpTo<Destination.PurchaseSuccess> { inclusive = true }
                            }
                        },
                        // Same target as system back (`navigateToPurchaseSuccess`'s own kdoc: Purchase
                        // Success sits directly on top of My Learning's own FRESH root) — a plain
                        // `popBackStack()` lands there identically, verified by
                        // `NavigationShellTest.purchaseSuccess_backLandsOnMyLearningRoot...`. Deliberately
                        // NOT a fresh `navigate(Destination.MyLearning)`, so the button-tap and
                        // system-back paths are byte-identical, per this task's own instruction.
                        onBackToMyLearning = { navController.popBackStack() },
                    )
                }
            }

            // T17: Destination.AiTutor's own tab root, no child destinations — reached exclusively via
            // the bottom-nav tab tap / onTabTapped (the [coursePlayerContent]'s own `onOpenAiTutor` is
            // a full TAB SWITCH into this same graph root, not a push of a child destination here) so
            // this is the graph's only registration, confirmed not needed under any other TabGraph.
            navigation<TabGraph.AiTutorGraph>(startDestination = Destination.AiTutor) {
                composable<Destination.AiTutor> { AiTutorScreen(sdk = sdk) }
            }

            // T18: ProfileGraph is the only graph either destination is reachable from — Profile is
            // this graph's own tab root (never pushed from elsewhere) and Settings is pushed
            // exclusively from Profile, so its transitive closure never crosses into another graph.
            // Confirmed via the same navigation-registration checklist D90/D91/T17 already applied.
            navigation<TabGraph.ProfileGraph>(startDestination = Destination.Profile) {
                composable<Destination.Profile> {
                    ProfileScreen(sdk = sdk, onOpenSettings = { navController.navigate(Destination.Settings) })
                }
                composable<Destination.Settings> { SettingsScreen(sdk = sdk, themeController = themeController) }
            }

            // Outside the tab bar entirely (ux/NAVIGATION_SPEC.md § 3) — top-level siblings of the
            // 5 tab graphs, reachable from anywhere since the outer graph is always an ancestor.
            composable<Destination.Login> {
                LoginScreen(
                    sdk = sdk,
                    onOpenRegister = { navController.navigate(Destination.Register) },
                )
            }
            composable<Destination.Register> {
                RegisterScreen(
                    sdk = sdk,
                    onOpenLogin = { navController.navigate(Destination.Login) },
                )
            }
        }
    }
}

/** Tap-active-tab-pops-to-root vs. switch-tab, per `ux/MOBILE_UX.md § 1`. The switch-tab branch is
 * the exact documented `saveState`/`restoreState` pattern this task mandates; the pop-to-root branch
 * intentionally omits `saveState` so the tab's saved sub-stack is discarded, not preserved.
 *
 * T6 fix-up (Finding 1): the switch-tab branch used to pop to
 * `navController.graph.findStartDestination().id` — a structural lookup that goes stale (and then
 * silently no-ops) the moment any full-stack reset removes that destination from the back stack.
 * [anchorTab] is [MentoraNavHost]'s own explicitly-tracked "which tab is actually at the bottom of
 * the stack right now" — always kept in sync with reality at every reset site — so popping to ITS
 * own leaf route (type-safe, same `popUpTo<T>()` API the same-tab branch above already uses) is
 * always a currently-valid anchor. See [MentoraNavHost]'s own kdoc for the full rationale.
 *
 * **Bug 1 fix-up (Phase 4 final acceptance): `isCurrentTab` is now computed HERE, from the live
 * [NavHostController.currentDestination], never trusted from the caller.** Several call sites
 * (`onContinue` from Quiz Results, `onBackToMyLearning`/`onOpenAiTutor` from Course Player,
 * `onOpenMyLearning` from Certificates) used to hardcode `isCurrentTab = false` on the theory that
 * those destinations are reachable from more than one tab, so "am I already on `tab`" could never be
 * assumed true — true in general, but WRONG on exactly the path this bug's repro takes (guest enroll
 * → login → purchase → Course Player → Quiz → Quiz Results, all pushed UNDER `MyLearningGraph` by
 * [navigateToPurchaseSuccess]'s own reset): there, `tab == anchorTab == MyLearningGraph` AND the
 * current destination (Quiz Results) genuinely IS already inside `MyLearningGraph`'s own hierarchy,
 * yet the hardcoded `false` still routed through the switch-tab branch below —
 * `navController.navigate(MyLearningGraph) { popUpTo<Destination.MyLearning>{saveState=true};
 * launchSingleTop=true; restoreState=true }` — asking Navigation Compose to pop up to AND restore-
 * navigate to the SAME graph it's already sitting inside, in one call. Confirmed live (instrumented
 * `Log.d` around this function, `adb logcat`): that exact self-referential shape is a deterministic,
 * repeatable no-op — `currentBackStack.value` byte-identical before/after, current destination
 * unchanged, no exception thrown — not a one-off timing/transition-in-flight race (reproduced twice,
 * a minute apart); a genuinely DIFFERENT cross-tab switch called immediately after, in the same
 * session, succeeded normally. Computing `isCurrentTab` from the real back stack here (via
 * [isInTab], the same helper [MentoraNavHost]'s own `currentTab` already uses) routes this exact
 * scenario through the plain `popBackStack<Destination.MyLearning>(inclusive = false)` branch instead
 * — the same operation system back already performs successfully from this exact screen — avoiding
 * the broken self-referential shape entirely, rather than retrying/delaying it. */
private fun onTabTapped(navController: NavHostController, tab: TabGraph, anchorTab: TabGraph) {
    val isCurrentTab = navController.currentDestination.isInTab(tab)
    if (isCurrentTab) {
        when (tab) {
            TabGraph.HomeGraph -> navController.popBackStack<Destination.Home>(inclusive = false)
            TabGraph.ExploreGraph -> navController.popBackStack<Destination.Explore>(inclusive = false)
            TabGraph.MyLearningGraph -> navController.popBackStack<Destination.MyLearning>(inclusive = false)
            TabGraph.AiTutorGraph -> navController.popBackStack<Destination.AiTutor>(inclusive = false)
            TabGraph.ProfileGraph -> navController.popBackStack<Destination.Profile>(inclusive = false)
        }
    } else {
        navController.navigate(tab) {
            when (anchorTab) {
                TabGraph.HomeGraph -> popUpTo<Destination.Home> { saveState = true }
                TabGraph.ExploreGraph -> popUpTo<Destination.Explore> { saveState = true }
                TabGraph.MyLearningGraph -> popUpTo<Destination.MyLearning> { saveState = true }
                TabGraph.AiTutorGraph -> popUpTo<Destination.AiTutor> { saveState = true }
                TabGraph.ProfileGraph -> popUpTo<Destination.Profile> { saveState = true }
            }
            launchSingleTop = true
            restoreState = true
        }
    }
}

/**
 * Purchase Success → back → My Learning tab, never back into Demo Checkout
 * (`ux/NAVIGATION_SPEC.md § 6`). Two-step by design: [Destination.PurchaseSuccess] is registered
 * under [TabGraph.MyLearningGraph] specifically — not wherever [Destination.DemoCheckout] was
 * pushed from (Explore, per the mobile nav table) — so that `popBackStack()`/system back from it
 * lands on My Learning's own root, never wherever Checkout happened to be pushed from. Step 1
 * clears the entire current stack (whichever tab Checkout was reached through) down to the outer
 * graph's root and lands fresh on the My Learning tab's own root; step 2 pushes Purchase Success on
 * top of that fresh root.
 *
 * T6 fix-up (Finding 1): this is one of the three full-stack-reset sites — [onAnchorTabChanged] lets
 * the caller ([MentoraNavHost]) update its tracked `anchorTab` to [TabGraph.MyLearningGraph], the tab
 * this reset actually lands on, so a subsequent tab switch's `popUpTo` still targets a real
 * back-stack entry instead of a stale structural lookup. See [MentoraNavHost]'s kdoc.
 */
private fun navigateToPurchaseSuccess(
    navController: NavHostController,
    courseId: String,
    onAnchorTabChanged: (TabGraph) -> Unit,
) {
    navController.navigate(TabGraph.MyLearningGraph) {
        popUpTo(navController.graph.id) { inclusive = true }
    }
    onAnchorTabChanged(TabGraph.MyLearningGraph)
    navController.navigate(Destination.PurchaseSuccess(courseId))
}

private fun NavDestination?.isInTab(tab: TabGraph): Boolean {
    if (this == null) return false
    return when (tab) {
        TabGraph.HomeGraph -> hierarchy.any { it.hasRoute<TabGraph.HomeGraph>() }
        TabGraph.ExploreGraph -> hierarchy.any { it.hasRoute<TabGraph.ExploreGraph>() }
        TabGraph.MyLearningGraph -> hierarchy.any { it.hasRoute<TabGraph.MyLearningGraph>() }
        TabGraph.AiTutorGraph -> hierarchy.any { it.hasRoute<TabGraph.AiTutorGraph>() }
        TabGraph.ProfileGraph -> hierarchy.any { it.hasRoute<TabGraph.ProfileGraph>() }
    }
}

/**
 * T11 fix-up: [Destination.DemoCheckout]/[Destination.PurchaseSuccess]'s titles are now real,
 * localized `stringResource`-driven text (the placeholder era's raw English literals, per this
 * task's own instruction) — this function had to become `@Composable` to do that. Every other
 * branch below is untouched, pre-existing T6-era hardcoded English literal — out of this task's
 * scope to localize (T11 only owns the two new destinations it introduces); PurchaseSuccess's own
 * branch is unreachable in practice today ([isChromeless] above suppresses `MentoraTopBar` entirely
 * for it) but is still kept accurate here rather than left stale, in case that ever changes.
 */
@Composable
private fun titleFor(destination: NavDestination?): String = when {
    destination == null -> ""
    // T18 review fix (MEDIUM): these 3 were hardcoded English literals ever since Task 6 — invisible
    // debt before this task (the device's own OS locale governed every `stringResource` in the app
    // regardless, so "Home" was simply correct on every device that could ever reach it), a genuine
    // visible defect now that `com.mentora.android.locale.LocalizedContent` makes a language switch
    // real: the bottom-nav label directly below would correctly read "الرئيسية" while this title kept
    // reading "Home". All 3 string resources already existed (reused verbatim from
    // `MobileBottomNavigation`'s own tab labels) — same reasoning as `AiTutor`/`Profile` below.
    destination.hasRoute<Destination.Home>() -> stringResource(R.string.nav_home)
    destination.hasRoute<Destination.Explore>() -> stringResource(R.string.nav_explore)
    destination.hasRoute<Destination.MyLearning>() -> stringResource(R.string.nav_my_learning)
    // T17: localized, real string resource (already existed for `MobileBottomNavigation`'s own tab
    // label — reused verbatim here, same "AI Tutor" title text) — same reasoning as
    // `LearningPathDetails`/`Quiz`/`Certificates` above (this destination moves from placeholder to a
    // real screen in this task).
    destination.hasRoute<Destination.AiTutor>() -> stringResource(R.string.nav_ai_tutor)
    // T18: localized, real string resource — same "already existed for `MobileBottomNavigation`'s own
    // tab label, reused verbatim" reasoning as `AiTutor` above.
    destination.hasRoute<Destination.Profile>() -> stringResource(R.string.nav_profile)
    destination.hasRoute<Destination.Login>() -> "Login"
    destination.hasRoute<Destination.Register>() -> "Register"
    // T18 review fix (MEDIUM), same reasoning as `Home`/`Explore`/`MyLearning` above — a new string
    // resource, since no prior task needed one for this destination's own title specifically.
    destination.hasRoute<Destination.CourseDetails>() -> stringResource(R.string.course_details_nav_title)
    // T16: localized, real string resource — same reasoning as `Quiz`/`QuizResults`/`Certificates`
    // above (this destination moves from placeholder to a real screen in this task).
    destination.hasRoute<Destination.LearningPathDetails>() -> stringResource(R.string.learning_path_details_nav_title)
    destination.hasRoute<Destination.CoursePlayer>() -> "Course Player"
    // Task 14: localized, real string resources — same reasoning as `DemoCheckout`/`PurchaseSuccess`
    // below (T11 fix-up note): both destinations move from placeholder to real screens in this task.
    destination.hasRoute<Destination.Quiz>() -> stringResource(R.string.quiz_nav_title)
    destination.hasRoute<Destination.QuizResults>() -> stringResource(R.string.quiz_results_nav_title)
    // T15: localized, real string resources — same reasoning as `Quiz`/`QuizResults` above (both
    // destinations move from placeholder to real screens in this task).
    destination.hasRoute<Destination.Certificates>() -> stringResource(R.string.certificates_nav_title)
    destination.hasRoute<Destination.CertificateDetail>() -> stringResource(R.string.certificate_detail_nav_title)
    // T18: localized, real string resource — this destination moves from placeholder to a real
    // screen in this task, same reasoning as `Certificates`/`CertificateDetail` above.
    destination.hasRoute<Destination.Settings>() -> stringResource(R.string.settings_nav_title)
    destination.hasRoute<Destination.DemoCheckout>() -> stringResource(R.string.demo_checkout_nav_title)
    destination.hasRoute<Destination.PurchaseSuccess>() -> stringResource(R.string.purchase_success_nav_title)
    else -> ""
}
