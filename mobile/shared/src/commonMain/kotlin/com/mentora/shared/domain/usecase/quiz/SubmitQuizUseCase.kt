package com.mentora.shared.domain.usecase.quiz

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.quiz.QuizRepository
import com.mentora.shared.domain.model.Quiz
import com.mentora.shared.domain.model.QuizAnswer
import com.mentora.shared.domain.model.QuizAttemptResult

/**
 * `POST /api/v1/courses/{id}/quiz/attempts`.
 *
 * Takes the already-fetched [quiz] as a parameter rather than re-fetching it itself via
 * [QuizRepository] — by the time a caller is ready to submit, it has already rendered
 * [com.mentora.shared.domain.usecase.quiz.GetQuizUseCase]'s [Quiz] to the student and collected
 * [answers] against it, so re-fetching here would be a redundant network round trip for a shape the
 * caller already holds, and would only reintroduce the exact race this design avoids (the student
 * answering questions from one fetched [Quiz] while a second, possibly different, fetch is used to
 * validate against). This also keeps the "answered every question?" test free of any mock
 * quiz-fetch: a missing answer must fail with ZERO network calls, verified against the injected
 * [repository] alone.
 *
 * Before any network call, validates that [answers] contains exactly one entry per
 * [Quiz.questions] entry (by `questionId`) — if any question in [quiz] has no corresponding answer,
 * returns a local [ApiErrorCode.ValidationError] failure (`httpStatus = 0`, the same convention
 * `RegisterUseCase`'s local validation uses) instead of forwarding an incomplete submission to the
 * server. The backend itself would happily grade an incomplete submission (`QuizService.grade()`
 * never requires completeness — see the domain `AttemptBreakdown`'s kdoc), but `shared` never lets
 * a student accidentally submit while questions are still unanswered.
 *
 * `score`/`passed` on the returned [QuizAttemptResult] are always the server's own computation —
 * never computed locally here.
 */
class SubmitQuizUseCase(private val repository: QuizRepository) {
    suspend operator fun invoke(courseId: String, quiz: Quiz, answers: List<QuizAnswer>): ApiResult<QuizAttemptResult> {
        val answeredQuestionIds = answers.map { it.questionId }.toSet()
        val missingQuestionIds = quiz.questions.map { it.questionId }.filterNot { it in answeredQuestionIds }
        if (missingQuestionIds.isNotEmpty()) {
            return ApiResult.Failure(
                code = ApiErrorCode.ValidationError,
                message = "Every question must be answered before submitting.",
                fields = missingQuestionIds.associateWith { "ANSWER_REQUIRED" },
                httpStatus = 0,
            )
        }
        return repository.submitAttempt(courseId, answers)
    }
}
