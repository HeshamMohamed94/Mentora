package com.mentora.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.mentora.android.theme.MentoraDimens
import com.mentora.android.theme.extendedColors
import com.mentora.android.theme.stateOpacities

/** The 5 states `design-system/COMPONENTS.md` § Answer Options (lines 722-734) defines. */
enum class AnswerOptionState { Default, SelectedUnsubmitted, Correct, Incorrect, DisabledPostSubmit }

/** Test-only hooks (`ui.test.onNodeWithTag`) for asserting the Correct/Incorrect icon actually
 *  renders alongside the text+color signal — mirrors [MentoraFieldErrorIconTestTag]'s rigor. */
const val AnswerOptionCorrectIconTestTag = "mentora-answer-option-correct-icon"
const val AnswerOptionIncorrectIconTestTag = "mentora-answer-option-incorrect-icon"

/**
 * Resolves the 5-state table from plain booleans: not submitted yet -> Default/SelectedUnsubmitted;
 * submitted -> every option matching the real correct answer renders Correct (revealing which one
 * was right, whether or not the user picked it), the user's own wrong pick renders Incorrect, and
 * every other unselected/non-correct option renders DisabledPostSubmit — the standard "reveal the
 * right answer" quiz-result pattern the spec's state names imply (`Correct (after submit)` /
 * `Incorrect (after submit)` are keyed to `isCorrectAnswer`, not to `isSelected` alone).
 */
fun answerOptionStateFor(isSelected: Boolean, isSubmitted: Boolean, isCorrectAnswer: Boolean): AnswerOptionState = when {
    !isSubmitted && isSelected -> AnswerOptionState.SelectedUnsubmitted
    !isSubmitted -> AnswerOptionState.Default
    isCorrectAnswer -> AnswerOptionState.Correct
    isSelected -> AnswerOptionState.Incorrect
    else -> AnswerOptionState.DisabledPostSubmit
}

/**
 * `design-system/COMPONENTS.md` § Answer Options. Full-width row, height 52 (literal component-table
 * value), radius `radius.medium`, border `border.width.default`, padding `space.4` horizontal.
 *
 * **"Never color alone" (`ACCESSIBILITY.md` § 8 / the spec's own explicit rule).** [Correct]/
 * [Incorrect] each render **icon + text + color together**: [MentoraIconName.CheckCircle]/
 * `correctLabel`/`color.success.default` for Correct, [MentoraIconName.Cancel]/`incorrectLabel`/
 * `color.error.default` for Incorrect — three simultaneous, independent signals, mirroring this
 * kit's own [MentoraTextField] error-state rigor (icon+text+color together, never a bare colored
 * border). [SelectedUnsubmitted] similarly pairs its accent color with a filled check icon (the
 * spec's "radio/check filled" — reusing [MentoraIconName.CheckCircle] since no separate radio glyph
 * exists in the ported 42-icon set, rather than fabricating a new one).
 *
 * Pure presentational: [isSelected]/[isSubmitted]/[isCorrectAnswer] are plain booleans, not real quiz
 * domain models — Task 14 wires those in.
 */
@Composable
fun AnswerOption(
    text: String,
    isSelected: Boolean,
    isSubmitted: Boolean,
    isCorrectAnswer: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    correctLabel: String = "Correct",
    incorrectLabel: String = "Incorrect",
) {
    val state = answerOptionStateFor(isSelected, isSubmitted, isCorrectAnswer)
    val colorScheme = MaterialTheme.colorScheme
    val extended = MaterialTheme.extendedColors
    val opacities = MaterialTheme.stateOpacities
    val disabledText = colorScheme.onSurface.copy(alpha = opacities.disabledContent)

    val background = when (state) {
        AnswerOptionState.Default -> colorScheme.surface
        AnswerOptionState.SelectedUnsubmitted -> colorScheme.primaryContainer
        AnswerOptionState.Correct -> extended.successContainer
        AnswerOptionState.Incorrect -> colorScheme.errorContainer
        AnswerOptionState.DisabledPostSubmit -> colorScheme.surfaceVariant
    }
    val border = when (state) {
        AnswerOptionState.Default -> colorScheme.outlineVariant
        AnswerOptionState.SelectedUnsubmitted -> colorScheme.primary
        AnswerOptionState.Correct -> extended.success
        AnswerOptionState.Incorrect -> colorScheme.error
        AnswerOptionState.DisabledPostSubmit -> colorScheme.outlineVariant
    }
    val contentColor = when (state) {
        AnswerOptionState.Default -> colorScheme.onSurface
        AnswerOptionState.SelectedUnsubmitted -> colorScheme.onPrimaryContainer
        AnswerOptionState.Correct -> extended.onSuccessContainer
        AnswerOptionState.Incorrect -> colorScheme.onErrorContainer
        AnswerOptionState.DisabledPostSubmit -> disabledText
    }
    val clickable = state == AnswerOptionState.Default || state == AnswerOptionState.SelectedUnsubmitted

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp) // component.answerOption.height (52), a literal.
            .clip(MaterialTheme.shapes.small) // radius.medium.
            .background(background)
            .border(BorderStroke(MentoraDimens.borderWidthDefault, border), MaterialTheme.shapes.small)
            .let { if (clickable) it.clickable(onClick = onClick) else it }
            .padding(horizontal = MentoraDimens.spacing.space4)
            .semantics {
                if (!clickable) disabled()
                when (state) {
                    AnswerOptionState.Correct -> stateDescription = correctLabel
                    AnswerOptionState.Incorrect -> stateDescription = incorrectLabel
                    else -> {}
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MentoraDimens.spacing.space3),
    ) {
        when (state) {
            AnswerOptionState.SelectedUnsubmitted -> MentoraIcon(
                name = MentoraIconName.CheckCircle,
                contentDescription = null,
                size = MentoraDimens.iconSize.medium,
                tint = colorScheme.primary,
            )
            AnswerOptionState.Correct -> MentoraIcon(
                name = MentoraIconName.CheckCircle,
                contentDescription = null,
                modifier = Modifier.testTag(AnswerOptionCorrectIconTestTag),
                size = MentoraDimens.iconSize.medium,
                tint = extended.success,
            )
            AnswerOptionState.Incorrect -> MentoraIcon(
                name = MentoraIconName.Cancel,
                contentDescription = null,
                modifier = Modifier.testTag(AnswerOptionIncorrectIconTestTag),
                size = MentoraDimens.iconSize.medium,
                tint = colorScheme.error,
            )
            else -> {}
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            modifier = Modifier.weight(1f),
        )
        if (state == AnswerOptionState.Correct) {
            Text(text = correctLabel, style = MaterialTheme.typography.labelMedium, color = extended.success)
        }
        if (state == AnswerOptionState.Incorrect) {
            Text(text = incorrectLabel, style = MaterialTheme.typography.labelMedium, color = colorScheme.error)
        }
    }
}
