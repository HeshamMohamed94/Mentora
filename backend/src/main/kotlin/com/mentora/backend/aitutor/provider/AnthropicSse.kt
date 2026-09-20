package com.mentora.backend.aitutor.provider

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A [content_block_delta] payload that fails to parse — never silently dropped, per
 * PHASE_6_SYSTEM_DESIGN.md § 3.4. Callers decide how to surface this: pre-first-token, it maps to
 * `ApiException.ServiceUnavailable`; mid-stream, it propagates raw out of the token [kotlinx.coroutines.flow.Flow].
 */
class AnthropicStreamException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

/** The events this parser cares about — everything else is [Ignored] by design (Design § 3.4). */
sealed class AnthropicEvent {
    data class TextDelta(val text: String) : AnthropicEvent()
    data class InputUsage(val inputTokens: Int?) : AnthropicEvent()
    data class OutputUsage(val outputTokens: Int?) : AnthropicEvent()
    data class Error(val type: String, val message: String) : AnthropicEvent()

    /** A genuine `message_stop` event arrived — the assistant finished normally. */
    object MessageStop : AnthropicEvent()

    /** F2: the channel reached EOF with nothing left buffered — the connection closed WITHOUT
     * Anthropic ever telling us it was done. Never equivalent to [MessageStop]: pre-first-token
     * both mean "no usable response" (503), but mid-stream this means the response may be
     * truncated and must be treated as a stream failure, not a successful completion. */
    object ChannelExhausted : AnthropicEvent()

    /** `ping`, `content_block_start`, `content_block_stop`, and any unrecognized event type. */
    object Ignored : AnthropicEvent()
}

/**
 * Manual, line-based SSE reader over a live [ByteReadChannel] — Design § 3.4. Pull-based
 * (`next()`) rather than a [kotlinx.coroutines.flow.Flow] so [AnthropicAiProvider] can drive it in
 * two phases (read-to-first-token, then hand the same source to the caller's streaming callback)
 * without the source outliving the response body it reads from.
 *
 * Contract: reads lines until EOF. A blank line ends the current event. `:`-prefixed lines are
 * comments and are ignored. `event:`/`data:` lines accumulate into one event, emitted at the
 * blank-line boundary (or at EOF if the stream ends without a trailing blank line).
 */
class AnthropicSse(private val channel: ByteReadChannel) {

    /** Returns the next event, or [AnthropicEvent.ChannelExhausted] once the channel is exhausted
     * with nothing left buffered. */
    suspend fun next(): AnthropicEvent {
        var eventType: String? = null
        val dataLines = mutableListOf<String>()

        while (true) {
            val line = channel.readUTF8Line()
                ?: return if (eventType != null || dataLines.isNotEmpty()) {
                    parse(eventType, dataLines.joinToString("\n"))
                } else {
                    AnthropicEvent.ChannelExhausted
                }

            when {
                line.isEmpty() -> {
                    if (eventType != null || dataLines.isNotEmpty()) {
                        return parse(eventType, dataLines.joinToString("\n"))
                    }
                    // blank line with nothing accumulated yet — keep reading
                }
                line.startsWith(":") -> Unit // comment, ignored
                line.startsWith("event:") -> eventType = line.removePrefix("event:").trim()
                line.startsWith("data:") -> dataLines.add(line.removePrefix("data:").trim())
                else -> Unit // unrecognized SSE field, ignored
            }
        }
    }

    private fun parse(eventType: String?, data: String): AnthropicEvent {
        if (data.isBlank()) return typeOnlyEvent(eventType)

        val root: JsonElement = try {
            anthropicJson.parseToJsonElement(data)
        } catch (e: SerializationException) {
            if (eventType == "content_block_delta") {
                throw AnthropicStreamException("Malformed content_block_delta payload (not valid JSON).", e)
            }
            return AnthropicEvent.Ignored
        }

        // F7: `.jsonObject`/`.jsonPrimitive` throw IllegalArgumentException (not
        // SerializationException) when the element isn't actually a JSON object/primitive — e.g. a
        // syntactically-valid `data: [1,2,3]` on an event type that's supposed to be ignorable. That
        // must be tolerated the same way a JSON-parse failure is, except for `content_block_delta`,
        // which must still surface as a genuine stream failure.
        val jsonObject: JsonObject
        val type: String?
        try {
            jsonObject = root.jsonObject
            type = eventType ?: jsonObject["type"]?.jsonPrimitive?.content
        } catch (e: IllegalArgumentException) {
            if (eventType == "content_block_delta") {
                throw AnthropicStreamException("Malformed content_block_delta payload (not a JSON object).", e)
            }
            return AnthropicEvent.Ignored
        }

        return when (type) {
            "message_start" -> {
                val inputTokens = try {
                    anthropicJson.decodeFromJsonElement(AnthropicMessageStart.serializer(), jsonObject)
                        .message.usage?.inputTokens
                } catch (e: SerializationException) {
                    null
                }
                AnthropicEvent.InputUsage(inputTokens)
            }

            "content_block_delta" -> {
                val decoded = try {
                    anthropicJson.decodeFromJsonElement(AnthropicContentBlockDelta.serializer(), jsonObject)
                } catch (e: SerializationException) {
                    throw AnthropicStreamException("Malformed content_block_delta payload.", e)
                }
                if (decoded.delta.type == "text_delta" && decoded.delta.text != null) {
                    AnthropicEvent.TextDelta(decoded.delta.text)
                } else {
                    AnthropicEvent.Ignored
                }
            }

            "message_delta" -> {
                val outputTokens = try {
                    anthropicJson.decodeFromJsonElement(AnthropicMessageDelta.serializer(), jsonObject).usage?.outputTokens
                } catch (e: SerializationException) {
                    null
                }
                AnthropicEvent.OutputUsage(outputTokens)
            }

            "error" -> {
                try {
                    val decoded = anthropicJson.decodeFromJsonElement(AnthropicErrorEnvelope.serializer(), jsonObject)
                    AnthropicEvent.Error(decoded.error.type, decoded.error.message)
                } catch (e: SerializationException) {
                    AnthropicEvent.Error("unknown_error", "Unrecognized error payload from the AI provider.")
                }
            }

            "message_stop" -> AnthropicEvent.MessageStop

            else -> AnthropicEvent.Ignored // ping, content_block_start, content_block_stop, or unknown — forward compatible
        }
    }

    private fun typeOnlyEvent(eventType: String?): AnthropicEvent = when (eventType) {
        "message_stop" -> AnthropicEvent.MessageStop
        else -> AnthropicEvent.Ignored
    }
}
