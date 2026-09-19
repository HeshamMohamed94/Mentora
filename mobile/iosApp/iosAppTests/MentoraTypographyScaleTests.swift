import XCTest
import SwiftUI
import UIKit
@testable import iosApp

// Phase 5 Task T8 slice 1 -- tests for the ADDITIVE `scale` parameter on `MentoraFontModifier` /
// `.mentoraFont(_:scale:)` (`Theme/MentoraTypography.swift`), added for `Avatar`'s "label.large
// scaled to avatar size" requirement (`COMPONENTS.md § Avatar`). A NEW file (not appended to
// `MentoraTypographyTests.swift`/`MentoraTypographyGeometryTests.swift`) since it tests a distinct,
// additive capability layered on top of slice 1/2's already-complete 249-line typography math, which
// this file does not re-touch.
//
// `import UIKit` is required directly for `UIHostingController` (module imports are not transitive --
// the same rule every other file in this target's header comments already call out).
@MainActor
final class MentoraTypographyScaleTests: XCTestCase {

    // MARK: - 1. Pure arithmetic: `scale: 1.0` is EXACTLY a no-op

    /// `scaledSize * 1.0 == scaledSize` is bit-identical for any finite `CGFloat` under IEEE 754
    /// multiplication -- asserted here with NO accuracy tolerance (`XCTAssertEqual` on `CGFloat`,
    /// exact equality), for every one of the 12 styles' own token `fontSize`, plus a few arbitrary
    /// values, so this is provable as a fact about the FORMULA itself, not merely "close enough" at
    /// whatever sizes Dynamic Type happens to resolve to on the CI runner's OS version.
    func test_scaleOneIsExactlyANoOp_pureFormula() {
        for style in MentoraTextStyle.allCases {
            let size = style.metrics.fontSize
            XCTAssertEqual(MentoraTypographyRules.scaledFontSize(scaledSize: size, scale: 1.0), size,
                "\(style.rawValue): scale 1.0 must reproduce scaledSize exactly, bit-for-bit")
        }
        for size: CGFloat in [0, 1, 12.5, 96, 1234.567] {
            XCTAssertEqual(MentoraTypographyRules.scaledFontSize(scaledSize: size, scale: 1.0), size)
        }
    }

    // MARK: - 2. Pure arithmetic: a real non-1.0 scale produces the exact expected proportional size

    /// The exact ratio `Avatar` needs: `.xlarge` (96pt) scaled relative to the `.medium` (40pt)
    /// baseline is `96 / 40 = 2.4`. Asserted as EXACT arithmetic (not an accuracy-tolerant rendered-
    /// geometry ratio) -- a rendered-geometry measurement of the two Text views would be contaminated
    /// by the deliberately UNSCALED `tracking`/`lineSpacing` contributions (see
    /// `MentoraFontModifier.scale`'s doc comment), which do not participate in this ratio at all and
    /// would make an end-to-end geometry assertion either flaky or wrong, not stronger.
    func test_realScaleProducesExactProportionalSize_pureFormula() {
        XCTAssertEqual(MentoraTypographyRules.scaledFontSize(scaledSize: 40, scale: 96.0 / 40.0), 96, accuracy: 0.0001)
        XCTAssertEqual(MentoraTypographyRules.scaledFontSize(scaledSize: 40, scale: 24.0 / 40.0), 24, accuracy: 0.0001)
        XCTAssertEqual(MentoraTypographyRules.scaledFontSize(scaledSize: 40, scale: 64.0 / 40.0), 64, accuracy: 0.0001)
        XCTAssertEqual(MentoraTypographyRules.scaledFontSize(scaledSize: 14, scale: 2.0), 28, accuracy: 0.0001)
    }

    // MARK: - 3. Rendered no-op: `.mentoraFont(_:)` and `.mentoraFont(_:scale: 1.0)` are byte-identical

    /// Both overloads route through the exact SAME `MentoraFontModifier` with the exact SAME literal
    /// `1.0` scale value (the no-scale overload's `init` default), so this end-to-end render carries
    /// none of test 2's contamination risk -- there is no scale-dependent quantity being measured
    /// here at all, only "do the two call sites produce the identical view". Measured via
    /// `UIHostingController`, matching this repo's existing `MentoraTypographyGeometryTests.swift`
    /// harness convention exactly (its own file, not imported, since these two files' assertions are
    /// otherwise unrelated).
    func test_scaleOneIsExactlyANoOp_rendered() {
        let en = Locale(identifier: "en")
        for style in MentoraTextStyle.allCases {
            let unscaled = measureSize(
                Text(verbatim: "Mentora").mentoraFont(style)
                    .fixedSize(horizontal: true, vertical: true)
                    .environment(\.locale, en)
            )
            let scaledByOne = measureSize(
                Text(verbatim: "Mentora").mentoraFont(style, scale: 1.0)
                    .fixedSize(horizontal: true, vertical: true)
                    .environment(\.locale, en)
            )
            XCTAssertEqual(scaledByOne.width, unscaled.width, "\(style.rawValue): scale: 1.0 width differed from the no-scale overload")
            XCTAssertEqual(scaledByOne.height, unscaled.height, "\(style.rawValue): scale: 1.0 height differed from the no-scale overload")
        }
    }

    // MARK: - Harness

    /// Named `measureSize`, not `measure` -- `XCTestCase` already declares an inherited
    /// `measure(_ block: () -> Void)` (performance testing); a same-named overload on a concrete `View`
    /// argument resolves correctly today (only the generic candidate is viable for a non-`() -> Void`
    /// argument), but this repo's own precedent (`MentoraTypographyGeometryTests.swift`'s `Geometry`
    /// enum) deliberately avoids the shadow entirely rather than relying on overload resolution to
    /// pick the right one in code no compiler here can check.
    private func measureSize<V: View>(_ view: V) -> CGSize {
        let host = UIHostingController(rootView: view)
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        return host.sizeThatFits(in: CGSize(width: 200_000, height: 200_000))
    }
}
