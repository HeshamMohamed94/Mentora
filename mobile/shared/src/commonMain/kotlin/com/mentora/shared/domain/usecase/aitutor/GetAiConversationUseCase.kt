package com.mentora.shared.domain.usecase.aitutor

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.aitutor.AiTutorRepository
import com.mentora.shared.domain.model.AiConversation

/** `GET /api/v1/ai-tutor/conversation?cursor&limit` — a thin wrapper over
 * [AiTutorRepository.getConversation], mirroring every other paged-read use case's shape (e.g.
 * [com.mentora.shared.domain.usecase.catalog.SearchCoursesUseCase]). No validation of its own:
 * `cursor` is an opaque, server-issued token round-tripped verbatim, and `limit` is left to the
 * server's own clamping (`PageRequest.fromCall`'s `coerceIn(1, MAX_LIMIT)`, verified from source). */
class GetAiConversationUseCase(private val repository: AiTutorRepository) {
    suspend operator fun invoke(cursor: String? = null, limit: Int? = null): ApiResult<AiConversation> =
        repository.getConversation(cursor, limit)
}
