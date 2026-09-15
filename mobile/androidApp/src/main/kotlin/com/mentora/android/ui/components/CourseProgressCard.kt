package com.mentora.android.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.mentora.android.R
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
    // T19 review fix (LOW-1, D94): generalizing ErrorState.retryLabel's own HIGH fix — see that
    // component's kdoc. `progressLabelFormatter`'s own English literal is untouched here — it's a
    // pre-existing, already-disclosed gap (every real call site overrides it; no localized formatter
    // exists yet), not part of this fix's scope.
    resumeLabel: String = stringResource(R.string.course_progress_card_resume_label),
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
