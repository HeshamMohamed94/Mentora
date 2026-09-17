package com.mentora.android.navigation

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
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
import com.mentora.android.theme.ThemeController
import com.mentora.android.ui.checkout.DemoCheckoutConfirmButtonTestTag
import com.mentora.android.ui.checkout.PurchaseSuccessBackToMyLearningButtonTestTag
import com.mentora.android.ui.checkout.PurchaseSuccessContentTestTag
import com.mentora.android.ui.checkout.PurchaseSuccessStartLearningButtonTestTag
import com.mentora.android.ui.coursedetails.CourseDetailsCtaButtonTestTag
import com.mentora.android.ui.courseplayer.CoursePlayerPrimaryActionTestTag
import com.mentora.android.ui.courseplayer.CoursePlayerScreenTestTag
import com.mentora.android.ui.quiz.QuizPrimaryActionTestTag
import com.mentora.android.ui.quiz.QuizScreenTestTag
import com.mentora.android.ui.quiz.QuizResultsPrimaryActionTestTag
import com.mentora.android.ui.explore.ExploreCourseCardTestTag
import com.mentora.android.ui.home.HomeCertificatesStatCardTestTag
import com.mentora.android.ui.home.HomeContinueLearningCardTestTag
import com.mentora.android.ui.home.HomeScreenTestTag
import com.mentora.android.ui.mylearning.MyLearningScreenTestTag
import com.mentora.android.ui.profile.SettingsScreenTestTag
import com.mentora.android.ui.shell.MobileBottomNavigationTestTag
import com.mentora.shared.MentoraSdk
import com.mentora.shared.auth.AndroidTokenStorage
import com.mentora.shared.auth.AuthState
import com.mentora.shared.auth.Role
import com.mentora.shared.auth.SessionUser
import com.mentora.shared.auth.TokenStorage
import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.defaultHttpClientEngine
import com.mentora.shared.settings.AndroidPreferenceStore
import com.mentora.shared.settings.PreferenceStore
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.runBlocking
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
 * credential-form screens need one to construct their `AuthViewModel`). [sharedSdk] below
 * constructs one via the public `MentoraSdk.create(...)` factory — the same one
 * `MentoraApplication.onCreate()` calls — rather than a "fake" (that class's own kdoc: `MentoraSdk`'s
 * constructor is `internal`, so `:androidApp` cannot construct a mock/fake, only a real instance).
 * This is safe here because `initKoin` builds a non-global `KoinApplication` (see that function's own
 * kdoc) and every test except [registerFreshRealStudent]'s two callers never taps a real
 * login/register network call at all.
 *
 * **T11 fix-up — ONE [MentoraSdk] for the whole class run, not one per test method.** Originally
 * `@Before` built a fresh `sdk` per test (`buildTestSdk()`, now removed). That looked safe — each
 * `MentoraSdk.create(...)` builds its own non-global Koin graph, its own `HttpClient`, and its own
 * `SessionManager` — but `AndroidTokenStorage` delegates to `by preferencesDataStore(name =
 * "mentora_secure_tokens")`, a SINGLE process-wide file, and nothing ever closes a test's Koin graph
 * afterward. So N still-alive per-test SDKs ended up sharing that one token file: once
 * [purchaseSuccess_backLandsOnMyLearningRoot_neverBackIntoDemoCheckout] and
 * [purchaseThenRepeatedTabSwitches_preservesEachTabsSubStack_neverResetsNeverAccumulates] (the first
 * two tests in this class needing a genuinely completed real purchase — `GET/POST .../checkout` have
 * no `listEnrollments`-style fail-safe, per `execution/INTEGRATION_CONTRACT.md`'s Enrollment section:
 * "Student role required on all three routes") both existed, whichever ran SECOND could observe a
 * real enrollment that actually belonged to the FIRST one's still-live `SessionManager` racing a
 * write to that shared file — never a false "enrolled" from bad production logic, always a real
 * completed purchase, just attributed to the wrong test (confirmed via direct backend JWT/DB
 * inspection). A single shared `sdk` for the whole class removes the second `SessionManager`
 * entirely, which is the actual fix — not a disclosed, unfixable quirk (contrast Task 6's genuinely
 * navigation-runtime "first tap after a full-stack reset" quirk, which stays disclosed).
 * [registerFreshRealStudent]'s own `logout()` remains: with one shared `sdk`, an EARLIER test in the
 * same run can still leave a real session logged in when [registerFreshRealStudent] needs a clean one.
 */
@RunWith(AndroidJUnit4::class)
class NavigationShellTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var sdk: MentoraSdk

    /** T18 review fix: `MentoraNavHost` now requires a real [ThemeController] (threaded from
     *  `MainActivity`'s own `app.themeController` in production — see that parameter's own kdoc on
     *  `MentoraNavHost` for why this test can no longer let `SettingsScreen` resolve one via
     *  `LocalContext.current.applicationContext as MentoraApplication`, which this class's own
     *  `NoOpApplicationTestRunner` makes impossible). Built from the SAME [sharedPreferenceStore]
     *  instance [sharedSdk]'s own Koin module uses — not a second, independent one — mirroring
     *  `MentoraApplication`'s real "one `AndroidPreferenceStore` instance, handed to both Koin and
     *  this controller" contract. */
    private lateinit var themeController: ThemeController

    /** Captured by every `setContent` call below (including [setContentWithAuthState]) so
     *  [openFirstCourseFromExplore]/[assertOnCourseDetailsFor] can read the REAL back-stack
     *  destination/args directly, instead of parsing screen text — see those functions' own kdoc for
     *  why that changed with T10's real Course Details screen. */
    private lateinit var navController: NavHostController

    @Before
    fun setUp() {
        sdk = sharedSdk
        themeController = sharedThemeController
    }

    companion object {
        /** See [sharedSdk]'s own kdoc — the single [AndroidPreferenceStore] both it and
         *  [sharedThemeController] are built from. */
        private val sharedPreferenceStore: AndroidPreferenceStore by lazy {
            AndroidPreferenceStore(ApplicationProvider.getApplicationContext())
        }

        /** See this class's own kdoc ("ONE `MentoraSdk` for the whole class run"). `by lazy`'s
         *  default `SYNCHRONIZED` mode makes the one-time construction safe regardless of which
         *  thread the JUnit runner first calls [setUp] from. */
        private val sharedSdk: MentoraSdk by lazy {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val platformModule = module {
                single<TokenStorage> { AndroidTokenStorage(context) }
                single<PreferenceStore> { sharedPreferenceStore }
                single<HttpClientEngine> { defaultHttpClientEngine() }
            }
            MentoraSdk.create(
                environment = ApiEnvironment.androidEmulator(),
                platformModule = platformModule,
                enableNetworkLogging = false,
            )
        }

        private val sharedThemeController: ThemeController by lazy { ThemeController(sharedPreferenceStore, sharedSdk) }
    }

    private val authenticatedStudent = AuthState.Authenticated(
        SessionUser(id = "u1", email = "ada@example.com", name = "Ada", role = Role.Student, preferredLocale = "en"),
    )

    /**
     * T11 fix-up: `GET .../checkout` + `POST .../checkout/complete` are both real, Student-role-gated
     * endpoints with no fail-safe fallback (unlike `listEnrollments`'s deliberate "no token -> not
     * enrolled" degradation — `execution/INTEGRATION_CONTRACT.md`'s Enrollment section: "Student role
     * required on all three routes"). Reaching PurchaseSuccess for real (tests 5/6's actual subject:
     * the real `navigateToPurchaseSuccess` full-stack-reset mechanism) needs a real access token
     * backing [sdk]'s own HTTP client, which this class's fake-`authState`-only harness (see this
     * class's own kdoc) never provides on its own. Registers a brand-new, unique throwaway account per
     * test run — same disposable-account precedent as Task 7's own live-backend verification — rather
     * than reusing a seeded demo account, so the CTA is guaranteed to read "Enroll" (never "Continue
     * Learning" from a stale prior run's real enrollment) every time this runs. Neither account is
     * deleted afterward (no delete-account/unenroll endpoint exists to call from an instrumented test,
     * unlike Task 7's one-time manual `mongosh` cleanup) — repeated real runs of this class add
     * throwaway `t11-navshelltest-*@example.com` accounts/enrollments to the shared local dev
     * database; sweep them out manually before using that database for a demo.
     *
     * **Logs out first.** With [sdk] now shared for the whole class run (see this class's own kdoc),
     * an EARLIER test can leave a real session logged in — [purchaseSuccess_backLandsOnMyLearningRoot_neverBackIntoDemoCheckout]/
     * [purchaseThenRepeatedTabSwitches_preservesEachTabsSubStack_neverResetsNeverAccumulates] both
     * need their OWN clean session before registering, not a leftover one.
     */
    private fun registerFreshRealStudent(prefix: String = "t11-navshelltest") {
        runBlocking { runCatching { sdk.auth.logout() } }
        val email = "$prefix-${System.currentTimeMillis()}@example.com"
        val result = runBlocking {
            sdk.auth.register(email = email, password = "MentoraTest1", name = "Test Student")
        }
        check(result is ApiResult.Success) { "real register failed in test setup: $result" }
    }

    private fun setContentWithAuthState(initial: AuthState) {
        composeTestRule.setContent {
            var authState by mutableStateOf(initial)
            // Exposed to test bodies via a local var isn't possible across the setContent boundary,
            // so tests that need to flip auth state declare it themselves (see test 4) instead of
            // calling this helper. This helper is for the fixed-auth-state tests (1, 2, 3, 5).
            val nc = rememberNavController()
            navController = nc
            MentoraTheme {
                MentoraNavHost(authState = authState, sdk = sdk, themeController = themeController, navController = nc)
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
        // T12 fix-up: My Learning is now the real screen (`ui/mylearning/MyLearningScreen.kt`), which
        // no longer renders a literal "My Learning (placeholder)" text node — asserts on the real
        // screen's own root test tag instead (present regardless of its async load state), same
        // T10-established pattern as every other placeholder-text assertion this phase has retired.
        composeTestRule.onNodeWithTag("bottom_nav_my_learning").performClick()
        composeTestRule.onNodeWithTag(MyLearningScreenTestTag).assertExists()

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
     *
     * T13 fix-up: Course Player is now the real screen (`ui/courseplayer/CoursePlayerScreen.kt`),
     * which no longer renders a literal "Course Player (placeholder): $courseId" text node — asserts
     * on the real screen's own root test tag instead ([CoursePlayerScreenTestTag], present regardless
     * of async load state — this harness's fake auth means `getCourseProgress` genuinely fails with
     * `ForbiddenNotEnrolled` here, landing on the real screen's Error state, which still renders that
     * tag; same T10-established pattern as every other placeholder-text assertion this phase has
     * retired). Reaching Quiz now navigates directly via [navController] too, instead of tapping a
     * "Take Quiz" button — the real screen's footer never reaches a "Take Quiz" state in this
     * un-enrolled harness (D85's own "Tests that must change" note: this harness can never legitimately
     * reach a real 100%-complete "Take Quiz" state).
     */
    @Test
    fun bottomNavIsFullyHiddenOnCoursePlayerAndQuiz_andReappearsOnBack() {
        setContentWithAuthState(authenticatedStudent)

        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertExists()

        val courseId = openFirstCourseFromExplore()
        assertOnCourseDetailsFor(courseId)

        composeTestRule.runOnUiThread { navController.navigate(Destination.CoursePlayer(courseId)) }
        composeTestRule.onNodeWithTag(CoursePlayerScreenTestTag).assertExists()

        // Fully removed from the tree, not just invisible.
        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertDoesNotExist()

        composeTestRule.runOnUiThread { navController.navigate(Destination.Quiz(courseId)) }
        // T14 fix-up: Quiz's placeholder was retired for the real QuizScreen (same pattern as T10/T13's
        // own placeholder-retirement fix-ups) — this harness's fake auth has no real enrollment, so the
        // real screen renders an Error content state; QuizScreenTestTag is on the outer Scaffold (an
        // ancestor of every content state), same "present regardless of async load state" convention.
        composeTestRule.onNodeWithTag(QuizScreenTestTag).assertExists()
        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertDoesNotExist()

        // Back out of Quiz, then Course Player — the bottom nav reappears once neither is current.
        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.onNodeWithTag(CoursePlayerScreenTestTag).assertExists()
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
                MentoraNavHost(authState = authState, sdk = sdk, themeController = themeController, navController = nc)
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
        // T11 fix-up: DemoCheckout is now the real screen (`ui/checkout/DemoCheckoutScreen.kt`), which
        // no longer renders a literal "Demo Checkout (placeholder): $courseId" text node — this reads
        // the id straight off the real back-stack entry/args instead, same T10-established pattern as
        // `assertOnCourseDetailsFor`.
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.DemoCheckout>() == true
        }
        assertEquals(courseId, navController.currentBackStackEntry!!.toRoute<Destination.DemoCheckout>().courseId)
    }

    // ---- Test 5: Purchase Success back-stack shape ----
    @Test
    fun purchaseSuccess_backLandsOnMyLearningRoot_neverBackIntoDemoCheckout() {
        registerFreshRealStudent()
        composeTestRule.setContent {
            val nc = rememberNavController()
            navController = nc
            MentoraTheme {
                MentoraNavHost(authState = authenticatedStudent, sdk = sdk, themeController = themeController, navController = nc)
            }
        }

        val courseId = openFirstCourseFromExplore()
        // T11: [registerFreshRealStudent] just established a real, brand-new session — the real CTA
        // reads "Enroll" (`CourseDetailsCtaState.Enroll`) because this account genuinely has zero
        // enrollments yet, not because of any fail-safe (contrast the fake-`authState`-only tests
        // above, which never reach a real `listEnrollments()` call at all).
        // T11 fix-up: DemoCheckout/PurchaseSuccess are now the real screens (`ui/checkout/*.kt`),
        // which no longer render literal "... (placeholder): $courseId" text nodes — these reads go
        // straight off the real back-stack entry/args instead (same T10-established pattern as
        // `assertOnCourseDetailsFor`), and the confirm tap now waits for the real screen's async
        // checkout-preview fetch to resolve (the old placeholder rendered its button unconditionally,
        // with no loading state) before performing it, via the real screen's own test tag.
        composeTestRule.onNodeWithText("Enroll").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.DemoCheckout>() == true
        }
        assertEquals(courseId, navController.currentBackStackEntry!!.toRoute<Destination.DemoCheckout>().courseId)

        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(DemoCheckoutConfirmButtonTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(DemoCheckoutConfirmButtonTestTag).performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.PurchaseSuccess>() == true
        }
        composeTestRule.onNodeWithTag(PurchaseSuccessContentTestTag).assertExists()

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }

        // T12 fix-up: same real-screen test-tag treatment as test 1 above.
        composeTestRule.onNodeWithTag(MyLearningScreenTestTag).assertExists()
        composeTestRule.onNodeWithTag(PurchaseSuccessContentTestTag).assertDoesNotExist()
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
        registerFreshRealStudent()
        composeTestRule.setContent {
            val nc = rememberNavController()
            navController = nc
            MentoraTheme {
                MentoraNavHost(authState = authenticatedStudent, sdk = sdk, themeController = themeController, navController = nc)
            }
        }

        // Complete a purchase — the full-stack reset in navigateToPurchaseSuccess.
        // T11 fix-up: same real-screen wait/tag treatment as test 5 above — see that test's own
        // comment for the full rationale.
        val firstCourseId = openFirstCourseFromExplore()
        composeTestRule.onNodeWithText("Enroll").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(DemoCheckoutConfirmButtonTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(DemoCheckoutConfirmButtonTestTag).performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.PurchaseSuccess>() == true
        }
        assertEquals(firstCourseId, navController.currentBackStackEntry!!.toRoute<Destination.PurchaseSuccess>().courseId)

        // T11 fix-up: Purchase Success is now the real, `shell: "none"` screen (`ui/checkout/
        // PurchaseSuccessScreen.kt`) — it genuinely hides the bottom nav (see `MentoraNavHost.kt`'s
        // `isPurchaseSuccess` flag), unlike the T6-era placeholder this test originally exercised,
        // which never hid it. This test's own subject (repeated tab switches after the full-stack
        // reset) needs to be ON one of the 5 tabs first — tapping the real "Back to My Learning"
        // action lands on the SAME already-reset My Learning root test 5 verifies (that button's own
        // callback is a plain `popBackStack()`, per `MentoraNavHost.kt`), which is exactly where the
        // bottom nav becomes visible again.
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(PurchaseSuccessBackToMyLearningButtonTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(PurchaseSuccessBackToMyLearningButtonTestTag).performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.MyLearning>() == true
        }

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
        // T12 fix-up: Home is now the real screen (`ui/home/HomeScreen.kt`), which no longer renders
        // a literal "Home (placeholder)" text node — same real-screen test-tag treatment as every
        // other placeholder-text assertion this phase has retired.
        composeTestRule.onNodeWithTag(HomeScreenTestTag).assertExists()

        // Push sub-navigation into 2 different tabs, post-reset.
        val courseId = openFirstCourseFromExplore()
        assertOnCourseDetailsFor(courseId)

        composeTestRule.onNodeWithTag("bottom_nav_profile").performClick()
        composeTestRule.onNodeWithText("Settings").performClick()
        // T18 fix-up: Settings is now the real screen (`ui/profile/SettingsScreen.kt`), which no
        // longer renders a literal "Settings (placeholder)" text node — same real-screen test-tag
        // treatment as every other placeholder-text assertion this phase has retired.
        composeTestRule.onNodeWithTag(SettingsScreenTestTag).assertExists()

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
            composeTestRule.onNodeWithTag(SettingsScreenTestTag).assertExists()
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

    // ---- Test 7 (T12): Home/My Learning's "Explore Courses" empty-state CTA must genuinely SWITCH to
    // the Explore tab (the real `onTabTapped` save/restore mechanism), never a bare cross-graph
    // `navigate()` — `Destinations.kt`'s own kdoc documents that a destination nested only under one
    // tab's graph is unreachable-by-name from a sibling tab's graph, so this is a real correctness
    // property, not just a style check. A freshly registered account genuinely has zero enrollments
    // (a real, not fail-safe, empty success — contrast the fake-`authState`-only tests above, which
    // never reach a real `getMyLearning()` call at all and would render an ErrorState instead of this
    // empty state), so both screens' real empty-state CTA is reachable. ----
    @Test
    fun homeAndMyLearning_emptyStateExploreCoursesCta_switchesToTheRealExploreTab() {
        registerFreshRealStudent(prefix = "t12-navshelltest")
        composeTestRule.setContent {
            val nc = rememberNavController()
            navController = nc
            MentoraTheme {
                MentoraNavHost(authState = authenticatedStudent, sdk = sdk, themeController = themeController, navController = nc)
            }
        }

        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText("Explore Courses").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Explore Courses").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.Explore>() == true
        }
        // A genuine tab switch (not a redundant push) — Explore's own root content renders and the
        // Explore tab is the one now highlighted.
        composeTestRule.onNodeWithText("Find your next course").assertExists()
        composeTestRule.onNodeWithTag(MobileBottomNavigationTestTag).assertExists()

        // Same CTA, from My Learning this time.
        composeTestRule.onNodeWithTag("bottom_nav_my_learning").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithText("Explore Courses").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Explore Courses").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.Explore>() == true
        }
        composeTestRule.onNodeWithText("Find your next course").assertExists()
    }

    // ---- Test 8 (T12): Home's Certificates entry point (the tapped stat card) is a real, working
    // navigation affordance, verified end to end after a genuine completed demo purchase (real G3-
    // joined data — enrollment + course + progress — actually renders the Continue Learning module and
    // stat row before this test taps into it). ----
    @Test
    fun home_certificatesEntryPoint_navigatesToCertificates_afterARealPurchase() {
        registerFreshRealStudent(prefix = "t12-navshelltest")
        composeTestRule.setContent {
            val nc = rememberNavController()
            navController = nc
            MentoraTheme {
                MentoraNavHost(authState = authenticatedStudent, sdk = sdk, themeController = themeController, navController = nc)
            }
        }

        openFirstCourseFromExplore()
        composeTestRule.onNodeWithText("Enroll").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(DemoCheckoutConfirmButtonTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(DemoCheckoutConfirmButtonTestTag).performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.PurchaseSuccess>() == true
        }
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(PurchaseSuccessBackToMyLearningButtonTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(PurchaseSuccessBackToMyLearningButtonTestTag).performClick()

        // T6 fix-up (Finding 1)'s disclosed, NOT-in-scope-to-eliminate quirk (see test 6's own
        // comment above): the very FIRST tab tap immediately after a full-stack reset (landing on
        // My Learning here IS that reset) can be a dead no-op — tapping twice isolates that known
        // quirk from what this test actually verifies.
        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.onNodeWithTag("bottom_nav_home").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(HomeContinueLearningCardTestTag).fetchSemanticsNodes().isNotEmpty()
        }

        composeTestRule.onNodeWithTag(HomeCertificatesStatCardTestTag).performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.Certificates>() == true
        }
    }

    // ---- Test 9 (Phase 4 final acceptance, Bug 1): Quiz Results' "Continue" reached via the FULL
    // guest-style real chain — purchase → Course Player → Quiz → Quiz Results (not a direct
    // `navController.navigate(Destination.QuizResults(...))` push, which would never reproduce this
    // bug: the whole point is that a REAL completed purchase, via [navigateToPurchaseSuccess]'s own
    // full-stack reset, anchors Course Player/Quiz/Quiz Results under [TabGraph.MyLearningGraph]
    // (`anchorTab` is set to that graph there), so by the time "Continue" fires, `tab` and the
    // CURRENT tab are the SAME [TabGraph.MyLearningGraph] — exactly the shape that used to make
    // `onTabTapped`'s hardcoded `isCurrentTab = false` at that call site route through the
    // switch-tab `navigate(){ popUpTo{saveState=true}; restoreState=true }` branch and silently
    // no-op (see that function's own "Bug 1 fix-up" kdoc for the full, live-confirmed mechanism).
    // Reaches a real "passed" Quiz Results (not "failed") via this seeded course's own real quiz
    // (`SeedData.kt`'s `QUIZ_COURSE`, always [openFirstCourseFromExplore]'s first result — the only
    // seeded course a real quiz exists for): every question's FIRST rendered answer option is always
    // the seeded-correct one (`SeedData.kt`'s own `question(prompt, correct, incorrect, order)`
    // helper always lists the correct option first, and the backend never shuffles), so selecting it
    // 3 times deterministically passes. Before this bug's fix, the final `waitUntil` below would
    // time out (the tap was a genuine, deterministic no-op, not a flaky race) and this test would
    // fail. ----
    // This test's own `waitUntil` calls previously used a non-standard 30s budget (every other test
    // in this class uses the file-wide 15s convention) — originally attributed to a disclosed,
    // NOT-in-scope-to-eliminate AVD-level `ActivityManager` cached-process freeze (same category as
    // test 6/8's own disclosed "first tap after reset" quirk above). That diagnosis turned out to be
    // wrong for THIS test: a Phase 4 final-acceptance review traced the actual hang to a real race in
    // the lesson-completion loop below (`navController.currentBackStackEntry` read directly, with no
    // `Espresso.onIdle()` barrier, right after `performClick()` — see that loop's own comment) —
    // confirmed via logcat that `com.mentora.android`/`com.mentora.android.test` were NEVER in the
    // freezer's process list during a failing run, ruling out the freeze theory for this specific
    // failure. With the race fixed, this test now uses the file's normal 15s budget throughout,
    // verified reliable across 8 consecutive real-emulator runs (3 at the old 30s budget, 5 at 15s)
    // during that review. The AVD-freezer flake pattern itself remains real and disclosed elsewhere
    // (README's "Known, disclosed flake pattern" note) for the screenshot/pixel-capture tests it
    // actually affects — if this test starts flaking again, check that pattern before assuming a
    // regression, and re-run it alone
    // (`-Pandroid.testInstrumentationRunnerArguments.class=...`) to confirm.

    @Test
    fun quizResults_continueReachedViaFullPurchaseChain_navigatesToMyLearning() {
        registerFreshRealStudent(prefix = "t20-navshelltest")
        composeTestRule.setContent {
            val nc = rememberNavController()
            navController = nc
            MentoraTheme {
                MentoraNavHost(authState = authenticatedStudent, sdk = sdk, themeController = themeController, navController = nc)
            }
        }

        openFirstCourseFromExplore()
        composeTestRule.onNodeWithText("Enroll").performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(DemoCheckoutConfirmButtonTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(DemoCheckoutConfirmButtonTestTag).performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.PurchaseSuccess>() == true
        }
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(PurchaseSuccessStartLearningButtonTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(PurchaseSuccessStartLearningButtonTestTag).performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.CoursePlayer>() == true
        }

        // Mark every lesson complete — the SAME footer test tag throughout (`Mark Complete` auto-
        // advances after each real progress-update call, then the same button reads `Take Quiz` on
        // the last lesson, see `CoursePlayerScreen.kt`'s own `CoursePlayerFooter`) — until the real
        // Quiz destination is reached. `assertIsEnabled` before each tap waits out the real network
        // round trip each `Mark Complete` tap triggers (`isCompletionInFlight` disables the button
        // mid-flight), rather than racing it.
        // Race fix: the `while` condition below reads `navController.currentBackStackEntry` directly
        // on the instrumentation thread with no idle barrier, unlike every other navController read in
        // this file (wrapped in `composeTestRule.waitUntil`, which gets an `Espresso.onIdle()` between
        // polls). `performClick()` returns before Compose's click coroutine actually fires `onClick`,
        // so after the FINAL "Take Quiz" tap the loop's condition can still read `CoursePlayer`,
        // entering one extra iteration that then hangs waiting on `CoursePlayerPrimaryActionTestTag`,
        // which no longer exists once Quiz is current (confirmed via logcat: not the AVD-freezer
        // flakiness disclosed above this test's own `@Test` — this app's own process is never in the
        // freeze list on a failing run). The `waitUntil` inside the loop body now waits for EITHER the
        // real navigation to Quiz OR the button re-enabling (the normal between-lessons case) —
        // genuinely idle-checked, so if an extra iteration is entered right after the final tap, this
        // wait correctly observes Quiz having already arrived and the `if` below breaks out instead of
        // clicking a button that no longer exists.
        var lessonTaps = 0
        while (navController.currentBackStackEntry?.destination?.hasRoute<Destination.Quiz>() != true) {
            check(++lessonTaps <= 20) { "Course Player never reached the Quiz after $lessonTaps primary-action taps" }
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                navController.currentBackStackEntry?.destination?.hasRoute<Destination.Quiz>() == true ||
                    runCatching { composeTestRule.onNodeWithTag(CoursePlayerPrimaryActionTestTag).assertIsEnabled() }.isSuccess
            }
            if (navController.currentBackStackEntry?.destination?.hasRoute<Destination.Quiz>() == true) break
            composeTestRule.onNodeWithTag(CoursePlayerPrimaryActionTestTag).performClick()
        }

        // Answer all 3 questions with the first rendered option (always the seeded-correct one, see
        // this test's own kdoc) and advance/submit via the same primary-action tag throughout.
        val firstAnswerOptionMatcher = SemanticsMatcher("first quiz answer option") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("quiz-answer-option-") == true
        }
        repeat(3) {
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                composeTestRule.onAllNodes(firstAnswerOptionMatcher).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onAllNodes(firstAnswerOptionMatcher)[0].performClick()
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                runCatching { composeTestRule.onNodeWithTag(QuizPrimaryActionTestTag).assertIsEnabled() }.isSuccess
            }
            composeTestRule.onNodeWithTag(QuizPrimaryActionTestTag).performClick()
        }
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.QuizResults>() == true
        }

        // The actual regression: before the fix, this exact tap was a deterministic no-op (confirmed
        // live via `adb logcat` around `onTabTapped` — see that function's own kdoc) — the
        // `waitUntil` below would time out and fail this test, not flake.
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            composeTestRule.onAllNodesWithTag(QuizResultsPrimaryActionTestTag).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag(QuizResultsPrimaryActionTestTag).performClick()
        composeTestRule.waitUntil(timeoutMillis = 15_000) {
            navController.currentBackStackEntry?.destination?.hasRoute<Destination.MyLearning>() == true
        }
        composeTestRule.onNodeWithTag(MyLearningScreenTestTag).assertExists()
    }
}
