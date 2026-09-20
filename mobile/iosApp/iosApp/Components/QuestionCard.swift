import SwiftUI
import shared

// Phase 5 Task T11 slice 3 (Component Kit B) -- `design-system/COMPONENTS.md § QuestionCard` (lines
// 716-720): "same shell as a StatCard" (radius `radius.large`, border `color.border.default`, padding
// `space.5`), containing a "Question N of M" progress indicator (`typography.caption`) paired with a
// slim `MentoraProgressBar`, then the question text (`heading.h4`). Ported directly from Android's own
// `QuestionCard.kt`.
//
// Pure presentational -- `questionNumber`/`totalQuestions`/`questionText` are plain values; a later
// task wires real quiz domain models into these parameters, not this component's job.
enum QuestionCardRules {
    /// `totalQuestions > 0 ? questionNumber / totalQuestions : 0` -- a directly-testable divide-by-zero
    /// guard, mirroring Android's own identical inline conditional exactly.
    static func progressFraction(questionNumber: Int, totalQuestions: Int) -> Double {
        guard totalQuestions > 0 else { return 0 }
        return Double(questionNumber) / Double(totalQuestions)
    }
}

struct QuestionCard: View {
    let questionNumber: Int
    let totalQuestions: Int
    let questionText: String
    /// DISCLOSED DEVIATION from Android's own default-parameter convenience -- see
    /// `CourseProgressCard.swift`'s identical `resumeLabel` doc comment for the full rationale. Real,
    /// pre-existing `question_card_progress_label` key (`%1$@`/`%2$@`, confirmed present in
    /// `Resources/Localizable.xcstrings` before this task began), REQUIRED here, always resolved and
    /// passed in by the caller via `MentoraStrings.text(_:locale:_:)`.
    var progressLabel: String

    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space2) {
            Text(progressLabel)
                .mentoraFont(.caption)
                .foregroundStyle(Color.mentoraTextSecondary)
            MentoraProgressBar(
                progress: QuestionCardRules.progressFraction(questionNumber: questionNumber, totalQuestions: totalQuestions),
                accessibilityLabel: progressLabel
            )
            Text(questionText)
                .mentoraFont(.h4)
                .foregroundStyle(Color.mentoraTextPrimary)
        }
        .padding(MentoraSpacing.space5)
        .background(Color.mentoraSurfaceDefault)
        .clipShape(MentoraShape.large) // radius.large (16).
        .overlay {
            MentoraShape.large.strokeBorder(Color.mentoraBorderDefault, lineWidth: MentoraBorderWidth.default)
        }
    }
}
