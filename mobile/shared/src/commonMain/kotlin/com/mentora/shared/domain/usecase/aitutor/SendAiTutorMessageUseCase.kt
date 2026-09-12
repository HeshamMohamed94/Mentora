package com.mentora.shared.domain.usecase.aitutor

import com.mentora.shared.data.network.ApiErrorCode
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.aitutor.AiStreamResult
import com.mentora.shared.data.repository.aitutor.AiTutorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * `POST /api/v1/ai-tutor/conversation/messages`, streamed. Validates locally, BEFORE any network
 * call, mirroring `AiTutorService.validatedContent()` (`backend/.../aitutor/service/AiTutorService
 * .kt:72-82`, verified from source) so an obviously-invalid send never leaves the device:
 * - `content` (after trimming) must be non-blank and at most [MAX_CONTENT_LENGTH] (4000)
 *   characters — same limit the server enforces, same `"content" to "REQUIRED"/"TOO_LONG"` field
 *   shape the server's own `ApiException.Validation` uses, so a caller branching on
 *   [ApiResult.Failure.fields] sees the identical shape regardless of whether the failure was
 *   caught here or on the server.
 * - [courseId]/[lessonContextId] are a strict pair — both present or both absent, never exactly
 *   one — mirroring the server's `(courseId == null) != (lessonContextId == null)` check
 *   (`"lessonContext" to "COURSE_AND_LESSON_REQUIRED_TOGETHER"`).
 *
 * A local validation failure is surfaced as [AiStreamResult.PreStreamFailure] (a single-element
 * `Flow`, zero network calls) — the same shape a real server-side pre-stream rejection (403/404/
 * 429) would arrive as, so a caller does not need to special-case "was this rejected locally or by
 * the server."
 *
 * **Architecture boundary (Task 14):** this class depends on nothing but [AiTutorRepository] —
 * ZERO dependency on `com.mentora.shared.data.repository.quiz.QuizRepository` or any other
 * quiz-domain type. [com.mentora.shared.domain.model.AiQuickAction.QuizMe] is one of the five quick
 * actions a platform UI can offer, but tapping it only ever results in calling this class with an
 * ordinary text message — this use case never reaches into the quiz domain to fetch, validate, or
 * inspect anything quiz-shaped, and never creates a `QuizAttempt` from any AI path. Verified
 * structurally by `SendAiTutorMessageUseCaseArchitectureTest` (`androidUnitTest` — real JVM
 * reflection over this class's constructor/fields, deliberately not `commonTest` per Decision
 * D-B's "no JVM-only APIs" rule).
 */
class SendAiTutorMessageUseCase(private val repository: AiTutorRepository) {
    operator fun invoke(
        content: String,
        courseId: String? = null,
        lessonContextId: String? = null,
    ): Flow<AiStreamResult> {
        val trimmed = content.trim()
        val validationFailure = validate(trimmed, courseId, lessonContextId)
        if (validationFailure != null) return flowOf(AiStreamResult.PreStreamFailure(validationFailure))
        return repository.sendMessage(trimmed, courseId, lessonContextId)
    }

    private fun validate(content: String, courseId: String?, lessonContextId: String?): ApiResult.Failure? {
        if (content.isBlank()) return validationFailure(mapOf("content" to "REQUIRED"))
        if (content.length > MAX_CONTENT_LENGTH) return validationFailure(mapOf("content" to "TOO_LONG"))
        if ((courseId == null) != (lessonContextId == null)) {
            return validationFailure(mapOf("lessonContext" to "COURSE_AND_LESSON_REQUIRED_TOGETHER"))
        }
        return null
    }

    private fun validationFailure(fields: Map<String, String>): ApiResult.Failure = ApiResult.Failure(
        code = ApiErrorCode.ValidationError,
        message = "AI Tutor message failed local validation.",
        fields = fields,
        httpStatus = 0,
    )

    private companion object {
        const val MAX_CONTENT_LENGTH = 4_000
    }
}
