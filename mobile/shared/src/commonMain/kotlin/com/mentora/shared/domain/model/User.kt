package com.mentora.shared.domain.model

import com.mentora.shared.auth.Role

/**
 * The full user profile returned by `GET /users/me` — mirrors the backend's `UserProfile`
 * (`backend/src/main/kotlin/com/mentora/backend/users/service/UserService.kt:12-20`) field-for-field:
 * `id, email, name, role, avatarMediaId?, preferredLocale?, createdAt`. Deliberately distinct from
 * [com.mentora.shared.auth.SessionUser] — the narrower shape embedded in
 * `POST /auth/register`/`POST /auth/login` responses, which never carries [avatarMediaId]/
 * [createdAt] — the two must never be conflated (`execution/PHASE_3_KMP_PLAN.md` Task 5/6).
 *
 * [avatarMediaId] is carried through structurally only: Task 6 builds no avatar-upload/read use
 * case around it — it is a known dead field nothing on the backend ever writes yet (D44,
 * `execution/PHASE_3_KMP_PLAN.md` Task 6 "Must NOT").
 *
 * [createdAt] is kept as the raw ISO-8601 wire string (the backend's `kotlinx.datetime.Instant`
 * field JSON-encodes to exactly this shape) rather than parsed into a `kotlinx.datetime.Instant` —
 * `shared`'s Gradle build does not yet depend on `kotlinx-datetime` (see `shared/build.gradle.kts`),
 * and no Phase 3 use case needs anything beyond displaying this value verbatim.
 */
data class User(
    val id: String,
    val email: String,
    val name: String,
    val role: Role,
    val avatarMediaId: String?,
    val preferredLocale: String?,
    val createdAt: String,
)
