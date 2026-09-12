package com.mentora.shared.data.repository.quiz

import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.dto.AttemptAnswerRequestDto
import com.mentora.shared.data.network.dto.AttemptBreakdownDto
import com.mentora.shared.data.network.dto.AttemptResponseDto
import com.mentora.shared.data.network.dto.QuizDto
import com.mentora.shared.data.network.dto.QuizOptionDto
import com.mentora.shared.data.network.dto.QuizQuestionDto
import com.mentora.shared.data.network.dto.SubmitAttemptRequestDto
import com.mentora.shared.domain.model.AttemptBreakdown
import com.mentora.shared.domain.model.Quiz
import com.mentora.shared.domain.model.QuizAnswer
import com.mentora.shared.domain.model.QuizAttemptResult
import com.mentora.shared.domain.model.QuizOption
import com.mentora.shared.domain.model.QuizQuestion

/** The real [QuizRepository]. No `?language=` on any of these three endpoints — quiz prompt/option
 * text is not translated (not one of the 4 reads that carries `?language=`, per
 * `execution/PHASE_3_KMP_PLAN.md`'s D57/C3 list). */
internal class QuizRepositoryImpl(private val apiClient: ApiClient) : QuizRepository {

    override suspend fun getQuiz(courseId: String): ApiResult<Quiz> =
        when (val result = apiClient.get<QuizDto>("/api/v1/courses/$courseId/quiz")) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }

    override suspend fun submitAttempt(courseId: String, answers: List<QuizAnswer>): ApiResult<QuizAttemptResult> =
        when (
            val result = apiClient.post<SubmitAttemptRequestDto, AttemptResponseDto>(
                "/api/v1/courses/$courseId/quiz/attempts",
                SubmitAttemptRequestDto(answers.map { AttemptAnswerRequestDto(it.questionId, it.selectedOptionId) }),
            )
        ) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }

    override suspend fun getLatestAttempt(courseId: String): ApiResult<QuizAttemptResult> =
        when (val result = apiClient.get<AttemptResponseDto>("/api/v1/courses/$courseId/quiz/attempts/latest")) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }

    private fun QuizOptionDto.toDomain(): QuizOption = QuizOption(optionId, text)

    /** Sorted by [QuizOption]... options carry no `order` of their own — only sorted defensively at
     * the question level below, mirroring `CatalogRepositoryImpl`'s section/lesson convention. */
    private fun QuizQuestionDto.toDomain(): QuizQuestion =
        QuizQuestion(questionId, prompt, order, options.map { it.toDomain() })

    private fun QuizDto.toDomain(): Quiz = Quiz(questions.map { it.toDomain() }.sortedBy { it.order })

    private fun AttemptBreakdownDto.toDomain(): AttemptBreakdown =
        AttemptBreakdown(questionId, selectedOptionId, correctOptionId, isCorrect)

    private fun AttemptResponseDto.toDomain(): QuizAttemptResult =
        QuizAttemptResult(score, passed, breakdown.map { it.toDomain() })
}
