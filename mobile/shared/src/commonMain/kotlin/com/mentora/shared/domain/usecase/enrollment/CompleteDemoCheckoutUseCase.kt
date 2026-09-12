package com.mentora.shared.domain.usecase.enrollment

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.enrollment.EnrollmentRepository
import com.mentora.shared.domain.model.EnrollmentCompletion

/**
 * `POST /api/v1/courses/{id}/checkout/complete`. A repeat call for a course the caller is already
 * enrolled in is an ordinary [ApiResult.Success] with `alreadyEnrolled = true`, never an error —
 * see [EnrollmentCompletion]'s kdoc. This use case adds no client-side retry of its own; a caller
 * must never wrap this in a retry loop (`execution/PHASE_3_KMP_PLAN.md` Task 8 "Must NOT").
 */
class CompleteDemoCheckoutUseCase(private val repository: EnrollmentRepository) {
    suspend operator fun invoke(courseId: String): ApiResult<EnrollmentCompletion> = repository.completeCheckout(courseId)
}
