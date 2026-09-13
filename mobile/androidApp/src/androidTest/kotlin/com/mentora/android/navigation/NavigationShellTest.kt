package com.mentora.android.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import com.mentora.android.ui.coursedetails.CourseDetailsCtaButtonTestTag
import com.mentora.android.ui.explore.ExploreCourseCardTestTag
import com.mentora.android.ui.shell.MobileBottomNavigationTestTag
import com.mentora.shared.MentoraSdk
import com.mentora.shared.auth.AndroidTokenStorage
import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.Role
import com.mentora.shared.auth.SessionUser
import com.mentora.shared.auth.TokenStorage
import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.defaultHttpClientEngine
import com.mentora.shared.settings.AndroidPreferenceStore
import com.mentora.shared.settings.PreferenceStore
import io.ktor.client.engine.HttpClientEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module

/**
 * T6 — the navigation shell's real, testable behaviors, per
 * `execution/PHASE_4_ANDROID_PLAN.md` T6's own test list. Drives [MentoraNavHost] directly with a
 * plain `authState: AuthState` value (a `mutableStateOf<AuthState>` the test flips itself) rather
 * than reading `sdk.auth` for THAT part — see `AuthGate.kt`'s kdoc for why that seam exists and is
 * exactly what makes tests 4/5 below possible without needing a real, functioning login/register
 * network round trip.
 *
 * T7 fix-up: [MentoraNavHost] now takes a required `sdk: MentoraSdk` (Login/Register's real
 * credential-form screens need one to construct their `AuthViewModel`). [buildTestSdk] below
 * constructs one via the public `MentoraSdk.create(...)` factory — the same one
 * `MentoraApplication.onCreate()` calls — rather than a "fake" (that class's own kdoc: `MentoraSdk`'s
 * constructor is `internal`, so `:androidApp` cannot construct a mock/fake, only a real instance).
 * This is safe here because `initKoin` builds a non-global `KoinApplication` (see that function's own
 * kdoc) and none of the tests below ever tap Login/Register's submit button, so this real `sdk`'s
 * `auth.login`/`auth.register` never actually fire a network call.
 */
@RunWith(AndroidJUnit4::class)
class NavigationShellTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var sdk: MentoraSdk

    /** Captured by every `setContent` call below (including [setContentWithAuthState]) so
     *  [openFirstCourseFromExplore]/[assertOnCourseDetailsFor] can read the REAL back-stack
     *  destination/args directly, instead of parsing screen text — see those functions' own kdoc for
     *  why that changed with T10's real Course Details screen. */
    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        sdk = buildTestSdk()
    }

    private fun buildTestSdk(): MentoraSdk {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val platformModule = module {
            single<TokenStorage> { AndroidTokenStorage(context) }
            single<PreferenceStore> { AndroidPreferenceStore(context) }
            single<HttpClientEngine> { defaultHttpClientEngine() }
        }
        return MentoraSdk.create(
            environment = ApiEnvironment.androidEmulator(),
            platformModule = platformModule,
            enableNetworkLogging = false,
        )
    }

    private val authenticatedStudent = AuthState.Authenticated(
        SessionUser(id = "u1", email = "ada@example.com", name = "Ada", role = Role.Student, preferredLocale = "en"),
    )

    private fun setContentWithAuthState(initial: AuthState) {
        composeTestRule.setContent {
            var authState by mutableStateOf(initial)
            // Exposed to test bodies via a local var isn't possible across the setContent boundary,
            // so tests that need to flip auth state declare it themselves (see test 4) instead of
            // calling this helper. This helper is for the fixed-auth-state tests (1, 2, 3, 5).
            val nc = rememberNavController()
            navController = nc
            MentoraTheme {
                MentoraNavHost(authState = authState, sdk = sdk, navController = nc)
            }
        }
    }

    /**
     * T9 fix-up: Explore is now the real screen (`ui/explore/ExploreScreen.kt`) — it needs at least
     * one real course to have loaded from the live backend before a course card exists to tap
     * (there is no fake `MentoraSdk`, see this class's own kdoc). Every test below that used to push
     * `CourseDetails` via the T6-era "Open Course Details" placeholder trigger now goes through this
     * helper instead, and every downstream assertion interpolates the REAL, dynamically-returned
     * course id (never a hardcoded `"course-1"` fixture, since the live backend — not this test —
     * decides which course search returns first).
     *
     * **T10 fix-up.** Course Details is now the real screen (`ui/coursedetails/CourseDetailsScreen.kt`)
     * — it no longer renders a literal `"Course Details (placeholder): $courseId"` text node to parse
     * the id back out of, so this now reads the id straight off [navController]'s own real back-stack
     * entry/args instead (requires [navController] to already be captured — see [setContentWithAuthState]
     * and every other `setContent` block below), and waits for the real screen's CTA button
     * ([CourseDetailsCtaButtonTestTag]) to exist before returning, proving the course actually finished
     * loading (not just that navigation started).
     */
    private fun openFirstCourseFromExplore(clickExploreTab: Boolean = true): String {
        if (clickExploreTab) {
            composeTestRule.onNodeWithTag("bottom_nav_explore").performClick()
        }
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(ExploreCourseCardTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithTag(ExploreCourseCardTestTag)[0].performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.CourseDetails>() == true
        }
        val courseId = navController.currentBackStackEntry!!.toRoute<Destination.CourseDetails>().courseId
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(CourseDetailsCtaButtonTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        return courseId
    }

    /** Replaces the old `onNodeWithText("Course Details (placeholder): $courseId").assertExists()`
     *  assertion (see [openFirstCourseFromExplore]'s own kdoc) — verifies via the REAL back-stack
     *  entry/args that Course Details is still the current destination for this exact [courseId], plus
     *  that the real screen's content actually rendered (its CTA button exists), not just that
     *  navigation is structurally positioned there. */
    private fun assertOnCourseDetailsFor(courseId: String) {
        val entry = navController.currentBackStackEntry
        assertTrue(
            "expected the current destination to be CourseDetails",
            entry?.destination?.hasRoute<Destination.CourseDetails>() == true,
        )
        assertEquals(courseId, entry!!.toRoute<Destination.CourseDetails>().courseId)
        composeTestRule.onNodeWithTag(CourseDetailsCtaButtonTestTag).assertExists()
    }

    // ---- Test 1: per-tab back-stack isolation ----
    @Test
    fun perTabBackStackIsolation_pushedSubDestinationSurvivesATabSwitchAwayAndBack() {
        setContentWithAuthState(authenticatedStudent)

        val courseId = openFirstCourseFromExplore()
        assertOnCourseDetailsFor(courseId)

        // Switch away to a different tab, then back.
        composeTestRule.onNodeWithTag("bottom_nav_my_learning").performClick()
        composeTestRule.onNodeWithText("My Learning (placeholder)").assertExists()

        composeTestRule.onNodeWithTag("bottom_nav_explore").performClick()

        // Explore's pushed CourseDetails is still on top — state was preserved, not reset to Explore's
        // own root.
        assertOnCourseDetailsFor(courseId)
    }

    // ---- Test 2: tap-active-tab-pops-to-root ----
    @Test
    fun tapActiveTabPopsToRoot_tappingTheCurrentTabAgainClearsItsPushedStack() {
        setContentWithAuthState(authenticatedStudent)

        val courseId = openFirstCourseFromExplore()
        assertOnCourseDetailsFor(courseId)

        // Tap the SAME (already active) Explore tab again.
        composeTestRule.onNodeWithTag("bottom_nav_explore").performClick()

        composeTestRule.onNodeWithText("Find your next course").assertExists()
        composeTestRule.onNodeWithTag(CourseDetailsCtaButtonTestTag).assertDoesNotExist()
    }

    // ---- Test 3: nav hidden on Course Player/Quiz, reappears on back ----
    /**
     * T10 fix-up: this test used to reach Course Player by tapping the OLD placeholder's
     * unconditional "Continue Learning" button (rendered regardless of any real enrollment state).
     * The real Course Details screen only shows that label/wires that click when
     * [com.mentora.android.ui.coursedetails.CourseDetailsCtaState.ContinueLearning] is the real,
     * backend-derived CTA state — which requires a genuinely enrolled, authenticated session. This
     * test class's whole suite deliberately never performs a real `sdk.auth.login()` (see this
     * class's own kdoc) — [authenticatedStudent] is only a UI-level `AuthState` the test sets
     * directly, so `sdk.enrollment.listEnrollments()` has no real access token backing it and
     * correctly fails-safe to "not enrolled" (`CourseDetailsViewModel.isEnrolledIn`'s own fail-safe).
     * This test's actual concern is `MentoraNavHost`'s bottom-nav-hide mechanism around
     * `CoursePlayer`/`Quiz` (a T6 concern) — not Course Details' CTA gating (a T10 concern) — so it
     * now reaches [Destination.CoursePlayer] by navigating directly via [navController], the same
     * technique tests 5/6 already use for back-stack-shape assertions, sidestepping a CTA state this
     * fake-auth harness cannot legitimately produce.
     */
    @Test
    fun bottomNavIsFullyHiddenOnCoursePlayerAndQuiz_andReappearsOnBack() {
        setContentWithAuthState(authenticatedStudent)

        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertExists()

        val courseId = openFirstCourseFromExplore()
        assertOnCourseDetailsFor(courseId)

        composeTestRule.runOnUiThread { navController.navigate(Destination.CoursePlayer(courseId)) }
        composeTestRule.onNodeWithText("Course Player (placeholder): $courseId").assertExists()

        // Fully removed from the tree, not just invisible.
        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertDoesNotExist()

        composeTestRule.onNodeWithText("Take Quiz").performClick()
        composeTestRule.onNodeWithText("Quiz (placeholder): $courseId").assertExists()
        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertDoesNotExist()

        // Back out of Quiz, then Course Player — the bottom nav reappears once neither is current.
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.onNodeWithText("Course Player (placeholder): $courseId").assertExists()
        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertDoesNotExist()

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        assertOnCourseDetailsFor(courseId)
        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertExists()
    }

    // ---- Test 3b (T10): the sticky CTA bar and the bottom nav coexist, both visible, on Course
    // Details — `mobile-course-details.json`'s resolved `conflicts[0]`. ----
    @Test
    fun courseDetails_stickyCtaBarCoexistsWithTheBottomNav_bothVisibleSimultaneously() {
        setContentWithAuthState(authenticatedStudent)

        openFirstCourseFromExplore()

        composeTestRule.onNodeWithTag(CourseDetailsCtaButtonTestTag).assertExists()
        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertExists()
    }

    // ---- Test 4: guest -> auth gate -> return to the original intent ----
    @Test
    fun guestTriggeringAuthGate_recordsPendingIntent_andReturnsToItAfterAuthentication() {
        lateinit var setAuthState: (AuthState) -> Unit
        composeTestRule.setContent {
            var authState by mutableStateOf<AuthState>(AuthState.Unauthenticated)
            setAuthState = { authState = it }
            val nc = rememberNavController()
            navController = nc
            MentoraTheme {
                MentoraNavHost(authState = authState, sdk = sdk, navController = nc)
            }
        }

        // Guest mode: no bottom nav, landed on Explore (never the Student-only Home).
        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertDoesNotExist()
        composeTestRule.onNodeWithText("Find your next course").assertExists()

        // Guest browsing is allowed with no redirect: Explore -> Course Details. Guest mode has no
        // bottom nav at all, and the app already opens straight onto Explore — no tab tap needed.
        val courseId = openFirstCourseFromExplore(clickExploreTab = false)
        assertOnCourseDetailsFor(courseId)

        // T10 fix-up: a guest's real CTA label is "Login to Enroll" (never the OLD placeholder's bare
        // "Enroll", which was unconditional regardless of auth state) —
        // `CourseDetailsViewModel`'s guest branch (`!isAuthenticated -> LoginToEnroll`). Both labels
        // wire to the identical `onEnrollRequiringAuth` callback/destination either way (this
        // composable's own kdoc), so the auth-gate mechanism under test here is unaffected — only the
        // button text asserted below changed.
        composeTestRule.onNodeWithText("Login to Enroll").performClick()
        composeTestRule.onNodeWithText("Log in to Mentora").assertExists()

        // Simulate the auth state flipping to Authenticated (e.g. a real login call resolving).
        composeTestRule.runOnUiThread { setAuthState(authenticatedStudent) }

        // Lands on the ORIGINALLY intended route (Demo Checkout for the same course), never a
        // generic Home.
        composeTestRule.onNodeWithText("Demo Checkout (placeholder): $courseId").assertExists()
    }

    // ---- Test 5: Purchase Success back-stack shape ----
    @Test
    fun purchaseSuccess_backLandsOnMyLearningRoot_neverBackIntoDemoCheckout() {
        composeTestRule.setContent {
            val nc = rememberNavController()
            navController = nc
            MentoraTheme {
                MentoraNavHost(authState = authenticatedStudent, sdk = sdk, navController = nc)
            }
        }

        val courseId = openFirstCourseFromExplore()
        // The fake `authenticatedStudent` AuthState carries no real backend session (this class's own
        // kdoc) — `listEnrollments()` therefore fails-safe to "not enrolled" and the real CTA reads
        // "Enroll" (`CourseDetailsCtaState.Enroll`), never "Continue Learning".
        composeTestRule.onNodeWithText("Enroll").performClick()
        composeTestRule.onNodeWithText("Demo Checkout (placeholder): $courseId").assertExists()

        composeTestRule.onNodeWithText("Complete Demo Purchase").performClick()
        composeTestRule.onNodeWithText("Purchase Success (placeholder): $courseId").assertExists()

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }

        composeTestRule.onNodeWithText("My Learning (placeholder)").assertExists()
        composeTestRule.onNodeWithText("Demo Checkout (placeholder): $courseId").assertDoesNotExist()
        composeTestRule.onNodeWithTag("bottom_nav_my_learning").assertExists()

        // Real back-stack shape (not just surface text) — Finding 1's fix-up: DemoCheckout/
        // CourseDetails/ExploreGraph must be genuinely GONE (the full-stack reset actually cleared
        // them), never lingering off-screen, and the top of stack must be My Learning's own root.
        val backStack = navController.currentBackStack.value
        assertTrue(
            "expected no DemoCheckout entry left anywhere on the back stack",
            backStack.none { it.destination.hasRoute<Destination.DemoCheckout>() },
        )
        assertTrue(
            "expected no CourseDetails entry left anywhere on the back stack",
            backStack.none { it.destination.hasRoute<Destination.CourseDetails>() },
        )
        assertTrue(
            "expected no ExploreGraph entry left anywhere on the back stack",
            backStack.none { it.destination.hasRoute<TabGraph.ExploreGraph>() },
        )
        assertTrue(
            "expected the top of the back stack to be My Learning's own root",
            backStack.last().destination.hasRoute<Destination.MyLearning>(),
        )
    }

    // ---- Test 6 (T6 fix-up, Finding 1/4): a full-stack reset must never permanently break the
    // multiple-back-stacks mechanism. This is the exact reviewer-reproduced scenario: complete a
    // purchase (one of the three full-stack-reset sites), then repeatedly switch among all 5 tabs
    // with sub-navigation pushed into 2 of them, and assert via the REAL back stack
    // (`currentBackStack.value`) that each tab's sub-stack is genuinely preserved — never reset to
    // root, never accumulating duplicate entries from the repeated switches. ----
    @Test
    fun purchaseThenRepeatedTabSwitches_preservesEachTabsSubStack_neverResetsNeverAccumulates() {
        composeTestRule.setContent {
            val nc = rememberNavController()
            navController = nc
            MentoraTheme {
                MentoraNavHost(authState = authenticatedStudent, sdk = sdk, navController = nc)
            }
        }

        // Complete a purchase — the full-stack reset in navigateToPurchaseSuccess.
        val firstCourseId = openFirstCourseFromExplore()
        composeTestRule.onNodeWithText("Enroll").performClick()
        composeTestRule.onNodeWithText("Complete Demo Purchase").performClick()
        composeTestRule.onNodeWithText("Purchase Success (placeholder): $firstCourseId").assertExists()

        // T6 fix-up (Finding 1) disclosed, NOT-in-scope-to-eliminate quirk: the very FIRST tab tap
        // immediately after a full-stack reset can be a dead no-op (navigation-compose's own
        // `restoreState = true` resolving a null saved-state key for a graph id that was just
        // discarded, not merely saved, by the preceding hard `popUpTo(graph.id) { inclusive = true }`
        // reset) — the SAME quirk the reviewer's own repro called out ("tap Home → nothing happens →
        // tap Home again → works"). This is independent of, and not eliminated by, Finding 1's actual
        // fix (confirmed: without the fix, the SECOND tap doesn't merely restore stale state, it
        // never restores at all — see this test's own history). Tapping twice here isolates that
        // known quirk from what this test actually needs to verify below.
        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.onNodeWithText("Home (placeholder)").assertExists()

        // Push sub-navigation into 2 different tabs, post-reset.
        val courseId = openFirstCourseFromExplore()
        assertOnCourseDetailsFor(courseId)

        composeTestRule.onNodeWithTag("bottom_nav_profile").performClick()
        composeTestRule.onNodeWithText("Settings").performClick()
        composeTestRule.onNodeWithText("Settings (placeholder)").assertExists()

        // Repeatedly switch among all 5 tabs, more than once. Note: `currentBackStack.value` only
        // ever reflects the CURRENTLY active tab's own resident subtree — every other tab's subtree
        // is, by the correct/intended `saveState`/`restoreState` design this task mandates, physically
        // POPPED off the real back stack while inactive and held in a separate saved-state side
        // registry (restored back onto the real stack only once that tab is selected again). So the
        // "no accumulation" assertions below are taken right after switching TO Explore/Profile
        // (while each is still the active, on-stack tab), not after the loop has moved on to a
        // different tab.
        var courseDetailsCount = 0
        var settingsCount = 0
        var myLearningGraphCount = 0
        repeat(2) {
            composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
            composeTestRule.onNodeWithTag("bottom_nav_my_learning").performClick()
            composeTestRule.onNodeWithTag("bottom_nav_ai_tutor").performClick()

            composeTestRule.onNodeWithTag("bottom_nav_explore").performClick()
            // Explore's pushed CourseDetails must still be on top — preserved, not reset to root.
            assertOnCourseDetailsFor(courseId)
            val afterExplore = navController.currentBackStack.value
            courseDetailsCount = afterExplore.count { it.destination.hasRoute<Destination.CourseDetails>() }
            myLearningGraphCount = afterExplore.count { it.destination.hasRoute<TabGraph.MyLearningGraph>() }

            composeTestRule.onNodeWithTag("bottom_nav_profile").performClick()
            // Profile's pushed Settings must still be on top too.
            composeTestRule.onNodeWithText("Settings (placeholder)").assertExists()
            val afterProfile = navController.currentBackStack.value
            settingsCount = afterProfile.count { it.destination.hasRoute<Destination.Settings>() }
        }

        // The assertion that actually would have caught Finding 1: the repeated switches above must
        // not have accumulated duplicate entries on the real back stack.
        assertEquals(
            "Explore's pushed CourseDetails must be preserved exactly once, never duplicated by repeated tab switches",
            1,
            courseDetailsCount,
        )
        assertEquals(
            "Profile's pushed Settings must be preserved exactly once, never duplicated by repeated tab switches",
            1,
            settingsCount,
        )
        assertEquals(
            "MyLearningGraph (this session's post-purchase anchor) must appear exactly once, never re-pushed " +
                "on top of itself by repeated tab switches",
            1,
            myLearningGraphCount,
        )
    }
}
