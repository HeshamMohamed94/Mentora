package com.mentora.android

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * T11 root-cause fix (independent Codex second-opinion finding, verified). Instrumentation attaches
 * to the TARGET app's own process (`android:targetPackage="com.mentora.android"`, see the generated
 * `androidTest` manifest) — the `androidTest` source set's own `AndroidManifest.xml`/package is a
 * separate, largely-vestigial APK whose `<application>` tag has NO effect on that target process
 * (confirmed against the merged manifest: overriding it there changed nothing — the real app process
 * still ran `.MentoraApplication`, and `NavigationShellTest`'s tests 5/6 failed identically). The
 * actual, documented mechanism to substitute the target app's Application class during instrumented
 * tests is a custom [AndroidJUnitRunner] overriding [newApplication] — set as this module's
 * `testInstrumentationRunner` below.
 *
 * **Why this matters**: without it, `MentoraApplication.onCreate()` (`MentoraApplication.kt`) still
 * constructs its OWN `MentoraSdk`/`SessionManager` for every instrumented test run (via
 * `sdk.auth.restoreSession()` in an application-scoped coroutine), IN ADDITION to whatever SDK a
 * test builds for itself. Both bind `AndroidTokenStorage` against the SAME process-wide
 * `preferencesDataStore(name = "mentora_secure_tokens")` file, so the app-scoped bootstrap's own
 * session-restore/refresh activity can read or write that shared file concurrently with a test's own
 * login/register/logout calls — this was the actual mechanism behind `NavigationShellTest`'s tests
 * 5/6 intermittently observing a different account's session than the one they had just registered,
 * which persisted even after giving each *test* exactly one shared `MentoraSdk`
 * (`NavigationShellTest.sharedSdk`'s own kdoc) — that fix never addressed this separate,
 * always-present application-scoped instance.
 *
 * No instrumented test in this module launches `MainActivity` or otherwise depends on
 * `MentoraApplication`'s real bootstrap sequence (locale seeding, theme controller, session
 * restore) — every test drives its own composable tree directly via
 * `createAndroidComposeRule<ComponentActivity>().setContent {}`. Substituting the plain base
 * [Application] for the whole instrumentation process is therefore safe module-wide.
 */
class NoOpApplicationTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, Application::class.java.name, context)
}
