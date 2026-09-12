package com.mentora.shared.domain.model

/**
 * Mirrors the backend's `CourseSummary` (`CourseService.kt:39-43`) field-for-field:
 * `id, title, description, categoryId, level, contentLanguage, priceDisplay, thumbnailMediaId?,
 * ratingSeed, instructorId, instructorName`. Returned by `GET /api/v1/courses` (list/search),
 * paginated as `CursorPage<CourseSummary>`.
 *
 * [ratingSeed] is STATIC SEED DATA the backend hardcodes at course-creation time
 * (`CourseService.create()`: `ratingSeed = 4.5`) — it is never a computed/aggregated real rating
 * from actual student reviews. Never surface it as a "real rating" without that caveat.
 *
 * Deliberately has no `isEnrolled` field — that has never been implemented anywhere in the backend
 * (`execution/PHASE_3_KMP_PLAN.md` Task 7 "Must NOT").
 */
data class CourseSummary(
    val id: String,
    val title: String,
    val description: String,
    val categoryId: String,
    val level: CourseLevel,
    val contentLanguage: ContentLanguage,
    val priceDisplay: PriceDisplay,
    val thumbnailMediaId: String?,
    val ratingSeed: Double,
    val instructorId: String,
    val instructorName: String,
)
