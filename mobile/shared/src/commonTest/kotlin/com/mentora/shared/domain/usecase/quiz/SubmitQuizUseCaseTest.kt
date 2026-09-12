package com.mentora.shared.domain.usecase.quiz

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.quiz.QuizRepository
import com.mentora.shared.domain.model.AttemptBreakdown
import com.mentora.shared.domain.model.Quiz
import com.mentora.shared.domain.model.QuizAnswer
import com.mentora.shared.domain.model.QuizAttemptResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

private class RecordingQuizRepository(private val submitResult: ApiResult<QuizAttemptResult>) : QuizRepository {
    var submitAttemptCallCount = 0
        private set
    var lastSubmittedAnswers: List<QuizAnswer>? = null
        private set

    override suspend fun getQuiz(courseId: String): ApiResult<Quiz> = throw NotImplementedError()

    override suspend fun submitAttempt(courseId: String, answers: List<QuizAnswer>): ApiResult<QuizAttemptResult> {
        submitAttemptCallCount++
        lastSubmittedAnswers = answers
        return submitResult
    }

    override suspend fun getLatestAttempt(courseId: String): ApiResult<QuizAttemptResult> = throw NotImplementedError()
}

private val sampleResult = QuizAttemptResult(
    score = 100, passed = true,
    breakdown = listOf(
        AttemptBreakdown("q1", "o1b", "o1b", true),
        AttemptBreakdown("q2", "o2b", "o2b", true),
        AttemptBreakdown("q3", "o3a", "o3a", true),
    ),
)

class SubmitQuizUseCaseTest {

    @Test
    fun `a fully-answered submission succeeds and forwards the exact answers to the repository`() = runTest {
        val repository = RecordingQuizRepository(ApiResult.Success(sampleResult))
        val useCase = SubmitQuizUseCase(repository)
        val quiz = sampleQuiz()
        val answers = listOf(QuizAnswer("q1", "o1b"), QuizAnswer("q2", "o2b"), QuizAnswer("q3", "o3a"))

        val result = useCase("c1", quiz, answers)

        assertEquals(ApiResult.Success(sampleResult), result)
        assertEquals(1, repository.submitAttemptCallCount)
        assertEquals(answers, repository.lastSubmittedAnswers)
        // score/passed are exactly the repository's (server's) values, never recomputed here
        require(result is ApiResult.Success)
        assertEquals(100, result.data.score)
        assertEquals(true, result.data.passed)
    }

    @Test
    fun `a submission missing an answer for one question fails locally with zero network calls`() = runTest {
        val repository = RecordingQuizRepository(ApiResult.Success(sampleResult))
        val useCase = SubmitQuizUseCase(repository)
        val quiz = sampleQuiz()
        // q3 has no answer
        val answers = listOf(QuizAnswer("q1", "o1b"), QuizAnswer("q2", "o2b"))

        val result = useCase("c1", quiz, answers)

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.ValidationError, result.code)
        assertEquals(0, result.httpStatus, "a local-only failure must never look like a real network round trip")
        assertEquals(mapOf("q3" to "ANSWER_REQUIRED"), result.fields)
        assertEquals(0, repository.submitAttemptCallCount)
    }

    @Test
    fun `an entirely empty answer list fails locally naming every question`() = runTest {
        val repository = RecordingQuizRepository(ApiResult.Success(sampleResult))
        val useCase = SubmitQuizUseCase(repository)
        val quiz = sampleQuiz()

        val result = useCase("c1", quiz, emptyList())

        require(result is ApiResult.Failure)
        assertEquals(ApiErrorCode.ValidationError, result.code)
        assertEquals(setOf("q1", "q2", "q3"), result.fields?.keys)
        assertEquals(0, repository.submitAttemptCallCount)
    }
}
