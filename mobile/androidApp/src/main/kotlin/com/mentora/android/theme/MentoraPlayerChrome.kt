package com.mentora.android.theme

import androidx.compose.ui.graphics.Color

/**
 * Task 13 "C3" — `execution/DECISIONS_LOG.md` D85 Decision 5's theme-invariant video-control-chrome
 * colors, pinned to the LIGHT resolution of each locked token (a disclosed workaround for a real
 * defect in the locked token tree — `text.inverse` resolves to near-black in Dark theme, which would
 * make Dark-theme player controls dark-on-dark; see that decision's full rationale). Every value here
 * is the exact literal the locked showcase's own player frames use
 * (`design-review-locked/Mentora Showcase.dc.html:2245-2248`), never invented:
 * - [controlBarBackground] = [MentoraColorsLight.overlayScrim] (`rgba(17,18,23,0.48)` — that constant
 *   is already baked at 48% alpha, `0x7A / 0xFF ≈ 0.478`, so it is reused directly, not recomputed).
 * - [controlIconAndText] = [MentoraColorsLight.textInverse] (`#FFFFFF`).
 * - [scrubberFillAndThumb] = [MentoraColorsLight.brandPrimary] (`#6558D3`).
 * - [scrubberTrack] = white at [MentoraStateOpacityLight.hoverOpacity] (0.08) — the showcase's own
 *   `rgba(255,255,255,0.08)` scrubber-track literal (`Mentora Showcase.dc.html:2246`).
 * - [scrubberBuffered] = white at [MentoraStateOpacityLight.pressedOpacity] (0.12), per this
 *   decision's own pinned list. **Disclosed: currently unused.** [com.mentora.android.playback
 *   .PlaybackController] exposes no buffered-position stream at all (C1/C2 never added one), so
 *   `PlayerControls.kt`'s scrubber renders no separate buffered-progress layer — this constant is kept
 *   for completeness/documentation against Decision 5's own literal instruction, not because anything
 *   currently reads it.
 * - [speedChipBackground] = [MentoraColorsLight.overlayChipScrim] (`rgba(17,18,23,0.72)`, already
 *   identical in both themes). **Disclosed: currently unused** — the showcase's speed-control chip
 *   ("1×", `mobile-course-player.json` order-1 content) is a decorative-only display with no backing
 *   playback-rate mechanism anywhere in [com.mentora.android.playback.PlaybackController]/
 *   `MediaPlaybackController`; per this app's own established "never ship a no-op icon/control" rule
 *   (T10's precedent), the chip itself is not built in C3. This constant is kept only because it is
 *   part of Decision 5's own pinned literal set, for whichever later task adds real speed control.
 */
object MentoraPlayerChrome {
    val controlBarBackground: Color = MentoraColorsLight.overlayScrim
    val controlIconAndText: Color = MentoraColorsLight.textInverse
    val scrubberFillAndThumb: Color = MentoraColorsLight.brandPrimary
    val scrubberTrack: Color = Color.White.copy(alpha = MentoraStateOpacityLight.hoverOpacity)
    val scrubberBuffered: Color = Color.White.copy(alpha = MentoraStateOpacityLight.pressedOpacity)
    val speedChipBackground: Color = MentoraColorsLight.overlayChipScrim
}
