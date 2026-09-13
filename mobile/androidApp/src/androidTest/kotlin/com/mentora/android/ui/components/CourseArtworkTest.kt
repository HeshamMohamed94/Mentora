package com.mentora.android.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T8 — `design-to-code/shared/artwork.json`'s `assignmentRule`: "never a random per-render pick, so
 * a given course's card looks identical everywhere it appears." [CourseArtworkHashTest] (plain JVM)
 * already covers the pure [motifFor] function directly; this instrumented test verifies the SAME
 * determinism via an actual rendered property (a pixel-for-pixel comparison of two independently
 * composed [CourseArtwork]s for the same seed), not just the function in isolation.
 */
@RunWith(AndroidJUnit4::class)
class CourseArtworkTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun sameSeedRendersPixelIdenticalArtworkTwice() {
        val seed = "Software Development"
        composeTestRule.setContent {
            MentoraTheme {
                Row {
                    CourseArtwork(
                        motif = motifFor(seed),
                        contentDescription = "artwork one",
                        modifier = Modifier.testTag("artwork-1").size(width = 160.dp, height = 90.dp),
                    )
                    CourseArtwork(
                        motif = motifFor(seed),
                        contentDescription = "artwork two",
                        modifier = Modifier.testTag("artwork-2").size(width = 160.dp, height = 90.dp),
                    )
                }
            }
        }

        val pixelMap1 = composeTestRule.onNodeWithTag("artwork-1").captureToImage().toPixelMap()
        val pixelMap2 = composeTestRule.onNodeWithTag("artwork-2").captureToImage().toPixelMap()

        assertEquals(pixelMap1.width, pixelMap2.width)
        assertEquals(pixelMap1.height, pixelMap2.height)

        // Sample a grid of points rather than every pixel — sufficient to catch a non-deterministic
        // gradient/motif choice while keeping the test fast.
        val xs = listOf(0, pixelMap1.width / 4, pixelMap1.width / 2, pixelMap1.width - 1)
        val ys = listOf(0, pixelMap1.height / 4, pixelMap1.height / 2, pixelMap1.height - 1)
        for (x in xs) {
            for (y in ys) {
                assertEquals("pixel ($x,$y) differed between two renders of the same seed", pixelMap1[x, y], pixelMap2[x, y])
            }
        }
    }

    @Test
    fun differentMotifsRenderVisiblyDifferentArtwork() {
        composeTestRule.setContent {
            MentoraTheme {
                Row {
                    CourseArtwork(motif = CourseMotif.Analytics, contentDescription = "a", modifier = Modifier.testTag("motif-analytics").size(width = 160.dp, height = 90.dp))
                    CourseArtwork(motif = CourseMotif.Layers, contentDescription = "b", modifier = Modifier.testTag("motif-layers").size(width = 160.dp, height = 90.dp))
                }
            }
        }

        val analyticsPixels = composeTestRule.onNodeWithTag("motif-analytics").captureToImage().toPixelMap()
        val layersPixels = composeTestRule.onNodeWithTag("motif-layers").captureToImage().toPixelMap()

        // Top-left corner base-gradient color differs between these two motifs' documented stops
        // (#241C5C vs #191A20) — confirms the 5-motif system actually varies visually, not just by name.
        val topLeftAnalytics = analyticsPixels[0, 0]
        val topLeftLayers = layersPixels[0, 0]
        val differs = kotlin.math.abs(topLeftAnalytics.red - topLeftLayers.red) > 0.02f ||
            kotlin.math.abs(topLeftAnalytics.green - topLeftLayers.green) > 0.02f ||
            kotlin.math.abs(topLeftAnalytics.blue - topLeftLayers.blue) > 0.02f
        assertTrue("expected the analytics and layers motifs to render visibly different base colors", differs)
    }

    @Test
    fun courseThumbnailWithoutUrlExposesTheAccessibleName() {
        composeTestRule.setContent {
            MentoraTheme {
                CourseThumbnail(
                    mediaId = null,
                    thumbnailUrl = null,
                    seed = "course-without-image",
                    categoryId = null,
                    contentDescription = "Building Reliable REST APIs thumbnail",
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Building Reliable REST APIs thumbnail").assertExists()
    }
}
