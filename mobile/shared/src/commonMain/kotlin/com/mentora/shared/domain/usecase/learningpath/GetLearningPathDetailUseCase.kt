package com.mentora.shared.domain.usecase.learningpath

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.learningpath.LearningPathRepository
import com.mentora.shared.domain.model.LearningPathDetail

/**
 * `GET /api/v1/learning-paths/{id}?language=`, optional-auth and guest-safe. Forwards [id] straight
 * through to [LearningPathRepository.getLearningPath] with zero validation/reformatting — a
 * nonexistent [id] is an ordinary 404 `LEARNING_PATH_NOT_FOUND` [ApiResult.Failure], not folded into
 * a fake success. See [LearningPathDetail]'s kdoc for the guest-vs-authenticated
 * `progressPercent`/`isFollowing` behavior.
 */
class GetLearningPathDetailUseCase(private val repository: LearningPathRepository) {
    suspend operator fun invoke(id: String): ApiResult<LearningPathDetail> = repository.getLearningPath(id)
}
