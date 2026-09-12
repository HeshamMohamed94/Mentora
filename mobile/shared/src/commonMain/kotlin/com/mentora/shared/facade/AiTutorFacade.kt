package com.mentora.shared.facade

import com.mentora.shared.domain.usecase.aitutor.GetAiConversationUseCase
import com.mentora.shared.domain.usecase.aitutor.SendAiTutorMessageUseCase
import org.koin.core.Koin

/** Task 14's AI Tutor domain — e.g. `sdk.aiTutor.sendMessage(content).collect { ... }`. */
class AiTutorFacade internal constructor(koin: Koin) {
    val getConversation: GetAiConversationUseCase = koin.get()
    val sendMessage: SendAiTutorMessageUseCase = koin.get()
}
