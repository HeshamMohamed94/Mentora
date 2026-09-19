import SwiftUI
import shared

// Phase 5 Task T8 slice 1 (Component Kit A, atoms) -- `design-system/COMPONENTS.md § CategoryChip`
// (Chips & Badges section, lines ~366-380). h28, `MentoraRadius.full`, horizontal padding
// `MentoraSpacing.space3`, `.labelMedium` text. Composed from the semantic/primitive token layer at
// the point of use -- there is deliberately no generated `component.categoryChip.*` Swift constant
// (`PHASE_5_IOS_SYSTEM_DESIGN.md § 15.2`; Android emitted none either).
//
// Takes an already-resolved `String` label -- NEVER resolves `MentoraStrings` itself.
//
// SCOPE NOTE: this slice ships rendering only -- no tap handler, no `.isSelected`/`.isEnabled`
// accessibility trait, and no explicit 44pt hit-area expansion (G7). Android's `CategoryChip.kt` is
// genuinely interactive (`clickable(enabled:onClick:)`, `minimumInteractiveComponentSize()`,
// `semantics { role = Role.Button; selected; disabled() }`). Whichever T11/T12 call site makes this
// chip tappable (a filter row) owns adding those -- disclosed here so that task doesn't inherit a
// silently inert chip without noticing.

/// One of `COMPONENTS.md § CategoryChip`'s 4 states, each a background/foreground color pair. A pure,
/// testable enum -- `CategoryChipTests.swift` asserts every mapping without rendering anything. Real
/// accessor names confirmed by reading `Theme/Color+Mentora.swift` directly, not guessed.
enum CategoryChipState: CaseIterable {
    /// Unselected filter chip.
    case `default`
    case selected
    /// Sitting on top of a course-thumbnail image (the artwork scrim), per `COMPONENTS.md`'s
    /// "on image overlay" row.
    case onImageOverlay
    case disabled

    var backgroundColor: Color {
        switch self {
        case .`default`:      return .mentoraSurfaceVariant
        case .selected:       return .mentoraBrandPrimaryContainer
        case .onImageOverlay: return .mentoraOverlayChipScrim
        case .disabled:       return .mentoraSurfaceVariant
        }
    }

    /// `.disabled` deliberately resolves to the exact SAME `.mentoraTextDisabled` value as any other
    /// use of that token -- no opacity modifier is ever applied here, per `COMPONENTS.md`'s explicit
    /// "no opacity modifier -- background already reads as inactive against `text.disabled`" note.
    /// `CategoryChipTests.swift`'s disabled-state test asserts this literal equality, not merely "some
    /// dimmed color".
    var foregroundColor: Color {
        switch self {
        case .`default`:      return .mentoraTextSecondary
        case .selected:       return .mentoraBrandOnPrimaryContainer
        case .onImageOverlay: return .mentoraTextInverse
        case .disabled:       return .mentoraTextDisabled
        }
    }
}

/// `COMPONENTS.md § CategoryChip`.
struct CategoryChip: View {
    let label: String
    let state: CategoryChipState

    init(_ label: String, state: CategoryChipState = .`default`) {
        self.label = label
        self.state = state
    }

    var body: some View {
        Text(label)
            .mentoraFont(.labelMedium)
            .foregroundStyle(state.foregroundColor)
            .padding(.horizontal, MentoraSpacing.space3)
            .frame(height: 28)
            .background(state.backgroundColor, in: MentoraShape.full)
    }
}

#if DEBUG

private struct CategoryChipSwatches: View {
    var body: some View {
        HStack(spacing: MentoraSpacing.space2) {
            CategoryChip("Design", state: .`default`)
            CategoryChip("Design", state: .selected)
            CategoryChip("Design", state: .onImageOverlay)
            CategoryChip("Design", state: .disabled)
        }
    }
}

#Preview("CategoryChip -- Light, all 4 states") {
    MentoraPreviewHost(title: "CategoryChip -- Light / en", theme: .light, locale: .english) {
        CategoryChipSwatches()
    }
}

#Preview("CategoryChip -- Dark, all 4 states") {
    MentoraPreviewHost(title: "CategoryChip -- Dark / en", theme: .dark, locale: .english) {
        CategoryChipSwatches()
    }
}

#endif
