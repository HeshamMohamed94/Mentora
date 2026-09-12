package com.mentora.shared.data.repository.enrollment

import com.mentora.shared.data.network.ApiClient
import com.mentora.shared.data.network.ApiResult
import com.mentora.shared.data.network.CursorPage
import com.mentora.shared.data.network.dto.CheckoutCourseDto
import com.mentora.shared.data.network.dto.CheckoutPreviewDto
import com.mentora.shared.data.network.dto.EmptyCheckoutCompleteRequestDto
import com.mentora.shared.data.network.dto.EnrollmentCompletionDto
import com.mentora.shared.data.network.dto.EnrollmentDto
import com.mentora.shared.data.network.dto.PriceDisplayDto
import com.mentora.shared.data.network.localeQueryParam
import com.mentora.shared.domain.model.CheckoutCourse
import com.mentora.shared.domain.model.CheckoutPreview
import com.mentora.shared.domain.model.Enrollment
import com.mentora.shared.domain.model.EnrollmentCompletion
import com.mentora.shared.domain.model.PriceDisplay
import com.mentora.shared.settings.PreferenceStore

/**
 * The real [EnrollmentRepository]. Reuses [localeQueryParam] for the checkout-preview's
 * `?language=` — the one shared mechanism `execution/PHASE_3_KMP_PLAN.md` Task 7 AC #5 /
 * `execution/DECISIONS_LOG.md` D73 asks this task to reuse rather than re-deriving the `"language"`
 * wire key or the locale-to-wire-value mapping itself.
 */
internal class EnrollmentRepositoryImpl(
    private val apiClient: ApiClient,
    private val preferenceStore: PreferenceStore,
) : EnrollmentRepository {

    override suspend fun getCheckoutPreview(courseId: String): ApiResult<CheckoutPreview> {
        val (key, value) = localeQueryParam(preferenceStore.locale.value)
        return when (
            val result = apiClient.get<CheckoutPreviewDto>("/api/v1/courses/$courseId/checkout", mapOf(key to value))
        ) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }
    }

    override suspend fun completeCheckout(courseId: String): ApiResult<EnrollmentCompletion> {
        val result = apiClient.post<EmptyCheckoutCompleteRequestDto, EnrollmentCompletionDto>(
            "/api/v1/courses/$courseId/checkout/complete",
            EmptyCheckoutCompleteRequestDto,
        )
        return when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.toDomain())
            is ApiResult.Failure -> result
        }
    }

    override suspend fun listEnrollments(cursor: String?, limit: Int?): ApiResult<CursorPage<Enrollment>> {
        val queryParams = buildMap {
            cursor?.let { put("cursor", it) }
            limit?.let { put("limit", it.toString()) }
        }
        return when (val result = apiClient.getPage<EnrollmentDto>("/api/v1/enrollments", queryParams)) {
            is ApiResult.Success -> ApiResult.Success(
                CursorPage(items = result.data.items.map { it.toDomain() }, nextCursor = result.data.nextCursor),
            )
            is ApiResult.Failure -> result
        }
    }

    private fun CheckoutCourseDto.toDomain(): CheckoutCourse = CheckoutCourse(id, title, thumbnailMediaId)

    private fun PriceDisplayDto.toDomain(): PriceDisplay = PriceDisplay(amount, currency)

    private fun CheckoutPreviewDto.toDomain(): CheckoutPreview =
        CheckoutPreview(course.toDomain(), instructorName, priceDisplay.toDomain())

    private fun EnrollmentDto.toDomain(): Enrollment = Enrollment(id, courseId, source, enrolledAt, status)

    private fun EnrollmentCompletionDto.toDomain(): EnrollmentCompletion =
        EnrollmentCompletion(enrollment.toDomain(), alreadyEnrolled)
}
