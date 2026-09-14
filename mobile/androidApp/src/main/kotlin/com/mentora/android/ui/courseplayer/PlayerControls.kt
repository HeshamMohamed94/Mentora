package com.mentora.android.ui.courseplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraPlayerChrome
import com.mentora.android.ui.components.MentoraIcon
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.shared.playback.PlaybackState
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Task 13 "C3" — `execution/DECISIONS_LOG.md` D85 Decision 4's LTR-locked scrubber, plus play/pause
 * and the time label, overlaid on the video (D85 Decision 5's theme-invariant chrome — see
 * [MentoraPlayerChrome]). **Collects [playbackPositionFlow] here, nowhere else** — the exact D85
 * Decision 1 warning: "Collect it inside the scrubber composable only — never at the screen root, or
 * every 250ms recomposes the whole player screen including the curriculum list." This composable is
 * genuinely the leaf that needs it, not `CoursePlayerScreen`'s root.
 *
 * **The LTR exception, scoped exactly per Decision 4.** Only the [Slider] itself is wrapped in
 * `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr)` — it is built as an M3
 * [Slider] with custom `track`/`thumb` lambdas rather than a hand-drawn `Canvas`, per that decision's
 * own reasoning (inherits drag handling, the 48dp touch target, and `ProgressBarRangeInfo` semantics;
 * confirmed from `Slider.kt`'s own source that `SliderState.isRtl` is derived straight from
 * `LocalLayoutDirection`, which is exactly the mirroring this override defeats). The time label text
 * gets the SAME override individually (its own, narrower `CompositionLocalProvider`), per Decision 4's
 * "scrubber AND time labels" — so the "MM:SS / MM:SS" string itself never bidi-reorders under an
 * ambient RTL context — but it still sits inside the REST of this control bar's normally-mirroring
 * `Row` (play/pause icon + time label), per that decision's own "the surrounding
 * play/pause/volume/fullscreen row lays out start-to-end and DOES mirror as a group" instruction.
 *
 * **No fullscreen control** — D85's own Open Question 1 recommends deferring it to a conditional C4
 * sub-commit; omitted entirely here, not shipped as a no-op icon. **No captions/volume control** — see
 * D85's own "three content gaps" disclosure (no caption asset/media kind exists anywhere in the
 * product; volume is absent from the mobile showcase frame, hardware keys cover it on a phone). **No
 * speed-control chip** — see [MentoraPlayerChrome]'s own kdoc on [MentoraPlayerChrome.speedChipBackground].
 * **No buffered-progress layer** — [com.mentora.android.playback.PlaybackController] exposes no
 * buffered-position stream at all; see [MentoraPlayerChrome.scrubberBuffered]'s own kdoc.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerControls(
    playbackStateFlow: StateFlow<PlaybackState>,
    playbackPositionFlow: StateFlow<Duration>,
    playbackDurationFlow: StateFlow<Duration?>,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Duration) -> Unit,
    modifier: Modifier = Modifier,
) {
    val playbackState by playbackStateFlow.collectAsState()
    val position by playbackPositionFlow.collectAsState()
    val duration by playbackDurationFlow.collectAsState()
    val durationMillis = duration?.inWholeMilliseconds?.takeIf { it > 0 } ?: 0L

    // Local drag override while the user's finger is actively moving the thumb — see this file's own
    // "onSeek only fires on release" rationale below.
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val reportedFraction = if (durationMillis > 0) {
        (position.inWholeMilliseconds.toFloat() / durationMillis.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val displayedFraction = dragFraction ?: reportedFraction

    Column(
        modifier = modifier
            .background(MentoraPlayerChrome.controlBarBackground)
            .padding(horizontal = MentoraDimens.spacing.space3, vertical = MentoraDimens.spacing.space2)
            .testTag(PlayerControlsTestTag),
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
    ) {
        // Review finding (round 5, MEDIUM): the M3 Slider ships built-in `ProgressBarRangeInfo`
        // semantics (per this file's own kdoc on why Slider was chosen over a hand-drawn Canvas), but
        // with no content description a screen reader announces only a bare percentage, never what
        // the control IS or the actual elapsed/total time — explicit `contentDescription` +
        // `stateDescription` close that gap. `formatPlaybackTime`'s own `Locale.US` pin (this file's
        // own kdoc on it) keeps the announced state description on the same locked-numerals rule as
        // the visible time label.
        val scrubberContentDescription = stringResource(R.string.course_player_scrubber_content_description)
        val scrubberStateDescription = stringResource(
            R.string.course_player_scrubber_state_description,
            formatPlaybackTime(position),
            duration?.let { formatPlaybackTime(it) } ?: PlaceholderTime,
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Slider(
                value = displayedFraction,
                onValueChange = { dragFraction = it },
                onValueChangeFinished = {
                    val finalFraction = dragFraction
                    dragFraction = null
                    if (finalFraction != null && durationMillis > 0) {
                        onSeek((finalFraction * durationMillis).toLong().milliseconds)
                    }
                },
                enabled = durationMillis > 0,
                track = { PlayerScrubberTrack(fraction = displayedFraction) },
                thumb = { PlayerScrubberThumb() },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = scrubberContentDescription
                        stateDescription = scrubberStateDescription
                    }
                    .testTag(PlayerScrubberTestTag),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
        ) {
            PlayerChromeIconButton(
                icon = if (playbackState is PlaybackState.Playing) MentoraIconName.Pause else MentoraIconName.Play,
                contentDescription = stringResource(
                    if (playbackState is PlaybackState.Playing) {
                        R.string.course_player_pause_content_description
                    } else {
                        R.string.course_player_play_content_description
                    },
                ),
                onClick = onPlayPauseToggle,
            )
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    text = stringResource(
                        R.string.course_player_time_label,
                        formatPlaybackTime(position),
                        duration?.let { formatPlaybackTime(it) } ?: PlaceholderTime,
                    ),
                    style = MaterialTheme.typography.labelSmall, // typography.caption (see MentoraTheme.kt's own mapping).
                    color = MentoraPlayerChrome.controlIconAndText,
                )
            }
        }
    }
}

@Composable
private fun PlayerScrubberTrack(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PlayerScrubberTrackHeight)
            .clip(CircleShape)
            .background(MentoraPlayerChrome.scrubberTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MentoraPlayerChrome.scrubberFillAndThumb),
        )
    }
}

@Composable
private fun PlayerScrubberThumb() {
    Box(
        modifier = Modifier
            .size(PlayerScrubberThumbSize)
            .clip(CircleShape)
            .background(MentoraPlayerChrome.scrubberFillAndThumb),
    )
}

/** A plain clickable [MentoraIcon] tinted to [MentoraPlayerChrome]'s theme-invariant colors —
 *  deliberately NOT [com.mentora.android.ui.components.MentoraIconButton] (that component tints from
 *  `MaterialTheme.colorScheme`, which is exactly what D85 Decision 5 says the player chrome must NOT
 *  do). [MentoraDimens.touchTargetMin] keeps the 48dp minimum touch target regardless of the icon's
 *  own visual size. */
@Composable
private fun PlayerChromeIconButton(
    icon: MentoraIconName,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(MentoraDimens.touchTargetMin)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        MentoraIcon(
            name = icon,
            contentDescription = contentDescription,
            tint = MentoraPlayerChrome.controlIconAndText,
        )
    }
}

/** `Locale.US` — never the device locale — guarantees Western-numeral digits regardless of the app's
 *  current locale (`design-system/LOCALIZATION.md § 8`), the same rule `CourseDetailsScreen.kt`'s
 *  `formatDemoPrice` kdoc documents for its own numeral formatting. */
private fun formatPlaybackTime(duration: Duration): String {
    val totalSeconds = duration.inWholeSeconds.coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

private const val PlaceholderTime = "--:--"

private val PlayerScrubberTrackHeight = 4.dp
private val PlayerScrubberThumbSize = 12.dp

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val PlayerControlsTestTag = "course-player-controls"
const val PlayerScrubberTestTag = "course-player-scrubber"
