package com.mentora.backend.aitutor.provider

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StubAiProvider : AiProvider {
    override suspend fun complete(
        request: AiCompletionRequest,
        onStream: suspend (Flow<AiToken>) -> Unit,
    ): AiUsage {
        onStream(
            flow {
                PLACEHOLDER.split(" ").forEachIndexed { index, word ->
                    if (index > 0) emit(AiToken(" "))
                    emit(AiToken(word))
                    delay(30)
                }
            },
        )
        return AiUsage(null, null)
    }

    companion object {
        const val PLACEHOLDER =
            "This is a placeholder AI Tutor response. The real Anthropic Claude integration arrives in a later phase."
    }
}
