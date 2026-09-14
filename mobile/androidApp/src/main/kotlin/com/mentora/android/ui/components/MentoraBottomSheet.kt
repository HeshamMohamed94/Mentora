package com.mentora.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mentora.android.locale.WithCurrentAppLocale
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraRadiusTokens

/** Test-only hook for measuring the sheet's own rendered background color (Finding 2 verification). */
const val MentoraBottomSheetSurfaceTestTag = "mentora-bottom-sheet-surface"

/**
 * `design-system/COMPONENTS.md` § BottomSheet (lines 529-538). `radius.xlarge` (24) **top corners
 * only** — finally resolving `MentoraTheme.kt`'s Task 2 TODO on this exact point (that file's
 * `MentoraShapes.large` comment: "apply the top-corners-only override at the sheet/dialog call site
 * when that task lands" — this is that call site). `surface.elevated`, `elevation.4`, `space.5`
 * content padding (plus Material3's own safe-area-aware `contentWindowInsets` default, left
 * un-overridden so the platform's real bottom-inset/keyboard-avoidance behavior applies rather than
 * a hand-rolled approximation), 32×4 drag handle in `border.strong`.
 *
 * Built on Material3's own [ModalBottomSheet] (styled to these tokens), not a from-scratch overlay.
 *
 * **Elevation (disclosed API constraint).** Unlike [AppDialog]/[CourseCard] — which sit on a plain
 * [androidx.compose.material3.Surface] and can swap `tonalElevation` for a real `shadowElevation` to
 * avoid `MentoraTheme.kt`'s deliberate `surfaceTint = colorScheme.primary` visibly tinting
 * `surface.elevated` purple — Compose Material3 1.4.0's [ModalBottomSheet] does not expose a
 * `shadowElevation` parameter at all (its internal sheet `Surface` is only ever given a
 * `tonalElevation`, verified against `material3-android-1.4.0-sources.jar`). `tonalElevation = 0.dp`
 * here means "no tonal tint" (the correct fix for the measured lavender-wash bug), at the cost of the
 * spec's `elevation.4` shadow depth cue, which this API surface has no way to reproduce untinted. A
 * top border (`border.default`) is added instead, in this kit's own established "borders over shadow"
 * spirit, as the depth cue this API *can* carry without a color side effect.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentoraBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetShape = RoundedCornerShape(topStart = MentoraRadiusTokens.xlarge, topEnd = MentoraRadiusTokens.xlarge)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.border(
            BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
            shape = sheetShape,
        ),
        sheetState = sheetState,
        shape = sheetShape,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, // color.surface.elevated.
        // No tonalElevation — see this file's kdoc "Elevation (disclosed API constraint)" note above:
        // any nonzero tonalElevation here would visibly tint surfaceContainerHigh purple via
        // surfaceColorAtElevation, since MentoraTheme.kt deliberately leaves surfaceTint mapped to
        // colorScheme.primary.
        tonalElevation = 0.dp,
        dragHandle = { MentoraBottomSheetDragHandle() },
    ) {
        // Tagged here (our own content Column), not on the ModalBottomSheet's own `modifier` param
        // above — that outer modifier is applied to an internal, not-actually-rendered-in-place M3
        // Surface instance in this Compose Material3 version (verified: a testTag placed there resolves
        // to a semantics node reporting stale/zeroed bounds, not the real on-screen sheet), whereas this
        // Column is genuinely part of the rendered content tree at the real on-screen position, letting
        // [MentoraBottomSheetSurfaceTestTag]'s captured pixels reflect the Surface's actual painted
        // `containerColor` showing through this (background-less) Column.
        Column(
            modifier = Modifier
                .testTag(MentoraBottomSheetSurfaceTestTag)
                .padding(MentoraDimens.spacing.space5),
        ) {
            // T18 fix — see `com.mentora.android.locale.LocalizedContent`'s own kdoc, "The Dialog/
            // Popup/BottomSheet gap": `ModalBottomSheet` (built on `Popup`) resets `LocalContext`/
            // `LocalConfiguration` for its own sub-composition, so a `stringResource` call inside
            // [content] (real call sites exist, e.g. `CurriculumBottomSheet`) would otherwise silently
            // fall back to the device's OS locale after a language switch, independent of what the
            // rest of the app correctly shows.
            WithCurrentAppLocale {
                content()
            }
        }
    }
}

@Composable
private fun MentoraBottomSheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = MentoraDimens.spacing.space2)
            .size(width = 32.dp, height = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outline), // color.border.strong.
    )
}
