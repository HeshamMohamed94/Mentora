package com.mentora.shared.di

import com.mentora.shared.MentoraSdk
import com.mentora.shared.auth.FakeTokenStorage
import com.mentora.shared.auth.SessionManager
import com.mentora.shared.auth.TokenStorage
import com.mentora.shared.config.ApiEnvironment
import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.domain.usecase.aitutor.GetAiConversationUseCase
import com.mentora.shared.domain.usecase.aitutor.SendAiTutorMessageUseCase
import com.mentora.shared.domain.usecase.auth.LoginUseCase
import com.mentora.shared.domain.usecase.auth.LogoutUseCase
import com.mentora.shared.domain.usecase.auth.ObserveAuthStateUseCase
import com.mentora.shared.domain.usecase.auth.RefreshSessionUseCase
import com.mentora.shared.domain.usecase.auth.RegisterUseCase
import com.mentora.shared.domain.usecase.auth.RestoreSessionUseCase
import com.mentora.shared.domain.usecase.catalog.GetCourseCurriculumUseCase
import com.mentora.shared.domain.usecase.catalog.GetCourseDetailsUseCase
import com.mentora.shared.domain.usecase.catalog.ListCategoriesUseCase
import com.mentora.shared.domain.usecase.catalog.SearchCoursesUseCase
import com.mentora.shared.domain.usecase.certificate.GetCertificateUseCase
import com.mentora.shared.domain.usecase.certificate.ListCertificatesUseCase
import com.mentora.shared.domain.usecase.enrollment.CompleteDemoCheckoutUseCase
import com.mentora.shared.domain.usecase.enrollment.GetCheckoutPreviewUseCase
import com.mentora.shared.domain.usecase.enrollment.GetMyLearningUseCase
import com.mentora.shared.domain.usecase.enrollment.ListEnrollmentsUseCase
import com.mentora.shared.domain.usecase.learningpath.FollowLearningPathUseCase
import com.mentora.shared.domain.usecase.learningpath.GetLearningPathDetailUseCase
import com.mentora.shared.domain.usecase.learningpath.ListLearningPathsUseCase
import com.mentora.shared.domain.usecase.learningpath.UnfollowLearningPathUseCase
import com.mentora.shared.domain.usecase.media.GetLessonPlaybackSourceUseCase
import com.mentora.shared.domain.usecase.media.RefreshPlaybackUrlUseCase
import com.mentora.shared.domain.usecase.media.ResolveThumbnailUrlUseCase
import com.mentora.shared.domain.usecase.progress.CompleteLessonUseCase
import com.mentora.shared.domain.usecase.progress.GetCourseProgressUseCase
import com.mentora.shared.domain.usecase.progress.ReportPlaybackPositionUseCase
import com.mentora.shared.domain.usecase.progress.ResumeCourseUseCase
import com.mentora.shared.domain.usecase.quiz.GetLatestAttemptUseCase
import com.mentora.shared.domain.usecase.quiz.GetQuizUseCase
import com.mentora.shared.domain.usecase.quiz.SubmitQuizUseCase
import com.mentora.shared.domain.usecase.user.GetProfileUseCase
import com.mentora.shared.domain.usecase.user.ObserveLocaleUseCase
import com.mentora.shared.domain.usecase.user.SetLocaleUseCase
import com.mentora.shared.domain.usecase.user.SetThemeUseCase
import com.mentora.shared.domain.usecase.user.UpdateProfileUseCase
import com.mentora.shared.settings.FakePreferenceStore
import com.mentora.shared.settings.PreferenceStore
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

private val testEnvironment = ApiEnvironment.custom("http://localhost")

/**
 * A minimal, valid `platformModule` — the exact 3 bindings [initKoin]'s kdoc says any real
 * platform module must supply ([TokenStorage], [PreferenceStore], [HttpClientEngine]) — standing
 * in for `androidMain`'s `platformModule(context)`/`iosMain`'s `platformModule()` in this
 * commonTest-only resolution check.
 */
private fun testPlatformModule(): Module = module {
    single<TokenStorage> { FakeTokenStorage() }
    single<PreferenceStore> { FakePreferenceStore() }
    single<HttpClientEngine> { MockEngine { respondOk() } }
}

/**
 * Task 15 AC: "a Koin module-resolution check" — proves every repository/use case declared across
 * the `di` package's modules actually resolves from a real [initKoin] graph with no missing-
 * dependency/circular-dependency error, and that the plan's `single`-vs-`factory` conventions are wired as intended
 * ([SessionManager] shared, every use case a fresh instance per resolution).
 */
class InitKoinTest {

    @Test
    fun `every registered repository and use case resolves from a real initKoin graph`() {
        val koin = initKoin(testEnvironment, testPlatformModule()).koin

        // Network layer (di/NetworkModule.kt)
        koin.get<ApiClient>()
        koin.get<SessionManager>()

        // Auth (Task 5)
        koin.get<RegisterUseCase>()
        koin.get<LoginUseCase>()
        koin.get<LogoutUseCase>()
        koin.get<ObserveAuthStateUseCase>()
        koin.get<RefreshSessionUseCase>()
        koin.get<RestoreSessionUseCase>()

        // User (Task 6)
        koin.get<GetProfileUseCase>()
        koin.get<UpdateProfileUseCase>()
        koin.get<ObserveLocaleUseCase>()
        koin.get<SetLocaleUseCase>()
        koin.get<SetThemeUseCase>()

        // Catalog (Task 7)
        koin.get<ListCategoriesUseCase>()
        koin.get<SearchCoursesUseCase>()
        koin.get<GetCourseDetailsUseCase>()
        koin.get<GetCourseCurriculumUseCase>()

        // Enrollment (Task 8)
        koin.get<GetCheckoutPreviewUseCase>()
        koin.get<CompleteDemoCheckoutUseCase>()
        koin.get<ListEnrollmentsUseCase>()
        koin.get<GetMyLearningUseCase>()

        // Progress (Task 9)
        koin.get<GetCourseProgressUseCase>()
        koin.get<CompleteLessonUseCase>()
        koin.get<ResumeCourseUseCase>()
        koin.get<ReportPlaybackPositionUseCase>()

        // Quiz (Task 10)
        koin.get<GetQuizUseCase>()
        koin.get<SubmitQuizUseCase>()
        koin.get<GetLatestAttemptUseCase>()

        // Certificates (Task 11)
        koin.get<ListCertificatesUseCase>()
        koin.get<GetCertificateUseCase>()

        // Learning Paths (Task 12)
        koin.get<ListLearningPathsUseCase>()
        koin.get<GetLearningPathDetailUseCase>()
        koin.get<FollowLearningPathUseCase>()
        koin.get<UnfollowLearningPathUseCase>()

        // Media (Task 13)
        koin.get<GetLessonPlaybackSourceUseCase>()
        koin.get<RefreshPlaybackUrlUseCase>()
        koin.get<ResolveThumbnailUrlUseCase>()

        // AI Tutor (Task 14)
        koin.get<GetAiConversationUseCase>()
        koin.get<SendAiTutorMessageUseCase>()
    }

    @Test
    fun `SessionManager is a shared single never a fresh instance per resolution`() {
        val koin = initKoin(testEnvironment, testPlatformModule()).koin
        assertSame(koin.get<SessionManager>(), koin.get<SessionManager>())
    }

    @Test
    fun `use cases are factories with a fresh instance per resolution`() {
        val koin = initKoin(testEnvironment, testPlatformModule()).koin
        assertNotSame(koin.get<LoginUseCase>(), koin.get<LoginUseCase>())
    }

    @Test
    fun `MentoraSdk resolves every facade with no exception`() {
        val sdk = MentoraSdk.create(testEnvironment, testPlatformModule())

        sdk.auth
        sdk.user
        sdk.catalog
        sdk.enrollment
        sdk.progress
        sdk.quiz
        sdk.certificates
        sdk.learningPaths
        sdk.media
        sdk.aiTutor
    }
}
