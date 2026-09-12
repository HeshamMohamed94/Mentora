package com.mentora.shared.di

import com.mentora.shared.config.ApiEnvironment
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.koinApplication

/**
 * The single Koin entry point for the whole `shared` module (`execution/PHASE_3_KMP_PLAN.md`
 * Task 15 AC). Every repository/use case/[com.mentora.shared.auth.SessionManager]/
 * [com.mentora.shared.auth.TokenStorage]/[com.mentora.shared.settings.PreferenceStore] declared
 * across this module and [platformModule] is resolvable from the returned [KoinApplication]'s
 * `.koin`.
 *
 * [platformModule] supplies exactly what `shared` itself cannot: the platform's [TokenStorage]/
 * [PreferenceStore] `actual` implementation and the underlying Ktor `HttpClientEngine` (OkHttp on
 * Android, Darwin on iOS, `MockEngine` in tests) — see `AndroidPreferenceStore`/`AndroidTokenStorage`'s
 * kdoc, which already names this exact function as where they get constructed. Android supplies
 * this via its own `androidMain`-only `platformModule(context: Context)` factory (needs a
 * `Context`); iOS supplies an analogous no-arg `platformModule()`. Neither is built here — Phase
 * 4/5 own the app-level `Context`/lifecycle that constructs them; `shared` only defines the shape
 * every platform module must satisfy (bindings for `TokenStorage`, `PreferenceStore`, and
 * `HttpClientEngine`).
 *
 * Deliberately built via [koinApplication] (a fresh, non-global [KoinApplication] instance) rather
 * than the global [org.koin.core.context.startKoin] — a library module should not silently claim
 * process-wide global DI state on the caller's behalf (an Android `Application`/iOS app entry point
 * may have its own reasons to manage that itself, or this may be constructed more than once in a
 * test), and a non-global instance can be built repeatedly with zero risk of Koin's
 * "already started" error. [com.mentora.shared.MentoraSdk.create] is the intended way most callers
 * actually consume this — see that class's kdoc — but `initKoin` itself remains public since it is
 * the one true wiring entry point the plan requires.
 */
fun initKoin(
    environment: ApiEnvironment,
    platformModule: Module,
    enableNetworkLogging: Boolean = false,
): KoinApplication = koinApplication {
    modules(
        platformModule,
        networkModule(environment, enableNetworkLogging),
        authModule,
        userModule,
        catalogModule,
        enrollmentModule,
        progressModule,
        quizModule,
        certificateModule,
        learningPathModule,
        mediaModule,
        aiTutorModule,
    )
}
