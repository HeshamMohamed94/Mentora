import SwiftUI
import shared

// Phase 5 Task T11 slice 3 (Component Kit B) -- `design-system/COMPONENTS.md § AITutorBubble` (lines
// 678-689). AI: `color.surface.variant` background / `color.text.primary` text. User:
// `color.brand.primaryContainer` background / `color.brand.onPrimaryContainer` text. Radius
// `radius.large` (16) with the tail corner (bottom-leading for AI, bottom-trailing for user) reduced to
// `radius.small` (8). Max width 80% of the chat container. Ported directly from Android's own
// `AITutorBubble.kt`.
//
// "The AI Tutor uses the exact same surfaces, radii, and type scale as the rest of the product" per the
// spec's own note -- every value below is a semantic token, no bubble-only literal (beyond the 80%
// fraction and the tail-corner radius swap, both stated explicitly by the spec).
//
// **Leading/trailing, not left/right -- RTL-correct by construction.** Android's `RoundedCornerShape`
// uses `topStart`/`topEnd`/`bottomStart`/`bottomEnd` (LOGICAL, layout-direction-aware corners, auto-
// mirrored under RTL). `UnevenRoundedRectangle(topLeadingRadius:...)` (iOS 17+, already used by
// `Theme/MentoraShape.swift`'s own `.sheetTop` case and `Components/CourseArtwork.swift`'s
// `CourseThumbnailTopCornersShape`) is the exact same kind of logical, layout-direction-aware corner
// API on this platform -- "leading"/"trailing" here are NOT "left"/"right", they flip together with the
// environment's layout direction exactly as Compose's Start/End do, so the tail corner stays on the
// correct visual side under Arabic RTL without any extra branching.
//
// **Max-width measurement -- `.background` + `PreferenceKey`, NOT a bare `GeometryReader` wrapping the
// bubble's own content.** Android's `BoxWithConstraints` measures its own available width WITHOUT
// constraining its content's height (the text can wrap to any number of lines). A `GeometryReader`
// placed directly around variable-height text has the opposite, well-documented problem: it does not
// report an intrinsic height at all -- proposed an "ideal" height by a parent that isn't itself fixed-
// height (this row, inside a scrolling message list), it collapses toward zero rather than sizing to
// its multi-line text content. `MentoraProgressBar.swift`'s own `GeometryReader` usage (this kit's only
// prior precedent) sidesteps that exact problem by giving its `GeometryReader` a FIXED `.frame(height:)`
// immediately outside it (a determinate progress track always has one fixed height) -- not available
// here, since a chat bubble's height is exactly the thing that must stay free to vary with its text.
// The standard, well-established alternative for "measure a width without affecting your own layout"
// (Apple's own WWDC sample code and the broadly-documented SwiftUI idiom for this exact class of
// problem) is a `.background(GeometryReader { ... })` reading the ALREADY-LAID-OUT outer row's width via
// a `PreferenceKey`, fed back into a plain `CGFloat` `@State` that then caps the bubble's own
// `.frame(maxWidth:)` -- `.background` does not influence its parent's own sizing decision (it is sized
// to match the parent AFTER that parent's own layout pass), so this measures the row's real width
// without ever constraining the bubble's height.
//
// DISCLOSED FIRST-FRAME CAVEAT: `availableWidth` starts at 0 (no cap applied that frame) until the
// `PreferenceKey` round-trip completes and triggers a re-render with the real measured value -- this
// happens within the same layout pass in every observed SwiftUI/UIKit hosting scenario, but is not
// device/simulator-confirmed here (no Mac -- see this project's own standing MC-2/MC-3 limitation). Not
// device-testable is not "silently assumed correct": flagged as an MC-3 live-verification item, not
// hidden.
enum AiTutorSender {
    case ai
    case user
}

enum AITutorBubbleRules {
    static let maxWidthFraction: CGFloat = 0.8

    static func background(for sender: AiTutorSender) -> Color {
        sender == .ai ? .mentoraSurfaceVariant : .mentoraBrandPrimaryContainer
    }

    static func contentColor(for sender: AiTutorSender) -> Color {
        sender == .ai ? .mentoraTextPrimary : .mentoraBrandOnPrimaryContainer
    }

    static func shape(for sender: AiTutorSender) -> UnevenRoundedRectangle {
        switch sender {
        case .ai:
            return UnevenRoundedRectangle(
                topLeadingRadius: MentoraRadius.large,
                bottomLeadingRadius: MentoraRadius.small,
                bottomTrailingRadius: MentoraRadius.large,
                topTrailingRadius: MentoraRadius.large,
                style: .circular
            )
        case .user:
            return UnevenRoundedRectangle(
                topLeadingRadius: MentoraRadius.large,
                bottomLeadingRadius: MentoraRadius.large,
                bottomTrailingRadius: MentoraRadius.small,
                topTrailingRadius: MentoraRadius.large,
                style: .circular
            )
        }
    }
}

private struct AITutorBubbleWidthKey: PreferenceKey {
    static var defaultValue: CGFloat = 0
    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

struct AITutorBubble: View {
    let text: String
    let sender: AiTutorSender

    @State private var rowWidth: CGFloat = 0

    var body: some View {
        HStack {
            if sender == .user { Spacer(minLength: 0) }
            Text(text)
                .mentoraFont(.bodyMedium)
                .foregroundStyle(AITutorBubbleRules.contentColor(for: sender))
                .padding(.horizontal, MentoraSpacing.space4)
                .padding(.vertical, MentoraSpacing.space3)
                .background(AITutorBubbleRules.background(for: sender))
                .clipShape(AITutorBubbleRules.shape(for: sender))
                .frame(
                    maxWidth: rowWidth > 0 ? rowWidth * AITutorBubbleRules.maxWidthFraction : nil,
                    alignment: sender == .user ? .trailing : .leading
                )
            if sender == .ai { Spacer(minLength: 0) }
        }
        .background(
            GeometryReader { geometry in
                Color.clear.preference(key: AITutorBubbleWidthKey.self, value: geometry.size.width)
            }
        )
        .onPreferenceChange(AITutorBubbleWidthKey.self) { rowWidth = $0 }
    }
}
