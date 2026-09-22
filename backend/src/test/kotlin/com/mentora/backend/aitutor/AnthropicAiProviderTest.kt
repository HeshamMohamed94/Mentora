package com.mentora.backend.aitutor

import com.mentora.backend.aitutor.provider.AiCompletionRequest
import com.mentora.backend.aitutor.provider.AiHistoryTurn
import com.mentora.backend.aitutor.provider.AiToken
import com.mentora.backend.aitutor.provider.AnthropicAiProvider
import com.mentora.backend.aitutor.provider.AnthropicStreamException
import com.mentora.backend.common.ApiException
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * PHASE_6_SYSTEM_DESIGN.md § 20.1 — all 14 cases, zero real network calls (MockEngine only).
 * The "onStream never invoked" assertion is the mechanical proof of § 1.3: every pre-first-token
 * failure must be a clean ApiException thrown from `complete()` itself, before the caller's
 * streaming callback ever runs.
 */
class AnthropicAiProviderTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `request shape has exactly the five documented body fields and the right headers`() = runBlocking {
        var captured: HttpRequestData? = null
        val provider = provider(MockEngine { request ->
            captured = request
            respond(sse(happyPathEvents()), headers = sseHeaders())
        }, model = "claude-test-model")

        val tokens = mutableListOf<String>()
        provider.complete(requestOf(maxResponseTokens = 777)) { flow -> flow.collect { tokens.add(it.text) } }

        val req = requireNotNull(captured)
        assertTrue(req.url.toString().endsWith("/v1/messages"), "unexpected URL: ${req.url}")
        assertEquals("test-key-not-a-real-credential", req.headers[TEST_API_KEY_HEADER])
        assertEquals("2023-06-01", req.headers["anthropic-version"])

        val body = req.body as TextContent
        assertEquals(ContentType.Application.Json, body.contentType?.withoutParameters())
        val root = json.parseToJsonElement(body.text).jsonObject
        assertEquals(setOf("model", "max_tokens", "system", "messages", "stream"), root.keys)
        assertEquals(true, root.getValue("stream").jsonPrimitive.boolean)
        assertEquals("claude-test-model", root.getValue("model").jsonPrimitive.content)
        assertEquals(777, root.getValue("max_tokens").jsonPrimitive.int)
    }

    @Test
    fun `key appears only in the header, never in the URL or body`() = runBlocking {
        val secretKey = "sk-super-secret-not-real-0000000000"
        var captured: HttpRequestData? = null
        val provider = provider(
            MockEngine { request ->
                captured = request
                respond(sse(happyPathEvents()), headers = sseHeaders())
            },
            apiKey = secretKey,
        )

        provider.complete(requestOf()) { flow -> flow.collect { } }

        val req = requireNotNull(captured)
        assertEquals(secretKey, req.headers[TEST_API_KEY_HEADER])
        assertFalse(req.url.toString().contains(secretKey))
        val body = (req.body as TextContent).text
        assertFalse(body.contains(secretKey))

        // Codex second opinion, finding 3: checking only the URL/body isn't rigorous enough — a
        // future regression duplicating the key into e.g. Authorization or Cookie would still pass
        // that check. Scan every captured header and assert the secret appears exactly once, as the
        // sole value of x-api-key, nowhere else.
        val matchingHeaders = req.headers.entries()
            .flatMap { (name, values) -> values.map { name to it } }
            .filter { (_, value) -> value == secretKey }
        assertEquals(listOf(TEST_API_KEY_HEADER to secretKey), matchingHeaders)
    }

    @Test
    fun `happy path streams three tokens in order and returns usage`() = runBlocking {
        val provider = provider(MockEngine { respond(sse(happyPathEvents()), headers = sseHeaders()) })

        val tokens = mutableListOf<String>()
        val usage = provider.complete(requestOf()) { flow -> flow.collect { tokens.add(it.text) } }

        assertEquals(listOf("Hello", " there", "!"), tokens)
        assertEquals(25, usage.inputTokens)
        assertEquals(12, usage.outputTokens)
    }

    @Test
    fun `history maps 1-to-1 by role and the new user turn is appended last`() = runBlocking {
        var captured: HttpRequestData? = null
        val provider = provider(MockEngine { request ->
            captured = request
            respond(sse(happyPathEvents()), headers = sseHeaders())
        })

        val history = listOf(
            AiHistoryTurn("user", "What is Kotlin?"),
            AiHistoryTurn("assistant", "A JVM language."),
        )
        provider.complete(requestOf(history = history, userMessage = "Tell me more")) { flow -> flow.collect { } }

        val messages = json.parseToJsonElement((requireNotNull(captured).body as TextContent).text)
            .jsonObject.getValue("messages").jsonArray
        assertEquals(3, messages.size)
        assertEquals("user", messages[0].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("What is Kotlin?", messages[0].jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals("assistant", messages[1].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("A JVM language.", messages[1].jsonObject.getValue("content").jsonPrimitive.content)
        assertEquals("user", messages[2].jsonObject.getValue("role").jsonPrimitive.content)
        assertEquals("Tell me more", messages[2].jsonObject.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun `401 authentication_error maps to Internal, client message is generic, hint lives on cause`() = runBlocking {
        val provider = provider(MockEngine {
            respond(errorBody("authentication_error", "bad key"), status = HttpStatusCode.Unauthorized, headers = jsonHeaders())
        })

        var invoked = false
        val error = assertFailsWith<ApiException.Internal> {
            provider.complete(requestOf()) { invoked = true }
        }
        assertFalse(invoked)
        // F3: no diagnostic detail on the client-visible message — only on `cause`, for logging.
        assertEquals("Something went wrong.", error.message)
        assertFalse(error.message.contains("AI_PROVIDER_API_KEY"))
        assertTrue(error.cause?.message.orEmpty().contains("AI_PROVIDER_API_KEY"))
    }

    @Test
    fun `404 not_found_error maps to Internal, client message is generic, hint lives on cause`() = runBlocking {
        val provider = provider(MockEngine {
            respond(errorBody("not_found_error", "no such model"), status = HttpStatusCode.NotFound, headers = jsonHeaders())
        })

        var invoked = false
        val error = assertFailsWith<ApiException.Internal> {
            provider.complete(requestOf()) { invoked = true }
        }
        assertFalse(invoked)
        assertEquals("Something went wrong.", error.message)
        assertFalse(error.message.contains("AI_PROVIDER_MODEL"))
        assertTrue(error.cause?.message.orEmpty().contains("AI_PROVIDER_MODEL"))
    }

    @Test
    fun `429 rate_limit_error maps to 503 ServiceUnavailable, never 429, and never invokes onStream`() = runBlocking {
        val provider = provider(MockEngine {
            respond(errorBody("rate_limit_error", "slow down"), status = HttpStatusCode.TooManyRequests, headers = jsonHeaders())
        })

        var invoked = false
        assertFailsWith<ApiException.ServiceUnavailable> {
            provider.complete(requestOf()) { invoked = true }
        }
        assertFalse(invoked)
    }

    @Test
    fun `529 overloaded_error maps to ServiceUnavailable and never invokes onStream`() = runBlocking {
        val provider = provider(MockEngine {
            respond(
                errorBody("overloaded_error", "overloaded"),
                status = HttpStatusCode(529, "Overloaded"),
                headers = jsonHeaders(),
            )
        })

        var invoked = false
        assertFailsWith<ApiException.ServiceUnavailable> {
            provider.complete(requestOf()) { invoked = true }
        }
        assertFalse(invoked)
    }

    @Test
    fun `SSE error as the very first event maps to ServiceUnavailable and never invokes onStream`() = runBlocking {
        val body = sseEvent("error", """{"type":"error","error":{"type":"overloaded_error","message":"overloaded"}}""")
        val provider = provider(MockEngine { respond(body, headers = sseHeaders()) })

        var invoked = false
        assertFailsWith<ApiException.ServiceUnavailable> {
            provider.complete(requestOf()) { invoked = true }
        }
        assertFalse(invoked)
    }

    @Test
    fun `SSE error after two deltas invokes onStream, emits the two tokens, then throws raw (F8)`() = runBlocking {
        // F8 (Phase 8 A5): a mid-stream provider error must propagate raw (AnthropicStreamException),
        // not be mapped to ApiException.ServiceUnavailable — the response was already committed to the
        // caller, so AiTutorService must be able to bucket this as a distinct outcome=stream_failed
        // rather than conflating it with a genuine pre-stream outcome=provider_unavailable outage.
        val body = buildString {
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"A"}}"""))
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"B"}}"""))
            append(sseEvent("error", """{"type":"error","error":{"type":"overloaded_error","message":"mid-stream overload"}}"""))
        }
        val provider = provider(MockEngine { respond(body, headers = sseHeaders()) })

        var invoked = false
        val collected = mutableListOf<String>()
        var thrownDuringCollect: Throwable? = null

        val overall = runCatching {
            provider.complete(requestOf()) { flow ->
                invoked = true
                try {
                    flow.collect { collected.add(it.text) }
                } catch (e: Throwable) {
                    thrownDuringCollect = e
                    throw e
                }
            }
        }

        assertTrue(invoked)
        assertEquals(listOf("A", "B"), collected)
        assertTrue(thrownDuringCollect is AnthropicStreamException)
        assertTrue(overall.isFailure)
    }

    @Test
    fun `clean stream with zero text deltas maps to ServiceUnavailable and never invokes onStream`() = runBlocking {
        val body = buildString {
            append(sseEvent("message_start", """{"type":"message_start","message":{"usage":{"input_tokens":10}}}"""))
            append(sseEvent("message_stop", """{"type":"message_stop"}"""))
        }
        val provider = provider(MockEngine { respond(body, headers = sseHeaders()) })

        var invoked = false
        assertFailsWith<ApiException.ServiceUnavailable> {
            provider.complete(requestOf()) { invoked = true }
        }
        assertFalse(invoked)
    }

    @Test
    fun `non-JSON error body maps by status code without an unhandled SerializationException`() = runBlocking {
        val provider = provider(MockEngine {
            respond(
                "<html><body>Bad Gateway</body></html>",
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, "text/html"),
            )
        })

        var invoked = false
        assertFailsWith<ApiException.ServiceUnavailable> {
            provider.complete(requestOf()) { invoked = true }
        }
        assertFalse(invoked)
    }

    @Test
    fun `unknown and ping events interspersed with real deltas are ignored`() = runBlocking {
        val body = buildString {
            append(sseEvent("message_start", """{"type":"message_start","message":{"usage":{"input_tokens":5}}}"""))
            append(sseEvent("ping", """{"type":"ping"}"""))
            append(sseEvent("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""))
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}"""))
            append(sseEvent("some_future_event_type", """{"type":"some_future_event_type","whatever":true}"""))
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"!"}}"""))
            append(sseEvent("content_block_stop", """{"type":"content_block_stop","index":0}"""))
            append(sseEvent("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":2}}"""))
            append(sseEvent("message_stop", """{"type":"message_stop"}"""))
        }
        val provider = provider(MockEngine { respond(body, headers = sseHeaders()) })

        val tokens = mutableListOf<String>()
        val usage = provider.complete(requestOf()) { flow -> flow.collect { tokens.add(it.text) } }

        assertEquals(listOf("Hi", "!"), tokens)
        assertEquals(5, usage.inputTokens)
        assertEquals(2, usage.outputTokens)
    }

    @Test
    fun `connection failure maps to ServiceUnavailable and never invokes onStream`() = runBlocking {
        val provider = provider(MockEngine { throw IOException("connection refused") })

        var invoked = false
        assertFailsWith<ApiException.ServiceUnavailable> {
            provider.complete(requestOf()) { invoked = true }
        }
        assertFalse(invoked)
    }

    @Test
    fun `a real CancellationException propagates unmodified instead of becoming ServiceUnavailable`() = runBlocking {
        // F4: simulates a client disconnect during the request — a real CancellationException must
        // never be swallowed/converted by the provider's broad connection-failure catch clause.
        val provider = provider(MockEngine { throw CancellationException("client disconnected") })

        var invoked = false
        assertFailsWith<CancellationException> {
            provider.complete(requestOf()) { invoked = true }
        }
        assertFalse(invoked)
    }

    @Test
    fun `clean channel EOF without a message_stop event mid-stream is a failure, not a success`() = runBlocking {
        // F2: two real deltas stream, then the channel just ends (no message_stop, no error event)
        // — this must NOT be treated as a successful completion, since the response may be truncated.
        // F8 (Phase 8 A5): and it must throw raw (AnthropicStreamException), not
        // ApiException.ServiceUnavailable, for the same outcome=stream_failed reasoning as the SSE
        // mid-stream error case above.
        val body = buildString {
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"partial"}}"""))
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" answer"}}"""))
        }
        val provider = provider(MockEngine { respond(sse(body), headers = sseHeaders()) })

        var invoked = false
        val collected = mutableListOf<String>()
        var thrownDuringCollect: Throwable? = null

        val overall = runCatching {
            provider.complete(requestOf()) { flow ->
                invoked = true
                try {
                    flow.collect { collected.add(it.text) }
                } catch (e: Throwable) {
                    thrownDuringCollect = e
                    throw e
                }
            }
        }

        assertTrue(invoked)
        assertEquals(listOf("partial", " answer"), collected)
        assertTrue(thrownDuringCollect is AnthropicStreamException)
        assertTrue(overall.isFailure)
    }

    @Test
    fun `a ping event with a syntactically valid but non-object payload is ignored, not a crash`() = runBlocking {
        // F7: `data: [1,2,3]` is valid JSON but not a JSON object — this must be silently ignored
        // for an ignorable event type like `ping`, not crash the whole request with an uncaught
        // IllegalArgumentException.
        val body = buildString {
            append(sseEvent("message_start", """{"type":"message_start","message":{"usage":{"input_tokens":5}}}"""))
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hi"}}"""))
            append(sseEvent("ping", "[1,2,3]"))
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"!"}}"""))
            append(sseEvent("message_stop", """{"type":"message_stop"}"""))
        }
        val provider = provider(MockEngine { respond(body, headers = sseHeaders()) })

        val tokens = mutableListOf<String>()
        val usage = provider.complete(requestOf()) { flow -> flow.collect { tokens.add(it.text) } }

        assertEquals(listOf("Hi", "!"), tokens)
        assertEquals(5, usage.inputTokens)
    }

    @Test
    fun `malformed content_block_delta before the first token maps to ServiceUnavailable`() = runBlocking {
        // F9: exercises AnthropicStreamException's pre-first-token path — a content_block_delta
        // missing its required `delta` field fails to decode.
        val body = sseEvent("content_block_delta", """{"type":"content_block_delta","index":0}""")
        val provider = provider(MockEngine { respond(body, headers = sseHeaders()) })

        var invoked = false
        assertFailsWith<ApiException.ServiceUnavailable> {
            provider.complete(requestOf()) { invoked = true }
        }
        assertFalse(invoked)
    }

    @Test
    fun `malformed content_block_delta mid-stream invokes onStream, emits the prior token, then throws`() = runBlocking {
        // F9: the mid-stream counterpart — the malformed event arrives after a real token, so it
        // must propagate raw as AnthropicStreamException rather than being converted to a clean 503.
        val body = buildString {
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"A"}}"""))
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0}"""))
        }
        val provider = provider(MockEngine { respond(body, headers = sseHeaders()) })

        var invoked = false
        val collected = mutableListOf<String>()
        var thrownDuringCollect: Throwable? = null

        val overall = runCatching {
            provider.complete(requestOf()) { flow ->
                invoked = true
                try {
                    flow.collect { collected.add(it.text) }
                } catch (e: Throwable) {
                    thrownDuringCollect = e
                    throw e
                }
            }
        }

        assertTrue(invoked)
        assertEquals(listOf("A"), collected)
        assertTrue(thrownDuringCollect is AnthropicStreamException)
        assertTrue(overall.isFailure)
    }

    @Test
    fun `an empty text delta does not count as the first token`() = runBlocking {
        // Codex second opinion, finding 2: an empty content_block_delta followed immediately by
        // message_stop must not be reported as a successful completion with zero real text — it
        // must be treated the same as "clean stream with zero (real) text deltas" (503).
        val body = buildString {
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":""}}"""))
            append(sseEvent("message_stop", """{"type":"message_stop"}"""))
        }
        val provider = provider(MockEngine { respond(body, headers = sseHeaders()) })

        var invoked = false
        assertFailsWith<ApiException.ServiceUnavailable> {
            provider.complete(requestOf()) { invoked = true }
        }
        assertFalse(invoked)
    }

    @Test
    fun `an empty text delta is skipped and a later real delta becomes the first token`() = runBlocking {
        val body = buildString {
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":""}}"""))
            append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Real text"}}"""))
            append(sseEvent("message_stop", """{"type":"message_stop"}"""))
        }
        val provider = provider(MockEngine { respond(body, headers = sseHeaders()) })

        val tokens = mutableListOf<String>()
        provider.complete(requestOf()) { flow -> flow.collect { tokens.add(it.text) } }

        assertEquals(listOf("Real text"), tokens)
    }

    // Codex second opinion, finding 1: mapHttpError's own suspending body read (readRemaining) has
    // the same cancellation-swallowing risk F4 already fixed in complete()'s outer catch chain, one
    // suspension point earlier -- fixed by catching CancellationException specifically and
    // rethrowing it before the generic recovery catch, mirroring F4's already-proven-correct
    // pattern exactly. Not given its own MockEngine test: a `ByteChannel` cancelled ahead of time
    // (`.cancel(cause)`) reads back as an empty body rather than propagating the cause through
    // `readRemaining` (cancellation-during-an-in-flight-suspension isn't reliably reproducible
    // through MockEngine's synchronous, already-buffered channels) -- confirmed empirically before
    // deciding this was disproportionate effort for one narrow edge case. The fix is verified by
    // code inspection and by direct analogy to F4's already-tested case, not by a dedicated test.

    @Test
    fun `CRLF line endings are handled identically to bare LF`() = runBlocking {
        // F9: the implementation plan explicitly calls for \r\n SSE framing coverage.
        val provider = provider(MockEngine { respond(sse(happyPathEvents().replace("\n", "\r\n")), headers = sseHeaders()) })

        val tokens = mutableListOf<String>()
        val usage = provider.complete(requestOf()) { flow -> flow.collect { tokens.add(it.text) } }

        assertEquals(listOf("Hello", " there", "!"), tokens)
        assertEquals(25, usage.inputTokens)
        assertEquals(12, usage.outputTokens)
    }

    // ---- fixtures ----

    private fun provider(engine: MockEngine, apiKey: String = "test-key-not-a-real-credential", model: String = "test-model") =
        AnthropicAiProvider(
            httpClient = HttpClient(engine) {
                expectSuccess = false
                install(HttpTimeout) {
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 30_000
                    requestTimeoutMillis = 30_000
                }
            },
            apiKey = apiKey,
            model = model,
        )

    // F1: AiCompletionRequest.history is now the complete turn sequence (history + the new user
    // message) — this fixture folds `userMessage` into `history` as the trailing turn so every
    // existing caller below keeps behaving exactly as before.
    private fun requestOf(
        history: List<AiHistoryTurn> = emptyList(),
        userMessage: String = "Hello",
        maxResponseTokens: Int = 512,
    ) = AiCompletionRequest(
        systemPrompt = "You are Mentora's AI Tutor.",
        history = history + AiHistoryTurn("user", userMessage),
        maxResponseTokens = maxResponseTokens,
    )

    private fun sseEvent(type: String, data: String): String = "event: $type\ndata: $data\n\n"

    private fun happyPathEvents(): String = buildString {
        append(sseEvent("message_start", """{"type":"message_start","message":{"usage":{"input_tokens":25}}}"""))
        append(sseEvent("content_block_start", """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}"""))
        append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Hello"}}"""))
        append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":" there"}}"""))
        append(sseEvent("content_block_delta", """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"!"}}"""))
        append(sseEvent("content_block_stop", """{"type":"content_block_stop","index":0}"""))
        append(sseEvent("message_delta", """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":12}}"""))
        append(sseEvent("message_stop", """{"type":"message_stop"}"""))
    }

    private fun errorBody(type: String, message: String): String =
        """{"type":"error","error":{"type":"$type","message":"$message"}}"""

    private fun sse(body: String) = ByteReadChannel(body)

    private fun sseHeaders() = headersOf(HttpHeaders.ContentType, "text/event-stream")
    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    private companion object {
        const val TEST_API_KEY_HEADER = "x-api-key"
    }
}
