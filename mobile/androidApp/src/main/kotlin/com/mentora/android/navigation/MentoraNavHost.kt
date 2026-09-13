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
import com.mentora.android.ui.screens.AiTutorScreen
import com.mentora.android.ui.screens.CertificateDetailScreen
import com.mentora.android.ui.screens.CertificatesScreen
import com.mentora.android.ui.screens.CourseDetailsScreen
import com.mentora.android.ui.screens.CoursePlayerScreen
import com.mentora.android.ui.screens.DemoCheckoutScreen
import com.mentora.android.ui.screens.ExploreScreen
import com.mentora.android.ui.screens.HomeScreen
import com.mentora.android.ui.auth.LoginScreen
import com.mentora.android.ui.auth.RegisterScreen
import com.mentora.android.ui.screens.LearningPathDetailsScreen
import com.mentora.android.ui.screens.LearningPathsScreen
import com.mentora.android.ui.screens.MyLearningScreen
import com.mentora.android.ui.screens.ProfileScreen
import com.mentora.android.ui.screens.PurchaseSuccessScreen
import com.mentora.android.ui.screens.QuizResultsScreen
import com.mentora.android.ui.screens.QuizScreen
import com.mentora.android.ui.screens.SettingsScreen
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
    val coursePlayerContent: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = { entry ->
        val args = entry.toRoute<Destination.CoursePlayer>()
        CoursePlayerScreen(
            courseId = args.courseId,
            lessonId = args.lessonId,
            onTakeQuiz = { navController.navigate(Destination.Quiz(args.courseId)) },
        )
    }
    val quizContent: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = { entry ->
        QuizScreen(courseId = entry.toRoute<Destination.Quiz>().courseId)
    }
    val quizResultsContent: @Composable AnimatedContentScope.(androidx.navigation.NavBackStackEntry) -> Unit = { entry ->
        val args = entry.toRoute<Destination.QuizResults>()
        QuizResultsScreen(courseId = args.courseId, attemptId = args.attemptId)
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
    val isFocusedLearningScreen = currentDestination?.hierarchy?.any {
        it.hasRoute<Destination.CoursePlayer>() || it.hasRoute<Destination.Quiz>()
    } ?: false
    // Bottom nav hidden on Course Player/Quiz (fully removed, never just dimmed — ux/MOBILE_UX.md
    // § 1) AND while a guest (the bottom nav only ever shows once authenticated).
    val showBottomNav = authState is AuthState.Authenticated && !isFocusedLearningScreen
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

    Scaffold(
        modifier = modifier,
        topBar = {
            if (!isAuthRoute) {
                MentoraTopBar(
                    title = titleFor(currentDestination),
                    showBackButton = !isTabRoot,
                    onBackClick = { navController.popBackStack() },
                )
            }
        },
        bottomBar = {
            val tab = currentTab
            if (showBottomNav && tab != null) {
                MobileBottomNavigation(
                    selectedTab = tab,
                    onTabSelected = { tapped ->
                        onTabTapped(navController, tapped, isCurrentTab = tapped == tab, anchorTab = anchorTab)
                    },
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
                composable<Destination.Home> { HomeScreen() }
                composable<Destination.CoursePlayer>(content = coursePlayerContent)
                composable<Destination.Quiz>(content = quizContent)
                composable<Destination.QuizResults>(content = quizResultsContent)
            }

            navigation<TabGraph.ExploreGraph>(startDestination = Destination.Explore) {
                composable<Destination.Explore> {
                    ExploreScreen(
                        onOpenCourseDetails = { courseId -> navController.navigate(Destination.CourseDetails(courseId)) },
                        onOpenLearningPaths = { navController.navigate(Destination.LearningPaths) },
                    )
                }
                composable<Destination.LearningPaths> {
                    LearningPathsScreen(
                        onOpenPathDetails = { pathId -> navController.navigate(Destination.LearningPathDetails(pathId)) },
                    )
                }
                composable<Destination.LearningPathDetails> { entry ->
                    LearningPathDetailsScreen(pathId = entry.toRoute<Destination.LearningPathDetails>().pathId)
                }
                composable<Destination.CourseDetails> { entry ->
                    val courseId = entry.toRoute<Destination.CourseDetails>().courseId
                    CourseDetailsScreen(
                        courseId = courseId,
                        onEnrollRequiringAuth = { requireAuth(Destination.DemoCheckout(courseId)) },
                        // T6 fix-up (Finding 5): Course Player is enrollment-gated per
                        // `ux/NAVIGATION_SPEC.md § 3` — this used to `navigate()` straight there,
                        // bypassing the auth gate every other gated action goes through.
                        onContinueLearning = { requireAuth(Destination.CoursePlayer(courseId)) },
                    )
                }
                composable<Destination.DemoCheckout> { entry ->
                    val courseId = entry.toRoute<Destination.DemoCheckout>().courseId
                    DemoCheckoutScreen(
                        courseId = courseId,
                        onCompletePurchase = {
                            navigateToPurchaseSuccess(navController, courseId) { anchorTab = it }
                        },
                    )
                }
                composable<Destination.CoursePlayer>(content = coursePlayerContent)
                composable<Destination.Quiz>(content = quizContent)
                composable<Destination.QuizResults>(content = quizResultsContent)
            }

            navigation<TabGraph.MyLearningGraph>(startDestination = Destination.MyLearning) {
                composable<Destination.MyLearning> { MyLearningScreen() }
                composable<Destination.CoursePlayer>(content = coursePlayerContent)
                composable<Destination.Quiz>(content = quizContent)
                composable<Destination.QuizResults>(content = quizResultsContent)
                composable<Destination.LearningPathDetails> { entry ->
                    LearningPathDetailsScreen(pathId = entry.toRoute<Destination.LearningPathDetails>().pathId)
                }
                composable<Destination.Certificates> { CertificatesScreen() }
                composable<Destination.CertificateDetail> { entry ->
                    CertificateDetailScreen(certificateId = entry.toRoute<Destination.CertificateDetail>().certificateId)
                }
                composable<Destination.PurchaseSuccess> { entry ->
                    PurchaseSuccessScreen(courseId = entry.toRoute<Destination.PurchaseSuccess>().courseId)
                }
            }

            navigation<TabGraph.AiTutorGraph>(startDestination = Destination.AiTutor) {
                composable<Destination.AiTutor> { AiTutorScreen() }
            }

            navigation<TabGraph.ProfileGraph>(startDestination = Destination.Profile) {
                composable<Destination.Profile> {
                    ProfileScreen(onOpenSettings = { navController.navigate(Destination.Settings) })
                }
                composable<Destination.Settings> { SettingsScreen() }
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
 * always a currently-valid anchor. See [MentoraNavHost]'s own kdoc for the full rationale. */
private fun onTabTapped(navController: NavHostController, tab: TabGraph, isCurrentTab: Boolean, anchorTab: TabGraph) {
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

private fun titleFor(destination: NavDestination?): String = when {
    destination == null -> ""
    destination.hasRoute<Destination.Home>() -> "Home"
    destination.hasRoute<Destination.Explore>() -> "Explore"
    destination.hasRoute<Destination.MyLearning>() -> "My Learning"
    destination.hasRoute<Destination.AiTutor>() -> "AI Tutor"
    destination.hasRoute<Destination.Profile>() -> "Profile"
    destination.hasRoute<Destination.Login>() -> "Login"
    destination.hasRoute<Destination.Register>() -> "Register"
    destination.hasRoute<Destination.CourseDetails>() -> "Course Details"
    destination.hasRoute<Destination.LearningPaths>() -> "Learning Paths"
    destination.hasRoute<Destination.LearningPathDetails>() -> "Learning Path Details"
    destination.hasRoute<Destination.CoursePlayer>() -> "Course Player"
    destination.hasRoute<Destination.Quiz>() -> "Quiz"
    destination.hasRoute<Destination.QuizResults>() -> "Quiz Results"
    destination.hasRoute<Destination.Certificates>() -> "Certificates"
    destination.hasRoute<Destination.CertificateDetail>() -> "Certificate"
    destination.hasRoute<Destination.Settings>() -> "Settings"
    destination.hasRoute<Destination.DemoCheckout>() -> "Demo Checkout"
    destination.hasRoute<Destination.PurchaseSuccess>() -> "Purchase Success"
    else -> ""
}
