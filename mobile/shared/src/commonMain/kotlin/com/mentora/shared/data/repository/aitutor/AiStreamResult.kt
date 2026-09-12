package com.mentora.shared.data.repository.aitutor

import com.mentora.shared.data.network.ApiResult

/**
 * One event emitted by [AiTutorRepository.sendMessage]'s `Flow` — distinguishes the two failure
 * modes `execution/PHASE_3_KMP_PLAN.md` Task 14 requires callers be able to tell apart:
 *
 * - A **pre-stream** failure (403/404/429 — not enrolled, rate limited, etc.) arrives as a normal
 *   JSON error envelope BEFORE any streaming begins ([PreStreamFailure]). No partial assistant
 *   text exists yet in this case — nothing was ever appended to the conversation.
 * - A **mid-stream** failure (rare — e.g. the connection drops after some tokens already arrived)
 *   is modeled separately ([StreamFailed]) so a UI can still show/keep whatever text streamed in
 *   before the failure, rather than discarding it as if nothing had arrived.
 *
 * [Chunk] carries one piece of streamed assistant text — chunk boundaries are an implementation
 * detail of how bytes happened to arrive off the wire (see [Utf8ChunkDecoder]'s kdoc), never a
 * semantic unit (e.g. never assumed to be one whole word/sentence/token).
 */
sealed class AiStreamResult {
    data class Chunk(val text: String) : AiStreamResult()
    data class PreStreamFailure(val failure: ApiResult.Failure) : AiStreamResult()
    data class StreamFailed(val partialText: String, val message: String) : AiStreamResult()
}
