package com.mentora.shared.domain.model

/**
 * Mirrors the backend's `SectionResponse` (`CourseService.kt:33-35`) field-for-field:
 * `sectionId, title, order, lessons`.
 *
 * The backend already returns [lessons] sorted by [Lesson.order]
 * (`CourseService.kt`'s `Section.toResponse()`: `lessons.sortedBy { it.order }`) —
 * `CatalogRepositoryImpl`'s DTO->domain mapping additionally re-sorts both this list and its own
 * position within `Course.sections` defensively, as cheap insurance against a future backend
 * regression rather than trusting wire order blindly.
 */
data class Section(
    val sectionId: String,
    val title: String,
    val order: Int,
    val lessons: List<Lesson>,
)
