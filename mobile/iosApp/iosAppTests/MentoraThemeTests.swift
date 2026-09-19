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
        // digit-rendering assertions cannot serve as an indirect guard on this platform (Apple's ICU
        // already defaults bare "ar" to Latin digits -- see that test's own correction comment), so
        // this is the one assertion that would actually catch arabicLocaleIdentifier silently losing
        // its numbering-system extension (e.g. a typo dropping "-u-nu-latn" entirely).
        XCTAssertTrue(MentoraThemeRules.arabicLocaleIdentifier.contains("-u-nu-latn"),
            "arabicLocaleIdentifier must carry the -u-nu-latn numbering-system extension")
    }

    // MARK: - 5. Arabic locale forces Western (ASCII) numerals

    /// `NumberFormatter` is the real Foundation API that respects a `Locale`'s numbering-system
    /// extension (`-u-nu-latn`) when formatting. `arabicLocaleIdentifier`'s `-u-nu-latn` suffix exists
    /// to force Western/ASCII digits (`design-system/LOCALIZATION.md § 8`).
    ///
    /// CORRECTION (CI run #24, `ios-ci.yml`, T6 slice 3b): an earlier version of this test asserted
    /// that bare `"ar"` (no `-u-nu-latn`) renders Eastern Arabic-Indic digits (٠-٩) by default, and used
    /// that as a negative control. That assumption is FALSE on Apple platforms: Apple's own ICU data
    /// patches the `ar` locale's default numbering system to `latn`, not `arab` (confirmed against
    /// `apple-oss-distributions/ICU`'s `ar.txt` across the ICU versions this CI's Xcode/simulator could
    /// select -- `default{"latn"}`, with `arab` reachable only as the explicit `native` system, never
    /// the default; the equivalent upstream CLDR change landed independently at CLDR 46). So a bare
    /// `"ar"` `NumberFormatter` was never going to produce Eastern digits here, regardless of
    /// `numberStyle` -- the earlier fix attempt (`.none` -> `.decimal`) targeted the wrong mechanism.
    /// The correct negative control forces the numbering system EXPLICITLY via the same `-u-nu-*`
    /// extension mechanism `arabicLocaleIdentifier` itself depends on (`"ar-u-nu-arab"`, not bare
    /// `"ar"`), which is deterministic across ICU/CLDR versions and vendors rather than resting on a
    /// locale's default numbering-system data (exactly the kind of platform-default assumption that
    /// just broke). `numberStyle` reverted to `.none` -- it was never the actual issue.
    func test_arabicLocaleForcesWesternNumerals() {
        let value = 1_234_567_890

        let formatter = NumberFormatter()
        formatter.locale = MentoraThemeRules.foundationLocale(for: .arabic)
        formatter.numberStyle = .none
        formatter.usesGroupingSeparator = false

        let formatted = formatter.string(from: NSNumber(value: value))
        XCTAssertEqual(formatted, String(value),
            "Arabic locale (ar-u-nu-latn) must format digits identically to plain ASCII -- got \(formatted ?? "nil")")

        let easternArabicDigits = CharacterSet(charactersIn: "٠١٢٣٤٥٦٧٨٩")
        XCTAssertNil(formatted?.unicodeScalars.first { easternArabicDigits.contains($0) },
            "Formatted output must contain no Eastern Arabic-Indic digit characters")

        // Negative control: proves NumberFormatter actually honors the `-u-nu-*` numbering-system
        // extension mechanism at all (the same mechanism arabicLocaleIdentifier depends on), rather
        // than this test passing merely because of some other, unrelated reason. Uses an EXPLICIT
        // "ar-u-nu-arab" extension rather than relying on bare "ar"'s default numbering system --
        // that default is ICU/CLDR-version- and vendor-dependent (Apple's ICU defaults bare "ar" to
        // "latn", not "arab" -- see the correction above), so asserting against it would silently stop
        // testing anything the moment a platform's default data changes, exactly as just happened here.
        let arabDigitsFormatter = NumberFormatter()
        arabDigitsFormatter.locale = Locale(identifier: "ar-u-nu-arab")
        arabDigitsFormatter.numberStyle = .none
        arabDigitsFormatter.usesGroupingSeparator = false
        let arabDigitsFormatted = arabDigitsFormatter.string(from: NSNumber(value: value))
        XCTAssertNotNil(arabDigitsFormatted?.unicodeScalars.first { easternArabicDigits.contains($0) },
            "\"ar-u-nu-arab\" is expected to render Eastern Arabic-Indic digits -- if it no longer does, " +
            "NumberFormatter no longer honors the -u-nu-* extension mechanism at all, and this test's " +
            "positive assertions above are no longer a real regression guard for arabicLocaleIdentifier")
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
