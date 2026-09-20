import SwiftUI
import shared

// Phase 5 Task T11 slice 4 (Component Kit B) -- `design-system/COMPONENTS.md § SuccessState` (lines
// 568-583) -- "structurally identical [to EmptyState/ErrorState] ... but using the success semantic
// identity." Icon (`icon.large`, `color.success.default`) -> title (`heading.h3`) -> description
// (`body.small`) -> `PrimaryButton`, `space.10` vertical container padding. Ported directly from
// Android's own `SuccessState.kt`.
//
// **Entrance motion + reduced motion (unconditional per the spec's own wording).** Normally: scale
// 0.9->1 + fade 0->1 over `motion.duration.slow` + `easing.decelerate`. With Reduce Motion on: an
// opacity-only cross-fade at `motion.duration.fast` -- no scale -- per `ACCESSIBILITY.md § 9`'s
// "cross-fade or cut instantly instead of playing slide/scale transitions."
//
// **Simplification vs. Android, idiomatic-SwiftUI, not a gap.** Android's own `rememberReducedMotionEnabled()`
// manually reads `Settings.Global.ANIMATOR_DURATION_SCALE` because Compose's own kdoc there discloses
// that check is actually REDUNDANT with what Compose's animation system already does automatically --
// kept there only to drive a DIFFERENT animation shape (cross-fade vs. scale+fade), not because Compose
// needs help detecting the setting. `@Environment(\.accessibilityReduceMotion)` is this platform's own
// real, native, always-current equivalent signal (unlike Android's own kdoc-disclosed "checked once,
// never re-observed" limitation, this environment value is live and would update immediately if toggled
// while this view is on screen) -- reading it directly here needs no bespoke settings-polling code at
// all, so this port uses the idiomatic native mechanism rather than replicating Android's own
// platform-specific workaround line-for-line.
struct SuccessState: View {
    let title: String
    let description: String
    let actionLabel: String
    let onActionClick: () -> Void
    var icon: MentoraIconName = .checkCircle

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var isVisible = false

    private var duration: Double {
        reduceMotion ? MentoraMotionDuration.fast : MentoraMotionDuration.slow
    }

    var body: some View {
        VStack(spacing: MentoraSpacing.space3) {
            MentoraIcon(name: icon, size: MentoraIconSize.large)
                .foregroundStyle(Color.mentoraSuccessDefault)
                .accessibilityHidden(true)
            Text(title)
                .mentoraFont(.h3)
                .foregroundStyle(Color.mentoraTextPrimary)
                .multilineTextAlignment(.center)
            Text(description)
                .mentoraFont(.bodySmall)
                .foregroundStyle(Color.mentoraTextSecondary)
                .multilineTextAlignment(.center)
            PrimaryButton(label: actionLabel, action: onActionClick)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, MentoraSpacing.space10)
        .opacity(isVisible ? 1 : 0)
        .scaleEffect(reduceMotion ? 1 : (isVisible ? 1 : 0.9))
        .animation(MentoraMotionEasing.animation(MentoraMotionEasing.decelerate, duration: duration), value: isVisible)
        .onAppear { isVisible = true }
    }
}
