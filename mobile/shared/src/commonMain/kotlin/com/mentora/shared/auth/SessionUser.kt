package com.mentora.shared.auth

/**
 * The narrow user shape embedded in `POST /auth/register`/`POST /auth/login`'s response —
 * mirrors `AuthUser` in `backend/src/main/kotlin/com/mentora/backend/auth/service/AuthService.kt:23-25`
 * (`id, email, name, role, preferredLocale`) exactly. Deliberately distinct from the full `User`
 * model Task 6 will define for `GET /users/me` (that response additionally carries
 * `avatarMediaId`/`createdAt`, which this type never does) — the two must never be conflated, per
 * `execution/PHASE_3_KMP_PLAN.md` Task 5.
 */
data class SessionUser(
    val id: String,
    val email: String,
    val name: String,
    val role: Role,
    val preferredLocale: String?,
)
