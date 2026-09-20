import SwiftUI
import shared

// Phase 5 Task T11 slice 4 (Component Kit B) -- `design-system/COMPONENTS.md § EmptyState` (lines
// 585-593). Centered icon (`icon.large`, `color.text.secondary`) -> title (`heading.h4`) -> description
// (`body.small`, max ~2 lines soft guidance -- no hard clamp, matching Android's own already-correct
// unclamped treatment) -> optional primary/tonal action, `space.10` vertical container padding. Ported
// directly from Android's own `EmptyState.kt`.
struct EmptyState: View {
    let icon: MentoraIconName
    let title: String
    let description: String
    var actionLabel: String? = nil
    var onActionClick: (() -> Void)? = nil
    var actionVariant: MentoraButtonVariant = .tonal

    var body: some View {
        VStack(spacing: MentoraSpacing.space3) {
            MentoraIcon(name: icon, size: MentoraIconSize.large)
                .foregroundStyle(Color.mentoraTextSecondary)
                .accessibilityHidden(true)
            Text(title)
                .mentoraFont(.h4)
                .foregroundStyle(Color.mentoraTextPrimary)
                .multilineTextAlignment(.center)
            Text(description)
                .mentoraFont(.bodySmall)
                .foregroundStyle(Color.mentoraTextSecondary)
                .multilineTextAlignment(.center)
            if let actionLabel, let onActionClick {
                MentoraButton(actionLabel, variant: actionVariant, action: onActionClick)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, MentoraSpacing.space10)
    }
}
