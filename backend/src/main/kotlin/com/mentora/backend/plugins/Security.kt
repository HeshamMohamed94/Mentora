package com.mentora.backend.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mentora.backend.common.ApiException
import com.mentora.backend.common.MentoraPrincipal
import com.mentora.backend.common.Role
import com.mentora.backend.config.AppConfig
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.jwt.jwt
import org.bson.types.ObjectId

private const val ACCESS_TOKEN_COOKIE = "mentora_access_token"

fun Application.configureSecurity(appConfig: AppConfig) {
    install(Authentication) {
        jwt("jwt-auth") {
            verifier(
                JWT.require(Algorithm.HMAC256(appConfig.jwtSigningSecret))
                    .withIssuer(appConfig.jwtIssuer)
                    .build()
            )
            authHeader { call ->
                call.request.cookies[ACCESS_TOKEN_COOKIE]
                    ?.let { HttpAuthHeader.Single("Bearer", it) }
                    ?: call.request.parseAuthorizationHeader()
            }
            validate { credential ->
                runCatching {
                    val userId = credential.payload.getClaim("userId").asString()
                    val role = credential.payload.getClaim("role").asString()
                    MentoraPrincipal(ObjectId(userId), Role.fromClaim(role))
                }.getOrNull()
            }
            challenge { _, _ -> throw ApiException.TokenInvalid() }
        }
    }
}
