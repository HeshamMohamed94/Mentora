package com.mentora.backend.plugins

import com.mentora.backend.common.ApiError
import com.mentora.backend.common.ApiErrorBody
import com.mentora.backend.common.ApiException
import com.mentora.backend.common.ApiMeta
import com.mentora.backend.common.requestId
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.application.install
import io.ktor.server.plugins.requestvalidation.RequestValidationException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond

fun Application.configureStatusPages() {
    val logger = log
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(
                cause.status,
                ApiError(ApiErrorBody(cause.code, cause.message, cause.fields), ApiMeta(call.requestId()))
            )
        }
        exception<RequestValidationException> { call, cause ->
            val validation = ApiException.Validation(fields = mapOf("request" to cause.reasons.joinToString("; ")))
            call.respond(
                validation.status,
                ApiError(
                    ApiErrorBody(validation.code, validation.message, validation.fields),
                    ApiMeta(call.requestId())
                )
            )
        }
        exception<Throwable> { call, cause ->
            logger.error("Unhandled request failure; requestId={}", call.requestId(), cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ApiError(
                    ApiErrorBody("INTERNAL_ERROR", "Something went wrong."),
                    ApiMeta(call.requestId())
                )
            )
        }
    }
}
