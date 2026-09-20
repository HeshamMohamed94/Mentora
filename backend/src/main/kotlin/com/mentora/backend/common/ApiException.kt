package com.mentora.backend.common

import io.ktor.http.HttpStatusCode

/**
 * The full error-code taxonomy — API_CONTRACT.md § 4. StatusPages (plugins/StatusPages.kt) maps every
 * one of these to the fixed error envelope. Never throw a raw exception a route handler expects the
 * client to parse — always throw (or a service-layer function returns and a route throws) one of these.
 */
sealed class ApiException(
    val status: HttpStatusCode,
    val code: String,
    override val message: String,
    val fields: Map<String, String>? = null,
) : RuntimeException(message) {

    class Validation(message: String = "The request could not be validated.", fields: Map<String, String>? = null) :
        ApiException(HttpStatusCode.BadRequest, "VALIDATION_ERROR", message, fields)

    class InvalidCredentials(message: String = "Invalid email or password.") :
        ApiException(HttpStatusCode.Unauthorized, "AUTH_INVALID_CREDENTIALS", message)

    class TokenExpired(message: String = "The access token has expired.") :
        ApiException(HttpStatusCode.Unauthorized, "AUTH_TOKEN_EXPIRED", message)

    class TokenInvalid(message: String = "The token is invalid.") :
        ApiException(HttpStatusCode.Unauthorized, "AUTH_TOKEN_INVALID", message)

    class ForbiddenRole(message: String = "Your role cannot perform this action.") :
        ApiException(HttpStatusCode.Forbidden, "FORBIDDEN_ROLE", message)

    class ForbiddenNotOwner(message: String = "You do not own this resource.") :
        ApiException(HttpStatusCode.Forbidden, "FORBIDDEN_NOT_OWNER", message)

    class ForbiddenNotEnrolled(message: String = "You are not enrolled in this course.") :
        ApiException(HttpStatusCode.Forbidden, "FORBIDDEN_NOT_ENROLLED", message)

    /** CSRF header check failure on a cookie-authenticated state-changing request — AUTH_SECURITY.md § 10.
     * Not enumerated in API_CONTRACT.md's original example list; added here as a same-family (403) code
     * since that table's codes are documented as examples, not an exhaustive enum. Recorded in
     * execution/INTEGRATION_CONTRACT.md § 4 as an as-built taxonomy addition. */
    class ForbiddenCsrf(message: String = "Missing or invalid CSRF header.") :
        ApiException(HttpStatusCode.Forbidden, "FORBIDDEN_CSRF", message)

    class NotFound(resourceCode: String, message: String = "The requested resource was not found.") :
        ApiException(HttpStatusCode.NotFound, resourceCode, message)

    class Conflict(conflictCode: String, message: String) :
        ApiException(HttpStatusCode.Conflict, conflictCode, message)

    class RateLimited(rateLimitCode: String, message: String = "Too many requests. Please slow down.") :
        ApiException(HttpStatusCode.TooManyRequests, rateLimitCode, message)

    /** The AI Tutor provider is unreachable, timed out, overloaded, or produced no usable response —
     * PHASE_6_SYSTEM_DESIGN.md § 11. Always a transient/upstream condition, never a client mistake. */
    class ServiceUnavailable(
        code: String = "AI_TUTOR_UNAVAILABLE",
        message: String = "The AI Tutor is temporarily unavailable. Please try again.",
    ) : ApiException(HttpStatusCode.ServiceUnavailable, code, message)

    class Internal(message: String = "Something went wrong.", cause: Throwable? = null) :
        ApiException(HttpStatusCode.InternalServerError, "INTERNAL_ERROR", message) {
        init {
            cause?.let { initCause(it) }
        }
    }
}
