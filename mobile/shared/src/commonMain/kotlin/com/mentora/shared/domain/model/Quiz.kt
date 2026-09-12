package com.mentora.shared.domain.model

/**
 * `GET /api/v1/courses/{id}/quiz` response — mirrors the backend's STUDENT-FACING
 * `StudentQuizResponse`/`StudentQuizQuestion`/`StudentQuizOption`
 * (`backend/src/main/kotlin/com/mentora/backend/quiz/service/QuizService.kt:21-25`), verified from
 * source (not paraphrased):
 * ```
 * @Serializable data class StudentQuizOption(val optionId: String, val text: String)
 * @Serializable data class StudentQuizQuestion(
 *     val questionId: String, val prompt: String, val order: Int, val options: List<StudentQuizOption>,
 * )
 * @Serializable data class StudentQuizResponse(val courseId: String, val questions: List<StudentQuizQuestion>)
 * ```
 * This is a genuinely DISTINCT backend type from the instructor-editor-facing
 * `EditorQuizOption(optionId, text, isCorrect)` (`QuizService.kt:26`) — [QuizOption] mirrors
 * `StudentQuizOption` only, and therefore has NO `isCorrect` property at all, structurally (not a
 * nullable/always-null field). Correctness is revealed only post-submission, via
 * [AttemptBreakdown] — see that type's kdoc.
 *
 * [courseId] is dropped from the domain shape (the caller already supplies it to fetch this quiz
 * in the first place) — the same convention as other course-scoped reads in this module.
 *
 * A course with no quiz at all responds `404 QUIZ_NOT_FOUND`
 * (`QuizService.kt:139-140`'s `requireQuiz()`) — a legitimate, expected state (per
 * `execution/PHASE_3_KMP_PLAN.md` Task 10), not an error to hide. See
 * `com.mentora.shared.domain.usecase.quiz.GetQuizUseCase`/`QuizLookupResult` for how a caller
 * distinguishes "this course has no quiz" from a real failure.
 */
data class Quiz(val questions: List<QuizQuestion>)

/** Mirrors `StudentQuizQuestion` field-for-field: `questionId, prompt, order, options`. */
data class QuizQuestion(
    val questionId: String,
    val prompt: String,
    val order: Int,
    val options: List<QuizOption>,
)

/**
 * Mirrors `StudentQuizOption` field-for-field: `optionId, text`. Deliberately has NO `isCorrect`
 * property — see [Quiz]'s kdoc for the verified backend source proving this is a structurally
 * distinct type from the instructor-only `EditorQuizOption`, not just an omitted-at-runtime field
 * on a broader shape.
 */
data class QuizOption(val optionId: String, val text: String)

/**
 * A single answer to submit as part of `POST /api/v1/courses/{id}/quiz/attempts`'s request body —
 * mirrors the backend's `AttemptAnswerRequest(questionId, selectedOptionId)`
 * (`QuizService.kt:36`). Not part of the plan's explicit domain/model list, but modeled as a domain
 * type (rather than a bare `Pair<String, String>`) since it is a first-class concept a caller
 * assembles one-per-answered-question before calling
 * `com.mentora.shared.domain.usecase.quiz.SubmitQuizUseCase`.
 */
data class QuizAnswer(val questionId: String, val selectedOptionId: String)
