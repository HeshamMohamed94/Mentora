import SwiftUI

// Named MentoraElevationLevel, not MentoraElevation, to avoid a redeclaration collision with the
// already-generated Theme/MentoraTokens.swift#MentoraElevation -- same collision class as slice 1's
// MentoraTypography/MentoraTypographyRules split. Composes shadow + 1pt border + a background
// fill, all three, because a caller who applies only .shadow()+.overlay() without an opaque
// background gets the shadow rendered on the view's own alpha-derived shape (often the text glyphs
// themselves) -- a common, silent SwiftUI mistake. Applies its border and shadow at EVERY level
// including .level0 (radius 0 / alpha 0, so it's a real no-op at that level, keeping one
// branch-free code path) since the border is what actually carries visible elevation in dark mode
// per system design's own elevation rules.

/// `elevation.0..4` (design-tokens.json#/elevation) behavior layer, on top of the generated
/// `MentoraTokens.swift#MentoraElevation` metrics.
enum MentoraElevationLevel: String, CaseIterable {
    case level0, level1, level2, level3, level4

    /// The generated `mentoraShadowElevation<N>` colorset asset name this level resolves to.
    var shadowColorAssetName: String {
        "mentoraShadowElevation\(levelNumber)"
    }

    /// Resolved via `shadowColorAssetName` (not a second hand-written per-case switch) so the two
    /// can never independently drift out of sync -- a mis-wire here would otherwise be silent.
    var shadowColor: Color {
        Color(shadowColorAssetName)
    }

    private var levelNumber: Int {
        switch self {
        case .level0: return 0
        case .level1: return 1
        case .level2: return 2
        case .level3: return 3
        case .level4: return 4
        }
    }

    /// The generated `MentoraTokens.swift#MentoraElevation.level<N>` step this level resolves to --
    /// NOT re-transcribed here, so a future `design-tokens.json` change can't silently desync a
    /// hand-typed copy from the generated source of truth.
    private var generatedStep: MentoraElevationStep {
        switch self {
        case .level0: return MentoraElevation.level0
        case .level1: return MentoraElevation.level1
        case .level2: return MentoraElevation.level2
        case .level3: return MentoraElevation.level3
        case .level4: return MentoraElevation.level4
        }
    }

    /// `design-tokens.json#/elevation/<N>/ios` radius/y, read from the generated step.
    var radius: CGFloat { generatedStep.radius }
    var y: CGFloat { generatedStep.y }
}

extension View {
    /// `design-system/platform-mapping.md`'s elevation handle. Composes a background fill, the
    /// step's shadow, and a 1pt border in one call -- deliberately no `.clipShape` on this call's
    /// own output; adding one after `.background` here would clip the background's own shadow away.
    /// A caller whose content must follow the shape (e.g. `CourseCard`'s thumbnail per
    /// `COMPONENTS.md`'s "top corners clipped to card radius") applies its own
    /// `.clipShape(shape)` on the content BEFORE calling this modifier, which clips only the
    /// content and leaves this modifier's background shadow intact.
    ///
    /// Defaults to `.mentoraSurfaceElevated`, not `.mentoraSurfaceDefault`: per
    /// `design-tokens.json#/elevation/darkModeNote` and `DESIGN_SYSTEM.md`, dark surfaces carry
    /// elevation via a lighter surface tone rather than heavier shadow (shadow alpha is already
    /// reduced ~30% in dark mode by the generator). `surface.default == surface.elevated` in light
    /// mode, so this is a no-op there and a real fix in dark mode.
    func mentoraElevation(
        _ level: MentoraElevationLevel,
        in shape: MentoraShape = .large,
        fill: Color = .mentoraSurfaceElevated,
        border: Color = .mentoraBorderDefault
    ) -> some View {
        self
            .background {
                shape.fill(fill)
                    .shadow(color: level.shadowColor, radius: level.radius, x: 0, y: level.y)
            }
            .overlay {
                shape.strokeBorder(border, lineWidth: MentoraBorderWidth.`default`)
            }
    }
}
