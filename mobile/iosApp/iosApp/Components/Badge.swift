import SwiftUI
import shared

// Phase 5 Task T8 slice 1 (Component Kit A, atoms) -- `design-system/COMPONENTS.md § Badge` (Chips &
// Badges section, lines ~382-398). h20 (text) / h8 (dot) variant, `MentoraRadius.full`, horizontal
// padding `MentoraSpacing.space2`, `.labelMedium` text. Every color/shape composed from the
// semantic/primitive token layer at the point of use -- there is deliberately no generated
// `component.badge.*` Swift constant to read from (`PHASE_5_IOS_SYSTEM_DESIGN.md § 15.2`; Android
// emitted none either).
//
// Takes an already-resolved `String` label -- NEVER resolves `MentoraStrings` itself (callers pass
// the localized string in).

/// One of `COMPONENTS.md § Badge`'s 6 semantic variants, each a `{x}.container`/`on{X}Container`
/// color-pair. A pure, testable enum -- `BadgeVariantTests.swift` asserts every mapping without
/// rendering anything. Real accessor names confirmed by reading `Theme/Color+Mentora.swift` directly,
/// not guessed.
enum BadgeVariant: CaseIterable {
    case neutral
    case success
    case warning
    case error
    case info
    case brand

    /// The badge's background. Neutral is the one variant with no dedicated `color.*.container`
    /// token of its own -- `COMPONENTS.md` maps it to `color.surface.variant` instead.
    var containerColor: Color {
        switch self {
        case .neutral: return .mentoraSurfaceVariant
        case .success: return .mentoraSuccessContainer
        case .warning: return .mentoraWarningContainer
        case .error:   return .mentoraErrorContainer
        case .info:    return .mentoraInfoContainer
        case .brand:   return .mentoraBrandPrimaryContainer
        }
    }

    /// The badge's foreground (label text color). Neutral maps to `color.text.secondary`, per
    /// `COMPONENTS.md`'s explicit Neutral row -- not a generic "on-surface-variant" token, since none
    /// exists in the generated color set.
    var onContainerColor: Color {
        switch self {
        case .neutral: return .mentoraTextSecondary
        case .success: return .mentoraSuccessOnSuccessContainer
        case .warning: return .mentoraWarningOnWarningContainer
        case .error:   return .mentoraErrorOnErrorContainer
        case .info:    return .mentoraInfoOnInfoContainer
        case .brand:   return .mentoraBrandOnPrimaryContainer
        }
    }
}

/// `COMPONENTS.md § Badge`. Two presentation modes: a text label (height 20) or a dot-only indicator
/// (height 8, no label) -- both share the same variant -> color-pair resolution above.
struct Badge: View {
    private enum Mode {
        case label(String)
        case dot
    }

    private let variant: BadgeVariant
    private let mode: Mode

    /// The text-badge initializer. `label` is an ALREADY-LOCALIZED string -- this view never calls
    /// `MentoraStrings` itself.
    init(_ label: String, variant: BadgeVariant) {
        self.variant = variant
        self.mode = .label(label)
    }

    /// The dot-only badge initializer -- no label, just an 8pt filled circle in the variant's
    /// container color.
    static func dot(_ variant: BadgeVariant) -> Badge {
        Badge(variant: variant, mode: .dot)
    }

    private init(variant: BadgeVariant, mode: Mode) {
        self.variant = variant
        self.mode = mode
    }

    var body: some View {
        switch mode {
        case .label(let text):
            Text(text)
                .mentoraFont(.labelMedium)
                .lineLimit(1)
                .foregroundStyle(variant.onContainerColor)
                .padding(.horizontal, MentoraSpacing.space2)
                .frame(height: 20)
                .background(variant.containerColor, in: MentoraShape.full)
        case .dot:
            MentoraShape.full
                .fill(variant.containerColor)
                .frame(width: 8, height: 8)
        }
    }
}

#if DEBUG

/// All 6 variants, both the text-label and dot-only presentations -- reused by both light/dark
/// `#Preview`s below so the two swatch grids stay identical.
private struct BadgeSwatches: View {
    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space3) {
            HStack(spacing: MentoraSpacing.space2) {
                Badge("Neutral", variant: .neutral)
                Badge("Success", variant: .success)
                Badge("Warning", variant: .warning)
            }
            HStack(spacing: MentoraSpacing.space2) {
                Badge("Error", variant: .error)
                Badge("Info", variant: .info)
                Badge("Brand", variant: .brand)
            }
            HStack(spacing: MentoraSpacing.space2) {
                ForEach(BadgeVariant.allCases, id: \.self) { variant in
                    Badge.dot(variant)
                }
            }
        }
    }
}

#Preview("Badge -- Light, all 6 variants") {
    MentoraPreviewHost(title: "Badge -- Light / en", theme: .light, locale: .english) {
        BadgeSwatches()
    }
}

#Preview("Badge -- Dark, all 6 variants") {
    MentoraPreviewHost(title: "Badge -- Dark / en", theme: .dark, locale: .english) {
        BadgeSwatches()
    }
}

#endif
