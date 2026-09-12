package com.mentora.shared.data.repository.aitutor

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.domain.model.AiConversation
import kotlinx.coroutines.flow.Flow

/**
 * The only AI Tutor network surface `domain/usecase/aitutor` use cases are allowed to depend on —
 * mirrors `com.mentora.shared.data.repository.quiz.QuizRepository`'s "interface + Impl" pattern
 * (`execution/PHASE_3_KMP_PLAN.md` Task 14).
 */
interface AiTutorRepository {
    /** `GET /api/v1/ai-tutor/conversation?cursor&limit` → [AiConversation] — see that type's
     * kdoc for why this is its own shape, not [com.mentora.shared.data.network.CursorPage]. */
    suspend fun getConversation(cursor: String? = null, limit: Int? = null): ApiResult<AiConversation>

    /**
     * `POST /api/v1/ai-tutor/conversation/messages` with body `{content, courseId?,
     * lessonContextId?}` → a raw chunked `text/plain` stream, per Decision D-E — bypasses
     * [com.mentora.shared.data.network.ApiClient] entirely (its `get`/`post`/etc. all assume a
     * JSON [com.mentora.shared.data.network.ApiSuccess] envelope on success, which this endpoint's
     * success response is not).
     *
     * [content]/[courseId]/[lessonContextId] are passed through verbatim — this interface performs
     * no validation of its own (that is
     * [com.mentora.shared.domain.usecase.aitutor.SendAiTutorMessageUseCase]'s job, run BEFORE this
     * is ever called, so an invalid call never reaches the network).
     *
     * Returns a cold `Flow` (not `suspend`) — collecting it is what actually issues the request;
     * see [AiStreamResult]'s kdoc for the emitted event shapes.
     */
    fun sendMessage(content: String, courseId: String? = null, lessonContextId: String? = null): Flow<AiStreamResult>
}
