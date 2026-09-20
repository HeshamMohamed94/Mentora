import SwiftUI
import shared

// Phase 5 Task T11 slice 4 (Component Kit B) -- `design-system/COMPONENTS.md § ErrorState` (lines
// 595-606). Icon (`icon.large`, `color.error.default`) -> friendly title -> plain-language description
// -> retry action, `space.10` vertical container padding. Ported directly from Android's own
// `ErrorState.kt`.
//
// **Never a raw backend error string.** `title`/`description` are plain caller-supplied strings -- every
// real call site must resolve them from this kit's own central error-copy mapping (`ErrorCopy.swift`,
// already established by T10's `LoginModel`/`RegisterModel`), never a raw `MentoraError` message
// directly. This component builds no second error-copy mechanism -- it only renders whatever friendly
// copy the caller already resolved.
struct ErrorState: View {
    let title: String
    let description: String
    let onRetryClick: () -> Void
    /// DISCLOSED DEVIATION from Android's own default-parameter convenience -- see
    /// `CourseProgressCard.swift`'s identical `resumeLabel` doc comment for the full rationale. Real,
    /// pre-existing `error_state_retry_label` key -- REQUIRED here (no default), always resolved and
    /// passed in by the caller. Android's own T19 review fix (D94, HIGH) found its matching default
    /// parameter was a raw, reachable English literal at 7 real call sites; requiring this parameter
    /// outright, rather than giving it any Swift-side default, closes that entire bug class by
    /// construction rather than replicating a default that class of bug could reappear behind.
    var retryLabel: String
    var retryVariant: MentoraButtonVariant = .tonal

    var body: some View {
        VStack(spacing: MentoraSpacing.space3) {
            MentoraIcon(name: .cancel, size: MentoraIconSize.large)
                .foregroundStyle(Color.mentoraErrorDefault)
                .accessibilityHidden(true)
            Text(title)
                .mentoraFont(.h4)
                .foregroundStyle(Color.mentoraTextPrimary)
                .multilineTextAlignment(.center)
            Text(description)
                .mentoraFont(.bodySmall)
                .foregroundStyle(Color.mentoraTextSecondary)
                .multilineTextAlignment(.center)
            MentoraButton(retryLabel, variant: retryVariant, action: onRetryClick)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, MentoraSpacing.space10)
    }
}
