package com.mentora.shared.domain.usecase.enrollment

import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.repository.enrollment.EnrollmentRepository
import com.mentora.shared.domain.model.Enrollment

/**
 * `GET /api/v1/enrollments`, paged. Scoped to the authenticated student automatically by the JWT
 * server-side — see [EnrollmentRepository.listEnrollments]'s kdoc for why there is no user-id
 * parameter here.
 */
class ListEnrollmentsUseCase(private val repository: EnrollmentRepository) {
    suspend operator fun invoke(cursor: String? = null, limit: Int? = null): ApiResult<CursorPage<Enrollment>> =
        repository.listEnrollments(cursor, limit)
}
