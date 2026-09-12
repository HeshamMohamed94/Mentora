package com.mentora.shared.domain.usecase.aitutor

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.AiConversation
import com.mentora.shared.domain.model.AiMessage
import com.mentora.shared.domain.model.AiMessageRole
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class GetAiConversationUseCaseTest {

    @Test
    fun `forwards the repository result verbatim`() = runTest {
        val conversation = AiConversation(
            conversationId = "conv1",
            messages = listOf(
                AiMessage("m1", AiMessageRole.User, "hi", null, Instant.parse("2026-09-12T10:00:00Z")),
            ),
            nextCursor = "next",
        )
        val repository = FakeAiTutorRepository(conversationResult = ApiResult.Success(conversation))
        val useCase = GetAiConversationUseCase(repository)

        val result = useCase(cursor = "abc", limit = 10)

        assertEquals(ApiResult.Success(conversation), result)
        assertEquals(1, repository.getConversationCallCount)
        assertEquals("abc" to 10, repository.lastGetConversationArgs)
    }
}
