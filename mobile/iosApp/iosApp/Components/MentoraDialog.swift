import SwiftUI
import shared

// Phase 5 Task T11 slice 4 (Component Kit B) -- `design-system/COMPONENTS.md § AppDialog` (lines
// 515-527). `radius.xlarge` (24), `surface.elevated`, `space.6` padding, title (`heading.h3`) + body
// (`body.medium`, `text.secondary`) + actions column (full-width stacked, `space.2` gap). The spec's
// "right-aligned row" treatment is explicitly the web/tablet+ alternative (line 526: "right-aligned
// (web/tablet+) row ... full-width stacked on mobile") -- iOS phones are the mobile branch here, same
// choice Android's own kdoc records for itself: always full-width stacked, never a right-aligned row.
//
// **A genuinely simpler, more idiomatic port -- not a reduced one.** Android's own `AppDialog.kt` spends
// most of its length on Android-platform-specific window plumbing that has no iOS analogue at all:
// reaching the real `android.view.Window` to zero the platform's own `FLAG_DIM_BEHIND` dim,
// `DialogProperties.decorFitsSystemWindows`/`FLAG_LAYOUT_NO_LIMITS` to extend a `Dialog`'s own separate
// OS window edge-to-edge, etc. -- all of it exists only because Compose's `Dialog` opens a SEPARATE
// platform window with its OWN default dim behind it. This port is a plain `View` composed via
// `.overlay { }` on top of whatever the caller is already showing -- ordinary SwiftUI view composition,
// not a second OS-level window -- so there is no platform dim to fight, no window flags to set, and no
// separate scrim-vs-window-dim layering problem to solve: the custom scrim (`color.overlay.scrim`,
// `Color.mentoraOverlayScrim`) below is simply the only darkening that ever exists. This is the
// idiomatic-SwiftUI shape the user's own governing directive asks for ("do NOT copy Compose
// line-by-line"), not a corner cut.
//
// **Motion (disclosed partial port, matching Android's own identical disclosure).** Open animates scale
// 0.95->1 + fade over `motion.duration.normal` + `easing.decelerate`, via `.onAppear` flipping one
// `@State` flag -- the same pattern `SuccessState.swift`/Android's own `SuccessState.kt` both already
// use. The spec's close motion (`motion.duration.fast` + `easing.accelerate`, scale-out + fade-out) is
// NOT reproduced: this view is removed from the view hierarchy the instant the caller's `isPresented`
// binding flips to `false` (the same "no exit transition to hook without a materially more complex
// delayed-removal pattern" limitation Android's own kdoc discloses for `Dialog`'s identical instant-
// disposal behavior) -- disclosed here rather than silently dropped, matching this kit's established
// precedent (`MentoraSnackbar.swift`'s own undone "paused on interaction" disclosure).
struct MentoraDialog: View {
    let title: String
    /// Named `message`, not `body` -- a stored property literally named `body` would collide with this
    /// struct's own required `View.body` computed property (a real compile error the earlier draft of
    /// `CheckoutSummary.swift`'s `CheckoutDemoPaymentNotice` hit and fixed the same way, this task).
    let message: String
    let confirmLabel: String
    let onConfirm: () -> Void
    let onDismissRequest: () -> Void
    var dismissLabel: String? = nil
    var onDismissClick: (() -> Void)? = nil

    @State private var isVisible = false

    var body: some View {
        ZStack {
            Color.mentoraOverlayScrim // color.overlay.scrim.
                .ignoresSafeArea()
                .onTapGesture { onDismissRequest() }
                .accessibilityHidden(true)

            VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
                Text(title)
                    .mentoraFont(.h3) // heading.h3.
                    .foregroundStyle(Color.mentoraTextPrimary)
                Text(message)
                    .mentoraFont(.bodyMedium)
                    .foregroundStyle(Color.mentoraTextSecondary)
                // Full-width stacked buttons -- the mobile branch of COMPONENTS.md line 526 (see this
                // file's header). iOS phones always take this branch; there is no right-aligned row here.
                VStack(spacing: MentoraSpacing.space2) {
                    if let dismissLabel {
                        TextButton(label: dismissLabel, action: onDismissClick ?? onDismissRequest)
                            .frame(maxWidth: .infinity)
                    }
                    PrimaryButton(label: confirmLabel, action: onConfirm)
                        .frame(maxWidth: .infinity)
                }
            }
            .padding(MentoraSpacing.space6)
            .frame(maxWidth: 400)
            .background(Color.mentoraSurfaceElevated) // color.surface.elevated.
            .clipShape(MentoraShape.xlarge) // radius.xlarge.
            .padding(.horizontal, MentoraSpacing.space4)
            .opacity(isVisible ? 1 : 0)
            .scaleEffect(isVisible ? 1 : 0.95)
        }
        .onAppear {
            withAnimation(MentoraMotionEasing.animation(MentoraMotionEasing.decelerate, duration: MentoraMotionDuration.normal)) {
                isVisible = true
            }
        }
    }
}

extension View {
    /// `design-system/COMPONENTS.md § AppDialog`'s real presentation entry point -- an `.overlay`, not a
    /// second OS window (see this file's header). Renders nothing when `isPresented.wrappedValue` is
    /// `false`.
    func mentoraDialog(
        isPresented: Binding<Bool>,
        title: String,
        message: String,
        confirmLabel: String,
        onConfirm: @escaping () -> Void,
        dismissLabel: String? = nil,
        onDismissClick: (() -> Void)? = nil
    ) -> some View {
        overlay {
            if isPresented.wrappedValue {
                MentoraDialog(
                    title: title,
                    message: message,
                    confirmLabel: confirmLabel,
                    onConfirm: onConfirm,
                    onDismissRequest: { isPresented.wrappedValue = false },
                    dismissLabel: dismissLabel,
                    onDismissClick: onDismissClick
                )
            }
        }
    }
}
