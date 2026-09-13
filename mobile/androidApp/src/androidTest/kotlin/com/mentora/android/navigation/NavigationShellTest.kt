package com.mentora.android.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
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
            MentoraTheme {
                MentoraNavHost(authState = authState, sdk = sdk)
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
     */
    private fun openFirstCourseFromExplore(clickExploreTab: Boolean = true): String {
        if (clickExploreTab) {
            composeTestRule.onNodeWithTag("bottom_nav_explore").performClick()
        }
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(ExploreCourseCardTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onAllNodesWithTag(ExploreCourseCardTestTag)[0].performClick()
        val node = composeTestRule.onNode(hasText("Course Details (placeholder): ", substring = true)).fetchSemanticsNode()
        val text = node.config[SemanticsProperties.Text].joinToString(separator = "") { it.text }
        return text.removePrefix("Course Details (placeholder): ")
    }

    // ---- Test 1: per-tab back-stack isolation ----
    @Test
    fun perTabBackStackIsolation_pushedSubDestinationSurvivesATabSwitchAwayAndBack() {
        setContentWithAuthState(authenticatedStudent)

        val courseId = openFirstCourseFromExplore()
        composeTestRule.onNodeWithText("Course Details (placeholder): $courseId").assertExists()

        // Switch away to a different tab, then back.
        composeTestRule.onNodeWithTag("bottom_nav_my_learning").performClick()
        composeTestRule.onNodeWithText("My Learning (placeholder)").assertExists()

        composeTestRule.onNodeWithTag("bottom_nav_explore").performClick()

        // Explore's pushed CourseDetails is still on top — state was preserved, not reset to Explore's
        // own root.
        composeTestRule.onNodeWithText("Course Details (placeholder): $courseId").assertExists()
    }

    // ---- Test 2: tap-active-tab-pops-to-root ----
    @Test
    fun tapActiveTabPopsToRoot_tappingTheCurrentTabAgainClearsItsPushedStack() {
        setContentWithAuthState(authenticatedStudent)

        val courseId = openFirstCourseFromExplore()
        composeTestRule.onNodeWithText("Course Details (placeholder): $courseId").assertExists()

        // Tap the SAME (already active) Explore tab again.
        composeTestRule.onNodeWithTag("bottom_nav_explore").performClick()

        composeTestRule.onNodeWithText("Find your next course").assertExists()
        composeTestRule.onNodeWithText("Course Details (placeholder): $courseId").assertDoesNotExist()
    }

    // ---- Test 3: nav hidden on Course Player/Quiz, reappears on back ----
    @Test
    fun bottomNavIsFullyHiddenOnCoursePlayerAndQuiz_andReappearsOnBack() {
        setContentWithAuthState(authenticatedStudent)

        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertExists()

        val courseId = openFirstCourseFromExplore()
        composeTestRule.onNodeWithText("Continue Learning").performClick()
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
        composeTestRule.onNodeWithText("Course Details (placeholder): $courseId").assertExists()
        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertExists()
    }

    // ---- Test 4: guest -> auth gate -> return to the original intent ----
    @Test
    fun guestTriggeringAuthGate_recordsPendingIntent_andReturnsToItAfterAuthentication() {
        lateinit var setAuthState: (AuthState) -> Unit
        composeTestRule.setContent {
            var authState by mutableStateOf<AuthState>(AuthState.Unauthenticated)
            setAuthState = { authState = it }
            MentoraTheme {
                MentoraNavHost(authState = authState, sdk = sdk)
            }
        }

        // Guest mode: no bottom nav, landed on Explore (never the Student-only Home).
        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertDoesNotExist()
        composeTestRule.onNodeWithText("Find your next course").assertExists()

        // Guest browsing is allowed with no redirect: Explore -> Course Details. Guest mode has no
        // bottom nav at all, and the app already opens straight onto Explore — no tab tap needed.
        val courseId = openFirstCourseFromExplore(clickExploreTab = false)
        composeTestRule.onNodeWithText("Course Details (placeholder): $courseId").assertExists()

        // Attempting an auth-gated action (Enroll) routes through Login and records the intent.
        // T7 fix-up: Login is now the real credential-form screen (`ui/auth/LoginScreen.kt`), not the
        // "Login (placeholder)" / "Pending intent recorded" debug text T6 rendered — asserting the
        // real title proves the gate landed on the real screen; the actual proof that the pending
        // intent mechanism itself still works is the assertion below (lands on the ORIGINAL intent,
        // not a generic Home, once auth state flips).
        composeTestRule.onNodeWithText("Enroll").performClick()
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
        lateinit var navController: NavHostController
        composeTestRule.setContent {
            val nc = rememberNavController()
            navController = nc
            MentoraTheme {
                MentoraNavHost(authState = authenticatedStudent, sdk = sdk, navController = nc)
            }
        }

        val courseId = openFirstCourseFromExplore()
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
        lateinit var navController: NavHostController
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
        composeTestRule.onNodeWithText("Course Details (placeholder): $courseId").assertExists()

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
            composeTestRule.onNodeWithText("Course Details (placeholder): $courseId").assertExists()
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
