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
import kotlin.test.assertIs

private class FakeQuizRepositoryForLatestAttempt(private val latestResult: ApiResult<QuizAttemptResult>) : QuizRepository {
    var getLatestAttemptCallCount = 0
        private set

    override suspend fun getQuiz(courseId: String): ApiResult<Quiz> = throw NotImplementedError()
    override suspend fun submitAttempt(courseId: String, answers: List<QuizAnswer>): ApiResult<QuizAttemptResult> =
        throw NotImplementedError()

    override suspend fun getLatestAttempt(courseId: String): ApiResult<QuizAttemptResult> {
        getLatestAttemptCallCount++
        return latestResult
    }
}

private val sampleResult = QuizAttemptResult(
    score = 67, passed = false,
    breakdown = listOf(
        AttemptBreakdown("q1", "o1b", "o1b", true),
        AttemptBreakdown("q2", null, "o2b", false),
    ),
)

class GetLatestAttemptUseCaseTest {

    @Test
    fun `a found attempt maps to a Success wrapping LatestAttemptLookupResult Found`() = runTest {
        val repository = FakeQuizRepositoryForLatestAttempt(ApiResult.Success(sampleResult))
        val useCase = GetLatestAttemptUseCase(repository)

        val result = useCase("c1")

        require(result is ApiResult.Success)
        val lookup = assertIs<LatestAttemptLookupResult.Found>(result.data)
        assertEquals(sampleResult, lookup.result)
        assertEquals(1, repository.getLatestAttemptCallCount)
    }

    @Test
    fun `a 404 ATTEMPT_NOT_FOUND maps to a clean NoAttemptYet result, not a generic failure`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.AttemptNotFound, "No quiz attempt was found.", null, 404)
        val repository = FakeQuizRepositoryForLatestAttempt(failure)
        val useCase = GetLatestAttemptUseCase(repository)

        val result = useCase("c1")

        require(result is ApiResult.Success)
        assertEquals(LatestAttemptLookupResult.NoAttemptYet, result.data)
    }

    @Test
    fun `a non-enrolled 403 failure is forwarded unchanged, never folded into NoAttemptYet`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.ForbiddenNotEnrolled, "Not enrolled.", null, 403)
        val repository = FakeQuizRepositoryForLatestAttempt(failure)
        val useCase = GetLatestAttemptUseCase(repository)

        val result = useCase("c1")

        assertEquals(failure, result)
    }
}
