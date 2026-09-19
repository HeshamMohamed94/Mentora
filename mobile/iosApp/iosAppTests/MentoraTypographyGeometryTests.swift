import XCTest
import SwiftUI
import UIKit
@testable import iosApp

// Phase 5 Task T6 slice 2 -- Dynamic Type GEOMETRY-rendering tests for typography, layered on top of
// slice 1's pure-function tests (`MentoraTypographyTests.swift`). Slice 1 proves the FORMULAS in
// `MentoraTypographyRules` are correct as pure math; this file proves those formulas actually reach
// real rendered `Text` geometry through `MentoraFontModifier`'s `@ScaledMetric` + `.tracking` +
// `.lineSpacing` wiring, end to end, via `UIHostingController` measurement.
//
// `import UIKit` is required because this file calls `UIView.setNeedsLayout()`/`.layoutIfNeeded()`
// directly on `UIHostingController.view` (`UIHostingController` itself is declared in SwiftUI, not
// UIKit, but its `.view` is a plain `UIView`) -- and module imports are not transitive through
// `SwiftUI` or `@testable import iosApp`, the same class of bug slice 1's review caught for a missing
// `import SwiftUI`. Each file must declare every module it uses directly.
//
// WHY EVERY ASSERTION HERE IS A RATIO OR A DIFFERENTIAL, NEVER AN ABSOLUTE FONT METRIC:
// `.github/workflows/ios-ci.yml`'s simulator runtime is NOT pinned to a fixed OS version (it selects
// the newest available), so any assertion against an absolute font metric (a literal point height, a
// literal pixel width) is a future flake by construction -- SF Pro/SF Arabic metrics have shifted
// across OS releases before and will again. Every assertion below is therefore either:
//   (a) a ratio or a differential measured WITHIN one run (immune to font-metric drift across OS
//       versions -- both sides of the comparison render under the same OS/toolchain), or
//   (b) an en-vs-ar difference over IDENTICAL ASCII text in an IDENTICAL font (every font unknown --
//       the actual glyph metrics of the substituted face -- cancels algebraically, leaving only the
//       modifier's own `isArabic`-gated behavior).
// No test in this file asserts "this style is N points tall" or "this run is N pixels wide" in
// isolation.
@MainActor
final class MentoraTypographyGeometryTests: XCTestCase {

    private let en = Locale(identifier: "en")
    private let ar = Locale(identifier: "ar")

    // MARK: - 1. Guard test: prove the harness itself is real

    /// Order matters: this MUST run first (alphabetically it also sorts first, but that's incidental
    /// -- the real reason it's first in this file is narrative). If THIS test fails, every other test
    /// in this file is measuring the same size twice under two different labels and is meaningless --
    /// diagnose this one before trusting any red/green result below it.
    ///
    /// `.large` is `@ScaledMetric`'s identity content-size category (no scaling relative to the
    /// system default), so `scaledSize(style, .large)` should reproduce the token's own unscaled
    /// `fontSize` to within measurement noise. `.accessibility5` is the largest standard category, so
    /// a real injected Dynamic Type size must grow the measured value substantially.
    ///
    /// Contingency: if this fails, the most likely cause is that `.dynamicTypeSize(dts)` applied to
    /// the probe view is not reaching the `@ScaledMetric` inside `MentoraFontModifier`/`ScaledSizeProbe`
    /// through `UIHostingController`. The fix in that case is to ALSO apply
    /// `.environment(\.sizeCategory, ...)` (the pre-`DynamicTypeSize` UIKit-bridged environment key) on
    /// the root view passed to `UIHostingController`, since some hosting paths only forward one of the
    /// two.
    func test_injectedDynamicTypeSizeActuallyReachesScaledMetric() {
        for style in MentoraTextStyle.allCases {
            let large = Geometry.scaledSize(style, .large)
            XCTAssertEqual(large, style.metrics.fontSize, accuracy: 0.05,
                "\(style.rawValue): .large scaledSize should reproduce the unscaled token fontSize. " +
                "If this fails, see this test's doc comment for the .environment(\\.sizeCategory, ...) contingency.")

            let ax5 = Geometry.scaledSize(style, .accessibility5)
            XCTAssertGreaterThanOrEqual(ax5, large * 1.4,
                "\(style.rawValue): .accessibility5 scaledSize did not grow meaningfully over .large -- " +
                "the injected DynamicTypeSize is likely not reaching @ScaledMetric. See this test's doc comment.")
        }
    }

    // MARK: - 2. lineSpacing stays non-negative at real resolved scaled sizes

    /// Slice 1 already proves `lineSpacing >= 0` for synthetic scale factors as a pure function. This
    /// repeats the check using REAL scaled sizes resolved through `UIHostingController` +
    /// `@ScaledMetric`, closing the gap between "the formula never goes negative" and "the formula
    /// never goes negative for any size Dynamic Type will actually produce".
    func test_lineSpacingIsNonNegativeAtRealResolvedScaledSizes() {
        for style in MentoraTextStyle.allCases {
            for isArabic in [false, true] {
                for dts in [DynamicTypeSize.large, .accessibility5] {
                    let s = Geometry.scaledSize(style, dts)
                    let spacing = MentoraTypographyRules.lineSpacing(for: style, scaledSize: s, isArabic: isArabic)
                    XCTAssertGreaterThanOrEqual(spacing, 0,
                        "\(style.rawValue) isArabic=\(isArabic) dts=\(dts): lineSpacing went negative at a real resolved scaledSize=\(s)")
                }
            }
        }
    }

    // MARK: - 3. Rendered line pitch never collapses into overlap

    /// `Geometry.linePitch` measures the actual vertical distance between successive baselines by
    /// differencing the height of an 11-line block from a 1-line block (dividing out the one-off
    /// leading/trailing inset that a single line's `sizeThatFits` otherwise includes). The 1.05 floor
    /// here is DELIBERATELY conservative -- the true floor implied by `naturalLineHeightFactor` is
    /// ~1.19 for SF Pro (and larger still for the substituted SF Arabic face) -- so this can never
    /// spuriously fail on an ordinary font-metric change across OS versions. It exists solely to catch
    /// a regression of the class that the previously WITHDRAWN `lineSpacing` formula produced: that
    /// formula's accessibility-size pitch-to-size ratio was measured at ~0.70 (a real ~30%+ collapse
    /// into overlapping lines), which this test would have caught immediately.
    ///
    /// Caveat: `NSParagraphStyle.lineSpacing` is documented as always non-negative, so if SwiftUI
    /// clamps a negative `.lineSpacing(_:)` value to 0 rather than honoring it, the withdrawn formula
    /// would produce no visible overlap and this specific test would not catch it on its own --
    /// `test_leadingRatioIsPreservedFromLargeToAccessibility5` below is the robust backstop for that
    /// case (a clamped-to-0 lineSpacing still collapses the measured pitch/size ratio by >30% between
    /// `.large` and `.accessibility5`, well outside its 3% tolerance either way).
    func test_renderedLinePitchNeverCollapsesIntoOverlap() {
        for style in MentoraTextStyle.allCases {
            for isArabic in [false, true] {
                let line = isArabic ? Sample.arabicLine : Sample.latinLine
                let locale = isArabic ? ar : en
                for dts in [DynamicTypeSize.large, .accessibility5] {
                    let s = Geometry.scaledSize(style, dts)
                    let p = Geometry.linePitch(style: style, line: line, locale: locale, dts: dts)
                    XCTAssertGreaterThanOrEqual(p, s * 1.05,
                        "\(style.rawValue) isArabic=\(isArabic) dts=\(dts): rendered line pitch (\(p)) collapsed toward the scaled font size (\(s)) -- possible line overlap")
                }
            }
        }
    }

    // MARK: - 4. Leading ratio is preserved from .large to .accessibility5

    /// Asserts scale-invariance of the MEASURED pitch-to-size ratio between two real content-size
    /// categories, NOT equality to the token's `lineHeightRatio`. The token's `naturalLineHeightFactor
    /// = 1.2` is only an approximation of the real natural leading factor (~1.19 for the SF Pro Latin
    /// face, and materially larger for the substituted SF Arabic face), so asserting the MEASURED
    /// ratio equals the TOKEN ratio from real rendered geometry is not achievable and was not
    /// attempted here. What IS asserted, and IS achievable, is that whatever the real ratio measures
    /// out to at `.large`, it holds (within a small tolerance) at `.accessibility5` too -- i.e. the
    /// formula's scale-invariance survives real rendering, not just pure arithmetic.
    ///
    /// If CI ever reports drift between 3% and 10% here, treat that as a real font-metric finding to
    /// record in DECISIONS_LOG.md, not a reason to silently widen the tolerance.
    func test_leadingRatioIsPreservedFromLargeToAccessibility5() {
        for style in MentoraTextStyle.allCases {
            for isArabic in [false, true] {
                let line = isArabic ? Sample.arabicLine : Sample.latinLine
                let locale = isArabic ? ar : en

                let sLarge = Geometry.scaledSize(style, .large)
                let pLarge = Geometry.linePitch(style: style, line: line, locale: locale, dts: .large)
                let rLarge = pLarge / sLarge

                let sAx5 = Geometry.scaledSize(style, .accessibility5)
                let pAx5 = Geometry.linePitch(style: style, line: line, locale: locale, dts: .accessibility5)
                let rAx5 = pAx5 / sAx5

                XCTAssertEqual(rAx5, rLarge, accuracy: rLarge * 0.03,
                    "\(style.rawValue) isArabic=\(isArabic): measured pitch/size ratio drifted between .large (\(rLarge)) and .accessibility5 (\(rAx5))")
            }
        }
    }

    // MARK: - 5. Height and pitch both grow with Dynamic Type size

    /// A second, content-geometry guard against a silently-no-op environment injection, paired with
    /// test 1's `@ScaledMetric`-level guard: even if `@ScaledMetric` itself resolved correctly but a
    /// later modifier in the chain (`.fixedSize`, `.environment(\.locale, ...)` ordering, etc.)
    /// somehow suppressed the effect on actual `Text` layout, this would catch it. 1.3 is a
    /// conservative floor -- the real minimum growth factor across these styles is ~1.56.
    func test_renderedHeightAndPitchGrowWithDynamicTypeSize() {
        for style in MentoraTextStyle.allCases {
            for isArabic in [false, true] {
                let line = isArabic ? Sample.arabicLine : Sample.latinLine
                let locale = isArabic ? ar : en

                let h11Large = Geometry.textSize(style: style, text: Sample.block(line, lines: Geometry.pitchLineCount), locale: locale, dts: .large).height
                let h11Ax5 = Geometry.textSize(style: style, text: Sample.block(line, lines: Geometry.pitchLineCount), locale: locale, dts: .accessibility5).height
                XCTAssertGreaterThanOrEqual(h11Ax5, h11Large * 1.3,
                    "\(style.rawValue) isArabic=\(isArabic): 11-line block height did not grow with Dynamic Type size")

                let pLarge = Geometry.linePitch(style: style, line: line, locale: locale, dts: .large)
                let pAx5 = Geometry.linePitch(style: style, line: line, locale: locale, dts: .accessibility5)
                XCTAssertGreaterThanOrEqual(pAx5, pLarge * 1.3,
                    "\(style.rawValue) isArabic=\(isArabic): line pitch did not grow with Dynamic Type size")
            }
        }
    }

    // MARK: - 6. Tracking is applied unscaled and zeroed for Arabic

    /// Measures a DOUBLE differential, not a single one: `(W_en(560) - W_ar(560)) - (W_en(280) -
    /// W_ar(280))`. A single `W(560) - W(280)` differential does NOT isolate tracking -- it leaves one
    /// full copy of the (unknown) glyph-advance contribution behind, since doubling the run doubles
    /// BOTH the advance total and the tracking total, and subtracting only removes one copy of each.
    /// The en-vs-ar subtraction is what actually cancels the glyph-advance term (identical ASCII glyphs
    /// render at an identical advance under both locales -- only the `isArabic`-gated tracking differs),
    /// leaving exactly `280 * tracking` (the count-of-gaps off-by-one convention, `n` vs `n-1` gaps,
    /// cancels too, since it's identical on both sides of the en/ar subtraction).
    func test_trackingIsAppliedUnscaledAndZeroedForArabic() {
        for style in MentoraTextStyle.allCases {
            var trackedDeltas: [DynamicTypeSize: CGFloat] = [:]

            for dts in [DynamicTypeSize.large, .accessibility5] {
                let wEn280 = Geometry.textSize(style: style, text: Sample.trackingRun, locale: en, dts: dts).width
                let wEn560 = Geometry.textSize(style: style, text: Sample.trackingRunLong, locale: en, dts: dts).width
                let wAr280 = Geometry.textSize(style: style, text: Sample.trackingRun, locale: ar, dts: dts).width
                let wAr560 = Geometry.textSize(style: style, text: Sample.trackingRunLong, locale: ar, dts: dts).width

                let tracked = (wEn560 - wAr560) - (wEn280 - wAr280)
                trackedDeltas[dts] = tracked

                XCTAssertEqual(tracked, 280 * style.metrics.tracking, accuracy: 4.0,
                    "\(style.rawValue) dts=\(dts): en-vs-ar tracking-attributable width delta did not match 280 * token tracking")
            }

            XCTAssertEqual(trackedDeltas[.accessibility5]!, trackedDeltas[.large]!, accuracy: 4.0,
                "\(style.rawValue): tracking-attributable width delta changed between .large and .accessibility5 -- tracking should NOT be scaled by Dynamic Type")
        }
    }

    // MARK: - 7. Arabic line-spacing override fires geometrically, on body steps only

    /// Uses `Sample.latinLine` (pure ASCII) under BOTH the `en` and `ar` locales, DELIBERATELY --
    /// this isolates `MentoraFontModifier`'s own Arabic line-spacing bump (`isArabic` gated purely
    /// off `@Environment(\.locale)`, per `MentoraTypographyRules`) from SF Arabic's own different
    /// natural leading, which would otherwise contaminate the comparison if Arabic glyphs were used.
    /// Rendering the same Latin text under an `ar` locale environment still selects the `en`/Latin
    /// font face for the glyphs themselves (no bundled font, no `layoutDirection` injected here) but
    /// still flips `MentoraTypographyRules.isArabic(locale)` to `true` inside the modifier -- exactly
    /// the variable this test wants isolated.
    func test_arabicLineSpacingOverrideFiresGeometricallyOnBodyStepsOnly() {
        for style in MentoraTextStyle.allCases {
            for dts in [DynamicTypeSize.large, .accessibility5] {
                let pEn = Geometry.linePitch(style: style, line: Sample.latinLine, locale: en, dts: dts)
                let pAr = Geometry.linePitch(style: style, line: Sample.latinLine, locale: ar, dts: dts)

                let s = Geometry.scaledSize(style, dts)
                let expectedDelta = MentoraTypographyRules.lineSpacing(for: style, scaledSize: s, isArabic: true)
                    - MentoraTypographyRules.lineSpacing(for: style, scaledSize: s, isArabic: false)

                XCTAssertEqual(pAr - pEn, expectedDelta, accuracy: 0.75,
                    "\(style.rawValue) dts=\(dts): measured ar-vs-en line pitch delta did not match the predicted lineSpacing delta. " +
                    "Expected ~0 for the 9 non-body steps and a real positive value (growing at accessibility sizes) for the 3 body steps.")
            }
        }
    }
}

// MARK: - Private harness

private enum Sample {
    static let latinLine  = "Mentora lesson"
    static let arabicLine = "مرحبا بك في منتورا"  // undiacritized deliberately -- combining marks can add height in some layout paths
    static let trackingRun     = String(repeating: "Mentora", count: 40)   // 280 chars
    static let trackingRunLong = String(repeating: "Mentora", count: 80)   // 560 chars
    static func block(_ line: String, lines: Int) -> String {
        Array(repeating: line, count: lines).joined(separator: "\n")
    }
}

/// A minimal probe view whose measured (via `Geometry.measure`) size encodes `@ScaledMetric`'s
/// resolved value, amplified x100 so pixel rounding at any simulator scale factor contributes well
/// under 0.01pt of error once divided back out. Hoisted to file scope (rather than nested inside a
/// function, as an earlier draft had it) since a `View`-conforming type with a `@ScaledMetric` stored
/// property is otherwise unprecedented in this repo and is easier to read/verify in isolation.
private struct ScaledSizeProbe: View {
    let style: MentoraTextStyle
    var body: some View {
        Color.clear.frame(width: measuredScaledSize * 100, height: measuredScaledSize * 100)
    }
    @ScaledMetric private var measuredScaledSize: CGFloat
    init(style: MentoraTextStyle) {
        self.style = style
        _measuredScaledSize = ScaledMetric(wrappedValue: style.metrics.fontSize, relativeTo: style.anchor)
    }
}

private enum Geometry {
    static let proposal = CGSize(width: 200_000, height: 200_000)
    static let pitchLineCount = 11  // pitch = (H(11 lines) - H(1 line)) / 10 -- see rationale above

    @MainActor
    static func measure<V: View>(_ view: V, file: StaticString = #filePath, line: UInt = #line) -> CGSize {
        let host = UIHostingController(rootView: view)
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        let size = host.sizeThatFits(in: proposal)
        if !size.width.isFinite || !size.height.isFinite || size.width <= 0 || size.height <= 0 {
            XCTFail("UIHostingController measurement produced a non-finite or non-positive size: \(size). If this ever fails on a future OS/toolchain, fall back to: host.view.frame = CGRect(origin: .zero, size: proposal); host.view.systemLayoutSizeFitting(proposal, withHorizontalFittingPriority: .fittingSizeLevel, verticalFittingPriority: .fittingSizeLevel)", file: file, line: line)
            return .zero
        }
        return size
    }

    @MainActor
    static func scaledSize(_ style: MentoraTextStyle, _ dts: DynamicTypeSize, file: StaticString = #filePath, line: UInt = #line) -> CGFloat {
        let size = measure(ScaledSizeProbe(style: style).dynamicTypeSize(dts), file: file, line: line)
        return size.width / 100
    }

    @MainActor
    static func textSize(style: MentoraTextStyle, text: String, locale: Locale, dts: DynamicTypeSize, file: StaticString = #filePath, line: UInt = #line) -> CGSize {
        measure(
            Text(verbatim: text)
                .mentoraFont(style)
                .fixedSize(horizontal: true, vertical: true)
                .environment(\.locale, locale)
                .dynamicTypeSize(dts),
            file: file, line: line
        )
    }

    @MainActor
    static func linePitch(style: MentoraTextStyle, line: String, locale: Locale, dts: DynamicTypeSize, file: StaticString = #filePath, sourceLine: UInt = #line) -> CGFloat {
        let h11 = textSize(style: style, text: Sample.block(line, lines: pitchLineCount), locale: locale, dts: dts, file: file, line: sourceLine).height
        let h1 = textSize(style: style, text: Sample.block(line, lines: 1), locale: locale, dts: dts, file: file, line: sourceLine).height
        return (h11 - h1) / CGFloat(pitchLineCount - 1)
    }
}
