package com.mentora.shared.data.network.dto

import com.mentora.shared.domain.model.EnrollmentSource
import com.mentora.shared.domain.model.EnrollmentStatus
import kotlinx.serialization.Serializable

/**
 * Wire shapes for `backend/src/main/kotlin/com/mentora/backend/enrollment/{routes/EnrollmentRoutes.kt,
 * service/EnrollmentService.kt}`'s 3 endpoints, verified from source (not paraphrased). Reuses
 * [PriceDisplayDto] from `CourseDto.kt` — the checkout preview's `priceDisplay` field is the exact
 * same wire shape as a course's.
 */
@Serializable
data class CheckoutCourseDto(val id: String, val title: String, val thumbnailMediaId: String? = null)

/** `GET /courses/{id}/checkout`'s response — mirrors `EnrollmentService.kt:25-29`'s `CheckoutPreview`. */
@Serializable
data class CheckoutPreviewDto(
    val course: CheckoutCourseDto,
    val instructorName: String,
    val priceDisplay: PriceDisplayDto,
)

/**
 * Mirrors `EnrollmentService.kt:30-36`'s `EnrollmentResponse`. [source]/[status] deserialize
 * directly into the domain enums (the same convention `CourseDto`'s `level`/`contentLanguage`
 * already uses) — see [EnrollmentSource]/[EnrollmentStatus]'s kdoc for the exact (single) wire
 * value each currently ever carries.
 */
@Serializable
data class EnrollmentDto(
    val id: String,
    val courseId: String,
    val source: EnrollmentSource,
    val enrolledAt: String,
    val status: EnrollmentStatus,
)

/** `POST /courses/{id}/checkout/complete`'s response — mirrors `EnrollmentService.kt:37`'s
 * `EnrollmentCompletion`. */
@Serializable
data class EnrollmentCompletionDto(val enrollment: EnrollmentDto, val alreadyEnrolled: Boolean)

/**
 * `POST /courses/{id}/checkout/complete` reads no request body at all — its handler
 * (`EnrollmentRoutes.kt:27-32`) never calls `call.receive()`, it only reads the path parameter and
 * the CSRF header. This object exists solely to satisfy
 * [com.mentora.shared.data.network.ApiClient.post]'s generic `TBody` parameter; its generated
 * serializer encodes to `{}` (same convention as `EmptyResponseDto`, `data/network/dto/AuthDto.kt`),
 * and the backend never looks at it.
 */
@Serializable
object EmptyCheckoutCompleteRequestDto
