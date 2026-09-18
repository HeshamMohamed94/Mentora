// GENERATED — DO NOT EDIT.
// Source: design-system/design-tokens.json, design-system/themes/theme-{light,dark}.json
// Regenerate with: npm run generate (from tools/token-pipeline/), or node tools/token-pipeline/generate.js

import CoreGraphics

/// One typography.scale entry (design-tokens.json § typography.scale) — METRICS ONLY (size /
/// lineHeight / weight / tracking numbers). Deliberately NOT a SwiftUI Font: Dynamic Type needs
/// `Font.custom(_:size:relativeTo:)` (which requires a bundled font) or a `ViewModifier` +
/// `@ScaledMetric` composed at runtime from these numbers — see
/// design-to-code/shared/platform-contract.json#/ios/typographyMapping and
/// PHASE_5_IOS_SYSTEM_DESIGN.md § 15.1 (a later task, not this one, implements that modifier).
struct MentoraTypographyMetrics {
    let fontSize: CGFloat
    let lineHeight: CGFloat
    let fontWeight: CGFloat
    let tracking: CGFloat
}

/// elevation.<N>.ios (design-tokens.json) — shadow radius/y/opacity for one elevation step. The
/// step's shadow COLOR (elevationShadowBase resolved per theme, dark reduced by 30%) lives in the
/// generated `mentoraShadowElevation<N>` colorset instead (Color+Mentora.swift / MentoraColors.xcassets),
/// not here.
struct MentoraElevationStep {
    let radius: CGFloat
    let y: CGFloat
    let opacity: Double
}

/// spacing.scale (design-tokens.json), as pt.
enum MentoraSpacing {
    static let space0: CGFloat = 0
    static let space1: CGFloat = 4
    static let space2: CGFloat = 8
    static let space3: CGFloat = 12
    static let space4: CGFloat = 16
    static let space5: CGFloat = 20
    static let space6: CGFloat = 24
    static let space8: CGFloat = 32
    static let space10: CGFloat = 40
    static let space12: CGFloat = 48
    static let space16: CGFloat = 64
}

/// shape.radius (design-tokens.json), as pt.
enum MentoraRadius {
    static let none: CGFloat = 0
    static let small: CGFloat = 8
    static let medium: CGFloat = 12
    static let large: CGFloat = 16
    static let xlarge: CGFloat = 24
    static let full: CGFloat = 999
}

/// elevation.0..4 (design-tokens.json), using each step's ios.{radius,y,opacity}.
enum MentoraElevation {
    static let level0 = MentoraElevationStep(radius: 0, y: 0, opacity: 0)
    static let level1 = MentoraElevationStep(radius: 2, y: 1, opacity: 0.06)
    static let level2 = MentoraElevationStep(radius: 6, y: 2, opacity: 0.08)
    static let level3 = MentoraElevationStep(radius: 12, y: 4, opacity: 0.1)
    static let level4 = MentoraElevationStep(radius: 20, y: 8, opacity: 0.12)
}

/// icon.sizes (design-tokens.json), as pt.
enum MentoraIconSize {
    static let small: CGFloat = 16
    static let medium: CGFloat = 20
    static let `default`: CGFloat = 24
    static let large: CGFloat = 32
}

/// typography.scale (design-tokens.json) — one MentoraTypographyMetrics per scale entry.
enum MentoraTypography {
    static let displayLarge = MentoraTypographyMetrics(
        fontSize: 48,
        lineHeight: 56,
        fontWeight: 700,
        tracking: -0.25
    )

    static let displayMedium = MentoraTypographyMetrics(
        fontSize: 40,
        lineHeight: 48,
        fontWeight: 700,
        tracking: -0.25
    )

    static let headingH1 = MentoraTypographyMetrics(
        fontSize: 32,
        lineHeight: 40,
        fontWeight: 700,
        tracking: 0
    )

    static let headingH2 = MentoraTypographyMetrics(
        fontSize: 28,
        lineHeight: 36,
        fontWeight: 700,
        tracking: 0
    )

    static let headingH3 = MentoraTypographyMetrics(
        fontSize: 24,
        lineHeight: 32,
        fontWeight: 600,
        tracking: 0
    )

    static let headingH4 = MentoraTypographyMetrics(
        fontSize: 20,
        lineHeight: 28,
        fontWeight: 600,
        tracking: 0.15
    )

    static let bodyLarge = MentoraTypographyMetrics(
        fontSize: 18,
        lineHeight: 28,
        fontWeight: 400,
        tracking: 0.15
    )

    static let bodyMedium = MentoraTypographyMetrics(
        fontSize: 16,
        lineHeight: 24,
        fontWeight: 400,
        tracking: 0.25
    )

    static let bodySmall = MentoraTypographyMetrics(
        fontSize: 14,
        lineHeight: 20,
        fontWeight: 400,
        tracking: 0.25
    )

    static let labelLarge = MentoraTypographyMetrics(
        fontSize: 14,
        lineHeight: 20,
        fontWeight: 600,
        tracking: 0.1
    )

    static let labelMedium = MentoraTypographyMetrics(
        fontSize: 12,
        lineHeight: 16,
        fontWeight: 600,
        tracking: 0.5
    )

    static let caption = MentoraTypographyMetrics(
        fontSize: 12,
        lineHeight: 16,
        fontWeight: 400,
        tracking: 0.4
    )
}

/// theme-light.json's stateOpacity — hover/pressed/focus/disabled interaction-state opacities.
enum MentoraStateOpacityLight {
    static let hoverOpacity: Double = 0.08
    static let pressedOpacity: Double = 0.12
    static let focusOpacity: Double = 0.12
    static let disabledContentOpacity: Double = 0.38
    static let disabledContainerOpacity: Double = 0.12
}

/// theme-dark.json's stateOpacity — same properties as MentoraStateOpacityLight, dark values.
enum MentoraStateOpacityDark {
    static let hoverOpacity: Double = 0.08
    static let pressedOpacity: Double = 0.16
    static let focusOpacity: Double = 0.16
    static let disabledContentOpacity: Double = 0.38
    static let disabledContainerOpacity: Double = 0.16
}

/// touchTarget.ios_pt (design-tokens.json) — minimum touch target size.
enum MentoraTouchTarget {
    static let iosPt: CGFloat = 44
}
