package com.mentora.shared.domain.model

/**
 * A single embedded course reference inside [LearningPathDetail.courses] — mirrors the backend's
 * `LearningPathCourse` (`LearningPathService.kt:16-18`) field-for-field: `id, title, thumbnailMediaId`.
 *
 * Deliberately a narrower shape than [CourseSummary]/[Course] — the backend resolves the full course
 * (via `CourseService.get()`) internally per path entry but only projects `id`/`title`/`thumbnailMediaId`
 * onto the wire (`LearningPathService.get()`'s `.let { LearningPathCourse(it.id, it.title, it.thumbnailMediaId) }`).
 * `shared` mirrors that projection rather than widening it.
 *
 * Dangling/deleted course ids inside a path's stored `courseIds` are already omitted server-side —
 * `LearningPathService.get()` catches a per-course `COURSE_NOT_FOUND` and skips that entry
 * (`mapNotNull` over `path.courseIds`) — so [LearningPathDetail.courses] never needs a defensive
 * client-side filter for a "hole" left by a deleted course.
 */
data class LearningPathCourse(
    val id: String,
    val title: String,
    val thumbnailMediaId: String?,
)
