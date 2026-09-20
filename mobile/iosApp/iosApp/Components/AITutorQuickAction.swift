import SwiftUI
import shared

// Phase 5 Task T11 slice 3 (Component Kit B) -- `design-system/COMPONENTS.md § AITutorQuickAction`
// (lines 691-710). Height 36 (literal component-table value -- same modeling as `Badge`/`CategoryChip`'s
// literal height, not a `spacing.scale` alias), radius `radius.full`, paddingX `space.4`, typography
// `label.medium`, text `color.brand.primary`. Ported directly from Android's own `AITutorQuickAction.kt`.
//
// The 5 default quick-action labels ("Explain this lesson", "Summarize", ...) are a shared `AiQuickAction`
// domain concern (`mobile/shared`), NOT hardcoded here -- this view only renders whatever `label` string
// it's given; a later task (AI Tutor) supplies the real label/prompt pairing.
//
// **Hit-area vs. visual-size ordering** -- same "clickable outermost, 44pt minimum hit area next,
// exact 36pt visual pill innermost" ordering `MentoraButton.swift`'s own header comment establishes
// (mirrors Android's own `minimumInteractiveComponentSize()` + `.height(36.dp)` ordering,
// `ACCESSIBILITY.md § 4` names chips explicitly).
enum AITutorQuickActionRules {
    static func background(isPressed: Bool, isEnabled: Bool) -> Color {
        (isPressed && isEnabled) ? .mentoraBrandPrimaryContainer : .mentoraSurfaceDefault
    }

    static func borderColor(isPressed: Bool, isEnabled: Bool) -> Color {
        (isPressed && isEnabled) ? .mentoraBrandPrimary : .mentoraBorderDefault
    }

    static func textColor(isEnabled: Bool, disabledContentOpacity: Double) -> Color {
        isEnabled ? .mentoraBrandPrimary : Color.mentoraBrandPrimary.opacity(disabledContentOpacity)
    }
}

private struct AITutorQuickActionStyle: ButtonStyle {
    let isEnabled: Bool

    @Environment(\.colorScheme) private var colorScheme

    func makeBody(configuration: Configuration) -> some View {
        let disabledContentOpacity = colorScheme == .dark ? MentoraStateOpacityDark.disabledContentOpacity : MentoraStateOpacityLight.disabledContentOpacity
        configuration.label
            .mentoraFont(.labelMedium)
            .foregroundStyle(AITutorQuickActionRules.textColor(isEnabled: isEnabled, disabledContentOpacity: disabledContentOpacity))
            .padding(.horizontal, MentoraSpacing.space4)
            .frame(height: 36) // component.aiTutorQuickAction.height (36), a literal (see file header).
            .background(AITutorQuickActionRules.background(isPressed: configuration.isPressed, isEnabled: isEnabled))
            .clipShape(MentoraShape.full)
            .overlay {
                MentoraShape.full.strokeBorder(
                    AITutorQuickActionRules.borderColor(isPressed: configuration.isPressed, isEnabled: isEnabled),
                    lineWidth: MentoraBorderWidth.default
                )
            }
            .frame(minWidth: MentoraTouchTarget.iosPt, minHeight: MentoraTouchTarget.iosPt)
            .contentShape(Rectangle())
    }
}

struct AITutorQuickAction: View {
    let label: String
    let onClick: () -> Void
    var isEnabled: Bool = true

    var body: some View {
        Button(action: onClick) {
            Text(label)
        }
        .buttonStyle(AITutorQuickActionStyle(isEnabled: isEnabled))
        .disabled(!isEnabled)
    }
}
