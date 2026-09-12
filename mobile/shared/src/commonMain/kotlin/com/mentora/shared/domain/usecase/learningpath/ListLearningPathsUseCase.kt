package com.mentora.shared.domain.usecase.learningpath

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.learningpath.LearningPathRepository
import com.mentora.shared.domain.model.LearningPath

/**
 * `GET /api/v1/learning-paths`, plain unpaginated — guest-accessible, no auth state required. See
 * [LearningPathRepository.listLearningPaths]'s kdoc for why the result is a bare `List<LearningPath>`,
 * never a `CursorPage`.
 */
class ListLearningPathsUseCase(private val repository: LearningPathRepository) {
    suspend operator fun invoke(): ApiResult<List<LearningPath>> = repository.listLearningPaths()
}
