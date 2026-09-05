package com.mentora.backend.auth.service

import com.mentora.backend.auth.repository.AuthRepository
import com.mentora.backend.auth.repository.RefreshTokenDocument
import com.mentora.backend.common.ApiException
import com.mentora.backend.common.Role
import com.mentora.backend.config.AppConfig
import com.mentora.backend.users.repository.UserDocument
import com.mongodb.ErrorCategory
import com.mongodb.MongoWriteException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId
import org.mindrot.jbcrypt.BCrypt
import kotlin.time.Duration.Companion.days

@Serializable data class RegisterRequest(val email: String, val password: String, val name: String)
@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class RefreshRequest(val refreshToken: String? = null)
@Serializable data class AuthUser(
    val id: String, val email: String, val name: String, val role: Role, val preferredLocale: String? = null,
)
@Serializable data class AuthResponse(val accessToken: String, val refreshToken: String, val user: AuthUser)
@Serializable data class RefreshResponse(val accessToken: String, val refreshToken: String)
data class TokenPair(val accessToken: String, val refreshToken: String)

class AuthService(
    private val repository: AuthRepository,
    private val tokenIssuer: TokenIssuer,
    private val config: AppConfig,
) {
    private val refreshMutationMutex = Mutex()

    suspend fun register(request: RegisterRequest): AuthResponse {
        val email = normalizeAndValidateEmail(request.email)
        validatePassword(request.password)
        val name = request.name.trim().also {
            if (it.isBlank()) throw ApiException.Validation(fields = mapOf("name" to "REQUIRED"))
            if (it.length > 120) throw ApiException.Validation(fields = mapOf("name" to "TOO_LONG"))
        }
        val user = UserDocument(
            email = email, passwordHash = BCrypt.hashpw(request.password, BCrypt.gensalt(12)),
            role = Role.student, name = name, createdAt = Clock.System.now(),
        )
        val id = try {
            repository.insertUser(user)
        } catch (error: MongoWriteException) {
            if (error.error.category == ErrorCategory.DUPLICATE_KEY) {
                throw ApiException.Conflict("EMAIL_ALREADY_REGISTERED", "This email is already registered.")
            }
            throw error
        }
        val persisted = user.copy(id = id)
        val pair = issueNewFamily(persisted)
        return AuthResponse(pair.accessToken, pair.refreshToken, persisted.toAuthUser())
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        val user = repository.findUserByEmail(request.email.trim().lowercase())
        val passwordHash = user?.passwordHash ?: DUMMY_PASSWORD_HASH
        val valid = BCrypt.checkpw(request.password, passwordHash) && user != null
        if (!valid) throw ApiException.InvalidCredentials()
        val pair = issueNewFamily(requireNotNull(user))
        return AuthResponse(pair.accessToken, pair.refreshToken, user.toAuthUser())
    }

    suspend fun refresh(rawToken: String?): RefreshResponse = refreshMutationMutex.withLock {
        val raw = rawToken?.takeIf { it.isNotBlank() } ?: throw ApiException.TokenInvalid()
        val stored = repository.findRefreshToken(tokenIssuer.sha256(raw)) ?: throw ApiException.TokenInvalid()
        val now = Clock.System.now()
        if (stored.revokedAt != null) {
            repository.revokeFamily(stored.familyId, now)
            throw ApiException.TokenInvalid()
        }
        if (stored.expiresAt < now) throw ApiException.TokenExpired()
        if (!repository.revokeIfActive(requireNotNull(stored.id), now)) {
            repository.revokeFamily(stored.familyId, now)
            throw ApiException.TokenInvalid()
        }
        val user = repository.findUserById(stored.userId) ?: throw ApiException.TokenInvalid()
        val next = tokenIssuer.refreshToken(stored.familyId)
        repository.insertRefreshToken(next.document(stored.userId, now))
        RefreshResponse(tokenIssuer.accessToken(stored.userId, user.role), next.raw)
    }

    suspend fun logout(rawToken: String?) = refreshMutationMutex.withLock {
        val raw = rawToken?.takeIf { it.isNotBlank() } ?: return@withLock
        repository.findRefreshToken(tokenIssuer.sha256(raw))?.let {
            repository.revokeFamily(it.familyId, Clock.System.now())
        }
    }

    private suspend fun issueNewFamily(user: UserDocument): TokenPair {
        val id = requireNotNull(user.id)
        val refresh = tokenIssuer.refreshToken()
        repository.insertRefreshToken(refresh.document(id, Clock.System.now()))
        return TokenPair(tokenIssuer.accessToken(id, user.role), refresh.raw)
    }

    private fun IssuedRefreshToken.document(userId: ObjectId, now: Instant) = RefreshTokenDocument(
        userId = userId, tokenHash = hash, familyId = familyId,
        expiresAt = now + config.refreshTokenTtlDays.days,
    )
    private fun UserDocument.toAuthUser() = AuthUser(
        requireNotNull(id).toHexString(), email, name, role, preferredLocale,
    )
    private fun normalizeAndValidateEmail(raw: String): String {
        val email = raw.trim().lowercase()
        if (email.length > 254 || !EMAIL.matches(email)) {
            throw ApiException.Validation(fields = mapOf("email" to "INVALID"))
        }
        return email
    }
    private fun validatePassword(password: String) {
        if (password.length < 8 || password.none(Char::isLetter) || password.none(Char::isDigit)) {
            throw ApiException.Validation(fields = mapOf("password" to "WEAK"))
        }
    }
    private companion object {
        val EMAIL = Regex("^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$")
        val DUMMY_PASSWORD_HASH: String = BCrypt.hashpw("dummy-password-not-used-1", BCrypt.gensalt(12))
    }
}
