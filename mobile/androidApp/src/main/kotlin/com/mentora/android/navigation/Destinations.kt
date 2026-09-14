package com.mentora.android.navigation

import androidx.compose.runtime.saveable.Saver
import kotlinx.serialization.Serializable

/**
 * T6 — the complete Android navigation route model: `execution/PHASE_4_ANDROID_PLAN.md` § 2's
 * 18-screen inventory, minus the two screens that don't exist on mobile at all (Landing, a
 * standalone Progress screen). Every route is a `@Serializable` type consumed by
 * Navigation-Compose's type-safe API (`composable<T>()` / `navigate(T)` / `popUpTo<T>()` /
 * `popBackStack<T>()`) — chosen over plain string-arg routes because navigation-compose 2.8.x (the
 * version pinned for this task, see `gradle/libs.versions.toml`'s own comment on `androidxNavigation`)
 * makes this the lower-friction default: IDs are carried as typed constructor args (`courseId:
 * String`, never string-interpolated into a route pattern), there is no manual
 * `NavType`/argument-parsing boilerplate, and every `navigate()` call site is compile-time checked.
 * This is also exactly the pattern Google's own "Now in Android" sample and its official
 * multiple-back-stacks guidance use.
 *
 * [TabGraph] is the separate, parallel set of 5 marker routes for the *nested graph* each bottom-nav
 * tab owns (`navigation<TabGraph.HomeGraph>(startDestination = Destination.Home) { ... }` in
 * [MentoraNavHost]) — never rendered as a screen itself; it only exists as the `navigate()` target
 * the bottom-nav-tap handler uses for the documented `popUpTo(...) { saveState = true }` /
 * `restoreState = true` dance (see [TabGraphSaver]'s kdoc below for exactly which destination that
 * `popUpTo` targets, and why).
 *
 * Screens reachable from more than one tab per `ux/NAVIGATION_SPEC.md § 3` ([Destination.CoursePlayer],
 * [Destination.Quiz], [Destination.QuizResults], [Destination.LearningPathDetails]) are deliberately
 * registered as `composable<T>()` children of EVERY tab-graph that can reach them (see
 * `MentoraNavHost.kt`). **Not because an unregistered route is unreachable** — Navigation-Compose
 * 2.8.3's `matchRouteComprehensive` searches the current graph's own children first, then falls back
 * to its PARENT graph's ancestry (child match wins ties), so `navigate(route)` for a route nested only
 * under one tab's graph still resolves, just under the WRONG tab: the push lands as a child of
 * whichever graph actually declares it, not the tab the user was on, silently mis-anchoring that tab's
 * bottom-nav highlight and any later `popUpTo(anchorTab)`-based reset. Duplicating the *registration*
 * (never the route *type* — there is exactly one `Destination.CoursePlayer` class) across every tab
 * that can push it is the correct, intended shape of this pattern for a shared deep screen, not a
 * workaround; each registration resolves to the identical destination ID since the ID is derived
 * purely from the route's own serialized shape, not from which graph it's registered under.
 *
 * T6 fix-up (Finding 2): the sealed interface itself is `@Serializable` (in addition to every one
 * of its individual subtypes already being `@Serializable`, unchanged) so kotlinx-serialization's
 * sealed-hierarchy support can generate a single polymorphic serializer for the whole `Destination`
 * type. That's what lets [com.mentora.android.navigation.PendingNavIntentSaver] round-trip an
 * arbitrary [Destination] value (whichever concrete subtype it happens to be) to/from a JSON string
 * for `rememberSaveable` — see that Saver's kdoc in `AuthGate.kt`.
 */
@Serializable
sealed interface Destination {
    // ---- The 5 bottom-nav tab roots, in the locked order (navigation.json#/mobileStudentShell) ----
    @Serializable
    data object Home : Destination

    @Serializable
    data object Explore : Destination

    @Serializable
    data object MyLearning : Destination

    @Serializable
    data object AiTutor : Destination

    @Serializable
    data object Profile : Destination

    // ---- Outside the tab bar entirely (ux/NAVIGATION_SPEC.md § 3: "Login | (outside tab bar)") ----
    @Serializable
    data object Login : Destination

    @Serializable
    data object Register : Destination

    // ---- Explore subtree ----
    @Serializable
    data class CourseDetails(val courseId: String) : Destination

    /**
     * T9 fix-up: the T6-era `Destination.LearningPaths` PUSH destination (and its placeholder
     * screen) is removed — `ux/MOBILE_UX.md § 3`'s "segment/tab... inside Explore" requirement is now
     * literally satisfied by `ExploreScreen`'s own internal tab state
     * ([com.mentora.android.ui.explore.ExploreTab]), not by a second pushed screen showing the exact
     * same list. [LearningPathDetails] (the per-path detail screen) is unaffected — still its own
     * pushed destination, reachable from Explore's Learning Paths tab.
     */
    @Serializable
    data class LearningPathDetails(val pathId: String) : Destination

    // ---- Checkout (ux/NAVIGATION_SPEC.md § 6 — Purchase Success's back-stack rule) ----
    @Serializable
    data class DemoCheckout(val courseId: String) : Destination

    @Serializable
    data class PurchaseSuccess(val courseId: String) : Destination

    // ---- Course Player / Quiz — the two screens that hide the bottom nav entirely ----
    @Serializable
    data class CoursePlayer(val courseId: String, val lessonId: String? = null) : Destination

    @Serializable
    data class Quiz(val courseId: String) : Destination

    /**
     * Task 14 fix-up: the T6-era `attemptId: String? = null` param is removed — dead API surface,
     * never populated by anything (no domain type anywhere carries an attempt id;
     * [com.mentora.shared.domain.model.QuizAttemptResult] has none). Quiz Results always looks up
     * "the latest attempt" itself (`QuizResultsViewModel`'s own `getLatestAttempt` call, this task's
     * decision 1 — re-fetch, don't pass the attempt through navigation), so this param served no
     * purpose.
     */
    @Serializable
    data class QuizResults(val courseId: String) : Destination

    // ---- My Learning subtree ----
    @Serializable
    data object Certificates : Destination

    @Serializable
    data class CertificateDetail(val certificateId: String) : Destination

    // ---- Profile subtree ----
    @Serializable
    data object Settings : Destination
}

/** One marker route per bottom-nav tab's nested graph — see this file's kdoc. Never has its own UI. */
sealed interface TabGraph {
    @Serializable
    data object HomeGraph : TabGraph

    @Serializable
    data object ExploreGraph : TabGraph

    @Serializable
    data object MyLearningGraph : TabGraph

    @Serializable
    data object AiTutorGraph : TabGraph

    @Serializable
    data object ProfileGraph : TabGraph
}

/** All 5 [TabGraph] values, in the locked bottom-nav order. */
val AllTabGraphs: List<TabGraph> = listOf(
    TabGraph.HomeGraph,
    TabGraph.ExploreGraph,
    TabGraph.MyLearningGraph,
    TabGraph.AiTutorGraph,
    TabGraph.ProfileGraph,
)

/**
 * T6 fix-up (Finding 1): [MentoraNavHost] tracks which [TabGraph] currently sits at the BOTTOM of
 * the back stack (the "anchor") in a `rememberSaveable(saver = TabGraphSaver)` — see that
 * composable's `anchorTab` state and its kdoc for why `navController.graph.findStartDestination()`
 * (a purely structural lookup, fixed to whichever tab [androidx.navigation.compose.NavHost]'s own
 * `startDestination` param was at first mount) is no longer used as the tab-switch `popUpTo` target.
 * [TabGraph] values aren't `Parcelable`/`Serializable` (Java) themselves, so this Saver stores just
 * an index into [AllTabGraphs] (a fixed, ordered, closed set of 5) rather than pulling in
 * kotlinx-serialization for something this simple.
 */
val TabGraphSaver: Saver<TabGraph, Int> = Saver(
    save = { AllTabGraphs.indexOf(it) },
    restore = { index -> AllTabGraphs[index] },
)
