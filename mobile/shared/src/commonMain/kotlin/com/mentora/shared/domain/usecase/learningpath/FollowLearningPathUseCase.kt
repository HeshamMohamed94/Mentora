package com.mentora.shared.domain.usecase.learningpath

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.learningpath.LearningPathRepository

/**
 * `POST /api/v1/learning-paths/{id}/follow` — Student role required server-side. Idempotent:
 * following an already-followed path again is an ordinary [ApiResult.Success]`(true)`, never an
 * error. The CSRF header is attached globally by `HttpClientFactory` (Task 3) — no extra work here.
 */
class FollowLearningPathUseCase(private val repository: LearningPathRepository) {
    suspend operator fun invoke(id: String): ApiResult<Boolean> = repository.follow(id)
}
