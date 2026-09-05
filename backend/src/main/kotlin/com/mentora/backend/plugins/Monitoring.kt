package com.mentora.backend.plugins

import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callId
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import java.util.UUID

fun Application.configureMonitoring() {
    install(CallId) {
        retrieveFromHeader(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        verify { it.isNotBlank() && it.length <= 128 }
        replyToHeader(HttpHeaders.XRequestId)
    }

    install(CallLogging) {
        mdc("requestId") { it.callId }
        mdc("method") { it.request.httpMethod.value }
        mdc("path") { it.request.path() }
        mdc("status") { it.response.status()?.value?.toString() }
    }
}
