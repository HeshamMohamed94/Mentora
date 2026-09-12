package com.mentora.shared.domain.usecase.learningpath

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.learningpath.LearningPathRepository

/**
 * `DELETE /api/v1/learning-paths/{id}/follow` — Student role required server-side. Idempotent:
 * unfollowing a path that isn't followed is an ordinary [ApiResult.Success]`(false)`, never an
 * error. The CSRF header is attached globally by `HttpClientFactory` (Task 3) — no extra work here.
 */
class UnfollowLearningPathUseCase(private val repository: LearningPathRepository) {
    suspend operator fun invoke(id: String): ApiResult<Boolean> = repository.unfollow(id)
}
