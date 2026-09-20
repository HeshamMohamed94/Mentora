import SwiftUI
import shared

// Phase 5 Task T11 slice 3 (Component Kit B) -- `design-system/COMPONENTS.md § Answer Options`
// (lines 722-734). Full-width row, height 52 (literal component-table value), radius `radius.medium`,
// border `border.width.default`, padding `space.4` horizontal. Ported directly from Android's own
// `AnswerOption.kt`.
//
// **"Never color alone" (`ACCESSIBILITY.md § 8` / the spec's own explicit rule).** `.correct`/
// `.incorrect` each render **icon + text + color together**: `MentoraIconName.checkCircle`/
// `correctLabel`/`color.success.default` for Correct, `MentoraIconName.cancel`/`incorrectLabel`/
// `color.error.default` for Incorrect -- three simultaneous, independent signals, mirroring this kit's
// own `MentoraTextField` error-state rigor. `.selectedUnsubmitted` similarly pairs its accent color
// with a filled check icon (the spec's "radio/check filled" -- reusing `.checkCircle` since no separate
// radio glyph exists in this kit's 42-icon set, matching Android's own identical disclosed choice).
//
// Pure presentational: `isSelected`/`isSubmitted`/`isCorrectAnswer` are plain booleans, not real quiz
// domain models -- a later task wires those in.

/// The 5 states `design-system/COMPONENTS.md § Answer Options` defines.
enum AnswerOptionState {
    case `default`
    case selectedUnsubmitted
    case correct
    case incorrect
    case disabledPostSubmit
}

enum AnswerOptionRules {
    /// Resolves the 5-state table from plain booleans -- ported verbatim from Android's own
    /// `answerOptionStateFor`, including its exact precedence order (not submitted yet ->
    /// Default/SelectedUnsubmitted; submitted -> every option matching the real correct answer renders
    /// Correct regardless of whether the user picked it, the user's own wrong pick renders Incorrect,
    /// every other option renders DisabledPostSubmit).
    static func state(isSelected: Bool, isSubmitted: Bool, isCorrectAnswer: Bool) -> AnswerOptionState {
        if !isSubmitted && isSelected { return .selectedUnsubmitted }
        if !isSubmitted { return .`default` }
        if isCorrectAnswer { return .correct }
        if isSelected { return .incorrect }
        return .disabledPostSubmit
    }

    /// `.default`/`.selectedUnsubmitted` are the only two clickable states -- matches Android's own
    /// identical `clickable` boolean exactly.
    static func isClickable(_ state: AnswerOptionState) -> Bool {
        state == .`default` || state == .selectedUnsubmitted
    }
}

/// One (background, border, content) resolution for an `AnswerOptionState` -- pure data, directly
/// testable without rendering anything.
struct AnswerOptionColorSet: Equatable {
    let background: Color
    let border: Color
    let content: Color
}

extension AnswerOptionState {
    func colorSet(disabledTextOpacity: Double) -> AnswerOptionColorSet {
        switch self {
        case .`default`:
            return AnswerOptionColorSet(background: .mentoraSurfaceDefault, border: .mentoraBorderDefault, content: .mentoraTextPrimary)
        case .selectedUnsubmitted:
            return AnswerOptionColorSet(background: .mentoraBrandPrimaryContainer, border: .mentoraBrandPrimary, content: .mentoraBrandOnPrimaryContainer)
        case .correct:
            return AnswerOptionColorSet(background: .mentoraSuccessContainer, border: .mentoraSuccessDefault, content: .mentoraSuccessOnSuccessContainer)
        case .incorrect:
            return AnswerOptionColorSet(background: .mentoraErrorContainer, border: .mentoraErrorDefault, content: .mentoraErrorOnErrorContainer)
        case .disabledPostSubmit:
            return AnswerOptionColorSet(background: .mentoraSurfaceVariant, border: .mentoraBorderDefault, content: .mentoraTextPrimary.opacity(disabledTextOpacity))
        }
    }
}

struct AnswerOption: View {
    let text: String
    let isSelected: Bool
    let isSubmitted: Bool
    let isCorrectAnswer: Bool
    let onClick: () -> Void
    /// DISCLOSED DEVIATION from Android's own default-parameter convenience -- see
    /// `CourseProgressCard.swift`'s identical `resumeLabel` doc comment for the full rationale. Real,
    /// pre-existing `answer_option_correct_label` key.
    var correctLabel: String
    /// Same rationale as `correctLabel` above. Real, pre-existing `answer_option_incorrect_label` key.
    var incorrectLabel: String

    @Environment(\.colorScheme) private var colorScheme

    private var state: AnswerOptionState {
        AnswerOptionRules.state(isSelected: isSelected, isSubmitted: isSubmitted, isCorrectAnswer: isCorrectAnswer)
    }

    private var clickable: Bool {
        AnswerOptionRules.isClickable(state)
    }

    private var colors: AnswerOptionColorSet {
        // `MentoraButtonMetrics.StateOpacities` (`MentoraButton.swift`) does not expose
        // `disabledContentOpacity` (only hover/pressed/disabledContainer -- see that struct's own doc
        // comment), so the raw token enum is read directly here rather than widening that struct for
        // one unrelated call site.
        let disabledContentOpacity = colorScheme == .dark ? MentoraStateOpacityDark.disabledContentOpacity : MentoraStateOpacityLight.disabledContentOpacity
        return state.colorSet(disabledTextOpacity: disabledContentOpacity)
    }

    private var accessibilityStateValue: String? {
        switch state {
        case .correct: return correctLabel
        case .incorrect: return incorrectLabel
        default: return nil
        }
    }

    var body: some View {
        Button {
            onClick()
        } label: {
            HStack(spacing: MentoraSpacing.space3) {
                switch state {
                case .selectedUnsubmitted:
                    MentoraIcon(name: .checkCircle, size: MentoraIconSize.medium)
                        .foregroundStyle(Color.mentoraBrandPrimary)
                case .correct:
                    MentoraIcon(name: .checkCircle, size: MentoraIconSize.medium)
                        .foregroundStyle(Color.mentoraSuccessDefault)
                case .incorrect:
                    MentoraIcon(name: .cancel, size: MentoraIconSize.medium)
                        .foregroundStyle(Color.mentoraErrorDefault)
                default:
                    EmptyView()
                }
                Text(text)
                    .mentoraFont(.bodyMedium)
                    .foregroundStyle(colors.content)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if state == .correct {
                    Text(correctLabel)
                        .mentoraFont(.labelMedium)
                        .foregroundStyle(Color.mentoraSuccessDefault)
                }
                if state == .incorrect {
                    Text(incorrectLabel)
                        .mentoraFont(.labelMedium)
                        .foregroundStyle(Color.mentoraErrorDefault)
                }
            }
            .padding(.horizontal, MentoraSpacing.space4)
            .frame(maxWidth: .infinity)
            .frame(height: 52) // component.answerOption.height (52), a literal (see file header).
            .background(colors.background)
            .clipShape(MentoraShape.medium) // radius.medium.
            .overlay {
                MentoraShape.medium.strokeBorder(colors.border, lineWidth: MentoraBorderWidth.default)
            }
        }
        .buttonStyle(.plain)
        .disabled(!clickable)
        .modifier(AnswerOptionAccessibilityValue(value: accessibilityStateValue))
    }
}

/// Applies `.accessibilityValue(_:)` only when non-`nil` -- VoiceOver's closest real analogue to
/// Android's `semantics { stateDescription = ... }` (an additional state string spoken after the
/// element's label/value), applied only for `.correct`/`.incorrect`, matching Android's own identical
/// `when` exactly (no value override for `.default`/`.selectedUnsubmitted`/`.disabledPostSubmit`).
private struct AnswerOptionAccessibilityValue: ViewModifier {
    let value: String?

    func body(content: Content) -> some View {
        if let value {
            content.accessibilityValue(value)
        } else {
            content
        }
    }
}
