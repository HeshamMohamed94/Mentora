package com.mentora.shared.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Wire shapes for the 3 Student-facing quiz endpoints
 * (`backend/src/main/kotlin/com/mentora/backend/quiz/{routes/QuizRoutes.kt,
 * service/QuizService.kt}`), verified from source (not paraphrased). Mirrors ONLY the
 * `Student*`/`AttemptResponse` family — the instructor-editor-facing `Editor*`/`PutQuizRequest`
 * types are Web-only and deliberately absent from `shared` (see
 * `execution/PHASE_3_KMP_PLAN.md` Task 10 "Must NOT").
 *
 * [QuizOptionDto] mirrors the backend's `StudentQuizOption(optionId, text)` exactly —
 * `QuizService.kt:21` — and therefore has NO `isCorrect` field, structurally. The backend's
 * distinct `EditorQuizOption(optionId, text, isCorrect)` (`QuizService.kt:26`) is a different type
 * entirely and is never modeled here.
 */
@Serializable
data class QuizOptionDto(val optionId: String, val text: String)

/** `StudentQuizQuestion` (`QuizService.kt:22-24`): `questionId, prompt, order, options`. */
@Serializable
data class QuizQuestionDto(
    val questionId: String,
    val prompt: String,
    val order: Int,
    val options: List<QuizOptionDto> = emptyList(),
)

/** `GET /api/v1/courses/{id}/quiz`'s response — `StudentQuizResponse` (`QuizService.kt:25`).
 * `courseId` is intentionally dropped when mapping to the domain [com.mentora.shared.domain.model.Quiz]. */
@Serializable
data class QuizDto(val courseId: String, val questions: List<QuizQuestionDto> = emptyList())

/** A single answer in `POST /api/v1/courses/{id}/quiz/attempts`'s request body — mirrors
 * `AttemptAnswerRequest(questionId, selectedOptionId)` (`QuizService.kt:36`) exactly. Unlike the
 * stored `AnswerRecord`, this request-side `selectedOptionId` is non-null — every submitted answer
 * names a chosen option. */
@Serializable
data class AttemptAnswerRequestDto(val questionId: String, val selectedOptionId: String)

/** `POST /api/v1/courses/{id}/quiz/attempts`'s request body — mirrors `SubmitAttemptRequest(answers)`
 * (`QuizService.kt:37`) exactly: a single `answers` field. */
@Serializable
data class SubmitAttemptRequestDto(val answers: List<AttemptAnswerRequestDto>)

/** Mirrors `AttemptBreakdown(questionId, selectedOptionId, correctOptionId, isCorrect)`
 * (`QuizService.kt:38-40`) exactly. [selectedOptionId] is genuinely nullable on the wire — see the
 * domain `AttemptBreakdown`'s kdoc for why. */
@Serializable
data class AttemptBreakdownDto(
    val questionId: String,
    val selectedOptionId: String? = null,
    val correctOptionId: String,
    val isCorrect: Boolean,
)

/** The shared response shape for BOTH submit-attempt and latest-attempt — mirrors the backend's
 * single `AttemptResponse(score, passed, breakdown)` (`QuizService.kt:41`), which both endpoints
 * return verbatim. */
@Serializable
data class AttemptResponseDto(val score: Int, val passed: Boolean, val breakdown: List<AttemptBreakdownDto> = emptyList())
