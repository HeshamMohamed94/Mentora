package com.mentora.shared.domain.model

/**
 * A downloadable/linked resource attached to one lesson (e.g. a slide deck or supplementary link)
 * — mirrors the backend's `ResourceDto` (`CourseService.kt:28`) field-for-field.
 */
data class LessonResource(
    val label: String,
    val url: String,
)
