package com.mentora.shared.domain.usecase.quiz

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.quiz.QuizRepository
import com.mentora.shared.domain.model.QuizAttemptResult

/**
 * The result of looking up a student's latest quiz attempt — distinguishes a genuine [Found]
 * attempt from [NoAttemptYet], the legitimate "this student has never submitted this quiz" state
 * (`404 ATTEMPT_NOT_FOUND`, `QuizService.kt:103-104`'s `repository.findLatest(...) ?: throw
 * ApiException.NotFound("ATTEMPT_NOT_FOUND", ...)`). Both are folded into [ApiResult.Success] by
 * [GetLatestAttemptUseCase] — mirrors [QuizLookupResult]'s same "typed sealed state inside Success,
 * real failures stay Failure" convention.
 */
sealed class LatestAttemptLookupResult {
    data class Found(val result: QuizAttemptResult) : LatestAttemptLookupResult()
    data object NoAttemptYet : LatestAttemptLookupResult()
}

/**
 * `GET /api/v1/courses/{id}/quiz/attempts/latest`. Translates the specific `404 ATTEMPT_NOT_FOUND`
 * failure into [LatestAttemptLookupResult.NoAttemptYet] wrapped in [ApiResult.Success] — a caller
 * branches on the returned [LatestAttemptLookupResult], never on error codes, to tell "no attempt
 * yet" apart from "an attempt exists." Every other failure is forwarded unchanged as an
 * [ApiResult.Failure].
 */
class GetLatestAttemptUseCase(private val repository: QuizRepository) {
    suspend operator fun invoke(courseId: String): ApiResult<LatestAttemptLookupResult> =
        when (val result = repository.getLatestAttempt(courseId)) {
            is ApiResult.Success -> ApiResult.Success(LatestAttemptLookupResult.Found(result.data))
            is ApiResult.Failure ->
                if (result.code == ApiErrorCode.AttemptNotFound) {
                    ApiResult.Success(LatestAttemptLookupResult.NoAttemptYet)
                } else {
                    result
                }
        }
}
