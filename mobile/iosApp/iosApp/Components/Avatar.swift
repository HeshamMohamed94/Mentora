import SwiftUI
import shared

// Phase 5 Task T8 slice 1 (Component Kit A, atoms) -- `design-system/COMPONENTS.md § Avatar`
// (lines ~402-411). Sizes via `Theme/MentoraDimens.swift`'s `MentoraAvatarSize` (24/40/64/96),
// always `MentoraRadius.full` (`MentoraShape(.full)`, a `Capsule` -- a circle on this view's square
// frame). Composed from the semantic/primitive token layer at the point of use -- there is
// deliberately no generated `component.avatar.*` Swift constant (`PHASE_5_IOS_SYSTEM_DESIGN.md
// § 15.2`; Android emitted none either).
//
// No image support yet -- this builds the initials-fallback path only, mirroring Android's own scope
// note in `androidApp/.../ui/components/Avatar.kt` ("No image support yet -- task brief scopes that
// to a later task with real user/course data").
//
// SCOPE NOTE: this view applies no VoiceOver semantics of its own (no combined accessibility element,
// no `accessibilityLabel`). That matches Android's own precedent -- D93 finding 3 (the "avatar+name
// announced piecemeal" defect) was fixed at the SCREEN call site (e.g. ProfileScreen combining
// avatar+name into one element), not inside the shared `Avatar` component -- but criterion I2 names
// "avatar+name" combination explicitly as an iOS requirement too, so whichever T11+ screen composes
// this Avatar with a name label must apply that combination itself; it is not free here.

/// Pure, testable Avatar logic -- kept OUT of the view body per this slice's own requirement, exactly
/// like `BadgeVariant`/`CategoryChipState`'s color-pair resolvers above.
enum AvatarRules {

    /// Initials derivation, cross-checked against `COMPONENTS.md § Avatar` (which specifies WHAT to
    /// show -- "initials on `color.brand.primaryContainer` background" -- but not HOW to derive them
    /// from a name) and Android's own `Avatar.kt#initialsOf`, which this mirrors: split on whitespace,
    /// uppercase the first letter of the first word; if more than one word exists, append the
    /// uppercased first letter of the LAST word. `"?"` for an empty/all-whitespace name (no letter to
    /// show at all).
    ///
    /// Arabic names: Arabic script has no case distinction, so `.uppercased()` is a safe no-op on an
    /// Arabic letter (returns the same character unchanged) -- this function needs no script-specific
    /// branch, matching Android's identical `uppercaseChar()` no-op behavior on a non-cased
    /// `Char`.
    static func initials(from name: String) -> String {
        let parts = name
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }

        guard let first = parts.first?.first else { return "?" }

        if parts.count > 1, let last = parts.last?.first {
            return "\(first)\(last)".uppercased()
        }
        return String(first).uppercased()
    }

    /// `COMPONENTS.md § Avatar`: "Online/status dot ... 25% of avatar diameter."
    static func statusDotDiameter(for size: MentoraAvatarSize) -> CGFloat {
        size.diameter * 0.25
    }

    /// `COMPONENTS.md § Avatar`: "`border.width.default` ring in `color.surface.default`."
    static let statusDotRingWidth: CGFloat = MentoraBorderWidth.`default`

    /// The scale factor fed to `.mentoraFont(.labelLarge, scale:)`, relative to `.medium` (40pt).
    ///
    /// `COMPONENTS.md § Avatar` says the fallback text uses "`typography.label.large` (scaled to
    /// avatar size)" but does NOT name which size is the scaling baseline. `.medium` (40pt) is used
    /// here as a documented ASSUMPTION, not a confirmed spec value -- chosen because it is this
    /// design system's most common/default avatar size (matching Android's own identical assumption
    /// and identical baseline choice in `Avatar.kt`, arrived at independently from the same
    /// underspecified prose, not copied blindly).
    static func labelScale(for size: MentoraAvatarSize) -> CGFloat {
        size.diameter / MentoraAvatarSize.medium.diameter
    }
}

/// `COMPONENTS.md § Avatar`. No image support (see file header) -- always renders the initials
/// fallback.
struct Avatar: View {
    let name: String
    var size: MentoraAvatarSize = .medium
    var showStatusDot: Bool = false

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            MentoraShape.full
                .fill(Color.mentoraBrandPrimaryContainer)
                .frame(width: size.diameter, height: size.diameter)
                .overlay(
                    Text(AvatarRules.initials(from: name))
                        .mentoraFont(.labelLarge, scale: AvatarRules.labelScale(for: size))
                        .foregroundStyle(Color.mentoraBrandOnPrimaryContainer)
                )

            if showStatusDot {
                let dotDiameter = AvatarRules.statusDotDiameter(for: size)
                MentoraShape.full
                    .fill(Color.mentoraSuccessDefault)
                    .frame(width: dotDiameter, height: dotDiameter)
                    .overlay(
                        MentoraShape.full.strokeBorder(Color.mentoraSurfaceDefault, lineWidth: AvatarRules.statusDotRingWidth)
                    )
            }
        }
    }
}

#if DEBUG

private struct AvatarSwatches: View {
    var body: some View {
        VStack(alignment: .leading, spacing: MentoraSpacing.space4) {
            HStack(alignment: .bottom, spacing: MentoraSpacing.space3) {
                Avatar(name: "Sara Ahmed", size: .small)
                Avatar(name: "Sara Ahmed", size: .medium)
                Avatar(name: "Sara Ahmed", size: .large)
                Avatar(name: "Sara Ahmed", size: .xlarge)
            }
            HStack(alignment: .bottom, spacing: MentoraSpacing.space3) {
                Avatar(name: "Sara Ahmed", size: .small, showStatusDot: true)
                Avatar(name: "Sara Ahmed", size: .medium, showStatusDot: true)
                Avatar(name: "Sara Ahmed", size: .large, showStatusDot: true)
                Avatar(name: "Sara Ahmed", size: .xlarge, showStatusDot: true)
            }
        }
    }
}

#Preview("Avatar -- Light, all 4 sizes, with/without status dot") {
    MentoraPreviewHost(title: "Avatar -- Light / en", theme: .light, locale: .english) {
        AvatarSwatches()
    }
}

#Preview("Avatar -- Dark, all 4 sizes, with/without status dot") {
    MentoraPreviewHost(title: "Avatar -- Dark / en", theme: .dark, locale: .english) {
        AvatarSwatches()
    }
}

#endif
