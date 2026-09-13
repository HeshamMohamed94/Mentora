package com.mentora.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mentora.android.theme.MentoraMotionDuration
import com.mentora.android.theme.MentoraMotionEasing
import com.mentora.android.theme.extendedColors
import com.mentora.android.theme.stateOpacities

/**
 * `design-system/COMPONENTS.md` § ProgressBar (lines 415-430). Height 8 (literal, see `Badge.kt`'s
 * kdoc), radius `radius.full` -> [CircleShape], track `color.surface.variant`, fill
 * `color.brand.primary` by default.
 *
 * Default/Active fill animates width change over `motion.duration.normal` + `motion.easing.standard`
 * — both from [MentoraMotionDuration]/[MentoraMotionEasing] (see that file's kdoc for why they're
 * hand-authored: `motion.*` has no generated Kotlin constant at all, unlike every other token
 * category). Complete swaps fill to `color.success.default`. Paused is `color.text.disabled`
 * (`ColorScheme.onSurface @ state.disabledContent`, this design system's established resolution for
 * that semantic color — see `MentoraTheme.kt`'s own comment), static (no animation — implemented by
 * skipping [animateFloatAsState] entirely for that state).
 */
enum class MentoraProgressBarState { Active, Complete, Paused }

@Composable
fun MentoraProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    state: MentoraProgressBarState = MentoraProgressBarState.Active,
    contentDescriptionLabel: String? = null,
) {
    val clamped = progress.coerceIn(0f, 1f)
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val opacities = MaterialTheme.stateOpacities

    val fillColor = when (state) {
        MentoraProgressBarState.Active -> colorScheme.primary
        MentoraProgressBarState.Complete -> extended.success
        MentoraProgressBarState.Paused -> colorScheme.onSurface.copy(alpha = opacities.disabledContent)
    }

    val animatedProgress = if (state == MentoraProgressBarState.Paused) {
        clamped
    } else {
        animateFloatAsState(
            targetValue = clamped,
            animationSpec = tween(durationMillis = MentoraMotionDuration.normal, easing = MentoraMotionEasing.standard),
            label = "mentora-progress-bar",
        ).value
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp) // component.progressBar.height (8), a literal (see file kdoc).
            .clip(CircleShape)
            .background(colorScheme.surfaceVariant)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(current = clamped, range = 0f..1f)
                contentDescriptionLabel?.let { contentDescription = it }
            },
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(fillColor),
        )
    }
}

/**
 * Indeterminate variant (e.g. AI Tutor "thinking") — a looping linear sweep across the track.
 * `motion.easing.linear` ("never uses `easing.standard` (that's for state changes, not loops)" per
 * the spec's own note) drives a highlight bar's horizontal position, drawn directly in [drawBehind]
 * (rather than a nested `Box` + offset) so its pixel position is computed from the track's own
 * measured [androidx.compose.ui.graphics.drawscope.DrawScope.size] instead of a second layout pass.
 */
@Composable
fun MentoraIndeterminateProgressBar(
    modifier: Modifier = Modifier,
    contentDescriptionLabel: String? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "mentora-indeterminate-progress")
    val sweep by transition.animateFloat(
        initialValue = -0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = MentoraMotionDuration.slow * 3, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(colorScheme.surfaceVariant)
            .drawBehind {
                val barWidth = size.width * 0.35f
                drawRoundRect(
                    color = colorScheme.primary,
                    topLeft = Offset(x = sweep * size.width, y = 0f),
                    size = Size(barWidth, size.height),
                    cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                )
            }
            .semantics { contentDescriptionLabel?.let { contentDescription = it } },
    )
}
