package com.mentora.android.ui.components

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mentora.android.theme.MentoraColorsDark
import com.mentora.android.theme.MentoraColorsLight
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Finding 2 (dialog background tint), Finding 4 (scrim color), and Finding 5 (full-width stacked
 * actions) regression coverage for [AppDialog].
 *
 * The scrim assertions ([lightThemeScrimMatchesOverlayScrimTokenExactly],
 * [darkThemeScrimMatchesOverlayScrimTokenExactly]) deliberately do NOT use
 * `onNodeWithTag(...).captureToImage()`. That call only captures the dialog window's own rendered
 * Compose surface, which is blind to the *platform* window's own separate dim overlay
 * (`FLAG_DIM_BEHIND`) drawn underneath it — exactly the bug class this regression is guarding against
 * (a residual, un-zeroed platform dim compositing behind an otherwise-correct custom scrim Box, making
 * the real on-screen result far darker than the token while `captureToImage()` on the tagged node still
 * reports the correct, un-composited color and false-passes). Instead, they take a REAL full-device
 * screenshot ([android.app.UiAutomation.takeScreenshot]) and sample raw pixels from it, which is the
 * only capture path that actually observes what a person looking at the device sees.
 */
@RunWith(AndroidJUnit4::class)
class AppDialogColorAndLayoutTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var density: Density

    private fun renderDialog(darkTheme: Boolean) {
        composeTestRule.setContent {
            density = LocalDensity.current
            MentoraTheme(darkTheme = darkTheme) {
                AppDialog(
                    title = "Discard changes?",
                    body = "You have unsaved changes.",
                    confirmLabel = "Discard",
                    onConfirm = {},
                    onDismissRequest = {},
                    dismissLabel = "Cancel",
                )
            }
        }
    }

    /** Composites a translucent [foreground] over an opaque white background — the dialog host
     *  Activity's own window background (`Theme.Material.Light.NoActionBar`'s default `windowBackground`
     *  is white; see `AndroidManifest.xml`), which is what's actually behind the dialog's scrim on a real
     *  screen. A real full-screen screenshot is always fully opaque (there is nothing further behind it
     *  to composite with), so the expected sample must be pre-composited the same way before comparing. */
    private fun compositeOverWhite(foreground: Color): Color {
        val a = foreground.alpha
        return Color(
            red = foreground.red * a + 1f * (1f - a),
            green = foreground.green * a + 1f * (1f - a),
            blue = foreground.blue * a + 1f * (1f - a),
            alpha = 1f,
        )
    }

    /** Takes a real full-device screenshot and samples two points guaranteed to be outside the dialog
     *  card's own bounds but inside the full-screen scrim: one in the general scrim area (left of the
     *  centered card), and one in the status-bar strip (above the card, right at the top of the screen —
     *  only actually scrim-covered once the dialog draws edge-to-edge under the system bars via
     *  `DialogProperties.decorFitsSystemWindows = false` + `FLAG_LAYOUT_NO_LIMITS`; see `AppDialog.kt`'s
     *  kdoc). Returns (generalScrimSample, statusBarSample). */
    private fun captureRealScrimSamples(): Pair<Color, Color> {
        composeTestRule.waitForIdle()
        // composeTestRule.waitForIdle() only waits for Compose's own composition/animation clock — it
        // does NOT wait for the platform WindowManager to actually apply setDimAmount(0f) /
        // setDecorFitsSystemWindows(false) and have SurfaceFlinger composite a settled frame with those
        // changes applied. Empirically (real device measurement), screenshotting immediately after
        // waitForIdle() sometimes still captures a transient frame with the platform dim only partially
        // removed, producing a flaky, too-dark sample; a short settle delay makes this consistent.
        Thread.sleep(700)

        val cardBounds = composeTestRule.onNodeWithTag(AppDialogSurfaceTestTag).getUnclippedBoundsInRoot()
        val cardLeftPx = with(density) { cardBounds.left.toPx() }
        val cardTopPx = with(density) { cardBounds.top.toPx() }
        check(cardLeftPx > 2f) {
            "test assumption violated: the dialog card has no usable left margin ($cardLeftPx px) to " +
                "sample an off-card scrim pixel from — the card may be rendering full-width"
        }
        check(cardTopPx > 10f) {
            "test assumption violated: the dialog card has no usable top margin ($cardTopPx px) to " +
                "sample a status-bar-strip scrim pixel from"
        }

        val screenshot: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: error("uiAutomation.takeScreenshot() returned null — device/emulator screenshot capture failed")

        // Strictly between the screen edge and the card's own left edge — guaranteed outside the card
        // regardless of the y coordinate chosen.
        val generalScrimX = (cardLeftPx / 2f).toInt().coerceIn(1, screenshot.width - 1)
        val generalScrimY = screenshot.height / 2
        // Literally the top few px of the screen (the actual system status bar strip), not just
        // "somewhere above the card" — the card top is always well below this on a real device, so this
        // stays comfortably clear of the card regardless.
        val statusBarX = screenshot.width / 2
        val statusBarY = 5.coerceIn(1, screenshot.height - 1)

        val generalScrimSample = Color(screenshot.getPixel(generalScrimX, generalScrimY))
        val statusBarSample = Color(screenshot.getPixel(statusBarX, statusBarY))
        screenshot.recycle()
        return generalScrimSample to statusBarSample
    }

    @Test
    fun lightThemeCardRendersPureSurfaceElevatedNotATintedWash() {
        renderDialog(darkTheme = false)
        val surface = composeTestRule.onNodeWithTag(AppDialogSurfaceTestTag).captureToImage().toPixelMap()
        // Sampled well inside the card's own space6 padding + xlarge corner radius, so this pixel is
        // genuinely part of the Surface's own painted background, not the outer padding gap where the
        // scrim shows through underneath.
        val sample = surface[surface.width / 2, surface.height / 2]
        // color.surface.elevated (light) — pure white, MentoraTokens.kt's MentoraColorsLight.surfaceElevated.
        assertTrue("expected pure white, got $sample", colorsClose(Color(0xFFFFFFFF), sample))
    }

    @Test
    fun darkThemeCardRendersExactSurfaceElevatedToken() {
        renderDialog(darkTheme = true)
        val surface = composeTestRule.onNodeWithTag(AppDialogSurfaceTestTag).captureToImage().toPixelMap()
        val sample = surface[surface.width / 2, surface.height / 2]
        // color.surface.elevated (dark) — MentoraTokens.kt's MentoraColorsDark.surfaceElevated (0xFF24252E).
        assertTrue("expected #24252E, got $sample", colorsClose(Color(0xFF24252E), sample))
    }

    @Test
    fun lightThemeScrimMatchesOverlayScrimTokenExactly() {
        renderDialog(darkTheme = false)
        val (generalScrimSample, statusBarSample) = captureRealScrimSamples()
        // color.overlay.scrim (light) — MentoraColorsLight.overlayScrim (0x7A111217): a purple-tinted dark
        // overlay, NOT plain black at the platform's own default dim alpha. Composited over the host
        // window's white background, since a real screenshot is always fully opaque.
        val expected = compositeOverWhite(MentoraColorsLight.overlayScrim)
        // A wider tolerance than the in-process captureToImage() comparisons elsewhere in this file — a
        // real device/emulator screenshot goes through actual display compositing (dithering, minor
        // rounding), not a bit-exact software render.
        assertTrue(
            "expected the general scrim area to match the light overlay.scrim token composited over " +
                "white ($expected), got $generalScrimSample — if this is close to the platform's own " +
                "un-zeroed dim color instead, the FLAG_DIM_BEHIND regression is back",
            colorsClose(expected, generalScrimSample, tolerance = 0.06f),
        )
        assertTrue(
            "expected the status-bar strip to be scrim-covered the same as the rest of the screen " +
                "(expected ~$expected), got $statusBarSample — if this is undimmed, " +
                "the DialogProperties.decorFitsSystemWindows / FLAG_LAYOUT_NO_LIMITS edge-to-edge fix regressed",
            colorsClose(expected, statusBarSample, tolerance = 0.06f),
        )
    }

    @Test
    fun darkThemeScrimMatchesOverlayScrimTokenExactly() {
        renderDialog(darkTheme = true)
        val (generalScrimSample, statusBarSample) = captureRealScrimSamples()
        // color.overlay.scrim (dark) — MentoraColorsDark.overlayScrim (0xA3000000).
        val expected = compositeOverWhite(MentoraColorsDark.overlayScrim)
        assertTrue(
            "expected the general scrim area to match the dark overlay.scrim token composited over " +
                "white ($expected), got $generalScrimSample — if this is close to the platform's own " +
                "un-zeroed dim color instead, the FLAG_DIM_BEHIND regression is back",
            colorsClose(expected, generalScrimSample, tolerance = 0.06f),
        )
        assertTrue(
            "expected the status-bar strip to be scrim-covered the same as the rest of the screen " +
                "(expected ~$expected), got $statusBarSample — if this is undimmed, " +
                "the DialogProperties.decorFitsSystemWindows / FLAG_LAYOUT_NO_LIMITS edge-to-edge fix regressed",
            colorsClose(expected, statusBarSample, tolerance = 0.06f),
        )
    }

    /** The regression this most directly targets: before the fix, the scrim was Compose [Dialog]'s
     *  platform default window dim — always plain black at a fixed alpha, regardless of theme. If that
     *  were still true, light and dark would render an IDENTICAL scrim color. They must now differ,
     *  since `MentoraTheme.kt` maps a different `overlayScrim` token per theme. */
    @Test
    fun scrimColorActuallyVariesByThemeProvingItsThemeSourcedNotAPlatformDefault() {
        // Both dialogs rendered simultaneously (each its own real platform Window) — a single
        // ComposeContentTestRule only allows one setContent() call per test, so this cannot be two
        // sequential renders. onAllNodesWithTag disambiguates the two same-tagged scrim nodes by the
        // order they were added to the composition.
        composeTestRule.setContent {
            MentoraTheme(darkTheme = false) {
                AppDialog(
                    title = "Discard changes?",
                    body = "You have unsaved changes.",
                    confirmLabel = "Discard",
                    onConfirm = {},
                    onDismissRequest = {},
                    dismissLabel = "Cancel",
                )
            }
            MentoraTheme(darkTheme = true) {
                AppDialog(
                    title = "Discard changes?",
                    body = "You have unsaved changes.",
                    confirmLabel = "Discard",
                    onConfirm = {},
                    onDismissRequest = {},
                    dismissLabel = "Cancel",
                )
            }
        }

        val scrimNodes = composeTestRule.onAllNodesWithTag(AppDialogScrimTestTag)
        val lightSample = scrimNodes[0].captureToImage().toPixelMap()[5, 5]
        val darkSample = scrimNodes[1].captureToImage().toPixelMap()[5, 5]

        assertTrue(
            "expected the light and dark scrim colors to differ (light=$lightSample, dark=$darkSample) — " +
                "a hardcoded platform-default scrim would render identically regardless of theme",
            !colorsClose(lightSample, darkSample, tolerance = 0.05f, includeAlpha = true),
        )
    }

    /** Finding 5: `COMPONENTS.md` line 526's mobile branch — full-width stacked buttons, not a
     *  right-aligned row. Both actions must span nearly the full card width and be stacked vertically
     *  (dismiss above confirm), not side-by-side. */
    @Test
    fun actionsAreFullWidthStackedNotARightAlignedRow() {
        renderDialog(darkTheme = false)

        val cardBounds = composeTestRule.onNodeWithTag(AppDialogSurfaceTestTag).getUnclippedBoundsInRoot()
        val dismissBounds = composeTestRule.onNodeWithText("Cancel").getUnclippedBoundsInRoot()
        val confirmBounds = composeTestRule.onNodeWithText("Discard").getUnclippedBoundsInRoot()

        // Stacked, not side-by-side: dismiss sits entirely above confirm.
        assertTrue(
            "expected the dismiss button to sit above the confirm button (stacked), was dismissBottom=${dismissBounds.bottom} confirmTop=${confirmBounds.top}",
            dismissBounds.bottom <= confirmBounds.top,
        )

        // Full-width: each button's own width is close to the card's inner content width (card width
        // minus the space6 padding on both sides), not a compact, hug-content, right-aligned button.
        val innerWidth = cardBounds.width - (MentoraDimens.spacing.space6 * 2)
        assertTrue(
            "expected the dismiss button to span nearly the full card width, was ${dismissBounds.width} vs card inner width ~$innerWidth",
            dismissBounds.width.value > innerWidth.value * 0.85f,
        )
        assertTrue(
            "expected the confirm button to span nearly the full card width, was ${confirmBounds.width} vs card inner width ~$innerWidth",
            confirmBounds.width.value > innerWidth.value * 0.85f,
        )

        // Not right-aligned: the dismiss button's left edge sits near the card's own left inset, not
        // clustered together with confirm on the right-hand side only.
        assertTrue(
            "expected the dismiss button's left edge to be near the card's left content inset, not right-aligned",
            (dismissBounds.left - cardBounds.left).value < innerWidth.value * 0.3f,
        )
    }
}
