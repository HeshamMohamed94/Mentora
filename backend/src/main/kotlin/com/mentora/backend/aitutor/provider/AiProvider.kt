package com.mentora.backend.aitutor.provider

import kotlinx.coroutines.flow.Flow

interface AiProvider {
    fun complete(request: AiCompletionRequest): Flow<AiToken>
}

data class AiCompletionRequest(
    val systemPrompt: String,
    val lessonContext: LessonContext?,
    val history: List<AiHistoryTurn>,
    val userMessage: String,
)

data class LessonContext(val title: String, val description: String)
data class AiHistoryTurn(val role: String, val content: String)
data class AiToken(val text: String)
