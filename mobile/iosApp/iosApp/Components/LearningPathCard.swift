import SwiftUI
import shared

// Phase 5 Task T11 slice 2 (Component Kit B) -- `design-system/COMPONENTS.md § LearningPathCard`
// (lines 309-321). `color.brand.primaryContainer` background (a deliberate differentiator from the
// plain-surface `CourseCard`/`StatCard`/`CertificateCard`) -- title/description/meta all render in
// `color.brand.onPrimaryContainer` at FULL opacity, hierarchy coming only from the type-scale step
// (`heading.h4` -> `body.small` -> `caption`), never from fading the text color, per design principle 4
// ("hierarchy via type scale and spacing, not extra colors") -- quoted directly in the spec's own note
// and in Android's own `LearningPathCard.kt` kdoc (read directly for this port).
//
// **Action button choice (disclosed deviation from the literal "TextButton" wording, matches Android's
// own identical choice).** The spec offers two explicit alternatives: "`TextButton` in
// `color.brand.onPrimaryContainer` OR a small `PrimaryButton`." This kit's `TextButton`
// (`MentoraButton.swift`) hardcodes its content color to `color.brand.primary` with no color-override
// parameter -- giving it one would mean modifying a T8 atom, which this task's brief says not to
// rebuild. `PrimaryButton` is used instead, taking the spec's own explicitly-permitted second option,
// exactly matching Android's own `LearningPathCard.kt` choice (read directly, not independently
// re-derived).
struct LearningPathCard: View {
    let title: String
    let description: String
    let metaLabel: String
    let actionLabel: String
    let onActionClick: () -> Void
    var onClick: (() -> Void)? = nil

    private var card: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space2) {
            Text(title)
                .mentoraFont(.h4)
                .foregroundStyle(Color.mentoraBrandOnPrimaryContainer)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            Text(description)
                .mentoraFont(.bodySmall)
                .foregroundStyle(Color.mentoraBrandOnPrimaryContainer)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
            Text(metaLabel) // e.g. "6 courses • 12h total" -- caller formats/localizes.
                .mentoraFont(.caption)
                .foregroundStyle(Color.mentoraBrandOnPrimaryContainer)
            PrimaryButton(label: actionLabel, action: onActionClick)
        }
        .padding(MentoraSpacing.space5)
        .background(Color.mentoraBrandPrimaryContainer)
        .clipShape(MentoraShape.large) // radius.large (16).
    }

    var body: some View {
        if let onClick {
            Button(action: onClick) { card }
                .buttonStyle(.plain)
        } else {
            card
        }
    }
}
