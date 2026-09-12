package com.mentora.shared.domain.usecase.quiz

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.quiz.QuizRepository
import com.mentora.shared.domain.model.Quiz

/**
 * The result of looking up a course's quiz — distinguishes a genuine [Found] quiz from [NoQuiz],
 * the legitimate "this course simply has no quiz at all" state (`404 QUIZ_NOT_FOUND`,
 * `QuizService.kt:139-140`'s `requireQuiz()` — at least one seeded course has none, per
 * `execution/PHASE_3_KMP_PLAN.md` Task 10 AC #5). Both are folded into [ApiResult.Success] by
 * [GetQuizUseCase] — a caller branches on this sealed type, never on an error code, to tell "no
 * quiz" apart from "quiz exists." Any OTHER failure (403 not-enrolled, network error, 500, ...)
 * remains an ordinary [ApiResult.Failure], forwarded unchanged.
 */
sealed class QuizLookupResult {
    data class Found(val quiz: Quiz) : QuizLookupResult()
    data object NoQuiz : QuizLookupResult()
}

/**
 * `GET /api/v1/courses/{id}/quiz`. Translates the specific `404 QUIZ_NOT_FOUND` failure into
 * [QuizLookupResult.NoQuiz] wrapped in [ApiResult.Success] — a caller can branch on the returned
 * [QuizLookupResult] with a plain `when`, never inspecting error codes to tell "no quiz" apart from
 * "quiz exists." Every other failure (e.g. `403 FORBIDDEN_NOT_ENROLLED`) is forwarded unchanged as
 * an [ApiResult.Failure] — this use case never turns a real error into a fake success.
 */
class GetQuizUseCase(private val repository: QuizRepository) {
    suspend operator fun invoke(courseId: String): ApiResult<QuizLookupResult> =
        when (val result = repository.getQuiz(courseId)) {
            is ApiResult.Success -> ApiResult.Success(QuizLookupResult.Found(result.data))
            is ApiResult.Failure ->
                if (result.code == ApiErrorCode.QuizNotFound) {
                    ApiResult.Success(QuizLookupResult.NoQuiz)
                } else {
                    result
                }
        }
}
