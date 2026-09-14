package com.mentora.android.playback

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mentora.shared.MentoraSdk
import com.mentora.shared.auth.AndroidTokenStorage
import com.mentora.shared.auth.TokenStorage
import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.defaultHttpClientEngine
import com.mentora.shared.playback.PlaybackState
import com.mentora.shared.settings.AndroidPreferenceStore
import com.mentora.shared.settings.PreferenceStore
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.dsl.module
import kotlin.time.Duration

/**
 * Task 13 C1's real-device, real-backend proof — `execution/PHASE_4_ANDROID_PLAN.md § 7` /
 * `execution/DECISIONS_LOG.md` D85 both require the ExoPlayer binding to be proven in isolation,
 * against the LIVE local backend (never a fake/mocked source), BEFORE any screen is built on top of
 * it. Follows [com.mentora.android.AndroidTokenStorageInstrumentedTest]'s own convention for a
 * real-device, non-Compose `connectedDebugAndroidTest`: no `composeTestRule`, plain `runBlocking` +
 * real, throwaway network calls.
 *
 * **What this actually proves — and what it deliberately does not.** This constructs one real
 * [MediaPlaybackController], hands it a REAL [com.mentora.shared.domain.model.PlaybackSource] for a
 * real seeded lesson video (obtained via a fresh throwaway student account + a real
 * `GET /api/v1/courses`/`.../curriculum` walk to find the first seeded lesson with a real
 * `videoMediaId`, enrolling into whichever course owns it first — the backend's playback-url route
 * requires either course ownership or enrollment), calls [MediaPlaybackController.play], and asserts
 * the controller's own [com.mentora.shared.playback.PlaybackState.Playing] is genuinely reached —
 * not merely "no exception was thrown". D85's own verification-plan note is why this test does NOT
 * attempt to exercise [PlaybackUrlResolver]'s mid-session refresh path: the seeded lesson videos are
 * 19.5-25.5 s long (`tools/seed-media/generate-lesson-videos.js:166-170`), fully buffered in a single
 * request within milliseconds — natural playback of one of them can never reach the 5-minute TTL
 * boundary. That path is proven separately by [PlaybackUrlResolverTest]'s three branch tests; this
 * test's own job is proving the real ExoPlayer/`ResolvingDataSource`/audio-attributes/listener wiring
 * actually plays a real file end to end.
 */
@RunWith(AndroidJUnit4::class)
class MediaPlaybackControllerInstrumentedTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Same `MentoraSdk.create(...)` construction as `NavigationShellTest`'s own `sharedSdk` — see
     *  that class's kdoc for why this is a real instance, never a fake (the façade constructors are
     *  `internal`). A fresh instance per test class run is fine here (unlike `NavigationShellTest`'s
     *  multi-test-sharing concern) since this class has exactly one test. */
    private val sdk: MentoraSdk by lazy {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        val platformModule = module {
            single<TokenStorage> { AndroidTokenStorage(appContext) }
            single<PreferenceStore> { AndroidPreferenceStore(appContext) }
            single<HttpClientEngine> { defaultHttpClientEngine() }
        }
        MentoraSdk.create(
            environment = ApiEnvironment.androidEmulator(),
            platformModule = platformModule,
            enableNetworkLogging = false,
        )
    }

    /** Walks the real, live-backend catalog (paginating `GET /api/v1/courses` fully, same
     *  never-just-the-first-page discipline as `CourseDetailsViewModel.isEnrolledIn`'s G4) for the
     *  first seeded lesson with a real, non-null `videoMediaId`, then completes a real demo-checkout
     *  enrollment into that lesson's course (`sdk`'s freshly-registered account has zero enrollments,
     *  and the backend's playback-url route 403s a non-owner who isn't enrolled). */
    private suspend fun findRealVideoMediaIdAndEnroll(): String {
        var cursor: String? = null
        while (true) {
            val page = when (val result = sdk.catalog.searchCourses(cursor = cursor)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> error("real searchCourses failed in test setup: $result")
            }
            for (course in page.items) {
                val curriculum = sdk.catalog.getCourseCurriculum(course.id)
                val sections = (curriculum as? ApiResult.Success)?.data ?: continue
                val videoMediaId = sections.flatMap { it.lessons }
                    .firstOrNull { it.videoMediaId != null }
                    ?.videoMediaId
                    ?: continue
                val completion = sdk.enrollment.completeDemoCheckout(course.id)
                check(completion is ApiResult.Success) { "real completeDemoCheckout failed in test setup: $completion" }
                return videoMediaId
            }
            cursor = page.nextCursor ?: error(
                "no seeded lesson with a real videoMediaId found across the full live-backend catalog",
            )
        }
    }

    @Test
    fun realExoPlayer_reachesPlayingState_onARealSeededLessonVideo() {
        lateinit var controller: MediaPlaybackController

        runBlocking {
            runCatching { sdk.auth.logout() }
            val email = "t13-playbacktest-${System.currentTimeMillis()}@example.com"
            val register = sdk.auth.register(email = email, password = "MentoraTest1", name = "Playback Test Student")
            check(register is ApiResult.Success) { "real register failed in test setup: $register" }

            val mediaId = findRealVideoMediaIdAndEnroll()

            val source = when (val result = sdk.media.getLessonPlaybackSource(mediaId)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> error("real getLessonPlaybackSource failed in test setup: $result")
            }

            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                controller = MediaPlaybackController(context) { id, current -> sdk.media.refreshPlaybackUrl(id, current) }
                controller.prepareLesson(mediaId, source, Duration.ZERO)
                controller.play()
            }
        }

        // Reviewer finding (Task 13 C1 review): waiting only on `Playing` means a real
        // `PlaybackState.Error` (e.g. a genuine backend/auth failure) is indistinguishable from an
        // unrelated hang — both just time out after 20s with no diagnosis. Racing `Error` in too
        // turns that into an immediate, actionable failure instead of a 20s mystery.
        val finalState = runBlocking {
            runCatching {
                withTimeout(20_000) {
                    controller.state.first { it is PlaybackState.Playing || it is PlaybackState.Error }
                }
            }
        }

        InstrumentationRegistry.getInstrumentation().runOnMainSync { controller.release() }

        assertTrue(
            "expected a real ExoPlayer to reach PlaybackState.Playing on a real seeded lesson video " +
                "within 20s, outcome was: $finalState",
            finalState.getOrNull() is PlaybackState.Playing,
        )
    }
}
