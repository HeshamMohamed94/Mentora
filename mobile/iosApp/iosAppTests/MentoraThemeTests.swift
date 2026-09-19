import XCTest
import SwiftUI
import UIKit
import shared
@testable import iosApp

// Phase 5 Task T6 slice 3b -- theme-root wiring (`Theme/MentoraTheme.swift`). Covers the pure rules in
// `MentoraThemeRules` (color scheme / locale / layout-direction mapping), the cross-slice integration
// with slice 1's `MentoraTypographyRules.isArabic(_:)`, and a real environment-propagation round-trip
// through `.mentoraTheme(...)` via the same `UIHostingController` harness pattern established by
// `MentoraTypographyGeometryTests.swift`.
//
// WHAT THIS FILE DELIBERATELY DOES NOT TEST: a round-trip of `.preferredColorScheme` itself reaching
// `\.colorScheme`. `.preferredColorScheme` is a SwiftUI PREFERENCE, not a plain environment write -- it
// propagates UP to the enclosing presentation/window (the mechanism `\.locale`/`\.layoutDirection`'s
// `.transformEnvironment` do not use). A bare, unattached `UIHostingController` in a unit test has no
// real window/scene to receive that preference, so any such round-trip here would not be reliably
// meaningful and would risk becoming a future flake rather than a real regression guard. This is
// intentionally left to code review (W) plus the MC-3 live device/simulator check
// (`PHASE_5_IOS_SYSTEM_DESIGN.md § 14`), not to this test file.
@MainActor
final class MentoraThemeTests: XCTestCase {

    // MARK: - 1. colorScheme(for:) mapping

    func test_colorSchemeMapping() {
        XCTAssertEqual(MentoraThemeRules.colorScheme(for: .light), .light)
        XCTAssertEqual(MentoraThemeRules.colorScheme(for: .dark), .dark)
        XCTAssertNil(MentoraThemeRules.colorScheme(for: .system),
            ".system must map to nil so .preferredColorScheme(nil) keeps following live OS appearance changes")
    }

    // MARK: - 2. Enum case-count guard

    /// Guards the exhaustive, `default:`-free switches in `MentoraThemeRules` against a future Kotlin
    /// case silently going unmapped. If either count ever changes, `MentoraThemeRules` must be updated
    /// to handle the new case FIRST (which will itself fail to compile until done, since none of its
    /// switches has a `default:` clause) -- this test is a second, independent guard for anyone who
    /// only reads test failures rather than compiler errors.
    func test_enumCaseCountsGuardAgainstUnmappedFutureCases() {
        XCTAssertEqual(ThemePreference.allCases.count, 3,
            "ThemePreference gained/lost a case -- MentoraThemeRules.colorScheme(for:) must be updated to handle it")
        XCTAssertEqual(AppLocale.allCases.count, 2,
            "AppLocale gained/lost a case -- MentoraThemeRules.foundationLocale(for:)/layoutDirection(for:) must be updated to handle it")
    }

    // MARK: - 3. layoutDirection(for:) mapping

    func test_layoutDirectionMapping() {
        XCTAssertEqual(MentoraThemeRules.layoutDirection(for: .arabic), .rightToLeft)
        XCTAssertEqual(MentoraThemeRules.layoutDirection(for: .english), .leftToRight)
    }

    // MARK: - 4. Arabic locale keeps the "ar" language subtag (cross-slice integration)

    /// `ar-u-nu-latn`'s `-u-nu-latn` suffix is a Unicode locale EXTENSION (numbering system), not a
    /// language-subtag change -- `language.languageCode` must still read `"ar"`. This is asserted both
    /// directly and via slice 1's real `MentoraTypographyRules.isArabic(_:)` (`Theme/MentoraTypography.swift`)
    /// so a typo in `arabicLocaleIdentifier` that broke Arabic detection would be caught here, not only
    /// discovered later via a typography-side symptom.
    func test_arabicLocaleKeepsArabicLanguageSubtag() {
        let arabicLocale = MentoraThemeRules.foundationLocale(for: .arabic)
        XCTAssertEqual(arabicLocale.language.languageCode?.identifier, "ar")

        XCTAssertTrue(MentoraTypographyRules.isArabic(arabicLocale),
            "MentoraThemeRules.foundationLocale(for: .arabic) must keep MentoraTypographyRules.isArabic(_:) true")
        XCTAssertFalse(MentoraTypographyRules.isArabic(MentoraThemeRules.foundationLocale(for: .english)),
            "MentoraThemeRules.foundationLocale(for: .english) must keep MentoraTypographyRules.isArabic(_:) false")

        // Direct typo guard on the identifier string itself. test_arabicLocaleForcesWesternNumerals's
        // digit-rendering assertions were vacuous under numberStyle = .none (CI runs #24/#25 -- see
        // that test's own correction comment), so this is the one assertion that reliably catches
        // arabicLocaleIdentifier silently losing its numbering-system extension (e.g. a typo dropping
        // "-u-nu-latn" entirely).
        XCTAssertTrue(MentoraThemeRules.arabicLocaleIdentifier.contains("-u-nu-latn"),
            "arabicLocaleIdentifier must carry the -u-nu-latn numbering-system extension")
    }

    // MARK: - 5. Arabic locale forces Western (ASCII) numerals

    /// `arabicLocaleIdentifier`'s `-u-nu-latn` suffix exists to force Western/ASCII digits
    /// (`design-system/LOCALIZATION.md § 8`). This formats a large integer through the REAL production
    /// locale and asserts the rendered digits are ASCII.
    ///
    /// CORRECTION (CI runs #24 and #25, `ios-ci.yml`): BOTH previous failures of this test had the same
    /// single cause -- `numberStyle = .none` -- and neither had anything to do with locale default data.
    /// `.none` (`kCFNumberFormatterNoStyle`) is CoreFoundation's deliberately NON-localized integer
    /// style: it emits unshaped ASCII digits regardless of the locale's numbering system, whether that
    /// system comes from the locale's default data (run #24, bare `"ar"`) or from an EXPLICIT
    /// `-u-nu-arab` extension (run #25). Run #24's "passing" positive assertion was therefore NOT
    /// evidence that `-u-nu-latn` was honored -- ASCII is also exactly what "no shaping at all" looks
    /// like, so under `.none` this test could never have failed even if `arabicLocaleIdentifier` had
    /// silently become bare `"ar"` or `"ar_EG"`. It guarded nothing. `.decimal` is the style that
    /// actually consults the locale's numbering system, so the assertions below are a real guard.
    /// (The earlier claim that Apple's ICU is confirmed to default bare `"ar"` to `latn` rests only on
    /// reading `apple-oss-distributions/ICU`'s open-source data, NOT on anything CI actually observed --
    /// under `.none` the numbering system was never consulted either way, so neither run carries
    /// information about `"ar"`'s real default on this platform.)
    ///
    /// The Eastern-digit NEGATIVE control is deliberately NOT a `NumberFormatter` round-trip anymore.
    /// Whether Darwin resolves a `-u-nu-*` extension at all is still an open question neither CI run
    /// answered, and a regression guard must not depend on an unverified platform behavior to be
    /// non-vacuous. Non-vacuity is proven directly, against a literal, below; the open platform question
    /// is answered by the diagnostics at the end of this test, which only PRINT (never assert) -- see
    /// the `MENTORA-NUMFMT` lines in the `xcodebuild test` log. A future commit can promote whichever
    /// construction the log proves works into a real negative control, with zero guessing.
    func test_arabicLocaleForcesWesternNumerals() {
        let value = 1_234_567_890
        let easternArabicDigits = CharacterSet(charactersIn: "٠١٢٣٤٥٦٧٨٩")

        // Non-vacuity guard: proves the detector below can actually SEE Eastern Arabic-Indic digits,
        // and does not fire on plain ASCII. Without this, the XCTAssertNil assertion further down
        // could pass merely because the detector itself was broken.
        XCTAssertNotNil("١٢٣٤٥٦٧٨٩٠".unicodeScalars.first { easternArabicDigits.contains($0) },
            "Eastern Arabic-Indic detector is broken -- it must match U+0660-U+0669")
        XCTAssertNil(String(value).unicodeScalars.first { easternArabicDigits.contains($0) },
            "Eastern Arabic-Indic detector is broken -- it must not match ASCII digits")

        // `numberStyle` FIRST: assigning a style re-derives the formatter's other defaults, so it must
        // not be set after `usesGroupingSeparator` / the fraction-digit limits.
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.locale = MentoraThemeRules.foundationLocale(for: .arabic)
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 0

        let formatted = formatter.string(from: NSNumber(value: value))
        XCTAssertEqual(formatted, String(value),
            "Arabic locale (\(MentoraThemeRules.arabicLocaleIdentifier)) must render Western/ASCII " +
            "digits with no grouping separator -- got \(formatted ?? "nil")")
        XCTAssertNil(formatted?.unicodeScalars.first { easternArabicDigits.contains($0) },
            "Formatted output must contain no Eastern Arabic-Indic digit characters -- " +
            "got \(formatted ?? "nil")")

        // ---- DIAGNOSTICS ONLY -- no assertions, this block can never fail the test. ----
        // Records what Darwin's real ICU actually does with each way of requesting a numbering system,
        // under both styles, so the still-open questions (is -u-nu-* resolved at all? what IS bare
        // "ar"'s default numbering system on Apple's ICU? does .none shape anything?) are answered by
        // real CI evidence rather than by another round of guess-and-check.
        let probes: [(String, Locale)] = [
            ("production", MentoraThemeRules.foundationLocale(for: .arabic)),
            ("ar", Locale(identifier: "ar")),
            ("ar_EG", Locale(identifier: "ar_EG")),
            ("ar-u-nu-arab", Locale(identifier: "ar-u-nu-arab")),
            ("ar@numbers=arab", Locale(identifier: "ar@numbers=arab")),
            ("ar_EG@numbers=arab", Locale(identifier: "ar_EG@numbers=arab"))
        ]
        let styles: [(String, NumberFormatter.Style)] = [
            ("decimal", NumberFormatter.Style.decimal),
            ("none", NumberFormatter.Style.none)
        ]
        for (label, probeLocale) in probes {
            for (styleName, style) in styles {
                let probeFormatter = NumberFormatter()
                probeFormatter.numberStyle = style
                probeFormatter.locale = probeLocale
                probeFormatter.usesGroupingSeparator = false
                probeFormatter.minimumFractionDigits = 0
                probeFormatter.maximumFractionDigits = 0
                let out = probeFormatter.string(from: NSNumber(value: value)) ?? "<nil>"
                let scalars = out.unicodeScalars
                    .map { String(format: "U+%04X", $0.value) }
                    .joined(separator: " ")
                print("MENTORA-NUMFMT | requested=\(label) | style=\(styleName) " +
                      "| canonical=\(probeLocale.identifier) " +
                      "| nu=\(probeLocale.numberingSystem.identifier) " +
                      "| out=\(out) | scalars=\(scalars)")
            }
        }
    }

    // MARK: - 6. \.locale / \.layoutDirection propagate through .mentoraTheme(...)

    /// Reuses the exact `UIHostingController` + `setNeedsLayout()`/`layoutIfNeeded()` harness pattern
    /// established by `MentoraTypographyGeometryTests.swift`'s `Geometry.measure(_:)`, adapted here to
    /// record `@Environment` values (via a reference-type sink written from the probe's `body`) rather
    /// than measure geometry.
    func test_localeEnvironmentPropagatesThroughMentoraTheme() {
        let arabicSink = probeEnvironment(theme: .light, locale: .arabic)
        XCTAssertEqual(arabicSink.locale?.identifier, MentoraThemeRules.foundationLocale(for: .arabic).identifier)
        XCTAssertEqual(arabicSink.layoutDirection, .rightToLeft)

        let englishSink = probeEnvironment(theme: .light, locale: .english)
        XCTAssertEqual(englishSink.locale?.identifier, MentoraThemeRules.foundationLocale(for: .english).identifier)
        XCTAssertEqual(englishSink.layoutDirection, .leftToRight)

        // nil locale must leave both environment values at their un-transformed default -- i.e.
        // identical to a probe hosted with no .mentoraTheme(...) applied at all.
        let nilSink = probeEnvironment(theme: .light, locale: nil)
        let bareSink = probeEnvironmentBare()
        // Proves the harness itself is live for this leg first -- without this, a probe whose body
        // never ran (e.g. an unattached-hierarchy layout regression) would make both sinks equally
        // nil and the two assertions below would pass vacuously instead of catching anything.
        XCTAssertNotNil(bareSink.locale, "EnvironmentProbe.body did not run -- harness is not live")
        XCTAssertEqual(nilSink.locale?.identifier, bareSink.locale?.identifier,
            "nil locale must leave \\.locale untransformed")
        XCTAssertEqual(nilSink.layoutDirection, bareSink.layoutDirection,
            "nil locale must leave \\.layoutDirection untransformed")
    }

    // MARK: - Private harness

    @MainActor
    private func probeEnvironment(theme: ThemePreference, locale: AppLocale?) -> EnvironmentSink {
        let sink = EnvironmentSink()
        let host = UIHostingController(
            rootView: EnvironmentProbe(sink: sink).mentoraTheme(theme: theme, locale: locale)
        )
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        _ = host.sizeThatFits(in: CGSize(width: 200, height: 200))
        return sink
    }

    @MainActor
    private func probeEnvironmentBare() -> EnvironmentSink {
        let sink = EnvironmentSink()
        let host = UIHostingController(rootView: EnvironmentProbe(sink: sink))
        host.view.setNeedsLayout()
        host.view.layoutIfNeeded()
        _ = host.sizeThatFits(in: CGSize(width: 200, height: 200))
        return sink
    }
}

/// Reference-type sink so `EnvironmentProbe.body` (a `struct` `View`) can record the `@Environment`
/// values it reads during a real SwiftUI render pass, for the test to inspect afterward.
@MainActor
private final class EnvironmentSink {
    var locale: Locale?
    var layoutDirection: LayoutDirection?
}

private struct EnvironmentProbe: View {
    let sink: EnvironmentSink

    @Environment(\.locale) private var locale
    @Environment(\.layoutDirection) private var layoutDirection

    var body: some View {
        sink.locale = locale
        sink.layoutDirection = layoutDirection
        return Color.clear.frame(width: 10, height: 10)
    }
}
