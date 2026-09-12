package com.mentora.shared.domain.usecase.aitutor

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.repository.aitutor.AiStreamResult
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SendAiTutorMessageUseCaseTest {

    @Test
    fun `a valid message is forwarded to the repository verbatim after trimming`() = runTest {
        val repository = FakeAiTutorRepository(sendMessageFlow = flowOf(AiStreamResult.Chunk("hi")))
        val useCase = SendAiTutorMessageUseCase(repository)

        val results = useCase("  Explain this lesson  ", "c1", "l1").toList()

        assertEquals(1, repository.sendMessageCallCount)
        assertEquals(Triple("Explain this lesson", "c1", "l1"), repository.lastSendMessageArgs)
        assertEquals(listOf(AiStreamResult.Chunk("hi")), results)
    }

    @Test
    fun `blank content fails local validation with zero network calls`() = runTest {
        val repository = FakeAiTutorRepository()
        val useCase = SendAiTutorMessageUseCase(repository)

        val results = useCase("   ", null, null).toList()

        assertEquals(0, repository.sendMessageCallCount)
        val failure = (results.single() as AiStreamResult.PreStreamFailure).failure
        assertEquals(ApiErrorCode.ValidationError, failure.code)
        assertEquals(0, failure.httpStatus)
        assertEquals(mapOf("content" to "REQUIRED"), failure.fields)
    }

    @Test
    fun `content over 4000 characters fails local validation with zero network calls`() = runTest {
        val repository = FakeAiTutorRepository()
        val useCase = SendAiTutorMessageUseCase(repository)

        val results = useCase("a".repeat(4001), null, null).toList()

        assertEquals(0, repository.sendMessageCallCount)
        val failure = (results.single() as AiStreamResult.PreStreamFailure).failure
        assertEquals(ApiErrorCode.ValidationError, failure.code)
        assertEquals(mapOf("content" to "TOO_LONG"), failure.fields)
    }

    @Test
    fun `content at exactly 4000 characters passes local validation`() = runTest {
        val repository = FakeAiTutorRepository()
        val useCase = SendAiTutorMessageUseCase(repository)

        useCase("a".repeat(4000), null, null).toList()

        assertEquals(1, repository.sendMessageCallCount)
    }

    @Test
    fun `courseId without lessonContextId fails local validation with zero network calls`() = runTest {
        val repository = FakeAiTutorRepository()
        val useCase = SendAiTutorMessageUseCase(repository)

        val results = useCase("Explain this", "c1", null).toList()

        assertEquals(0, repository.sendMessageCallCount)
        val failure = (results.single() as AiStreamResult.PreStreamFailure).failure
        assertEquals(ApiErrorCode.ValidationError, failure.code)
        assertEquals(mapOf("lessonContext" to "COURSE_AND_LESSON_REQUIRED_TOGETHER"), failure.fields)
    }

    @Test
    fun `lessonContextId without courseId fails local validation with zero network calls`() = runTest {
        val repository = FakeAiTutorRepository()
        val useCase = SendAiTutorMessageUseCase(repository)

        val results = useCase("Explain this", null, "l1").toList()

        assertEquals(0, repository.sendMessageCallCount)
        val failure = (results.single() as AiStreamResult.PreStreamFailure).failure
        assertTrue(failure.fields?.containsKey("lessonContext") == true)
    }

    @Test
    fun `both courseId and lessonContextId absent is valid`() = runTest {
        val repository = FakeAiTutorRepository()
        val useCase = SendAiTutorMessageUseCase(repository)

        useCase("Quiz me", null, null).toList()

        assertEquals(1, repository.sendMessageCallCount)
        assertEquals(Triple("Quiz me", null, null), repository.lastSendMessageArgs)
    }
}
