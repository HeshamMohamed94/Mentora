package com.mentora.shared.domain.usecase.enrollment

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.repository.enrollment.EnrollmentRepository
import com.mentora.shared.domain.model.CheckoutPreview

/**
 * `GET /api/v1/courses/{id}/checkout`. A non-existent or unpublished course id surfaces as an
 * ordinary `ApiResult.Failure(ApiErrorCode.CourseNotFound)` — see
 * [EnrollmentRepository.getCheckoutPreview]'s kdoc.
 */
class GetCheckoutPreviewUseCase(private val repository: EnrollmentRepository) {
    suspend operator fun invoke(courseId: String): ApiResult<CheckoutPreview> = repository.getCheckoutPreview(courseId)
}
