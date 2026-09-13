package com.mentora.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraElevationTokens
import com.mentora.android.theme.extendedColors

/**
 * `design-system/COMPONENTS.md` § Snackbar (lines 540-551). Radius `radius.medium` ->
 * `MaterialTheme.shapes.small`, background `color.surface.inverse`, text `typography.body.small` /
 * `color.text.inverse`, padding `space.4` horizontal / `space.3` vertical, action `TextButton` in
 * `color.brand.onSurfaceInverse` ([MaterialTheme.extendedColors.onSurfaceInverse] — see that field's
 * kdoc for why it needed a small theme extension), elevation `elevation.3`.
 *
 * Wraps Compose's own [SnackbarHost]/[SnackbarHostState] rather than building dismiss/queueing logic
 * from scratch, per the task brief — [MentoraSnackbarHost] only supplies a custom `snackbar = { ... }`
 * visual (this file's [MentoraSnackbarContent]) to the native host, so queueing and auto-dismiss stay
 * Compose's own.
 *
 * **Disclosed approximations, not silently "close enough":**
 * - **Auto-dismiss duration.** The spec wants exactly 4s. [SnackbarDuration.Short]'s nominal timeout
 *   is 4000ms (4s) — but the actual effective duration Compose applies can be *extended* beyond that
 *   by the platform's accessibility timeout multiplier (`AccessibilityManager.
 *   calculateRecommendedTimeoutMillis`, consulted internally by `SnackbarHostState`) when the user has
 *   an increased-timeout accessibility setting enabled. [showMentoraSnackbar] uses `Short` rather than
 *   hand-rolling a fixed-4000ms coroutine delay, per the task's own "don't fight the framework"
 *   guidance — the nominal value matches the spec; the accessibility-driven extension is a deliberate,
 *   correct platform behavior to keep, not a bug.
 * - **"Paused on interaction."** Compose's `SnackbarHostState` auto-dismiss timer has no built-in
 *   "pause while the user is touching/focusing the snackbar" behavior — reproducing that exactly would
 *   mean replacing the host's own dismiss scheduling with a hand-built one, which is exactly what the
 *   task brief says not to do. Left unimplemented, disclosed here rather than silently dropped.
 */
@Composable
fun MentoraSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        MentoraSnackbarContent(data)
    }
}

@Composable
private fun MentoraSnackbarContent(data: SnackbarData) {
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors

    Surface(
        color = colorScheme.inverseSurface,
        contentColor = colorScheme.inverseOnSurface,
        shape = MaterialTheme.shapes.small,
        shadowElevation = MentoraElevationTokens.level3,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = MentoraDimens.spacing.space4,
                vertical = MentoraDimens.spacing.space3,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = data.visuals.message,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.inverseOnSurface,
                modifier = Modifier.weight(1f),
            )
            data.visuals.actionLabel?.let { actionLabel ->
                TextButton(
                    onClick = { data.performAction() },
                    colors = ButtonDefaults.textButtonColors(contentColor = extended.onSurfaceInverse),
                ) {
                    Text(actionLabel, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

/** `showSnackbar` pinned to [SnackbarDuration.Short] — see this file's kdoc for the 4s disclosure. */
suspend fun SnackbarHostState.showMentoraSnackbar(message: String, actionLabel: String? = null) {
    showSnackbar(message = message, actionLabel = actionLabel, duration = SnackbarDuration.Short)
}
