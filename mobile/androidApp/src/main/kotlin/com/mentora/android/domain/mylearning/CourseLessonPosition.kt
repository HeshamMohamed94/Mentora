package com.mentora.android.domain.mylearning

import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.Lesson

/**
 * T12 — "Lesson N of M" (`mobile-home.json` line 2046 / `mobile-my-learning.json` line 2170's exact
 * showcase copy) plus, for Home's Continue Learning module specifically, the actual lesson TITLE the
 * showcase's own card headlines with (`mobile-home.json` line 2045, "Grouping and aggregating" — a
 * lesson title, not the course title). Neither [Course] nor [CourseProgress] carries either
 * pre-computed — both are derived here from the same two fields web's own
 * `DashboardScreen.orderedLessons`/`upNextEntries` (`web/src/components/screens/dashboard-screen.tsx`)
 * already derives them from, kept as one small shared helper rather than reimplemented per call site.
 *
 * [currentLesson] is `null` only when [totalLessons] is `0` (a course with no lessons at all).
 */
data class CourseLessonPosition(val currentLessonNumber: Int, val totalLessons: Int, val currentLesson: Lesson?)

/** [Course.sections]/[com.mentora.shared.domain.model.Section.lessons] already arrive sorted by
 *  `order` from `shared` (that field's own kdoc), but this defensively re-sorts anyway — the same
 *  precedent `CatalogRepositoryImpl`'s own mapping already established for the identical data. */
private fun orderedLessons(course: Course): List<Lesson> =
    course.sections.sortedBy { it.order }.flatMap { section -> section.lessons.sortedBy { it.order } }

/**
 * Resolves "currently on lesson N of M" for [course]/[progress]:
 * - If [CourseProgress.currentLessonId] matches a real lesson in the curriculum, that lesson's
 *   1-based position is "N" (the showcase's own meaning — "Lesson 15 of 24" is the lesson the student
 *   is ON, not a completed-count).
 * - Otherwise (a fresh enrollment that has never reported a position, or a stale/removed lesson id),
 *   falls back to `completedLessonIds.size` (coerced within bounds) so a student who has completed
 *   some lessons but has no live "current" position still reads a plausible position rather than
 *   always "1".
 * - `totalLessons == 0` (a course with no lessons at all, never expected at seed-data scale but not
 *   impossible) resolves `currentLessonNumber` to `0` too, rather than a nonsensical "1 of 0".
 */
fun resolveLessonPosition(course: Course, progress: CourseProgress): CourseLessonPosition {
    val lessons = orderedLessons(course)
    val total = lessons.size
    if (total == 0) return CourseLessonPosition(currentLessonNumber = 0, totalLessons = 0, currentLesson = null)

    val currentIndex = lessons.indexOfFirst { it.lessonId == progress.currentLessonId }
    val resolvedIndex = if (currentIndex >= 0) currentIndex else progress.completedLessonIds.size.coerceIn(0, total - 1)
    return CourseLessonPosition(
        currentLessonNumber = resolvedIndex + 1,
        totalLessons = total,
        currentLesson = lessons[resolvedIndex],
    )
}
