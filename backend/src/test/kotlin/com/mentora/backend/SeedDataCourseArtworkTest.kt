package com.mentora.backend

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Fast, DB-free structural checks on [COURSE_ARTWORK_SEEDS] — the seed step that upgrades each
 * targeted course's thumbnail from a fake placeholder to a real, topic-specific image (see
 * `ensureCourseArtwork`). These would fail immediately in CI if a future edit ever left a real
 * seed course without distinct artwork, or pointed at a missing/placeholder-sized image resource.
 */
class SeedDataCourseArtworkTest {
    @Test
    fun `every currently-published seed course has exactly one artwork upgrade target`() {
        val publishedCourseTitles = COURSES.filter { it.published }.map { it.title }.toSet()
        val upgradedCourseTitles = COURSE_ARTWORK_SEEDS.map { it.courseTitle }
        assertEquals(
            publishedCourseTitles, upgradedCourseTitles.toSet(),
            "Every published seed course must have a COURSE_ARTWORK_SEEDS entry, and every entry " +
                "must target a real published seed course — otherwise some course would keep " +
                "rendering the shared fallback motif instead of its own distinct artwork.",
        )
        assertEquals(
            upgradedCourseTitles.size, upgradedCourseTitles.toSet().size,
            "COURSE_ARTWORK_SEEDS has more than one entry for the same course.",
        )
    }

    @Test
    fun `no two artwork seeds point at the same image resource`() {
        val resources = COURSE_ARTWORK_SEEDS.map { it.imageResource }
        assertEquals(
            resources.size, resources.toSet().size,
            "Two courses share the same artwork image resource — they would render identical " +
                "artwork, defeating the point of per-course distinct identity.",
        )
    }

    @Test
    fun `every course artwork seed's bundled resource exists and is a real image, not a placeholder`() {
        COURSE_ARTWORK_SEEDS.forEach { seed ->
            val stream = assertNotNull(
                Thread.currentThread().contextClassLoader.getResourceAsStream(seed.imageResource),
                "Missing seed artwork resource on the classpath: ${seed.imageResource}",
            )
            val bytes = stream.use { it.readBytes() }
            // Every other seeded course's fake thumbnail placeholder is
            // `"seed-thumbnail-<title>".encodeToByteArray()` — a few dozen bytes at most. A real
            // encoded JPEG is orders of magnitude larger; this threshold catches a resource ever
            // being swapped back for a placeholder by mistake.
            assertTrue(
                bytes.size > 10_000,
                "Seed artwork resource '${seed.imageResource}' is only ${bytes.size} bytes — too " +
                    "small to be a real image (looks like a placeholder).",
            )
        }
    }
}
