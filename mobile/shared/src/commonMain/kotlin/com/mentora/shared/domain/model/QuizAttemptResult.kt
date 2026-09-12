package com.mentora.shared.domain.model

/**
 * The response shape for BOTH `POST /api/v1/courses/{id}/quiz/attempts` (submit) and
 * `GET /api/v1/courses/{id}/quiz/attempts/latest` — mirrors the backend's single `AttemptResponse`
 * type, which both endpoints share verbatim
 * (`backend/src/main/kotlin/com/mentora/backend/quiz/service/QuizService.kt:38-41`), verified from
 * source (not paraphrased):
 * ```
 * @Serializable data class AttemptBreakdown(
 *     val questionId: String, val selectedOptionId: String?, val correctOptionId: String, val isCorrect: Boolean,
 * )
 * @Serializable data class AttemptResponse(val score: Int, val passed: Boolean, val breakdown: List<AttemptBreakdown>)
 * ```
 *
 * [score]/[passed] are ALWAYS the server's own computation
 * (`QuizService.grade()`: `score = breakdown.count { it.isCorrect } * 100 / quiz.questions.size`,
 * `passed = score >= PASS_PERCENT` with the 70% threshold defined and applied server-side only,
 * `QuizService.kt:120-121,159`) — `shared` never recomputes either locally and never needs to know
 * the threshold number itself; both fields are forwarded verbatim from the wire response.
 *
 * `correctOptionId`/`isCorrect` appear ONLY on this post-submission type — never on the
 * pre-submission [QuizOption] (see [Quiz]'s kdoc) — this is the single place correctness is ever
 * revealed to a student.
 */
data class QuizAttemptResult(
    val score: Int,
    val passed: Boolean,
    val breakdown: List<AttemptBreakdown>,
)

/**
 * Mirrors `AttemptBreakdown` field-for-field: `questionId, selectedOptionId?, correctOptionId,
 * isCorrect`. [selectedOptionId] is genuinely nullable on the wire — the backend's
 * `AnswerRecord.selectedOptionId` (`quiz/repository/QuizRepository.kt:23`) is itself nullable, and
 * neither `QuizService.submit()` nor `.grade()` requires every question to have been answered
 * before scoring an incomplete submission — an unanswered question still appears in the breakdown
 * with `selectedOptionId = null` and `isCorrect = false`. `shared`'s own
 * `com.mentora.shared.domain.usecase.quiz.SubmitQuizUseCase` never sends such a submission (it
 * validates completeness locally first), but this shape still models what the wire genuinely
 * allows, e.g. for a `latest` attempt fetched after some other client submitted incompletely.
 */
data class AttemptBreakdown(
    val questionId: String,
    val selectedOptionId: String?,
    val correctOptionId: String,
    val isCorrect: Boolean,
)
