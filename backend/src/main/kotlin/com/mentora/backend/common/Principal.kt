package com.mentora.backend.common

import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.authentication
import org.bson.types.ObjectId

enum class Role {
    student, instructor, admin;

    companion object {
        fun fromClaim(value: String): Role = entries.firstOrNull { it.name == value }
            ?: throw ApiException.TokenInvalid("Unknown role claim: $value")
    }
}

/** The verified identity attached to every authenticated request — API_CONTRACT.md § 2. */
data class MentoraPrincipal(
    val userId: ObjectId,
    val role: Role,
)

/** Resolves the verified principal attached by the Authentication plugin. Throws if absent. */
fun ApplicationCall.mentoraPrincipal(): MentoraPrincipal =
    this.authentication.principal<MentoraPrincipal>()
        ?: throw ApiException.TokenInvalid("No authenticated principal on this request.")

fun MentoraPrincipal.requireRole(vararg allowed: Role) {
    if (role !in allowed) throw ApiException.ForbiddenRole()
}
