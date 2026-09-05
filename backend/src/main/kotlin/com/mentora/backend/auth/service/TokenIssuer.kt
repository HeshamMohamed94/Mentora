package com.mentora.backend.auth.service

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.mentora.backend.common.Role
import com.mentora.backend.config.AppConfig
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import java.util.UUID
import org.bson.types.ObjectId

data class IssuedRefreshToken(val raw: String, val hash: String, val familyId: String)

class TokenIssuer(private val config: AppConfig) {
    private val random = SecureRandom()
    private val algorithm = Algorithm.HMAC256(config.jwtSigningSecret)

    fun accessToken(userId: ObjectId, role: Role): String {
        val now = java.time.Instant.now()
        return JWT.create().withIssuer(config.jwtIssuer)
            .withClaim("userId", userId.toHexString()).withClaim("role", role.name)
            .withIssuedAt(Date.from(now))
            .withExpiresAt(Date.from(now.plus(config.accessTokenTtlMinutes, ChronoUnit.MINUTES)))
            .sign(algorithm)
    }

    fun refreshToken(familyId: String = UUID.randomUUID().toString()): IssuedRefreshToken {
        val bytes = ByteArray(32).also(random::nextBytes)
        val raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return IssuedRefreshToken(raw, sha256(raw), familyId)
    }

    fun sha256(raw: String): String = MessageDigest.getInstance("SHA-256")
        .digest(raw.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
