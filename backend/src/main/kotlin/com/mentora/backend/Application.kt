package com.mentora.backend

import com.mentora.backend.config.AppConfig
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
        modules(configKoinModule(appConfig), databaseKoinModule)
    }
    configureDatabaseLifecycle()

    configureMonitoring()
    configureSerialization()
    configureCors(appConfig)
    configureSecurity(appConfig)
    configureRequestValidation()
    configureRateLimiting()
    configureStatusPages()

    routing {
        healthRoutes(get())
    }
}
