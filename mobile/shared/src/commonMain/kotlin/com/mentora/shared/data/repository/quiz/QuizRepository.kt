package com.mentora.shared.data.repository.quiz

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.Quiz
import com.mentora.shared.domain.model.QuizAnswer
import com.mentora.shared.domain.model.QuizAttemptResult

/**
 * The only quiz network surface `domain/usecase/quiz` use cases are allowed to depend on — mirrors
 * `com.mentora.shared.data.repository.progress.ProgressRepository`'s "interface + Impl" pattern
 * (`execution/PHASE_3_KMP_PLAN.md` Task 10).
 *
 * Every method forwards the server's response/failure verbatim — no grading, scoring, or
 * pass/fail computation ever happens on this side. `404 QUIZ_NOT_FOUND`/`404 ATTEMPT_NOT_FOUND`
 * are returned here as ordinary [ApiResult.Failure]s (this interface does not itself decide they
 * are "legitimate states" — that translation is the corresponding use case's job, per
 * `com.mentora.shared.domain.usecase.quiz.GetQuizUseCase`/`GetLatestAttemptUseCase`'s kdoc).
 */
interface QuizRepository {
    /** `GET /api/v1/courses/{id}/quiz` → [Quiz]. A course with no quiz at all responds
     * `404 QUIZ_NOT_FOUND`; a non-enrolled caller responds `403 FORBIDDEN_NOT_ENROLLED`
     * (`QuizService.studentQuiz()`'s `enrollments.requireEnrollment(...)`) — both are ordinary,
     * typed [ApiResult.Failure]s. */
    suspend fun getQuiz(courseId: String): ApiResult<Quiz>

    /** `POST /api/v1/courses/{id}/quiz/attempts` with body `{answers: [{questionId,
     * selectedOptionId}]}` → [QuizAttemptResult], server-graded (`score`/`passed` computed
     * server-side only). */
    suspend fun submitAttempt(courseId: String, answers: List<QuizAnswer>): ApiResult<QuizAttemptResult>

    /** `GET /api/v1/courses/{id}/quiz/attempts/latest` → [QuizAttemptResult], or
     * `404 ATTEMPT_NOT_FOUND` when the caller has never submitted an attempt for this course's
     * quiz — an ordinary, typed [ApiResult.Failure] at this layer. */
    suspend fun getLatestAttempt(courseId: String): ApiResult<QuizAttemptResult>
}
