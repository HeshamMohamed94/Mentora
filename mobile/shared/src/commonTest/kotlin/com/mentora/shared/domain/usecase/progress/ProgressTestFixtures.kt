package com.mentora.shared.domain.usecase.progress

import com.mentora.shared.domain.model.ContentLanguage
import com.mentora.shared.domain.model.Course
import com.mentora.shared.domain.model.CourseLevel
import com.mentora.shared.domain.model.CourseStatus
import com.mentora.shared.domain.model.Lesson
import com.mentora.shared.domain.model.PriceDisplay
import com.mentora.shared.domain.model.Section

/**
 * A realistic 3-section/4-lesson-per-section (12 total) curriculum fixture, per
 * `execution/PHASE_3_KMP_PLAN.md` Task 9's "not a trivial 1-lesson case" test requirement — the
 * same section/lesson id convention `CatalogRepositoryImplTest`'s fixture uses for Task 7
 * (`s0/s1/s2`, `l000..l003`/`l100..l103`/`l200..l203`), so curriculum order across this whole
 * fixture is unambiguous: `l000, l001, l002, l003, l100, l101, l102, l103, l200, l201, l202, l203`.
 */
internal fun progressTestCourse(courseId: String = "c1"): Course {
    fun lesson(id: String, order: Int) =
        Lesson(lessonId = id, title = "Lesson $id", description = "desc", order = order, videoMediaId = null, resources = emptyList())

    fun section(id: String, order: Int, lessonIds: List<String>) =
        Section(sectionId = id, title = "Section $id", order = order, lessons = lessonIds.mapIndexed { i, lessonId -> lesson(lessonId, i) })

    return Course(
        id = courseId, title = "Kotlin Mastery", description = "Learn Kotlin end to end", categoryId = "cat1",
        level = CourseLevel.Intermediate, contentLanguage = ContentLanguage.English,
        priceDisplay = PriceDisplay(4999, "USD"), thumbnailMediaId = "m1", status = CourseStatus.Published,
        ratingSeed = 4.7, instructorId = "i1", instructorName = "Jane Doe",
        sections = listOf(
            section("s0", 0, listOf("l000", "l001", "l002", "l003")),
            section("s1", 1, listOf("l100", "l101", "l102", "l103")),
            section("s2", 2, listOf("l200", "l201", "l202", "l203")),
        ),
        translations = emptyMap(),
    )
}
