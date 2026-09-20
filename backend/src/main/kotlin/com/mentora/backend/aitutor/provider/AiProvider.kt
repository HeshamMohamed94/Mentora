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

data class AiCompletionRequest(
    val systemPrompt: String,
    val history: List<AiHistoryTurn>,
    val userMessage: String,
    val maxResponseTokens: Int,
)

data class AiHistoryTurn(val role: String, val content: String)
data class AiToken(val text: String)
data class AiUsage(val inputTokens: Int?, val outputTokens: Int?)
