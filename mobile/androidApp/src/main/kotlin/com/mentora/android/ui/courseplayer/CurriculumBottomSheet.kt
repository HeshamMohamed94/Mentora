package com.mentora.android.ui.courseplayer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mentora.android.R
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors
import com.mentora.android.ui.components.MentoraIcon
import com.mentora.android.ui.components.MentoraIconButton
import com.mentora.android.ui.components.MentoraIconName
import com.mentora.android.ui.components.MentoraBottomSheet

/**
 * Task 13 "C3" — `execution/DECISIONS_LOG.md` D85 Decision 8's Curriculum Bottom Sheet. Built on
 * [MentoraBottomSheet] **unchanged** (that decision's own instruction — its drag handle,
 * top-corners-only radius, `surface.elevated`, T8 border-over-shadow treatment, and `space.5` padding
 * are already exactly right; "no component change is needed or permitted"). Two concrete cautions that
 * decision calls out, both followed here: the lesson list is a [LazyColumn] with an explicit
 * `heightIn(max = ...)` derived from [LocalConfiguration] (the content slot is a `ColumnScope` — an
 * unbounded `LazyColumn` inside a `Column` is the classic "measured with infinity maximum height"
 * crash), and [MentoraBottomSheet]'s own default `rememberModalBottomSheetState()`
 * (`skipPartiallyExpanded = false`) is left untouched so the partial-height default
 * (`ux/MOBILE_UX.md § 7`) applies. [MaxSheetListHeightFraction] caps the list at roughly "several
 * lessons visible, scrollable" rather than the full screen height, matching that same rule.
 *
 * Row tap calls [onLessonSelected] AND dismisses the sheet — D85 Decision 8's own instruction; this
 * composable stays a plain "row tap invokes the callback" component (matching every other
 * stateless-presentation composable in this codebase), so the DISMISS half of that instruction is
 * composed by the caller (`CoursePlayerScreen.kt` passes an [onLessonSelected]/`onTakeQuiz` lambda
 * that both forwards to the ViewModel/nav callback AND closes the sheet). Swipe/scrim dismiss (handled
 * entirely by [MentoraBottomSheet]/[onDismissRequest]) never navigates anywhere on its own. The quiz
 * row (only rendered when [CurriculumSheetState.quizRow] is
 * non-null — `null` means [com.mentora.shared.domain.usecase.quiz.QuizLookupResult.NoQuiz], a
 * legitimate state per that decision) is likewise tappable, reusing the SAME [onTakeQuiz] navigation
 * callback the footer's own "Take Quiz" state already calls (`CoursePlayerScreen.kt`) — not spelled
 * out verbatim by D85 Decision 8, but the natural, already-wired extension of "row tap navigates and
 * dismisses" to the one other navigable row this sheet renders, rather than a dead informational row.
 *
 * **Disclosed content gaps (D85 Decision 8's own "Three content gaps" section — not fabricated
 * here):** no per-lesson duration label (`Lesson.duration` does not exist anywhere in `shared`'s
 * domain model or any read endpoint); no `lock_open`/`quiz` leading glyph on `NotStarted` lesson rows
 * or the quiz row (no equivalent exists in the ported 42-icon set, `MentoraIcons.kt`'s own G8/D80
 * disclosure) — a blank leading-icon-width [Spacer]-equivalent keeps row text alignment consistent
 * instead. Completed/Current rows DO get a real icon ([MentoraIconName.CheckCircle]/
 * [MentoraIconName.Play]) — completion state is icon PLUS text, never colour alone
 * (`ux/SCREEN_UX_SPECS.md § 421`, cited by D85 Decision 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurriculumBottomSheet(
    sheet: CurriculumSheetState,
    onLessonSelected: (lessonId: String) -> Unit,
    onTakeQuiz: () -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val maxListHeight = LocalConfiguration.current.screenHeightDp.dp * MaxSheetListHeightFraction

    MentoraBottomSheet(onDismissRequest = onDismissRequest, modifier = modifier.testTag(CurriculumBottomSheetTestTag)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.course_player_curriculum_sheet_title),
                style = MaterialTheme.typography.headlineSmall, // heading.h4-equivalent per showcase.
                color = MaterialTheme.colorScheme.onSurface,
            )
            MentoraIconButton(
                icon = MentoraIconName.Close,
                contentDescription = stringResource(R.string.course_player_curriculum_sheet_close_content_description),
                onClick = onDismissRequest,
                modifier = Modifier.testTag(CurriculumBottomSheetCloseButtonTestTag),
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxListHeight)
                .padding(top = MentoraDimens.spacing.space3),
        ) {
            sheet.sections.forEach { section ->
                item(key = "section-${section.sectionId}") {
                    Text(
                        text = stringResource(
                            R.string.course_player_sheet_section_header,
                            section.order.toString(),
                            section.title,
                        ),
                        style = MaterialTheme.typography.labelSmall, // typography.caption.
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            horizontal = MentoraDimens.spacing.space3,
                            vertical = MentoraDimens.spacing.space2,
                        ),
                    )
                }
                items(section.lessons, key = { it.lessonId }) { lesson ->
                    CurriculumLessonRow(
                        lesson = lesson,
                        onClick = { onLessonSelected(lesson.lessonId) },
                    )
                }
            }
            sheet.quizRow?.let { quizRow ->
                item(key = "quiz-row") {
                    CurriculumQuizRow(quizRow = quizRow, onClick = onTakeQuiz)
                }
            }
        }
    }
}

@Composable
private fun CurriculumLessonRow(lesson: SheetLesson, onClick: () -> Unit) {
    val isCurrent = lesson.state == SheetLessonState.Current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MentoraDimens.touchTargetMin)
            .clip(MaterialTheme.shapes.medium)
            .let { if (isCurrent) it.background(MaterialTheme.colorScheme.primaryContainer) else it }
            .clickable(onClick = onClick)
            .padding(horizontal = MentoraDimens.spacing.space3, vertical = MentoraDimens.spacing.space2)
            .testTag(CurriculumLessonRowTestTag(lesson.lessonId)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
    ) {
        CurriculumRowLeadingSlot {
            when (lesson.state) {
                SheetLessonState.Completed -> MentoraIcon(
                    name = MentoraIconName.CheckCircle,
                    contentDescription = stringResource(R.string.course_player_sheet_lesson_completed_content_description),
                    tint = MaterialTheme.extendedColors.success,
                )
                SheetLessonState.Current -> MentoraIcon(
                    name = MentoraIconName.Play,
                    contentDescription = stringResource(R.string.course_player_sheet_lesson_current_content_description),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                // No lock_open equivalent in the ported icon set — see this file's own kdoc.
                SheetLessonState.NotStarted -> Unit
            }
        }
        Text(
            text = stringResource(R.string.course_player_sheet_lesson_row, lesson.globalIndex.toString(), lesson.title),
            style = if (isCurrent) MaterialTheme.typography.labelLarge else MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CurriculumQuizRow(quizRow: SheetQuizRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MentoraDimens.touchTargetMin)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = MentoraDimens.spacing.space3, vertical = MentoraDimens.spacing.space2)
            .testTag(CurriculumQuizRowTestTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
    ) {
        // No `quiz` glyph equivalent in the ported icon set — see this file's own kdoc.
        CurriculumRowLeadingSlot {}
        Text(
            text = stringResource(R.string.course_player_sheet_quiz_row_title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.course_player_sheet_quiz_row_question_count, quizRow.questionCount.toString()),
            style = MaterialTheme.typography.labelSmall, // typography.caption.
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Fixed-width leading slot every row reserves (whether or not it renders an icon) so lesson/quiz row
 *  TEXT stays aligned to the same start position regardless of which rows do/don't have a leading
 *  glyph (see this file's own kdoc on the disclosed missing `lock_open`/`quiz` icons). */
@Composable
private fun CurriculumRowLeadingSlot(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.size(MentoraDimens.iconSize.default),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}

/** `ux/MOBILE_UX.md § 7`'s partial-height default ("several lessons visible, scrollable") — this
 *  fraction of the screen's own height, not the full screen (the showcase's own captured frame shows
 *  it fully expanded instead, D85 Decision 8's own disclosed `knownGaps[0]` — not independently
 *  pixel-verified against a partial-height capture). */
private const val MaxSheetListHeightFraction = 0.6f

private fun CurriculumLessonRowTestTag(lessonId: String) = "course-player-sheet-lesson-$lessonId"

// Test-only hooks (`ui.test.onNodeWithTag`), unused by production code otherwise.
const val CurriculumBottomSheetTestTag = "course-player-curriculum-sheet"
const val CurriculumBottomSheetCloseButtonTestTag = "course-player-curriculum-sheet-close"
const val CurriculumQuizRowTestTag = "course-player-sheet-quiz-row"
