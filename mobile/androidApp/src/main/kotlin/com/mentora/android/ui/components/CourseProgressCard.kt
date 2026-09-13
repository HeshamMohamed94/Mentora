package com.mentora.android.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import kotlin.math.roundToInt

/**
 * `design-system/COMPONENTS.md` § CourseProgressCard (lines 305-307): "same shell as CourseCard —
 * always shows `ProgressBar` + '% complete' + 'Resume' `TonalButton`, and drops rating/student-count
 * row." Delegates to the same internal [BaseCourseCard] as [CourseCard] (shared shell, per that
 * file's own kdoc), always passing `progress`/`progressLabel`, never a `metaRow`.
 */
@Composable
fun CourseProgressCard(
    title: String,
    instructorName: String,
    seed: String,
    categoryId: String?,
    categoryLabel: String,
    thumbnailContentDescription: String,
    progress: Float,
    onResumeClick: () -> Unit,
    modifier: Modifier = Modifier,
    mediaId: String? = null,
    thumbnailUrl: String? = null,
    onClick: (() -> Unit)? = null,
    resumeLabel: String = "Resume",
    progressLabelFormatter: (Float) -> String = { p -> "${(p * 100).roundToInt()}% complete" },
) {
    BaseCourseCard(
        title = title,
        instructorName = instructorName,
        seed = seed,
        categoryId = categoryId,
        categoryLabel = categoryLabel,
        mediaId = mediaId,
        thumbnailUrl = thumbnailUrl,
        thumbnailContentDescription = thumbnailContentDescription,
        actionLabel = resumeLabel,
        onActionClick = onResumeClick,
        modifier = modifier,
        onClick = onClick,
        metaRow = null, // dropped per spec.
        progress = progress,
        progressLabel = progressLabelFormatter(progress),
    )
}
