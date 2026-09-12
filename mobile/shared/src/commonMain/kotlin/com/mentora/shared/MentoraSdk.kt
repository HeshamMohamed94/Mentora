package com.mentora.shared

import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.di.initKoin
import com.mentora.shared.facade.AiTutorFacade
import com.mentora.shared.facade.AuthFacade
import com.mentora.shared.facade.CatalogFacade
import com.mentora.shared.facade.CertificateFacade
import com.mentora.shared.facade.EnrollmentFacade
import com.mentora.shared.facade.LearningPathFacade
import com.mentora.shared.facade.MediaFacade
import com.mentora.shared.facade.ProgressFacade
import com.mentora.shared.facade.QuizFacade
import com.mentora.shared.facade.UserFacade
import org.koin.core.Koin
import org.koin.core.module.Module

/**
 * The ONLY surface Phase 4 (Android)/Phase 5 (iOS) are meant to consume, per
 * `execution/PHASE_3_KMP_PLAN.md` Task 15 AC: "a `MentoraSdk` façade ... is the only surface Phase
 * 4/5 consume — never a repository impl or `ApiClient` directly." Every property below is a
 * per-domain façade ([AuthFacade], [CatalogFacade], ...) that exposes ONLY the already-constructed
 * Task 5-14 use cases as callable properties (`sdk.auth.login(...)`, `sdk.catalog.searchCourses(...)`)
 * — never a repository, `ApiClient`, `HttpClient`, or Koin type. Each use case's own `operator fun
 * invoke(...)` signature already leans on sealed types/enums throughout (`ApiResult`, `AuthState`,
 * `AiStreamResult`, `AiQuickAction`, `LessonProgressTarget`, `QuizLookupResult`, ...) — deliberately
 * so, per Task 15 AC's "sealed types/enums used deliberately at the façade for clean Swift
 * interop": SKIE (host-guarded — see `shared/build.gradle.kts`) turns every one of those into an
 * idiomatic Swift `enum`, which is the whole reason this SDK never needed to invent a parallel
 * "façade-only" result-type hierarchy of its own.
 *
 * Every property is resolved lazily and exactly once per [MentoraSdk] instance, so stateful use
 * cases (e.g. [ProgressFacade.reportPlaybackPosition]'s throttle) behave correctly as long as a
 * caller holds onto (and reuses) the same [MentoraSdk]/facade instance for the app's lifetime,
 * rather than constructing a fresh one per call.
 */
class MentoraSdk internal constructor(koin: Koin) {
    val auth: AuthFacade by lazy { AuthFacade(koin) }
    val user: UserFacade by lazy { UserFacade(koin) }
    val catalog: CatalogFacade by lazy { CatalogFacade(koin) }
    val enrollment: EnrollmentFacade by lazy { EnrollmentFacade(koin) }
    val progress: ProgressFacade by lazy { ProgressFacade(koin) }
    val quiz: QuizFacade by lazy { QuizFacade(koin) }
    val certificates: CertificateFacade by lazy { CertificateFacade(koin) }
    val learningPaths: LearningPathFacade by lazy { LearningPathFacade(koin) }
    val media: MediaFacade by lazy { MediaFacade(koin) }
    val aiTutor: AiTutorFacade by lazy { AiTutorFacade(koin) }

    companion object {
        /**
         * Builds the whole Koin DI graph ([initKoin]) and wraps it in a [MentoraSdk]. This is the
         * intended single call site for Phase 4/5 app startup — see [platformModule]'s kdoc
         * (`androidMain`/`iosMain`) for what each platform must pass as [platformModule] here.
         */
        fun create(
            environment: ApiEnvironment,
            platformModule: Module,
            enableNetworkLogging: Boolean = false,
        ): MentoraSdk = MentoraSdk(initKoin(environment, platformModule, enableNetworkLogging).koin)
    }
}
