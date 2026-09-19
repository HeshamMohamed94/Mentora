import XCTest
import SwiftUI
@testable import iosApp

// Phase 5 Task T6 slice 1 -- the 12-style metric table AND the in-target drift test (this file
// doubles as both, since `test_metricsMatchDesignTokens` transcribes design-tokens.json's real
// values directly). No `import shared` needed -- nothing here touches KMP.
//
// `import SwiftUI` is required despite `@testable import iosApp`: module imports are not
// transitive, and `test_anchorsMatchContract` names `Font.TextStyle` directly (a real, CI-caught-
// by-review gap in an earlier draft of this file -- module imports must be explicit per file).
final class MentoraTypographyTests: XCTestCase {

    func test_allTwelveCasesExist() {
        let expected: Set<String> = [
            "displayLarge", "displayMedium", "h1", "h2", "h3", "h4",
            "bodyLarge", "bodyMedium", "bodySmall", "labelLarge", "labelMedium", "caption"
        ]
        XCTAssertEqual(MentoraTextStyle.allCases.count, 12)
        XCTAssertEqual(Set(MentoraTextStyle.allCases.map(\.rawValue)), expected)
    }

    /// Catches a copy-paste error in the 12-arm `metrics` switch -- the single most likely defect
    /// in this file. bodySmall/labelLarge share size+lineHeight (14/20) but differ in weight;
    /// labelMedium/caption share size+lineHeight (12/16) but differ in weight -- so all 12 are
    /// pairwise distinct as (size,lineHeight,weight,tracking) 4-tuples.
    ///
    /// NOTE: `MentoraTypographyMetrics.fontWeight` is declared `CGFloat` (not `Int`) in the real,
    /// generated `Theme/MentoraTokens.swift` -- `Key.weight` is typed `CGFloat` accordingly.
    func test_eachStyleMapsToDistinctMetrics() {
        struct Key: Hashable { let size, lineHeight, tracking, weight: CGFloat }
        let keys = MentoraTextStyle.allCases.map { style -> Key in
            let m = style.metrics
            return Key(size: m.fontSize, lineHeight: m.lineHeight, tracking: m.tracking, weight: m.fontWeight)
        }
        XCTAssertEqual(Set(keys).count, 12, "Two or more styles resolved to identical metrics -- check the `metrics` switch for a copy-paste error.")
    }

    /// Transcribed directly from `design-system/design-tokens.json#/typography/scale`. This IS the
    /// drift test -- if the generated MentoraTokens.swift ever changes, this fails.
    ///
    /// NOTE: the `fontWeight` column is typed `CGFloat` (not `Int`) to match the real, generated
    /// `MentoraTypographyMetrics.fontWeight` property type.
    func test_metricsMatchDesignTokens() {
        let table: [(MentoraTextStyle, CGFloat, CGFloat, CGFloat, CGFloat)] = [
            // style, fontSize, lineHeight, fontWeight, tracking
            (.displayLarge,  48, 56, 700, -0.25),
            (.displayMedium, 40, 48, 700, -0.25),
            (.h1,            32, 40, 700,  0.0),
            (.h2,            28, 36, 700,  0.0),
            (.h3,            24, 32, 600,  0.0),
            (.h4,            20, 28, 600,  0.15),
            (.bodyLarge,     18, 28, 400,  0.15),
            (.bodyMedium,    16, 24, 400,  0.25),
            (.bodySmall,     14, 20, 400,  0.25),
            (.labelLarge,    14, 20, 600,  0.1),
            (.labelMedium,   12, 16, 600,  0.5),
            (.caption,       12, 16, 400,  0.4),
        ]
        for (style, size, lineHeight, weight, tracking) in table {
            let m = style.metrics
            XCTAssertEqual(m.fontSize, size, accuracy: 0.0001, "\(style.rawValue) fontSize")
            XCTAssertEqual(m.lineHeight, lineHeight, accuracy: 0.0001, "\(style.rawValue) lineHeight")
            XCTAssertEqual(m.fontWeight, weight, accuracy: 0.0001, "\(style.rawValue) fontWeight")
            XCTAssertEqual(m.tracking, tracking, accuracy: 0.0001, "\(style.rawValue) tracking")
        }
    }

    /// Transcribed from `platform-contract.json#/ios/typographyMapping/relativeToAnchors`.
    func test_anchorsMatchContract() {
        let table: [(MentoraTextStyle, Font.TextStyle)] = [
            (.displayLarge, .largeTitle), (.displayMedium, .largeTitle),
            (.h1, .title), (.h2, .title2), (.h3, .title3), (.h4, .headline),
            (.bodyLarge, .body), (.bodyMedium, .body), (.bodySmall, .subheadline),
            (.labelLarge, .subheadline), (.labelMedium, .footnote), (.caption, .caption),
        ]
        for (style, anchor) in table {
            XCTAssertEqual(style.anchor, anchor, "\(style.rawValue) anchor")
        }
    }

    func test_weightMapping() {
        XCTAssertEqual(MentoraTypographyRules.weight(for: .h1), .bold)       // 700
        XCTAssertEqual(MentoraTypographyRules.weight(for: .h3), .semibold)   // 600
        XCTAssertEqual(MentoraTypographyRules.weight(for: .bodyMedium), .regular) // 400
    }

    func test_isBodyStep() {
        let bodySteps: Set<MentoraTextStyle> = [.bodyLarge, .bodyMedium, .bodySmall]
        for style in MentoraTextStyle.allCases {
            XCTAssertEqual(style.isBodyStep, bodySteps.contains(style), "\(style.rawValue).isBodyStep")
        }
    }

    /// lineSpacing at scaledSize == the token's own (unscaled) fontSize -- i.e. the default content
    /// size category, where @ScaledMetric's output equals its base value.
    func test_lineSpacingAtDefaultSize_en() {
        let expected: [MentoraTextStyle: CGFloat] = [
            .displayLarge: 0, .displayMedium: 0,
            .h1: 1.6, .h2: 2.4, .h3: 3.2, .h4: 4.0,
            .bodyLarge: 6.4, .bodyMedium: 4.8, .bodySmall: 3.2,
            .labelLarge: 3.2, .labelMedium: 1.6, .caption: 1.6,
        ]
        for style in MentoraTextStyle.allCases {
            let spacing = MentoraTypographyRules.lineSpacing(for: style, scaledSize: style.metrics.fontSize, isArabic: false)
            XCTAssertEqual(spacing, expected[style]!, accuracy: 0.0001, "\(style.rawValue) en lineSpacing")
        }
    }

    func test_lineSpacingAtDefaultSize_ar() {
        let expected: [MentoraTextStyle: CGFloat] = [
            .displayLarge: 0, .displayMedium: 0,
            .h1: 1.6, .h2: 2.4, .h3: 3.2, .h4: 4.0,
            .bodyLarge: 9.2, .bodyMedium: 7.2, .bodySmall: 5.2,
            .labelLarge: 3.2, .labelMedium: 1.6, .caption: 1.6,
        ]
        for style in MentoraTextStyle.allCases {
            let spacing = MentoraTypographyRules.lineSpacing(for: style, scaledSize: style.metrics.fontSize, isArabic: true)
            XCTAssertEqual(spacing, expected[style]!, accuracy: 0.0001, "\(style.rawValue) ar lineSpacing")
        }
    }

    func test_displayStepsClampToZero() {
        for style in [MentoraTextStyle.displayLarge, .displayMedium] {
            XCTAssertEqual(MentoraTypographyRules.lineSpacing(for: style, scaledSize: style.metrics.fontSize, isArabic: false), 0)
            XCTAssertEqual(MentoraTypographyRules.lineSpacing(for: style, scaledSize: style.metrics.fontSize, isArabic: true), 0)
            let ratio = MentoraTypographyRules.lineHeightRatio(for: style, isArabic: false)
            XCTAssertLessThanOrEqual(ratio, MentoraTypographyRules.naturalLineHeightFactor, "\(style.rawValue) ratio should be at or below the natural factor, proving the clamp is actually reached")
        }
    }

    /// THE regression test for the withdrawn formula -- catches what `test_lineSpacingAtDefaultSize_*`
    /// cannot, since those only ever pass `scaledSize == fontSize` (a scale factor of 1.0). The
    /// withdrawn `.lineSpacing(lineHeight - scaledSize)` form was specifically an ACCESSIBILITY-SIZE
    /// bug -- it agreed with the current formula at 1.0x and only diverged (and went negative) as
    /// `scaledSize` grew past `lineHeight`. Sweeping scale factors here needs no rendering, no
    /// hosting controller, and no Mac: `lineSpacing` is a pure function of `(style, scaledSize,
    /// isArabic)`, and the design formula is scale-invariant by construction (§ 15.1) -- the target
    /// leading RATIO must hold identically at every scale, and the result must never go negative.
    func test_lineSpacingScalesCorrectlyAtAccessibilitySizes() {
        let scaleFactors: [CGFloat] = [1.0, 1.35, 1.6, 2.0, 2.35, 3.0, 3.5, 5.0]
        for style in MentoraTextStyle.allCases {
            for isArabic in [false, true] {
                let ratio = MentoraTypographyRules.lineHeightRatio(for: style, isArabic: isArabic)
                let isClamped = ratio <= MentoraTypographyRules.naturalLineHeightFactor
                for factor in scaleFactors {
                    let scaledSize = style.metrics.fontSize * factor
                    let spacing = MentoraTypographyRules.lineSpacing(for: style, scaledSize: scaledSize, isArabic: isArabic)

                    XCTAssertGreaterThanOrEqual(spacing, 0,
                        "\(style.rawValue) isArabic=\(isArabic) factor=\(factor): lineSpacing went negative -- this is exactly the withdrawn-formula failure mode")

                    if !isClamped {
                        let renderedRatio = (scaledSize * MentoraTypographyRules.naturalLineHeightFactor + spacing) / scaledSize
                        XCTAssertEqual(renderedRatio, ratio, accuracy: 0.000001,
                            "\(style.rawValue) isArabic=\(isArabic) factor=\(factor): rendered leading ratio drifted from the target as size scaled")
                    }
                }
            }
        }
    }

    func test_trackingUnscaledAndZeroedForArabic() {
        for style in MentoraTextStyle.allCases {
            XCTAssertEqual(MentoraTypographyRules.tracking(for: style, isArabic: false), style.metrics.tracking, accuracy: 0.0001, "\(style.rawValue) en tracking")
            XCTAssertEqual(MentoraTypographyRules.tracking(for: style, isArabic: true), 0, "\(style.rawValue) ar tracking")
        }
    }

    func test_arabicRatioBumpAppliesToBodyOnly() {
        for style in MentoraTextStyle.allCases {
            let en = MentoraTypographyRules.lineHeightRatio(for: style, isArabic: false)
            let ar = MentoraTypographyRules.lineHeightRatio(for: style, isArabic: true)
            let expectedMultiplier: CGFloat = style.isBodyStep ? 1.10 : 1.0
            XCTAssertEqual(ar / en, expectedMultiplier, accuracy: 0.0001, "\(style.rawValue) ar/en ratio multiplier")
        }
    }

    func test_isArabicDetection() {
        XCTAssertTrue(MentoraTypographyRules.isArabic(Locale(identifier: "ar")))
        XCTAssertTrue(MentoraTypographyRules.isArabic(Locale(identifier: "ar-u-nu-latn")))
        XCTAssertTrue(MentoraTypographyRules.isArabic(Locale(identifier: "ar_EG")))
        XCTAssertFalse(MentoraTypographyRules.isArabic(Locale(identifier: "en")))
        XCTAssertFalse(MentoraTypographyRules.isArabic(Locale(identifier: "en-US")))
    }

    func test_naturalLineHeightFactorIsOneConstant() {
        XCTAssertEqual(MentoraTypographyRules.naturalLineHeightFactor, 1.2)
    }
}
