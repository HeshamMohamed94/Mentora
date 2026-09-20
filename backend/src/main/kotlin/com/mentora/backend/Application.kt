package com.mentora.backend

import com.mentora.backend.admin.adminModule
import com.mentora.backend.admin.routes.adminRoutes
import com.mentora.backend.admin.service.AdminService
import com.mentora.backend.aitutor.aiTutorModule
import com.mentora.backend.aitutor.provider.AiProvider
import com.mentora.backend.aitutor.repository.ensureAiTutorIndexes
import com.mentora.backend.aitutor.routes.aiTutorRoutes
import com.mentora.backend.aitutor.service.AiTutorService
import com.mentora.backend.auth.authModule
import com.mentora.backend.auth.repository.ensureAuthIndexes
import com.mentora.backend.auth.routes.authRoutes
import com.mentora.backend.auth.service.AuthService
import com.mentora.backend.categories.categoriesModule
import com.mentora.backend.categories.repository.ensureCategoriesIndexes
import com.mentora.backend.categories.routes.categoryRoutes
import com.mentora.backend.categories.service.CategoryService
import com.mentora.backend.certificates.certificatesModule
import com.mentora.backend.certificates.repository.ensureCertificateIndexes
import com.mentora.backend.certificates.routes.certificateRoutes
import com.mentora.backend.certificates.service.CertificateService
import com.mentora.backend.config.AppConfig
import com.mentora.backend.courses.coursesModule
import com.mentora.backend.courses.repository.ensureCoursesIndexes
import com.mentora.backend.courses.routes.courseRoutes
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.enrollment.enrollmentModule
import com.mentora.backend.enrollment.repository.ensureEnrollmentIndexes
import com.mentora.backend.enrollment.routes.enrollmentRoutes
import com.mentora.backend.enrollment.service.EnrollmentService
import com.mentora.backend.instructor.instructorModule
import com.mentora.backend.instructor.routes.instructorRoutes
import com.mentora.backend.instructor.service.InstructorService
import com.mentora.backend.learningpaths.learningPathsModule
import com.mentora.backend.learningpaths.repository.ensureLearningPathIndexes
import com.mentora.backend.learningpaths.routes.learningPathRoutes
import com.mentora.backend.learningpaths.service.LearningPathService
import com.mentora.backend.media.mediaModule
import com.mentora.backend.media.repository.ensureMediaIndexes
import com.mentora.backend.media.routes.mediaRoutes
import com.mentora.backend.media.service.MediaService
import com.mentora.backend.plugins.configureCors
import com.mentora.backend.plugins.configureDatabaseLifecycle
import com.mentora.backend.plugins.configureMonitoring
import com.mentora.backend.plugins.configureRateLimiting
import com.mentora.backend.plugins.configureRequestValidation
import com.mentora.backend.plugins.configureSecurity
import com.mentora.backend.plugins.configureSerialization
import com.mentora.backend.plugins.configureStatusPages
import com.mentora.backend.plugins.databaseKoinModule
import com.mentora.backend.plugins.healthRoutes
import com.mentora.backend.plugins.configKoinModule
import com.mentora.backend.progress.progressModule
import com.mentora.backend.progress.repository.ensureProgressIndexes
import com.mentora.backend.progress.routes.progressRoutes
import com.mentora.backend.progress.service.ProgressService
import com.mentora.backend.quiz.quizModule
import com.mentora.backend.quiz.repository.ensureQuizIndexes
import com.mentora.backend.quiz.routes.quizRoutes
import com.mentora.backend.quiz.service.QuizService
import com.mentora.backend.users.routes.userRoutes
import com.mentora.backend.users.service.UserService
import com.mentora.backend.users.usersModule
import com.mongodb.MongoTimeoutException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.plugins.partialcontent.PartialContent
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() = module(AppConfig.load())

internal fun Application.module(appConfig: AppConfig, aiProviderOverride: AiProvider? = null) {
    log.info("Starting Mentora backend with {}", appConfig.redactedSummary())
    log.info(
        "AI Tutor provider mode: {}",
        when (appConfig.aiProviderMode()) {
            "anthropic" -> "ANTHROPIC (model=${appConfig.aiProviderModel}, " +
                "maxResponseTokens=${appConfig.aiProviderMaxResponseTokens})"
            else -> "STUB — AI_PROVIDER_API_KEY is not set, so AI Tutor replies are placeholder text. " +
                "Set AI_PROVIDER_API_KEY in backend/.env for real responses (architecture/DEPLOYMENT.md § 4)."
        },
    )

    install(Koin) {
        slf4jLogger()
        modules(
            configKoinModule(appConfig), databaseKoinModule, authModule, usersModule, categoriesModule,
            coursesModule, enrollmentModule, progressModule, quizModule, certificatesModule, learningPathsModule,
            mediaModule, instructorModule, adminModule, aiTutorModule(aiProviderOverride),
        )
    }
    configureDatabaseLifecycle()

    configureMonitoring()
    configureSerialization()
    configureCors(appConfig)
    configureSecurity(appConfig)
    configureRequestValidation()
    configureRateLimiting(appConfig)
    configureStatusPages()
    install(PartialContent)

    kotlinx.coroutines.runBlocking {
        try {
            ensureAuthIndexes(get())
            ensureCategoriesIndexes(get())
            ensureCoursesIndexes(get())
            ensureEnrollmentIndexes(get())
            ensureProgressIndexes(get())
            ensureQuizIndexes(get())
            ensureCertificateIndexes(get())
            ensureLearningPathIndexes(get())
            ensureMediaIndexes(get())
            ensureAiTutorIndexes(get())
        } catch (error: MongoTimeoutException) {
            log.warn("Could not ensure database indexes because MongoDB is unavailable", error)
        }
    }

    routing {
        healthRoutes(get())
        authRoutes(get<AuthService>(), appConfig)
        userRoutes(get<UserService>())
        categoryRoutes(get<CategoryService>())
        courseRoutes(get<CourseService>())
        enrollmentRoutes(get<EnrollmentService>())
        progressRoutes(get<ProgressService>(), get<CertificateService>())
        quizRoutes(get<QuizService>(), get<CertificateService>())
        certificateRoutes(get<CertificateService>())
        learningPathRoutes(get<LearningPathService>())
        mediaRoutes(get<MediaService>())
        instructorRoutes(get<InstructorService>())
        adminRoutes(get<AdminService>())
        aiTutorRoutes(get<AiTutorService>())
    }
}
