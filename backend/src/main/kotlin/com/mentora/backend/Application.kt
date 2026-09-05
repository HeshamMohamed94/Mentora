package com.mentora.backend

import com.mentora.backend.auth.authModule
import com.mentora.backend.auth.repository.ensureAuthIndexes
import com.mentora.backend.auth.routes.authRoutes
import com.mentora.backend.auth.service.AuthService
import com.mentora.backend.categories.categoriesModule
import com.mentora.backend.categories.repository.ensureCategoriesIndexes
import com.mentora.backend.categories.routes.categoryRoutes
import com.mentora.backend.categories.service.CategoryService
import com.mentora.backend.config.AppConfig
import com.mentora.backend.courses.coursesModule
import com.mentora.backend.courses.repository.ensureCoursesIndexes
import com.mentora.backend.courses.routes.courseRoutes
import com.mentora.backend.courses.service.CourseService
import com.mentora.backend.enrollment.enrollmentModule
import com.mentora.backend.enrollment.repository.ensureEnrollmentIndexes
import com.mentora.backend.enrollment.routes.enrollmentRoutes
import com.mentora.backend.enrollment.service.EnrollmentService
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
import com.mentora.backend.users.routes.userRoutes
import com.mentora.backend.users.service.UserService
import com.mentora.backend.users.usersModule
import com.mongodb.MongoTimeoutException
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.netty.EngineMain
import io.ktor.server.routing.routing
import org.koin.ktor.ext.get
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() = module(AppConfig.load())

internal fun Application.module(appConfig: AppConfig) {
    log.info("Starting Mentora backend with {}", appConfig.redactedSummary())

    install(Koin) {
        slf4jLogger()
        modules(
            configKoinModule(appConfig), databaseKoinModule, authModule, usersModule, categoriesModule,
            coursesModule, enrollmentModule, progressModule,
        )
    }
    configureDatabaseLifecycle()

    configureMonitoring()
    configureSerialization()
    configureCors(appConfig)
    configureSecurity(appConfig)
    configureRequestValidation()
    configureRateLimiting()
    configureStatusPages()

    kotlinx.coroutines.runBlocking {
        try {
            ensureAuthIndexes(get())
            ensureCategoriesIndexes(get())
            ensureCoursesIndexes(get())
            ensureEnrollmentIndexes(get())
            ensureProgressIndexes(get())
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
        progressRoutes(get<ProgressService>())
    }
}
