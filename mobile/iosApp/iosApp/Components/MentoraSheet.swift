import SwiftUI
import shared

// Phase 5 Task T11 slice 4 (Component Kit B) -- `design-system/COMPONENTS.md § BottomSheet` (lines
// 529-538). `radius.xlarge` (24) top corners only, `surface.elevated`, `space.5` content padding, a
// 32x4 drag handle in `border.strong`. Android's own `MentoraBottomSheet.kt` builds this on Material3's
// `ModalBottomSheet` (styled to these tokens, not a from-scratch overlay); this port builds it on
// SwiftUI's own native `.sheet(isPresented:)` the same way, styled via real presentation modifiers
// rather than a hand-rolled overlay.
//
// **Custom drag handle, not the system's native one -- token-exactness, not a missing native option.**
// `.presentationDragIndicator(.visible)` is real, native, and would need no custom code at all -- but its
// appearance (size/color) is not configurable via any public API, so it cannot be made to match the
// spec's exact `border.strong`/32x4 values. This kit's own established practice throughout Component Kit
// A/B is to prefer exact token compliance over a close-but-uncustomizable platform default wherever a
// real config knob doesn't already achieve it (the same reasoning `MentoraButton.swift`'s custom
// `ButtonStyle` exists at all, rather than a plain system `Button` style) -- so the system indicator is
// hidden (`.presentationDragIndicator(.hidden)`) and a token-exact handle is drawn as real content
// instead, exactly mirroring Android's own custom drag-handle composable.
//
// **Corner radius -- a disclosed, real platform-API limitation, not a silently-dropped requirement.**
// `.presentationCornerRadius(_:)` (iOS 16.4+, well within this project's iOS 17+ minimum) is the real,
// public hook for a sheet's corner radius -- but it rounds all four corners uniformly; no public API
// exposes a "top corners only" override for a system sheet the way Compose's `ModalBottomSheet` accepts
// an arbitrary custom `Shape`. `.presentationCornerRadius(MentoraRadius.xlarge)` is applied here as the
// closest real match; the bottom corners rounding too (rather than staying square against the screen
// edge, as Android's literal top-corners-only shape does) is a disclosed, platform-driven visual
// difference, not a missing capability -- flagged for MC-2 live comparison, never silently assumed
// pixel-identical to Android.
enum MentoraSheetMetrics {
    static let dragHandleWidth: CGFloat = 32
    static let dragHandleHeight: CGFloat = 4
}

private struct MentoraSheetDragHandle: View {
    var body: some View {
        Capsule(style: .circular)
            .fill(Color.mentoraBorderStrong)
            .frame(width: MentoraSheetMetrics.dragHandleWidth, height: MentoraSheetMetrics.dragHandleHeight)
            .padding(.top, MentoraSpacing.space2)
    }
}

private struct MentoraSheetContent<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            MentoraSheetDragHandle()
            content()
                .padding(MentoraSpacing.space5)
        }
        .frame(maxWidth: .infinity)
        .background(Color.mentoraSurfaceElevated) // color.surface.elevated.
        .overlay(alignment: .top) {
            // A top border, in this kit's own established "borders over shadow" spirit (matches
            // Android's own identical disclosed choice, `MentoraBottomSheet.kt`'s "Elevation" kdoc note)
            // -- `color.border.default`.
            Rectangle().fill(Color.mentoraBorderDefault).frame(height: MentoraBorderWidth.default)
        }
        .presentationDragIndicator(.hidden)
        .presentationCornerRadius(MentoraRadius.xlarge)
    }
}

extension View {
    /// `design-system/COMPONENTS.md § BottomSheet`'s real presentation entry point. `content` is a
    /// caller-supplied `@ViewBuilder` closure -- matches Android's own `content: @Composable
    /// ColumnScope.() -> Unit` shape.
    func mentoraSheet<SheetContent: View>(
        isPresented: Binding<Bool>,
        onDismiss: (() -> Void)? = nil,
        @ViewBuilder content: @escaping () -> SheetContent
    ) -> some View {
        sheet(isPresented: isPresented, onDismiss: onDismiss) {
            MentoraSheetContent(content: content)
        }
    }
}
