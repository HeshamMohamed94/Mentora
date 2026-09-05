package com.mentora.backend.plugins

import com.mentora.backend.config.AppConfig
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.cors.routing.CORS
import java.net.URI

fun Application.configureCors(appConfig: AppConfig) {
    install(CORS) {
        appConfig.corsAllowedOrigins.forEach { origin ->
            val uri = URI(origin)
            val authority = requireNotNull(uri.rawAuthority) { "CORS origin must include a host: $origin" }
            allowHost(authority, schemes = listOf(requireNotNull(uri.scheme)))
        }
        allowCredentials = true
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Head)
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Accept)
        allowHeader(HttpHeaders.XRequestId)
        allowHeader("X-Requested-With")
    }
}
