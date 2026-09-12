package com.mentora.shared.data.network.dto

import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseStatus
import kotlinx.serialization.Serializable

/**
 * Wire shapes for `GET /api/v1/courses` (list) and `GET /api/v1/courses/{id}` (detail) — mirror
 * `backend/src/main/kotlin/com/mentora/backend/courses/service/CourseService.kt:27-49`'s response
 * DTOs field-for-field, verified from source (not paraphrased). Re-verify against that file if
 * this ever needs to change.
 *
 * [CourseLevel]/[ContentLanguage]/[CourseStatus] are used directly as field types here (rather than
 * raw `String` + a separate mapping step) — the same convention `UserProfileDto` already uses for
 * `role: Role` (`data/network/dto/UserDto.kt`).
 */
@Serializable
data class PriceDisplayDto(val amount: Int, val currency: String)

@Serializable
data class LessonResourceDto(val label: String, val url: String)

@Serializable
data class LessonDto(
    val lessonId: String,
    val title: String,
    val description: String,
    val order: Int,
    val videoMediaId: String? = null,
    val resources: List<LessonResourceDto> = emptyList(),
)

@Serializable
data class SectionDto(
    val sectionId: String,
    val title: String,
    val order: Int,
    val lessons: List<LessonDto> = emptyList(),
)

/** Keyed by locale ("en"/"ar") in [CourseDto.translations] — see `CourseTranslationDto`'s
 * backend-side kdoc (`CourseService.kt:36-37`) and the domain `CourseTranslation`'s kdoc. */
@Serializable
data class CourseTranslationDto(val title: String, val description: String)

/** `GET /api/v1/courses`'s per-item shape — mirrors `CourseService.kt:39-43`'s `CourseSummary`. */
@Serializable
data class CourseSummaryDto(
    val id: String,
    val title: String,
    val description: String,
    val categoryId: String,
    val level: CourseLevel,
    val contentLanguage: ContentLanguage,
    val priceDisplay: PriceDisplayDto,
    val thumbnailMediaId: String? = null,
    val ratingSeed: Double,
    val instructorId: String,
    val instructorName: String,
)

/** `GET /api/v1/courses/{id}`'s full shape — mirrors `CourseService.kt:44-49`'s `CourseResponse`. */
@Serializable
data class CourseDto(
    val id: String,
    val title: String,
    val description: String,
    val categoryId: String,
    val level: CourseLevel,
    val contentLanguage: ContentLanguage,
    val priceDisplay: PriceDisplayDto,
    val thumbnailMediaId: String? = null,
    val status: CourseStatus,
    val ratingSeed: Double,
    val instructorId: String,
    val instructorName: String,
    val sections: List<SectionDto> = emptyList(),
    val translations: Map<String, CourseTranslationDto> = emptyMap(),
)
