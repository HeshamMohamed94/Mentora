package com.mentora.backend.aitutor.provider

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class StubAiProvider : AiProvider {
    override fun complete(request: AiCompletionRequest): Flow<AiToken> = flow {
        PLACEHOLDER.split(" ").forEachIndexed { index, word ->
            if (index > 0) emit(AiToken(" "))
            emit(AiToken(word))
            delay(30)
        }
    }

    companion object {
        const val PLACEHOLDER =
            "This is a placeholder AI Tutor response. The real Anthropic Claude integration arrives in a later phase."
    }
}
