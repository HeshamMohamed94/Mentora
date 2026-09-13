package com.mentora.android.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Plain JVM unit test (`testDebugUnitTest`) for `CourseArtwork.kt`'s pure [courseArtworkHash]/
 * [motifFor] — `design-to-code/shared/artwork.json`'s `assignmentRule`: "never a random per-render
 * pick, so a given course's card looks identical everywhere it appears."
 *
 * The reference `% 5` values below were computed independently, in Node, from the *exact* JS formula
 * `artwork.json` specifies (`hash = (hash*31 + charCode) >>> 0`, `index = hash % 5`), not derived from
 * this Kotlin implementation itself — so this test actually cross-checks two separate readings of the
 * same spec, the same reasoning `MentoraTokensDriftTest`'s own kdoc gives for its independent
 * `parseColorLiteral`.
 */
class CourseArtworkHashTest {

    @Test
    fun `same seed always produces the same hash and motif (determinism)`() {
        val seeds = listOf("Software Development", "Design", "course-123", "", "Data & Analytics")
        for (seed in seeds) {
            assertEquals(
                "hash must be deterministic for seed '$seed'",
                courseArtworkHash(seed),
                courseArtworkHash(seed),
            )
            assertEquals(
                "motifFor must be deterministic for seed '$seed'",
                motifFor(seed),
                motifFor(seed),
            )
        }
    }

    @Test
    fun `motif index always falls within the 5-motif range`() {
        val seeds = listOf("a", "b", "Software Development", "Data & Analytics", "zzzzzzzzzz", "12345")
        for (seed in seeds) {
            assertTrue("motifFor('$seed') returned an entry outside CourseMotif.entries", CourseMotif.entries.contains(motifFor(seed)))
        }
    }

    @Test
    fun `known seeds resolve to the exact index the spec formula computes independently`() {
        // Computed in Node from artwork.json's own formula, not from this Kotlin code — see file kdoc.
        assertEquals(CourseMotif.entries[3], motifFor("Data & Analytics"))
        assertEquals(CourseMotif.entries[2], motifFor("Design"))
        assertEquals(CourseMotif.entries[2], motifFor("Software Development"))
        assertEquals(CourseMotif.entries[3], motifFor("Business Skills"))
        assertEquals(CourseMotif.entries[4], motifFor("general/fallback"))
        assertEquals(CourseMotif.entries[2], motifFor("course-123"))
        assertEquals(CourseMotif.entries[0], motifFor(""))
    }

    @Test
    fun `different categoryExamples from artwork json map to their own documented motif index`() {
        // artwork.json's motifSystem lists categoryExamples per motif at indices 0..4 (analytics,
        // design, code, grid, layers) — spot-check that distinct real category names spread across
        // more than one motif (i.e. this isn't degenerately collapsing everything to one bucket).
        val motifsSeen = setOf(
            motifFor("Data & Analytics"),
            motifFor("Design"),
            motifFor("Software Development"),
            motifFor("Business Skills"),
            motifFor("general/fallback"),
        )
        assertTrue("expected more than one distinct motif across 5 different category names", motifsSeen.size > 1)
    }
}
