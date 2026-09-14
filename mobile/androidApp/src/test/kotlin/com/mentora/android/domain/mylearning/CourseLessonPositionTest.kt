package com.mentora.android.domain.mylearning

import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseProgress
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.Lesson
import com.mentora.shared.domain.model.PriceDisplay
import com.mentora.shared.domain.model.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun lesson(id: String, order: Int) = Lesson(
    lessonId = id,
    title = "Lesson $id",
    description = "Description $id",
    order = order,
    videoMediaId = null,
    resources = emptyList(),
)

private fun courseWithLessons(vararg lessonsBySection: List<Lesson>) = Course(
    id = "course-1",
    title = "Course",
    description = "Description",
    categoryId = "cat-1",
    level = CourseLevel.Beginner,
    contentLanguage = ContentLanguage.English,
    priceDisplay = PriceDisplay(amount = 899, currency = "EGP"),
    thumbnailMediaId = null,
    status = CourseStatus.Published,
    ratingSeed = 4.5,
    instructorId = "instructor-1",
    instructorName = "Instructor",
    sections = lessonsBySection.mapIndexed { index, lessons ->
        Section(sectionId = "section-$index", title = "Section $index", order = index, lessons = lessons)
    },
    translations = emptyMap(),
)

private fun progress(currentLessonId: String?, completedLessonIds: List<String> = emptyList()) = CourseProgress(
    courseId = "course-1",
    completedLessonIds = completedLessonIds,
    currentLessonId = currentLessonId,
    currentPositionSeconds = null,
    quizPassed = null,
    completionPercent = 50,
    courseCompletedAt = null,
)

/**
 * T12 — [resolveLessonPosition]'s pure "Lesson N of M" derivation, as a plain JVM unit test. Covers
 * the three edge cases that function's own kdoc documents (a matched `currentLessonId`, a
 * missing/stale one falling back to completed-count, and a zero-lesson course) plus the multi-section
 * ordering/flattening this task's implementation relies on but the reviewer found untested.
 */
class CourseLessonPositionTest {

    @Test
    fun currentLessonId_matchesARealLesson_resolvesToThatLessonsPosition() {
        val course = courseWithLessons(listOf(lesson("l1", 0), lesson("l2", 1), lesson("l3", 2)))
        val position = resolveLessonPosition(course, progress(currentLessonId = "l2"))

        assertEquals(2, position.currentLessonNumber)
        assertEquals(3, position.totalLessons)
        assertEquals("l2", position.currentLesson?.lessonId)
    }

    @Test
    fun currentLessonId_null_fallsBackToCompletedCount() {
        val course = courseWithLessons(listOf(lesson("l1", 0), lesson("l2", 1), lesson("l3", 2)))
        val position = resolveLessonPosition(course, progress(currentLessonId = null, completedLessonIds = listOf("l1")))

        // completedLessonIds.size (1) -> index 1 -> lesson "l2", position 2.
        assertEquals(2, position.currentLessonNumber)
        assertEquals("l2", position.currentLesson?.lessonId)
    }

    @Test
    fun currentLessonId_staleOrRemoved_fallsBackToCompletedCount_neverCrashes() {
        val course = courseWithLessons(listOf(lesson("l1", 0), lesson("l2", 1)))
        val position = resolveLessonPosition(
            course,
            progress(currentLessonId = "removed-lesson-id", completedLessonIds = listOf("l1")),
        )

        assertEquals(2, position.currentLessonNumber)
        assertEquals("l2", position.currentLesson?.lessonId)
    }

    @Test
    fun completedCountAtOrPastTheEnd_coercesToTheLastLesson_neverIndexOutOfBounds() {
        val course = courseWithLessons(listOf(lesson("l1", 0), lesson("l2", 1)))
        val position = resolveLessonPosition(
            course,
            progress(currentLessonId = null, completedLessonIds = listOf("l1", "l2", "l3-not-real")),
        )

        assertEquals(2, position.currentLessonNumber)
        assertEquals("l2", position.currentLesson?.lessonId)
    }

    @Test
    fun zeroLessonCourse_resolvesToZeroOfZero_currentLessonNull() {
        val course = courseWithLessons(emptyList())
        val position = resolveLessonPosition(course, progress(currentLessonId = null))

        assertEquals(0, position.currentLessonNumber)
        assertEquals(0, position.totalLessons)
        assertNull(position.currentLesson)
    }

    @Test
    fun multipleSections_flattenInSectionThenLessonOrder_regardlessOfInputOrder() {
        // Section 1's lessons interleaved out of order, section 0 also out of order — the function's
        // own kdoc: sections/lessons are defensively re-sorted by `order`, not trusted as pre-sorted.
        val course = courseWithLessons(
            listOf(lesson("s0-l2", 1), lesson("s0-l1", 0)),
            listOf(lesson("s1-l2", 1), lesson("s1-l1", 0)),
        )
        val position = resolveLessonPosition(course, progress(currentLessonId = "s1-l1"))

        // Flattened order: s0-l1(1), s0-l2(2), s1-l1(3), s1-l2(4).
        assertEquals(3, position.currentLessonNumber)
        assertEquals(4, position.totalLessons)
        assertEquals("s1-l1", position.currentLesson?.lessonId)
    }
}
