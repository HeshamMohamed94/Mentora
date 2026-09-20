import SwiftUI
import shared

// Phase 5 Task T11 slice 4 (Component Kit B) -- `design-system/COMPONENTS.md § LoadingState` (lines
// 557-566). "Skeleton loaders preferred over spinners for content areas (cards, lists, text blocks).
// Spinners reserved for buttons/inline actions and full-screen initial load." -- this file provides both
// families: `SkeletonBlock`/`CourseCardSkeleton` for the former, `FullScreenLoadingState` for the latter
// (button/inline spinners already live in `MentoraButton`/`MentoraSelect`, not duplicated here). Ported
// directly from Android's own `LoadingState.kt`.
//
// Shimmer: animated gradient sweep, `color.surface.variant` -> `color.border.default` ->
// `color.surface.variant`, looping over `motion.duration.slow` (per the spec's literal wording) --
// implemented as a `3x motion.duration.slow` full sweep period (900ms), same reasoning
// `MentoraIndeterminateProgressBar.swift` already documents for its own loop period, so the sweep's
// *visible pass* reads at a `motion.duration.slow`-scale pace rather than snapping back instantly.
//
// **No `GeometryReader` needed here -- simpler than Android's own pixel-space `Offset` gradient by
// construction.** `LinearGradient(startPoint:endPoint:)`'s `UnitPoint`s are already proportional
// (0...1-relative) to whatever size this view is actually given -- animating those unit coordinates
// past that range (e.g. from -1 to 2) produces the exact same "band slides from off-screen-left to
// off-screen-right" sweep Android's own pixel-space `Offset(sweep * size.width - size.width, 0)` math
// computes, with zero dependency on measuring real pixel dimensions at all. This sidesteps
// `AITutorBubble.swift`'s own disclosed `GeometryReader`-height-collapse problem entirely, since it
// never needed `GeometryReader` in the first place for THIS specific effect.
struct SkeletonBlock: View {
    var shape: AnyShape = AnyShape(MentoraShape.small) // Compose's `shapes.extraSmall` == radius.small.
    /// REQUIRED, no default -- see `CourseProgressCard.swift`'s identical `resumeLabel` doc comment for
    /// the full rationale (`Components/*.swift` has no `AppEnvironment` to resolve `MentoraStrings`
    /// from). Real, pre-existing `loading_state_content_description` key.
    let accessibilityLabel: String

    @State private var sweep: CGFloat = -1

    var body: some View {
        LinearGradient(
            colors: [Color.mentoraSurfaceVariant, Color.mentoraBorderDefault, Color.mentoraSurfaceVariant],
            startPoint: UnitPoint(x: sweep - 1, y: 0),
            endPoint: UnitPoint(x: sweep, y: 1)
        )
        .clipShape(shape)
        .accessibilityLabel(accessibilityLabel)
        .onAppear {
            withAnimation(MentoraMotionEasing.linear(duration: MentoraMotionDuration.slow * 3).repeatForever(autoreverses: false)) {
                sweep = 2
            }
        }
    }
}

/// A `CourseCard`-shaped skeleton: 16:9 thumbnail block + title/instructor/action-shaped bars, same
/// `radius.large` shell (border + shape) as the real card it stands in for.
///
/// DISCLOSED SIMPLIFICATION: Android's own title/instructor bars use `Modifier.fillMaxWidth(0.7f)`/
/// `fillMaxWidth(0.4f)` (70%/40% of the card's width) so the skeleton bars visually vary in length like
/// real wrapped text. Reproducing an exact width FRACTION here would need a `GeometryReader` measuring
/// this view's own resolved width -- safe in principle (each bar already has an explicit, fixed height,
/// the same category of case `MentoraProgressBar.swift`'s own precedent covers), but this skeleton's
/// outer shell (matching Android's own `Surface(modifier = modifier, ...)`) does not itself force full
/// card width -- that comes from whatever `modifier`/frame the real call site applies, external to this
/// view -- so measuring "this view's own width" here would not reliably equal "the real card's width"
/// the fraction is meant to be relative to. Every bar renders full-width instead: a purely cosmetic
/// loading-placeholder simplification (the shimmer motion and shape are unchanged), not a functional
/// gap -- flagged for optional MC-2 live-visual comparison against Android's varied-width bars, never
/// silently assumed identical.
struct CourseCardSkeleton: View {
    let accessibilityLabel: String

    var body: some View {
        VStack(spacing: 0) {
            SkeletonBlock(shape: AnyShape(CourseThumbnailTopCornersShape), accessibilityLabel: accessibilityLabel)
                .aspectRatio(16.0 / 9.0, contentMode: .fit)
            VStack(alignment: .leading, spacing: MentoraSpacing.space2) {
                SkeletonBlock(accessibilityLabel: accessibilityLabel)
                    .frame(height: 20)
                SkeletonBlock(accessibilityLabel: accessibilityLabel)
                    .frame(height: 14)
                SkeletonBlock(shape: AnyShape(MentoraShape.medium), accessibilityLabel: accessibilityLabel) // radius.medium.
                    .frame(height: 40)
            }
            .padding(MentoraSpacing.space4)
        }
        .background(Color.mentoraSurfaceDefault)
        .clipShape(MentoraShape.large) // radius.large.
    }
}

/// Full-screen initial-load spinner -- `color.brand.primary` stroke, indeterminate rotation, per the
/// spec's "Spinners reserved for ... full-screen initial load."
struct FullScreenLoadingState: View {
    /// REQUIRED, no default -- see `SkeletonBlock`'s identical rationale above. Real, pre-existing
    /// `loading_state_content_description` key.
    let accessibilityLabel: String

    var body: some View {
        ProgressView()
            .progressViewStyle(.circular)
            .tint(Color.mentoraBrandPrimary)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .accessibilityLabel(accessibilityLabel)
    }
}
