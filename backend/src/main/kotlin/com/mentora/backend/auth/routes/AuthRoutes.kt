package com.mentora.backend.auth.routes

import com.mentora.backend.auth.service.AuthService
import com.mentora.backend.auth.service.LoginRequest
import com.mentora.backend.auth.service.RefreshRequest
import com.mentora.backend.auth.service.RegisterRequest
import com.mentora.backend.common.requireCsrfHeader
import com.mentora.backend.common.respondData
import com.mentora.backend.config.AppConfig
import io.ktor.http.CookieEncoding
import io.ktor.http.Cookie
import io.ktor.http.HttpStatusCode
import io.ktor.util.date.GMTDate
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.receive
import io.ktor.server.request.receiveNullable
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.json.buildJsonObject

private const val ACCESS_COOKIE = "mentora_access_token"
private const val REFRESH_COOKIE = "mentora_refresh_token"

fun Route.authRoutes(service: AuthService, config: AppConfig) {
    rateLimit(RateLimitName("auth")) {
        route("/api/v1/auth") {
            post("/register") {
                call.requireCsrfHeader()
                val response = service.register(call.receive<RegisterRequest>())
                call.setAuthCookies(response.accessToken, response.refreshToken, config)
                call.respondData(response, HttpStatusCode.Created)
            }
            post("/login") {
                call.requireCsrfHeader()
                val response = service.login(call.receive<LoginRequest>())
                call.setAuthCookies(response.accessToken, response.refreshToken, config)
                call.respondData(response)
            }
            post("/refresh") {
                call.requireCsrfHeader()
                val response = service.refresh(call.refreshToken())
                call.setAuthCookies(response.accessToken, response.refreshToken, config)
                call.respondData(response)
            }
            post("/logout") {
                call.requireCsrfHeader()
                service.logout(call.refreshToken())
                call.clearAuthCookie(ACCESS_COOKIE)
                call.clearAuthCookie(REFRESH_COOKIE)
                call.respondData(buildJsonObject { })
            }
        }
    }
}

private fun ApplicationCall.clearAuthCookie(name: String) {
    response.cookies.append(
        Cookie(
            name = name, value = "", encoding = CookieEncoding.RAW, maxAge = 0,
            expires = GMTDate.START, path = "/", secure = true, httpOnly = true,
            extensions = mapOf("SameSite" to "Lax"),
        )
    )
}

private suspend fun ApplicationCall.refreshToken(): String? =
    request.cookies[REFRESH_COOKIE] ?: receiveNullable<RefreshRequest>()?.refreshToken

private fun ApplicationCall.setAuthCookies(accessToken: String, refreshToken: String, config: AppConfig) {
    response.cookies.append(
        ACCESS_COOKIE, accessToken, encoding = CookieEncoding.RAW,
        maxAge = config.accessTokenTtlMinutes * 60, path = "/", secure = true, httpOnly = true,
        extensions = mapOf("SameSite" to "Lax"),
    )
    response.cookies.append(
        REFRESH_COOKIE, refreshToken, encoding = CookieEncoding.RAW,
        maxAge = config.refreshTokenTtlDays * 24 * 60 * 60, path = "/", secure = true, httpOnly = true,
        extensions = mapOf("SameSite" to "Lax"),
    )
}
