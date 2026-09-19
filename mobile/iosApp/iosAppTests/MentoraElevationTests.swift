import XCTest
import SwiftUI
import UIKit
@testable import iosApp

// Phase 5 Task T6 slice 3a -- MentoraElevationLevel (design-tokens.json#/elevation) and
// MentoraBorderWidth (design-tokens.json#/border/width). No `import shared` needed -- nothing here
// touches KMP.
//
// `import UIKit` is required for `UIColor(named:in:compatibleWith:)` and `UITraitCollection`,
// which resolve the REAL generated `mentoraShadowElevation<N>` colorset assets -- the strongest
// tests in this file, since they prove the generator's light/dark/0.7-dark-factor baking actually
// landed in the shipped asset catalog, not just that the Swift accessor compiles.
@MainActor
final class MentoraElevationTests: XCTestCase {

    func test_fiveLevelsExist() {
        XCTAssertEqual(MentoraElevationLevel.allCases.count, 5)
    }

    // MARK: - 1. Drift test

    /// Transcribed directly from `design-system/design-tokens.json#/elevation/*/ios`.
    func test_radiusAndYMatchDesignTokens() {
        let table: [(MentoraElevationLevel, CGFloat, CGFloat)] = [
            // level, radius, y
            (.level0, 0, 0),
            (.level1, 2, 1),
            (.level2, 6, 2),
            (.level3, 12, 4),
            (.level4, 20, 8),
        ]
        for (level, radius, y) in table {
            XCTAssertEqual(level.radius, radius, "\(level.rawValue) radius")
            XCTAssertEqual(level.y, y, "\(level.rawValue) y")
        }
    }

    // MARK: - 2. Asset-name mapping

    func test_shadowColorAssetNamePerLevel() {
        XCTAssertEqual(MentoraElevationLevel.level0.shadowColorAssetName, "mentoraShadowElevation0")
        XCTAssertEqual(MentoraElevationLevel.level1.shadowColorAssetName, "mentoraShadowElevation1")
        XCTAssertEqual(MentoraElevationLevel.level2.shadowColorAssetName, "mentoraShadowElevation2")
        XCTAssertEqual(MentoraElevationLevel.level3.shadowColorAssetName, "mentoraShadowElevation3")
        XCTAssertEqual(MentoraElevationLevel.level4.shadowColorAssetName, "mentoraShadowElevation4")
    }

    // MARK: - 3. Real asset resolution (light/dark, opacity + RGB)

    /// `design-system/design-tokens.json#/elevation/*/ios/opacity` (light) and the generator's
    /// `IOS_DARK_SHADOW_OPACITY_FACTOR = 0.7` (dark = light * 0.7) --
    /// `tools/token-pipeline/generate.js`, confirmed by reading the real generator source.
    private static let lightOpacity: [MentoraElevationLevel: CGFloat] = [
        .level0: 0, .level1: 0.06, .level2: 0.08, .level3: 0.10, .level4: 0.12,
    ]
    private static let darkOpacityFactor: CGFloat = 0.7

    /// `design-system/themes/theme-{light,dark}.json#/elevationShadowBase`.
    private static let lightBaseRgb: (CGFloat, CGFloat, CGFloat) = (17 / 255, 18 / 255, 23 / 255)
    private static let darkBaseRgb: (CGFloat, CGFloat, CGFloat) = (0, 0, 0)

    private func rgbaComponents(_ color: UIColor) -> (r: CGFloat, g: CGFloat, b: CGFloat, a: CGFloat) {
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        color.getRed(&r, green: &g, blue: &b, alpha: &a)
        return (r, g, b, a)
    }

    func test_shadowColorsetsResolveToRealLightAndDarkValues() {
        let rgbTolerance: CGFloat = 1.0 / 255.0 * 2

        for level in MentoraElevationLevel.allCases {
            guard let base = UIColor(named: level.shadowColorAssetName, in: Bundle.main, compatibleWith: nil) else {
                XCTFail("\(level.rawValue): could not resolve asset '\(level.shadowColorAssetName)' in Bundle.main")
                continue
            }

            let lightColor = base.resolvedColor(with: UITraitCollection(mutations: { $0.userInterfaceStyle = .light }))
            let lightComponents = rgbaComponents(lightColor)
            let expectedLightOpacity = Self.lightOpacity[level]!
            XCTAssertEqual(lightComponents.a, expectedLightOpacity, accuracy: 0.005, "\(level.rawValue) light alpha")
            // An alpha-0 color has no observable hue -- asserting its RGB channels is meaningless,
            // and (depending on whether the asset catalog stores the rendition premultiplied)
            // possibly a false-failing one, so skip level0's RGB checks entirely.
            if expectedLightOpacity > 0 {
                XCTAssertEqual(lightComponents.r, Self.lightBaseRgb.0, accuracy: rgbTolerance, "\(level.rawValue) light red")
                XCTAssertEqual(lightComponents.g, Self.lightBaseRgb.1, accuracy: rgbTolerance, "\(level.rawValue) light green")
                XCTAssertEqual(lightComponents.b, Self.lightBaseRgb.2, accuracy: rgbTolerance, "\(level.rawValue) light blue")
            }

            let darkColor = base.resolvedColor(with: UITraitCollection(mutations: { $0.userInterfaceStyle = .dark }))
            let darkComponents = rgbaComponents(darkColor)
            let expectedDarkOpacity = expectedLightOpacity * Self.darkOpacityFactor
            XCTAssertEqual(darkComponents.a, expectedDarkOpacity, accuracy: 0.005, "\(level.rawValue) dark alpha")
            if expectedLightOpacity > 0 {
                XCTAssertEqual(darkComponents.r, Self.darkBaseRgb.0, accuracy: rgbTolerance, "\(level.rawValue) dark red")
                XCTAssertEqual(darkComponents.g, Self.darkBaseRgb.1, accuracy: rgbTolerance, "\(level.rawValue) dark green")
                XCTAssertEqual(darkComponents.b, Self.darkBaseRgb.2, accuracy: rgbTolerance, "\(level.rawValue) dark blue")
            }
        }
    }

    // MARK: - 4. Border width drift test

    /// Transcribed directly from `design-system/design-tokens.json#/border/width`.
    func test_borderWidthMatchesDesignTokens() {
        XCTAssertEqual(MentoraBorderWidth.`default`, 1)
        XCTAssertEqual(MentoraBorderWidth.focus, 2)
    }

    // MARK: - 5. Render smoke test

    /// Proves `.mentoraElevation(...)` composes (background + shadow + overlay border) without
    /// crashing or collapsing the view -- no absolute geometry asserted (slice 2's no-absolute-
    /// metrics rule), only that the elevated view's measured size is finite, positive, and no
    /// smaller than the un-elevated view's own size.
    func test_mentoraElevationComposesWithoutCollapsingOrCrashing() {
        let plain = measure(Text("x"))
        let elevated = measure(Text("x").mentoraElevation(.level4, in: .sheetTop))

        XCTAssertTrue(elevated.width.isFinite && elevated.height.isFinite)
        XCTAssertGreaterThan(elevated.width, 0)
        XCTAssertGreaterThan(elevated.height, 0)
        XCTAssertGreaterThanOrEqual(elevated.width, plain.width)
        XCTAssertGreaterThanOrEqual(elevated.height, plain.height)
    }

    private func measure<V: View>(_ view: V) -> CGSize {
        let host = UIHostingController(rootView: view)
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        return host.sizeThatFits(in: CGSize(width: 200_000, height: 200_000))
    }
}
