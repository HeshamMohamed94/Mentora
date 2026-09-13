package com.mentora.android

import android.app.Application
import android.util.Log
import com.mentora.android.locale.LocaleController
import com.mentora.android.theme.ThemeController
import com.mentora.shared.MentoraSdk
import com.mentora.shared.auth.AndroidTokenStorage
import com.mentora.shared.auth.TokenStorage
import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.defaultHttpClientEngine
import com.mentora.shared.settings.AndroidPreferenceStore
import com.mentora.shared.settings.PreferenceStore
import io.ktor.client.engine.HttpClientEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.dsl.module

/**
 * App process entry point. Owns the ONE [MentoraSdk] instance for this process's lifetime (T4 AC —
 * `execution/PHASE_4_ANDROID_PLAN.md` § 1: "Hold exactly one instance for the app's process
 * lifetime").
 *
 * **G1 resolution (theme read path)**: [UserFacade][com.mentora.shared.facade.UserFacade.setTheme]
 * is write-only — `shared` exposes no `observeTheme`/`getTheme` through the façade. Rather than
 * letting `platformModule(applicationContext)` construct its own, app-inaccessible
 * [AndroidPreferenceStore] internally, this class constructs exactly ONE [AndroidPreferenceStore]
 * itself ([androidPreferenceStore], below), retains it as a field, and hands that SAME instance to
 * Koin as the [PreferenceStore] binding. The app (see `theme.ThemeController`) then reads
 * `androidPreferenceStore.getTheme()` directly — a plain synchronous getter — while `shared`
 * internally uses the identical instance for [PreferenceStore.setTheme]/locale. There is never a
 * second [AndroidPreferenceStore] instance anywhere in this process.
 *
 * **F3 fix (bootstrap ordering)**: this class also owns the ONE, ordered, application-scoped
 * bootstrap sequence — `sdk.auth.restoreSession()` exactly once, followed by
 * `localeController.seedInitialLocaleIfNeeded()` — run from [applicationScope] in [onCreate].
 * Neither `AppSessionViewModel` nor `MainActivity` calls `restoreSession()` or the locale
 * controller directly any more; doing so from Activity/Compose-scoped code allowed two races: two
 * `AppSessionViewModel` instances both calling `restoreSession()` concurrently, and locale seeding
 * running (and reading auth state) before restore had reached a terminal state. Running both,
 * strictly in order, from a single application-scoped coroutine closes both races categorically —
 * there is exactly one caller of `restoreSession()` in the whole process, and locale seeding never
 * overlaps with an in-flight restore.
 */
class MentoraApplication : Application() {

    /** Application-scoped coroutine scope for the one-time bootstrap sequence below. Not tied to
     * any Activity/Compose lifecycle, so it survives Activity re-creation and never re-runs. */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The one [AndroidPreferenceStore] instance for this process — see this class's kdoc (G1). */
    lateinit var androidPreferenceStore: AndroidPreferenceStore
        private set

    /** The one [MentoraSdk] instance for this process's lifetime. */
    lateinit var sdk: MentoraSdk
        private set

    /** The one [LocaleController] instance for this process's lifetime — only ever invoked from
     * [applicationScope]'s bootstrap sequence in [onCreate] (see this class's kdoc, F3 fix). */
    lateinit var localeController: LocaleController
        private set

    /** The one [ThemeController] instance for this process's lifetime (F3 fix — previously
     * `remember`-constructed inside `MainActivity`'s composable, which meant a second composable
     * constructing its own `ThemeController` would hold an independent cached `StateFlow` that
     * never observed writes made through a different instance). `MainActivity` obtains this SAME
     * instance instead of constructing its own. */
    lateinit var themeController: ThemeController
        private set

    override fun onCreate() {
        super.onCreate()

        androidPreferenceStore = AndroidPreferenceStore(applicationContext)

        // Same three bindings androidMain's platformModule(context) provides, except the
        // PreferenceStore binding is wired to the retained androidPreferenceStore instance above
        // instead of letting a second instance get constructed inside the module closure.
        val appPlatformModule = module {
            single<TokenStorage> { AndroidTokenStorage(applicationContext) }
            single<PreferenceStore> { androidPreferenceStore }
            single<HttpClientEngine> { defaultHttpClientEngine() }
        }

        val environment = if (BuildConfig.DEBUG) {
            ApiEnvironment.androidEmulator()
        } else {
            // No production backend exists anywhere in this project (Phases 1-3 never deployed one
            // either). This is a disclosed, deliberate limitation — NOT a bug to silently fix by
            // inventing a fake HTTPS endpoint. A real release build needs a real
            // ApiEnvironment.custom(url) here once a production backend exists. Using the emulator
            // URL as a placeholder is intentional and will simply fail cleanly (blocked cleartext,
            // no such host) rather than silently misbehaving.
            ApiEnvironment.androidEmulator()
        }

        sdk = MentoraSdk.create(
            environment = environment,
            platformModule = appPlatformModule,
            enableNetworkLogging = BuildConfig.DEBUG,
        )

        themeController = ThemeController(androidPreferenceStore, sdk)
        localeController = LocaleController(applicationContext, sdk)

        // F3 fix: the ONE ordered bootstrap sequence for this process — restore the session fully
        // before seeding the initial locale, so seeding never races an in-flight restore (see this
        // class's kdoc).
        applicationScope.launch {
            sdk.auth.restoreSession()
            localeController.seedInitialLocaleIfNeeded()
            Log.d("MentoraApplication", "Bootstrap sequence (restoreSession -> seedInitialLocaleIfNeeded) completed")
        }
    }
}
