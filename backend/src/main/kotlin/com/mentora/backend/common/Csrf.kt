package com.mentora.backend.common

import io.ktor.server.application.ApplicationCall

private const val CSRF_HEADER = "X-Requested-With"
private const val CSRF_HEADER_VALUE = "mentora-web"

/**
 * CSRF defense for cookie-authenticated state-changing requests — AUTH_SECURITY.md § 10. Every
 * POST/PATCH/PUT/DELETE route must call this. Mobile's Bearer-header auth is not cookie-based and
 * is not CSRF-exposed, but the header costs it nothing to send, so the check is applied uniformly
 * rather than branching on auth transport.
 */
fun ApplicationCall.requireCsrfHeader() {
    if (request.headers[CSRF_HEADER] != CSRF_HEADER_VALUE) throw ApiException.ForbiddenCsrf()
}
