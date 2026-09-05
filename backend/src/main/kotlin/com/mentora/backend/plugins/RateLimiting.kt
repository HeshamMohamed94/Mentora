package com.mentora.backend.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.ratelimit.RateLimit
import io.ktor.server.plugins.ratelimit.RateLimitName
import kotlin.time.Duration.Companion.minutes

fun Application.configureRateLimiting() {
    install(RateLimit) {
        global {
            rateLimiter(limit = 300, refillPeriod = 1.minutes)
        }
        register(RateLimitName("auth")) {
            rateLimiter(limit = 10, refillPeriod = 1.minutes)
        }
        register(RateLimitName("aiTutor")) {
            rateLimiter(limit = 20, refillPeriod = 1.minutes)
        }
    }
}
