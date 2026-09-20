package com.mentora.backend.aitutor.provider

import com.mentora.backend.common.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.readString
import kotlinx.serialization.encodeToString

/**
 * Real Anthropic Claude Messages API provider — PHASE_6_SYSTEM_DESIGN.md § 3.
 *
 * Deliberately **not** a `data class`, and [apiKey] is a private constructor property only, so a
 * stray `toString()`/log interpolation of this instance can never print the key (Design § 19.2/A8).
 */
class AnthropicAiProvider(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String = "https://api.anthropic.com",
) : AiProvider {

    override suspend fun complete(
        request: AiCompletionRequest,
        onStream: suspend (Flow<AiToken>) -> Unit,
    ): AiUsage {
        val requestBody = anthropicJson.encodeToString(
            AnthropicRequest(
                model = model,
                maxTokens = request.maxResponseTokens,
                system = request.systemPrompt,
                // F1: request.history is already the complete, normalized turn sequence (history +
                // new user turn) — mapped 1-to-1, never appending a turn of our own here.
                messages = request.history.map { AnthropicMessage(role = it.role, content = it.content) },
                stream = true,
            ),
        )

        // True once the first assistant text delta has been handed to [onStream]. Everything before
        // this point that fails maps to a clean ApiException (Design § 1.3/§ 11); everything after
        // propagates unmodified — the response has already been committed to the caller (§ 11 row 12).
        var firstTokenEmitted = false
        var usage = AiUsage(null, null)

        try {
            httpClient.preparePost("$baseUrl/v1/messages") {
                header("x-api-key", apiKey)
                header("anthropic-version", ANTHROPIC_VERSION)
                header("accept", "text/event-stream")
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    throw mapHttpError(response)
                }

                val sse = AnthropicSse(response.bodyAsChannel())
                var inputTokens: Int? = null
                var firstText: String? = null

                while (firstText == null) {
                    when (val event = sse.next()) {
                        // Codex second opinion, finding 2: an empty text delta must not count as
                        // "the first token" — otherwise a message_stop right after it would report a
                        // successful completion with zero actual assistant text (violates the
                        // pre-stream contract's "no usable response" guarantee).
                        is AnthropicEvent.TextDelta -> if (event.text.isNotEmpty()) firstText = event.text
                        is AnthropicEvent.InputUsage -> inputTokens = event.inputTokens ?: inputTokens
                        is AnthropicEvent.OutputUsage -> Unit
                        is AnthropicEvent.Error -> throw mapAnthropicFailure(errorType = event.type)
                        // F2: pre-first-token, a real message_stop and a bare channel EOF are both
                        // "no usable response" — no need to distinguish them here.
                        AnthropicEvent.MessageStop -> throw ApiException.ServiceUnavailable()
                        AnthropicEvent.ChannelExhausted -> throw ApiException.ServiceUnavailable()
                        AnthropicEvent.Ignored -> Unit
                    }
                }

                var outputTokens: Int? = null
                firstTokenEmitted = true
                onStream(
                    flow {
                        emit(AiToken(firstText))
                        while (true) {
                            when (val event = sse.next()) {
                                is AnthropicEvent.TextDelta -> emit(AiToken(event.text))
                                is AnthropicEvent.InputUsage -> inputTokens = event.inputTokens ?: inputTokens
                                is AnthropicEvent.OutputUsage -> outputTokens = event.outputTokens ?: outputTokens
                                is AnthropicEvent.Error -> throw ApiException.ServiceUnavailable()
                                AnthropicEvent.MessageStop -> return@flow
                                // F2: mid-stream, the channel closing WITHOUT a message_stop means the
                                // connection ended before Anthropic told us it was actually done — the
                                // text collected so far may be truncated, so this is a stream failure,
                                // not a successful completion (never persist it as a complete answer).
                                AnthropicEvent.ChannelExhausted -> throw ApiException.ServiceUnavailable()
                                AnthropicEvent.Ignored -> Unit
                            }
                        }
                    },
                )
                usage = AiUsage(inputTokens, outputTokens)
            }
        } catch (e: ApiException) {
            throw e
        } catch (e: AnthropicStreamException) {
            // Malformed content_block_delta: pre-first-token -> clean 503; mid-stream -> propagate raw.
            if (firstTokenEmitted) throw e else throw ApiException.ServiceUnavailable()
        } catch (e: CancellationException) {
            // F4: a real coroutine cancellation (e.g. the client disconnected) must propagate
            // unmodified — never converted to ApiException.ServiceUnavailable, which would break
            // structured-concurrency cancellation semantics and misreport a client disconnect as a
            // provider outage. Must be caught before the generic `catch (e: Exception)` below, since
            // CancellationException is itself an Exception subtype and Kotlin evaluates catch
            // clauses in order.
            throw e
        } catch (e: Exception) {
            // Connection refused / DNS / TLS / IOException / request or socket timeout.
            if (firstTokenEmitted) throw e else throw ApiException.ServiceUnavailable()
        }

        return usage
    }

    /**
     * Reads a bounded amount of a non-2xx body and maps it per PHASE_6_SYSTEM_DESIGN.md § 11.
     *
     * Codex second opinion, finding 1: the body read is a suspending call, so a plain
     * `catch (Exception)` around it would also catch a real `CancellationException` (e.g. the
     * client disconnected while this was reading) and convert it into an ordinary "couldn't parse
     * the error body" case — breaking cancellation semantics the same way F4 already fixed in
     * [complete]'s own catch chain, just at a different suspension point. Caught and rethrown
     * unmodified before the recovery catches below.
     */
    private suspend fun mapHttpError(response: HttpResponse): ApiException {
        val bodyText = try {
            response.bodyAsChannel().readRemaining(MAX_ERROR_BODY_BYTES).readString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ""
        }
        val errorType = try {
            anthropicJson.decodeFromString(AnthropicErrorEnvelope.serializer(), bodyText).error.type
        } catch (e: Exception) {
            null
        }
        return mapAnthropicFailure(errorType = errorType, statusCode = response.status)
    }

    /**
     * F3: the client-visible `ApiException.Internal.message` must stay generic (the class's own
     * default, "Something went wrong.") — none of this diagnostic detail (Anthropic's raw error
     * type, or a Mentora env-var name) may reach the HTTP response body. The detail is preserved
     * for logging only, via `cause`, since `ApiException.Internal` already supports one.
     */
    private fun mapAnthropicFailure(errorType: String?, statusCode: HttpStatusCode? = null): ApiException {
        val status = statusCode?.value
        return when {
            status == 401 || errorType == "authentication_error" -> internalWithDetail(
                "AI Tutor provider rejected the request as unauthenticated " +
                    "(status=$status, error.type=$errorType). Check AI_PROVIDER_API_KEY.",
            )

            status == 403 || errorType == "permission_error" -> internalWithDetail(
                "AI Tutor provider denied access to the configured model or key " +
                    "(status=$status, error.type=$errorType).",
            )

            status == 404 || errorType == "not_found_error" -> internalWithDetail(
                "AI Tutor provider could not find the configured model " +
                    "(status=$status, error.type=$errorType). Check AI_PROVIDER_MODEL.",
            )

            status == 400 || status == 413 ||
                errorType == "invalid_request_error" || errorType == "request_too_large" -> internalWithDetail(
                "AI Tutor provider rejected the request as invalid " +
                    "(status=$status, error.type=$errorType) — this is a Mentora prompt-construction bug.",
            )

            // 429 rate_limit_error, 500/503/529 overloaded/api errors, and any other/unknown SSE
            // error type or status all land here — always transient/upstream, never the student's fault.
            else -> ApiException.ServiceUnavailable()
        }
    }

    private fun internalWithDetail(detail: String): ApiException.Internal =
        ApiException.Internal(cause = RuntimeException(detail))

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val MAX_ERROR_BODY_BYTES = 4096L
    }
}
