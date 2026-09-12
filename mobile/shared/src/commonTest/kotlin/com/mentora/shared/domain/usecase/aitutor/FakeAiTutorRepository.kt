package com.mentora.shared.domain.usecase.aitutor

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.aitutor.AiStreamResult
import com.mentora.shared.data.repository.aitutor.AiTutorRepository
import com.mentora.shared.domain.model.AiConversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** A minimal [AiTutorRepository] test double — records every call so a test can assert whether
 * the network was actually reached (critical for the "zero network calls on local validation
 * failure" AC), and returns a canned result/flow for each method. */
internal class FakeAiTutorRepository(
    private val conversationResult: ApiResult<AiConversation> = ApiResult.Success(AiConversation("conv1", emptyList(), null)),
    private val sendMessageFlow: Flow<AiStreamResult> = flowOf(AiStreamResult.Chunk("stubbed reply")),
) : AiTutorRepository {

    var getConversationCallCount = 0
        private set
    var lastGetConversationArgs: Pair<String?, Int?>? = null
        private set
    var sendMessageCallCount = 0
        private set
    var lastSendMessageArgs: Triple<String, String?, String?>? = null
        private set

    override suspend fun getConversation(cursor: String?, limit: Int?): ApiResult<AiConversation> {
        getConversationCallCount++
        lastGetConversationArgs = cursor to limit
        return conversationResult
    }

    override fun sendMessage(content: String, courseId: String?, lessonContextId: String?): Flow<AiStreamResult> {
        sendMessageCallCount++
        lastSendMessageArgs = Triple(content, courseId, lessonContextId)
        return sendMessageFlow
    }
}
