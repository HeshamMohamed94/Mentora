package com.mentora.shared.domain.usecase.progress

import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.LessonProgressTarget

/**
 * The SINGLE place `shared` computes "which lesson comes next" from curriculum order — depended on
 * by both [ResumeCourseUseCase] (resume target) and [CompleteLessonUseCase] (auto-advance target)
 * so Android and iOS can never diverge on this resolution, per
 * `execution/PHASE_3_KMP_PLAN.md` Task 9 AC #5 ("computed once in `shared`"). Neither use case
 * reimplements curriculum-order iteration itself — both call into this object only.
 *
 * `internal`: this is plumbing for the two use cases above, never part of the public façade a
 * Phase 4/5 caller resolves from Koin directly.
 *
 * Curriculum order is `(section.order, lesson.order)` ascending, defensively re-sorted here even
 * though [com.mentora.shared.data.repository.catalog.CatalogRepositoryImpl] already sorts on the
 * way in — the same "cheap insurance, never trust wire order blindly" convention
 * [com.mentora.shared.domain.model.Section]'s kdoc documents for Task 7.
 */
internal object CurriculumLessonResolver {

    /** The full ordered list of lesson ids across every section of [course], ascending by
     * `(section.order, lesson.order)`. */
    fun orderedLessonIds(course: Course): List<String> =
        course.sections
            .sortedBy { it.order }
            .flatMap { section -> section.lessons.sortedBy { it.order }.map { it.lessonId } }

    /**
     * Resolves where a student should resume a course, from SERVER-AUTHORITATIVE progress data
     * plus [course]'s curriculum order:
     * - if [currentLessonId] is set AND still exists somewhere in [course]'s current curriculum,
     *   resume there, carrying [currentPositionSeconds] along;
     * - otherwise (including the edge case where [currentLessonId] points at a lesson the
     *   curriculum no longer contains, e.g. it was restructured after progress existed) fall back
     *   to the FIRST lesson in curriculum order whose id is not in [completedLessonIds];
     * - if every lesson in the curriculum is already completed (or the curriculum is empty),
     *   [LessonProgressTarget.CourseFinished].
     *
     * A brand-new enrollment with zero progress ([currentLessonId] `null`, [completedLessonIds]
     * empty) falls straight into the "first incomplete lesson" branch, resolving to the very first
     * lesson overall.
     */
    fun resolveResumeTarget(
        course: Course,
        completedLessonIds: Set<String>,
        currentLessonId: String?,
        currentPositionSeconds: Int?,
    ): LessonProgressTarget {
        val ordered = orderedLessonIds(course)
        if (currentLessonId != null && currentLessonId in ordered) {
            return LessonProgressTarget.LessonTarget(currentLessonId, currentPositionSeconds)
        }
        val firstIncomplete = ordered.firstOrNull { it !in completedLessonIds }
        return firstIncomplete?.let { LessonProgressTarget.LessonTarget(it) } ?: LessonProgressTarget.CourseFinished
    }

    /**
     * Resolves the auto-advance target immediately after [completedLessonId] was just marked
     * complete: the next lesson in curriculum order (crossing a section boundary transparently —
     * the last lesson of one section advances to the first lesson of the next), or
     * [LessonProgressTarget.CourseFinished] if [completedLessonId] was the very last lesson of the
     * whole course, or if it can no longer be located in [course]'s curriculum at all.
     */
    fun resolveNextAfter(course: Course, completedLessonId: String): LessonProgressTarget {
        val ordered = orderedLessonIds(course)
        val index = ordered.indexOf(completedLessonId)
        val next = if (index == -1) null else ordered.getOrNull(index + 1)
        return next?.let { LessonProgressTarget.LessonTarget(it) } ?: LessonProgressTarget.CourseFinished
    }
}
