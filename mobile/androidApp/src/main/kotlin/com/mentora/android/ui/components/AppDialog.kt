package com.mentora.android.ui.components

import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraElevationTokens
import com.mentora.android.theme.MentoraMotionDuration
import com.mentora.android.theme.MentoraMotionEasing
import com.mentora.android.theme.MentoraRadiusTokens

/** Test-only hooks (`ui.test.onNodeWithTag`) for asserting dialog confirm/dismiss actions fire. */
const val AppDialogConfirmButtonTestTag = "mentora-app-dialog-confirm"
const val AppDialogDismissButtonTestTag = "mentora-app-dialog-dismiss"

/** Test-only hook for measuring the custom scrim's actual rendered color (Finding 4 verification). */
const val AppDialogScrimTestTag = "mentora-app-dialog-scrim"

/** Test-only hook for measuring the dialog card's own background color (Finding 2 verification). */
const val AppDialogSurfaceTestTag = "mentora-app-dialog-surface"

/**
 * `design-system/COMPONENTS.md` § AppDialog (lines 515-527). `radius.xlarge` (24), `surface.elevated`,
 * `elevation.4`, `space.6` padding, title (`heading.h3`) + body (`body.medium`, `text.secondary`) +
 * actions column (full-width stacked, `space.2` gap). The spec's "right-aligned row" treatment is
 * explicitly the *web/tablet+* alternative (`COMPONENTS.md` line 526: "right-aligned (web/tablet+) row
 * ... full-width stacked on mobile") — this is an Android-only app, so it always takes the mobile
 * branch: a full-width-stacked [Column] of buttons, never the right-aligned row.
 *
 * Built on Compose's own [Dialog] (styled to these tokens), not a from-scratch overlay.
 *
 * **Custom scrim.** `COMPONENTS.md` line 523 specifies `color.overlay.scrim` behind the dialog, not
 * plain black — but [Dialog]'s platform window dim has no color-override parameter in this Compose
 * version, so this composable draws its own full-screen [MaterialTheme.colorScheme.scrim] background
 * behind the dialog card instead. That alone is not enough: the platform [android.view.Window] this
 * [Dialog] creates applies its *own* dim (`FLAG_DIM_BEHIND`, default `dimAmount` ~0.6, always plain
 * black) underneath whatever Compose content is drawn inside it, regardless of
 * [DialogProperties.usePlatformDefaultWidth] (which only affects width/sizing measurement, not the
 * window's dim). So the real fix is to reach the [DialogWindowProvider]'s [android.view.Window] and
 * call `setDimAmount(0f)` on it, removing the platform dim entirely so the custom scrim Box is the
 * *only* darkening applied. [DialogProperties.decorFitsSystemWindows] is also set `false` so the dialog
 * window draws edge-to-edge under the system status/nav bars, letting the full-screen scrim Box cover
 * those strips too instead of leaving them at the bare, undimmed platform default — set via that
 * *property* (not a raw `WindowCompat.setDecorFitsSystemWindows(window, false)` call from the
 * `SideEffect` below) because Compose's own `AndroidDialog_androidKt` reapplies
 * `window.setDecorFitsSystemWindows` from this property on every update, which silently stomps a manual
 * override back to `true` on the very next recomposition. Even with that property set, a *Dialog*
 * window's own WindowManager-allocated frame is still fit to the "safe" content area by default (unlike
 * an Activity window), so `WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS` is also set (this one *is*
 * safe to set imperatively — Compose doesn't manage or reset this flag) to actually extend the window's
 * bounds behind the bars. Tapping the scrim calls [onDismissRequest]; the card itself consumes its own
 * taps (a no-op `clickable`) so a tap inside the dialog doesn't fall through to the scrim's dismiss
 * handler.
 *
 * **Motion (disclosed partial implementation).** Open animates scale 0.95->1 + fade over
 * `motion.duration.normal` + `easing.decelerate`, driven by an internal `visible` flag set true in a
 * [LaunchedEffect] right after first composition (the same pattern [SuccessState] uses). The spec's
 * close motion (`motion.duration.fast` + `easing.accelerate`, scale-out + fade-out) is **not**
 * reproduced: [Dialog] is removed from composition the instant the caller stops rendering it (e.g.
 * flips `showDialog` to `false`), which disposes this composable immediately — there's no exit
 * transition to hook without a materially more complex "delay real removal until an exit animation
 * finishes" pattern. Disclosed here rather than silently dropped, matching this kit's own established
 * precedent (see `MentoraSnackbar.kt`'s kdoc on its own undone "paused on interaction" behavior).
 */
@Composable
fun AppDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String? = null,
    onDismissClick: (() -> Unit)? = null,
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = MentoraMotionDuration.normal, easing = MentoraMotionEasing.decelerate),
        label = "app-dialog-alpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.95f,
        animationSpec = tween(durationMillis = MentoraMotionDuration.normal, easing = MentoraMotionEasing.decelerate),
        label = "app-dialog-scale",
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        // decorFitsSystemWindows = false here (Compose's own official DialogProperties channel for
        // this), NOT a raw WindowCompat.setDecorFitsSystemWindows(window, false) call from a SideEffect
        // below — Compose's own AndroidDialog_androidKt reapplies `window.setDecorFitsSystemWindows`
        // from this property on every update, which would silently stomp a manual override back to this
        // property's own default (true) on the very next recomposition (confirmed via
        // `adb shell dumpsys window windows`: a manual override left the dialog window's own
        // WindowManager.LayoutParams `fitTypes` still listing STATUS_BARS/NAVIGATION_BARS even after
        // calling setDecorFitsSystemWindows(false) directly).
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        SideEffect {
            val window = (view.parent as? DialogWindowProvider)?.window
            // Zero the platform's own FLAG_DIM_BEHIND dim — without this, the custom scrim Box below is
            // drawn ON TOP OF the platform's still-active ~60%-black dim rather than replacing it. See
            // this file's kdoc "Custom scrim" note.
            window?.setDimAmount(0f)
            // decorFitsSystemWindows = false (above) is still not sufficient on its own for a *Dialog*
            // window to actually extend behind the system bars (unlike an Activity window, a Dialog's
            // WindowManager-allocated frame is fit to the "safe" content area by default regardless);
            // FLAG_LAYOUT_NO_LIMITS is also required so the full-screen scrim Box below can cover the
            // status/nav bar strips too, instead of leaving them at the bare, undimmed platform default.
            window?.setFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag(AppDialogScrimTestTag)
                // color.overlay.scrim — see this file's kdoc "Custom scrim" note: with the platform dim
                // zeroed via setDimAmount(0f) above, this is now the only darkening applied.
                .background(MaterialTheme.colorScheme.scrim)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismissRequest,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = modifier
                    .testTag(AppDialogSurfaceTestTag)
                    .padding(horizontal = MentoraDimens.spacing.space4)
                    .widthIn(max = 400.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        scaleX = scale
                        scaleY = scale
                    }
                    // Consumes its own taps so a tap inside the dialog card doesn't fall through to the
                    // scrim Box's dismiss-on-click-outside handler above.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(MentoraRadiusTokens.xlarge),
                color = MaterialTheme.colorScheme.surfaceContainerHigh, // color.surface.elevated.
                // shadowElevation (a real drop-shadow), not tonalElevation — this kit's own established
                // "borders over shadow"/no-tonal-tint pattern (see MentoraSnackbar.kt). MentoraTheme.kt
                // deliberately leaves surfaceTint mapped to colorScheme.primary (brand purple), so a
                // nonzero tonalElevation here would composite a visible purple wash on top of
                // surfaceContainerHigh via Surface's surfaceColorAtElevation — shadowElevation carries the
                // same elevation.4 depth without shifting the rendered background color away from the
                // spec'd surface.elevated token.
                shadowElevation = MentoraElevationTokens.level4,
            ) {
                Column(
                    modifier = Modifier.padding(MentoraDimens.spacing.space6),
                    verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space4),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall, // heading.h3.
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Full-width stacked buttons — the mobile branch of COMPONENTS.md line 526 (see this
                    // file's kdoc). Android always takes this branch; there is no right-aligned row here.
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
                    ) {
                        if (dismissLabel != null) {
                            MentoraTextButton(
                                text = dismissLabel,
                                onClick = onDismissClick ?: onDismissRequest,
                                modifier = Modifier.fillMaxWidth().testTag(AppDialogDismissButtonTestTag),
                            )
                        }
                        PrimaryButton(
                            text = confirmLabel,
                            onClick = onConfirm,
                            modifier = Modifier.fillMaxWidth().testTag(AppDialogConfirmButtonTestTag),
                        )
                    }
                }
            }
        }
    }
}
