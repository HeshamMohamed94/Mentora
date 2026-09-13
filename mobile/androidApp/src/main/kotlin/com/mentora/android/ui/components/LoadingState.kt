package com.mentora.android.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraMotionDuration

/**
 * `design-system/COMPONENTS.md` § LoadingState (lines 557-566). "Skeleton loaders preferred over
 * spinners for content areas (cards, lists, text blocks). Spinners reserved for buttons/inline
 * actions and full-screen initial load." — this file provides both families: [SkeletonBlock]/
 * [CourseCardSkeleton] for the former, [FullScreenLoadingState] for the latter (button/inline
 * spinners already live in [MentoraButton]/[MentoraSelect], not duplicated here).
 *
 * Shimmer: animated gradient sweep, `color.surface.variant` -> `color.border.default` ->
 * `color.surface.variant`, looping over `motion.duration.slow` (per the spec's literal wording) —
 * implemented as a `3x motion.duration.slow` full sweep period (900ms) so the sweep's *visible pass*
 * across the block reads at a `motion.duration.slow`-scale pace rather than snapping back
 * instantly every 300ms, the same reasoning [MentoraIndeterminateProgressBar] already documents for
 * its own loop period.
 */
@Composable
fun SkeletonBlock(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraSmall,
) {
    val colorScheme = MaterialTheme.colorScheme
    val transition = rememberInfiniteTransition(label = "skeleton-shimmer")
    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = MentoraMotionDuration.slow * 3, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithCache {
                val brush = Brush.linearGradient(
                    colors = listOf(colorScheme.surfaceVariant, colorScheme.outlineVariant, colorScheme.surfaceVariant),
                    start = Offset(sweep * size.width - size.width, 0f),
                    end = Offset(sweep * size.width, size.height),
                )
                onDrawBehind { drawRect(brush) }
            }
            .semantics { contentDescription = "Loading" },
    )
}

/**
 * A [CourseCard]-shaped skeleton: 16:9 thumbnail block + title/instructor/action-shaped bars, same
 * `radius.large` shell (border + `MaterialTheme.shapes.medium`) as the real card it stands in for.
 */
@Composable
fun CourseCardSkeleton(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium, // radius.large — matches CourseCard's own shape.
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column {
            SkeletonBlock(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shape = CourseThumbnailTopCornersShape,
            )
            Column(
                modifier = Modifier.padding(MentoraDimens.spacing.space4),
                verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
            ) {
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.7f).height(20.dp))
                SkeletonBlock(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp))
                SkeletonBlock(modifier = Modifier.fillMaxWidth().height(40.dp), shape = MaterialTheme.shapes.small)
            }
        }
    }
}

/** Full-screen initial-load spinner — `color.brand.primary` stroke, indeterminate rotation, per the
 *  spec's "Spinners reserved for ... full-screen initial load." */
@Composable
fun FullScreenLoadingState(
    modifier: Modifier = Modifier,
    contentDescriptionLabel: String = "Loading",
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .semantics { contentDescription = contentDescriptionLabel },
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}
