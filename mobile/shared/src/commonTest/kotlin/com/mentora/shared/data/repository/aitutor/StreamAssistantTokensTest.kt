package com.mentora.shared.data.repository.aitutor

import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises [streamAssistantTokens] directly against a synthetic in-memory
 * [io.ktor.utils.io.ByteChannel] — no HTTP/MockEngine involved — so the "mid-stream failure
 * preserves partial text" AC (`execution/PHASE_3_KMP_PLAN.md` Task 14) is verified independently of
 * how the real network response happens to chunk bytes.
 *
 * Verified empirically against this project's exact Ktor 3.0.1 `ByteChannel`: closing it with a
 * failure cause does NOT throw that cause out of a subsequent `readAvailable` call — any bytes
 * still sitting unread in the channel at the moment of `close(cause)` are discarded outright, and
 * the next `readAvailable` simply returns `-1` (indistinguishable from a clean EOF) with the cause
 * recorded on `closedCause` instead (see [streamAssistantTokens]'s own kdoc for why it checks
 * `closedCause`, not just a thrown exception). Consequently, these tests only observe "preserved"
 * partial text for bytes the collector had ALREADY pulled out of the channel before the close call
 * — modeled here with a genuinely concurrent producer/consumer (the collector started
 * [CoroutineStart.UNDISPATCHED] so it is actively suspended waiting on the channel before this test
 * ever writes/closes it), the same as a real network response where bytes already delivered and
 * read off the wire are never retroactively un-read just because the connection later drops.
 */
class StreamAssistantTokensTest {

    @Test
    fun `a cleanly closed channel emits its full content as Chunk events and nothing else`() = runTest {
        val channel = ByteChannel(autoFlush = true)
        val results = mutableListOf<AiStreamResult>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            channelFlow { streamAssistantTokens(channel) }.collect { results += it }
        }

        channel.writeStringUtf8("This is a placeholder AI Tutor response.")
        channel.close()
        collector.join()

        assertTrue(results.all { it is AiStreamResult.Chunk }, "expected only Chunk events, got: $results")
        val accumulated = results.filterIsInstance<AiStreamResult.Chunk>().joinToString("") { it.text }
        assertEquals("This is a placeholder AI Tutor response.", accumulated)
    }

    @Test
    fun `a channel closed with a failure cause after content already read preserves that partial text`() = runTest {
        val channel = ByteChannel(autoFlush = true)
        val results = mutableListOf<AiStreamResult>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            channelFlow { streamAssistantTokens(channel) }.collect { results += it }
        }

        // The collector above is UNDISPATCHED and starts with nothing yet written, so it is already
        // suspended inside `channel.readAvailable(...)` at this point — writing now wakes it, and
        // `yield()` lets it actually drain this text into `results` before the channel is failed.
        channel.writeStringUtf8("Partial answer before the ")
        yield()
        channel.close(RuntimeException("connection dropped"))
        collector.join()

        val accumulatedChunks = results.filterIsInstance<AiStreamResult.Chunk>().joinToString("") { it.text }
        assertEquals("Partial answer before the ", accumulatedChunks)

        val failure = results.filterIsInstance<AiStreamResult.StreamFailed>().single()
        assertEquals("Partial answer before the ", failure.partialText)
        assertEquals("connection dropped", failure.message)
    }

    @Test
    fun `a channel closed with a failure cause and zero prior content still surfaces StreamFailed with empty partial text`() = runTest {
        val channel = ByteChannel(autoFlush = true)
        channel.close(RuntimeException("never started"))

        val collected = mutableListOf<AiStreamResult>()
        channelFlow { streamAssistantTokens(channel) }.collect { collected += it }

        assertEquals(emptyList(), collected.filterIsInstance<AiStreamResult.Chunk>())
        val failure = collected.filterIsInstance<AiStreamResult.StreamFailed>().single()
        assertEquals("", failure.partialText)
        assertEquals("never started", failure.message)
    }
}
