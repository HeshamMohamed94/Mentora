package com.mentora.shared.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors `backend/src/main/kotlin/com/mentora/backend/common/Principal.kt`'s `Role` enum exactly
 * (`student`/`instructor`/`admin`, lowercase on the wire — see the [SerialName] on each entry,
 * matching both `AuthUser.role`'s JSON encoding and the JWT `role` claim `TokenIssuer.kt` embeds).
 *
 * `shared` is Mobile/Student-only (`execution/PHASE_3_KMP_PLAN.md` § 2's constraint table: "Mobile
 * is Student-only — Instructor/Admin are Web-only" — zero instructor/admin surfaces are ever built
 * here). [Instructor]/[Admin] exist only because they are structurally present on this shared wire
 * shape, never because `shared` implements any instructor/admin-facing behavior.
 */
@Serializable
enum class Role {
    @SerialName("student") Student,
    @SerialName("instructor") Instructor,
    @SerialName("admin") Admin,
}
