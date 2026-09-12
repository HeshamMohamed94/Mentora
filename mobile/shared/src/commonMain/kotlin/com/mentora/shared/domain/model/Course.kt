package com.mentora.shared.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The backend's two valid `CourseDocument.status` values (`CourseService.kt`'s private
 * `DRAFT`/`PUBLISHED` constants).
 *
 * A [Course] fetched via `GET /api/v1/courses/{id}` can genuinely come back [Draft] for an
 * ALREADY-ENROLLED student even after an instructor has since unpublished it — `CourseService
 * .get()` explicitly keeps that read path open for enrolled students
 * (`ux/INSTRUCTOR_ADMIN_UX.md`'s "unpublishing keeps existing enrolled students' access intact",
 * D64), while a non-enrolled/guest caller instead gets a 404 `COURSE_NOT_FOUND` for the exact same
 * course id and never sees this status value at all. `shared` must never treat "the request
 * succeeded with a 200" as proof a course is [Published] — check this field.
 */
@Serializable
enum class CourseStatus {
    @SerialName("draft") Draft,
    @SerialName("published") Published,
}

/**
 * The full course detail returned by `GET /api/v1/courses/{id}` — mirrors the backend's
 * `CourseResponse` (`CourseService.kt:44-49`) field-for-field: every [CourseSummary] field plus
 * `status, sections, translations`.
 *
 * `GET /api/v1/courses/{id}` uses `authenticate("jwt-auth", optional = true)`
 * (`CourseRoutes.kt:43`) — an anonymous/guest request is allowed; see [CourseStatus]'s kdoc for
 * the one behavior difference an authenticated, already-enrolled caller gets.
 *
 * [translations] is keyed by locale string ("en"/"ar") — see [CourseTranslation]'s kdoc for why
 * the key stays a raw `String` rather than [ContentLanguage].
 *
 * [sections] arrives from the backend already sorted by [Section.order] (and each section's
 * [Lesson]s already sorted by [Lesson.order]); `CatalogRepositoryImpl`'s mapping defensively
 * re-sorts both anyway — see [Section]'s kdoc.
 */
data class Course(
    val id: String,
    val title: String,
    val description: String,
    val categoryId: String,
    val level: CourseLevel,
    val contentLanguage: ContentLanguage,
    val priceDisplay: PriceDisplay,
    val thumbnailMediaId: String?,
    val status: CourseStatus,
    val ratingSeed: Double,
    val instructorId: String,
    val instructorName: String,
    val sections: List<Section>,
    val translations: Map<String, CourseTranslation>,
)
