package com.mentora.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fast, DB-free structural checks on [COURSES]' realistic multi-section curricula (the
 * pre-Phase-3 seed data expansion — see `ensureCurriculum`). These would fail immediately in CI
 * if a future edit ever left a published seed course with a too-thin or generic-placeholder
 * curriculum, a missing/broken-looking demo video resource, or a lesson nobody wrote real content
 * for.
 */
class SeedDataCurriculumTest {
    private val publishedCourses = COURSES.filter { it.published }

    @Test
    fun `every published course has 3 sections with 3 to 4 lessons each, 8 to 12 lessons total`() {
        publishedCourses.forEach { course ->
            assertEquals(3, course.sections.size, "'${course.title}' should have 3 sections")
            course.sections.forEach { section ->
                assertTrue(
                    section.lessons.size in 3..4,
                    "'${course.title}' section '${section.title}' has ${section.lessons.size} lessons; expected 3-4",
                )
            }
            val total = course.sections.sumOf { it.lessons.size }
            assertTrue(
                total in 8..12,
                "'${course.title}' has $total total lessons; expected 8-12",
            )
        }
    }

    @Test
    fun `no published course still carries the old generic placeholder curriculum`() {
        val genericLessonTitles = setOf("Core concepts", "Applied workshop")
        publishedCourses.forEach { course ->
            course.sections.forEach { section ->
                assertTrue(
                    section.title != "Reliability and Evolution",
                    "'${course.title}' still carries the placeholder-only section 'Reliability and Evolution'",
                )
                section.lessons.forEach { lesson ->
                    assertTrue(
                        lesson.title !in genericLessonTitles,
                        "'${course.title}' section '${section.title}' still carries the generic " +
                            "placeholder lesson title '${lesson.title}'",
                    )
                }
            }
        }
    }

    @Test
    fun `every lesson has a non-blank, distinct title and a real description`() {
        publishedCourses.forEach { course ->
            val allLessons = course.sections.flatMap { it.lessons }
            val titles = allLessons.map { it.title }
            assertEquals(
                titles.size, titles.toSet().size,
                "'${course.title}' has duplicate lesson titles across its curriculum",
            )
            allLessons.forEach { lesson ->
                assertTrue(lesson.title.isNotBlank(), "Blank lesson title in '${course.title}'")
                assertTrue(lesson.description.isNotBlank(), "Blank description for lesson '${lesson.title}' in '${course.title}'")
                assertTrue(
                    lesson.description.length > 20,
                    "Lesson '${lesson.title}' in '${course.title}' has a suspiciously short, " +
                        "likely copy-pasted description",
                )
            }
        }
    }

    @Test
    fun `every published course's demo video resource exists and is a real video, not a placeholder`() {
        publishedCourses.forEach { course ->
            val stream = assertNotNull(
                Thread.currentThread().contextClassLoader.getResourceAsStream(course.demoVideoResource),
                "Missing seed video resource on the classpath for '${course.title}': ${course.demoVideoResource}",
            )
            val bytes = stream.use { it.readBytes() }
            // A real encoded MP4 is orders of magnitude larger than any placeholder byte array;
            // this threshold catches a resource ever being swapped back for a placeholder.
            assertTrue(
                bytes.size > 10_000,
                "Seed video resource '${course.demoVideoResource}' is only ${bytes.size} bytes — too " +
                    "small to be a real video (looks like a placeholder).",
            )
            assertTrue(
                course.demoVideoDurationSeconds > 0,
                "Course '${course.title}' has a non-positive demoVideoDurationSeconds",
            )
        }
    }

    @Test
    fun `draft courses stay out of curriculum scope`() {
        COURSES.filterNot { it.published }.forEach { course ->
            assertTrue(course.sections.isEmpty(), "Draft course '${course.title}' unexpectedly has curriculum")
        }
    }
}
