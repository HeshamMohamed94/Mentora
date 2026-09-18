package com.mentora.shared.domain.usecase.quiz

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.quiz.QuizRepository
import com.mentora.shared.domain.model.Quiz
import com.mentora.shared.domain.model.QuizAnswer
import com.mentora.shared.domain.model.QuizAttemptResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

private class FakeQuizRepositoryForGetQuiz(private val quizResult: ApiResult<Quiz>) : QuizRepository {
    var getQuizCallCount = 0
        private set

    override suspend fun getQuiz(courseId: String): ApiResult<Quiz> {
        getQuizCallCount++
        return quizResult
    }

    override suspend fun submitAttempt(courseId: String, answers: List<QuizAnswer>): ApiResult<QuizAttemptResult> =
        throw NotImplementedError()

    override suspend fun getLatestAttempt(courseId: String): ApiResult<QuizAttemptResult> = throw NotImplementedError()
}

class GetQuizUseCaseTest {

    @Test
    fun `a found quiz maps to a Success wrapping QuizLookupResult Found with all questions and options`() = runTest {
        val quiz = sampleQuiz()
        val repository = FakeQuizRepositoryForGetQuiz(ApiResult.Success(quiz))
        val useCase = GetQuizUseCase(repository)

        val result = useCase("c1")

        require(result is ApiResult.Success)
        val lookup = assertIs<QuizLookupResult.Found>(result.data)
        assertEquals(quiz, lookup.quiz)
        assertEquals(3, lookup.quiz.questions.size)
        assertEquals(1, repository.getQuizCallCount)
    }

    @Test
    fun `a 404 QUIZ_NOT_FOUND maps to a clean NoQuiz result not a generic failure`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.QuizNotFound, "The quiz was not found.", null, 404)
        val repository = FakeQuizRepositoryForGetQuiz(failure)
        val useCase = GetQuizUseCase(repository)

        val result = useCase("c1")

        require(result is ApiResult.Success)
        assertEquals(QuizLookupResult.NoQuiz, result.data)
    }

    @Test
    fun `a non-enrolled 403 failure is forwarded unchanged and never folded into NoQuiz`() = runTest {
        val failure = ApiResult.Failure(ApiErrorCode.ForbiddenNotEnrolled, "Not enrolled.", null, 403)
        val repository = FakeQuizRepositoryForGetQuiz(failure)
        val useCase = GetQuizUseCase(repository)

        val result = useCase("c1")

        assertEquals(failure, result)
    }
}
