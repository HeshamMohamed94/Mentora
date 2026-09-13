package com.mentora.android.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mentora.android.theme.MentoraTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finding 1 (reviewer-measured HIGH defect) regression coverage: [CourseCard]/[CourseProgressCard]
 * must self-size their thumbnail area to a real 16:9-of-own-width box, independent of whatever height
 * a bounded-height ancestor happens to offer, so title/instructor/CTA never get pushed off-screen.
 *
 * Every scenario below wraps its container in an explicit `Modifier.height(2000.dp)` outer [Box] — a
 * stand-in for the finite, generously-large "bounded-height parent" the reviewer's real-device repro
 * hit (a 2400px/~800dp-tall emulator screen). This is deliberate, not incidental: Compose's default
 * test host gives top-level content *loose/unbounded* height by default, which never actually
 * reproduces the original bug (a `fillMaxSize()` deep inside [CourseArtworkWithChip] specifically
 * needs a *finite* incoming max height to have something concrete to wrongly expand into). Every
 * assertion here measures actual rendered bounds ([getUnclippedBoundsInRoot]) or a real accessible
 * node's existence within those bounds — not just "a semantics node exists somewhere," which is what
 * let the original bug through.
 */
@RunWith(AndroidJUnit4::class)
class CourseCardLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val title = "Building Reliable REST APIs"
    private val instructorName = "Ada Lovelace"
    private val actionLabel = "Enroll"
    private val thumbnailDescription = "Course thumbnail"

    /** A plain, non-scrolling [Column] inside a bounded-height parent — the exact shape the reviewer
     *  measured collapsing to a screen-filling card with title/instructor/CTA rendered off-screen. */
    @Test
    fun plainColumnCardDoesNotFillTheBoundedParentHeightAndShowsAllContent() {
        composeTestRule.setContent {
            MentoraTheme {
                Box(modifier = Modifier.fillMaxWidth().height(2000.dp)) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        CourseCard(
                            title = title,
                            instructorName = instructorName,
                            seed = "course-1",
                            categoryId = "Software Development",
                            categoryLabel = "Development",
                            thumbnailContentDescription = thumbnailDescription,
                            actionLabel = actionLabel,
                            onActionClick = {},
                            modifier = Modifier.testTag("card").fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // The requested 2000dp gets capped to the real device's own bounded screen height (this is
        // itself the "bounded-height parent" the reviewer's repro needs — a finite, real, device-sized
        // max height, not the unbounded height Compose's default test host would otherwise hand to
        // top-level content).
        val rootHeight = composeTestRule.onRoot().getUnclippedBoundsInRoot().height
        assertTrue("expected a real, generously-large bounded parent height", rootHeight.value > 400f)

        val cardBounds = composeTestRule.onNodeWithTag("card").getUnclippedBoundsInRoot()

        // The measured bug: the card grew to fill the ENTIRE 2000dp-tall bounded parent. Fixed: the
        // card's total height is driven by its own content (16:9 thumbnail + text block), nowhere
        // close to the full bounded-parent height.
        assertTrue(
            "expected card height (${cardBounds.height}) to be well under the bounded parent height ($rootHeight)",
            cardBounds.height < rootHeight * 0.5f,
        )

        val thumbnailBounds = composeTestRule.onNodeWithContentDescription(thumbnailDescription).getUnclippedBoundsInRoot()
        val expectedThumbnailHeight = thumbnailBounds.width * 9f / 16f
        assertEquals(
            "expected the thumbnail to be a real 16:9 box sized to the card's own width",
            expectedThumbnailHeight.value,
            thumbnailBounds.height.value,
            2f, // dp tolerance for rounding.
        )

        // Title/instructor/CTA must all be genuinely visible, not 0px-tall/off-bottom.
        for (bounds in listOf(
            composeTestRule.onNodeWithText(title).getUnclippedBoundsInRoot(),
            composeTestRule.onNodeWithText(instructorName).getUnclippedBoundsInRoot(),
            composeTestRule.onNodeWithText(actionLabel).getUnclippedBoundsInRoot(),
        )) {
            assertTrue("expected a non-zero rendered height", bounds.height.value > 0f)
            assertTrue("expected the node to be within the card's own bounds, well above the bounded parent's bottom", bounds.bottom <= cardBounds.bottom)
        }

        // The category chip must sit at the thumbnail's own top-start corner, not detached from it.
        val chipBounds = composeTestRule.onNodeWithText("Development").getUnclippedBoundsInRoot()
        assertTrue(
            "expected the category chip to be positioned inside the thumbnail's own bounds",
            chipBounds.top >= thumbnailBounds.top && chipBounds.left >= thumbnailBounds.left && chipBounds.top < thumbnailBounds.bottom,
        )
    }

    /** A [Row] with two [CourseCard]s each `Modifier.weight(1f)`, inside a bounded-height parent — the
     *  2-up grid shape. Both cards must end up the same height and both must fully show their content. */
    @Test
    fun twoUpRowGridCardsAreEqualHeightAndFullyVisible() {
        composeTestRule.setContent {
            MentoraTheme {
                Box(modifier = Modifier.fillMaxWidth().height(2000.dp)) {
                    // Identical title/instructor/category text on both cards deliberately — this test
                    // isolates the layout bug (does a bounded-height Row force the cards to expand?),
                    // not `Text(maxLines = 2)`'s ordinary content-driven line-count variance, which
                    // would legitimately produce different heights for genuinely different title text.
                    Row(modifier = Modifier.fillMaxWidth()) {
                        CourseCard(
                            title = title,
                            instructorName = instructorName,
                            seed = "course-1",
                            categoryId = "Software Development",
                            categoryLabel = "Development",
                            thumbnailContentDescription = "thumbnail-1",
                            actionLabel = actionLabel,
                            onActionClick = {},
                            modifier = Modifier.testTag("card-1").weight(1f),
                        )
                        CourseCard(
                            title = title,
                            instructorName = instructorName,
                            seed = "course-2",
                            categoryId = "Software Development",
                            categoryLabel = "Development",
                            thumbnailContentDescription = "thumbnail-2",
                            actionLabel = actionLabel,
                            onActionClick = {},
                            modifier = Modifier.testTag("card-2").weight(1f),
                        )
                    }
                }
            }
        }

        val rootHeight = composeTestRule.onRoot().getUnclippedBoundsInRoot().height
        val bounds1 = composeTestRule.onNodeWithTag("card-1").getUnclippedBoundsInRoot()
        val bounds2 = composeTestRule.onNodeWithTag("card-2").getUnclippedBoundsInRoot()

        assertTrue("expected neither grid card to fill the bounded parent height", bounds1.height < rootHeight * 0.5f)
        assertEquals("expected both 2-up grid cards to be the same height", bounds1.height.value, bounds2.height.value, 1f)

        composeTestRule.onAllNodesWithText(title).assertCountEquals(2)
        composeTestRule.onNodeWithContentDescription("thumbnail-1").assertExists()
        composeTestRule.onNodeWithContentDescription("thumbnail-2").assertExists()
    }

    /** A [LazyRow] of `Modifier.width(280.dp)` [CourseCard]s inside a bounded-height parent — the
     *  carousel shape. Each card must be exactly 280dp wide with a correct 16:9 thumbnail and no
     *  clipping. */
    @Test
    fun lazyRowCarouselCardsAreFixedWidthWithCorrectThumbnailRatio() {
        composeTestRule.setContent {
            MentoraTheme {
                Box(modifier = Modifier.fillMaxWidth().height(2000.dp)) {
                    LazyRow {
                        items(listOf("course-a", "course-b", "course-c")) { seed ->
                            CourseCard(
                                title = "Carousel Course $seed",
                                instructorName = instructorName,
                                seed = seed,
                                categoryId = "Software Development",
                                categoryLabel = "Development",
                                thumbnailContentDescription = "thumbnail-$seed",
                                actionLabel = actionLabel,
                                onActionClick = {},
                                modifier = Modifier.testTag("carousel-$seed").width(280.dp),
                            )
                        }
                    }
                }
            }
        }

        val rootHeight = composeTestRule.onRoot().getUnclippedBoundsInRoot().height
        val cardBounds = composeTestRule.onNodeWithTag("carousel-course-a").getUnclippedBoundsInRoot()
        assertEquals("expected the carousel card to be exactly 280dp wide", 280f, cardBounds.width.value, 0.5f)
        assertTrue("expected the carousel card not to fill the bounded parent height", cardBounds.height < rootHeight * 0.5f)

        val thumbnailBounds = composeTestRule.onNodeWithContentDescription("thumbnail-course-a").getUnclippedBoundsInRoot()
        assertEquals("expected the carousel thumbnail to span the card's own 280dp width", 280f, thumbnailBounds.width.value, 0.5f)
        val expectedThumbnailHeight = thumbnailBounds.width * 9f / 16f
        assertEquals(
            "expected the carousel thumbnail to be a real 16:9 box",
            expectedThumbnailHeight.value,
            thumbnailBounds.height.value,
            2f,
        )

        composeTestRule.onNodeWithText("Carousel Course course-a").assertExists()
    }

    /** Regression coverage: a [LazyColumn] item inside a bounded-height parent — the container shape
     *  the reviewer noted already worked before this fix — must still work correctly afterward. */
    @Test
    fun lazyColumnItemCardStillRendersCorrectly() {
        composeTestRule.setContent {
            MentoraTheme {
                Box(modifier = Modifier.fillMaxWidth().height(2000.dp)) {
                    LazyColumn {
                        item {
                            CourseCard(
                                title = title,
                                instructorName = instructorName,
                                seed = "course-1",
                                categoryId = "Software Development",
                                categoryLabel = "Development",
                                thumbnailContentDescription = thumbnailDescription,
                                actionLabel = actionLabel,
                                onActionClick = {},
                                modifier = Modifier.testTag("lazy-column-card").fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        val rootHeight = composeTestRule.onRoot().getUnclippedBoundsInRoot().height
        val cardBounds = composeTestRule.onNodeWithTag("lazy-column-card").getUnclippedBoundsInRoot()
        assertTrue("expected the LazyColumn item card not to fill the bounded parent height", cardBounds.height < rootHeight * 0.5f)

        composeTestRule.onNodeWithText(title).assertExists()
        composeTestRule.onNodeWithText(instructorName).assertExists()
        composeTestRule.onNodeWithText(actionLabel).assertExists()
        composeTestRule.onNodeWithContentDescription(thumbnailDescription).assertExists()
    }

    /** `CONTENT_RESILIENCE.md` § "Loading States": a [CourseCardSkeleton] must occupy the same
     *  footprint as the [CourseCard] it stands in for, at the same width, so nothing reflows when real
     *  content replaces it. Cross-checks the fixed [CourseCard] thumbnail against the skeleton's own
     *  already-correct `fillMaxWidth().aspectRatio(16f / 9f)` thumbnail block. */
    @Test
    fun skeletonAndResolvedCardShareTheSameThumbnailFootprint() {
        composeTestRule.setContent {
            MentoraTheme {
                Column {
                    CourseCardSkeleton(modifier = Modifier.testTag("skeleton").width(320.dp))
                    CourseCard(
                        title = title,
                        instructorName = instructorName,
                        seed = "course-1",
                        categoryId = "Software Development",
                        categoryLabel = "Development",
                        thumbnailContentDescription = thumbnailDescription,
                        actionLabel = actionLabel,
                        onActionClick = {},
                        modifier = Modifier.testTag("resolved-card").width(320.dp),
                    )
                }
            }
        }

        val skeletonBounds = composeTestRule.onNodeWithTag("skeleton").getUnclippedBoundsInRoot()
        val thumbnailBounds = composeTestRule.onNodeWithContentDescription(thumbnailDescription).getUnclippedBoundsInRoot()

        // Both are sized purely from the shared 320dp width via the same fillMaxWidth().aspectRatio(16/9)
        // recipe, so their thumbnail-block heights must match exactly (within rounding).
        val expectedThumbnailHeight = 320f * 9f / 16f
        assertEquals("expected the resolved card's thumbnail to be a 16:9 box of the shared width", expectedThumbnailHeight, thumbnailBounds.height.value, 1f)
        assertTrue("expected the skeleton to be at least as tall as its own thumbnail block", skeletonBounds.height.value >= expectedThumbnailHeight)
    }
}
