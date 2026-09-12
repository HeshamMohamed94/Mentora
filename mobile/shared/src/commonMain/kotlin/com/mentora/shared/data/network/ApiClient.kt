package com.mentora.shared.data.network

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.serializer

/**
 * The only networking surface a repository (Task 5+) is allowed to touch. Every function returns
 * an [ApiResult] — a raw [HttpResponse], Ktor exception, or unparsed JSON body must never escape
 * this class. The injected [httpClient] is expected to already be fully configured (base URL,
 * timeouts, retry, CSRF header) by [HttpClientFactory] — one client, configured centrally; this
 * class adds no auth/bearer-token logic of its own (that is Task 5's `SessionManager`/auth plugin).
 *
 * Members below `@PublishedApi internal` exist only because `suspend inline fun <reified T>` needs
 * access to them from an inlined call site — they are implementation detail, not part of the
 * intended public surface (callers use only `get`/`post`/`patch`/`delete`/`getPage`).
 */
class ApiClient(@PublishedApi internal val httpClient: HttpClient) {

    suspend inline fun <reified T> get(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
    ): ApiResult<T> {
        val outcome = performRequest {
            httpClient.get(path) {
                queryParams.forEach { (key, value) -> parameter(key, value) }
            }
        }
        return when (outcome) {
            is RequestOutcome.Err -> outcome.failure
            is RequestOutcome.Ok -> decodeDataEnvelope(outcome.response, path)
        }
    }

    suspend inline fun <reified TBody, reified TResponse> post(
        path: String,
        body: TBody,
    ): ApiResult<TResponse> {
        val outcome = performRequest {
            httpClient.post(path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return when (outcome) {
            is RequestOutcome.Err -> outcome.failure
            is RequestOutcome.Ok -> decodeDataEnvelope(outcome.response, path)
        }
    }

    suspend inline fun <reified TBody, reified TResponse> patch(
        path: String,
        body: TBody,
    ): ApiResult<TResponse> {
        val outcome = performRequest {
            httpClient.patch(path) {
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
        return when (outcome) {
            is RequestOutcome.Err -> outcome.failure
            is RequestOutcome.Ok -> decodeDataEnvelope(outcome.response, path)
        }
    }

    suspend inline fun <reified T> delete(path: String): ApiResult<T> {
        val outcome = performRequest { httpClient.delete(path) }
        return when (outcome) {
            is RequestOutcome.Err -> outcome.failure
            is RequestOutcome.Ok -> decodeDataEnvelope(outcome.response, path)
        }
    }

    /**
     * Assembles a [CursorPage] from the envelope's `data` array plus `meta.nextCursor` — a
     * paginated list response is never its own JSON shape on the wire (see [CursorPage] doc).
     */
    suspend inline fun <reified T> getPage(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
    ): ApiResult<CursorPage<T>> {
        val outcome = performRequest {
            httpClient.get(path) {
                queryParams.forEach { (key, value) -> parameter(key, value) }
            }
        }
        return when (outcome) {
            is RequestOutcome.Err -> outcome.failure
            is RequestOutcome.Ok -> decodePageEnvelope(outcome.response, path)
        }
    }

    /** Runs the HTTP call, turning any thrown exception (timeout, connection refused, unresolved
     * host, ...) into a generic [ApiResult.Failure] instead of letting it escape as a raw exception. */
    @PublishedApi
    internal suspend fun performRequest(call: suspend () -> HttpResponse): RequestOutcome = try {
        RequestOutcome.Ok(call())
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        RequestOutcome.Err(networkFailure(cause))
    }

    @PublishedApi
    internal suspend inline fun <reified T> decodeDataEnvelope(response: HttpResponse, path: String): ApiResult<T> {
        val status = response.status.value
        val bodyText = response.bodyAsText()

        if (status == 429) return synthesizeRateLimitedFailure(status, path, bodyText)
        if (status !in 200..299) return decodeErrorBody(status, bodyText)

        return try {
            val envelope = MentoraJson.decodeFromString(ApiSuccess.serializer(serializer<T>()), bodyText)
            ApiResult.Success(envelope.data)
        } catch (cause: Exception) {
            unparseableSuccessFailure(status)
        }
    }

    @PublishedApi
    internal suspend inline fun <reified T> decodePageEnvelope(
        response: HttpResponse,
        path: String,
    ): ApiResult<CursorPage<T>> {
        val status = response.status.value
        val bodyText = response.bodyAsText()

        if (status == 429) return synthesizeRateLimitedFailure(status, path, bodyText)
        if (status !in 200..299) return decodeErrorBody(status, bodyText)

        return try {
            val envelope = MentoraJson.decodeFromString(
                ApiSuccess.serializer(ListSerializer(serializer<T>())),
                bodyText,
            )
            ApiResult.Success(CursorPage(items = envelope.data, nextCursor = envelope.meta.nextCursor))
        } catch (cause: Exception) {
            unparseableSuccessFailure(status)
        }
    }

    /** Attempts to decode the standard [ApiError] envelope; falls back to a generic
     * [ApiErrorCode.Unknown]-based failure for a body that isn't parseable JSON at all (e.g. a raw
     * 500 HTML error page). */
    @PublishedApi
    internal fun decodeErrorBody(status: Int, bodyText: String): ApiResult.Failure = try {
        MentoraJson.decodeFromString(ApiError.serializer(), bodyText).toFailure(status)
    } catch (cause: Exception) {
        ApiResult.Failure(
            code = ApiErrorCode.Unknown("HTTP_$status"),
            message = "Request failed with status $status.",
            fields = null,
            httpStatus = status,
        )
    }

    @PublishedApi
    internal fun unparseableSuccessFailure(status: Int): ApiResult.Failure = ApiResult.Failure(
        code = ApiErrorCode.Unknown("UNPARSEABLE_RESPONSE"),
        message = "The server returned a successful status but an unparseable body.",
        fields = null,
        httpStatus = status,
    )

    @PublishedApi
    internal fun networkFailure(cause: Throwable): ApiResult.Failure = ApiResult.Failure(
        code = ApiErrorCode.Unknown("NETWORK_ERROR"),
        message = cause.message ?: "The network request failed.",
        fields = null,
        httpStatus = 0,
    )

    /** Non-generic so `performRequest` never triggers the "public inline fun leaks private/erased
     * generic member" concern that a raw `ApiResult<HttpResponse>` return type would raise. */
    @PublishedApi
    internal sealed class RequestOutcome {
        data class Ok(val response: HttpResponse) : RequestOutcome()
        data class Err(val failure: ApiResult.Failure) : RequestOutcome()
    }
}
