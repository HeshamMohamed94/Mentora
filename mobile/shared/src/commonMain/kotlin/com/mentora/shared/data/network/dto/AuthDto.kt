package com.mentora.shared.data.network.dto

import com.mentora.shared.auth.Role
import kotlinx.serialization.Serializable

/**
 * Wire-shape request/response DTOs for `backend/src/main/kotlin/com/mentora/backend/auth/routes/AuthRoutes.kt`'s
 * 4 endpoints — kept separate from the `com.mentora.shared.auth`/domain types the rest of `shared`
 * actually consumes (mapped in `AuthRepositoryImpl`). Field names/types match the backend's
 * `RegisterRequest`/`LoginRequest`/`RefreshRequest`/`AuthUser`/`AuthResponse`/`RefreshResponse`
 * (`backend/.../auth/service/AuthService.kt:20-27`) exactly.
 */
@Serializable
data class RegisterRequestDto(val email: String, val password: String, val name: String)

@Serializable
data class LoginRequestDto(val email: String, val password: String)

/** Also doubles as the `/auth/logout` request body — both endpoints accept `{refreshToken}`. */
@Serializable
data class RefreshRequestDto(val refreshToken: String)

@Serializable
data class SessionUserDto(
    val id: String,
    val email: String,
    val name: String,
    val role: Role,
    val preferredLocale: String? = null,
)

/** `/auth/register` (201) and `/auth/login` (200) share this exact response shape. */
@Serializable
data class AuthResponseDto(
    val accessToken: String,
    val refreshToken: String,
    val user: SessionUserDto,
)

/** `/auth/refresh`'s response — same rotated-token pair, deliberately no `user` field. */
@Serializable
data class RefreshResponseDto(val accessToken: String, val refreshToken: String)

/**
 * `/auth/logout`'s trivial `{}` body (`call.respondData(buildJsonObject { })` in
 * `AuthRoutes.kt:53`) — a Kotlin `object`'s generated serializer encodes/decodes to/from exactly
 * `{}`, matching the wire shape with no fields to keep in sync.
 */
@Serializable
object EmptyResponseDto
