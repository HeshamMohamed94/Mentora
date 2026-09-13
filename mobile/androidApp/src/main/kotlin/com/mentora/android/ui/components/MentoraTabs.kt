package com.mentora.android.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mentora.android.theme.MentoraMotionDuration
import com.mentora.android.theme.MentoraMotionEasing
import com.mentora.android.theme.stateOpacities

/**
 * `design-system/COMPONENTS.md` § Tabs (lines 660-673). Spec height is 44 (literal, see `Badge.kt`'s
 * kdoc) — **disclosed deviation (F5):** measured (bounds probe), M3's `Tab` composable has its own
 * 48dp intrinsic minimum height that a parent `TabRow`'s `.height(44.dp)` constraint does not
 * override (the tab content actually renders at 48dp regardless, overflowing 4dp past a nominal
 * 44dp row and misaligning the indicator with the tab's real bottom edge). 48dp is also Android's
 * own control-height minimum (`ACCESSIBILITY.md` § 4), so this component is left at M3's real,
 * internally-consistent 48dp height rather than a literal 44dp that silently overflows — no
 * `Modifier.height()` constraint is applied to [TabRow] here.
 *
 * Typography `label.large`, indicator 2px underline in `color.brand.primary`, animating position via
 * [animateDpAsState] over `motion.duration.normal` + `motion.easing.standard`
 * ([MentoraMotionDuration]/[MentoraMotionEasing]).
 *
 * Built on Material3's `TabRow`/`Tab` for tab-position measurement (each tab's left/width per
 * `TabPosition`) rather than hand-measuring, then a custom indicator `Box` animated from those
 * positions — `TabRow`'s own default indicator does not animate on its own, it simply snaps to the
 * new `tabPositions[selectedIndex]` each recomposition, so the animation here is explicit.
 */
data class MentoraTabOption<T>(val id: T, val label: String, val enabled: Boolean = true)

@Composable
fun <T> MentoraTabs(
    options: List<MentoraTabOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val opacities = MaterialTheme.stateOpacities
    val selectedIndex = options.indexOfFirst { it.id == selected }.coerceAtLeast(0)

    TabRow(
        selectedTabIndex = selectedIndex,
        // No `.height(44.dp)` here — see file kdoc (F5): M3's `Tab` resists shrinking below its own
        // 48dp intrinsic minimum, so a `.height(44.dp)` constraint here would silently overflow
        // rather than actually constrain anything. `modifier` is passed through unconstrained.
        modifier = modifier,
        containerColor = Color.Transparent,
        contentColor = colorScheme.primary,
        indicator = { tabPositions ->
            if (selectedIndex in tabPositions.indices) {
                val position = tabPositions[selectedIndex]
                val animationSpec = tween<Dp>(
                    durationMillis = MentoraMotionDuration.normal,
                    easing = MentoraMotionEasing.standard,
                )
                val indicatorStart by animateDpAsState(position.left, animationSpec, label = "mentora-tab-indicator-start")
                val indicatorWidth by animateDpAsState(position.width, animationSpec, label = "mentora-tab-indicator-width")
                Box(
                    modifier = Modifier
                        .wrapContentSize(Alignment.BottomStart)
                        .offset(x = indicatorStart)
                        .width(indicatorWidth)
                        .height(2.dp) // 2px underline, the spec's own literal.
                        .background(colorScheme.primary),
                )
            }
        },
        divider = {},
    ) {
        options.forEachIndexed { index, option ->
            val disabledText = colorScheme.onSurface.copy(alpha = opacities.disabledContent)
            val textColor = when {
                !option.enabled -> disabledText
                index == selectedIndex -> colorScheme.onSurface // Active -> color.text.primary.
                else -> colorScheme.onSurfaceVariant // Default -> color.text.secondary.
            }
            Tab(
                selected = index == selectedIndex,
                onClick = { if (option.enabled) onSelect(option.id) },
                enabled = option.enabled,
                text = { Text(option.label, style = MaterialTheme.typography.labelLarge, color = textColor) },
            )
        }
    }
}
