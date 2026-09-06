package com.mentora.backend.plugins

import com.mentora.backend.config.AppConfig
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.days

fun Application.configureRateLimiting(appConfig: AppConfig) {
    install(RateLimit) {
        global {
            rateLimiter(limit = 300, refillPeriod = 1.minutes)
        }
        register(RateLimitName("auth")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
        }
        register(RateLimitName("aiTutor")) {
            rateLimiter(limit = appConfig.aiTutorMessagesPerMinute, refillPeriod = 1.minutes)
        }
        register(RateLimitName("aiTutorDaily")) {
            rateLimiter(limit = appConfig.aiTutorMessagesPerDay, refillPeriod = 1.days)
        }
    }
}
