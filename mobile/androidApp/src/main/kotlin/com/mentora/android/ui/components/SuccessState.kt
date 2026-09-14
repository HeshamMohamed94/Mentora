package com.mentora.android.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraMotionDuration
import com.mentora.android.theme.MentoraMotionEasing
import com.mentora.android.theme.extendedColors

/**
 * Whether the system "remove animations" preference is active, checked via
 * `Settings.Global.ANIMATOR_DURATION_SCALE` (`0` when the user has that accessibility setting on).
 *
 * **Corrected disclosure — Compose's own animations already respect this setting.** An earlier version
 * of this kdoc claimed Compose's `animateFloatAsState`/`tween` do *not* consult
 * `Settings.Global.ANIMATOR_DURATION_SCALE`, and that only the View system's `ValueAnimator` does. That
 * was factually wrong, verified directly: Compose's `Recomposer`/frame clock implements
 * `MotionDurationScale`, which *does* read that system setting automatically — setting
 * `animator_duration_scale=0` causes a real `tween(300)` animation to complete in ~2 frames instead of
 * ~300ms, with zero manual code. So this explicit check is **redundant** with what Compose already does
 * on its own, not a gap Compose leaves open.
 *
 * **Why it's kept anyway.** [SuccessState]'s spec (`ACCESSIBILITY.md` § 9) additionally wants a
 * *different* animation shape under reduced motion — an opacity-only cross-fade instead of the normal
 * scale+fade, at a shorter duration — not just "the same animation, sped up," which is all
 * `MotionDurationScale` gives you automatically. This manual check drives that shape swap; it's
 * redundant with, not a replacement for, Compose's own automatic duration-scale handling, and is left in
 * place rather than removed since dropping it would regress that shape swap.
 *
 * **Check-once, by design, not a bug.** This reads the setting once via a keyless `remember` and never
 * re-observes a mid-session toggle. That's an intentional, disclosed tradeoff for this component
 * specifically (not a general pattern to copy): a success screen is a one-shot celebratory moment that
 * gets freshly recomposed from scratch each time it's shown (e.g. after a checkout completes), so
 * "checked once at composition start" already covers its real use case — there's no long-lived
 * [SuccessState] instance sitting on screen for a settings toggle to invalidate mid-display.
 */
@Composable
internal fun rememberReducedMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * `design-system/COMPONENTS.md` § SuccessState (lines 568-583) — "structurally identical [to
 * EmptyState/ErrorState] ... but using the success semantic identity." Icon (`icon.large`,
 * `color.success.default`) -> title (`heading.h3`) -> description (`body.small`, max ~2 lines) ->
 * `PrimaryButton`, `space.10` vertical container padding.
 *
 * **Entrance motion + reduced motion (unconditional per the spec's own wording — "no exception for
 * this component just because it's a celebratory moment").** Normally: scale 0.9->1 + fade 0->1 over
 * `motion.duration.slow` + `easing.decelerate`. With reduced motion on ([rememberReducedMotionEnabled]):
 * an opacity-only cross-fade at `motion.duration.fast` — no scale — per `ACCESSIBILITY.md` § 9's
 * "cross-fade or cut instantly instead of playing slide/scale transitions."
 */
@Composable
fun SuccessState(
    title: String,
    description: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: MentoraIconName = MentoraIconName.CheckCircle,
) {
    val reducedMotion = rememberReducedMotionEnabled()
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val durationMillis = if (reducedMotion) MentoraMotionDuration.fast else MentoraMotionDuration.slow
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = durationMillis, easing = MentoraMotionEasing.decelerate),
        label = "success-state-alpha",
    )
    val scale by animateFloatAsState(
        // Reduced motion drops the scale entirely (target pinned to 1f throughout) — only the alpha
        // cross-fade above plays, per this file's kdoc.
        targetValue = if (reducedMotion) 1f else if (visible) 1f else 0.9f,
        animationSpec = tween(durationMillis = durationMillis, easing = MentoraMotionEasing.decelerate),
        label = "success-state-scale",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = MentoraDimens.spacing.space10)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
    ) {
        MentoraIcon(
            name = icon,
            contentDescription = null,
            size = MentoraDimens.iconSize.large,
            tint = MaterialTheme.extendedColors.success,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall, // heading.h3.
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            // No maxLines/ellipsis clamp — CONTENT_RESILIENCE.md § 1 / COMPONENTS.md line 583's "~2
            // lines" is soft guidance for typical content length, not a hard truncation rule; this
            // wraps freely, matching ErrorState.kt's already-correct unclamped treatment.
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(text = actionLabel, onClick = onActionClick)
    }
}
