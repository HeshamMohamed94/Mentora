package com.mentora.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.MentoraMotionDuration
import java.util.Locale
import kotlin.math.roundToInt

/**
 * `design-system/COMPONENTS.md` § CourseCard (lines 274-303). Content hierarchy top to bottom:
 * thumbnail (16:9, top corners clipped) → category chip overlay → title (`heading.h4`, 2-line
 * ellipsis) → instructor name (`body.small`, `text.secondary`) → optional rating/student-count/
 * duration meta row → optional progress bar (only if [isEnrolled]) → full-width [TonalButton]
 * primary action.
 *
 * **Shell shared with [CourseProgressCard]** via the internal [BaseCourseCard] below — same radius/
 * border/elevation/padding/pressed-scale, differing only in which optional rows render.
 *
 * **Meta row icon gap (disclosed, matches web's own precedent).** The design spec calls for
 * rating/student-count/duration icons at `icon.small`. The ported 42-icon set
 * ([MentoraIcons]/`web/src/components/ui/icon.tsx`) has no star or clock/duration glyph — verified,
 * neither exists under any name. `web/src/components/ui/course-card.tsx` hits the identical gap and
 * resolves it with a literal `★` glyph for rating (no icon at all) rather than inventing a new SVG;
 * [CourseMetaRow] below follows that exact precedent (a plain `★` text glyph for rating, the
 * already-existing [MentoraIconName.People] for student count, plain text with no icon for
 * duration) instead of fabricating icons this task's brief doesn't authorize adding.
 *
 * **Pressed state:** scale 0.98 over `motion.duration.fast`, via `graphicsLayer`/
 * `animateFloatAsState` on the card's own `interactionSource`, per the spec's "Pressed | scale 0.98
 * (`motion.duration.fast`)" row — not Compose's default ripple treatment.
 */
@Composable
internal fun BaseCourseCard(
    title: String,
    instructorName: String,
    seed: String,
    categoryId: String?,
    categoryLabel: String,
    mediaId: String?,
    thumbnailUrl: String?,
    thumbnailContentDescription: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    metaRow: (@Composable () -> Unit)? = null,
    progress: Float? = null,
    progressLabel: String? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1f,
        animationSpec = tween(durationMillis = MentoraMotionDuration.fast),
        label = "course-card-scale",
    )

    Surface(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .let {
                if (onClick != null) {
                    it.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                } else {
                    it
                }
            },
        shape = MaterialTheme.shapes.medium, // radius.large (16).
        color = MaterialTheme.colorScheme.surface,
        // elevation.1 resting: border only, no tonalElevation — MentoraTheme.kt deliberately leaves
        // surfaceTint mapped to colorScheme.primary, so any nonzero tonalElevation on a
        // colorScheme.surface Surface visibly tints it purple via surfaceColorAtElevation (measured:
        // #F7F6FD instead of pure white in light theme). Matches this kit's own sibling cards
        // (StatCard.kt, CertificateCard.kt), which realize their resting elevation with a border alone,
        // not a shadow/tonal elevation this low.
        border = BorderStroke(MentoraDimens.borderWidthDefault, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            CourseArtworkWithChip(
                seed = seed,
                categoryId = categoryId,
                categoryLabel = categoryLabel,
                contentDescription = thumbnailContentDescription,
                mediaId = mediaId,
                thumbnailUrl = thumbnailUrl,
                thumbnailShape = CourseThumbnailTopCornersShape,
                modifier = Modifier.fillMaxWidth(),
            )
            Column(
                modifier = Modifier.padding(MentoraDimens.spacing.space4),
                verticalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space2),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge, // heading.h4.
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = instructorName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, // CONTENT_RESILIENCE.md § 1 — single-line for grid-rhythm reasons.
                    overflow = TextOverflow.Ellipsis,
                )
                metaRow?.invoke()
                if (progress != null) {
                    MentoraProgressBar(progress = progress, contentDescriptionLabel = progressLabel)
                    if (progressLabel != null) {
                        Text(
                            text = progressLabel,
                            style = MaterialTheme.typography.labelSmall, // typography.caption.
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                TonalButton(
                    text = actionLabel,
                    onClick = onActionClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** Rating (`★`, see file kdoc) + student count ([MentoraIconName.People]) + duration (plain text) —
 *  any subset may be null; only non-null entries render, space-separated. */
@Composable
internal fun CourseMetaRow(
    rating: Float?,
    studentCount: Int?,
    durationLabel: String?,
    modifier: Modifier = Modifier,
) {
    if (rating == null && studentCount == null && durationLabel == null) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
    ) {
        rating?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1)) {
                Text(text = "★", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // T19 review fix (MEDIUM, D94): `Locale.getDefault(FORMAT)` — the implicit locale an
                // unqualified `String.format` uses — resolves to Arabic-Indic digits on an Arabic
                // device, violating design-system/LOCALIZATION.md § 8's locked "Western numerals
                // everywhere" rule. `Locale.US` pinned, same precedent as `PlayerControls
                // .formatPlaybackTime`/`CertificateFormatting`'s own identical fix.
                Text(text = String.format(Locale.US, "%.1f", it), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        studentCount?.let {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space1)) {
                MentoraIcon(name = MentoraIconName.People, contentDescription = null, size = MentoraDimens.iconSize.small, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(text = it.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        durationLabel?.let {
            Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun CourseCard(
    title: String,
    instructorName: String,
    seed: String,
    categoryId: String?,
    categoryLabel: String,
    thumbnailContentDescription: String,
    actionLabel: String,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier,
    mediaId: String? = null,
    thumbnailUrl: String? = null,
    onClick: (() -> Unit)? = null,
    rating: Float? = null,
    studentCount: Int? = null,
    durationLabel: String? = null,
    /** `COMPONENTS.md` line 295: "Progress bar (only if enrolled)". `progress` is only rendered when
     *  [isEnrolled] is true, regardless of whether the caller also passed a value while not
     *  enrolled — the conditional is the single source of truth, not the nullability of [progress]
     *  alone, so a caller can't accidentally show a stale progress bar on a not-yet-enrolled card. */
    isEnrolled: Boolean = false,
    progress: Float? = null,
    progressLabelFormatter: (Float) -> String = { p -> "${(p * 100).roundToInt()}% complete" },
) {
    val effectiveProgress = if (isEnrolled) progress else null
    BaseCourseCard(
        title = title,
        instructorName = instructorName,
        seed = seed,
        categoryId = categoryId,
        categoryLabel = categoryLabel,
        mediaId = mediaId,
        thumbnailUrl = thumbnailUrl,
        thumbnailContentDescription = thumbnailContentDescription,
        actionLabel = actionLabel,
        onActionClick = onActionClick,
        modifier = modifier,
        onClick = onClick,
        metaRow = if (rating != null || studentCount != null || durationLabel != null) {
            { CourseMetaRow(rating = rating, studentCount = studentCount, durationLabel = durationLabel) }
        } else {
            null
        },
        progress = effectiveProgress,
        progressLabel = effectiveProgress?.let { progressLabelFormatter(it) },
    )
}
