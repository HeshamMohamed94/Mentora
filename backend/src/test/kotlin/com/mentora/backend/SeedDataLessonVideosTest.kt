package com.mentora.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fast, DB-free structural checks on [LESSON_VIDEO_SEEDS] — the seed step that upgrades one
 * lesson per published seed course from a fake placeholder to a real, browser-playable video (see
 * `ensureLessonVideos`). These would fail immediately in CI if a future edit ever left a seed
 * course, or a newly-added one, without a working real-video target: a typo'd course/section
 * title, a missing bundled video resource, or a published course nobody wired up at all.
 */
class SeedDataLessonVideosTest {
    @Test
    fun `every currently-published seed course has exactly one real-video upgrade target`() {
        val publishedCourseTitles = COURSES.filter { it.published }.map { it.title }.toSet()
        val upgradedCourseTitles = LESSON_VIDEO_SEEDS.map { it.courseTitle }
        assertEquals(
            publishedCourseTitles, upgradedCourseTitles.toSet(),
            "Every published seed course must have a LESSON_VIDEO_SEEDS entry, and every entry " +
                "must target a real published seed course — otherwise some course's Course Player " +
                "would open into missing/broken lesson media.",
        )
        assertEquals(
            upgradedCourseTitles.size, upgradedCourseTitles.toSet().size,
            "LESSON_VIDEO_SEEDS has more than one entry for the same course.",
        )
    }

    @Test
    fun `every lesson video seed targets a real section that exists on its course`() {
        LESSON_VIDEO_SEEDS.forEach { seed ->
            val course = assertNotNull(
                COURSES.firstOrNull { it.title == seed.courseTitle },
                "LESSON_VIDEO_SEEDS references a course that does not exist in COURSES: '${seed.courseTitle}'",
            )
            assertTrue(
                course.sections.any { it.title == seed.sectionTitle },
                "LESSON_VIDEO_SEEDS references section '${seed.sectionTitle}' which does not exist " +
                    "on course '${seed.courseTitle}'",
            )
        }
    }

    @Test
    fun `every lesson video seed's bundled resource exists and is a real video, not a placeholder`() {
        LESSON_VIDEO_SEEDS.forEach { seed ->
            val stream = assertNotNull(
                Thread.currentThread().contextClassLoader.getResourceAsStream(seed.videoResource),
                "Missing seed video resource on the classpath: ${seed.videoResource}",
            )
            val bytes = stream.use { it.readBytes() }
            // Every other seeded lesson's fake placeholder is `"seed-video-<title>".encodeToByteArray()`
            // — a few dozen bytes at most. A real encoded MP4 is orders of magnitude larger; this
            // threshold catches a resource ever being swapped back for a placeholder by mistake.
            assertTrue(
                bytes.size > 10_000,
                "Seed video resource '${seed.videoResource}' is only ${bytes.size} bytes — too small " +
                    "to be a real video (looks like a placeholder).",
            )
            assertTrue(seed.durationSeconds > 0, "Seed video '${seed.videoResource}' has a non-positive duration")
        }
    }

    @Test
    fun `lesson titles and descriptions are non-blank and distinct from the generic placeholder titles`() {
        LESSON_VIDEO_SEEDS.forEach { seed ->
            assertTrue(seed.lessonTitle.isNotBlank(), "Blank lessonTitle for '${seed.courseTitle}'")
            assertTrue(seed.lessonDescription.isNotBlank(), "Blank lessonDescription for '${seed.courseTitle}'")
            assertTrue(
                seed.lessonTitle != "Core concepts" && seed.lessonTitle != "Applied workshop",
                "'${seed.courseTitle}' lesson still carries the generic placeholder title '${seed.lessonTitle}'",
            )
        }
    }
}
