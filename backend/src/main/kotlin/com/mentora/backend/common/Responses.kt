package com.mentora.backend.common

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.plugins.callid.callId
import io.ktor.server.response.respond

/** requestId is always sourced from the CallId plugin (plugins/Monitoring.kt) — never generated ad hoc. */
fun ApplicationCall.requestId(): String = this.callId ?: "unknown"

suspend inline fun <reified T> ApplicationCall.respondData(
    data: T,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respond(status, ApiSuccess(data, ApiMeta(requestId = requestId())))
}

suspend inline fun <reified T> ApplicationCall.respondPage(
    page: Page<T>,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respond(status, ApiSuccess(page.items, ApiMeta(requestId = requestId(), nextCursor = page.nextCursor)))
}
