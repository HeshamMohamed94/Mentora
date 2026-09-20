package com.mentora.backend.aitutor.provider

import kotlinx.coroutines.flow.Flow

/**
 * Opens a completion stream and hands it to [onStream]. Any failure knowable before the first
 * assistant character exists — connection, timeout, non-2xx, upstream error event, empty
 * completion — throws an ApiException from THIS function, before [onStream] is invoked, so the
 * caller can still produce a normal JSON error response. A failure after [onStream] has been
 * invoked propagates out of the returned Flow (mid-stream failure).
 */
interface AiProvider {
    suspend fun complete(
        request: AiCompletionRequest,
        onStream: suspend (Flow<AiToken>) -> Unit,
    ): AiUsage
}

/**
 * [history] is the complete, already-normalized turn sequence to send — including the new user
 * turn (F1: normalization/merging of the new message against the trailing history turn happens
 * once, before this request is built, via `AiPromptBuilder.appendUserTurn`; this provider never
 * appends a turn of its own, so it can never re-introduce an adjacent same-role pair).
 */
data class AiCompletionRequest(
    val systemPrompt: String,
    val history: List<AiHistoryTurn>,
    val maxResponseTokens: Int,
)

data class AiHistoryTurn(val role: String, val content: String)
data class AiToken(val text: String)
data class AiUsage(val inputTokens: Int?, val outputTokens: Int?)
