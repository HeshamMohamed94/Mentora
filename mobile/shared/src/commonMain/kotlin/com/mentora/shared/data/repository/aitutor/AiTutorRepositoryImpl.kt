package com.mentora.shared.data.repository.aitutor

import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiError
import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.MentoraJson
import com.mentora.shared.data.network.dto.AiConversationDto
import com.mentora.shared.data.network.dto.AiMessageDto
import com.mentora.shared.data.network.dto.SendAiMessageRequestDto
import com.mentora.shared.data.network.synthesizeRateLimitedFailure
import com.mentora.shared.data.network.toFailure
import com.mentora.shared.domain.model.AiConversation
import com.mentora.shared.domain.model.AiMessage
import io.ktor.client.HttpClient
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

/**
 * The real [AiTutorRepository]. [getConversation] goes through [apiClient] like every other read
 * in `shared` (its envelope is still the standard `{data, meta}` shape — only the `data` payload's
 * inner shape is non-standard, see [AiConversation]'s kdoc). [sendMessage] instead talks to the
 * injected [httpClient] directly, per Decision D-E — [apiClient]'s `post` assumes a JSON success
 * envelope this endpoint's success response never sends.
 */
class AiTutorRepositoryImpl(
    private val apiClient: ApiClient,
    private val httpClient: HttpClient,
) : AiTutorRepository {

    override suspend fun getConversation(cursor: String?, limit: Int?): ApiResult<AiConversation> {
        val queryParams = buildMap {
            cursor?.let { put("cursor", it) }
            limit?.let { put("limit", it.toString()) }
        }
        return when (val result = apiClient.get<AiConversationDto>(CONVERSATION_PATH, queryParams)) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }
    }

    override fun sendMessage(content: String, courseId: String?, lessonContextId: String?): Flow<AiStreamResult> =
        channelFlow {
            httpClient.preparePost(MESSAGES_PATH) {
                contentType(ContentType.Application.Json)
                setBody(SendAiMessageRequestDto(content, courseId, lessonContextId))
            }.execute { response ->
                if (response.status.value !in 200..299) {
                    send(AiStreamResult.PreStreamFailure(decodePreStreamFailure(response)))
                } else {
                    streamAssistantTokens(response.bodyAsChannel())
                }
            }
        }

    /** Decodes the pre-stream failure envelope exactly the way
     * [com.mentora.shared.data.network.ApiClient.decodeErrorBody]/[synthesizeRateLimitedFailure] do
     * for every other endpoint — duplicated in miniature here (rather than reusing those
     * `ApiClient`-internal members) since this repository deliberately does not route this request
     * through [ApiClient] at all (Decision D-E). */
    private suspend fun decodePreStreamFailure(response: HttpResponse): ApiResult.Failure {
        val status = response.status.value
        val bodyText = response.bodyAsText()
        if (status == 429) return synthesizeRateLimitedFailure(status, MESSAGES_PATH, bodyText)
        return try {
            MentoraJson.decodeFromString(ApiError.serializer(), bodyText).toFailure(status)
        } catch (cause: Exception) {
            ApiResult.Failure(
                code = ApiErrorCode.Unknown("HTTP_$status"),
                message = "Request failed with status $status.",
                fields = null,
                httpStatus = status,
            )
        }
    }

    private fun AiMessageDto.toDomain(): AiMessage = AiMessage(id, role, content, lessonContextId, createdAt)

    private fun AiConversationDto.toDomain(): AiConversation =
        AiConversation(conversationId, messages.map { it.toDomain() }, nextCursor)

    private companion object {
        const val CONVERSATION_PATH = "/api/v1/ai-tutor/conversation"
        const val MESSAGES_PATH = "/api/v1/ai-tutor/conversation/messages"
    }
}

/** Arbitrary, comfortably-sized read buffer — chunk boundaries emitted to callers are not
 * semantic (see [AiStreamResult]'s kdoc), so this size is not load-bearing for correctness, only
 * for how finely streamed text is broken up. */
private const val STREAM_READ_BUFFER_SIZE = 512

/**
 * Reads [channel] to completion, emitting each decoded piece of text as an [AiStreamResult.Chunk]
 * on this [ProducerScope]. A failure that happens after streaming has already begun (content
 * already flowed) is surfaced as [AiStreamResult.StreamFailed] with whatever text had already been
 * decoded — never silently dropped, never re-thrown as a generic `Flow` exception (which would
 * also discard the partial text from the collector's point of view).
 *
 * A standalone, internal top-level function (rather than a private method on
 * [AiTutorRepositoryImpl]) so it is directly unit-testable against a synthetic
 * [io.ktor.utils.io.ByteChannel] — closed with a failure cause partway through, to exercise the
 * "mid-stream failure preserves partial text" AC — without needing a real/mocked HTTP round trip.
 */
internal suspend fun ProducerScope<AiStreamResult>.streamAssistantTokens(channel: ByteReadChannel) {
    val decoder = Utf8ChunkDecoder()
    val partial = StringBuilder()
    val buffer = ByteArray(STREAM_READ_BUFFER_SIZE)
    try {
        while (true) {
            val bytesRead = channel.readAvailable(buffer)
            if (bytesRead == -1) break
            if (bytesRead == 0) continue
            val text = decoder.decode(buffer, bytesRead)
            if (text.isNotEmpty()) {
                partial.append(text)
                send(AiStreamResult.Chunk(text))
            }
        }
        // A channel closed with a failure cause does NOT necessarily surface it by throwing out of
        // readAvailable — empirically (Ktor 3.0.1's `ByteChannel`), a channel closed with a cause
        // and no further unread bytes simply returns -1 from readAvailable like a clean EOF would,
        // recording the cause on `closedCause` instead. This check is what actually distinguishes
        // "the stream ended because the server finished" from "the stream ended because it broke"
        // for THIS engine/channel implementation — the `catch` block below remains only as a
        // defensive fallback for any engine/channel that instead throws synchronously out of a read.
        channel.closedCause?.let { cause ->
            send(AiStreamResult.StreamFailed(partial.toString(), cause.message ?: "The AI Tutor stream failed."))
        }
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Exception) {
        send(AiStreamResult.StreamFailed(partial.toString(), cause.message ?: "The AI Tutor stream failed."))
    }
}
