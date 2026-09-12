package com.mentora.shared.domain.model

/**
 * Mirrors the backend's `LessonResponse` (`CourseService.kt:29-32`) field-for-field:
 * `lessonId, title, description, order, videoMediaId?, resources`.
 *
 * Deliberately has **no duration property**. No response DTO anywhere in the backend carries
 * lesson duration — `MediaDocument.durationSeconds` exists (`MediaService.kt:35-36`) but nothing
 * exposes it on a course/lesson read path. A future curriculum-sheet UI gets duration from the
 * player at runtime for the CURRENT lesson only, never from this model
 * (`execution/PHASE_3_KMP_PLAN.md` Task 7 / Decision C4). Do not add one.
 */
data class Lesson(
    val lessonId: String,
    val title: String,
    val description: String,
    val order: Int,
    val videoMediaId: String?,
    val resources: List<LessonResource>,
)
